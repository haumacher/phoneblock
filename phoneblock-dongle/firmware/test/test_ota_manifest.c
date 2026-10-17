// Host test for ota_manifest.c — which build, app URL and integrity block a
// dongle takes from a (multi-chip) OTA manifest.
#include <assert.h>
#include <stdio.h>
#include <string.h>

#include "cJSON.h"
#include "ota_manifest.h"

#define H64(c) c c c c c c c c c c c c c c c c c c c c c c c c c c c c c c c c \
               c c c c c c c c c c c c c c c c c c c c c c c c c c c c c c c c
#define HASH_A H64("a")
#define HASH_C H64("c")
#define HASH_S H64("5")

#define BASE "https://cdn.example/dongle/firmware/9.9.9/"

#define ESP32_BUILD \
    "{\"chipFamily\":\"ESP32\",\"parts\":[" \
    "{\"path\":\"" BASE "bootloader.bin\",\"offset\":4096}," \
    "{\"path\":\"" BASE "phoneblock_dongle.bin\",\"offset\":131072}]}"

#define C3_BUILD \
    "{\"chipFamily\":\"ESP32-C3\",\"parts\":[" \
    "{\"path\":\"" BASE "esp32c3/bootloader.bin\",\"offset\":0}," \
    "{\"path\":\"" BASE "esp32c3/phoneblock_dongle.bin\",\"offset\":131072}]," \
    "\"integrity\":{\"app_sha256\":\"" HASH_C "\",\"signature\":\"SIGC\"}}"

#define S3_BUILD \
    "{\"chipFamily\":\"ESP32-S3\",\"parts\":[" \
    "{\"path\":\"" BASE "esp32s3/bootloader.bin\",\"offset\":0}," \
    "{\"path\":\"" BASE "esp32s3/phoneblock_dongle.bin\",\"offset\":131072}]," \
    "\"integrity\":{\"app_sha256\":\"" HASH_S "\",\"signature\":\"SIGS\"}}"

#define TOP_INTEGRITY \
    "\"integrity\":{\"app_sha256\":\"" HASH_A "\",\"signature\":\"SIGA\"}"

static ota_pick_result_t pick(const char *json, const char *chip,
                              ota_build_t *out)
{
    cJSON *root = cJSON_Parse(json);
    assert(root);
    ota_pick_result_t r = ota_manifest_pick(root, chip, out);
    // Copy borrowed strings out before freeing: compare against literals.
    static char url[256], hash[65], sig[32];
    if (r == OTA_PICK_OK) {
        snprintf(url,  sizeof url,  "%s", out->app_url);
        snprintf(hash, sizeof hash, "%s", out->app_sha256);
        snprintf(sig,  sizeof sig,  "%s", out->signature);
        out->app_url = url; out->app_sha256 = hash; out->signature = sig;
    }
    cJSON_Delete(root);
    return r;
}

