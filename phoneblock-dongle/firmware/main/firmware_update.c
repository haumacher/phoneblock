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
#include "esp_ota_ops.h"
#include "esp_partition.h"
#include "esp_system.h"
#include "esp_task_wdt.h"
#include "cJSON.h"

#include "mbedtls/base64.h"
#include "mbedtls/md.h"
#include "mbedtls/pk.h"
#include "mbedtls/sha256.h"

#include "sdkconfig.h"

#include "config.h"
#include "manifest_sig.h"
#include "version_cmp.h"

static const char *TAG = "fwup";

// ---------------------------------------------------------------------------
// Manifest signature verify. The release public key(s) and the ECDSA verify
// live in manifest_sig.c, shared with the i18n asset path; here we only
// build the OTA-specific signed payload and hash it.
// ---------------------------------------------------------------------------

// Signed payload — must match sign-manifest.sh byte-for-byte. Kept tiny
// so it fits comfortably on the task stack: domain tag + version + hex
// hash plus three newlines and two `key=` literals stays well under 200 B.
static int build_signing_payload(char *buf, size_t cap,
                                 const char *version,
                                 const char *app_sha256_hex)
{
    int n = snprintf(buf, cap,
                     "phoneblock-dongle-ota-v1\n"
                     "version=%s\n"
                     "app_sha256=%s\n",
                     version, app_sha256_hex);
    if (n <= 0 || n >= (int)cap) return -1;
    return n;
}

static bool verify_manifest_signature(const char *version,
                                      const char *app_sha256_hex,
                                      const uint8_t *sig, size_t sig_len)
{
    char payload[200];
    int n = build_signing_payload(payload, sizeof(payload),
                                  version, app_sha256_hex);
    if (n < 0) return false;

    uint8_t hash[32];
    int rc = mbedtls_md(mbedtls_md_info_from_type(MBEDTLS_MD_SHA256),
                        (const uint8_t *)payload, (size_t)n, hash);
    if (rc != 0) {
        ESP_LOGE(TAG, "mbedtls_md(SHA-256): -0x%04x", -rc);
        return false;
    }
    return manifest_sig_verify_hash(hash, sig, sig_len);
}

// hex_decode: dst gets `len/2` bytes from `len` hex chars. Returns true
// iff the input is exactly hex and even-length.
static bool hex_decode(const char *src, size_t len,
                       uint8_t *dst, size_t dst_cap)
{
    if (len % 2 != 0 || dst_cap < len / 2) return false;
    for (size_t i = 0; i < len; i++) {
        char c = src[i];
        int  v;
        if      (c >= '0' && c <= '9') v = c - '0';
        else if (c >= 'a' && c <= 'f') v = 10 + c - 'a';
        else if (c >= 'A' && c <= 'F') v = 10 + c - 'A';
        else return false;
        if ((i & 1) == 0) dst[i / 2]  = (uint8_t)(v << 4);
        else              dst[i / 2] |= (uint8_t)v;
    }
    return true;
}

// --- helpers --------------------------------------------------------

static void copy_str(char *dst, size_t cap, const char *src)
{
    if (cap == 0) return;
    if (!src) { dst[0] = '\0'; return; }
    size_t n = strnlen(src, cap - 1);
    memcpy(dst, src, n);
    dst[n] = '\0';
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
    // 2.5 KB: this task logs a WARN line (→ capture path + the log hook's
    // frame) right before esp_restart(); 2 KB left too little headroom for
    // the hook. See the stack-sizing note in log_capture.c.
    xTaskCreate(reboot_task, "fw_reboot", 2560, NULL, 5, NULL);
}

