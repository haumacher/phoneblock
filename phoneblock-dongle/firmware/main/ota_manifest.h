#pragma once

#include "cJSON.h"

// Locating this chip's build in an OTA manifest. Pure cJSON, no ESP-IDF —
// host-tested in test/test_ota_manifest.c.
//
// Two files have this shape. The dongle polls the per-chip
// <channel>/ota-<target>.json (scripts/ota-manifest.sh), which holds just
// its own build with its own integrity block. As fallback it reads the
// browser installer's <channel>/manifest.json, which carries one build per
// chip family:
//
//   {"version": "1.7.0",
//    "builds": [
//      {"chipFamily": "ESP32",    "parts": [... ".../phoneblock_dongle.bin" ...]},
//      {"chipFamily": "ESP32-C3", "parts": [... ".../esp32c3/phoneblock_dongle.bin" ...],
//       "integrity": {"app_sha256": "...", "signature": "..."}}],
//    "integrity": {"app_sha256": "...", "signature": "..."}}
//
// The ESP32 build must stay at builds[0] and is covered by the top-level
// integrity block: firmware before C3 support takes builds[0] and the
// top-level hash/signature unconditionally. Any further build carries its
// own integrity block.

typedef enum {
    OTA_PICK_OK = 0,
    OTA_PICK_ERR_PARSE,      // no build for this chip / no app part
    OTA_PICK_ERR_INTEGRITY,  // hash or signature missing / malformed
} ota_pick_result_t;

typedef struct {
    // All pointers borrow from the cJSON tree passed to ota_manifest_pick().
    const char *app_url;     // part path ending in "/phoneblock_dongle.bin"
    const char *app_sha256;  // 64 hex chars (length checked, digits not)
    const char *signature;   // base64, undecoded
    const char *error;       // static message, set on failure
} ota_build_t;

// Picks the build whose chipFamily equals `chip_family`, its app binary URL
// and the integrity block that covers it (the build's own, else the
// top-level one).
ota_pick_result_t ota_manifest_pick(const cJSON *root, const char *chip_family,
                                    ota_build_t *out);
