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

// --- Manifest → index → set -----------------------------------------------------

#define SHA "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

#define FILE_(path, bytes) \
    "{\"path\":\"" path "\",\"sha256\":\"" SHA "\",\"bytes\":" #bytes "}"
#define SPK(id, gender, sets) \
    "{\"id\":\"" id "\",\"name\":\"N-" id "\",\"gender\":\"" gender "\"," \
    "\"preview\":\"tts/x/" id "/hello-1.mp3\",\"sets\":[" sets "]}"

// German index: two speakers in three tiers each.
#define DE_INDEX "{\"speakers\":[" \
    SPK("leonie", "female", FILE_("dialog-de-leonie-s.bin", 130000) "," \
                            FILE_("dialog-de-leonie-m.bin", 200000) "," \
                            FILE_("dialog-de-leonie-l.bin", 300000)) "," \
    SPK("roger", "male",    FILE_("dialog-de-roger-s.bin", 125000) "," \
                            FILE_("dialog-de-roger-m.bin", 210000) "," \
                            FILE_("dialog-de-roger-l.bin", 310000)) "]}"
// English index: Roger has only a large set, so a small budget falls back.
#define EN_INDEX "{\"speakers\":[" \
    SPK("leonie", "female", FILE_("dialog-en-leonie-s.bin", 120000)) "," \
    SPK("roger", "male",    FILE_("dialog-en-roger-l.bin", 400000)) "]}"

#define ANN(l) "\"announcement\":{\"path\":\"announcement-" l ".alaw\"," \
               "\"sha256\":\"" SHA "\",\"bytes\":78762}"

// The channel manifest: fr without a dialog, en and de with an index.
static const char *const CHANNEL_ASSETS =
    "{\"fr\":{" ANN("fr") "},"
    "\"en\":{" ANN("en") ",\"dialog\":" FILE_("dialog-en-11111111.json", 900) "},"
    "\"de\":{" ANN("de") ",\"dialog\":" FILE_("dialog-de-22222222.json", 1200) "}}";

// The whole selection as the sync does it, with the index files served from
// memory by their path. Returns the pick; *lang, *speaker, *path copied out.
static dialog_pick_t select_full(const char *const *chain, int n, const char *speaker,
                                 uint64_t budget, char *lang, char *spk, char *path)
{
    lang[0] = spk[0] = path[0] = '\0';
    cJSON *assets = cJSON_Parse(CHANNEL_ASSETS);
    CHECK(assets != NULL);
    audio_asset_t ix;
    dialog_pick_t r = DIALOG_PICK_NONE;
    if (dialog_manifest_index(assets, chain, n, &ix)) {
        snprintf(lang, 12, "%s", ix.lang);
        const char *text = strcmp(ix.path, "dialog-de-22222222.json") == 0 ? DE_INDEX
                         : strcmp(ix.path, "dialog-en-11111111.json") == 0 ? EN_INDEX : NULL;
        CHECK(text != NULL);
        cJSON *index = cJSON_Parse(text);
        CHECK(index != NULL);
        dialog_manifest_set_t set;
        r = dialog_index_select(index, speaker, budget, &set);
        if (r != DIALOG_PICK_NONE) {
            snprintf(spk, DIALOG_SPEAKER_CAP, "%s", set.speaker);
            snprintf(path, 64, "%s", set.path);
            CHECK(set.sha256 && strcmp(set.sha256, SHA) == 0 && set.bytes > DIALOG_HEADER_MIN);
        }
        cJSON_Delete(index);
    }
    cJSON_Delete(assets);
    return r;
}

