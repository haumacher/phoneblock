#include "sip_parse.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>

const char *find_header(const char *msg, int msg_len, const char *name)
{
    size_t name_len = strlen(name);
    const char *p = msg;
    const char *end = msg + msg_len;
    // Skip first line.
    while (p < end && *p != '\n') p++;
    if (p < end) p++;
    while (p + name_len + 1 < end) {
        if (strncasecmp(p, name, name_len) == 0 && p[name_len] == ':') {
            p += name_len + 1;
            while (p < end && (*p == ' ' || *p == '\t')) p++;
            return p;
        }
        while (p < end && *p != '\n') p++;
        if (p < end) p++;
    }
    return NULL;
}

int header_value(const char *p, const char *end, char *out, int cap)
{
    int n = 0;
    while (p < end && *p != '\r' && *p != '\n' && n < cap - 1) {
        out[n++] = *p++;
    }
    out[n] = '\0';
    return n;
}

int parse_method(const char *pkt, int len, char *method, int cap)
{
    int i = 0;
    while (i < len && i < cap - 1 && pkt[i] != ' ' && pkt[i] != '\r' && pkt[i] != '\n') {
        method[i] = pkt[i];
        i++;
    }
    method[i] = '\0';
    return i;
}

int parse_status_code(const char *resp, int len)
{
    if (len < 12) return -1;
    if (strncmp(resp, "SIP/2.0 ", 8) != 0) return -1;
    int status = 0;
    for (int i = 8; i < len && i < 12; i++) {
        if (resp[i] >= '0' && resp[i] <= '9') {
            status = status * 10 + (resp[i] - '0');
        } else break;
    }
    return status;
}

uint32_t parse_cseq(const char *req, int req_len)
{
    const char *p = find_header(req, req_len, "CSeq");
    if (!p) return 0;
    uint32_t n = 0;
    while (*p >= '0' && *p <= '9') {
        n = n * 10 + (*p - '0');
        p++;
    }
    return n;
}

void parse_call_id(const char *req, int req_len, char *out, int cap)
{
    const char *p = find_header(req, req_len, "Call-ID");
    if (!p) { out[0] = '\0'; return; }
    const char *end = req + req_len;
    int n = 0;
    while (p < end && *p != '\r' && *p != '\n' && n < cap - 1) {
        out[n++] = *p++;
    }
    out[n] = '\0';
    while (n > 0 && (out[n - 1] == ' ' || out[n - 1] == '\t')) out[--n] = '\0';
}

void parse_tag(const char *hdr_val, int val_len, char *out, int cap)
{
    const char *end = hdr_val + val_len;
    const char *p = hdr_val;
    while (p + 5 < end) {
        if (strncasecmp(p, ";tag=", 5) == 0) {
            p += 5;
            int n = 0;
            while (p < end && *p != ';' && *p != ' ' && *p != '\t'
                   && *p != '\r' && *p != '\n' && n < cap - 1) {
                out[n++] = *p++;
            }
            out[n] = '\0';
            return;
        }
        p++;
    }
    out[0] = '\0';
}

void parse_uri(const char *hdr_val, int val_len, char *out, int cap)
{
    const char *end = hdr_val + val_len;
    const char *lt = NULL, *gt = NULL;
    for (const char *p = hdr_val; p < end; p++) {
        if (*p == '<') lt = p + 1;
        else if (*p == '>') { gt = p; break; }
    }
    const char *start, *stop;
    if (lt && gt && gt > lt) {
        start = lt; stop = gt;
    } else {
        start = hdr_val;
        while (start + 4 < end && strncasecmp(start, "sip:", 4) != 0) start++;
        if (start + 4 >= end) { out[0] = '\0'; return; }
        stop = start;
        while (stop < end && *stop != ';' && *stop != ' ' && *stop != '\t'
               && *stop != '\r' && *stop != '\n') stop++;
    }
    const char *semi = start;
    while (semi < stop && *semi != ';') semi++;
    if (semi < stop) stop = semi;

    int n = stop - start;
    if (n >= cap) n = cap - 1;
    memcpy(out, start, n);
    out[n] = '\0';
}

int user_from_uri(const char *uri, char *out, int cap)
{
    const char *p = uri;
    if (strncasecmp(p, "sip:", 4) == 0) p += 4;
    else if (strncasecmp(p, "sips:", 5) == 0) p += 5;
    else if (strncasecmp(p, "tel:", 4) == 0) p += 4;
    int n = 0;
    while (*p && *p != '@' && *p != ';' && *p != ':' && n < cap - 1) {
        out[n++] = *p++;
    }
    out[n] = '\0';
    return n;
}

