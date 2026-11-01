#define _GNU_SOURCE

#include "web_http.h"

#include <microhttpd.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <unistd.h>

// Request bodies are buffered before the login check, so the cap
// (WEB_HTTP_MAX_BODY, web_http.h) bounds what any LAN client can make the
// service hold: the largest regular upload is the announcement
// (ANNOUNCEMENT_MAX_BYTES, 240 KB). Only a route that names a larger
// max_body (the dialog set, STORAGE_DIALOG_PLAIN_FS_MAX) takes more; the
// connection limit multiplies the largest into the worst case (32 MB).
// Responses are the service's own data, so their cap (WEB_HTTP_MAX_RESPONSE)
// only guards against a runaway handler: the largest is a dialog set
// (GET /api/dialog).
#define WEB_HTTP_MAX_CONNECTIONS 16U

// web.c keeps request state in plain statics (the OAuth nonce, the pending
// Fritz!Box 2FA handshake), written for the ESP32's single httpd task. Run
// the handlers one at a time here too; the connections themselves (and the
// buffering of their bodies) stay parallel.
static pthread_mutex_t s_handler_lock = PTHREAD_MUTEX_INITIALIZER;

typedef struct {
    char *name;
    char *value;
} response_header_t;

struct web_http_request {
    struct MHD_Connection *connection;
    const web_http_route_t *route;
    char *url;
    char *method;
    char *body;
    size_t body_length;
    size_t body_capacity;
    size_t body_offset;
    char *response_body;
    size_t response_length;
    size_t response_capacity;
    // web_http_send_fd(): the body is this file (0 = none, else fd + 1, so a
    // calloc'ed request has none), streamed by libmicrohttpd.
    int response_fd1;
    size_t response_fd_size;
    response_header_t headers[32];
    size_t header_count;
    unsigned status;
    bool have_content_length;
    bool initialized;
    size_t max_body;                  // of the route, or WEB_HTTP_MAX_BODY
    unsigned long long refused_length; // the declared length of a 413
    web_http_refused_t on_refused;
};

struct web_http_server {
    struct MHD_Daemon *daemon;
    const web_http_route_t *routes;
    size_t route_count;
    web_http_refused_t on_body_refused;
};

typedef struct {
    web_http_work_t work;
    void *argument;
} queued_work_t;

static void request_free(web_http_request_t *request)
{
    if (!request) return;
    for (size_t index = 0; index < request->header_count; index++) {
        free(request->headers[index].name);
        free(request->headers[index].value);
    }
    free(request->response_body);
    if (request->response_fd1) close(request->response_fd1 - 1);
    free(request->body);
    free(request->method);
    free(request->url);
    free(request);
}

static enum MHD_Result add_header(void *argument, enum MHD_ValueKind kind,
                                  const char *key, const char *value)
{
    (void)kind;
    web_http_request_t *request = argument;
    if (strcasecmp(key, MHD_HTTP_HEADER_CONTENT_LENGTH) != 0) return MHD_YES;
    // A second Content-Length is a framing ambiguity (request smuggling
    // through a proxy); refuse it rather than pick one.
    if (request->have_content_length) {
        request->status = 400;
        return MHD_NO;
    }
    request->have_content_length = true;
    char *end = NULL;
    unsigned long long length = strtoull(value, &end, 10);
    if (!end || *end || length > request->max_body) {
        request->status = end && !*end ? 413 : 400;
        request->refused_length = length;
        return MHD_NO;
    }
    request->body_capacity = (size_t)length;
    if (request->body_capacity) {
        request->body = malloc(request->body_capacity);
        if (!request->body) {
            request->status = 500;
            return MHD_NO;
        }
    }
    return MHD_YES;
}

