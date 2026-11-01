// Host test for the recent-calls list in stats.c: the row actions the web
// UI's call list depends on, and its persistence in the call file (#589) —
// reopen ("reboot"), wrap-around, in-place updates, a damaged file, the
// time back-fill and clearing.
//
// A listed call shows the caller's current state: after "report as spam",
// "add as contact" or "whitelist" every row with that number must read the new
// assessment, because the web UI derives the buttons it offers from it. And a
// test call (**622 to the dongle's own extension) is listed as "Test" but must
// not move the spam counter.
#include <stdio.h>
#include <string.h>

#include "platform.h"
#include "stats.h"
#include "calls_test_util.h"

static int tests_run, tests_failed;

#define CHECK(cond, what) do {                                  \
    tests_run++;                                                \
    if (cond) printf("  ok: %s\n", (what));                     \
    else { tests_failed++; printf("  FAIL: %s (%s:%d)\n",       \
                                  (what), __FILE__, __LINE__); } \
} while (0)

#define NUM_A "+4930123456"
#define NUM_B "+4940987654"

// Record an API-checked call the way check_invite_caller() does.
static void record_checked(const char *number, pb_assessment_t a, bool wildcard)
{
    pb_check_result_t r;
    memset(&r, 0, sizeof(r));
    r.verdict      = (a == PB_ASSESS_SPAM || a == PB_ASSESS_SPAM_LIST)
                   ? VERDICT_SPAM : VERDICT_LEGITIMATE;
    r.assessment   = a;
    r.direct_votes = 7;
    r.range_votes  = 3;
    r.wildcard     = wildcard;
    stats_record_call_checked(number, "", &r);
}

// Newest-first entry `i` of the list.
static stats_call_t call_at(int i)
{
    static stats_call_t calls[STATS_CALL_CAPACITY];
    int n = stats_snapshot_calls(calls, i + 1);
    stats_call_t c;
    memset(&c, 0, sizeof(c));
    if (i < n) c = calls[i];
    return c;
}

static void test_test_call_not_counted(void)
{
    printf("test call:\n");
    stats_clear_calls();
    stats_counters_t before, after;
    stats_snapshot_counters(&before);
    stats_record_call_assessed("**622", "", VERDICT_SPAM, PB_ASSESS_TEST);
    stats_snapshot_counters(&after);
    CHECK(after.spam_blocked == before.spam_blocked, "spam counter unchanged");
    CHECK(after.total_calls == before.total_calls, "total counter unchanged");
    CHECK(call_at(0).assessment == PB_ASSESS_TEST, "listed as TEST");

    stats_record_call_assessed(NUM_A, "", VERDICT_SPAM, PB_ASSESS_NAME_PATTERN);
    stats_snapshot_counters(&after);
    CHECK(after.spam_blocked == before.spam_blocked + 1,
          "a real spam call still counts");
}

static void test_set_assessment_all_rows(void)
{
    printf("set assessment:\n");
    stats_clear_calls();
    record_checked(NUM_A, PB_ASSESS_SPAM_LIST, true);
    record_checked(NUM_B, PB_ASSESS_UNKNOWN, false);
    record_checked(NUM_A, PB_ASSESS_SPAM_LIST, true);

    int n = stats_set_assessment(NUM_A, PB_ASSESS_LEGITIMATE);
    CHECK(n == 2, "both rows of the number updated");
    stats_call_t newest = call_at(0), oldest = call_at(2), other = call_at(1);
    CHECK(newest.assessment == PB_ASSESS_LEGITIMATE
          && oldest.assessment == PB_ASSESS_LEGITIMATE, "new state on every row");
    CHECK(!newest.wildcard && newest.direct_votes == 0 && newest.range_votes == 0,
          "exact-number decision: wildcard and votes cleared");
    CHECK(other.assessment == PB_ASSESS_UNKNOWN && other.direct_votes == 7,
          "other number untouched");
    CHECK(stats_set_assessment("+491111", PB_ASSESS_BLACKLIST) == 0,
          "unlisted number updates nothing");
}

