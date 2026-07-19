// Implementation of i18n_sync.h: fetch a signed asset manifest from the CDN,
// download the announcement (and mail pack) for the active ui_lang with
// SHA-256 verification, prune stale-locale files.

#include "i18n_sync.h"

#include <dirent.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <unistd.h>

#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"

#include "cJSON.h"
#include "esp_app_desc.h"
#include "esp_crt_bundle.h"
#include "esp_http_client.h"
#include "esp_log.h"
#include "esp_spiffs.h"
#include "esp_timer.h"
#include "mbedtls/base64.h"
#include "mbedtls/sha256.h"

#include "sdkconfig.h"

#include "announcement.h"
#include "config.h"
#include "http_util.h"
#include "manifest_sig.h"
#include "scheduler.h"
#include "version_cmp.h"

static const char *TAG = "i18nsync";

// Domain-separation prefix over the manifest bytes — must match the release
// script's signing payload byte-for-byte (scripts/i18n-assets.sh). Keeps an
// i18n signature from ever being accepted on the OTA path and vice versa.
#define I18N_SIG_DOMAIN "phoneblock-dongle-i18n-v1\n"

#define SPIFFS_LABEL     "storage"
#define SPIFFS_DIR       "/spiffs"
#define DOWNLOAD_CHUNK   1024
// Headroom kept free on the storage partition (SPIFFS metadata overhead the
// raw free-byte count overstates), matching blocklist_sync's guard.
#define FS_MARGIN        (48 * 1024)
// Manifest is small JSON (a few assets per locale); cap generously and
// heap-allocate so a growing locale set never smashes the task stack.
#define MANIFEST_CAP     (8 * 1024)
#define SIG_B64_CAP      256
#define SIG_DER_CAP      128

static SemaphoreHandle_t   s_lock = NULL;
static i18n_sync_status_t  s_status;

// ---------------------------------------------------------------------------
// Small helpers.
// ---------------------------------------------------------------------------

static void set_status(bool ok, const char *lang, const char *err)
{
    if (!s_lock) return;
    xSemaphoreTake(s_lock, portMAX_DELAY);
    s_status.ever_ran   = true;
    s_status.last_ok    = ok;
    s_status.last_at_us = esp_timer_get_time();
    strncpy(s_status.lang, lang ? lang : "", sizeof(s_status.lang) - 1);
    s_status.lang[sizeof(s_status.lang) - 1] = '\0';
    if (ok || !err) {
        s_status.last_error[0] = '\0';
    } else {
        strncpy(s_status.last_error, err, sizeof(s_status.last_error) - 1);
        s_status.last_error[sizeof(s_status.last_error) - 1] = '\0';
    }
    xSemaphoreGive(s_lock);
}

static int64_t spiffs_free_bytes(void)
{
    size_t total = 0, used = 0;
    if (esp_spiffs_info(SPIFFS_LABEL, &total, &used) != ESP_OK) return -1;
    return (int64_t)total - (int64_t)used;
}

static void bytes_to_hex(const uint8_t *in, size_t n, char *out)
{
    static const char hex[] = "0123456789abcdef";
    for (size_t i = 0; i < n; i++) {
        out[i * 2]     = hex[in[i] >> 4];
        out[i * 2 + 1] = hex[in[i] & 0x0f];
    }
    out[n * 2] = '\0';
}

// SHA-256 of an existing file as lowercase hex, into out[65]. False if the
// file cannot be read.
static bool sha256_file_hex(const char *path, char out[65])
{
    FILE *f = fopen(path, "rb");
    if (!f) return false;
    mbedtls_sha256_context sha;
    mbedtls_sha256_init(&sha);
    mbedtls_sha256_starts(&sha, 0);
    uint8_t buf[DOWNLOAD_CHUNK];
    size_t n;
    while ((n = fread(buf, 1, sizeof(buf), f)) > 0) {
        mbedtls_sha256_update(&sha, buf, n);
    }
    bool read_ok = !ferror(f);
    fclose(f);
    uint8_t digest[32];
    mbedtls_sha256_finish(&sha, digest);
    mbedtls_sha256_free(&sha);
    if (!read_ok) return false;
    bytes_to_hex(digest, sizeof(digest), out);
    return true;
}

