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
#include "esp_random.h"
#include "esp_system.h"
#include "cJSON.h"

#include "mbedtls/base64.h"
#include "mbedtls/md.h"
#include "mbedtls/pk.h"
#include "mbedtls/sha256.h"

#include "sdkconfig.h"

#include "config.h"

static const char *TAG = "fwup";

// 24 h between scheduled checks — same cadence as the daily token
// self-test, with the same ±30 min skew so a fleet-wide power blip
// doesn't line every dongle up onto the same minute on the CDN.
#define FWUP_INTERVAL_MS    (24 * 3600 * 1000)
#define FWUP_JITTER_MS      (30 * 60 * 1000)

static TaskHandle_t s_task = NULL;

// ---------------------------------------------------------------------------
// OTA signing keys.
//
// ECDSA-P256 (prime256v1) public keys in DER SubjectPublicKeyInfo format
// (91 bytes each). The build host signs `manifest.json.integrity.signature`
// with the matching private key (kept in KeePassXC, see RELEASE.md). Verify
// only runs on the CDN-pull path; local USB flash and `POST /api/firmware`
// uploads bypass it on purpose, so a lost private key isn't a brick.
//
// `OTA_PUBKEY_NEXT` is the rotation slot. During key rotation the new
// public key gets baked in here for one release first; the next release
// signs with the new key, the one after that promotes it into PRIMARY
// and clears NEXT. See RELEASE.md → "Schlüssel rotieren".
// ---------------------------------------------------------------------------
static const uint8_t OTA_PUBKEY_PRIMARY[] = {
    0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x02,
    0x01, 0x06, 0x08, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x03, 0x01, 0x07, 0x03,
    0x42, 0x00, 0x04, 0xdb, 0x14, 0x2d, 0x82, 0x18, 0xdd, 0x79, 0x2e, 0x08,
    0x60, 0x33, 0xd0, 0x2e, 0xc6, 0xba, 0x5d, 0x6e, 0xa6, 0xad, 0x21, 0x7f,
    0xb1, 0xf1, 0xd9, 0xae, 0x08, 0xb6, 0xde, 0x07, 0xa5, 0x6e, 0x6f, 0x96,
    0x01, 0x2c, 0xa7, 0x4e, 0xa3, 0x7e, 0x86, 0x4b, 0xcc, 0x52, 0x8d, 0x37,
    0x94, 0x33, 0x52, 0xf3, 0x73, 0x15, 0x0a, 0xda, 0xdd, 0x31, 0x3a, 0xbe,
    0x52, 0x58, 0x16, 0xb2, 0x7f, 0xf7, 0xd9
};

static const struct {
    const uint8_t *der;
    size_t         len;
} OTA_PUBKEYS[] = {
    { OTA_PUBKEY_PRIMARY, sizeof(OTA_PUBKEY_PRIMARY) },
    // Add an entry here during rotation, e.g.:
    // { OTA_PUBKEY_NEXT,    sizeof(OTA_PUBKEY_NEXT) },
};

// ---------------------------------------------------------------------------
// Manifest signature verify.
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

static bool verify_with_pubkey(const uint8_t *pubkey_der, size_t pubkey_len,
                               const uint8_t *payload, size_t payload_len,
                               const uint8_t *sig, size_t sig_len)
{
    mbedtls_pk_context pk;
    mbedtls_pk_init(&pk);
    bool ok = false;

    int rc = mbedtls_pk_parse_public_key(&pk, pubkey_der, pubkey_len);
    if (rc != 0) {
        ESP_LOGE(TAG, "pk_parse_public_key: -0x%04x", -rc);
        goto done;
    }
    uint8_t hash[32];
    rc = mbedtls_md(mbedtls_md_info_from_type(MBEDTLS_MD_SHA256),
                    payload, payload_len, hash);
    if (rc != 0) {
        ESP_LOGE(TAG, "mbedtls_md(SHA-256): -0x%04x", -rc);
        goto done;
    }
    rc = mbedtls_pk_verify(&pk, MBEDTLS_MD_SHA256,
                           hash, sizeof(hash), sig, sig_len);
    if (rc != 0) {
        // -0x4e80 = MBEDTLS_ERR_ECP_VERIFY_FAILED for ECDSA. Logged at
        // WARN — a single failure here is recoverable across the
        // remaining pubkey slots.
        ESP_LOGW(TAG, "pk_verify: -0x%04x", -rc);
        goto done;
    }
    ok = true;
done:
    mbedtls_pk_free(&pk);
    return ok;
}