static const web_http_route_t *find_route(const web_http_server_t *server,
                                         const char *url, const char *method)
{
    web_http_method_t requested;
    if (strcmp(method, MHD_HTTP_METHOD_GET) == 0) {
        requested = WEB_HTTP_GET;
    } else if (strcmp(method, MHD_HTTP_METHOD_POST) == 0) {
        requested = WEB_HTTP_POST;
    } else if (strcmp(method, MHD_HTTP_METHOD_OPTIONS) == 0) {
        requested = WEB_HTTP_OPTIONS;
    } else {
        return NULL;
    }

    for (size_t index = 0; index < server->route_count; index++) {
        const web_http_route_t *route = &server->routes[index];
        if (route->method == requested && strcmp(route->uri, url) == 0)
            return route;
    }
    if (requested == WEB_HTTP_OPTIONS && server->routes[server->route_count - 1].method == WEB_HTTP_OPTIONS
            && strcmp(server->routes[server->route_count - 1].uri, "/*") == 0) {
        return &server->routes[server->route_count - 1];
    }
    return NULL;
}

static void *capture_raw_uri(void *context, const char *uri,
                             struct MHD_Connection *connection)
{
    (void)context;
    (void)connection;
    web_http_request_t *request = calloc(1, sizeof(*request));
    if (!request) return NULL;
    request->url = strdup(uri);
    if (!request->url) {
        request_free(request);
        return NULL;
    }
    return request;
}

static int reserve_body(web_http_request_t *request, size_t needed)
{
    if (needed > request->max_body) return -1;
    if (needed <= request->body_capacity) return 0;
    size_t capacity = request->body_capacity ? request->body_capacity : 1024;
    while (capacity < needed) {
        if (capacity > request->max_body / 2) {
            capacity = request->max_body;
            break;
        }
        capacity *= 2;
    }
    char *body = realloc(request->body, capacity);
    if (!body) return -1;
    request->body = body;
    request->body_capacity = capacity;
    return 0;
}

static int reserve_response(web_http_request_t *request, size_t needed)
{
    if (needed > WEB_HTTP_MAX_RESPONSE) return -1;
    if (needed <= request->response_capacity) return 0;
    size_t capacity = request->response_capacity ? request->response_capacity : 1024;
    while (capacity < needed) {
        if (capacity > WEB_HTTP_MAX_RESPONSE / 2) {
            capacity = WEB_HTTP_MAX_RESPONSE;
            break;
        }
        capacity *= 2;
    }
    char *body = realloc(request->response_body, capacity);
    if (!body) return -1;
    request->response_body = body;
    request->response_capacity = capacity;
    return 0;
}

static int queue_request_response(web_http_request_t *request)
{
    struct MHD_Response *response;
    if (request->response_fd1) {
        // libmicrohttpd owns the descriptor from here on and closes it.
        response = MHD_create_response_from_fd(request->response_fd_size,
                                               request->response_fd1 - 1);
        if (response) request->response_fd1 = 0;
    } else {
        response = MHD_create_response_from_buffer(
            request->response_length, request->response_body, MHD_RESPMEM_MUST_COPY);
    }
    if (!response) return MHD_NO;
    for (size_t index = 0; index < request->header_count; index++) {
        if (MHD_add_response_header(response, request->headers[index].name,
                                    request->headers[index].value) != MHD_YES) {
            MHD_destroy_response(response);
            return MHD_NO;
        }
    }
    enum MHD_Result result = MHD_queue_response(request->connection,
                                                 request->status,
                                                 response);
    MHD_destroy_response(response);
    return result;
}

// Answers a request whose body add_header() or the upload refused. A body
// too large gets the JSON shape of web.c's refusals, and the owner's
// on_body_refused hook logs it (a WARN on the device's log panel).
static enum MHD_Result queue_rejection(web_http_request_t *request)
{
    if (request->status == 413) {
        if (request->on_refused) {
            request->on_refused(request->url, request->refused_length,
                                request->max_body);
        }
        char json[192];
        snprintf(json, sizeof(json),
                 "{\"ok\":false,\"code\":\"too_large\",\"message\":"
                 "\"The request body has %llu bytes; this endpoint takes at "
                 "most %zu.\"}\n", request->refused_length, request->max_body);
        if (web_http_set_type(request, "application/json") != 0
                || web_http_send_text(request, json) != 0) {
            return MHD_NO;
        }
        return queue_request_response(request);
    }
    const char *message = request->status == 500 ? "Out of memory\n"
        : "Invalid request body length\n";
    if (web_http_send_text(request, message) != 0) return MHD_NO;
    return queue_request_response(request);
}

