// Host-side unit tests for main/sip_parse.{c,h}.
//
// Each test case is a single line of the form
//     expect_<fn>(expected, input);
// The expected value comes first to match standard assertion-library
// style and to keep the visual weight on the intent.
//
// Build and run:
//     make test

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>

#include "sip_parse.h"

// ---------------------------------------------------------------------------
// Assertion helpers — each one runs a parser and compares to expected.
// ---------------------------------------------------------------------------

static int g_tests    = 0;
static int g_failures = 0;

static void report_str(const char *what, const char *input,
                       const char *expected, const char *got)
{
    g_tests++;
    if (strcmp(got, expected) == 0) return;
    fprintf(stderr,
            "FAIL %-20s input=<<<%s>>>\n  expected=\"%s\"\n  got=     \"%s\"\n",
            what, input, expected, got);
    g_failures++;
}

static void report_int(const char *what, const char *input,
                       long expected, long got)
{
    g_tests++;
    if (got == expected) return;
    fprintf(stderr,
            "FAIL %-20s input=<<<%s>>>\n  expected=%ld\n  got=     %ld\n",
            what, input, expected, got);
    g_failures++;
}

static void report_bool(const char *what, const char *input,
                        bool expected, bool got)
{
    g_tests++;
    if (got == expected) return;
    fprintf(stderr,
            "FAIL %-20s input=<<<%s>>>\n  expected=%s\n  got=     %s\n",
            what, input, expected ? "true" : "false", got ? "true" : "false");
    g_failures++;
}

// --- Per-parser wrappers ---------------------------------------------------

static void expect_find_header(const char *expected, const char *msg, const char *name)
{
    const char *p = find_header(msg, (int)strlen(msg), name);
    if (!expected) {
        report_bool("find_header/missing", name, false, p != NULL);
    } else {
        char buf[128] = {0};
        if (p) {
            const char *end = msg + strlen(msg);
            header_value(p, end, buf, sizeof(buf));
        }
        report_str("find_header", name, expected, buf);
    }
}

static void expect_status(int expected, const char *resp)
{
    int got = parse_status_code(resp, (int)strlen(resp));
    report_int("parse_status_code", resp, expected, got);
}

static void expect_cseq(uint32_t expected, const char *req)
{
    uint32_t got = parse_cseq(req, (int)strlen(req));
    report_int("parse_cseq", req, (long)expected, (long)got);
}

static void expect_method(const char *expected, const char *pkt)
{
    char out[32] = {0};
    parse_method(pkt, (int)strlen(pkt), out, sizeof(out));
    report_str("parse_method", pkt, expected, out);
}

static void expect_call_id(const char *expected, const char *req)
{
    char out[128] = {0};
    parse_call_id(req, (int)strlen(req), out, sizeof(out));
    report_str("parse_call_id", req, expected, out);
}

static void expect_tag(const char *expected, const char *hdr_val)
{
    char out[64] = {0};
    parse_tag(hdr_val, (int)strlen(hdr_val), out, sizeof(out));
    report_str("parse_tag", hdr_val, expected, out);
}

static void expect_parse_uri(const char *expected, const char *hdr_val)
{
    char out[128] = {0};
    parse_uri(hdr_val, (int)strlen(hdr_val), out, sizeof(out));
    report_str("parse_uri", hdr_val, expected, out);
}

static void expect_user_from_uri(const char *expected, const char *uri)
{
    char out[64] = {0};
    user_from_uri(uri, out, sizeof(out));
    report_str("user_from_uri", uri, expected, out);
}

static void expect_display_name(const char *expected, const char *hdr_val)
{
    char out[64] = {0};
    parse_display_name(hdr_val, (int)strlen(hdr_val), out, sizeof(out));
    report_str("parse_display_name", hdr_val, expected, out);
}

static void expect_phone_number_like(bool expected, const char *s)
{
    report_bool("is_phone_number_like", s, expected, is_phone_number_like(s));
}

static void expect_dialable(bool expected, const char *n)
{
    report_bool("looks_dialable", n, expected, looks_dialable(n));
}

static void expect_normalize_de(const char *expected, const char *raw)
{
    char out[64] = {0};
    normalize_de(raw, out, sizeof(out));
    report_str("normalize_de", raw, expected, out);
}

static void expect_same_call_id(bool expected, const char *a, const char *b)
{
    char both[128];
    snprintf(both, sizeof(both), "'%s' vs '%s'", a ? a : "(null)", b ? b : "(null)");
    report_bool("same_call_id", both, expected, same_call_id(a, b));
}

// ---------------------------------------------------------------------------
// Sample messages used by header-level tests.
// ---------------------------------------------------------------------------

