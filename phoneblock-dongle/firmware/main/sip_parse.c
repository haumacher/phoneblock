#include "sip_parse.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

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

int parse_reason_phrase(const char *resp, int len, char *out, int cap)
{
    if (cap > 0) out[0] = '\0';
    if (cap <= 0 || parse_status_code(resp, len) < 0) return 0;
    // Status line: "SIP/2.0" SP code SP reason-phrase. Skip the version
    // and the 3-digit code, then the single SP before the phrase.
    const char *p   = resp + 8;          // past "SIP/2.0 "
    const char *end = resp + len;
    while (p < end && *p != ' ' && *p != '\r' && *p != '\n') p++;  // code
    while (p < end && *p == ' ') p++;                              // SP(s)
    int n = 0;
    while (p < end && *p != '\r' && *p != '\n' && n < cap - 1) {
        out[n++] = *p++;
    }
    out[n] = '\0';
    return n;
}

// Parse a non-negative integer at *pp up to end. Advances *pp past the
// digits. Returns -1 if no digits, otherwise the parsed value clamped
// to 30 days.
static int parse_uint_clamped(const char **pp, const char *end)
{
    const char *p = *pp;
    if (p >= end || *p < '0' || *p > '9') return -1;
    const int max_seconds = 86400 * 30;
    int n = 0;
    while (p < end && *p >= '0' && *p <= '9') {
        n = n * 10 + (*p - '0');
        if (n > max_seconds) n = max_seconds;
        p++;
    }
    *pp = p;
    return n;
}

// Extract the contact-level ";expires=<n>" from a single Contact header
// value [c, eol). Returns -1 when the parameter is absent.
static int contact_expires(const char *c, const char *eol)
{
    for (const char *q = c; q + 9 <= eol; q++) {
        if (q[0] == ';' && strncasecmp(q + 1, "expires=", 8) == 0) {
            const char *p = q + 9;
            return parse_uint_clamped(&p, eol);
        }
    }
    return -1;
}

