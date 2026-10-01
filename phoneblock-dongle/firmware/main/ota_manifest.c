#include "ota_manifest.h"

#include <string.h>

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

// The browser installer flashes every entry in parts[] (bootloader,
// partition-table, ota_data, app). OTA only writes the app slot, so pick the
// part whose URL ends in the app binary name. Suffix match keeps us decoupled
// from the partition layout (offsets), the per-chip subdirectory and absolute
// URL prefixes.
static const char *find_app_url(const cJSON *build)
{
    static const char APP_SUFFIX[] = "/phoneblock_dongle.bin";
    const size_t slen = sizeof(APP_SUFFIX) - 1;

    const cJSON *parts = cJSON_GetObjectItem(build, "parts");
    const cJSON *p;
    cJSON_ArrayForEach(p, parts) {
        const cJSON *jp = cJSON_GetObjectItem(p, "path");
        if (!cJSON_IsString(jp)) continue;
        size_t plen = strlen(jp->valuestring);
        if (plen >= slen
                && strcmp(jp->valuestring + plen - slen, APP_SUFFIX) == 0) {
            return jp->valuestring;
        }
    }
    return NULL;
}

ota_pick_result_t ota_manifest_pick(const cJSON *root, const char *chip_family,
                                    ota_build_t *out)
{
    memset(out, 0, sizeof(*out));

    // URLs are *not* signed; trust comes from the post-download hash compare
    // against the signed value of the same build.
    const cJSON *builds = cJSON_GetObjectItem(root, "builds");
    if (!cJSON_IsArray(builds) || cJSON_GetArraySize(builds) == 0) {
        out->error = "Manifest has no builds[].";
        return OTA_PICK_ERR_PARSE;
    }
    const cJSON *build = NULL;
    const cJSON *b;
    cJSON_ArrayForEach(b, builds) {
        const cJSON *fam = cJSON_GetObjectItem(b, "chipFamily");
        if (cJSON_IsString(fam) && strcmp(fam->valuestring, chip_family) == 0) {
            build = b;
            break;
        }
    }
    if (!build) {
        out->error = "Manifest has no build for this chip.";
        return OTA_PICK_ERR_PARSE;
    }

    out->app_url = find_app_url(build);
    if (!out->app_url) {
        out->error = "Manifest has no app binary part.";
        return OTA_PICK_ERR_PARSE;
    }

    const cJSON *integrity = cJSON_GetObjectItem(build, "integrity");
    if (!integrity) integrity = cJSON_GetObjectItem(root, "integrity");
    const cJSON *hash = cJSON_IsObject(integrity)
                      ? cJSON_GetObjectItem(integrity, "app_sha256") : NULL;
    const cJSON *sig  = cJSON_IsObject(integrity)
                      ? cJSON_GetObjectItem(integrity, "signature")  : NULL;
    if (!cJSON_IsString(hash) || strlen(hash->valuestring) != 64) {
        out->error = "Manifest missing integrity.app_sha256.";
        return OTA_PICK_ERR_INTEGRITY;
    }
    if (!cJSON_IsString(sig)) {
        out->error = "Manifest missing integrity.signature.";
        return OTA_PICK_ERR_INTEGRITY;
    }
    out->app_sha256 = hash->valuestring;
    out->signature  = sig->valuestring;
    return OTA_PICK_OK;
}
