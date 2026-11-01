#ifndef ESP_PLATFORM
#define _POSIX_C_SOURCE 200809L   // fsync, fileno
#endif

#include "stats.h"

#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

#include "display.h"
#include "platform.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static const char *TAG = "stats";

// All mutable state lives here and is guarded by s_mutex. Callers
// either record an event (briefly lock, mutate, unlock) or take a
// snapshot (briefly lock, copy, unlock). Snapshots mean the web
// layer never holds the mutex while talking to its HTTP client.
//
// Nothing logs while holding the lock: on the device a WARN goes through
// the log hook into stats_record_error(), which takes it again.

static pb_mutex_t *s_mutex;

static stats_counters_t s_counters;

// --- The call file (issue #589) ---------------------------------------
//
// A header, then STATS_CALL_CAPACITY fixed-size records. The call with
// sequence number `seq` lives in slot seq % capacity, so the listed calls
// are the contiguous run of sequence numbers ending at the newest, and a
// call is found by its number with one seek. The header holds only what
// changes rarely (the floor after a clear, the mail's high-water mark):
// recording a call writes its record and nothing else — on LittleFS a write
// to block 0 would rewrite the whole file (its blocks form a backwards
// skip list), so a header update per call would triple the flash writes.
// Head, count and next sequence number are derived from the records when
// the file is opened.
//
// The file has its full size from the start (empty slots are zero), so its
// footprint on the shared partition is constant (storage_budget.h) and a
// truncated file is recognised.

#define CALLS_MAGIC   0x4C434250u   // "PBCL" little-endian
#define CALLS_VERSION 1u

typedef struct {
    uint32_t magic;
    uint16_t version;
    uint16_t record_size;
    uint16_t capacity;
    uint16_t reserved0;
    uint32_t floor_seq;     // no call below this one is listed (create / clear)
    uint32_t mailed_seq;    // stats_set_mailed_seq()
    uint8_t  reserved[12];
} calls_header_t;

typedef struct {
    uint32_t seq;           // 0: empty slot
    uint32_t uptime_s;      // pb_monotonic_us() / 1e6 at the call
    int64_t  epoch_s;       // UTC seconds, 0 = not known
    int32_t  direct_votes;
    int32_t  range_votes;
    uint16_t duration_s;
    uint8_t  verdict;
    uint8_t  assessment;
    uint8_t  wildcard;
    uint8_t  reserved[3];
    char     number[STATS_NUMBER_LEN];
    char     display[STATS_DISPLAY_LEN];
    char     label[STATS_LABEL_LEN];
    char     location[STATS_LOCATION_LEN];
} call_record_t;

_Static_assert(sizeof(calls_header_t) == STATS_CALL_HEADER_BYTES, "header layout");
_Static_assert(sizeof(call_record_t) == STATS_CALL_RECORD_BYTES, "record layout");

static char     s_calls_path[PB_DATA_PATH_CAP];
static bool     s_calls_open;
static uint32_t s_floor_seq = 1;    // header.floor_seq
static uint32_t s_mailed_seq;       // header.mailed_seq
static uint32_t s_next_seq = 1;     // next number handed out
static uint32_t s_last_seq;         // newest listed call, 0: none since the file exists
static uint32_t s_boot_seq = 1;     // first number of this boot
// A call of this boot was recorded without a time (clock not set).
static bool     s_backfill_pending;
static int64_t (*s_clock)(void);
static stats_call_listener_t s_call_listener;

static stats_error_t s_errors[STATS_MAX_ERRORS];
static int           s_errors_head;
static int           s_errors_count;

static void lock(void)   { pb_mutex_lock(s_mutex); }
static void unlock(void) { pb_mutex_unlock(s_mutex); }

void stats_setup(void)
{
    s_mutex = pb_mutex_create();
    memset(&s_counters, 0, sizeof(s_counters));
    memset(s_errors, 0, sizeof(s_errors));
    s_errors_head = s_errors_count = 0;
}

