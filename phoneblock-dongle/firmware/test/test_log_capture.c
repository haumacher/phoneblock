// Host-side unit tests for main/log_capture.{c,h} parsing.
//
// The log hook decides what reaches the web UI's log panel by parsing
// ESP-IDF's formatted log lines (level letter + tag + message), so the
// parsing has to survive the colour escapes, the timestamp variants and
// messages that themselves contain ": ". Pin all of that here, without
// an ESP-IDF build.

#include <stdio.h>
#include <string.h>

#include "log_capture.h"

static int g_tests = 0;
static int g_failures = 0;

#define CHECK_CHAR(name, expected, got) do {                         \
    g_tests++;                                                       \
    if ((char)(expected) != (char)(got)) {                          \
        fprintf(stderr, "FAIL %s: expected '%c'(%d) got '%c'(%d)\n", \
                name, (expected) ? (expected) : '0', (int)(expected),\
                (got) ? (got) : '0', (int)(got));                    \
        g_failures++;                                                \
    }                                                                \
} while (0)

#define CHECK_STR(name, expected, got) do {                          \
    g_tests++;                                                       \
    if (strcmp((expected), (got)) != 0) {                            \
        fprintf(stderr, "FAIL %s: expected <<<%s>>> got <<<%s>>>\n", \
                name, (expected), (got));                            \
        g_failures++;                                                \
    }                                                                \
} while (0)

// Convenience: run the parser and check level+tag+message in one go.
static void expect(const char *name, const char *line,
                   char want_level, const char *want_tag, const char *want_msg)
{
    char tag[16] = "?";
    char msg[128] = "?";
    char lvl = log_capture_parse(line, tag, sizeof(tag), msg, sizeof(msg));
    char nbuf[128];
    snprintf(nbuf, sizeof(nbuf), "%s/level", name);
    CHECK_CHAR(nbuf, want_level, lvl);
    if (want_level) {
        snprintf(nbuf, sizeof(nbuf), "%s/tag", name);
        CHECK_STR(nbuf, want_tag, tag);
        snprintf(nbuf, sizeof(nbuf), "%s/msg", name);
        CHECK_STR(nbuf, want_msg, msg);
    }
}

// ESP-IDF colour prefixes (CONFIG_LOG_COLORS=y).
#define RED    "\033[0;31m"
#define YELLOW "\033[0;33m"
#define GREEN  "\033[0;32m"
#define RESET  "\033[0m"

