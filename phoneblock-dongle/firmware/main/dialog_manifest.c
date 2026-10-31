// Implementation of dialog_manifest.h.

#include "dialog_manifest.h"

#include <string.h>

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

// A JSON number that is a whole uint32 (lo..UINT32_MAX).
static bool get_u32(const cJSON *n, uint32_t lo, uint32_t *out)
{
    if (!cJSON_IsNumber(n)) return false;
    double v = n->valuedouble;
    if (!(v >= (double)lo && v <= 4294967295.0)) return false;
    uint32_t u = (uint32_t)v;
    if ((double)u != v) return false;
    *out = u;
    return true;
}

// One tier entry; false if malformed.
static bool parse_tier(const cJSON *t, dialog_manifest_set_t *out)
{
    if (!cJSON_IsObject(t)) return false;
    const cJSON *path  = cJSON_GetObjectItemCaseSensitive(t, "path");
    const cJSON *sha   = cJSON_GetObjectItemCaseSensitive(t, "sha256");
    const cJSON *bytes = cJSON_GetObjectItemCaseSensitive(t, "bytes");
    if (!cJSON_IsString(path) || !audio_manifest_path_ok(path->valuestring)) return false;
    if (!cJSON_IsString(sha) || !audio_manifest_sha_ok(sha->valuestring)) return false;
    if (!get_u32(bytes, DIALOG_HEADER_MIN + 1, &out->bytes)) return false;
    out->path   = path->valuestring;
    out->sha256 = sha->valuestring;
    return true;
}

// The largest tier of `dialog` within `budget`. False if the list is
// malformed (then the whole locale is) or no tier fits.
static bool pick_tier(const cJSON *dialog, uint64_t budget,
                      dialog_manifest_set_t *out)
{
    if (!cJSON_IsArray(dialog) || cJSON_GetArraySize(dialog) < 1) return false;
    bool found = false;
    const cJSON *t;
    cJSON_ArrayForEach(t, dialog) {
        dialog_manifest_set_t tier;
        if (!parse_tier(t, &tier)) return false;
        if (tier.bytes > budget) continue;
        if (!found || tier.bytes > out->bytes) {
            out->path   = tier.path;
            out->sha256 = tier.sha256;
            out->bytes  = tier.bytes;
            found = true;
        }
    }
    return found;
}

bool dialog_manifest_select(const cJSON *assets, const char *const *chain,
                            int chain_n, uint64_t budget,
                            dialog_manifest_set_t *out)
{
    memset(out, 0, sizeof(*out));
    if (!cJSON_IsObject(assets)) return false;
    for (int i = 0; i < chain_n; i++) {
        // The locale names the stored file (dialog_store.h).
        if (!dialog_lang_valid(chain[i])) continue;
        const cJSON *locale = cJSON_GetObjectItemCaseSensitive(assets, chain[i]);
        const cJSON *dialog = cJSON_IsObject(locale)
            ? cJSON_GetObjectItemCaseSensitive(locale, "dialog") : NULL;
        if (pick_tier(dialog, budget, out)) {
            out->lang = chain[i];
            return true;
        }
        memset(out, 0, sizeof(*out));   // never a partial pick from this locale
    }
    return false;
}
