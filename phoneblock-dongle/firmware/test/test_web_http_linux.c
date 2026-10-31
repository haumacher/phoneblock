#define _POSIX_C_SOURCE 200809L

#include "web_http.h"

#include <arpa/inet.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

#include "linux_test_util.h"

static web_http_error_t handle_health(web_http_request_t *request)
{
    REQUIRE(web_http_set_type(request, "text/plain; charset=utf-8") == 0);
    REQUIRE(web_http_set_header(request, "Cache-Control", "no-store") == 0);
    return web_http_send_text(request, "ok\n");
}

static web_http_error_t handle_text(web_http_request_t *request)
{
    return web_http_send(request, "sentinel body", WEB_HTTP_RESP_USE_STRLEN);
}

static web_http_error_t handle_echo(web_http_request_t *request)
{
    size_t length = web_http_body_length(request);
    char *body = malloc(length + 1);
    REQUIRE(body != NULL);
    REQUIRE(web_http_read_body(request, body, length) == (int)length);
    body[length] = '\0';
    REQUIRE(web_http_set_type(request, "text/plain; charset=utf-8") == 0);
    int result = web_http_send(request, body, length);
    free(body);
    return result;
}

static web_http_error_t handle_query(web_http_request_t *request)
{
    char query[256];
    char value[128];
    REQUIRE(web_http_get_query_string(request, query, sizeof(query)) == 0);
    REQUIRE(web_http_query_value(query, "token", value, sizeof(value)) == 0);
    REQUIRE(web_http_set_type(request, "text/plain; charset=utf-8") == 0);
    return web_http_send_text(request, value);
}

// A later web_http_set_type() replaces the type, like on the ESP32.
static web_http_error_t handle_retype(web_http_request_t *request)
{
    REQUIRE(web_http_set_type(request, "text/html; charset=utf-8") == 0);
    REQUIRE(web_http_set_type(request, "application/json") == 0);
    return web_http_send_text(request, "{}");
}

static web_http_error_t handle_has_header(web_http_request_t *request)
{
    return web_http_send_text(request,
        web_http_has_header(request, "X-Ingress-Path") ? "yes" : "no");
}

// Records how many handlers ever ran at the same time: web.c relies on one
// handler at a time, as on the ESP32's single httpd task.
static atomic_int s_running;
static atomic_int s_max_running;

static web_http_error_t handle_slow(web_http_request_t *request)
{
    int now = atomic_fetch_add(&s_running, 1) + 1;
    int max = atomic_load(&s_max_running);
    while (now > max && !atomic_compare_exchange_weak(&s_max_running, &max, now)) {}
    struct timespec pause = { .tv_sec = 0, .tv_nsec = 100 * 1000 * 1000 };
    nanosleep(&pause, NULL);
    atomic_fetch_sub(&s_running, 1);
    return web_http_send_text(request, "slow");
}

// Answers the length of the body it got (a route with a larger max_body).
static web_http_error_t handle_length(web_http_request_t *request)
{
    char text[32];
    snprintf(text, sizeof(text), "got %zu", web_http_body_length(request));
    return web_http_send_text(request, text);
}

#define BIG_MAX (512U * 1024U)

// What on_body_refused last reported.
static char s_refused_uri[64];
static unsigned long long s_refused_length;
static size_t s_refused_max;
static int s_refused_calls;

static void on_refused(const char *uri, unsigned long long length, size_t max)
{
    snprintf(s_refused_uri, sizeof(s_refused_uri), "%s", uri);
    s_refused_length = length;
    s_refused_max = max;
    s_refused_calls++;
}

static const web_http_route_t routes[] = {
    { "/big", WEB_HTTP_POST, handle_length, BIG_MAX },
    { "/slow", WEB_HTTP_GET, handle_slow, 0 },
    { "/health", WEB_HTTP_GET, handle_health, 0 },
    { "/text", WEB_HTTP_GET, handle_text, 0 },
    { "/echo", WEB_HTTP_POST, handle_echo, 0 },
    { "/query", WEB_HTTP_GET, handle_query, 0 },
    { "/retype", WEB_HTTP_GET, handle_retype, 0 },
    { "/has-header", WEB_HTTP_GET, handle_has_header, 0 },
};

