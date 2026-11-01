// The audio of a call recording (#663): both directions of a dialog call in
// one mono A-law track on the dongle's timeline. Pure, host-tested
// (test/test_call_rec.c); only in builds with PB_CALL_RECORDING.
//
// Per 20 ms tick the dialog task sends one frame (prompt + comfort noise,
// rtp.c mix_frame()) and receives whatever the caller's packets brought —
// usually one, under jitter none or two, a 30 ms packet is 20 + 10 ms. The
// receive side therefore goes through a small jitter buffer placed by the
// packets' RTP timestamps: a sample lands at its own position, so bursts,
// reordering and 30 ms packets keep the caller's voice in order, a lost
// packet leaves silence exactly where it was, and nothing drifts over a
// long call. Each tick then takes the next CALL_REC_FRAME samples out of
// the buffer and adds them to the frame that was sent.
#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define CALL_REC_FRAME 160          // samples per tick: 20 ms at 8 kHz

// Jitter buffer: CALL_REC_RX_CAP samples (256 ms, a power of two) of PCM.
// Playout runs CALL_REC_RX_DELAY (40 ms) behind the first packet, which
// absorbs arrival jitter up to that much without a gap.
#define CALL_REC_RX_CAP   2048u
#define CALL_REC_RX_DELAY 320u
// A timestamp more than this far (1 s) from the playout position is a new
// stream (another SSRC, a gateway re-INVITE): the buffer starts over.
#define CALL_REC_RX_RESYNC 8000

typedef struct {
    int16_t  pcm[CALL_REC_RX_CAP];  // 0 = nothing received for that sample
    uint32_t play_ts;               // RTP timestamp of the next sample out
    uint32_t hi_ts;                 // one past the newest sample written
    bool     synced;                // a packet was placed since init
    uint32_t dropped;               // samples given up (too late, overflow)
} call_rec_rx_t;

void call_rec_rx_init(call_rec_rx_t *rx);

// Places the `n` A-law samples of one received packet whose first sample has
// RTP timestamp `ts`. Samples whose time has already been played out are
// dropped — unless the buffer has run dry, then the playout restarts
// CALL_REC_RX_DELAY behind this packet (the caller's clock is slower, or the
// network stalled): nothing of the caller's speech is lost, the mix only
// shifts by that much. A packet beyond the buffer moves the playout forward
// to CALL_REC_RX_DELAY behind it, dropping the oldest samples (a faster
// clock, or the burst after a stall longer than the buffer).
void call_rec_rx_push(call_rec_rx_t *rx, uint32_t ts, const uint8_t *alaw, size_t n);

// The next CALL_REC_FRAME samples of the caller (0 where nothing arrived),
// advancing the playout by one tick.
void call_rec_rx_pop(call_rec_rx_t *rx, int16_t out[CALL_REC_FRAME]);

// One recorded frame: the A-law frame that was sent plus the caller's PCM,
// summed in PCM and saturated at the 16-bit limits. A sample without caller
// audio is the sent byte unchanged.
void call_rec_mix(uint8_t out[CALL_REC_FRAME], const uint8_t tx[CALL_REC_FRAME],
                  const int16_t rx[CALL_REC_FRAME]);
