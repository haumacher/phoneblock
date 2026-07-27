#include "heap_guard.h"

#include "esp_log.h"

// Must be last: bans unsafe string APIs for the rest of this file.
// (Placed after every <...> / IDF include below.)

#if CONFIG_HEAP_GUARD_ENABLE

#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "esp_attr.h"
#include "esp_core_dump.h"
#include "esp_err.h"
#include "esp_heap_caps.h"
#include "esp_system.h"
#include "esp_timer.h"

#include "heap_guard_scan.h"
#include "rtp.h"
#include "strbuf.h"

#include "banned_apis.h"

static const char *TAG = "heap_guard";

// How much of the neighbourhood to preserve. The bytes *before* the smashed
// block are the tail of the allocation that overran it — that is where the
// payload identifying the writer lives — plus the smashed header itself. The
// bytes after are for context.
#define HG_WIN_BEFORE   192
#define HG_WIN_AFTER     64

// Boot-walk duration worth complaining about. The heap lock is a portMUX
// spinlock, so the whole walk runs with interrupts disabled on this core; the
// measured figure is logged on every boot so the real cost comes back from the
// field rather than being estimated here.
#define HG_WALK_BUDGET_US 2000

// Marker at the head of the capture so the evidence can be found in a core
// dump with `strings` alone, without gdb or a matching ELF.
#define HG_MAGIC "PBHEAPGUARD1"

// Everything worth knowing about the corruption, laid out to be readable
// straight out of the crashed task's stack. Lives as a local of the function
// that panics: a core dump preserves live task stacks, which is the only part
// of RAM it does preserve (the heap is absent, and capturing all of DRAM does
// not fit the 52 KB coredump partition).
typedef struct {
    char      magic[16];
    char      summary[320];   // human-readable diagnosis, also the abort reason
    uintptr_t heap_start;
    uintptr_t heap_end;
    uintptr_t prev_ptr;       // the allocation that presumably overran
    uint32_t  prev_size;
    uint32_t  prev_used;
    uint32_t  have_prev;
    uintptr_t bad_ptr;        // the block whose header no longer makes sense
    uint32_t  bad_size;
    uint32_t  bad_used;
    uint32_t  blocks;         // blocks walked before the bad one
    uint32_t  verdict;        // hg_verdict_t
    uintptr_t win_from;
    uint32_t  win_len;
    uint8_t   win[HG_WIN_BEFORE + HG_WIN_AFTER];
} hg_capture_t;

// Walk state threaded through the callback.
typedef struct {
    hg_pool_t     pool;       // bounds of the heap currently being walked
    bool          have_prev;
    uintptr_t     prev_ptr;
    size_t        prev_size;
    bool          prev_used;
    uint32_t      blocks;
    hg_verdict_t  verdict;    // HG_OK unless the walk was stopped
    uintptr_t     bad_ptr;    // set alongside `verdict`
    size_t        bad_size;
    hg_capture_t *cap;        // NULL for the boot self-test: judge, don't copy
} hg_walk_t;

// Survives a warm reboot but not a power cycle — exactly the right lifetime for
// "how many times have I panicked myself": it bounds a self-inflicted reboot
// loop, and a user pulling the plug gets a clean slate.
#define HG_RTC_MAGIC 0x48475031u   // 'HGP1'
static RTC_NOINIT_ATTR uint32_t s_rtc_magic;
static RTC_NOINIT_ATTR uint32_t s_rtc_panics;

static bool               s_armed;
static bool               s_deferred;
static esp_timer_handle_t s_timer;

// A ring rather than a single "last operation": the dashboard polls
// /api/status every few seconds, so one slot would be permanently stamped
// "http" and would mask the operation that actually mattered.
//
// Each field is a single word, so a reader never sees a half-written pointer.
// Two tasks noting at the same instant can land in the same slot and lose one
// entry — that costs a line of context in a diagnostic, which is not worth a
// lock on a path meant to be free.
#define HG_NOTES 4
typedef struct {
    const char *what;
    int64_t     at_us;
} hg_note_t;
static volatile hg_note_t s_notes[HG_NOTES];
static volatile uint32_t  s_note_next;

void heap_guard_note(const char *what)
{
    uint32_t i = s_note_next++ % HG_NOTES;
    s_notes[i].what  = what ? what : "?";
    s_notes[i].at_us = esp_timer_get_time();
}

