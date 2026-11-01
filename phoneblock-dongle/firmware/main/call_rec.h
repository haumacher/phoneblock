// Recordings of the calls the dongle holds in dialog mode (#663). Only in
// builds with PB_CALL_RECORDING (call_rec_cfg.h): the T-Dongle-S3 and the
// Linux service.
//
// What: the whole conversation of a spam call answered in dialog mode, both
// directions mixed into one mono 8 kHz A-law track (8000 B/s) on the
// dongle's timeline (call_rec_mix.h). Opt-in (config_rec_enabled(), off by
// default); rtp.c records a call only when it is on.
//
// Where: one file per call in a directory of the data directory
// (CALL_REC_DIR: /spiffs/rec on the S3, <data dir>/rec on Linux), named by
// the call's sequence number in the call list (stats.h): "<seq>.al", raw
// A-law. While a call is being recorded its file is "<seq>.tmp"; it is
// renamed when the call ends, so a recording is either complete or absent.
//
// Bound to the call list: a recording whose call has left the list (ring
// wrap, stats_clear_calls()) is deleted at once (stats_set_drop_hook()),
// recording files without a listed call (a crash, an older list) are
// removed at start (call_rec_init()), and a missing file is just "no
// recording". Across a restart the recordings stay attached to their calls:
// the sequence numbers persist with the list.
//
// Budget: all recordings together take at most CALL_REC_CAP on-flash bytes
// (whole LittleFS blocks, storage_budget.h), each call at most
// CALL_REC_CALL_MAX bytes of audio. When the recording in progress would
// exceed the cap, the oldest recordings are deleted first.
//
// Real time: the S3 has no PSRAM, and the dialog task must never wait for
// the flash. It hands each mixed 20 ms frame to a RAM ring of
// CALL_REC_RING_FRAMES frames (2 s), allocated per call; a writer task of
// the lowest priority moves them into the file. If the ring overflows (the
// flash stalled for longer than that), the recording ends there with one
// WARN — the call goes on unaffected. At the end of the call the writer
// flushes and closes the file; a recording shorter than CALL_REC_MIN_FRAMES
// (1 s) is discarded.
#pragma once

#include "call_rec_cfg.h"

#if PB_CALL_RECORDING

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "call_rec_mix.h"
#include "storage_budget.h"

// The directory name in the data directory.
#define CALL_REC_DIR_NAME "rec"

#ifdef ESP_PLATFORM
#define CALL_REC_CAP      STORAGE_REC_LFS_CAP
#define CALL_REC_CALL_MAX STORAGE_REC_LFS_CALL_MAX
#else
#define CALL_REC_CAP      STORAGE_REC_HOSTFS_CAP
#define CALL_REC_CALL_MAX STORAGE_REC_HOSTFS_CALL_MAX
#endif

// Bytes per second of a recording (8 kHz A-law).
#define CALL_REC_BYTES_PER_S 8000u

// The RAM ring between the dialog task and the writer: 100 frames of 160 B,
// 16000 B, 2 s of audio — on the heap only while a call is recorded.
#define CALL_REC_RING_FRAMES 100u
// Shorter recordings (1 s) are discarded.
#define CALL_REC_MIN_FRAMES  50u
// The writer task: lowest priority, woken every CALL_REC_WRITER_MS.
#define CALL_REC_WRITER_STACK 4096
#define CALL_REC_WRITER_MS    100u

// Sets up the recordings in directory `dir` (created if missing): the index
// is built from the files there, every file that is not the recording of a
// listed call is removed (call the list's stats_calls_open() first), and
// the cap is enforced. Registers the call list's drop hook. Logs one INFO
// line with what it found. False (one WARN) if the directory is unusable:
// calls are then not recorded.
bool call_rec_init(const char *dir);

// --- Recording a call (rtp.c) ------------------------------------------------

typedef struct call_rec call_rec_t;

// Starts recording the listed call `seq` and its writer task. NULL (and no
// recording) when recordings are not set up, another recording is still
// being written, the call is not listed, or memory or the task cannot be
// had — each with one INFO / WARN line. The caller checks
// config_rec_enabled() first.
call_rec_t *call_rec_begin(uint32_t seq);

// The caller's audio: one received PCMA packet, `n` A-law samples whose first
// has RTP timestamp `ts`. Never blocks.
void call_rec_rx(call_rec_t *r, uint32_t ts, const uint8_t *alaw, size_t n);

// One tick: `tx` is the frame just sent (CALL_REC_FRAME A-law bytes); it is
// mixed with the caller's audio of this tick and queued for the writer.
// Never blocks: a full ring or the per-call maximum ends the recording.
void call_rec_tick(call_rec_t *r, const uint8_t tx[CALL_REC_FRAME]);

// The call has ended: the writer finishes the file and frees the recording.
// `r` must not be used afterwards. NULL is a no-op.
void call_rec_end(call_rec_t *r);

// --- The stored recordings (web.c) ---------------------------------------

// Length in seconds of the recording of call `seq` (rounded, at least 1);
// 0 if there is none.
uint32_t call_rec_seconds(uint32_t seq);

// Opens the recording of call `seq` for reading; -1 if there is none. On
// success *size is its length in bytes. The caller closes the descriptor.
int call_rec_open(uint32_t seq, size_t *size);

// Deletes the recording of call `seq`. False if there was none.
bool call_rec_delete(uint32_t seq);

// Deletes every stored recording (factory reset). The one in progress, if
// any, is still stored when its call ends.
void call_rec_delete_all(void);

typedef struct {
    int      count;         // recordings stored
    uint64_t audio_bytes;   // their audio
    uint64_t used_bytes;    // their footprint on flash, counted against cap
    uint64_t cap_bytes;     // CALL_REC_CAP
    uint32_t call_max_s;    // CALL_REC_CALL_MAX in seconds
    bool     active;        // a call is being recorded right now
} call_rec_info_t;

void call_rec_get_info(call_rec_info_t *out);

// On-flash footprint of a recording of `bytes`: whole LittleFS blocks
// (storage_budget_lfs_file_blocks()); the same rule on Linux, where it is a
// close estimate of a filesystem's 4 KiB blocks.
uint64_t call_rec_footprint(uint64_t bytes);

// --- For the host tests ------------------------------------------------------

// Other limits than CALL_REC_CAP / CALL_REC_CALL_MAX (bytes); 0 restores a
// default.
void call_rec_set_limits(uint64_t cap_bytes, uint32_t call_max_bytes);

// true: call_rec_begin() starts no writer task; the test drives the writer
// with call_rec_writer_step() (deterministic overflow and timing).
void call_rec_set_manual_writer(bool manual);

// One pass of the writer of the recording in progress: moves the queued
// frames into the file, finishes the file once the call has ended. Returns
// true while a recording is still in progress.
bool call_rec_writer_step(void);

#endif  // PB_CALL_RECORDING
