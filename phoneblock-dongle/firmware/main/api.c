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

// Hash-prefix length sent to /api/check-prefix (k-anonymity bucket
// size). 4 hex chars = 16 bit ≈ ~3000 plausible German numbers per
// bucket, the OpenAPI-recommended minimum.
#define HASH_PREFIX_HEX 4

// Server-side thresholds for wildcard SPAM range detection — must
// match DB.MIN_AGGREGATE_10 / MIN_AGGREGATE_100 in the Java backend
// (DB.computeWildcardVotes).
#define MIN_AGGREGATE_10  4
#define MIN_AGGREGATE_100 3

// Per-entry buffer for the streaming check-prefix scanner. Each entry
// in the response is a single PhoneInfo (or RangeMatch) JSON object
// — typically 200–300 bytes, never plausibly close to this limit.
// Sized so a deformed/oversized entry triggers a loud, recorded error
// instead of silently misclassifying a call.
#define ENTRY_BUF_SIZE 2048

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

// --- Streaming /api/check-prefix response scanner -----------------------
//
// /api/check-prefix returns:
//   {"numbers":[{...},{...}],"range10":[{...}],"range100":[{...}]}
//
// At a 4-hex-char prefix the response is small in the typical case but
// has no upper bound — a hot bucket could be tens of KB. Buffering the
// whole response and trusting the buffer size would silently
// misclassify SPAM whenever the bucket exceeds the buffer. The
// scanner below feeds bytes from HTTP_EVENT_ON_DATA chunks through a
// tiny depth-tracking state machine, isolates each per-entry JSON
// object, and parses each entry independently with cJSON on a small
// fixed-size buffer (ENTRY_BUF_SIZE). Memory cost is O(1) in the
// number of entries.
//
// A per-entry overflow (one PhoneInfo > 2 KB — never plausible for the
// schema) sets `obj_overflow` so the caller produces VERDICT_ERROR
// and a recorded stats error instead of a silent miss.

typedef enum {
    ARR_NONE,
    ARR_NUMBERS,
    ARR_RANGE10,
    ARR_RANGE100,
} current_array_t;

typedef struct {
    const char *phone;
    int phone_len;

    // Top-level JSON-scanner state.
    bool in_string;
    bool escape_next;
    int  brace_depth;     // counts {} only
    int  bracket_depth;   // counts [] only
    current_array_t current_array;

    // Most recent string seen at brace_depth==1, bracket_depth==0 —
    // used to identify which top-level array follows.
    bool collecting_key;
    char key_buf[16];
    int  key_len;

    // Buffer for the current per-entry object.
    bool collecting_obj;
    char obj_buf[ENTRY_BUF_SIZE];
    int  obj_len;
    bool obj_overflow;

    // Accumulated results.
    int direct_votes;
    int v10, c10;
    int v100, c100;

    bool error;
} scan_state_t;

static void scan_handle_object(scan_state_t *s)
{
    cJSON *o = cJSON_Parse(s->obj_buf);
    if (!o) {
        ESP_LOGW(TAG, "failed to parse entry");
        s->error = true;
        return;
    }

    if (s->current_array == ARR_NUMBERS) {
        const cJSON *p = cJSON_GetObjectItemCaseSensitive(o, "phone");
        if (cJSON_IsString(p) && p->valuestring &&
            strcmp(p->valuestring, s->phone) == 0) {
            const cJSON *v = cJSON_GetObjectItemCaseSensitive(o, "votes");
            if (cJSON_IsNumber(v)) s->direct_votes = v->valueint;
        }
    } else if (s->current_array == ARR_RANGE10 ||
               s->current_array == ARR_RANGE100) {
        int expected_len = (s->current_array == ARR_RANGE10)
            ? s->phone_len - 1 : s->phone_len - 2;
        if (expected_len > 0) {
            const cJSON *p = cJSON_GetObjectItemCaseSensitive(o, "prefix");
            if (cJSON_IsString(p) && p->valuestring &&
                (int)strlen(p->valuestring) == expected_len &&
                strncmp(p->valuestring, s->phone, expected_len) == 0) {
                const cJSON *v = cJSON_GetObjectItemCaseSensitive(o, "votes");
                const cJSON *c = cJSON_GetObjectItemCaseSensitive(o, "cnt");
                int votes = cJSON_IsNumber(v) ? v->valueint : 0;
                int cnt   = cJSON_IsNumber(c) ? c->valueint : 0;
                if (s->current_array == ARR_RANGE10) { s->v10 = votes;  s->c10 = cnt; }
                else                                  { s->v100 = votes; s->c100 = cnt; }
            }
        }
    }

    cJSON_Delete(o);
}

