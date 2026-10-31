// Host test for the interactive answer-bot dialog (issue #429): the state
// machine (dialog.c), the clip-set header (dialog.c), the choice of a size
// tier from the i18n manifest (dialog_manifest.c), the clip store
// (dialog_store.c, in a temp directory) and the RTP receive parser (rtp_rx.c).
#define _POSIX_C_SOURCE 200809L
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "cJSON.h"
#include "dialog.h"
#include "dialog_manifest.h"
#include "dialog_store.h"
#include "platform.h"
#include "rtp_rx.h"

static int failures = 0;
static int checks = 0;

#define CHECK(cond) do {                                                \
    checks++;                                                           \
    if (!(cond)) {                                                      \
        failures++;                                                     \
        fprintf(stderr, "FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond); \
    }                                                                   \
} while (0)

#define TICK 20

// --- Deterministic random sources -------------------------------------------

static uint32_t rng_zero(void *ctx)
{
    (void)ctx;
    return 0;
}

// Cycles through a fixed sequence.
typedef struct { const uint32_t *v; int n; int i; } seq_rng_t;

static uint32_t rng_seq(void *ctx)
{
    seq_rng_t *s = ctx;
    uint32_t r = s->v[s->i % s->n];
    s->i++;
    return r;
}

static const uint8_t ONE_EACH[DIALOG_PHASE_COUNT] = { 1, 1, 1, 1, 1 };

// Ticks until the dialog asks for something; returns that action. Sends
// clip_done on the first tick (the prompt was played).
static dialog_action_t finish_clip(dialog_sm_t *d)
{
    return dialog_tick(d, TICK, true);
}

// Ticks `ms` of an await without VAD events; returns the first non-NONE
// action, or NONE if nothing happened.
static dialog_action_t wait_ms(dialog_sm_t *d, uint32_t ms, uint32_t *elapsed)
{
    for (uint32_t t = 0; t < ms; t += TICK) {
        dialog_action_t a = dialog_tick(d, TICK, false);
        if (a.kind != DIALOG_ACT_NONE) {
            if (elapsed) *elapsed = t + TICK;
            return a;
        }
    }
    dialog_action_t none = { .kind = DIALOG_ACT_NONE };
    return none;
}

// The caller speaks, then the VAD reports their silence: what the dialog
// sees after a turn.
static dialog_action_t caller_turn(dialog_sm_t *d)
{
    CHECK(dialog_tick(d, TICK, false).kind == DIALOG_ACT_NONE);
    dialog_vad(d, VAD_SPEECH_ONSET);
    CHECK(dialog_tick(d, TICK, false).kind == DIALOG_ACT_NONE);
    dialog_vad(d, VAD_SILENCE_ONSET);
    return dialog_tick(d, TICK, false);
}

static bool says(dialog_action_t a, dialog_phase_t phase)
{
    return a.kind == DIALOG_ACT_SAY && a.phase == phase;
}

// --- State machine -----------------------------------------------------------

static void test_hello_then_silence_asks_who_is_there(void)
{
    dialog_sm_t d;
    dialog_action_t a = dialog_start(&d, ONE_EACH, rng_zero, NULL);
    CHECK(says(a, DIALOG_PHASE_HELLO) && a.clip == 0);

    // Playing the hello clip: nothing to do until it is over.
    CHECK(dialog_tick(&d, TICK, false).kind == DIALOG_ACT_NONE);
    CHECK(finish_clip(&d).kind == DIALOG_ACT_LISTEN);

    // No speech: exactly after the 3 s await timeout, who-is-there.
    uint32_t at = 0;
    a = wait_ms(&d, 5000, &at);
    CHECK(says(a, DIALOG_PHASE_WHO_IS_THERE));
    CHECK(at == DIALOG_AWAIT_TIMEOUT_MS);

    // Still silent after who-is-there: the intro repeats it.
    CHECK(finish_clip(&d).kind == DIALOG_ACT_LISTEN);
    CHECK(says(wait_ms(&d, 5000, NULL), DIALOG_PHASE_WHO_IS_THERE));
}

static void test_speech_leads_to_am_aparat_then_questions(void)
{
    dialog_sm_t d;
    dialog_start(&d, ONE_EACH, rng_zero, NULL);
    CHECK(finish_clip(&d).kind == DIALOG_ACT_LISTEN);

    // The caller answers the hello: am-aparat, and the question loop starts.
    CHECK(says(caller_turn(&d), DIALOG_PHASE_AM_APARAT));
    CHECK(finish_clip(&d).kind == DIALOG_ACT_LISTEN);
    CHECK(says(caller_turn(&d), DIALOG_PHASE_QUESTION));

    // Question stage, caller silent: cant-hear-you after the timeout.
    CHECK(finish_clip(&d).kind == DIALOG_ACT_LISTEN);
    uint32_t at = 0;
    CHECK(says(wait_ms(&d, 5000, &at), DIALOG_PHASE_CANT_HEAR_YOU));
    CHECK(at == DIALOG_AWAIT_TIMEOUT_MS);

    // ... and when they speak again, another question (never back to intro).
    CHECK(finish_clip(&d).kind == DIALOG_ACT_LISTEN);
    CHECK(says(caller_turn(&d), DIALOG_PHASE_QUESTION));
}

static void test_turn_waits_for_min_silence(void)
{
    dialog_sm_t d;
    dialog_start(&d, ONE_EACH, rng_zero, NULL);
    CHECK(finish_clip(&d).kind == DIALOG_ACT_LISTEN);

    // A silence onset before any speech is the await's leading quiet.
    dialog_vad(&d, VAD_SILENCE_ONSET);
    CHECK(dialog_tick(&d, TICK, false).kind == DIALOG_ACT_NONE);

    // Once the caller speaks, the 3 s timeout no longer applies: the turn
    // lasts until the VAD reports their silence, however long they talk.
    dialog_vad(&d, VAD_SPEECH_ONSET);
    CHECK(wait_ms(&d, 10000, NULL).kind == DIALOG_ACT_NONE);
    dialog_vad(&d, VAD_SILENCE_ONSET);
    CHECK(says(dialog_tick(&d, TICK, false), DIALOG_PHASE_AM_APARAT));
}

static void test_vad_ignored_while_saying(void)
{
    dialog_sm_t d;
    dialog_start(&d, ONE_EACH, rng_zero, NULL);
    // The caller talks over the hello: does not count as an answer.
    dialog_vad(&d, VAD_SPEECH_ONSET);
    dialog_vad(&d, VAD_SILENCE_ONSET);
    CHECK(finish_clip(&d).kind == DIALOG_ACT_LISTEN);
    CHECK(says(wait_ms(&d, 5000, NULL), DIALOG_PHASE_WHO_IS_THERE));
}

// Plays the dialog into the question loop and returns the clip index of
// `rounds` consecutive questions.
static void question_clips(dialog_sm_t *d, int *out, int rounds)
{
    CHECK(finish_clip(d).kind == DIALOG_ACT_LISTEN);
    CHECK(says(caller_turn(d), DIALOG_PHASE_AM_APARAT));
    for (int i = 0; i < rounds; i++) {
        CHECK(finish_clip(d).kind == DIALOG_ACT_LISTEN);
        dialog_action_t a = caller_turn(d);
        CHECK(says(a, DIALOG_PHASE_QUESTION));
        out[i] = a.clip;
    }
}

static void test_no_immediate_repeat_with_three_clips(void)
{
    const uint8_t count[DIALOG_PHASE_COUNT] = { 1, 1, 1, 3, 1 };
    // A source that would pick the same index over and over if allowed.
    static const uint32_t same[] = { 7, 7, 7, 7 };
    seq_rng_t rs = { same, 4, 0 };
    dialog_sm_t d;
    dialog_start(&d, count, rng_seq, &rs);
    int clips[40];
    question_clips(&d, clips, 40);
    int seen[3] = { 0, 0, 0 };
    for (int i = 0; i < 40; i++) {
        CHECK(clips[i] >= 0 && clips[i] < 3);
        if (clips[i] >= 0 && clips[i] < 3) seen[clips[i]]++;
        if (i > 0) CHECK(clips[i] != clips[i - 1]);
    }

    // With a varied source all three come up, still never twice in a row.
    static const uint32_t varied[] = { 0, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89 };
    seq_rng_t rv = { varied, 11, 0 };
    dialog_start(&d, count, rng_seq, &rv);
    question_clips(&d, clips, 40);
    memset(seen, 0, sizeof(seen));
    for (int i = 0; i < 40; i++) {
        if (clips[i] >= 0 && clips[i] < 3) seen[clips[i]]++;
        if (i > 0) CHECK(clips[i] != clips[i - 1]);
    }
    CHECK(seen[0] > 0 && seen[1] > 0 && seen[2] > 0);
}

static void test_single_clip_repeats(void)
{
    dialog_sm_t d;
    dialog_start(&d, ONE_EACH, rng_zero, NULL);
    int clips[5];
    question_clips(&d, clips, 5);
    for (int i = 0; i < 5; i++) CHECK(clips[i] == 0);
}

// Drives a silent caller whose media keeps arriving (a packet every tick when
// `packets`): prompts end at once, awaits time out. Returns the time of the
// END, or 0 if none came within `ms`.
static uint32_t run_line(dialog_sm_t *d, uint32_t ms, bool packets)
{
    bool saying = true;
    for (uint32_t t = TICK; t <= ms; t += TICK) {
        if (packets) dialog_rx(d);
        dialog_action_t a = dialog_tick(d, TICK, saying);
        if (a.kind == DIALOG_ACT_END) return t;
        if (a.kind == DIALOG_ACT_LISTEN) saying = false;
        if (a.kind == DIALOG_ACT_SAY) saying = true;
    }
    return 0;
}

static void test_packets_keep_dialog_alive(void)
{
    // No time cap: two hours of a line that keeps sending media — silent,
    // so prompts and awaits alternate the whole time — never end.
    dialog_sm_t d;
    dialog_start(&d, ONE_EACH, rng_zero, NULL);
    CHECK(run_line(&d, 2u * 3600u * 1000u, true) == 0);
    CHECK(d.elapsed_ms == 2u * 3600u * 1000u);

    // A caller who talks without pause for an hour: their turn just lasts.
    dialog_start(&d, ONE_EACH, rng_zero, NULL);
    CHECK(finish_clip(&d).kind == DIALOG_ACT_LISTEN);
    dialog_vad(&d, VAD_SPEECH_ONSET);
    bool ended = false;
    for (uint32_t t = 0; t < 3600u * 1000u; t += TICK) {
        dialog_rx(&d);
        if (dialog_tick(&d, TICK, false).kind != DIALOG_ACT_NONE) ended = true;
    }
    CHECK(!ended);
}

static void test_no_packets_end_after_rx_timeout(void)
{
    // The caller's media stops (lost BYE): END exactly 30 s after the last
    // packet — here: none since the start.
    dialog_sm_t d;
    dialog_start(&d, ONE_EACH, rng_zero, NULL);
    CHECK(run_line(&d, 60000, false) == DIALOG_RX_TIMEOUT_MS);
    CHECK(dialog_media_lost(&d));
    // And it stays over.
    CHECK(dialog_tick(&d, TICK, true).kind == DIALOG_ACT_END);
    dialog_rx(&d);
    dialog_vad(&d, VAD_SPEECH_ONSET);
    CHECK(dialog_tick(&d, TICK, false).kind == DIALOG_ACT_END);

    // Also in the middle of a prompt that never finishes.
    dialog_start(&d, ONE_EACH, rng_zero, NULL);
    uint32_t end = 0;
    for (uint32_t t = TICK; t <= 60000 && !end; t += TICK) {
        if (dialog_tick(&d, TICK, false).kind == DIALOG_ACT_END) end = t;
    }
    CHECK(end == DIALOG_RX_TIMEOUT_MS);
    CHECK(d.mode == DIALOG_SM_DONE);
}

static void test_packet_resets_rx_timeout(void)
{
    // A packet at 29.98 s (the last tick before the timeout) restarts the
    // 30 s: the END comes 30 s after it, not at 30 s.
    dialog_sm_t d;
    dialog_start(&d, ONE_EACH, rng_zero, NULL);
    uint32_t last_rx = DIALOG_RX_TIMEOUT_MS - TICK;
    uint32_t end = 0;
    bool saying = true;
    for (uint32_t t = TICK; t <= 120000 && !end; t += TICK) {
        if (t == last_rx) dialog_rx(&d);
        dialog_action_t a = dialog_tick(&d, TICK, saying);
        if (a.kind == DIALOG_ACT_END) end = t;
        if (a.kind == DIALOG_ACT_LISTEN) saying = false;
        if (a.kind == DIALOG_ACT_SAY) saying = true;
    }
    // The packet arrives before the tick at last_rx counts, so the quiet
    // starts with that tick.
    CHECK(end == last_rx - TICK + DIALOG_RX_TIMEOUT_MS);
    CHECK(dialog_media_lost(&d));
}

static void test_incomplete_set_refused(void)
{
    const uint8_t count[DIALOG_PHASE_COUNT] = { 1, 1, 0, 1, 1 };
    dialog_sm_t d;
    CHECK(dialog_start(&d, count, rng_zero, NULL).kind == DIALOG_ACT_END);
    CHECK(!dialog_media_lost(&d));   // the set, not the caller's media
    CHECK(dialog_tick(&d, TICK, true).kind == DIALOG_ACT_END);
}

static void test_phase_names(void)
{
    CHECK(strcmp(dialog_phase_name(DIALOG_PHASE_HELLO), "hello") == 0);
    CHECK(strcmp(dialog_phase_name(DIALOG_PHASE_WHO_IS_THERE), "who-is-there") == 0);
    CHECK(strcmp(dialog_phase_name(DIALOG_PHASE_AM_APARAT), "am-aparat") == 0);
    CHECK(strcmp(dialog_phase_name(DIALOG_PHASE_QUESTION), "question") == 0);
    CHECK(strcmp(dialog_phase_name(DIALOG_PHASE_CANT_HEAR_YOU), "cant-hear-you") == 0);
    CHECK(dialog_phase_name(DIALOG_PHASE_COUNT) == NULL);
}

// --- Clip-set header ------------------------------------------------------------

static void put32(uint8_t *p, uint32_t v)
{
    p[0] = (uint8_t)v; p[1] = (uint8_t)(v >> 8);
    p[2] = (uint8_t)(v >> 16); p[3] = (uint8_t)(v >> 24);
}

// Builds a set file as audio-assets.sh does: the header for `phases` phases
// with counts[p] clips each, then the clips (lens[], phase by phase, each
// filled with 0xD5) back to back. Returns the file's length.
static size_t build_set(uint8_t *out, size_t cap, int phases,
                        const uint8_t *counts, const uint32_t *lens)
{
    size_t clips = 0;
    for (int p = 0; p < phases; p++) clips += counts[p];
    size_t hlen = 8 + (size_t)phases + 8 * clips;
    size_t total = hlen;
    for (size_t i = 0; i < clips; i++) total += lens[i];
    CHECK(total <= cap);
    if (total > cap) return 0;
    memset(out, 0xD5, total);
    memcpy(out, "PBDS", 4);
    out[4] = DIALOG_HEADER_VERSION;
    out[5] = (uint8_t)phases;
    out[6] = (uint8_t)hlen; out[7] = (uint8_t)(hlen >> 8);
    uint32_t off = (uint32_t)hlen;
    uint8_t *e = out + 8 + phases;
    for (int p = 0; p < phases; p++) out[8 + p] = counts[p];
    for (size_t i = 0; i < clips; i++, e += 8) {
        put32(e, off);
        put32(e + 4, lens[i]);
        off += lens[i];
    }
    return total;
}

// A German-sized set: 2 hello, 2 who-is-there, 3 am-aparat, 4 questions,
// 2 cant-hear-you (the TTS set's shape), short clips.
static const uint8_t DE_COUNTS[DIALOG_PHASE_COUNT] = { 2, 2, 3, 4, 2 };
static const uint32_t DE_LENS[13] = { 101, 102, 103, 104, 105, 106, 107,
                                      108, 109, 110, 111, 112, 113 };
#define DE_HLEN (8 + 5 + 8 * 13)

static void test_header_parse(void)
{
    static uint8_t f[4096];
    size_t n = build_set(f, sizeof(f), 5, DE_COUNTS, DE_LENS);
    dialog_set_t set;
    memset(&set, 0, sizeof(set));
    snprintf(set.lang, sizeof(set.lang), "de");
    CHECK(dialog_header_parse(f, n, n, &set) == DIALOG_HEADER_OK);
    CHECK(strcmp(set.lang, "de") == 0);   // the caller's
    uint32_t off = DE_HLEN;
    int i = 0;
    for (int p = 0; p < DIALOG_PHASE_COUNT; p++) {
        CHECK(set.count[p] == DE_COUNTS[p]);
        for (int c = 0; c < DE_COUNTS[p]; c++, i++) {
            CHECK(set.clip[p][c].off == off && set.clip[p][c].len == DE_LENS[i]);
            off += DE_LENS[i];
        }
    }
    CHECK(off == n);
    // Only the header needs to be in the buffer.
    CHECK(dialog_header_parse(f, DE_HLEN, n, &set) == DIALOG_HEADER_OK);

    // Truncated: the header not read completely yet, or the file itself
    // ending early (the last clip past its end), or too short for anything.
    CHECK(dialog_header_parse(f, DE_HLEN - 1, n, &set) == DIALOG_HEADER_SHORT);
    CHECK(set.count[0] == 0 && set.clip[0][0].len == 0);   // cleared
    CHECK(dialog_header_parse(f, 7, n, &set) == DIALOG_HEADER_SHORT);
    CHECK(dialog_header_parse(f, 12, n, &set) == DIALOG_HEADER_SHORT);
    CHECK(dialog_header_parse(f, n - 1, n - 1, &set) == DIALOG_HEADER_BAD);
    CHECK(dialog_header_parse(f, DE_HLEN - 1, DE_HLEN - 1, &set) == DIALOG_HEADER_BAD);
    CHECK(dialog_header_parse(f, 7, 7, &set) == DIALOG_HEADER_BAD);
    CHECK(dialog_header_parse(f, 0, 0, &set) == DIALOG_HEADER_BAD);
    // Bytes past the stated file size do not count.
    CHECK(dialog_header_parse(f, n, DE_HLEN - 1, &set) == DIALOG_HEADER_BAD);

    // Malformed: each variant of the valid file.
    static uint8_t g[4096];
    struct { size_t at; uint8_t v; } bad[] = {
        { 0, 'X' },                       // magic
        { 4, DIALOG_HEADER_VERSION + 1 }, // version
        { 4, 0 },
        { 5, 4 },                         // fewer phases than this firmware has
        { 6, DE_HLEN + 1 },               // header length off by one
        { 6, DE_HLEN - 1 },
        { 8, 0 },                         // a phase without a clip
        { 8 + 3, 5 },                     // counts no longer match the length
        { 8 + 5 + 4, 0 },                 // first clip: length 0
        { 8 + 5 + 0, 0 },                 // first clip inside the header
        { 8 + 5 + 8 * 12 + 2, 1 },        // last clip past the end of the file
        { 8 + 5 + 8 * 12 + 7, 0xFF },     // ... a 32-bit length overflowing it
    };
    for (size_t k = 0; k < sizeof(bad) / sizeof(bad[0]); k++) {
        memcpy(g, f, n);
        g[bad[k].at] = bad[k].v;
        if (bad[k].at == 8 + 5 + 8 * 12 + 7) g[8 + 5 + 8 * 12 + 3] = 0xFF;  // offset too
        CHECK(dialog_header_parse(g, n, n, &set) == DIALOG_HEADER_BAD);
        CHECK(set.count[0] == 0);
    }
    // A header length beyond DIALOG_HEADER_MAX (the device reads no more).
    memcpy(g, f, n);
    g[6] = (uint8_t)(DIALOG_HEADER_MAX + 8); g[7] = (uint8_t)((DIALOG_HEADER_MAX + 8) >> 8);
    CHECK(dialog_header_parse(g, n, 100000, &set) == DIALOG_HEADER_BAD);

    // DIALOG_MAX_CLIPS per phase fits the header limit; one more does not
    // parse.
    uint8_t max[DIALOG_PHASE_COUNT] = { DIALOG_MAX_CLIPS, DIALOG_MAX_CLIPS, DIALOG_MAX_CLIPS,
                                        DIALOG_MAX_CLIPS, DIALOG_MAX_CLIPS };
    static uint32_t lens[DIALOG_PHASE_COUNT * (DIALOG_MAX_CLIPS + 1)];
    for (size_t k = 0; k < sizeof(lens) / sizeof(lens[0]); k++) lens[k] = 1;
    n = build_set(g, sizeof(g), 5, max, lens);
    CHECK(8 + 5 + 8 * 5 * DIALOG_MAX_CLIPS <= DIALOG_HEADER_MAX);
    CHECK(dialog_header_parse(g, n, n, &set) == DIALOG_HEADER_OK);
    CHECK(set.count[DIALOG_PHASE_QUESTION] == DIALOG_MAX_CLIPS);
    CHECK(set.clip[DIALOG_PHASE_CANT_HEAR_YOU][DIALOG_MAX_CLIPS - 1].off == n - 1);
    max[DIALOG_PHASE_QUESTION]++;
    n = build_set(g, sizeof(g), 5, max, lens);
    CHECK(dialog_header_parse(g, n, n, &set) == DIALOG_HEADER_BAD);
}

static void test_lang_valid(void)
{
    CHECK(dialog_lang_valid("de") && dialog_lang_valid("zh-hans") &&
          dialog_lang_valid("abcdefghijk"));
    const char *bad[] = { NULL, "", "d", "De", "../x", "de/x", "-de", "de-",
                          "zh--hans", "abcdefghijkl", "de.alaw" };
    for (size_t i = 0; i < sizeof(bad) / sizeof(bad[0]); i++) CHECK(!dialog_lang_valid(bad[i]));
}

// --- Manifest selection ----------------------------------------------------------

#define SHA "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

#define TIER(path, bytes) \
    "{\"path\":\"" path "\",\"sha256\":\"" SHA "\",\"bytes\":" #bytes "}"

// German in three tiers, as audio-assets.sh publishes a large set.
#define DE_TIERS "\"dialog\":[" TIER("audio/dialog-de-s.alaw", 130000) "," \
                 TIER("audio/dialog-de-m.alaw", 200000) "," \
                 TIER("audio/dialog-de-l.alaw", 300000) "]"

#define ANN(l) "\"announcement\":{\"path\":\"audio/announcement-" l ".alaw\"," \
               "\"sha256\":\"" SHA "\",\"bytes\":78762}"

static cJSON *assets_of(cJSON *root)
{
    return cJSON_GetObjectItem(root, "assets");
}

static bool picks(cJSON *root, const char *const *chain, int n, uint64_t budget,
                  const char *lang, const char *path)
{
    dialog_manifest_set_t set;
    if (!dialog_manifest_select(assets_of(root), chain, n, budget, &set)) return false;
    return set.lang && strcmp(set.lang, lang) == 0 && set.path &&
           strcmp(set.path, path) == 0 && set.sha256 && strcmp(set.sha256, SHA) == 0;
}

static void test_manifest_tier_by_budget(void)
{
    cJSON *root = cJSON_Parse("{\"version\":\"1.7.0\",\"assets\":{"
                              "\"de\":{" ANN("de") "," DE_TIERS "}}}");
    CHECK(root != NULL);
    const char *de[] = { "de" };
    CHECK(picks(root, de, 1, 131072, "de", "audio/dialog-de-s.alaw"));
    CHECK(picks(root, de, 1, 229376, "de", "audio/dialog-de-m.alaw"));
    CHECK(picks(root, de, 1, UINT64_MAX, "de", "audio/dialog-de-l.alaw"));
    // The boundaries: a tier of exactly the budget fits, one byte less not.
    CHECK(picks(root, de, 1, 200000, "de", "audio/dialog-de-m.alaw"));
    CHECK(picks(root, de, 1, 199999, "de", "audio/dialog-de-s.alaw"));
    CHECK(picks(root, de, 1, 130000, "de", "audio/dialog-de-s.alaw"));
    dialog_manifest_set_t set;
    CHECK(!dialog_manifest_select(assets_of(root), de, 1, 129999, &set));
    CHECK(set.lang == NULL && set.path == NULL && set.bytes == 0);
    CHECK(dialog_manifest_select(assets_of(root), de, 1, 300000, &set) && set.bytes == 300000);
    cJSON_Delete(root);

    // The order in the list does not matter, nor does a single tier.
    root = cJSON_Parse("{\"assets\":{\"de\":{\"dialog\":["
                       TIER("l.alaw", 300000) "," TIER("s.alaw", 130000) ","
                       TIER("m.alaw", 200000) "]},"
                       "\"it\":{\"dialog\":[" TIER("it.alaw", 90000) "]}}}");
    CHECK(picks(root, de, 1, 250000, "de", "m.alaw"));
    const char *it[] = { "it" };
    CHECK(picks(root, it, 1, 131072, "it", "it.alaw"));
    CHECK(picks(root, it, 1, UINT64_MAX, "it", "it.alaw"));
    cJSON_Delete(root);
}

static void test_manifest_chain_fallback(void)
{
    cJSON *root = cJSON_Parse(
        "{\"version\":\"1.7.0\",\"assets\":{"
        "\"fr\":{" ANN("fr") "},"
        "\"en\":{" ANN("en") ",\"dialog\":[" TIER("audio/dialog-en-m.alaw", 150000) "]},"
        "\"de\":{" ANN("de") "," DE_TIERS "}}}");
    CHECK(root != NULL);
    const char *chain[] = { "fr", "en", "de" };
    // No set for fr; en has only a tier above the small budget, so the whole
    // German set is taken — never German tiers mixed with English ones.
    CHECK(picks(root, chain, 3, 131072, "de", "audio/dialog-de-s.alaw"));
    // With room for it, English wins.
    CHECK(picks(root, chain, 3, 229376, "en", "audio/dialog-en-m.alaw"));
    CHECK(picks(root, chain, 3, UINT64_MAX, "en", "audio/dialog-en-m.alaw"));
    // A chain that ends before German has nothing for the small budget.
    dialog_manifest_set_t set;
    CHECK(!dialog_manifest_select(assets_of(root), chain, 2, 131072, &set));
    // An invalid chain entry is not looked up (it would name the file).
    const char *bogus[] = { "../de", "de" };
    CHECK(picks(root, bogus, 2, 131072, "de", "audio/dialog-de-s.alaw"));
    cJSON_Delete(root);
}

// Each malformed variant skips its locale as a whole, German following —
// also when the bad tier is not the one the budget would pick.
static void test_manifest_malformed_locale_skipped(void)
{
    static const char *const bad_it[] = {
        "{}", "\"x\"", "[]", "[1]", "null",
        // the single-object form of an earlier draft
        TIER("a.alaw", 50000),
        // short sha / no path / empty path / no bytes / too few bytes /
        // fractional / negative / not a number
        "[{\"path\":\"a.alaw\",\"sha256\":\"abc\",\"bytes\":50000}]",
        "[{\"sha256\":\"" SHA "\",\"bytes\":50000}]",
        "[{\"path\":\"\",\"sha256\":\"" SHA "\",\"bytes\":50000}]",
        "[{\"path\":\"a.alaw\",\"sha256\":\"" SHA "\"}]",
        "[{\"path\":\"a.alaw\",\"sha256\":\"" SHA "\",\"bytes\":8}]",
        "[{\"path\":\"a.alaw\",\"sha256\":\"" SHA "\",\"bytes\":50000.5}]",
        "[{\"path\":\"a.alaw\",\"sha256\":\"" SHA "\",\"bytes\":-1}]",
        "[{\"path\":\"a.alaw\",\"sha256\":\"" SHA "\",\"bytes\":\"50000\"}]",
        "[{\"path\":7,\"sha256\":\"" SHA "\",\"bytes\":50000}]",
        // a good small tier next to a broken large one
        "[" TIER("s.alaw", 50000) ",{\"path\":\"l.alaw\",\"bytes\":300000}]",
    };
    for (size_t i = 0; i < sizeof(bad_it) / sizeof(bad_it[0]); i++) {
        char json[2048];
        snprintf(json, sizeof(json),
                 "{\"assets\":{\"de\":{" DE_TIERS "},\"it\":{\"dialog\":%s}}}",
                 bad_it[i]);
        cJSON *root = cJSON_Parse(json);
        CHECK(root != NULL);
        const char *chain[] = { "it", "de" };
        CHECK(picks(root, chain, 2, 131072, "de", "audio/dialog-de-s.alaw"));
        cJSON_Delete(root);
    }
}

static void test_manifest_old_format(void)
{
    // A bundle from before the dialog: no "dialog" anywhere → no set, the
    // call takes the announcement path.
    cJSON *root = cJSON_Parse(
        "{\"version\":\"1.6.0\",\"assets\":{"
        "\"de\":{" ANN("de") "},\"en\":{" ANN("en") "}}}");
    const char *chain[] = { "fr", "en", "de" };
    dialog_manifest_set_t set;
    CHECK(!dialog_manifest_select(assets_of(root), chain, 3, UINT64_MAX, &set));
    CHECK(set.lang == NULL);
    CHECK(!dialog_manifest_select(NULL, chain, 3, UINT64_MAX, &set));
    cJSON_Delete(root);
}

// --- Clip store on the file system -------------------------------------------------

static void write_buf(const char *path, const uint8_t *buf, size_t n)
{
    FILE *f = fopen(path, "wb");
    CHECK(f != NULL);
    if (!f) return;
    CHECK(fwrite(buf, 1, n, f) == n);
    fclose(f);
}

static void write_bytes(const char *path, size_t n)
{
    static uint8_t junk[64];
    memset(junk, 0xD5, sizeof(junk));
    write_buf(path, junk, n < sizeof(junk) ? n : sizeof(junk));
}

static void write_text(const char *dir, const char *name, const char *text)
{
    char path[512];
    snprintf(path, sizeof(path), "%s/%s", dir, name);
    write_buf(path, (const uint8_t *)text, strlen(text));
}

static bool exists(const char *dir, const char *name)
{
    char path[512];
    snprintf(path, sizeof(path), "%s/%s", dir, name);
    FILE *f = fopen(path, "rb");
    if (f) fclose(f);
    return f != NULL;
}

static void test_store(const char *dir)
{
    static uint8_t f[4096];
    size_t n = build_set(f, sizeof(f), 5, DE_COUNTS, DE_LENS);
    dialog_set_t back;
    CHECK(!dialog_store_load(&back));    // nothing there yet

    char path[DIALOG_STORE_PATH_CAP];
    dialog_store_file_path(path, sizeof(path), "de");
    char want[512];
    snprintf(want, sizeof(want), "%s/dialog-de.alaw", dir);
    CHECK(strcmp(path, want) == 0);

    // The index alone: a set without its file is no set.
    CHECK(dialog_store_write_index("de") == PB_OK);
    CHECK(!dialog_store_load(&back) && back.lang[0] == '\0');
    // A file cut short is not one either, nor one without a header.
    write_buf(path, f, n - 1);
    CHECK(!dialog_store_load(&back) && !dialog_store_read_file("de", &back));
    write_bytes(path, 64);
    CHECK(!dialog_store_load(&back));
    write_buf(path, f, n);
    CHECK(dialog_store_read_file("de", &back) && strcmp(back.lang, "de") == 0);
    CHECK(dialog_store_load(&back) && strcmp(back.lang, "de") == 0);
    CHECK(back.count[DIALOG_PHASE_QUESTION] == 4 &&
          back.clip[DIALOG_PHASE_HELLO][0].off == DE_HLEN);
    CHECK(!dialog_store_read_file("fr", &back) && !dialog_store_read_file("../x", &back));

    // The index names the locale, exactly "<lang>\n".
    write_text(dir, "dialog.idx", "de");
    CHECK(!dialog_store_load(&back));
    write_text(dir, "dialog.idx", "de\nde\n");
    CHECK(!dialog_store_load(&back));
    write_text(dir, "dialog.idx", "../x\n");
    CHECK(!dialog_store_load(&back));
    // The text format of an earlier draft does not parse.
    write_text(dir, "dialog.idx", "de\n0:1\n0:1\n0:1\n0:1\n0:1\n");
    CHECK(!dialog_store_load(&back));
    CHECK(dialog_store_write_index("../x") == PB_FAIL);
    CHECK(dialog_store_write_index("de") == PB_OK && dialog_store_load(&back));

    // Another locale's leftovers go, the kept locale's file stays — and so
    // does the index, which remove_others never touches.
    char other[512];
    snprintf(other, sizeof(other), "%s/dialog-fr.alaw", dir);
    write_bytes(other, 10);
    snprintf(other, sizeof(other), "%s/dialog-de.alaw.tmp", dir);
    write_bytes(other, 10);
    snprintf(other, sizeof(other), "%s/announcement-de.alaw", dir);
    write_bytes(other, 10);
    CHECK(dialog_store_remove_others("de") == 2);
    CHECK(exists(dir, "dialog-de.alaw") && exists(dir, "dialog.idx"));
    CHECK(!exists(dir, "dialog-fr.alaw") && !exists(dir, "dialog-de.alaw.tmp"));

    dialog_store_drop_index();
    CHECK(!dialog_store_load(&back) && exists(dir, "dialog-de.alaw"));

    CHECK(dialog_store_write_index("de") == PB_OK);
    CHECK(dialog_store_remove_all() == 2);
    CHECK(!exists(dir, "dialog-de.alaw") && !exists(dir, "dialog.idx"));
    CHECK(exists(dir, "announcement-de.alaw"));   // not the store's business
    unlink(other);
}

// --- RTP receive parser --------------------------------------------------------------

static void test_rtp_rx(void)
{
    uint8_t pkt[64];
    memset(pkt, 0xD5, sizeof(pkt));
    pkt[0] = 0x80; pkt[1] = 8;       // V=2, PT=8
    const uint8_t *pl;
    size_t n;
    CHECK(rtp_rx_payload(pkt, 12 + 20, 8, &pl, &n));
    CHECK(pl == pkt + 12 && n == 20);

    // Marker bit set: still PCMA.
    pkt[1] = 0x80 | 8;
    CHECK(rtp_rx_payload(pkt, 32, 8, &pl, &n) && n == 20);

    // Another payload type (telephone-event) is not audio for the VAD.
    pkt[1] = 101;
    CHECK(!rtp_rx_payload(pkt, 32, 8, &pl, &n));
    pkt[1] = 8;

    // Two CSRCs.
    pkt[0] = 0x82;
    CHECK(rtp_rx_payload(pkt, 32, 8, &pl, &n) && pl == pkt + 20 && n == 12);

    // Header extension of one word after the fixed header.
    pkt[0] = 0x90;
    pkt[12] = 0xBE; pkt[13] = 0xDE; pkt[14] = 0; pkt[15] = 1;
    CHECK(rtp_rx_payload(pkt, 32, 8, &pl, &n) && pl == pkt + 20 && n == 12);

    // Padding: the last byte counts the padding bytes.
    pkt[0] = 0xA0;
    pkt[31] = 4;
    CHECK(rtp_rx_payload(pkt, 32, 8, &pl, &n) && pl == pkt + 12 && n == 16);
    pkt[31] = 40;                      // more padding than packet
    CHECK(!rtp_rx_payload(pkt, 32, 8, &pl, &n));

    // Not RTP version 2 (e.g. a STUN response, first byte 0x01).
    pkt[0] = 0x01;
    CHECK(!rtp_rx_payload(pkt, 32, 8, &pl, &n));
    // Too short, header only.
    pkt[0] = 0x80;
    CHECK(!rtp_rx_payload(pkt, 11, 8, &pl, &n));
    CHECK(!rtp_rx_payload(pkt, 12, 8, &pl, &n));
    // CSRC count beyond the packet.
    pkt[0] = 0x8F;
    CHECK(!rtp_rx_payload(pkt, 32, 8, &pl, &n));
}

int main(void)
{
    test_hello_then_silence_asks_who_is_there();
    test_speech_leads_to_am_aparat_then_questions();
    test_turn_waits_for_min_silence();
    test_vad_ignored_while_saying();
    test_no_immediate_repeat_with_three_clips();
    test_single_clip_repeats();
    test_packets_keep_dialog_alive();
    test_no_packets_end_after_rx_timeout();
    test_packet_resets_rx_timeout();
    test_incomplete_set_refused();
    test_phase_names();
    test_header_parse();
    test_lang_valid();
    test_manifest_tier_by_budget();
    test_manifest_chain_fallback();
    test_manifest_malformed_locale_skipped();
    test_manifest_old_format();
    test_rtp_rx();

    const char *tmp = getenv("TMPDIR");
    char dir[256];
    snprintf(dir, sizeof(dir), "%s/pb-dialog-XXXXXX", tmp && tmp[0] ? tmp : "/tmp");
    CHECK(mkdtemp(dir) != NULL);
    CHECK(setenv("PB_DIALOG_TEST_DIR", dir, 1) == 0);
    test_store(dir);
    rmdir(dir);
    printf("test_dialog: %d checks, %d failures\n", checks, failures);
    return failures ? 1 : 0;
}
