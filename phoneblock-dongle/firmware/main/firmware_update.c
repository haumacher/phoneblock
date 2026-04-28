#include "firmware_update.h"

#include <stdio.h>
#include <string.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include "esp_app_desc.h"
#include "esp_crt_bundle.h"
#include "esp_http_client.h"
#include "esp_https_ota.h"
#include "esp_log.h"
#include "esp_random.h"
#include "esp_system.h"
#include "cJSON.h"

#include "sdkconfig.h"

#include "config.h"

static const char *TAG = "fwup";

// 24 h between scheduled checks — same cadence as the daily token
// self-test, with the same ±30 min skew so a fleet-wide power blip
// doesn't line every dongle up onto the same minute on the CDN.
#define FWUP_INTERVAL_MS    (24 * 3600 * 1000)
#define FWUP_JITTER_MS      (30 * 60 * 1000)

static TaskHandle_t s_task = NULL;

// --- helpers --------------------------------------------------------

static void copy_str(char *dst, size_t cap, const char *src)
{
    if (cap == 0) return;
    if (!src) { dst[0] = '\0'; return; }
    size_t n = strnlen(src, cap - 1);
    memcpy(dst, src, n);
    dst[n] = '\0';
}

// Lightweight semver-ish comparison for our own version strings
// (Project version from CMakeLists.txt, e.g. "1.5.3" or "1.5.3-rc1").
// Returns <0 / 0 / >0 like strcmp. Numeric major.minor.patch compare,
// then any pre-release suffix ("-rc1", "-dev", …) is treated as
// strictly less than the same release without the suffix — enough
// to keep "1.5.3-rc1" from displacing a freshly flashed "1.5.3".
// Two pre-release suffixes are treated as equal; we don't have rc
// orderings to worry about today.
static int semver_cmp(const char *a, const char *b)
{
    int aMaj = 0, aMin = 0, aPat = 0;
    int bMaj = 0, bMin = 0, bPat = 0;
    sscanf(a, "%d.%d.%d", &aMaj, &aMin, &aPat);
    sscanf(b, "%d.%d.%d", &bMaj, &bMin, &bPat);
    if (aMaj != bMaj) return aMaj < bMaj ? -1 : 1;
    if (aMin != bMin) return aMin < bMin ? -1 : 1;
    if (aPat != bPat) return aPat < bPat ? -1 : 1;
    int aPre = strchr(a, '-') ? 1 : 0;
    int bPre = strchr(b, '-') ? 1 : 0;
    if (aPre != bPre) return aPre ? -1 : 1;
    return 0;
}

static void reboot_task(void *arg)
{
    (void)arg;
    vTaskDelay(pdMS_TO_TICKS(500));
    ESP_LOGW(TAG, "firmware update complete — restarting");
    esp_restart();
}

void firmware_schedule_reboot(void)
{
    xTaskCreate(reboot_task, "fw_reboot", 2048, NULL, 5, NULL);
}

// Fetch the JSON manifest into the caller-provided buffer. Returns
// ESP_OK with a NUL-terminated body on success. On failure, writes a
// human-readable cause into `err` (DNS/TLS/HTTP-status/empty-body) so
// the web UI can show it instead of a generic "could not fetch".
static esp_err_t fetch_manifest(char *body, size_t cap,
                                char *err, size_t err_cap)
{
    const char *url = CONFIG_PHONEBLOCK_OTA_MANIFEST_URL;
    esp_http_client_config_t cfg = {
        .url = url,
        .crt_bundle_attach = esp_crt_bundle_attach,
        .timeout_ms = 10000,
    };
    esp_http_client_handle_t client = esp_http_client_init(&cfg);
    if (!client) {
        copy_str(err, err_cap, "esp_http_client_init failed");
        return ESP_FAIL;
    }
    esp_http_client_set_header(client, "Accept", "application/json");

    esp_err_t e = esp_http_client_open(client, 0);
    if (e != ESP_OK) {
        ESP_LOGE(TAG, "esp_http_client_open(%s): %s", url, esp_err_to_name(e));
        snprintf(err, err_cap, "Connect failed: %s", esp_err_to_name(e));
        esp_http_client_cleanup(client);
        return e;
    }
    int content_len = esp_http_client_fetch_headers(client);
    int status = esp_http_client_get_status_code(client);
    if (status != 200) {
        ESP_LOGE(TAG, "manifest HTTP %d (%s)", status, url);
        snprintf(err, err_cap, "HTTP %d from manifest URL", status);
        esp_http_client_close(client);
        esp_http_client_cleanup(client);
        return ESP_FAIL;
    }
    int total = 0;
    // Chunked responses return -1 from fetch_headers; read until the
    // server closes in that case.
    while (total < (int)cap - 1) {
        int n = esp_http_client_read(client, body + total, cap - 1 - total);
        if (n <= 0) break;
        total += n;
        if (content_len > 0 && total >= content_len) break;
    }
    body[total] = '\0';
    esp_http_client_close(client);
    esp_http_client_cleanup(client);
    if (total == 0) {
        copy_str(err, err_cap, "Manifest response was empty");
        return ESP_FAIL;
    }
    if (content_len > 0 && total < content_len) {
        snprintf(err, err_cap,
                 "Manifest truncated (%d of %d bytes)", total, content_len);
        return ESP_FAIL;
    }
    return ESP_OK;
}

