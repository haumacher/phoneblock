#include "api.h"

#include <stdatomic.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"

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

// Raw event timestamps captured during one esp_http_client_perform()
// on the shared client. Filled by http_event_shared(); turned into an
// api_phases_t breakdown by derive_phases(). All values are
// esp_timer_get_time() readings (microseconds); 0 means the event did
// not fire. See api_phases_t in api.h for the phase semantics.
typedef struct {
    int64_t t_start;         // set by run_and_time() before perform()
    int64_t t_connected;     // HTTP_EVENT_ON_CONNECTED
    int64_t t_headers_sent;  // HTTP_EVENT_HEADERS_SENT
    int64_t t_first_header;  // first HTTP_EVENT_ON_HEADER
    int64_t t_finish;        // HTTP_EVENT_ON_FINISH
} pb_timing_t;

// Per-request sink for the shared client (see s_check_client). The one
// esp_http_client handle serves two response shapes - the streaming
// /api/check-prefix JSON and the plain /api/test body - so its single
// event handler dispatches on a tag set per request via set_user_data().
typedef enum { PB_SINK_SCAN, PB_SINK_BUFFER } pb_sink_kind_t;

typedef struct {
    pb_sink_kind_t kind;
    pb_timing_t    timing;    // event timeline of the in-flight request
    union {
        api_scan_t        *scan;  // PB_SINK_SCAN: streaming JSON parser
        response_buffer_t *buf;   // PB_SINK_BUFFER: fixed-size body buffer
    };
} pb_sink_t;

// Event dispatcher for the shared client. Beyond routing response body
// data (the check path streams into the api_scan parser, the selftest
// path appends into a fixed buffer), it timestamps the connection
// lifecycle into sink->timing so derive_phases() can split the call
// latency into phases — the core measurement for issue #329.
static esp_err_t http_event_shared(esp_http_client_event_t *evt)
{
    pb_sink_t *sink = (pb_sink_t *)evt->user_data;
    if (sink == NULL) {
        return ESP_OK;
    }
    switch (evt->event_id) {
    case HTTP_EVENT_ON_CONNECTED:
        sink->timing.t_connected = esp_timer_get_time();
        break;
    case HTTP_EVENT_HEADERS_SENT:
        sink->timing.t_headers_sent = esp_timer_get_time();
        break;
    case HTTP_EVENT_ON_HEADER:
        // Fires once per response header; the first one marks time to
        // first byte. Later headers must not overwrite it.
        if (sink->timing.t_first_header == 0) {
            sink->timing.t_first_header = esp_timer_get_time();
        }
        break;
    case HTTP_EVENT_ON_FINISH:
        sink->timing.t_finish = esp_timer_get_time();
        break;
    case HTTP_EVENT_ON_DATA:
        if (sink->kind == PB_SINK_SCAN) {
            api_scan_feed(sink->scan, (const char *)evt->data, evt->data_len);
        } else if (!esp_http_client_is_chunked_response(evt->client)) {
            response_buffer_t *resp = sink->buf;
            int remaining = resp->cap - resp->len - 1;
            int copy = evt->data_len < remaining ? evt->data_len : remaining;
            if (copy > 0) {
                memcpy(resp->data + resp->len, evt->data, copy);
                resp->len += copy;
                resp->data[resp->len] = '\0';
            }
        }
        break;
    default:
        break;
    }
    return ESP_OK;
}

// Turns the raw event timeline of one request into a phase breakdown.
// A phase whose bounding event never fired stays 0.
static api_phases_t derive_phases(const pb_timing_t *t, int64_t total_us)
{
    api_phases_t p = {0};
    p.total_us = total_us;
    p.valid = true;
    if (t->t_connected > t->t_start) {
        p.connect_us = t->t_connected - t->t_start;
    }
    if (t->t_headers_sent > t->t_connected && t->t_connected > 0) {
        p.request_us = t->t_headers_sent - t->t_connected;
    }
    if (t->t_first_header > t->t_headers_sent && t->t_headers_sent > 0) {
        p.wait_us = t->t_first_header - t->t_headers_sent;
    }
    if (t->t_finish > t->t_first_header && t->t_first_header > 0) {
        p.download_us = t->t_finish - t->t_first_header;
    }
    return p;
}

