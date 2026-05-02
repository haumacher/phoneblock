#include "api.h"

#include <stdatomic.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#include "esp_log.h"
#include "esp_http_client.h"
#include "esp_crt_bundle.h"
#include "esp_timer.h"
#include "cJSON.h"
#include "mbedtls/sha1.h"

#include "api_scan.h"
#include "config.h"
#include "http_util.h"
#include "stats.h"

static const char *TAG = "api";

// Live token-health flag, fed from every Bearer-authenticated call.
// Optimistic boot default: until the first call has run, "ok" is the
// safer assumption — otherwise the LED would briefly drop to DEGRADED
// at every power-up. The first selftest (synchronous from app_main)
// or the first real /api/check-prefix lookup overwrites it.
static atomic_int s_token_ok = 1;

bool api_token_is_valid(void) { return atomic_load(&s_token_ok) != 0; }

// Updates s_token_ok after a Bearer-authenticated request. 401/403
// flips to "bad" — the server has explicitly rejected the token, and
// that signal is deterministic enough to act on without hysteresis.
// 2xx flips back to "ok" — proves the token is currently accepted.
// Transport errors and 5xx are deliberately ignored: they say nothing
// about the token's validity, only about the network or server, and
// must not flap the dashboard between "valid" and "rejected" while
// the user's connection is just blinking.
static void note_api_response(esp_err_t transport_err, int http_status)
{
    if (transport_err != ESP_OK) return;
    if (http_status >= 500)      return;
    if (http_status == 401 || http_status == 403) {
        atomic_store(&s_token_ok, 0);
    } else if (http_status >= 200 && http_status < 300) {
        atomic_store(&s_token_ok, 1);
    }
}

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

// HTTP_EVENT_ON_DATA dispatcher for the streaming /api/check-prefix
// scanner. Logic lives in api_scan.{c,h} so it can be unit-tested
// host-side without ESP-IDF dependencies.
static esp_err_t http_event_check(esp_http_client_event_t *evt)
{
    if (evt->event_id == HTTP_EVENT_ON_DATA) {
        api_scan_t *s = (api_scan_t *)evt->user_data;
        api_scan_feed(s, (const char *)evt->data, evt->data_len);
    }
    return ESP_OK;
}

