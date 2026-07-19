#include "sip_frame.h"

#include <string.h>

#include "sip_parse.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

void sip_framer_init(sip_framer_t *f, char *buf, int cap)
{
    f->buf = buf;
    f->cap = cap;
    f->have = 0;
}

void sip_framer_reset(sip_framer_t *f)
{
    f->have = 0;
}

int sip_framer_append(sip_framer_t *f, const void *data, int len)
{
    if (len <= 0) return 0;
    if (f->have + len > f->cap) return -1;
    memcpy(f->buf + f->have, data, len);
    f->have += len;
    return 0;
}

// Locate the "\r\n\r\n" that ends the SIP header block. Returns the
// offset of the first body byte (or one past the end if there is no
// body), or -1 if the terminator is not yet in the buffer.
static int find_headers_end(const char *p, int len)
{
    for (int i = 0; i + 3 < len; i++) {
        if (p[i] == '\r' && p[i+1] == '\n'
         && p[i+2] == '\r' && p[i+3] == '\n')
            return i + 4;
    }
    return -1;
}

// Read the Content-Length value from the header block. Returns:
//   >=0 the parsed length
//    -1 absent (caller treats as 0; some registrars omit it on empty bodies)
//    -2 malformed (non-numeric, or absurdly large)
static int parse_content_length(const char *p, int len)
{
    // Compact form "l:" (RFC 3261 §7.3.3) is allowed by spec but no
    // mainstream registrar emits it; keeping the parse simple.
    const char *v = find_header(p, len, "Content-Length");
    if (!v) return -1;
    int val = 0;
    bool any = false;
    while (v < p + len && *v >= '0' && *v <= '9') {
        val = val * 10 + (*v - '0');
        if (val > (1 << 20)) return -2;  // 1 MiB cap; SIP messages never approach this
        v++;
        any = true;
    }
    if (!any) return -2;
    return val;
}

int sip_framer_pop(sip_framer_t *f, char *out, int out_cap)
{
    // RFC 3261 §7.5 / RFC 5626 §4.4.1: leading CRLFs before the start-line
    // are keep-alives, not part of the next message. A CRLFCRLF is a PING
    // (popped as a 4-byte frame below so the caller can pong it); a lone
    // CRLF is a PONG answering our own ping. Surface the lone CRLF as its
    // own 2-byte frame immediately — so the caller can log the inbound pong
    // the moment it arrives (live ping↔pong correlation), AND so it can
    // never glue onto the next message's start-line ("\r\nSIP/2.0 200 OK…"
    // → bogus "rejected: -1", the ~30 min re-REGISTER failure on the
    // persistent Telekom connection).
    //
    // Surfacing without waiting means a CRLFCRLF ping split across two
    // recv()s (its two CRLFs in separate reads) is seen as two pongs and
    // not answered — harmless and very rare (a 4-byte ping rides one TLS
    // record), and worth it for the visibility.
    if (f->have >= 2 && f->buf[0] == '\r' && f->buf[1] == '\n'
            && !(f->have >= 4 && f->buf[2] == '\r' && f->buf[3] == '\n')) {
        if (out_cap < 3) return -1;
        out[0] = '\r'; out[1] = '\n'; out[2] = '\0';
        int rest = f->have - 2;
        if (rest > 0) memmove(f->buf, f->buf + 2, rest);
        f->have = rest;
        return 2;
    }

    int hdr_end = find_headers_end(f->buf, f->have);
    if (hdr_end < 0) return 0;

    int body_len = parse_content_length(f->buf, hdr_end);
    if (body_len == -1) body_len = 0;
    if (body_len < 0)   return -1;

    int total = hdr_end + body_len;
    if (f->have < total) return 0;
    if (total >= out_cap) return -1;

    memcpy(out, f->buf, total);
    out[total] = '\0';

    int leftover = f->have - total;
    if (leftover > 0) memmove(f->buf, f->buf + total, leftover);
    f->have = leftover;
    return total;
}
