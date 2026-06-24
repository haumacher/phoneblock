#pragma once

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

// Energy-based voice-activity detection for the answer-bot audio path.
//
// This mirrors the scheme the Cloud/Docker answer bot uses
// (org.mjsip…AlawSilenceTrimmer): the caller's incoming audio is split
// into short frames (one 20 ms RTP frame = one analysis window), the
// energy of each frame is measured in dBFS (decibels relative to 16-bit
// full scale), and a frame is classified as "silence" when it sits at or
// below `silence_db`. The caller is considered to have *stopped talking*
// only after `min_silence_ms` of continuous silence — short gaps between
// words do not trip it.
//
// The module is deliberately pure C with no ESP-IDF / FreeRTOS
// dependency so it can be unit-tested on the host (see test/test_vad.c).
// It computes levels and transitions only; logging and any audible
// reaction (the test beep) live in the RTP task that drives it.

typedef enum {
    VAD_NONE = 0,        // no classification change this frame
    VAD_SPEECH_ONSET,    // energy rose above the threshold → caller speaking
    VAD_SILENCE_ONSET,   // >= min_silence_ms of continuous silence reached
} vad_event_t;

// A loud frame only counts as speech (and resets the silence timer) once
// it has persisted this many frames in a row. Isolated 1-2 frame blips —
// line-noise / comfort-noise / echo spikes that sit at speech level on a
// real telephone line — are ignored, so they cannot keep restarting the
// silence accumulation (the cause of "1.5 s configured but ~4.5 s felt").
// 3 frames = 60 ms: far shorter than any real utterance, long enough to
// reject transients.
#define VAD_SPEECH_CONFIRM_FRAMES 3

typedef struct {
    int  silence_db;        // dBFS threshold; frame <= this counts as silence
    int  min_silence_ms;    // continuous silence before VAD_SILENCE_ONSET
    // --- internal state ---
    bool speaking;          // current high-level classification
    int  silence_ms;        // accumulated continuous silence so far
    bool silence_reported;  // VAD_SILENCE_ONSET already emitted for this run
    int  loud_run;          // consecutive loud frames seen (debounce counter)
} vad_t;

// (Re)initialise a detector. `silence_db` is the dBFS threshold (e.g.
// -35), `min_silence_ms` the continuous-silence span (e.g. 1500).
//
// The detector starts in the "speaking" state so that the very first
// stretch of silence on a fresh call still produces a VAD_SILENCE_ONSET
// — i.e. the caller staying quiet right away is detectable, without
// having to speak first. (The later dialog stage may want the opposite
// "wait for intro" bias; that is a policy choice for that stage, not the
// detector's job.)
void vad_init(vad_t *v, int silence_db, int min_silence_ms);

// Feed one frame of `n` signed-16-bit PCM samples spanning `frame_ms`
// milliseconds. If `out_dbfs` is non-NULL, the frame's measured level is
// written there (a value <= 0, floored at VAD_DBFS_FLOOR). Returns the
// transition that this frame triggered, or VAD_NONE.
vad_event_t vad_process(vad_t *v, const int16_t *pcm, size_t n,
                        int frame_ms, int *out_dbfs);

// Level of a PCM frame in dBFS (always <= 0). Pure full-scale RMS;
// exposed separately so tests can assert on the measurement independent
// of the state machine. Empty / near-silent frames return VAD_DBFS_FLOOR.
#define VAD_DBFS_FLOOR (-100)
int vad_frame_dbfs(const int16_t *pcm, size_t n);
