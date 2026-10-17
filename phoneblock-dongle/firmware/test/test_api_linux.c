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

static void *serve_test_endpoint(void *unused)
{
    (void)unused;
    for (int request_number = 0; request_number < 2; request_number++) {
        int client = accept(server_fd, NULL, NULL);
        REQUIRE(client >= 0);
        char request[2048] = {0};
        ssize_t received = recv(client, request, sizeof(request) - 1, 0);
        REQUIRE(received > 0);
        REQUIRE(strstr(request, "Authorization: Bearer linux-api-test") != NULL);
        const char *body;
        const char *content_type;
        if (request_number == 0) {
            REQUIRE(strstr(request, "GET /api/test ") != NULL);
            body = "ok";
            content_type = "text/plain";
        } else {
            REQUIRE(strstr(request, "GET /api/check-prefix?sha1=") != NULL);
            body = "{\"numbers\":[{\"phone\":\"+49301234567\",\"votes\":4,\"label\":\"Loopback\",\"location\":\"Test\"}],\"range10\":[],\"range100\":[]}";
            content_type = "application/json";
        }
        char response[512];
        int response_len = snprintf(response, sizeof(response),
            "HTTP/1.1 200 OK\r\nContent-Type: %s\r\nContent-Length: %zu\r\n"
            "Connection: close\r\n\r\n%s", content_type, strlen(body), body);
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
    REQUIRE(pthread_join(server_thread, NULL) == 0);

    close(server_fd);
    REQUIRE(config_erase() == 0);
    unlink(config_path);
    unsetenv("PHONEBLOCK_CONFIG_FILE");
    puts("test_api_linux: all tests passed");
    return 0;
}