static void test_assessment_for_number(void)
{
    printf("assessment for number:\n");
    stats_clear_calls();
    pb_assessment_t a = PB_ASSESS_ERROR;
    CHECK(!stats_assessment_for_number(NUM_A, &a), "not listed → false");

    record_checked(NUM_A, PB_ASSESS_UNKNOWN, false);
    record_checked(NUM_A, PB_ASSESS_SPAM, false);
    CHECK(stats_assessment_for_number(NUM_A, &a) && a == PB_ASSESS_SPAM,
          "newest row wins");

    // Wrap the ring so the newest entry sits at a low slot again.
    for (int i = 0; i < STATS_CALL_CAPACITY - 1; i++)
        record_checked(NUM_B, PB_ASSESS_UNKNOWN, false);
    record_checked(NUM_A, PB_ASSESS_SUSPECT, false);
    CHECK(stats_assessment_for_number(NUM_A, &a) && a == PB_ASSESS_SUSPECT,
          "newest row wins after the ring wrapped");
}

static void test_contact_flow(void)
{
    printf("contact:\n");
    stats_clear_calls();
    record_checked(NUM_A, PB_ASSESS_BLACKLIST, false);
    CHECK(stats_set_display(NUM_A, "Mama") == 1, "name filled in");
    stats_set_assessment(NUM_A, PB_ASSESS_LEGITIMATE);
    stats_call_t c = call_at(0);
    CHECK(strcmp(c.display, "Mama") == 0 && c.assessment == PB_ASSESS_LEGITIMATE,
          "row shows the contact as legitimate");
}

// A known caller with "list known calls" off: counted, not listed (its
// number and name only go to the display, which shows every call).
static void test_counters_only_not_listed(void)
{
    printf("counters only:\n");
    stats_clear_calls();
    stats_counters_t before, after;
    stats_snapshot_counters(&before);
    stats_record_call_counters_only(NUM_A, "Max Müller", VERDICT_LEGITIMATE,
                                    PB_ASSESS_LEGITIMATE);
    stats_snapshot_counters(&after);
    stats_call_t calls[2];
    CHECK(stats_snapshot_calls(calls, 2) == 0, "not listed");
    CHECK(after.total_calls == before.total_calls + 1, "total counter bumped");
    CHECK(after.legitimate == before.legitimate + 1, "legitimate counter bumped");
}

// Listener for the webhook hook: remembers every sequence number it gets.
static uint32_t s_heard[2 * STATS_CALL_CAPACITY];
static int      s_heard_count;

static void hear(uint32_t seq)
{
    if (s_heard_count < (int)(sizeof(s_heard) / sizeof(s_heard[0])))
        s_heard[s_heard_count++] = seq;
}

