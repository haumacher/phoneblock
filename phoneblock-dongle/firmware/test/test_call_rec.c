// Host test of the call recordings (#663): the mix of both directions with
// the caller's jitter buffer (call_rec_mix.c), and the recorder (call_rec.c)
// with its RAM ring, writer, budget and binding to the persistent call list
// (stats.c) — on the real list in a temp directory, the writer driven by
// hand (call_rec_set_manual_writer()) so overflow and timing are exact.
//
// Phone numbers are synthetic (030 1234 56xx).

#define _POSIX_C_SOURCE 200809L

#include <dirent.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include "audio.h"
#include "call_rec.h"
#include "call_rec_mix.h"
#include "calls_test_util.h"

static int tests_run, tests_failed;

#define CHECK(cond, ...) do {                                   \
    tests_run++;                                                \
    if (cond) { printf("  ok: "); printf(__VA_ARGS__); printf("\n"); } \
    else { tests_failed++; printf("  FAIL (%s:%d): ", __FILE__, __LINE__); \
           printf(__VA_ARGS__); printf("\n"); }                 \
} while (0)

// --- The mix ---------------------------------------------------------------------

static void test_mix(void)
{
    printf("mix\n");
    uint8_t tx[CALL_REC_FRAME], out[CALL_REC_FRAME];
    int16_t rx[CALL_REC_FRAME];

    // A known frame plus known caller samples: summed in PCM.
    for (int i = 0; i < CALL_REC_FRAME; i++) {
        tx[i] = pcm_to_alaw((int16_t)(1000 + 10 * i));
        rx[i] = (int16_t)(2000 - 20 * i);
    }
    call_rec_mix(out, tx, rx);
    bool ok = true;
    for (int i = 0; i < CALL_REC_FRAME; i++) {
        if (out[i] != pcm_to_alaw((int16_t)(alaw_to_pcm(tx[i]) + rx[i]))) ok = false;
    }
    CHECK(ok, "frame + caller = A-law of the PCM sum");

    // No caller audio: every A-law code passes unchanged.
    memset(rx, 0, sizeof(rx));
    ok = true;
    for (int base = 0; base < 256; base += CALL_REC_FRAME) {
        for (int i = 0; i < CALL_REC_FRAME; i++) tx[i] = (uint8_t)(base + i);
        call_rec_mix(out, tx, rx);
        for (int i = 0; i < CALL_REC_FRAME; i++) if (out[i] != tx[i]) ok = false;
    }
    CHECK(ok, "silent caller: the sent bytes unchanged (all 256 codes)");

    // Saturation at both PCM limits instead of wrapping.
    for (int i = 0; i < CALL_REC_FRAME; i++) {
        bool neg = i & 1;
        tx[i] = pcm_to_alaw(neg ? -30000 : 30000);
        rx[i] = neg ? -30000 : 30000;
    }
    call_rec_mix(out, tx, rx);
    ok = true;
    for (int i = 0; i < CALL_REC_FRAME; i++) {
        if (out[i] != ((i & 1) ? 0x2A : 0xAA)) ok = false;
    }
    CHECK(ok, "sum beyond +/-32767 saturates to A-law full scale (0xAA / 0x2A)");
}

// --- The jitter buffer -------------------------------------------------------------

// The caller's sample i (never A-law silence, so a decoded 0 means "nothing").
static uint8_t caller_code(uint32_t i) { return (uint8_t)(i * 7u + 3u); }

static uint32_t s_rng = 12345;
static uint32_t rnd(uint32_t n)
{
    s_rng ^= s_rng << 13; s_rng ^= s_rng >> 17; s_rng ^= s_rng << 5;
    return s_rng % n;
}