// Fetch the JSON manifest into the caller-provided buffer. Returns
// ESP_OK with a NUL-terminated body on success. On failure, writes a
// human-readable cause into `err` (DNS/TLS/HTTP-status/empty-body) so
// the web UI can show it instead of a generic "could not fetch".
static esp_err_t fetch_manifest(char *body, size_t cap,
                                char *err, size_t err_cap)
{
    // Manifest URL = <base>/<channel>/manifest.json. The channel is a
    // client-side opt-in (NVS, web UI toggle); config_ota_channel()
    // clamps it to the known-safe literals "stable"/"beta" so it is
    // safe to splice into the path here.
    char url[256];
    snprintf(url, sizeof(url), "%s/%s/manifest.json",
             CONFIG_PHONEBLOCK_OTA_BASE_URL, config_ota_channel());
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

// --- internal: manifest decision (no side-effects on flash / NVS) ---

// Result of a read-only manifest evaluation. URL + expected_hash are
// only valid when result == FW_UPDATE_NEW_AVAILABLE; the install path
// then takes them straight from here without re-fetching the cJSON.
typedef struct {
    fw_update_result_t result;
    char    new_version[32];
    char    app_url[256];
    uint8_t expected_hash[32];
    char    error[64];
} manifest_decision_t;

static void resolve_manifest(bool force, const char *current_version,
                             manifest_decision_t *d)
{
    memset(d, 0, sizeof(*d));

    char body[2048];
    if (fetch_manifest(body, sizeof(body),
                       d->error, sizeof(d->error)) != ESP_OK) {
        d->result = FW_UPDATE_ERR_NETWORK;
        if (d->error[0] == '\0') {
            copy_str(d->error, sizeof(d->error),
                     "Could not fetch OTA manifest.");
        }
        return;
    }
    ESP_LOGI(TAG, "manifest: %s", body);

    cJSON *root = cJSON_Parse(body);
    if (!root) {
        d->result = FW_UPDATE_ERR_PARSE;
        copy_str(d->error, sizeof(d->error), "Manifest JSON invalid.");
        return;
    }

    // ---- Pull the fields we need to verify before trusting anything. ----
    const cJSON *j_ver       = cJSON_GetObjectItem(root, "version");
    const cJSON *j_builds    = cJSON_GetObjectItem(root, "builds");
    const cJSON *j_integrity = cJSON_GetObjectItem(root, "integrity");
    const cJSON *j_app_hash  = cJSON_IsObject(j_integrity)
                             ? cJSON_GetObjectItem(j_integrity, "app_sha256") : NULL;
    const cJSON *j_sig_b64   = cJSON_IsObject(j_integrity)
                             ? cJSON_GetObjectItem(j_integrity, "signature")  : NULL;

    if (!cJSON_IsString(j_ver)) {
        cJSON_Delete(root);
        d->result = FW_UPDATE_ERR_PARSE;
        copy_str(d->error, sizeof(d->error),
                 "Manifest missing version field.");
        return;
    }
    if (!cJSON_IsString(j_app_hash) || strlen(j_app_hash->valuestring) != 64) {
        cJSON_Delete(root);
        d->result = FW_UPDATE_ERR_SIGNATURE;
        copy_str(d->error, sizeof(d->error),
                 "Manifest missing integrity.app_sha256.");
        return;
    }
    if (!cJSON_IsString(j_sig_b64)) {
        cJSON_Delete(root);
        d->result = FW_UPDATE_ERR_SIGNATURE;
        copy_str(d->error, sizeof(d->error),
                 "Manifest missing integrity.signature.");
        return;
    }

    const char *new_version  = j_ver->valuestring;
    const char *app_hash_hex = j_app_hash->valuestring;
    const char *sig_b64      = j_sig_b64->valuestring;

    // ---- Verify signature over (domain-tag, version, app_sha256). ----
    // Up to ~80 B of base64 holds the typical ~70 B ASN.1-DER ECDSA-P256
    // signature comfortably; reject anything wildly larger as a sanity
    // guard before we feed it to mbedtls.
    uint8_t sig[96];
    size_t  sig_len = 0;
    int rc = mbedtls_base64_decode(sig, sizeof(sig), &sig_len,
                                   (const unsigned char *)sig_b64,
                                   strlen(sig_b64));
    if (rc != 0) {
        ESP_LOGE(TAG, "base64_decode(signature): -0x%04x", -rc);
        cJSON_Delete(root);
        d->result = FW_UPDATE_ERR_SIGNATURE;
        copy_str(d->error, sizeof(d->error),
                 "Signature is not valid base64.");
        return;
    }
    if (!verify_manifest_signature(new_version, app_hash_hex, sig, sig_len)) {
        ESP_LOGE(TAG, "manifest signature verification failed");
        cJSON_Delete(root);
        d->result = FW_UPDATE_ERR_SIGNATURE;
        copy_str(d->error, sizeof(d->error),
                 "Manifest signature does not match.");
        return;
    }
    ESP_LOGI(TAG, "manifest signature OK (version=%s)", new_version);

    // Decode the expected hash now so the post-download compare is just
    // a memcmp.
    if (!hex_decode(app_hash_hex, 64, d->expected_hash,
                    sizeof(d->expected_hash))) {
        cJSON_Delete(root);
        d->result = FW_UPDATE_ERR_SIGNATURE;
        copy_str(d->error, sizeof(d->error),
                 "app_sha256 is not 64 hex chars.");
        return;
    }

    // ---- Pick app URL from builds[] (URL is *not* signed; trust comes
    // from the post-download hash compare against the signed value). ----
    if (!cJSON_IsArray(j_builds) || cJSON_GetArraySize(j_builds) == 0) {
        cJSON_Delete(root);
        d->result = FW_UPDATE_ERR_PARSE;
        copy_str(d->error, sizeof(d->error),
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
        d->result = FW_UPDATE_ERR_PARSE;
        copy_str(d->error, sizeof(d->error),
                 "Manifest has no app binary part.");
        return;
    }
    copy_str(d->new_version, sizeof(d->new_version), new_version);
    copy_str(d->app_url,     sizeof(d->app_url),     new_url);

    int vcmp = version_cmp(new_version, current_version);
    if (vcmp == 0) {
        cJSON_Delete(root);
        d->result = FW_UPDATE_NO_NEW;
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
                 new_version, current_version);
        cJSON_Delete(root);
        d->result = FW_UPDATE_NO_NEW;
        return;
    }

    const char *failed = config_last_failed_ota();
    if (failed[0] != '\0' && strcmp(failed, new_version) == 0 && !force) {
        ESP_LOGW(TAG, "skipping update to %s — same version previously "
                      "rolled back; clear via web UI to retry", new_version);
        cJSON_Delete(root);
        d->result = FW_UPDATE_SKIPPED_FAILED;
        return;
    }

    cJSON_Delete(root);
    d->result = FW_UPDATE_NEW_AVAILABLE;
}

// --- internal: download + flash (writes NVS marker, schedules reboot)

static void install_resolved(const manifest_decision_t *d,
                             fw_update_outcome_t *out)
{
    ESP_LOGI(TAG, "OTA: %s → %s from %s",
             out->current_version, d->new_version, d->app_url);

    // Pessimistic marker: write *before* the OTA so a brick + rollback
    // can be detected on the next boot. Cleared in main.c once the
    // running image equals this version (i.e. it survived). Overwrites
    // any prior marker — the manual force=true path comes through here
    // even when an old failure named the same version.
    config_set_last_failed_ota(d->new_version);

    // ---- Download via begin/perform/finish so we can hash the
    // just-written partition before activating it. The all-in-one
    // esp_https_ota() flips the boot partition implicitly, leaving no
    // room to reject a wrong-hash binary. ----
    esp_http_client_config_t http_cfg = {
        .url = d->app_url,
        .crt_bundle_attach = esp_crt_bundle_attach,
        .timeout_ms = 30000,
        .keep_alive_enable = true,
    };
    esp_https_ota_config_t ota_cfg = {
        .http_config = &http_cfg,
    };

    esp_https_ota_handle_t ota_h = NULL;
    esp_err_t err = esp_https_ota_begin(&ota_cfg, &ota_h);
    if (err != ESP_OK || ota_h == NULL) {
        ESP_LOGE(TAG, "esp_https_ota_begin: %s", esp_err_to_name(err));
        config_set_last_failed_ota(NULL);
        out->result = FW_UPDATE_ERR_OTA;
        copy_str(out->error, sizeof(out->error), esp_err_to_name(err));
        return;
    }

    while ((err = esp_https_ota_perform(ota_h)) == ESP_ERR_HTTPS_OTA_IN_PROGRESS) {
        // perform() yields after each chunk; let lower-priority tasks run.
        vTaskDelay(1);
        // Feed the task watchdog explicitly: this loop typically runs
        // 15–40 s on the HTTPD task (manual install via the web UI),
        // well past CONFIG_ESP_TASK_WDT_TIMEOUT_S. The per-handler
        // feeder posted by web.c only fires between handler calls,
        // not during this loop. esp_task_wdt_reset() is a safe no-op
        // when called from update_task (the background path), which
        // isn't subscribed.
        esp_task_wdt_reset();
    }
    if (err != ESP_OK || !esp_https_ota_is_complete_data_received(ota_h)) {
        ESP_LOGE(TAG, "esp_https_ota_perform: %s", esp_err_to_name(err));
        esp_https_ota_abort(ota_h);
        config_set_last_failed_ota(NULL);
        out->result = FW_UPDATE_ERR_OTA;
        copy_str(out->error, sizeof(out->error), esp_err_to_name(err));
        return;
    }

    // Hash the partition we just wrote. esp_https_ota writes into the
    // *next* OTA slot; reading from there is safe even though it
    // hasn't been activated yet (otadata still points at the running
    // slot). 4 KiB chunks: an even multiple of the flash sector size.
    const esp_partition_t *next = esp_ota_get_next_update_partition(NULL);
    int written = esp_https_ota_get_image_len_read(ota_h);
    if (next == NULL || written <= 0) {
        ESP_LOGE(TAG, "OTA partition/length unavailable (next=%p len=%d)",
                 next, written);
        esp_https_ota_abort(ota_h);
        config_set_last_failed_ota(NULL);
        out->result = FW_UPDATE_ERR_OTA;
        copy_str(out->error, sizeof(out->error),
                 "OTA finished without a writable partition.");
        return;
    }
    uint8_t computed_hash[32];
    {
        mbedtls_sha256_context sha;
        mbedtls_sha256_init(&sha);
        mbedtls_sha256_starts(&sha, 0);
        uint8_t chunk[1024];
        size_t  off = 0;
        while (off < (size_t)written) {
            size_t take = sizeof(chunk);
            if (off + take > (size_t)written) take = (size_t)written - off;
            esp_err_t re = esp_partition_read(next, off, chunk, take);
            if (re != ESP_OK) {
                ESP_LOGE(TAG, "esp_partition_read @%u: %s",
                         (unsigned)off, esp_err_to_name(re));
                mbedtls_sha256_free(&sha);
                esp_https_ota_abort(ota_h);
                config_set_last_failed_ota(NULL);
                out->result = FW_UPDATE_ERR_OTA;
                copy_str(out->error, sizeof(out->error),
                         "Partition read-back failed.");
                return;
            }
            mbedtls_sha256_update(&sha, chunk, take);
            off += take;
            // ~1400 iterations across a 1.4 MB image. Flash read +
            // SHA update is fast (<1 s total) but the loop runs on
            // the HTTPD task right after the OTA download, so we're
            // still inside the window where the periodic feeder
            // can't reach us.
            esp_task_wdt_reset();
        }
        mbedtls_sha256_finish(&sha, computed_hash);
        mbedtls_sha256_free(&sha);
    }

    if (memcmp(computed_hash, d->expected_hash, sizeof(d->expected_hash)) != 0) {
        ESP_LOGE(TAG, "downloaded image hash does not match signed manifest");
        esp_https_ota_abort(ota_h);
        // Pessimistic marker stays cleared: this is an integrity
        // failure, not a "this version always bricks me". The next
        // attempt should retry with the same target.
        config_set_last_failed_ota(NULL);
        out->result = FW_UPDATE_ERR_SIGNATURE;
        copy_str(out->error, sizeof(out->error),
                 "App binary hash does not match signed manifest.");
        return;
    }

    err = esp_https_ota_finish(ota_h);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "esp_https_ota_finish: %s", esp_err_to_name(err));
        config_set_last_failed_ota(NULL);
        out->result = FW_UPDATE_ERR_OTA;
        copy_str(out->error, sizeof(out->error), esp_err_to_name(err));
        return;
    }

    out->result = FW_UPDATE_INSTALLED;
    firmware_schedule_reboot();
}