static int64_t now_epoch(void)
{
    return s_clock ? s_clock() : 0;
}

static uint32_t now_uptime_s(void)
{
    return (uint32_t)(pb_monotonic_us() / 1000000);
}

static long slot_offset(uint32_t seq)
{
    return (long)STATS_CALL_HEADER_BYTES
         + (long)(seq % STATS_CALL_CAPACITY) * (long)STATS_CALL_RECORD_BYTES;
}

// Seq of the oldest listed call; with s_last_seq the listed run. 0 if none.
static uint32_t oldest_seq(void)
{
    if (s_last_seq == 0 || s_last_seq < s_floor_seq) return 0;
    uint32_t span = s_last_seq - s_floor_seq + 1;
    return span > STATS_CALL_CAPACITY
        ? s_last_seq - STATS_CALL_CAPACITY + 1 : s_floor_seq;
}

static FILE *calls_fopen(const char *mode)
{
    FILE *f = fopen(s_calls_path, mode);
    // Unbuffered: every access is one record (240 B) or one header, so a
    // stdio buffer would only cost a malloc and a copy.
    if (f) setvbuf(f, NULL, _IONBF, 0);
    return f;
}

static bool read_record(FILE *f, uint32_t seq, call_record_t *rec)
{
    return fseek(f, slot_offset(seq), SEEK_SET) == 0
        && fread(rec, sizeof(*rec), 1, f) == 1
        && rec->seq == seq;
}

static bool write_at(FILE *f, long offset, const void *data, size_t n)
{
    return fseek(f, offset, SEEK_SET) == 0 && fwrite(data, n, 1, f) == 1;
}

// Flushes and closes a file written through calls_fopen(). LittleFS commits
// on close; on Linux fsync() takes it to the disk (a power cut).
static bool close_written(FILE *f)
{
    bool ok = fflush(f) == 0;
#ifndef ESP_PLATFORM
    if (ok) ok = fsync(fileno(f)) == 0;
#endif
    return fclose(f) == 0 && ok;
}

static void fill_header(calls_header_t *h)
{
    memset(h, 0, sizeof(*h));
    h->magic       = CALLS_MAGIC;
    h->version     = CALLS_VERSION;
    h->record_size = STATS_CALL_RECORD_BYTES;
    h->capacity    = STATS_CALL_CAPACITY;
    h->floor_seq   = s_floor_seq;
    h->mailed_seq  = s_mailed_seq;
}

// Writes an empty list (the header and zeroed slots) at full size. Called
// under the lock, or before the list is in use.
static bool write_empty_file(void)
{
    FILE *f = calls_fopen("wb");
    if (!f) return false;
    calls_header_t h;
    fill_header(&h);
    bool ok = fwrite(&h, sizeof(h), 1, f) == 1;
    call_record_t zero;
    memset(&zero, 0, sizeof(zero));
    for (int i = 0; ok && i < STATS_CALL_CAPACITY; i++) {
        ok = fwrite(&zero, sizeof(zero), 1, f) == 1;
    }
    return close_written(f) && ok;
}

static bool write_header(void)
{
    FILE *f = calls_fopen("r+b");
    if (!f) return false;
    calls_header_t h;
    fill_header(&h);
    bool ok = write_at(f, 0, &h, sizeof(h));
    return close_written(f) && ok;
}

