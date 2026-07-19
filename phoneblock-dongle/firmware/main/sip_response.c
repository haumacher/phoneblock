#define _GNU_SOURCE   // memmem
#include "sip_response.h"

#include <stdarg.h>
#include <stdio.h>
#include <string.h>

#include "sip_parse.h"   // find_header

// Append a formatted fragment at `out` (capacity `cap`, incl. NUL) and return
// the number of bytes written — but ONLY if the whole fragment fit. If it
// would not fit (or cap <= 0), nothing usable is emitted and 0 is returned.
//
// This is the invariant the whole builder rests on: because a fragment that
// doesn't fit contributes 0, a running accumulator `n` can never be advanced
// past `out_cap - 1`. Every subsequent `out + n` therefore stays inside the
// buffer and every `out_cap - n` stays >= 1. snprintf's return value (the
// length it WOULD have written) is deliberately not trusted as the advance —
// that unclamped value on the ";tag=" path is what previously ran the cursor
// off the end and smashed adjacent heap metadata.
static int append_fmt(char *out, int cap, const char *fmt, ...)
{
    if (cap <= 0) return 0;
    va_list ap;
    va_start(ap, fmt);
    int w = vsnprintf(out, (size_t)cap, fmt, ap);
    va_end(ap);
    return (w > 0 && w < cap) ? w : 0;
}

// Copy one full "Name: value\r\n" line from req into out. Returns bytes
// written (0 if the header is absent or doesn't fit).
static int echo_header_line(const char *req, int req_len, const char *name,
                            char *out, int out_cap)
{
    const char *val = find_header(req, req_len, name);
    if (!val) return 0;

    const char *end = req + req_len;
    const char *eol = val;
    while (eol < end && *eol != '\r' && *eol != '\n') eol++;
    int val_len = (int)(eol - val);

    return append_fmt(out, out_cap, "%s: %.*s\r\n", name, val_len, val);
}

// Like echo_header_line, but for the To header of a dialog-forming request:
// if the To has no tag yet, append ";tag=<our_tag>". Both branches go through
// append_fmt, so an over-long To is dropped whole rather than overrunning.
static int echo_to_with_tag(const char *req, int req_len,
                            const char *our_tag, char *out, int out_cap)
{
    const char *val = find_header(req, req_len, "To");
    if (!val) return 0;
    const char *end = req + req_len;
    const char *eol = val;
    while (eol < end && *eol != '\r' && *eol != '\n') eol++;
    int val_len = (int)(eol - val);

    if (memmem(val, val_len, ";tag=", 5)) {
        return append_fmt(out, out_cap, "To: %.*s\r\n", val_len, val);
    }
    return append_fmt(out, out_cap, "To: %.*s;tag=%s\r\n",
                      val_len, val, our_tag);
}

int sip_response_build(const char *req, int req_len,
                       int status, const char *reason,
                       const char *our_tag, const char *body,
                       const char *contact_user, const char *contact_host,
                       int contact_port,
                       char *out, int out_cap)
{
    if (out_cap <= 0) return 0;
    int body_len = body ? (int)strlen(body) : 0;

    int n = 0;
    n += append_fmt(out + n, out_cap - n, "SIP/2.0 %d %s\r\n", status, reason);
    n += echo_header_line(req, req_len, "Via",     out + n, out_cap - n);
    n += echo_header_line(req, req_len, "From",    out + n, out_cap - n);
    n += echo_to_with_tag(req, req_len, our_tag,   out + n, out_cap - n);
    n += echo_header_line(req, req_len, "Call-ID", out + n, out_cap - n);
    n += echo_header_line(req, req_len, "CSeq",    out + n, out_cap - n);
    n += append_fmt(out + n, out_cap - n,
        "Contact: <sip:%s@%s:%d>\r\n"
        "Allow: INVITE, ACK, CANCEL, BYE, OPTIONS\r\n"
        "User-Agent: phoneblock-dongle/0.1\r\n",
        contact_user, contact_host, contact_port);

    if (body_len > 0) {
        n += append_fmt(out + n, out_cap - n,
            "Content-Type: application/sdp\r\n"
            "Content-Length: %d\r\n\r\n%s",
            body_len, body);
    } else {
        n += append_fmt(out + n, out_cap - n,
            "Content-Length: 0\r\n\r\n");
    }

    // n is always <= out_cap - 1; guarantee a terminator regardless.
    out[n < out_cap ? n : out_cap - 1] = '\0';
    return n;
}