// A long call (30 min) of bursty 30 ms packets: each delayed by 0..35 ms (so
// up to two arrive in one 20 ms tick, then none), one packet lost. The
// caller's samples come out in order, at one constant offset from the
// sender's clock for the whole call (no drift), with silence exactly where
// the lost packet was and nothing dropped.
static void test_jitter_long_call(void)
{
    printf("jitter buffer: 30 min of bursty 30 ms packets\n");
    static call_rec_rx_t rx;
    call_rec_rx_init(&rx);
    const uint32_t ts0 = 0xFFFFF000u;          // the timestamp wraps early on
    const uint32_t PKT = 240, PKT_MS = 30, TICKS = 30u * 60u * 50u;
    const uint32_t LOST = 1000;                // packet index that never arrives
    uint32_t next_pkt = 0;
    uint32_t delay[4096];                       // per packet, a ring
    for (uint32_t i = 0; i < 4096; i++) delay[i] = rnd(36);
    uint8_t payload[240];
    int16_t out[CALL_REC_FRAME];
    int64_t offset = -1;                        // out index - sender index
    uint64_t o = 0, mismatches = 0, silent_in_call = 0, lost_silent = 0;
    for (uint32_t k = 1; k <= TICKS; k++) {
        uint32_t now_ms = 20 * k;
        // Every packet sent and arrived by now, in arrival order (equal
        // delays keep sending order; a later packet can overtake).
        for (;;) {
            uint32_t j = next_pkt;
            if (j * PKT_MS + delay[j % 4096] > now_ms) break;
            if (j != LOST) {
                for (uint32_t i = 0; i < PKT; i++) payload[i] = caller_code(j * PKT + i);
                call_rec_rx_push(&rx, ts0 + j * PKT, payload, PKT);
            }
            next_pkt++;
        }
        call_rec_rx_pop(&rx, out);
        for (int i = 0; i < CALL_REC_FRAME; i++, o++) {
            if (offset < 0) {
                if (out[i] == 0) continue;
                offset = (int64_t)o;            // sender sample 0 shows up here
            }
            int64_t sidx = (int64_t)o - offset;
            uint32_t pkt = (uint32_t)(sidx / PKT);
            bool sent = (uint64_t)sidx < (uint64_t)next_pkt * PKT;
            if (pkt == LOST && sent) {
                if (out[i] == 0) lost_silent++;
                else mismatches++;
            } else if (sent && pkt + 2 < next_pkt) {
                if (out[i] == 0) silent_in_call++;
                else if (out[i] != alaw_to_pcm(caller_code((uint32_t)sidx))) mismatches++;
            }
        }
    }
    CHECK(offset >= 0 && mismatches == 0 && silent_in_call == 0,
          "every sample in order at one offset (%lld samples) over %u ticks: "
          "%llu wrong, %llu gaps", (long long)offset, (unsigned)TICKS,
          (unsigned long long)mismatches, (unsigned long long)silent_in_call);
    CHECK(lost_silent == PKT, "the lost packet is %llu samples of silence, in place",
          (unsigned long long)lost_silent);
    CHECK(rx.dropped == 0, "nothing dropped (%u)", (unsigned)rx.dropped);
    CHECK(offset < (int64_t)(CALL_REC_RX_DELAY + 36 * 8 + CALL_REC_FRAME),
          "latency stays within the delay plus the jitter (%lld samples)",
          (long long)offset);
}

// A 300 ms network stall, then the backlog at once: the caller's samples
// stay in order (never repeated, never reversed), and once it has settled
// the stream is continuous again with nothing more dropped.
static void test_jitter_stall(void)
{
    printf("jitter buffer: a 300 ms stall\n");
    static call_rec_rx_t rx;
    call_rec_rx_init(&rx);
    uint8_t payload[160];
    int16_t out[CALL_REC_FRAME];
    int64_t last = -1;
    bool ordered = true;
    uint32_t dropped_settled = 0;
    uint64_t gaps_after = 0;
    const uint32_t TICKS = 600;
    uint32_t pending_from = 0;   // first 20 ms packet not delivered yet
    for (uint32_t k = 0; k < TICKS; k++) {
        // Packets of 20 ms; ticks 100..114 deliver nothing, tick 115 all.
        bool stalled = k >= 100 && k < 115;
        if (!stalled) {
            for (uint32_t j = pending_from; j <= k; j++) {
                for (uint32_t i = 0; i < 160; i++) payload[i] = caller_code(j * 160 + i);
                call_rec_rx_push(&rx, 5000 + j * 160, payload, 160);
            }
            pending_from = k + 1;
        }
        call_rec_rx_pop(&rx, out);
        if (k == 200) dropped_settled = rx.dropped;
        for (int i = 0; i < CALL_REC_FRAME; i++) {
            if (out[i] == 0) { if (k > 200) gaps_after++; continue; }
            // Which sender sample is it? Codes repeat every 256 samples, so
            // search forward from the last one.
            int64_t s = last + 1;
            while (s < last + 1 + 4096 && alaw_to_pcm(caller_code((uint32_t)s)) != out[i]) s++;
            if (s >= last + 1 + 4096) { ordered = false; break; }
            last = s;
        }
    }
    CHECK(ordered, "samples strictly in sending order across the stall");
    CHECK(gaps_after == 0 && rx.dropped == dropped_settled,
          "settled afterwards: no gap, nothing more dropped (%u dropped in the burst)",
          (unsigned)rx.dropped);
}

