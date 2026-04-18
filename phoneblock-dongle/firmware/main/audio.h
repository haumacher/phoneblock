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