static void test_select_speaker_and_tier(void)
{
    char lang[12], spk[DIALOG_SPEAKER_CAP], path[64];
    const char *de[] = { "de" };
    // The chosen speaker present: its largest fitting tier.
    CHECK(select_full(de, 1, "roger", 229376, lang, spk, path) == DIALOG_PICK_CHOSEN);
    CHECK(strcmp(lang, "de") == 0 && strcmp(spk, "roger") == 0 &&
          strcmp(path, "dialog-de-roger-m.bin") == 0);
    CHECK(select_full(de, 1, "roger", 131072, lang, spk, path) == DIALOG_PICK_CHOSEN &&
          strcmp(path, "dialog-de-roger-s.bin") == 0);
    CHECK(select_full(de, 1, "roger", UINT64_MAX, lang, spk, path) == DIALOG_PICK_CHOSEN &&
          strcmp(path, "dialog-de-roger-l.bin") == 0);
    // A tier of exactly the budget fits, one byte less does not.
    CHECK(select_full(de, 1, "leonie", 200000, lang, spk, path) == DIALOG_PICK_CHOSEN &&
          strcmp(path, "dialog-de-leonie-m.bin") == 0);
    CHECK(select_full(de, 1, "leonie", 199999, lang, spk, path) == DIALOG_PICK_CHOSEN &&
          strcmp(path, "dialog-de-leonie-s.bin") == 0);
    // None chosen: the default (first) speaker.
    CHECK(select_full(de, 1, "", 229376, lang, spk, path) == DIALOG_PICK_CHOSEN &&
          strcmp(spk, "leonie") == 0 && strcmp(path, "dialog-de-leonie-m.bin") == 0);
    CHECK(select_full(de, 1, NULL, 229376, lang, spk, path) == DIALOG_PICK_CHOSEN &&
          strcmp(spk, "leonie") == 0);
    // The chosen speaker absent in this locale: the default's set.
    CHECK(select_full(de, 1, "hans", 229376, lang, spk, path) == DIALOG_PICK_ABSENT &&
          strcmp(spk, "leonie") == 0 && strcmp(path, "dialog-de-leonie-m.bin") == 0);
    // Budget below every tier of the chosen speaker: the default's fitting
    // tier (English Roger has only 400 KB) ...
    const char *en[] = { "en" };
    CHECK(select_full(en, 1, "roger", 229376, lang, spk, path) == DIALOG_PICK_NO_FIT &&
          strcmp(spk, "leonie") == 0 && strcmp(path, "dialog-en-leonie-s.bin") == 0);
    CHECK(select_full(en, 1, "roger", UINT64_MAX, lang, spk, path) == DIALOG_PICK_CHOSEN &&
          strcmp(path, "dialog-en-roger-l.bin") == 0);
    // ... and nothing when the default does not fit either (no other
    // speaker, no other locale: the locale is the first with an index).
    CHECK(select_full(en, 1, "roger", 119999, lang, spk, path) == DIALOG_PICK_NONE &&
          strcmp(lang, "en") == 0 && spk[0] == '\0' && path[0] == '\0');
    CHECK(select_full(de, 1, "roger", 124999, lang, spk, path) == DIALOG_PICK_NONE);
    CHECK(select_full(de, 1, "leonie", 125000, lang, spk, path) == DIALOG_PICK_NONE);
}

static void test_select_locale_chain(void)
{
    char lang[12], spk[DIALOG_SPEAKER_CAP], path[64];
    // fr has no dialog entry: en's index; it has Roger.
    const char *fr[] = { "fr", "en", "de" };
    CHECK(select_full(fr, 3, "roger", UINT64_MAX, lang, spk, path) == DIALOG_PICK_CHOSEN &&
          strcmp(lang, "en") == 0 && strcmp(path, "dialog-en-roger-l.bin") == 0);
    const char *fr_de[] = { "fr", "de" };
    CHECK(select_full(fr_de, 2, "roger", 131072, lang, spk, path) == DIALOG_PICK_CHOSEN &&
          strcmp(lang, "de") == 0 && strcmp(path, "dialog-de-roger-s.bin") == 0);
    // Nothing in the chain.
    const char *it[] = { "it", "fr" };
    CHECK(select_full(it, 2, "", UINT64_MAX, lang, spk, path) == DIALOG_PICK_NONE &&
          lang[0] == '\0');
    // Invalid codes and the own set's pseudo-locale are never looked up.
    cJSON *assets = cJSON_Parse("{\"own\":{\"dialog\":" FILE_("o.json", 10) "},"
                                "\"de\":{\"dialog\":" FILE_("d.json", 10) "}}");
    const char *bogus[] = { "../de", "own", "de" };
    audio_asset_t ix;
    CHECK(dialog_manifest_index(assets, bogus, 3, &ix) && strcmp(ix.lang, "de") == 0 &&
          strcmp(ix.path, "d.json") == 0 && ix.bytes == 10);
    CHECK(!dialog_manifest_index(assets, bogus, 2, &ix) && ix.lang == NULL && ix.path == NULL);
    CHECK(!dialog_manifest_index(NULL, bogus, 3, &ix));
    cJSON_Delete(assets);
}

