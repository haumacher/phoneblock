// Implementation of blocklist_sync.h: streaming HTTPS download into a
// SPIFFS temp file, atomic rename, daily background task.

#include "blocklist_sync.h"

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"

#include "esp_log.h"
#include "esp_timer.h"
#include "esp_http_client.h"
#include "esp_crt_bundle.h"
#include "esp_spiffs.h"

#include "config.h"
#include "http_util.h"
#include "scheduler.h"

static const char *TAG = "blsync";

// Stream-read chunk for the HTTPS download. Small to stay friendly with
// the stack-allocated buffer; SPIFFS writes batch internally anyway.
#define DOWNLOAD_CHUNK     1024

#define COMMUNITY_TMP      BLOCKLIST_COMMUNITY_PATH ".tmp"
#define PERSONAL_TMP       BLOCKLIST_PERSONAL_PATH ".tmp"

// SPIFFS partition label shared with announcement.c — the community,
// personal and announcement files all live on this one mount.
#define SPIFFS_LABEL       "storage"

// Headroom kept free on the storage partition: SPIFFS block/metadata
// overhead the raw free-byte count overstates, plus room for the small
// personal list. Used both to size the budget advertised to the server
// (&maxBytes) and to guard each download against a mid-write ENOSPC.
#define BLOCKLIST_FS_MARGIN      (48 * 1024)

// Community budget advertised when the filesystem size cannot be read.
#define DEFAULT_COMMUNITY_BUDGET (256 * 1024)

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

// Free bytes on the storage SPIFFS partition, or -1 if it cannot be read.
static int64_t spiffs_free_bytes(void)
{
    size_t total = 0, used = 0;
    if (esp_spiffs_info(SPIFFS_LABEL, &total, &used) != ESP_OK) {
        return -1;
    }
    return (int64_t) total - (int64_t) used;
}

// Storage budget the dongle offers the community file: current free space
// minus a margin for SPIFFS overhead and the small personal list. The old
// community file is deleted before the download starts (see sync_one), so
// its bytes are already part of this free count — no need to add them back.
// Sent to the server as &maxBytes so it caps the encoded file to what fits.
static int64_t community_budget_bytes(void)
{
    int64_t freeb = spiffs_free_bytes();
    if (freeb < 0) {
        return DEFAULT_COMMUNITY_BUDGET;
    }
    int64_t budget = freeb - BLOCKLIST_FS_MARGIN;
    return budget > 0 ? budget : 0;
}