static bool hg_walker(walker_heap_into_t heap, walker_block_info_t blk,
                      void *user)
{
    hg_walk_t *w = (hg_walk_t *)user;

    // heap_caps_walk_all() visits each registered heap in turn. New bounds mean
    // a new pool, so the predecessor chain restarts — a block is only ever the
    // successor of one in the same heap.
    if ((uintptr_t)heap.start != w->pool.start ||
        (uintptr_t)heap.end   != w->pool.end) {
        w->pool.start = (uintptr_t)heap.start;
        w->pool.end   = (uintptr_t)heap.end;
        w->have_prev  = false;
    }
    w->blocks++;

    hg_verdict_t v = hg_classify(w->pool, (uintptr_t)blk.ptr, blk.size);
    if (v == HG_OK) {
        w->prev_ptr  = (uintptr_t)blk.ptr;
        w->prev_size = blk.size;
        w->prev_used = blk.used;
        w->have_prev = true;
        return true;                 // keep walking
    }

    w->verdict  = v;
    w->bad_ptr  = (uintptr_t)blk.ptr;
    w->bad_size = blk.size;

    // Snapshot the evidence here, while the heap lock is still held: the moment
    // this returns the lock drops and another task can allocate over the very
    // bytes that identify the writer. Only scalar stores and one memcpy — no
    // formatting, no logging, no allocation, because this runs inside a portMUX
    // critical section with interrupts disabled.
    hg_capture_t *c = w->cap;
    if (c) {
        c->heap_start = w->pool.start;
        c->heap_end   = w->pool.end;
        c->bad_ptr    = (uintptr_t)blk.ptr;
        c->bad_size   = (uint32_t)blk.size;
        c->bad_used   = blk.used ? 1u : 0u;
        c->blocks     = w->blocks;
        c->verdict    = (uint32_t)v;
        c->have_prev  = w->have_prev ? 1u : 0u;
        c->prev_ptr   = w->have_prev ? w->prev_ptr : 0;
        c->prev_size  = w->have_prev ? (uint32_t)w->prev_size : 0;
        c->prev_used  = (w->have_prev && w->prev_used) ? 1u : 0u;

        // Anchor on the smashed block, whose header the preceding allocation
        // ran into. If that pointer is itself wild there is nothing safe to
        // read there, so fall back to the last block we trusted.
        uintptr_t centre = (v == HG_BAD_PTR && w->have_prev)
                         ? w->prev_ptr : (uintptr_t)blk.ptr;
        uintptr_t from = 0;
        size_t    len  = 0;
        hg_window(w->pool, centre, HG_WIN_BEFORE, HG_WIN_AFTER, &from, &len);
        if (len > sizeof(c->win)) len = sizeof(c->win);
        c->win_from = from;
        c->win_len  = (uint32_t)len;
        if (len) memcpy(c->win, (const void *)from, len);
    }

    // Stops tlsf_walk_pool before it steps onto the bad block — this is what
    // turns the fault that killed 1.5.0 and 1.5.1 into a clean detection.
    return false;
}

static void hg_summarise(hg_capture_t *c)
{
    strbuf_t sb = sb_init(c->summary, sizeof c->summary);
    sb_appendf(&sb, "heap corruption: %s; ",
               hg_verdict_str((hg_verdict_t)c->verdict));
    sb_appendf(&sb, "bad blk=0x%08x size=%u %s; ",
               (unsigned)c->bad_ptr, (unsigned)c->bad_size,
               c->bad_used ? "used" : "free");
    if (c->have_prev)
        sb_appendf(&sb, "prev blk=0x%08x size=%u %s; ",
                   (unsigned)c->prev_ptr, (unsigned)c->prev_size,
                   c->prev_used ? "used" : "free");
    else
        sb_appendf(&sb, "prev none; ");
    sb_appendf(&sb, "heap=0x%08x..0x%08x blocks=%u; win=0x%08x+%u; ",
               (unsigned)c->heap_start, (unsigned)c->heap_end,
               (unsigned)c->blocks, (unsigned)c->win_from,
               (unsigned)c->win_len);
    int64_t now = esp_timer_get_time();
    sb_appendf(&sb, "up=%llds; recent:", (long long)(now / 1000000));

    // Newest first, with each entry's age — this is the attribution: it names
    // what the dongle was doing in the seconds before the heap went bad.
    for (uint32_t k = 1; k <= HG_NOTES; k++) {
        uint32_t i = (s_note_next - k) % HG_NOTES;
        const char *what = s_notes[i].what;
        if (!what) continue;
        sb_appendf(&sb, " %s@-%llums", what,
                   (unsigned long long)((now - s_notes[i].at_us) / 1000));
    }
}

