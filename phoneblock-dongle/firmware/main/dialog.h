#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "vad.h"

// Interactive answer-bot dialog (issue #429): the state machine that decides
// which prompt the dongle says next, driven by the caller's speech.
//
// The flow is fixed in code:
//
//   say(hello)
//   intro:    await → caller spoke? yes → say(am-aparat), go to question
//                                   no  → say(who-is-there), stay in intro
//   question: await → caller spoke? yes → say(question)
//   (endless)                       no  → say(cant-hear-you)
//
// Each phase is a set of 1..DIALOG_MAX_CLIPS recorded clips (delivered by the
// CDN, see dialog_manifest.h, with the clip table in the file's header); the
// number is data, not code. A phase picks a
// random clip and, with more than one, never the clip it played last.
//
// await(): if the caller speaks, their turn ends once they have been quiet for
// the VAD's min-silence (the VAD reports that as VAD_SILENCE_ONSET); if they
// stay silent, it ends after DIALOG_AWAIT_TIMEOUT_MS.
//
// There is no time cap: a caller may talk for as long as they like — wasting
// the spammer's time is the point. The dialog ends only when the caller's
// media stops: no RTP packet at all for DIALOG_RX_TIMEOUT_MS means they are
// gone (a lost BYE, a dead NAT mapping), so the dongle hangs up. Every
// datagram counts (dialog_rx()), also one the bot cannot decode, so a deaf
// dialog (no SRTP key) still notices the end.
//
// Pure C, no I/O and no clock of its own: the RTP task feeds it VAD events
// and 20 ms ticks and plays what it asks for (rtp.c); test/test_dialog.c
// drives it the same way with an injected random source.

typedef enum {
    DIALOG_PHASE_HELLO = 0,
    DIALOG_PHASE_WHO_IS_THERE,
    DIALOG_PHASE_AM_APARAT,
    DIALOG_PHASE_QUESTION,
    DIALOG_PHASE_CANT_HEAR_YOU,
    DIALOG_PHASE_COUNT
} dialog_phase_t;

// Most clips a phase may have on the device. More in a set's header make the
// set malformed (dialog_header_parse()).
#define DIALOG_MAX_CLIPS        32
// How long an await waits for a caller who says nothing.
#define DIALOG_AWAIT_TIMEOUT_MS 3000
// No packet from the caller for this long ends the dialog.
#define DIALOG_RX_TIMEOUT_MS    30000u

// The phase's name in the manifest and the source tree ("hello",
// "who-is-there", "am-aparat", "question", "cant-hear-you"); NULL for an
// out-of-range value.
const char *dialog_phase_name(dialog_phase_t phase);

// Random source, e.g. a wrapper around pb_random_u32(). Injected so the
// tests can make the clip choice deterministic.
typedef uint32_t (*dialog_rng_fn)(void *ctx);

typedef enum {
    DIALOG_ACT_NONE = 0,  // nothing changes this tick
    DIALOG_ACT_SAY,       // start playing clip `clip` of `phase`
    DIALOG_ACT_LISTEN,    // the clip is over, an await begins: reset the VAD
    DIALOG_ACT_END,       // the dialog is over (caller's media gone): hang up
} dialog_act_t;

typedef struct {
    dialog_act_t   kind;
    dialog_phase_t phase;   // DIALOG_ACT_SAY only
    int            clip;    // DIALOG_ACT_SAY only: 0 .. count[phase] - 1
} dialog_action_t;

typedef struct {
    // --- configuration ---
    uint8_t        count[DIALOG_PHASE_COUNT];
    dialog_rng_fn  rng;
    void          *rng_ctx;
    // --- state ---
    enum { DIALOG_SM_SAYING, DIALOG_SM_AWAITING, DIALOG_SM_DONE } mode;
    bool           in_question;   // false: intro stage, true: question loop
    bool           spoke;         // caller spoke during this await
    bool           turn_over;     // ... and has been quiet for min-silence since
    uint32_t       await_ms;      // time spent in this await
    uint32_t       elapsed_ms;    // time since dialog_start (saturates)
    uint32_t       quiet_ms;      // time since the caller's last packet
    int            last[DIALOG_PHASE_COUNT];   // clip played last, -1 = none
} dialog_sm_t;

// Starts a dialog with `count[phase]` clips per phase and returns the first
// action: say a hello clip. Returns DIALOG_ACT_END instead if any phase has
// no clip (the caller only starts a dialog with a complete set).
dialog_action_t dialog_start(dialog_sm_t *d,
                             const uint8_t count[DIALOG_PHASE_COUNT],
                             dialog_rng_fn rng, void *rng_ctx);

// A packet from the caller's media endpoint arrived (any datagram, before
// decryption or payload checks): the caller is still on the line.
void dialog_rx(dialog_sm_t *d);

