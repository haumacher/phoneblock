// Probe tests for the interactive dialog (issue #429), written independently
// of the delivery: compose the dialog with the await timeout, the time cap and
// the fleet-compatibility rules of the i18n manifest.
#include <stdio.h>
#include <string.h>

#include "cJSON.h"
#include "dialog.h"
#include "dialog_manifest.h"
#include "storage_budget.h"

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
#define TIER(path, bytes) \
    "{\"path\":\"" path "\",\"sha256\":\"" SHA "\",\"bytes\":" #bytes "}"

static void probe_manifest_forward_compatible(void)
{
    // A later channel adds fields to the index entry and keys next to it; a
    // later index adds fields to speakers and tiers and a fourth, larger
    // tier: the largest tier within the budget is still found, the rest
    // ignored.
    cJSON *assets = cJSON_Parse(
        "{\"de\":{\"announcement\":{},\"dialog-v2\":{},\"dialog\":{"
        "\"path\":\"dialog-de-1.json\",\"sha256\":\"" SHA "\",\"bytes\":3000,\"x\":1}}}");
    CHECK(assets != NULL);
    const char *chain[] = { "fr", "en", "de" };
    audio_asset_t ix;
    CHECK(dialog_manifest_index(assets, chain, 3, &ix) && strcmp(ix.lang, "de") == 0);
    cJSON_Delete(assets);
    cJSON *index = cJSON_Parse(
        "{\"v\":2,\"speakers\":[{\"id\":\"leonie\",\"accent\":\"x\",\"sets\":["
        "{\"path\":\"s.bin\",\"sha256\":\"" SHA "\",\"bytes\":120000,\"voice\":\"x\",\"clips\":[1,2]},"
        "{\"path\":\"xl.bin\",\"sha256\":\"" SHA "\",\"bytes\":900000,\"voice\":\"y\"}]}]}");
    dialog_manifest_set_t set;
    CHECK(dialog_index_select(index, "", 131072, &set) == DIALOG_PICK_CHOSEN);
    CHECK(set.bytes == 120000 && strcmp(set.path, "s.bin") == 0);
    cJSON_Delete(index);
}

// A locale whose `dialog` is not an index entry (garbage from a broken
// channel, or the tier list of the unpublished first format) is skipped,
// the next chain locale is used.
static void probe_manifest_garbage_dialog(void)
{
    const char *json =
        "{\"fr\":{\"dialog\":\"oops\"},"
        "\"en\":{\"dialog\":[1,2,3]},"
        "\"it\":{\"dialog\":[" TIER("dialog-it-s.bin", 500) "]},"
        "\"de\":{\"dialog\":" TIER("dialog-de-1.json", 500) "}}";
    cJSON *assets = cJSON_Parse(json);
    const char *chain[] = { "fr", "en", "it", "de" };
    audio_asset_t ix;
    CHECK(dialog_manifest_index(assets, chain, 4, &ix));
    CHECK(ix.lang && strcmp(ix.lang, "de") == 0);
    cJSON_Delete(assets);
}

// A later firmware's sixth phase in the header (its clips after ours) leaves
// this firmware's five intact.
static void probe_header_extra_phase(void)
{
    // P = 6, one clip each: header 8 + 6 + 6 * 8 = 62 bytes, clips of 10.
    uint8_t f[62 + 60];
    memset(f, 0xD5, sizeof(f));
    memcpy(f, "PBDS", 4);
    f[4] = DIALOG_HEADER_VERSION;
    f[5] = 6;
    f[6] = 62; f[7] = 0;
    for (int p = 0; p < 6; p++) {
        f[8 + p] = 1;
        uint32_t off = 62 + 10u * (uint32_t)p;
        uint8_t *e = f + 14 + 8 * p;
        e[0] = (uint8_t)off; e[1] = (uint8_t)(off >> 8); e[2] = 0; e[3] = 0;
        e[4] = 10; e[5] = 0; e[6] = 0; e[7] = 0;
    }
    dialog_set_t set;
    memset(&set, 0, sizeof(set));
    CHECK(dialog_header_parse(f, sizeof(f), sizeof(f), &set) == DIALOG_HEADER_OK);
    for (int p = 0; p < DIALOG_PHASE_COUNT; p++) {
        CHECK(set.count[p] == 1 && set.clip[p][0].off == 62 + 10u * (uint32_t)p);
    }
    // A later format version is not guessed at.
    f[4] = DIALOG_HEADER_VERSION + 1;
    CHECK(dialog_header_parse(f, sizeof(f), sizeof(f), &set) == DIALOG_HEADER_BAD);
}


// --- Tier selection at the boundaries (written independently of the delivery) --

#define SHA64 "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
#define BTIER(name, n) "{\"path\":\"" name "\",\"sha256\":\"" SHA64 "\",\"bytes\":" #n "}"