static void test_call_listener(void)
{
    printf("call listener:\n");
    stats_clear_calls();
    s_heard_count = 0;
    stats_set_call_listener(hear);

    stats_record_call(NUM_A, "Max", VERDICT_LEGITIMATE);
    CHECK(s_heard_count == 1 && s_heard[0] == stats_last_call_seq(),
          "a legitimate call is notified when recorded");
    record_checked(NUM_B, PB_ASSESS_SPAM, false);
    uint32_t spam_seq = stats_last_call_seq();
    CHECK(s_heard_count == 1, "an answered spam call is not notified yet");
    stats_record_call_counters_only(NUM_A, "Max", VERDICT_LEGITIMATE,
                                    PB_ASSESS_LEGITIMATE);
    CHECK(stats_last_call_seq() == spam_seq,
          "a counters-only call gets no sequence number");
    stats_call_ended(spam_seq, 12600000);   // 12.6 s
    CHECK(s_heard_count == 2, "the spam call is notified when it ends");
    CHECK(s_heard[0] != 0 && s_heard[1] == s_heard[0] + 1,
          "sequence numbers count up from a nonzero start");
    stats_call_ended(s_heard[0], 5000000);
    CHECK(s_heard_count == 2, "ending a call the dongle did not answer is ignored");

    stats_call_t c;
    CHECK(stats_get_call(s_heard[0], &c) && strcmp(c.number, NUM_A) == 0
          && strcmp(c.display, "Max") == 0, "first call found by its number");
    CHECK(stats_get_call(s_heard[1], &c) && strcmp(c.number, NUM_B) == 0
          && c.direct_votes == 7, "checked call found with its API fields");
    CHECK(c.duration_s == 13, "duration rounded to whole seconds");
    CHECK(stats_get_call(s_heard[0], &c) && c.duration_s == 0,
          "a call the dongle did not answer has no duration");

    // A caller who hangs up at once, and a call that would overflow the field.
    stats_record_call(NUM_B, "", VERDICT_SPAM);
    stats_call_ended(stats_last_call_seq(), 400000);
    CHECK(stats_get_call(stats_last_call_seq(), &c) && c.duration_s == 0,
          "under half a second rounds to 0");
    stats_record_call(NUM_B, "", VERDICT_SPAM);
    stats_call_ended(stats_last_call_seq(), (int64_t)100000 * 1000000);
    CHECK(stats_get_call(stats_last_call_seq(), &c) && c.duration_s == UINT16_MAX,
          "a duration beyond the field is clamped");
    CHECK(s_heard_count == 4, "each ended spam call is notified once");
    stats_call_ended(0, 1000000);
    CHECK(s_heard_count == 4, "sequence number 0 is ignored");
    CHECK(!stats_get_call(0, &c), "0 is never a call");
    CHECK(!stats_get_call(stats_last_call_seq() + 1, &c), "a future number is not found");

    // The list keeps STATS_CALL_CAPACITY entries: once that many newer
    // calls came in, the first one is gone.
    uint32_t first = s_heard[0];
    for (int i = 0; i < STATS_CALL_CAPACITY - 3; i++)
        stats_record_call(NUM_B, "", VERDICT_LEGITIMATE);
    CHECK(!stats_get_call(first, &c), "overwritten call is gone");
    CHECK(stats_get_call(first + 1, &c) && strcmp(c.number, NUM_B) == 0,
          "the next call is still listed");

    stats_clear_calls();
    CHECK(!stats_get_call(first + 1, &c), "cleared list finds nothing");

    stats_set_call_listener(NULL);
    s_heard_count = 0;
    stats_record_call(NUM_A, "", VERDICT_LEGITIMATE);
    CHECK(s_heard_count == 0, "removed listener hears nothing");
}

// Issue #551: only a 2xx response is a latency; a failed call is recorded
// as its cause and leaves the last latency alone.
static void test_api_fail_kind(void)
{
    printf("api failure classification:\n");
    CHECK(api_fail_kind(true, PB_HTTP_FAIL_OTHER, 200) == API_FAIL_NONE, "200 is success");
    CHECK(api_fail_kind(true, PB_HTTP_FAIL_OTHER, 204) == API_FAIL_NONE, "204 is success");
    CHECK(api_fail_kind(true, PB_HTTP_FAIL_OTHER, 401) == API_FAIL_HTTP, "401 is an HTTP failure");
    CHECK(api_fail_kind(true, PB_HTTP_FAIL_OTHER, 503) == API_FAIL_HTTP, "503 is an HTTP failure");
    CHECK(api_fail_kind(true, PB_HTTP_FAIL_CONNECT, 302) == API_FAIL_HTTP,
          "with a response, the transport class is ignored");
    CHECK(api_fail_kind(false, PB_HTTP_FAIL_DNS, 0) == API_FAIL_DNS, "DNS");
    CHECK(api_fail_kind(false, PB_HTTP_FAIL_CONNECT, 0) == API_FAIL_CONNECT, "connect");
    CHECK(api_fail_kind(false, PB_HTTP_FAIL_TLS, 0) == API_FAIL_TLS, "TLS");
    CHECK(api_fail_kind(false, PB_HTTP_FAIL_TIMEOUT, 0) == API_FAIL_TIMEOUT, "timeout");
    CHECK(api_fail_kind(false, PB_HTTP_FAIL_OTHER, 0) == API_FAIL_OTHER, "other");
    CHECK(api_fail_kind(false, 999, 0) == API_FAIL_OTHER, "unknown class is other");
    CHECK(strcmp(api_fail_kind_name(API_FAIL_NONE), "") == 0, "name of none");
    CHECK(strcmp(api_fail_kind_name(API_FAIL_CONNECT), "connect") == 0, "name of connect");
    CHECK(strcmp(api_fail_kind_name(API_FAIL_DNS), "dns") == 0, "name of dns");
    CHECK(strcmp(api_fail_kind_name(API_FAIL_TLS), "tls") == 0, "name of tls");
    CHECK(strcmp(api_fail_kind_name(API_FAIL_TIMEOUT), "timeout") == 0, "name of timeout");
    CHECK(strcmp(api_fail_kind_name(API_FAIL_HTTP), "http") == 0, "name of http");
    CHECK(strcmp(api_fail_kind_name(API_FAIL_OTHER), "other") == 0, "name of other");
}