// noinline so the capture cannot be optimised out of the caller's frame: its
// address escapes into a function the compiler cannot see through, which is
// what guarantees the evidence really is on the stack the core dump preserves.
static void __attribute__((noinline, noreturn))
hg_panic_with_evidence(hg_capture_t *c)
{
    ESP_LOGE(TAG, "%s", c->summary);
    esp_system_abort(c->summary);
}

static void hg_check(void)
{
    if (!s_armed) return;

    // An answered call is streaming RTP. The heap lock disables interrupts for
    // the length of the walk, so defer rather than walk mid-frame — and defer
    // rather than skip, because call handling is one of the heap-heavy paths
    // most worth covering. The walk then runs on the first tick after streaming
    // stops, tagged so the capture says so.
    if (rtp_streaming_active()) {
        s_deferred = true;
        return;
    }

    if (s_deferred) {
        s_deferred = false;
        heap_guard_note("post-call");
    }

    hg_capture_t cap;
    memset(&cap, 0, sizeof cap);
    memcpy(cap.magic, HG_MAGIC, sizeof HG_MAGIC);

    hg_walk_t w = { .cap = &cap };
    heap_caps_walk_all(hg_walker, &w);
    if (w.verdict == HG_OK) return;

    hg_summarise(&cap);

    // A dump from an earlier panic is still waiting to be uploaded. Panicking
    // again would overwrite it (CONFIG_ESP_COREDUMP_FLASH_NO_OVERWRITE is off)
    // and trade the first, best evidence for a duplicate of it.
    if (esp_core_dump_image_check() == ESP_OK) {
        ESP_LOGE(TAG, "%s -- staying up: an earlier dump is still pending upload",
                 cap.summary);
        s_armed = false;
        return;
    }

    // Bound a self-inflicted reboot loop. The daily firmware-update check never
    // runs on a device that reboots more often than once a day, so a dongle that
    // keeps re-corrupting itself has to be left up eventually or it can never be
    // fixed remotely. The dumps already captured carry the same evidence.
    if (s_rtc_panics >= CONFIG_HEAP_GUARD_MAX_PANICS) {
        ESP_LOGE(TAG, "%s -- staying up: already panicked %u times since power-on",
                 cap.summary, (unsigned)s_rtc_panics);
        s_armed = false;
        return;
    }

    s_rtc_panics++;
    hg_panic_with_evidence(&cap);
}

static void hg_timer_cb(void *arg)
{
    (void)arg;
    hg_check();
}