static dialog_pick_t pick(const char *json, const char *speaker, uint64_t budget,
                          char *path_out)
{
    cJSON *index = cJSON_Parse(json);
    CHECK(index != NULL);
    dialog_manifest_set_t out;
    dialog_pick_t r = dialog_index_select(index, speaker, budget, &out);
    path_out[0] = '\0';
    if (r != DIALOG_PICK_NONE) snprintf(path_out, 64, "%s", out.path);
    cJSON_Delete(index);
    return r;
}

static void probe_tier_boundaries(void)
{
    // Tiers listed out of order; a budget exactly at a tier's size takes it.
    const char *json = "{\"speakers\":["
        "{\"id\":\"anna\",\"sets\":[" BTIER("a-s.bin", 100000) "]},"
        "{\"id\":\"bert\",\"sets\":[" BTIER("b-l.bin", 500000) ","
                                     BTIER("b-s.bin", 120000) ","
                                     BTIER("b-m.bin", 220000) "]}]}";
    char p[64];
    CHECK(pick(json, "bert", 120000, p) == DIALOG_PICK_CHOSEN && strcmp(p, "b-s.bin") == 0);
    CHECK(pick(json, "bert", 219999, p) == DIALOG_PICK_CHOSEN && strcmp(p, "b-s.bin") == 0);
    CHECK(pick(json, "bert", 220000, p) == DIALOG_PICK_CHOSEN && strcmp(p, "b-m.bin") == 0);
    CHECK(pick(json, "bert", UINT64_MAX, p) == DIALOG_PICK_CHOSEN && strcmp(p, "b-l.bin") == 0);
    // Every tier of the chosen speaker too large: the default's fits.
    CHECK(pick(json, "bert", 110000, p) == DIALOG_PICK_NO_FIT && strcmp(p, "a-s.bin") == 0);
    // Nothing fits anywhere.
    CHECK(pick(json, "bert", 99999, p) == DIALOG_PICK_NONE && p[0] == '\0');
    CHECK(pick(json, "", 99999, p) == DIALOG_PICK_NONE);
}

// The real partitions: LittleFS takes M on the C3's 640 KB and the L ceiling
// on the S3's ~9.6 MB; the budget never shrinks as the partition grows, and
// every budget picks the matching published tier (s/m/l).
static void probe_budget_by_partition(void)
{
    const storage_lfs_geometry_t g = { 4096, STORAGE_LFS_RESERVE_BLOCKS };
    CHECK(storage_budget_lfs_dialog(&g, 0xA0000) == STORAGE_DIALOG_TIER_M);
    CHECK(storage_budget_lfs_dialog(&g, 0x9A0000) == STORAGE_DIALOG_PLAIN_FS_MAX);
    uint64_t prev = 0;
    for (uint64_t t = 100000; t < 12000000; t += 7919) {
        uint64_t b = storage_budget_lfs_dialog(&g, t);
        CHECK(b >= prev);
        prev = b;
    }
    // The speakers index is in the M derivation at its cap.
    static const uint32_t peers[] = STORAGE_LFS_DIALOG_PEERS;
    CHECK(peers[3] == DIALOG_INDEX_MAX_BYTES);

    // Tiers as audio-assets.sh publishes them (s, m, l at most at their
    // limits): S on SPIFFS, M on the C3, L on the S3 and Linux.
    char json[512];
    snprintf(json, sizeof(json), "{\"speakers\":[{\"id\":\"anna\",\"sets\":["
             BTIER("a-s.bin", %u) "," BTIER("a-m.bin", %u) "," BTIER("a-l.bin", 520000) "]}]}",
             STORAGE_DIALOG_TIER_S, STORAGE_DIALOG_TIER_M);
    char p[64];
    CHECK(pick(json, "anna", STORAGE_DIALOG_TIER_S, p) == DIALOG_PICK_CHOSEN
          && strcmp(p, "a-s.bin") == 0);
    CHECK(pick(json, "anna", storage_budget_lfs_dialog(&g, 0xA0000), p) == DIALOG_PICK_CHOSEN
          && strcmp(p, "a-m.bin") == 0);
    CHECK(pick(json, "anna", storage_budget_lfs_dialog(&g, 0x9A0000), p) == DIALOG_PICK_CHOSEN
          && strcmp(p, "a-l.bin") == 0);
    CHECK(pick(json, "anna", STORAGE_DIALOG_PLAIN_FS_MAX, p) == DIALOG_PICK_CHOSEN
          && strcmp(p, "a-l.bin") == 0);
    // Only s and l published (a locale whose m equals s): the C3 takes s.
    snprintf(json, sizeof(json), "{\"speakers\":[{\"id\":\"anna\",\"sets\":["
             BTIER("a-s.bin", 120000) "," BTIER("a-l.bin", 520000) "]}]}");
    CHECK(pick(json, "anna", storage_budget_lfs_dialog(&g, 0xA0000), p) == DIALOG_PICK_CHOSEN
          && strcmp(p, "a-s.bin") == 0);
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
    probe_header_extra_phase();
    probe_tier_boundaries();
    probe_budget_by_partition();
    printf("test_dialog_probe: %d checks, %d failures\n", checks, failures);
    return failures ? 1 : 0;
}