// A malformed index entry in the manifest skips its locale.
static void test_manifest_index_malformed(void)
{
    static const char *const bad[] = {
        "{}", "\"x\"", "[]", "null",
        // the tier list of the unpublished first format
        "[" FILE_("dialog-it-s.bin", 50000) "]",
        "{\"path\":\"a.json\",\"sha256\":\"abc\",\"bytes\":500}",
        "{\"path\":\"../a.json\",\"sha256\":\"" SHA "\",\"bytes\":500}",
        "{\"sha256\":\"" SHA "\",\"bytes\":500}",
        "{\"path\":\"a.json\",\"sha256\":\"" SHA "\"}",
        "{\"path\":\"a.json\",\"sha256\":\"" SHA "\",\"bytes\":0}",
        "{\"path\":\"a.json\",\"sha256\":\"" SHA "\",\"bytes\":1.5}",
        "{\"path\":\"a.json\",\"sha256\":\"" SHA "\",\"bytes\":8193}",   // > DIALOG_INDEX_MAX_BYTES
    };
    for (size_t i = 0; i < sizeof(bad) / sizeof(bad[0]); i++) {
        char json[512];
        snprintf(json, sizeof(json), "{\"it\":{\"dialog\":%s},"
                 "\"de\":{\"dialog\":" FILE_("dialog-de-1.json", 8192) "}}", bad[i]);
        cJSON *assets = cJSON_Parse(json);
        CHECK(assets != NULL);
        const char *chain[] = { "it", "de" };
        audio_asset_t ix;
        CHECK(dialog_manifest_index(assets, chain, 2, &ix) && strcmp(ix.lang, "de") == 0);
        cJSON_Delete(assets);
    }
}

// Malformed speakers are skipped, the others stay usable; the first
// well-formed one is the default.
static void test_index_malformed_speakers(void)
{
    const char *json = "{\"speakers\":["
        "{\"id\":\"Bad\",\"sets\":[" FILE_("a.bin", 1000) "]},"           // id not lower case
        "{\"id\":\"nosets\"},"
        "{\"id\":\"empty\",\"sets\":[]},"
        "{\"id\":\"brokentier\",\"sets\":[" FILE_("a.bin", 1000) ",{\"path\":\"b.bin\"}]},"
        "{\"id\":\"smalltier\",\"sets\":[" FILE_("a.bin", 8) "]},"        // not > header min
        "{\"id\":\"escapes\",\"sets\":[" FILE_("../a.bin", 1000) "]},"
        "7,"
        "{\"id\":\"anna\",\"gender\":\"female\",\"sets\":[" FILE_("anna.bin", 1000) "],\"x\":1},"
        "{\"id\":\"anna\",\"sets\":[" FILE_("anna2.bin", 900) "]},"      // duplicate id
        "{\"id\":\"ben\",\"name\":\"Ben B.\",\"gender\":\"male\",\"preview\":\"tts/de/ben/hello-1.mp3\","
        " \"sets\":[" FILE_("ben.bin", 2000) "]},"
        "{\"id\":\"carl\",\"preview\":\"../x.mp3\",\"sets\":[" FILE_("carl.bin", 3000) "]}"
        "]}";
    cJSON *index = cJSON_Parse(json);
    CHECK(index != NULL);
    dialog_manifest_set_t set;
    CHECK(dialog_index_select(index, "", UINT64_MAX, &set) == DIALOG_PICK_CHOSEN &&
          strcmp(set.speaker, "anna") == 0 && strcmp(set.path, "anna.bin") == 0);
    CHECK(dialog_index_select(index, "ben", UINT64_MAX, &set) == DIALOG_PICK_CHOSEN &&
          strcmp(set.path, "ben.bin") == 0 && set.lang == NULL);
    CHECK(dialog_index_select(index, "brokentier", UINT64_MAX, &set) == DIALOG_PICK_ABSENT &&
          strcmp(set.speaker, "anna") == 0);
    CHECK(dialog_index_select(index, "Bad", UINT64_MAX, &set) == DIALOG_PICK_ABSENT);
    CHECK(dialog_index_select(index, "ben", 1999, &set) == DIALOG_PICK_NO_FIT &&
          strcmp(set.speaker, "anna") == 0);
    CHECK(dialog_index_select(index, "ben", 999, &set) == DIALOG_PICK_NONE &&
          set.path == NULL && set.speaker == NULL);

    // The listing: anna, ben, carl — names, genders, previews checked.
    dialog_speaker_t sp;
    CHECK(dialog_index_speaker(index, 0, &sp) && strcmp(sp.id, "anna") == 0 &&
          strcmp(sp.name, "anna") == 0 && strcmp(sp.gender, "female") == 0 &&
          strcmp(sp.preview, "") == 0);
    CHECK(dialog_index_speaker(index, 1, &sp) && strcmp(sp.id, "ben") == 0 &&
          strcmp(sp.name, "Ben B.") == 0 && strcmp(sp.gender, "male") == 0 &&
          strcmp(sp.preview, "tts/de/ben/hello-1.mp3") == 0);
    CHECK(dialog_index_speaker(index, 2, &sp) && strcmp(sp.id, "carl") == 0 &&
          strcmp(sp.preview, "") == 0 && strcmp(sp.gender, "") == 0);
    CHECK(!dialog_index_speaker(index, 3, &sp) && sp.id == NULL);
    CHECK(!dialog_index_speaker(index, -1, &sp));
    cJSON_Delete(index);

    // No usable speaker at all.
    const char *none[] = { "{}", "{\"speakers\":{}}", "{\"speakers\":[]}", "[]",
                           "{\"speakers\":[{\"id\":\"a\"}]}" };
    for (size_t i = 0; i < sizeof(none) / sizeof(none[0]); i++) {
        cJSON *ix = cJSON_Parse(none[i]);
        CHECK(dialog_index_select(ix, "", UINT64_MAX, &set) == DIALOG_PICK_NONE);
        CHECK(!dialog_index_speaker(ix, 0, &sp));
        bool re;
        CHECK(dialog_index_resolve_speaker(ix, "", 0, &re) == NULL && !re);
        cJSON_Delete(ix);
    }
    CHECK(dialog_index_select(NULL, "", UINT64_MAX, &set) == DIALOG_PICK_NONE);
}