// --- public API -----------------------------------------------------

void firmware_try_update(bool force, fw_update_outcome_t *out)
{
    fw_update_outcome_t scratch;
    if (!out) out = &scratch;
    memset(out, 0, sizeof(*out));

    const esp_app_desc_t *app = esp_app_get_description();
    copy_str(out->current_version, sizeof(out->current_version),
             app ? app->version : "");

    // Manifest is the esp-web-tools format also consumed by the
    // browser installer (same file at .../firmware/stable/manifest.json).
    // 2 KiB is comfortably above the ~700 B we currently see and leaves
    // room for longer version strings / multiple chip families.
    char body[2048];
    if (fetch_manifest(body, sizeof(body),
                       out->error, sizeof(out->error)) != ESP_OK) {
        out->result = FW_UPDATE_ERR_NETWORK;
        if (out->error[0] == '\0') {
            copy_str(out->error, sizeof(out->error),
                     "Could not fetch OTA manifest.");
        }
        return;
    }
    ESP_LOGI(TAG, "manifest: %s", body);

    cJSON *root = cJSON_Parse(body);
    if (!root) {
        out->result = FW_UPDATE_ERR_PARSE;
        copy_str(out->error, sizeof(out->error), "Manifest JSON invalid.");
        return;
    }
    const cJSON *j_ver    = cJSON_GetObjectItem(root, "version");
    const cJSON *j_builds = cJSON_GetObjectItem(root, "builds");
    if (!cJSON_IsString(j_ver)) {
        cJSON_Delete(root);
        out->result = FW_UPDATE_ERR_PARSE;
        copy_str(out->error, sizeof(out->error),
                 "Manifest missing version field.");
        return;
    }
    if (!cJSON_IsArray(j_builds) || cJSON_GetArraySize(j_builds) == 0) {
        cJSON_Delete(root);
        out->result = FW_UPDATE_ERR_PARSE;
        copy_str(out->error, sizeof(out->error),
                 "Manifest has no builds[].");
        return;
    }
    // The browser installer flashes every entry in parts[]
    // (bootloader, partition-table, ota_data, app). OTA only writes
    // the app slot, so pick the part whose URL ends in the build's
    // app binary name. Suffix match keeps us decoupled from the
    // partition layout (offsets) and from absolute URL prefixes.
    static const char APP_SUFFIX[] = "/phoneblock_dongle.bin";
    const cJSON *j_build0 = cJSON_GetArrayItem(j_builds, 0);
    const cJSON *j_parts  = cJSON_IsObject(j_build0)
                          ? cJSON_GetObjectItem(j_build0, "parts") : NULL;
    const char *new_url = NULL;
    if (cJSON_IsArray(j_parts)) {
        int n = cJSON_GetArraySize(j_parts);
        for (int i = 0; i < n; i++) {
            const cJSON *p = cJSON_GetArrayItem(j_parts, i);
            const cJSON *jp = cJSON_GetObjectItem(p, "path");
            if (!cJSON_IsString(jp)) continue;
            const char *path = jp->valuestring;
            size_t plen = strlen(path);
            size_t slen = sizeof(APP_SUFFIX) - 1;
            if (plen >= slen && strcmp(path + plen - slen, APP_SUFFIX) == 0) {
                new_url = path;
                break;
            }
        }
    }
    if (!new_url) {
        cJSON_Delete(root);
        out->result = FW_UPDATE_ERR_PARSE;
        copy_str(out->error, sizeof(out->error),
                 "Manifest has no app binary part.");
        return;
    }
    const char *new_version = j_ver->valuestring;
    copy_str(out->new_version, sizeof(out->new_version), new_version);

    int vcmp = semver_cmp(new_version, out->current_version);
    if (vcmp == 0) {
        cJSON_Delete(root);
        out->result = FW_UPDATE_NO_NEW;
        return;
    }
    if (vcmp < 0 && !force) {
        // Manifest is older than what we're running. The unforced path
        // (auto-update background task, "Auf Aktualisierung prüfen"
        // without manual override) treats this as "nothing newer" so
        // a CDN downgrade — accidental or otherwise — never silently
        // overwrites a newer locally-flashed build. The forced manual
        // path still allows downgrades on explicit user request.
        ESP_LOGI(TAG, "manifest version %s is older than running %s; skipping",
                 new_version, out->current_version);
        cJSON_Delete(root);
        out->result = FW_UPDATE_NO_NEW;
        return;
    }

    const char *failed = config_last_failed_ota();
    if (failed[0] != '\0' && strcmp(failed, new_version) == 0) {
        if (!force) {
            ESP_LOGW(TAG, "skipping update to %s — same version previously "
                          "rolled back; clear via web UI to retry", new_version);
            cJSON_Delete(root);
            out->result = FW_UPDATE_SKIPPED_FAILED;
            return;
        }
        // Manual override: forget the prior failure, the user is asking
        // us to try again.
        ESP_LOGW(TAG, "force-update: clearing last_failed_ota=%s", failed);
        config_set_last_failed_ota(NULL);
    }

    ESP_LOGI(TAG, "OTA: %s → %s from %s",
             out->current_version, new_version, new_url);

    // Pessimistic marker: write *before* the OTA so a brick + rollback
    // can be detected on the next boot. Cleared in main.c once the
    // running image equals this version (i.e. it survived).
    config_set_last_failed_ota(new_version);

    esp_http_client_config_t http_cfg = {
        .url = new_url,
        .crt_bundle_attach = esp_crt_bundle_attach,
        .timeout_ms = 30000,
        .keep_alive_enable = true,
    };
    esp_https_ota_config_t ota_cfg = {
        .http_config = &http_cfg,
    };
    esp_err_t err = esp_https_ota(&ota_cfg);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "esp_https_ota: %s", esp_err_to_name(err));
        // The download itself failed (network / signature / flash
        // write). Nothing was activated, so don't keep the pessimistic
        // marker around — leaving it set would block a healthy retry
        // after a transient WiFi glitch.
        config_set_last_failed_ota(NULL);
        cJSON_Delete(root);
        out->result = FW_UPDATE_ERR_OTA;
        copy_str(out->error, sizeof(out->error), esp_err_to_name(err));
        return;
    }

    cJSON_Delete(root);
    out->result = FW_UPDATE_INSTALLED;
    firmware_schedule_reboot();
}

