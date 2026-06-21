#include "improv_proto.h"

#include <string.h>

static const uint8_t HEADER[6] = { 'I', 'M', 'P', 'R', 'O', 'V' };

#define IMPROV_VERSION 1

void improv_parser_reset(improv_parser_t *p)
{
    p->len = 0;
    p->expected = 0;
}

// Restart header matching with the just-rejected byte: a packet may
// legitimately start right after garbage that ended in 'I' look-alikes
// ("…IIMPROV…"), so the byte that broke the previous match can still
// open a new one.
static void resync(improv_parser_t *p, uint8_t byte)
{
    p->len = 0;
    p->expected = 0;
    if (byte == HEADER[0]) {
        p->buf[0] = byte;
        p->len = 1;
    }
}

bool improv_parser_feed(improv_parser_t *p, uint8_t byte,
                        uint8_t *type, const uint8_t **data,
                        uint8_t *data_len)
{
    if (p->len < sizeof(HEADER)) {
        if (byte != HEADER[p->len]) {
            resync(p, byte);
            return false;
        }
        p->buf[p->len++] = byte;
        return false;
    }

    p->buf[p->len++] = byte;

    if (p->len == 9) {
        if (p->buf[6] != IMPROV_VERSION) {
            resync(p, byte);
            return false;
        }
        // length is one byte, so 9 + length + 1 always fits buf.
        p->expected = 9 + (size_t)p->buf[8] + 1;
        return false;
    }
    if (p->len < 9 || p->len < p->expected) {
        return false;
    }

    // Complete packet: verify checksum over everything before it.
    uint8_t sum = 0;
    for (size_t i = 0; i + 1 < p->expected; i++) {
        sum += p->buf[i];
    }
    bool ok = (sum == p->buf[p->expected - 1]);
    if (ok) {
        *type = p->buf[7];
        *data_len = p->buf[8];
        *data = &p->buf[9];
    }
    // Returned pointers reference p->buf; they stay valid until the
    // next feed call appends bytes (which starts over at len = 0).
    p->len = 0;
    p->expected = 0;
    return ok;
}

static size_t build(uint8_t pkt_type, const uint8_t *payload, size_t n,
                    uint8_t *out, size_t cap)
{
    size_t total = 9 + n + 1;
    if (n > 255 || cap < total) {
        return 0;
    }
    memcpy(out, HEADER, sizeof(HEADER));
    out[6] = IMPROV_VERSION;
    out[7] = pkt_type;
    out[8] = (uint8_t)n;
    memcpy(&out[9], payload, n);

    uint8_t sum = 0;
    for (size_t i = 0; i + 1 < total; i++) {
        sum += out[i];
    }
    out[total - 1] = sum;
    return total;
}

size_t improv_build_current_state(uint8_t state, uint8_t *out, size_t cap)
{
    return build(IMPROV_TYPE_CURRENT_STATE, &state, 1, out, cap);
}

size_t improv_build_error(uint8_t error, uint8_t *out, size_t cap)
{
    return build(IMPROV_TYPE_ERROR_STATE, &error, 1, out, cap);
}

size_t improv_build_rpc_result(uint8_t command,
                               const char *const *strings, size_t n_strings,
                               uint8_t *out, size_t cap)
{
    uint8_t payload[255];
    size_t n = 0;

    payload[n++] = command;
    payload[n++] = 0;  // strings_total_len, patched below

    for (size_t i = 0; i < n_strings; i++) {
        size_t slen = strlen(strings[i]);
        if (slen > 255 || n + 1 + slen > sizeof(payload)) {
            return 0;
        }
        payload[n++] = (uint8_t)slen;
        memcpy(&payload[n], strings[i], slen);
        n += slen;
    }

    size_t strings_total = n - 2;
    if (strings_total > 255) {
        return 0;
    }
    payload[1] = (uint8_t)strings_total;

    return build(IMPROV_TYPE_RPC_RESULT, payload, n, out, cap);
}

bool improv_parse_wifi_settings(const uint8_t *data, uint8_t data_len,
                                char *ssid, size_t ssid_cap,
                                char *pass, size_t pass_cap)
{
    if (data_len < 1) {
        return false;
    }
    size_t ssid_len = data[0];
    if ((size_t)data_len < 1 + ssid_len + 1) {
        return false;
    }
    size_t pass_len = data[1 + ssid_len];
    if ((size_t)data_len != 1 + ssid_len + 1 + pass_len) {
        return false;
    }
    if (ssid_len + 1 > ssid_cap || pass_len + 1 > pass_cap) {
        return false;
    }
    memcpy(ssid, &data[1], ssid_len);
    ssid[ssid_len] = '\0';
    memcpy(pass, &data[1 + ssid_len + 1], pass_len);
    pass[pass_len] = '\0';
    return true;
}