static enum MHD_Result access_request(void *context,
                                     struct MHD_Connection *connection,
                                     const char *url, const char *method,
                                     const char *version, const char *upload_data,
                                     size_t *upload_data_size, void **request_context)
{
    (void)version;
    web_http_server_t *server = context;
    web_http_request_t *request = *request_context;

    if (!request) {
        request = calloc(1, sizeof(*request));
        if (!request) return MHD_NO;
        request->url = strdup(url);
        if (!request->url) {
            request_free(request);
            return MHD_NO;
        }
        *request_context = request;
    }

    if (!request->initialized) {
        request->connection = connection;
        request->method = strdup(method);
        request->status = 200;
        request->route = find_route(server, url, method);
        request->max_body = request->route && request->route->max_body
            ? request->route->max_body : WEB_HTTP_MAX_BODY;
        request->on_refused = server->on_body_refused;
        if (!request->method) return MHD_NO;
        if (MHD_get_connection_values(connection, MHD_HEADER_KIND,
                                      add_header, request) < 0) {
            return MHD_NO;
        }
        request->initialized = true;
        // Refuse a bad or oversized body now, before any of it is read;
        // MHD then closes the connection instead of draining the upload.
        if (request->status != 200) return queue_rejection(request);
        return MHD_YES;
    }

    if (*upload_data_size) {
        size_t size = *upload_data_size;
        if (request->status == 413 || request->status == 400
                || request->status == 500) {
            *upload_data_size = 0;
            return MHD_YES;
        }
        if (request->body_length + size > request->max_body) {
            // No (or a lying) Content-Length: refused while streaming.
            request->status = 413;
            request->refused_length = request->body_length + size;
        } else if (reserve_body(request, request->body_length + size) != 0) {
            request->status = 500;
        } else {
            memcpy(request->body + request->body_length, upload_data, size);
            request->body_length += size;
        }
        *upload_data_size = 0;
        return MHD_YES;
    }

    if (request->status != 200) return queue_rejection(request);

    if (!request->route) {
        web_http_set_status(request, "404 Not Found");
        web_http_set_type(request, "text/plain; charset=utf-8");
        web_http_send_text(request, "Not found\n");
    } else {
        pthread_mutex_lock(&s_handler_lock);
        request->route->handler(request);
        pthread_mutex_unlock(&s_handler_lock);
    }
    return queue_request_response(request);
}

static void request_completed(void *context, struct MHD_Connection *connection,
                              void **request_context,
                              enum MHD_RequestTerminationCode reason)
{
    (void)context;
    (void)connection;
    (void)reason;
    request_free(*request_context);
    *request_context = NULL;
}

static void *run_work(void *argument)
{
    queued_work_t *queued = argument;
    queued->work(queued->argument);
    free(queued);
    return NULL;
}

