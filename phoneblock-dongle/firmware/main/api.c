#include "api.h"

#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#include "esp_log.h"
#include "esp_http_client.h"
#include "esp_crt_bundle.h"
#include "esp_timer.h"
#include "cJSON.h"
#include "mbedtls/sha1.h"

#include "config.h"
#include "http_util.h"
#include "stats.h"

static const char *TAG = "api";

// 16 KB — /api/check-prefix with a 4-hex prefix returns every entry in
// the bucket. ~1M known numbers / 65536 buckets ≈ 15–30 numbers per
// bucket, ~250 B JSON each → 5–8 KB typical. 16 KB has headroom for
// outlier buckets without straining the heap.
#define RESP_BUF_SIZE (16 * 1024)

// Hash-prefix length sent to /api/check-prefix (k-anonymity bucket
// size). 4 hex chars = 16 bit ≈ ~3000 plausible German numbers per
// bucket, the OpenAPI-recommended minimum.
#define HASH_PREFIX_HEX 4

// Server-side thresholds for wildcard SPAM range detection — must
// match DB.MIN_AGGREGATE_10 / MIN_AGGREGATE_100 in the Java backend
// (DB.computeWildcardVotes).
#define MIN_AGGREGATE_10  4
#define MIN_AGGREGATE_100 3

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

// SHA-1 of `input` → first `hex_chars` uppercase hex digits in `out`.
// `out` must hold hex_chars + 1 bytes. hex_chars must be even and <= 40.
static void sha1_hex_prefix(const char *input, int hex_chars, char *out)
{
    unsigned char digest[20];
    mbedtls_sha1((const unsigned char *)input, strlen(input), digest);
    static const char H[] = "0123456789ABCDEF";
    int bytes = hex_chars / 2;
    for (int i = 0; i < bytes; i++) {
        out[2 * i]     = H[(digest[i] >> 4) & 0x0F];
        out[2 * i + 1] = H[digest[i] & 0x0F];
    }
    out[hex_chars] = '\0';
}

// Local replica of DB.computeWildcardVotes — combines 10-/100-range
// aggregations into a single wildcard vote count using the same
// thresholds (MIN_AGGREGATE_10/100) as the server.
static int compute_wildcard_votes(int votes10, int cnt10, int votes100, int cnt100)
{
    if (cnt100 >= MIN_AGGREGATE_100) {
        int v = votes100;
        if (cnt10 < MIN_AGGREGATE_10) v += votes10;
        return v;
    }
    if (cnt10 >= MIN_AGGREGATE_10) return votes10;
    return 0;
}

// Scans an aggregation array (range10 / range100) for the entry whose
// `prefix` field matches the leading characters of `phone` with
// length `expected_len`. Returns true on hit, in which case
// out_votes / out_cnt are filled.
static bool match_range(const cJSON *array, const char *phone, int expected_len,
                        int *out_votes, int *out_cnt)
{
    if (!cJSON_IsArray(array)) return false;
    int phone_len = (int)strlen(phone);
    if (expected_len <= 0 || expected_len > phone_len) return false;

    cJSON *item;
    cJSON_ArrayForEach(item, array) {
        const cJSON *prefix = cJSON_GetObjectItemCaseSensitive(item, "prefix");
        if (!cJSON_IsString(prefix) || !prefix->valuestring) continue;
        if ((int)strlen(prefix->valuestring) != expected_len) continue;
        if (strncmp(prefix->valuestring, phone, expected_len) != 0) continue;

        const cJSON *v = cJSON_GetObjectItemCaseSensitive(item, "votes");
        const cJSON *c = cJSON_GetObjectItemCaseSensitive(item, "cnt");
        *out_votes = cJSON_IsNumber(v) ? v->valueint : 0;
        *out_cnt   = cJSON_IsNumber(c) ? c->valueint : 0;
        return true;
    }
    return false;
}