// --- The recorder ------------------------------------------------------------------

static char s_rec_dir[400];

static long file_size(const char *path)
{
    struct stat st;
    return stat(path, &st) == 0 ? (long)st.st_size : -1;
}

static long rec_file(uint32_t seq, const char *ext)
{
    char path[512];
    snprintf(path, sizeof(path), "%s/%u.%s", s_rec_dir, (unsigned)seq, ext);
    return file_size(path);
}

static uint32_t new_spam_call(int i)
{
    char number[24];
    snprintf(number, sizeof(number), "03012345%02d", i % 100);
    stats_record_call(number, "", VERDICT_SPAM);
    return stats_last_call_seq();
}

static void tx_frame(uint8_t f[CALL_REC_FRAME], uint32_t n)
{
    for (int i = 0; i < CALL_REC_FRAME; i++) f[i] = (uint8_t)(n * 31u + (uint32_t)i);
}

// Records `frames` ticks of call `seq`, the writer stepping every `every`
// ticks (0: never during the call); then ends it and lets the writer finish.
static void record(uint32_t seq, uint32_t frames, uint32_t every)
{
    call_rec_t *r = call_rec_begin(seq);
    uint8_t f[CALL_REC_FRAME];
    for (uint32_t n = 0; n < frames; n++) {
        tx_frame(f, n);
        call_rec_tick(r, f);
        if (every && n % every == every - 1) call_rec_writer_step();
    }
    call_rec_end(r);
    for (int i = 0; i < 1000 && call_rec_writer_step(); i++) {}
}

static int dir_entries(void)
{
    DIR *d = opendir(s_rec_dir);
    if (!d) return -1;
    int n = 0;
    struct dirent *e;
    while ((e = readdir(d)) != NULL) {
        if (strcmp(e->d_name, ".") && strcmp(e->d_name, "..")) n++;
    }
    closedir(d);
    return n;
}

