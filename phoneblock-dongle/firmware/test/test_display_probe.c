// Probe for the T-Dongle-S3 caller screen (#628), composing it with what the
// Fritz!Box and the rest of the firmware actually deliver:
//
//   * unknown callers whose display name is the number itself, in the
//     formats the box uses (national, international, with separators) — the
//     screen must keep the rating, not switch to the "name" layout;
//   * a phone-book name on a call the caller-name filter or the blacklist
//     blocked — the name shows, and so does the red SPAM band;
//   * names the font cannot draw (emoji) and an anonymous caller;
//   * re-rating a listed call through the call list (stats_set_assessment)
//     and naming it (stats_set_display) — badge and screen follow.
//
// Synthetic numbers only.
#include <stdio.h>
#include <string.h>

#include "display_model.h"
#include "stats.h"
#include "calls_test_util.h"

static int tests_run, tests_failed;

#define CHECK(cond, what) do {                                  \
    tests_run++;                                                \
    if (cond) printf("  ok: %s\n", (what));                     \
    else { tests_failed++; printf("  FAIL: %s (%s:%d)\n",       \
                                  (what), __FILE__, __LINE__); } \
} while (0)

static stats_call_t s_calls[DM_BADGE_CALLS];

static int badge(void)
{
    int n = stats_snapshot_calls(s_calls, DM_BADGE_CALLS);
    return dm_count_listed_spam(s_calls, n);
}

static void newest_screen(dm_call_screen_t *out)
{
    int n = stats_snapshot_calls(s_calls, 1);
    if (n == 0) { memset(out, 0, sizeof(*out)); return; }
    dm_call_screen(&s_calls[0], out);
}

static void checked(const char *number, const char *display,
                    pb_assessment_t a, int votes)
{
    pb_check_result_t r;
    memset(&r, 0, sizeof(r));
    r.verdict = (a == PB_ASSESS_SPAM || a == PB_ASSESS_SPAM_LIST
                 || a == PB_ASSESS_BLACKLIST) ? VERDICT_SPAM : VERDICT_LEGITIMATE;
    r.assessment = a;
    r.direct_votes = votes;
    stats_record_call_checked(number, display, &r);
}

static void number_as_name(const char *display)
{
    char what[128];
    stats_clear_calls();
    checked("+4930111222", display, PB_ASSESS_SPAM, 7);
    dm_call_screen_t s;
    newest_screen(&s);
    snprintf(what, sizeof(what), "display name '%s' is not a name", display);
    CHECK(!s.named, what);
    snprintf(what, sizeof(what), "display name '%s' keeps the SPAM class", display);
    CHECK(s.cls == DM_CLASS_SPAM, what);
}

int main(void)
{
    stats_setup();
    calls_test_open();

    printf("number sent as display name\n");
    number_as_name("030111222");
    number_as_name("+4930111222");
    number_as_name("004930111222");
    number_as_name("030 111222");
    number_as_name("030/111-222");
    number_as_name("+49 (30) 111222");

    printf("phone-book name on a blocked call\n");
    {
        stats_clear_calls();
        stats_record_call_assessed("+4930333444", "Werbung Gewinnspiel",
                                   VERDICT_SPAM, PB_ASSESS_NAME_PATTERN);
        dm_call_screen_t s;
        newest_screen(&s);
        CHECK(s.named, "name-pattern block shows the name");
        CHECK(s.cls == DM_CLASS_SPAM, "name-pattern block is red");
        CHECK(strstr(s.band.text, "SPAM") != NULL, "band says SPAM");
        CHECK(badge() == 1, "name-pattern block counts for the badge");

        checked("+4930333555", "Ex-Kollege", PB_ASSESS_BLACKLIST, 0);
        newest_screen(&s);
        CHECK(s.named && s.cls == DM_CLASS_SPAM, "blacklisted contact: name + red");
        CHECK(badge() == 2, "blacklist counts for the badge");
    }

    printf("names the font cannot draw, anonymous caller\n");
    {
        stats_clear_calls();
        stats_record_call("+4930555666", "Oma \xF0\x9F\x91\xB5", VERDICT_LEGITIMATE);
        dm_call_screen_t s;
        newest_screen(&s);
        CHECK(!s.named, "emoji name falls back to the number");
        CHECK(strcmp(s.main.text, "+4930555666") == 0, "number shown instead");

        stats_record_call("anonymous", "", VERDICT_LEGITIMATE);
        newest_screen(&s);
        CHECK(s.main.font != NULL && s.main.text[0] != '\0',
              "anonymous caller shows something");
        CHECK(s.main.width <= DM_TEXT_MAX_PX, "anonymous fits the panel");
    }

    printf("re-rating and naming from the call list\n");
    {
        stats_clear_calls();
        checked("+4930777888", "", PB_ASSESS_SPAM, 3);
        checked("+4930777888", "", PB_ASSESS_SPAM, 3);
        checked("+4930777999", "", PB_ASSESS_SPAM_LIST, 0);
        CHECK(badge() == 3, "two calls of one number count twice, like the list");

        CHECK(stats_set_assessment("+4930777888", PB_ASSESS_LEGITIMATE) == 2,
              "re-rate both entries as legitimate");
        CHECK(badge() == 1, "badge drops both re-rated entries");

        CHECK(stats_set_assessment("+4930777888", PB_ASSESS_BLACKLIST) == 2,
              "re-rate both as blacklisted");
        CHECK(badge() == 3, "badge counts them again");

        CHECK(stats_set_display("+4930777999", "Callcenter X") == 1,
              "name the list-cache spammer");
        dm_call_screen_t s;
        newest_screen(&s);
        CHECK(s.named && s.cls == DM_CLASS_SPAM, "named spammer stays red");
        CHECK(s.votes[0] == '\0', "list-cache hit shows no vote count");

        stats_clear_calls();
        CHECK(badge() == 0, "clearing the list clears the badge");
    }

    printf("\n%d tests, %d failures\n", tests_run, tests_failed);
    calls_test_cleanup();
    return tests_failed ? 1 : 0;
}