// Runs the request on `client` (its sink already wired via
// set_user_data) and times it: stamps the start, performs, derives the
// phase breakdown, logs it, records it to stats for the dashboard, and
// — if phases_out is non-NULL — copies it out. `what` tags the log
// line. Returns the esp_http_client_perform() result.
static esp_err_t run_and_time(esp_http_client_handle_t client, pb_sink_t *sink,
                              const char *what, api_phases_t *phases_out)
{
    sink->timing.t_start = esp_timer_get_time();
    esp_err_t err = esp_http_client_perform(client);
    int64_t total = esp_timer_get_time() - sink->timing.t_start;

    api_phases_t p = derive_phases(&sink->timing, total);
    ESP_LOGI(TAG, "%s latency: total=%lldms connect=%lldms request=%lldms wait=%lldms download=%lldms",
             what, (long long)(p.total_us / 1000), (long long)(p.connect_us / 1000),
             (long long)(p.request_us / 1000), (long long)(p.wait_us / 1000),
             (long long)(p.download_us / 1000));
    stats_record_api_phases(&p);
    if (phases_out) {
        *phases_out = p;
    }
    return err;
}

// --- Shared, session-resuming HTTPS client ---------------------------
//
// The dongle makes only 1-2 /api/check-prefix calls per day. A fresh
// TLS handshake costs ~1-2 s on the ESP32 - long enough for the
// Fritz!Box to start ringing the real phones before the verdict is in.
// To cut that, the spam-lookup path reuses one long-lived
// esp_http_client handle with save_client_session enabled: the TCP
// connection is closed after every request (no socket and no server
// state held while idle), but the TLS session ticket is cached in the
// handle and replayed on the next request, which then skips the
// certificate exchange and the asymmetric crypto. esp_http_client frees
// the saved ticket only on cleanup()/destroy, not on close(), so
// closing between requests is safe.
//
// phoneblock_selftest() deliberately shares this handle: it runs
// synchronously at boot and again once a day, so it primes the ticket
// before the very first spam call and refreshes it every 24 h. That
// keeps the ticket inside the server's lifetime window even on dongles
// that go days without a spam call - without it, resumption would only
// hold while consecutive calls stay within that window.
//
// The handle is created lazily via check_client(). s_check_mutex
// serialises access - phoneblock_check() runs from both the SIP task
// and the LAN debug-query server, the selftest from app_main and its
// daily task, and a single esp_http_client handle must not be driven
// from two tasks at once.
static esp_http_client_handle_t s_check_client = NULL;
static SemaphoreHandle_t        s_check_mutex  = NULL;

void phoneblock_api_init(void)
{
    if (s_check_mutex == NULL) {
        s_check_mutex = xSemaphoreCreateMutex();
        if (s_check_mutex == NULL) {
            ESP_LOGE(TAG, "failed to create API mutex");
        }
    }
}

// Returns the shared, session-resuming HTTPS client for phoneblock.net,
// creating it on first use. The caller must hold s_check_mutex. Returns
// NULL when the handle could not be created.
static esp_http_client_handle_t check_client(void)
{
    if (s_check_client == NULL) {
        esp_http_client_config_t config = {
            .url = config_phoneblock_base_url(),  // overwritten per request
            .event_handler = http_event_shared,
            .crt_bundle_attach = esp_crt_bundle_attach,
            .timeout_ms = 10000,
            .auth_type = HTTP_AUTH_TYPE_NONE,
            // Pin TLS 1.2. esp_http_client saves the session for
            // resumption right after the handshake - but a TLS 1.3
            // server delivers its session ticket as a post-handshake
            // message, too late for that save, so TLS 1.3 would
            // capture an unusable session and resume nothing. TLS 1.2
            // sends the ticket in-handshake, so save_client_session
            // below actually captures a resumable session. (TLS 1.3
            // is also disabled project-wide - see sdkconfig.defaults.)
            .tls_version = ESP_HTTP_CLIENT_TLS_VER_TLS_1_2,
#if CONFIG_ESP_TLS_CLIENT_SESSION_TICKETS
            .save_client_session = true,
#endif
        };
        s_check_client = esp_http_client_init(&config);
        if (s_check_client == NULL) {
            ESP_LOGE(TAG, "failed to create shared HTTP client");
        }
    }
    return s_check_client;
}