int parse_display_name(const char *hdr_val, int val_len, char *out, int cap)
{
    const char *end = hdr_val + val_len;
    const char *p = hdr_val;
    while (p < end && (*p == ' ' || *p == '\t')) p++;

    if (p < end && *p == '"') {
        p++;
        const char *start = p;
        while (p < end && *p != '"') p++;
        int n = p - start;
        if (n >= cap) n = cap - 1;
        memcpy(out, start, n);
        out[n] = '\0';
        return n;
    }
    if (p < end && *p == '<') {
        out[0] = '\0';
        return 0;
    }
    const char *start = p;
    while (p < end && *p != '<' && *p != '\r' && *p != '\n') p++;
    const char *stop = p;
    while (stop > start && (stop[-1] == ' ' || stop[-1] == '\t')) stop--;
    int n = stop - start;
    if (n >= cap) n = cap - 1;
    memcpy(out, start, n);
    out[n] = '\0';
    return n;
}

bool is_phone_number_like(const char *s)
{
    if (!*s) return false;
    bool saw_digit = false;
    for (const char *p = s; *p; p++) {
        if (*p >= '0' && *p <= '9') { saw_digit = true; continue; }
        if (*p == '+' || *p == ' ' || *p == '-' || *p == '/'
            || *p == '(' || *p == ')' || *p == '.' || *p == '\t') continue;
        return false;
    }
    return saw_digit;
}

bool is_known_contact(const char *display)
{
    if (!display || !*display) return false;
    if (is_phone_number_like(display)) return false;

    // Skip leading whitespace. Fritz!Box does not normally pad display
    // names, but stay defensive against vCard quoting artefacts.
    const char *p = display;
    while (*p == ' ' || *p == '\t') p++;

    // "SPAM" prefix (case-insensitive) marks Fritz!Box phonebook
    // entries that were imported from PhoneBlock's CardDAV blocklist
    // — those still need an API check and a public-detail link.
    if ((p[0] == 'S' || p[0] == 's') &&
        (p[1] == 'P' || p[1] == 'p') &&
        (p[2] == 'A' || p[2] == 'a') &&
        (p[3] == 'M' || p[3] == 'm')) {
        // Match only as a token boundary so a hypothetical real name
        // like "Spamer GmbH" is still treated as a known contact.
        char next = p[4];
        if (next == '\0' || next == ':' || next == ' ' || next == '\t'
            || next == '-' || next == '_' || next == '.') {
            return false;
        }
    }
    return true;
}

bool looks_dialable(const char *number)
{
    if (!*number) return false;
    if (*number == '+') return number[1] >= '0' && number[1] <= '9';
    return *number >= '0' && *number <= '9';
}

void normalize_de(const char *raw, char *out, int cap)
{
    char buf[48];
    int n = 0;
    for (const char *p = raw; *p && n < (int)sizeof(buf) - 1; p++) {
        if (*p == ' ' || *p == '-' || *p == '(' || *p == ')' || *p == '/') continue;
        buf[n++] = *p;
    }
    buf[n] = '\0';

    const char *src = buf;
    int w = 0;

    if (src[0] == '+') {
        // Already in E.164 form — pass through.
    } else if (src[0] == '0' && src[1] == '0') {
        // International with "00" escape ("0049…", "001…") → "+<cc>…".
        out[w++] = '+';
        src += 2;
    } else if (src[0] == '0' && src[1] >= '0' && src[1] <= '9') {
        // National German (single leading zero) → strip zero, prepend +49.
        out[w++] = '+';
        out[w++] = '4';
        out[w++] = '9';
        src += 1;
    }
    // Else (empty, "**622", "*21#", anything not starting with '+' or a
    // digit-with-leading-0) → pass through unchanged; looks_dialable()
    // will reject it.

    while (*src && w < cap - 1) {
        out[w++] = *src++;
    }
    out[w] = '\0';
}

bool same_call_id(const char *a, const char *b)
{
    if (!a || !b || !*a || !*b) return false;
    return strcasecmp(a, b) == 0;
}

// Find a line (LF-separated) that starts with the given prefix and
// return a pointer to the first character *after* the prefix. Matches
// are line-anchored: "c=" on its own line, not embedded inside some
// header value. Returns NULL if no such line exists.
static const char *find_sdp_line(const char *msg, int msg_len,
                                 const char *prefix)
{
    size_t plen = strlen(prefix);
    const char *p = msg;
    const char *end = msg + msg_len;
    bool at_line_start = true;
    while (p < end) {
        if (at_line_start && (size_t)(end - p) >= plen
            && strncmp(p, prefix, plen) == 0) {
            return p + plen;
        }
        at_line_start = (*p == '\n');
        p++;
    }
    return NULL;
}

void parse_sdp_connection_ip(const char *msg, int msg_len, char *out, int cap)
{
    const char *p = find_sdp_line(msg, msg_len, "c=IN IP4 ");
    if (!p) { out[0] = '\0'; return; }
    const char *end = msg + msg_len;
    int n = 0;
    while (p < end && *p != '\r' && *p != '\n' && *p != ' ' && *p != '/'
           && n < cap - 1) {
        out[n++] = *p++;
    }
    out[n] = '\0';
}

int parse_sdp_audio_port(const char *msg, int msg_len)
{
    const char *p = find_sdp_line(msg, msg_len, "m=audio ");
    if (!p) return 0;
    int port = 0;
    while (*p >= '0' && *p <= '9') {
        port = port * 10 + (*p - '0');
        p++;
    }
    return port;
}