web_http_error_t web_http_server_start(web_http_server_t **server_out,
                                       const web_http_server_config_t *config,
                                       const web_http_route_t *routes,
                                       size_t route_count)
{
    if (!server_out || !config || !routes || !route_count)
        return -1;
    web_http_server_t *server = calloc(1, sizeof(*server));
    if (!server) return -1;
    server->routes = routes;
    server->route_count = route_count;
    server->on_body_refused = config->on_body_refused;
    struct sockaddr_storage bind_address = {0};
    unsigned flags = MHD_USE_INTERNAL_POLLING_THREAD
                   | MHD_USE_THREAD_PER_CONNECTION;
    if (config->bind_address && strchr(config->bind_address, ':')) {
        struct sockaddr_in6 *address = (struct sockaddr_in6 *)&bind_address;
        address->sin6_family = AF_INET6;
        address->sin6_port = htons(config->port);
        if (inet_pton(AF_INET6, config->bind_address, &address->sin6_addr) != 1) {
            free(server);
            return WEB_HTTP_FAIL;
        }
        flags |= MHD_USE_IPv6;
    } else {
        struct sockaddr_in *address = (struct sockaddr_in *)&bind_address;
        address->sin_family = AF_INET;
        address->sin_port = htons(config->port);
        address->sin_addr.s_addr = htonl(INADDR_ANY);
        if (config->bind_address
                && inet_pton(AF_INET, config->bind_address,
                             &address->sin_addr) != 1) {
            free(server);
            return WEB_HTTP_FAIL;
        }
    }
    server->daemon = MHD_start_daemon(
        flags,
        config->port, NULL, NULL, access_request, server,
        MHD_OPTION_SOCK_ADDR,
        (const struct sockaddr *)&bind_address,
        MHD_OPTION_URI_LOG_CALLBACK, capture_raw_uri, server,
        MHD_OPTION_NOTIFY_COMPLETED, request_completed, NULL,
        MHD_OPTION_CONNECTION_TIMEOUT, 30U,
        MHD_OPTION_CONNECTION_LIMIT, WEB_HTTP_MAX_CONNECTIONS,
        MHD_OPTION_END);
    if (!server->daemon) {
        free(server);
        return -1;
    }
    *server_out = server;
    return 0;
}

void web_http_server_stop(web_http_server_t *server)
{
    if (!server) return;
    if (server->daemon) MHD_stop_daemon(server->daemon);
    free(server);
}

uint16_t web_http_server_port(web_http_server_t *server)
{
    if (!server || !server->daemon) return 0;
    const union MHD_DaemonInfo *info = MHD_get_daemon_info(
        server->daemon, MHD_DAEMON_INFO_BIND_PORT);
    return info ? info->port : 0;
}

web_http_error_t web_http_server_queue_work(web_http_server_t *server,
                                            web_http_work_t work, void *argument)
{
    (void)server;
    if (!work) return -1;
    queued_work_t *queued = malloc(sizeof(*queued));
    if (!queued) return -1;
    queued->work = work;
    queued->argument = argument;
    pthread_t thread;
    int error = pthread_create(&thread, NULL, run_work, queued);
    if (error != 0) {
        free(queued);
        return -1;
    }
    pthread_detach(thread);
    return 0;
}

static int append_header(web_http_request_t *request, const char *name,
                         const char *value)
{
    if (request->header_count == sizeof(request->headers) / sizeof(request->headers[0]))
        return -1;
    response_header_t *header = &request->headers[request->header_count];
    header->name = strdup(name);
    header->value = strdup(value);
    if (!header->name || !header->value) {
        free(header->name);
        free(header->value);
        return -1;
    }
    request->header_count++;
    return 0;
}

// Like httpd_resp_set_type() on the ESP32: a later call replaces the type,
// so an error response sent after a handler already set one does not go out
// with two Content-Type headers.
int web_http_set_type(web_http_request_t *request, const char *content_type)
{
    for (size_t index = 0; index < request->header_count; index++) {
        response_header_t *header = &request->headers[index];
        if (strcasecmp(header->name, MHD_HTTP_HEADER_CONTENT_TYPE) != 0) continue;
        char *value = strdup(content_type);
        if (!value) return -1;
        free(header->value);
        header->value = value;
        return 0;
    }
    return append_header(request, MHD_HTTP_HEADER_CONTENT_TYPE, content_type);
}

int web_http_set_header(web_http_request_t *request, const char *name,
                        const char *value)
{
    return append_header(request, name, value);
}

int web_http_set_status(web_http_request_t *request, const char *status)
{
    unsigned code = (unsigned)strtoul(status, NULL, 10);
    if (code < 100 || code > 599) return -1;
    request->status = code;
    return 0;
}

int web_http_send(web_http_request_t *request, const void *body, size_t length)
{
    if (length == WEB_HTTP_RESP_USE_STRLEN) {
        if (!body) return -1;
        length = strlen(body);
    }
    if (!request || (!body && length) || reserve_response(request, length) != 0)
        return -1;
    if (length) memcpy(request->response_body, body, length);
    request->response_length = length;
    return 0;
}

