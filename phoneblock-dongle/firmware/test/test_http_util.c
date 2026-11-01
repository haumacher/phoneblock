// Host test for http_util.c, the User-Agent the server parses: the device
// id must stay the last bracket (DongleRecognizer.DEVICE_ID, the
// REGEXP_REPLACE in DiagnosticsMapper), with the platform comment before
// it. http_util_user_agent() caches its string, so every case runs in its
// own child process.
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>
#include <unistd.h>

#include "config.h"
#include "http_util.h"
#include "linux_test_util.h"
#include "platform.h"

// Stubs for what http_util.c reads from the config and platform layers.
static const char *s_version;
static const char *s_platform;
static const char *s_device_id;

const char *pb_firmware_version(void) { return s_version; }
const char *pb_platform_tag(void) { return s_platform; }
const char *config_device_id(void) { return s_device_id; }

#define ID "01234567-89ab-cdef-0123-456789abcdef"

static void expect(const char *version, const char *platform,
                   const char *device_id, const char *expected)
{
    pid_t pid = fork();
    REQUIRE(pid >= 0);
    if (pid == 0) {
        s_version = version;
        s_platform = platform;
        s_device_id = device_id;
        const char *agent = http_util_user_agent();
        if (strcmp(agent, expected) != 0) {
            fprintf(stderr, "FAIL: got \"%s\", expected \"%s\"\n",
                    agent, expected);
            _exit(1);
        }
        _exit(0);
    }
    int status;
    REQUIRE(waitpid(pid, &status, 0) == pid);
    REQUIRE(WIFEXITED(status) && WEXITSTATUS(status) == 0);
}

// --- http_url_for_log() / http_body_summary() (#532) ---------------------

static void expect_url(const char *url, size_t cap, const char *expected)
{
    char out[256];
    REQUIRE(cap <= sizeof(out));
    memset(out, 'X', sizeof(out));
    http_url_for_log(url, out, cap);
    if (strcmp(out, expected) != 0) {
        fprintf(stderr, "FAIL url: got \"%s\", expected \"%s\"\n", out, expected);
        exit(1);
    }
}

static void expect_summary(const char *body, size_t len, bool echo,
                           const char *expected)
{
    // The body goes into an exact-size heap copy without a NUL, so the
    // AddressSanitizer catches any read past `len`.
    char *copy = NULL;
    if (body) {
        copy = malloc(len ? len : 1);
        REQUIRE(copy != NULL);
        memcpy(copy, body, len);
    }
    char out[HTTP_BODY_SUMMARY_TEXT_MAX + 32];
    http_body_summary(copy, len, echo, out, sizeof(out));
    free(copy);
    if (strcmp(out, expected) != 0) {
        fprintf(stderr, "FAIL summary: got \"%s\", expected \"%s\"\n",
                out, expected);
        exit(1);
    }
}

static void test_url_for_log(void)
{
    expect_url("https://phoneblock.net/phoneblock/api/test", 256,
               "https://phoneblock.net/phoneblock/api/test");
    // Query and fragment never reach the log.
    expect_url("https://phoneblock.net/phoneblock/api/check-prefix?sha1=ABCD&format=json",
               256, "https://phoneblock.net/phoneblock/api/check-prefix");
    expect_url("http://host.example:8080/pb/x#frag", 256,
               "http://host.example:8080/pb/x");
    // User info is dropped.
    expect_url("https://user:secret@host.example/pb/api/test", 256,
               "https://host.example/pb/api/test");
    // No path at all, query directly after the host.
    expect_url("https://host.example?token=secret", 256, "https://host.example");
    // Clamped to the buffer, still terminated.
    expect_url("https://phoneblock.net/phoneblock/api/test", 15, "https://phoneb");
    expect_url(NULL, 16, "");
}