// Forward compatible: unknown fields and a speaker of a later shape next
// to a usable one.
static void test_index_forward_compatible(void)
{
    cJSON *index = cJSON_Parse("{\"format\":2,\"speakers\":["
        "{\"id\":\"leonie\",\"styles\":[1],\"sets\":["
        "{\"path\":\"s.bin\",\"sha256\":\"" SHA "\",\"bytes\":120000,\"codec\":\"pcma\"},"
        "{\"path\":\"xl.bin\",\"sha256\":\"" SHA "\",\"bytes\":900000}]}],\"extra\":{}}");
    dialog_manifest_set_t set;
    CHECK(dialog_index_select(index, "", 131072, &set) == DIALOG_PICK_CHOSEN &&
          set.bytes == 120000 && strcmp(set.path, "s.bin") == 0);
    cJSON_Delete(index);
}

// The random default: uniform over all speakers; a speaker the locale
// lacks (or none yet) is drawn again, one it has stays.
static void test_speaker_resolve(void)
{
    cJSON *index = cJSON_Parse("{\"speakers\":["
        SPK("leonie", "female", FILE_("l.bin", 1000)) ","
        SPK("roger", "male", FILE_("r.bin", 1000)) ","
        SPK("anna", "female", FILE_("a.bin", 1000)) "]}");
    // r maps onto the list by r % n: first, middle, last, and wrap-around.
    CHECK(strcmp(dialog_index_random_speaker(index, 0), "leonie") == 0);
    CHECK(strcmp(dialog_index_random_speaker(index, 1), "roger") == 0);
    CHECK(strcmp(dialog_index_random_speaker(index, 2), "anna") == 0);
    CHECK(strcmp(dialog_index_random_speaker(index, 3), "leonie") == 0);
    CHECK(strcmp(dialog_index_random_speaker(index, UINT32_MAX), "leonie") == 0);  // 2^32-1 = 0 mod 3
    CHECK(strcmp(dialog_index_random_speaker(index, UINT32_MAX - 1), "anna") == 0);
    // Uniform: each speaker equally often over full cycles.
    int seen[3] = { 0 };
    for (uint32_t r = 0; r < 300; r++) {
        const char *id = dialog_index_random_speaker(index, r);
        seen[strcmp(id, "leonie") == 0 ? 0 : strcmp(id, "roger") == 0 ? 1 : 2]++;
    }
    CHECK(seen[0] == 100 && seen[1] == 100 && seen[2] == 100);

    bool re;
    // Empty: a pick within the list, to be persisted (first and last index).
    const char *p = dialog_index_resolve_speaker(index, "", 0, &re);
    CHECK(re && p && strcmp(p, "leonie") == 0);
    p = dialog_index_resolve_speaker(index, NULL, 2, &re);
    CHECK(re && p && strcmp(p, "anna") == 0);
    // Present: unchanged, nothing to persist, whatever r.
    p = dialog_index_resolve_speaker(index, "roger", 0, &re);
    CHECK(!re && strcmp(p, "roger") == 0);
    p = dialog_index_resolve_speaker(index, "anna", 1, &re);
    CHECK(!re && strcmp(p, "anna") == 0);
    // Absent from this locale (a German-only voice, a speaker removed from
    // the CDN): drawn again.
    p = dialog_index_resolve_speaker(index, "hans", 1, &re);
    CHECK(re && strcmp(p, "roger") == 0);
    dialog_manifest_set_t set;
    CHECK(dialog_index_select(index, p, UINT64_MAX, &set) == DIALOG_PICK_CHOSEN &&
          strcmp(set.speaker, "roger") == 0);
    cJSON_Delete(index);
}

