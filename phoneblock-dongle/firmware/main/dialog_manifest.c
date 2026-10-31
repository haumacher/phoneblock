// Implementation of dialog_manifest.h.

#include "dialog_manifest.h"

#include <string.h>

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

// A JSON number that is a whole uint32 in lo..hi.
static bool get_u32(const cJSON *n, uint32_t lo, uint32_t hi, uint32_t *out)
{
    if (!cJSON_IsNumber(n)) return false;
    double v = n->valuedouble;
    if (!(v >= (double)lo && v <= (double)hi)) return false;
    uint32_t u = (uint32_t)v;
    if ((double)u != v) return false;
    *out = u;
    return true;
}

// {path, sha256, bytes in lo..hi}; false if malformed.
static bool parse_file(const cJSON *t, uint32_t lo, uint32_t hi,
                       const char **path_out, const char **sha_out,
                       uint32_t *bytes_out)
{
    if (!cJSON_IsObject(t)) return false;
    const cJSON *path  = cJSON_GetObjectItemCaseSensitive(t, "path");
    const cJSON *sha   = cJSON_GetObjectItemCaseSensitive(t, "sha256");
    const cJSON *bytes = cJSON_GetObjectItemCaseSensitive(t, "bytes");
    if (!cJSON_IsString(path) || !audio_manifest_path_ok(path->valuestring)) return false;
    if (!cJSON_IsString(sha) || !audio_manifest_sha_ok(sha->valuestring)) return false;
    if (!get_u32(bytes, lo, hi, bytes_out)) return false;
    *path_out = path->valuestring;
    *sha_out  = sha->valuestring;
    return true;
}

bool dialog_manifest_index(const cJSON *assets, const char *const *chain,
                           int chain_n, audio_asset_t *out)
{
    memset(out, 0, sizeof(*out));
    if (!cJSON_IsObject(assets)) return false;
    for (int i = 0; i < chain_n; i++) {
        // The locale names the stored file (dialog_store.h); the own set's
        // pseudo-locale is never a CDN one.
        if (!dialog_lang_valid(chain[i]) || strcmp(chain[i], DIALOG_OWN_LANG) == 0) continue;
        const cJSON *locale = cJSON_GetObjectItemCaseSensitive(assets, chain[i]);
        const cJSON *dialog = cJSON_IsObject(locale)
            ? cJSON_GetObjectItemCaseSensitive(locale, "dialog") : NULL;
        if (parse_file(dialog, 1, DIALOG_INDEX_MAX_BYTES, &out->path, &out->sha256,
                       &out->bytes)) {
            out->lang = chain[i];
            return true;
        }
        memset(out, 0, sizeof(*out));
    }
    return false;
}

// Whether every tier of `sets` is well-formed (and there is one at all).
static bool sets_ok(const cJSON *sets)
{
    if (!cJSON_IsArray(sets) || cJSON_GetArraySize(sets) < 1) return false;
    const cJSON *t;
    cJSON_ArrayForEach(t, sets) {
        const char *p, *s;
        uint32_t b;
        if (!parse_file(t, DIALOG_HEADER_MIN + 1, UINT32_MAX, &p, &s, &b)) return false;
    }
    return true;
}

// The speaker's id if `sp` is well-formed, else NULL (duplicates not checked).
static const char *speaker_id(const cJSON *sp)
{
    if (!cJSON_IsObject(sp)) return NULL;
    const cJSON *id = cJSON_GetObjectItemCaseSensitive(sp, "id");
    if (!cJSON_IsString(id) || !dialog_speaker_valid(id->valuestring)) return NULL;
    if (!sets_ok(cJSON_GetObjectItemCaseSensitive(sp, "sets"))) return NULL;
    return id->valuestring;
}

// The `n`-th well-formed speaker object of `index`, NULL past the last. A
// later entry with the id of an earlier well-formed one does not count.
static const cJSON *nth_speaker(const cJSON *index, int n)
{
    if (!cJSON_IsObject(index) || n < 0) return NULL;
    const cJSON *list = cJSON_GetObjectItemCaseSensitive(index, "speakers");
    if (!cJSON_IsArray(list)) return NULL;
    int seen = 0;
    const cJSON *sp;
    cJSON_ArrayForEach(sp, list) {
        const char *id = speaker_id(sp);
        if (!id) continue;
        bool dup = false;
        for (const cJSON *q = list->child; q && q != sp; q = q->next) {
            const char *qid = speaker_id(q);
            if (qid && strcmp(qid, id) == 0) { dup = true; break; }
        }
        if (dup) continue;
        if (seen++ == n) return sp;
    }
    return NULL;
}

