#include "web_http.h"

#include <limits.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "call_rec_cfg.h"   // PB_CALL_RECORDING

struct web_http_server {
    httpd_handle_t handle;
    uint16_t port;
    web_http_route_t *routes;
    size_t route_count;
};

static esp_err_t dispatch_route(httpd_req_t *request)
{
    const web_http_route_t *route = request->user_ctx;
    return route->handler(request);
}

// The OPTIONS route "/*" (the PNA preflight) matches every URI, so a GET for
// a path no route serves (an iPhone asking for /apple-touch-icon.png) reaches
// httpd as "method not allowed". For GET/HEAD that is really "not found":
// answer a plain 404 without httpd_resp_send_err(), whose WARN would land in
// the log panel for every such request (#560). Other methods keep the 405.
static esp_err_t on_method_not_allowed(httpd_req_t *req, httpd_err_code_t err)
{
    if (req->method == HTTP_GET || req->method == HTTP_HEAD) {
        httpd_resp_set_status(req, HTTPD_404);
        httpd_resp_set_type(req, HTTPD_TYPE_TEXT);
        return httpd_resp_sendstr(req, "Not found");
    }
    httpd_resp_send_err(req, err, NULL);
    return ESP_FAIL;
}

web_http_error_t web_http_server_start(web_http_server_t **server_out,
                                       const web_http_server_config_t *config,
                                       const web_http_route_t *routes,
                                       size_t route_count)
{
    if (!server_out || !config || !routes || route_count == 0) {
        return ESP_ERR_INVALID_ARG;
    }
    *server_out = NULL;

    web_http_server_t *server = calloc(1, sizeof(*server));
    if (!server) return ESP_ERR_NO_MEM;
    server->routes = calloc(route_count, sizeof(*server->routes));
    if (!server->routes) {
        free(server);
        return ESP_ERR_NO_MEM;
    }
    memcpy(server->routes, routes, route_count * sizeof(*routes));
    server->route_count = route_count;
    server->port = config->port;

    httpd_config_t idf_config = HTTPD_DEFAULT_CONFIG();
    idf_config.server_port = config->port;
    idf_config.max_uri_handlers = config->max_uri_handlers;
    idf_config.stack_size = config->worker_stack_size;
    idf_config.lru_purge_enable = config->lru_purge;
    if (config->wildcard_uri_match) {
        idf_config.uri_match_fn = httpd_uri_match_wildcard;
    }
    esp_err_t result = httpd_start(&server->handle, &idf_config);
    if (result != ESP_OK) {
        free(server->routes);
        free(server);
        return result;
    }
    httpd_register_err_handler(server->handle, HTTPD_405_METHOD_NOT_ALLOWED,
                               on_method_not_allowed);

    for (size_t index = 0; index < route_count; index++) {
        httpd_method_t method;
        switch (routes[index].method) {
        case WEB_HTTP_GET: method = HTTP_GET; break;
        case WEB_HTTP_POST: method = HTTP_POST; break;
        case WEB_HTTP_OPTIONS: method = HTTP_OPTIONS; break;
        default:
            httpd_stop(server->handle);
            free(server->routes);
            free(server);
            return ESP_ERR_INVALID_ARG;
        }
        httpd_uri_t uri = {
            .uri = server->routes[index].uri,
            .method = method,
            .handler = dispatch_route,
            .user_ctx = &server->routes[index],
        };
        result = httpd_register_uri_handler(server->handle, &uri);
        if (result != ESP_OK) {
            httpd_stop(server->handle);
            free(server->routes);
            free(server);
            return result;
        }
    }

    *server_out = server;
    return ESP_OK;
}

void web_http_server_stop(web_http_server_t *server)
{
    if (!server) return;
    if (server->handle) httpd_stop(server->handle);
    free(server->routes);
    free(server);
}

uint16_t web_http_server_port(web_http_server_t *server)
{
    return server ? server->port : 0;
}

web_http_error_t web_http_server_queue_work(web_http_server_t *server,
                                            web_http_work_t work, void *arg)
{
    if (!server || !work) return ESP_ERR_INVALID_ARG;
    return httpd_queue_work(server->handle, work, arg);
}

