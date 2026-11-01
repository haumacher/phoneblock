// Host test for the recent-calls ring in stats.c, as far as the call list's
// row actions depend on it.
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

// Newest-first snapshot entry `i`.
static stats_call_t call_at(int i)
{
    stats_call_t calls[STATS_MAX_CALLS];
    int n = stats_snapshot_calls(calls, STATS_MAX_CALLS);
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

    // Wrap the ring so the newest entry sits at a low index again.
    for (int i = 0; i < STATS_MAX_CALLS - 1; i++)
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
    stats_call_t calls[STATS_MAX_CALLS];
    CHECK(stats_snapshot_calls(calls, STATS_MAX_CALLS) == 0, "not listed");
    CHECK(after.total_calls == before.total_calls + 1, "total counter bumped");
    CHECK(after.legitimate == before.legitimate + 1, "legitimate counter bumped");
}

// Listener for the webhook hook: remembers every sequence number it gets.
static uint32_t s_heard[2 * STATS_MAX_CALLS];
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

    // The ring keeps STATS_MAX_CALLS entries: once that many newer calls
    // came in, the first one is gone.
    uint32_t first = s_heard[0];
    for (int i = 0; i < STATS_MAX_CALLS - 3; i++)
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

int main(void)
{
    stats_setup();
    test_api_fail_kind();
    test_api_result_recording();
    test_test_call_not_counted();
    test_set_assessment_all_rows();
    test_assessment_for_number();
    test_contact_flow();
    test_counters_only_not_listed();
    test_call_listener();
    printf("\n%d checks, %d failed\n", tests_run, tests_failed);
    return tests_failed ? 1 : 0;
}
