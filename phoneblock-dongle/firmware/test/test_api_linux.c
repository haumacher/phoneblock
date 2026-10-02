#define _POSIX_C_SOURCE 200809L

#include <assert.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <unistd.h>

#include "api.h"
#include "config.h"
#include "stats.h"

static int server_fd;

static void *serve_test_endpoint(void *unused)
{
    (void)unused;
    for (int request_number = 0; request_number < 2; request_number++) {
        int client = accept(server_fd, NULL, NULL);
        assert(client >= 0);
        char request[2048] = {0};
        ssize_t received = recv(client, request, sizeof(request) - 1, 0);
        assert(received > 0);
        assert(strstr(request, "Authorization: Bearer linux-api-test") != NULL);
        const char *body;
        const char *content_type;
        if (request_number == 0) {
            assert(strstr(request, "GET /api/test ") != NULL);
            body = "ok";
            content_type = "text/plain";
        } else {
            assert(strstr(request, "GET /api/check-prefix?sha1=") != NULL);
            body = "{\"numbers\":[{\"phone\":\"+49301234567\",\"votes\":4,\"label\":\"Loopback\",\"location\":\"Test\"}],\"range10\":[],\"range100\":[]}";
            content_type = "application/json";
        }
        char response[512];
        int response_len = snprintf(response, sizeof(response),
            "HTTP/1.1 200 OK\r\nContent-Type: %s\r\nContent-Length: %zu\r\n"
            "Connection: close\r\n\r\n%s", content_type, strlen(body), body);
        assert(response_len > 0 && (size_t)response_len < sizeof(response));
        assert(send(client, response, (size_t)response_len, 0) == response_len);
        close(client);
    }
    return NULL;
}

int main(void)
{
    const char *config_path = "/tmp/phoneblock-api-linux-test.conf";
    unlink(config_path);
    assert(setenv("PHONEBLOCK_CONFIG_FILE", config_path, 1) == 0);
    config_load();

    server_fd = socket(AF_INET, SOCK_STREAM, 0);
    assert(server_fd >= 0);
    struct sockaddr_in address = {
        .sin_family = AF_INET,
        .sin_addr.s_addr = htonl(INADDR_LOOPBACK),
        .sin_port = 0,
    };
    assert(bind(server_fd, (struct sockaddr *)&address, sizeof(address)) == 0);
    assert(listen(server_fd, 1) == 0);
    socklen_t address_len = sizeof(address);
    assert(getsockname(server_fd, (struct sockaddr *)&address, &address_len) == 0);

    char base_url[64];
    snprintf(base_url, sizeof(base_url), "http://127.0.0.1:%u",
             (unsigned)ntohs(address.sin_port));
    config_update_t update = {
        .phoneblock_base_url = base_url,
        .phoneblock_token = "linux-api-test",
    };
    assert(config_update(&update) == 0);

    stats_setup();
    phoneblock_api_init();
    pthread_t server_thread;
    assert(pthread_create(&server_thread, NULL, serve_test_endpoint, NULL) == 0);

    api_phases_t phases = {0};
    assert(phoneblock_selftest(&phases));
    assert(phases.valid);
    assert(phases.total_us > 0);
    pb_check_result_t result;
    assert(phoneblock_check("+49301234567", &result, NULL) == VERDICT_SPAM);
    assert(result.direct_votes == 4);
    assert(strcmp(result.label, "Loopback") == 0);
    assert(pthread_join(server_thread, NULL) == 0);

    close(server_fd);
    assert(config_erase() == 0);
    unlink(config_path);
    unsetenv("PHONEBLOCK_CONFIG_FILE");
    puts("test_api_linux: all tests passed");
    return 0;
}