// Reads an existing file into the RAM state. NULL if it is usable,
// otherwise why not (for the one WARN).
static const char *load_file(FILE *f)
{
    calls_header_t h;
    if (fread(&h, sizeof(h), 1, f) != 1) return "truncated";
    if (h.magic != CALLS_MAGIC) return "not a call list";
    if (h.version != CALLS_VERSION) return "other format version";
    if (h.record_size != STATS_CALL_RECORD_BYTES) return "other record size";
    if (h.capacity != STATS_CALL_CAPACITY) return "other capacity";
    if (h.floor_seq == 0) return "bad header";
    if (fseek(f, 0, SEEK_END) != 0) return "unreadable";
    long size = ftell(f);
    if (size < (long)STATS_CALLS_FILE_BYTES(STATS_CALL_CAPACITY)) return "truncated";
    if (size > (long)STATS_CALLS_FILE_BYTES(STATS_CALL_CAPACITY)) return "too long";
    if (fseek(f, STATS_CALL_HEADER_BYTES, SEEK_SET) != 0) return "unreadable";

    // Streamed, one record at a time: the newest sequence number and a
    // consistency check (every record in its slot, the listed run complete).
    uint32_t newest = 0;
    int used = 0;
    call_record_t rec;
    for (int slot = 0; slot < STATS_CALL_CAPACITY; slot++) {
        if (fread(&rec, sizeof(rec), 1, f) != 1) return "truncated";
        if (rec.seq == 0) continue;
        if (rec.seq % STATS_CALL_CAPACITY != (uint32_t)slot
                || rec.seq < h.floor_seq) {
            return "inconsistent records";
        }
        used++;
        if (rec.seq > newest) newest = rec.seq;
    }
    uint32_t expect = 0;
    if (newest) {
        uint32_t span = newest - h.floor_seq + 1;
        expect = span > STATS_CALL_CAPACITY ? STATS_CALL_CAPACITY : span;
    }
    if ((uint32_t)used != expect) return "inconsistent records";
    uint32_t next = newest >= h.floor_seq ? newest + 1 : h.floor_seq;
    if (h.mailed_seq >= next) return "bad header";

    s_floor_seq  = h.floor_seq;
    s_mailed_seq = h.mailed_seq;
    s_last_seq   = newest;
    s_next_seq   = next;
    return NULL;
}

bool stats_calls_open(const char *path)
{
    const char *bad = NULL;
    bool created = false, ok = true;
    int err = 0;

    lock();
    s_calls_open = false;
    int len = snprintf(s_calls_path, sizeof(s_calls_path), "%s", path ? path : "");
    if (len <= 0 || (size_t)len >= sizeof(s_calls_path)) {
        unlock();
        pb_log_err(TAG, "call list path does not fit: %s", path ? path : "(null)");
        return false;
    }
    s_floor_seq = 1;
    s_mailed_seq = 0;
    s_last_seq = 0;
    s_next_seq = 1;

    FILE *f = calls_fopen("rb");
    if (f) {
        bad = load_file(f);
        fclose(f);
    }
    if (!f || bad) {
        // Missing (first start), or unusable: an empty list in its place.
        s_floor_seq = 1;
        s_mailed_seq = 0;
        s_last_seq = 0;
        s_next_seq = 1;
        ok = write_empty_file();
        if (!ok) err = errno;
        created = ok;
    }
    s_boot_seq = s_next_seq;
    s_backfill_pending = false;
    s_calls_open = ok;
    unlock();

    if (bad) {
        pb_log_warn(TAG, "call list %s discarded (%s), starting empty", path, bad);
    }
    if (!ok) {
        pb_log_err(TAG, "call list %s cannot be created: %s — calls are not listed",
                   path, strerror(err));
    } else if (created && !bad) {
        pb_log_info(TAG, "call list %s created (%d calls)", path, STATS_CALL_CAPACITY);
    }
    display_calls_changed();
    return ok;
}

void stats_calls_close(void)
{
    lock();
    s_calls_open = false;
    s_last_seq = 0;
    s_floor_seq = 1;
    s_next_seq = 1;
    unlock();
}

void stats_set_clock(int64_t (*clock)(void))
{
    s_clock = clock;
}

