#pragma once

#include <stdint.h>

// Encode a single 16-bit signed-linear PCM sample to an 8-bit G.711
// A-law byte (payload type 8 in RTP/AVP). Based on the ITU-T G.711
// reference; pure C, host-testable.
//
// Verified reference vectors:
//   0       → 0xD5   (zero / silence)
//    32767  → 0xAA   (positive full scale)
//   -32768  → 0x2A   (negative full scale)
uint8_t pcm_to_alaw(int16_t pcm);

// Decode a single 8-bit G.711 A-law byte back to a 16-bit signed-linear
// PCM sample — the inverse of pcm_to_alaw, used on the receive path to
// turn the caller's incoming PCMA RTP into linear samples for energy /
// VAD analysis. Pure C, host-testable.
//
// Verified reference vectors (round-trip of pcm_to_alaw's):
//   0xD5 → 8      (decoded zero / silence; A-law has no exact 0)
//   0xAA → 32256  (positive full scale)
//   0x2A → -32256 (negative full scale)
int16_t alaw_to_pcm(uint8_t alaw);
