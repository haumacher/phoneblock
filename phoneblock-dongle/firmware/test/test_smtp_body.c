// Host test for smtp_body.c — the SMTP DATA wire encoding of the status
// mail body: bare LF -> CRLF and dot-stuffing of leading dots. Pure libc.
#include <assert.h>
#include <stdio.h>
#include <string.h>

#include "smtp_body.h"

// Sink that appends encoded chunks into a fixed buffer.
struct buf { char data[512]; size_t len; };

static int buf_sink(void *ctx, const char *data, size_t len)
{
    struct buf *b = ctx;
    assert(b->len + len < sizeof(b->data));
    memcpy(b->data + b->len, data, len);
    b->len += len;
    b->data[b->len] = '\0';
    return 0;
}

// Encode `in` and assert the result equals `want`.
static void check(const char *in, const char *want)
{
    struct buf b = { .len = 0 };
    b.data[0] = '\0';
    assert(smtp_encode_body(in, buf_sink, &b) == 0);
    if (strcmp(b.data, want) != 0) {
        fprintf(stderr, "smtp_encode_body(%s):\n  got:  %s\n  want: %s\n",
                in, b.data, want);
        assert(0);
    }
}

// A sink that fails on its Nth call, to check abort propagation.
struct failing { int calls; int fail_on; };
static int failing_sink(void *ctx, const char *data, size_t len)
{
    (void)data; (void)len;
    struct failing *f = ctx;
    return (++f->calls == f->fail_on) ? -1 : 0;
}

int main(void)
{
    // Empty body -> no output.
    check("", "");

    // Single line, no trailing newline -> passed through verbatim, no CRLF.
    check("hello", "hello");

    // Bare LF becomes CRLF.
    check("a\nb", "a\r\nb");

    // Trailing newline -> trailing CRLF.
    check("line\n", "line\r\n");

    // The real status-mail shape: several lines, blank line in between.
    check("Statusmeldung\n\nGeraet: x\n",
          "Statusmeldung\r\n\r\nGeraet: x\r\n");

    // Dot-stuffing: a line starting with '.' gets an extra leading '.'.
    check(".hidden\n", "..hidden\r\n");

    // A line that is only a dot -> "..".
    check(".\n", "..\r\n");

    // Dot not at line start is left alone.
    check("a.b\n", "a.b\r\n");

    // Leading dot after a newline is stuffed too (per-line, not just first).
    check("ok\n.danger\n", "ok\r\n..danger\r\n");

    // Sink abort code is propagated.
    struct failing f = { .calls = 0, .fail_on = 1 };
    assert(smtp_encode_body("x\ny", failing_sink, &f) == -1);

    printf("test_smtp_body: OK\n");
    return 0;
}