static void test_speaker_valid(void)
{
    const char *good[] = { "roger", "a", "a-1", "leonie", "abcdefghijklmnop" };
    for (size_t i = 0; i < sizeof(good) / sizeof(good[0]); i++) CHECK(dialog_speaker_valid(good[i]));
    const char *bad[] = { NULL, "", "Roger", "1a", "abcdefghijklmnopq", "a_b", "../x",
                          "-a", "a b", "a.b", "a/b" };
    for (size_t i = 0; i < sizeof(bad) / sizeof(bad[0]); i++) CHECK(!dialog_speaker_valid(bad[i]));
}

// A set the web UI's builder made (main/web/index.html, dlgBuild(); checked
// against this vector by dialog_set_js_check.js): clip counts 1,2,1,3,1,
// the i-th clip of the file 3 + 2*i bytes of phase*16 + clip.
static const char JS_SET_VECTOR[] =
    "5042445301054d0001020103014d00000003000000500000000500000055000000070000005c"
    "00000009000000650000000b000000700000000d0000007d0000000f0000008c000000110000"
    "0000000010101010101111111111111120202020202020202030303030303030303030303131"
    "3131313131313131313131323232323232323232323232323232404040404040404040404040"
    "4040404040";

static void test_js_built_set(void)
{
    uint8_t f[256];
    size_t n = strlen(JS_SET_VECTOR) / 2;
    CHECK(n <= sizeof(f));
    for (size_t i = 0; i < n; i++) {
        unsigned v;
        CHECK(sscanf(JS_SET_VECTOR + 2 * i, "%2x", &v) == 1);
        f[i] = (uint8_t)v;
    }
    dialog_set_t set;
    memset(&set, 0, sizeof(set));
    CHECK(dialog_header_parse(f, n, n, &set) == DIALOG_HEADER_OK);
    static const uint8_t counts[DIALOG_PHASE_COUNT] = { 1, 2, 1, 3, 1 };
    // Read back: every clip where the builder put it, with its bytes.
    uint8_t g[256];
    memset(g, 0, sizeof(g));
    uint32_t off = 8 + 5 + 8 * 8;
    int i = 0;
    for (int p = 0; p < DIALOG_PHASE_COUNT; p++) {
        CHECK(set.count[p] == counts[p]);
        for (int k = 0; k < counts[p]; k++, i++) {
            uint32_t len = 3 + 2 * (uint32_t)i;
            CHECK(set.clip[p][k].off == off && set.clip[p][k].len == len);
            for (uint32_t b = 0; b < len; b++) CHECK(f[off + b] == (uint8_t)(p * 16 + k));
            off += len;
        }
    }
    CHECK(off == n);
    // Write it again from the parse (as audio-assets.sh / build_set do) →
    // the same bytes.
    uint32_t lens[8];
    for (int k = 0; k < 8; k++) lens[k] = 3 + 2 * (uint32_t)k;
    size_t m = build_set(g, sizeof(g), 5, counts, lens);
    CHECK(m == n && memcmp(g, f, 8 + 5 + 8 * 8) == 0);
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
    CHECK(dialog_store_write_index("de", "roger") == PB_OK);
    CHECK(!dialog_store_load(&back) && back.lang[0] == '\0');
    // A file cut short is not one either, nor one without a header.
    write_buf(path, f, n - 1);
    CHECK(!dialog_store_load(&back) && !dialog_store_read_file("de", &back));
    write_bytes(path, 64);
    CHECK(!dialog_store_load(&back));
    write_buf(path, f, n);
    CHECK(dialog_store_read_file("de", &back) && strcmp(back.lang, "de") == 0);
    CHECK(dialog_store_load(&back) && strcmp(back.lang, "de") == 0 &&
          strcmp(back.speaker, "roger") == 0);
    CHECK(back.count[DIALOG_PHASE_QUESTION] == 4 &&
          back.clip[DIALOG_PHASE_HELLO][0].off == DE_HLEN);
    CHECK(!dialog_store_read_file("fr", &back) && !dialog_store_read_file("../x", &back));

    // The index names the locale and the speaker: "<lang>\n<speaker>\n",
    // or the speakerless "<lang>\n" (own set, earlier firmware).
    write_text(dir, "dialog.idx", "de\n");
    CHECK(dialog_store_load(&back) && back.speaker[0] == '\0');
    write_text(dir, "dialog.idx", "de\n\n");
    CHECK(dialog_store_load(&back) && back.speaker[0] == '\0');
    write_text(dir, "dialog.idx", "de\nleonie\n");
    CHECK(dialog_store_load(&back) && strcmp(back.speaker, "leonie") == 0);
    write_text(dir, "dialog.idx", "de");
    CHECK(!dialog_store_load(&back));
    write_text(dir, "dialog.idx", "de\nleonie");
    CHECK(!dialog_store_load(&back));
    write_text(dir, "dialog.idx", "de\nLeonie\n");
    CHECK(!dialog_store_load(&back));
    write_text(dir, "dialog.idx", "de\nleonie\nx\n");
    CHECK(!dialog_store_load(&back));
    write_text(dir, "dialog.idx", "../x\n");
    CHECK(!dialog_store_load(&back));
    // The text format of an earlier draft does not parse.
    write_text(dir, "dialog.idx", "de\n0:1\n0:1\n0:1\n0:1\n0:1\n");
    CHECK(!dialog_store_load(&back));
    CHECK(dialog_store_write_index("../x", NULL) == PB_FAIL);
    CHECK(dialog_store_write_index("de", "Bad") == PB_FAIL);
    CHECK(dialog_store_write_index("de", NULL) == PB_OK && dialog_store_load(&back) &&
          back.speaker[0] == '\0');

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

    CHECK(dialog_store_write_index("de", "leonie") == PB_OK);
    CHECK(dialog_store_remove_all() == 2);
    CHECK(!exists(dir, "dialog-de.alaw") && !exists(dir, "dialog.idx"));
    CHECK(exists(dir, "announcement-de.alaw"));   // not the store's business
    unlink(other);
}