// ---------------------------------------------------------------------------
// HTTP GET into a caller buffer (for the small manifest + signature files).
// ---------------------------------------------------------------------------

static bool http_get_buf(const char *url, char *body, size_t cap,
                         char *err, size_t err_cap)
{
    esp_http_client_config_t cfg = {
        .url               = url,
        .method            = HTTP_METHOD_GET,
        .crt_bundle_attach = esp_crt_bundle_attach,
        .timeout_ms        = 15000,
    };
    esp_http_client_handle_t client = esp_http_client_init(&cfg);
    if (!client) { snprintf(err, err_cap, "http_client_init"); return false; }
    http_util_set_user_agent(client);

    bool ok = false;
    esp_err_t e = esp_http_client_open(client, 0);
    if (e != ESP_OK) {
        snprintf(err, err_cap, "open: %s", esp_err_to_name(e));
        goto out;
    }
    int content_len = esp_http_client_fetch_headers(client);
    int status = esp_http_client_get_status_code(client);
    if (status != 200) {
        snprintf(err, err_cap, "HTTP %d", status);
        goto out_close;
    }
    int total = 0;
    while (total < (int)cap - 1) {
        int n = esp_http_client_read(client, body + total, cap - 1 - total);
        if (n <= 0) break;
        total += n;
        if (content_len > 0 && total >= content_len) break;
    }
    body[total] = '\0';
    if (total == 0) {
        snprintf(err, err_cap, "empty body");
    } else if (content_len > 0 && total < content_len) {
        snprintf(err, err_cap, "truncated (%d/%d)", total, content_len);
    } else if (content_len > 0 && total >= (int)cap - 1 && total < content_len) {
        snprintf(err, err_cap, "manifest too large");
    } else {
        ok = true;
    }
out_close:
    esp_http_client_close(client);
out:
    esp_http_client_cleanup(client);
    return ok;
}

// ---------------------------------------------------------------------------
// Streaming asset download → SHA-256 verify → atomic rename into place.
// ---------------------------------------------------------------------------