static void test_api_result_recording(void)
{
    printf("api result recording:\n");
    stats_counters_t c;
    stats_snapshot_counters(&c);
    CHECK(c.last_api_duration_us == 0 && !c.last_api_phases.valid,
          "no latency before the first call");
    CHECK(!c.last_api_failed && c.last_api_failure.kind == API_FAIL_NONE,
          "no failure before the first call");

    api_phases_t ok = { .valid = true, .total_us = 230000, .connect_us = 120000,
                        .wait_us = 90000, .download_us = 20000 };
    stats_record_api_result(&ok, NULL);
    stats_snapshot_counters(&c);
    CHECK(c.last_api_duration_us == 230000, "success sets the latency");
    CHECK(c.last_api_phases.valid && c.last_api_phases.connect_us == 120000,
          "success sets the phases");
    CHECK(!c.last_api_failed, "success is not a failure");

    // The #551 case: connect never succeeded, the whole time is the timeout.
    api_phases_t timeout = { .valid = true, .total_us = 10075000 };
    api_failure_t f = { .kind = API_FAIL_CONNECT, .elapsed_us = 10075000 };
    snprintf(f.detail, sizeof(f.detail), "%s", "ESP_ERR_HTTP_CONNECT");
    snprintf(f.call, sizeof(f.call), "%s", "test");
    stats_record_api_result(&timeout, &f);
    stats_snapshot_counters(&c);
    CHECK(c.last_api_duration_us == 230000, "failure keeps the last latency");
    CHECK(c.last_api_phases.total_us == 230000, "failure keeps the last phases");
    CHECK(c.last_api_failed, "failure flag set");
    CHECK(c.last_api_failure.kind == API_FAIL_CONNECT, "failure kind stored");
    CHECK(strcmp(c.last_api_failure.detail, "ESP_ERR_HTTP_CONNECT") == 0, "detail stored");
    CHECK(strcmp(c.last_api_failure.call, "test") == 0, "call stored");
    CHECK(c.last_api_failure.elapsed_us == 10075000, "elapsed time stored");
    CHECK(c.last_api_failure.at_us > 0, "failure is time-stamped");

    // A kind of API_FAIL_NONE is a success, like NULL.
    api_failure_t none = { .kind = API_FAIL_NONE };
    api_phases_t ok2 = { .valid = true, .total_us = 180000 };
    stats_record_api_result(&ok2, &none);
    stats_snapshot_counters(&c);
    CHECK(c.last_api_duration_us == 180000, "recovery updates the latency");
    CHECK(!c.last_api_failed, "recovery clears the failure flag");
    CHECK(c.last_api_failure.kind == API_FAIL_CONNECT,
          "the last failure stays on record after recovery");

    api_phases_t ok3 = { .valid = true, .total_us = 50000 };
    api_failure_t http = { .kind = API_FAIL_HTTP, .http_status = 503 };
    snprintf(http.detail, sizeof(http.detail), "%s", "HTTP 503");
    stats_record_api_result(&ok3, &http);
    stats_snapshot_counters(&c);
    CHECK(c.last_api_duration_us == 180000, "an HTTP error is no latency either");
    CHECK(c.last_api_failed && c.last_api_failure.kind == API_FAIL_HTTP
          && c.last_api_failure.http_status == 503, "HTTP failure replaces the older one");
}

