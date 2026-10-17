#include "platform.h"

#include <limits.h>
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

#include "ticks_util.h"

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

int pb_md5(const void *input, size_t input_len, uint8_t output[16])
{
    return input && output
        && mbedtls_md5(input, input_len, output) == 0 ? PB_OK : PB_FAIL;
}

int pb_sha1(const void *input, size_t input_len, uint8_t output[20])
{
    return input && output
        && mbedtls_sha1(input, input_len, output) == 0 ? PB_OK : PB_FAIL;
}

int pb_ecdsa_p256_verify(const uint8_t *public_key_der, size_t public_key_len,
                         const uint8_t hash[32], const uint8_t *signature,
                         size_t signature_len, int *native)
{
    if (native) *native = 0;
    if (!public_key_der || !hash || !signature || signature_len == 0)
        return PB_SIG_MISMATCH;
    mbedtls_pk_context key;
    mbedtls_pk_init(&key);
    int verdict = PB_SIG_VALID;
    int rc = mbedtls_pk_parse_public_key(&key, public_key_der, public_key_len);
    if (rc != 0 || !mbedtls_pk_can_do(&key, MBEDTLS_PK_ECDSA)
            || mbedtls_pk_get_bitlen(&key) != 256) {
        verdict = PB_SIG_BAD_KEY;
    } else {
        rc = mbedtls_pk_verify(&key, MBEDTLS_MD_SHA256, hash, 32,
                               signature, signature_len);
        if (rc != 0) verdict = PB_SIG_MISMATCH;
    }
    mbedtls_pk_free(&key);
    if (native) *native = rc;
    return verdict;
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

int pb_sha256_update(pb_sha256_t *context, const void *data, size_t length)
{
    return context && !context->finished && data
        && mbedtls_sha256_update(&context->context, data, length) == 0
        ? PB_OK : PB_FAIL;
}

int pb_sha256_finish(pb_sha256_t *context, uint8_t output[32])
{
    if (!context || !output || context->finished) return PB_FAIL;
    context->finished = true;
    return mbedtls_sha256_finish(&context->context, output) == 0
        ? PB_OK : PB_FAIL;
}

void pb_sha256_destroy(pb_sha256_t *context)
{
    if (!context) return;
    mbedtls_sha256_free(&context->context);
    free(context);
}

static int base64_result(int rc)
{
    if (rc == 0) return PB_OK;
    if (rc == MBEDTLS_ERR_BASE64_BUFFER_TOO_SMALL) return PB_ERR_INVALID_SIZE;
    return PB_FAIL;
}

int pb_base64_encode(unsigned char *dst, size_t dst_len, size_t *output_len,
                     const unsigned char *src, size_t src_len)
{
    return base64_result(
        mbedtls_base64_encode(dst, dst_len, output_len, src, src_len));
}

int pb_base64_decode(unsigned char *dst, size_t dst_len, size_t *output_len,
                     const unsigned char *src, size_t src_len)
{
    return base64_result(
        mbedtls_base64_decode(dst, dst_len, output_len, src, src_len));
}

void pb_random_fill(void *out, size_t len)
{
    esp_fill_random(out, len);
}

bool pb_watchdog_is_subscribed(void)
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

int pb_task_create(void (*fn)(void *), void *arg, const char *name,
                    size_t stack_bytes, pb_task_prio_t prio)
{
    if (!fn) return PB_FAIL;

    pb_task_start_t *start = malloc(sizeof(*start));
    if (!start) return PB_FAIL;
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
        return PB_FAIL;
    }
    return PB_OK;
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

uint32_t pb_event_wait_s(pb_event_t *event, uint32_t timeout_s)
{
    if (!event) return 0;
    return (uint32_t)xEventGroupWaitBits(event->handle, 0x00ffffffu, pdTRUE,
                                         pdFALSE, seconds_to_ticks(timeout_s));
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
        // Only consulted for https:// URLs (blocklist / Fritz!Box phonebook
        // downloads); plain-HTTP TR-064 on the LAN ignores it.
        .crt_bundle_attach = esp_crt_bundle_attach,
        .timeout_ms = (int)timeout_ms,
    };
    esp_http_client_handle_t client = esp_http_client_init(&config);
    if (!client) return PB_HTTP_ERROR;

    esp_http_client_method_t http_method = HTTP_METHOD_GET;
    if (strcmp(method, "POST") == 0) http_method = HTTP_METHOD_POST;
    else if (strcmp(method, "PUT") == 0) http_method = HTTP_METHOD_PUT;
    else if (strcmp(method, "DELETE") == 0) http_method = HTTP_METHOD_DELETE;
    esp_http_client_set_method(client, http_method);
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
    // esp_err_t is the native result (ESP_OK == PB_HTTP_OK,
    // ESP_FAIL == PB_HTTP_ERROR), see platform.h.
    return (int)result;
}

const char *pb_http_err_name(int result)
{
    return esp_err_to_name((esp_err_t)result);
}

