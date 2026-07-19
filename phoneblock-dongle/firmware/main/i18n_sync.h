#pragma once

#include <stdbool.h>
#include <stdint.h>

// Downloads the localized device assets — the answer-bot announcement audio
// and (later) the status-mail string pack — for the active ui_lang from the
// CDN, so the firmware carries no per-language payload (issue #460).
//
// Source of truth is a signed manifest at
//   <CONFIG_PHONEBLOCK_I18N_BASE_URL>/manifest.json
// with a detached signature at .../manifest.json.sig (ECDSA-P256 over
// "phoneblock-dongle-i18n-v1\n" + the manifest bytes; same release key as
// OTA, verified via manifest_sig.c). The manifest maps each locale to its
// assets and their SHA-256:
//
//   { "version": "1",
//     "assets": {
//       "en": { "announcement": {"path":"audio/announcement-en.alaw",
//                                "sha256":"<hex>", "bytes": 78762},
//               "mail":         {"path":"mail/mail-en.json",
//                                "sha256":"<hex>", "bytes": 1234} },
//       "de": { ... }, ... } }
//
// Each asset is streamed to a SPIFFS temp file, its SHA-256 checked against
// the (signature-authenticated) manifest, then renamed into place:
//   announcement → /spiffs/announcement-<lang>.alaw  (announcement.c reads it)
//   mail pack    → /spiffs/mail-<lang>.json          (mail.c reads it)
// Assets for other locales are pruned so the shared 640 KB storage partition
// is not filled by stale downloads. A locale whose SHA already matches the
// on-disk file is skipped (no re-download).
//
// Runs on the scheduler task (never on the httpd thread): daily, ~shortly
// after boot, and on demand when the user switches the UI language.

void i18n_sync_init(void);

// Perform one sync pass for the active ui_lang. Safe to call only from the
// scheduler task (does blocking HTTPS + SPIFFS I/O). A no-op with a clear
// status if offline / the manifest is missing or fails signature check.
void i18n_sync_run(void);

// Ask the scheduler task to run i18n_sync_run() on its own stack. Returns
// false if the scheduler is not up yet or a pass is already running.
bool i18n_sync_trigger_now(void);

typedef struct {
    bool  ever_ran;
    bool  last_ok;
    bool  running;
    int64_t last_at_us;      // esp_timer time of the last completed pass
    char  lang[12];          // locale the last pass synced
    char  last_error[64];    // empty when last_ok
} i18n_sync_status_t;

void i18n_sync_snapshot(i18n_sync_status_t *out);
