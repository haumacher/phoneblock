#include "platform.h"

#include <stdlib.h>
#include <string.h>

#include "esp_mac.h"
#include "esp_crt_bundle.h"
#include "esp_app_desc.h"
#include "esp_random.h"
#include "esp_http_client.h"
#include "esp_task_wdt.h"
#include "esp_tls.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/event_groups.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "mbedtls/base64.h"
#include "mbedtls/md5.h"
#include "mbedtls/pk.h"
#include "mbedtls/sha256.h"
#include "mbedtls/sha1.h"
#include "nvs.h"

struct pb_event {
    EventGroupHandle_t handle;
};

struct pb_tls {
    esp_tls_t *handle;
};

typedef struct {
    char *buffer;
    size_t capacity;
    size_t length;
    pb_http_data_fn on_data;
    void *data_context;
    uint64_t started_us;
    uint64_t connected_us;
    uint64_t headers_sent_us;
    uint64_t first_header_us;
    uint64_t finished_us;
} pb_http_response_buffer_t;

struct pb_mutex {
    SemaphoreHandle_t handle;
};

struct pb_kv {
    nvs_handle_t handle;
};

struct pb_sha256 {
    mbedtls_sha256_context context;
    bool finished;
};

uint64_t pb_monotonic_us(void)
{
    return (uint64_t)esp_timer_get_time();
}

const char *pb_firmware_version(void)
{
    const esp_app_desc_t *description = esp_app_get_description();
    return description ? description->version : "unknown";
}

uint32_t pb_random_u32(void)
{
    return esp_random();
}

bool pb_md5(const void *input, size_t input_len, uint8_t output[16])
{
    return input && output
        && mbedtls_md5(input, input_len, output) == 0;
}

bool pb_sha1(const void *input, size_t input_len, uint8_t output[20])
{
    return input && output
        && mbedtls_sha1(input, input_len, output) == 0;
}

bool pb_ecdsa_p256_verify(const uint8_t *public_key_der, size_t public_key_len,
                          const uint8_t hash[32], const uint8_t *signature,
                          size_t signature_len)
{
    if (!public_key_der || !hash || !signature || signature_len == 0) return false;
    mbedtls_pk_context key;
    mbedtls_pk_init(&key);
    int result = mbedtls_pk_parse_public_key(&key, public_key_der, public_key_len);
    if (result == 0 && mbedtls_pk_can_do(&key, MBEDTLS_PK_ECDSA)
            && mbedtls_pk_get_bitlen(&key) == 256) {
        result = mbedtls_pk_verify(&key, MBEDTLS_MD_SHA256, hash, 32,
                                   signature, signature_len);
    } else {
        result = -1;
    }
    mbedtls_pk_free(&key);
    return result == 0;
}

pb_sha256_t *pb_sha256_create(void)
{
    pb_sha256_t *context = calloc(1, sizeof(*context));
    if (!context) return NULL;
    mbedtls_sha256_init(&context->context);
    if (mbedtls_sha256_starts(&context->context, 0) != 0) {
        mbedtls_sha256_free(&context->context);
        free(context);
        return NULL;
    }
    return context;
}

bool pb_sha256_update(pb_sha256_t *context, const void *data, size_t length)
{
    return context && !context->finished && data
        && mbedtls_sha256_update(&context->context, data, length) == 0;
}

bool pb_sha256_finish(pb_sha256_t *context, uint8_t output[32])
{
    if (!context || !output || context->finished) return false;
    context->finished = true;
    return mbedtls_sha256_finish(&context->context, output) == 0;
}

void pb_sha256_destroy(pb_sha256_t *context)
{
    if (!context) return;
    mbedtls_sha256_free(&context->context);
    free(context);
}

int pb_base64_encode(unsigned char *dst, size_t dst_len, size_t *output_len,
                     const unsigned char *src, size_t src_len)
{
    return mbedtls_base64_encode(dst, dst_len, output_len, src, src_len);
}

int pb_base64_decode(unsigned char *dst, size_t dst_len, size_t *output_len,
                     const unsigned char *src, size_t src_len)
{
    return mbedtls_base64_decode(dst, dst_len, output_len, src, src_len);
}

void pb_random_fill(void *out, size_t len)
{
    esp_fill_random(out, len);
}

int pb_watchdog_is_subscribed(void)
{
    return esp_task_wdt_status(NULL) == ESP_OK;
}

void pb_watchdog_subscribe(void)
{
    esp_task_wdt_add(NULL);
}

void pb_watchdog_reset(void)
{
    esp_task_wdt_reset();
}