// --- background task ------------------------------------------------

static void update_task(void *arg)
{
    (void)arg;
    // First iteration runs after a full (jittered) interval. The boot
    // path already validated the running image; no point overwriting
    // it the moment we come up.
    while (1) {
        uint32_t jitter = esp_random() % (2u * FWUP_JITTER_MS);
        uint32_t delay  = FWUP_INTERVAL_MS - FWUP_JITTER_MS + jitter;
        vTaskDelay(pdMS_TO_TICKS(delay));
        // Skip until the device is provisioned. Using the PhoneBlock
        // token as the "is configured" proxy mirrors the self-test
        // task — an unconfigured dongle has nothing to lose by
        // staying on its current build.
        if (strlen(config_phoneblock_token()) == 0) continue;
        // Honour the user's "freeze on this build" choice (set
        // automatically by the manual firmware-upload path, or
        // explicitly via the web UI toggle). The "Auf Aktualisierung
        // prüfen" button bypasses this — that's a manual call and
        // signals intent.
        if (!config_auto_update_enabled()) {
            ESP_LOGI(TAG, "scheduled update check skipped (auto-update off)");
            continue;
        }
        ESP_LOGI(TAG, "scheduled firmware update check");
        firmware_try_update(false, NULL);
    }
}

void firmware_update_start(void)
{
    if (s_task) return;
    xTaskCreate(update_task, "fw_update", 8192, NULL, 3, &s_task);
}
