// Probe tests for the interactive dialog (issue #429), written independently
// of the delivery: compose the dialog with the await timeout, the time cap and
// the fleet-compatibility rules of the i18n manifest.
#include <stdio.h>
#include <string.h>

#include "cJSON.h"
#include "dialog.h"
#include "dialog_manifest.h"

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

static uint32_t rng_zero(void *ctx) { (void)ctx; return 0; }

static const uint8_t ONE_EACH[DIALOG_PHASE_COUNT] = { 1, 1, 1, 1, 1 };

// Speech that starts just before the await timeout and lasts beyond it must
// not be cut off as "no answer": the turn belongs to the caller until they are
// quiet again.
static void probe_speech_straddles_timeout(void)
{
    dialog_sm_t d;
    CHECK(dialog_start(&d, ONE_EACH, rng_zero, NULL).kind == DIALOG_ACT_SAY);
    CHECK(dialog_tick(&d, TICK, true).kind == DIALOG_ACT_LISTEN);

    uint32_t t = TICK;
    while (t < DIALOG_AWAIT_TIMEOUT_MS - 100) {
        CHECK(dialog_tick(&d, TICK, false).kind == DIALOG_ACT_NONE);
        t += TICK;
    }
    dialog_vad(&d, VAD_SPEECH_ONSET);
    // Two more seconds of talking, well past the timeout.
    for (int i = 0; i < 2000 / TICK; i++) {
        CHECK(dialog_tick(&d, TICK, false).kind == DIALOG_ACT_NONE);
    }
    dialog_vad(&d, VAD_SILENCE_ONSET);
    dialog_action_t a = dialog_tick(&d, TICK, false);
    CHECK(a.kind == DIALOG_ACT_SAY);
    CHECK(a.phase == DIALOG_PHASE_AM_APARAT);
}

// A caller who keeps the line busy with endless speech (a recording, music)
// while their media keeps arriving: no time cap, the dialog never ends.
static void probe_endless_speech_with_packets_no_end(void)
{
    dialog_sm_t d;
    dialog_start(&d, ONE_EACH, rng_zero, NULL);
    CHECK(dialog_tick(&d, TICK, true).kind == DIALOG_ACT_LISTEN);
    dialog_vad(&d, VAD_SPEECH_ONSET);
    bool ended = false;
    for (uint32_t t = TICK; t <= 3u * 3600u * 1000u; t += TICK) {
        dialog_rx(&d);
        dialog_action_t a = dialog_tick(&d, TICK, false);
        if (a.kind == DIALOG_ACT_END) { ended = true; break; }
        CHECK(a.kind == DIALOG_ACT_NONE);
    }
    CHECK(!ended);
}

// A silent caller whose line still delivers packets (comfort noise, silence
// frames): prompts and awaits alternate for good, no END.
static void probe_silence_with_packets_no_end(void)
{
    dialog_sm_t d;
    dialog_action_t a = dialog_start(&d, ONE_EACH, rng_zero, NULL);
    bool saying = a.kind == DIALOG_ACT_SAY;
    bool ended = false;
    for (uint32_t t = 0; t <= 3u * 3600u * 1000u; t += TICK) {
        dialog_rx(&d);
        a = dialog_tick(&d, TICK, saying);   // every prompt ends at once
        if (a.kind == DIALOG_ACT_END) { ended = true; break; }
        if (a.kind == DIALOG_ACT_LISTEN) saying = false;
        if (a.kind == DIALOG_ACT_SAY) saying = true;
    }
    CHECK(!ended);
}

// No packets at all (the caller is gone without a BYE): END after
// DIALOG_RX_TIMEOUT_MS, also while a prompt is still playing.
static void probe_no_packets_end(void)
{
    dialog_sm_t d;
    dialog_start(&d, ONE_EACH, rng_zero, NULL);
    uint32_t end = 0;
    for (uint32_t t = TICK; t <= DIALOG_RX_TIMEOUT_MS + 1000 && !end; t += TICK) {
        if (dialog_tick(&d, TICK, false).kind == DIALOG_ACT_END) end = t;
    }
    CHECK(end == DIALOG_RX_TIMEOUT_MS);
    CHECK(dialog_tick(&d, TICK, false).kind == DIALOG_ACT_END);
}

