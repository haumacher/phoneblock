#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// Energy-based voice-activity detection for the interactive answer bot
// (issue #429).
//
// Same scheme as the Cloud/Docker answer bot (AlawSilenceTrimmer): the
// caller's audio is cut into 20 ms frames (one RTP packet each), the energy
// of every frame is measured in dBFS (decibels relative to 16-bit full
// scale), and a frame at or below `silence_db` counts as silence. The caller
// has *stopped talking* only after `min_silence_ms` of continuous silence, so
// short gaps between words do not end their turn.
//
// Pure C, no ESP-IDF / FreeRTOS dependency: host-tested in test/test_vad.c.
// It computes levels and transitions only; the dialog (dialog.h) decides
// what they mean, the RTP task (rtp.c) drives both.

typedef enum {
    VAD_NONE = 0,        // no classification change this frame
    VAD_SPEECH_ONSET,    // sustained energy above the threshold: caller speaks
    VAD_SILENCE_ONSET,   // min_silence_ms of continuous silence reached
} vad_event_t;

// A loud frame only counts as speech (and restarts the silence timer) once
// it has persisted this many frames in a row. Isolated 1-2 frame blips —
// line noise, comfort-noise or echo spikes at speech level on a real
// telephone line — are ignored, so they cannot keep restarting the silence
// accumulation. 3 frames = 60 ms: far shorter than any real utterance, long
// enough to reject transients.
#define VAD_SPEECH_CONFIRM_FRAMES 3

typedef struct {
    int  silence_db;        // dBFS threshold; a frame <= this is silence
    int  min_silence_ms;    // continuous silence before VAD_SILENCE_ONSET
    // --- internal state ---
    bool speaking;          // current classification
    int  silence_ms;        // continuous silence accumulated so far
    bool silence_reported;  // VAD_SILENCE_ONSET already emitted for this run
    int  loud_run;          // consecutive loud frames (debounce counter)
} vad_t;

// (Re)initialise a detector, e.g. silence_db = -35, min_silence_ms = 500.
//
// The detector starts non-speaking, so the caller's first utterance always
// produces a VAD_SPEECH_ONSET — the dialog keys "did the caller answer" on
// it. A caller who stays silent still gets one VAD_SILENCE_ONSET after
// min_silence_ms; that transition does not depend on the speaking flag.
void vad_init(vad_t *v, int silence_db, int min_silence_ms);

// Feed one frame of `n` signed 16-bit PCM samples spanning `frame_ms`
// milliseconds. If `out_dbfs` is non-NULL, the frame's level is written
// there. Returns the transition this frame triggered, or VAD_NONE.
vad_event_t vad_process(vad_t *v, const int16_t *pcm, size_t n,
                        int frame_ms, int *out_dbfs);

// Level of a PCM frame in dBFS (always <= 0): full-scale RMS. Empty or
// near-silent frames return VAD_DBFS_FLOOR.
#define VAD_DBFS_FLOOR (-100)
int vad_frame_dbfs(const int16_t *pcm, size_t n);