// --- The call file (#589) -----------------------------------------------

#define NUM_C "+493012345670"
#define NUM_D "+493012345671"

static int64_t s_epoch;                      // the injected wall clock
static int64_t test_clock(void) { return s_epoch; }

// Everything a reader sees of a call that survives a reboot (not this_boot
// and at_us, which belong to the boot that recorded it).
static bool same_call(const stats_call_t *a, const stats_call_t *b)
{
    return a->seq == b->seq && a->epoch_s == b->epoch_s
        && strcmp(a->number, b->number) == 0 && strcmp(a->display, b->display) == 0
        && strcmp(a->label, b->label) == 0 && strcmp(a->location, b->location) == 0
        && a->verdict == b->verdict && a->assessment == b->assessment
        && a->direct_votes == b->direct_votes && a->range_votes == b->range_votes
        && a->wildcard == b->wildcard && a->duration_s == b->duration_s;
}

static void test_reopen(void)
{
    printf("call file survives a reboot:\n");
    stats_clear_calls();
    s_epoch = 1793000000;
    pb_check_result_t r;
    memset(&r, 0, sizeof(r));
    r.verdict = VERDICT_SPAM;
    r.assessment = PB_ASSESS_SPAM;
    r.direct_votes = 12;
    r.range_votes = 40;
    r.wildcard = true;
    snprintf(r.label, sizeof(r.label), "%s", "(DE) 030 1234567x");
    snprintf(r.location, sizeof(r.location), "%s", "Berlin");
    stats_record_call_checked(NUM_C, "", &r);
    stats_record_call(NUM_D, "Max Mustermann", VERDICT_LEGITIMATE);
    stats_record_call_assessed("**622", "", VERDICT_SPAM, PB_ASSESS_TEST);
    stats_record_call(NUM_A, "", VERDICT_ERROR);
    stats_record_call(NUM_B, "", VERDICT_LEGITIMATE);

    stats_call_t before[5], after[5];
    int nb = stats_snapshot_calls(before, 5);
    uint32_t last = stats_last_call_seq();
    s_log_warns = s_log_errors = 0;
    CHECK(calls_test_reboot(), "reopened");
    int na = stats_snapshot_calls(after, 5);
    bool same = nb == 5 && na == 5;
    for (int i = 0; same && i < 5; i++) same = same_call(&before[i], &after[i]);
    CHECK(same, "the same 5 calls, field by field");
    CHECK(strcmp(after[0].number, NUM_B) == 0 && strcmp(after[4].number, NUM_C) == 0
          && after[0].seq == last && after[4].seq == last - 4, "newest first");
    CHECK(!after[0].this_boot && after[0].epoch_s == 1793000000,
          "a call of the earlier boot keeps its time");
    CHECK(s_log_warns == 0 && s_log_errors == 0, "a good file opens without a WARN");
    CHECK(stats_last_call_seq() == last, "last seq restored");
    stats_record_call(NUM_A, "", VERDICT_LEGITIMATE);
    CHECK(stats_last_call_seq() == last + 1 && call_at(0).this_boot,
          "sequence numbers go on after the reboot");
    stats_calls_info_t info;
    stats_calls_info(&info);
    CHECK(info.count == 6 && info.newest_seq == last + 1 && info.oldest_seq == last - 4,
          "info: 6 calls");
}