static const char *SAMPLE_INVITE =
    "INVITE sip:phoneblock-ab@192.168.178.22:5061 SIP/2.0\r\n"
    "Via: SIP/2.0/UDP 192.168.178.1:5060;branch=z9hG4bK5DBE35795BA63E78\r\n"
    "From: \"Haui Mobil\" <sip:**622@fritz.box>;tag=B36A0F8B30FDE1CD\r\n"
    "To: <sip:phoneblock-ab@192.168.178.22:5061>\r\n"
    "Call-ID: 4B8A3DA30AC9B91D@192.168.178.1\r\n"
    "CSeq: 5 INVITE\r\n"
    "Contact: <sip:2C04299A538E7E86CB11CD56FBB9BB6@192.168.178.1>\r\n"
    "Content-Length: 0\r\n\r\n";

static const char *SAMPLE_401 =
    "SIP/2.0 401 Unauthorized\r\n"
    "Via: SIP/2.0/UDP 10.0.2.15:5061;branch=z9hG4bKcf58dc0b;rport=56718;received=192.168.178.22\r\n"
    "From: <sip:phoneblock-ab@192.168.178.1>;tag=995f443d\r\n"
    "To: <sip:phoneblock-ab@192.168.178.1>;tag=FA26CC97\r\n"
    "Call-ID: cb9319c3@phoneblock\r\n"
    "CSeq: 879113571 REGISTER\r\n"
    "WWW-Authenticate: Digest realm=\"fritz.box\", nonce=\"055DA32FA85599EA\"\r\n"
    "Content-Length: 0\r\n\r\n";

// ---------------------------------------------------------------------------
// Test groups
// ---------------------------------------------------------------------------

static void test_parse_method(void)
{
    expect_method("INVITE",   SAMPLE_INVITE);
    expect_method("REGISTER", "REGISTER sip:fritz.box SIP/2.0\r\n");
    expect_method("OPTIONS",  "OPTIONS sip:x SIP/2.0\r\n");
    expect_method("BYE",      "BYE sip:x SIP/2.0\r\n");
    expect_method("ACK",      "ACK sip:x SIP/2.0\r\n");
}

static void test_parse_status_code(void)
{
    expect_status(401, SAMPLE_401);
    expect_status(200, "SIP/2.0 200 OK\r\n\r\n");
    expect_status(486, "SIP/2.0 486 Busy Here\r\n\r\n");
    expect_status(100, "SIP/2.0 100 Trying\r\n\r\n");
    expect_status(-1,  SAMPLE_INVITE);             // not a response
    expect_status(-1,  "");                         // too short
    expect_status(-1,  "HTTP/1.1 200 OK\r\n\r\n"); // wrong protocol
}

static void test_parse_cseq(void)
{
    expect_cseq(5,         SAMPLE_INVITE);
    expect_cseq(879113571, SAMPLE_401);
    expect_cseq(0,         "FOO sip:x SIP/2.0\r\n\r\n");  // no CSeq header
}

static void test_parse_call_id(void)
{
    expect_call_id("4B8A3DA30AC9B91D@192.168.178.1", SAMPLE_INVITE);
    expect_call_id("cb9319c3@phoneblock",            SAMPLE_401);
    expect_call_id("",                                "FOO sip:x SIP/2.0\r\n\r\n");
    // Trailing whitespace is trimmed.
    expect_call_id("abc123",
                   "INVITE sip:x SIP/2.0\r\n"
                   "Call-ID:  abc123 \t\r\n\r\n");
}

static void test_find_header(void)
{
    // Case-insensitive match.
    expect_find_header("\"Haui Mobil\" <sip:**622@fritz.box>;tag=B36A0F8B30FDE1CD",
                       SAMPLE_INVITE, "From");
    expect_find_header("\"Haui Mobil\" <sip:**622@fritz.box>;tag=B36A0F8B30FDE1CD",
                       SAMPLE_INVITE, "from");
    expect_find_header("Digest realm=\"fritz.box\", nonce=\"055DA32FA85599EA\"",
                       SAMPLE_401, "WWW-Authenticate");
    expect_find_header(NULL, SAMPLE_INVITE, "X-Does-Not-Exist");
}

static void test_parse_tag(void)
{
    expect_tag("abc",              "<sip:a@b>;tag=abc");
    expect_tag("abc",              "<sip:a@b>;tag=abc;other=xyz");
    expect_tag("B36A0F8B30FDE1CD", "\"Haui\" <sip:**622@fritz.box>;tag=B36A0F8B30FDE1CD");
    expect_tag("UPPER",            "<sip:a@b>;TAG=UPPER");   // case-insensitive
    expect_tag("",                 "<sip:a@b>");              // no tag
    expect_tag("",                 "");
}

