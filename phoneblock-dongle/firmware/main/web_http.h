#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define WEB_HTTP_RESP_USE_STRLEN SIZE_MAX
enum {
    WEB_HTTP_OK = 0,
    WEB_HTTP_FAIL = -1,
    WEB_HTTP_RECV_TIMEOUT = -2,
    WEB_HTTP_STATUS_BAD_REQUEST = 400,
    WEB_HTTP_STATUS_NOT_FOUND = 404,
    WEB_HTTP_STATUS_INTERNAL_SERVER_ERROR = 500,
    WEB_HTTP_STATUS_NOT_IMPLEMENTED = 501,
};

#ifdef ESP_PLATFORM
#include "esp_http_server.h"
typedef httpd_req_t web_http_request_t;
typedef esp_err_t web_http_error_t;
#else
typedef struct web_http_request web_http_request_t;
typedef int web_http_error_t;
#endif

typedef enum {
    WEB_HTTP_GET,
    WEB_HTTP_POST,
    WEB_HTTP_OPTIONS,
} web_http_method_t;

typedef web_http_error_t (*web_http_handler_t)(web_http_request_t *request);
typedef void (*web_http_work_t)(void *arg);

// Linux port: request bodies are buffered whole before the handler runs, up
// to WEB_HTTP_MAX_BODY unless the route names a larger max_body (the own
// dialog set). Responses are buffered up to WEB_HTTP_MAX_RESPONSE. The
// ESP32 streams both, so these limits and max_body do not apply there (its
// handlers check the length themselves).
#define WEB_HTTP_MAX_BODY     (256U * 1024U)
#define WEB_HTTP_MAX_RESPONSE (2U * 1024U * 1024U)

typedef struct {
    const char *uri;
    web_http_method_t method;
    web_http_handler_t handler;
    size_t max_body;   // Linux: largest request body; 0 = WEB_HTTP_MAX_BODY
} web_http_route_t;

// Linux: called (from the server thread) when a request body is refused for
// its size, before any of it is read; `max` is the route's limit. The
// client gets a 413 with a JSON {ok:false, code:"too_large", message}.
typedef void (*web_http_refused_t)(const char *uri, unsigned long long length,
                                   size_t max);

typedef struct web_http_server web_http_server_t;

typedef struct {
    uint16_t port;
    const char *bind_address;
    size_t max_uri_handlers;
    size_t worker_stack_size;
    bool wildcard_uri_match;
    bool lru_purge;
    web_http_refused_t on_body_refused;   // Linux only; NULL = none
} web_http_server_config_t;

web_http_error_t web_http_server_start(web_http_server_t **server,
                                       const web_http_server_config_t *config,
                                       const web_http_route_t *routes,
                                       size_t route_count);
void web_http_server_stop(web_http_server_t *server);
uint16_t web_http_server_port(web_http_server_t *server);
web_http_error_t web_http_server_queue_work(web_http_server_t *server,
                                            web_http_work_t work, void *arg);

int web_http_set_type(web_http_request_t *request, const char *content_type);
int web_http_set_header(web_http_request_t *request, const char *name,
                        const char *value);
int web_http_set_status(web_http_request_t *request, const char *status);
int web_http_send(web_http_request_t *request, const void *body, size_t length);
int web_http_send_text(web_http_request_t *request, const char *body);
int web_http_send_chunk(web_http_request_t *request, const void *body,
                        size_t length);
int web_http_send_error(web_http_request_t *request, int status,
                        const char *message);
int web_http_get_header(web_http_request_t *request, const char *name,
                        char *value, size_t capacity);
// True if the request carries header `name`, whatever its length; needs no
// buffer, so a presence check costs no stack.
bool web_http_has_header(web_http_request_t *request, const char *name);
int web_http_get_query_string(web_http_request_t *request, char *query,
                              size_t capacity);
int web_http_query_value(const char *query, const char *name, char *value,
                         size_t capacity);
size_t web_http_body_length(web_http_request_t *request);
int web_http_read_body(web_http_request_t *request, void *buffer, size_t length);
int web_http_request_to_sockfd(web_http_request_t *request);