// The kept dialog index and the user's own set.
static void test_store_own(const char *dir)
{
    static uint8_t f[4096];
    size_t n = build_set(f, sizeof(f), 5, DE_COUNTS, DE_LENS);
    dialog_set_t back;

    // The speakers file: read back verbatim, refused when absent or too big.
    CHECK(dialog_store_read_speakers() == NULL);
    char spk[DIALOG_STORE_PATH_CAP];
    dialog_store_speakers_path(spk, sizeof(spk));
    char want[512];
    snprintf(want, sizeof(want), "%s/dialog-speakers.json", dir);
    CHECK(strcmp(spk, want) == 0);
    write_text(dir, "dialog-speakers.json", "{\"speakers\":[]}");
    char *text = dialog_store_read_speakers();
    CHECK(text && strcmp(text, "{\"speakers\":[]}") == 0);
    free(text);
    static char big[DIALOG_INDEX_MAX_BYTES + 2];
    memset(big, ' ', sizeof(big) - 1);
    write_text(dir, "dialog-speakers.json", big);
    CHECK(dialog_store_read_speakers() == NULL);
    write_text(dir, "dialog-speakers.json", "{}");

    // A CDN set in place, then the own set written as the upload does:
    // file, then index line "own".
    char path[DIALOG_STORE_PATH_CAP];
    dialog_store_file_path(path, sizeof(path), "de");
    write_buf(path, f, n);
    CHECK(dialog_store_write_index("de", "roger") == PB_OK);
    CHECK(!dialog_store_has_own() && !dialog_store_claim_own());
    CHECK(dialog_store_load(&back) && strcmp(back.lang, "de") == 0);   // untouched

    char own[DIALOG_STORE_PATH_CAP];
    dialog_store_file_path(own, sizeof(own), DIALOG_OWN_LANG);
    write_buf(own, f, n);
    CHECK(dialog_store_has_own());
    // The sync's pass with an own set present (here: interrupted before its
    // index line): the index is repaired to "own", the CDN set goes, the own
    // file keeps its bytes.
    CHECK(dialog_store_claim_own());
    CHECK(dialog_store_load(&back) && strcmp(back.lang, DIALOG_OWN_LANG) == 0 &&
          back.speaker[0] == '\0' && back.count[DIALOG_PHASE_QUESTION] == 4);
    CHECK(!exists(dir, "dialog-de.alaw") && exists(dir, "dialog-own.alaw"));
    // A second pass changes nothing.
    CHECK(dialog_store_claim_own());
    CHECK(dialog_store_load(&back) && strcmp(back.lang, DIALOG_OWN_LANG) == 0);
    uint8_t g[4096];
    FILE *fp = fopen(own, "rb");
    CHECK(fp && fread(g, 1, sizeof(g), fp) == n && memcmp(f, g, n) == 0);
    if (fp) fclose(fp);
    // A broken own file is no own set.
    write_bytes(own, 40);
    CHECK(!dialog_store_has_own() && !dialog_store_claim_own());
    write_buf(own, f, n);

    // An upload's temp file goes with the others.
    write_text(dir, "dialog-own.alaw.part", "x");
    CHECK(dialog_store_remove_others(DIALOG_OWN_LANG) == 1 && exists(dir, "dialog-own.alaw"));

    // Leaving dialog mode: everything goes, the own set and the index too.
    CHECK(dialog_store_remove_all() == 3);
    CHECK(!exists(dir, "dialog-own.alaw") && !exists(dir, "dialog.idx") &&
          !exists(dir, "dialog-speakers.json"));
    CHECK(!dialog_store_has_own() && !dialog_store_load(&back));

    // The upload flag (web.c / i18n_sync.c).
    CHECK(!dialog_store_upload_active());
    dialog_store_set_upload_active(true);
    CHECK(dialog_store_upload_active());
    dialog_store_set_upload_active(false);
}

