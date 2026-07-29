#pragma once

#include <stdbool.h>
#include <stdint.h>

// Downloads the localized device assets — the answer-bot announcement audio
// and (later) the status-mail string pack — for the active ui_lang from the
// CDN, so the firmware carries no per-language payload (issue #460).
//
// Assets are co-located with the firmware release on the CDN, under
//   <CONFIG_PHONEBLOCK_OTA_BASE_URL>/<this-firmware-version>/i18n/
// so each release carries its own i18n bundle next to its .bin and an older
// release in the field is never affected by a newer one's key changes.
// Source of truth is a signed manifest at
//   <…>/i18n/manifest.json
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
// Exactly ONE announcement and ONE mail pack are kept on the device, for the
// active ui_lang. Which locale's content fills them is chosen at download time
// by a fallback chain — ui_lang → en → de — so a locale with no recording /
// no translation still gets a usable asset (e.g. German audio) instead of
// nothing. The chosen content is stored under the ui_lang name:
//   announcement → /spiffs/announcement-<lang>.alaw  (announcement.c reads it)
//   mail pack    → /spiffs/mail-<lang>.json          (mail_i18n.c reads it)
// Each asset is streamed to a SPIFFS temp file, its SHA-256 checked against
// the (signature-authenticated) manifest, then renamed into place. Assets for
// other locales are pruned so the shared 640 KB storage partition is not
// filled by stale downloads. A locale whose SHA already matches the on-disk
// file is skipped (no re-download).
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
