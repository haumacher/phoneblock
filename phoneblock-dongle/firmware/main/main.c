#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <errno.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include "esp_log.h"
#include "esp_event.h"
#include "esp_netif.h"
#include "esp_http_client.h"
#include "esp_crt_bundle.h"
#include "nvs_flash.h"
#include "cJSON.h"

#include "lwip/sockets.h"
#include "lwip/netdb.h"

#include "protocol_examples_common.h"

static const char *TAG = "phoneblock";

#define RESP_BUF_SIZE 4096
#define SIP_SERVER_PORT 5060
#define MAX_NUMBER_LEN 40

typedef enum {
    VERDICT_LEGITIMATE,
    VERDICT_SPAM,
    VERDICT_ERROR,
} verdict_t;

typedef struct {
    char *data;
    int len;
    int cap;
} response_buffer_t;

static esp_err_t http_event_handler(esp_http_client_event_t *evt)
{
    response_buffer_t *resp = (response_buffer_t *)evt->user_data;

    if (evt->event_id == HTTP_EVENT_ON_DATA && !esp_http_client_is_chunked_response(evt->client)) {
        int remaining = resp->cap - resp->len - 1;
        int copy = evt->data_len < remaining ? evt->data_len : remaining;
        if (copy > 0) {
            memcpy(resp->data + resp->len, evt->data, copy);
            resp->len += copy;
            resp->data[resp->len] = '\0';
        }
    }
    return ESP_OK;
}

static verdict_t check_number(const char *phone_number)
{
    verdict_t verdict = VERDICT_ERROR;

    char url[256];
    snprintf(url, sizeof(url), "%s/num/%s?format=json",
             CONFIG_PHONEBLOCK_BASE_URL, phone_number);

    char auth_header[128];
    snprintf(auth_header, sizeof(auth_header), "Bearer %s", CONFIG_PHONEBLOCK_TOKEN);

    response_buffer_t resp = {
        .data = calloc(1, RESP_BUF_SIZE),
        .len = 0,
        .cap = RESP_BUF_SIZE,
    };
    if (!resp.data) {
        ESP_LOGE(TAG, "out of memory");
        return VERDICT_ERROR;
    }

    esp_http_client_config_t config = {
        .url = url,
        .event_handler = http_event_handler,
        .user_data = &resp,
        .crt_bundle_attach = esp_crt_bundle_attach,
        .timeout_ms = 10000,
    };

    esp_http_client_handle_t client = esp_http_client_init(&config);
    esp_http_client_set_header(client, "Authorization", auth_header);
    esp_http_client_set_header(client, "Accept", "application/json");

    ESP_LOGI(TAG, "GET %s", url);
    esp_err_t err = esp_http_client_perform(client);

    if (err != ESP_OK) {
        ESP_LOGE(TAG, "HTTP request failed: %s", esp_err_to_name(err));
        goto cleanup;
    }

    int status = esp_http_client_get_status_code(client);
    ESP_LOGI(TAG, "HTTP %d, %d bytes: %s", status, resp.len, resp.data);

    if (status != 200) {
        goto cleanup;
    }

    cJSON *root = cJSON_Parse(resp.data);
    if (!root) {
        ESP_LOGE(TAG, "JSON parse failed");
        goto cleanup;
    }

    const cJSON *votes = cJSON_GetObjectItemCaseSensitive(root, "votes");
    int vote_count = cJSON_IsNumber(votes) ? votes->valueint : 0;
    verdict = vote_count > 0 ? VERDICT_SPAM : VERDICT_LEGITIMATE;

    ESP_LOGI(TAG, "Number %s → %d votes → %s",
             phone_number, vote_count,
             verdict == VERDICT_SPAM ? "SPAM" : "LEGITIMATE");

    cJSON_Delete(root);

cleanup:
    esp_http_client_cleanup(client);
    free(resp.data);
    return verdict;
}

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
    verdict_t v = check_number(number);
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

    ESP_ERROR_CHECK(example_connect());

    if (strlen(CONFIG_PHONEBLOCK_TOKEN) == 0) {
        ESP_LOGE(TAG, "CONFIG_PHONEBLOCK_TOKEN is empty — run idf.py menuconfig");
        return;
    }

    // Sanity query at startup to verify API connectivity.
    ESP_LOGI(TAG, "initial self-test query");
    check_number(CONFIG_PHONEBLOCK_TEST_NUMBER);

    xTaskCreate(sip_server_task, "sip_server", 8192, NULL, 5, NULL);
}