static const char *str_field(const cJSON *o, const char *key)
{
    const cJSON *v = cJSON_GetObjectItemCaseSensitive(o, key);
    return cJSON_IsString(v) ? v->valuestring : NULL;
}

bool dialog_index_speaker(const cJSON *index, int n, dialog_speaker_t *out)
{
    memset(out, 0, sizeof(*out));
    const cJSON *sp = nth_speaker(index, n);
    if (!sp) return false;
    out->id = str_field(sp, "id");
    const char *name = str_field(sp, "name");
    out->name = name && name[0] ? name : out->id;
    const char *g = str_field(sp, "gender");
    out->gender = g ? g : "";
    const char *pv = str_field(sp, "preview");
    out->preview = pv && audio_manifest_path_ok(pv) ? pv : "";
    return true;
}

// The largest tier of speaker object `sp` within `budget`; false if none.
static bool pick_tier(const cJSON *sp, uint64_t budget, dialog_manifest_set_t *out)
{
    bool found = false;
    const cJSON *t;
    cJSON_ArrayForEach(t, cJSON_GetObjectItemCaseSensitive(sp, "sets")) {
        dialog_manifest_set_t tier = { 0 };
        if (!parse_file(t, DIALOG_HEADER_MIN + 1, UINT32_MAX, &tier.path, &tier.sha256,
                        &tier.bytes)) {
            return false;   // sets_ok() vouched for it; never reached
        }
        if (tier.bytes > budget) continue;
        if (!found || tier.bytes > out->bytes) {
            out->path   = tier.path;
            out->sha256 = tier.sha256;
            out->bytes  = tier.bytes;
            found = true;
        }
    }
    if (found) out->speaker = str_field(sp, "id");
    return found;
}

dialog_pick_t dialog_index_select(const cJSON *index, const char *speaker,
                                  uint64_t budget, dialog_manifest_set_t *out)
{
    memset(out, 0, sizeof(*out));
    const cJSON *def = nth_speaker(index, 0);
    if (!def) return DIALOG_PICK_NONE;
    dialog_pick_t why = DIALOG_PICK_CHOSEN;
    if (speaker && speaker[0] && strcmp(speaker, str_field(def, "id")) != 0) {
        const cJSON *chosen = NULL;
        for (int i = 1; (chosen = nth_speaker(index, i)) != NULL; i++) {
            if (strcmp(str_field(chosen, "id"), speaker) == 0) break;
        }
        if (chosen && pick_tier(chosen, budget, out)) return DIALOG_PICK_CHOSEN;
        why = chosen ? DIALOG_PICK_NO_FIT : DIALOG_PICK_ABSENT;
        memset(out, 0, sizeof(*out));
    }
    if (pick_tier(def, budget, out)) return why;
    memset(out, 0, sizeof(*out));
    return DIALOG_PICK_NONE;
}

static bool has_speaker(const cJSON *index, const char *id)
{
    const cJSON *sp;
    for (int i = 0; (sp = nth_speaker(index, i)) != NULL; i++) {
        if (strcmp(str_field(sp, "id"), id) == 0) return true;
    }
    return false;
}

const char *dialog_index_random_speaker(const cJSON *index, uint32_t r)
{
    uint32_t n = 0;
    while (nth_speaker(index, (int)n)) n++;
    if (n == 0) return NULL;
    return str_field(nth_speaker(index, (int)(r % n)), "id");
}

const char *dialog_index_resolve_speaker(const cJSON *index, const char *speaker,
                                         uint32_t r, bool *repicked)
{
    *repicked = false;
    if (speaker && speaker[0] && has_speaker(index, speaker)) return speaker;
    const char *pick = dialog_index_random_speaker(index, r);
    if (pick) *repicked = true;
    return pick;
}
