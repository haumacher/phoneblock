// Host test for the dialog's receive path: the G.711 A-law decoder
// (audio.c) and the energy-based voice-activity detector (vad.c). Pure libc +
// libm; mirrors the device's frame cadence (160 samples / 20 ms).
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "audio.h"
#include "vad.h"

#define FRAME 160
#define FRAME_MS 20

static int failures = 0;
static int checks = 0;

#define CHECK(cond) do {                                                \
    checks++;                                                           \
    if (!(cond)) {                                                      \
        failures++;                                                     \
        fprintf(stderr, "FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond); \
    }                                                                   \
} while (0)

// --- A-law decoder ------------------------------------------------------

static void test_alaw_reference_vectors(void)
{
    CHECK(alaw_to_pcm(0xD5) == 8);        // silence (A-law has no exact 0)
    CHECK(alaw_to_pcm(0x55) == -8);
    CHECK(alaw_to_pcm(0xAA) == 32256);    // positive full scale
    CHECK(alaw_to_pcm(0x2A) == -32256);   // negative full scale
    // The encoder's silence decodes to near zero.
    CHECK(abs(alaw_to_pcm(pcm_to_alaw(0))) <= 8);
}

static void test_alaw_sign_symmetry(void)
{
    // The sign bit is the only difference between a value and its negative.
    for (int a = 0; a < 256; a++) {
        CHECK(alaw_to_pcm((uint8_t)(a ^ 0x80)) == -alaw_to_pcm((uint8_t)a));
    }
}

static void test_alaw_round_trip(void)
{
    // A-law is log-companded, so the error grows with the magnitude; it
    // stays inside one quantisation step (~3 % of |x|, at least 16).
    for (int x = -32768; x <= 32767; x += 7) {
        int16_t back = alaw_to_pcm(pcm_to_alaw((int16_t)x));
        int tol = (abs(x) >> 5) + 256;
        CHECK(abs(back - x) <= tol);
        if (x > 256)  CHECK(back > 0);
        if (x < -256) CHECK(back < 0);
    }
    // Every code decodes to a value that encodes back to itself.
    for (int a = 0; a < 256; a++) {
        CHECK(pcm_to_alaw(alaw_to_pcm((uint8_t)a)) == (uint8_t)a);
    }
}

// --- dBFS measurement ------------------------------------------------------

static void fill_sine(int16_t *buf, size_t n, double amp)
{
    for (size_t i = 0; i < n; i++)
        buf[i] = (int16_t)(amp * sin(2.0 * M_PI * 400.0 * i / 8000.0));
}

static void test_dbfs_levels(void)
{
    int16_t silence[FRAME] = {0};
    CHECK(vad_frame_dbfs(silence, FRAME) == VAD_DBFS_FLOOR);
    CHECK(vad_frame_dbfs(silence, 0) == VAD_DBFS_FLOOR);

    // Full-scale sine ≈ -3 dBFS (RMS of a sine is amplitude / sqrt(2)).
    int16_t loud[FRAME];
    fill_sine(loud, FRAME, 32767.0);
    CHECK(vad_frame_dbfs(loud, FRAME) == -3);

    // Amplitude 200 ≈ -47 dBFS, well below a -35 dBFS threshold.
    int16_t quiet[FRAME];
    fill_sine(quiet, FRAME, 200.0);
    int db = vad_frame_dbfs(quiet, FRAME);
    CHECK(db <= -46 && db >= -48);
}

// --- VAD state machine -------------------------------------------------------

static vad_event_t feed(vad_t *v, double amp)
{
    int16_t buf[FRAME];
    if (amp <= 0.0) memset(buf, 0, sizeof(buf));
    else            fill_sine(buf, FRAME, amp);
    return vad_process(v, buf, FRAME, FRAME_MS, NULL);
}

#define LOUD 20000.0

static void test_leading_silence(void)
{
    // A caller who says nothing: exactly one SILENCE_ONSET, at min-silence.
    vad_t v;
    vad_init(&v, -35, 1500);
    int onsets = 0, onset_frame = -1;
    for (int f = 0; f < 200; f++) {
        if (feed(&v, 0.0) == VAD_SILENCE_ONSET) { onsets++; onset_frame = f; }
    }
    CHECK(onsets == 1);
    CHECK(onset_frame == 1500 / FRAME_MS - 1);   // the 75th frame = 1500 ms
}

static void test_silence_exactly_after_speech_end(void)
{
    vad_t v;
    vad_init(&v, -35, 800);

    // The first utterance fires SPEECH_ONSET once the debounce has passed.
    for (int f = 0; f < VAD_SPEECH_CONFIRM_FRAMES - 1; f++)
        CHECK(feed(&v, LOUD) == VAD_NONE);
    CHECK(feed(&v, LOUD) == VAD_SPEECH_ONSET);
    for (int f = 0; f < 50; f++) CHECK(feed(&v, LOUD) == VAD_NONE);

    // Speech ends: SILENCE_ONSET exactly on the frame that completes 800 ms
    // of silence, not one frame earlier or later.
    int frames = 800 / FRAME_MS;
    for (int f = 0; f < frames - 1; f++) CHECK(feed(&v, 0.0) == VAD_NONE);
    CHECK(feed(&v, 0.0) == VAD_SILENCE_ONSET);
    for (int f = 0; f < 100; f++) CHECK(feed(&v, 0.0) == VAD_NONE);

    // Talking again after a real silence is a fresh onset.
    for (int f = 0; f < VAD_SPEECH_CONFIRM_FRAMES - 1; f++)
        CHECK(feed(&v, LOUD) == VAD_NONE);
    CHECK(feed(&v, LOUD) == VAD_SPEECH_ONSET);
}

static void test_single_loud_frame_debounced(void)
{
    vad_t v;
    vad_init(&v, -35, 800);
    // 400 ms of silence, one loud frame, more silence: the blip is not
    // speech, and it does not restart the silence timer either — the onset
    // still comes at 800 ms of silence counted around the blip.
    for (int f = 0; f < 20; f++) CHECK(feed(&v, 0.0) == VAD_NONE);
    CHECK(feed(&v, LOUD) == VAD_NONE);
    for (int f = 0; f < 19; f++) CHECK(feed(&v, 0.0) == VAD_NONE);
    CHECK(feed(&v, 0.0) == VAD_SILENCE_ONSET);

    // Two loud frames are still below the confirm window.
    vad_t w;
    vad_init(&w, -35, 800);
    CHECK(feed(&w, LOUD) == VAD_NONE);
    CHECK(feed(&w, LOUD) == VAD_NONE);
    CHECK(feed(&w, 0.0) == VAD_NONE);
    CHECK(!w.speaking);
}

// Speech-level blips during a pause (line noise, echo) must not keep the
// caller's turn open indefinitely.
static void test_blips_do_not_block_silence(void)
{
    vad_t v;
    vad_init(&v, -35, 1500);
    for (int f = 0; f < VAD_SPEECH_CONFIRM_FRAMES + 2; f++) feed(&v, LOUD);

    int onset_at = -1;
    for (int f = 0; f < 200 && onset_at < 0; f++) {
        double amp = (f % 10 == 9) ? LOUD : 0.0;   // 1-frame blip every 200 ms
        if (feed(&v, amp) == VAD_SILENCE_ONSET) onset_at = f;
    }
    // 75 silent frames needed, one in ten is a (held) blip.
    CHECK(onset_at >= 75);
    CHECK(onset_at < 90);
}

static void test_threshold_is_respected(void)
{
    int16_t buf[FRAME];
    fill_sine(buf, FRAME, 350.0);          // ≈ -42 dBFS
    int db = vad_frame_dbfs(buf, FRAME);
    CHECK(db < -35 && db > -50);

    vad_t lax; vad_init(&lax, -50, 100);   // -42 dBFS is speech here
    int sp = 0;
    for (int f = 0; f < 10; f++)
        if (feed(&lax, 350.0) == VAD_SPEECH_ONSET) sp++;
    CHECK(sp == 1);

    vad_t strict; vad_init(&strict, -35, 100);   // ... and silence here
    int got = 0;
    for (int f = 0; f < 10; f++)
        if (feed(&strict, 350.0) == VAD_SILENCE_ONSET) got++;
    CHECK(got == 1);
}

int main(void)
{
    test_alaw_reference_vectors();
    test_alaw_sign_symmetry();
    test_alaw_round_trip();
    test_dbfs_levels();
    test_leading_silence();
    test_silence_exactly_after_speech_end();
    test_single_loud_frame_debounced();
    test_blips_do_not_block_silence();
    test_threshold_is_respected();
    printf("test_vad: %d checks, %d failures\n", checks, failures);
    return failures ? 1 : 0;
}
