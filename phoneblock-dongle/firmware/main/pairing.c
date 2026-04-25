#include "pairing.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include "esp_crt_bundle.h"
#include "esp_http_client.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "esp_partition.h"
#include "lwip/sockets.h"   // INET_ADDRSTRLEN

#include "config.h"
#include "http_util.h"
#include "pairing_parse.h"

static const char *TAG = "pairing";

bool pairing_load(uint8_t out_secret[PAIRING_SECRET_LEN])
{
    const esp_partition_t *part = esp_partition_find_first(
            ESP_PARTITION_TYPE_DATA, 0x40, "pairing");
    if (part == NULL) {
        ESP_LOGI(TAG, "no pairing partition (OTA-only image or older flash layout)");
        return false;
    }

    uint8_t buf[PAIRING_HEADER_LEN];
    esp_err_t err = esp_partition_read(part, 0, buf, sizeof(buf));
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "esp_partition_read: %s", esp_err_to_name(err));
        return false;
    }

    if (!pairing_parse(buf, sizeof(buf), out_secret)) {
        // Erased flash (all 0xFF) is the expected first-line case here
        // for OTA-only dongles, so log at INFO not WARN.
        ESP_LOGI(TAG, "pairing partition empty or invalid — skipping handshake");
        return false;
    }

    ESP_LOGI(TAG, "pairing secret loaded (16 bytes)");
    return true;
}

// --- Async registration --------------------------------------------------

static void hexlify(const uint8_t *bytes, size_t n, char *out)
{
    static const char hex[] = "0123456789abcdef";
    for (size_t i = 0; i < n; i++) {
        out[i * 2 + 0] = hex[(bytes[i] >> 4) & 0x0F];
        out[i * 2 + 1] = hex[ bytes[i]       & 0x0F];
    }
    out[n * 2] = '\0';
}

static bool current_lan_ip(char *out, size_t cap)
{
    out[0] = '\0';
    esp_netif_t *netif = esp_netif_get_default_netif();
    if (!netif) return false;
    esp_netif_ip_info_t info;
    if (esp_netif_get_ip_info(netif, &info) != ESP_OK) return false;
    if (info.ip.addr == 0) return false;
    esp_ip4addr_ntoa(&info.ip, out, cap);
    return true;
}

// Attempts one POST. Returns true on a 2xx response; any transport
// error or non-2xx status is treated as retryable.
static bool register_post_once(const char *secret_hex)
{
    char ip[INET_ADDRSTRLEN] = "";
    if (!current_lan_ip(ip, sizeof(ip))) {
        ESP_LOGW(TAG, "no LAN IP yet — deferring");
        return false;
    }

    char url[160];
    snprintf(url, sizeof(url), "%s/api/dongle/register",
             config_phoneblock_base_url());

    char body[128];
    int body_len = snprintf(body, sizeof(body),
            "{\"secret\":\"%s\",\"lanIp\":\"%s\"}", secret_hex, ip);
    if (body_len < 0 || body_len >= (int)sizeof(body)) {
        ESP_LOGE(TAG, "body buffer overflow");
        return false;
    }

    esp_http_client_config_t cfg = {
        .url = url,
        .method = HTTP_METHOD_POST,
        .crt_bundle_attach = esp_crt_bundle_attach,
        .timeout_ms = 10000,
        .auth_type = HTTP_AUTH_TYPE_NONE,
    };
    esp_http_client_handle_t client = esp_http_client_init(&cfg);
    if (!client) {
        ESP_LOGE(TAG, "esp_http_client_init failed");
        return false;
    }
    http_util_set_user_agent(client);
    esp_http_client_set_header(client, "Content-Type", "application/json");
    esp_http_client_set_post_field(client, body, body_len);

    ESP_LOGI(TAG, "POST %s (ip=%s)", url, ip);
    esp_err_t err = esp_http_client_perform(client);
    int status = (err == ESP_OK) ? esp_http_client_get_status_code(client) : 0;
    esp_http_client_cleanup(client);

    if (err != ESP_OK) {
        ESP_LOGW(TAG, "register transport: %s", esp_err_to_name(err));
        return false;
    }
    if (status / 100 != 2) {
        ESP_LOGW(TAG, "register: HTTP %d", status);
        return false;
    }
    ESP_LOGI(TAG, "register: HTTP %d — handshake complete", status);
    return true;
}

// Heap-owned context handed to the worker task. The task frees it
// before self-deleting, regardless of outcome.
typedef struct {
    uint8_t secret[PAIRING_SECRET_LEN];
} pairing_task_ctx_t;

static void pairing_register_task(void *arg)
{
    pairing_task_ctx_t *ctx = arg;

    // Standard CRC-32 hex layout — 32 chars + NUL.
    char secret_hex[PAIRING_SECRET_LEN * 2 + 1];
    hexlify(ctx->secret, PAIRING_SECRET_LEN, secret_hex);

    // Wipe the heap secret as soon as we have the hex form: the
    // hex string is what every retry needs, the raw bytes aren't.
    memset(ctx->secret, 0, sizeof(ctx->secret));
    free(ctx);

    static const TickType_t backoffs_ms[] = { 1000, 4000, 15000, 60000 };
    const size_t n_backoffs = sizeof(backoffs_ms) / sizeof(backoffs_ms[0]);

    for (size_t attempt = 0; attempt < n_backoffs; attempt++) {
        if (register_post_once(secret_hex)) {
            // Wipe the hex copy too — no need to keep the secret in
            // RAM after the server has acknowledged it.
            memset(secret_hex, 0, sizeof(secret_hex));
            vTaskDelete(NULL);
            return;
        }
        TickType_t delay = pdMS_TO_TICKS(backoffs_ms[attempt]);
        ESP_LOGI(TAG, "retry %zu/%zu in %lu ms",
                 attempt + 1, n_backoffs,
                 (unsigned long)backoffs_ms[attempt]);
        vTaskDelay(delay);
    }

    ESP_LOGW(TAG, "register: gave up after %zu attempts — install page will fall back to mDNS",
             n_backoffs);
    memset(secret_hex, 0, sizeof(secret_hex));
    vTaskDelete(NULL);
}

void pairing_register_async(const uint8_t secret[PAIRING_SECRET_LEN])
{
    pairing_task_ctx_t *ctx = malloc(sizeof(*ctx));
    if (!ctx) {
        ESP_LOGE(TAG, "register: out of memory");
        return;
    }
    memcpy(ctx->secret, secret, PAIRING_SECRET_LEN);

    // 8 KB stack matches sip_server_task — TLS handshake (mbedtls) is
    // the dominant stack consumer. Priority 3 keeps it below the
    // user-facing web/SIP tasks; the registration is best-effort.
    BaseType_t ok = xTaskCreate(pairing_register_task, "pairing_reg",
                                8192, ctx, 3, NULL);
    if (ok != pdPASS) {
        ESP_LOGE(TAG, "register: xTaskCreate failed");
        memset(ctx->secret, 0, sizeof(ctx->secret));
        free(ctx);
    }
}
