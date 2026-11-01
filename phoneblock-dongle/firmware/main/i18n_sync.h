#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// Downloads the localized device assets — the web-UI and status-mail string
// packs, the answer-bot announcement audio and the dialog clip set — for the
// active ui_lang from the CDN, so the firmware carries no per-language
// payload (issue #460). Two sources:
//
//  * The firmware bundle, co-located with the release on the CDN, under
//      <CONFIG_PHONEBLOCK_OTA_BASE_URL>/<this-firmware-version>/i18n/
//    with the UI and mail packs, whose keys belong to this firmware's code,
//    so an older release in the field is never affected by a newer one's
//    key changes (scripts/i18n-assets.sh).
//  * The audio channel, versioned by the audio format alone
//      <CONFIG_PHONEBLOCK_OTA_BASE_URL>/audio/v<AUDIO_FORMAT_VERSION>/
//    with the announcements and the dialog clip sets, republished in place
//    whenever the audio changes (audio_manifest.h, scripts/audio-assets.sh).
//
// Neither manifest is signed — unlike the OTA manifest, which is
// (manifest_sig.c). The assets are display strings and audio: transport
// trust is HTTPS + the cert bundle, and a hostile CDN would at worst show the
// user wrong text. That is a deliberate trade for a workable development
// loop — publishing a bundle for an unreleased build must not need the
// release key. Consequence to keep in mind when consuming these strings:
// treat them as untrusted input, i.e. never interpolate a pack string into
// HTML unescaped (see the browser side in main/web/index.html).
//
// The bundle's manifest maps each locale to its packs and their SHA-256:
//
//   { "version": "1.7.0",
//     "assets": {
//       "en": { "ui":   {"path":"ui/lang-en.json", "sha256":"<hex>", "bytes": 24310},
//               "mail": {"path":"mail/mail-en.json", "sha256":"<hex>", "bytes": 1234} },
//       ... } }
//
// The audio channel's manifest has the same shape with the announcement and
// the dialog's speaker index per locale (audio_manifest.h, dialog_manifest.h).
//
// Exactly ONE announcement and ONE mail pack are kept on the device, for the
// active ui_lang. Which locale's announcement fills it is chosen at download
// time by a fallback chain — ui_lang → en → de — so a locale with no
// recording still gets usable audio (e.g. German) instead of nothing; the
// packs take only ui_lang (their fallback is the embedded English). The
// chosen content is stored under the ui_lang name:
//   announcement → /spiffs/announcement-<lang>.alaw  (announcement.c reads it)
//   mail pack    → /spiffs/mail-<lang>.json          (mail_i18n.c reads it)
// Each asset is streamed to a temp file on the storage partition, its SHA-256 checked against
// the manifest (integrity against a truncated or corrupted transfer — the
// manifest itself is only as trustworthy as the CDN), then renamed into place.
// Assets for other locales are pruned so the shared storage is not filled by
// stale downloads. The dialog clip set of the interactive answer bot (#429)
// is kept only while ab_mode is "dialog": the speaker index of the first
// chain locale that has one is kept (re-downloaded only when its SHA
// changes); the configured dialog_speaker is used while the index lists it,
// otherwise one is picked at random and persisted (INFO); of it the largest
// tier within the budget is stored under that locale's name (dialog_store.h).
// An own set (POST /api/dialog) takes precedence: while one is on the device,
// no CDN set is fetched. The set takes the place of the localized announcement, which
// is then neither downloaded nor kept (and of the own recording, which the
// switch to dialog mode deletes, web.c). A file whose SHA already matches the
// manifest is skipped (no re-download).
//
// Runs on the scheduler task (never on the httpd thread): shortly after boot,
// retried with backoff until a pass succeeds, then re-checked once a day
// (scheduler.c) — the audio channel changes without a firmware update; with
// nothing changed a pass costs the two manifest GETs. And on demand when the
// user switches the UI language or the answer-bot mode.

void i18n_sync_init(void);

// Perform one sync pass for the active ui_lang. Safe to call only from the
// scheduler task (does blocking HTTPS + flash I/O). A no-op with a clear
// status if offline / the manifest is missing or unparseable. Returns
// whether the pass succeeded (same as the snapshot's last_ok). A firmware
// whose bundle is not published (its manifest answers 404, e.g. a dev
// build, #673) is no failure: the packs are skipped with one WARN per boot,
// the audio is synced, and the pass counts as done.
bool i18n_sync_run(void);

// Ask the scheduler task to run i18n_sync_run() on its own stack. Returns
// false if the scheduler is not up yet or a pass is already running — in the
// latter case one more pass follows the running one, since what the caller
// changed may postdate what that pass looked at.
bool i18n_sync_trigger_now(void);

// Where the dialog clip set stands (#429), so the web UI can tell "being
// downloaded" from "download failed" from "nothing published" — all three
// look the same from the set alone (no set on the device).
typedef enum {
    I18N_DIALOG_IDLE = 0,     // not in dialog mode, or no pass looked yet
    I18N_DIALOG_PENDING,      // a pass is due (trigger, speaker or lang change)
    I18N_DIALOG_DOWNLOADING,  // the set (dialog_bytes) is being downloaded
    I18N_DIALOG_FAILED,       // the last attempt failed: dialog_error
    I18N_DIALOG_UNAVAILABLE,  // the audio channel has no set for this device
    I18N_DIALOG_OK,           // the set is in place (or the own set is)
} i18n_dialog_state_t;

// The JSON name of `state` ("idle", "pending", ...), for /api/status.
const char *i18n_dialog_state_name(i18n_dialog_state_t state);

typedef struct {
    bool  ever_ran;
    bool  last_ok;
    bool  running;
    int64_t last_at_us;      // esp_timer time of the last completed pass
    char  lang[12];          // locale the last pass synced
    char  last_error[64];    // empty when last_ok
    i18n_dialog_state_t dialog_state;
    uint32_t dialog_bytes;   // size of the set being downloaded
    char  dialog_error[64];  // reason of I18N_DIALOG_FAILED / _UNAVAILABLE
} i18n_sync_status_t;

void i18n_sync_snapshot(i18n_sync_status_t *out);

// Path of the downloaded web-UI string pack for `lang` on the storage partition. This module
// owns the layout; web.c serves the file at /api/i18n/ui. Size `out` with
// PB_DATA_PATH_CAP (platform.h); false (and `out` not to be used) if the path
// does not fit — a Linux data directory too long for it.
bool i18n_sync_ui_path(char *out, size_t cap, const char *lang);

// Same for the status-mail string pack (mail_i18n.c reads it). Both are
// exposed so the dev upload route in web.c can write a bundle without
// duplicating the on-device layout.
bool i18n_sync_mail_path(char *out, size_t cap, const char *lang);

// Whether a downloaded UI pack for `lang` is present. False means
// /api/i18n/ui falls back to the embedded English pack — which the web UI
// needs to distinguish from "still downloading", so it can say
// "translation unavailable" straight away instead of polling for a minute.
bool i18n_sync_have_ui_pack(const char *lang);