int web_http_set_type(web_http_request_t *request, const char *content_type)
{
    return httpd_resp_set_type(request, content_type) == ESP_OK ? 0 : -1;
}

int web_http_set_header(web_http_request_t *request, const char *name,
                        const char *value)
{
    return httpd_resp_set_hdr(request, name, value) == ESP_OK ? 0 : -1;
}

int web_http_set_status(web_http_request_t *request, const char *status)
{
    httpd_resp_set_status(request, status);
    return 0;
}

int web_http_send(web_http_request_t *request, const void *body, size_t length)
{
    return httpd_resp_send(request, body, (ssize_t)length) == ESP_OK ? 0 : -1;
}

int web_http_send_text(web_http_request_t *request, const char *body)
{
    return web_http_send(request, body, strlen(body));
}

int web_http_send_chunk(web_http_request_t *request, const void *body,
                        size_t length)
{
    return httpd_resp_send_chunk(request, body, (ssize_t)length) == ESP_OK
        ? 0 : -1;
}

#if PB_CALL_RECORDING
int web_http_send_fd(web_http_request_t *request, int fd, size_t size)
{
    if (fd < 0) return -1;
    // On the heap: the httpd task's stack is small.
    enum { CHUNK = 2048 };
    uint8_t *buf = malloc(CHUNK);
    int rc = buf ? 0 : -1;
    while (rc == 0 && size > 0) {
        ssize_t n = read(fd, buf, size < CHUNK ? size : CHUNK);
        if (n <= 0) { rc = -1; break; }
        if (httpd_resp_send_chunk(request, (const char *)buf, n) != ESP_OK) rc = -1;
        size -= (size_t)n;
    }
    free(buf);
    close(fd);
    // Terminates the chunked answer; after a failure the client sees it cut.
    if (httpd_resp_send_chunk(request, NULL, 0) != ESP_OK) rc = -1;
    return rc;
}
#endif

int web_http_send_error(web_http_request_t *request, int status,
                        const char *message)
{
    if (status == 413) {
        httpd_resp_set_status(request, "413 Payload Too Large");
        return httpd_resp_sendstr(request, message) == ESP_OK ? 0 : -1;
    }
    httpd_err_code_t code = status == 400 ? HTTPD_400_BAD_REQUEST
        : status == 401 ? HTTPD_401_UNAUTHORIZED
        : status == 403 ? HTTPD_403_FORBIDDEN
        : status == 404 ? HTTPD_404_NOT_FOUND
        : status == 501 ? HTTPD_501_METHOD_NOT_IMPLEMENTED
        : HTTPD_500_INTERNAL_SERVER_ERROR;
    return httpd_resp_send_err(request, code, message) == ESP_OK ? 0 : -1;
}

int web_http_get_header(web_http_request_t *request, const char *name,
                        char *value, size_t capacity)
{
    return httpd_req_get_hdr_value_str(request, name, value, capacity) == ESP_OK
        ? 0 : -1;
}

bool web_http_has_header(web_http_request_t *request, const char *name)
{
    return httpd_req_get_hdr_value_len(request, name) > 0;
}

int web_http_get_query_string(web_http_request_t *request, char *query,
                              size_t capacity)
{
    if (capacity > INT_MAX) return -1;
    return httpd_req_get_url_query_str(request, query, (size_t)capacity) == ESP_OK
        ? 0 : -1;
}

int web_http_query_value(const char *query, const char *name, char *value,
                         size_t capacity)
{
    if (capacity > INT_MAX) return -1;
    return httpd_query_key_value(query, name, value, (size_t)capacity) == ESP_OK
        ? 0 : -1;
}

size_t web_http_body_length(web_http_request_t *request)
{
    return request ? request->content_len : 0;
}

int web_http_read_body(web_http_request_t *request, void *buffer, size_t length)
{
    if (length > INT_MAX) return -1;
    int result = httpd_req_recv(request, buffer, (int)length);
    return result == HTTPD_SOCK_ERR_TIMEOUT ? WEB_HTTP_RECV_TIMEOUT : result;
}

int web_http_request_to_sockfd(web_http_request_t *request)
{
    return httpd_req_to_sockfd(request);
}
