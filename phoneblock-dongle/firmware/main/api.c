#include "api.h"

#include <stdatomic.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#ifdef ESP_PLATFORM
#include "esp_log.h"
#include "esp_http_client.h"
#include "esp_crt_bundle.h"
#endif

#include "api_scan.h"
#include "cJSON.h"
#include "strbuf.h"
#include "config.h"
#include "http_util.h"
#include "stats.h"
#include "platform.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

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
static void note_api_response(int transport_result, int http_status)
{
    if (transport_result != PB_HTTP_OK) return;
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

// SHA-1 of `input` → first `hex_chars` uppercase hex digits in `out`.
// `out` must hold hex_chars + 1 bytes. hex_chars must be even and <= 40.
static bool sha1_hex_prefix(const char *input, int hex_chars, char *out)
{
    unsigned char digest[20];
    if (pb_sha1(input, strlen(input), digest) != PB_OK) return false;
    static const char H[] = "0123456789ABCDEF";
    int bytes = hex_chars / 2;
    for (int i = 0; i < bytes; i++) {
        out[2 * i]     = H[(digest[i] >> 4) & 0x0F];
        out[2 * i + 1] = H[digest[i] & 0x0F];
    }
    out[hex_chars] = '\0';
    return true;
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
// pb_monotonic_us() readings (microseconds); 0 means the event did
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

// pb_http_data_fn routing the response body into the request's sink: the
// check path streams into the api_scan parser, the buffer paths append into
// a fixed-size response_buffer_t. Never aborts — the bodies are small, and a
// truncated body simply fails the parse downstream.
static bool api_sink_data(int http_status, int64_t content_length,
                          const void *data, size_t length, void *context)
{
    (void)http_status;
    (void)content_length;
    pb_sink_t *sink = context;
    if (data == NULL || sink == NULL) return true;  // status announcement
    if (sink->kind == PB_SINK_SCAN) {
        api_scan_feed(sink->scan, (const char *)data, (int)length);
    } else {
        // Copy unconditionally — see http_event_shared() on why gating on
        // is_chunked_response() silently discards chunked bodies.
        response_buffer_t *resp = sink->buf;
        int remaining = resp->cap - resp->len - 1;
        size_t copy = length < (size_t)(remaining > 0 ? remaining : 0)
                    ? length : (size_t)(remaining > 0 ? remaining : 0);
        if (copy > 0) {
            memcpy(resp->data + resp->len, data, copy);
            resp->len += (int)copy;
            resp->data[resp->len] = '\0';
        }
    }
    return true;
}

#ifdef ESP_PLATFORM
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
        sink->timing.t_connected = pb_monotonic_us();
        break;
    case HTTP_EVENT_HEADERS_SENT:
        sink->timing.t_headers_sent = pb_monotonic_us();
        break;
    case HTTP_EVENT_ON_HEADER:
        // Fires once per response header; the first one marks time to
        // first byte. Later headers must not overwrite it.
        if (sink->timing.t_first_header == 0) {
            sink->timing.t_first_header = pb_monotonic_us();
        }
        break;
    case HTTP_EVENT_ON_FINISH:
        sink->timing.t_finish = pb_monotonic_us();
        break;
    case HTTP_EVENT_ON_DATA:
        if (sink->kind == PB_SINK_SCAN) {
            api_scan_feed(sink->scan, (const char *)evt->data, evt->data_len);
        } else {
            // Copy unconditionally — see http_event_handler() above on why an
            // is_chunked_response() gate silently discards chunked bodies.
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

#endif

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

#ifdef ESP_PLATFORM
// Runs the request on `client` (its sink already wired via
// set_user_data) and times it: stamps the start, performs, derives the
// phase breakdown, logs it, records it to stats for the dashboard, and
// — if phases_out is non-NULL — copies it out. `what` tags the log
// line. Returns the esp_http_client_perform() result.
static esp_err_t run_and_time(esp_http_client_handle_t client, pb_sink_t *sink,
                              const char *what, api_phases_t *phases_out)
{
    sink->timing.t_start = pb_monotonic_us();
    esp_err_t err = esp_http_client_perform(client);
    int64_t total = pb_monotonic_us() - sink->timing.t_start;

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

#endif

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
#ifdef ESP_PLATFORM
static esp_http_client_handle_t s_check_client = NULL;
#endif
static pb_mutex_t *             s_check_mutex  = NULL;

void phoneblock_api_init(void)
{
    if (s_check_mutex == NULL) {
        s_check_mutex = pb_mutex_create();
        if (s_check_mutex == NULL) {
            pb_log_err(TAG, "failed to create API mutex");
        }
    }
}

#ifdef ESP_PLATFORM
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
#endif  // ESP_PLATFORM

// --- One API request ---------------------------------------------------

typedef struct {
    const char *method;              // "GET" or "POST"
    const char *url;
    const pb_http_header_t *headers;
    size_t header_count;
    const char *body;                // no payload unless body || body_len
    size_t body_len;
    pb_sink_t *sink;                 // response body destination, or NULL
    const char *what;                // latency log/stats tag; NULL skips it
} api_request_t;

// Runs `req` once via pb_http_request(), timing it into the sink and the
// stats when `what` is set. Returns the pb_http_request() result;
// *status_out is the HTTP status (0 on transport failure).
static int api_perform(const api_request_t *req, api_phases_t *phases_out,
                       int *status_out)
{
    pb_sink_t *sink = req->sink;
    // Without a sink the body is discarded; keep a small one anyway so a
    // short error body is still captured for the failure log.
    char discard[128];
    size_t discard_len = 0;
    pb_http_timing_t timing;
    int status = 0;
    int64_t t_start = pb_monotonic_us();
    int result = pb_http_request(req->method, req->url,
                                 req->headers, req->header_count,
                                 req->body, req->body_len, 10000,
                                 sink ? NULL : discard,
                                 sink ? 0 : sizeof(discard),
                                 sink ? NULL : &discard_len, &status,
                                 sink ? api_sink_data : NULL, sink, &timing);
    (void)discard_len;
    if (sink && timing.valid) {
        sink->timing.t_start         = t_start;
        sink->timing.t_connected     = t_start + (int64_t)timing.connect_us;
        sink->timing.t_headers_sent  = sink->timing.t_connected
                                     + (int64_t)timing.request_us;
        sink->timing.t_first_header  = sink->timing.t_headers_sent
                                     + (int64_t)timing.wait_us;
        sink->timing.t_finish        = t_start + (int64_t)timing.total_us;
    }
    if (req->what) {
        pb_sink_t empty_sink = { .kind = PB_SINK_BUFFER };
        const pb_timing_t *line = sink ? &sink->timing : &empty_sink.timing;
        int64_t total = pb_monotonic_us() - t_start;
        api_phases_t ph = derive_phases(line, total);
        pb_log_info(TAG,
                    "%s latency: total=%lldms connect=%lldms request=%lldms "
                    "wait=%lldms download=%lldms",
                    req->what, (long long)(ph.total_us / 1000),
                    (long long)(ph.connect_us / 1000),
                    (long long)(ph.request_us / 1000),
                    (long long)(ph.wait_us / 1000),
                    (long long)(ph.download_us / 1000));
        stats_record_api_phases(&ph);
        if (phases_out) {
            *phases_out = ph;
        }
    }
    if (status_out) *status_out = status;
    return result;
}

// Runs `req` on the shared, session-resuming client (see s_check_client) on
// the ESP32 — the caller must hold s_check_mutex. The Linux build has no
// session to resume, so there it is api_perform(): pb_http_request() opens
// a fresh connection per call.
static int check_perform(const api_request_t *req, api_phases_t *phases_out,
                         int *status_out)
{
#ifdef ESP_PLATFORM
    esp_http_client_handle_t client = check_client();
    if (client == NULL) return PB_HTTP_ERROR;

    esp_http_client_set_url(client, req->url);
    // Every user of the shared handle sets its own method: the handle is
    // reused across GET (check, selftest) and POST (log upload) requests,
    // so none may rely on a leftover default. See phoneblock_post_log().
    esp_http_client_set_method(client,
        strcmp(req->method, "POST") == 0 ? HTTP_METHOD_POST
                                         : HTTP_METHOD_GET);
    esp_http_client_set_user_data(client, req->sink);
    for (size_t i = 0; i < req->header_count; i++) {
        esp_http_client_set_header(client, req->headers[i].name,
                                   req->headers[i].value);
    }
    if (req->body || req->body_len) {
        esp_http_client_set_post_field(client, req->body ? req->body : "",
                                       (int)req->body_len);
    }

    esp_err_t err = req->what && req->sink
        ? run_and_time(client, req->sink, req->what, phases_out)
        : esp_http_client_perform(client);
    int status = (err == ESP_OK) ? esp_http_client_get_status_code(client) : 0;

    // Clear the POST body so a later GET on this shared handle (check,
    // selftest) carries no stale payload — those requests set only their
    // method, not the body. Close (not destroy) to keep the refreshed TLS
    // ticket for the next request.
    if (req->body || req->body_len) {
        esp_http_client_set_post_field(client, NULL, 0);
    }
    esp_http_client_close(client);
    if (status_out) *status_out = status;
    return (int)err;
#else
    return api_perform(req, phases_out, status_out);
#endif
}

verdict_t phoneblock_check(const char *phone_number, pb_check_result_t *out,
                           api_phases_t *phases_opt)
{
    if (out) {
        memset(out, 0, sizeof(*out));
        // Default to the error characterisation; any early return (mutex
        // missing, transport/parse failure) then logs as "Fehler" rather
        // than the memset-zero VERDICT_LEGITIMATE / PB_ASSESS_UNKNOWN. The
        // success path below overwrites both.
        out->verdict    = VERDICT_ERROR;
        out->assessment = PB_ASSESS_ERROR;
    }
    verdict_t verdict = VERDICT_ERROR;
    int phone_len = (int)strlen(phone_number);

    // k-anonymity: send only short hash prefixes — server cannot
    // identify which number we asked about. prefix10 / prefix100
    // enable range-based SPAM detection (last 1 / 2 digits dropped
    // before hashing).
    char hash_full[HASH_PREFIX_HEX + 1];
    char hash_p10[HASH_PREFIX_HEX + 1];
    char hash_p100[HASH_PREFIX_HEX + 1];
    if (!sha1_hex_prefix(phone_number, HASH_PREFIX_HEX, hash_full))
        return VERDICT_ERROR;

    bool have_p10  = phone_len > 1;
    bool have_p100 = phone_len > 2;
    if (have_p10) {
        char shorter[64];
        int n = phone_len - 1;
        if (n >= (int)sizeof(shorter)) n = sizeof(shorter) - 1;
        memcpy(shorter, phone_number, n);
        shorter[n] = '\0';
        if (!sha1_hex_prefix(shorter, HASH_PREFIX_HEX, hash_p10))
            return VERDICT_ERROR;
    }
    if (have_p100) {
        char shorter[64];
        int n = phone_len - 2;
        if (n >= (int)sizeof(shorter)) n = sizeof(shorter) - 1;
        memcpy(shorter, phone_number, n);
        shorter[n] = '\0';
        if (!sha1_hex_prefix(shorter, HASH_PREFIX_HEX, hash_p100))
            return VERDICT_ERROR;
    }

    char url[256];
    strbuf_t ub = sb_init(url, sizeof(url));
    sb_appendf(&ub, "%s/api/check-prefix?sha1=%s&format=json",
               config_phoneblock_base_url(), hash_full);
    if (have_p10)  sb_appendf(&ub, "&prefix10=%s", hash_p10);
    if (have_p100) sb_appendf(&ub, "&prefix100=%s", hash_p100);

    char auth_header[128];
    snprintf(auth_header, sizeof(auth_header), "Bearer %s", config_phoneblock_token());

    api_scan_t scan;
    api_scan_init(&scan, phone_number);

    if (s_check_mutex == NULL) {
        // phoneblock_api_init() was never called - refuse rather than
        // race two tasks into the request path.
        pb_log_err(TAG, "phoneblock_check before phoneblock_api_init()");
        return VERDICT_ERROR;
    }
    pb_mutex_lock(s_check_mutex);

    pb_sink_t sink = { .kind = PB_SINK_SCAN, .scan = &scan };
    const pb_http_header_t headers[] = {
        { "User-Agent", http_util_user_agent() },
        { "Authorization", auth_header },
        { "Accept", "application/json" },
    };
    api_request_t req = {
        .method = "GET", .url = url,
        .headers = headers,
        .header_count = sizeof(headers) / sizeof(headers[0]),
        .sink = &sink, .what = "check-prefix",
    };
    pb_log_info(TAG, "GET %s", url);
    int status = 0;
    int err = check_perform(&req, phases_opt, &status);
    note_api_response(err, status);

    if (err != PB_HTTP_OK) {
        pb_log_err(TAG, "HTTP request failed: %s", pb_http_err_name(err));
        goto cleanup;
    }

    if (status != 200) {
        pb_log_err(TAG, "check-prefix HTTP %d for hash %s", status, hash_full);
        goto cleanup;
    }

    pb_log_info(TAG, "HTTP %d", status);

    if (scan.error) {
        // Per-entry buffer overflow or per-entry parse failure — verdict
        // intentionally stays VERDICT_ERROR so the call is not silently
        // misclassified. Surface the scanner's reason on both the log
        // and the device dashboard.
        const char *reason = scan.error_reason ? scan.error_reason : "unknown";
        pb_log_err(TAG, "check-prefix scanner: %s", reason);
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

    pb_log_info(TAG, "Number %s → direct=%d wildcard=%d (range10 v=%d cnt=%d, range100 v=%d cnt=%d) thresholds=(direct=%d range=%d) wl=%d bl=%d → %s",
             phone_number, scan.direct_votes, wildcard_votes,
             scan.v10, scan.c10, scan.v100, scan.c100,
             min_direct, min_range,
             scan.white_listed, scan.black_listed,
             verdict == VERDICT_SPAM ? "SPAM" : "LEGITIMATE");

    if (out) {
        out->verdict = verdict;
        out->wildcard = false;   // API hits are per exact number
        strncpy(out->label,    scan.label,    sizeof(out->label)    - 1);
        strncpy(out->location, scan.location, sizeof(out->location) - 1);

        // Surface both community tallies so the label can show
        // "n direkt, m Range". direct is the exact-number vote count;
        // range is the raw neighbourhood signal (strongest of the 10-/
        // 100-block aggregations), independent of the aggregation-count
        // gate that compute_wildcard_votes() applies for the decision.
        out->direct_votes = scan.direct_votes;
        out->range_votes  = scan.v10 > scan.v100 ? scan.v10 : scan.v100;
        int community = scan.direct_votes;
        if (scan.v10  > community) community = scan.v10;
        if (scan.v100 > community) community = scan.v100;

        // Personal override wins; then a positive community verdict; then
        // a soft (below-threshold) signal; else nothing is known. Absence
        // of a signal is "unbekannt", never "legitim" — only a whitelist
        // entry earns the legitimate characterisation here.
        if (scan.white_listed) {
            out->assessment = PB_ASSESS_LEGITIMATE;
        } else if (scan.black_listed) {
            out->assessment = PB_ASSESS_BLACKLIST;
        } else if (verdict == VERDICT_SPAM) {
            out->assessment = PB_ASSESS_SPAM;
        } else if (community > 0) {
            out->assessment = PB_ASSESS_SUSPECT;
        } else {
            out->assessment = PB_ASSESS_UNKNOWN;
        }
    }

cleanup:
    pb_mutex_unlock(s_check_mutex);
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

    const pb_http_header_t headers[] = {
        { "User-Agent", http_util_user_agent() },
        { "Authorization", auth_header },
    };
    api_request_t req = {
        .method = "POST", .url = url,
        .headers = headers,
        .header_count = sizeof(headers) / sizeof(headers[0]),
        .body = "", .body_len = 0,
    };
    pb_log_info(TAG, "POST %s", url);
    int status = 0;
    int err = api_perform(&req, NULL, &status);
    note_api_response(err, status);

    if (err != PB_HTTP_OK) {
        pb_log_err(TAG, "report-call transport: %s", pb_http_err_name(err));
        return false;
    }
    if (status != 204 && status != 200) {
        pb_log_err(TAG, "report-call: HTTP %d for %s", status, phone);
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

    // JSON body, built with the bounded builder because the comment is
    // free text from the user. It MUST go through sb_append_json_escaped:
    // interpolated with a plain %s, a single '"' in the comment would end
    // the JSON string and the remainder would become structure — enough to
    // overwrite the rating being submitted. Phone and rating are validated
    // enum-like tokens, so they need no escaping.
    //
    // Sized for the caller-side comment cap (PB_RATE_COMMENT_CAP) at its
    // worst-case expansion (every byte a control character → 6 bytes).
    char body[1024];
    strbuf_t sb = sb_init(body, sizeof(body));
    sb_appendf(&sb, "{\"phone\":\"%s\",\"rating\":\"%s\"", phone, rating);
    if (comment && *comment) {
        sb_appendf(&sb, "%s", ",\"comment\":\"");
        sb_append_json_escaped(&sb, comment);
        sb_appendf(&sb, "%s", "\"");
    }
    sb_appendf(&sb, "%s", "}");
    if (sb.truncated) {
        // All-or-nothing: a clipped body is malformed JSON, and silently
        // dropping the user's reason would be worse than failing visibly.
        pb_log_err(TAG, "rate body truncated (comment too long?)");
        return false;
    }

    const pb_http_header_t headers[] = {
        { "User-Agent", http_util_user_agent() },
        { "Authorization", auth_header },
        { "Content-Type", "application/json" },
    };
    api_request_t req = {
        .method = "POST", .url = url,
        .headers = headers,
        .header_count = sizeof(headers) / sizeof(headers[0]),
        .body = body, .body_len = strlen(body),
    };
    pb_log_info(TAG, "POST %s (phone=%s rating=%s)", url, phone, rating);
    int status = 0;
    int err = api_perform(&req, NULL, &status);
    note_api_response(err, status);

    if (err != PB_HTTP_OK) {
        pb_log_err(TAG, "rate transport: %s", pb_http_err_name(err));
        return false;
    }
    if (status != 200) {
        pb_log_err(TAG, "rate: HTTP %d for %s", status, phone);
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
        pb_log_err(TAG, "self-test: out of memory");
        return false;
    }

    if (s_check_mutex == NULL) {
        pb_log_err(TAG, "phoneblock_selftest before phoneblock_api_init()");
        free(resp.data);
        return false;
    }

    // Runs on the shared session-resuming client on purpose: the
    // selftest fires synchronously at boot and again once a day, so
    // routing it here primes the TLS session ticket before the first
    // spam call and refreshes it every 24 h - keeping check-path
    // lookups on the abbreviated handshake even on dongles that go
    // days without a spam call. See the comment above s_check_client.
    // (The Linux build has no session to resume; check_perform() is a
    // plain pb_http_request() there.)
    pb_mutex_lock(s_check_mutex);

    pb_sink_t sink = { .kind = PB_SINK_BUFFER, .buf = &resp };
    const pb_http_header_t headers[] = {
        { "User-Agent", http_util_user_agent() },
        { "Authorization", auth_header },
        { "Accept", "text/plain" },
    };
    api_request_t req = {
        .method = "GET", .url = url,
        .headers = headers,
        .header_count = sizeof(headers) / sizeof(headers[0]),
        .sink = &sink, .what = "test",
    };

    // Log a short token fingerprint so a wrong-instance or truncated
    // token is obvious from the log without leaking the secret.
    size_t tlen = strlen(token);
    pb_log_info(TAG, "GET %s (token %zu chars, prefix \"%.6s…\")",
                url, tlen, tlen > 0 ? token : "");

    int status = 0;
    int err = check_perform(&req, phases_opt, &status);
    note_api_response(err, status);

    bool ok = false;
    if (err != PB_HTTP_OK) {
        pb_log_err(TAG, "self-test transport: %s", pb_http_err_name(err));
    } else if (status == 200) {
        pb_log_info(TAG, "self-test: HTTP 200, token accepted");
        ok = true;
    } else {
        pb_log_err(TAG, "self-test: HTTP %d, body: %.*s",
                   status, resp.len, resp.data);
    }
    pb_mutex_unlock(s_check_mutex);
    free(resp.data);
    return ok;
}

bool phoneblock_fetch_dial_prefix(char *out, size_t cap)
{
    if (out == NULL || cap == 0) return false;
    out[0] = '\0';

    if (s_check_mutex == NULL) {
        pb_log_err(TAG, "phoneblock_fetch_dial_prefix before phoneblock_api_init()");
        return false;
    }

    char url[160];
    snprintf(url, sizeof(url), "%s/api/account", config_phoneblock_base_url());

    char auth_header[128];
    snprintf(auth_header, sizeof(auth_header), "Bearer %s",
             config_phoneblock_token());

    // The account object is a handful of short fields; 512 bytes covers it
    // with room to spare, and a truncated body simply fails the cJSON parse
    // below rather than yielding a half-read prefix.
    response_buffer_t resp = { .data = calloc(1, 512), .len = 0, .cap = 512 };
    if (!resp.data) {
        pb_log_err(TAG, "account: out of memory");
        return false;
    }

    pb_mutex_lock(s_check_mutex);

    pb_sink_t sink = { .kind = PB_SINK_BUFFER, .buf = &resp };
    const pb_http_header_t headers[] = {
        { "User-Agent", http_util_user_agent() },
        { "Authorization", auth_header },
        { "Accept", "application/json" },
    };
    api_request_t req = {
        .method = "GET", .url = url,
        .headers = headers,
        .header_count = sizeof(headers) / sizeof(headers[0]),
        .sink = &sink, .what = "account",
    };
    int status = 0;
    int err = check_perform(&req, NULL, &status);
    note_api_response(err, status);
    pb_mutex_unlock(s_check_mutex);

    bool ok = false;
    if (err != PB_HTTP_OK) {
        pb_log_err(TAG, "account transport: %s", pb_http_err_name(err));
    } else if (status != 200) {
        // Status only, never the body: unlike /api/test this response
        // carries the account's login, display name and e-mail address,
        // and every WARN/ERROR line is mirrored into the web UI's log
        // panel (see log_capture.c).
        pb_log_err(TAG, "account: HTTP %d", status);
    } else {
        cJSON *root = cJSON_ParseWithLength(resp.data, resp.len);
        if (root == NULL) {
            pb_log_err(TAG, "account: malformed JSON response");
        } else {
            const cJSON *dp = cJSON_GetObjectItemCaseSensitive(root, "dialPrefix");
            if (cJSON_IsString(dp) && dp->valuestring && dp->valuestring[0]) {
                strncpy(out, dp->valuestring, cap - 1);
                out[cap - 1] = '\0';
                ok = true;
            } else {
                pb_log_warn(TAG, "account: no dialPrefix in response");
            }
            cJSON_Delete(root);
        }
    }

    free(resp.data);
    return ok;
}

int phoneblock_post_log(const char *body, size_t len)
{
    if (s_check_mutex == NULL) {
        pb_log_err(TAG, "phoneblock_post_log before phoneblock_api_init()");
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
        pb_log_err(TAG, "log upload: out of memory");
        return -1;
    }

    pb_mutex_lock(s_check_mutex);

    // Runs on the shared session-resuming client on purpose: the daily
    // selftest primes the TLS ticket moments before this call, so the
    // upload resumes it (abbreviated handshake, no certificate chain
    // verify) instead of paying a second full handshake. The full-
    // handshake fallback (when the per-worker ticket misses) is the
    // stack-hungry P-384 path - the caller keeps its big snapshot off
    // the stack so even that case fits. See the comment above
    // s_check_client.
    pb_sink_t sink = { .kind = PB_SINK_BUFFER, .buf = &resp };
    const pb_http_header_t headers[] = {
        { "User-Agent", http_util_user_agent() },
        { "Authorization", auth_header },
        { "Content-Type", "text/plain" },
    };
    api_request_t req = {
        .method = "POST", .url = url,
        .headers = headers,
        .header_count = sizeof(headers) / sizeof(headers[0]),
        .body = body, .body_len = len,
        .sink = &sink,
    };
    pb_log_info(TAG, "POST %s (%u bytes)", url, (unsigned)len);
    int status = 0;
    int err = check_perform(&req, NULL, &status);
    if (err != PB_HTTP_OK) {
        pb_log_err(TAG, "log upload transport: %s", pb_http_err_name(err));
        pb_mutex_unlock(s_check_mutex);
        free(resp.data);
        return -1;
    }
    pb_mutex_unlock(s_check_mutex);
    free(resp.data);
    return status;
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
        pb_log_err(TAG, "verify-code: body too large");
        return false;
    }

    response_buffer_t resp = {
        .data = calloc(1, 512),
        .len = 0,
        .cap = 512,
    };
    if (!resp.data) {
        pb_log_err(TAG, "verify-code: out of memory");
        return false;
    }

    pb_sink_t sink = { .kind = PB_SINK_BUFFER, .buf = &resp };
    const pb_http_header_t headers[] = {
        { "User-Agent", http_util_user_agent() },
        { "Content-Type", "application/x-www-form-urlencoded" },
    };
    api_request_t req = {
        .method = "POST", .url = url,
        .headers = headers,
        .header_count = sizeof(headers) / sizeof(headers[0]),
        .body = body, .body_len = (size_t)body_len,
        .sink = &sink,
    };
    pb_log_info(TAG, "POST %s", url);
    int status = 0;
    int err = api_perform(&req, NULL, &status);

    bool ok = false;
    if (err != PB_HTTP_OK) {
        pb_log_err(TAG, "verify-code transport: %s", pb_http_err_name(err));
    } else if (status != 200) {
        pb_log_err(TAG, "verify-code: HTTP %d", status);
    } else {
        cJSON *root = cJSON_Parse(resp.data);
        if (!root) {
            pb_log_warn(TAG, "verify-code: bad JSON: %.*s", resp.len, resp.data);
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
                pb_log_warn(TAG, "verify-code: rejected (reason=%s)",
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
        pb_log_err(TAG, "mint-ticket: no API token configured");
        return false;
    }

    char url[160];
    snprintf(url, sizeof(url), "%s/api/auth/login-ticket",
             config_phoneblock_base_url());

    char next_enc[256];
    if (!form_encode(next, next_enc, sizeof(next_enc))) {
        pb_log_err(TAG, "mint-ticket: 'next' too long");
        return false;
    }
    char body[320];
    int body_len = snprintf(body, sizeof(body), "next=%s", next_enc);
    if (body_len < 0 || body_len >= (int)sizeof(body)) {
        pb_log_err(TAG, "mint-ticket: body too large");
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
        pb_log_err(TAG, "mint-ticket: out of memory");
        return false;
    }

    pb_sink_t sink = { .kind = PB_SINK_BUFFER, .buf = &resp };
    const pb_http_header_t headers[] = {
        { "User-Agent", http_util_user_agent() },
        { "Authorization", auth_header },
        { "Content-Type", "application/x-www-form-urlencoded" },
    };
    api_request_t req = {
        .method = "POST", .url = url,
        .headers = headers,
        .header_count = sizeof(headers) / sizeof(headers[0]),
        .body = body, .body_len = (size_t)body_len,
        .sink = &sink,
    };
    pb_log_info(TAG, "POST %s next=%s", url, next);
    int status = 0;
    int err = api_perform(&req, NULL, &status);
    note_api_response(err, status);

    bool ok = false;
    if (err != PB_HTTP_OK) {
        pb_log_err(TAG, "mint-ticket transport: %s", pb_http_err_name(err));
    } else if (status != 200) {
        pb_log_err(TAG, "mint-ticket: HTTP %d", status);
    } else {
        cJSON *root = cJSON_Parse(resp.data);
        if (!root) {
            pb_log_warn(TAG, "mint-ticket: bad JSON: %.*s", resp.len, resp.data);
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
                    pb_log_err(TAG, "mint-ticket: URL buffer too small");
                }
            } else {
                pb_log_warn(TAG, "mint-ticket: response missing ticket");
            }
            cJSON_Delete(root);
        }
    }
    free(resp.data);
    return ok;
}