bool pb_http_is_connect_error(int result)
{
    return result == ESP_ERR_HTTP_CONNECT;
}

// The stream is the esp_http_client handle itself: no wrapper allocation,
// and the calls below are exactly the open/fetch_headers/read/close sequence
// the downloads used before the platform layer existed.
int pb_http_stream_open(const char *url,
                        const pb_http_header_t *headers, size_t header_count,
                        uint32_t timeout_ms, pb_http_stream_t **stream,
                        int *http_status, int64_t *content_length)
{
    if (stream) *stream = NULL;
    if (http_status) *http_status = 0;
    if (content_length) *content_length = 0;
    if (!url || !stream) return PB_HTTP_ERROR;

    esp_http_client_config_t config = {
        .url               = url,
        .method            = HTTP_METHOD_GET,
        .crt_bundle_attach = esp_crt_bundle_attach,
        .timeout_ms        = (int)timeout_ms,
        .tls_version       = ESP_HTTP_CLIENT_TLS_VER_TLS_1_2,
    };
    esp_http_client_handle_t client = esp_http_client_init(&config);
    if (!client) return PB_HTTP_ERROR;
    for (size_t i = 0; i < header_count; i++) {
        if (headers[i].name && headers[i].value)
            esp_http_client_set_header(client, headers[i].name,
                                       headers[i].value);
    }

    esp_err_t result = esp_http_client_open(client, 0);
    if (result != ESP_OK) {
        esp_http_client_cleanup(client);
        return (int)result;
    }
    int64_t length = esp_http_client_fetch_headers(client);
    if (http_status) *http_status = esp_http_client_get_status_code(client);
    if (content_length) *content_length = length;
    *stream = (pb_http_stream_t *)client;
    return PB_HTTP_OK;
}

int pb_http_stream_read(pb_http_stream_t *stream, void *buffer,
                        size_t capacity)
{
    if (!stream || !buffer || capacity == 0) return -1;
    if (capacity > INT_MAX) capacity = INT_MAX;
    return esp_http_client_read((esp_http_client_handle_t)stream, buffer,
                                (int)capacity);
}

void pb_http_stream_close(pb_http_stream_t *stream)
{
    if (!stream) return;
    esp_http_client_handle_t client = (esp_http_client_handle_t)stream;
    esp_http_client_close(client);
    esp_http_client_cleanup(client);
}

// NVS results map onto the PB_KV_* contract: ESP_OK is PB_KV_OK,
// ESP_ERR_NVS_NOT_FOUND becomes PB_KV_NOT_FOUND, and every other esp_err_t
// is passed through unchanged (none of them equals PB_KV_NOT_FOUND), so
// pb_kv_err_name() can report the actual NVS error.
static int kv_result(esp_err_t err)
{
    return err == ESP_ERR_NVS_NOT_FOUND ? PB_KV_NOT_FOUND : (int)err;
}

const char *pb_kv_err_name(int result)
{
    return esp_err_to_name(result == PB_KV_NOT_FOUND ? ESP_ERR_NVS_NOT_FOUND
                                                      : (esp_err_t)result);
}

int pb_kv_open(const char *name_space, bool writable, pb_kv_t **store)
{
    if (!name_space || !store) return PB_KV_ERROR;
    *store = NULL;
    pb_kv_t *result = malloc(sizeof(*result));
    if (!result) return ESP_ERR_NO_MEM;
    esp_err_t err = nvs_open(name_space,
                             writable ? NVS_READWRITE : NVS_READONLY,
                             &result->handle);
    if (err != ESP_OK) {
        free(result);
        return kv_result(err);
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
    return kv_result(nvs_get_str(store->handle, key, value, value_len));
}

int pb_kv_get_i32(pb_kv_t *store, const char *key, int32_t *value)
{
    if (!store || !key || !value) return PB_KV_ERROR;
    return kv_result(nvs_get_i32(store->handle, key, value));
}

int pb_kv_set_string(pb_kv_t *store, const char *key, const char *value)
{
    if (!store || !key || !value) return PB_KV_ERROR;
    return kv_result(nvs_set_str(store->handle, key, value));
}

int pb_kv_set_i32(pb_kv_t *store, const char *key, int32_t value)
{
    if (!store || !key) return PB_KV_ERROR;
    return kv_result(nvs_set_i32(store->handle, key, value));
}

int pb_kv_erase_key(pb_kv_t *store, const char *key)
{
    if (!store || !key) return PB_KV_ERROR;
    return kv_result(nvs_erase_key(store->handle, key));
}

int pb_kv_erase_all(pb_kv_t *store)
{
    if (!store) return PB_KV_ERROR;
    return kv_result(nvs_erase_all(store->handle));
}

int pb_kv_commit(pb_kv_t *store)
{
    if (!store) return PB_KV_ERROR;
    return kv_result(nvs_commit(store->handle));
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