// Downloads `url` into `tmp_path`. The caller has already removed the live
// file this temp will be renamed onto, so the whole free partition is
// available here. On any error returns false and writes a message to `err`.
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

    // Storage pre-check: refuse a body that won't fit before any bytes land,
    // turning a mid-write ENOSPC (which strands a half-written .tmp) into a
    // clean, actionable error. The old live file was already removed by the
    // caller, so the free count is exactly what this download has to work with.
    if (content_length > 0) {
        int64_t freeb = spiffs_free_bytes();
        if (freeb >= 0 && content_length + BLOCKLIST_FS_MARGIN > freeb) {
            snprintf(err, err_cap, "too large: %lld B needs > %lld B free",
                     (long long) content_length, (long long) freeb);
            esp_http_client_close(client);
            esp_http_client_cleanup(client);
            fclose(out);
            unlink(tmp_path);
            return false;
        }
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
    // Drop the old cached file up front, before downloading the new one.
    // SPIFFS has no atomic replace — rename() onto an existing name returns
    // SPIFFS_ERR_CONFLICTING_NAME, which the VFS maps to the catch-all EIO,
    // so a temp+rename swap fails with "rename …: I/O error" on every run
    // after the first. Removing the live file first makes the rename target
    // absent (so it succeeds) and means only one copy is ever on flash, so
    // the download never needs double the space. The cost is no on-flash
    // fallback during the download window: if the sync fails, the call-time
    // path falls back to the PhoneBlock API until the next successful run.
    unlink(path);

    char url[256];
    int n = snprintf(url, sizeof(url), "%s/api/blocklist?format=binary&type=%s",
                     config_phoneblock_base_url(), type);

    // The community list carries no per-entry vote counts, so the server
    // applies our two thresholds at encode time. Send them so the
    // downloaded list matches exactly what the API-fallback path
    // (api.c: direct >= min_direct, wildcard >= min_range) would decide.
    // The personal list is the user's explicit black/white set — no
    // thresholding, so no parameters.
    if (strcmp(type, "community") == 0 && n > 0 && (size_t) n < sizeof(url)) {
        // maxBytes lets the server cap the (size-unbounded) community list to
        // our free flash: it keeps all wildcards + whitelist and truncates the
        // Heat-ranked direct numbers to fit. minDirect/minRange keep the
        // encoded verdict identical to the API-fallback path.
        snprintf(url + n, sizeof(url) - n, "&minDirect=%d&minRange=%d&maxBytes=%lld",
                 config_min_direct_votes(), config_min_range_votes(),
                 (long long) community_budget_bytes());
    }

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

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

void blocklist_sync_init(void)
{
    if (s_lock != NULL) {
        return;
    }
    memset(&s_status, 0, sizeof(s_status));
    s_lock = xSemaphoreCreateMutex();

    xSemaphoreTake(s_lock, portMAX_DELAY);
    refresh_sizes_locked();
    xSemaphoreGive(s_lock);
}

void blocklist_sync_run(void)
{
    if (s_lock == NULL) {
        return;
    }
    if (!config_blocklist_enabled()) {
        // Feature switched off in the web UI: don't refresh the on-flash
        // files. The existing files stay put but are never consulted (the
        // call-time path skips them too), so they simply age out.
        ESP_LOGI(TAG, "skipped — local blocklist cache disabled");
        return;
    }

    xSemaphoreTake(s_lock, portMAX_DELAY);
    s_status.running = true;
    xSemaphoreGive(s_lock);

    run_once();

    xSemaphoreTake(s_lock, portMAX_DELAY);
    s_status.running = false;
    xSemaphoreGive(s_lock);
}

bool blocklist_sync_trigger_now(void)
{
    if (s_lock == NULL || !config_blocklist_enabled()) {
        return false;
    }
    xSemaphoreTake(s_lock, portMAX_DELAY);
    bool running = s_status.running;
    xSemaphoreGive(s_lock);
    if (running) {
        return false;
    }
    // Hand off to the scheduler task, which runs blocklist_sync_run() on
    // its own stack — the download must not run on the caller's httpd
    // thread.
    return scheduler_request_blocklist_sync();
}

blocklist_verdict_t blocklist_sync_check(const char *digits,
                                         bool consult_wildcards)
{
    return blocklist_sync_check_ex(digits, consult_wildcards, NULL, NULL);
}

blocklist_verdict_t blocklist_sync_check_ex(const char *digits,
                                            bool consult_wildcards,
                                            bool *wildcard_out,
                                            bool *personal_out)
{
    if (wildcard_out) *wildcard_out = false;
    if (personal_out) *personal_out = false;

    blocklist_verdict_t v = BLOCKLIST_UNKNOWN;
    bool wildcard = false;

    blocklist_t *personal = blocklist_open(BLOCKLIST_PERSONAL_PATH);
    if (personal != NULL) {
        v = blocklist_lookup_ex(personal, digits, consult_wildcards, &wildcard);
        blocklist_close(personal);
    }
    if (v != BLOCKLIST_UNKNOWN) {
        if (wildcard_out) *wildcard_out = wildcard;
        if (personal_out) *personal_out = true;
        return v;
    }

    blocklist_t *community = blocklist_open(BLOCKLIST_COMMUNITY_PATH);
    if (community != NULL) {
        v = blocklist_lookup_ex(community, digits, consult_wildcards, &wildcard);
        blocklist_close(community);
    }
    if (v != BLOCKLIST_UNKNOWN && wildcard_out) {
        *wildcard_out = wildcard;
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
