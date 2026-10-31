// Host test for the interactive answer-bot dialog (issue #429): the state
// machine (dialog.c), the on-device clip-set index (dialog.c), the selection
// of a clip set from the i18n manifest (dialog_manifest.c), the clip store
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
#include "strbuf.h"

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

// --- Clip-set index ------------------------------------------------------------

// The German set as published: hello, who-is-there, am-aparat, three
// questions, cant-hear-you, back to back in one 63903-byte file.
static dialog_set_t german_set(void)
{
    dialog_set_t s;
    memset(&s, 0, sizeof(s));
    snprintf(s.lang, sizeof(s.lang), "de");
    static const uint32_t len[] = { 7431, 7802, 7059, 7802, 11146, 12260, 10403 };
    static const int phase_of[] = { 0, 1, 2, 3, 3, 3, 4 };
    uint32_t off = 0;
    for (int i = 0; i < 7; i++) {
        int p = phase_of[i];
        s.clip[p][s.count[p]].off = off;
        s.clip[p][s.count[p]].len = len[i];
        s.count[p]++;
        off += len[i];
    }
    return s;
}

static void test_set_index_round_trip(void)
{
    dialog_set_t s = german_set();
    char text[DIALOG_SET_TEXT_CAP];
    int n = dialog_set_format(&s, text, sizeof(text));
    CHECK(n > 0 && strcmp(text, "de\n0:7431\n7431:7802\n15233:7059\n"
                                "22292:7802 30094:11146 41240:12260\n"
                                "53500:10403\n") == 0);
    dialog_set_t back;
    CHECK(dialog_set_parse(text, &back));
    CHECK(dialog_set_equal(&back, &s));
    CHECK(dialog_set_within(&back, 63903));
    CHECK(!dialog_set_within(&back, 63902));   // last clip one byte short

    // The largest table (8 clips per phase, 10-digit numbers, longest
    // locale) fits the documented buffer.
    dialog_set_t big;
    memset(&big, 0, sizeof(big));
    snprintf(big.lang, sizeof(big.lang), "zh-hans-xyz");
    for (int p = 0; p < DIALOG_PHASE_COUNT; p++) {
        big.count[p] = DIALOG_MAX_CLIPS;
        for (int c = 0; c < DIALOG_MAX_CLIPS; c++) {
            big.clip[p][c].off = 4000000000u;
            big.clip[p][c].len = 1000000000u;
        }
    }
    CHECK(dialog_set_format(&big, text, sizeof(text)) > 0);
    CHECK(dialog_set_parse(text, &back) == false);   // off+len > 32 bit
    for (int p = 0; p < DIALOG_PHASE_COUNT; p++)
        for (int c = 0; c < DIALOG_MAX_CLIPS; c++) big.clip[p][c].off = 1000000000u;
    CHECK(dialog_set_format(&big, text, sizeof(text)) > 0);
    CHECK(dialog_set_parse(text, &back) && dialog_set_equal(&back, &big));

    char tiny[8];
    CHECK(dialog_set_format(&s, tiny, sizeof(tiny)) == -1);

    // Different tables are not equal.
    dialog_set_t other = s;
    other.clip[DIALOG_PHASE_QUESTION][2].len--;
    CHECK(!dialog_set_equal(&other, &s));
    other = s;
    snprintf(other.lang, sizeof(other.lang), "en");
    CHECK(!dialog_set_equal(&other, &s));

    const char *bad[] = {
        "", "de", "de\n", "de\n0:1\n0:1\n0:1\n0:1\n",            // phase missing
        "de\n0:1\n0:1\n0:1\n0:1\n0:1\n0:1\n",                     // extra line
        "de\n0:1\n0:1\n0:0\n0:1\n0:1\n",                          // empty clip
        "de\n0:1\n0:1\n0:1\n0:1 0:1 0:1 0:1 0:1 0:1 0:1 0:1 0:1\n0:1\n", // 9 clips
        "De\n0:1\n0:1\n0:1\n0:1\n0:1\n", "../x\n0:1\n0:1\n0:1\n0:1\n0:1\n",
        "de\n0:1\n0:1\n0:1\n0:1\n0:1",                            // no final newline
        "de\n0:1\n0:1\n0:1\n0:1\n0:1x\n", "de\n-1:1\n0:1\n0:1\n0:1\n0:1\n",
        "de\n0:1  0:1\n0:1\n0:1\n0:1\n0:1\n", "de\n0;1\n0:1\n0:1\n0:1\n0:1\n",
        "de\n4294967296:1\n0:1\n0:1\n0:1\n0:1\n",
    };
    for (size_t i = 0; i < sizeof(bad) / sizeof(bad[0]); i++) {
        CHECK(!dialog_set_parse(bad[i], &back));
        CHECK(back.lang[0] == '\0' && back.count[0] == 0);
    }
    CHECK(!dialog_set_parse(NULL, &back));
}

