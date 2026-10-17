#ifndef ESP_PLATFORM

#include "api_http_linux.h"

#include <stdlib.h>
#include <string.h>

struct pb_api_http_client {
    const char *url;
    const char *body;
    size_t body_len;
    int method;
    int timeout_ms;
    int status;
    esp_http_client_event_handler_t event_handler;
    void *user_data;
    pb_http_header_t headers[8];
    size_t header_count;
    pb_http_timing_t timing;
};

esp_http_client_handle_t esp_http_client_init(const esp_http_client_config_t *config)
{
    if (!config) return NULL;
    esp_http_client_handle_t client = calloc(1, sizeof(*client));
    if (!client) return NULL;
    client->url = config->url;
    client->method = config->method;
    client->timeout_ms = config->timeout_ms > 0 ? config->timeout_ms : 10000;
    client->event_handler = config->event_handler;
    client->user_data = config->user_data;
    return client;
}

void esp_http_client_set_url(esp_http_client_handle_t client, const char *url)
{
    if (client) client->url = url;
}

void esp_http_client_set_method(esp_http_client_handle_t client, int method)
{
    if (client) client->method = method;
}

void esp_http_client_set_user_data(esp_http_client_handle_t client, void *user_data)
{
    if (client) client->user_data = user_data;
}

void esp_http_client_set_header(esp_http_client_handle_t client,
                                const char *name, const char *value)
{
    if (!client || !name || !value) return;
    for (size_t i = 0; i < client->header_count; i++) {
        if (strcmp(client->headers[i].name, name) == 0) {
            client->headers[i].value = value;
            return;
        }
    }
    if (client->header_count < sizeof(client->headers) / sizeof(client->headers[0])) {
        client->headers[client->header_count++] = (pb_http_header_t){ name, value };
    }
}

void esp_http_client_set_post_field(esp_http_client_handle_t client,
                                    const char *body, int body_len)
{
    if (!client) return;
    client->body = body;
    client->body_len = body_len > 0 ? (size_t)body_len : 0;
}

static void deliver_data(const void *data, size_t length, void *context)
{
    esp_http_client_handle_t client = context;
    if (!client->event_handler) return;
    esp_http_client_event_t event = {
        .event_id = HTTP_EVENT_ON_DATA,
        .user_data = client->user_data,
        .data = (char *)data,
        .data_len = (int)length,
    };
    client->event_handler(&event);
}

esp_err_t esp_http_client_perform(esp_http_client_handle_t client)
{
    if (!client || !client->url) return PB_HTTP_ERROR;
    return pb_http_request(client->method == HTTP_METHOD_POST ? "POST" : "GET",
                           client->url, client->headers, client->header_count,
                           client->body, client->body_len,
                           (uint32_t)client->timeout_ms, NULL, 0, NULL,
                           &client->status, deliver_data, client, &client->timing);
}

int esp_http_client_get_status_code(esp_http_client_handle_t client)
{
    return client ? client->status : 0;
}

void esp_http_client_close(esp_http_client_handle_t client)
{
    (void)client;
}

void esp_http_client_cleanup(esp_http_client_handle_t client)
{
    free(client);
}

void api_http_linux_get_timing(esp_http_client_handle_t client,
                               pb_http_timing_t *timing)
{
    if (timing) {
        if (client) *timing = client->timing;
        else memset(timing, 0, sizeof(*timing));
    }
}

#endif
