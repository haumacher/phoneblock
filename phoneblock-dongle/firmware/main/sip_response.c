#define _GNU_SOURCE   // memmem
#include "sip_response.h"

#include <stdarg.h>
#include <stdio.h>
#include <string.h>

#include "sip_parse.h"   // find_header

// Format one fragment at `out` (capacity `cap`, incl. NUL). Returns the number
// of bytes written on success, or -1 if the fragment did not fit entirely (or
// cap <= 0). vsnprintf never writes more than `cap` bytes, so this can't
// overrun `out`; the -1 lets the builder abandon a response that would be
// truncated instead of emitting a partial (and, on TCP, mis-framed) one.
//
// IMPORTANT: a caller must treat -1 as "stop" and must NOT fold it into a
// running length with `n += append_fmt(...)`. Doing so would move the cursor
// *backwards*, and repeated failures would drive it negative — pointing
// `out + n` before the buffer and making `cap - n` larger than the buffer,
// i.e. reintroducing the very overflow this guards against. sip_response_build
// routes every call through the ADD() macro so the check cannot be skipped.
static int append_fmt(char *out, int cap, const char *fmt, ...)
{
    if (cap <= 0) return -1;
    va_list ap;
    va_start(ap, fmt);
    int w = vsnprintf(out, (size_t)cap, fmt, ap);
    va_end(ap);
    return (w >= 0 && w < cap) ? w : -1;
}

// Copy one full "Name: value\r\n" line from req into out. Returns bytes
// written, 0 if the header is absent (nothing to echo — not an error), or -1
// if the header is present but does not fit.
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
// if the To has no tag yet, append ";tag=<our_tag>". Returns bytes written,
// 0 if there is no To header, or -1 if the To does not fit.
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
    if (out_cap <= 0) return -1;
    int body_len = body ? (int)strlen(body) : 0;

    int n = 0;

    // Append a fragment (or the result of an echo_* helper) and advance the
    // cursor — but abandon the whole response if it did not fit. Because n is
    // only ever advanced by a non-negative amount, it stays within
    // [0, out_cap - 1], so every `out + n` / `out_cap - n` below is in range.
    // Skipping the `< 0` check on any single call would break that invariant
    // (see append_fmt()'s note), so every append MUST go through this macro.
    #define ADD(expr) do {          \
        int w_ = (expr);            \
        if (w_ < 0) return -1;      \
        n += w_;                    \
    } while (0)

    ADD(append_fmt(out + n, out_cap - n, "SIP/2.0 %d %s\r\n", status, reason));
    ADD(echo_header_line(req, req_len, "Via",     out + n, out_cap - n));
    ADD(echo_header_line(req, req_len, "From",    out + n, out_cap - n));
    ADD(echo_to_with_tag(req, req_len, our_tag,   out + n, out_cap - n));
    ADD(echo_header_line(req, req_len, "Call-ID", out + n, out_cap - n));
    ADD(echo_header_line(req, req_len, "CSeq",    out + n, out_cap - n));
    ADD(append_fmt(out + n, out_cap - n,
        "Contact: <sip:%s@%s:%d>\r\n"
        "Allow: INVITE, ACK, CANCEL, BYE, OPTIONS\r\n"
        "User-Agent: phoneblock-dongle/0.1\r\n",
        contact_user, contact_host, contact_port));

    if (body_len > 0) {
        ADD(append_fmt(out + n, out_cap - n,
            "Content-Type: application/sdp\r\n"
            "Content-Length: %d\r\n\r\n%s",
            body_len, body));
    } else {
        ADD(append_fmt(out + n, out_cap - n,
            "Content-Length: 0\r\n\r\n"));
    }

    #undef ADD

    out[n] = '\0';   // n is in [0, out_cap - 1]
    return n;
}