static void test_writer(void)
{
    printf("writer and ring\n");
    // A normal call: the file holds every frame, exactly as sent (no caller).
    uint32_t a = new_spam_call(1);
    s_log_warns = 0;
    record(a, 250, 10);
    bool same = rec_file(a, "al") == 250L * CALL_REC_FRAME && call_rec_seconds(a) == 5;
    size_t size = 0;
    int fd = call_rec_open(a, &size);
    uint8_t got[CALL_REC_FRAME], want[CALL_REC_FRAME];
    for (uint32_t n = 0; same && fd >= 0 && n < 250; n++) {
        tx_frame(want, n);
        if (read(fd, got, sizeof(got)) != (ssize_t)sizeof(got)
                || memcmp(got, want, sizeof(got)) != 0) same = false;
    }
    if (fd >= 0) close(fd);
    CHECK(same && fd >= 0 && size == 250u * CALL_REC_FRAME && s_log_warns == 0
          && rec_file(a, "tmp") < 0,
          "250 frames -> a 40000 B file of exactly those frames, 5 s, no temp file left");

    // The writer never runs during the call (a stalled flash): the ring fills
    // after CALL_REC_RING_FRAMES frames, the recording ends there with one
    // WARN, the frames before it are kept; pushing more does nothing.
    uint32_t b = new_spam_call(2);
    s_log_warns = 0;
    record(b, CALL_REC_RING_FRAMES + 150, 0);
    CHECK(s_log_warns == 1 && rec_file(b, "al") == (long)CALL_REC_RING_FRAMES * CALL_REC_FRAME,
          "overflow: exactly one WARN, the recording ends after the %u queued frames "
          "(%ld B)", CALL_REC_RING_FRAMES, rec_file(b, "al"));

    // Shorter than 1 s: discarded, nothing left in the directory.
    uint32_t c = new_spam_call(3);
    int before = dir_entries();
    s_log_warns = 0;
    record(c, CALL_REC_MIN_FRAMES - 1, 5);
    CHECK(rec_file(c, "al") < 0 && rec_file(c, "tmp") < 0 && call_rec_seconds(c) == 0
          && dir_entries() == before && s_log_warns == 0,
          "%u frames (< 1 s): discarded, no file", CALL_REC_MIN_FRAMES - 1);
    record(c, CALL_REC_MIN_FRAMES, 5);
    CHECK(rec_file(c, "al") == (long)CALL_REC_MIN_FRAMES * CALL_REC_FRAME,
          "%u frames (1 s): kept", CALL_REC_MIN_FRAMES);

    // One recording at a time: a second call while the first is still being
    // written is not recorded.
    uint32_t d = new_spam_call(4);
    uint32_t e = new_spam_call(5);
    call_rec_t *rd = call_rec_begin(d);
    call_rec_t *re = call_rec_begin(e);
    CHECK(rd && !re, "a second recording while one is in progress: refused");
    call_rec_end(rd);
    while (call_rec_writer_step()) {}

    // A call that is not listed is not recorded.
    CHECK(call_rec_begin(e + 100) == NULL, "unlisted call: not recorded");
}

static void test_budget(void)
{
    printf("budget\n");
    call_rec_delete_all();
    // Room for three recordings of 80 frames (12800 B: 4 blocks each).
    uint64_t one = call_rec_footprint(80u * CALL_REC_FRAME);
    call_rec_set_limits(3 * one, 0);
    uint32_t s[5];
    for (int i = 0; i < 4; i++) {
        s[i] = new_spam_call(10 + i);
        record(s[i], 80, 7);
    }
    call_rec_info_t info;
    call_rec_get_info(&info);
    CHECK(info.count == 3 && call_rec_seconds(s[0]) == 0 && rec_file(s[0], "al") < 0
          && call_rec_seconds(s[1]) > 0 && call_rec_seconds(s[3]) > 0
          && info.used_bytes <= 3 * one,
          "a 4th recording beyond the cap evicts the oldest (%d stored, %llu of %llu B)",
          info.count, (unsigned long long)info.used_bytes, (unsigned long long)(3 * one));

    // A long one makes room for itself oldest first, while it is written.
    s[4] = new_spam_call(14);
    record(s[4], 160, 7);
    call_rec_get_info(&info);
    CHECK(rec_file(s[4], "al") == 160L * CALL_REC_FRAME && call_rec_seconds(s[1]) == 0
          && call_rec_seconds(s[2]) == 0 && call_rec_seconds(s[3]) > 0
          && info.used_bytes <= 3 * one,
          "a recording twice the size evicts the two oldest, keeps the newest");

    // The per-call maximum stops the recording at the limit (INFO, no WARN).
    call_rec_set_limits(0, 60u * CALL_REC_FRAME);
    uint32_t m = new_spam_call(15);
    s_log_warns = 0;
    record(m, 200, 5);
    CHECK(rec_file(m, "al") == 60L * CALL_REC_FRAME && s_log_warns == 0,
          "per-call maximum: stops at 60 frames, no WARN");
    call_rec_set_limits(0, 0);
}

static void put_file(const char *name, size_t size)
{
    char path[512];
    snprintf(path, sizeof(path), "%s/%s", s_rec_dir, name);
    FILE *f = fopen(path, "wb");
    for (size_t i = 0; f && i < size; i++) fputc(0xD5, f);
    if (f) fclose(f);
}

