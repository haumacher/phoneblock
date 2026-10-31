// Host test for the audio channel's manifest (audio_manifest.c, issue #429):
// the format gate, content-addressed paths, the announcement's locale chain,
// and the dialog tiers read from the same manifest (dialog_manifest.c).
#include <stdio.h>
#include <string.h>

#include "audio_manifest.h"
#include "cJSON.h"
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

#define SHA  "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
#define SHA2 "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"
#define ASSET(path, sha, bytes) \
    "{\"path\":\"" path "\",\"sha256\":\"" sha "\",\"bytes\":" #bytes "}"

// A channel as scripts/audio-assets.sh publishes it: content-addressed names
// (<kind>-<lang>[-<tier>]-<sha8>), German with three tiers, English with an
// announcement and one tier, French with an announcement only.
static const char *CHANNEL =
    "{\"format\":1,\"assets\":{"
    "\"de\":{\"announcement\":" ASSET("announcement-de-01234567.alaw", SHA, 78762) ","
    "\"dialog\":[" ASSET("dialog-de-s-01234567.bin", SHA, 117995) ","
                   ASSET("dialog-de-m-fedcba98.bin", SHA2, 221243) ","
                   ASSET("dialog-de-l-01234567.bin", SHA, 489075) "]},"
    "\"en\":{\"announcement\":" ASSET("announcement-en-fedcba98.alaw", SHA2, 70001) ","
    "\"dialog\":[" ASSET("dialog-en-s-fedcba98.bin", SHA2, 128037) "]},"
    "\"fr\":{\"announcement\":" ASSET("announcement-fr-01234567.alaw", SHA, 80000) "}}}";

static void test_format_gate(void)
{
    cJSON *root = cJSON_Parse(CHANNEL);
    CHECK(root != NULL);
    CHECK(audio_manifest_assets(root) == cJSON_GetObjectItem(root, "assets"));
    cJSON_Delete(root);

    const char *other[] = {
        "{\"format\":2,\"assets\":{}}",          // a later format: not ours
        "{\"format\":0,\"assets\":{}}",
        "{\"format\":\"1\",\"assets\":{}}",
        "{\"format\":1.5,\"assets\":{}}",
        "{\"assets\":{}}",                       // a firmware bundle's manifest
        "{\"version\":\"1.7.0\",\"assets\":{}}",
        "{\"format\":1}", "{\"format\":1,\"assets\":[]}", "[]", "1",
    };
    for (size_t i = 0; i < sizeof(other) / sizeof(other[0]); i++) {
        root = cJSON_Parse(other[i]);
        CHECK(root != NULL);
        CHECK(audio_manifest_assets(root) == NULL);
        cJSON_Delete(root);
    }
    CHECK(audio_manifest_assets(NULL) == NULL);
}