verdict_t phoneblock_check(const char *phone_number, pb_check_result_t *out)
{
    if (out) memset(out, 0, sizeof(*out));
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

    api_scan_t scan;
    api_scan_init(&scan, phone_number);

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

    int status = (err == ESP_OK) ? esp_http_client_get_status_code(client) : 0;
    note_api_response(err, status);

    if (err != ESP_OK) {
        ESP_LOGE(TAG, "HTTP request failed: %s", esp_err_to_name(err));
        char msg[96];
        snprintf(msg, sizeof(msg), "HTTP transport: %s", esp_err_to_name(err));
        stats_record_error("api", msg);
        goto cleanup;
    }

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
        // misclassified. Surface the scanner's reason on both the log
        // and the device dashboard.
        const char *reason = scan.error_reason ? scan.error_reason : "unknown";
        ESP_LOGE(TAG, "check-prefix scanner: %s", reason);
        char msg[96];
        snprintf(msg, sizeof(msg), "check-prefix scanner: %s", reason);
        stats_record_error("api", msg);
        goto cleanup;
    }

    int wildcard_votes = compute_wildcard_votes(scan.v10, scan.c10,
                                                scan.v100, scan.c100);
    int min_direct = config_min_direct_votes();
    int min_range  = config_min_range_votes();
    bool direct_hit = scan.direct_votes >= min_direct;
    bool range_hit  = (min_range >= 1) && (wildcard_votes >= min_range);

    // Per-user personalization wins over the community signal. The
    // server emits these flags on numbers[] entries when the bearer
    // token belongs to a user who has the number on their personal
    // BLOCKLIST. White-list trumps black-list if both are somehow set.
    if (scan.white_listed) {
        verdict = VERDICT_LEGITIMATE;
    } else if (scan.black_listed) {
        verdict = VERDICT_SPAM;
    } else {
        verdict = (direct_hit || range_hit) ? VERDICT_SPAM : VERDICT_LEGITIMATE;
    }

    ESP_LOGI(TAG, "Number %s → direct=%d wildcard=%d (range10 v=%d cnt=%d, range100 v=%d cnt=%d) thresholds=(direct=%d range=%d) wl=%d bl=%d → %s",
             phone_number, scan.direct_votes, wildcard_votes,
             scan.v10, scan.c10, scan.v100, scan.c100,
             min_direct, min_range,
             scan.white_listed, scan.black_listed,
             verdict == VERDICT_SPAM ? "SPAM" : "LEGITIMATE");

    if (out) {
        out->verdict = verdict;
        out->white_listed = scan.white_listed;
        out->black_listed = scan.black_listed;
        strncpy(out->label,    scan.label,    sizeof(out->label)    - 1);
        strncpy(out->location, scan.location, sizeof(out->location) - 1);
        // For the count: keep showing the underlying community signal
        // even when an override is in effect. Lets the UI render
        // "Whitelist (sonst SPAM, 12 Stimmen)" / "Blacklist (0 Stimmen
        // in Community)" without losing context.
        int community = scan.direct_votes;
        if (scan.v10  > community) community = scan.v10;
        if (scan.v100 > community) community = scan.v100;
        if (scan.white_listed || scan.black_listed) {
            out->votes = community;
            out->suspected = false;  // override is final, no soft state
        } else if (verdict == VERDICT_SPAM) {
            // Show whichever side actually cleared its threshold;
            // direct dominates when both did (more specific signal).
            out->votes = direct_hit ? scan.direct_votes : wildcard_votes;
            out->suspected = false;
        } else if (community > 0) {
            // Some evidence exists but it didn't clear the relevant
            // threshold (or range-blocking is off). SPAM-VERDACHT.
            out->votes = community;
            out->suspected = true;
        } else {
            out->votes = 0;
            out->suspected = false;
        }
    }

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
    note_api_response(err, status);
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
    note_api_response(err, status);
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

    int status = (err == ESP_OK) ? esp_http_client_get_status_code(client) : 0;
    note_api_response(err, status);

    bool ok = false;
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "self-test transport: %s", esp_err_to_name(err));
        char msg[96];
        snprintf(msg, sizeof(msg), "self-test transport: %s", esp_err_to_name(err));
        stats_record_error("api", msg);
    } else {
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

bool phoneblock_verify_auth_code(const char *code, const char *state,
                                 char *user_out, size_t user_cap)
{
    if (user_out && user_cap > 0) user_out[0] = '\0';
    if (!code || !*code) return false;

    char url[160];
    snprintf(url, sizeof(url), "%s/auth/verify-code",
             config_phoneblock_base_url());

    // Form-encoded body: the code is a JWT (URL-safe Base64), state
    // is a 32-char hex nonce — neither needs escaping. Cap at 1 KB to
    // bound the stack allocation.
    char body[1024];
    int body_len = snprintf(body, sizeof(body),
        "code=%s&state=%s", code, state ? state : "");
    if (body_len < 0 || body_len >= (int)sizeof(body)) {
        ESP_LOGE(TAG, "verify-code: body too large");
        return false;
    }

    response_buffer_t resp = {
        .data = calloc(1, 512),
        .len = 0,
        .cap = 512,
    };
    if (!resp.data) {
        ESP_LOGE(TAG, "verify-code: out of memory");
        return false;
    }

    esp_http_client_config_t config = {
        .url = url,
        .method = HTTP_METHOD_POST,
        .event_handler = http_event_handler,
        .user_data = &resp,
        .crt_bundle_attach = esp_crt_bundle_attach,
        .timeout_ms = 10000,
        .auth_type = HTTP_AUTH_TYPE_NONE,
    };
    esp_http_client_handle_t client = esp_http_client_init(&config);
    http_util_set_user_agent(client);
    esp_http_client_set_header(client, "Content-Type",
        "application/x-www-form-urlencoded");
    esp_http_client_set_post_field(client, body, body_len);

    ESP_LOGI(TAG, "POST %s", url);
    esp_err_t err = esp_http_client_perform(client);
    int status = (err == ESP_OK) ? esp_http_client_get_status_code(client) : 0;
    esp_http_client_cleanup(client);

    bool ok = false;
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "verify-code transport: %s", esp_err_to_name(err));
    } else if (status != 200) {
        ESP_LOGE(TAG, "verify-code: HTTP %d", status);
    } else {
        cJSON *root = cJSON_Parse(resp.data);
        if (!root) {
            ESP_LOGW(TAG, "verify-code: bad JSON: %.*s", resp.len, resp.data);
        } else {
            cJSON *ok_node   = cJSON_GetObjectItem(root, "ok");
            cJSON *user_node = cJSON_GetObjectItem(root, "user");
            if (cJSON_IsTrue(ok_node) && cJSON_IsString(user_node)
                && user_node->valuestring && user_node->valuestring[0]
                && user_out && user_cap > 0) {
                size_t n = strnlen(user_node->valuestring, user_cap - 1);
                memcpy(user_out, user_node->valuestring, n);
                user_out[n] = '\0';
                ok = true;
            } else {
                cJSON *reason = cJSON_GetObjectItem(root, "reason");
                ESP_LOGW(TAG, "verify-code: rejected (reason=%s)",
                    (cJSON_IsString(reason) && reason->valuestring)
                        ? reason->valuestring : "unknown");
            }
            cJSON_Delete(root);
        }
    }
    free(resp.data);
    return ok;
}