static void test_wrap_around(void)
{
    printf("call file wraps around:\n");
    stats_clear_calls();
    uint32_t first = 0;
    char num[24];
    int total = STATS_CALL_CAPACITY + 7;
    for (int i = 0; i < total; i++) {
        snprintf(num, sizeof(num), "+4930123%05d", i);
        stats_record_call(num, "", VERDICT_LEGITIMATE);
        if (i == 0) first = stats_last_call_seq();
    }
    for (int pass = 0; pass < 2; pass++) {
        stats_calls_info_t info;
        stats_calls_info(&info);
        CHECK(info.count == STATS_CALL_CAPACITY, "count == capacity");
        CHECK(info.newest_seq == first + (uint32_t)total - 1
              && info.oldest_seq == first + 7, "the 7 oldest dropped");
        // Page through the whole list, 7 at a time.
        bool order = true;
        int seen = 0;
        uint32_t cursor = 0;
        stats_call_t page[7];
        int n;
        while ((n = stats_read_calls(cursor, page, 7)) > 0) {
            for (int i = 0; i < n; i++, seen++) {
                snprintf(num, sizeof(num), "+4930123%05d", total - 1 - seen);
                if (page[i].seq != info.newest_seq - (uint32_t)seen
                        || strcmp(page[i].number, num) != 0) order = false;
            }
            cursor = page[n - 1].seq;
        }
        CHECK(order && seen == STATS_CALL_CAPACITY, "paged newest first, seqs consecutive");
        stats_call_t c;
        CHECK(!stats_get_call(first + 6, &c) && stats_get_call(first + 7, &c),
              "the oldest listed is found, the one before it not");
        if (pass == 0) CHECK(calls_test_reboot(), "reopened after the wrap");
    }
}

static void test_updates_persist(void)
{
    printf("in-place updates persist:\n");
    stats_clear_calls();
    stats_record_call(NUM_C, "", VERDICT_SPAM);
    uint32_t spam = stats_last_call_seq();
    stats_record_call(NUM_D, "", VERDICT_LEGITIMATE);
    stats_record_call(NUM_A, "", VERDICT_LEGITIMATE);
    stats_call_ended(spam, 42400000);
    CHECK(stats_set_display(NUM_D, "Erika Musterfrau") == 1, "name set");
    CHECK(stats_set_assessment(NUM_A, PB_ASSESS_BLACKLIST) == 1, "assessment set");
    CHECK(calls_test_reboot(), "reopened");
    stats_call_t c;
    CHECK(stats_get_call(spam, &c) && c.duration_s == 42, "duration kept");
    CHECK(stats_get_call(spam + 1, &c) && strcmp(c.display, "Erika Musterfrau") == 0,
          "name kept");
    pb_assessment_t a;
    CHECK(stats_assessment_for_number(NUM_A, &a) && a == PB_ASSESS_BLACKLIST,
          "assessment kept");
    char name[STATS_DISPLAY_LEN];
    CHECK(stats_display_for_number(NUM_D, name, sizeof(name))
          && strcmp(name, "Erika Musterfrau") == 0, "name found by number");
}

// Writes `n` bytes of `data` as the whole call file.
static void put_file(const void *data, size_t n)
{
    FILE *f = fopen(s_calls_file, "wb");
    if (f) { fwrite(data, 1, n, f); fclose(f); }
}

static long file_size(void)
{
    FILE *f = fopen(s_calls_file, "rb");
    if (!f) return -1;
    fseek(f, 0, SEEK_END);
    long n = ftell(f);
    fclose(f);
    return n;
}

static void check_discarded(const char *what)
{
    s_log_warns = s_log_errors = 0;
    stats_calls_close();
    bool ok = stats_calls_open(s_calls_file);
    stats_calls_info_t info;
    stats_calls_info(&info);
    char line[128];
    snprintf(line, sizeof(line), "%s: empty list, one WARN, file rewritten", what);
    CHECK(ok && info.count == 0 && s_log_warns == 1 && s_log_errors == 0
          && file_size() == STATS_CALLS_FILE_BYTES(STATS_CALL_CAPACITY), line);
    stats_record_call(NUM_A, "", VERDICT_LEGITIMATE);
    snprintf(line, sizeof(line), "%s: then works as usual", what);
    CHECK(stats_last_call_seq() == 1 && calls_test_reboot() && call_at(0).seq == 1
          && s_log_warns == 1, line);
}