static bool download_verify_rename(const char *url, const char *final_path,
                                   const char *expected_sha_hex,
                                   char *err, size_t err_cap)
{
    char tmp_path[80];
    snprintf(tmp_path, sizeof(tmp_path), "%s.tmp", final_path);

    FILE *out = fopen(tmp_path, "wb");
    if (!out) { snprintf(err, err_cap, "fopen %s: %s", tmp_path, strerror(errno)); return false; }

    esp_http_client_config_t cfg = {
        .url               = url,
        .method            = HTTP_METHOD_GET,
        .crt_bundle_attach = esp_crt_bundle_attach,
        .timeout_ms        = 30000,
    };
    esp_http_client_handle_t client = esp_http_client_init(&cfg);
    if (!client) { snprintf(err, err_cap, "http_client_init"); fclose(out); unlink(tmp_path); return false; }
    http_util_set_user_agent(client);

    mbedtls_sha256_context sha;
    mbedtls_sha256_init(&sha);
    mbedtls_sha256_starts(&sha, 0);

    esp_err_t e = esp_http_client_open(client, 0);
    if (e != ESP_OK) { snprintf(err, err_cap, "open: %s", esp_err_to_name(e)); goto out; }
    int64_t content_length = esp_http_client_fetch_headers(client);
    int status = esp_http_client_get_status_code(client);
    if (status != 200) { snprintf(err, err_cap, "HTTP %d", status); goto out_close; }

    if (content_length > 0) {
        if (content_length > ANNOUNCEMENT_MAX_BYTES) {
            snprintf(err, err_cap, "asset too large: %lld B", (long long)content_length);
            goto out_close;
        }
        int64_t freeb = spiffs_free_bytes();
        if (freeb >= 0 && content_length + FS_MARGIN > freeb) {
            snprintf(err, err_cap, "no space: %lld B needs > %lld free",
                     (long long)content_length, (long long)freeb);
            goto out_close;
        }
    }

    char buf[DOWNLOAD_CHUNK];
    int64_t total = 0;
    for (;;) {
        int n = esp_http_client_read(client, buf, sizeof(buf));
        if (n < 0) { snprintf(err, err_cap, "read failed at %lld", (long long)total); goto out_close; }
        if (n == 0) break;
        mbedtls_sha256_update(&sha, (const uint8_t *)buf, (size_t)n);
        if (fwrite(buf, 1, (size_t)n, out) != (size_t)n) {
            snprintf(err, err_cap, "fwrite short at %lld: %s",
                     (long long)total, strerror(errno));
            goto out_close;
        }
        total += n;
    }
    esp_http_client_close(client);
    esp_http_client_cleanup(client);
    client = NULL;

    if (fflush(out) != 0 || fclose(out) != 0) {
        snprintf(err, err_cap, "close %s: %s", tmp_path, strerror(errno));
        out = NULL;
        mbedtls_sha256_free(&sha);
        unlink(tmp_path);
        return false;
    }
    out = NULL;

    uint8_t digest[32];
    char got_hex[65];
    mbedtls_sha256_finish(&sha, digest);
    mbedtls_sha256_free(&sha);
    bytes_to_hex(digest, sizeof(digest), got_hex);

    if (strcasecmp(got_hex, expected_sha_hex) != 0) {
        snprintf(err, err_cap, "sha mismatch");
        unlink(tmp_path);
        return false;
    }

    // SPIFFS has no atomic replace: rename() onto an existing name fails, so
    // drop the live file first (same reasoning as blocklist_sync.c). The
    // window without a file is harmless — the announcement just falls back
    // to silence until the rename lands.
    unlink(final_path);
    if (rename(tmp_path, final_path) != 0) {
        snprintf(err, err_cap, "rename: %s", strerror(errno));
        unlink(tmp_path);
        return false;
    }
    ESP_LOGI(TAG, "downloaded %lld B → %s", (long long)total, final_path);
    return true;

out_close:
    if (client) esp_http_client_close(client);
out:
    if (client) esp_http_client_cleanup(client);
    if (out) fclose(out);
    mbedtls_sha256_free(&sha);
    unlink(tmp_path);
    return false;
}

// ---------------------------------------------------------------------------
// Manifest fetch + signature verify.
// ---------------------------------------------------------------------------

// Verify the detached signature over I18N_SIG_DOMAIN + manifest bytes.
static bool verify_manifest(const char *manifest, size_t manifest_len,
                            const char *sig_b64)
{
    // Trim trailing whitespace/newline the CDN or base64 tool may append.
    size_t b64len = strlen(sig_b64);
    while (b64len > 0 && (sig_b64[b64len - 1] == '\n' ||
                          sig_b64[b64len - 1] == '\r' ||
                          sig_b64[b64len - 1] == ' ')) {
        b64len--;
    }
    uint8_t sig[SIG_DER_CAP];
    size_t sig_len = 0;
    int rc = mbedtls_base64_decode(sig, sizeof(sig), &sig_len,
                                   (const uint8_t *)sig_b64, b64len);
    if (rc != 0) {
        ESP_LOGW(TAG, "sig base64 decode: -0x%04x", -rc);
        return false;
    }

    uint8_t hash[32];
    mbedtls_sha256_context sha;
    mbedtls_sha256_init(&sha);
    mbedtls_sha256_starts(&sha, 0);
    mbedtls_sha256_update(&sha, (const uint8_t *)I18N_SIG_DOMAIN,
                          strlen(I18N_SIG_DOMAIN));
    mbedtls_sha256_update(&sha, (const uint8_t *)manifest, manifest_len);
    mbedtls_sha256_finish(&sha, hash);
    mbedtls_sha256_free(&sha);

    return manifest_sig_verify_hash(hash, sig, sig_len);
}

// ---------------------------------------------------------------------------
// Prune localized files that are not for `keep_lang`.
// ---------------------------------------------------------------------------