// Percent-encode `src` into `dst` for use as a form-encoded value.
// Slash is allowed unencoded since it's the dominant character in the
// `next` paths we send. Returns false if `dst` would overflow.
static bool form_encode(const char *src, char *dst, size_t cap)
{
    char *p = dst;
    while (*src) {
        if ((size_t)(p - dst) + 4 >= cap) return false;
        unsigned c = (unsigned char)*src++;
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
            (c >= '0' && c <= '9') || c == '-' || c == '_' ||
            c == '.' || c == '~' || c == '/') {
            *p++ = (char)c;
        } else {
            static const char H[] = "0123456789ABCDEF";
            *p++ = '%';
            *p++ = H[(c >> 4) & 0xF];
            *p++ = H[c & 0xF];
        }
    }
    if ((size_t)(p - dst) + 1 >= cap) return false;
    *p = '\0';
    return true;
}

bool phoneblock_mint_login_ticket(const char *next, char *url_out, size_t url_cap)
{
    if (url_out && url_cap > 0) url_out[0] = '\0';
    if (!next || !*next || !url_out || url_cap == 0) return false;

    const char *token = config_phoneblock_token();
    if (!token || !token[0]) {
        ESP_LOGE(TAG, "mint-ticket: no API token configured");
        return false;
    }

    char url[160];
    snprintf(url, sizeof(url), "%s/api/auth/login-ticket",
             config_phoneblock_base_url());

    char next_enc[256];
    if (!form_encode(next, next_enc, sizeof(next_enc))) {
        ESP_LOGE(TAG, "mint-ticket: 'next' too long");
        return false;
    }
    char body[320];
    int body_len = snprintf(body, sizeof(body), "next=%s", next_enc);
    if (body_len < 0 || body_len >= (int)sizeof(body)) {
        ESP_LOGE(TAG, "mint-ticket: body too large");
        return false;
    }

    char auth_header[128];
    snprintf(auth_header, sizeof(auth_header), "Bearer %s", token);

    response_buffer_t resp = {
        .data = calloc(1, 1024),
        .len = 0,
        .cap = 1024,
    };
    if (!resp.data) {
        ESP_LOGE(TAG, "mint-ticket: out of memory");
        return false;
    }

    esp_http_client_config_t config = {
        .url = url,
        .method = HTTP_METHOD_POST,
        .event_handler = http_event_handler,
        .user_data = &resp,
        .crt_bundle_attach = esp_crt_bundle_attach,
        .timeout_ms = 10000,
    };
    esp_http_client_handle_t client = esp_http_client_init(&config);
    http_util_set_user_agent(client);
    esp_http_client_set_header(client, "Authorization", auth_header);
    esp_http_client_set_header(client, "Content-Type",
        "application/x-www-form-urlencoded");
    esp_http_client_set_post_field(client, body, body_len);

    ESP_LOGI(TAG, "POST %s next=%s", url, next);
    esp_err_t err = esp_http_client_perform(client);
    int status = (err == ESP_OK) ? esp_http_client_get_status_code(client) : 0;
    note_api_response(err, status);
    esp_http_client_cleanup(client);

    bool ok = false;
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "mint-ticket transport: %s", esp_err_to_name(err));
    } else if (status != 200) {
        ESP_LOGE(TAG, "mint-ticket: HTTP %d", status);
    } else {
        cJSON *root = cJSON_Parse(resp.data);
        if (!root) {
            ESP_LOGW(TAG, "mint-ticket: bad JSON: %.*s", resp.len, resp.data);
        } else {
            cJSON *ticket_node = cJSON_GetObjectItem(root, "ticket");
            if (cJSON_IsString(ticket_node) && ticket_node->valuestring
                && ticket_node->valuestring[0]) {
                int n = snprintf(url_out, url_cap,
                    "%s/auth/login-ticket?t=%s",
                    config_phoneblock_base_url(),
                    ticket_node->valuestring);
                if (n > 0 && n < (int)url_cap) {
                    ok = true;
                } else {
                    ESP_LOGE(TAG, "mint-ticket: URL buffer too small");
                }
            } else {
                ESP_LOGW(TAG, "mint-ticket: response missing ticket");
            }
            cJSON_Delete(root);
        }
    }
    free(resp.data);
    return ok;
}