// `test_dialog FILE...`: checks that each file is a valid set (the staged
// .bin files of audio-assets.sh --no-upload).
static int check_files(int argc, char **argv)
{
    static uint8_t hdr[DIALOG_HEADER_MAX];
    int bad = 0;
    for (int i = 1; i < argc; i++) {
        FILE *f = fopen(argv[i], "rb");
        if (!f) { fprintf(stderr, "%s: cannot open\n", argv[i]); bad++; continue; }
        size_t n = fread(hdr, 1, sizeof(hdr), f);
        fseek(f, 0, SEEK_END);
        long size = ftell(f);
        fclose(f);
        dialog_set_t set;
        if (size <= 0 || dialog_header_parse(hdr, n, (uint64_t)size, &set) != DIALOG_HEADER_OK) {
            fprintf(stderr, "%s: not a valid dialog set\n", argv[i]);
            bad++;
            continue;
        }
        printf("%s: ok, %ld B, clips %u/%u/%u/%u/%u\n", argv[i], size, set.count[0],
               set.count[1], set.count[2], set.count[3], set.count[4]);
    }
    return bad ? 1 : 0;
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

int main(int argc, char **argv)
{
    if (argc > 1) return check_files(argc, argv);
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
    test_select_speaker_and_tier();
    test_select_locale_chain();
    test_manifest_index_malformed();
    test_index_malformed_speakers();
    test_index_forward_compatible();
    test_speaker_resolve();
    test_speaker_valid();
    test_js_built_set();
    test_rtp_rx();

    const char *tmp = getenv("TMPDIR");
    char dir[256];
    snprintf(dir, sizeof(dir), "%s/pb-dialog-XXXXXX", tmp && tmp[0] ? tmp : "/tmp");
    CHECK(mkdtemp(dir) != NULL);
    CHECK(setenv("PB_DIALOG_TEST_DIR", dir, 1) == 0);
    test_store(dir);
    test_store_own(dir);
    rmdir(dir);
    printf("test_dialog: %d checks, %d failures\n", checks, failures);
    return failures ? 1 : 0;
}