// Scans the `numbers` array for an entry whose plaintext `phone` field
// equals our local number — direct string compare avoids any SHA-1
// collision corner case. Returns the entry's `votes`, or 0 if no
// match.
static int match_number_votes(const cJSON *numbers, const char *phone)
{
    if (!cJSON_IsArray(numbers)) return 0;
    cJSON *item;
    cJSON_ArrayForEach(item, numbers) {
        const cJSON *p = cJSON_GetObjectItemCaseSensitive(item, "phone");
        if (!cJSON_IsString(p) || !p->valuestring) continue;
        if (strcmp(p->valuestring, phone) != 0) continue;
        const cJSON *v = cJSON_GetObjectItemCaseSensitive(item, "votes");
        return cJSON_IsNumber(v) ? v->valueint : 0;
    }
    return 0;
}

verdict_t phoneblock_check(const char *phone_number)
{
    verdict_t verdict = VERDICT_ERROR;
    int phone_len = (int)strlen(phone_number);

    // k-anonymity: send only short hash prefixes — server cannot
    // identify which number we asked about. prefix10 / prefix100
    // enable range-based SPAM detection (last 1 / 2 digits dropped
    // before hashing).
    char hash_full[HASH_PREFIX_HEX + 1];
    char hash_p10[HASH_PREFIX_HEX + 1];
    char hash_p100[HASH_PREFIX_HEX + 1];
    sha1_hex_prefix(phone_number, HASH_PREFIX_HEX, hash_full);

    bool have_p10  = phone_len > 1;
    bool have_p100 = phone_len > 2;
    if (have_p10) {
        char shorter[64];
        int n = phone_len - 1;
        if (n >= (int)sizeof(shorter)) n = sizeof(shorter) - 1;
        memcpy(shorter, phone_number, n);
        shorter[n] = '\0';
        sha1_hex_prefix(shorter, HASH_PREFIX_HEX, hash_p10);
    }
    if (have_p100) {
        char shorter[64];
        int n = phone_len - 2;
        if (n >= (int)sizeof(shorter)) n = sizeof(shorter) - 1;
        memcpy(shorter, phone_number, n);
        shorter[n] = '\0';
        sha1_hex_prefix(shorter, HASH_PREFIX_HEX, hash_p100);
    }

    char url[256];
    int n = snprintf(url, sizeof(url),
                     "%s/api/check-prefix?sha1=%s&format=json",
                     config_phoneblock_base_url(), hash_full);
    if (have_p10 && n < (int)sizeof(url)) {
        n += snprintf(url + n, sizeof(url) - n, "&prefix10=%s", hash_p10);
    }
    if (have_p100 && n < (int)sizeof(url)) {
        n += snprintf(url + n, sizeof(url) - n, "&prefix100=%s", hash_p100);
    }

    char auth_header[128];
    snprintf(auth_header, sizeof(auth_header), "Bearer %s", config_phoneblock_token());

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
        .auth_type = HTTP_AUTH_TYPE_NONE,
    };

    esp_http_client_handle_t client = esp_http_client_init(&config);
    http_util_set_user_agent(client);
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
    ESP_LOGI(TAG, "HTTP %d, %d bytes", status, resp.len);

    if (status != 200) {
        char msg[96];
        snprintf(msg, sizeof(msg), "HTTP %d for hash %s", status, hash_full);
        stats_record_error("api", msg);
        goto cleanup;
    }

    cJSON *root = cJSON_Parse(resp.data);
    if (!root) {
        ESP_LOGE(TAG, "JSON parse failed");
        stats_record_error("api", "JSON parse failed");
        goto cleanup;
    }

    const cJSON *numbers  = cJSON_GetObjectItemCaseSensitive(root, "numbers");
    const cJSON *range10  = cJSON_GetObjectItemCaseSensitive(root, "range10");
    const cJSON *range100 = cJSON_GetObjectItemCaseSensitive(root, "range100");

    int direct_votes = match_number_votes(numbers, phone_number);

    int v10 = 0, c10 = 0, v100 = 0, c100 = 0;
    if (have_p10)  match_range(range10,  phone_number, phone_len - 1, &v10,  &c10);
    if (have_p100) match_range(range100, phone_number, phone_len - 2, &v100, &c100);
    int wildcard_votes = compute_wildcard_votes(v10, c10, v100, c100);

    verdict = (direct_votes > 0 || wildcard_votes > 0)
        ? VERDICT_SPAM : VERDICT_LEGITIMATE;

    ESP_LOGI(TAG, "Number %s → votes=%d wildcard=%d (range10 v=%d cnt=%d, range100 v=%d cnt=%d) → %s",
             phone_number, direct_votes, wildcard_votes,
             v10, c10, v100, c100,
             verdict == VERDICT_SPAM ? "SPAM" : "LEGITIMATE");

    cJSON_Delete(root);