// Gives the calls of this boot recorded without a time their time, now that
// the clock is set. Under the lock; false if a write failed.
static bool backfill_locked(int64_t epoch)
{
    s_backfill_pending = false;
    uint32_t lo = oldest_seq();
    if (!s_calls_open || lo == 0) return true;
    if (lo < s_boot_seq) lo = s_boot_seq;
    if (lo > s_last_seq) return true;
    FILE *f = calls_fopen("r+b");
    if (!f) return false;
    uint32_t now_s = now_uptime_s();
    bool ok = true;
    call_record_t rec;
    for (uint32_t seq = lo; seq <= s_last_seq && ok; seq++) {
        if (!read_record(f, seq, &rec) || rec.epoch_s != 0) continue;
        rec.epoch_s = epoch - (int64_t)(now_s - rec.uptime_s);
        ok = write_at(f, slot_offset(seq), &rec, sizeof(rec));
    }
    return close_written(f) && ok;
}

// The lazy path of stats_time_synced(), on every write.
static bool maybe_backfill_locked(void)
{
    if (!s_backfill_pending) return true;
    int64_t epoch = now_epoch();
    return epoch == 0 || backfill_locked(epoch);
}

void stats_time_synced(void)
{
    int64_t epoch = now_epoch();
    if (epoch == 0) return;
    lock();
    bool ok = !s_backfill_pending || backfill_locked(epoch);
    unlock();
    if (!ok) pb_log_warn(TAG, "call list: storing the call times failed");
}

// --- Event hooks ----------------------------------------------------

static void copy_trim(char *dst, size_t cap, const char *src)
{
    if (!src) { dst[0] = '\0'; return; }
    size_t n = strnlen(src, cap - 1);
    memcpy(dst, src, n);
    dst[n] = '\0';
}

static void bump_counters(verdict_t verdict)
{
    s_counters.total_calls++;
    switch (verdict) {
        case VERDICT_SPAM:       s_counters.spam_blocked++; break;
        case VERDICT_LEGITIMATE: s_counters.legitimate++;   break;
        case VERDICT_ERROR:      s_counters.errors++;       break;
    }
}

void stats_record_call(const char *number, const char *display, verdict_t verdict)
{
    // Verdict-only entries are phone-book matches (legit) or internal
    // errors — neither carries a community signal. Map the action verdict
    // onto its log characterisation: a phone-book hit is "legitim", not
    // merely "unbekannt".
    stats_record_call_assessed(number, display, verdict,
                               (verdict == VERDICT_SPAM)  ? PB_ASSESS_SPAM
                             : (verdict == VERDICT_ERROR) ? PB_ASSESS_ERROR
                             :                              PB_ASSESS_LEGITIMATE);
}

static void fill_call(stats_call_t *c, const char *number, const char *display,
                      verdict_t verdict, pb_assessment_t assessment)
{
    memset(c, 0, sizeof(*c));
    c->at_us      = (int64_t)pb_monotonic_us();
    c->this_boot  = true;
    c->verdict    = verdict;
    c->assessment = assessment;
    copy_trim(c->number,  sizeof(c->number),  number);
    copy_trim(c->display, sizeof(c->display), display);
}

static void call_to_record(const stats_call_t *c, call_record_t *r)
{
    memset(r, 0, sizeof(*r));
    r->seq          = c->seq;
    r->uptime_s     = (uint32_t)(c->at_us / 1000000);
    r->epoch_s      = c->epoch_s;
    r->direct_votes = c->direct_votes;
    r->range_votes  = c->range_votes;
    r->duration_s   = c->duration_s;
    r->verdict      = (uint8_t)c->verdict;
    r->assessment   = (uint8_t)c->assessment;
    r->wildcard     = c->wildcard ? 1 : 0;
    copy_trim(r->number,   sizeof(r->number),   c->number);
    copy_trim(r->display,  sizeof(r->display),  c->display);
    copy_trim(r->label,    sizeof(r->label),    c->label);
    copy_trim(r->location, sizeof(r->location), c->location);
}