static void test_body_summary(void)
{
    // A Tomcat error page behind a proxy: only the title.
    const char *tomcat =
        "<!doctype html><html lang=\"en\"><head>\n"
        "<title>HTTP Status 502 \xE2\x80\x93 Bad Gateway</title>"
        "<style type=\"text/css\">body {font-family:Tahoma,Arial,sans-serif;} "
        "h1, h2, h3, b {color:white;background-color:#525D76;}</style></head>"
        "<body><h1>HTTP Status 502 \xE2\x80\x93 Bad Gateway</h1></body></html>";
    expect_summary(tomcat, strlen(tomcat), true,
                   "HTML page \"HTTP Status 502 \xE2\x80\x93 Bad Gateway\"");
    // Also when the body's content must not be echoed: the title is the
    // server's, not the account's.
    expect_summary(tomcat, strlen(tomcat), false,
                   "HTML page \"HTTP Status 502 \xE2\x80\x93 Bad Gateway\"");

    // Upper-case tag, leading blank lines, line breaks inside the title.
    const char *nginx = "\r\n  <HTML><HEAD><TITLE>\n 404\n  Not Found </TITLE></HEAD></HTML>";
    expect_summary(nginx, strlen(nginx), true, "HTML page \"404 Not Found\"");

    // HTML without a title, and a title cut off by the receive buffer.
    const char *untitled = "<html><body>Bad Gateway</body></html>";
    expect_summary(untitled, strlen(untitled), true, "HTML page, 37 bytes");
    const char *cut_title = "<html><head><title>Service Unavail";
    expect_summary(cut_title, strlen(cut_title), true,
                   "HTML page \"Service Unavail\"");
    const char *long_title = "<html><head><title>Internal Server Error: "
        "java.lang.IllegalStateException in the request dispatcher</title>";
    expect_summary(long_title, strlen(long_title), true,
        "HTML page \"Internal Server Error: java.lang.IllegalStateException in th...\"");
    const char *empty_title = "<html><head><title> </title></head></html>";
    expect_summary(empty_title, strlen(empty_title), true, "HTML page, 42 bytes");

    // JSON: clamped to HTTP_BODY_SUMMARY_TEXT_MAX bytes, newlines collapsed.
    const char *json =
        "{\n  \"error\": \"internal\",\n  \"message\": \"The server failed to "
        "process the request because the database connection pool was "
        "exhausted while waiting\"\n}";
    expect_summary(json, strlen(json), true,
        "\"{ \"error\": \"internal\", \"message\": \"The server failed to "
        "proc...\"");
    // A short text is shown whole.
    expect_summary("token rejected\n", 15, true, "\"token rejected\"");
    // Not echoed: only its size.
    expect_summary(json, strlen(json), false, "147 bytes");

    // Empty bodies.
    expect_summary("", 0, true, "empty body");
    expect_summary(NULL, 0, true, "empty body");
    expect_summary(" \r\n\t", 4, true, "empty body");

    // A body filling the receive buffer without a NUL: only `len` bytes
    // are read (expect_summary() passes an exact-size copy).
    char raw[200];
    memset(raw, 'a', sizeof(raw));
    memcpy(raw + 150, "ZZZZ", 4);
    char expected[HTTP_BODY_SUMMARY_TEXT_MAX + 8];
    memset(expected, 'a', sizeof(expected));
    expected[0] = '"';
    memcpy(expected + 1 + HTTP_BODY_SUMMARY_TEXT_MAX, "...\"", 5);
    char short_expected[44];
    memset(short_expected, 'a', sizeof(short_expected));
    short_expected[0] = '"';
    memcpy(short_expected + 41, "\"", 2);
    expect_summary(raw, 40, true, short_expected);
    expect_summary(raw, sizeof(raw), true, expected);
    char html_raw[64];
    memset(html_raw, ' ', sizeof(html_raw));
    memcpy(html_raw, "<html><title>Gateway Timeout", 28);
    // No closing tag inside the 28 bytes; the blanks after it are cut off.
    expect_summary(html_raw, 28, true, "HTML page \"Gateway Timeout\"");

    // The clamp never splits a UTF-8 character: a run of 2-byte characters
    // that crosses the limit ends on the last complete one.
    char umlauts[2 * HTTP_BODY_SUMMARY_TEXT_MAX];
    for (size_t i = 0; i < sizeof(umlauts); i += 2) {
        umlauts[i] = (char)0xC3;
        umlauts[i + 1] = (char)0xBC;   // "ü"
    }
    char ue_expected[HTTP_BODY_SUMMARY_TEXT_MAX + 8];
    ue_expected[0] = '"';
    memcpy(ue_expected + 1, umlauts, HTTP_BODY_SUMMARY_TEXT_MAX);
    memcpy(ue_expected + 1 + HTTP_BODY_SUMMARY_TEXT_MAX, "...\"", 5);
    expect_summary(umlauts, sizeof(umlauts), true, ue_expected);
    // Odd limit position: 1 ASCII byte first, so the last byte that fits is
    // a lead byte.
    char shifted[1 + 2 * HTTP_BODY_SUMMARY_TEXT_MAX];
    shifted[0] = 'x';
    memcpy(shifted + 1, umlauts, sizeof(umlauts));
    char sh_expected[HTTP_BODY_SUMMARY_TEXT_MAX + 8];
    sh_expected[0] = '"';
    sh_expected[1] = 'x';
    memcpy(sh_expected + 2, umlauts, HTTP_BODY_SUMMARY_TEXT_MAX - 2);
    memcpy(sh_expected + HTTP_BODY_SUMMARY_TEXT_MAX, "...\"", 5);
    expect_summary(shifted, sizeof(shifted), true, sh_expected);

    // A too-small output buffer is clamped, never overrun.
    char small[8];
    memset(small, 'X', sizeof(small));
    http_body_summary(tomcat, strlen(tomcat), true, small, sizeof(small));
    REQUIRE(strcmp(small, "HTML pa") == 0);
}

