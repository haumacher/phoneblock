// Host test for name_filter.c — the '|'-separated caller-name patterns the user
// configures to catch calls the Fritz!Box announces under a spam-list name
// (issue #502).
//
// Two things are worth pinning down. First the parsing: the spec is free text
// typed into one form field, so stray separators, whitespace and over-long
// alternatives all have to resolve to something sane — and an alternative that
// gets *truncated* rather than dropped would block more callers than the user
// asked for. Second the matching contract: substring, case-insensitive, and
// nothing else — no wildcards, no anchors. The real names from the issue are
// used as fixtures so the promise made there stays testable. Pure libc.
#include <stdio.h>
#include <string.h>

#include "name_filter.h"

static int tests_run, tests_failed;

#define CHECK(cond, what) do {                                  \
    tests_run++;                                                \
    if (cond) printf("  ok: %s\n", (what));                     \
    else { tests_failed++; printf("  FAIL: %s (%s:%d)\n",       \
                                  (what), __FILE__, __LINE__); } \
} while (0)

// The two labels the reporting user actually sees from their Tellows phonebook.
#define TELLOWS "tellows Score 8 Aggressive advertising"
#define SCORE9  "my Score 9, unbekannt"

// Parse `spec` and assert the resulting patterns joined with '/' equal `want`
// ("" for an empty filter).
static void check_parse(const char *spec, const char *want, const char *what)
{
    name_filter_t f;
    name_filter_parse(spec, &f);

    char got[512] = "";
    for (int i = 0; i < f.count; i++) {
        if (i) strcat(got, "/");
        strcat(got, f.pat[i]);
    }
    if (strcmp(got, want) == 0) {
        tests_run++;
        printf("  ok: %s\n", what);
    } else {
        tests_run++; tests_failed++;
        printf("  FAIL: %s\n    got:  %s\n    want: %s\n", what, got, want);
    }
}

// Assert that `display` matches `spec` (or not), and that the reported hit is
// the alternative named in `want_hit` (NULL for "no match").
static void check_match(const char *spec, const char *display,
                        const char *want_hit, const char *what)
{
    name_filter_t f;
    name_filter_parse(spec, &f);
    const char *hit = name_filter_match(&f, display);

    bool ok = want_hit ? (hit && strcmp(hit, want_hit) == 0) : (hit == NULL);
    tests_run++;
    if (ok) printf("  ok: %s\n", what);
    else {
        tests_failed++;
        printf("  FAIL: %s\n    got:  %s\n    want: %s\n", what,
               hit ? hit : "(no match)", want_hit ? want_hit : "(no match)");
    }
}

static void test_parse_basic(void)
{
    printf("test_parse_basic\n");
    check_parse("tellows", "tellows", "a lone pattern parses unchanged");
    check_parse("  tellows  ", "tellows", "surrounding whitespace trimmed");
    check_parse("tellows|Score|SPAM", "tellows/Score/SPAM",
                "three alternatives split on '|'");
    check_parse(" tellows | Score ", "tellows/Score",
                "whitespace around each alternative trimmed");
    // The separator is the *only* structure; a space inside an alternative is
    // part of the text to look for, not a second alternative.
    check_parse("Score 8", "Score 8", "interior whitespace is significant");
}

static void test_parse_degenerate(void)
{
    printf("test_parse_degenerate\n");
    // The UI trims the field but does not police separators, so all of these
    // reach the parser. count == 0 is how the caller reads "filter off".
    check_parse("tellows|", "tellows", "trailing separator ignored");
    check_parse("|tellows", "tellows", "leading separator ignored");
    check_parse("tellows||Score", "tellows/Score", "empty alternative skipped");
    check_parse("", "", "empty spec yields no pattern");
    check_parse(" | | ", "", "separators and blanks only yield no pattern");
    check_parse(NULL, "", "NULL spec yields no pattern");

    name_filter_t f;
    CHECK(name_filter_parse("tellows|Score", &f) == 2, "parse returns the count");
    CHECK(!f.dropped, "a well-formed spec drops nothing");
}