// --- Manifest selection ----------------------------------------------------------

#define SHA "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

// A dialog asset: one file plus the clip table.
#define DIALOG(path, bytes, phases) \
    "\"dialog\":{\"path\":\"" path "\",\"sha256\":\"" SHA "\"," \
    "\"bytes\":" #bytes ",\"phases\":{" phases "}}"

// The complete German set with three questions, as i18n-assets.sh publishes it.
#define DE_PHASES \
    "\"hello\":[[0,7431]],\"who-is-there\":[[7431,7802]]," \
    "\"am-aparat\":[[15233,7059]]," \
    "\"question\":[[22292,7802],[30094,11146],[41240,12260]]," \
    "\"cant-hear-you\":[[53500,10403]]"
#define DE_DIALOG DIALOG("audio/dialog-de.alaw", 63903, DE_PHASES)

// Five one-clip phases in a 50-byte file.
#define SMALL_PHASES \
    "\"hello\":[[0,10]],\"who-is-there\":[[10,10]],\"am-aparat\":[[20,10]]," \
    "\"question\":[[30,10]],\"cant-hear-you\":[[40,10]]"

// English with a phase missing (no cant-hear-you).
#define EN_INCOMPLETE DIALOG("audio/dialog-en.alaw", 40, \
    "\"hello\":[[0,10]],\"who-is-there\":[[10,10]],\"am-aparat\":[[20,10]]," \
    "\"question\":[[30,10]]")

#define ANN(l) "\"announcement\":{\"path\":\"audio/announcement-" l ".alaw\"," \
               "\"sha256\":\"" SHA "\",\"bytes\":78762}"

static cJSON *assets_of(cJSON *root)
{
    return cJSON_GetObjectItem(root, "assets");
}

static void test_manifest_fallback_to_german(void)
{
    cJSON *root = cJSON_Parse(
        "{\"version\":\"1.7.0\",\"assets\":{"
        "\"fr\":{" ANN("fr") "},"
        "\"en\":{" ANN("en") "," EN_INCOMPLETE "},"
        "\"de\":{" ANN("de") "," DE_DIALOG "}}}");
    CHECK(root != NULL);
    const char *chain[] = { "fr", "en", "de" };
    dialog_manifest_set_t set;
    CHECK(dialog_manifest_select(assets_of(root), chain, 3, &set));
    CHECK(set.lang && strcmp(set.lang, "de") == 0);
    CHECK(strcmp(set.set.lang, "de") == 0);
    // The whole German set: its file, never mixed with the English phases.
    CHECK(set.path && strcmp(set.path, "audio/dialog-de.alaw") == 0);
    CHECK(set.sha256 && strcmp(set.sha256, SHA) == 0);
    CHECK(set.bytes == 63903);
    dialog_set_t want = german_set();
    CHECK(dialog_set_equal(&set.set, &want));

    // The incomplete English set alone yields nothing.
    const char *en_only[] = { "en" };
    CHECK(!dialog_manifest_select(assets_of(root), en_only, 1, &set));
    CHECK(set.lang == NULL && set.path == NULL && set.set.count[0] == 0);
    cJSON_Delete(root);
}

