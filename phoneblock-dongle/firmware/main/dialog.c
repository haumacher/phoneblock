// Implementation of dialog.h.

#include "dialog.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>


// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static const char *const PHASE_NAMES[DIALOG_PHASE_COUNT] = {
    [DIALOG_PHASE_HELLO]         = "hello",
    [DIALOG_PHASE_WHO_IS_THERE]  = "who-is-there",
    [DIALOG_PHASE_AM_APARAT]     = "am-aparat",
    [DIALOG_PHASE_QUESTION]      = "question",
    [DIALOG_PHASE_CANT_HEAR_YOU] = "cant-hear-you",
};

const char *dialog_phase_name(dialog_phase_t phase)
{
    if ((int)phase < 0 || phase >= DIALOG_PHASE_COUNT) return NULL;
    return PHASE_NAMES[phase];
}

static dialog_action_t act(dialog_act_t kind)
{
    dialog_action_t a = { .kind = kind, .phase = DIALOG_PHASE_HELLO, .clip = 0 };
    return a;
}

// Picks a clip of `phase` and switches to saying it. With more than one clip
// the previous one is excluded, the others are equally likely: draw from the
// n-1 remaining and skip over the excluded index.
static dialog_action_t say(dialog_sm_t *d, dialog_phase_t phase)
{
    int n = d->count[phase];
    int last = d->last[phase];
    int clip = 0;
    if (n > 1) {
        uint32_t r = d->rng(d->rng_ctx);
        if (last < 0) {
            clip = (int)(r % (uint32_t)n);
        } else {
            clip = (int)(r % (uint32_t)(n - 1));
            if (clip >= last) clip++;
        }
    }
    d->last[phase] = clip;
    d->mode = DIALOG_SM_SAYING;
    dialog_action_t a = { .kind = DIALOG_ACT_SAY, .phase = phase, .clip = clip };
    return a;
}

dialog_action_t dialog_start(dialog_sm_t *d,
                             const uint8_t count[DIALOG_PHASE_COUNT],
                             dialog_rng_fn rng, void *rng_ctx)
{
    memset(d, 0, sizeof(*d));
    d->rng     = rng;
    d->rng_ctx = rng_ctx;
    for (int p = 0; p < DIALOG_PHASE_COUNT; p++) {
        d->count[p] = count[p];
        d->last[p]  = -1;
    }
    for (int p = 0; p < DIALOG_PHASE_COUNT; p++) {
        if (count[p] == 0 || count[p] > DIALOG_MAX_CLIPS) {
            d->mode = DIALOG_SM_DONE;
            return act(DIALOG_ACT_END);
        }
    }
    return say(d, DIALOG_PHASE_HELLO);
}

void dialog_rx(dialog_sm_t *d)
{
    d->quiet_ms = 0;
}

bool dialog_media_lost(const dialog_sm_t *d)
{
    return d->mode == DIALOG_SM_DONE && d->quiet_ms >= DIALOG_RX_TIMEOUT_MS;
}

void dialog_vad(dialog_sm_t *d, vad_event_t event)
{
    if (d->mode != DIALOG_SM_AWAITING) return;
    if (event == VAD_SPEECH_ONSET) {
        d->spoke     = true;
        d->turn_over = false;   // talking again: their turn goes on
    } else if (event == VAD_SILENCE_ONSET && d->spoke) {
        // A silence onset before any speech is just the leading quiet of the
        // await; only one after speech ends the caller's turn.
        d->turn_over = true;
    }
}

// An await resolved (`spoke`: the caller said something): the next prompt
// per the fixed flow.
static dialog_action_t resolve(dialog_sm_t *d, bool spoke)
{
    if (!d->in_question) {
        if (!spoke) return say(d, DIALOG_PHASE_WHO_IS_THERE);
        d->in_question = true;
        return say(d, DIALOG_PHASE_AM_APARAT);
    }
    return say(d, spoke ? DIALOG_PHASE_QUESTION : DIALOG_PHASE_CANT_HEAR_YOU);
}

