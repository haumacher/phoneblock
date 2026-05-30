// Implementation of blocklist_sync.h: streaming HTTPS download into a
// SPIFFS temp file, atomic rename, daily background task.

#include "blocklist_sync.h"

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/semphr.h"

#include "esp_log.h"
#include "esp_timer.h"
#include "esp_http_client.h"
#include "esp_crt_bundle.h"

#include "config.h"
#include "http_util.h"

static const char *TAG = "blsync";

// 24 hours between scheduled runs.
#define SYNC_INTERVAL_US   (24LL * 3600LL * 1000000LL)

// Stream-read chunk for the HTTPS download. Small to stay friendly with
// the stack-allocated buffer; SPIFFS writes batch internally anyway.
#define DOWNLOAD_CHUNK     1024

#define COMMUNITY_TMP      BLOCKLIST_COMMUNITY_PATH ".tmp"
#define PERSONAL_TMP       BLOCKLIST_PERSONAL_PATH ".tmp"

static TaskHandle_t      s_task    = NULL;
static SemaphoreHandle_t s_trigger = NULL;
static SemaphoreHandle_t s_lock    = NULL;
static blocklist_sync_status_t s_status;

// ---------------------------------------------------------------------------
// Status helpers (caller may or may not hold s_lock; documented per use).
// ---------------------------------------------------------------------------

static void set_error(const char *msg)
{
    if (!msg) return;
    strncpy(s_status.last_error, msg, sizeof(s_status.last_error) - 1);
    s_status.last_error[sizeof(s_status.last_error) - 1] = '\0';
}

static void refresh_sizes_locked(void)
{
    blocklist_t *bl;
    if ((bl = blocklist_open(BLOCKLIST_COMMUNITY_PATH)) != NULL) {
        s_status.have_community = true;
        s_status.community_size = blocklist_size(bl);
        blocklist_close(bl);
    } else {
        s_status.have_community = false;
        s_status.community_size = 0;
    }
    if ((bl = blocklist_open(BLOCKLIST_PERSONAL_PATH)) != NULL) {
        s_status.have_personal = true;
        s_status.personal_size = blocklist_size(bl);
        blocklist_close(bl);
    } else {
        s_status.have_personal = false;
        s_status.personal_size = 0;
    }
}

// ---------------------------------------------------------------------------
// Streaming HTTPS download into a temp file.
// ---------------------------------------------------------------------------

// Downloads `url` into `tmp_path`. On any error returns false and writes a
// short diagnostic into `err`. Caller is responsible for removing the
// temp file on failure.
static bool download_to_tmp(const char *url, const char *tmp_path,
                            char *err, size_t err_cap)
{
    FILE *out = fopen(tmp_path, "wb");
    if (out == NULL) {
        snprintf(err, err_cap, "fopen %s: %s", tmp_path, strerror(errno));
        return false;
    }

    esp_http_client_config_t cfg = {
        .url               = url,
        .method            = HTTP_METHOD_GET,
        .crt_bundle_attach = esp_crt_bundle_attach,
        .timeout_ms        = 30000,
        .tls_version       = ESP_HTTP_CLIENT_TLS_VER_TLS_1_2,
    };
    esp_http_client_handle_t client = esp_http_client_init(&cfg);
    if (client == NULL) {
        snprintf(err, err_cap, "http_client_init failed");
        fclose(out);
        return false;
    }

    char auth_header[128];
    snprintf(auth_header, sizeof(auth_header), "Bearer %s",
             config_phoneblock_token());

    http_util_set_user_agent(client);
    esp_http_client_set_header(client, "Authorization", auth_header);
    esp_http_client_set_header(client, "Accept", "application/octet-stream");

    esp_err_t err_open = esp_http_client_open(client, 0);
    if (err_open != ESP_OK) {
        snprintf(err, err_cap, "open: %s", esp_err_to_name(err_open));
        esp_http_client_cleanup(client);
        fclose(out);
        return false;
    }

    int64_t content_length = esp_http_client_fetch_headers(client);
    int status = esp_http_client_get_status_code(client);
    if (status != 200) {
        snprintf(err, err_cap, "HTTP %d", status);
        esp_http_client_close(client);
        esp_http_client_cleanup(client);
        fclose(out);
        return false;
    }

    char buf[DOWNLOAD_CHUNK];
    int64_t total = 0;
    for (;;) {
        int n = esp_http_client_read(client, buf, sizeof(buf));
        if (n < 0) {
            snprintf(err, err_cap, "read failed after %lld bytes",
                     (long long)total);
            esp_http_client_close(client);
            esp_http_client_cleanup(client);
            fclose(out);
            return false;
        }
        if (n == 0) {
            break;
        }
        size_t w = fwrite(buf, 1, (size_t)n, out);
        if (w != (size_t)n) {
            snprintf(err, err_cap, "fwrite short at %lld bytes",
                     (long long)total);
            esp_http_client_close(client);
            esp_http_client_cleanup(client);
            fclose(out);
            return false;
        }
        total += n;
    }

    esp_http_client_close(client);
    esp_http_client_cleanup(client);

    if (fflush(out) != 0 || fclose(out) != 0) {
        snprintf(err, err_cap, "close %s: %s", tmp_path, strerror(errno));
        return false;
    }

    if (content_length > 0 && total != content_length) {
        snprintf(err, err_cap, "short body: got %lld of %lld",
                 (long long)total, (long long)content_length);
        return false;
    }

    ESP_LOGI(TAG, "downloaded %lld bytes → %s", (long long)total, tmp_path);
    return true;
}