int pb_task_stack_free(void)
{
    // ESP-IDF reports the high-water mark in bytes (StackType_t is uint8_t).
    return (int)uxTaskGetStackHighWaterMark(NULL);
}

void pb_task_sleep_ms(uint32_t milliseconds)
{
    vTaskDelay(pdMS_TO_TICKS(milliseconds));
}

void pb_task_yield(void)
{
    taskYIELD();
}

void pb_deadline_init(pb_deadline_t *deadline)
{
    deadline->at = xTaskGetTickCount();
}

void pb_task_delay_until(pb_deadline_t *deadline, uint32_t interval_ms)
{
    TickType_t at = (TickType_t)deadline->at;
    vTaskDelayUntil(&at, pdMS_TO_TICKS(interval_ms));
    deadline->at = at;
}

typedef struct {
    void (*fn)(void *);
    void *arg;
} pb_task_start_t;

static void pb_task_start(void *opaque)
{
    pb_task_start_t start = *(pb_task_start_t *)opaque;
    free(opaque);
    start.fn(start.arg);
    vTaskDelete(NULL);
}

bool pb_task_create(void (*fn)(void *), void *arg, const char *name,
                    size_t stack_bytes, pb_task_prio_t prio)
{
    if (!fn) return false;

    pb_task_start_t *start = malloc(sizeof(*start));
    if (!start) return false;
    start->fn = fn;
    start->arg = arg;

    // The values the tasks had before the platform layer: SIP 5, RTP 6 —
    // media above protocol work, so frames keep their pace while the SIP
    // task sits in a synchronous HTTPS lookup.
    UBaseType_t priority = prio == PB_PRIO_MEDIA ? 6
                         : prio == PB_PRIO_BACKGROUND ? 3 : 5;

    // ESP-IDF's FreeRTOS takes the stack depth in bytes (StackType_t is
    // uint8_t), matching the byte budget the caller passes.
    if (xTaskCreate(pb_task_start, name ? name : "phoneblock",
                    (configSTACK_DEPTH_TYPE)stack_bytes, start, priority,
                    NULL) != pdPASS) {
        free(start);
        return false;
    }
    return true;
}

pb_event_t *pb_event_create(void)
{
    pb_event_t *event = calloc(1, sizeof(*event));
    if (!event) return NULL;
    event->handle = xEventGroupCreate();
    if (!event->handle) {
        free(event);
        return NULL;
    }
    return event;
}

void pb_event_destroy(pb_event_t *event)
{
    if (!event) return;
    vEventGroupDelete(event->handle);
    free(event);
}

void pb_event_set(pb_event_t *event, uint32_t bits)
{
    if (event) xEventGroupSetBits(event->handle, bits);
}

uint32_t pb_event_wait(pb_event_t *event, uint32_t timeout_ms)
{
    if (!event) return 0;
    uint64_t ticks = ((uint64_t)timeout_ms * configTICK_RATE_HZ + 999u) / 1000u;
    return (uint32_t)xEventGroupWaitBits(event->handle, 0x00ffffffu, pdTRUE,
                                         pdFALSE, (TickType_t)ticks);
}

pb_tls_t *pb_tls_connect(const char *host, int port, const char *server_name,
                         uint32_t timeout_ms)
{
    if (!host || !host[0] || port <= 0) return NULL;
    pb_tls_t *tls = calloc(1, sizeof(*tls));
    if (!tls) return NULL;
    tls->handle = esp_tls_init();
    if (!tls->handle) {
        free(tls);
        return NULL;
    }

    esp_tls_cfg_t config = {
        .crt_bundle_attach = esp_crt_bundle_attach,
        .timeout_ms = (int)timeout_ms,
        .common_name = server_name && server_name[0] ? server_name : NULL,
    };
    int rc = esp_tls_conn_new_sync(host, (int)strlen(host), port, &config,
                                   tls->handle);
    if (rc != 1) {
        esp_tls_conn_destroy(tls->handle);
        free(tls);
        return NULL;
    }
    return tls;
}

int pb_tls_fd(pb_tls_t *tls)
{
    int fd = -1;
    return tls && esp_tls_get_conn_sockfd(tls->handle, &fd) == ESP_OK
         ? fd : -1;
}

int pb_tls_pending(pb_tls_t *tls)
{
    return tls ? (int)esp_tls_get_bytes_avail(tls->handle) : 0;
}

int pb_tls_read(pb_tls_t *tls, void *buffer, size_t capacity)
{
    if (!tls || !buffer) return -1;
    ssize_t result = esp_tls_conn_read(tls->handle, buffer, capacity);
    if (result == ESP_TLS_ERR_SSL_WANT_READ) return PB_TLS_WANT_READ;
    if (result == ESP_TLS_ERR_SSL_WANT_WRITE) return PB_TLS_WANT_WRITE;
    return result > INT_MAX ? -1 : (int)result;
}