int main(void)
{
    // --- log_capture_level: cheap pre-filter on the format head -------
    CHECK_CHAR("level/E-colour", 'E', log_capture_level(RED "E (1) t: m" RESET "\n"));
    CHECK_CHAR("level/W-colour", 'W', log_capture_level(YELLOW "W (1) t: m" RESET "\n"));
    CHECK_CHAR("level/I-colour", 'I', log_capture_level(GREEN "I (1) t: m" RESET "\n"));
    CHECK_CHAR("level/E-plain",  'E', log_capture_level("E (1) t: m\n"));
    CHECK_CHAR("level/garbage",  '\0', log_capture_level("not a log line"));
    CHECK_CHAR("level/null",     '\0', log_capture_level(NULL));

    // --- typical error / warning lines --------------------------------
    expect("err-colour", RED "E (12345) sip: REGISTER failed" RESET "\n",
           'E', "sip", "REGISTER failed");
    expect("warn-colour", YELLOW "W (678) wifi: weak signal" RESET "\n",
           'W', "wifi", "weak signal");

    // Info still parses (the hook just won't act on it).
    expect("info-colour", GREEN "I (1) main: booting" RESET "\n",
           'I', "main", "booting");

    // No colour escape (e.g. CONFIG_LOG_COLORS off).
    expect("err-plain", "E (5) api: HTTP 500\n",
           'E', "api", "HTTP 500");

    // Library tags are kept too — no allowlist filtering: the esp-tls /
    // mbedtls detail is exactly what we want on the panel.
    expect("lib-tag", RED "E (9) esp-tls: handshake failed -0x2700" RESET "\n",
           'E', "esp-tls", "handshake failed -0x2700");

    // Message that itself contains ": " — split on the FIRST one, so the
    // tag is just the tag and the colon stays in the message.
    expect("colon-in-msg", RED "E (9) sip: connect to host: refused" RESET "\n",
           'E', "sip", "connect to host: refused");

    // Alternative timestamp format (CONFIG_LOG_TIMESTAMP_SOURCE_SYSTEM)
    // still ends the prefix at ") ".
    expect("ts-clock", "W (00:00:01.234) tr064: GetState slow\n",
           'W', "tr064", "GetState slow");

    // Unparseable lines (no "tag: msg" shape) yield no level.
    expect("no-colon", "E (5) just a banner line\n", '\0', "", "");
    expect("garbage", "random text without structure", '\0', "", "");

    // --- truncation: must never overflow caller buffers ---------------
    {
        char tag[4] = "?";   // room for 3 chars + NUL
        char msg[5] = "?";   // room for 4 chars + NUL
        char lvl = log_capture_parse(RED "E (1) longtag: longmessage" RESET "\n",
                                     tag, sizeof(tag), msg, sizeof(msg));
        CHECK_CHAR("trunc/level", 'E', lvl);
        CHECK_STR ("trunc/tag", "lon", tag);
        CHECK_STR ("trunc/msg", "long", msg);
    }

    // --- truncation must not split a multibyte UTF-8 character ----------
    // A byte-cut through "→" (E2 86 92) would leave a dangling continuation
    // byte — invalid UTF-8 that makes the /api/errors JSON unparseable.
    // The incomplete trailing character must be dropped.
    {
        char tag[8], msg[5];   // 4 usable bytes: the cut lands inside "→"
        log_capture_parse("I (1) t: ab→cd", tag, sizeof(tag), msg, sizeof(msg));
        CHECK_STR("utf8-trunc/msg", "ab", msg);
    }
    // A complete multibyte character that still fits is kept intact.
    {
        char tag[8], msg[6];   // 5 usable bytes: "ab" + the 3-byte "→"
        log_capture_parse("I (1) t: ab→cd", tag, sizeof(tag), msg, sizeof(msg));
        CHECK_STR("utf8-keep/msg", "ab→", msg);
    }

    // --- library-noise suppression -----------------------------------
    // The benign httpd recv WARN is dropped from the ring; everything else
    // (other tags, other messages, the same line at ERROR) is kept.
    CHECK_CHAR("suppress/httpd-recv-warn", 1,
               log_capture_suppressed('W', "httpd_txrx",
                                      "httpd_sock_err: error in recv : 104"));
    // The matching send WARN is the same routine client-drop case (a browser
    // closing an SSE stream mid-send, EBADF/ECONNRESET) — also dropped.
    CHECK_CHAR("suppress/httpd-send-warn", 1,
               log_capture_suppressed('W', "httpd_txrx",
                                      "httpd_sock_err: error in send : 9"));
    CHECK_CHAR("suppress/httpd-send-warn-104", 1,
               log_capture_suppressed('W', "httpd_txrx",
                                      "httpd_sock_err: error in send : 104"));
    // Only WARN is suppressed — a genuine ERROR still surfaces.
    CHECK_CHAR("suppress/httpd-recv-error", 0,
               log_capture_suppressed('E', "httpd_txrx",
                                      "httpd_sock_err: error in recv : 104"));
    CHECK_CHAR("suppress/httpd-send-error", 0,
               log_capture_suppressed('E', "httpd_txrx",
                                      "httpd_sock_err: error in send : 9"));
    // A different tag with the same substring is left alone.
    CHECK_CHAR("suppress/other-tag", 0,
               log_capture_suppressed('W', "sip", "error in recv : 104"));
    // Unrelated lines pass through untouched.
    CHECK_CHAR("suppress/unrelated", 0,
               log_capture_suppressed('W', "wifi", "weak signal"));
    CHECK_CHAR("suppress/null", 0, log_capture_suppressed('W', NULL, NULL));

    // --- log_strip_ansi: colour removal for the live log stream -------
    {
        // A coloured line collapses to its plain text (length returned,
        // not NUL-terminated by the function — terminate before compare).
        char a[] = GREEN "I (12345) sip: REGISTER ok" RESET "\n";
        int n = log_strip_ansi(a, (int)strlen(a));
        a[n] = '\0';
        CHECK_STR("strip/coloured", "I (12345) sip: REGISTER ok\n", a);

        // No escapes → unchanged, same length.
        char b[] = "W (7) wifi: weak signal\n";
        int m = log_strip_ansi(b, (int)strlen(b));
        b[m] = '\0';
        CHECK_STR("strip/plain", "W (7) wifi: weak signal\n", b);

        // Escape with no terminating 'm' before end is dropped wholesale
        // (no stray bytes left behind).
        char c[] = "x\033[0;3";
        int k = log_strip_ansi(c, (int)strlen(c));
        c[k] = '\0';
        CHECK_STR("strip/truncated-escape", "x", c);

        // Embedded reset between two runs of text.
        char d[] = "ab" RESET "cd";
        int j = log_strip_ansi(d, (int)strlen(d));
        d[j] = '\0';
        CHECK_STR("strip/embedded-reset", "abcd", d);
    }

    printf("log_capture: %d tests, %d failures\n", g_tests, g_failures);
    return g_failures ? 1 : 0;
}