// Under the lock (s_boot_seq, the clock).
static void record_to_call(const call_record_t *r, stats_call_t *c)
{
    memset(c, 0, sizeof(*c));
    c->seq          = r->seq;
    c->this_boot    = r->seq >= s_boot_seq;
    c->at_us        = c->this_boot ? (int64_t)r->uptime_s * 1000000 : 0;
    c->epoch_s      = r->epoch_s;
    if (c->epoch_s == 0 && c->this_boot) {
        // Not back-filled yet: the time follows from the age.
        int64_t epoch = now_epoch();
        if (epoch) c->epoch_s = epoch - (int64_t)(now_uptime_s() - r->uptime_s);
    }
    c->direct_votes = r->direct_votes;
    c->range_votes  = r->range_votes;
    c->duration_s   = r->duration_s;
    c->verdict      = (verdict_t)r->verdict;
    c->assessment   = (pb_assessment_t)r->assessment;
    c->wildcard     = r->wildcard != 0;
    // The file is not trusted to be terminated.
    copy_trim(c->number,   sizeof(c->number),   r->number);
    copy_trim(c->display,  sizeof(c->display),  r->display);
    copy_trim(c->label,    sizeof(c->label),    r->label);
    copy_trim(c->location, sizeof(c->location), r->location);
}

// Every screened call reaches the display (T-Dongle-S3; DISPLAY_PRESENT is 0
// and the notifications are empty elsewhere, so this compiles away). Called
// outside the lock: display_calls_changed() recounts the badge through
// stats_count_calls().
static void announce_call(const stats_call_t *shown)
{
    display_call_started(shown);
    display_calls_changed();
}

// Tells the listener (the call webhook) about a newly listed call. Outside
// the lock, like the display: the listener only queues the number.
static void notify_listener(uint32_t seq)
{
    stats_call_listener_t listener = s_call_listener;
    if (listener) listener(seq);
}

void stats_set_call_listener(stats_call_listener_t listener)
{
    s_call_listener = listener;
}

// Lists `call` (seq and time are filled in here) and bumps the counters
// unless it is a test call. Returns its sequence number, 0 if it could not
// be listed. Logs (outside the lock) and notifies.
static uint32_t list_call(stats_call_t *call, bool count)
{
    bool write_failed = false;
    int err = 0;
    uint32_t seq = 0;
    lock();
    if (count) bump_counters(call->verdict);
    if (s_calls_open) {
        call->seq     = s_next_seq;
        call->epoch_s = now_epoch();
        call_record_t rec;
        call_to_record(call, &rec);
        FILE *f = calls_fopen("r+b");
        bool ok = f && write_at(f, slot_offset(rec.seq), &rec, sizeof(rec));
        if (f) ok = close_written(f) && ok;
        if (ok) {
            seq = s_next_seq++;
            if (s_next_seq == 0) s_next_seq = 1;
            s_last_seq = seq;
            if (call->epoch_s == 0) s_backfill_pending = true;
            else ok = maybe_backfill_locked();
        }
        write_failed = !ok;
        err = errno;
    }
    if (seq == 0) call->seq = 0;
    unlock();
    if (write_failed) pb_log_warn(TAG, "call list: writing a call failed: %s", strerror(err));
    if (DISPLAY_PRESENT) announce_call(call);
    if (seq && call->verdict != VERDICT_SPAM) notify_listener(seq);
    return seq;
}

void stats_record_call_assessed(const char *number, const char *display,
                                verdict_t verdict, pb_assessment_t assessment)
{
    stats_call_t call;
    fill_call(&call, number, display, verdict, assessment);
    list_call(&call, assessment != PB_ASSESS_TEST);
}

void stats_record_call_checked(const char *number, const char *display,
                               const pb_check_result_t *result)
{
    stats_call_t call;
    fill_call(&call, number, display, result->verdict, result->assessment);
    call.direct_votes = result->direct_votes;
    call.range_votes  = result->range_votes;
    call.wildcard     = result->wildcard;
    copy_trim(call.label,    sizeof(call.label),    result->label);
    copy_trim(call.location, sizeof(call.location), result->location);
    list_call(&call, true);
}

void stats_record_call_counters_only(const char *number, const char *display,
                                     verdict_t verdict, pb_assessment_t assessment)
{
    lock();
    bump_counters(verdict);
    unlock();
    // Not listed, but the display is the phone in the room, not the list:
    // it shows every call.
    if (DISPLAY_PRESENT) {
        stats_call_t shown;
        fill_call(&shown, number, display, verdict, assessment);
        display_call_started(&shown);
    }
}