dialog_action_t dialog_tick(dialog_sm_t *d, uint32_t ms, bool clip_done)
{
    if (d->mode == DIALOG_SM_DONE) return act(DIALOG_ACT_END);

    // Saturating: an endless call must not wrap the counters (49 days).
    d->elapsed_ms = d->elapsed_ms > UINT32_MAX - ms ? UINT32_MAX : d->elapsed_ms + ms;
    d->quiet_ms   = d->quiet_ms   > UINT32_MAX - ms ? UINT32_MAX : d->quiet_ms + ms;
    if (d->quiet_ms >= DIALOG_RX_TIMEOUT_MS) {
        d->mode = DIALOG_SM_DONE;
        return act(DIALOG_ACT_END);
    }

    if (d->mode == DIALOG_SM_SAYING) {
        if (!clip_done) return act(DIALOG_ACT_NONE);
        d->mode      = DIALOG_SM_AWAITING;
        d->spoke     = false;
        d->turn_over = false;
        d->await_ms  = 0;
        return act(DIALOG_ACT_LISTEN);
    }

    // Awaiting the caller.
    d->await_ms += ms;
    if (d->spoke && d->turn_over) return resolve(d, true);
    if (!d->spoke && d->await_ms >= DIALOG_AWAIT_TIMEOUT_MS) return resolve(d, false);
    return act(DIALOG_ACT_NONE);
}

// --- Clip-set header --------------------------------------------------------

bool dialog_lang_valid(const char *s)
{
    if (!s) return false;
    // memchr stops at the terminator: never reads past a short string.
    const char *z = memchr(s, '\0', 12);
    size_t n = z ? (size_t)(z - s) : 12;
    if (n < 2 || n > 11) return false;
    if (s[0] == '-' || s[n - 1] == '-') return false;
    for (size_t i = 0; i < n; i++) {
        char c = s[i];
        bool ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') ||
                  (c == '-' && s[i - 1] != '-');
        if (!ok) return false;
    }
    return true;
}

static uint32_t le32(const uint8_t *p)
{
    return (uint32_t)p[0] | (uint32_t)p[1] << 8 | (uint32_t)p[2] << 16 |
           (uint32_t)p[3] << 24;
}

static dialog_header_result_t header_fail(dialog_set_t *set,
                                          dialog_header_result_t r)
{
    memset(set->count, 0, sizeof(set->count));
    memset(set->clip, 0, sizeof(set->clip));
    return r;
}

dialog_header_result_t dialog_header_parse(const uint8_t *buf, size_t n,
                                           uint64_t file_size, dialog_set_t *set)
{
    header_fail(set, DIALOG_HEADER_OK);
    if (n > file_size) n = (size_t)file_size;   // never trust bytes past the end
    if (n < DIALOG_HEADER_MIN) {
        // Too few bytes for the fixed part: SHORT only while the file could
        // still hold one.
        return header_fail(set, file_size < DIALOG_HEADER_MIN ? DIALOG_HEADER_BAD
                                                              : DIALOG_HEADER_SHORT);
    }
    if (memcmp(buf, "PBDS", 4) != 0) return header_fail(set, DIALOG_HEADER_BAD);
    if (buf[4] != DIALOG_HEADER_VERSION) return header_fail(set, DIALOG_HEADER_BAD);
    uint32_t phases = buf[5];
    uint32_t hlen   = (uint32_t)buf[6] | (uint32_t)buf[7] << 8;
    if (phases < DIALOG_PHASE_COUNT) return header_fail(set, DIALOG_HEADER_BAD);
    if (hlen > DIALOG_HEADER_MAX || hlen > file_size) {
        return header_fail(set, DIALOG_HEADER_BAD);
    }
    if (n < DIALOG_HEADER_MIN + phases) return header_fail(set, DIALOG_HEADER_SHORT);

    // The table's size from the counts, before any entry is read.
    uint32_t clips = 0;
    for (uint32_t p = 0; p < phases; p++) {
        uint8_t c = buf[DIALOG_HEADER_MIN + p];
        if (p < DIALOG_PHASE_COUNT && (c < 1 || c > DIALOG_MAX_CLIPS)) {
            return header_fail(set, DIALOG_HEADER_BAD);
        }
        clips += c;
    }
    if (hlen != DIALOG_HEADER_MIN + phases + 8u * clips) {
        return header_fail(set, DIALOG_HEADER_BAD);
    }
    if (n < hlen) return header_fail(set, DIALOG_HEADER_SHORT);

    const uint8_t *e = buf + DIALOG_HEADER_MIN + phases;
    for (int p = 0; p < DIALOG_PHASE_COUNT; p++) {
        uint8_t c = buf[DIALOG_HEADER_MIN + p];
        for (int k = 0; k < c; k++, e += 8) {
            uint32_t off = le32(e), len = le32(e + 4);
            if (len == 0 || off < hlen || (uint64_t)off + len > file_size) {
                return header_fail(set, DIALOG_HEADER_BAD);
            }
            set->clip[p][k].off = off;
            set->clip[p][k].len = len;
        }
        set->count[p] = c;
    }
    // Entries of further phases are not this firmware's to check.
    return DIALOG_HEADER_OK;
}