static void test_fetch_classify(void)
{
    REQUIRE(http_fetch_classify(PB_HTTP_OK, 200) == HTTP_FETCH_OK);
    REQUIRE(http_fetch_classify(PB_HTTP_OK, 204) == HTTP_FETCH_OK);
    // Only a definite 404 means "not published".
    REQUIRE(http_fetch_classify(PB_HTTP_OK, 404) == HTTP_FETCH_ABSENT);
    REQUIRE(http_fetch_classify(PB_HTTP_OK, 403) == HTTP_FETCH_RETRY);
    REQUIRE(http_fetch_classify(PB_HTTP_OK, 410) == HTTP_FETCH_RETRY);
    REQUIRE(http_fetch_classify(PB_HTTP_OK, 429) == HTTP_FETCH_RETRY);
    REQUIRE(http_fetch_classify(PB_HTTP_OK, 500) == HTTP_FETCH_RETRY);
    REQUIRE(http_fetch_classify(PB_HTTP_OK, 502) == HTTP_FETCH_RETRY);
    REQUIRE(http_fetch_classify(PB_HTTP_OK, 503) == HTTP_FETCH_RETRY);
    REQUIRE(http_fetch_classify(PB_HTTP_OK, 301) == HTTP_FETCH_RETRY);
    REQUIRE(http_fetch_classify(PB_HTTP_OK, 0) == HTTP_FETCH_RETRY);
    // A transport error is never "absent", whatever status is left over.
    REQUIRE(http_fetch_classify(PB_HTTP_ERROR, 404) == HTTP_FETCH_RETRY);
    REQUIRE(http_fetch_classify(PB_HTTP_ERROR, 0) == HTTP_FETCH_RETRY);
    REQUIRE(http_fetch_classify(7 /* e.g. CURLE_COULDNT_CONNECT */, 0)
            == HTTP_FETCH_RETRY);
}

int main(void)
{
    test_fetch_classify();
    test_url_for_log();
    test_body_summary();

    // No platform comment: the plain format older firmware sends.
    expect("1.7.0", NULL, ID, "PhoneBlock-Dongle/1.7.0 (" ID ")");
    expect("1.7.0", "", ID, "PhoneBlock-Dongle/1.7.0 (" ID ")");

    // The platform comment goes before the device id.
    expect("1.7.0", "ESP32-C3", ID,
           "PhoneBlock-Dongle/1.7.0 (ESP32-C3) (" ID ")");
    expect("1.7.0-beta4-12-g092776f", "Linux", ID,
           "PhoneBlock-Dongle/1.7.0-beta4-12-g092776f (Linux) (" ID ")");
    expect("1.7.0-beta4-12-g092776f", "Linux; HA", ID,
           "PhoneBlock-Dongle/1.7.0-beta4-12-g092776f (Linux; HA) (" ID ")");

    // No device id: no platform comment either, or the server would read it
    // as the device id.
    expect("1.7.0", "Linux", "", "PhoneBlock-Dongle/1.7.0");
    expect("1.7.0", "Linux", NULL, "PhoneBlock-Dongle/1.7.0");

    // Missing version.
    expect(NULL, "Linux", ID, "PhoneBlock-Dongle/0.0.0 (Linux) (" ID ")");

    // Too long for the buffer with the platform comment: the comment is
    // dropped instead of cutting off the device id. With this 41-character
    // version the string is 98 bytes without the comment, 110 with
    // "Linux; HA" and 122 with "Linux; Home Assistant"; the buffer holds
    // 119 plus the NUL.
    const char *long_version = "1.7.0-beta4-12345-g0123456789abcdef-dirty";
    char expected[128];
    snprintf(expected, sizeof(expected),
             "PhoneBlock-Dongle/%s (Linux; HA) (" ID ")", long_version);
    expect(long_version, "Linux; HA", ID, expected);
    snprintf(expected, sizeof(expected),
             "PhoneBlock-Dongle/%s (" ID ")", long_version);
    expect(long_version, "Linux; Home Assistant", ID, expected);

    printf("test_http_util: all passed\n");
    return 0;
}