// Every clip index the dialog asks for is in range, for all phase sizes.
static void probe_clip_index_in_range(void)
{
    for (int n = 1; n <= DIALOG_MAX_CLIPS; n++) {
        uint8_t count[DIALOG_PHASE_COUNT] = { (uint8_t)n, (uint8_t)n, (uint8_t)n,
                                              (uint8_t)n, (uint8_t)n };
        dialog_sm_t d;
        dialog_action_t a = dialog_start(&d, count, rng_zero, NULL);
        for (int round = 0; round < 200; round++) {
            // The caller's media keeps arriving (no inactivity END).
            dialog_rx(&d);
            CHECK(a.kind != DIALOG_ACT_END);
            if (a.kind == DIALOG_ACT_SAY) {
                CHECK(a.clip >= 0 && a.clip < n);
                a = dialog_tick(&d, TICK, true);
            } else {
                // Alternate between answering and staying silent.
                if (round % 3 == 0) {
                    dialog_vad(&d, VAD_SPEECH_ONSET);
                    dialog_vad(&d, VAD_SILENCE_ONSET);
                }
                a = dialog_tick(&d, TICK, false);
                for (int i = 0; a.kind == DIALOG_ACT_NONE && i < 400; i++) {
                    dialog_rx(&d);
                    a = dialog_tick(&d, TICK, false);
                }
            }
        }
    }
}

// --- Manifest: what a future bundle may add must not break this firmware ----

#define SHA "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
#define FIVE_PHASES \
    "\"hello\":[[0,100]],\"who-is-there\":[[100,100]],\"am-aparat\":[[200,100]]," \
    "\"question\":[[300,100]],\"cant-hear-you\":[[400,100]]"

static void probe_manifest_forward_compatible(void)
{
    // A later bundle adds a sixth phase, an extra field on the asset, and a
    // third element per clip.
    const char *json =
        "{\"de\":{\"announcement\":{},\"dialog\":{"
        "\"path\":\"audio/dialog-de.alaw\",\"sha256\":\"" SHA "\","
        "\"bytes\":600,\"voice\":\"x\",\"phases\":{"
        "\"hello\":[[0,100,\"h\"]],"
        "\"who-is-there\":[[100,100]],"
        "\"am-aparat\":[[200,100]],"
        "\"question\":[[300,100]],"
        "\"cant-hear-you\":[[400,100]],"
        "\"goodbye\":[[500,100]]}}}}";
    cJSON *assets = cJSON_Parse(json);
    CHECK(assets != NULL);
    const char *chain[] = { "fr", "en", "de" };
    dialog_manifest_set_t set;
    CHECK(dialog_manifest_select(assets, chain, 3, &set));
    CHECK(set.lang && strcmp(set.lang, "de") == 0);
    for (int p = 0; p < DIALOG_PHASE_COUNT; p++) CHECK(set.set.count[p] == 1);
    cJSON_Delete(assets);
}

// A locale whose `dialog` is not an object (garbage from a broken bundle) is
// skipped, the next chain locale is used.
static void probe_manifest_garbage_dialog(void)
{
    const char *json =
        "{\"fr\":{\"dialog\":\"oops\"},"
        "\"en\":{\"dialog\":[1,2,3]},"
        "\"de\":{\"dialog\":{\"path\":\"audio/dialog-de.alaw\",\"sha256\":\"" SHA "\","
        "\"bytes\":500,\"phases\":{" FIVE_PHASES "}}}}";
    cJSON *assets = cJSON_Parse(json);
    const char *chain[] = { "fr", "en", "de" };
    dialog_manifest_set_t set;
    CHECK(dialog_manifest_select(assets, chain, 3, &set));
    CHECK(set.lang && strcmp(set.lang, "de") == 0);
    cJSON_Delete(assets);
}

int main(void)
{
    probe_speech_straddles_timeout();
    probe_endless_speech_with_packets_no_end();
    probe_silence_with_packets_no_end();
    probe_no_packets_end();
    probe_clip_index_in_range();
    probe_manifest_forward_compatible();
    probe_manifest_garbage_dialog();
    printf("test_dialog_probe: %d checks, %d failures\n", checks, failures);
    return failures ? 1 : 0;
}
