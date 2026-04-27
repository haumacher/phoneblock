// Host-side unit tests for main/sip_frame.{c,h}.
//
// Build and run:
//     make test_sip_frame && ./test_sip_frame
// (or "make test" to run the full suite).

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>

#include "sip_frame.h"

static int g_tests    = 0;
static int g_failures = 0;

#define BUF_CAP 4096

static void check_int(const char *what, long expected, long got)
{
    g_tests++;
    if (got == expected) return;
    fprintf(stderr, "FAIL %s: expected=%ld got=%ld\n", what, expected, got);
    g_failures++;
}

static void check_str_eq(const char *what, const char *expected, const char *got)
{
    g_tests++;
    if (strcmp(expected, got) == 0) return;
    fprintf(stderr, "FAIL %s\n  expected: <<<%s>>>\n  got:      <<<%s>>>\n",
            what, expected, got);
    g_failures++;
}

// ---------------------------------------------------------------------------
// Sample messages
// ---------------------------------------------------------------------------

// 200 OK to a REGISTER, no body.
static const char MSG_OK_NOBODY[] =
    "SIP/2.0 200 OK\r\n"
    "Via: SIP/2.0/TCP 192.0.2.7:5060;branch=z9hG4bKabc;rport=5060\r\n"
    "From: <sip:alice@example.com>;tag=ft1\r\n"
    "To: <sip:alice@example.com>;tag=tt1\r\n"
    "Call-ID: 1234@phoneblock\r\n"
    "CSeq: 2 REGISTER\r\n"
    "Contact: <sip:alice@192.0.2.7:5060>;expires=300\r\n"
    "Content-Length: 0\r\n\r\n";

// INVITE with 120-byte SDP body. The body itself is exactly 120 bytes.
#define SDP_120 \
    "v=0\r\n" \
    "o=- 1 1 IN IP4 192.0.2.99\r\n" \
    "s=-\r\n" \
    "c=IN IP4 192.0.2.99\r\n" \
    "t=0 0\r\n" \
    "m=audio 31000 RTP/AVP 8\r\n" \
    "a=rtpmap:8 PCMA/8000\r\n"
static const char MSG_INVITE_HDRS[] =
    "INVITE sip:alice@192.0.2.7:5060 SIP/2.0\r\n"
    "Via: SIP/2.0/TCP 192.0.2.99:5060;branch=z9hG4bKxyz\r\n"
    "From: <sip:bob@example.com>;tag=fb1\r\n"
    "To: <sip:alice@example.com>\r\n"
    "Call-ID: invite-1@example.com\r\n"
    "CSeq: 1 INVITE\r\n"
    "Contact: <sip:bob@192.0.2.99:5060>\r\n"
    "Content-Type: application/sdp\r\n"
    "Content-Length: 120\r\n\r\n";

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

// Single complete message in one append.
static void test_single_message(void)
{
    char buf[BUF_CAP];
    char out[BUF_CAP];
    sip_framer_t f;
    sip_framer_init(&f, buf, sizeof(buf));

    int len = (int)strlen(MSG_OK_NOBODY);
    check_int("single: append rc",   0,   sip_framer_append(&f, MSG_OK_NOBODY, len));
    check_int("single: pop length",  len, sip_framer_pop(&f, out, sizeof(out)));
    check_str_eq("single: pop content", MSG_OK_NOBODY, out);
    check_int("single: pop empty",   0,   sip_framer_pop(&f, out, sizeof(out)));
}

// Bytes split across two appends — common when the registrar's response
// straddles a TCP segment boundary.
static void test_split_across_reads(void)
{
    char buf[BUF_CAP];
    char out[BUF_CAP];
    sip_framer_t f;
    sip_framer_init(&f, buf, sizeof(buf));

    int len   = (int)strlen(MSG_OK_NOBODY);
    int split = 50;  // mid-headers
    sip_framer_append(&f, MSG_OK_NOBODY, split);
    check_int("split: pop after partial", 0, sip_framer_pop(&f, out, sizeof(out)));
    sip_framer_append(&f, MSG_OK_NOBODY + split, len - split);
    check_int("split: pop length", len, sip_framer_pop(&f, out, sizeof(out)));
    check_str_eq("split: pop content", MSG_OK_NOBODY, out);
}

// "\r\n\r\n" boundary itself split between two reads (CRLF | CRLF), which
// is the bug class find_headers_end has to get right.
static void test_split_at_terminator(void)
{
    char buf[BUF_CAP];
    char out[BUF_CAP];
    sip_framer_t f;
    sip_framer_init(&f, buf, sizeof(buf));

    int len   = (int)strlen(MSG_OK_NOBODY);
    // The MSG_OK_NOBODY ends with "...Content-Length: 0\r\n\r\n".
    // Cut right between the two \r\n pairs.
    int split = len - 2;
    sip_framer_append(&f, MSG_OK_NOBODY, split);
    check_int("term-split: incomplete", 0, sip_framer_pop(&f, out, sizeof(out)));
    sip_framer_append(&f, MSG_OK_NOBODY + split, len - split);
    check_int("term-split: pop length", len, sip_framer_pop(&f, out, sizeof(out)));
}

