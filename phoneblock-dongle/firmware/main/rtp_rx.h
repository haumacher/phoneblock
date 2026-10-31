#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// Parsing of a received (plain, or already SRTP-decrypted) RTP packet
// (RFC 3550): the payload behind the fixed header, the CSRC list and an
// optional header extension, without the padding. Pure, host-tested in
// test/test_dialog.c.
//
// Returns true and points *payload / *n at the payload if `pkt` is a
// well-formed version-2 packet of payload type `want_pt` (8 = PCMA) with a
// non-empty payload; false otherwise (another codec such as a DTMF
// telephone-event, comfort noise, a STUN packet, or garbage).
bool rtp_rx_payload(const uint8_t *pkt, size_t len, int want_pt,
                    const uint8_t **payload, size_t *n);