cleanup:
    esp_http_client_cleanup(client);
    free(resp.data);
    return verdict;
}

bool phoneblock_report_call(const char *phone)
{
    if (!phone || !*phone) return false;

    // Send the number in legacy "00"-prefix form to avoid URL-encoding
    // the leading "+". /api/check-prefix already hides which number we
    // queried; report-call is the fair-use contribution that lets the
    // server keep tailored Fritz!Box-sized blocklists current.
    char path_phone[64];
    if (phone[0] == '+') {
        path_phone[0] = '0';
        path_phone[1] = '0';
        size_t rest = strlen(phone + 1);
        if (rest >= sizeof(path_phone) - 3) rest = sizeof(path_phone) - 3;
        memcpy(path_phone + 2, phone + 1, rest);
        path_phone[2 + rest] = '\0';
    } else {
        snprintf(path_phone, sizeof(path_phone), "%s", phone);
    }

    char url[200];
    snprintf(url, sizeof(url), "%s/api/report-call/%s",
             config_phoneblock_base_url(), path_phone);

    char auth_header[128];
    snprintf(auth_header, sizeof(auth_header), "Bearer %s", config_phoneblock_token());

    esp_http_client_config_t config = {
        .url = url,
        .method = HTTP_METHOD_POST,
        .crt_bundle_attach = esp_crt_bundle_attach,
        .timeout_ms = 10000,
        .auth_type = HTTP_AUTH_TYPE_NONE,
    };
    esp_http_client_handle_t client = esp_http_client_init(&config);
    http_util_set_user_agent(client);
    esp_http_client_set_header(client, "Authorization", auth_header);
    esp_http_client_set_post_field(client, "", 0);

    ESP_LOGI(TAG, "POST %s", url);
    esp_err_t err = esp_http_client_perform(client);
    int status = (err == ESP_OK) ? esp_http_client_get_status_code(client) : 0;
    esp_http_client_cleanup(client);

    if (err != ESP_OK) {
        ESP_LOGE(TAG, "report-call transport: %s", esp_err_to_name(err));
        char msg[96];
        snprintf(msg, sizeof(msg), "report-call transport: %s", esp_err_to_name(err));
        stats_record_error("api", msg);
        return false;
    }
    if (status != 204 && status != 200) {
        ESP_LOGE(TAG, "report-call: HTTP %d for %s", status, phone);
        char msg[96];
        snprintf(msg, sizeof(msg), "report-call: HTTP %d", status);
        stats_record_error("api", msg);
        return false;
    }
    return true;
}