// Two complete messages delivered in one recv (common with TCP coalescing).
static void test_multiple_in_one_read(void)
{
    char combined[BUF_CAP];
    int n = snprintf(combined, sizeof(combined), "%s%s",
                     MSG_OK_NOBODY, MSG_OK_NOBODY);

    char buf[BUF_CAP];
    char out[BUF_CAP];
    sip_framer_t f;
    sip_framer_init(&f, buf, sizeof(buf));

    sip_framer_append(&f, combined, n);

    int one_len = (int)strlen(MSG_OK_NOBODY);
    check_int("two-in-one: pop #1 length", one_len, sip_framer_pop(&f, out, sizeof(out)));
    check_str_eq("two-in-one: pop #1", MSG_OK_NOBODY, out);
    check_int("two-in-one: pop #2 length", one_len, sip_framer_pop(&f, out, sizeof(out)));
    check_str_eq("two-in-one: pop #2", MSG_OK_NOBODY, out);
    check_int("two-in-one: empty after", 0, sip_framer_pop(&f, out, sizeof(out)));
}

// Headers complete but Content-Length bytes still pending → must wait.
static void test_body_streaming(void)
{
    char buf[BUF_CAP];
    char out[BUF_CAP];
    sip_framer_t f;
    sip_framer_init(&f, buf, sizeof(buf));

    int hdrs_len = (int)strlen(MSG_INVITE_HDRS);
    sip_framer_append(&f, MSG_INVITE_HDRS, hdrs_len);
    check_int("body: incomplete after headers", 0, sip_framer_pop(&f, out, sizeof(out)));

    // Append SDP body in two chunks.
    sip_framer_append(&f, SDP_120, 60);
    check_int("body: incomplete mid-body",   0, sip_framer_pop(&f, out, sizeof(out)));
    sip_framer_append(&f, SDP_120 + 60, 60);
    int total = hdrs_len + 120;
    check_int("body: pop length",          total, sip_framer_pop(&f, out, sizeof(out)));
    check_int("body: SDP terminator",      0,
              memcmp(out + hdrs_len, SDP_120, 120));
}

// Missing Content-Length on a TCP message: framer treats as 0 so a
// well-formed status response with a body-less header block still pops.
static void test_missing_content_length(void)
{
    static const char MSG[] =
        "SIP/2.0 100 Trying\r\n"
        "Via: SIP/2.0/TCP 192.0.2.7:5060;branch=z9hG4bKtryx\r\n"
        "Call-ID: 1234@phoneblock\r\n"
        "CSeq: 1 INVITE\r\n\r\n";

    char buf[BUF_CAP];
    char out[BUF_CAP];
    sip_framer_t f;
    sip_framer_init(&f, buf, sizeof(buf));

    int len = (int)strlen(MSG);
    sip_framer_append(&f, MSG, len);
    check_int("no-cl: pop length", len, sip_framer_pop(&f, out, sizeof(out)));
    check_str_eq("no-cl: content", MSG, out);
}

// Garbage Content-Length must surface as -1 so the caller drops the
// connection rather than silently buffering forever.
static void test_malformed_content_length(void)
{
    static const char MSG[] =
        "SIP/2.0 200 OK\r\n"
        "Content-Length: NaN\r\n\r\n";

    char buf[BUF_CAP];
    char out[BUF_CAP];
    sip_framer_t f;
    sip_framer_init(&f, buf, sizeof(buf));

    sip_framer_append(&f, MSG, (int)strlen(MSG));
    check_int("bad-cl: pop returns -1", -1, sip_framer_pop(&f, out, sizeof(out)));
}

// A frame that exceeds the buffer must be reported via append, not later
// — because the next message's bytes would otherwise corrupt parsing.
static void test_overflow_append(void)
{
    char buf[64];   // intentionally tiny
    sip_framer_t f;
    sip_framer_init(&f, buf, sizeof(buf));

    int rc = sip_framer_append(&f, MSG_OK_NOBODY, (int)strlen(MSG_OK_NOBODY));
    check_int("overflow: append returns -1", -1, rc);
}

// reset() drops a half-buffered partial so a reconnect doesn't see a
// stale prefix in front of the new connection's first message.
static void test_reset_clears_partial(void)
{
    char buf[BUF_CAP];
    char out[BUF_CAP];
    sip_framer_t f;
    sip_framer_init(&f, buf, sizeof(buf));

    sip_framer_append(&f, MSG_OK_NOBODY, 30);  // partial
    sip_framer_reset(&f);
    int len = (int)strlen(MSG_OK_NOBODY);
    sip_framer_append(&f, MSG_OK_NOBODY, len);
    check_int("reset: pop length after", len, sip_framer_pop(&f, out, sizeof(out)));
    check_str_eq("reset: pop content after", MSG_OK_NOBODY, out);
}

int main(void)
{
    test_single_message();
    test_split_across_reads();
    test_split_at_terminator();
    test_multiple_in_one_read();
    test_body_streaming();
    test_missing_content_length();
    test_malformed_content_length();
    test_overflow_append();
    test_reset_clears_partial();

    printf("test_sip_frame: %d tests, %d failures\n", g_tests, g_failures);
    return g_failures == 0 ? 0 : 1;
}