// Validates that the file at `tmp_path` parses as a binary blocklist.
// Returns true if it does. Catches truncated downloads, wrong content-type
// from a misconfigured proxy, and the like.
static bool tmp_parses_ok(const char *tmp_path)
{
    blocklist_t *bl = blocklist_open(tmp_path);
    if (bl == NULL) {
        return false;
    }
    blocklist_close(bl);
    return true;
}

// Downloads one of the two list types and atomic-renames it into place.
// Sets *err on failure.
static bool sync_one(const char *type, const char *path, const char *tmp_path,
                     char *err, size_t err_cap)
{
    char url[256];
    snprintf(url, sizeof(url), "%s/api/blocklist?format=binary&type=%s",
             config_phoneblock_base_url(), type);

    unlink(tmp_path);

    if (!download_to_tmp(url, tmp_path, err, err_cap)) {
        unlink(tmp_path);
        return false;
    }

    if (!tmp_parses_ok(tmp_path)) {
        snprintf(err, err_cap, "downloaded %s is malformed", type);
        unlink(tmp_path);
        return false;
    }

    if (rename(tmp_path, path) != 0) {
        snprintf(err, err_cap, "rename %s: %s", type, strerror(errno));
        unlink(tmp_path);
        return false;
    }
    return true;
}

// ---------------------------------------------------------------------------
// Run-loop.
// ---------------------------------------------------------------------------

static void run_once(void)
{
    if (config_phoneblock_token()[0] == '\0') {
        ESP_LOGI(TAG, "skipped — no PhoneBlock token");
        xSemaphoreTake(s_lock, portMAX_DELAY);
        s_status.ever_ran    = true;
        s_status.last_ok     = false;
        s_status.last_at_us  = esp_timer_get_time();
        set_error("no PhoneBlock token");
        refresh_sizes_locked();
        xSemaphoreGive(s_lock);
        return;
    }

    char err[64] = "";
    bool community_ok = sync_one("community",
                                 BLOCKLIST_COMMUNITY_PATH, COMMUNITY_TMP,
                                 err, sizeof(err));
    if (!community_ok) {
        ESP_LOGW(TAG, "community sync failed: %s", err);
    }

    char err_personal[64] = "";
    bool personal_ok = sync_one("personal",
                                BLOCKLIST_PERSONAL_PATH, PERSONAL_TMP,
                                err_personal, sizeof(err_personal));
    if (!personal_ok) {
        ESP_LOGW(TAG, "personal sync failed: %s", err_personal);
    }

    xSemaphoreTake(s_lock, portMAX_DELAY);
    s_status.ever_ran   = true;
    s_status.last_ok    = community_ok && personal_ok;
    s_status.last_at_us = esp_timer_get_time();
    s_status.last_error[0] = '\0';
    if (!community_ok) {
        set_error(err);
    } else if (!personal_ok) {
        set_error(err_personal);
    }
    refresh_sizes_locked();
    xSemaphoreGive(s_lock);

    if (community_ok && personal_ok) {
        ESP_LOGI(TAG, "sync done: community=%d entries, personal=%d entries",
                 s_status.community_size, s_status.personal_size);
    }
}

static void task_loop(void *arg)
{
    (void)arg;
    TickType_t timeout = pdMS_TO_TICKS(SYNC_INTERVAL_US / 1000);
    while (1) {
        xSemaphoreTake(s_trigger, timeout);

        xSemaphoreTake(s_lock, portMAX_DELAY);
        s_status.running = true;
        xSemaphoreGive(s_lock);

        run_once();

        xSemaphoreTake(s_lock, portMAX_DELAY);
        s_status.running = false;
        xSemaphoreGive(s_lock);
    }
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

void blocklist_sync_start(void)
{
    if (s_task != NULL) {
        return;
    }
    memset(&s_status, 0, sizeof(s_status));
    s_lock    = xSemaphoreCreateMutex();
    s_trigger = xSemaphoreCreateBinary();

    xSemaphoreTake(s_lock, portMAX_DELAY);
    refresh_sizes_locked();
    xSemaphoreGive(s_lock);

    xTaskCreate(task_loop, "blsync", 6144, NULL, 3, &s_task);

    // Fire an initial sync at boot so a freshly powered dongle picks up
    // the current blocklist on the way out of WiFi/TLS init, rather than
    // waiting 24 h. The task itself short-circuits if no token is set.
    xSemaphoreGive(s_trigger);
}

bool blocklist_sync_trigger_now(void)
{
    if (s_task == NULL || s_trigger == NULL) {
        return false;
    }
    xSemaphoreTake(s_lock, portMAX_DELAY);
    bool running = s_status.running;
    xSemaphoreGive(s_lock);
    if (running) {
        return false;
    }
    xSemaphoreGive(s_trigger);
    return true;
}

blocklist_verdict_t blocklist_sync_check(const char *digits,
                                         bool consult_wildcards)
{
    blocklist_verdict_t v = BLOCKLIST_UNKNOWN;

    blocklist_t *personal = blocklist_open(BLOCKLIST_PERSONAL_PATH);
    if (personal != NULL) {
        v = blocklist_lookup(personal, digits, consult_wildcards);
        blocklist_close(personal);
    }
    if (v != BLOCKLIST_UNKNOWN) {
        return v;
    }

    blocklist_t *community = blocklist_open(BLOCKLIST_COMMUNITY_PATH);
    if (community != NULL) {
        v = blocklist_lookup(community, digits, consult_wildcards);
        blocklist_close(community);
    }
    return v;
}

void blocklist_sync_snapshot(blocklist_sync_status_t *out)
{
    if (!out) return;
    if (s_lock == NULL) {
        memset(out, 0, sizeof(*out));
        return;
    }
    xSemaphoreTake(s_lock, portMAX_DELAY);
    *out = s_status;
    xSemaphoreGive(s_lock);
}
