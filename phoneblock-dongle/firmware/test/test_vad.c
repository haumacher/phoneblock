// Host test for the answer-bot receive path: the G.711 A-law decoder
// (audio.c) and the energy-based voice-activity detector (vad.c). Pure
// libc + libm; mirrors the on-device frame cadence (160 samples / 20 ms).
#include <assert.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "audio.h"
#include "vad.h"

#define FRAME 160
#define FRAME_MS 20

// --- A-law codec ----------------------------------------------------

static void test_alaw_reference_vectors(void)
{
    // The exact decoded values documented in audio.h.
    assert(alaw_to_pcm(0xD5) == 8);        // silence (A-law has no exact 0)
    assert(alaw_to_pcm(0xAA) == 32256);    // positive full scale
    assert(alaw_to_pcm(0x2A) == -32256);   // negative full scale

    // Round-trip: encode every PCM value, decode, and require the result
    // to be close (A-law is lossy/log-companded, so allow segment-sized
    // error that grows with magnitude — never more than ~4% of |x|+256).
    for (int x = -32768; x <= 32767; x += 7) {
        int16_t back = alaw_to_pcm(pcm_to_alaw((int16_t)x));
        int tol = (abs(x) >> 5) + 256;
        assert(abs(back - x) <= tol);
        // Sign must always be preserved (a positive must not decode negative).
        if (x > 256)  assert(back > 0);
        if (x < -256) assert(back < 0);
    }
}

// --- dBFS measurement ----------------------------------------------

static void fill_sine(int16_t *buf, size_t n, double amp)
{
    for (size_t i = 0; i < n; i++)
        buf[i] = (int16_t)(amp * sin(2.0 * M_PI * 50.0 * i / 8000.0));
}

static void test_dbfs_levels(void)
{
    int16_t silence[FRAME] = {0};
    assert(vad_frame_dbfs(silence, FRAME) == VAD_DBFS_FLOOR);

    // Full-scale sine ≈ -3 dBFS (RMS of a sine is amplitude/sqrt(2)).
    int16_t loud[FRAME];
    fill_sine(loud, FRAME, 32767.0);
    int db = vad_frame_dbfs(loud, FRAME);
    assert(db <= 0 && db >= -6);

    // A quiet sine sits well below a -35 dBFS threshold.
    int16_t quiet[FRAME];
    fill_sine(quiet, FRAME, 200.0);       // ~ -47 dBFS
    assert(vad_frame_dbfs(quiet, FRAME) < -35);
}

// --- VAD state machine ---------------------------------------------

static vad_event_t feed(vad_t *v, double amp)
{
    int16_t buf[FRAME];
    if (amp <= 0.0) memset(buf, 0, sizeof(buf));
    else            fill_sine(buf, FRAME, amp);
    return vad_process(v, buf, FRAME, FRAME_MS, NULL);
}

static void test_vad_transitions(void)
{
    vad_t v;
    vad_init(&v, -35, 1500);   // Cloud-AB defaults

    // Leading silence: starts "speaking", so after 1500 ms of silence we
    // get exactly one SILENCE_ONSET (frame 75 = 1500 ms), not before.
    int onsets = 0, onset_frame = -1;
    for (int f = 0; f < 100; f++) {
        if (feed(&v, 0.0) == VAD_SILENCE_ONSET) { onsets++; onset_frame = f; }
    }
    assert(onsets == 1);
    assert(onset_frame == 1500 / FRAME_MS - 1);   // 74 → 75th frame = 1500 ms

    // Caller starts talking → SPEECH_ONSET only after the loudness has
    // persisted VAD_SPEECH_CONFIRM_FRAMES in a row (debounce).
    for (int f = 0; f < VAD_SPEECH_CONFIRM_FRAMES - 1; f++)
        assert(feed(&v, 20000.0) == VAD_NONE);
    assert(feed(&v, 20000.0) == VAD_SPEECH_ONSET);
    // Continued speech: no further events.
    for (int f = 0; f < 50; f++) assert(feed(&v, 20000.0) == VAD_NONE);

    // A full 1500 ms pause does trip exactly one silence onset...
    int late = 0;
    for (int f = 0; f < 1500 / FRAME_MS; f++)
        if (feed(&v, 0.0) == VAD_SILENCE_ONSET) late++;
    assert(late == 1);
    // ...and sustained speech again after a real silence is a fresh onset.
    for (int f = 0; f < VAD_SPEECH_CONFIRM_FRAMES - 1; f++)
        assert(feed(&v, 20000.0) == VAD_NONE);
    assert(feed(&v, 20000.0) == VAD_SPEECH_ONSET);
}

// The actual fix for "1.5 s configured but ~4.5 s felt": isolated loud
// blips during a silence (speech-level line-noise/echo spikes) must not
// keep restarting the silence accumulation.
static void test_blips_do_not_block_silence(void)
{
    vad_t v;
    vad_init(&v, -35, 1500);
    // Get into the speaking state first.
    for (int f = 0; f < VAD_SPEECH_CONFIRM_FRAMES + 2; f++) feed(&v, 20000.0);

    // Caller stops: mostly silent, but a single loud frame every 200 ms —
    // exactly the on-line pattern the dongle logged (resets every few
    // hundred ms). The onset must still arrive, and soon.
    int onset_at = -1;
    for (int f = 0; f < 200 && onset_at < 0; f++) {
        double amp = (f % 10 == 9) ? 20000.0 : 0.0;   // 1-frame blip / 200 ms
        if (feed(&v, amp) == VAD_SILENCE_ONSET) onset_at = f;
    }
    // Without debounce each blip zeroed the timer → silence never reached
    // 1500 ms → no onset ever. With debounce it arrives in ~2 s, not never.
    assert(onset_at >= 0);
    assert(onset_at < 120);
}

static void test_threshold_is_respected(void)
{
    // A signal between two thresholds is silence for the strict one and
    // speech for the lax one.
    int16_t buf[FRAME];
    fill_sine(buf, FRAME, 350.0);          // ≈ -42 dBFS
    int db = vad_frame_dbfs(buf, FRAME);
    assert(db < -35 && db > -50);

    vad_t strict; vad_init(&strict, -50, 100);   // -42 dBFS counts as speech
    assert(feed(&strict, 350.0) == VAD_NONE);     // already "speaking" → no event
    for (int f = 0; f < 10; f++) assert(feed(&strict, 350.0) == VAD_NONE);

    vad_t lax; vad_init(&lax, -35, 100);          // -42 dBFS counts as silence
    int got = 0;
    for (int f = 0; f < 10; f++)
        if (feed(&lax, 350.0) == VAD_SILENCE_ONSET) got++;
    assert(got == 1);
}

int main(void)
{
    test_alaw_reference_vectors();
    test_dbfs_levels();
    test_vad_transitions();
    test_blips_do_not_block_silence();
    test_threshold_is_respected();
    printf("test_vad: all assertions passed\n");
    return 0;
}