static void scan_feed(scan_state_t *s, const char *data, int len)
{
    for (int i = 0; i < len; i++) {
        char b = data[i];

        // Raw passthrough into the per-entry buffer — preserves strings,
        // whitespace, and any unknown fields verbatim for cJSON.
        if (s->collecting_obj) {
            if (s->obj_len < (int)sizeof(s->obj_buf) - 1) {
                s->obj_buf[s->obj_len++] = b;
            } else {
                s->obj_overflow = true;
            }
        }

        if (s->in_string) {
            if (s->collecting_key && !s->escape_next && b != '\\' && b != '"') {
                if (s->key_len < (int)sizeof(s->key_buf) - 1) {
                    s->key_buf[s->key_len++] = b;
                }
            } else if (s->collecting_key && s->escape_next) {
                if (s->key_len < (int)sizeof(s->key_buf) - 1) {
                    s->key_buf[s->key_len++] = b;
                }
            }
            if (s->escape_next)        s->escape_next = false;
            else if (b == '\\')        s->escape_next = true;
            else if (b == '"') {
                s->in_string = false;
                if (s->collecting_key) {
                    s->collecting_key = false;
                    s->key_buf[s->key_len] = '\0';
                }
            }
            continue;
        }

        switch (b) {
        case '"':
            s->in_string = true;
            // Top-level keys live at brace_depth==1, bracket_depth==0.
            if (s->brace_depth == 1 && s->bracket_depth == 0 && !s->collecting_obj) {
                s->collecting_key = true;
                s->key_len = 0;
            }
            break;
        case '{':
            s->brace_depth++;
            // Entry object opens at brace_depth==2 inside one of the
            // recognised top-level arrays.
            if (!s->collecting_obj && s->bracket_depth == 1 &&
                s->brace_depth == 2 && s->current_array != ARR_NONE) {
                s->collecting_obj = true;
                s->obj_len = 0;
                s->obj_overflow = false;
                s->obj_buf[s->obj_len++] = '{';
            }
            break;
        case '}':
            s->brace_depth--;
            if (s->collecting_obj && s->brace_depth == 1) {
                if (s->obj_overflow) {
                    ESP_LOGE(TAG, "entry exceeded %u-byte buffer",
                             (unsigned)sizeof(s->obj_buf));
                    s->error = true;
                } else {
                    s->obj_buf[s->obj_len] = '\0';
                    scan_handle_object(s);
                }
                s->collecting_obj = false;
            }
            break;
        case '[':
            s->bracket_depth++;
            if (s->brace_depth == 1 && s->bracket_depth == 1) {
                if      (strcmp(s->key_buf, "numbers")  == 0) s->current_array = ARR_NUMBERS;
                else if (strcmp(s->key_buf, "range10")  == 0) s->current_array = ARR_RANGE10;
                else if (strcmp(s->key_buf, "range100") == 0) s->current_array = ARR_RANGE100;
                else                                          s->current_array = ARR_NONE;
            }
            break;
        case ']':
            s->bracket_depth--;
            if (s->bracket_depth == 0) s->current_array = ARR_NONE;
            break;
        default:
            break;
        }
    }
}

static esp_err_t http_event_check(esp_http_client_event_t *evt)
{
    if (evt->event_id == HTTP_EVENT_ON_DATA) {
        scan_state_t *s = (scan_state_t *)evt->user_data;
        scan_feed(s, (const char *)evt->data, evt->data_len);
    }
    return ESP_OK;
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

    scan_state_t scan = {
        .phone = phone_number,
        .phone_len = phone_len,
        .current_array = ARR_NONE,
    };

    esp_http_client_config_t config = {
        .url = url,
        .event_handler = http_event_check,
        .user_data = &scan,
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
    ESP_LOGI(TAG, "HTTP %d", status);

    if (status != 200) {
        char msg[96];
        snprintf(msg, sizeof(msg), "HTTP %d for hash %s", status, hash_full);
        stats_record_error("api", msg);
        goto cleanup;
    }

    if (scan.error) {
        // Per-entry buffer overflow or per-entry parse failure — verdict
        // intentionally stays VERDICT_ERROR so the call is not silently
        // misclassified. The scanner has already logged the cause; record
        // a stats error so the failure shows up on the device dashboard.
        stats_record_error("api", "check-prefix scanner: oversized/invalid entry");
        goto cleanup;
    }

    int wildcard_votes = compute_wildcard_votes(scan.v10, scan.c10,
                                                scan.v100, scan.c100);
    verdict = (scan.direct_votes > 0 || wildcard_votes > 0)
        ? VERDICT_SPAM : VERDICT_LEGITIMATE;

    ESP_LOGI(TAG, "Number %s → votes=%d wildcard=%d (range10 v=%d cnt=%d, range100 v=%d cnt=%d) → %s",
             phone_number, scan.direct_votes, wildcard_votes,
             scan.v10, scan.c10, scan.v100, scan.c100,
             verdict == VERDICT_SPAM ? "SPAM" : "LEGITIMATE");

cleanup:
    esp_http_client_cleanup(client);
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
