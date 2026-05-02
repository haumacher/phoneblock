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
// unless `force` is set, which both ignores and clears the marker.
//
// `firmware_update_start` spawns a background task that runs
// `firmware_try_update(false, ...)` every ~24 h with ±30 min jitter,
// modelled on selftest.c. The task starts unconditionally; it skips
// runs while no PhoneBlock token is configured (which also acts as a
// proxy for "device is provisioned and the user wants automation").

typedef enum {
    FW_UPDATE_NO_NEW,           // manifest version matches running image
    FW_UPDATE_INSTALLED,        // download + flash succeeded; reboot scheduled
    FW_UPDATE_SKIPPED_FAILED,   // manifest matches last_failed_ota (and !force)
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

// Synchronous OTA check + (if applicable) install. On
// FW_UPDATE_INSTALLED the function schedules a reboot before
// returning; the caller has ~500 ms to flush an HTTP response.
// `force=true` is the manual-override path: ignores the
// last_failed_ota guard and clears it.
void firmware_try_update(bool force, fw_update_outcome_t *out);

// Spawns the background auto-update task (idempotent).
void firmware_update_start(void);

// Schedules an esp_restart() ~500 ms in the future on a small task
// so an in-flight HTTP response can drain first. Exposed so the
// manual file-upload path in web.c uses the same delay.
void firmware_schedule_reboot(void);
