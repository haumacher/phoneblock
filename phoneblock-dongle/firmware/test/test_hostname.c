// Host test for hostname.c — the dongle's host name setting (#714).
//
// The name goes out as DHCP option 12 and as the mDNS name `<name>.local`,
// so it has to be one RFC 1123 label: [a-z0-9-], 1..32 characters, no hyphen
// at either end. User input is trimmed and lower-cased; empty input means
// "back to the default". Pure libc.
#include <stdio.h>
#include <string.h>

#include "hostname.h"

static int tests_run, tests_failed;

#define CHECK(cond, what) do {                                  \
    tests_run++;                                                \
    if (cond) printf("  ok: %s\n", (what));                     \
    else { tests_failed++; printf("  FAIL: %s (%s:%d)\n",       \
                                  (what), __FILE__, __LINE__); } \
} while (0)

// Normalize `in`; expect acceptance with result `want`.
static void accepts(const char *in, const char *want, const char *what)
{
    char out[HOSTNAME_CAP] = "garbage";
    bool ok = hostname_normalize(in, out, sizeof(out));
    CHECK(ok && strcmp(out, want) == 0, what);
}

// Normalize `in`; expect refusal and an empty output.
static void refuses(const char *in, const char *what)
{
    char out[HOSTNAME_CAP] = "garbage";
    bool ok = hostname_normalize(in, out, sizeof(out));
    CHECK(!ok && out[0] == '\0', what);
}

int main(void)
{
    printf("hostname_normalize:\n");
    accepts("answerbot", "answerbot", "default name is accepted");
    accepts("Answerbot-S3", "answerbot-s3", "upper case is lowered");
    accepts("  keller-2\t", "keller-2", "surrounding whitespace is dropped");
    accepts("a", "a", "one character is enough");
    accepts("0", "0", "a digit alone is a valid label");
    accepts("", "", "empty means reset to default");
    accepts("   ", "", "whitespace only means reset to default");
    accepts("abcdefghijklmnopqrstuvwxyz012345", "abcdefghijklmnopqrstuvwxyz012345",
            "32 characters are accepted");
    refuses("abcdefghijklmnopqrstuvwxyz0123456", "33 characters are refused");
    refuses("-x", "leading hyphen is refused");
    refuses("x-", "trailing hyphen is refused");
    refuses("a_b", "underscore is refused");
    refuses("a.b", "dot is refused (one label only)");
    refuses("\xc3\xa4", "non-ASCII (umlaut) is refused");
    refuses("a b", "inner space is refused");
    refuses(NULL, "NULL is refused");

    char small[HOSTNAME_CAP - 1];
    CHECK(!hostname_normalize("x", small, sizeof(small)), "too small output buffer is refused");

    printf("hostname_valid:\n");
    CHECK(hostname_valid(HOSTNAME_DEFAULT), "the default is valid");
    CHECK(hostname_valid("answerbot-s3"), "lower-case label is valid");
    CHECK(!hostname_valid("Answerbot"), "stored value must already be lower case");
    CHECK(!hostname_valid(""), "empty is not a stored name");
    CHECK(!hostname_valid(NULL), "NULL is not valid");

    printf("%d/%d checks passed\n", tests_run - tests_failed, tests_run);
    return tests_failed ? 1 : 0;
}