int pb_tls_write(pb_tls_t *tls, const void *buffer, size_t length)
{
    if (!tls || (!buffer && length)) return -1;
    ssize_t result = esp_tls_conn_write(tls->handle, buffer, length);
    if (result == ESP_TLS_ERR_SSL_WANT_READ) return PB_TLS_WANT_READ;
    if (result == ESP_TLS_ERR_SSL_WANT_WRITE) return PB_TLS_WANT_WRITE;
    return result > INT_MAX ? -1 : (int)result;
}

void pb_tls_destroy(pb_tls_t *tls)
{
    if (!tls) return;
    esp_tls_conn_destroy(tls->handle);
    free(tls);
}

static esp_err_t pb_http_event(esp_http_client_event_t *event)
{
    pb_http_response_buffer_t *response = event->user_data;
    if (!response) return ESP_OK;
    uint64_t now = pb_monotonic_us();
    switch (event->event_id) {
    case HTTP_EVENT_ON_CONNECTED:
        response->connected_us = now;
        break;
    case HTTP_EVENT_HEADERS_SENT:
        response->headers_sent_us = now;
        break;
    case HTTP_EVENT_ON_HEADER:
        if (!response->first_header_us) response->first_header_us = now;
        break;
    case HTTP_EVENT_ON_FINISH:
        response->finished_us = now;
        break;
    case HTTP_EVENT_ON_DATA: {
        if (response->on_data)
            response->on_data(event->data, (size_t)event->data_len,
                              response->data_context);

        if (response->buffer) {
            size_t available = response->capacity > response->length + 1
                             ? response->capacity - response->length - 1 : 0;
            size_t copied = (size_t)event->data_len < available
                          ? (size_t)event->data_len : available;
            if (copied > 0) {
                memcpy(response->buffer + response->length, event->data, copied);
                response->length += copied;
                response->buffer[response->length] = '\0';
            }
        }
        break;
    }
    default:
        break;
    }
    return ESP_OK;
}

int pb_http_request(const char *method, const char *url,
                    const pb_http_header_t *headers, size_t header_count,
                    const void *body, size_t body_len, uint32_t timeout_ms,
                    char *response, size_t response_cap,
            size_t *response_len, int *http_status,
            pb_http_data_fn on_data, void *data_context,
            pb_http_timing_t *timing)
{
    if (!method || !url || (!response && !on_data)
        || (response && response_cap == 0)
            || (!body && body_len)) return PB_HTTP_ERROR;
    if (response) response[0] = '\0';
    if (response_len) *response_len = 0;
    if (http_status) *http_status = 0;
    if (timing) memset(timing, 0, sizeof(*timing));

    pb_http_response_buffer_t output = {
        .buffer = response,
        .capacity = response_cap,
    .on_data = on_data,
    .data_context = data_context,
    .started_us = pb_monotonic_us(),
    };
    esp_http_client_config_t config = {
        .url = url,
        .event_handler = pb_http_event,
        .user_data = &output,
        .timeout_ms = (int)timeout_ms,
    };
    esp_http_client_handle_t client = esp_http_client_init(&config);
    if (!client) return PB_HTTP_ERROR;

    esp_http_client_method_t http_method = HTTP_METHOD_GET;
    if (strcmp(method, "POST") == 0) http_method = HTTP_METHOD_POST;
    else if (strcmp(method, "PUT") == 0) http_method = HTTP_METHOD_PUT;
    else if (strcmp(method, "DELETE") == 0) http_method = HTTP_METHOD_DELETE;
    esp_http_client_set_method(client, http_method);
    esp_http_client_set_header(client, "User-Agent", "phoneblock-dongle");
    for (size_t i = 0; i < header_count; i++) {
        if (headers[i].name && headers[i].value)
            esp_http_client_set_header(client, headers[i].name,
                                       headers[i].value);
    }
    if (body || body_len)
        esp_http_client_set_post_field(client, body ? body : "", (int)body_len);

    esp_err_t result = esp_http_client_perform(client);
    uint64_t finished = pb_monotonic_us();
    if (http_status)
        *http_status = esp_http_client_get_status_code(client);
    if (response_len) *response_len = output.length;
    if (timing) {
        uint64_t connected = output.connected_us;
        uint64_t headers = output.headers_sent_us;
        uint64_t first_header = output.first_header_us;
        uint64_t finished_at = output.finished_us ? output.finished_us : finished;
        timing->connect_us = connected > output.started_us
                           ? connected - output.started_us : 0;
        timing->request_us = headers > connected ? headers - connected : 0;
        timing->wait_us = first_header > headers ? first_header - headers : 0;
        timing->download_us = finished_at > first_header
                            ? finished_at - first_header : 0;
        timing->total_us = finished > output.started_us
                         ? finished - output.started_us : 0;
        timing->valid = result == ESP_OK;
    }
    esp_http_client_cleanup(client);
    if (result == ESP_OK) return PB_HTTP_OK;
    if (result == ESP_ERR_HTTP_CONNECT) return PB_HTTP_CONNECT_ERROR;
    return PB_HTTP_ERROR;
}

