#define _POSIX_C_SOURCE 200809L

// Wi-Fi transmit power setting through the real Linux web/config path.

#include <arpa/inet.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

#include "config.h"
#include "linux_test_util.h"
#include "web.h"

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

static void post_config(uint16_t port, const char *body,
                        char *response, size_t capacity)
{
    char message[512];
    int length = snprintf(message, sizeof(message),
        "POST /api/config HTTP/1.1\r\nHost: localhost\r\n"
        "Content-Type: application/x-www-form-urlencoded\r\n"
        "Content-Length: %zu\r\nConnection: close\r\n\r\n%s",
        strlen(body), body);
    REQUIRE(length > 0 && (size_t)length < sizeof(message));
    REQUIRE(request(port, message, response, capacity) > 0);
}

int main(void)
{
    char config_path[256];
    use_temp_config(config_path, sizeof(config_path));
    config_load();
    REQUIRE(config_wifi_tx_power_dbm() == 15);

    web_configure_listener(0, "127.0.0.1");
    web_start();
    uint16_t port = web_listening_port();
    REQUIRE(port != 0);

    char response[4096];
    post_config(port, "wifi_tx_power_dbm=8", response, sizeof(response));
    REQUIRE(strstr(response, "200 OK") != NULL);
    REQUIRE(config_wifi_tx_power_dbm() == 8);
    config_load();
    REQUIRE(config_wifi_tx_power_dbm() == 8);

    post_config(port, "wifi_tx_power_dbm=20", response, sizeof(response));
    REQUIRE(strstr(response, "200 OK") != NULL);
    config_load();
    REQUIRE(config_wifi_tx_power_dbm() == 20);

    post_config(port, "wifi_tx_power_dbm=", response, sizeof(response));
    REQUIRE(strstr(response, "200 OK") != NULL);
    REQUIRE(config_wifi_tx_power_dbm() == 20);

    post_config(port, "wifi_tx_power_dbm=%31%35", response, sizeof(response));
    REQUIRE(strstr(response, "200 OK") != NULL);
    REQUIRE(config_wifi_tx_power_dbm() == 15);

    post_config(port, "sync_enabled=1", response, sizeof(response));
    REQUIRE(strstr(response, "200 OK") != NULL);
    REQUIRE(config_wifi_tx_power_dbm() == 15);
    REQUIRE(config_sync_enabled());

    post_config(port, "wifi_tx_power_dbm=7&sync_enabled=0", response, sizeof(response));
    REQUIRE(strstr(response, "400 Bad Request") != NULL);
    REQUIRE(config_wifi_tx_power_dbm() == 15);
    REQUIRE(config_sync_enabled());

    post_config(port, "wifi_tx_power_dbm=21&sync_enabled=0", response, sizeof(response));
    REQUIRE(strstr(response, "400 Bad Request") != NULL);
    REQUIRE(config_wifi_tx_power_dbm() == 15);
    REQUIRE(config_sync_enabled());

    post_config(port, "wifi_tx_power_dbm=15x&sync_enabled=0", response, sizeof(response));
    REQUIRE(strstr(response, "400 Bad Request") != NULL);
    REQUIRE(config_wifi_tx_power_dbm() == 15);
    REQUIRE(config_sync_enabled());

    web_stop();
    unlink(config_path);
    puts("test_web_wifi_linux: all tests passed");
    return 0;
}
