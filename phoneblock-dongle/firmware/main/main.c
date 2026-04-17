#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include "esp_log.h"
#include "esp_event.h"
#include "esp_netif.h"
#include "esp_http_client.h"
#include "esp_crt_bundle.h"
#include "nvs_flash.h"
#include "cJSON.h"

#include "protocol_examples_common.h"

static const char *TAG = "phoneblock";

#define RESP_BUF_SIZE 4096

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

static void query_phoneblock(const char *phone_number)
{
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
        return;
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
    ESP_LOGI(TAG, "HTTP %d, %d bytes", status, resp.len);
    ESP_LOGI(TAG, "Raw response: %s", resp.data);

    if (status != 200) {
        ESP_LOGE(TAG, "Non-OK status, aborting JSON parse");
        goto cleanup;
    }

    cJSON *root = cJSON_Parse(resp.data);
    if (!root) {
        ESP_LOGE(TAG, "JSON parse failed");
        goto cleanup;
    }

    const cJSON *votes = cJSON_GetObjectItemCaseSensitive(root, "votes");
    const cJSON *phone = cJSON_GetObjectItemCaseSensitive(root, "phone");

    int vote_count = cJSON_IsNumber(votes) ? votes->valueint : 0;
    const char *phone_str = cJSON_IsString(phone) ? phone->valuestring : phone_number;

    ESP_LOGI(TAG, "═══════════════════════════════════════");
    ESP_LOGI(TAG, "  Number:    %s", phone_str);
    ESP_LOGI(TAG, "  Votes:     %d", vote_count);
    ESP_LOGI(TAG, "  Verdict:   %s", vote_count > 0 ? "SPAM" : "clean");
    ESP_LOGI(TAG, "═══════════════════════════════════════");

    cJSON_Delete(root);

cleanup:
    esp_http_client_cleanup(client);
    free(resp.data);
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

    query_phoneblock(CONFIG_PHONEBLOCK_TEST_NUMBER);
}
