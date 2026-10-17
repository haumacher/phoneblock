#define _POSIX_C_SOURCE 200809L

#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <unistd.h>

#include "api.h"
#include "config.h"
#include "linux_test_util.h"
#include "stats.h"

static int server_fd;

// Reads one request: headers plus the Content-Length body, if any.
static void recv_request(int client, char *request, size_t cap)
{
    size_t got = 0;
    for (;;) {
        ssize_t n = recv(client, request + got, cap - 1 - got, 0);
        REQUIRE(n > 0);
        got += (size_t)n;
        request[got] = '\0';
        const char *end = strstr(request, "\r\n\r\n");
        if (!end) continue;
        const char *length = strstr(request, "Content-Length: ");
        size_t body = length ? (size_t)atoi(length + 16) : 0;
        if (got >= (size_t)(end + 4 - request) + body) return;
    }
}

// One expected request and the canned answer.
typedef struct {
    const char *request_line;   // must occur in the request
    const char *body_part;      // must occur in the request body, or NULL
    const char *status;         // e.g. "200 OK"
    const char *content_type;
    const char *body;
} exchange_t;

static const exchange_t EXCHANGES[] = {
    { "GET /api/test ", NULL, "200 OK", "text/plain", "ok" },
    { "GET /api/check-prefix?sha1=", NULL, "200 OK", "application/json",
      "{\"numbers\":[{\"phone\":\"+49301234567\",\"votes\":4,\"label\":\"Loopback\",\"location\":\"Test\"}],\"range10\":[],\"range100\":[]}" },
    { "POST /api/report-call/0049301234567 ", NULL, "204 No Content", "text/plain", "" },
    { "POST /api/rate ", "\"comment\":\"say \\\"hi\\\"\"", "200 OK", "application/json", "{}" },
    { "POST /api/dongle/log ", "W (1) sip: test line", "200 OK", "text/plain", "" },
    { "GET /api/test ", NULL, "401 Unauthorized", "text/plain", "bad token" },
};
#define EXCHANGE_COUNT (sizeof(EXCHANGES) / sizeof(EXCHANGES[0]))

static void *serve_test_endpoint(void *unused)
{
    (void)unused;
    for (size_t i = 0; i < EXCHANGE_COUNT; i++) {
        const exchange_t *x = &EXCHANGES[i];
        int client = accept(server_fd, NULL, NULL);
        REQUIRE(client >= 0);
        char request[4096];
        recv_request(client, request, sizeof(request));
        REQUIRE(strstr(request, "Authorization: Bearer linux-api-test") != NULL);
        REQUIRE(strstr(request, "User-Agent: PhoneBlock-Dongle/") != NULL);
        REQUIRE(strstr(request, x->request_line) != NULL);
        if (x->body_part) REQUIRE(strstr(request, x->body_part) != NULL);
        char response[512];
        int response_len = snprintf(response, sizeof(response),
            "HTTP/1.1 %s\r\nContent-Type: %s\r\nContent-Length: %zu\r\n"
            "Connection: close\r\n\r\n%s", x->status, x->content_type,
            strlen(x->body), x->body);
        REQUIRE(response_len > 0 && (size_t)response_len < sizeof(response));
        REQUIRE(send(client, response, (size_t)response_len, 0) == response_len);
        close(client);
    }
    return NULL;
}

int main(void)
{
    char config_path[256];
    use_temp_config(config_path, sizeof(config_path));
    config_load();

    server_fd = socket(AF_INET, SOCK_STREAM, 0);
    REQUIRE(server_fd >= 0);
    struct sockaddr_in address = {
        .sin_family = AF_INET,
        .sin_addr.s_addr = htonl(INADDR_LOOPBACK),
        .sin_port = 0,
    };
    REQUIRE(bind(server_fd, (struct sockaddr *)&address, sizeof(address)) == 0);
    REQUIRE(listen(server_fd, 1) == 0);
    socklen_t address_len = sizeof(address);
    REQUIRE(getsockname(server_fd, (struct sockaddr *)&address, &address_len) == 0);

    char base_url[64];
    snprintf(base_url, sizeof(base_url), "http://127.0.0.1:%u",
             (unsigned)ntohs(address.sin_port));
    config_update_t update = {
        .phoneblock_base_url = base_url,
        .phoneblock_token = "linux-api-test",
    };
    REQUIRE(config_update(&update) == 0);

    stats_setup();
    phoneblock_api_init();
    pthread_t server_thread;
    REQUIRE(pthread_create(&server_thread, NULL, serve_test_endpoint, NULL) == 0);

    api_phases_t phases = {0};
    REQUIRE(phoneblock_selftest(&phases));
    REQUIRE(phases.valid);
    REQUIRE(phases.total_us > 0);
    pb_check_result_t result;
    REQUIRE(phoneblock_check("+49301234567", &result, NULL) == VERDICT_SPAM);
    REQUIRE(result.direct_votes == 4);
    REQUIRE(strcmp(result.label, "Loopback") == 0);
    REQUIRE(phoneblock_report_call("+49301234567"));
    REQUIRE(phoneblock_rate("+49301234567", "B_MISSED", "say \"hi\""));
    static const char log_body[] = "W (1) sip: test line\n";
    REQUIRE(phoneblock_post_log(log_body, sizeof(log_body) - 1) == 200);
    REQUIRE(api_token_is_valid());
    // A rejected token fails the self-test and flips the token flag.
    REQUIRE(!phoneblock_selftest(NULL));
    REQUIRE(!api_token_is_valid());
    REQUIRE(pthread_join(server_thread, NULL) == 0);

    close(server_fd);
    REQUIRE(config_erase() == 0);
    unlink(config_path);
    unsetenv("PHONEBLOCK_CONFIG_FILE");
    puts("test_api_linux: all tests passed");
    return 0;
}
