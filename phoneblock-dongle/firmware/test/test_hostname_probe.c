// Probe for #714: inputs the hostname tests don't spell out — the shapes a
// user actually types into the System field.
#include <stdio.h>
#include <string.h>

#include "hostname.h"

static int s_tests, s_failures;
#define CHECK(c) do { s_tests++; if (!(c)) { s_failures++; \
    fprintf(stderr, "%s:%d: CHECK(%s) failed\n", __FILE__, __LINE__, #c); } } while (0)

static const char *norm(const char *in, bool *ok)
{
    static char out[HOSTNAME_CAP + 8];
    *ok = hostname_normalize(in, out, HOSTNAME_CAP);
    return out;
}

int main(void)
{
    bool ok;
    // Copied from the Fritz!Box list with a trailing newline and tab.
    CHECK(strcmp(norm("\tAnswerBot-Flur\n", &ok), "answerbot-flur") == 0 && ok);
    // RFC 1123 allows an all-digit label and punycode labels.
    CHECK(strcmp(norm("2", &ok), "2") == 0 && ok);
    CHECK(strcmp(norm("xn--flr-0ra", &ok), "xn--flr-0ra") == 0 && ok);
    // A lone hyphen, embedded blank, the full "answerbot.local", a URL.
    norm("-", &ok);                    CHECK(!ok);
    norm("answer bot", &ok);           CHECK(!ok);
    norm("answerbot.local", &ok);      CHECK(!ok);
    norm("http://answerbot", &ok);     CHECK(!ok);
    // Only whitespace = reset to the default.
    CHECK(strcmp(norm("   ", &ok), "") == 0 && ok);
    // 32 after trimming is fine even if the raw input was longer.
    CHECK(strcmp(norm("  abcdefghijklmnopqrstuvwxyz012345  ", &ok),
                  "abcdefghijklmnopqrstuvwxyz012345") == 0 && ok);
    // A failed normalize leaves no half value behind.
    CHECK(strcmp(norm("a_b", &ok), "") == 0 && !ok);
    // What is stored round-trips through the validator used on read.
    CHECK(hostname_valid(norm("PhoneBlock-2", &ok)) && ok);
    CHECK(!hostname_valid("answerbot ") && !hostname_valid(""));

    printf("test_hostname_probe: %d tests, %d failures\n", s_tests, s_failures);
    return s_failures ? 1 : 0;
}