// Number of occurrences of `needle` in `haystack`.
static int count(const char *haystack, const char *needle)
{
    int n = 0;
    for (const char *p = strstr(haystack, needle); p; p = strstr(p + 1, needle))
        n++;
    return n;
}

static size_t request(uint16_t port, const char *message,
                      char *response, size_t capacity)
{
    int client = socket(AF_INET, SOCK_STREAM, 0);
    REQUIRE(client >= 0);
    struct sockaddr_in address = {
        .sin_family = AF_INET,
        .sin_addr.s_addr = htonl(INADDR_LOOPBACK),
        .sin_port = htons(port),
    };
    REQUIRE(connect(client, (struct sockaddr *)&address, sizeof(address)) == 0);

    size_t message_length = strlen(message);
    size_t sent = 0;
    while (sent < message_length) {
        ssize_t count = send(client, message + sent, message_length - sent, 0);
        REQUIRE(count > 0);
        sent += (size_t)count;
    }

    size_t received = 0;
    while (received + 1 < capacity) {
        ssize_t count = recv(client, response + received,
                             capacity - received - 1, 0);
        if (count == 0) break;
        REQUIRE(count > 0);
        received += (size_t)count;
    }
    response[received] = '\0';
    close(client);
    return received;
}

typedef struct {
    uint16_t port;
    char response[1024];
} slow_client_t;

static void *slow_client(void *argument)
{
    slow_client_t *client = argument;
    request(client->port,
            "GET /slow HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n",
            client->response, sizeof(client->response));
    return NULL;
}

