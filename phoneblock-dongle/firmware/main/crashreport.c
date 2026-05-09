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
#include "esp_heap_caps.h"
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

// Body chunk for streaming flash → socket. Small on purpose: every
// extra byte we hold is a byte mbedTLS doesn't have for its bignum
// scratch during the cert-chain verification. ECDSA / RSA verify on
// a real-world Let's Encrypt chain wants ~30 KB of heap; if we keep
// our footprint to ~1 KB we leave ~80 KB clear at this point in boot.
#define CRASHREPORT_CHUNK_BYTES 1024

// Heap-free guard: if we don't have at least this much when we get
// to the upload, defer to the next boot rather than fail the TLS
// handshake with MBEDTLS_ERR_MPI_ALLOC_FAILED (which leaves the
// dump intact but adds noise to the dashboard). 64 KB is comfortable
// headroom for the cert-chain verify plus the streaming chunk plus
// esp_http_client / mbedTLS session state.
#define CRASHREPORT_MIN_FREE_HEAP (64 * 1024)

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
        vTaskDelete(NULL);
        return;
    }

    // Heap pressure check. The TLS handshake to phoneblock.net wants
    // ~30 KB of bignum scratch for ECDSA / RSA cert-chain verification;
    // failing that allocation surfaces as MBEDTLS_ERR_MPI_ALLOC_FAILED
    // (-0x10) deep inside the cert bundle code, after the request URL
    // is logged but before any bytes go on the wire. Defer rather than
    // fail noisily — the next boot, with whatever transient demand has
    // settled, will retry. 64 KB threshold leaves comfortable margin
    // above the observed handshake peak.
    size_t free_heap = esp_get_free_heap_size();
    if (free_heap < CRASHREPORT_MIN_FREE_HEAP) {
        ESP_LOGI(TAG, "heap %zu B below %d B — deferring upload to next boot",
                 free_heap, CRASHREPORT_MIN_FREE_HEAP);
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
        vTaskDelete(NULL);
        return;
    }
    http_util_set_user_agent(client);
    esp_http_client_set_header(client, "Authorization", auth);
    esp_http_client_set_header(client, "Content-Type",
                               "application/octet-stream");

    ESP_LOGI(TAG, "POST %s (%zu bytes, heap %zu B)", url, size, free_heap);

    // Open opens the TCP/TLS connection and emits the request line +
    // headers (Content-Length: size). The TLS handshake — and thus the
    // cert-chain verify that wants the heap — happens here.
    err = esp_http_client_open(client, (int)size);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "upload open: %s — keeping dump for next boot",
                 esp_err_to_name(err));
        esp_http_client_cleanup(client);
        vTaskDelete(NULL);
        return;
    }

    // Stream body: read CRASHREPORT_CHUNK_BYTES from flash, write to
    // socket, repeat. Total RAM held at any moment is one chunk —
    // never the whole dump.
    uint8_t chunk[CRASHREPORT_CHUNK_BYTES];
    size_t in_offset = offset - part->address;
    size_t remaining = size;
    bool ok = true;
    while (remaining > 0) {
        size_t n = remaining > sizeof(chunk) ? sizeof(chunk) : remaining;
        err = esp_partition_read(part, in_offset, chunk, n);
        if (err != ESP_OK) {
            ESP_LOGE(TAG, "partition_read at %zu: %s",
                     in_offset, esp_err_to_name(err));
            ok = false;
            break;
        }
        int written = esp_http_client_write(client, (const char *)chunk, n);
        if (written != (int)n) {
            ESP_LOGW(TAG, "write %d/%zu — aborting upload",
                     written, n);
            ok = false;
            break;
        }
        in_offset += n;
        remaining -= n;
    }

    int status = 0;
    if (ok) {
        int rcl = esp_http_client_fetch_headers(client);
        if (rcl < 0) {
            ESP_LOGW(TAG, "fetch_headers: %d — keeping dump for next boot", rcl);
            ok = false;
        } else {
            status = esp_http_client_get_status_code(client);
        }
    }
    esp_http_client_close(client);
    esp_http_client_cleanup(client);

    if (!ok) {
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