bool phoneblock_rate(const char *phone, const char *rating, const char *comment)
{
    if (!phone || !*phone || !rating || !*rating) return false;

    char url[160];
    snprintf(url, sizeof(url), "%s/api/rate", config_phoneblock_base_url());

    char auth_header[128];
    snprintf(auth_header, sizeof(auth_header), "Bearer %s", config_phoneblock_token());

    // JSON body — small enough for a stack buffer; phone is typically
    // +49... (16 chars), rating is the enum name (≤16 chars), comment
    // cap 120 keeps us well under 256 bytes.
    char body[256];
    if (comment && *comment) {
        snprintf(body, sizeof(body),
            "{\"phone\":\"%s\",\"rating\":\"%s\",\"comment\":\"%.120s\"}",
            phone, rating, comment);
    } else {
        snprintf(body, sizeof(body),
            "{\"phone\":\"%s\",\"rating\":\"%s\"}",
            phone, rating);
    }

    esp_http_client_config_t config = {
        .url = url,
        .method = HTTP_METHOD_POST,
        .crt_bundle_attach = esp_crt_bundle_attach,
        .timeout_ms = 10000,
        .auth_type = HTTP_AUTH_TYPE_NONE,
    };
    esp_http_client_handle_t client = esp_http_client_init(&config);
    http_util_set_user_agent(client);
    esp_http_client_set_header(client, "Authorization", auth_header);
    esp_http_client_set_header(client, "Content-Type", "application/json");
    esp_http_client_set_post_field(client, body, strlen(body));

    ESP_LOGI(TAG, "POST %s (phone=%s rating=%s)", url, phone, rating);
    esp_err_t err = esp_http_client_perform(client);
    int status = (err == ESP_OK) ? esp_http_client_get_status_code(client) : 0;
    esp_http_client_cleanup(client);

    if (err != ESP_OK) {
        ESP_LOGE(TAG, "rate transport: %s", esp_err_to_name(err));
        return false;
    }
    if (status != 200) {
        ESP_LOGE(TAG, "rate: HTTP %d for %s", status, phone);
        return false;
    }
    return true;
}

bool phoneblock_selftest(void)
{
    char url[160];
    snprintf(url, sizeof(url), "%s/api/test", config_phoneblock_base_url());

    const char *token = config_phoneblock_token();
    char auth_header[128];
    snprintf(auth_header, sizeof(auth_header), "Bearer %s", token);

    response_buffer_t resp = {
        .data = calloc(1, 512),
        .len = 0,
        .cap = 512,
    };
    if (!resp.data) {
        ESP_LOGE(TAG, "self-test: out of memory");
        return false;
    }

    esp_http_client_config_t config = {
        .url = url,
        .event_handler = http_event_handler,
        .user_data = &resp,
        .crt_bundle_attach = esp_crt_bundle_attach,
        .timeout_ms = 10000,
        // Tell esp_http_client not to auto-retry with a challenged auth
        // scheme — we set Authorization ourselves. Silences the noisy
        // "Basic realm=... not supported" error on every 401.
        .auth_type = HTTP_AUTH_TYPE_NONE,
    };
    esp_http_client_handle_t client = esp_http_client_init(&config);
    http_util_set_user_agent(client);
    esp_http_client_set_header(client, "Authorization", auth_header);
    esp_http_client_set_header(client, "Accept", "text/plain");

    // Log a short token fingerprint so a wrong-instance or truncated
    // token is obvious from the log without leaking the secret.
    size_t tlen = strlen(token);
    ESP_LOGI(TAG, "GET %s (token %zu chars, prefix \"%.6s…\")",
             url, tlen, tlen > 0 ? token : "");

    int64_t started = esp_timer_get_time();
    esp_err_t err = esp_http_client_perform(client);
    stats_record_api_duration(esp_timer_get_time() - started);

    bool ok = false;
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "self-test transport: %s", esp_err_to_name(err));
        char msg[96];
        snprintf(msg, sizeof(msg), "self-test transport: %s", esp_err_to_name(err));
        stats_record_error("api", msg);
    } else {
        int status = esp_http_client_get_status_code(client);
        if (status == 200) {
            ESP_LOGI(TAG, "self-test: HTTP 200, token accepted");
            ok = true;
        } else {
            ESP_LOGE(TAG, "self-test: HTTP %d, body: %.*s",
                     status, resp.len, resp.data);
            char msg[96];
            int body_len = resp.len < 48 ? resp.len : 48;
            snprintf(msg, sizeof(msg), "self-test: HTTP %d: %.*s",
                     status, body_len, resp.data);
            stats_record_error("api", msg);
        }
    }
    esp_http_client_cleanup(client);
    free(resp.data);
    return ok;
}
