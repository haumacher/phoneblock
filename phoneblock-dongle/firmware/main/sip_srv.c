#include "sip_srv.h"

#include <stdio.h>
#include <string.h>

// ---------------------------------------------------------------------------
// DNS wire-format helpers (pure C, host-testable)
// ---------------------------------------------------------------------------

// Decode a DNS name starting at offset 'pos' in 'msg'. Output is the
// dotted string. Follows RFC 1035 §4.1.4 compression pointers, with a
// loop guard. Returns the offset right after the encoded name in the
// original message (i.e. the cursor for the next field), or -1 on
// malformed input. The cursor returned is for the *first* encoding
// the parser saw, even if it followed pointers — which is what we
// need for sequential record walking.
static int dns_decode_name(const uint8_t *msg, int msg_len, int pos,
                           char *out, int out_cap)
{
    int written = 0;
    int cursor_after = -1;
    int hops = 0;

    if (out_cap > 0) out[0] = '\0';

    while (pos >= 0 && pos < msg_len && hops < 16) {
        uint8_t b = msg[pos];
        if ((b & 0xC0) == 0xC0) {
            if (pos + 1 >= msg_len) return -1;
            int newpos = ((b & 0x3F) << 8) | msg[pos + 1];
            if (cursor_after < 0) cursor_after = pos + 2;
            pos = newpos;
            hops++;
            continue;
        }
        if ((b & 0xC0) != 0) return -1;  // 0x40/0x80 reserved
        if (b == 0) {
            if (out_cap > 0) {
                int term = written < out_cap ? written : out_cap - 1;
                out[term] = '\0';
            }
            return cursor_after >= 0 ? cursor_after : pos + 1;
        }
        int len = b;
        if (pos + 1 + len > msg_len) return -1;
        if (written > 0 && written < out_cap - 1) out[written++] = '.';
        for (int i = 0; i < len && written < out_cap - 1; i++) {
            out[written++] = (char)msg[pos + 1 + i];
        }
        pos += 1 + len;
    }
    return -1;
}

// ---------------------------------------------------------------------------
// Response parser
// ---------------------------------------------------------------------------

int sip_srv_parse(const uint8_t *resp, int resp_len,
                  sip_srv_record_t *records, int max)
{
    if (resp_len < 12) return -1;

    uint16_t flags   = ((uint16_t)resp[2] << 8) | resp[3];
    uint16_t qdcount = ((uint16_t)resp[4] << 8) | resp[5];
    uint16_t ancount = ((uint16_t)resp[6] << 8) | resp[7];

    // RCODE != 0 means the resolver could not answer (NXDOMAIN, SERVFAIL).
    // Treat any non-zero RCODE as "no records" rather than malformed.
    if ((flags & 0x000F) != 0) return 0;

    int pos = 12;
    char tmp[SIP_SRV_TARGET_MAX];

    for (int i = 0; i < qdcount; i++) {
        pos = dns_decode_name(resp, resp_len, pos, tmp, sizeof(tmp));
        if (pos < 0 || pos + 4 > resp_len) return -1;
        pos += 4;  // QTYPE + QCLASS
    }

    int n = 0;
    for (int i = 0; i < ancount && n < max; i++) {
        pos = dns_decode_name(resp, resp_len, pos, tmp, sizeof(tmp));
        if (pos < 0 || pos + 10 > resp_len) return -1;
        uint16_t type  = ((uint16_t)resp[pos]     << 8) | resp[pos + 1];
        // class (2) + ttl (4) skipped
        uint16_t rdlen = ((uint16_t)resp[pos + 8] << 8) | resp[pos + 9];
        pos += 10;
        if (pos + rdlen > resp_len) return -1;

        // SRV = 33. Other types (CNAME, A, …) silently skipped.
        if (type == 33 && rdlen >= 7) {
            sip_srv_record_t *r = &records[n];
            r->priority = ((uint16_t)resp[pos]     << 8) | resp[pos + 1];
            r->weight   = ((uint16_t)resp[pos + 2] << 8) | resp[pos + 3];
            r->port     = ((uint16_t)resp[pos + 4] << 8) | resp[pos + 5];
            int target_after = dns_decode_name(resp, resp_len, pos + 6,
                                               r->target, sizeof(r->target));
            if (target_after < 0) return -1;
            // Strip trailing dot the resolver may have left in place.
            int tlen = (int)strlen(r->target);
            if (tlen > 0 && r->target[tlen - 1] == '.') {
                r->target[tlen - 1] = '\0';
            }
            n++;
        }
        pos += rdlen;
    }
    return n;
}

// ---------------------------------------------------------------------------
// RFC 2782 selection
// ---------------------------------------------------------------------------

int sip_srv_pick(const sip_srv_record_t *records, int n, uint32_t rnd)
{
    if (n <= 0) return -1;

    uint16_t min_prio = 0xFFFF;
    for (int i = 0; i < n; i++) {
        if (records[i].priority < min_prio) min_prio = records[i].priority;
    }

    uint32_t total = 0;
    for (int i = 0; i < n; i++) {
        if (records[i].priority == min_prio) total += records[i].weight;
    }

    // RFC 2782 special case: zero total weight → first record wins.
    if (total == 0) {
        for (int i = 0; i < n; i++) {
            if (records[i].priority == min_prio) return i;
        }
        return -1;
    }

    uint32_t target = rnd % total;
    uint32_t cum = 0;
    for (int i = 0; i < n; i++) {
        if (records[i].priority != min_prio) continue;
        cum += records[i].weight;
        if (cum > target) return i;
    }
    // Floating-point-style rounding edge — fall back to first match.
    for (int i = 0; i < n; i++) {
        if (records[i].priority == min_prio) return i;
    }
    return -1;
}