verdict_t phoneblock_check(const char *phone_number, pb_check_result_t *out,
                           api_phases_t *phases_opt)
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

    if (s_check_mutex == NULL) {
        // phoneblock_api_init() was never called - refuse rather than
        // race two tasks into esp_http_client_init().
        ESP_LOGE(TAG, "phoneblock_check before phoneblock_api_init()");
        return VERDICT_ERROR;
    }
    xSemaphoreTake(s_check_mutex, portMAX_DELAY);

    esp_http_client_handle_t client = check_client();
    if (client == NULL) {
        xSemaphoreGive(s_check_mutex);
        return VERDICT_ERROR;
    }

    pb_sink_t sink = { .kind = PB_SINK_SCAN, .scan = &scan };
    esp_http_client_set_url(client, url);
    // Every user of the shared handle sets its own method: the handle is
    // reused across GET (check, selftest) and POST (log upload) requests,
    // so none may rely on a leftover default. See phoneblock_post_log().
    esp_http_client_set_method(client, HTTP_METHOD_GET);
    esp_http_client_set_user_data(client, &sink);
    http_util_set_user_agent(client);
    esp_http_client_set_header(client, "Authorization", auth_header);
    esp_http_client_set_header(client, "Accept", "application/json");

    ESP_LOGI(TAG, "GET %s", url);
    esp_err_t err = run_and_time(client, &sink, "check-prefix", phases_opt);

    int status = (err == ESP_OK) ? esp_http_client_get_status_code(client) : 0;
    note_api_response(err, status);

    if (err != ESP_OK) {
        ESP_LOGE(TAG, "HTTP request failed: %s", esp_err_to_name(err));
        goto cleanup;
    }

    if (status != 200) {
        ESP_LOGE(TAG, "check-prefix HTTP %d for hash %s", status, hash_full);
        goto cleanup;
    }

    ESP_LOGI(TAG, "HTTP %d", status);

    if (scan.error) {
        // Per-entry buffer overflow or per-entry parse failure — verdict
        // intentionally stays VERDICT_ERROR so the call is not silently
        // misclassified. Surface the scanner's reason on both the log
        // and the device dashboard.
        const char *reason = scan.error_reason ? scan.error_reason : "unknown";
        ESP_LOGE(TAG, "check-prefix scanner: %s", reason);
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
    // Close the TCP/TLS connection but keep the handle: esp_http_client
    // retains the saved TLS session ticket across close() (only
    // cleanup()/destroy frees it), so the next call resumes the session
    // instead of doing a full handshake. No connection and no socket
    // are held while the dongle is idle.
    esp_http_client_close(client);
    xSemaphoreGive(s_check_mutex);
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
        return false;
    }
    if (status != 204 && status != 200) {
        ESP_LOGE(TAG, "report-call: HTTP %d for %s", status, phone);
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

bool phoneblock_selftest(api_phases_t *phases_opt)
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

    if (s_check_mutex == NULL) {
        ESP_LOGE(TAG, "phoneblock_selftest before phoneblock_api_init()");
        free(resp.data);
        return false;
    }

    // Runs on the shared session-resuming client on purpose: the
    // selftest fires synchronously at boot and again once a day, so
    // routing it here primes the TLS session ticket before the first
    // spam call and refreshes it every 24 h - keeping check-path
    // lookups on the abbreviated handshake even on dongles that go
    // days without a spam call. See the comment above s_check_client.
    xSemaphoreTake(s_check_mutex, portMAX_DELAY);

    esp_http_client_handle_t client = check_client();
    if (client == NULL) {
        xSemaphoreGive(s_check_mutex);
        free(resp.data);
        return false;
    }

    pb_sink_t sink = { .kind = PB_SINK_BUFFER, .buf = &resp };
    esp_http_client_set_url(client, url);
    esp_http_client_set_method(client, HTTP_METHOD_GET);
    esp_http_client_set_user_data(client, &sink);
    http_util_set_user_agent(client);
    esp_http_client_set_header(client, "Authorization", auth_header);
    esp_http_client_set_header(client, "Accept", "text/plain");

    // Log a short token fingerprint so a wrong-instance or truncated
    // token is obvious from the log without leaking the secret.
    size_t tlen = strlen(token);
    ESP_LOGI(TAG, "GET %s (token %zu chars, prefix \"%.6s…\")",
             url, tlen, tlen > 0 ? token : "");

    esp_err_t err = run_and_time(client, &sink, "test", phases_opt);

    int status = (err == ESP_OK) ? esp_http_client_get_status_code(client) : 0;
    note_api_response(err, status);

    bool ok = false;
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "self-test transport: %s", esp_err_to_name(err));
    } else if (status == 200) {
        ESP_LOGI(TAG, "self-test: HTTP 200, token accepted");
        ok = true;
    } else {
        ESP_LOGE(TAG, "self-test: HTTP %d, body: %.*s",
                 status, resp.len, resp.data);
    }
    // Close but keep the handle so the freshly issued TLS session
    // ticket survives for the next spam-lookup call.
    esp_http_client_close(client);
    xSemaphoreGive(s_check_mutex);
    free(resp.data);
    return ok;
}