int web_http_send_text(web_http_request_t *request, const char *body)
{
    return web_http_send(request, body, strlen(body));
}

int web_http_send_chunk(web_http_request_t *request, const void *body,
                        size_t length)
{
    if (!request || (!body && length)
            || reserve_response(request, request->response_length + length) != 0)
        return -1;
    if (length) memcpy(request->response_body + request->response_length, body, length);
    request->response_length += length;
    return 0;
}

int web_http_send_fd(web_http_request_t *request, int fd, size_t size)
{
    if (!request || fd < 0) {
        if (fd >= 0) close(fd);
        return -1;
    }
    if (request->response_fd1) close(request->response_fd1 - 1);
    request->response_fd1 = fd + 1;
    request->response_fd_size = size;
    request->response_length = 0;
    return 0;
}

int web_http_send_error(web_http_request_t *request, int status,
                        const char *message)
{
    char *body = NULL;
    int length = asprintf(&body, "%s\n", message ? message : "");
    if (length < 0) return -1;
    web_http_set_type(request, "text/plain; charset=utf-8");
    web_http_set_status(request, "500 Internal Server Error");
    request->status = (unsigned)status;
    int result = web_http_send(request, body, (size_t)length);
    free(body);
    return result;
}

static int copy_value(const char *source, char *value, size_t capacity)
{
    if (!source || !value || !capacity) return -1;
    size_t length = strlen(source);
    if (length >= capacity) return -1;
    memcpy(value, source, length + 1);
    return 0;
}

int web_http_get_header(web_http_request_t *request, const char *name,
                        char *value, size_t capacity)
{
    return copy_value(MHD_lookup_connection_value(request->connection,
                                                   MHD_HEADER_KIND, name),
                      value, capacity);
}

bool web_http_has_header(web_http_request_t *request, const char *name)
{
    return MHD_lookup_connection_value(request->connection, MHD_HEADER_KIND,
                                       name) != NULL;
}

int web_http_get_query_string(web_http_request_t *request, char *query,
                              size_t capacity)
{
    if (!request || !query || !capacity) return -1;
    const char *question = strchr(request->url, '?');
    if (!question) return -1;
    question++;
    size_t length = strcspn(question, "#");
    if (length >= capacity) return -1;
    memcpy(query, question, length);
    query[length] = '\0';
    return 0;
}

int web_http_query_value(const char *query, const char *name, char *value,
                         size_t capacity)
{
    if (!query || !name || !value || !capacity) return -1;
    size_t name_length = strlen(name);
    const char *item = query;
    while (*item) {
        const char *end = strchr(item, '&');
        size_t item_length = end ? (size_t)(end - item) : strlen(item);
        const char *equals = memchr(item, '=', item_length);
        size_t key_length = equals ? (size_t)(equals - item) : item_length;
        if (key_length == name_length && memcmp(item, name, name_length) == 0) {
            const char *value_start = equals ? equals + 1 : item + item_length;
            size_t value_length = item_length - (size_t)(value_start - item);
            if (value_length >= capacity) return -1;
            memcpy(value, value_start, value_length);
            value[value_length] = '\0';
            return 0;
        }
        if (!end) break;
        item = end + 1;
    }
    return -1;
}

size_t web_http_body_length(web_http_request_t *request)
{
    return request ? request->body_length : 0;
}

int web_http_read_body(web_http_request_t *request, void *buffer, size_t length)
{
    if (!request || !buffer || length > request->body_length - request->body_offset)
        return -1;
    memcpy(buffer, request->body + request->body_offset, length);
    request->body_offset += length;
    return (int)length;
}

int web_http_request_to_sockfd(web_http_request_t *request)
{
    const union MHD_ConnectionInfo *info = MHD_get_connection_info(
        request->connection, MHD_CONNECTION_INFO_CONNECTION_FD);
    return info ? info->connect_fd : -1;
}