static void test_damaged_file(void)
{
    printf("damaged or foreign file:\n");
    static uint8_t buf[STATS_CALLS_FILE_BYTES(STATS_CALL_CAPACITY) + 64];

    memset(buf, 'x', sizeof(buf));
    put_file(buf, 1000);
    check_discarded("foreign file");

    put_file(buf, 0);
    check_discarded("empty file");

    // A good file, then damaged in different ways.
    stats_clear_calls();
    stats_record_call(NUM_B, "", VERDICT_LEGITIMATE);
    uint32_t want = stats_last_call_seq();
    FILE *f = fopen(s_calls_file, "rb");
    size_t good = f ? fread(buf, 1, sizeof(buf), f) : 0;
    if (f) fclose(f);
    CHECK(good == STATS_CALLS_FILE_BYTES(STATS_CALL_CAPACITY), "file has its full size");

    put_file(buf, good - 100);
    check_discarded("truncated file");

    put_file(buf, good + 64);
    check_discarded("file too long");

    uint8_t saved = buf[8];
    buf[8] ^= 0x01;                     // capacity
    put_file(buf, good);
    check_discarded("other capacity");
    buf[8] = saved;

    saved = buf[4];
    buf[4] = 9;                         // format version
    put_file(buf, good);
    check_discarded("other version");
    buf[4] = saved;

    // A record in the wrong slot.
    uint32_t seq;
    size_t at = STATS_CALL_HEADER_BYTES
              + (want % STATS_CALL_CAPACITY) * STATS_CALL_RECORD_BYTES;
    memcpy(&seq, buf + at, sizeof(seq));
    CHECK(seq == want, "the record of a call sits in slot seq % capacity");
    seq = want + 1;
    memcpy(buf + at, &seq, sizeof(seq));
    put_file(buf, good);
    check_discarded("record in the wrong slot");

    // No file at all: created quietly.
    unlink(s_calls_file);
    s_log_warns = s_log_errors = 0;
    stats_calls_close();
    CHECK(stats_calls_open(s_calls_file) && s_log_warns == 0 && s_log_errors == 0
          && file_size() == STATS_CALLS_FILE_BYTES(STATS_CALL_CAPACITY),
          "missing file: created without a WARN");
}

static void test_time_backfill(void)
{
    printf("call time back-fill:\n");
    stats_clear_calls();
    s_epoch = 0;                         // clock not set yet
    stats_record_call(NUM_C, "", VERDICT_LEGITIMATE);
    uint32_t early = stats_last_call_seq();
    stats_call_t c;
    CHECK(stats_get_call(early, &c) && c.epoch_s == 0 && c.this_boot,
          "before the clock is set: no time");

    s_epoch = 1793100000;
    // Before the back-fill is written, readers derive the time from the age.
    CHECK(stats_get_call(early, &c) && c.epoch_s >= s_epoch - 1 && c.epoch_s <= s_epoch,
          "readers see the time as soon as the clock is set");
    stats_time_synced();
    s_epoch = 0;                         // the next boot starts without a clock
    CHECK(calls_test_reboot(), "reopened");
    CHECK(stats_get_call(early, &c) && !c.this_boot
          && c.epoch_s >= 1793100000 - 1 && c.epoch_s <= 1793100000,
          "back-filled time stored in the file");

    // A call of this (new) boot before the clock, then a reboot before it
    // was set: that call keeps "time unknown" for good.
    stats_record_call(NUM_D, "", VERDICT_LEGITIMATE);
    uint32_t lost = stats_last_call_seq();
    CHECK(calls_test_reboot(), "reopened again");
    s_epoch = 1793200000;
    stats_time_synced();
    stats_record_call(NUM_A, "", VERDICT_LEGITIMATE);
    CHECK(stats_get_call(lost, &c) && c.epoch_s == 0 && !c.this_boot,
          "a call of an earlier boot without a time stays 0");
    CHECK(stats_get_call(stats_last_call_seq(), &c) && c.epoch_s == 1793200000,
          "a call after the clock is set gets its time");

    // The lazy path: no "synced" event, the next write back-fills.
    s_epoch = 0;
    stats_record_call(NUM_B, "", VERDICT_LEGITIMATE);
    uint32_t lazy = stats_last_call_seq();
    s_epoch = 1793300000;
    stats_record_call(NUM_A, "", VERDICT_LEGITIMATE);
    s_epoch = 0;
    CHECK(calls_test_reboot() && stats_get_call(lazy, &c)
          && c.epoch_s >= 1793300000 - 1 && c.epoch_s <= 1793300000,
          "the next write back-fills without a synced event");
}

