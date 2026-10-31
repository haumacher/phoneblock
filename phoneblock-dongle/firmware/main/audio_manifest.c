// Implementation of audio_manifest.h.

#include "audio_manifest.h"

#include <string.h>

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

bool audio_manifest_path_ok(const char *path)
{
    if (!path) return false;
    size_t n = strlen(path);
    if (n == 0 || n > 128) return false;
    size_t seg = 0;          // length of the current segment
    bool dots_only = true;   // ... and whether it is all '.'
    for (size_t i = 0; i <= n; i++) {
        char c = path[i];
        if (c == '/' || c == '\0') {
            if (seg == 0) return false;               // leading, trailing, "//"
            if (dots_only && seg <= 2) return false;  // "." or ".."
            seg = 0;
            dots_only = true;
            continue;
        }
        bool ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                  (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
        if (!ok) return false;
        if (c != '.') dots_only = false;
        seg++;
    }
    return true;
}

bool audio_manifest_sha_ok(const char *s)
{
    if (!s || strlen(s) != 64) return false;
    for (int i = 0; i < 64; i++) {
        char c = s[i];
        bool ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') ||
                  (c >= 'A' && c <= 'F');
        if (!ok) return false;
    }
    return true;
}

const cJSON *audio_manifest_assets(const cJSON *root)
{
    if (!cJSON_IsObject(root)) return NULL;
    const cJSON *format = cJSON_GetObjectItemCaseSensitive(root, "format");
    if (!cJSON_IsNumber(format) || format->valuedouble != (double)AUDIO_FORMAT_VERSION) {
        return NULL;
    }
    const cJSON *assets = cJSON_GetObjectItemCaseSensitive(root, "assets");
    return cJSON_IsObject(assets) ? assets : NULL;
}

static bool parse_asset(const cJSON *a, audio_asset_t *out)
{
    if (!cJSON_IsObject(a)) return false;
    const cJSON *path  = cJSON_GetObjectItemCaseSensitive(a, "path");
    const cJSON *sha   = cJSON_GetObjectItemCaseSensitive(a, "sha256");
    const cJSON *bytes = cJSON_GetObjectItemCaseSensitive(a, "bytes");
    if (!cJSON_IsString(path) || !audio_manifest_path_ok(path->valuestring)) return false;
    if (!cJSON_IsString(sha) || !audio_manifest_sha_ok(sha->valuestring)) return false;
    if (!cJSON_IsNumber(bytes)) return false;
    double v = bytes->valuedouble;
    if (!(v >= 1.0 && v <= 4294967295.0) || (double)(uint32_t)v != v) return false;
    out->path   = path->valuestring;
    out->sha256 = sha->valuestring;
    out->bytes  = (uint32_t)v;
    return true;
}

bool audio_manifest_announcement(const cJSON *assets, const char *const *chain,
                                 int chain_n, audio_asset_t *out)
{
    memset(out, 0, sizeof(*out));
    if (!cJSON_IsObject(assets)) return false;
    for (int i = 0; i < chain_n; i++) {
        const cJSON *lo = cJSON_GetObjectItemCaseSensitive(assets, chain[i]);
        const cJSON *a = cJSON_IsObject(lo)
            ? cJSON_GetObjectItemCaseSensitive(lo, "announcement") : NULL;
        if (parse_asset(a, out)) {
            out->lang = chain[i];
            return true;
        }
        memset(out, 0, sizeof(*out));
    }
    return false;
}
