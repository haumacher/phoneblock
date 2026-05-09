#include "crashreport.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include "esp_app_desc.h"
#include "esp_core_dump.h"
#include "esp_crt_bundle.h"
#include "esp_err.h"
#include "esp_http_client.h"
#include "esp_log.h"
#include "esp_partition.h"

#include "config.h"
#include "http_util.h"

static const char *TAG = "crashrep";

// Hard cap matches the partition size (see partitions.csv: 0xD000 = 52 KB).
// Reading more than the partition holds is impossible by construction;
// the cap exists only as a sanity guard against an absurd image_get()
// return. Anything beyond is treated as a corrupt header.
#define CRASHREPORT_MAX_BYTES (52 * 1024)

// Generous timeout: a 50 KB POST over a slow uplink (DSL up, mobile
// hotspot) plus the TLS handshake can creep past 10 s. 30 s is well
// within what users tolerate at boot for a one-shot best-effort task.
#define CRASHREPORT_HTTP_TIMEOUT_MS 30000

static void crashreport_task(void *arg)
{
    (void)arg;

    esp_err_t err = esp_core_dump_image_check();
    if (err == ESP_ERR_NOT_FOUND || err == ESP_ERR_INVALID_SIZE) {
        // No partition (legacy layout) or no dump stored. Nothing to do.
        ESP_LOGI(TAG, "no core dump pending");
        vTaskDelete(NULL);
        return;
    }
    if (err != ESP_OK) {
        // Stored dump is corrupt — erase it so the slot is reusable.
        ESP_LOGW(TAG, "stored core dump invalid (%s) — erasing",
                 esp_err_to_name(err));
        esp_core_dump_image_erase();
        vTaskDelete(NULL);
        return;
    }

    size_t offset = 0;
    size_t size   = 0;
    err = esp_core_dump_image_get(&offset, &size);
    if (err != ESP_OK || size == 0 || size > CRASHREPORT_MAX_BYTES) {
        ESP_LOGE(TAG, "image_get: err=%s size=%zu — erasing",
                 esp_err_to_name(err), size);
        esp_core_dump_image_erase();
        vTaskDelete(NULL);
        return;
    }

    if (!config_crash_report_enabled()) {
        // User opted out from the web UI. Erase the dump locally so
        // it doesn't sit in flash forever — and so a later re-enable
        // doesn't ship historical state out the door that the user
        // had explicitly asked us not to send.
        ESP_LOGI(TAG, "core dump pending (%zu bytes) but crash reports "
                 "are disabled — erasing", size);
        esp_core_dump_image_erase();
        vTaskDelete(NULL);
        return;
    }

    if (strlen(config_phoneblock_token()) == 0) {
        // Dump exists but the dongle isn't paired yet — keep it; the
        // next boot after the user runs the OAuth wizard can ship it.
        ESP_LOGI(TAG, "core dump pending (%zu bytes) but no PhoneBlock "
                 "token yet — keeping for later", size);
        vTaskDelete(NULL);
        return;
    }

    const esp_partition_t *part = esp_partition_find_first(
        ESP_PARTITION_TYPE_DATA, ESP_PARTITION_SUBTYPE_DATA_COREDUMP, NULL);
    if (!part) {
        ESP_LOGE(TAG, "coredump partition not found despite image_check OK");
        vTaskDelete(NULL);
        return;
    }

    uint8_t *buf = malloc(size);
    if (!buf) {
        ESP_LOGW(TAG, "out of heap for %zu-byte dump — keeping for next boot",
                 size);
        vTaskDelete(NULL);
        return;
    }

    // esp_core_dump_image_get returns a *flash-absolute* address in
    // `offset` (despite the parameter name), but esp_partition_read
    // wants a partition-relative one. Subtracting part->address gives
    // the right value; passing the absolute address verbatim trips
    // ESP_ERR_INVALID_ARG because it lies past the partition end.
    if (offset < part->address || offset + size > part->address + part->size) {
        ESP_LOGE(TAG, "core dump (addr=0x%zx size=%zu) outside coredump "
                      "partition (0x%lx..0x%lx)", offset, size,
                 (unsigned long)part->address,
                 (unsigned long)(part->address + part->size));
        free(buf);
        vTaskDelete(NULL);
        return;
    }
    err = esp_partition_read(part, offset - part->address, buf, size);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "partition_read: %s", esp_err_to_name(err));
        free(buf);
        vTaskDelete(NULL);
        return;
    }

    const esp_app_desc_t *app = esp_app_get_description();
    const char *fw = (app && app->version[0]) ? app->version : "unknown";

    char url[200];
    snprintf(url, sizeof(url), "%s/api/dongle/coredump?fw=%s",
             config_phoneblock_base_url(), fw);

    char auth[160];
    snprintf(auth, sizeof(auth), "Bearer %s", config_phoneblock_token());

    esp_http_client_config_t cfg = {
        .url               = url,
        .method            = HTTP_METHOD_POST,
        .crt_bundle_attach = esp_crt_bundle_attach,
        .timeout_ms        = CRASHREPORT_HTTP_TIMEOUT_MS,
        .auth_type         = HTTP_AUTH_TYPE_NONE,
    };
    esp_http_client_handle_t client = esp_http_client_init(&cfg);
    if (!client) {
        ESP_LOGE(TAG, "http client init failed");
        free(buf);
        vTaskDelete(NULL);
        return;
    }
    http_util_set_user_agent(client);
    esp_http_client_set_header(client, "Authorization", auth);
    esp_http_client_set_header(client, "Content-Type",
                               "application/octet-stream");
    esp_http_client_set_post_field(client, (const char *)buf, (int)size);

    ESP_LOGI(TAG, "POST %s (%zu bytes)", url, size);
    err = esp_http_client_perform(client);
    int status = (err == ESP_OK) ? esp_http_client_get_status_code(client) : 0;
    esp_http_client_cleanup(client);
    free(buf);

    if (err != ESP_OK) {
        ESP_LOGW(TAG, "upload transport: %s — keeping dump for next boot",
                 esp_err_to_name(err));
        vTaskDelete(NULL);
        return;
    }
    if (status >= 200 && status < 300) {
        ESP_LOGI(TAG, "coredump uploaded (HTTP %d) — erasing partition",
                 status);
        esp_core_dump_image_erase();
    } else if (status == 503) {
        // Server reachable but has no storage configured (JNDI
        // coredump/dir unset). Keep the dump and retry on the next
        // boot — the operator may flip it on later.
        ESP_LOGW(TAG, "upload: HTTP 503 (server has no coredump storage) "
                 "— keeping dump for next boot");
    } else {
        // 401/403: token rejected — server can't accept it for *us*,
        // and won't on the next boot either. 413: oversized — won't
        // change. 4xx generally: erase, otherwise we retry forever.
        ESP_LOGW(TAG, "upload: HTTP %d — erasing dump (server rejected)",
                 status);
        esp_core_dump_image_erase();
    }

    vTaskDelete(NULL);
}

void crashreport_upload_async(void)
{
    // 6 KB stack is enough for the libcurl-style esp_http_client +
    // mbedTLS handshake + small JSON-free body assembly. The 52 KB
    // dump itself lives on the heap, not on this task's stack.
    xTaskCreate(crashreport_task, "crashrep", 6144, NULL, 2, NULL);
}