void stats_record_error(int level, const char *tag, const char *message)
{
    lock();

    stats_error_t *slot = &s_errors[s_errors_head];
    slot->at_us = (int64_t)pb_monotonic_us();
    slot->level = level;
    copy_trim(slot->tag,     sizeof(slot->tag),     tag);
    copy_trim(slot->message, sizeof(slot->message), message);

    s_errors_head = (s_errors_head + 1) % STATS_MAX_ERRORS;
    if (s_errors_count < STATS_MAX_ERRORS) s_errors_count++;

    unlock();
}

void stats_record_sip_state(bool registered)
{
    lock();
    if (registered && !s_counters.sip_registered) {
        s_counters.sip_registered_since_us = (int64_t)pb_monotonic_us();
    }
    s_counters.sip_registered = registered;
    unlock();
}

void stats_record_api_result(const api_phases_t *p, const api_failure_t *failure)
{
    lock();
    if (failure == NULL || failure->kind == API_FAIL_NONE) {
        s_counters.last_api_phases      = *p;
        s_counters.last_api_duration_us = p->total_us;
        s_counters.last_api_failed      = false;
    } else {
        s_counters.last_api_failure       = *failure;
        s_counters.last_api_failure.at_us = (int64_t)pb_monotonic_us();
        // The detail/call strings come from the caller; keep them terminated.
        s_counters.last_api_failure.detail[API_FAIL_DETAIL_LEN - 1] = '\0';
        s_counters.last_api_failure.call[API_FAIL_CALL_LEN - 1]     = '\0';
        s_counters.last_api_failed = true;
    }
    unlock();
}

// --- Snapshots ------------------------------------------------------

void stats_snapshot_counters(stats_counters_t *out)
{
    lock();
    *out = s_counters;
    unlock();
}

// Iterate the ring buffer from newest to oldest.
static int snapshot_ring(const void *buffer, size_t slot_size,
                         int head, int count, int ring_n,
                         void *out, int max)
{
    int n = count < max ? count : max;
    for (int i = 0; i < n; i++) {
        int src = (head - 1 - i + ring_n) % ring_n;
        memcpy((char *)out + (size_t)i * slot_size,
               (const char *)buffer + (size_t)src * slot_size,
               slot_size);
    }
    return n;
}

int stats_snapshot_errors(stats_error_t *out, int max)
{
    lock();
    int n = snapshot_ring(s_errors, sizeof(*s_errors),
                          s_errors_head, s_errors_count, STATS_MAX_ERRORS,
                          out, max);
    unlock();
    return n;
}

void stats_clear_errors(void)
{
    lock();
    s_errors_head = 0;
    s_errors_count = 0;
    unlock();
}

// --- Reading and updating the call list -----------------------------

int stats_read_calls(uint32_t before_seq, stats_call_t *out, int max)
{
    int n = 0;
    lock();
    uint32_t lo = oldest_seq();
    uint32_t hi = before_seq == 0 ? s_last_seq : before_seq - 1;
    if (s_calls_open && lo != 0 && max > 0 && hi >= lo) {
        if (hi > s_last_seq) hi = s_last_seq;
        FILE *f = calls_fopen("rb");
        call_record_t rec;
        for (uint32_t seq = hi; f && seq >= lo && n < max; seq--) {
            if (read_record(f, seq, &rec)) record_to_call(&rec, &out[n++]);
        }
        if (f) fclose(f);
    }
    unlock();
    return n;
}

int stats_snapshot_calls(stats_call_t *out, int max)
{
    return stats_read_calls(0, out, max);
}

void stats_calls_info(stats_calls_info_t *out)
{
    lock();
    uint32_t lo = s_calls_open ? oldest_seq() : 0;
    out->oldest_seq = lo;
    out->newest_seq = lo ? s_last_seq : 0;
    out->count      = lo ? (int)(s_last_seq - lo + 1) : 0;
    out->floor_seq  = s_floor_seq;
    out->mailed_seq = s_mailed_seq;
    unlock();
}

