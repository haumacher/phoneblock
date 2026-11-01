// Implementation of call_rec_mix.h.

#include "call_rec_mix.h"

#include <string.h>

#include "audio.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

#define MASK (CALL_REC_RX_CAP - 1u)

_Static_assert((CALL_REC_RX_CAP & MASK) == 0, "the buffer is a power of two");
_Static_assert(CALL_REC_RX_DELAY + 2 * CALL_REC_FRAME < CALL_REC_RX_CAP,
               "room for the delay and a burst");

void call_rec_rx_init(call_rec_rx_t *rx)
{
    memset(rx, 0, sizeof(*rx));
}

// Playout CALL_REC_RX_DELAY behind a packet starting at `ts`. Only called
// when every sample written so far has been played out (or cleared), so the
// whole buffer is silence.
static void anchor(call_rec_rx_t *rx, uint32_t ts)
{
    rx->play_ts = ts - CALL_REC_RX_DELAY;
    rx->hi_ts   = rx->play_ts;
    rx->synced  = true;
}

// Advances the playout by `k` samples without playing them.
static void skip(call_rec_rx_t *rx, uint32_t k)
{
    int32_t buffered = (int32_t)(rx->hi_ts - rx->play_ts);
    if (k >= CALL_REC_RX_CAP) {
        memset(rx->pcm, 0, sizeof(rx->pcm));
    } else {
        for (uint32_t i = 0; i < k; i++) rx->pcm[(rx->play_ts + i) & MASK] = 0;
    }
    if (buffered > 0) rx->dropped += (uint32_t)buffered < k ? (uint32_t)buffered : k;
    rx->play_ts += k;
    if ((int32_t)(rx->hi_ts - rx->play_ts) < 0) rx->hi_ts = rx->play_ts;
}

void call_rec_rx_push(call_rec_rx_t *rx, uint32_t ts, const uint8_t *alaw, size_t n)
{
    if (n == 0) return;
    if (n > CALL_REC_RX_CAP) {                  // only the newest fit at all
        size_t cut = n - CALL_REC_RX_CAP;
        rx->dropped += (uint32_t)cut;
        alaw += cut;
        ts += (uint32_t)cut;
        n = CALL_REC_RX_CAP;
    }
    if (!rx->synced) anchor(rx, ts);

    int32_t off = (int32_t)(ts - rx->play_ts);
    if (off > CALL_REC_RX_RESYNC || off < -CALL_REC_RX_RESYNC) {
        // Not this stream's timeline any more.
        int32_t buffered = (int32_t)(rx->hi_ts - rx->play_ts);
        if (buffered > 0) rx->dropped += (uint32_t)buffered;
        memset(rx->pcm, 0, sizeof(rx->pcm));
        anchor(rx, ts);
        off = (int32_t)CALL_REC_RX_DELAY;
    } else if (off < 0) {
        if ((int32_t)(rx->hi_ts - rx->play_ts) <= 0) {
            // Ran dry: whatever was written has been played (so the buffer
            // is silence), and this packet is behind the playout. Start
            // over behind it instead of dropping it.
            anchor(rx, ts);
            off = (int32_t)CALL_REC_RX_DELAY;
        } else {
            // A straggler (or a duplicate) whose slot has gone by.
            uint32_t late = (uint32_t)-off;
            if (late >= n) { rx->dropped += (uint32_t)n; return; }
            rx->dropped += late;
            alaw += late;
            ts += late;
            n -= late;
            off = 0;
        }
    }
    if ((uint32_t)off + n > CALL_REC_RX_CAP) {
        // Too far ahead: the playout jumps so that this packet lands
        // CALL_REC_RX_DELAY ahead again, as after a fresh start. Back to
        // the normal depth, not just to the brim — a buffer kept full would
        // clip every following packet.
        uint32_t target = CALL_REC_RX_DELAY;
        if (target > CALL_REC_RX_CAP - n) target = CALL_REC_RX_CAP - (uint32_t)n;
        skip(rx, (uint32_t)off - target);
        off = (int32_t)target;
    }
    for (size_t i = 0; i < n; i++) {
        rx->pcm[(ts + (uint32_t)i) & MASK] = alaw_to_pcm(alaw[i]);
    }
    uint32_t end = ts + (uint32_t)n;
    if ((int32_t)(end - rx->hi_ts) > 0) rx->hi_ts = end;
}

void call_rec_rx_pop(call_rec_rx_t *rx, int16_t out[CALL_REC_FRAME])
{
    if (!rx->synced) {
        memset(out, 0, CALL_REC_FRAME * sizeof(out[0]));
        return;
    }
    for (uint32_t i = 0; i < CALL_REC_FRAME; i++) {
        uint32_t slot = (rx->play_ts + i) & MASK;
        out[i] = rx->pcm[slot];
        rx->pcm[slot] = 0;
    }
    rx->play_ts += CALL_REC_FRAME;
    if ((int32_t)(rx->hi_ts - rx->play_ts) < 0) rx->hi_ts = rx->play_ts;
}

void call_rec_mix(uint8_t out[CALL_REC_FRAME], const uint8_t tx[CALL_REC_FRAME],
                  const int16_t rx[CALL_REC_FRAME])
{
    for (int i = 0; i < CALL_REC_FRAME; i++) {
        if (rx[i] == 0) { out[i] = tx[i]; continue; }
        int s = alaw_to_pcm(tx[i]) + rx[i];
        if (s > 32767)  s = 32767;
        if (s < -32768) s = -32768;
        out[i] = pcm_to_alaw((int16_t)s);
    }
}
