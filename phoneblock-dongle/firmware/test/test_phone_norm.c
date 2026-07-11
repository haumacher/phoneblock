// Host-side tests for the call-barring phone normaliser (phone_norm.c).
// No ESP-IDF dependencies — plain gcc build, see Makefile.
//
// Guards issue #469: wildcards and non-E.164 barring entries must be
// classified PHONE_SKIP so they are never forwarded to /api/rate.

#include <stdio.h>
#include <string.h>

#include "phone_norm.h"

static int failures = 0;

#define CHECK(cond) do {                                                  \
    if (!(cond)) {                                                        \
        fprintf(stderr, "FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond);   \
        failures++;                                                       \
    }                                                                     \
} while (0)

// Expect PHONE_RATEABLE with `in` normalising to `expected`.
static void ok(const char *in, const char *expected)
{
    char out[48];
    strcpy(out, "sentinel");
    phone_class_t cls = phone_normalise(in, out, sizeof(out));
    if (cls != PHONE_RATEABLE) {
        fprintf(stderr, "FAIL %s: expected RATEABLE, got SKIP\n", in);
        failures++;
        return;
    }
    if (strcmp(out, expected) != 0) {
        fprintf(stderr, "FAIL %s: got \"%s\", expected \"%s\"\n",
                in, out, expected);
        failures++;
    }
}

// Expect PHONE_SKIP and an emptied output buffer.
static void skip(const char *in)
{
    char out[48];
    strcpy(out, "sentinel");
    phone_class_t cls = phone_normalise(in, out, sizeof(out));
    if (cls != PHONE_SKIP) {
        fprintf(stderr, "FAIL %s: expected SKIP, got RATEABLE (\"%s\")\n",
                in, out);
        failures++;
        return;
    }
    if (out[0] != '\0') {
        fprintf(stderr, "FAIL %s: SKIP must empty out, got \"%s\"\n", in, out);
        failures++;
    }
}

int main(void)
{
    // --- normalisation of valid entries ---------------------------------
    ok("+4930123456", "+4930123456");   // already E.164, passed through
    ok("004930123456", "+4930123456");  // 00 international prefix → +
    ok("030123456", "+4930123456");     // leading-zero German national → +49
    // Real-world numbers from the tr064_parse fixtures.
    ok("069200940084", "+4969200940084");
    ok("030330759014", "+4930330759014");

    // --- wildcards (issue #469) -----------------------------------------
    skip("+43*");    // trailing wildcard on a country code
    skip("+8*");
    skip("08*");     // national wildcard
    skip("*");       // bare wildcard

    // --- bare non-E.164 numbers (issue #469) ----------------------------
    skip("1727905225");   // no +/0 prefix — no country context
    skip("2166123456");
    skip("800123456");

    // --- malformed / empty ----------------------------------------------
    skip("");
    skip(NULL);
    skip("+");            // '+' with no digits
    skip("00");           // "00" with no digits
    skip("0");            // "0" with no digits
    skip("+49 30 123");   // spaces are not digits
    skip("0049abc");      // non-digit tail

    if (failures) {
        fprintf(stderr, "%d test(s) failed\n", failures);
        return 1;
    }
    printf("OK — all phone_norm tests passed\n");
    return 0;
}
