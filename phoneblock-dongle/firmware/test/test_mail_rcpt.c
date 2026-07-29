// Host test for mail_rcpt.c — parsing the ';'-separated status-mail recipient
// list stored by the web UI, and rendering it as an RFC 5322 "To:" value.
//
// The parsing is the part worth pinning down: the stored spec is free text a
// user pasted, so trailing separators, stray whitespace and over-long entries
// all have to resolve to something sane. An entry that is silently truncated
// instead of dropped would deliver the household's status mail to a different
// mailbox, so that case gets its own check. Pure libc.
#include <stdio.h>
#include <string.h>

#include "mail_rcpt.h"

static int tests_run, tests_failed;

#define CHECK(cond, what) do {                                  \
    tests_run++;                                                \
    if (cond) printf("  ok: %s\n", (what));                     \
    else { tests_failed++; printf("  FAIL: %s (%s:%d)\n",       \
                                  (what), __FILE__, __LINE__); } \
} while (0)

// Parse `spec` and assert the resulting addresses joined with '|' equal
// `want` ("" for an empty list).
static void check_parse(const char *spec, const char *want, const char *what)
{
    mail_rcpt_list_t l;
    mail_rcpt_parse(spec, &l);

    char got[512] = "";
    for (int i = 0; i < l.count; i++) {
        if (i) strcat(got, "|");
        strcat(got, l.addr[i]);
    }
    if (strcmp(got, want) == 0) {
        tests_run++;
        printf("  ok: %s\n", what);
    } else {
        tests_run++; tests_failed++;
        printf("  FAIL: %s\n    got:  %s\n    want: %s\n", what, got, want);
    }
}

static void test_single(void)
{
    printf("test_single\n");
    check_parse("a@b.de", "a@b.de", "a lone address parses unchanged");
    check_parse("  a@b.de  ", "a@b.de", "surrounding whitespace trimmed");
}

static void test_list(void)
{
    printf("test_list\n");
    check_parse("a@b.de;c@d.de", "a@b.de|c@d.de", "two addresses split on ';'");
    check_parse(" a@b.de ; c@d.de ", "a@b.de|c@d.de",
                "whitespace around each entry trimmed");
    check_parse("a@b.de;c@d.de;e@f.de", "a@b.de|c@d.de|e@f.de",
                "three addresses");
}

static void test_degenerate(void)
{
    printf("test_degenerate\n");
    // The UI trims the field but does not police separators, so all of these
    // reach the parser. Each must yield the addresses actually present —
    // mail_configured() reads count == 0 as "no recipient set".
    check_parse("a@b.de;", "a@b.de", "trailing separator ignored");
    check_parse(";a@b.de", "a@b.de", "leading separator ignored");
    check_parse("a@b.de;;c@d.de", "a@b.de|c@d.de", "empty entry skipped");
    check_parse("", "", "empty spec yields no recipient");
    check_parse(" ; ; ", "", "separators and blanks only yield no recipient");
    check_parse(NULL, "", "NULL spec yields no recipient");

    mail_rcpt_list_t l;
    CHECK(mail_rcpt_parse("a@b.de;c@d.de", &l) == 2, "parse returns the count");
    CHECK(!l.dropped, "a well-formed list drops nothing");
}

static void test_limits(void)
{
    printf("test_limits\n");
    // Over MAIL_RCPT_MAX: the excess is dropped, and the caller can tell.
    char many[512] = "";
    for (int i = 0; i < MAIL_RCPT_MAX + 2; i++) {
        char one[32];
        snprintf(one, sizeof(one), "%sa%d@b.de", i ? ";" : "", i);
        strcat(many, one);
    }
    mail_rcpt_list_t l;
    CHECK(mail_rcpt_parse(many, &l) == MAIL_RCPT_MAX, "list capped at MAIL_RCPT_MAX");
    CHECK(l.dropped, "exceeding the cap is flagged");

    // An address longer than the per-entry capacity is dropped whole, never
    // clipped to a valid-looking but different address.
    char spec[MAIL_RCPT_ADDR_CAP + 64];
    char local[MAIL_RCPT_ADDR_CAP + 8];
    memset(local, 'x', sizeof(local) - 1);
    local[sizeof(local) - 1] = '\0';
    snprintf(spec, sizeof(spec), "%s@b.de;c@d.de", local);
    CHECK(mail_rcpt_parse(spec, &l) == 1, "over-long entry dropped, rest kept");
    CHECK(strcmp(l.addr[0], "c@d.de") == 0, "the surviving entry is the short one");
    CHECK(l.dropped, "dropping an over-long entry is flagged");
}

static void test_header(void)
{
    printf("test_header\n");
    mail_rcpt_list_t l;
    char hdr[256];

    mail_rcpt_parse("a@b.de", &l);
    CHECK(mail_rcpt_header(&l, hdr, sizeof(hdr)), "single: fits");
    CHECK(strcmp(hdr, "<a@b.de>") == 0, "single address angle-bracketed");

    // RFC 5322 §3.4 separates address-list members with ',' — never the ';'
    // the stored spec uses; a semicolon there would make the header invalid.
    mail_rcpt_parse("a@b.de;c@d.de", &l);
    CHECK(mail_rcpt_header(&l, hdr, sizeof(hdr)), "list: fits");
    CHECK(strcmp(hdr, "<a@b.de>, <c@d.de>") == 0, "list is comma-separated");

    mail_rcpt_parse("", &l);
    CHECK(mail_rcpt_header(&l, hdr, sizeof(hdr)), "empty list: fits");
    CHECK(hdr[0] == '\0', "empty list yields an empty header value");

    // A too-small buffer must report truncation and still leave a valid
    // C string (the caller drops the mail rather than send a broken header).
    char tiny[8];
    mail_rcpt_parse("a@b.de;c@d.de", &l);
    CHECK(!mail_rcpt_header(&l, tiny, sizeof(tiny)), "overflow reported");
    CHECK(strlen(tiny) < sizeof(tiny), "truncated header stays NUL-terminated");
}

int main(void)
{
    test_single();
    test_list();
    test_degenerate();
    test_limits();
    test_header();

    printf("\n%d checks, %d failed\n", tests_run, tests_failed);
    return tests_failed ? 1 : 0;
}
