// Host test for the status mail's call selection (issue #556): by default the
// call report lists every new call, as before; with "only blocked calls" it
// lists just the ones the dongle took as spam, in their original order. The
// calls since the last mail come from the persistent call list (#589): they
// survive a reboot, are mailed once, and calls that dropped off a full list
// are counted.
#include <stdio.h>
#include <string.h>

#include "mail_calls.h"
#include "calls_test_util.h"

static int tests_run, tests_failed;

#define CHECK(cond, what) do {                                  \
    tests_run++;                                                \
    if (cond) printf("  ok: %s\n", (what));                     \
    else { tests_failed++; printf("  FAIL: %s (%s:%d)\n",       \
                                  (what), __FILE__, __LINE__); } \
} while (0)

static stats_call_t make(const char *number, int64_t at_us, verdict_t v,
                         pb_assessment_t a)
{
    stats_call_t c;
    memset(&c, 0, sizeof(c));
    snprintf(c.number, sizeof(c.number), "%s", number);
    c.at_us = at_us;
    c.verdict = v;
    c.assessment = a;
    return c;
}

// A mixed, newest-first list like stats_snapshot_calls() returns.
static int mixed(stats_call_t *out)
{
    int n = 0;
    out[n++] = make("+4930000001", 900, VERDICT_SPAM,       PB_ASSESS_SPAM);
    out[n++] = make("+4930000002", 800, VERDICT_LEGITIMATE, PB_ASSESS_LEGITIMATE);
    out[n++] = make("+4930000003", 700, VERDICT_LEGITIMATE, PB_ASSESS_UNKNOWN);
    out[n++] = make("+4930000004", 600, VERDICT_SPAM,       PB_ASSESS_SPAM_LIST);
    out[n++] = make("+4930000005", 500, VERDICT_LEGITIMATE, PB_ASSESS_SUSPECT);
    out[n++] = make("+4930000006", 400, VERDICT_ERROR,      PB_ASSESS_ERROR);
    out[n++] = make("+4930000007", 300, VERDICT_SPAM,       PB_ASSESS_BLACKLIST);
    out[n++] = make("+4930000008", 200, VERDICT_SPAM,       PB_ASSESS_NAME_PATTERN);
    return n;
}

static void test_is_blocked(void)
{
    printf("blocked calls:\n");
    stats_call_t c = make("+4930000001", 1, VERDICT_SPAM, PB_ASSESS_SPAM);
    CHECK(mail_call_is_blocked(&c), "community spam is blocked");
    c = make("+4930000001", 1, VERDICT_SPAM, PB_ASSESS_BLACKLIST);
    CHECK(mail_call_is_blocked(&c), "blacklist is blocked");
    c = make("+4930000001", 1, VERDICT_SPAM, PB_ASSESS_NAME_PATTERN);
    CHECK(mail_call_is_blocked(&c), "name pattern is blocked");
    c = make("+4930000001", 1, VERDICT_LEGITIMATE, PB_ASSESS_SUSPECT);
    CHECK(!mail_call_is_blocked(&c), "suspect below threshold rings through");
    c = make("+4930000001", 1, VERDICT_LEGITIMATE, PB_ASSESS_UNKNOWN);
    CHECK(!mail_call_is_blocked(&c), "unknown caller rings through");
    c = make("+4930000001", 1, VERDICT_ERROR, PB_ASSESS_ERROR);
    CHECK(!mail_call_is_blocked(&c), "failed lookup rings through");
}

static void test_all_calls_by_default(void)
{
    printf("default (all calls):\n");
    stats_call_t calls[8], orig[8];
    int n = mixed(calls);
    memcpy(orig, calls, sizeof(calls));
    CHECK(mail_select_calls(calls, n, false) == n, "every call is kept");
    CHECK(memcmp(calls, orig, sizeof(stats_call_t) * (size_t)n) == 0,
          "list left untouched");
}

static void test_blocked_only(void)
{
    printf("only blocked calls:\n");
    stats_call_t calls[8];
    int n = mixed(calls);
    int kept = mail_select_calls(calls, n, true);
    CHECK(kept == 4, "four blocked calls kept");
    CHECK(strcmp(calls[0].number, "+4930000001") == 0
          && strcmp(calls[1].number, "+4930000004") == 0
          && strcmp(calls[2].number, "+4930000007") == 0
          && strcmp(calls[3].number, "+4930000008") == 0,
          "blocked calls in their original (newest-first) order");
    bool all_blocked = true;
    for (int i = 0; i < kept; i++) all_blocked &= mail_call_is_blocked(&calls[i]);
    CHECK(all_blocked, "nothing but blocked calls");

    stats_call_t legit[2] = {
        make("+4930000002", 800, VERDICT_LEGITIMATE, PB_ASSESS_LEGITIMATE),
        make("+4930000003", 700, VERDICT_LEGITIMATE, PB_ASSESS_UNKNOWN),
    };
    CHECK(mail_select_calls(legit, 2, true) == 0,
          "only legitimate calls: nothing to report");
    CHECK(mail_select_calls(legit, 0, true) == 0, "empty list");
}