static bool verify_manifest_signature(const char *version,
                                      const char *app_sha256_hex,
                                      const uint8_t *sig, size_t sig_len)
{
    char payload[200];
    int n = build_signing_payload(payload, sizeof(payload),
                                  version, app_sha256_hex);
    if (n < 0) return false;

    for (size_t i = 0; i < sizeof(OTA_PUBKEYS) / sizeof(OTA_PUBKEYS[0]); i++) {
        if (verify_with_pubkey(OTA_PUBKEYS[i].der, OTA_PUBKEYS[i].len,
                               (const uint8_t *)payload, (size_t)n,
                               sig, sig_len)) {
            return true;
        }
    }
    return false;
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
        out->result = FW_UPDATE_ERR_PARSE;
        copy_str(out->error, sizeof(out->error),
                 "Manifest missing version field.");
        return;
    }
    if (!cJSON_IsString(j_app_hash) || strlen(j_app_hash->valuestring) != 64) {
        cJSON_Delete(root);
        out->result = FW_UPDATE_ERR_SIGNATURE;
        copy_str(out->error, sizeof(out->error),
                 "Manifest missing integrity.app_sha256.");
        return;
    }
    if (!cJSON_IsString(j_sig_b64)) {
        cJSON_Delete(root);
        out->result = FW_UPDATE_ERR_SIGNATURE;
        copy_str(out->error, sizeof(out->error),
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
        out->result = FW_UPDATE_ERR_SIGNATURE;
        copy_str(out->error, sizeof(out->error),
                 "Signature is not valid base64.");
        return;
    }
    if (!verify_manifest_signature(new_version, app_hash_hex, sig, sig_len)) {
        ESP_LOGE(TAG, "manifest signature verification failed");
        cJSON_Delete(root);
        out->result = FW_UPDATE_ERR_SIGNATURE;
        copy_str(out->error, sizeof(out->error),
                 "Manifest signature does not match.");
        return;
    }
    ESP_LOGI(TAG, "manifest signature OK (version=%s)", new_version);

    // Decode the expected hash now so the post-download compare is just
    // a memcmp.
    uint8_t expected_hash[32];
    if (!hex_decode(app_hash_hex, 64, expected_hash, sizeof(expected_hash))) {
        cJSON_Delete(root);
        out->result = FW_UPDATE_ERR_SIGNATURE;
        copy_str(out->error, sizeof(out->error),
                 "app_sha256 is not 64 hex chars.");
        return;
    }

    // ---- Pick app URL from builds[] (URL is *not* signed; trust comes
    // from the post-download hash compare against the signed value). ----
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

    // ---- Download via begin/perform/finish so we can hash the
    // just-written partition before activating it. The all-in-one
    // esp_https_ota() flips the boot partition implicitly, leaving no
    // room to reject a wrong-hash binary. ----
    esp_http_client_config_t http_cfg = {
        .url = new_url,
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
        cJSON_Delete(root);
        out->result = FW_UPDATE_ERR_OTA;
        copy_str(out->error, sizeof(out->error), esp_err_to_name(err));
        return;
    }

    while ((err = esp_https_ota_perform(ota_h)) == ESP_ERR_HTTPS_OTA_IN_PROGRESS) {
        // perform() yields after each chunk; let lower-priority tasks run.
        vTaskDelay(1);
    }
    if (err != ESP_OK || !esp_https_ota_is_complete_data_received(ota_h)) {
        ESP_LOGE(TAG, "esp_https_ota_perform: %s", esp_err_to_name(err));
        esp_https_ota_abort(ota_h);
        config_set_last_failed_ota(NULL);
        cJSON_Delete(root);
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
        cJSON_Delete(root);
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
                cJSON_Delete(root);
                out->result = FW_UPDATE_ERR_OTA;
                copy_str(out->error, sizeof(out->error),
                         "Partition read-back failed.");
                return;
            }
            mbedtls_sha256_update(&sha, chunk, take);
            off += take;
        }
        mbedtls_sha256_finish(&sha, computed_hash);
        mbedtls_sha256_free(&sha);
    }

    if (memcmp(computed_hash, expected_hash, sizeof(expected_hash)) != 0) {
        ESP_LOGE(TAG, "downloaded image hash does not match signed manifest");
        esp_https_ota_abort(ota_h);
        // Pessimistic marker stays cleared: this is an integrity
        // failure, not a "this version always bricks me". The next
        // attempt should retry with the same target.
        config_set_last_failed_ota(NULL);
        cJSON_Delete(root);
        out->result = FW_UPDATE_ERR_SIGNATURE;
        copy_str(out->error, sizeof(out->error),
                 "App binary hash does not match signed manifest.");
        return;
    }

    err = esp_https_ota_finish(ota_h);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "esp_https_ota_finish: %s", esp_err_to_name(err));
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