static void test_linkage(void)
{
    printf("bound to the call list\n");
    call_rec_delete_all();
    uint32_t a = new_spam_call(20);
    record(a, 60, 5);
    uint32_t b = new_spam_call(21);
    record(b, 60, 5);
    CHECK(call_rec_seconds(a) && call_rec_seconds(b), "two recordings stored");

    // A restart: the list and the recordings come back, still attached.
    CHECK(calls_test_reboot() && call_rec_init(s_rec_dir)
          && call_rec_seconds(a) > 0 && call_rec_seconds(b) > 0,
          "after a restart both recordings are attached to their calls");

    // Strays at start: a temp file of a crashed recording, a recording of a
    // call no longer listed, an empty one, something else entirely.
    put_file("777.tmp", 8000);
    put_file("4000000.al", 8000);
    char name[32];
    snprintf(name, sizeof(name), "%u.al", (unsigned)(b + 1000));   // not listed
    put_file(name, 8000);
    put_file("junk.txt", 10);
    uint32_t c = new_spam_call(22);
    snprintf(name, sizeof(name), "%u.al", (unsigned)c);
    put_file(name, 0);                                          // empty
    CHECK(call_rec_init(s_rec_dir) && dir_entries() == 2
          && call_rec_seconds(a) > 0 && call_rec_seconds(b) > 0 && call_rec_seconds(c) == 0,
          "start-up removes the strays, keeps the two recordings (%d files)", dir_entries());

    // Ring wrap: once STATS_CALL_CAPACITY newer calls are listed, the calls
    // and their recordings are gone.
    for (int i = 0; i < STATS_CALL_CAPACITY; i++) {
        char number[24];
        snprintf(number, sizeof(number), "030123%04d", i);
        stats_record_call(number, "", VERDICT_LEGITIMATE);
    }
    CHECK(call_rec_seconds(a) == 0 && call_rec_seconds(b) == 0 && dir_entries() == 0,
          "ring wrap past %d calls deletes the recordings of the dropped calls",
          STATS_CALL_CAPACITY);

    // Clearing the list deletes every recording.
    uint32_t d = new_spam_call(23);
    record(d, 60, 5);
    uint32_t e = new_spam_call(24);
    record(e, 60, 5);
    CHECK(dir_entries() == 2, "two new recordings");
    stats_clear_calls();
    CHECK(dir_entries() == 0 && call_rec_seconds(d) == 0 && call_rec_seconds(e) == 0,
          "clearing the list deletes all recordings");

    // A recording whose call is cleared while it is being written is not kept.
    uint32_t f = new_spam_call(25);
    call_rec_t *r = call_rec_begin(f);
    uint8_t fr[CALL_REC_FRAME];
    for (int n = 0; n < 80; n++) { tx_frame(fr, (uint32_t)n); call_rec_tick(r, fr); }
    call_rec_writer_step();
    stats_clear_calls();
    call_rec_end(r);
    while (call_rec_writer_step()) {}
    CHECK(dir_entries() == 0, "cleared during the call: the recording is dropped");

    // Deleting one recording keeps the call.
    uint32_t g = new_spam_call(26);
    record(g, 60, 5);
    stats_call_t call;
    CHECK(call_rec_delete(g) && !call_rec_delete(g) && call_rec_seconds(g) == 0
          && stats_get_call(g, &call) && dir_entries() == 0,
          "delete: the recording goes, the call stays; a second delete finds none");
}

int main(void)
{
    test_mix();
    test_jitter_long_call();
    test_jitter_stall();

    calls_test_open();
    snprintf(s_rec_dir, sizeof(s_rec_dir), "%s/rec", s_calls_dir);
    call_rec_set_manual_writer(true);
    CHECK(call_rec_init(s_rec_dir), "recordings set up in %s", s_rec_dir);
    test_writer();
    test_budget();
    test_linkage();

    call_rec_delete_all();
    rmdir(s_rec_dir);
    calls_test_cleanup();
    printf("\n%d checks, %d failed\n", tests_run, tests_failed);
    return tests_failed ? 1 : 0;
}
