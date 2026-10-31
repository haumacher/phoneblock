#pragma once

#include <stdbool.h>

#include "cJSON.h"
#include "dialog.h"

// Selection of the dialog clip set from the i18n manifest (issue #429).
//
// The manifest (i18n_sync.h) carries, additively, a `dialog` asset per
// locale: ONE file holding every clip of the set, published like the other
// assets, plus `phases`, the clip table — per phase, in clip order, the
// [offset, length] of each clip within that file:
//
//   "assets": { "de": { ...,
//       "dialog": { "path": "audio/dialog-de.alaw", "sha256": "<hex>",
//                   "bytes": 63903,
//                   "phases": { "hello":         [[0, 7431]],
//                               "who-is-there":  [[7431, 7802]],
//                               "am-aparat":     [...],
//                               "question":      [[...], [...], [...]],
//                               "cant-hear-you": [...] } } } }
//
// Firmware without dialog support ignores the key. A set is taken as a whole
// from the first locale of the fallback chain (ui_lang → en → de) whose set
// is complete — the asset well-formed and all five phases present with
// 1..DIALOG_MAX_CLIPS clips each, every clip non-empty and inside `bytes` —
// never mixed across locales. A locale with a missing phase or a malformed
// entry is skipped entirely. Phases and fields this firmware does not know
// are ignored, so a later bundle can add them.
//
// Pure (cJSON only), host-tested in test/test_dialog.c.

typedef struct {
    const char  *lang;     // chain entry the set came from
    const char  *path;     // the file, relative to the manifest's i18n directory
    const char  *sha256;   // 64 hex digits
    uint32_t     bytes;    // file size, > 0
    dialog_set_t set;      // the clip table; set.lang == lang
} dialog_manifest_set_t;

// Looks for a complete set in `assets` (the manifest's "assets" object) along
// `chain` (chain_n locale codes, most preferred first). Fills *out and returns
// true for the first complete one; false if none of the locales has one. The
// strings point into `assets` and live as long as it does.
bool dialog_manifest_select(const cJSON *assets, const char *const *chain,
                            int chain_n, dialog_manifest_set_t *out);
