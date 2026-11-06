#pragma once

#include <stdbool.h>

// Auto-update infrastructure.
//
// `firmware_try_update` fetches the JSON manifest from the configured
// CDN URL, compares its "version" field to the running image, and (if
// newer) downloads + flashes the binary referenced by "url". The newly
// flashed slot comes up in PENDING_VERIFY; main.c's
// esp_ota_mark_app_valid_cancel_rollback() is the commit point.
//
// To break the "download → brick → rollback → retry-same-bits" loop,
// the target version is written to NVS as `last_failed_ota` *before*
// esp_https_ota() runs. main.c clears that marker once the running
// image matches the stored version (i.e. the new build booted far
// enough to confirm itself). If the auto-update task wakes up and
// sees the manifest still points at that same version, it skips —
// unless FW_UPDATE_RETRY_FAILED is set, which ignores and overwrites
// the marker.
//
// A manifest *older* than the running image (a CDN rollback, or the
// stable channel after running a beta, #707) is never installed
// unless FW_UPDATE_ALLOW_DOWNGRADE is set: the user confirmed it.
//
// `firmware_update_run` performs one scheduled `firmware_try_update(
// 0, ...)` pass and is invoked once every ~24 h by the shared
// scheduler task (see scheduler.c). It skips while no PhoneBlock token
// is configured (a proxy for "device is provisioned and the user wants
// automation") or while the user has frozen the build via the
// auto-update toggle.

typedef enum {
    FW_UPDATE_NO_NEW,           // manifest version matches running image
    FW_UPDATE_OLDER_AVAILABLE,  // manifest verified but older than the running
                                // image; installed only with ALLOW_DOWNGRADE
    FW_UPDATE_NEW_AVAILABLE,    // manifest verified; new version ready to install
                                // (only returned by firmware_check_manifest)
    FW_UPDATE_INSTALLED,        // download + flash succeeded; reboot scheduled
    FW_UPDATE_SKIPPED_FAILED,   // manifest matches last_failed_ota (no RETRY_FAILED)
    FW_UPDATE_ERR_NETWORK,      // could not fetch manifest
    FW_UPDATE_ERR_PARSE,        // manifest invalid / missing fields
    FW_UPDATE_ERR_SIGNATURE,    // manifest signature / app-binary hash invalid
    FW_UPDATE_ERR_OTA,          // esp_https_ota failed
} fw_update_result_t;

typedef struct {
    fw_update_result_t result;
    char current_version[32];
    char new_version[32];        // empty if manifest fetch/parse failed
    char error[64];              // human-readable detail for ERR_*
} fw_update_outcome_t;

// Flags for firmware_check_manifest() / firmware_try_update().
#define FW_UPDATE_RETRY_FAILED    0x1u  // try a version that was rolled back
#define FW_UPDATE_ALLOW_DOWNGRADE 0x2u  // install an older version

// Read-only manifest check: fetches the JSON, verifies the
// signature, picks the app URL, and compares versions. Does not
// touch any persistent state and does not write flash. Fast (~1-2s
// over HTTPS). Returns FW_UPDATE_NEW_AVAILABLE / NO_NEW /
// OLDER_AVAILABLE / SKIPPED_FAILED / ERR_*. The web UI's "Auf Aktualisierung prüfen"
// button calls this first to show the user what was found before
// committing to the ~30 s download.
void firmware_check_manifest(unsigned flags, fw_update_outcome_t *out);

// Synchronous OTA check + (if applicable) install. On
// FW_UPDATE_INSTALLED the function schedules a reboot before
// returning; the caller has ~500 ms to flush an HTTP response.
// The web UI passes FW_UPDATE_RETRY_FAILED (the user pressed the
// button, so a rolled-back version is tried again) and adds
// FW_UPDATE_ALLOW_DOWNGRADE only after the user confirmed one. Re-fetches the manifest, so
// callers do not have to keep state between check and install.
void firmware_try_update(unsigned flags, fw_update_outcome_t *out);

// One scheduled auto-update pass: token + auto-update gates, then a
// firmware_try_update() without flags. Called by the scheduler.
void firmware_update_run(void);

// Schedules an esp_restart() ~500 ms in the future on a small task
// so an in-flight HTTP response can drain first. Exposed so the
// manual file-upload path in web.c uses the same delay.
void firmware_schedule_reboot(void);
