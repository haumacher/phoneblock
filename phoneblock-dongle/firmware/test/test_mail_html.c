// Host test for mail_html.c — the pure string builders used to assemble the
// HTML status mail body. The escaping is the security-relevant part: the
// call list and log lines carry caller-controlled text (SIP/Fritz!Box
// display names, log messages), so an unescaped '<' would let a spam caller
// inject markup into the owner's status mail. Pure libc.
#include <assert.h>
#include <stdio.h>
#include <string.h>

#include "mail_html.h"

#define CAP 256

// Escape `in` into a fresh buffer and assert it equals `want`.
static void check_escape(const char *in, const char *want)
{
    char buf[CAP];
    size_t len = append_html_escaped(buf, CAP, 0, in);
    buf[len] = '\0';
    if (strcmp(buf, want) != 0) {
        fprintf(stderr, "append_html_escaped(%s):\n  got:  %s\n  want: %s\n",
                in, buf, want);
        assert(0);
    }
}

// URL-encode `in` into a fresh buffer and assert it equals `want`.
static void check_url(const char *in, const char *want)
{
    char buf[CAP];
    size_t len = append_url_encoded(buf, CAP, 0, in);
    buf[len] = '\0';
    if (strcmp(buf, want) != 0) {
        fprintf(stderr, "append_url_encoded(%s):\n  got:  %s\n  want: %s\n",
                in, buf, want);
        assert(0);
    }
}

int main(void)
{
    // --- append_str ---
    {
        char buf[CAP];
        size_t len = append_str(buf, CAP, 0, "Hallo");
        len = append_str(buf, CAP, len, ", Welt");
        buf[len] = '\0';
        assert(strcmp(buf, "Hallo, Welt") == 0);
    }

    // --- append_html_escaped: each entity, plain text, mixed ---
    check_escape("plain text 123", "plain text 123");
    check_escape("a & b", "a &amp; b");
    check_escape("<b>", "&lt;b&gt;");
    check_escape("\"q\"", "&quot;q&quot;");
    check_escape("it's", "it&#39;s");
    // The injection that motivates the escaping: a caller-set display name.
    check_escape("<script>alert(1)</script>",
                 "&lt;script&gt;alert(1)&lt;/script&gt;");
    check_escape("Mueller & Soehne <\"VIP\">",
                 "Mueller &amp; Soehne &lt;&quot;VIP&quot;&gt;");
    // UTF-8 bytes pass through untouched (only the 5 ASCII metachars escape).
    check_escape("Köln", "Köln");

    // --- append_url_encoded: number forms ---
    check_url("+4970412875", "%2B4970412875");   // leading '+' must encode
    check_url("004970412875", "004970412875");   // digits pass through
    check_url("0049-89/12 34", "0049-89%2F12%2034"); // '/' and ' ' encode, '-' stays

    // --- bounds: a near-full buffer never writes past cap nor a partial entity ---
    {
        // cap leaves only a few bytes; feeding all-escapable input must stop
        // cleanly (every appended token is a complete "&...;").
        char buf[16];
        size_t len = append_html_escaped(buf, sizeof(buf), 0, "<<<<<<<<<<");
        assert(len < sizeof(buf));
        buf[len] = '\0';
        // Whatever fit must be a whole number of "&lt;" tokens.
        assert(len % 4 == 0);
        for (size_t i = 0; i < len; i += 4)
            assert(strncmp(buf + i, "&lt;", 4) == 0);
    }
    {
        // Same for URL encoding: only whole "%XX" triples, never a split one.
        char buf[16];
        size_t len = append_url_encoded(buf, sizeof(buf), 0, "++++++++++");
        assert(len < sizeof(buf));
        buf[len] = '\0';
        assert(len % 3 == 0);
        for (size_t i = 0; i < len; i += 3)
            assert(strncmp(buf + i, "%2B", 3) == 0);
    }

    // --- regression: the builders must NUL-terminate, so a body assembled
    // into a dirty (uninitialised) buffer never streams trailing heap garbage
    // into the mail. Without the terminator, strcmp below reads the 'X' fill
    // past the appended content.
    {
        char buf[64];
        memset(buf, 'X', sizeof(buf));          // simulate uninitialised heap
        size_t len = append_str(buf, sizeof(buf), 0, "Hallo");
        assert(buf[len] == '\0');               // terminated by the helper itself
        assert(strcmp(buf, "Hallo") == 0);      // no trailing 'X' leaked
        // Chain the escaper and the url-encoder; the last call owns the NUL.
        memset(buf, 'X', sizeof(buf));
        len = append_str(buf, sizeof(buf), 0, "n=");
        len = append_html_escaped(buf, sizeof(buf), len, "<b>");
        assert(buf[len] == '\0');
        len = append_url_encoded(buf, sizeof(buf), len, " +4");
        assert(buf[len] == '\0');
        assert(strcmp(buf, "n=&lt;b&gt;%20%2B4") == 0);
    }

    printf("test_mail_html: all assertions passed\n");
    return 0;
}
