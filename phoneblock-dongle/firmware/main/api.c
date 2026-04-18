#include "api.h"

#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#include "esp_log.h"
#include "esp_http_client.h"
#include "esp_crt_bundle.h"
#include "esp_timer.h"
#include "cJSON.h"

#include "sdkconfig.h"
#include "stats.h"

static const char *TAG = "api";

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

verdict_t phoneblock_check(const char *phone_number)
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
    int64_t started = esp_timer_get_time();
    esp_err_t err = esp_http_client_perform(client);
    stats_record_api_duration(esp_timer_get_time() - started);

    if (err != ESP_OK) {
        ESP_LOGE(TAG, "HTTP request failed: %s", esp_err_to_name(err));
        char msg[96];
        snprintf(msg, sizeof(msg), "HTTP transport: %s", esp_err_to_name(err));
        stats_record_error("api", msg);
        goto cleanup;
    }

    int status = esp_http_client_get_status_code(client);
    ESP_LOGI(TAG, "HTTP %d, %d bytes: %s", status, resp.len, resp.data);

    if (status != 200) {
        char msg[96];
        snprintf(msg, sizeof(msg), "HTTP %d for number %s", status, phone_number);
        stats_record_error("api", msg);
        goto cleanup;
    }

    cJSON *root = cJSON_Parse(resp.data);
    if (!root) {
        ESP_LOGE(TAG, "JSON parse failed");
        stats_record_error("api", "JSON parse failed");
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