static void test_parse_uri(void)
{
    expect_parse_uri("sip:alice@example.com",  "<sip:alice@example.com>");
    expect_parse_uri("sip:alice@example.com",  "\"Alice\" <sip:alice@example.com>");
    expect_parse_uri("sip:alice@example.com",  "\"Alice\" <sip:alice@example.com>;tag=xyz");
    expect_parse_uri("sip:alice@example.com",  "<sip:alice@example.com;transport=udp>");
    expect_parse_uri("sip:alice@example.com",  "sip:alice@example.com");
    expect_parse_uri("sip:alice@example.com",  "sip:alice@example.com;tag=abc");
    expect_parse_uri("sip:**622@fritz.box",    "<sip:**622@fritz.box>");
    expect_parse_uri("sip:+4917412345@host",   "<sip:+4917412345@host>");
    expect_parse_uri("",                        "");
}

static void test_user_from_uri(void)
{
    expect_user_from_uri("alice",          "sip:alice@example.com");
    expect_user_from_uri("01234",          "sip:01234@fritz.box");
    expect_user_from_uri("+4917412345",    "sip:+4917412345@host");
    expect_user_from_uri("**622",          "sip:**622@fritz.box");
    expect_user_from_uri("bob",            "sips:bob@example.com:5061");
    expect_user_from_uri("+1234567890",    "tel:+1234567890");
    expect_user_from_uri("",                "");
}

static void test_parse_display_name(void)
{
    expect_display_name("Haui Mobil", "\"Haui Mobil\" <sip:**622@fritz.box>;tag=xyz");
    expect_display_name("Alice",      "\"Alice\" <sip:alice@example.com>");
    expect_display_name("Joe",        "Joe <sip:joe@example.com>");
    expect_display_name("",           "<sip:alice@example.com>");
    expect_display_name("",           "\"\" <sip:alice@example.com>");
    // Leading whitespace before quoted name is tolerated.
    expect_display_name("Alice",      "  \"Alice\" <sip:a@b>");
    // Unquoted multi-word, trimmed.
    expect_display_name("Max Meier",  "Max Meier <sip:m@b>");
}

static void test_is_phone_number_like(void)
{
    expect_phone_number_like(true,  "030123456");
    expect_phone_number_like(true,  "+49 (030) 123-456");
    expect_phone_number_like(true,  "+491234");
    expect_phone_number_like(true,  "12/34");
    expect_phone_number_like(false, "Haui Mobil");
    expect_phone_number_like(false, "Alice");
    expect_phone_number_like(false, "+");              // no digit
    expect_phone_number_like(false, "");                // empty
    expect_phone_number_like(false, "   ");             // no digit
}

static void test_looks_dialable(void)
{
    expect_dialable(true,  "01234");
    expect_dialable(true,  "030123456");
    expect_dialable(true,  "+4917412345");
    expect_dialable(false, "**622");                    // internal Fritz!Box code
    expect_dialable(false, "*21#0172123");              // feature code
    expect_dialable(false, "#123");
    expect_dialable(false, "");
    expect_dialable(false, "+");                         // + alone
    expect_dialable(false, "+a123");                     // + followed by non-digit
}

static void test_normalize_de(void)
{
    // National German → international +49 (leading 0 dropped).
    expect_normalize_de("+4917412345678",  "017412345678");
    expect_normalize_de("+4930123456",     "030123456");
    // Already E.164: untouched.
    expect_normalize_de("+4917412345678",  "+4917412345678");
    expect_normalize_de("+18886749072",    "+18886749072");
    // "00" international escape → "+".
    expect_normalize_de("+491741234",      "00491741234");
    expect_normalize_de("+18886749072",    "0018886749072");
    // Whitespace / dashes / parens / slashes stripped before prefix logic.
    expect_normalize_de("+4930123456",     "030 123 456");
    expect_normalize_de("+4930123456",     "030-123-456");
    expect_normalize_de("+4930123456",     "(030) 123456");
    expect_normalize_de("+4930123456",     "030/123456");
    expect_normalize_de("+4917412345678",  "+49 174 1234 5678");
    // Non-external inputs pass through unchanged; looks_dialable rejects
    // them later.
    expect_normalize_de("**622",           "**622");
    expect_normalize_de("*21#",            "*21#");
    expect_normalize_de("",                "");
}

static void test_same_call_id(void)
{
    expect_same_call_id(true,  "abc@host", "abc@host");
    expect_same_call_id(true,  "ABC@Host", "abc@host");    // case-insensitive
    expect_same_call_id(false, "abc@host", "def@host");
    expect_same_call_id(false, "",         "abc@host");
    expect_same_call_id(false, "abc@host", "");
    expect_same_call_id(false, NULL,       "abc@host");
    expect_same_call_id(false, "abc@host", NULL);
}

// ---------------------------------------------------------------------------

int main(void)
{
    test_parse_method();
    test_parse_status_code();
    test_parse_cseq();
    test_parse_call_id();
    test_find_header();
    test_parse_tag();
    test_parse_uri();
    test_user_from_uri();
    test_parse_display_name();
    test_is_phone_number_like();
    test_looks_dialable();
    test_normalize_de();
    test_same_call_id();

    printf("%d tests, %d failures\n", g_tests, g_failures);
    return g_failures == 0 ? 0 : 1;
}
