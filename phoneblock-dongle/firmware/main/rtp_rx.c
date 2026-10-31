// Implementation of rtp_rx.h.

#include "rtp_rx.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

#define RTP_FIXED_HEADER 12

bool rtp_rx_payload(const uint8_t *pkt, size_t len, int want_pt,
                    const uint8_t **payload, size_t *n)
{
    if (len < RTP_FIXED_HEADER) return false;
    if ((pkt[0] >> 6) != 2) return false;               // version
    if ((pkt[1] & 0x7F) != want_pt) return false;       // payload type

    size_t hdr = RTP_FIXED_HEADER + 4u * (pkt[0] & 0x0F);   // + CSRC list
    if (pkt[0] & 0x10) {                                 // header extension
        if (len < hdr + 4) return false;
        size_t words = ((size_t)pkt[hdr + 2] << 8) | pkt[hdr + 3];
        hdr += 4 + 4 * words;
    }
    size_t end = len;
    if (pkt[0] & 0x20) {                                 // padding
        size_t pad = pkt[len - 1];
        if (pad == 0 || pad > len) return false;
        end = len - pad;
    }
    if (end <= hdr) return false;
    *payload = pkt + hdr;
    *n = end - hdr;
    return true;
}
