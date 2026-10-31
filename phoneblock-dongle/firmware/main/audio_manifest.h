#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "cJSON.h"

// The audio channel on the CDN (issue #429): the localized announcements and
// the dialog clip sets, published independently of the firmware version —
// unlike the UI and mail packs, whose keys belong to the code
// (i18n_sync.h). A firmware reads exactly one channel, the one of the audio
// format it understands:
//
//   <CONFIG_PHONEBLOCK_OTA_BASE_URL>/audio/v<AUDIO_FORMAT_VERSION>/manifest.json
//
// with the files next to it. scripts/audio-assets.sh publishes it. New
// content (sentences, voices, locales) is republished into the same channel,
// under content-addressed file names, and reaches the fleet by SHA-256 on the
// next daily check; AUDIO_FORMAT_VERSION is raised only for a change this
// firmware could not read (manifest schema, the dialog header layout,
// another codec) — see i18n/README.md.
//
//   { "format": 1,
//     "assets": {
//       "de": { "announcement": { "path": "announcement-de-1a2b3c4d.alaw",
//                                 "sha256": "<hex>", "bytes": 78762 },
//               "dialog": { "path": "dialog-de-9c0d1e2f.json", ... } },
//                           // the speaker index, dialog_manifest.h
//       ... } }
//
// Pure (cJSON only), host-tested in test/test_audio_manifest.c.

// The audio format this firmware reads.
#define AUDIO_FORMAT_VERSION 1

// One downloadable file of the channel.
typedef struct {
    const char *lang;     // chain entry it came from
    const char *path;     // relative to the channel directory
    const char *sha256;   // 64 hex digits
    uint32_t    bytes;    // file size, > 0
} audio_asset_t;

// Whether `path` is a file name the channel may use: relative, [A-Za-z0-9._-]
// and '/' between non-empty segments, no "." or ".." segment, at most 128
// characters. The device appends it to the channel's URL.
bool audio_manifest_path_ok(const char *path);

// Whether `s` is 64 hex digits.
bool audio_manifest_sha_ok(const char *s);

// The "assets" object of a parsed channel manifest, or NULL if `root` is not
// one of AUDIO_FORMAT_VERSION (no or another "format", no assets object).
const cJSON *audio_manifest_assets(const cJSON *root);

// The announcement of the first locale of `chain` (chain_n codes, most
// preferred first) that has a well-formed one ({path, sha256, bytes > 0},
// further fields ignored). False and *out zeroed if none has.
bool audio_manifest_announcement(const cJSON *assets, const char *const *chain,
                                 int chain_n, audio_asset_t *out);
