#pragma once

#ifndef ESP_PLATFORM

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "platform.h"

typedef int esp_err_t;
typedef struct pb_api_http_client *esp_http_client_handle_t;
typedef struct esp_http_client_event esp_http_client_event_t;
typedef esp_err_t (*esp_http_client_event_handler_t)(esp_http_client_event_t *);

enum {
    HTTP_METHOD_GET,
    HTTP_METHOD_POST,
    HTTP_AUTH_TYPE_NONE,
    ESP_HTTP_CLIENT_TLS_VER_TLS_1_2,
};

typedef enum {
    HTTP_EVENT_ON_CONNECTED,
    HTTP_EVENT_HEADERS_SENT,
    HTTP_EVENT_ON_HEADER,
    HTTP_EVENT_ON_FINISH,
    HTTP_EVENT_ON_DATA,
} esp_http_client_event_id_t;

struct esp_http_client_event {
    esp_http_client_event_id_t event_id;
    void *user_data;
    char *data;
    int data_len;
};

typedef struct {
    const char *url;
    int method;
    esp_http_client_event_handler_t event_handler;
    void *user_data;
    int timeout_ms;
    int auth_type;
    int tls_version;
    bool save_client_session;
    void *crt_bundle_attach;
} esp_http_client_config_t;

#define ESP_OK PB_HTTP_OK
#define ESP_LOGI(tag, ...) pb_log_info(tag, __VA_ARGS__)
#define ESP_LOGW(tag, ...) pb_log_warn(tag, __VA_ARGS__)
#define ESP_LOGE(tag, ...) pb_log_err(tag, __VA_ARGS__)
#define esp_timer_get_time() ((int64_t)pb_monotonic_us())
#define esp_err_to_name(error) ((error) == PB_HTTP_CONNECT_ERROR ? "connect" : "HTTP")
#define portMAX_DELAY 0
#define xSemaphoreCreateMutex() pb_mutex_create()
#define xSemaphoreTake(mutex, timeout) ((void)(timeout), pb_mutex_lock(mutex), 1)
#define xSemaphoreGive(mutex) (pb_mutex_unlock(mutex), 1)
#define esp_crt_bundle_attach NULL
#define http_util_set_user_agent(client) ((void)(client))

esp_http_client_handle_t esp_http_client_init(const esp_http_client_config_t *config);
void esp_http_client_set_url(esp_http_client_handle_t client, const char *url);
void esp_http_client_set_method(esp_http_client_handle_t client, int method);
void esp_http_client_set_user_data(esp_http_client_handle_t client, void *user_data);
void esp_http_client_set_header(esp_http_client_handle_t client,
                                const char *name, const char *value);
void esp_http_client_set_post_field(esp_http_client_handle_t client,
                                    const char *body, int body_len);
esp_err_t esp_http_client_perform(esp_http_client_handle_t client);
int esp_http_client_get_status_code(esp_http_client_handle_t client);
void esp_http_client_close(esp_http_client_handle_t client);
void esp_http_client_cleanup(esp_http_client_handle_t client);
void api_http_linux_get_timing(esp_http_client_handle_t client,
                               pb_http_timing_t *timing);

#endif