static void test_manifest_preferred_locale_wins(void)
{
    cJSON *root = cJSON_Parse(
        "{\"assets\":{\"de\":{" DE_DIALOG "},"
        "\"it\":{" DIALOG("audio/dialog-it.alaw", 50, SMALL_PHASES) "}}}");
    const char *chain[] = { "it", "en", "de" };
    dialog_manifest_set_t set;
    CHECK(dialog_manifest_select(assets_of(root), chain, 3, &set));
    CHECK(set.lang && strcmp(set.lang, "it") == 0);
    CHECK(set.set.count[DIALOG_PHASE_QUESTION] == 1);
    CHECK(set.set.clip[DIALOG_PHASE_CANT_HEAR_YOU][0].off == 40);
    cJSON_Delete(root);
}

// Each malformed variant of an otherwise complete set must skip its locale
// as a whole, German following.
static void test_manifest_malformed_locale_skipped(void)
{
    static const char *const bad_it[] = {
        // short sha
        "{\"path\":\"a.alaw\",\"sha256\":\"abc\",\"bytes\":50,\"phases\":{" SMALL_PHASES "}}",
        // no path / empty path / no bytes / zero bytes / fractional bytes
        "{\"sha256\":\"" SHA "\",\"bytes\":50,\"phases\":{" SMALL_PHASES "}}",
        "{\"path\":\"\",\"sha256\":\"" SHA "\",\"bytes\":50,\"phases\":{" SMALL_PHASES "}}",
        "{\"path\":\"a.alaw\",\"sha256\":\"" SHA "\",\"phases\":{" SMALL_PHASES "}}",
        "{\"path\":\"a.alaw\",\"sha256\":\"" SHA "\",\"bytes\":0,\"phases\":{" SMALL_PHASES "}}",
        "{\"path\":\"a.alaw\",\"sha256\":\"" SHA "\",\"bytes\":50.5,\"phases\":{" SMALL_PHASES "}}",
        // no phases / phases not an object
        "{\"path\":\"a.alaw\",\"sha256\":\"" SHA "\",\"bytes\":50}",
        "{\"path\":\"a.alaw\",\"sha256\":\"" SHA "\",\"bytes\":50,\"phases\":[]}",
        // a clip past the end of the file
        "{\"path\":\"a.alaw\",\"sha256\":\"" SHA "\",\"bytes\":49,\"phases\":{" SMALL_PHASES "}}",
        // an empty clip, a negative offset, a clip that is not a pair
        "{\"path\":\"a.alaw\",\"sha256\":\"" SHA "\",\"bytes\":50,\"phases\":{"
        "\"hello\":[[0,0]],\"who-is-there\":[[10,10]],\"am-aparat\":[[20,10]],"
        "\"question\":[[30,10]],\"cant-hear-you\":[[40,10]]}}",
        "{\"path\":\"a.alaw\",\"sha256\":\"" SHA "\",\"bytes\":50,\"phases\":{"
        "\"hello\":[[-1,10]],\"who-is-there\":[[10,10]],\"am-aparat\":[[20,10]],"
        "\"question\":[[30,10]],\"cant-hear-you\":[[40,10]]}}",
        "{\"path\":\"a.alaw\",\"sha256\":\"" SHA "\",\"bytes\":50,\"phases\":{"
        "\"hello\":[[0]],\"who-is-there\":[[10,10]],\"am-aparat\":[[20,10]],"
        "\"question\":[[30,10]],\"cant-hear-you\":[[40,10]]}}",
        "{\"path\":\"a.alaw\",\"sha256\":\"" SHA "\",\"bytes\":50,\"phases\":{"
        "\"hello\":[{\"off\":0,\"len\":10}],\"who-is-there\":[[10,10]],\"am-aparat\":[[20,10]],"
        "\"question\":[[30,10]],\"cant-hear-you\":[[40,10]]}}",
        // a phase with no clip
        "{\"path\":\"a.alaw\",\"sha256\":\"" SHA "\",\"bytes\":50,\"phases\":{"
        "\"hello\":[],\"who-is-there\":[[10,10]],\"am-aparat\":[[20,10]],"
        "\"question\":[[30,10]],\"cant-hear-you\":[[40,10]]}}",
    };
    for (size_t i = 0; i < sizeof(bad_it) / sizeof(bad_it[0]); i++) {
        char json[2048];
        snprintf(json, sizeof(json),
                 "{\"assets\":{\"de\":{" DE_DIALOG "},\"it\":{\"dialog\":%s}}}",
                 bad_it[i]);
        cJSON *root = cJSON_Parse(json);
        CHECK(root != NULL);
        const char *chain[] = { "it", "de" };
        dialog_manifest_set_t set;
        CHECK(dialog_manifest_select(assets_of(root), chain, 2, &set));
        CHECK(set.lang && strcmp(set.lang, "de") == 0);
        CHECK(set.bytes == 63903);
        cJSON_Delete(root);
    }
}