static void test_parse_limits(void)
{
    printf("test_parse_limits\n");
    // Over NAME_FILTER_MAX: the excess is dropped, and the caller can tell.
    char many[NAME_FILTER_SPEC_CAP * 2] = "";
    for (int i = 0; i < NAME_FILTER_MAX + 2; i++) {
        char one[16];
        snprintf(one, sizeof(one), "%sp%d", i ? "|" : "", i);
        strcat(many, one);
    }
    name_filter_t f;
    CHECK(name_filter_parse(many, &f) == NAME_FILTER_MAX,
          "list capped at NAME_FILTER_MAX");
    CHECK(f.dropped, "exceeding the cap is flagged");

    // A pattern longer than the per-entry capacity is dropped whole. Clipping
    // it would leave a shorter — hence broader — pattern in force.
    char spec[NAME_FILTER_PATTERN_CAP + 64];
    char lng[NAME_FILTER_PATTERN_CAP + 8];
    memset(lng, 'x', sizeof(lng) - 1);
    lng[sizeof(lng) - 1] = '\0';
    snprintf(spec, sizeof(spec), "%s|Score", lng);
    CHECK(name_filter_parse(spec, &f) == 1, "over-long pattern dropped, rest kept");
    CHECK(strcmp(f.pat[0], "Score") == 0, "the surviving pattern is the short one");
    CHECK(f.dropped, "dropping an over-long pattern is flagged");
}

static void test_match_issue_names(void)
{
    printf("test_match_issue_names\n");
    // The cases the feature exists for: one spec covering both Tellows label
    // shapes the user reported.
    check_match("tellows|Score", TELLOWS, "tellows",
                "Tellows label caught by the vendor name");
    check_match("tellows|Score", SCORE9, "Score",
                "'my Score 9, unbekannt' caught by the score marker");
    // The other half of the promise: a self-made phonebook whose entries are
    // all named SPAM.
    check_match("SPAM", "SPAM", "SPAM", "a name that is exactly the pattern");
    check_match("SPAM", "SPAM: 0123456789", "SPAM", "the Fritz!Box 'SPAM:' marker");
}

static void test_match_semantics(void)
{
    printf("test_match_semantics\n");
    // Substring, anywhere in the name.
    check_match("Score", "Score first", "Score", "match at the start");
    check_match("Score", "a Score inside", "Score", "match in the middle");
    check_match("Score", "trailing Score", "Score", "match at the end");

    // Case-insensitive in both directions.
    check_match("tellows", "TELLOWS GmbH", "tellows", "lowercase pattern, upper name");
    check_match("TELLOWS", "tellows GmbH", "TELLOWS", "uppercase pattern, lower name");
    check_match("ScOrE", "my score 9", "ScOrE", "mixed case on both sides");

    // No metacharacters: '*' is literal text, so it only matches a literal '*'.
    check_match("Score*", SCORE9, NULL, "'*' is not a wildcard");
    check_match("*", "any name", NULL, "a lone '*' matches nothing by itself");
    check_match("*", "star * name", "*", "'*' matches a literal asterisk");

    // Misses.
    check_match("tellows", "Familie Meier", NULL, "unrelated name does not match");
    check_match("Score", "", NULL, "empty display name never matches");
    check_match("Score", NULL, NULL, "NULL display name never matches");
    check_match("", "Score 9", NULL, "empty filter never matches");
    // The first matching alternative wins — that is the one reported in the log.
    check_match("Aggressive|tellows", TELLOWS, "Aggressive",
                "the first matching alternative is reported");
}

int main(void)
{
    test_parse_basic();
    test_parse_degenerate();
    test_parse_limits();
    test_match_issue_names();
    test_match_semantics();

    printf("\n%d checks, %d failed\n", tests_run, tests_failed);
    return tests_failed ? 1 : 0;
}