// --- public API -----------------------------------------------------

// Fill out->current_version from the running app descriptor; copy
// shared fields from the manifest decision. Does not touch out->result
// or out->error — the caller picks those depending on whether they
// stop at the check or proceed to install.
static void prime_outcome(fw_update_outcome_t *out)
{
    memset(out, 0, sizeof(*out));
    const esp_app_desc_t *app = esp_app_get_description();
    copy_str(out->current_version, sizeof(out->current_version),
             app ? app->version : "");
}

static void copy_decision_into_outcome(const manifest_decision_t *d,
                                       fw_update_outcome_t *out)
{
    out->result = d->result;
    copy_str(out->new_version, sizeof(out->new_version), d->new_version);
    copy_str(out->error,       sizeof(out->error),       d->error);
}

void firmware_check_manifest(bool force, fw_update_outcome_t *out)
{
    fw_update_outcome_t scratch;
    if (!out) out = &scratch;
    prime_outcome(out);

    manifest_decision_t d;
    resolve_manifest(force, out->current_version, &d);
    copy_decision_into_outcome(&d, out);
}

void firmware_try_update(bool force, fw_update_outcome_t *out)
{
    fw_update_outcome_t scratch;
    if (!out) out = &scratch;
    prime_outcome(out);

    manifest_decision_t d;
    resolve_manifest(force, out->current_version, &d);
    if (d.result != FW_UPDATE_NEW_AVAILABLE) {
        copy_decision_into_outcome(&d, out);
        return;
    }

    // Carry version forward for the success-path JSON; install_resolved
    // sets out->result and (on failure) out->error.
    copy_str(out->new_version, sizeof(out->new_version), d.new_version);
    install_resolved(&d, out);
}

// --- scheduled entry point ------------------------------------------

void firmware_update_run(void)
{
    // Skip until the device is provisioned. Using the PhoneBlock token as
    // the "is configured" proxy mirrors the self-test — an unconfigured
    // dongle has nothing to lose by staying on its current build.
    if (strlen(config_phoneblock_token()) == 0) return;
    // Honour the user's "freeze on this build" choice (set automatically
    // by the manual firmware-upload path, or explicitly via the web UI
    // toggle). The "Auf Aktualisierung prüfen" button bypasses this —
    // that's a manual call and signals intent.
    if (!config_auto_update_enabled()) {
        ESP_LOGI(TAG, "scheduled update check skipped (auto-update off)");
        return;
    }
    ESP_LOGI(TAG, "scheduled firmware update check");
    firmware_try_update(false, NULL);
}