static void test_manifest_too_many_clips(void)
{
    char q[1024];
    strbuf_t sb = sb_init(q, sizeof(q));
    for (int i = 0; i <= DIALOG_MAX_CLIPS; i++)
        sb_appendf(&sb, "%s[%d,1]", i ? "," : "", i);
    CHECK(!sb.truncated);
    char json[2048];
    snprintf(json, sizeof(json),
             "{\"assets\":{\"de\":{\"dialog\":{\"path\":\"a.alaw\",\"sha256\":\"" SHA "\","
             "\"bytes\":50,\"phases\":{"
             "\"hello\":[[0,1]],\"who-is-there\":[[1,1]],\"am-aparat\":[[2,1]],"
             "\"question\":[%s],\"cant-hear-you\":[[3,1]]}}}}}", q);
    cJSON *root = cJSON_Parse(json);
    CHECK(root != NULL);
    const char *chain[] = { "de" };
    dialog_manifest_set_t set;
    CHECK(!dialog_manifest_select(assets_of(root), chain, 1, &set));
    cJSON_Delete(root);
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
    CHECK(!dialog_manifest_select(assets_of(root), chain, 3, &set));
    CHECK(set.lang == NULL);
    CHECK(!dialog_manifest_select(NULL, chain, 3, &set));
    cJSON_Delete(root);

    // The per-clip form of an early draft (phase → array of files) is not a
    // set either.
    root = cJSON_Parse(
        "{\"assets\":{\"de\":{\"dialog\":{\"hello\":[{\"path\":\"h.alaw\","
        "\"sha256\":\"" SHA "\",\"bytes\":10}]}}}}");
    CHECK(!dialog_manifest_select(assets_of(root), chain, 3, &set));
    cJSON_Delete(root);
}

// --- Clip store on the file system -------------------------------------------------

static void write_bytes(const char *path, size_t n)
{
    FILE *f = fopen(path, "wb");
    CHECK(f != NULL);
    if (!f) return;
    for (size_t i = 0; i < n; i++) fputc(0xD5, f);
    fclose(f);
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
    dialog_set_t s = german_set();
    dialog_set_t back;
    CHECK(!dialog_store_load(&back, false));    // nothing there yet

    char path[DIALOG_STORE_PATH_CAP];
    dialog_store_file_path(path, sizeof(path), "de");
    char want[512];
    snprintf(want, sizeof(want), "%s/dialog-de.alaw", dir);
    CHECK(strcmp(path, want) == 0);

    // The index alone: a set without its file is no set on the call path.
    CHECK(dialog_store_write_index(&s) == PB_OK);
    CHECK(dialog_store_load(&back, false) && dialog_set_equal(&back, &s));
    CHECK(!dialog_store_load(&back, true));
    // A file shorter than the table is not one either.
    write_bytes(path, 63902);
    CHECK(!dialog_store_load(&back, true));
    write_bytes(path, 63903);
    CHECK(dialog_store_load(&back, true) && dialog_set_equal(&back, &s));

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
    CHECK(!dialog_store_load(&back, false) && exists(dir, "dialog-de.alaw"));

    CHECK(dialog_store_write_index(&s) == PB_OK);
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
    test_set_index_round_trip();
    test_manifest_fallback_to_german();
    test_manifest_preferred_locale_wins();
    test_manifest_malformed_locale_skipped();
    test_manifest_too_many_clips();
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