int phoneblock_post_log(const char *body, size_t len)
{
    if (s_check_mutex == NULL) {
        ESP_LOGE(TAG, "phoneblock_post_log before phoneblock_api_init()");
        return -1;
    }

    char url[160];
    snprintf(url, sizeof(url), "%s/api/dongle/log", config_phoneblock_base_url());

    const char *token = config_phoneblock_token();
    char auth_header[128];
    snprintf(auth_header, sizeof(auth_header), "Bearer %s", token);

    // Discard the response body; only the status matters. A small buffer
    // is enough to capture a short error body for the failure log.
    response_buffer_t resp = { .data = calloc(1, 128), .len = 0, .cap = 128 };
    if (!resp.data) {
        ESP_LOGE(TAG, "log upload: out of memory");
        return -1;
    }

    xSemaphoreTake(s_check_mutex, portMAX_DELAY);

    // Runs on the shared session-resuming client on purpose: the daily
    // selftest primes the TLS ticket moments before this call, so the
    // upload resumes it (abbreviated handshake, no certificate chain
    // verify) instead of paying a second full handshake. The full-
    // handshake fallback (when the per-worker ticket misses) is the
    // stack-hungry P-384 path - the caller keeps its big snapshot off
    // the stack so even that case fits. See the comment above
    // s_check_client.
    esp_http_client_handle_t client = check_client();
    if (client == NULL) {
        xSemaphoreGive(s_check_mutex);
        free(resp.data);
        return -1;
    }

    pb_sink_t sink = { .kind = PB_SINK_BUFFER, .buf = &resp };
    esp_http_client_set_url(client, url);
    esp_http_client_set_method(client, HTTP_METHOD_POST);
    esp_http_client_set_user_data(client, &sink);
    http_util_set_user_agent(client);
    esp_http_client_set_header(client, "Authorization", auth_header);
    esp_http_client_set_header(client, "Content-Type", "text/plain");
    esp_http_client_set_post_field(client, body, (int)len);

    ESP_LOGI(TAG, "POST %s (%u bytes)", url, (unsigned)len);
    esp_err_t err = esp_http_client_perform(client);
    int status = (err == ESP_OK) ? esp_http_client_get_status_code(client) : -1;
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "log upload transport: %s", esp_err_to_name(err));
    }

    // Clear the POST body so a later GET on this shared handle (check,
    // selftest) carries no stale payload - those callers set only their
    // method, not the body. Close (not destroy) to keep the refreshed
    // TLS ticket for the next request.
    esp_http_client_set_post_field(client, NULL, 0);
    esp_http_client_close(client);
    xSemaphoreGive(s_check_mutex);
    free(resp.data);
    return status;
}