// ---------------------------------------------------------------------------
// On-device lookup (UDP/53 against the system resolver). Compiled out
// on host builds so the parser/picker can be unit-tested without IDF.
// ---------------------------------------------------------------------------

#ifdef ESP_PLATFORM

#include "esp_log.h"
#include "esp_random.h"

#include "lwip/sockets.h"
#include "lwip/dns.h"
#include "lwip/inet.h"
#include "lwip/ip_addr.h"

static const char *TAG = "sip_srv";

// Encode "_sips._tcp.tel.t-online.de" into DNS labels:
// 05 _sips 04 _tcp 03 tel 09 t-online 02 de 00
// Returns the number of bytes written, or 0 on failure (label too
// long, capacity exhausted, or empty/leading-dot input).
static int dns_encode_name(const char *name, uint8_t *out, int cap)
{
    int n = 0;
    const char *p = name;
    while (*p) {
        const char *dot = strchr(p, '.');
        int len = dot ? (int)(dot - p) : (int)strlen(p);
        if (len <= 0 || len > 63) return 0;
        if (n + 1 + len > cap) return 0;
        out[n++] = (uint8_t)len;
        memcpy(out + n, p, len);
        n += len;
        p = dot ? dot + 1 : p + len;
    }
    if (n + 1 > cap) return 0;
    out[n++] = 0;
    return n;
}

bool sip_srv_lookup(const char *service, const char *proto,
                    const char *domain,
                    char *target_out, int target_cap,
                    int *port_out)
{
    if (!service || !proto || !domain || !domain[0]
     || !target_out || target_cap <= 0 || !port_out) {
        return false;
    }

    // Resolver must be configured (DHCP usually fills this in).
    const ip_addr_t *dns_ip = dns_getserver(0);
    if (!dns_ip || ip_addr_isany(dns_ip)) {
        ESP_LOGD(TAG, "no DNS server configured; skipping SRV");
        return false;
    }

    // Build query.
    char qname[128];
    int qn = snprintf(qname, sizeof(qname), "_%s._%s.%s",
                      service, proto, domain);
    if (qn <= 0 || qn >= (int)sizeof(qname)) return false;

    uint8_t pkt[256];
    int n = 0;
    uint16_t txid = (uint16_t)esp_random();
    pkt[n++] = (uint8_t)(txid >> 8);
    pkt[n++] = (uint8_t)(txid & 0xFF);
    pkt[n++] = 0x01; pkt[n++] = 0x00;  // flags: standard query, RD=1
    pkt[n++] = 0x00; pkt[n++] = 0x01;  // QDCOUNT
    pkt[n++] = 0x00; pkt[n++] = 0x00;  // ANCOUNT
    pkt[n++] = 0x00; pkt[n++] = 0x00;  // NSCOUNT
    pkt[n++] = 0x00; pkt[n++] = 0x00;  // ARCOUNT
    int name_n = dns_encode_name(qname, pkt + n, sizeof(pkt) - n - 4);
    if (name_n <= 0) return false;
    n += name_n;
    pkt[n++] = 0x00; pkt[n++] = 33;    // QTYPE = SRV
    pkt[n++] = 0x00; pkt[n++] = 0x01;  // QCLASS = IN

    int s = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (s < 0) return false;

    struct sockaddr_in srv = {
        .sin_family      = AF_INET,
        .sin_port        = htons(53),
        .sin_addr.s_addr = ip4_addr_get_u32(ip_2_ip4(dns_ip)),
    };

    bool ok = false;
    if (sendto(s, pkt, n, 0, (struct sockaddr *)&srv, sizeof(srv)) < 0) {
        ESP_LOGW(TAG, "DNS sendto failed");
        close(s);
        return false;
    }

    struct timeval tv = { .tv_sec = 2, .tv_usec = 0 };
    setsockopt(s, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));

    uint8_t resp[1024];
    int r = recv(s, resp, sizeof(resp), 0);
    close(s);
    if (r <= 0) {
        ESP_LOGD(TAG, "SRV %s: no response (or timeout)", qname);
        return false;
    }
    if (r < 12 || (((uint16_t)resp[0] << 8) | resp[1]) != txid) {
        ESP_LOGW(TAG, "SRV %s: txid mismatch", qname);
        return false;
    }

    sip_srv_record_t recs[SIP_SRV_MAX_RECORDS];
    int rn = sip_srv_parse(resp, r, recs, SIP_SRV_MAX_RECORDS);
    if (rn <= 0) {
        ESP_LOGD(TAG, "SRV %s: %d records (no usable answer)", qname, rn);
        return false;
    }

    int idx = sip_srv_pick(recs, rn, esp_random());
    if (idx < 0) return false;

    strncpy(target_out, recs[idx].target, target_cap - 1);
    target_out[target_cap - 1] = '\0';
    *port_out = recs[idx].port;

    ESP_LOGI(TAG, "SRV %s → %s:%d (priority=%u weight=%u of %d records)",
             qname, target_out, *port_out,
             recs[idx].priority, recs[idx].weight, rn);
    ok = true;
    return ok;
}

#endif  // ESP_PLATFORM