static void test_paths(void)
{
    const char *good[] = { "announcement-de-01234567.alaw", "dialog-de-s-01234567.bin",
                           "a", "sub/dir/f.bin", "x...y", "...", "A_b.C-9" };
    for (size_t i = 0; i < sizeof(good) / sizeof(good[0]); i++)
        CHECK(audio_manifest_path_ok(good[i]));
    const char *bad[] = { NULL, "", "/abs.bin", "a/", "a//b", "..", ".", "../x",
                          "a/../b", "a/./b", "a b", "a?b", "a#b", "a%2e", "a\\b",
                          "https://evil/x", "ü.bin",
                          "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                          "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" };
    for (size_t i = 0; i < sizeof(bad) / sizeof(bad[0]); i++)
        CHECK(!audio_manifest_path_ok(bad[i]));
    char max[129];
    memset(max, 'a', 128);
    max[128] = '\0';
    CHECK(audio_manifest_path_ok(max));

    CHECK(audio_manifest_sha_ok(SHA) && audio_manifest_sha_ok(SHA2));
    CHECK(!audio_manifest_sha_ok(NULL) && !audio_manifest_sha_ok("abc") &&
          !audio_manifest_sha_ok(SHA "0") &&
          !audio_manifest_sha_ok("g123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));
}

static void test_announcement_chain(void)
{
    cJSON *root = cJSON_Parse(CHANNEL);
    const cJSON *assets = audio_manifest_assets(root);
    audio_asset_t a;

    const char *fr[] = { "fr", "en", "de" };
    CHECK(audio_manifest_announcement(assets, fr, 3, &a));
    CHECK(strcmp(a.lang, "fr") == 0 && strcmp(a.path, "announcement-fr-01234567.alaw") == 0 &&
          strcmp(a.sha256, SHA) == 0 && a.bytes == 80000);

    // A locale without any audio (Italian) falls back to English.
    const char *it[] = { "it", "en", "de" };
    CHECK(audio_manifest_announcement(assets, it, 3, &a));
    CHECK(strcmp(a.lang, "en") == 0 && a.bytes == 70001 && strcmp(a.sha256, SHA2) == 0);
    // Nothing anywhere in the chain.
    const char *xx[] = { "it", "nl" };
    CHECK(!audio_manifest_announcement(assets, xx, 2, &a));
    CHECK(a.lang == NULL && a.path == NULL && a.bytes == 0);
    CHECK(!audio_manifest_announcement(NULL, fr, 3, &a));
    cJSON_Delete(root);

    // Malformed announcements skip their locale; extra fields are fine.
    const char *bad[] = {
        ASSET("../announcement-it.alaw", SHA, 100),     // path escapes the channel
        ASSET("/announcement-it.alaw", SHA, 100),
        ASSET("", SHA, 100),
        ASSET("announcement-it.alaw", "abc", 100),       // short sha
        ASSET("announcement-it.alaw", SHA, 0),           // empty
        ASSET("announcement-it.alaw", SHA, 1.5),
        "{\"sha256\":\"" SHA "\",\"bytes\":100}",
        "\"announcement-it.alaw\"", "[]", "null",
    };
    for (size_t i = 0; i < sizeof(bad) / sizeof(bad[0]); i++) {
        char json[512];
        snprintf(json, sizeof(json),
                 "{\"it\":{\"announcement\":%s},\"de\":{\"announcement\":"
                 ASSET("announcement-de-01234567.alaw", SHA, 78762) "}}", bad[i]);
        cJSON *as = cJSON_Parse(json);
        CHECK(as != NULL);
        const char *chain[] = { "it", "de" };
        CHECK(audio_manifest_announcement(as, chain, 2, &a) && strcmp(a.lang, "de") == 0);
        cJSON_Delete(as);
    }
    cJSON *as = cJSON_Parse("{\"it\":{\"announcement\":{\"path\":\"a-1.alaw\",\"sha256\":\""
                            SHA "\",\"bytes\":5,\"voice\":\"x\"}}}");
    const char *it1[] = { "it" };
    CHECK(audio_manifest_announcement(as, it1, 1, &a) && a.bytes == 5);
    cJSON_Delete(as);
}

// The dialog tiers come from the same manifest: content-addressed paths,
// missing tiers (English has only s) and missing locales (French has none).
static void test_dialog_from_channel(void)
{
    cJSON *root = cJSON_Parse(CHANNEL);
    const cJSON *assets = audio_manifest_assets(root);
    dialog_manifest_set_t d;
    const char *de[] = { "de" };
    CHECK(dialog_manifest_select(assets, de, 1, 131072, &d) &&
          strcmp(d.path, "dialog-de-s-01234567.bin") == 0);
    CHECK(dialog_manifest_select(assets, de, 1, 229376, &d) &&
          strcmp(d.path, "dialog-de-m-fedcba98.bin") == 0 && strcmp(d.sha256, SHA2) == 0);
    CHECK(dialog_manifest_select(assets, de, 1, UINT64_MAX, &d) && d.bytes == 489075);

    // English has a set but only the s tier: the largest budget takes it.
    const char *en[] = { "en", "de" };
    CHECK(dialog_manifest_select(assets, en, 2, UINT64_MAX, &d) &&
          strcmp(d.lang, "en") == 0 && d.bytes == 128037);
    // French has no set: German follows, at the tier of the budget.
    const char *fr[] = { "fr", "en", "de" };
    CHECK(dialog_manifest_select(assets, fr, 3, 229376, &d) && strcmp(d.lang, "en") == 0);
    const char *fr_de[] = { "fr", "de" };
    CHECK(dialog_manifest_select(assets, fr_de, 2, 229376, &d) && strcmp(d.lang, "de") == 0 &&
          d.bytes == 221243);
    // English's s tier does not fit a budget below it: German's s does.
    CHECK(dialog_manifest_select(assets, en, 2, 120000, &d) && strcmp(d.lang, "de") == 0 &&
          d.bytes == 117995);
    cJSON_Delete(root);

    // A tier path that would leave the channel makes the locale malformed.
    cJSON *as = cJSON_Parse("{\"it\":{\"dialog\":[" ASSET("../x.bin", SHA, 1000) "]},"
                            "\"de\":{\"dialog\":[" ASSET("dialog-de-s-0.bin", SHA, 1000) "]}}");
    const char *it[] = { "it", "de" };
    CHECK(dialog_manifest_select(as, it, 2, 131072, &d) && strcmp(d.lang, "de") == 0);
    cJSON_Delete(as);
}

int main(void)
{
    test_format_gate();
    test_paths();
    test_announcement_chain();
    test_dialog_from_channel();
    printf("test_audio_manifest: %d checks, %d failures\n", checks, failures);
    return failures ? 1 : 0;
}