// True if `name` is "<prefix><lang><suffix>" for some lang != keep_lang.
static bool is_stale_localized(const char *name, const char *prefix,
                               const char *suffix, const char *keep_lang)
{
    size_t pn = strlen(prefix), sn = strlen(suffix), nn = strlen(name);
    if (nn <= pn + sn) return false;
    if (strncmp(name, prefix, pn) != 0) return false;
    if (strcmp(name + nn - sn, suffix) != 0) return false;
    size_t lang_len = nn - pn - sn;
    return !(lang_len == strlen(keep_lang) &&
             strncmp(name + pn, keep_lang, lang_len) == 0);
}

static void prune_stale(const char *keep_lang)
{
    DIR *d = opendir(SPIFFS_DIR);
    if (!d) return;
    struct dirent *e;
    while ((e = readdir(d)) != NULL) {
        // readdir on SPIFFS yields the basename; match against the localized
        // announcement / mail-pack naming. The user-uploaded custom file
        // "announcement.alaw" has no "-<lang>" and never matches.
        bool stale =
            is_stale_localized(e->d_name, "announcement-", ".alaw", keep_lang) ||
            is_stale_localized(e->d_name, "mail-", ".json", keep_lang) ||
            is_stale_localized(e->d_name, "ui-", ".json", keep_lang);
        if (stale) {
            // Sized for SPIFFS_DIR + '/' + a full dirent name so the
            // formatted path can never be truncated (-Wformat-truncation).
            char path[8 + sizeof(e->d_name)];
            snprintf(path, sizeof(path), "%s/%s", SPIFFS_DIR, e->d_name);
            if (unlink(path) == 0) {
                ESP_LOGI(TAG, "pruned stale asset %s", e->d_name);
            }
        }
    }
    closedir(d);
}

// ---------------------------------------------------------------------------
// Download one manifest asset into final_path (skip if the on-disk SHA already
// matches). The asset is stored under the ui_lang name regardless of which
// fallback locale it came from — the device keeps exactly one file per kind.
// ---------------------------------------------------------------------------

static bool download_asset(cJSON *asset, const char *kind, const char *base_url,
                           const char *final_path, char *err, size_t err_cap)
{
    cJSON *jpath = cJSON_GetObjectItem(asset, "path");
    cJSON *jsha  = cJSON_GetObjectItem(asset, "sha256");
    if (!cJSON_IsString(jpath) || !cJSON_IsString(jsha) ||
        strlen(jsha->valuestring) != 64) {
        snprintf(err, err_cap, "%s: bad asset entry", kind);
        return false;
    }

    char on_disk[65];
    if (sha256_file_hex(final_path, on_disk) &&
        strcasecmp(on_disk, jsha->valuestring) == 0) {
        ESP_LOGI(TAG, "%s up to date", kind);
        return true;   // already have exactly this content
    }

    char url[256];
    snprintf(url, sizeof(url), "%s/%s", base_url, jpath->valuestring);
    if (!download_verify_rename(url, final_path, jsha->valuestring, err, err_cap)) {
        ESP_LOGW(TAG, "%s download %s: %s", kind, url, err);
        return false;
    }
    return true;
}

// Download a single asset of `kind` (announcement / mail) for the first locale
// in `chain` (ui_lang → en → de) whose manifest entry has it, storing it under
// final_path. Fetching only ONE file — the fallback is over which locale's
// content to pick, not over how many are kept. If no chain locale offers it,
// remove any stale file and treat as success (that content is unavailable, the
// caller's compiled/inline fallback or silence takes over).
static bool sync_kind_chain(cJSON *assets, const char *kind, const char *base_url,
                            const char *final_path, const char **chain, int chain_n,
                            char *err, size_t err_cap)
{
    for (int i = 0; i < chain_n; i++) {
        cJSON *lo = cJSON_GetObjectItem(assets, chain[i]);
        cJSON *asset = cJSON_IsObject(lo) ? cJSON_GetObjectItem(lo, kind) : NULL;
        if (cJSON_IsObject(asset)) {
            if (i > 0) ESP_LOGI(TAG, "%s: no '%s', falling back to '%s'",
                                kind, chain[0], chain[i]);
            return download_asset(asset, kind, base_url, final_path, err, err_cap);
        }
    }
    unlink(final_path);   // not offered in any chain locale → serve fallback
    return true;
}