int main(void)
{
    web_http_server_config_t config = {
        .port = 0,
        .bind_address = "127.0.0.1",
        .on_body_refused = on_refused,
    };
    web_http_server_t *server = NULL;
    REQUIRE(web_http_server_start(&server, &config, routes,
                                 sizeof(routes) / sizeof(routes[0])) == 0);
    uint16_t port = web_http_server_port(server);
    REQUIRE(port != 0);

    char response[4096];
    REQUIRE(request(port,
                   "GET /health HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n",
                   response, sizeof(response)) > 0);
    REQUIRE(strstr(response, "200 OK") != NULL);
    REQUIRE(strstr(response, "Content-Type: text/plain; charset=utf-8") != NULL);
    REQUIRE(strstr(response, "Cache-Control: no-store") != NULL);
    REQUIRE(strstr(response, "\r\n\r\nok\n") != NULL);

    REQUIRE(request(port,
                   "GET /text HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n",
                   response, sizeof(response)) > 0);
    REQUIRE(strstr(response, "200 OK") != NULL);
    REQUIRE(strstr(response, "\r\n\r\nsentinel body") != NULL);

    const char post[] = "hello from browser";
    char post_request[512];
    snprintf(post_request, sizeof(post_request),
             "POST /echo HTTP/1.1\r\nHost: localhost\r\n"
             "Content-Length: %zu\r\nConnection: close\r\n\r\n%s",
             sizeof(post) - 1, post);
    REQUIRE(request(port, post_request, response, sizeof(response)) > 0);
    REQUIRE(strstr(response, "200 OK") != NULL);
    REQUIRE(strstr(response, post) != NULL);

    REQUIRE(request(port,
                   "GET /query?token=abc%2Fdef HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n",
                   response, sizeof(response)) > 0);
    REQUIRE(strstr(response, "abc%2Fdef") != NULL);

    REQUIRE(request(port,
                   "GET /missing HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n",
                   response, sizeof(response)) > 0);
    REQUIRE(strstr(response, "404 Not Found") != NULL);

    REQUIRE(request(port,
                   "GET /retype HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n",
                   response, sizeof(response)) > 0);
    REQUIRE(count(response, "Content-Type:") == 1);
    REQUIRE(strstr(response, "Content-Type: application/json") != NULL);

    REQUIRE(request(port,
                   "GET /has-header HTTP/1.1\r\nHost: localhost\r\n"
                   "X-Ingress-Path: /api/hassio_ingress/abc\r\nConnection: close\r\n\r\n",
                   response, sizeof(response)) > 0);
    REQUIRE(strstr(response, "\r\n\r\nyes") != NULL);
    REQUIRE(request(port,
                   "GET /has-header HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n",
                   response, sizeof(response)) > 0);
    REQUIRE(strstr(response, "\r\n\r\nno") != NULL);

    // Parallel connections, but their handlers run one after the other.
    slow_client_t clients[3];
    pthread_t threads[3];
    for (int i = 0; i < 3; i++) {
        clients[i].port = port;
        REQUIRE(pthread_create(&threads[i], NULL, slow_client, &clients[i]) == 0);
    }
    for (int i = 0; i < 3; i++) {
        REQUIRE(pthread_join(threads[i], NULL) == 0);
        REQUIRE(strstr(clients[i].response, "\r\n\r\nslow") != NULL);
    }
    REQUIRE(atomic_load(&s_max_running) == 1);

    // A body over the cap is refused from its Content-Length, before any
    // of it is buffered or a handler runs.
    REQUIRE(request(port,
                   "POST /echo HTTP/1.1\r\nHost: localhost\r\n"
                   "Content-Length: 1048576\r\nConnection: close\r\n\r\n",
                   response, sizeof(response)) > 0);
    REQUIRE(strstr(response, "413") != NULL);
    // ...as JSON naming the reason, and reported to the owner (its WARN).
    REQUIRE(strstr(response, "Content-Type: application/json") != NULL);
    REQUIRE(strstr(response, "\"code\":\"too_large\"") != NULL);
    REQUIRE(strstr(response, "1048576 bytes") != NULL);
    REQUIRE(s_refused_calls == 1);
    REQUIRE(strcmp(s_refused_uri, "/echo") == 0);
    REQUIRE(s_refused_length == 1048576ULL && s_refused_max == WEB_HTTP_MAX_BODY);

    // A route with a larger max_body takes a body over the default cap...
    {
        size_t n = WEB_HTTP_MAX_BODY + 4096;
        char head[160];
        int hl = snprintf(head, sizeof(head), "POST /big HTTP/1.1\r\nHost: localhost\r\n"
                          "Content-Length: %zu\r\nConnection: close\r\n\r\n", n);
        char *msg = malloc((size_t)hl + n + 1);
        REQUIRE(msg != NULL);
        memcpy(msg, head, (size_t)hl);
        memset(msg + hl, 'a', n);
        msg[(size_t)hl + n] = '\0';
        REQUIRE(request(port, msg, response, sizeof(response)) > 0);
        free(msg);
        char want[32];
        snprintf(want, sizeof(want), "\r\n\r\ngot %zu", n);
        REQUIRE(strstr(response, "200 OK") != NULL);
        REQUIRE(strstr(response, want) != NULL);
    }
    // ...but not one over its own.
    REQUIRE(request(port,
                   "POST /big HTTP/1.1\r\nHost: localhost\r\n"
                   "Content-Length: 524289\r\nConnection: close\r\n\r\n",
                   response, sizeof(response)) > 0);
    REQUIRE(strstr(response, "413") != NULL);
    REQUIRE(strstr(response, "\"code\":\"too_large\"") != NULL);
    REQUIRE(s_refused_calls == 2 && s_refused_max == BIG_MAX);

    // Two Content-Length headers: ambiguous framing, refused.
    REQUIRE(request(port,
                   "POST /echo HTTP/1.1\r\nHost: localhost\r\n"
                   "Content-Length: 2\r\nContent-Length: 2\r\n"
                   "Connection: close\r\n\r\nhi",
                   response, sizeof(response)) > 0);
    REQUIRE(strstr(response, "400") != NULL);
    REQUIRE(strstr(response, "\r\n\r\nhi") == NULL);

    web_http_server_stop(server);
    puts("test_web_http_linux: all tests passed");
    return 0;
}
