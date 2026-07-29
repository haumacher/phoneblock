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

// Expect PHONE_RATEABLE with `in` normalising to `expected` on a line in
// `dial_prefix`'s country.
static void ok_cc(const char *dial_prefix, const char *in, const char *expected)
{
    char out[48];
    strcpy(out, "sentinel");
    phone_class_t cls = phone_normalise(in, out, sizeof(out), dial_prefix);
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

// Shorthand for the German-line cases, which are the bulk of the suite.
static void ok(const char *in, const char *expected)
{
    ok_cc("+49", in, expected);
}

// Expect PHONE_SKIP and an emptied output buffer.
static void skip_cc(const char *dial_prefix, const char *in)
{
    char out[48];
    strcpy(out, "sentinel");
    phone_class_t cls = phone_normalise(in, out, sizeof(out), dial_prefix);
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

static void skip(const char *in)
{
    skip_cc("+49", in);
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

    // --- lines outside Germany (issue #505) ------------------------------
    // The national case is the only one the line's country feeds into;
    // barring entries pushed to /api/rate must carry the right one or the
    // server records a rating against a number nobody called.
    ok_cc("+44", "07520694441", "+447520694441");
    ok_cc("+44", "02071234567", "+442071234567");
    ok_cc("+385", "0912345678", "+385912345678");   // three-digit country code
    // Already international: unaffected by the line's country.
    ok_cc("+44", "+4930123456",   "+4930123456");
    ok_cc("+44", "004930123456",  "+4930123456");
    // A missing/empty prefix must not fabricate a number.
    skip_cc(NULL, "030123456");
    skip_cc("",   "030123456");

    // --- non-German dialling grammars (issue #432) -----------------------
    // Italy / Denmark / Czechia have no trunk prefix: the bare number *is*
    // the national form and a leading zero belongs to it, so unlike the
    // German case below it is rateable rather than ambiguous.
    ok_cc("+39",  "0612345678", "+390612345678");
    ok_cc("+39",  "3331234567", "+393331234567");
    ok_cc("+45",  "12345678",   "+4512345678");
    ok_cc("+420", "212345678",  "+420212345678");
    // NANP: trunk "1", escape "011".
    ok_cc("+1", "16023651873",   "+16023651873");
    ok_cc("+1", "01149301234",   "+49301234");
    // Russia: trunk "8", escape "810".
    ok_cc("+7", "84951234567",   "+74951234567");
    ok_cc("+7", "81049301234",   "+49301234");
    // Australia: the "0011" escape must beat the "0" trunk prefix.
    ok_cc("+61", "00114930123456", "+4930123456");
    ok_cc("+61", "0212345678",     "+61212345678");
    // A bare number in a trunk-prefix country stays ambiguous — these are
    // hand-typed barring entries, so we do not guess (issue #469).
    skip_cc("+49", "30123456");
    skip_cc("+44", "7520694441");

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
