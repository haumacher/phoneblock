#include "net_local.h"

#include <string.h>
#include <ctype.h>

// inet_pton + AF_INET* live in different headers on the two targets this
// file compiles for: LWIP (firmware) vs. the host libc (test harness).
#ifdef ESP_PLATFORM
#include "lwip/sockets.h"
#else
#include <arpa/inet.h>
#endif

// --- Parsing --------------------------------------------------------

static void set_v4_mapped(net_addr_t *out, const uint8_t v4[4])
{
    memset(out->bytes, 0, 10);
    out->bytes[10] = 0xff;
    out->bytes[11] = 0xff;
    memcpy(out->bytes + 12, v4, 4);
}

bool netloc_parse_ip(const char *s, net_addr_t *out)
{
    if (!s || !*s || !out) return false;

    uint8_t v6[16];
    if (inet_pton(AF_INET6, s, v6) == 1) {
        memcpy(out->bytes, v6, 16);
        return true;
    }
    uint8_t v4[4];
    if (inet_pton(AF_INET, s, v4) == 1) {
        set_v4_mapped(out, v4);
        return true;
    }
    return false;
}

// --- Classification -------------------------------------------------

static bool is_v4_mapped(const uint8_t b[16])
{
    static const uint8_t prefix[12] = {0,0,0,0,0,0,0,0,0,0,0xff,0xff};
    return memcmp(b, prefix, 12) == 0;
}

// Compare the first `bits` bits of two byte arrays.
static bool prefix_eq(const uint8_t *a, const uint8_t *b, int bits)
{
    int whole = bits / 8;
    int rem   = bits % 8;
    if (whole && memcmp(a, b, whole) != 0) return false;
    if (rem) {
        uint8_t mask = (uint8_t)(0xff << (8 - rem));
        if ((a[whole] & mask) != (b[whole] & mask)) return false;
    }
    return true;
}

static bool v4_is_local(const uint8_t v4[4], const netloc_self_t *self)
{
    // 127.0.0.0/8 loopback
    if (v4[0] == 127) return true;
    // 10.0.0.0/8
    if (v4[0] == 10) return true;
    // 172.16.0.0/12
    if (v4[0] == 172 && (v4[1] & 0xf0) == 16) return true;
    // 192.168.0.0/16
    if (v4[0] == 192 && v4[1] == 168) return true;
    // 169.254.0.0/16 link-local
    if (v4[0] == 169 && v4[1] == 254) return true;
    // Same subnet as the dongle's own IPv4.
    if (self && self->have_v4) {
        bool same = true;
        for (int i = 0; i < 4; i++) {
            if ((v4[i] & self->v4_mask[i]) !=
                (self->v4_addr[i] & self->v4_mask[i])) {
                same = false;
                break;
            }
        }
        if (same) return true;
    }
    // Everything else (public, and CGNAT 100.64/10) is remote.
    return false;
}

bool netloc_is_local(const net_addr_t *a, const netloc_self_t *self)
{
    if (!a) return false;

    if (is_v4_mapped(a->bytes)) {
        return v4_is_local(a->bytes + 12, self);
    }

    // Pure IPv6.
    static const uint8_t loopback[16] = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1};
    if (memcmp(a->bytes, loopback, 16) == 0) return true;      // ::1
    if (a->bytes[0] == 0xfe && (a->bytes[1] & 0xc0) == 0x80) return true; // fe80::/10
    if ((a->bytes[0] & 0xfe) == 0xfc) return true;             // fc00::/7 ULA

    if (self) {
        for (int i = 0; i < self->n_v6; i++) {
            int bits = self->v6[i].prefix_len ? self->v6[i].prefix_len : 64;
            if (prefix_eq(a->bytes, self->v6[i].addr, bits)) return true;
        }
    }
    return false;
}

// --- Forwarding-header parsing --------------------------------------

// Trim ASCII spaces/tabs from both ends of [*p, *end).
static void trim(const char **p, const char **end)
{
    while (*p < *end && (**p == ' ' || **p == '\t')) (*p)++;
    while (*end > *p && ((*end)[-1] == ' ' || (*end)[-1] == '\t')) (*end)--;
}

// Copy [p, end) into buf (NUL-terminated), truncating to cap.
static void copy_span(const char *p, const char *end, char *buf, size_t cap)
{
    size_t n = (size_t)(end - p);
    if (n >= cap) n = cap - 1;
    memcpy(buf, p, n);
    buf[n] = '\0';
}

bool netloc_xff_rightmost(const char *xff, net_addr_t *out)
{
    if (!xff || !out) return false;
    const char *end = xff + strlen(xff);
    // Drop trailing separators/whitespace so a trailing comma yields the
    // last non-empty token rather than an empty one.
    while (end > xff &&
           (end[-1] == ' ' || end[-1] == '\t' || end[-1] == ',')) end--;
    // Rightmost token starts after the last comma.
    const char *p = end;
    while (p > xff && p[-1] != ',') p--;
    const char *tp = p, *te = end;
    trim(&tp, &te);
    if (tp == te) return false;
    char buf[64];
    copy_span(tp, te, buf, sizeof(buf));
    return netloc_parse_ip(buf, out);
}

// Normalize a single Forwarded `for=` value (already isolated, may carry
// quotes, [brackets] and :port) into an IP literal, then parse it.
static bool parse_forwarded_value(const char *p, const char *end,
                                  net_addr_t *out)
{
    trim(&p, &end);
    // Strip surrounding double quotes.
    if (end - p >= 2 && *p == '"' && end[-1] == '"') { p++; end--; }
    trim(&p, &end);
    if (p == end) return false;

    char buf[80];
    if (*p == '[') {
        // "[v6]" or "[v6]:port" — take between the brackets.
        const char *rb = p + 1;
        while (rb < end && *rb != ']') rb++;
        if (rb >= end) return false;
        copy_span(p + 1, rb, buf, sizeof(buf));
    } else {
        // Bare token. A dotted-quad may carry ":port"; a bare IPv6 uses
        // colons as part of the address, so only strip a port when there
        // is exactly one colon (i.e. IPv4:port).
        int colons = 0;
        for (const char *q = p; q < end; q++) if (*q == ':') colons++;
        const char *ve = end;
        if (colons == 1) {
            const char *c = p;
            while (c < end && *c != ':') c++;
            ve = c;
        }
        copy_span(p, ve, buf, sizeof(buf));
    }
    return netloc_parse_ip(buf, out);
}

bool netloc_forwarded_last(const char *forwarded, net_addr_t *out)
{
    if (!forwarded || !out) return false;
    const char *end = forwarded + strlen(forwarded);

    // Walk all "for=" occurrences (case-insensitive), remember the last
    // that parses. Elements are separated by ',' or ';'.
    bool found = false;
    net_addr_t last;
    for (const char *p = forwarded; p < end; p++) {
        if ((p[0] == 'f' || p[0] == 'F') &&
            (p[1] == 'o' || p[1] == 'O') &&
            (p[2] == 'r' || p[2] == 'R') &&
             p + 3 < end && p[3] == '=') {
            const char *v = p + 4;
            // Value runs to the next ';' or ',' that is not inside quotes.
            const char *ve = v;
            bool inq = false;
            while (ve < end) {
                if (*ve == '"') inq = !inq;
                else if (!inq && (*ve == ';' || *ve == ',')) break;
                ve++;
            }
            net_addr_t tmp;
            if (parse_forwarded_value(v, ve, &tmp)) {
                last = tmp;
                found = true;
            }
            p = ve;   // continue scanning after this element
        }
    }
    if (found) *out = last;
    return found;
}