// Whether the dialog ended because the caller's media stopped (as opposed
// to an incomplete clip set at dialog_start()).
bool dialog_media_lost(const dialog_sm_t *d);

// A VAD transition of the caller's audio, in the order the frames arrived.
// Only counts while awaiting; during a prompt it is ignored (the VAD is reset
// when the await begins, DIALOG_ACT_LISTEN).
void dialog_vad(dialog_sm_t *d, vad_event_t event);

// One tick of `ms` milliseconds (20 on the device). `clip_done` is true once
// the clip asked for by the last DIALOG_ACT_SAY has been played completely.
// Returns what to do now: DIALOG_ACT_END once DIALOG_RX_TIMEOUT_MS have passed
// without dialog_rx(), whatever the dialog is doing. After DIALOG_ACT_END
// every further tick returns DIALOG_ACT_END again.
dialog_action_t dialog_tick(dialog_sm_t *d, uint32_t ms, bool clip_done);

// --- The clip set on the device -------------------------------------------
//
// The clips of a locale arrive as ONE file (one SPIFFS object costs far less
// than one per clip, test_storage_sim.c) that starts with its clip table: a
// header saying where in the file each clip of each phase lies. The
// manifest's SHA-256 covers the header like the audio after it, so a verified
// file is a consistent set. All integers little-endian:
//
//   offset    size  field
//   0         4     magic "PBDS"
//   4         1     version, DIALOG_HEADER_VERSION
//   5         1     P, the number of phases (>= DIALOG_PHASE_COUNT); the
//                   first DIALOG_PHASE_COUNT in dialog_phase_t order, any
//                   further ones a later firmware's, skipped here
//   6         2     H, the header's length: where the audio begins
//   8         P     clips per phase
//   8 + P     8 * n per clip, phase by phase in clip order: u32 offset,
//                   u32 length, the offset counted from the file's start
//   H         ...   the clips, raw A-law
//
// H must equal 8 + P + 8 * n (n: all clips of all P phases) and stay within
// DIALOG_HEADER_MAX; every clip of the known phases is non-empty and lies
// between H and the end of the file. scripts/audio-assets.sh writes it.

#define DIALOG_HEADER_VERSION 1
// Smallest header (no clip at all — never valid, but the fixed part).
#define DIALOG_HEADER_MIN     8
// Largest header the device reads: the five phases at DIALOG_MAX_CLIPS clips
// each take 1293 bytes; the rest is room for phases of a later firmware.
#define DIALOG_HEADER_MAX     2048

typedef struct {
    uint32_t off;   // byte offset in the set's file
    uint32_t len;   // bytes, > 0
} dialog_clip_t;

// Longest speaker id plus NUL (dialog_speaker_valid()).
#define DIALOG_SPEAKER_CAP 17
// Pseudo-locale of the user's own set (POST /api/dialog): stored like a
// downloaded set, never chosen from or replaced by the CDN.
#define DIALOG_OWN_LANG "own"

typedef struct {
    char          lang[12];                    // locale the clips came from,
                                               // DIALOG_OWN_LANG for an own set
    char          speaker[DIALOG_SPEAKER_CAP]; // whose CDN set; "" for an own
                                               // set or an index without one
    uint8_t       count[DIALOG_PHASE_COUNT];   // 1..DIALOG_MAX_CLIPS each
    dialog_clip_t clip[DIALOG_PHASE_COUNT][DIALOG_MAX_CLIPS];
} dialog_set_t;

typedef enum {
    DIALOG_HEADER_OK = 0,
    DIALOG_HEADER_SHORT,   // `n` bytes end before the header does
    DIALOG_HEADER_BAD,     // not a valid set (see the format above)
} dialog_header_result_t;

// Parses the header of a set file of `file_size` bytes whose first `n`
// bytes are in `buf` (reading min(file_size, DIALOG_HEADER_MAX) bytes is
// always enough). Fills set->count and set->clip on DIALOG_HEADER_OK and
// clears them otherwise; set->lang is the caller's. SHORT when the header
// runs past the `n` bytes — with the whole file or DIALOG_HEADER_MAX bytes
// read, a truncated or oversized file.
dialog_header_result_t dialog_header_parse(const uint8_t *buf, size_t n,
                                           uint64_t file_size, dialog_set_t *set);

// Whether `s` is a locale code a set may be stored under: [a-z0-9-], 2..11
// characters, no leading, trailing or doubled '-'.
bool dialog_lang_valid(const char *s);

// Whether `s` is a speaker id of the dialog index (dialog_manifest.h) and the
// `dialog_speaker` setting: [a-z][a-z0-9-]{0,15}.
bool dialog_speaker_valid(const char *s);