int pb_kv_open(const char *name_space, bool writable, pb_kv_t **store)
{
    if (!name_space || !store) return PB_KV_ERROR;
    *store = NULL;
    pb_kv_t *result = malloc(sizeof(*result));
    if (!result) return PB_KV_ERROR;
    esp_err_t err = nvs_open(name_space,
                             writable ? NVS_READWRITE : NVS_READONLY,
                             &result->handle);
    if (err != ESP_OK) {
        free(result);
        return err == ESP_ERR_NVS_NOT_FOUND ? PB_KV_NOT_FOUND : PB_KV_ERROR;
    }
    *store = result;
    return PB_KV_OK;
}

void pb_kv_close(pb_kv_t *store)
{
    if (!store) return;
    nvs_close(store->handle);
    free(store);
}

int pb_kv_get_string(pb_kv_t *store, const char *key,
                     char *value, size_t *value_len)
{
    if (!store || !key || !value || !value_len) return PB_KV_ERROR;
    esp_err_t err = nvs_get_str(store->handle, key, value, value_len);
    if (err == ESP_OK) return PB_KV_OK;
    return err == ESP_ERR_NVS_NOT_FOUND ? PB_KV_NOT_FOUND : PB_KV_ERROR;
}

int pb_kv_get_i32(pb_kv_t *store, const char *key, int32_t *value)
{
    if (!store || !key || !value) return PB_KV_ERROR;
    esp_err_t err = nvs_get_i32(store->handle, key, value);
    if (err == ESP_OK) return PB_KV_OK;
    return err == ESP_ERR_NVS_NOT_FOUND ? PB_KV_NOT_FOUND : PB_KV_ERROR;
}

int pb_kv_set_string(pb_kv_t *store, const char *key, const char *value)
{
    if (!store || !key || !value) return PB_KV_ERROR;
    return nvs_set_str(store->handle, key, value) == ESP_OK
         ? PB_KV_OK : PB_KV_ERROR;
}

int pb_kv_set_i32(pb_kv_t *store, const char *key, int32_t value)
{
    if (!store || !key) return PB_KV_ERROR;
    return nvs_set_i32(store->handle, key, value) == ESP_OK
         ? PB_KV_OK : PB_KV_ERROR;
}

int pb_kv_erase_key(pb_kv_t *store, const char *key)
{
    if (!store || !key) return PB_KV_ERROR;
    esp_err_t err = nvs_erase_key(store->handle, key);
    return err == ESP_OK || err == ESP_ERR_NVS_NOT_FOUND
         ? PB_KV_OK : PB_KV_ERROR;
}

int pb_kv_erase_all(pb_kv_t *store)
{
    if (!store) return PB_KV_ERROR;
    return nvs_erase_all(store->handle) == ESP_OK ? PB_KV_OK : PB_KV_ERROR;
}

int pb_kv_commit(pb_kv_t *store)
{
    if (!store) return PB_KV_ERROR;
    return nvs_commit(store->handle) == ESP_OK ? PB_KV_OK : PB_KV_ERROR;
}

pb_mutex_t *pb_mutex_create(void)
{
    pb_mutex_t *mutex = calloc(1, sizeof(*mutex));
    if (!mutex) return NULL;
    mutex->handle = xSemaphoreCreateMutex();
    if (!mutex->handle) {
        free(mutex);
        return NULL;
    }
    return mutex;
}

void pb_mutex_lock(pb_mutex_t *mutex)
{
    if (mutex) xSemaphoreTake(mutex->handle, portMAX_DELAY);
}

void pb_mutex_unlock(pb_mutex_t *mutex)
{
    if (mutex) xSemaphoreGive(mutex->handle);
}

int pb_get_mac(uint8_t mac[6])
{
    if (!mac) return -1;
    return esp_read_mac(mac, ESP_MAC_WIFI_STA) == ESP_OK ? 0 : -1;
}
