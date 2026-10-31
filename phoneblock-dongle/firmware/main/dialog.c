// Implementation of dialog.h.

#include "dialog.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "strbuf.h"

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

// --- Clip-set index ---------------------------------------------------------

static bool lang_ok(const char *s, size_t n)
{
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

bool dialog_set_equal(const dialog_set_t *a, const dialog_set_t *b)
{
    if (strcmp(a->lang, b->lang) != 0) return false;
    for (int p = 0; p < DIALOG_PHASE_COUNT; p++) {
        if (a->count[p] != b->count[p]) return false;
        for (int c = 0; c < a->count[p] && c < DIALOG_MAX_CLIPS; c++) {
            if (a->clip[p][c].off != b->clip[p][c].off ||
                a->clip[p][c].len != b->clip[p][c].len) return false;
        }
    }
    return true;
}

bool dialog_set_within(const dialog_set_t *set, uint64_t bytes)
{
    for (int p = 0; p < DIALOG_PHASE_COUNT; p++) {
        if (set->count[p] < 1 || set->count[p] > DIALOG_MAX_CLIPS) return false;
        for (int c = 0; c < set->count[p]; c++) {
            const dialog_clip_t *k = &set->clip[p][c];
            if (k->len == 0) return false;
            if ((uint64_t)k->off + k->len > bytes) return false;
        }
    }
    return true;
}

int dialog_set_format(const dialog_set_t *set, char *out, size_t cap)
{
    if (cap == 0 || cap > INT32_MAX) return -1;
    strbuf_t sb = sb_init(out, (int)cap);
    sb_appendf(&sb, "%s\n", set->lang);
    for (int p = 0; p < DIALOG_PHASE_COUNT; p++) {
        for (int c = 0; c < set->count[p]; c++) {
            sb_appendf(&sb, "%s%lu:%lu", c ? " " : "",
                       (unsigned long)set->clip[p][c].off,
                       (unsigned long)set->clip[p][c].len);
        }
        sb_appendf(&sb, "\n");
    }
    if (sb.truncated) return -1;
    return sb.len;
}

// A decimal uint32 at *p; advances *p. False if there is none or it overflows.
static bool parse_u32(const char **p, uint32_t *out)
{
    const char *s = *p;
    if (*s < '0' || *s > '9') return false;
    uint64_t v = 0;
    while (*s >= '0' && *s <= '9') {
        v = v * 10u + (uint64_t)(*s - '0');
        if (v > UINT32_MAX) return false;
        s++;
    }
    *out = (uint32_t)v;
    *p = s;
    return true;
}

bool dialog_set_parse(const char *text, dialog_set_t *set)
{
    memset(set, 0, sizeof(*set));
    if (!text) return false;

    dialog_set_t s;
    memset(&s, 0, sizeof(s));
    const char *p = text;
    size_t n = strcspn(p, "\n");
    if (!lang_ok(p, n) || p[n] != '\n') return false;
    memcpy(s.lang, p, n);
    p += n + 1;

    for (int ph = 0; ph < DIALOG_PHASE_COUNT; ph++) {
        int c = 0;
        for (;;) {
            if (c == DIALOG_MAX_CLIPS) return false;
            dialog_clip_t *k = &s.clip[ph][c];
            if (!parse_u32(&p, &k->off) || *p++ != ':') return false;
            if (!parse_u32(&p, &k->len) || k->len == 0) return false;
            if ((uint64_t)k->off + k->len > UINT32_MAX) return false;
            c++;
            if (*p == ' ') { p++; continue; }
            if (*p == '\n') { p++; break; }
            return false;
        }
        s.count[ph] = (uint8_t)c;
    }
    if (*p != '\0') return false;
    *set = s;
    return true;
}