void stats_set_mailed_seq(uint32_t seq)
{
    lock();
    bool ok = true;
    if (s_calls_open && seq != s_mailed_seq && seq < s_next_seq) {
        s_mailed_seq = seq;
        ok = write_header();
    }
    unlock();
    if (!ok) pb_log_warn(TAG, "call list: storing the mail mark failed");
}

// Visits the listed calls with this number (all with NULL), newest or
// oldest first. `visit` returns true for a record it takes (and, on a
// `writable` walk, changed: it is written back); it sets *stop to end the
// walk. Under the lock. Returns the number of records taken; *failed on a
// write error.
typedef bool (*record_visit_t)(call_record_t *rec, void *ctx, bool *stop);

static int walk_records(bool newest_first, bool writable, const char *number,
                        record_visit_t visit, void *ctx, bool *failed)
{
    *failed = false;
    uint32_t lo = oldest_seq();
    if (!s_calls_open || lo == 0) return 0;
    FILE *f = calls_fopen(writable ? "r+b" : "rb");
    if (!f) { *failed = writable; return 0; }
    int taken = 0;
    bool stop = false;
    call_record_t rec;
    uint32_t count = s_last_seq - lo + 1;
    for (uint32_t i = 0; i < count && !stop; i++) {
        uint32_t seq = newest_first ? s_last_seq - i : lo + i;
        if (!read_record(f, seq, &rec)) continue;
        if (number && strncmp(rec.number, number, sizeof(rec.number)) != 0) continue;
        if (!visit(&rec, ctx, &stop)) continue;
        taken++;
        if (writable && !write_at(f, slot_offset(seq), &rec, sizeof(rec))) {
            *failed = true;
            break;
        }
    }
    if (writable) {
        if (!close_written(f)) *failed = true;
    } else {
        fclose(f);
    }
    return taken;
}

typedef struct {
    const char            *display;
    const pb_assessment_t *assessment;
} update_ctx_t;

static bool update_visit(call_record_t *rec, void *ctx, bool *stop)
{
    (void)stop;
    const update_ctx_t *u = ctx;
    if (u->display) {
        // Never overwrite a name the Fritz!Box already delivered.
        if (rec->display[0]) return false;
        copy_trim(rec->display, sizeof(rec->display), u->display);
    }
    if (u->assessment) {
        rec->assessment   = (uint8_t)*u->assessment;
        rec->wildcard     = 0;
        rec->direct_votes = 0;
        rec->range_votes  = 0;
    }
    return true;
}

// Both updaters walk the whole list rather than only the newest match: a
// number that called twice appears twice, and leaving the older row stale
// would read as if the change had only half worked.
static int update_calls(const char *number, const char *display,
                        const pb_assessment_t *assessment)
{
    if (!number || !*number) return 0;
    update_ctx_t u = { display, assessment };
    bool failed;
    lock();
    int n = walk_records(false, true, number, update_visit, &u, &failed);
    if (!failed) failed = !maybe_backfill_locked();
    unlock();
    if (failed) pb_log_warn(TAG, "call list: updating %s failed", number);
    if (n > 0) display_calls_changed();
    return n;
}

int stats_set_display(const char *number, const char *display)
{
    if (!display || !*display) return 0;
    return update_calls(number, display, NULL);
}

int stats_set_assessment(const char *number, pb_assessment_t assessment)
{
    return update_calls(number, NULL, &assessment);
}

static bool assessment_visit(call_record_t *rec, void *ctx, bool *stop)
{
    *(pb_assessment_t *)ctx = (pb_assessment_t)rec->assessment;
    *stop = true;
    return true;
}