// ---------------------------------------------------------------------------
// Run-loop.
// ---------------------------------------------------------------------------

static void run_once(void)
{
    const char *lang = config_ui_lang();   // validated, safe for URLs/paths
    // i18n is keyed by the release TAG. A release build reports its exact tag
    // (version.txt), e.g. "1.5.0" or "1.5.0-rc2" or "1.6.0-rc1" — each gets its
    // own bundle (an rc must be able to ship new strings). A dev build reports
    // git-describe, "<tag>-<N>-g<hash>[-dirty]"; we strip only that dev suffix
    // so it reuses its tag's bundle instead of needing a publish per commit.
    // release.sh publishes i18n under the same tag (VERSION).
    char tag[48];
    strncpy(tag, esp_app_get_description()->version, sizeof(tag) - 1);
    tag[sizeof(tag) - 1] = '\0';
    version_release_tag(tag);   // in-place strip of the git-describe dev suffix
    char base[160];
    snprintf(base, sizeof(base), "%s/%s/i18n",
             CONFIG_PHONEBLOCK_OTA_BASE_URL, tag);
    char err[64] = "";

    char *manifest = malloc(MANIFEST_CAP);
    char *sig      = malloc(SIG_B64_CAP);
    if (!manifest || !sig) {
        free(manifest); free(sig);
        set_status(false, lang, "out of memory");
        return;
    }

    char url[256];
    snprintf(url, sizeof(url), "%s/manifest.json", base);
    if (!http_get_buf(url, manifest, MANIFEST_CAP, err, sizeof(err))) {
        ESP_LOGW(TAG, "manifest fetch %s: %s", url, err);
        free(manifest); free(sig);
        set_status(false, lang, err);
        return;
    }
    size_t manifest_len = strlen(manifest);

    snprintf(url, sizeof(url), "%s/manifest.json.sig", base);
    if (!http_get_buf(url, sig, SIG_B64_CAP, err, sizeof(err))) {
        ESP_LOGW(TAG, "signature fetch %s: %s", url, err);
        free(manifest); free(sig);
        set_status(false, lang, err);
        return;
    }

    if (!verify_manifest(manifest, manifest_len, sig)) {
        ESP_LOGE(TAG, "manifest signature INVALID — refusing assets");
        free(manifest); free(sig);
        set_status(false, lang, "bad signature");
        return;
    }
    free(sig);

    cJSON *root = cJSON_Parse(manifest);
    free(manifest);
    if (!root) {
        set_status(false, lang, "manifest parse");
        return;
    }

    cJSON *assets = cJSON_GetObjectItem(root, "assets");
    if (!cJSON_IsObject(assets)) {
        ESP_LOGW(TAG, "manifest has no assets object");
        cJSON_Delete(root);
        set_status(false, lang, "no assets");
        return;
    }

    // The announcement has NO embedded fallback (a missing recording means the
    // device answers silently), so we try to salvage SOME speech with a
    // download-time chain: prefer the active ui_lang, then English, then German
    // recording. Whichever is chosen is stored under the ui_lang name, so only
    // ONE announcement ever lives on the device (announcement.c reads it).
    const char *ann_chain[3];
    int acn = 0;
    ann_chain[acn++] = lang;
    if (strcmp(lang, "en") != 0) ann_chain[acn++] = "en";
    if (strcmp(lang, "de") != 0) ann_chain[acn++] = "de";

    char ann_path[48];
    announcement_localized_path(ann_path, sizeof(ann_path), lang);
    char mail_path[48];
    snprintf(mail_path, sizeof(mail_path), "%s/mail-%s.json", SPIFFS_DIR, lang);
    char ui_path[48];
    snprintf(ui_path, sizeof(ui_path), "%s/ui-%s.json", SPIFFS_DIR, lang);

    // Prune the PREVIOUS locale's assets BEFORE downloading the new ones. The
    // storage partition (~640 KB, shared with the blocklist) cannot hold two
    // locales at once — an announcement alone is ~80-96 KB — so downloading
    // first and pruning last overflowed SPIFFS mid-switch (fwrite short), which
    // left the new UI/mail pack half-written and falling back to English. Now
    // peak usage is one locale's assets. On-disk SHAs matching the manifest are
    // still skipped inside sync_kind_chain, so a no-op re-sync re-downloads
    // nothing. (A crash between prune and download self-heals on the next sync;
    // meanwhile the embedded English UI/mail and silent announcement cover it.)
    prune_stale(lang);

    // Download order is chosen for perceived responsiveness: the UI pack FIRST
    // (a browser waiting on the language switch re-renders as soon as it lands,
    // ~24 KB), then the small mail pack, and the big announcement (~80-96 KB)
    // LAST — it is only needed on the next answered call, so it must not delay
    // the visible UI update.
    //
    // Mail and UI packs both have an embedded ENGLISH fallback (mail_i18n.c /
    // web.c), so they download ONLY the active locale's pack — no en/de chain.
    // If it isn't published, download nothing and use the embedded English. (A
    // chain fallback would pull German for a missing locale, contradicting the
    // English fallback.) en itself is embedded, so downloading it is redundant
    // but harmless — the manifest publishes every locale uniformly.
    const char *single_chain[1] = { lang };
    bool ok = true;
    if (!sync_kind_chain(assets, "ui", base, ui_path, single_chain, 1,
                         err, sizeof(err))) {
        ESP_LOGW(TAG, "ui pack sync: %s", err);
        ok = false;
    }
    char err2[64] = "";
    if (!sync_kind_chain(assets, "mail", base, mail_path, single_chain, 1,
                         err2, sizeof(err2))) {
        ESP_LOGW(TAG, "mail pack sync: %s", err2);
        if (ok) { ok = false; strncpy(err, err2, sizeof(err) - 1); }
    }
    // The announcement has no embedded fallback (silence), so it uses the
    // [ui_lang, en, de] chain built above; downloaded last (biggest, least
    // time-critical).
    char err3[64] = "";
    if (!sync_kind_chain(assets, "announcement", base, ann_path, ann_chain, acn,
                         err3, sizeof(err3))) {
        ESP_LOGW(TAG, "announcement sync: %s", err3);
        if (ok) { ok = false; strncpy(err, err3, sizeof(err) - 1); }
    }

    cJSON_Delete(root);
    set_status(ok, lang, ok ? NULL : err);
    if (ok) ESP_LOGI(TAG, "i18n assets in sync for '%s'", lang);
}

// ---------------------------------------------------------------------------
// Public API.
// ---------------------------------------------------------------------------

void i18n_sync_init(void)
{
    if (s_lock) return;
    memset(&s_status, 0, sizeof(s_status));
    s_lock = xSemaphoreCreateMutex();
}

void i18n_sync_run(void)
{
    if (!s_lock) return;
    xSemaphoreTake(s_lock, portMAX_DELAY);
    s_status.running = true;
    xSemaphoreGive(s_lock);

    run_once();

    xSemaphoreTake(s_lock, portMAX_DELAY);
    s_status.running = false;
    xSemaphoreGive(s_lock);
}

bool i18n_sync_trigger_now(void)
{
    if (!s_lock) return false;
    xSemaphoreTake(s_lock, portMAX_DELAY);
    bool running = s_status.running;
    xSemaphoreGive(s_lock);
    if (running) return false;
    return scheduler_request_i18n_sync();
}

void i18n_sync_snapshot(i18n_sync_status_t *out)
{
    if (!out) return;
    if (!s_lock) { memset(out, 0, sizeof(*out)); return; }
    xSemaphoreTake(s_lock, portMAX_DELAY);
    *out = s_status;
    xSemaphoreGive(s_lock);
}