int parse_register_expires(const char *resp, int resp_len, const char *instance)
{
    const char *end = resp + resp_len;
    size_t inst_len = (instance && instance[0]) ? strlen(instance) : 0;

    // A 200 OK to REGISTER echoes ALL bindings currently held on the AOR,
    // not just ours — on a Telekom line shared with the router's own VoIP
    // (Fritz!Box / Speedport) that means several Contact headers with
    // different ";expires=" values. We must read the lease of OUR binding,
    // identified by the +sip.instance UUID we registered with; reading the
    // first Contact instead picks up a foreign device's (longer) grant and
    // makes us refresh too late (the binding lapses → missed inbound calls).
    int  first_contact_expires = -1;
    int  contact_count         = 0;
    bool have_match            = false;
    int  match_expires        = -1;

    const char *p = resp;
    while (p < end && *p != '\n') p++;   // skip the status line
    if (p < end) p++;

    while (p < end) {
        const char *eol = p;
        while (eol < end && *eol != '\r' && *eol != '\n') eol++;

        if ((size_t)(end - p) >= 8 && strncasecmp(p, "Contact:", 8) == 0) {
            const char *c = p + 8;
            while (c < eol && (*c == ' ' || *c == '\t')) c++;
            int exp = contact_expires(c, eol);
            contact_count++;
            if (contact_count == 1) first_contact_expires = exp;
            if (inst_len && !have_match) {
                for (const char *r = c; r + inst_len <= eol; r++) {
                    if (strncasecmp(r, instance, inst_len) == 0) {
                        have_match   = true;
                        match_expires = exp;
                        break;
                    }
                }
            }
        }

        p = eol;
        while (p < end && (*p == '\r' || *p == '\n')) p++;
    }

    // Our own binding's lease wins. If we matched it but it carried no
    // ";expires=", fall through to the top-level Expires header.
    if (have_match && match_expires >= 0) return match_expires;
    if (inst_len == 0) {
        // Caller opted out of disambiguation: legacy "first Contact wins".
        if (first_contact_expires >= 0) return first_contact_expires;
    } else if (!have_match && contact_count == 1 && first_contact_expires >= 0) {
        // Instance given but not echoed: a lone Contact can only be ours;
        // never guess from one of several foreign bindings.
        return first_contact_expires;
    }

    const char *e = find_header(resp, resp_len, "Expires");
    if (e) {
        int n = parse_uint_clamped(&e, end);
        if (n >= 0) return n;
    }

    return -1;
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

int parse_via_received(const char *resp, int resp_len, char *out, int cap)
{
    out[0] = '\0';
    const char *via = find_header(resp, resp_len, "Via");
    if (!via) return 0;
    // Bound the scan to this single Via line.
    const char *end = resp + resp_len;
    const char *eol = via;
    while (eol < end && *eol != '\r' && *eol != '\n') eol++;
    const char *p = via;
    while (p + 10 < eol) {   // strlen(";received=") == 10
        if (strncasecmp(p, ";received=", 10) == 0) {
            p += 10;
            int n = 0;
            while (p < eol && *p != ';' && *p != ' ' && *p != '\t'
                   && *p != '\r' && *p != '\n' && n < cap - 1) {
                out[n++] = *p++;
            }
            out[n] = '\0';
            return n > 0 ? 1 : 0;
        }
        p++;
    }
    return 0;
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

int parse_sdp_audio_savp(const char *msg, int msg_len)
{
    const char *p = find_sdp_line(msg, msg_len, "m=audio ");
    if (!p) return 0;
    const char *end = msg + msg_len;
    // Skip the port number and the single space after it.
    while (p < end && *p >= '0' && *p <= '9') p++;
    while (p < end && *p == ' ') p++;
    // The transport is the next whitespace-delimited token.
    return (size_t)(end - p) >= 8 && strncmp(p, "RTP/SAVP", 8) == 0;
}

int parse_sdp_crypto(const char *msg, int msg_len, char *key_b64, int cap)
{
    if (key_b64 && cap > 0) key_b64[0] = '\0';

    const char *p = msg;
    const char *end = msg + msg_len;
    bool at_line_start = true;
    while (p < end) {
        if (at_line_start && (size_t)(end - p) >= 9
            && strncmp(p, "a=crypto:", 9) == 0) {
            const char *q = p + 9;
            // Bound the scan to this single line.
            const char *eol = q;
            while (eol < end && *eol != '\r' && *eol != '\n') eol++;

            // Parse the crypto tag (decimal).
            int tag = 0;
            bool saw_digit = false;
            while (q < eol && *q >= '0' && *q <= '9') {
                tag = tag * 10 + (*q - '0');
                saw_digit = true;
                q++;
            }
            // One space, then the crypto-suite token.
            if (saw_digit && tag > 0 && q < eol && *q == ' ') {
                q++;
                const char suite[] = "AES_CM_128_HMAC_SHA1_80";
                size_t slen = sizeof(suite) - 1;
                if ((size_t)(eol - q) >= slen
                    && strncmp(q, suite, slen) == 0
                    && (q[slen] == ' ' || q + slen == eol)) {
                    // Supported suite. Extract the inline: key parameter.
                    for (const char *r = q; r + 7 <= eol; r++) {
                        if (strncmp(r, "inline:", 7) == 0) {
                            r += 7;
                            int n = 0;
                            // The inline value ends at '|' (lifetime/MKI),
                            // whitespace, or end of line.
                            while (r < eol && *r != '|' && *r != ' '
                                   && *r != '\t' && key_b64 && n < cap - 1) {
                                key_b64[n++] = *r++;
                            }
                            if (key_b64 && cap > 0) key_b64[n] = '\0';
                            break;
                        }
                    }
                    return tag;
                }
            }
            p = eol;
            continue;
        }
        at_line_start = (*p == '\n');
        p++;
    }
    return 0;
}