#if CONFIG_CRASH_TEST_ENABLE
// Rehearse the detection against a heap corrupted on purpose, so a dev build
// proves the sentinel actually fires — the boot self-test above only proves it
// stays quiet, and a detector never observed detecting is not worth shipping.
//
// The corruption is the real shape of the bug: write past the end of one
// allocation into the header of the one that follows. No knowledge of the
// allocator's internals is used, which is the whole point — that is precisely
// what the guard has to catch without it.
//
// The overwritten bytes are saved and put back, so the heap ends as it started,
// and the smashed span is bounded to lie strictly inside the two allocations we
// own. Between smash and restore the heap really is corrupt: another task
// allocating in that window could fault. The window is microseconds during
// early boot, and this is compiled in only under CONFIG_CRASH_TEST_ENABLE,
// never in production.
static void hg_rehearse(void)
{
    const size_t n = 64;
    uint8_t *a = malloc(n);
    uint8_t *b = malloc(n);
    if (!a || !b) {
        free(a); free(b);
        ESP_LOGW(TAG, "rehearsal skipped: allocation failed");
        return;
    }

    // Only proceed if the two allocations really are neighbours, so the span we
    // scribble over (the gap holding b's header, plus the first bytes of b's
    // payload) is memory we own. Otherwise there is no safe way to stage an
    // overflow without guessing at the allocator's layout.
    if (b <= a || (size_t)(b - a) > n + 32) {
        free(b); free(a);
        ESP_LOGW(TAG, "rehearsal skipped: allocations are not adjacent");
        return;
    }
    size_t span = (size_t)(b - a) - n + 4;

    uint8_t saved[36];
    if (span > sizeof saved) span = sizeof saved;

    // Launder the address through a volatile so the compiler stops tying it to
    // the 64-byte object: writing past `a` is undefined behaviour by the
    // language and is precisely the bug being staged, so GCC is right to reject
    // the plain form (-Werror=maybe-uninitialized) and this says "yes, meant
    // it" without weakening the flag for the whole file.
    volatile uintptr_t tail_addr = (uintptr_t)a + n;
    uint8_t *tail = (uint8_t *)tail_addr;

    memcpy(saved, tail, span);
    memset(tail, 'X', span);             // the overflow

    hg_walk_t w = { 0 };
    heap_caps_walk_all(hg_walker, &w);
    hg_verdict_t detected = w.verdict;

    memcpy(tail, saved, span);           // undo before anything else notices

    hg_walk_t after = { 0 };
    heap_caps_walk_all(hg_walker, &after);

    free(b);
    free(a);

    if (detected != HG_OK && after.verdict == HG_OK)
        ESP_LOGI(TAG, "rehearsal passed: %zu-byte overflow detected (%s), "
                      "heap clean again afterwards",
                 span, hg_verdict_str(detected));
    else
        ESP_LOGE(TAG, "rehearsal FAILED: detected=%s, after restore=%s -- the "
                      "sentinel would not catch a real overflow",
                 hg_verdict_str(detected), hg_verdict_str(after.verdict));
}
#endif

void heap_guard_start(void)
{
    if (s_rtc_magic != HG_RTC_MAGIC) {      // cold boot: RTC RAM is undefined
        s_rtc_magic  = HG_RTC_MAGIC;
        s_rtc_panics = 0;
    }

    // Prove the predicate against a heap known to be intact before arming
    // anything. If a healthy heap trips it, the assumption is wrong rather than
    // the heap — an IDF upgrade changing the walker's contract, say — and arming
    // would reboot healthy dongles every few seconds.
    int64_t  t0 = esp_timer_get_time();
    hg_walk_t w = { 0 };
    heap_caps_walk_all(hg_walker, &w);
    int64_t us = esp_timer_get_time() - t0;

    if (w.blocks == 0) {
        ESP_LOGE(TAG, "not arming: the heap walker reported no blocks at all");
        return;
    }
    if (w.verdict != HG_OK) {
        ESP_LOGE(TAG, "not arming: an intact heap fails the check (%s) at "
                      "blk=0x%08x size=%u after %u blocks -- the predicate is "
                      "wrong, not the heap",
                 hg_verdict_str(w.verdict), (unsigned)w.bad_ptr,
                 (unsigned)w.bad_size, (unsigned)w.blocks);
        return;
    }
    if (us > HG_WALK_BUDGET_US)
        ESP_LOGW(TAG, "boot walk took %lld us for %u blocks -- that is time with "
                      "interrupts disabled; consider a longer interval",
                 (long long)us, (unsigned)w.blocks);

#if CONFIG_CRASH_TEST_ENABLE
    hg_rehearse();          // dev builds only; proves the detection really fires
#endif

    const esp_timer_create_args_t args = {
        .callback        = hg_timer_cb,
        .name            = "heap_guard",
        .dispatch_method = ESP_TIMER_TASK,
    };
    esp_err_t err = esp_timer_create(&args, &s_timer);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "not arming: timer create: %s", esp_err_to_name(err));
        return;
    }
    err = esp_timer_start_periodic(
        s_timer, (uint64_t)CONFIG_HEAP_GUARD_INTERVAL_S * 1000000);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "not arming: timer start: %s", esp_err_to_name(err));
        return;
    }

    s_armed = true;
    ESP_LOGI(TAG, "armed: %u blocks in %lld us, checking every %d s "
                  "(%u self-triggered panics since power-on)",
             (unsigned)w.blocks, (long long)us,
             CONFIG_HEAP_GUARD_INTERVAL_S, (unsigned)s_rtc_panics);
}

#else  /* !CONFIG_HEAP_GUARD_ENABLE */

void heap_guard_start(void)
{
    ESP_LOGI("heap_guard", "disabled at build time");
}

void heap_guard_note(const char *what)
{
    (void)what;
}

#endif
