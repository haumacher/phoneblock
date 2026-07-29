#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// Downloads the localized device assets — the answer-bot announcement audio
// and (later) the status-mail string pack — for the active ui_lang from the
// CDN, so the firmware carries no per-language payload (issue #460).
//
// Assets are co-located with the firmware release on the CDN, under
//   <CONFIG_PHONEBLOCK_OTA_BASE_URL>/<this-firmware-version>/i18n/
// so each release carries its own i18n bundle next to its .bin and an older
// release in the field is never affected by a newer one's key changes.
// Source of truth is the manifest at
//   <…>/i18n/manifest.json
// It is NOT signed — unlike the OTA manifest, which is (manifest_sig.c). The
// assets are display strings and announcement audio: transport trust is
// HTTPS + the cert bundle, and a hostile CDN would at worst show the user
// wrong text. That is a deliberate trade for a workable development loop —
// publishing a bundle for an unreleased build must not need the release key.
// Consequence to keep in mind when consuming these strings: treat them as
// untrusted input, i.e. never interpolate a pack string into HTML unescaped
// (see the browser side in main/web/index.html).
//
// The manifest maps each locale to its assets and their SHA-256:
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
// the manifest (integrity against a truncated or corrupted transfer — the
// manifest itself is only as trustworthy as the CDN), then renamed into place.
// Assets for other locales are pruned so the shared 640 KB storage is not
// filled by stale downloads. A locale whose SHA already matches the on-disk
// file is skipped (no re-download).
//
// Runs on the scheduler task (never on the httpd thread): daily, ~shortly
// after boot, and on demand when the user switches the UI language.

void i18n_sync_init(void);

// Perform one sync pass for the active ui_lang. Safe to call only from the
// scheduler task (does blocking HTTPS + SPIFFS I/O). A no-op with a clear
// status if offline / the manifest is missing or unparseable.
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

// Path of the downloaded web-UI string pack for `lang` on SPIFFS. This module
// owns the layout; web.c serves the file at /api/i18n/ui.
void i18n_sync_ui_path(char *out, size_t cap, const char *lang);

// Whether a downloaded UI pack for `lang` is present. False means
// /api/i18n/ui falls back to the embedded English pack — which the web UI
// needs to distinguish from "still downloading", so it can say
// "translation unavailable" straight away instead of polling for a minute.
bool i18n_sync_have_ui_pack(const char *lang);
