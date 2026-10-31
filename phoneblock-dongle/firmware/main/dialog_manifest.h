#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "audio_manifest.h"
#include "cJSON.h"
#include "dialog.h"

// Selection of the dialog clip set from the audio channel's manifest
// (audio_manifest.h, issue #429).
//
// The manifest carries a `dialog` asset per locale: the set in up to three
// size tiers (storage_budget.h), each ONE file that holds its clip table in a
// header (dialog.h) and the clips after it:
//
//   "assets": { "fr": { ...,
//       "dialog": [ { "path": "dialog-fr-s-1a2b3c4d.bin", "sha256": "<hex>",
//                     "bytes": 130581 },
//                   { "path": "dialog-fr-m-5e6f7a8b.bin", "sha256": "<hex>",
//                     "bytes": 228023 },
//                   { "path": "dialog-fr-l-9c0d1e2f.bin", "sha256": "<hex>",
//                     "bytes": 494301 } ] } }
// The device takes the
// largest tier whose `bytes` fit its dialog budget (storage_dialog_budget()),
// from the first locale of the fallback chain (ui_lang → en → de) that has
// one — a whole locale, never a mix. A locale whose list is malformed (not a
// list, empty, or any tier without a valid path (audio_manifest_path_ok()),
// a 64-digit SHA-256 or a size above DIALOG_HEADER_MIN) is skipped entirely; fields this firmware does
// not know are ignored, so a later bundle can add them. Whether the clip
// table inside is valid is the downloaded file's business
// (dialog_header_parse()).
//
// Pure (cJSON only), host-tested in test/test_dialog.c.

// The chosen tier; bytes > DIALOG_HEADER_MIN.
typedef audio_asset_t dialog_manifest_set_t;

// Looks for a set in `assets` (the manifest's "assets" object) along `chain`
// (chain_n locale codes, most preferred first): the largest tier of at most
// `budget` bytes of the first locale that has one. Fills *out and returns
// true; false if no locale has a fitting tier. The strings point into
// `assets` and live as long as it does.
bool dialog_manifest_select(const cJSON *assets, const char *const *chain,
                            int chain_n, uint64_t budget,
                            dialog_manifest_set_t *out);