bool stats_assessment_for_number(const char *number, pb_assessment_t *out)
{
    if (!number || !*number || !out) return false;
    bool failed;
    pb_assessment_t a = PB_ASSESS_UNKNOWN;
    lock();
    int found = walk_records(true, false, number, assessment_visit, &a, &failed);
    unlock();
    if (found) *out = a;
    return found > 0;
}

typedef struct {
    char  *out;
    size_t cap;
} name_ctx_t;

static bool name_visit(call_record_t *rec, void *ctx, bool *stop)
{
    const name_ctx_t *n = ctx;
    if (!rec->display[0]) return false;
    copy_trim(n->out, n->cap, rec->display);
    *stop = true;
    return true;
}

bool stats_display_for_number(const char *number, char *out, size_t cap)
{
    if (!out || cap == 0) return false;
    out[0] = '\0';
    if (!number || !*number) return false;
    name_ctx_t n = { out, cap };
    bool failed;
    lock();
    int found = walk_records(true, false, number, name_visit, &n, &failed);
    unlock();
    return found > 0;
}

void stats_clear_calls(void)
{
    lock();
    bool ok = true;
    int err = 0;
    if (s_calls_open) {
        // Sequence numbers go on: a webhook receiver or the mail never sees
        // a number twice.
        s_floor_seq = s_next_seq;
        if (s_mailed_seq >= s_floor_seq) s_mailed_seq = s_floor_seq - 1;
        s_last_seq = 0;
        s_backfill_pending = false;
        ok = write_empty_file();
        if (!ok) err = errno;
    }
    unlock();
    if (!ok) pb_log_warn(TAG, "call list: clearing failed: %s", strerror(err));
    display_calls_changed();
}

uint32_t stats_last_call_seq(void)
{
    lock();
    uint32_t seq = s_calls_open ? s_last_seq : 0;
    unlock();
    return seq;
}

void stats_call_ended(uint32_t seq, int64_t duration_us)
{
    if (seq == 0) return;
    int64_t s = duration_us > 0 ? (duration_us + 500000) / 1000000 : 0;
    if (s > UINT16_MAX) s = UINT16_MAX;
    bool found = false, failed = false;
    lock();
    uint32_t lo = oldest_seq();
    if (s_calls_open && lo != 0 && seq >= lo && seq <= s_last_seq) {
        FILE *f = calls_fopen("r+b");
        call_record_t rec;
        if (f && read_record(f, seq, &rec) && rec.verdict == VERDICT_SPAM) {
            found = true;
            rec.duration_s = (uint16_t)s;
            failed = !write_at(f, slot_offset(seq), &rec, sizeof(rec));
        }
        if (f) failed = !close_written(f) || failed;
        else   failed = true;
        if (!failed) failed = !maybe_backfill_locked();
    }
    unlock();
    if (failed) pb_log_warn(TAG, "call list: storing the duration of call %u failed",
                            (unsigned)seq);
    if (found) notify_listener(seq);
}

bool stats_get_call(uint32_t seq, stats_call_t *out)
{
    if (seq == 0 || !out) return false;
    bool found = false;
    lock();
    uint32_t lo = oldest_seq();
    if (s_calls_open && lo != 0 && seq >= lo && seq <= s_last_seq) {
        FILE *f = calls_fopen("rb");
        call_record_t rec;
        if (f && read_record(f, seq, &rec)) {
            record_to_call(&rec, out);
            found = true;
        }
        if (f) fclose(f);
    }
    unlock();
    return found;
}

int stats_count_calls(bool (*match)(const stats_call_t *call), int newest)
{
    int n = 0;
    lock();
    uint32_t lo = oldest_seq();
    if (s_calls_open && lo != 0 && newest > 0) {
        FILE *f = calls_fopen("rb");
        call_record_t rec;
        stats_call_t c;
        int seen = 0;
        for (uint32_t seq = s_last_seq; f && seq >= lo && seen < newest; seq--, seen++) {
            if (!read_record(f, seq, &rec)) continue;
            record_to_call(&rec, &c);
            if (match(&c)) n++;
        }
        if (f) fclose(f);
    }
    unlock();
    return n;
}
