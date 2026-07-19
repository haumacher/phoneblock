// Host test for sip_response_build() — the SIP response builder split out of
// sip_register.c. The point of interest is memory safety: a received request
// with oversized headers must never overrun the fixed 2048-byte response
// buffer that send_response() allocates on the device.
//
// Build with AddressSanitizer (see Makefile). Before the fix, test_oversized_
// to_with_tag() overruns the heap buffer and ASan aborts; after the fix the
// builder reports -1 (all-or-nothing) and send_response drops the message.

#define _GNU_SOURCE
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "../main/sip_response.h"

// Same size as SIP_TX_BUF_SIZE in sip_register.c.
#define TX_CAP 2048

static int tests_run, tests_failed;
#define CHECK(cond, msg) do {                                   \
    tests_run++;                                                \
    if (!(cond)) { tests_failed++;                              \
        printf("  FAIL: %s (%s:%d)\n", msg, __FILE__, __LINE__);\
    } else printf("  ok: %s\n", msg);                           \
} while (0)

// A normal in-dialog BYE produces a well-formed 200 OK that echoes the
// routing headers unchanged.
static void test_normal_bye(void)
{
    printf("test_normal_bye\n");
    const char *req =
        "BYE sip:620@fritz.box SIP/2.0\r\n"
        "Via: SIP/2.0/UDP 192.168.178.1:5060;branch=z9hG4bKabc\r\n"
        "From: <sip:+491234@fritz.box>;tag=caller99\r\n"
        "To: <sip:620@fritz.box>;tag=dongle01\r\n"
        "Call-ID: 12345@fritz.box\r\n"
        "CSeq: 2 BYE\r\n"
        "\r\n";
    char *tx = malloc(TX_CAP);
    int n = sip_response_build(req, (int)strlen(req), 200, "OK",
                               "dongle01", NULL,
                               "620", "192.168.178.68", 15060, tx, TX_CAP);
    CHECK(n > 0 && n < TX_CAP, "length within buffer");
    CHECK(strncmp(tx, "SIP/2.0 200 OK\r\n", 16) == 0, "status line");
    CHECK(strstr(tx, "Call-ID: 12345@fritz.box\r\n") != NULL, "Call-ID echoed");
    CHECK(strstr(tx, "To: <sip:620@fritz.box>;tag=dongle01\r\n") != NULL,
          "tagged To echoed verbatim");
    CHECK(tx[n] == '\0', "NUL terminated");
    free(tx);
}

// The regression case: a request whose To header ALREADY carries ;tag= (any
// in-dialog request does) and is large enough that the echoed headers exceed
// the 2048-byte buffer. The pre-fix echo_to_with_tag() returned the
// unclamped snprintf length here, inflating the accumulator past the buffer
// end so the following Call-ID/Contact writes ran off the allocation and
// smashed adjacent heap metadata. The builder must now report -1 (drop the
// whole response) rather than write past the buffer or emit a partial one.
static void test_oversized_to_with_tag(void)
{
    printf("test_oversized_to_with_tag\n");
    // ~1900-byte To display name, plus a real ;tag= so the vulnerable branch
    // is taken. Uppercase filler mirrors the field crash signature.
    char big_to[2000];
    int p = 0;
    p += sprintf(big_to + p, "To: \"");
    memset(big_to + p, 'A', 1900); p += 1900;
    p += sprintf(big_to + p, "\" <sip:620@fritz.box>;tag=dongle01");
    big_to[p] = '\0';

    char req[4096];
    int r = snprintf(req, sizeof(req),
        "BYE sip:620@fritz.box SIP/2.0\r\n"
        "Via: SIP/2.0/UDP 192.168.178.1:5060;branch=z9hG4bKabc\r\n"
        "From: <sip:+491234@fritz.box>;tag=caller99\r\n"
        "%s\r\n"
        "Call-ID: aaaaaaaaaaaaaaaaaaaa@fritz.box\r\n"
        "CSeq: 2 BYE\r\n"
        "\r\n", big_to);
    assert(r > 0 && r < (int)sizeof(req));

    char *tx = malloc(TX_CAP);   // ASan-guarded: any OOB write aborts here
    int n = sip_response_build(req, r, 200, "OK", "dongle01", NULL,
                               "620", "192.168.178.68", 15060, tx, TX_CAP);
    // All-or-nothing: the oversized request is reported un-buildable, not
    // written partially. (ASan independently guards against any stray write.)
    CHECK(n < 0, "oversized request dropped (returns -1)");
    free(tx);
}

// Across every buffer size the builder either produces a complete, terminated
// response strictly within cap, or reports -1 — never a partial write, and
// never an out-of-bounds one (ASan-checked). This is the property that would
// break if any append_fmt() result went unchecked and drove the cursor
// negative.
static void test_all_or_nothing_across_sizes(void)
{
    printf("test_all_or_nothing_across_sizes\n");
    const char *req =
        "BYE sip:620@fritz.box SIP/2.0\r\n"
        "Via: SIP/2.0/UDP 192.168.178.1:5060;branch=z9hG4bKabc\r\n"
        "From: <sip:+491234@fritz.box>;tag=caller99\r\n"
        "To: <sip:620@fritz.box>;tag=dongle01\r\n"
        "Call-ID: 12345@fritz.box\r\n"
        "CSeq: 2 BYE\r\n\r\n";
    int first_ok = -1;
    for (int cap = 1; cap <= 400; cap++) {
        char *tx = malloc(cap);
        int n = sip_response_build(req, (int)strlen(req), 200, "OK",
                                   "dongle01", NULL, "620", "h", 5060, tx, cap);
        int ok = (n < 0) || (n > 0 && n < cap && tx[n] == '\0');
        if (!ok) { CHECK(0, "complete-or-dropped"); free(tx); return; }
        if (n >= 0 && first_ok < 0) first_ok = cap;
        free(tx);
    }
    CHECK(first_ok > 0, "some buffer size builds a complete response");
    // Once it fits, larger buffers keep fitting (monotonic), and the smallest
    // fitting size is well under the device's 2048-byte buffer.
    CHECK(first_ok < TX_CAP, "complete response fits well within 2048 bytes");
}

int main(void)
{
    test_normal_bye();
    test_oversized_to_with_tag();
    test_all_or_nothing_across_sizes();
    printf("\n%d checks, %d failed\n", tests_run, tests_failed);
    return tests_failed ? 1 : 0;
}
