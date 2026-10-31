// Implementation of dialog_manifest.h.

#include "dialog_manifest.h"

#include <stdio.h>
#include <string.h>

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static bool hex64(const char *s)
{
    if (strlen(s) != 64) return false;
    for (int i = 0; i < 64; i++) {
        char c = s[i];
        bool ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') ||
                  (c >= 'A' && c <= 'F');
        if (!ok) return false;
    }
    return true;
}

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

// The clip list of one phase; false if missing, empty, too long or with a
// malformed entry. Bounds against the file are checked by the caller.
static bool parse_phase(const cJSON *list, uint8_t *count,
                        dialog_clip_t clips[DIALOG_MAX_CLIPS])
{
    if (!cJSON_IsArray(list)) return false;
    int n = cJSON_GetArraySize(list);
    if (n < 1 || n > DIALOG_MAX_CLIPS) return false;
    for (int i = 0; i < n; i++) {
        const cJSON *e = cJSON_GetArrayItem(list, i);
        // [offset, length]; further elements are left for later bundles.
        if (!cJSON_IsArray(e) || cJSON_GetArraySize(e) < 2) return false;
        if (!get_u32(cJSON_GetArrayItem(e, 0), 0, &clips[i].off)) return false;
        if (!get_u32(cJSON_GetArrayItem(e, 1), 1, &clips[i].len)) return false;
    }
    *count = (uint8_t)n;
    return true;
}

static bool parse_set(const cJSON *dialog, const char *lang,
                      dialog_manifest_set_t *out)
{
    if (!cJSON_IsObject(dialog)) return false;
    const cJSON *path   = cJSON_GetObjectItemCaseSensitive(dialog, "path");
    const cJSON *sha    = cJSON_GetObjectItemCaseSensitive(dialog, "sha256");
    const cJSON *bytes  = cJSON_GetObjectItemCaseSensitive(dialog, "bytes");
    const cJSON *phases = cJSON_GetObjectItemCaseSensitive(dialog, "phases");
    if (!cJSON_IsString(path) || !path->valuestring[0]) return false;
    if (!cJSON_IsString(sha) || !hex64(sha->valuestring)) return false;
    if (!get_u32(bytes, 1, &out->bytes)) return false;
    if (!cJSON_IsObject(phases)) return false;
    if (strlen(lang) >= sizeof(out->set.lang)) return false;

    for (int p = 0; p < DIALOG_PHASE_COUNT; p++) {
        const cJSON *list = cJSON_GetObjectItemCaseSensitive(
            phases, dialog_phase_name((dialog_phase_t)p));
        if (!parse_phase(list, &out->set.count[p], out->set.clip[p])) return false;
    }
    if (!dialog_set_within(&out->set, out->bytes)) return false;

    out->lang   = lang;
    out->path   = path->valuestring;
    out->sha256 = sha->valuestring;
    snprintf(out->set.lang, sizeof(out->set.lang), "%s", lang);
    return true;
}

bool dialog_manifest_select(const cJSON *assets, const char *const *chain,
                            int chain_n, dialog_manifest_set_t *out)
{
    memset(out, 0, sizeof(*out));
    if (!cJSON_IsObject(assets)) return false;
    for (int i = 0; i < chain_n; i++) {
        const cJSON *locale = cJSON_GetObjectItemCaseSensitive(assets, chain[i]);
        const cJSON *dialog = cJSON_IsObject(locale)
            ? cJSON_GetObjectItemCaseSensitive(locale, "dialog") : NULL;
        if (parse_set(dialog, chain[i], out)) return true;
        memset(out, 0, sizeof(*out));   // never a partial set from this locale
    }
    return false;
}
