#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <errno.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include "esp_log.h"
#include "esp_event.h"
#include "esp_netif.h"
#include "esp_ota_ops.h"
#include "mdns.h"
#include "nvs_flash.h"

#include "lwip/sockets.h"
#include "lwip/netdb.h"

#include "protocol_examples_common.h"

#include "announcement.h"
#include "api.h"
#include "config.h"
#include "sip_register.h"
#include "stats.h"
#include "sync.h"
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

// Announce the dongle on the LAN as "answerbot" — the hostname the
// PhoneBlock server's OAuth callback validator whitelists, and the one
// the Fritz!Box displays in Heimnetz → Netzwerk instead of a bare MAC.
// Reachable as http://answerbot/, http://answerbot.local/, or by IP.
static void setup_hostname(void)
{
    static const char *HOSTNAME = "answerbot";

    esp_netif_t *netif = esp_netif_get_default_netif();
    if (netif) {
        esp_err_t err = esp_netif_set_hostname(netif, HOSTNAME);
        if (err != ESP_OK) {
            ESP_LOGW(TAG, "esp_netif_set_hostname: %s", esp_err_to_name(err));
        }
    }

    ESP_ERROR_CHECK(mdns_init());
    ESP_ERROR_CHECK(mdns_hostname_set(HOSTNAME));
    ESP_ERROR_CHECK(mdns_instance_name_set("PhoneBlock Dongle"));
    mdns_service_add(NULL, "_http", "_tcp", 80, NULL, 0);
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
    announcement_init();

    ESP_ERROR_CHECK(example_connect());
    setup_hostname();

    // Web UI comes up unconditionally so the setup wizards are reachable
    // even on a fresh device. SIP registration and the API self-test
    // only run when their respective NVS fields are populated.
    web_start();

    // Reaching this point means: WiFi is up, the web server is
    // listening — minimum viable "this firmware works". Mark the
    // running image valid so the bootloader does not roll back on
    // the next reset. A fresh USB-flash lands in ota_0 in state
    // ESP_OTA_IMG_VALID (no rollback active); OTA uploads land in
    // ESP_OTA_IMG_PENDING_VERIFY and need this confirmation.
    const esp_partition_t *running = esp_ota_get_running_partition();
    esp_ota_img_states_t ota_state;
    if (running != NULL
            && esp_ota_get_state_partition(running, &ota_state) == ESP_OK
            && ota_state == ESP_OTA_IMG_PENDING_VERIFY) {
        ESP_LOGI(TAG, "marking running firmware (%s) valid — rollback cancelled",
                 running->label);
        esp_ota_mark_app_valid_cancel_rollback();
    }

    bool token_set = strlen(config_phoneblock_token()) > 0;
    bool sip_set   = strlen(config_sip_host()) > 0;

    if (token_set) {
        ESP_LOGI(TAG, "initial self-test");
        phoneblock_selftest();
        xTaskCreate(sip_server_task, "sip_server", 8192, NULL, 5, NULL);
    } else {
        ESP_LOGI(TAG, "PhoneBlock token not configured yet — set via web UI");
    }

    if (sip_set) {
        sip_register_start();
    } else {
        ESP_LOGI(TAG, "SIP not configured yet — set via web UI");
    }

    // Blocklist sync task — safe to start even when no Fritz!Box
    // credentials are configured yet; the task itself will skip its
    // runs until a later setup fills those in.
    sync_start();
}