int main(void)
{
    ota_build_t b;
    const char *multi = "{\"version\":\"9.9.9\",\"builds\":["
                        ESP32_BUILD "," C3_BUILD "," S3_BUILD "]," TOP_INTEGRITY "}";

    // ESP32 takes builds[0] with the top-level integrity — exactly what
    // firmware before C3 support does, so both read the same values.
    assert(pick(multi, "ESP32", &b) == OTA_PICK_OK);
    assert(strcmp(b.app_url, BASE "phoneblock_dongle.bin") == 0);
    assert(strcmp(b.app_sha256, HASH_A) == 0);
    assert(strcmp(b.signature, "SIGA") == 0);

    // C3 takes its own build and its own integrity, not the top-level one.
    assert(pick(multi, "ESP32-C3", &b) == OTA_PICK_OK);
    assert(strcmp(b.app_url, BASE "esp32c3/phoneblock_dongle.bin") == 0);
    assert(strcmp(b.app_sha256, HASH_C) == 0);
    assert(strcmp(b.signature, "SIGC") == 0);

    // S3 likewise: its own build and integrity, neither C3's nor top-level.
    assert(pick(multi, "ESP32-S3", &b) == OTA_PICK_OK);
    assert(strcmp(b.app_url, BASE "esp32s3/phoneblock_dongle.bin") == 0);
    assert(strcmp(b.app_sha256, HASH_S) == 0);
    assert(strcmp(b.signature, "SIGS") == 0);

    // Build order does not matter for the non-ESP32 chips.
    const char *s3_first = "{\"version\":\"9.9.9\",\"builds\":["
                           ESP32_BUILD "," S3_BUILD "," C3_BUILD "]," TOP_INTEGRITY "}";
    assert(pick(s3_first, "ESP32-C3", &b) == OTA_PICK_OK);
    assert(strcmp(b.app_sha256, HASH_C) == 0);
    assert(pick(s3_first, "ESP32-S3", &b) == OTA_PICK_OK);
    assert(strcmp(b.app_sha256, HASH_S) == 0);

    // A C3-era manifest (ESP32 + C3, published before S3 support): the S3
    // must not fall back to either image — neither boots on it.
    const char *c3_era = "{\"version\":\"9.9.9\",\"builds\":["
                         ESP32_BUILD "," C3_BUILD "]," TOP_INTEGRITY "}";
    assert(pick(c3_era, "ESP32-S3", &b) == OTA_PICK_ERR_PARSE);
    assert(strstr(b.error, "no build for this chip"));
    // ...while ESP32 and C3 still find theirs.
    assert(pick(c3_era, "ESP32", &b) == OTA_PICK_OK);
    assert(pick(c3_era, "ESP32-C3", &b) == OTA_PICK_OK);

    // A pre-C3 manifest (ESP32 build only) still works for ESP32...
    const char *legacy = "{\"version\":\"9.9.9\",\"builds\":["
                         ESP32_BUILD "]," TOP_INTEGRITY "}";
    assert(pick(legacy, "ESP32", &b) == OTA_PICK_OK);
    assert(strcmp(b.app_sha256, HASH_A) == 0);
    // ...and a C3 must not fall back to the ESP32 image.
    assert(pick(legacy, "ESP32-C3", &b) == OTA_PICK_ERR_PARSE);
    assert(strstr(b.error, "no build for this chip"));
    assert(pick(legacy, "ESP32-S3", &b) == OTA_PICK_ERR_PARSE);
    assert(strstr(b.error, "no build for this chip"));

    // No builds at all.
    assert(pick("{\"builds\":[]," TOP_INTEGRITY "}", "ESP32", &b)
           == OTA_PICK_ERR_PARSE);
    assert(pick("{" TOP_INTEGRITY "}", "ESP32", &b) == OTA_PICK_ERR_PARSE);

    // Build without an app binary part.
    assert(pick("{\"builds\":[{\"chipFamily\":\"ESP32\",\"parts\":["
                "{\"path\":\"" BASE "bootloader.bin\",\"offset\":4096}]}],"
                TOP_INTEGRITY "}", "ESP32", &b) == OTA_PICK_ERR_PARSE);
    assert(strstr(b.error, "app binary"));

    // Suffix match needs the '/': a look-alike file name is not the app.
    assert(pick("{\"builds\":[{\"chipFamily\":\"ESP32\",\"parts\":["
                "{\"path\":\"" BASE "evil_phoneblock_dongle.bin\"}]}],"
                TOP_INTEGRITY "}", "ESP32", &b) == OTA_PICK_ERR_PARSE);

    // Missing / malformed integrity.
    assert(pick("{\"builds\":[" ESP32_BUILD "]}", "ESP32", &b)
           == OTA_PICK_ERR_INTEGRITY);
    assert(pick("{\"builds\":[" ESP32_BUILD "],\"integrity\":"
                "{\"app_sha256\":\"abc\",\"signature\":\"S\"}}", "ESP32", &b)
           == OTA_PICK_ERR_INTEGRITY);
    assert(pick("{\"builds\":[" ESP32_BUILD "],\"integrity\":"
                "{\"app_sha256\":\"" HASH_A "\"}}", "ESP32", &b)
           == OTA_PICK_ERR_INTEGRITY);
    assert(strstr(b.error, "signature"));

    // A C3 build without its own integrity falls back to the top-level
    // block. Harmless: that hash belongs to the ESP32 image, so the
    // downloaded C3 binary fails the post-download hash compare.
    assert(pick("{\"builds\":[" ESP32_BUILD ","
                "{\"chipFamily\":\"ESP32-C3\",\"parts\":[{\"path\":\""
                BASE "esp32c3/phoneblock_dongle.bin\"}]}],"
                TOP_INTEGRITY "}", "ESP32-C3", &b) == OTA_PICK_OK);
    assert(strcmp(b.app_sha256, HASH_A) == 0);

    // Per-chip OTA file (scripts/ota-manifest.sh): one build, only the app
    // part, own integrity block, no top-level one.
    const char *per_chip =
        "{\"version\":\"9.9.9\",\"builds\":[{\"chipFamily\":\"ESP32-C3\","
        "\"parts\":[{\"path\":\"" BASE "esp32c3/phoneblock_dongle.bin\"}],"
        "\"integrity\":{\"app_sha256\":\"" HASH_C "\",\"signature\":\"SIGC\"}}]}";
    assert(pick(per_chip, "ESP32-C3", &b) == OTA_PICK_OK);
    assert(strcmp(b.app_url, BASE "esp32c3/phoneblock_dongle.bin") == 0);
    assert(strcmp(b.app_sha256, HASH_C) == 0);
    assert(strcmp(b.signature, "SIGC") == 0);
    // Served to the wrong chip (misnamed file): refused, not flashed.
    assert(pick(per_chip, "ESP32", &b) == OTA_PICK_ERR_PARSE);
    assert(pick(per_chip, "ESP32-S3", &b) == OTA_PICK_ERR_PARSE);

    // Per-chip OTA file for the S3 (ota-esp32s3.json).
    const char *per_chip_s3 =
        "{\"version\":\"9.9.9\",\"builds\":[{\"chipFamily\":\"ESP32-S3\","
        "\"parts\":[{\"path\":\"" BASE "esp32s3/phoneblock_dongle.bin\"}],"
        "\"integrity\":{\"app_sha256\":\"" HASH_S "\",\"signature\":\"SIGS\"}}]}";
    assert(pick(per_chip_s3, "ESP32-S3", &b) == OTA_PICK_OK);
    assert(strcmp(b.app_url, BASE "esp32s3/phoneblock_dongle.bin") == 0);
    assert(strcmp(b.app_sha256, HASH_S) == 0);
    assert(strcmp(b.signature, "SIGS") == 0);
    assert(pick(per_chip_s3, "ESP32", &b) == OTA_PICK_ERR_PARSE);
    assert(pick(per_chip_s3, "ESP32-C3", &b) == OTA_PICK_ERR_PARSE);

    printf("test_ota_manifest: all tests passed\n");
    return 0;
}
