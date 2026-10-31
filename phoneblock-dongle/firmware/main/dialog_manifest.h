#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "audio_manifest.h"
#include "cJSON.h"
#include "dialog.h"

// Selection of the dialog clip set from the audio channel (audio_manifest.h,
// issue #429), in two steps.
//
// 1. The channel manifest names, per locale, ONE small index file:
//
//      "assets": { "fr": { ...,
//          "dialog": { "path": "dialog-fr-1a2b3c4d.json", "sha256": "<hex>",
//                      "bytes": 2210 } } }
//
//    The device takes the index of the first locale of the fallback chain
//    (ui_lang → en → de) that has a well-formed entry ({path, sha256, bytes
//    1..DIALOG_INDEX_MAX_BYTES}; DIALOG_OWN_LANG and invalid codes are never
//    looked up). dialog_manifest_index().
//
// 2. The index (downloaded, SHA-256 checked, kept on the device as
//    dialog-speakers.json, dialog_store.h) lists the locale's speakers, each
//    with the set in up to three size tiers (storage_budget.h), each ONE file
//    with its clip table in the header (dialog.h):
//
//      { "speakers": [
//          { "id": "leonie", "name": "Leonie", "gender": "female",
//            "preview": "tts/fr/leonie/hello-1.mp3",
//            "sets": [ { "path": "dialog-fr-leonie-s-5e6f7a8b.bin",
//                        "sha256": "<hex>", "bytes": 117995 }, ... ] },
//          { "id": "roger", ... } ] }
//
//    The first well-formed speaker is the locale's default. A speaker is
//    well-formed with a valid id (dialog_speaker_valid(), the first of
//    duplicate ids counts) and a non-empty `sets` list whose every tier has a
//    valid path (audio_manifest_path_ok()), a 64-digit SHA-256 and more than
//    DIALOG_HEADER_MIN bytes; any other speaker is skipped, the rest stay
//    usable. Fields this firmware does not know are ignored.
//    dialog_index_select().
//
// Whether the clip table inside a set is valid is the downloaded file's
// business (dialog_header_parse()).
//
// Pure (cJSON only), host-tested in test/test_dialog.c.

// Largest index file accepted (two speakers take ~1 KB).
#define DIALOG_INDEX_MAX_BYTES 8192

// The chosen tier and whose it is; bytes > DIALOG_HEADER_MIN.
typedef struct {
    const char *lang;      // the locale (step 1), set by the caller
    const char *speaker;   // id of the speaker the set belongs to
    const char *path;      // relative to the channel directory
    const char *sha256;
    uint32_t    bytes;
} dialog_manifest_set_t;

// Step 1: the index of the first locale of `chain` (chain_n codes, most
// preferred first) that has one. Fills *out (out->lang = that chain entry)
// and returns true; false and *out zeroed if none has. The strings point
// into `assets`.
bool dialog_manifest_index(const cJSON *assets, const char *const *chain,
                           int chain_n, audio_asset_t *out);

// One speaker of an index as the web UI lists it; strings point into the
// index. `name` falls back to the id, `gender` and `preview` are "" when
// missing or malformed (preview: not audio_manifest_path_ok()).
typedef struct {
    const char *id;
    const char *name;
    const char *gender;
    const char *preview;
} dialog_speaker_t;

// The `n`-th (0-based) well-formed speaker of `index`; false past the last.
bool dialog_index_speaker(const cJSON *index, int n, dialog_speaker_t *out);

typedef enum {
    DIALOG_PICK_NONE = 0,     // no set fits: neither the chosen nor the default
    DIALOG_PICK_CHOSEN,       // the chosen speaker's (or, none chosen, the default's)
    DIALOG_PICK_ABSENT,       // the chosen speaker is not in the index: the default's
    DIALOG_PICK_NO_FIT,       // no tier of the chosen speaker fits: the default's
} dialog_pick_t;

// Step 2: the largest tier of at most `budget` bytes of speaker `speaker`
// ("" or NULL: the default) in `index`. When the chosen speaker is missing
// or has no tier within the budget, the default speaker's largest fitting
// tier is taken instead (ABSENT / NO_FIT, for the caller's WARN); when that
// does not fit either, nothing (NONE, *out zeroed). out->lang is left NULL.
dialog_pick_t dialog_index_select(const cJSON *index, const char *speaker,
                                  uint64_t budget, dialog_manifest_set_t *out);

// A speaker of `index` drawn with the random value `r`: every well-formed
// speaker equally likely (the (r % n)-th of n), so a locale with more voices
// of one gender yields more picks of it. NULL if the index has no speaker.
const char *dialog_index_random_speaker(const cJSON *index, uint32_t r);

// Which speaker the device asks dialog_index_select() for, given the stored
// `speaker` ("" = none yet): the stored one while `index` has it; otherwise
// (empty, or not in this locale — e.g. a speaker removed from the CDN) a
// random pick (dialog_index_random_speaker() with `r`), which *repicked
// tells the caller to persist. NULL only for an index without any speaker.
const char *dialog_index_resolve_speaker(const cJSON *index, const char *speaker,
                                         uint32_t r, bool *repicked);
