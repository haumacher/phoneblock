#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <errno.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include "esp_log.h"
#include "esp_event.h"
#include "esp_netif.h"
#include "nvs_flash.h"

#include "lwip/sockets.h"
#include "lwip/netdb.h"

#include "protocol_examples_common.h"

#include "api.h"
#include "config.h"
#include "sip_register.h"
#include "stats.h"
#include "web.h"

static const char *TAG = "phoneblock";

#define SIP_SERVER_PORT 5060
#define MAX_NUMBER_LEN 40

static const char *verdict_to_line(verdict_t v)
{
    switch (v) {
        case VERDICT_SPAM:       return "SPAM\n";
        case VERDICT_LEGITIMATE: return "LEGITIMATE\n";
        default:                 return "ERROR\n";
    }
}

static bool read_line(int sock, char *buf, int max_len)
{
    int pos = 0;
    while (pos < max_len - 1) {
        char c;
        int n = recv(sock, &c, 1, 0);
        if (n <= 0) {
            return false;
        }
        if (c == '\n') {
            buf[pos] = '\0';
            // trim trailing \r if present (CRLF clients)
            if (pos > 0 && buf[pos - 1] == '\r') {
                buf[pos - 1] = '\0';
            }
            return true;
        }
        buf[pos++] = c;
    }
    // overflow: consume and discard rest of line
    buf[max_len - 1] = '\0';
    return false;
}

static void handle_client(int client_sock)
{
    char number[MAX_NUMBER_LEN];

    if (!read_line(client_sock, number, sizeof(number))) {
        ESP_LOGW(TAG, "client sent invalid/oversized input");
        send(client_sock, "ERROR\n", 6, 0);
        return;
    }

    if (number[0] == '\0') {
        send(client_sock, "ERROR\n", 6, 0);
        return;
    }

    ESP_LOGI(TAG, "client query: \"%s\"", number);
    verdict_t v = phoneblock_check(number);
    const char *line = verdict_to_line(v);
    send(client_sock, line, strlen(line), 0);
}

static void sip_server_task(void *arg)
{
    int listen_sock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (listen_sock < 0) {
        ESP_LOGE(TAG, "socket(): %s", strerror(errno));
        vTaskDelete(NULL);
        return;
    }

    int opt = 1;
    setsockopt(listen_sock, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    struct sockaddr_in addr = {
        .sin_family      = AF_INET,
        .sin_addr.s_addr = htonl(INADDR_ANY),
        .sin_port        = htons(SIP_SERVER_PORT),
    };
    if (bind(listen_sock, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        ESP_LOGE(TAG, "bind(): %s", strerror(errno));
        close(listen_sock);
        vTaskDelete(NULL);
        return;
    }
    if (listen(listen_sock, 4) < 0) {
        ESP_LOGE(TAG, "listen(): %s", strerror(errno));
        close(listen_sock);
        vTaskDelete(NULL);
        return;
    }

    ESP_LOGI(TAG, "SIP-dummy server listening on TCP :%d", SIP_SERVER_PORT);

    while (1) {
        struct sockaddr_in peer;
        socklen_t peer_len = sizeof(peer);
        int client_sock = accept(listen_sock, (struct sockaddr *)&peer, &peer_len);
        if (client_sock < 0) {
            ESP_LOGW(TAG, "accept(): %s", strerror(errno));
            continue;
        }

        char peer_ip[INET_ADDRSTRLEN];
        inet_ntoa_r(peer.sin_addr, peer_ip, sizeof(peer_ip));
        ESP_LOGI(TAG, "client connected from %s:%d", peer_ip, ntohs(peer.sin_port));

        handle_client(client_sock);
        close(client_sock);
    }
}

void app_main(void)
{
    ESP_ERROR_CHECK(nvs_flash_init());
    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());

    stats_setup();
    config_load();

    ESP_ERROR_CHECK(example_connect());

    if (strlen(config_phoneblock_token()) == 0) {
        ESP_LOGE(TAG, "PhoneBlock token empty — configure via web UI or sdkconfig.defaults.local");
        return;
    }

    // Sanity query at startup to verify API connectivity.
    ESP_LOGI(TAG, "initial self-test query");
    phoneblock_check(config_phoneblock_test_number());

    xTaskCreate(sip_server_task, "sip_server", 8192, NULL, 5, NULL);
    sip_register_start();
    web_start();
}