// Records the calls of mixed() into the call list, oldest first, so the list
// reads like mixed() newest first.
static void record_mixed(void)
{
    stats_call_t calls[8];
    int n = mixed(calls);
    for (int i = n - 1; i >= 0; i--) {
        stats_record_call_assessed(calls[i].number, "", calls[i].verdict,
                                   calls[i].assessment);
    }
}

// All calls of the window, read in pages of `page` calls.
static int read_all(const mail_calls_window_t *w, int page, stats_call_t *out, int max)
{
    uint32_t cursor = 0;
    int total = 0, n;
    while (total < max
           && (n = mail_calls_read(w, &cursor, out + total,
                                   page < max - total ? page : max - total)) > 0) {
        total += n;
    }
    return total;
}

static void test_new_calls(void)
{
    printf("new calls since the last mail:\n");
    stats_clear_calls();
    mail_calls_window_t w;
    mail_calls_window(false, false, &w);
    CHECK(w.new_calls == 0 && w.through_seq == 0, "empty list: nothing new");

    record_mixed();
    mail_calls_window(false, false, &w);
    CHECK(w.new_calls == 8 && w.not_listed == 0, "all new before the first mail");
    mail_calls_window(true, false, &w);
    CHECK(w.new_calls == 4, "four of them blocked");
    stats_call_t got[8];
    CHECK(read_all(&w, 3, got, 8) == 4 && strcmp(got[0].number, "+4930000001") == 0
          && strcmp(got[3].number, "+4930000008") == 0,
          "blocked calls read newest first across pages");

    mail_calls_mark_sent(&w);
    mail_calls_window(false, false, &w);
    CHECK(w.new_calls == 0 && w.through_seq == 0, "after the mail nothing is new");

    stats_record_call("+4930000011", "", VERDICT_LEGITIMATE);
    stats_record_call("+4930000012", "", VERDICT_SPAM);
    stats_record_call("+4930000013", "", VERDICT_LEGITIMATE);
    mail_calls_window(false, false, &w);
    CHECK(w.new_calls == 3 && w.not_listed == 0, "three newer than the mark");
    CHECK(read_all(&w, 8, got, 8) == 3 && strcmp(got[0].number, "+4930000013") == 0
          && strcmp(got[2].number, "+4930000011") == 0, "exactly those three");
    mail_calls_window(true, false, &w);
    CHECK(w.new_calls == 1 && read_all(&w, 8, got, 8) == 1
          && strcmp(got[0].number, "+4930000012") == 0,
          "one blocked call among the new ones");

    // A reboot before the mail: still to be reported, then reported once.
    CHECK(calls_test_reboot(), "reopened");
    mail_calls_window(false, false, &w);
    CHECK(w.new_calls == 3, "unreported calls survive a reboot");
    mail_calls_mark_sent(&w);
    CHECK(calls_test_reboot(), "reopened again");
    mail_calls_window(false, false, &w);
    CHECK(w.new_calls == 0, "and are mailed once");

    // The test mail lists the whole list, whatever was mailed.
    mail_calls_window(false, true, &w);
    CHECK(w.new_calls == 11 && w.not_listed == 0, "test mail: every listed call");
}

static void test_more_than_capacity(void)
{
    printf("more new calls than the list holds:\n");
    stats_clear_calls();
    mail_calls_window_t w;
    mail_calls_window(false, false, &w);
    mail_calls_mark_sent(&w);
    char num[24];
    int total = STATS_CALL_CAPACITY + 5;
    for (int i = 0; i < total; i++) {
        snprintf(num, sizeof(num), "+4930123%05d", i);
        stats_record_call(num, "", i % 2 ? VERDICT_SPAM : VERDICT_LEGITIMATE);
    }
    mail_calls_window(false, false, &w);
    CHECK(w.new_calls == STATS_CALL_CAPACITY && w.not_listed == 5,
          "capacity listed, 5 older not listed");
    mail_calls_window(true, false, &w);
    CHECK(w.new_calls == STATS_CALL_CAPACITY / 2 && w.not_listed == 5,
          "blocked only: the filter still applies, the count of the missing too");
    mail_calls_mark_sent(&w);

    // A cleared list: its calls are neither reported nor missing.
    stats_record_call("+4930000021", "", VERDICT_LEGITIMATE);
    stats_clear_calls();
    stats_record_call("+4930000022", "", VERDICT_LEGITIMATE);
    mail_calls_window(false, false, &w);
    CHECK(w.new_calls == 1 && w.not_listed == 0, "cleared calls are not counted as missing");
}

int main(void)
{
    stats_setup();
    calls_test_open();
    test_is_blocked();
    test_all_calls_by_default();
    test_blocked_only();
    test_new_calls();
    test_more_than_capacity();
    calls_test_cleanup();
    printf("\n%d checks, %d failed\n", tests_run, tests_failed);
    return tests_failed ? 1 : 0;
}
