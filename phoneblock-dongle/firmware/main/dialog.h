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
// CDN, see dialog_manifest.h); the number is data, not code. A phase picks a
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

// Most clips a phase may have on the device. More in the manifest are a
// malformed set (dialog_manifest.h skips the locale).
#define DIALOG_MAX_CLIPS        8
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
// than eight, test_storage_sim.c) plus a clip table: where in that file each
// clip of each phase lies. i18n_sync.c stores the table as a small index next
// to the file (dialog_store.h), written only once the file is in place, so the
// index's presence means the set is complete. Index format, one line each:
//
//   <lang>\n
//   <off>:<len> <off>:<len> ...\n     one line per phase, dialog_phase_t order

typedef struct {
    uint32_t off;   // byte offset in the set's file
    uint32_t len;   // bytes, > 0
} dialog_clip_t;

typedef struct {
    char          lang[12];                    // locale the clips came from
    uint8_t       count[DIALOG_PHASE_COUNT];   // 1..DIALOG_MAX_CLIPS each
    dialog_clip_t clip[DIALOG_PHASE_COUNT][DIALOG_MAX_CLIPS];
} dialog_set_t;

// Longest index (dialog_set_format()); a buffer of this size always holds it.
#define DIALOG_SET_TEXT_CAP 1024

// Whether two sets name the same locale and the same clip table.
bool dialog_set_equal(const dialog_set_t *a, const dialog_set_t *b);

// Whether every clip of `set` lies inside a file of `bytes`.
bool dialog_set_within(const dialog_set_t *set, uint64_t bytes);

// Writes the index for `set` into `out`. Returns its length, or -1 if it
// does not fit.
int dialog_set_format(const dialog_set_t *set, char *out, size_t cap);

// Parses an index. False (and *set zeroed) unless the locale is a plain code
// ([a-z0-9-], 2..11 chars) and every phase has 1..DIALOG_MAX_CLIPS clips of
// non-zero length whose end does not overflow 32 bits.
bool dialog_set_parse(const char *text, dialog_set_t *set);
