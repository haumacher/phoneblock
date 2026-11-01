// Host test for the status mail's call selection (issue #556): by default the
// call report lists every new call, as before; with "only blocked calls" it
// lists just the ones the dongle took as spam, in their original order.
#include <stdio.h>
#include <string.h>

#include "mail_calls.h"

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
    stats_call_t calls[STATS_MAX_CALLS], orig[STATS_MAX_CALLS];
    int n = mixed(calls);
    memcpy(orig, calls, sizeof(calls));
    CHECK(mail_select_calls(calls, n, false) == n, "every call is kept");
    CHECK(memcmp(calls, orig, sizeof(stats_call_t) * (size_t)n) == 0,
          "list left untouched");
}

static void test_blocked_only(void)
{
    printf("only blocked calls:\n");
    stats_call_t calls[STATS_MAX_CALLS];
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

static void test_new_calls(void)
{
    printf("new calls since the last mail:\n");
    stats_call_t calls[STATS_MAX_CALLS];
    int n = mixed(calls);
    CHECK(mail_count_new_calls(calls, n, 0) == n, "all new after boot");
    CHECK(mail_count_new_calls(calls, n, 650) == 3, "three newer than the mark");
    CHECK(mail_count_new_calls(calls, n, 900) == 0, "none newer than the newest");
    // The daily flush selects within the new run only.
    int fresh = mail_count_new_calls(calls, n, 650);
    CHECK(mail_select_calls(calls, fresh, true) == 1,
          "one blocked call among the new ones");
}

int main(void)
{
    test_is_blocked();
    test_all_calls_by_default();
    test_blocked_only();
    test_new_calls();
    printf("\n%d checks, %d failed\n", tests_run, tests_failed);
    return tests_failed ? 1 : 0;
}