static void test_reset_counters(void)
{
    printf("reset counters:\n");
    stats_clear_calls();
    stats_record_call(NUM_A, "", VERDICT_SPAM);
    stats_record_call(NUM_A, "", VERDICT_LEGITIMATE);
    stats_record_call(NUM_A, "", VERDICT_ERROR);
    stats_counters_t c;
    stats_snapshot_counters(&c);
    CHECK(c.total_calls >= 3 && c.spam_blocked >= 1 && c.legitimate >= 1
          && c.errors >= 1, "counters populated");

    stats_reset_counters();
    stats_snapshot_counters(&c);
    CHECK(c.total_calls == 0, "total reset");
    CHECK(c.spam_blocked == 0, "spam reset");
    CHECK(c.legitimate == 0, "legitimate reset");
    CHECK(c.errors == 0, "errors reset");
    stats_calls_info_t info;
    stats_calls_info(&info);
    CHECK(info.count == 3, "call list kept");
    CHECK(call_at(0).verdict == VERDICT_ERROR && call_at(2).verdict == VERDICT_SPAM,
          "listed calls unchanged");

    stats_record_call(NUM_A, "", VERDICT_SPAM);
    stats_snapshot_counters(&c);
    CHECK(c.total_calls == 1 && c.spam_blocked == 1, "counting continues from zero");
}

static void test_clear_persists(void)
{
    printf("clear:\n");
    stats_record_call(NUM_A, "", VERDICT_LEGITIMATE);
    uint32_t last = stats_last_call_seq();
    stats_clear_calls();
    stats_calls_info_t info;
    stats_calls_info(&info);
    CHECK(info.count == 0 && file_size() == STATS_CALLS_FILE_BYTES(STATS_CALL_CAPACITY),
          "cleared: empty");
    CHECK(calls_test_reboot(), "reopened");
    stats_calls_info(&info);
    stats_call_t c;
    CHECK(info.count == 0 && !stats_get_call(last, &c), "still empty after the reboot");
    stats_record_call(NUM_A, "", VERDICT_LEGITIMATE);
    CHECK(stats_last_call_seq() == last + 1, "sequence numbers go on after a clear");
}

// Without an open file (storage not mounted) calls are counted, not listed.
static void test_no_file(void)
{
    printf("no call file:\n");
    stats_calls_close();
    stats_counters_t before, after;
    stats_snapshot_counters(&before);
    s_heard_count = 0;
    stats_set_call_listener(hear);
    stats_record_call(NUM_A, "", VERDICT_LEGITIMATE);
    stats_snapshot_counters(&after);
    stats_call_t c[1];
    CHECK(after.total_calls == before.total_calls + 1 && stats_snapshot_calls(c, 1) == 0
          && stats_last_call_seq() == 0 && s_heard_count == 0,
          "counted, not listed, not announced");
    stats_set_call_listener(NULL);
    CHECK(calls_test_reboot(), "reopened");
}

int main(void)
{
    stats_setup();
    stats_set_clock(test_clock);
    calls_test_open();
    test_api_fail_kind();
    test_api_result_recording();
    test_test_call_not_counted();
    test_set_assessment_all_rows();
    test_assessment_for_number();
    test_contact_flow();
    test_counters_only_not_listed();
    test_call_listener();
    test_reopen();
    test_wrap_around();
    test_updates_persist();
    test_time_backfill();
    test_reset_counters();
    test_clear_persists();
    test_no_file();
    test_damaged_file();
    calls_test_cleanup();
    printf("\n%d checks, %d failed\n", tests_run, tests_failed);
    return tests_failed ? 1 : 0;
}
