#include "audio.h"

// Straight implementation of the ITU-T G.711 A-law encoder:
// 13-bit magnitude → 3-bit segment + 4-bit mantissa → XOR with 0x55
// to flip even bits (and the sign bit for positives). Matches the
// Sun Microsystems public-domain reference and common DSP libraries.
uint8_t pcm_to_alaw(int16_t pcm)
{
    int val = pcm >> 3;          // 16-bit → 13-bit
    int mask;

    if (val >= 0) {
        mask = 0xD5;             // even bits + sign bit
    } else {
        mask = 0x55;             // even bits only
        val = -val - 1;
    }

    int seg;
    if      (val < 32)   seg = 0;
    else if (val < 64)   seg = 1;
    else if (val < 128)  seg = 2;
    else if (val < 256)  seg = 3;
    else if (val < 512)  seg = 4;
    else if (val < 1024) seg = 5;
    else if (val < 2048) seg = 6;
    else if (val < 4096) seg = 7;
    else                 return 0x7F ^ mask;  // clip to max

    uint8_t aval;
    if (seg < 2) {
        aval = (seg << 4) | ((val >> 1) & 0x0F);
    } else {
        aval = (seg << 4) | ((val >> seg) & 0x0F);
    }
    return aval ^ mask;
}