// --- Diagnostic latency probe (issue #329) --------------------------

// Synthetic number for the probe's /api/check-prefix call. Exercises
// the real spam-lookup path (hashing, three k-anonymity buckets,
// streaming JSON parse) without querying a real subscriber. Never
// reported: phoneblock_check() does not POST /api/report-call.
#define PROBE_SYNTHETIC_NUMBER "+490000000000"

static void phases_add(api_phases_t *acc, const api_phases_t *p)
{
    acc->connect_us  += p->connect_us;
    acc->request_us  += p->request_us;
    acc->wait_us     += p->wait_us;
    acc->download_us += p->download_us;
    acc->total_us    += p->total_us;
}

// Appends one formatted phase line to report[pos..cap). Returns the new
// position, clamped to cap-1 so the buffer stays NUL-terminable.
static size_t probe_append(char *report, size_t pos, size_t cap,
                           const char *tag, const api_phases_t *p)
{
    if (pos >= cap) return cap - 1;
    int n = snprintf(report + pos, cap - pos,
        "%-6s total=%4lld connect=%4lld request=%3lld wait=%4lld download=%4lld (ms)\n",
        tag,
        (long long)(p->total_us    / 1000), (long long)(p->connect_us  / 1000),
        (long long)(p->request_us  / 1000), (long long)(p->wait_us     / 1000),
        (long long)(p->download_us / 1000));
    if (n < 0) return pos;
    pos += (size_t)n;
    return pos < cap ? pos : cap - 1;
}

int api_run_probe(int rounds, char *report, size_t cap)
{
    if (!report || cap == 0) return 0;
    if (rounds < 1) rounds = 1;
    if (rounds > 5) rounds = 5;

    report[0] = '\0';
    size_t pos = 0;
    int n = snprintf(report, cap, "PROBE issue#329 rounds=%d\n", rounds);
    if (n > 0) pos = (size_t)n < cap ? (size_t)n : cap - 1;

    api_phases_t test_sum = {0}, check_sum = {0};
    int measured = 0;
    char tag[8];

    for (int r = 1; r <= rounds; r++) {
        api_phases_t pt = {0};
        phoneblock_selftest(&pt);
        phases_add(&test_sum, &pt);
        snprintf(tag, sizeof(tag), "test%d", r);
        pos = probe_append(report, pos, cap, tag, &pt);
        measured++;

        api_phases_t pc = {0};
        phoneblock_check(PROBE_SYNTHETIC_NUMBER, NULL, &pc);
        phases_add(&check_sum, &pc);
        snprintf(tag, sizeof(tag), "chk%d", r);
        pos = probe_append(report, pos, cap, tag, &pc);
        measured++;
    }

    if (rounds > 1) {
        api_phases_t ta = test_sum, ca = check_sum;
        ta.connect_us /= rounds; ta.request_us /= rounds; ta.wait_us /= rounds;
        ta.download_us /= rounds; ta.total_us /= rounds;
        ca.connect_us /= rounds; ca.request_us /= rounds; ca.wait_us /= rounds;
        ca.download_us /= rounds; ca.total_us /= rounds;
        pos = probe_append(report, pos, cap, "AVGtst", &ta);
        pos = probe_append(report, pos, cap, "AVGchk", &ca);
    }

    report[pos < cap ? pos : cap - 1] = '\0';
    return measured;
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
