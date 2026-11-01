#include "web.h"

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <sys/stat.h>

#ifdef ESP_PLATFORM
#include "esp_log.h"
#include "esp_task_wdt.h"
#include "esp_timer.h"
#include "esp_app_desc.h"
#include "esp_heap_caps.h"   // heap_caps_get_largest_free_block
#include "esp_netif.h"
#include "esp_http_server.h"
#include "esp_ota_ops.h"
#include "esp_partition.h"
#include "esp_system.h"       // esp_restart
#include "esp_wifi.h"         // esp_wifi_restore for factory-reset
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "lwip/sockets.h"     // INET_ADDRSTRLEN
#else
#include <arpa/inet.h>
#include <sys/socket.h>
#include <unistd.h>
#endif
#include "cJSON.h"

#include "announcement.h"
#include "chip_temp.h"
#include "api.h"
#include "blocklist_sync.h"
#include "i18n_sync.h"
#include "storage.h"
#include "config.h"
#include "dialog_manifest.h"
#include "dialog_store.h"
#if CONFIG_IDF_TARGET_ESP32S3
#include "display.h"
#endif
#include "heap_guard.h"
#include "firmware_update.h"
#include "log_capture.h"
#include "mail.h"
#include "mail_rcpt.h"
#include "name_filter.h"
#include "platform.h"
#include "rtp.h"
#include "scheduler.h"
#include "sip_register.h"
#include "sip_parse.h"   // is_known_contact() for the "a contact is never spam" rule
#include "stats.h"
#include "sync.h"
#include "strbuf.h"
#include "time_sync.h"
#include "tr064.h"
#include "tr064_parse.h"
#include "web_auth.h"
#include "web_access.h"
#include "webhook.h"
#ifdef ESP_PLATFORM
#include "wifi.h"
#else
#include "link.h"
#endif
#include "web_http.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static const char *TAG = "web";

static web_http_server_t *s_server = NULL;
#ifdef ESP_PLATFORM
static uint16_t s_web_port = 80;
#else
static uint16_t s_web_port = 8080;
#endif
static char s_web_bind_address[64];

// Gate handlers behind the SSO session cookie. With the gate
// disabled (config_auth_enabled() == false) these are no-ops; once
// activated they bounce unauthenticated callers either to "/" (HTML
// — index.html itself renders the in-page login state) or to a 401
// (API). The do/while wrapper keeps the macro safe inside an
// if/else without braces.
//
// These macros also carry the heap sentinel's breadcrumb: every handler opens
// with one of them, which makes them the only central "a request is being
// served" hook short of touching each handler individually. See heap_guard.h.
#define REQUIRE_AUTH_HTML(req) do { \
    heap_guard_note("http:html"); \
    if (!web_auth_required((req), false)) return WEB_HTTP_OK; \
} while (0)
#define REQUIRE_AUTH_API(req) do { \
    heap_guard_note("http:api"); \
    if (!web_auth_required((req), true)) return WEB_HTTP_OK; \
} while (0)
// For the intentionally-public routes: LAN-only when the gate is off,
// fully public when it is on (so remote login works). See
// docs/network-access-control.md.
#define REQUIRE_LOCAL_HTML(req) do { \
    if (!web_public_allowed((req), false)) return WEB_HTTP_OK; \
} while (0)

// In-flight OAuth provisioning state: a random nonce set by
// /register-start, consumed once by /token-callback to defeat CSRF.
// Single entry — only one browser tab can provision at a time, which
// matches the "user sets up the dongle once" UX.
static char s_oauth_nonce[33] = "";

// Per-boot confirmation nonce for /api/factory-reset. Generated once
// at web_start and exposed through /api/status. Without this defense
// Firefox silently re-submits a queued factory-reset POST (body
// "confirm=yes" and all) as soon as the dongle is reachable again
// after reboot — so pressing the red button once and then rebooting
// the device (for any reason) effectively rearms another reset.
static char s_reset_nonce[17] = "";

static void generate_reset_nonce(void)
{
    static const char hex[] = "0123456789abcdef";
    for (int i = 0; i < 16; i++) {
        s_reset_nonce[i] = hex[pb_random_u32() & 0xf];
    }
    s_reset_nonce[16] = '\0';
}

// In-flight Fritz!Box 2FA state: when the initial TR-064 SetClient4 call
// fails with errorCode 866 we stash enough context here to complete the
// provisioning once the user has pressed a button / entered a DTMF code.
// Polled from the web UI via /api/fritzbox-2fa-status.
static struct {
    bool  active;
    int64_t started_us;
    char  fritz_host[64];
    char  fritz_user[32];
    char  fritz_pass[64];
    char  phone_name[48];
    char  token[64];
    char  state[32];
    char  methods[64];
} s_2fa;

// --- Embedded HTML (placeholder; full UI lands in the next step) ---

#ifdef ESP_PLATFORM
// Embedded via EMBED_FILES in CMakeLists.txt; the linker emits
// _binary_<file>_<ext>_{start,end} symbols which we alias to friendlier
// C names.
// The dashboard SPA is embedded gzip-compressed (see main/CMakeLists.txt) and
// served with Content-Encoding: gzip. Symbol name follows the .gz filename.
extern const uint8_t index_html_gz_start[] asm("_binary_index_html_gz_start");
extern const uint8_t index_html_gz_end[]   asm("_binary_index_html_gz_end");

extern const uint8_t ab_logo_bot_svg_start[] asm("_binary_ab_logo_bot_svg_start");
extern const uint8_t ab_logo_bot_svg_end[]   asm("_binary_ab_logo_bot_svg_end");
#else
#define WEB_ASSET_DIR "/usr/share/phoneblock-dongle/web"

static FILE *open_web_asset(const char *name)
{
    const char *directory = getenv("PHONEBLOCK_WEB_DIR");
    if (!directory || !directory[0]) directory = WEB_ASSET_DIR;
    char path[512];
    int length = snprintf(path, sizeof(path), "%s/%s", directory, name);
    if (length < 0 || (size_t)length >= sizeof(path)) return NULL;
    return fopen(path, "rb");
}

// `encoding` (e.g. "gzip") is announced only once the file is open, so a
// missing asset gets a plain-text 404 a browser can display.
static web_http_error_t send_web_asset(web_http_request_t *request,
                                       const char *name, const char *encoding)
{
    FILE *file = open_web_asset(name);
    if (!file) {
        web_http_send_error(request, WEB_HTTP_STATUS_NOT_FOUND,
                            "web asset not installed");
        return WEB_HTTP_OK;
    }
    if (encoding) web_http_set_header(request, "Content-Encoding", encoding);
    char buffer[4096];
    size_t length;
    while ((length = fread(buffer, 1, sizeof(buffer), file)) > 0) {
        if (web_http_send_chunk(request, buffer, length) != WEB_HTTP_OK) {
            fclose(file);
            return WEB_HTTP_FAIL;
        }
    }
    bool read_error = ferror(file);
    fclose(file);
    if (read_error) return WEB_HTTP_FAIL;
    return web_http_send_chunk(request, NULL, 0) == WEB_HTTP_OK
        ? WEB_HTTP_OK : WEB_HTTP_FAIL;
}
#endif

// --- Helpers --------------------------------------------------------

static void send_json(web_http_request_t *req, cJSON *root)
{
    char *body = cJSON_PrintUnformatted(root);
    web_http_set_type(req, "application/json; charset=utf-8");
    web_http_set_header(req, "Cache-Control", "no-store");
    if (body) {
        web_http_send_text(req, body);
        cJSON_free(body);
    } else {
        web_http_send(req, "{}", 2);
    }
    cJSON_Delete(root);
}

// Wire name for the log characterisation. The web UI (verdictLabel in
// index.html) maps these onto localised labels and colour classes; keep
// the set in sync with pb_assessment_t and the JS switch.
static const char *assessment_string(pb_assessment_t a)
{
    switch (a) {
        case PB_ASSESS_LEGITIMATE: return "legitimate";
        case PB_ASSESS_SUSPECT:    return "suspect";
        case PB_ASSESS_SPAM:       return "spam";
        case PB_ASSESS_SPAM_LIST:  return "spam_list";
        case PB_ASSESS_BLACKLIST:  return "blacklist";
        case PB_ASSESS_NAME_PATTERN: return "name_pattern";
        case PB_ASSESS_TEST:       return "test";
        case PB_ASSESS_ERROR:      return "error";
        case PB_ASSESS_UNKNOWN:
        default:                   return "unknown";
    }
}

// Whether a listed call is currently blocked as spam, from any source.
static bool assessment_is_spam(pb_assessment_t a)
{
    return a == PB_ASSESS_SPAM || a == PB_ASSESS_SPAM_LIST
        || a == PB_ASSESS_BLACKLIST || a == PB_ASSESS_NAME_PATTERN;
}

static void local_ip_str(char *out, size_t cap)
{
    if (!web_local_ipv4_string(out, cap)) out[0] = '\0';
}

// --- Private Network Access opt-in ---------------------------------
//
// Chrome 130+ / current Edge enforce a CORS preflight when a top-level
// navigation crosses from a public origin (https://phoneblock.net) to a
// private IP target (this dongle on http://192.168.x.x/). Without an
// explicit Allow-Private-Network: true response the navigation is
// silently blocked and the install page appears to "do nothing" after
// flashing — every diagnostic looks fine, the browser just refuses to
// hop networks. The opt-in is two pieces:
//
//  1. Answer the OPTIONS preflight on any URI with the PNA + ACAO
//     headers (handle_pna_preflight, registered as a wildcard route).
//  2. Mirror the PNA + ACAO headers on the regular response the
//     browser actually navigates to (set_pna_response_headers, called
//     from handle_root).
//
// The Origin allowlist is exactly one value. Anything else gets the
// regular 404/no-headers response so drive-by scanners don't see we
// even speak PNA.

static const char PNA_ALLOWED_ORIGIN[] = "https://phoneblock.net";

static bool pna_origin_allowed(const char *origin)
{
    return origin != NULL && strcmp(origin, PNA_ALLOWED_ORIGIN) == 0;
}

// Mirrors the PNA/ACAO headers onto a regular 2xx response if the
// caller's Origin is on the allowlist. No-op otherwise.
static void set_pna_response_headers(web_http_request_t *req)
{
    char origin[64];
    if (web_http_get_header(req, "Origin", origin, sizeof(origin)) != WEB_HTTP_OK) {
        return;
    }
    if (!pna_origin_allowed(origin)) {
        return;
    }
    web_http_set_header(req, "Access-Control-Allow-Origin",          PNA_ALLOWED_ORIGIN);
    web_http_set_header(req, "Access-Control-Allow-Private-Network", "true");
    web_http_set_header(req, "Vary",
                       "Origin, Access-Control-Request-Private-Network");
}

static web_http_error_t handle_pna_preflight(web_http_request_t *req)
{
    REQUIRE_LOCAL_HTML(req);
    char origin[64];
    if (web_http_get_header(req, "Origin", origin, sizeof(origin)) != WEB_HTTP_OK
            || !pna_origin_allowed(origin)) {
        web_http_set_status(req, "404 Not Found");
        web_http_send(req, NULL, 0);
        return WEB_HTTP_OK;
    }

    // The preflight is *triggered* by the PNA header; mirror it back
    // only when present so plain CORS-only callers (no PNA in use) see
    // a clean ACAO response.
    char pna[8];
    bool wants_pna = web_http_get_header(
        req, "Access-Control-Request-Private-Network",
        pna, sizeof(pna)) == WEB_HTTP_OK
        && strcmp(pna, "true") == 0;

    web_http_set_header(req, "Access-Control-Allow-Origin",  PNA_ALLOWED_ORIGIN);
    if (wants_pna) {
        web_http_set_header(req, "Access-Control-Allow-Private-Network", "true");
    }
    web_http_set_header(req, "Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    web_http_set_header(req, "Access-Control-Allow-Headers", "Content-Type");
    web_http_set_header(req, "Access-Control-Max-Age",       "600");
    web_http_set_header(req, "Vary",
                       "Origin, Access-Control-Request-Private-Network");

    web_http_set_status(req, "204 No Content");
    web_http_send(req, NULL, 0);
    return WEB_HTTP_OK;
}

// --- Handlers -------------------------------------------------------

static web_http_error_t handle_health(web_http_request_t *req)
{
    web_http_set_type(req, "text/plain; charset=utf-8");
    return web_http_send_text(req, "ok\n");
}

static web_http_error_t handle_favicon(web_http_request_t *req)
{
    REQUIRE_LOCAL_HTML(req);
    web_http_set_type(req, "image/svg+xml");
    web_http_set_header(req, "Cache-Control", "public, max-age=31536000, immutable");
#ifdef ESP_PLATFORM
    return web_http_send(req, ab_logo_bot_svg_start,
                         ab_logo_bot_svg_end - ab_logo_bot_svg_start) == 0
        ? WEB_HTTP_OK : WEB_HTTP_FAIL;
#else
    return send_web_asset(req, "ab-logo-bot.svg", NULL);
#endif
}

static web_http_error_t handle_root(web_http_request_t *req)
{
    // Locality-gated but NOT auth-gated. The SPA shell is harmless static
    // markup and contains both the dashboard and the in-page login state;
    // it chooses between them at runtime based on a 200/401 from the gated
    // /api/status. Auth-gating "/" would break the in-page login bounce
    // (web_auth_required redirects HTML routes to "/"). REQUIRE_LOCAL_HTML
    // still keeps a gate-off dongle from serving the shell to a remote
    // scanner, while staying fully public once the gate is on so remote
    // login works.
    REQUIRE_LOCAL_HTML(req);
    set_pna_response_headers(req);
    web_http_set_type(req, "text/html; charset=utf-8");
    // Served pre-compressed. Every browser that reaches this dashboard sends
    // "Accept-Encoding: gzip", so we emit gzip unconditionally rather than
    // embedding a second uncompressed copy (which would defeat the saving).
#ifdef ESP_PLATFORM
    web_http_set_header(req, "Content-Encoding", "gzip");
    return web_http_send(req, index_html_gz_start,
                         index_html_gz_end - index_html_gz_start) == 0
        ? WEB_HTTP_OK : WEB_HTTP_FAIL;
#else
    return send_web_asset(req, "index.html.gz", "gzip");
#endif
}

// Add heap figures and, where the run-time stats are compiled in, a
// per-task CPU breakdown under "system". This is the live field-diagnosis
// readout for "the dongle is hot / unstable": a runaway task shows up as a
// high cpu_pct instead of having to guess. The CPU figures are sampled
// over a short window (so they reflect *current* load, not a boot
// average), which briefly delays this one request — fine for a diagnostic
// poll. Percentages are of total CPU across both cores, so a task pegging
// a single core reads ~50%.
static void add_system_load(cJSON *root)
{
#ifdef ESP_PLATFORM
    cJSON *sys = cJSON_AddObjectToObject(root, "system");
    cJSON_AddNumberToObject(sys, "free_heap",     (double)esp_get_free_heap_size());
    cJSON_AddNumberToObject(sys, "min_free_heap", (double)esp_get_minimum_free_heap_size());
    // Largest contiguous free block (MALLOC_CAP_DEFAULT — what malloc/mbedTLS
    // draw from). When this falls below a TLS connection's buffer the
    // handshake fails with -0x7F00 even though free_heap still looks fine, so
    // a low value here vs. free_heap is the fingerprint of heap fragmentation.
    cJSON_AddNumberToObject(sys, "largest_free_block",
                            (double)heap_caps_get_largest_free_block(MALLOC_CAP_DEFAULT));

    // Data-partition ("storage", LittleFS, storage.h) usage. The
    // blocklist cache, the announcement blob, the packs, the dialog set and
    // the temp files used during sync all share this one mount; surface the
    // live free-byte count here for field diagnosis.
    size_t fs_total = 0, fs_used = 0;
    if (storage_info(&fs_total, &fs_used)) {
        cJSON_AddNumberToObject(sys, "fs_total", (double)fs_total);
        cJSON_AddNumberToObject(sys, "fs_used",  (double)fs_used);
        cJSON_AddNumberToObject(sys, "fs_free",  (double)(fs_total - fs_used));
    }

    // Die temperature, only on chips with a sensor (not the classic ESP32).
    float temp_c;
    if (chip_temp_read(&temp_c)) {
        cJSON_AddNumberToObject(sys, "temp_c", (double)((int)(temp_c * 10.0f + 0.5f)) / 10.0);
    }

    // Link quality: RSSI is what the dongle receives from the AP; tx_dbm is
    // the driver's transmit-power limit in effect.
    int rssi;
    float tx_dbm = 0;
    if (wifi_link_info(&rssi, &tx_dbm)) {
        cJSON_AddNumberToObject(sys, "wifi_rssi", rssi);
    }
    if (tx_dbm > 0) cJSON_AddNumberToObject(sys, "wifi_tx_dbm", tx_dbm);

#if CONFIG_FREERTOS_GENERATE_RUN_TIME_STATS
    UBaseType_t cap = uxTaskGetNumberOfTasks() + 4;   // headroom for mid-sample spawns
    TaskStatus_t *a = malloc(cap * sizeof(TaskStatus_t));
    TaskStatus_t *b = malloc(cap * sizeof(TaskStatus_t));
    if (!a || !b) { free(a); free(b); return; }

    uint32_t t0 = 0, t1 = 0;
    UBaseType_t na = uxTaskGetSystemState(a, cap, &t0);
    vTaskDelay(pdMS_TO_TICKS(250));
    UBaseType_t nb = uxTaskGetSystemState(b, cap, &t1);
    uint32_t dtotal = t1 - t0;

    cJSON *tasks = cJSON_AddArrayToObject(sys, "tasks");
    for (UBaseType_t i = 0; i < nb; i++) {
        uint32_t prev = 0;
        for (UBaseType_t j = 0; j < na; j++) {
            if (a[j].xHandle == b[i].xHandle) { prev = a[j].ulRunTimeCounter; break; }
        }
        uint32_t d   = b[i].ulRunTimeCounter - prev;   // wraps cleanly (unsigned)
        uint32_t pct = dtotal ? (uint32_t)(((uint64_t)d * 100) / dtotal) : 0;
        cJSON *t = cJSON_CreateObject();
        cJSON_AddStringToObject(t, "name",       b[i].pcTaskName);
        cJSON_AddNumberToObject(t, "cpu_pct",    pct);
        cJSON_AddNumberToObject(t, "stack_free", b[i].usStackHighWaterMark);
        cJSON_AddItemToArray(tasks, t);
    }
    free(a);
    free(b);
#endif
#else
    (void)root;
#endif
}

static web_http_error_t handle_status(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    stats_counters_t c;
    stats_snapshot_counters(&c);

    int64_t now_us   = pb_monotonic_us();
    int64_t uptime_s = now_us / 1000000;
    int64_t registered_for_s =
        c.sip_registered ? (now_us - c.sip_registered_since_us) / 1000000 : 0;

#ifdef ESP_PLATFORM
    const esp_app_desc_t *app = esp_app_get_description();
    const esp_partition_t *running = esp_ota_get_running_partition();
#endif

    char ip[INET_ADDRSTRLEN] = "";
    local_ip_str(ip, sizeof(ip));

    cJSON *root = cJSON_CreateObject();
#ifdef ESP_PLATFORM
    cJSON_AddStringToObject(root, "firmware_version",   app ? app->version : "");
    cJSON_AddStringToObject(root, "firmware_partition", running ? running->label : "");
    cJSON_AddStringToObject(root, "project_name",       app ? app->project_name : "");
#else
    cJSON_AddStringToObject(root, "firmware_version", pb_firmware_version());
    cJSON_AddStringToObject(root, "firmware_partition", "");
    cJSON_AddStringToObject(root, "project_name", "PhoneBlock-Dongle");
#endif
    cJSON_AddStringToObject(root, "firmware_failed", config_last_failed_ota());
    cJSON_AddNumberToObject(root, "uptime_s",         (double)uptime_s);
    cJSON_AddStringToObject(root, "ip_address",       ip);
    cJSON_AddStringToObject(root, "reset_nonce",      s_reset_nonce);
#ifdef ESP_PLATFORM
    cJSON_AddBoolToObject(root, "mail_reporting", true);
    cJSON_AddBoolToObject(root, "firmware_update", true);
    cJSON_AddBoolToObject(root, "factory_reset", true);
#else
    cJSON_AddBoolToObject(root, "mail_reporting", false);
    cJSON_AddBoolToObject(root, "firmware_update", false);
    cJSON_AddBoolToObject(root, "factory_reset", false);
#endif
#ifdef CONFIG_CRASH_TEST_ENABLE
    cJSON_AddBoolToObject  (root, "crash_test_enabled", true);
#else
    cJSON_AddBoolToObject  (root, "crash_test_enabled", false);
#endif

    cJSON *sip = cJSON_AddObjectToObject(root, "sip");
    cJSON_AddBoolToObject  (sip,  "registered",        c.sip_registered);
    cJSON_AddNumberToObject(sip,  "registered_for_s",  (double)registered_for_s);
    cJSON_AddStringToObject(sip,  "host",              config_sip_host());
    cJSON_AddNumberToObject(sip,  "port",              config_sip_port());
    cJSON_AddStringToObject(sip,  "user",              config_sip_user());
    // Never expose the password itself; surface only whether one is stored
    // so the manual form can pre-tick the "anonymous" (no-password) box.
    cJSON_AddBoolToObject  (sip,  "pass_set",          strlen(config_sip_pass()) > 0);
    cJSON_AddStringToObject(sip,  "internal_number",   config_sip_internal_number());
    cJSON_AddStringToObject(sip,  "transport",         config_sip_transport());
    cJSON_AddStringToObject(sip,  "auth_user",         config_sip_auth_user());
    cJSON_AddStringToObject(sip,  "outbound",          config_sip_outbound());
    cJSON_AddStringToObject(sip,  "realm",             config_sip_realm());
    cJSON_AddStringToObject(sip,  "srtp",              config_sip_srtp());
    cJSON_AddStringToObject(sip,  "stun",              config_stun_server());
    cJSON_AddStringToObject(sip,  "nat_mapping",       rtp_nat_mapping_str());
    // Local bind ports (to forward) + the public IP the registrar sees
    // us at — together these are what a UDP-behind-NAT setup needs.
    cJSON_AddNumberToObject(sip,  "local_port",        config_sip_local_port());
    cJSON_AddNumberToObject(sip,  "rtp_port",          config_rtp_port());
    cJSON_AddStringToObject(sip,  "public_ip",         sip_register_public_ip());

    cJSON *pb = cJSON_AddObjectToObject(root, "phoneblock");
    cJSON_AddStringToObject(pb,   "base_url",           config_phoneblock_base_url());
    cJSON_AddBoolToObject  (pb,   "token_set",          strlen(config_phoneblock_token()) > 0);
    cJSON_AddBoolToObject  (pb,   "token_ok",           api_token_is_valid());
    // The web sign-in (/register-start -> phoneblock.net -> /token-callback)
    // cannot complete through Home Assistant ingress: phoneblock.net refuses
    // most HA callback hosts, and ingress rejects the cross-site redirect
    // back (#619, #621). HA marks every ingress request with X-Ingress-Path;
    // there the UI hides the redirect sign-in (on Linux it offers the
    // device-code link below instead). Everywhere else it stays as it is.
    cJSON_AddBoolToObject  (pb,   "token_signin",
                            !web_http_has_header(req, "X-Ingress-Path"));
#ifndef ESP_PLATFORM
    // Under ingress the Linux service links the account with a code instead
    // (POST /api/link/start, #621): the token travels from phoneblock.net
    // to the service directly, nothing comes back through the browser.
    // Only set when true, so a direct request looks as before.
    if (web_http_has_header(req, "X-Ingress-Path")) {
        cJSON_AddBoolToObject(pb, "token_link", true);
    }
#endif
    // Latency of the last *successful* call; 0 before the first one. A
    // failed call no longer shows up here as its timeout (issue #551).
    cJSON_AddNumberToObject(pb,   "last_api_ms",        (double)(c.last_api_duration_us / 1000));
    // Whether the most recent timed call failed, and the most recent
    // failure (kept after a later success, so a flapping link stays
    // visible). The object is present only once a call has failed.
    cJSON_AddBoolToObject  (pb,   "last_api_failed",    c.last_api_failed);
    if (c.last_api_failure.kind != API_FAIL_NONE) {
        const api_failure_t *f = &c.last_api_failure;
        cJSON *fe = cJSON_AddObjectToObject(pb, "last_api_error");
        cJSON_AddStringToObject(fe, "kind",        api_fail_kind_name(f->kind));
        cJSON_AddStringToObject(fe, "detail",      f->detail);
        cJSON_AddNumberToObject(fe, "http_status", f->http_status);
        cJSON_AddStringToObject(fe, "call",        f->call);
        cJSON_AddNumberToObject(fe, "ago_s",       (double)((now_us - f->at_us) / 1000000));
        cJSON_AddNumberToObject(fe, "elapsed_ms",  (double)(f->elapsed_us / 1000));
    }
    // Phase breakdown of the last successful API call — investigation aid
    // for issue #329 (connect/request/wait/download split).
    cJSON *ph = cJSON_AddObjectToObject(pb, "last_api_phases");
    cJSON_AddBoolToObject  (ph, "valid",       c.last_api_phases.valid);
    cJSON_AddNumberToObject(ph, "total_ms",    (double)(c.last_api_phases.total_us    / 1000));
    cJSON_AddNumberToObject(ph, "connect_ms",  (double)(c.last_api_phases.connect_us  / 1000));
    cJSON_AddNumberToObject(ph, "request_ms",  (double)(c.last_api_phases.request_us  / 1000));
    cJSON_AddNumberToObject(ph, "wait_ms",     (double)(c.last_api_phases.wait_us     / 1000));
    cJSON_AddNumberToObject(ph, "download_ms", (double)(c.last_api_phases.download_us / 1000));
    cJSON_AddNumberToObject(pb,   "min_direct_votes",   config_min_direct_votes());
    cJSON_AddNumberToObject(pb,   "min_range_votes",    config_min_range_votes());

    cJSON *syn = cJSON_AddObjectToObject(root, "sync");
    sync_status_t ss;
    sync_snapshot(&ss);
    int64_t ago_s = ss.ever_ran ? (now_us - ss.last_at_us) / 1000000 : -1;
    cJSON_AddBoolToObject  (syn, "available", config_fritzbox_app_user()[0] != '\0');
    cJSON_AddBoolToObject  (syn, "enabled",   config_sync_enabled());
    cJSON_AddBoolToObject  (syn, "ever_ran",  ss.ever_ran);
    cJSON_AddBoolToObject  (syn, "last_ok",   ss.last_ok);
    cJSON_AddBoolToObject  (syn, "running",   ss.running);
    cJSON_AddNumberToObject(syn, "last_ago_s", (double)ago_s);
    cJSON_AddNumberToObject(syn, "last_pushed", ss.last_pushed);
    cJSON_AddNumberToObject(syn, "last_failed", ss.last_failed);
    cJSON_AddNumberToObject(syn, "last_skipped", ss.last_skipped);
    cJSON_AddStringToObject(syn, "last_error", ss.last_error);

    // Personal allowlist: whether the "add this caller to the
    // Fritz!Box address book" action can be offered at all, plus the
    // phonebook it would write to once one has been chosen.
    cJSON *pbk = cJSON_AddObjectToObject(root, "phonebook");
    cJSON_AddBoolToObject  (pbk, "available", config_fritzbox_app_user()[0] != '\0');
    cJSON_AddStringToObject(pbk, "id",        config_fritzbox_phonebook());
    cJSON_AddStringToObject(pbk, "name",      config_fritzbox_phonebook_name());

    cJSON *bl = cJSON_AddObjectToObject(root, "blocklist");
    blocklist_sync_status_t bs;
    blocklist_sync_snapshot(&bs);
    int64_t bl_ago_s = bs.ever_ran ? (now_us - bs.last_at_us) / 1000000 : -1;
    cJSON_AddBoolToObject  (bl, "have_community",  bs.have_community);
    cJSON_AddBoolToObject  (bl, "have_personal",   bs.have_personal);
    cJSON_AddNumberToObject(bl, "community_size",  bs.community_size);
    cJSON_AddNumberToObject(bl, "personal_size",   bs.personal_size);
    cJSON_AddBoolToObject  (bl, "ever_ran",        bs.ever_ran);
    cJSON_AddBoolToObject  (bl, "last_ok",         bs.last_ok);
    cJSON_AddBoolToObject  (bl, "running",         bs.running);
    cJSON_AddNumberToObject(bl, "last_ago_s",      (double)bl_ago_s);
    cJSON_AddStringToObject(bl, "last_error",      bs.last_error);
    cJSON_AddBoolToObject  (bl, "wildcards",       config_blocklist_wildcards());
    cJSON_AddBoolToObject  (bl, "enabled",         config_blocklist_enabled());

    // Caller-name filter (issue #502): the stored spec is echoed verbatim so
    // the form field round-trips exactly what the device will match on.
    cJSON *nm = cJSON_AddObjectToObject(root, "names");
    cJSON_AddStringToObject(nm, "patterns", config_spam_names());

    cJSON *ann = cJSON_AddObjectToObject(root, "announcement");
    cJSON_AddBoolToObject  (ann,  "custom",  announcement_is_custom());
    cJSON_AddStringToObject(ann,  "source",  announcement_source());
    cJSON_AddNumberToObject(ann,  "bytes",   (double)announcement_length());
    cJSON_AddNumberToObject(ann,  "max_bytes", (double)ANNOUNCEMENT_MAX_BYTES);
    cJSON_AddBoolToObject  (ann,  "accept_test_calls", config_accept_test_calls());

    // Answer-bot mode and the interactive dialog (#429): its tuning, and
    // whether a complete clip set is on the device and from which locale
    // (the set may come from a fallback locale, e.g. German for "fr").
    cJSON *dlg = cJSON_AddObjectToObject(root, "dialog");
    // ~1.3 KB of clip table: on the heap, not the httpd worker's stack.
    dialog_set_t *dset = malloc(sizeof(*dset));
    bool have_set = dset && dialog_store_load(dset);
    cJSON_AddStringToObject(dlg, "mode",                config_ab_mode());
    cJSON_AddNumberToObject(dlg, "vad_silence_db",      config_vad_silence_db());
    cJSON_AddNumberToObject(dlg, "vad_min_silence_ms",  config_vad_min_silence_ms());
    cJSON_AddNumberToObject(dlg, "comfort_noise_level", config_comfort_noise_db());
    cJSON_AddBoolToObject  (dlg, "clips_present",       have_set);
    cJSON_AddStringToObject(dlg, "clips_lang",          have_set ? dset->lang : "");
    // Whose set it is ("" for an own set), the chosen speaker ("" until the
    // sync picks one) and whether the set is the user's own (lang "own").
    cJSON_AddStringToObject(dlg, "clips_speaker",       have_set ? dset->speaker : "");
    cJSON_AddBoolToObject  (dlg, "own",
                            have_set && strcmp(dset->lang, DIALOG_OWN_LANG) == 0);
    cJSON_AddStringToObject(dlg, "speaker",             config_dialog_speaker());
    free(dset);
    // Where the set's download stands (i18n_sync.h): what the UI says while
    // there is no set — due, downloading, failed (and why), not published.
    {
        i18n_sync_status_t sync;
        i18n_sync_snapshot(&sync);
        cJSON_AddStringToObject(dlg, "sync", i18n_dialog_state_name(sync.dialog_state));
        cJSON_AddNumberToObject(dlg, "sync_bytes", (double)sync.dialog_bytes);
        cJSON_AddStringToObject(dlg, "sync_error", sync.dialog_error);
    }
    // Largest clip set file this device takes (the size tier it downloads,
    // STORAGE_DIALOG_PLAIN_FS_MAX on Linux). scripts/dev-i18n-push.sh picks
    // by it.
    cJSON_AddNumberToObject(dlg, "budget_bytes", (double)storage_dialog_budget());

    // Locale the device persisted (from the language selector's POST). The
    // UI reconciles its own localStorage pick against this so mail/audio
    // and the page agree; "de" when the user never changed it.
    cJSON_AddStringToObject(root, "ui_lang", config_ui_lang());
    // Whether ui_lang is a real user choice or still the "en" default — the web
    // UI auto-selects the browser's language on first contact when unset.
    cJSON_AddBoolToObject(root, "ui_lang_set", config_ui_lang_is_set());
    // State of the localized-asset download, so the page can tell "the pack is
    // on its way" from "it will not come". Without this the UI could only
    // observe that /api/i18n/ui keeps serving the English fallback, and had to
    // poll for a minute before concluding the pack was unavailable — on every
    // single page load. `sync_lang` is the locale the last pass handled: when
    // it differs from ui_lang, that pass says nothing about the current
    // locale (a switch was just requested), so the page keeps waiting.
    cJSON *i18 = cJSON_AddObjectToObject(root, "i18n");
    i18n_sync_status_t is;
    i18n_sync_snapshot(&is);
    cJSON_AddBoolToObject  (i18, "have_ui_pack", i18n_sync_have_ui_pack(config_ui_lang()));
    cJSON_AddBoolToObject  (i18, "running",      is.running);
    cJSON_AddBoolToObject  (i18, "ever_ran",     is.ever_ran);
    cJSON_AddBoolToObject  (i18, "last_ok",      is.last_ok);
    cJSON_AddStringToObject(i18, "sync_lang",    is.lang);
    cJSON_AddStringToObject(i18, "last_error",   is.last_error);
    // Country of the line, used to expand national caller IDs to E.164.
    // Shown (and changed) under Setup ▸ Region; "+49" until a token
    // activation adopts the account's value or the user picks one.
    cJSON_AddStringToObject(root, "dial_prefix", config_dial_prefix());
    cJSON_AddBoolToObject(root, "dial_prefix_set", config_dial_prefix_is_set());

    cJSON *cnt = cJSON_AddObjectToObject(root, "counters");
    cJSON_AddNumberToObject(cnt,  "total",        c.total_calls);
    cJSON_AddNumberToObject(cnt,  "spam_blocked", c.spam_blocked);
    cJSON_AddNumberToObject(cnt,  "legitimate",   c.legitimate);
    cJSON_AddNumberToObject(cnt,  "errors",       c.errors);

    cJSON *cl = cJSON_AddObjectToObject(root, "calls");
    cJSON_AddBoolToObject  (cl,   "log_known",   config_log_known_calls());

    cJSON *lg = cJSON_AddObjectToObject(root, "log");
    cJSON_AddBoolToObject  (lg,   "capture_info", config_log_info());

    cJSON *led = cJSON_AddObjectToObject(root, "led");
    cJSON_AddBoolToObject  (led,  "quiet",        config_led_quiet());

#if CONFIG_IDF_TARGET_ESP32S3
    // Only the S3 build has a display; its absence tells the UI to hide
    // the orientation option.
    cJSON *dsp = cJSON_AddObjectToObject(root, "display");
    cJSON_AddBoolToObject  (dsp,  "flipped",      config_lcd_flipped());
#endif

    cJSON *au = cJSON_AddObjectToObject(root, "auth");
    cJSON_AddBoolToObject  (au,   "enabled",     config_auth_enabled());
    cJSON_AddBoolToObject  (au,   "logged_in",   web_auth_is_logged_in(req));
    cJSON_AddStringToObject(au,   "user",        config_auth_user());

    cJSON *tm = cJSON_AddObjectToObject(root, "time");
    bool clock_ok = time_sync_valid();
    cJSON_AddBoolToObject  (tm,   "valid",   clock_ok);
    cJSON_AddStringToObject(tm,   "tz",      config_timezone());
    if (clock_ok) {
        time_t tnow = time(NULL);
        struct tm lt;
        char iso[32];
        time_sync_tz_lock();
        localtime_r(&tnow, &lt);
        strftime(iso, sizeof(iso), "%Y-%m-%dT%H:%M:%S", &lt);
        time_sync_tz_unlock();
        cJSON_AddNumberToObject(tm, "epoch_s", (double)tnow);
        cJSON_AddStringToObject(tm, "local",   iso);
    } else {
        cJSON_AddNumberToObject(tm, "epoch_s", 0);
        cJSON_AddStringToObject(tm, "local",   "");
    }

    cJSON *fw = cJSON_AddObjectToObject(root, "firmware");
    cJSON_AddBoolToObject  (fw,   "auto_update",  config_auto_update_enabled());
    cJSON_AddStringToObject(fw,   "channel",      config_ota_channel());
    cJSON_AddBoolToObject  (fw,   "crash_report", config_crash_report_enabled());

    cJSON *ml = cJSON_AddObjectToObject(root, "mail");
    cJSON_AddStringToObject(ml,   "host",     config_smtp_host());
    cJSON_AddNumberToObject(ml,   "port",     config_smtp_port());
    cJSON_AddStringToObject(ml,   "security", config_smtp_security());
    cJSON_AddStringToObject(ml,   "user",     config_smtp_user());
    cJSON_AddStringToObject(ml,   "from",     config_smtp_from());
    cJSON_AddStringToObject(ml,   "to",       config_smtp_to());
    // Never echo the password; the UI only needs to know whether one is
    // stored (to render a "leave blank to keep" placeholder).
    cJSON_AddBoolToObject  (ml,   "pass_set", config_smtp_pass()[0] != '\0');
    cJSON_AddBoolToObject  (ml,   "on_error", config_mail_on_error());
    cJSON_AddBoolToObject  (ml,   "on_spam",  config_mail_on_spam());
    cJSON_AddBoolToObject  (ml,   "spam_only", config_mail_spam_only());
    cJSON_AddBoolToObject  (ml,   "on_update", config_mail_on_update());

    cJSON *wh = cJSON_AddObjectToObject(root, "webhook");
    cJSON_AddStringToObject(wh,   "url",      config_webhook_url());

    add_system_load(root);

    send_json(req, root);
    return WEB_HTTP_OK;
}

cJSON *web_call_json(const stats_call_t *call, int64_t now_us,
                     int64_t now_epoch)
{
    cJSON *o = cJSON_CreateObject();
    int64_t age_s = (now_us - call->at_us) / 1000000;
    cJSON_AddNumberToObject(o, "age_s",       (double)age_s);
    cJSON_AddNumberToObject(o, "epoch_s",     now_epoch ? (double)(now_epoch - age_s) : 0);
    cJSON_AddStringToObject(o, "number",      call->number);
    cJSON_AddStringToObject(o, "display",     call->display);
    cJSON_AddStringToObject(o, "label",       call->label);
    cJSON_AddStringToObject(o, "location",    call->location);
    cJSON_AddStringToObject(o, "assessment",   assessment_string(call->assessment));
    cJSON_AddNumberToObject(o, "direct_votes", call->direct_votes);
    cJSON_AddNumberToObject(o, "range_votes",  call->range_votes);
    cJSON_AddBoolToObject  (o, "wildcard",     call->wildcard);
    cJSON_AddNumberToObject(o, "duration_s",   call->duration_s);
    return o;
}

static web_http_error_t handle_calls(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    stats_call_t calls[STATS_MAX_CALLS];
    int n = stats_snapshot_calls(calls, STATS_MAX_CALLS);
    int64_t now_us = pb_monotonic_us();
    // Absolute time for machine clients (Home Assistant, ioBroker, #644),
    // derived from the age instead of stored per call, so the call buffer
    // stays as it is. 0 while the clock is not set, as in /api/status.
    int64_t now_epoch = time_sync_now_epoch();

    cJSON *root = cJSON_CreateObject();
    cJSON *arr  = cJSON_AddArrayToObject(root, "calls");
    for (int i = 0; i < n; i++) {
        cJSON_AddItemToArray(arr, web_call_json(&calls[i], now_us, now_epoch));
    }
    send_json(req, root);
    return WEB_HTTP_OK;
}

// --- URL-encoded form parsing --------------------------------------

static int hex_digit(char c)
{
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

// Decode src (percent-encoded, '+' = space) into dst; NUL-terminates.
void url_decode(const char *src, int src_len, char *dst, size_t cap)
{
    size_t o = 0;
    for (int i = 0; i < src_len && o + 1 < cap; i++) {
        char c = src[i];
        if (c == '+') {
            dst[o++] = ' ';
        } else if (c == '%' && i + 2 < src_len) {
            int hi = hex_digit(src[i + 1]);
            int lo = hex_digit(src[i + 2]);
            if (hi >= 0 && lo >= 0) {
                dst[o++] = (char)((hi << 4) | lo);
                i += 2;
            } else {
                dst[o++] = c;
            }
        } else {
            dst[o++] = c;
        }
    }
    dst[o] = '\0';
}

// Look up `key` in a URL-encoded body, copy the (decoded) value into
// `out`. Returns true if the key was present (even if empty value).
static bool form_get(const char *body, const char *key, char *out, size_t cap)
{
    size_t keylen = strlen(key);
    const char *p = body;
    while (*p) {
        if (strncmp(p, key, keylen) == 0 && p[keylen] == '=') {
            const char *v = p + keylen + 1;
            const char *end = v;
            while (*end && *end != '&') end++;
            url_decode(v, (int)(end - v), out, cap);
            return true;
        }
        while (*p && *p != '&') p++;
        if (*p == '&') p++;
    }
    out[0] = '\0';
    return false;
}

// Fingerprint of every setting the SIP registration reads (FNV-1a, 64 bit).
// handle_config_post() compares it before and after a save, so a toggle
// unrelated to SIP (LED, display, mail, ...) does not drop a working
// registration. Hashing instead of copying keeps the httpd stack small.
static uint64_t sip_config_digest(void)
{
    uint64_t h = 0xcbf29ce484222325ULL;
    const char *strs[] = {
        config_sip_host(), config_sip_user(), config_sip_pass(),
        config_sip_internal_number(), config_sip_transport(),
        config_sip_auth_user(), config_sip_outbound(), config_sip_realm(),
        config_sip_srtp(), config_stun_server(),
    };
    for (size_t i = 0; i < sizeof(strs) / sizeof(strs[0]); i++) {
        // Include the terminator, so field boundaries count ("ab","" != "a","b").
        for (const char *c = strs[i] ? strs[i] : ""; ; c++) {
            h = (h ^ (uint8_t)*c) * 0x100000001b3ULL;
            if (!*c) break;
        }
    }
    const int ints[] = {
        config_sip_port(), config_sip_expires(),
        config_sip_local_port(), config_rtp_port(),
    };
    for (size_t i = 0; i < sizeof(ints) / sizeof(ints[0]); i++) {
        for (int b = 0; b < 4; b++) {
            h = (h ^ (uint8_t)(ints[i] >> (8 * b))) * 0x100000001b3ULL;
        }
    }
    return h;
}

static void send_fail_code(web_http_request_t *req, const char *status,
                           const char *code, const char *message);

static web_http_error_t handle_config_post(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    int total = web_http_body_length(req);
    if (total <= 0 || total > 2048) {
        web_http_send_error(req, WEB_HTTP_STATUS_BAD_REQUEST, "body missing or too large");
        return WEB_HTTP_OK;
    }
    char *body = malloc(total + 1);
    if (!body) {
        web_http_send_error(req, WEB_HTTP_STATUS_INTERNAL_SERVER_ERROR, "oom");
        return WEB_HTTP_OK;
    }
    int got = 0;
    while (got < total) {
        int n = web_http_read_body(req, body + got, total - got);
        if (n <= 0) {
            free(body);
            web_http_send_error(req, WEB_HTTP_STATUS_INTERNAL_SERVER_ERROR, "recv");
            return WEB_HTTP_OK;
        }
        got += n;
    }
    body[got] = '\0';

    // Call webhook URL, with one byte of slack like the lists below. On the
    // heap: this handler runs on the httpd stack, which has little to spare
    // on the ESP32-C3. Freed once config_update() has copied it.
    char *webhook_url = calloc(1, CONFIG_WEBHOOK_URL_CAP + 1);
    if (!webhook_url) {
        free(body);
        web_http_send_error(req, WEB_HTTP_STATUS_INTERNAL_SERVER_ERROR, "oom");
        return WEB_HTTP_OK;
    }

    char sip_host[64], sip_user[32], sip_pass[64];
    char pb_url[128],  pb_token[64];
    char sip_port_s[8] = "", sip_exp_s[8] = "";
    char sip_lport_s[8] = "", rtp_port_s[8] = "";
    char min_direct_s[8] = "", min_range_s[8] = "";
    char sip_transp[8]    = "";
    char sip_authuser[32] = "";
    char sip_outbound[80] = "";
    char sip_realm[64]    = "";
    char sip_srtp[16]     = "";
    char sip_stun[64]     = "";
    char sip_anon_s[4]    = "";
    char sync_en_s[4]     = "";
    char log_known_s[4]   = "";
    char log_info_s[4]    = "";
    char led_quiet_s[4]   = "";
    char lcd_flip_s[4]    = "";
    char auto_update_s[4] = "";
    char channel_s[8]     = "";
    char crash_rep_s[4]   = "";
    char test_calls_s[4]  = "";
    char bl_wild_s[4]     = "";
    char bl_enabled_s[4]  = "";
    char smtp_host[64]    = "";
    char smtp_port_s[8]   = "";
    char smtp_sec[10]     = "";
    char smtp_user[64]    = "";
    char smtp_pass[64]    = "";
    char smtp_from[64]    = "";
    // ';'-separated recipient list. One byte of slack over what config can
    // store, so an over-long value is detectable below instead of arriving
    // pre-clipped by form_get.
    char smtp_to[MAIL_RCPT_SPEC_CAP + 1] = "";
    // '|'-separated caller-name patterns, with the same one byte of slack so an
    // over-long spec is detectable below instead of arriving pre-clipped.
    char spam_names[NAME_FILTER_SPEC_CAP + 1] = "";
    char mail_err_s[4]    = "";
    char mail_spam_s[4]   = "";
    char mail_only_s[4]   = "";
    char mail_upd_s[4]    = "";
    char tz_s[64]         = "";
    char ui_lang_s[12]    = "";
    char dial_prefix_s[8] = "";
    char fb_pb_id_s[8]    = "";
    char fb_pb_name_s[64] = "";
    char ab_mode_s[16]    = "";
    char vad_db_s[8]      = "";
    char vad_ms_s[8]      = "";
    char cn_level_s[8]    = "";
    // Room for one character more than an id may have, so an over-long one
    // is refused instead of arriving clipped.
    char dlg_spk_s[DIALOG_SPEAKER_CAP + 8] = "";

    bool have_sip_host  = form_get(body, "sip_host",  sip_host,  sizeof(sip_host));
    bool have_sip_user  = form_get(body, "sip_user",  sip_user,  sizeof(sip_user));
    bool have_sip_pass  = form_get(body, "sip_pass",  sip_pass,  sizeof(sip_pass));
    bool have_sip_port  = form_get(body, "sip_port",  sip_port_s, sizeof(sip_port_s));
    bool have_sip_lport = form_get(body, "sip_local_port", sip_lport_s, sizeof(sip_lport_s));
    bool have_rtp_port  = form_get(body, "rtp_port",  rtp_port_s, sizeof(rtp_port_s));
    bool have_sip_exp   = form_get(body, "sip_expires", sip_exp_s, sizeof(sip_exp_s));
    bool have_sip_trans = form_get(body, "sip_transport", sip_transp, sizeof(sip_transp));
    bool have_sip_auth  = form_get(body, "sip_auth_user", sip_authuser, sizeof(sip_authuser));
    bool have_sip_out   = form_get(body, "sip_outbound",  sip_outbound, sizeof(sip_outbound));
    bool have_sip_realm = form_get(body, "sip_realm",     sip_realm,    sizeof(sip_realm));
    bool have_sip_srtp  = form_get(body, "sip_srtp",      sip_srtp,     sizeof(sip_srtp));
    bool have_sip_stun  = form_get(body, "sip_stun",      sip_stun,     sizeof(sip_stun));
    // Present only when the manual form's "anonymous" box is ticked. Its
    // presence (not its value) is the signal to clear a stored password.
    bool have_sip_anon  = form_get(body, "sip_anon",      sip_anon_s,   sizeof(sip_anon_s));
    bool have_sync_en   = form_get(body, "sync_enabled",  sync_en_s,    sizeof(sync_en_s));
    bool have_log_known = form_get(body, "log_known_calls", log_known_s, sizeof(log_known_s));
    bool have_log_info  = form_get(body, "log_info", log_info_s, sizeof(log_info_s));
    bool have_led_quiet = form_get(body, "led_quiet", led_quiet_s, sizeof(led_quiet_s));
    bool have_lcd_flip  = form_get(body, "lcd_flip",  lcd_flip_s,  sizeof(lcd_flip_s));
    bool have_auto_upd  = form_get(body, "auto_update",   auto_update_s, sizeof(auto_update_s));
    bool have_channel   = form_get(body, "channel",       channel_s,     sizeof(channel_s));
    bool have_crash_rep = form_get(body, "crash_report",  crash_rep_s,   sizeof(crash_rep_s));
    bool have_test_call = form_get(body, "accept_test_calls", test_calls_s, sizeof(test_calls_s));
    bool have_bl_wild   = form_get(body, "blocklist_wildcards", bl_wild_s, sizeof(bl_wild_s));
    bool have_bl_en     = form_get(body, "blocklist_enabled", bl_enabled_s, sizeof(bl_enabled_s));
    bool have_spam_names = form_get(body, "spam_names", spam_names, sizeof(spam_names));
    bool have_pb_url     = form_get(body, "pb_url",    pb_url,    sizeof(pb_url));
    bool have_pb_token   = form_get(body, "pb_token",  pb_token,  sizeof(pb_token));
    bool have_min_direct = form_get(body, "min_direct_votes", min_direct_s, sizeof(min_direct_s));
    bool have_min_range  = form_get(body, "min_range_votes",  min_range_s,  sizeof(min_range_s));
    bool have_smtp_host  = form_get(body, "smtp_host",     smtp_host,   sizeof(smtp_host));
    bool have_smtp_port  = form_get(body, "smtp_port",     smtp_port_s, sizeof(smtp_port_s));
    bool have_smtp_sec   = form_get(body, "smtp_security", smtp_sec,    sizeof(smtp_sec));
    bool have_smtp_user  = form_get(body, "smtp_user",     smtp_user,   sizeof(smtp_user));
    bool have_smtp_pass  = form_get(body, "smtp_pass",     smtp_pass,   sizeof(smtp_pass));
    bool have_smtp_from  = form_get(body, "smtp_from",     smtp_from,   sizeof(smtp_from));
    bool have_smtp_to    = form_get(body, "smtp_to",       smtp_to,     sizeof(smtp_to));
    bool have_webhook    = form_get(body, "webhook_url",   webhook_url, CONFIG_WEBHOOK_URL_CAP + 1);
    bool have_mail_err   = form_get(body, "mail_on_error", mail_err_s,  sizeof(mail_err_s));
    bool have_mail_spam  = form_get(body, "mail_on_spam",  mail_spam_s, sizeof(mail_spam_s));
    bool have_mail_only  = form_get(body, "mail_spam_only", mail_only_s, sizeof(mail_only_s));
    bool have_mail_upd   = form_get(body, "mail_on_update", mail_upd_s, sizeof(mail_upd_s));
    bool have_tz         = form_get(body, "timezone",      tz_s,        sizeof(tz_s));
    bool have_ui_lang    = form_get(body, "ui_lang",       ui_lang_s,   sizeof(ui_lang_s));
    bool have_dial_pfx   = form_get(body, "dial_prefix",   dial_prefix_s, sizeof(dial_prefix_s));
    bool have_fb_pb      = form_get(body, "fb_phonebook", fb_pb_id_s, sizeof(fb_pb_id_s));
    bool have_fb_pb_name = form_get(body, "fb_phonebook_name", fb_pb_name_s, sizeof(fb_pb_name_s));
    bool have_ab_mode    = form_get(body, "ab_mode",            ab_mode_s,  sizeof(ab_mode_s));
    bool have_vad_db     = form_get(body, "vad_silence_db",     vad_db_s,   sizeof(vad_db_s));
    bool have_vad_ms     = form_get(body, "vad_min_silence_ms", vad_ms_s,   sizeof(vad_ms_s));
    bool have_cn_level   = form_get(body, "comfort_noise_level", cn_level_s, sizeof(cn_level_s));
    bool have_dlg_spk    = form_get(body, "dialog_speaker",     dlg_spk_s,  sizeof(dlg_spk_s));
    free(body);

    // Phonebook selection for new contacts: only a decimal ID is stored, so
    // a malformed value can't reach the SOAP argument later. An empty field
    // clears the selection (back to "ask on the next add").
    if (have_fb_pb) {
        for (const char *p = fb_pb_id_s; *p; p++) {
            if (*p < '0' || *p > '9') { have_fb_pb = false; break; }
        }
    }

    // POSIX TZ string (e.g. "CET-1CEST,M3.5.0,M10.5.0/3"): accept only a
    // sane length of printable ASCII so a malformed POST can never feed
    // garbage into setenv/tzset. An empty / rejected value leaves the
    // stored timezone untouched. The browser derives this from its own
    // DST rules — newlib has no IANA database to map "Europe/Berlin".
    const char *timezone = NULL;
    if (have_tz) {
        size_t n = strlen(tz_s);
        bool ok = n >= 2 && n < sizeof(tz_s);
        for (size_t i = 0; ok && i < n; i++) {
            unsigned char ch = (unsigned char)tz_s[i];
            if (ch < 0x20 || ch >= 0x7f) ok = false;
        }
        if (ok) timezone = tz_s;
    }

    const char *new_host = have_sip_host && sip_host[0] ? sip_host : NULL;
    // A changed registrar (provider / manual edit moving to a different
    // box) invalidates the Fritz!Box-supplied extension number — blank
    // it out explicitly so the dashboard does not keep showing it.
    const char *clear_int_num = NULL;
    if (new_host && strcmp(new_host, config_sip_host()) != 0) {
        clear_int_num = "";
    }

    // OTA channel: only the two known channels are accepted. An
    // unrecognised value is ignored (left unchanged) rather than
    // persisted, so a malformed POST can never write a string the
    // manifest-URL builder would have to defend against.
    const char *channel = NULL;
    if (have_channel && (strcmp(channel_s, "stable") == 0 ||
                         strcmp(channel_s, "beta")   == 0)) {
        channel = channel_s;
    }

    // SMTP security: accept only the two known modes; anything else is
    // ignored (left unchanged) rather than persisted, mirroring the OTA
    // channel guard. config_smtp_security() clamps on read too.
    const char *smtp_security = NULL;
    if (have_smtp_sec && (strcmp(smtp_sec, "tls") == 0 ||
                          strcmp(smtp_sec, "starttls") == 0)) {
        smtp_security = smtp_sec;
    }

    // Status-mail recipients: a ';'-separated list (see mail_rcpt.h). The UI
    // validates it, but the API is open, so re-check here and ignore (leave
    // unchanged) a spec that would not survive being stored: one that is too
    // long for the NVS field, or whose entries do not all fit. Storing a
    // clipped address is the failure worth preventing — it would silently
    // mail the household's status report to whoever owns the shortened
    // address. An empty value is accepted: that is how the UI clears the
    // recipient and turns the status mail off.
    const char *smtp_to_val = NULL;
    if (have_smtp_to) {
        mail_rcpt_list_t rcpts;
        mail_rcpt_parse(smtp_to, &rcpts);
        if (strlen(smtp_to) < MAIL_RCPT_SPEC_CAP && !rcpts.dropped)
            smtp_to_val = smtp_to;
        else
            pb_log_warn(TAG, "smtp_to rejected: too long or too many recipients");
    }

    // Caller-name patterns: reject a spec that would not survive storage in
    // full, for the same reason as the recipient list — a clipped pattern is a
    // *shorter* pattern and would block more callers than the user wrote. An
    // empty value is accepted: that is how the UI turns the name filter off.
    const char *spam_names_val = NULL;
    if (have_spam_names) {
        name_filter_t nf;
        name_filter_parse(spam_names, &nf);
        if (strlen(spam_names) < NAME_FILTER_SPEC_CAP && !nf.dropped)
            spam_names_val = spam_names;
        else
            pb_log_warn(TAG, "spam_names rejected: pattern too long or too many patterns");
    }

    // Call webhook: only a plain-http URL that fits in full (a clipped URL
    // would post the household's calls somewhere else). Empty turns it off.
    // Whether the host is in the home network needs DNS; webhook.c checks
    // that on every delivery.
    const char *webhook_val = NULL;
    if (have_webhook) {
        if (webhook_url_valid(webhook_url))
            webhook_val = webhook_url;
        else
            pb_log_warn(TAG, "webhook_url rejected: not a plain http URL or too long");
    }

    // UI/content locale: accept only a well-formed code (validated the same
    // way the getter clamps on read), so a malformed POST can never write a
    // value that the asset-URL / file-name builder would have to
    // defend against. An unrecognised value is ignored (left unchanged).
    const char *ui_lang = (have_ui_lang && config_lang_code_valid(ui_lang_s))
                          ? ui_lang_s : NULL;

    // Country of the line — same guard: the value is prepended to a caller
    // ID that becomes an API URL and a SHA-1 lookup key, so only the "+NN"
    // shape the getter accepts is written; anything else is ignored.
    const char *dial_prefix = (have_dial_pfx && config_dial_prefix_valid(dial_prefix_s))
                              ? dial_prefix_s : NULL;
    if (have_dial_pfx && dial_prefix == NULL) {
        pb_log_warn(TAG, "dial_prefix rejected: expected \"+NN\"");
    }

    // Answer-bot mode and dialog settings (#429): only the two known modes,
    // a well-formed speaker id and values inside the documented ranges. Unlike
    // the older fields above (ignored with a WARN, the rest saved), an invalid
    // one refuses the WHOLE request — 400 naming the field(s), nothing
    // applied — so a caller never reads "Saved." for a value that was not.
    // An empty numeric field means "unchanged".
    char rejected[160] = "";
    strbuf_t rej = sb_init(rejected, sizeof(rejected));
    const char *ab_mode = NULL;
    if (have_ab_mode) {
        if (config_ab_mode_valid(ab_mode_s)) ab_mode = ab_mode_s;
        else sb_appendf(&rej, "%sab_mode (announcement or dialog)", rej.len ? ", " : "");
    }
    const char *dialog_speaker = NULL;
    if (have_dlg_spk) {
        // The user's choice; "" asks the next sync to pick again.
        if (config_dialog_speaker_valid(dlg_spk_s)) dialog_speaker = dlg_spk_s;
        else sb_appendf(&rej, "%sdialog_speaker ([a-z][a-z0-9-]{0,15} or empty)",
                        rej.len ? ", " : "");
    }
    int vad_db = 0, vad_ms = 0, cn_level = 0;
    if (have_vad_db && vad_db_s[0]) {
        int v = atoi(vad_db_s);
        if (config_vad_silence_db_valid(v)) vad_db = v;
        else sb_appendf(&rej, "%svad_silence_db (%d..%d)", rej.len ? ", " : "",
                        CONFIG_VAD_SILENCE_DB_MIN, CONFIG_VAD_SILENCE_DB_MAX);
    }
    if (have_vad_ms && vad_ms_s[0]) {
        int v = atoi(vad_ms_s);
        if (config_vad_min_silence_ms_valid(v)) vad_ms = v;
        else sb_appendf(&rej, "%svad_min_silence_ms (%d..%d)", rej.len ? ", " : "",
                        CONFIG_VAD_MIN_SILENCE_MS_MIN, CONFIG_VAD_MIN_SILENCE_MS_MAX);
    }
    if (have_cn_level && cn_level_s[0]) {
        int v = atoi(cn_level_s);
        if (config_comfort_noise_db_valid(v)) cn_level = v;
        else sb_appendf(&rej, "%scomfort_noise_level (%d..%d)", rej.len ? ", " : "",
                        CONFIG_COMFORT_NOISE_DB_MIN, CONFIG_COMFORT_NOISE_DB_MAX);
    }
    if (rej.len) {
        // Clamped (sb.truncated) is fine: the first field names the problem.
        char msg[200];
        snprintf(msg, sizeof(msg), "Invalid: %s. Nothing was saved.", rejected);
        pb_log_warn(TAG, "settings refused: %s", msg);
        free(webhook_url);
        send_fail_code(req, "400 Bad Request", "invalid", msg);
        return WEB_HTTP_OK;
    }

    config_update_t u = {
        .sip_host   = new_host,
        // Port present in the form writes through; absent (a partial
        // update such as a settings toggle) leaves the stored value
        // alone. An empty *or* "0" field both mean "auto" (0 → DNS-SRV /
        // transport default): this matches the prefill, which renders a
        // stored 0 as an empty field, and lets a provider switch clear a
        // stale pinned port back to auto.
        .has_sip_port = have_sip_port,
        .sip_port     = have_sip_port && sip_port_s[0] ? atoi(sip_port_s) : 0,
        // Local bind ports. Present-but-empty field → 0 → reset to the
        // built-in default (15060 / 16000); absent → leave unchanged.
        .has_sip_local_port = have_sip_lport,
        .sip_local_port     = have_sip_lport && sip_lport_s[0] ? atoi(sip_lport_s) : 0,
        .has_rtp_port       = have_rtp_port,
        .rtp_port           = have_rtp_port && rtp_port_s[0] ? atoi(rtp_port_s) : 0,
        .sip_user   = have_sip_user && sip_user[0]  ? sip_user  : NULL,
        // Password resolution, in order:
        //   - "anonymous" box ticked → "" : explicitly clear the stored
        //     password (the only way to reach a passwordless account like
        //     anonymous@t-online.de once a password was ever saved).
        //   - non-empty field         → use it.
        //   - empty field, no box     → NULL : keep existing, so the user
        //     need not retype it when editing other fields.
        .sip_pass   = have_sip_anon                 ? ""
                    : have_sip_pass && sip_pass[0]  ? sip_pass
                    : NULL,
        .sip_expires = have_sip_exp && sip_exp_s[0] ? atoi(sip_exp_s) : 0,
        .sip_internal_number = clear_int_num,
        // Extended SIP parameters: explicit empty string clears, missing
        // field (e.g. manual form without expert row visible) leaves
        // current value untouched.
        .sip_transport = have_sip_trans ? sip_transp   : NULL,
        .sip_auth_user = have_sip_auth  ? sip_authuser : NULL,
        .sip_outbound  = have_sip_out   ? sip_outbound : NULL,
        .sip_realm     = have_sip_realm ? sip_realm    : NULL,
        .sip_srtp      = have_sip_srtp  ? sip_srtp     : NULL,
        .sip_stun      = have_sip_stun  ? sip_stun     : NULL,
        .sync_enabled    = have_sync_en   ? sync_en_s    : NULL,
        .log_known_calls = have_log_known ? log_known_s  : NULL,
        .log_info        = have_log_info  ? log_info_s   : NULL,
        .led_quiet       = have_led_quiet ? led_quiet_s  : NULL,
        .lcd_flip        = have_lcd_flip  ? lcd_flip_s   : NULL,
        .auto_update     = have_auto_upd  ? auto_update_s : NULL,
        .ota_channel     = channel,
        .crash_report    = have_crash_rep ? crash_rep_s   : NULL,
        .accept_test_calls = have_test_call ? test_calls_s : NULL,
        .blocklist_wildcards = have_bl_wild ? bl_wild_s : NULL,
        .blocklist_enabled   = have_bl_en   ? bl_enabled_s : NULL,
        .spam_names          = spam_names_val,
        .fritzbox_phonebook      = have_fb_pb      ? fb_pb_id_s   : NULL,
        .fritzbox_phonebook_name = have_fb_pb_name ? fb_pb_name_s : NULL,
        .phoneblock_base_url = have_pb_url   && pb_url[0]   ? pb_url   : NULL,
        .phoneblock_token    = have_pb_token && pb_token[0] ? pb_token : NULL,
        .min_direct_votes = have_min_direct && min_direct_s[0] ? atoi(min_direct_s) : 0,
        // 0 is a meaningful value here (= disable range), so use the
        // explicit-flag setter pattern. Empty form field = leave alone.
        .has_min_range_votes = have_min_range && min_range_s[0],
        .min_range_votes     = have_min_range && min_range_s[0] ? atoi(min_range_s) : 0,
        // SMTP. Text fields write through when present (a present-but-empty
        // field clears the value — how the user disables mail by emptying
        // the recipient/host). Port: present-but-empty → 0 = "auto" (derive
        // 465/587 from the security mode). Password follows the SIP rule:
        // empty keeps the stored one, so editing other fields doesn't force
        // a re-type.
        .smtp_host     = have_smtp_host ? smtp_host : NULL,
        .has_smtp_port = have_smtp_port,
        .smtp_port     = have_smtp_port && smtp_port_s[0] ? atoi(smtp_port_s) : 0,
        .smtp_security = smtp_security,
        .smtp_user     = have_smtp_user ? smtp_user : NULL,
        .smtp_pass     = have_smtp_pass && smtp_pass[0] ? smtp_pass : NULL,
        .smtp_from     = have_smtp_from ? smtp_from : NULL,
        .smtp_to       = smtp_to_val,
        .mail_on_error = have_mail_err  ? mail_err_s  : NULL,
        .mail_on_spam  = have_mail_spam ? mail_spam_s : NULL,
        .mail_spam_only = have_mail_only ? mail_only_s : NULL,
        .mail_on_update = have_mail_upd ? mail_upd_s  : NULL,
        .webhook_url   = webhook_val,
        .timezone      = timezone,
        .ui_lang       = ui_lang,
        .dial_prefix   = dial_prefix,
        .ab_mode            = ab_mode,
        .vad_silence_db     = vad_db,
        .vad_min_silence_ms = vad_ms,
        .comfort_noise_db   = cn_level,
        .dialog_speaker     = dialog_speaker,
    };

    // Snapshot the PhoneBlock token before the update so we can detect
    // a fresh activation and kick off the blocklist sync immediately —
    // otherwise the user would wait up to 24 h for the local cache.
    char old_token[64];
    strncpy(old_token, config_phoneblock_token(), sizeof(old_token) - 1);
    old_token[sizeof(old_token) - 1] = '\0';

    // Snapshot the locale too: a change means the device needs a different
    // announcement (and mail pack) from the CDN.
    char old_lang[12];
    strncpy(old_lang, config_ui_lang(), sizeof(old_lang) - 1);
    old_lang[sizeof(old_lang) - 1] = '\0';

    uint64_t old_sip = sip_config_digest();
    bool old_dialog = config_ab_dialog();
    char old_speaker[DIALOG_SPEAKER_CAP];
    snprintf(old_speaker, sizeof(old_speaker), "%s", config_dialog_speaker());

    int err = config_update(&u);
    free(webhook_url);
    if (err != PB_KV_OK) {
        web_http_send_error(req, WEB_HTTP_STATUS_INTERNAL_SERVER_ERROR, "nvs write failed");
        return WEB_HTTP_OK;
    }

    // Apply a changed timezone to the running clock immediately so daily
    // jobs and the dashboard reflect it without a reboot.
    if (timezone) time_sync_set_timezone(config_timezone());

#if CONFIG_IDF_TARGET_ESP32S3
    // Turn the display right away, so the user sees the effect of the
    // checkbox while still holding the stick.
    if (have_lcd_flip) {
        display_set_flipped(config_lcd_flipped());
    }
#endif

    if (strcmp(old_token, config_phoneblock_token()) != 0
            && config_phoneblock_token()[0] != '\0') {
        // Token just arrived (or was rotated). Fire a sync; the trigger
        // is a no-op when one is already in flight, so this never
        // disrupts an ongoing run.
        blocklist_sync_trigger_now();
    }

    // Switched to dialog mode (#429): the own recording goes now — dialog
    // mode and an own recording do not coexist, the flash holds the clip set
    // in their place. The web UI asked the user before posting the switch.
    if (!old_dialog && config_ab_dialog() && announcement_is_custom()) {
        pb_log_info(TAG, "dialog mode on — own announcement deleted");
        announcement_reset();
    }

    // Choosing a CDN speaker (#429) replaces an own dialog set: only one set
    // fits on flash. The web UI asked the user before posting the choice.
    bool own_dropped = false;
    if (dialog_speaker && config_ab_dialog() && dialog_store_has_own()) {
        if (dialog_store_upload_active()) {
            pb_log_warn(TAG, "own dialog set kept: its upload is still running");
        } else {
            dialog_store_drop_index();
            dialog_store_remove_others(NULL);   // the speaker list stays
            own_dropped = true;
            pb_log_info(TAG, "speaker '%s' chosen — own dialog set deleted",
                        config_dialog_speaker()[0] ? config_dialog_speaker() : "(none)");
        }
    }

    // Locale changed: pull the matching announcement / mail pack now so the
    // user doesn't wait for the daily i18n slot. No-op if one is in flight.
    // A switch of the answer-bot mode fetches (or removes) the dialog clips,
    // another speaker its set.
    if (strcmp(old_lang, config_ui_lang()) != 0 || old_dialog != config_ab_dialog() ||
        strcmp(old_speaker, config_dialog_speaker()) != 0 || own_dropped) {
        i18n_sync_trigger_now();
    }

    // Re-register when a SIP setting changed, or when the SIP form itself
    // was submitted (saving it unchanged is how a user retries a failed
    // registration). The same endpoint stores unrelated toggles (LED,
    // display, mail, ...); re-registering on those dropped a working
    // registration for nothing. Manual edit / provider preset — no
    // Fritz!Box-internal extension race to absorb, no settle delay needed.
    bool sip_changed = have_sip_host || sip_config_digest() != old_sip;
    if (sip_changed) {
        sip_register_request_reload(false);
    }

    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject(root, "ok", true);
    cJSON_AddStringToObject(root, "message", sip_changed ? "Saved, re-registering." : "Saved.");
    send_json(req, root);
    return WEB_HTTP_OK;
}

// Receive up to `cap` bytes from the request body into `buf`.
// Returns bytes read, or a negative error code. NUL-terminates.
static int recv_body(web_http_request_t *req, char *buf, int cap)
{
    int total = web_http_body_length(req);
    if (total <= 0 || total > cap - 1) return -1;
    int got = 0;
    while (got < total) {
        int n = web_http_read_body(req, buf + got, total - got);
        if (n <= 0) return -1;
        got += n;
    }
    buf[got] = '\0';
    return got;
}

static void send_fail(web_http_request_t *req, const char *message)
{
    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject(root, "ok", false);
    cJSON_AddStringToObject(root, "message", message);
    web_http_set_status(req, "400 Bad Request");
    send_json(req, root);
}

// Common tail for both Fritz!Box provisioning paths (direct setup and
// the post-2FA retry): register the dongle's TR-064 app instance (best
// effort, see below), persist the freshly provisioned SIP profile, kick
// off a re-register, and emit the success JSON. `res` must come from a
// successful tr064_provision_sip_client(); `auth_token` is the 2FA token
// when one is in play, NULL otherwise.
static web_http_error_t finish_fritzbox_setup(web_http_request_t *req,
        const char *fritz_host, const char *fritz_user,
        const char *fritz_pass, const char *auth_token,
        const tr064_sip_result_t *res)
{
    // Best-effort dedicated app instance on the box: gives the sync task
    // its own Phone-rights-only credentials so we don't have to persist
    // the admin password. Failure (older Fritz!OS without AppSetup, a
    // rate-limit, …) is logged but non-fatal — SIP still works, only the
    // sync feature stays disabled.
    char app_user[32] = "";
    char app_pass[40] = "";
    int  app_err_code = 0;
    char app_err_msg[128] = "";
    int app_err = tr064_register_dongle_app(
        fritz_host, 49000, fritz_user, fritz_pass, auth_token,
        app_user, sizeof(app_user),
        app_pass, sizeof(app_pass),
        &app_err_code, app_err_msg, sizeof(app_err_msg));
    if (app_err != PB_OK) {
        // ERROR, not WARN: failing to register the TR-064 app leaves the
        // blocklist sync to the Fritz!Box disabled — a real loss of
        // function worth surfacing (and shipping via the log beacon),
        // not transient noise.
        pb_log_err(TAG,
            "RegisterApp failed (code %d, %s) — sync feature disabled",
            app_err_code, app_err_msg);
        app_user[0] = '\0';
        app_pass[0] = '\0';
    }

    // Persist as a *complete* new SIP profile: reset the expert
    // parameters to the Fritz!Box happy-path defaults so stale
    // transport/realm/srtp/port from an earlier manual or provider setup
    // can't survive config_update()'s merge and break the new UDP/5060
    // registration (#363).
    config_update_t u = {
        .sip_host = fritz_host,
        .has_sip_port = true,
        .sip_port = 5060,
        .sip_user = res->sip_user,
        .sip_pass = res->sip_pass,
        .sip_internal_number = res->internal_number,
        .sip_transport = "udp",
        .sip_auth_user = "",
        .sip_outbound  = "",
        .sip_realm     = "",
        .sip_srtp      = "off",
        .sip_stun      = "",
        .fritzbox_app_user = app_user,
        .fritzbox_app_pass = app_pass,
    };
    if (config_update(&u) != PB_KV_OK) {
        send_fail(req, "NVS write failed.");
        return WEB_HTTP_OK;
    }
    // TR-064 just provisioned a new extension on the Fritz!Box — give the
    // box 1.5 s to make it live before the first REGISTER, otherwise it
    // hits a not-yet-active slot and falls into 30 s retry.
    sip_register_request_reload(true);

    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject  (root, "ok", true);
    cJSON_AddStringToObject(root, "message",
        "Extension created. The dongle is registering now — "
        "see the status bar above for the current state.");
    cJSON_AddStringToObject(root, "sip_user", res->sip_user);
    cJSON_AddStringToObject(root, "internal_number", res->internal_number);
    cJSON_AddBoolToObject  (root, "app_registered", app_user[0] != '\0');
    send_json(req, root);
    return WEB_HTTP_OK;
}

// Shown when the box refuses TR-064 for apps altogether (#651): neither
// the username nor the password is the problem then.
static const char FRITZ_APP_ACCESS_OFF[] =
    "The Fritz!Box does not allow access for apps (TR-064). "
    "Enable \"Allow access for apps\" under Home Network → Network → "
    "Network Settings and try again.";

// POST /api/fritzbox-setup
// Body (URL-encoded): fritz_host=…&fritz_user=…&fritz_pass=…&phone_name=…
// Runs tr064_provision_sip_client; on success, stores the generated
// SIP credentials (plus the registrar host) via config_update() and
// triggers sip_register_request_reload() so the dongle registers
// with the new identity right away.
static web_http_error_t handle_fritzbox_setup(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    char body[512];
    if (recv_body(req, body, sizeof(body)) < 0) {
        send_fail(req, "Body missing or too large.");
        return WEB_HTTP_OK;
    }

    char fritz_host[64]  = "";
    char fritz_user[32]  = "";
    char fritz_pass[64]  = "";
    char phone_name[48]  = "";
    form_get(body, "fritz_host", fritz_host, sizeof(fritz_host));
    form_get(body, "fritz_user", fritz_user, sizeof(fritz_user));
    form_get(body, "fritz_pass", fritz_pass, sizeof(fritz_pass));
    form_get(body, "phone_name", phone_name, sizeof(phone_name));

    if (!fritz_host[0]) strncpy(fritz_host, "fritz.box", sizeof(fritz_host) - 1);
    if (!phone_name[0]) strncpy(phone_name, "PhoneBlock", sizeof(phone_name) - 1);

    // Username is optional — mirror the Fritz!Box web UI that only
    // asks for a password and falls back to the default (last-used)
    // account. Probe LANConfigSecurity:X_AVM-DE_GetUserList for the
    // preferred name before running the provisioning flow.
    if (!fritz_user[0] && fritz_pass[0]) {
        int code = 0;
        char detail[128] = "";
        int lerr = tr064_get_default_username(
            fritz_host, 49000, "", fritz_pass,
            fritz_user, sizeof(fritz_user),
            &code, detail, sizeof(detail));
        if (lerr != PB_OK && code == TR064_ERR_APP_ACCESS) {
            send_fail(req, FRITZ_APP_ACCESS_OFF);
            return WEB_HTTP_OK;
        }
        if (lerr != PB_OK || !fritz_user[0]) {
            char msg[240];
            snprintf(msg, sizeof(msg),
                "Could not auto-detect the Fritz!Box username (%s). "
                "Please enter it explicitly.",
                detail[0] ? detail : "no details");
            send_fail(req, msg);
            return WEB_HTTP_OK;
        }
    }
    if (!fritz_user[0]) strncpy(fritz_user, "admin", sizeof(fritz_user) - 1);
    if (!fritz_pass[0]) {
        send_fail(req, "Fritz!Box password missing.");
        return WEB_HTTP_OK;
    }

    tr064_sip_result_t res;
    memset(&res, 0, sizeof(res));
    int err = tr064_provision_sip_client(
        fritz_host, 49000, fritz_user, fritz_pass, phone_name, NULL, &res);
    if (err != PB_OK && res.error_code == 866) {
        // Fritz!Box requires 2FA — start the handshake, stash the
        // credentials + phone name for the retry, and tell the web UI
        // to show the "press a button" instructions + start polling.
        memset(&s_2fa, 0, sizeof(s_2fa));
        int aerr = tr064_auth_start(fritz_host, 49000,
            fritz_user, fritz_pass,
            s_2fa.token,   sizeof(s_2fa.token),
            s_2fa.state,   sizeof(s_2fa.state),
            s_2fa.methods, sizeof(s_2fa.methods));
        if (aerr != PB_OK || !s_2fa.token[0]) {
            send_fail(req, "2FA start failed (X_AVM-DE_Auth unreachable?)");
            return WEB_HTTP_OK;
        }
        s_2fa.active      = true;
        s_2fa.started_us  = pb_monotonic_us();
        strncpy(s_2fa.fritz_host, fritz_host, sizeof(s_2fa.fritz_host) - 1);
        strncpy(s_2fa.fritz_user, fritz_user, sizeof(s_2fa.fritz_user) - 1);
        strncpy(s_2fa.fritz_pass, fritz_pass, sizeof(s_2fa.fritz_pass) - 1);
        strncpy(s_2fa.phone_name, phone_name, sizeof(s_2fa.phone_name) - 1);

        cJSON *root = cJSON_CreateObject();
        cJSON_AddBoolToObject  (root, "ok", false);
        cJSON_AddBoolToObject  (root, "two_factor", true);
        cJSON_AddStringToObject(root, "methods", s_2fa.methods);
        cJSON_AddStringToObject(root, "state",   s_2fa.state);
        // `methods` is the machine-readable discriminator; the UI
        // renders the actual localised instructions from that (see
        // render2FAInstructions in index.html). `message` is just an
        // English fallback for direct API consumers.
        cJSON_AddStringToObject(root, "message",
            "Please press any button on the Fritz!Box now. "
            "Alternatively dial the DTMF sequence on a connected phone.");
        send_json(req, root);
        return WEB_HTTP_OK;
    }
    if (err != PB_OK) {
        char msg[320];
        switch (res.error_code) {
            case TR064_ERR_TRANSPORT:
                snprintf(msg, sizeof(msg),
                    "Fritz!Box at '%s:49000' unreachable (%s). "
                    "Check the hostname/IP and that the dongle is on the same LAN.",
                    fritz_host,
                    res.error_message[0] ? res.error_message : "unknown");
                break;
            case TR064_ERR_AUTH:
                snprintf(msg, sizeof(msg),
                    "Fritz!Box rejected the login: wrong username or password. "
                    "Note: this is the box's admin account, not MyFRITZ!.");
                break;
            case TR064_ERR_HTTP:
                snprintf(msg, sizeof(msg),
                    "Unexpected response from the Fritz!Box: %s. "
                    "Is TR-064 enabled on the router (Home Network → Network Settings)?",
                    res.error_message[0] ? res.error_message : "(no details)");
                break;
            case TR064_ERR_PARSE:
                snprintf(msg, sizeof(msg),
                    "Could not parse the Fritz!Box response: %s.",
                    res.error_message);
                break;
            case TR064_ERR_APP_ACCESS:
                snprintf(msg, sizeof(msg), "%s", FRITZ_APP_ACCESS_OFF);
                break;
            case 820:
            case 402:
                snprintf(msg, sizeof(msg),
                    "Fritz!Box rejected the arguments (code %d, %s).",
                    res.error_code, res.error_message);
                break;
            default:
                snprintf(msg, sizeof(msg),
                    "Fritz!Box error %d: %s",
                    res.error_code,
                    res.error_message[0] ? res.error_message : "(no details)");
                break;
        }
        send_fail(req, msg);
        return WEB_HTTP_OK;
    }

    // Provisioning succeeded — register the app instance, persist the
    // profile, re-register and answer (token=NULL: no 2FA was needed).
    return finish_fritzbox_setup(req, fritz_host, fritz_user, fritz_pass,
                                 NULL, &res);
}

// Percent-encode the unsafe characters in `in` into `out`. RFC 3986
// unreserved set (letters, digits, -_.~) plus a safe colon-safe
// handling for URLs. Good enough for our `callback=` parameter.
static void url_encode(const char *in, char *out, size_t cap)
{
    static const char hex[] = "0123456789ABCDEF";
    size_t o = 0;
    for (const unsigned char *p = (const unsigned char *)in;
         *p && o + 4 < cap; p++) {
        unsigned char c = *p;
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
            || (c >= '0' && c <= '9')
            || c == '-' || c == '_' || c == '.' || c == '~') {
            out[o++] = c;
        } else {
            out[o++] = '%';
            out[o++] = hex[c >> 4];
            out[o++] = hex[c & 0x0f];
        }
    }
    out[o] = '\0';
}

// GET /register-start
// Generate a CSRF nonce, build http(s)://<phoneblock>/pb/dongle-register
// ?callback=<us>&state=<nonce>, redirect the browser there. The server
// handles OAuth, then redirects back to /token-callback with ?token=…
// &state=<nonce>.
static web_http_error_t handle_register_start(web_http_request_t *req)
{
    REQUIRE_AUTH_HTML(req);
    // Random 32-hex-char nonce.
    static const char hex[] = "0123456789abcdef";
    for (int i = 0; i < 32; i++) {
        s_oauth_nonce[i] = hex[pb_random_u32() & 0x0f];
    }
    s_oauth_nonce[32] = '\0';

    // Callback URL uses whatever Host the browser reached us under
    // (answerbot / fritz.box-local-name / raw IP) so phoneblock.net
    // redirects back to the same origin the user came from.
    char host[64] = "answerbot";
    web_http_get_header(req, "Host", host, sizeof(host));

    char callback_plain[128];
    snprintf(callback_plain, sizeof(callback_plain),
             "http://%s/token-callback", host);

    char callback_enc[256];
    url_encode(callback_plain, callback_enc, sizeof(callback_enc));

    // Reuse the existing mobile-login flow on phoneblock.net via its
    // /mobile/login endpoint. `appId=PhoneBlockDongle` switches the
    // /create-token servlet into the dynamic-callback branch, where it
    // echoes our CSRF state back and redirects to our callback URL.
    char url[512];
    snprintf(url, sizeof(url),
             "%s/mobile/login?appId=PhoneBlockDongle&tokenLabel=%s"
             "&callback=%s&state=%s",
             config_phoneblock_base_url(), PB_TOKEN_LABEL,
             callback_enc, s_oauth_nonce);

    pb_log_info(TAG, "register-start → redirect to %s", url);
    web_http_set_status(req, "302 Found");
    web_http_set_header(req, "Location", url);
    web_http_send(req, NULL, 0);
    return WEB_HTTP_OK;
}

// GET /token-callback?token=…&state=<nonce>
// phoneblock.net redirects the user's browser here after successful
// login. We verify the CSRF nonce, commit the token to NVS, and show
// the user a friendly success page that links back to the main UI.
static web_http_error_t handle_token_callback(web_http_request_t *req)
{
    REQUIRE_AUTH_HTML(req);
    char query[512];
    if (web_http_get_query_string(req, query, sizeof(query)) != WEB_HTTP_OK) {
        web_http_set_type(req, "text/html; charset=utf-8");
        web_http_send(req,
            "<p>Missing query parameters. "
            "<a href=\"/\">Back to configuration</a>.</p>",
            WEB_HTTP_RESP_USE_STRLEN);
        return WEB_HTTP_OK;
    }
    char token_raw[128] = "";
    char token[128]     = "";
    char state_raw[64]  = "";
    char state[64]      = "";
    // Server's /create-token uses `loginToken` as the parameter name;
    // accept `token` too for robustness if we ever change the server.
    if (web_http_query_value(query, "loginToken", token_raw, sizeof(token_raw)) != WEB_HTTP_OK) {
        web_http_query_value(query, "token", token_raw, sizeof(token_raw));
    }
    web_http_query_value(query, "state", state_raw, sizeof(state_raw));
    // web_http_query_value does *not* unescape %XX sequences. A token
    // containing e.g. '+' or '/' arrives here as "%2B"/"%2F" and, if
    // stored raw, the server never recognises it — length mismatch
    // (35 vs 37 chars) was the tell. Decode both values.
    url_decode(token_raw, strlen(token_raw), token, sizeof(token));
    url_decode(state_raw, strlen(state_raw), state, sizeof(state));

    if (!s_oauth_nonce[0] || strcmp(state, s_oauth_nonce) != 0) {
        s_oauth_nonce[0] = '\0';
        pb_log_warn(TAG, "token-callback: bad or missing CSRF state");
        web_http_set_status(req, "400 Bad Request");
        web_http_set_type(req, "text/html; charset=utf-8");
        web_http_send(req,
            "<p>Invalid or expired CSRF state. "
            "<a href=\"/\">Please start over</a>.</p>",
            WEB_HTTP_RESP_USE_STRLEN);
        return WEB_HTTP_OK;
    }
    s_oauth_nonce[0] = '\0';  // single-use

    if (!token[0]) {
        web_http_set_status(req, "400 Bad Request");
        web_http_set_type(req, "text/html; charset=utf-8");
        web_http_send(req,
            "<p>No token in the redirect. "
            "<a href=\"/\">Back</a>.</p>",
            WEB_HTTP_RESP_USE_STRLEN);
        return WEB_HTTP_OK;
    }

    // Store, self-test, seed the country — the same steps as the Linux
    // device-code link (link.c), in one shared function.
    if (!phoneblock_adopt_token(token, "token-callback")) {
        web_http_send_error(req, WEB_HTTP_STATUS_INTERNAL_SERVER_ERROR, "nvs");
        return WEB_HTTP_OK;
    }

    // The web UI opens the sign-in in its own tab and waits on the status
    // page, whose pill flips to green on the next 3 s poll — so this tab
    // closes itself. Browsers allow that only for script-opened tabs; a
    // tab opened any other way (popup blocked, old bookmark) falls back to
    // the status landing. "./" rather than "/" so the fallback stays under
    // a reverse proxy's path prefix.
    static const char close_page[] =
        "<!DOCTYPE html><meta charset=\"utf-8\"><title>PhoneBlock</title>"
        "<script>window.close();"
        "setTimeout(function(){location.replace('./');},500);</script>"
        "<noscript><meta http-equiv=\"refresh\" content=\"0;url=./\"></noscript>";
    web_http_set_type(req, "text/html; charset=utf-8");
    web_http_set_header(req, "Cache-Control", "no-store");
    web_http_send(req, close_page, sizeof(close_page) - 1);
    return WEB_HTTP_OK;
}

#ifndef ESP_PLATFORM
// The device-code link (#621, link.h), offered by the web UI when
// /api/status reports token_link. Linux only: the ESP32 has the redirect
// flow above and carries none of this.

static cJSON *link_status_json(const link_status_t *st)
{
    cJSON *root = cJSON_CreateObject();
    cJSON_AddStringToObject(root, "state",     link_state_name(st->state));
    cJSON_AddStringToObject(root, "userCode",  st->user_code);
    cJSON_AddStringToObject(root, "verifyUrl", st->verify_url);
    cJSON_AddStringToObject(root, "message",   st->message);
    return root;
}

// POST /api/link/start
// Asks phoneblock.net for a code pair and starts the poll task. Answers
// {userCode, verifyUrl, expiresIn}; on failure 502 with {state:"error",
// message}. Never the device code.
static web_http_error_t handle_link_start(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    link_status_t st;
    if (!link_start(&st)) {
        web_http_set_status(req, "502 Bad Gateway");
        send_json(req, link_status_json(&st));
        return WEB_HTTP_OK;
    }
    cJSON *root = cJSON_CreateObject();
    cJSON_AddStringToObject(root, "userCode",  st.user_code);
    cJSON_AddStringToObject(root, "verifyUrl", st.verify_url);
    cJSON_AddNumberToObject(root, "expiresIn", st.expires_in_s);
    send_json(req, root);
    return WEB_HTTP_OK;
}

// GET /api/link/status
// {state: idle|pending|approved|denied|expired|error, userCode, verifyUrl,
// message}, polled by the web UI while a link runs.
static web_http_error_t handle_link_status(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    link_status_t st;
    link_get_status(&st);
    send_json(req, link_status_json(&st));
    return WEB_HTTP_OK;
}
#endif

// GET /api/fritzbox-2fa-status
// Polled by the web UI while a 2FA handshake is pending. Reports the
// current X_AVM-DE_Auth state. When the box reports "authenticated",
// retries SetClient4 with the stashed credentials + token, stores the
// resulting SIP credentials in NVS, and returns ok=true with the same
// shape as a direct success. On "stopped"/"blocked" or on a timeout
// (2 minutes) the pending state is wiped and an error returned.
static web_http_error_t handle_fritzbox_2fa_status(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    if (!s_2fa.active) {
        send_fail(req, "No 2FA request in progress.");
        return WEB_HTTP_OK;
    }
    // 2-minute hard cap, mirroring the Fritz!Box's own timeout.
    int64_t age_us = pb_monotonic_us() - s_2fa.started_us;
    if (age_us > 120LL * 1000 * 1000) {
        s_2fa.active = false;
        send_fail(req, "Timed out. Please start over.");
        return WEB_HTTP_OK;
    }

    char state[32] = "";
    int err = tr064_auth_get_state(s_2fa.fritz_host, 49000,
        s_2fa.fritz_user, s_2fa.fritz_pass, s_2fa.token,
        state, sizeof(state));
    if (err != PB_OK) {
        send_fail(req, "GetState failed.");
        return WEB_HTTP_OK;
    }

    if (strcmp(state, "authenticated") != 0) {
        // Still waiting / cancelled / blocked — report state, let UI poll on.
        cJSON *root = cJSON_CreateObject();
        bool terminal = (strcmp(state, "stopped") == 0
                      || strcmp(state, "blocked") == 0
                      || strcmp(state, "failure") == 0);
        cJSON_AddBoolToObject  (root, "ok",         false);
        cJSON_AddBoolToObject  (root, "two_factor", true);
        cJSON_AddBoolToObject  (root, "terminal",   terminal);
        cJSON_AddStringToObject(root, "state",      state);
        cJSON_AddStringToObject(root, "message",
            terminal ? "2FA cancelled or blocked."
                     : "Waiting for confirmation on the router…");
        if (terminal) s_2fa.active = false;
        send_json(req, root);
        return WEB_HTTP_OK;
    }

    // 2FA done → retry SetClient4 with the token.
    tr064_sip_result_t res;
    memset(&res, 0, sizeof(res));
    err = tr064_provision_sip_client(s_2fa.fritz_host, 49000,
        s_2fa.fritz_user, s_2fa.fritz_pass, s_2fa.phone_name,
        s_2fa.token, &res);

    if (err != PB_OK) {
        // Wipe the cached admin password before bailing — the round-trip
        // is over either way.
        memset(s_2fa.fritz_pass, 0, sizeof(s_2fa.fritz_pass));
        s_2fa.active = false;
        char msg[200];
        snprintf(msg, sizeof(msg),
            "Still failing after 2FA — error %d: %s",
            res.error_code,
            res.error_message[0] ? res.error_message : "(no details)");
        send_fail(req, msg);
        return WEB_HTTP_OK;
    }

    // Provisioning succeeded — same tail as the direct path, passing the
    // 2FA token so the app instance registers within the auth session.
    web_http_error_t rc = finish_fritzbox_setup(req, s_2fa.fritz_host,
        s_2fa.fritz_user, s_2fa.fritz_pass, s_2fa.token, &res);
    // The 2FA round-trip is over; wipe the cached admin password.
    memset(s_2fa.fritz_pass, 0, sizeof(s_2fa.fritz_pass));
    s_2fa.active = false;
    return rc;
}

static web_http_error_t handle_errors(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    stats_error_t errs[STATS_MAX_ERRORS];
    int n = stats_snapshot_errors(errs, STATS_MAX_ERRORS);
    int64_t now_us = pb_monotonic_us();

    cJSON *root = cJSON_CreateObject();
    cJSON *arr  = cJSON_AddArrayToObject(root, "errors");
    for (int i = 0; i < n; i++) {
        cJSON *o = cJSON_CreateObject();
        cJSON_AddNumberToObject(o, "age_s",   (double)((now_us - errs[i].at_us) / 1000000));
        cJSON_AddStringToObject(o, "level",
            errs[i].level == STATS_LOG_LEVEL_ERROR ? "E" :
            errs[i].level == STATS_LOG_LEVEL_WARN  ? "W" : "I");
        cJSON_AddStringToObject(o, "tag",     errs[i].tag);
        cJSON_AddStringToObject(o, "message", errs[i].message);
        cJSON_AddItemToArray(arr, o);
    }
    send_json(req, root);
    return WEB_HTTP_OK;
}

#ifdef ESP_PLATFORM
// GET /api/log/stream — follow the full log live (every line that hits
// the serial console, all levels), one subscriber at a time.
//
// esp_http_server dispatches handlers on its single worker task, so a
// handler that blocked in the stream loop would freeze the whole web UI.
// Instead we detach the request (httpd_req_async_handler_begin) and serve
// it from a dedicated task, leaving the worker free; the worker returns
// immediately. The task sends one chunk per log line — each chunk is its
// own TCP write, so lines reach the client the instant they are logged.

// True while the streaming client's socket is still open. During idle
// gaps we must notice a vanished client *without writing*: a heartbeat
// byte would litter the log with blank lines. A non-blocking MSG_PEEK
// returns 0 on an orderly close (FIN) and a hard error on a reset;
// EWOULDBLOCK means "connected, nothing to read" — the normal case for a
// stream the client only reads from.
static bool log_client_connected(int fd)
{
    if (fd < 0) return false;
    char probe;
    int r = recv(fd, &probe, 1, MSG_DONTWAIT | MSG_PEEK);
    if (r == 0) return false;                                   // client closed
    if (r < 0 && errno != EAGAIN && errno != EWOULDBLOCK)
        return false;                                           // reset / error
    return true;
}

static void log_stream_task(void *arg)
{
    web_http_request_t *req = (web_http_request_t *)arg;
    int fd = web_http_request_to_sockfd(req);
    char *buf = malloc(LOG_STREAM_LINE_MAX);
    if (buf) {
        web_http_set_type(req, "text/plain; charset=utf-8");
        web_http_set_header(req, "Cache-Control", "no-store");
        web_http_set_header(req, "X-Accel-Buffering", "no");  // no proxy buffering
        esp_err_t err = web_http_send_chunk(req, "--- live log ---\n",
                                              WEB_HTTP_RESP_USE_STRLEN);
        while (err == ESP_OK) {
            size_t n = log_stream_read(buf, LOG_STREAM_LINE_MAX, 5000);
            if (n > 0) {
                err = web_http_send_chunk(req, buf, n);  // one chunk per line
            } else if (!log_client_connected(fd)) {
                break;            // idle gap and the peer is gone — free the slot
            }
            // else: idle but still connected — emit nothing (no heartbeat noise)
        }
        free(buf);
    }
    web_http_send_chunk(req, NULL, 0);         // best-effort terminate
    httpd_req_async_handler_complete(req);
    log_stream_close();
    vTaskDelete(NULL);
}

static esp_err_t handle_log_stream(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);

    // Single subscriber: reserve the slot before detaching the request.
    if (!log_stream_open()) {
        web_http_set_status(req, "409 Conflict");
        web_http_set_type(req, "text/plain");
        web_http_send_text(req, "log stream already in use\n");
        return WEB_HTTP_OK;
    }

    web_http_request_t *areq = NULL;
    if (httpd_req_async_handler_begin(req, &areq) != ESP_OK) {
        log_stream_close();
        web_http_set_status(req, "500 Internal Server Error");
        web_http_set_type(req, "text/plain");
        web_http_send_text(req, "cannot start log stream\n");
        return WEB_HTTP_OK;
    }
    // 4 KB stack: the read buffer is on the heap, the send path is plain
    // HTTP (no TLS), so the chunk framing is all this task needs.
    if (xTaskCreate(log_stream_task, "logstream", 4096, areq,
                    tskIDLE_PRIORITY + 5, NULL) != pdPASS) {
        httpd_req_async_handler_complete(areq);  // can no longer respond on req
        log_stream_close();
    }
    return ESP_OK;
}
#endif

// GET /api/announcement — streams the currently active A-law audio.
// Content-Type: audio/basic (the IANA-registered type for G.711
// µ-law, close enough to A-law for a "audio payload" hint; browsers
// don't decode either natively, the UI wraps it in a WAV header).
static web_http_error_t handle_announcement_get(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    announcement_src_t src;
    announcement_open(&src);
    if (src.len == 0) {
        announcement_close(&src);
        web_http_send_error(req, WEB_HTTP_STATUS_NOT_FOUND, "no announcement");
        return WEB_HTTP_OK;
    }
    web_http_set_type(req, "audio/basic");
    web_http_set_header(req, "Cache-Control", "no-store");

    // Stream the announcement in chunks straight from its source (flash
    // RODATA for the default, the storage partition for a custom one) so we never hold
    // the whole ~240 KB blob in a heap buffer.
    uint8_t buf[1024];
    size_t n;
    while ((n = announcement_read(&src, buf, sizeof(buf))) > 0) {
        if (web_http_send_chunk(req, (const char *)buf, n) != WEB_HTTP_OK) {
            announcement_close(&src);
            return WEB_HTTP_FAIL;   // socket gone — abort the response
        }
    }
    announcement_close(&src);
    web_http_send_chunk(req, NULL, 0);   // terminate the chunked response
    return WEB_HTTP_OK;
}

// The ENGLISH web-UI strings, baked into the image from i18n/l10n/ui/ui_en.arb
// (stripped to JSON at build; see main/CMakeLists.txt) — the universal offline
// fallback, served when no downloaded pack for the active locale is present.
// TEXT embed → NUL-terminated, so the JSON length is (end - start - 1).
#ifdef ESP_PLATFORM
extern const char ui_en_json_start[] asm("_binary_ui_en_json_start");
extern const char ui_en_json_end[]   asm("_binary_ui_en_json_end");
#endif

// GET /api/i18n/ui — serves the UI translation pack for the active ui_lang.
// If i18n_sync has downloaded one to the storage partition, serve that (same-origin, so a
// configured dongle needs no CDN at runtime); otherwise serve the baked-in
// English fallback. Never 404s — the browser always gets a usable dict. No
// auth: the page needs this to render, possibly before login.
static web_http_error_t handle_i18n_ui(web_http_request_t *req)
{
    web_http_set_type(req, "application/json");
    web_http_set_header(req, "Cache-Control", "no-store");

    char path[PB_DATA_PATH_CAP];
    FILE *f = i18n_sync_ui_path(path, sizeof(path), config_ui_lang())
        ? fopen(path, "rb") : NULL;
    if (f) {
        char buf[1024];
        size_t n;
        while ((n = fread(buf, 1, sizeof(buf), f)) > 0) {
            if (web_http_send_chunk(req, buf, n) != WEB_HTTP_OK) {
                fclose(f);
                return WEB_HTTP_FAIL;
            }
        }
        fclose(f);
        web_http_send_chunk(req, NULL, 0);
        return WEB_HTTP_OK;
    }
    // No downloaded pack (a locale still downloading, or offline) → the
    // baked-in English fallback.
#ifdef ESP_PLATFORM
    web_http_send(req, ui_en_json_start,
                    (size_t)(ui_en_json_end - ui_en_json_start - 1));
    return WEB_HTTP_OK;
#else
    return send_web_asset(req, "ui_en.json", NULL);
#endif
}

// Receive timeouts tolerated per upload before giving up. One timeout
// is the httpd recv_wait_timeout (5 s) without a single byte.
#define UPLOAD_RECV_RETRIES 3

// Next piece of a streamed upload body. A receive timeout is retried a
// few times instead of ending the upload. Every timeout and the final
// failure are logged with the offset and the heap state, because the
// client only gets a generic "Upload interrupted.".
static int upload_recv(web_http_request_t *req, char *buf, int want,
                       const char *what, int got, int total)
{
    for (int timeouts = 0; ; timeouts++) {
        int n = web_http_read_body(req, buf, want);
        if (n > 0) return n;
        bool retry = n == WEB_HTTP_RECV_TIMEOUT && timeouts < UPLOAD_RECV_RETRIES;
#ifdef ESP_PLATFORM
        ESP_LOGW(TAG, "%s upload: recv %d at %d of %d bytes, %s "
                 "(heap free %u, largest %u)",
                 what, n, got, total, retry ? "retrying" : "giving up",
                 (unsigned) esp_get_free_heap_size(),
                 (unsigned) heap_caps_get_largest_free_block(MALLOC_CAP_8BIT));
#else
        pb_log_warn(TAG, "%s upload: recv %d at %d of %d bytes, %s",
                    what, n, got, total, retry ? "retrying" : "giving up");
#endif
        if (!retry) return n;
        pb_watchdog_reset();
    }
}

// POST /api/announcement — replaces the current announcement with the
// request body. Body is raw A-law bytes (8 kHz mono, no header); the
// UI does the WAV/MP3/OGG → A-law conversion via the browser's Web
// Audio API before uploading. Streamed straight into the storage partition via the
// announcement_write_* API so we don't hold the full 240 KB on heap.
static web_http_error_t handle_announcement_post(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    int total = web_http_body_length(req);
    if (total <= 0 || total > (int)ANNOUNCEMENT_MAX_BYTES) {
        char msg[64];
        snprintf(msg, sizeof(msg),
            "Audio missing or over %u bytes cap.",
            (unsigned)ANNOUNCEMENT_MAX_BYTES);
        send_fail(req, msg);
        return WEB_HTTP_OK;
    }

    // Dialog mode (#429) and an own recording do not coexist: the switch to
    // dialog deletes it, and a new one is only taken in announcement mode.
    if (config_ab_dialog()) {
        pb_log_warn(TAG, "announcement upload refused: dialog mode is on "
                         "(switch back to the announcement first)");
        send_fail(req, "Dialog mode is on: an own announcement can only be "
                       "uploaded in announcement mode.");
        return WEB_HTTP_OK;
    }

    int64_t t0 = pb_monotonic_us();
    int err = announcement_write_begin((size_t)total);
    if (err != PB_OK) {
        send_fail(req, "Could not open upload target.");
        return WEB_HTTP_OK;
    }

    // 8 KB chunks on the heap: bigger batches keep the filesystem's write
    // calls coarse and the httpd-worker stack uncluttered.
    const int CHUNK = 8192;
    char *chunk = malloc(CHUNK);
    if (!chunk) {
        announcement_write_abort();
        send_fail(req, "Out of memory.");
        return WEB_HTTP_OK;
    }
    int64_t recv_us = 0;
    int64_t write_us = 0;
    int got = 0;
    while (got < total) {
        int want = total - got;
        if (want > CHUNK) want = CHUNK;
        int64_t rs = pb_monotonic_us();
        int n = upload_recv(req, chunk, want, "announcement", got, total);
        recv_us += pb_monotonic_us() - rs;
        if (n <= 0) {
            free(chunk);
            announcement_write_abort();
            send_fail(req, "Upload interrupted.");
            return WEB_HTTP_OK;
        }
        int64_t ws = pb_monotonic_us();
        if (announcement_write_append((const uint8_t *)chunk, n) != PB_OK) {
            free(chunk);
            announcement_write_abort();
            send_fail(req, "Storage write failed.");
            return WEB_HTTP_OK;
        }
        write_us += pb_monotonic_us() - ws;
        got += n;
        // Up to 240 KB audio blob; flash write latency plus the
        // browser upload can push this past CONFIG_ESP_TASK_WDT_TIMEOUT_S
        // on a slow link. Same rationale as the firmware upload handler.
        pb_watchdog_reset();
    }
    free(chunk);

    int64_t cs = pb_monotonic_us();
    // Commit measured separately from the per-chunk write time.

    if (announcement_write_commit() != PB_OK) {
        send_fail(req, "Commit failed.");
        return WEB_HTTP_OK;
    }
    int64_t commit_us = pb_monotonic_us() - cs;
    int64_t total_us  = pb_monotonic_us() - t0;
    pb_log_info(TAG,
        "announcement upload: %d bytes in %lld ms "
        "(recv %lld ms, write %lld ms, commit %lld ms)",
        got,
        (long long)(total_us / 1000),
        (long long)(recv_us  / 1000),
        (long long)(write_us / 1000),
        (long long)(commit_us / 1000));

    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject  (root, "ok", true);
    cJSON_AddNumberToObject(root, "bytes", got);
    cJSON_AddStringToObject(root, "message", "Announcement saved.");
    send_json(req, root);
    return WEB_HTTP_OK;
}

// POST /api/announcement/reset — drop the custom announcement, fall
// back to the embedded default.
static web_http_error_t handle_announcement_reset(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    announcement_reset();
    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject  (root, "ok", true);
    cJSON_AddStringToObject(root, "message", "Announcement reset to default.");
    send_json(req, root);
    return WEB_HTTP_OK;
}

#ifdef ESP_PLATFORM
// POST /api/firmware — upload a new firmware binary. Streams the
// request body into the inactive OTA slot (ota_0/ota_1), verifies
// the image, flips the boot partition, and reboots. The new image
// comes up in "pending verify" state; unless the boot path reaches
// esp_ota_mark_app_valid_cancel_rollback() (in app_main after
// web_start), the bootloader rolls back on the next reset.
static esp_err_t handle_firmware_upload(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    int total = web_http_body_length(req);
    if (total <= 0) {
        send_fail(req, "Upload body missing.");
        return WEB_HTTP_OK;
    }

    const esp_partition_t *target = esp_ota_get_next_update_partition(NULL);
    if (!target) {
        send_fail(req, "No OTA slot configured.");
        return WEB_HTTP_OK;
    }
    if ((size_t)total > target->size) {
        char msg[96];
        snprintf(msg, sizeof(msg),
            "Firmware %d bytes exceeds partition size %u.",
            total, (unsigned)target->size);
        send_fail(req, msg);
        return ESP_OK;
    }

    esp_ota_handle_t ota = 0;
    esp_err_t err = esp_ota_begin(target, OTA_WITH_SEQUENTIAL_WRITES, &ota);
    if (err != ESP_OK) {
        pb_log_err(TAG, "esp_ota_begin: %s", esp_err_to_name(err));
        send_fail(req, "Could not start OTA write.");
        return ESP_OK;
    }

    const int CHUNK = 4096;
    char *buf = malloc(CHUNK);
    if (!buf) {
        esp_ota_abort(ota);
        send_fail(req, "Out of memory.");
        return ESP_OK;
    }
    int64_t t0 = pb_monotonic_us();
    int got = 0;
    while (got < total) {
        int want = total - got;
        if (want > CHUNK) want = CHUNK;
        int n = upload_recv(req, buf, want, "firmware", got, total);
        if (n <= 0) {
            free(buf);
            esp_ota_abort(ota);
            send_fail(req, "Upload interrupted.");
            return ESP_OK;
        }
        err = esp_ota_write(ota, buf, n);
        if (err != ESP_OK) {
            pb_log_err(TAG, "esp_ota_write: %s", esp_err_to_name(err));
            free(buf);
            esp_ota_abort(ota);
            send_fail(req, "Flash write failed.");
            return ESP_OK;
        }
        got += n;
        // Feed the watchdog ourselves: a ~1.4 MB upload in 4 KB
        // chunks easily takes 10–30 s on the HTTPD task, far past
        // CONFIG_ESP_TASK_WDT_TIMEOUT_S. The periodic feeder posted
        // from web_start() runs as a work item between handler
        // invocations, not while we're still inside one.
        esp_task_wdt_reset();
    }
    free(buf);

    err = esp_ota_end(ota);
    if (err != ESP_OK) {
        pb_log_err(TAG, "esp_ota_end: %s", esp_err_to_name(err));
        send_fail(req, "Image verification failed.");
        return ESP_OK;
    }
    err = esp_ota_set_boot_partition(target);
    if (err != ESP_OK) {
        pb_log_err(TAG, "esp_ota_set_boot_partition: %s", esp_err_to_name(err));
        send_fail(req, "Could not activate new partition.");
        return ESP_OK;
    }

    // Manual upload trumps the auto-update toggle: the user just
    // hand-picked this build, so the daily updater must not silently
    // overwrite it on the next nightly poll. The toggle in the web UI
    // surfaces the new state and lets the user re-enable auto-update
    // when ready to follow the released stream again.
    config_update_t off = { .auto_update = "0" };
    if (config_update(&off) != ESP_OK) {
        pb_log_warn(TAG, "firmware upload: auto-update disable failed (continuing)");
    } else {
        pb_log_info(TAG, "firmware upload: auto-update disabled");
    }

    int64_t total_us = pb_monotonic_us() - t0;
    pb_log_info(TAG, "firmware upload: %d bytes to %s in %lld ms",
             got, target->label, (long long)(total_us / 1000));

    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject  (root, "ok", true);
    cJSON_AddNumberToObject(root, "bytes", got);
    cJSON_AddStringToObject(root, "partition", target->label);
    cJSON_AddStringToObject(root, "message", "Firmware gespeichert — Neustart folgt.");
    send_json(req, root);

    firmware_schedule_reboot();
    return ESP_OK;
}
#else
static web_http_error_t handle_firmware_upload(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    web_http_send_error(req, WEB_HTTP_STATUS_NOT_IMPLEMENTED,
                        "firmware upload is not available on Linux");
    return WEB_HTTP_OK;
}
#endif

// POST /api/firmware/check — read-only manifest check (no download).
// Returns within ~1-2 s. If a newer version is available, the frontend
// shows the version number to the user before posting to /install, so
// the long "Lade Aktualisierung…" wait is no longer the first feedback
// the user sees after clicking "Auf Aktualisierung prüfen".
//
// force=true so the manual flow ignores the last_failed_ota guard the
// auto-update task uses to break a brick→rollback→retry-same-bits
// loop; the user pressing this button means "yes, try again". The
// matching marker is left in NVS until /install actually overwrites
// it, so a check + cancel never silently forgets the prior failure.
static web_http_error_t handle_firmware_check(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    fw_update_outcome_t out;
    firmware_check_manifest(true, &out);

    switch (out.result) {
        case FW_UPDATE_ERR_NETWORK:
        case FW_UPDATE_ERR_PARSE:
        case FW_UPDATE_ERR_SIGNATURE:
            send_fail(req, out.error);
            return WEB_HTTP_OK;
        // SKIPPED_FAILED is unreachable with force=true, but treat it
        // defensively so a future caller change does not silently
        // misreport "ok".
        case FW_UPDATE_SKIPPED_FAILED:
            send_fail(req, "Update skipped — previously rolled back.");
            return WEB_HTTP_OK;
        default:
            break;
    }

    cJSON *ok = cJSON_CreateObject();
    cJSON_AddBoolToObject  (ok, "ok", true);
    cJSON_AddBoolToObject  (ok, "available", out.result == FW_UPDATE_NEW_AVAILABLE);
    cJSON_AddStringToObject(ok, "current_version", out.current_version);
    cJSON_AddStringToObject(ok, "new_version",     out.new_version);
    send_json(req, ok);
    return WEB_HTTP_OK;
}

// POST /api/firmware/install — download + flash the version the
// preceding /check call advertised. Held synchronously for the full
// ~30 s download; the frontend shows "Neue Version X wird
// installiert…" while it waits. Re-fetches the manifest internally —
// keeps the protocol stateless and tolerates the rare case where the
// CDN flips between check and install.
static web_http_error_t handle_firmware_install(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    fw_update_outcome_t out;
    firmware_try_update(true, &out);

    switch (out.result) {
        case FW_UPDATE_ERR_NETWORK:
        case FW_UPDATE_ERR_PARSE:
        case FW_UPDATE_ERR_SIGNATURE:
        case FW_UPDATE_ERR_OTA:
            send_fail(req, out.error);
            return WEB_HTTP_OK;
        case FW_UPDATE_SKIPPED_FAILED:
            send_fail(req, "Update skipped — previously rolled back.");
            return WEB_HTTP_OK;
        default:
            break;
    }

    cJSON *ok = cJSON_CreateObject();
    cJSON_AddBoolToObject  (ok, "ok", true);
    cJSON_AddBoolToObject  (ok, "installed", out.result == FW_UPDATE_INSTALLED);
    cJSON_AddStringToObject(ok, "current_version", out.current_version);
    cJSON_AddStringToObject(ok, "new_version",     out.new_version);
    send_json(req, ok);
    return WEB_HTTP_OK;
}

#ifdef ESP_PLATFORM
// POST /api/factory-reset — erases our NVS namespace, the stored
// WiFi credentials and the uploaded announcement, then reboots.
// On the next boot wifi_connect sees an empty nvs.net80211 and
// falls through to WPS-PBC, so the dongle can be re-paired from
// scratch without a re-flash.
//
// The destructive work happens on a background task after a grace
// period so the HTTP response has time to reach the browser before
// esp_wifi_restore() tears down the TCP socket. Previously all of
// config_erase / esp_wifi_restore / announcement_reset ran in the
// URI handler and produced "httpd_sock_err: error in send : 113"
// because WiFi was already down by the time send_json() returned.
static void factory_reset_task(void *arg)
{
    (void)arg;
    // Let the HTTP response drain and the client close its socket.
    vTaskDelay(pdMS_TO_TICKS(1000));

    int err = config_erase();
    if (err != PB_KV_OK) {
        pb_log_err(TAG, "config_erase failed: %s", pb_kv_err_name(err));
    }
    esp_err_t wifi_err = esp_wifi_restore();
    if (wifi_err != ESP_OK) {
        pb_log_warn(TAG, "esp_wifi_restore: %s", esp_err_to_name(wifi_err));
    }
    announcement_reset();

    vTaskDelay(pdMS_TO_TICKS(200));
    pb_log_warn(TAG, "factory-reset: restarting now");
    esp_restart();
}

static esp_err_t handle_factory_reset(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    // Users have reported surprise factory resets without pressing the
    // red button. Log the caller so a recurrence is diagnosable — the
    // UI is behind a confirm() dialog, so any call reaching us is
    // either a deliberate click or an unexpected client (stale tab,
    // extension, scanner, ...).
    char peer_ip[INET6_ADDRSTRLEN] = "?";
    int fd = web_http_request_to_sockfd(req);
    if (fd >= 0) {
        struct sockaddr_in6 sa;
        socklen_t sa_len = sizeof(sa);
        if (getpeername(fd, (struct sockaddr *)&sa, &sa_len) == 0) {
            if (sa.sin6_family == AF_INET6) {
                inet_ntop(AF_INET6, &sa.sin6_addr, peer_ip, sizeof(peer_ip));
            }
        }
    }
    char ua[128] = "";
    if (web_http_get_header(req, "User-Agent", ua, sizeof(ua)) != ESP_OK) {
        ua[0] = '\0';
    }
    pb_log_warn(TAG, "factory-reset request from %s, UA=\"%s\"", peer_ip, ua);

    // Require an explicit confirmation token in the POST body. A naked
    // fetch('/api/factory-reset', {method:'POST'}) — stale tab, browser
    // replay, scanner — will have no body and is rejected. The UI
    // sends the token only after the user confirmed the dialog.
    char body[32] = "";
    int want = web_http_body_length(req);
    if (want > 0 && want < (int)sizeof(body)) {
        int have = 0;
        while (have < want) {
            int r = web_http_read_body(req, body + have, want - have);
            if (r <= 0) break;
            have += r;
        }
        body[have] = '\0';
    }
    char expected[48];
    snprintf(expected, sizeof(expected), "confirm=%s", s_reset_nonce);
    if (s_reset_nonce[0] == '\0' || strcmp(body, expected) != 0) {
        pb_log_warn(TAG, "factory-reset: stale/missing nonce — rejected");
        web_http_set_status(req, "400 Bad Request");
        cJSON *err_root = cJSON_CreateObject();
        cJSON_AddBoolToObject  (err_root, "ok", false);
        cJSON_AddStringToObject(err_root, "message",
            "Stale or missing confirmation nonce.");
        send_json(req, err_root);
        return ESP_OK;
    }

    // Acknowledge first. The actual erase + reboot runs on the
    // background task after a 1 s grace period so the TCP socket
    // can flush cleanly before esp_wifi_restore tears it down.
    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject  (root, "ok", true);
    cJSON_AddStringToObject(root, "message",
        "Configuration will be erased — rebooting.");
    send_json(req, root);
    // Make sure the next firefox replay after reboot carries a stale
    // nonce: invalidating the server-side value now is belt-and-braces
    // on top of the fresh nonce that will be generated next boot.
    s_reset_nonce[0] = '\0';
    xTaskCreate(factory_reset_task, "factory_reset", 3072, NULL, 5, NULL);
    return ESP_OK;
}
#else
static web_http_error_t handle_factory_reset(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    web_http_send_error(req, WEB_HTTP_STATUS_NOT_IMPLEMENTED,
                        "factory reset is not available on Linux");
    return WEB_HTTP_OK;
}
#endif

#ifdef CONFIG_CRASH_TEST_ENABLE
// Background task: short grace period to flush the HTTP response, then
// abort() — panics through the standard ESP32 path so the panic
// handler writes a core dump to the dedicated partition. On the next
// boot, crashreport.c uploads it. Used to exercise the full pipeline
// end-to-end without waiting for a real bug.
static void crash_test_task(void *arg)
{
    (void)arg;
    vTaskDelay(pdMS_TO_TICKS(500));
    esp_system_abort("crash-test requested via /api/dev/crash");
}

// POST /api/dev/crash — deliberately panic the dongle so the next
// boot ships a real core dump to the backend. Only compiled in when
// CONFIG_CRASH_TEST_ENABLE=y; production firmware has no handler at
// this URI and replies 404. Auth + per-boot nonce are mandatory: a
// stale Firefox replay after the post-panic reboot must not retrigger
// the panic, which would otherwise lock the device into a crash loop.
static esp_err_t handle_crash_test(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);

    char peer_ip[INET6_ADDRSTRLEN] = "?";
    int fd = web_http_request_to_sockfd(req);
    if (fd >= 0) {
        struct sockaddr_in6 sa;
        socklen_t sa_len = sizeof(sa);
        if (getpeername(fd, (struct sockaddr *)&sa, &sa_len) == 0
                && sa.sin6_family == AF_INET6) {
            inet_ntop(AF_INET6, &sa.sin6_addr, peer_ip, sizeof(peer_ip));
        }
    }
    pb_log_warn(TAG, "crash-test request from %s", peer_ip);

    char body[32] = "";
    int want = web_http_body_length(req);
    if (want > 0 && want < (int)sizeof(body)) {
        int have = 0;
        while (have < want) {
            int r = web_http_read_body(req, body + have, want - have);
            if (r <= 0) break;
            have += r;
        }
        body[have] = '\0';
    }
    char expected[48];
    snprintf(expected, sizeof(expected), "confirm=%s", s_reset_nonce);
    if (s_reset_nonce[0] == '\0' || strcmp(body, expected) != 0) {
        pb_log_warn(TAG, "crash-test: stale/missing nonce — rejected");
        web_http_set_status(req, "400 Bad Request");
        cJSON *err_root = cJSON_CreateObject();
        cJSON_AddBoolToObject  (err_root, "ok", false);
        cJSON_AddStringToObject(err_root, "message",
            "Stale or missing confirmation nonce.");
        send_json(req, err_root);
        return ESP_OK;
    }

    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject  (root, "ok", true);
    cJSON_AddStringToObject(root, "message",
        "Coredump test triggered — the dongle will panic and reboot.");
    send_json(req, root);

    // Same belt-and-braces as factory-reset: invalidate the in-memory
    // nonce now so an in-flight Firefox replay during the half-second
    // grace window before abort() cannot fire a second crash.
    s_reset_nonce[0] = '\0';
    xTaskCreate(crash_test_task, "crash_test", 2048, NULL, 5, NULL);
    return ESP_OK;
}

// POST /api/dev/heap-smash — corrupt the heap for real and let the sentinel take
// it from there: it detects, captures the evidence and panics, and the next boot
// uploads the core dump. Exercises the whole pipeline the way a genuine
// corruption would. Only compiled in when CONFIG_CRASH_TEST_ENABLE=y.
static esp_err_t handle_heap_smash(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);

    char detail[160] = "";
    bool ok = heap_guard_smash(detail, sizeof(detail));

    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject  (root, "ok", ok);
    cJSON_AddStringToObject(root, "message", detail);
    send_json(req, root);
    return ESP_OK;
}
#endif // CONFIG_CRASH_TEST_ENABLE

// POST /api/sync/run — trigger an immediate blocklist sync.
static web_http_error_t handle_sync_run(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    bool triggered = sync_trigger_now();
    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject  (root, "ok", triggered);
    cJSON_AddStringToObject(root, "message",
        triggered ? "Sync triggered." : "Sync is already running.");
    send_json(req, root);
    return WEB_HTTP_OK;
}

// POST /api/blocklist-sync/run — trigger an immediate download of the
// community + personal binary blocklist files.
static web_http_error_t handle_blocklist_sync_run(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    bool triggered = blocklist_sync_trigger_now();
    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject  (root, "ok", triggered);
    cJSON_AddStringToObject(root, "message",
        triggered ? "Download triggered." : "Download already running.");
    send_json(req, root);
    return WEB_HTTP_OK;
}

// POST /api/mail/test — send a test status mail using the saved SMTP
// settings, so the user can verify credentials right after entering them.
// Hands the send to the scheduler task and returns immediately: the
// SMTP/TLS conversation blocks for seconds, and this httpd worker is wired
// into the task watchdog (a wedged handler panics — see the WDT feeder
// below), so it must not run the send here. Fire-and-forget; the outcome
// lands in the log panel. Not destructive, so no replay nonce.
static web_http_error_t handle_mail_test(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
#ifndef ESP_PLATFORM
    web_http_send_error(req, WEB_HTTP_STATUS_NOT_IMPLEMENTED,
                        "mail sending is not available on Linux");
    return WEB_HTTP_OK;
#else
    if (!mail_configured()) {
        send_fail(req, "Bitte zuerst Server, Benutzer, Passwort und "
                       "Empfänger speichern.");
        return WEB_HTTP_OK;
    }
    bool ok = scheduler_request_mail_test();
    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject  (root, "ok", ok);
    cJSON_AddStringToObject(root, "message",
        ok ? "Test-E-Mail wird gesendet — das Ergebnis steht im Protokoll."
           : "Versand konnte nicht gestartet werden — bitte Protokoll prüfen.");
    send_json(req, root);
    return WEB_HTTP_OK;
#endif
}

// POST /api/errors/clear — drops all buffered error entries.
static web_http_error_t handle_errors_clear(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    stats_clear_errors();
    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject(root, "ok", true);
    send_json(req, root);
    return WEB_HTTP_OK;
}

// POST /api/calls/clear — drops all buffered call entries.
static web_http_error_t handle_calls_clear(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    stats_clear_calls();
    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject(root, "ok", true);
    send_json(req, root);
    return WEB_HTTP_OK;
}

// --- Fritz!Box phonebook (personal allowlist) ----------------------

// Reply {ok:false, code, message}. `code` is a stable discriminator the
// UI localises; `message` carries whatever detail the box gave us and is
// only shown when the code is unknown to the UI.
static void send_fail_code(web_http_request_t *req, const char *status,
                           const char *code, const char *message)
{
    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject  (root, "ok", false);
    cJSON_AddStringToObject(root, "code", code);
    cJSON_AddStringToObject(root, "message", message);
    web_http_set_status(req, status);
    send_json(req, root);
}

// True when the dongle holds Fritz!Box app credentials and knows a host to
// reach the box at — i.e. it was set up by the wizard. Without them no
// TR-064 call can be made at all.
static bool fritzbox_reachable(void)
{
    return config_sip_host()[0] != '\0'
        && config_fritzbox_app_user()[0] != '\0'
        && config_fritzbox_app_pass()[0] != '\0';
}

// GET /api/phonebooks
// Enumerates the box's phonebooks so the UI can offer a target for new
// contacts. Costs a handful of SOAP calls, so it is only fetched when the
// user actually opens the picker — never from the status poll.
static web_http_error_t handle_phonebooks(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    if (!fritzbox_reachable()) {
        send_fail_code(req, "409 Conflict", "no_fritzbox",
                       "No Fritz!Box app credentials — run the Fritz!Box setup.");
        return WEB_HTTP_OK;
    }

    tr064_phonebook_t *books =
        malloc(sizeof(tr064_phonebook_t) * TR064_MAX_PHONEBOOKS);
    if (!books) {
        send_fail_code(req, "500 Internal Server Error", "oom", "Out of memory.");
        return WEB_HTTP_OK;
    }

    int  count = 0;
    int  code  = 0;
    char msg[128] = "";
    int err = tr064_phonebook_list(config_sip_host(), 49000,
                                         config_fritzbox_app_user(),
                                         config_fritzbox_app_pass(),
                                         books, TR064_MAX_PHONEBOOKS, &count,
                                         &code, msg, sizeof(msg));
    if (err != PB_OK) {
        free(books);
        send_fail_code(req, "502 Bad Gateway", "tr064",
                       msg[0] ? msg : "Fritz!Box did not answer.");
        return WEB_HTTP_OK;
    }

    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject  (root, "ok", true);
    cJSON_AddStringToObject(root, "selected", config_fritzbox_phonebook());
    cJSON_AddStringToObject(root, "selected_name",
                            config_fritzbox_phonebook_name());
    cJSON *arr = cJSON_AddArrayToObject(root, "phonebooks");
    for (int i = 0; i < count; i++) {
        cJSON *o = cJSON_CreateObject();
        cJSON_AddNumberToObject(o, "id",       books[i].id);
        cJSON_AddStringToObject(o, "name",     books[i].name);
        cJSON_AddBoolToObject  (o, "writable", books[i].writable);
        cJSON_AddItemToArray(arr, o);
    }
    free(books);
    send_json(req, root);
    return WEB_HTTP_OK;
}

// POST /api/phonebook/add
// Body (URL-encoded): number=…&name=…&type=home&vip=0[&phonebook_id=0&phonebook_name=…]
//
// Writes one contact into the box's phonebook. `phonebook_id` is only sent
// when the user picked a target in the dialog; it is then remembered — but
// only after the write succeeded, so a pick that turns out to be
// unwritable is not persisted. Without it the stored selection is used,
// and if there is none the UI is told to ask (code "no_phonebook").
static web_http_error_t handle_phonebook_add(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    if (!fritzbox_reachable()) {
        send_fail_code(req, "409 Conflict", "no_fritzbox",
                       "No Fritz!Box app credentials — run the Fritz!Box setup.");
        return WEB_HTTP_OK;
    }

    int total = web_http_body_length(req);
    if (total <= 0 || total > 1024) {
        send_fail_code(req, "400 Bad Request", "invalid",
                       "Body missing or too large.");
        return WEB_HTTP_OK;
    }
    char *body = malloc(total + 1);
    if (!body) {
        send_fail_code(req, "500 Internal Server Error", "oom", "Out of memory.");
        return WEB_HTTP_OK;
    }
    int got = 0;
    while (got < total) {
        int n = web_http_read_body(req, body + got, total - got);
        if (n <= 0) { free(body); return WEB_HTTP_FAIL; }
        got += n;
    }
    body[got] = '\0';

    char number[48] = "";
    char name[TR064_CONTACT_NAME_CAP + 1] = "";
    char type[16]   = "";
    char vip_s[4]   = "";
    char pb_id_s[8] = "";
    char pb_name[TR064_PB_NAME_CAP] = "";
    form_get(body, "number", number, sizeof(number));
    form_get(body, "name",   name,   sizeof(name));
    form_get(body, "type",   type,   sizeof(type));
    form_get(body, "vip",    vip_s,  sizeof(vip_s));
    bool have_pick = form_get(body, "phonebook_id", pb_id_s, sizeof(pb_id_s));
    form_get(body, "phonebook_name", pb_name, sizeof(pb_name));
    free(body);

    if (!type[0]) {
        strncpy(type, "home", sizeof(type) - 1);
        type[sizeof(type) - 1] = '\0';
    }

    // Validated here as well as in tr064_build_contact_arg so the caller
    // gets a specific reason instead of a generic "invalid argument".
    if (!name[0] || strlen(name) >= TR064_CONTACT_NAME_CAP) {
        send_fail_code(req, "400 Bad Request", "invalid_name",
                       "Name missing or too long.");
        return WEB_HTTP_OK;
    }
    if (!tr064_number_plausible(number)) {
        send_fail_code(req, "400 Bad Request", "invalid_number",
                       "Not a number that can be stored in a phone book.");
        return WEB_HTTP_OK;
    }
    if (!tr064_number_type_valid(type)) {
        send_fail_code(req, "400 Bad Request", "invalid_type",
                       "Unknown number type.");
        return WEB_HTTP_OK;
    }

    if (have_pick) {
        for (const char *p = pb_id_s; *p; p++) {
            if (*p < '0' || *p > '9') { have_pick = false; break; }
        }
        if (!pb_id_s[0]) have_pick = false;
    }
    const char *target = have_pick ? pb_id_s : config_fritzbox_phonebook();
    if (!target[0]) {
        // No target yet: the UI reacts to this by fetching /api/phonebooks
        // and asking which phonebook to use.
        send_fail_code(req, "409 Conflict", "no_phonebook",
                       "No phone book selected yet.");
        return WEB_HTTP_OK;
    }

    // A stored ID needs re-checking before use: NewPhonebookID is positional,
    // so creating a phonebook shifts every later ID down by one and the
    // stored number can end up denoting a different book than the user chose
    // — including a writable one, where the contact would land silently in
    // the wrong place. The cached name is what makes that detectable.
    // A freshly picked ID (have_pick) comes from a list the user just saw, so
    // it needs no check.
    char resolved[8] = "";
    if (!have_pick && config_fritzbox_phonebook_name()[0]) {
        const char *want = config_fritzbox_phonebook_name();
        char actual[TR064_PB_NAME_CAP] = "";
        int nerr = tr064_phonebook_name(config_sip_host(), 49000,
                                              config_fritzbox_app_user(),
                                              config_fritzbox_app_pass(),
                                              (int)strtol(target, NULL, 10),
                                              actual, sizeof(actual),
                                              NULL, NULL, 0);
        if (nerr != PB_OK) {
            send_fail_code(req, "502 Bad Gateway", "tr064",
                           "Fritz!Box did not answer.");
            return WEB_HTTP_OK;
        }
        if (strcmp(actual, want) != 0) {
            // Shifted. Look for the chosen name again and follow it; only
            // when it is gone entirely does the user have to decide anew.
            pb_log_warn(TAG, "phonebook %s is now '%s', not '%s' — re-resolving",
                     target, actual, want);
            tr064_phonebook_t *books =
                malloc(sizeof(tr064_phonebook_t) * TR064_MAX_PHONEBOOKS);
            int count = 0;
            if (books && tr064_phonebook_list(config_sip_host(), 49000,
                                              config_fritzbox_app_user(),
                                              config_fritzbox_app_pass(),
                                              books, TR064_MAX_PHONEBOOKS,
                                              &count, NULL, NULL, 0) == PB_OK) {
                for (int i = 0; i < count; i++) {
                    if (books[i].writable && strcmp(books[i].name, want) == 0) {
                        snprintf(resolved, sizeof(resolved), "%d", books[i].id);
                        break;
                    }
                }
            }
            free(books);
            if (!resolved[0]) {
                send_fail_code(req, "409 Conflict", "phonebook_moved",
                               "The selected phone book no longer exists.");
                return WEB_HTTP_OK;
            }
            pb_log_info(TAG, "phonebook '%s' moved to ID %s", want, resolved);
            target = resolved;
        }
    }

    int  code = 0;
    char msg[128] = "";
    char uid[32]  = "";
    int err = tr064_phonebook_add(config_sip_host(), 49000,
                                        config_fritzbox_app_user(),
                                        config_fritzbox_app_pass(),
                                        (int)strtol(target, NULL, 10),
                                        name, number, type,
                                        vip_s[0] == '1',
                                        uid, sizeof(uid),
                                        &code, msg, sizeof(msg));
    if (err != PB_OK) {
        // 713 is the box's answer for a phonebook that mirrors an online
        // address book (CardDAV / Google): those are read-only for
        // TR-064, so tell the user to pick a different one instead of
        // reporting a SOAP failure they can do nothing about.
        if (code == 713) {
            send_fail_code(req, "409 Conflict", "not_writable",
                           "This phone book cannot be written to.");
        } else {
            send_fail_code(req, "502 Bad Gateway", "tr064",
                           msg[0] ? msg : "Fritz!Box did not answer.");
        }
        return WEB_HTTP_OK;
    }

    // Remember the target only now that a write to it has actually worked.
    // Also persists a re-resolved ID, so the shift is corrected once instead
    // of being chased again on every add.
    if (have_pick || resolved[0]) {
        config_update_t u = {
            .fritzbox_phonebook      = have_pick ? pb_id_s : resolved,
            .fritzbox_phonebook_name = have_pick ? pb_name : NULL,
        };
        if (config_update(&u) != PB_KV_OK) {
            pb_log_warn(TAG, "contact stored but phonebook selection not saved");
        }
    }

    // A caller listed as SPAM also goes onto the user's PhoneBlock whitelist:
    // the contact only wins on this box, while the whitelist entry also
    // reaches the user's other devices and lifts their own blacklist entry.
    // Best effort — the contact is stored either way, so a failed rating is
    // only reported back, not turned into an error.
    pb_assessment_t before;
    bool whitelisted = false;
    if (stats_assessment_for_number(number, &before) && assessment_is_spam(before)
        && config_phoneblock_token()[0] != '\0') {
        whitelisted = phoneblock_rate(number, "A_LEGITIMATE", "");
        if (whitelisted) {
            blocklist_sync_trigger_now();
        } else {
            pb_log_warn(TAG, "contact %s stored, but whitelisting it failed", number);
        }
    }

    // The Fritz!Box resolves this caller from now on; show the listed calls
    // in that new state: with the name, as legitimate.
    int touched = stats_set_display(number, name);
    stats_set_assessment(number, PB_ASSESS_LEGITIMATE);

    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject  (root, "ok", true);
    cJSON_AddStringToObject(root, "uid", uid);
    cJSON_AddStringToObject(root, "phonebook_id", target);
    cJSON_AddBoolToObject  (root, "whitelisted", whitelisted);
    cJSON_AddNumberToObject(root, "calls_updated", touched);
    send_json(req, root);
    return WEB_HTTP_OK;
}

// POST /api/rate
// Body (URL-encoded): number=…&rating=B_MISSED
//
// Submits a spam rating for a number straight from the call list, so a
// caller the community has not flagged yet can be reported without going
// to phoneblock.net. The vote is attributed to the account behind the
// stored API token, exactly like a rating made on the website.
//
// Only the negative ratings are accepted: this backs a "report as spam"
// action, and A_LEGITIMATE from here would be indistinguishable from a
// mis-click on a button labelled "Spam".
static web_http_error_t handle_rate(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    if (strlen(config_phoneblock_token()) == 0) {
        send_fail_code(req, "409 Conflict", "no_token",
                       "No PhoneBlock token — ratings need an account.");
        return WEB_HTTP_OK;
    }

    int total = web_http_body_length(req);
    if (total <= 0 || total > 512) {
        send_fail_code(req, "400 Bad Request", "invalid",
                       "Body missing or too large.");
        return WEB_HTTP_OK;
    }
    char *body = malloc(total + 1);
    if (!body) {
        send_fail_code(req, "500 Internal Server Error", "oom", "Out of memory.");
        return WEB_HTTP_OK;
    }
    int got = 0;
    while (got < total) {
        int n = web_http_read_body(req, body + got, total - got);
        if (n <= 0) { free(body); return WEB_HTTP_FAIL; }
        got += n;
    }
    body[got] = '\0';

    char number[48] = "";
    char rating[16] = "";
    char comment[PB_RATE_COMMENT_CAP + 1] = "";
    form_get(body, "number", number, sizeof(number));
    form_get(body, "rating", rating, sizeof(rating));
    form_get(body, "comment", comment, sizeof(comment));
    free(body);

    // Flatten control characters the textarea may have carried (newlines
    // above all) to spaces: the comment is shown as one line wherever it is
    // read, and this keeps the JSON escaping to the printable cases.
    for (char *p = comment; *p; p++) {
        if ((unsigned char)*p < 0x20) *p = ' ';
    }

    if (!tr064_number_plausible(number)) {
        send_fail_code(req, "400 Bad Request", "invalid_number",
                       "Not a number that can be rated.");
        return WEB_HTTP_OK;
    }
    // Whitelisted rather than forwarded: the server would reject an unknown
    // code anyway, but a typo should fail here with a clear reason instead
    // of costing a TLS round-trip to find out.
    static const char *const RATINGS[] = {
        "B_MISSED", "C_PING", "D_POLL", "E_ADVERTISING", "F_GAMBLE",
        "G_FRAUD", NULL
    };
    bool rating_ok = false;
    for (int i = 0; RATINGS[i] && !rating_ok; i++) {
        rating_ok = strcmp(rating, RATINGS[i]) == 0;
    }
    if (!rating_ok) {
        send_fail_code(req, "400 Bad Request", "invalid_rating",
                       "Unknown rating code.");
        return WEB_HTTP_OK;
    }

    // A caller the box resolves from a phonebook must not be ratable: the
    // phonebook entry already wins over every blocklist and API verdict in
    // check_invite_caller(), so the vote would be one this very device
    // ignores. The reverse direction stays open on purpose — a number rated
    // SPAM may still be turned into a contact, which is how a user rescues
    // a false positive.
    //
    // The recent-calls ring is the evidence, which makes this a backstop for
    // a stale page rather than an authoritative check: the ring is RAM-only,
    // so after a reboot a number that IS a contact is no longer known here
    // and a hand-crafted POST would get through. That is acceptable because
    // the rule's real home is the UI (a row with a name is rendered without
    // the vote button) and there is nothing left to click on for a call the
    // ring has forgotten. Making it authoritative would mean asking the box
    // per rating — OnTel has no by-number phonebook lookup, so it would cost
    // a full phonebook download.
    char known[STATS_DISPLAY_LEN] = "";
    if (stats_display_for_number(number, known, sizeof(known))
        && is_known_contact(known)) {
        send_fail_code(req, "409 Conflict", "is_contact",
                       "This caller is a phone-book contact and cannot be "
                       "rated as spam.");
        return WEB_HTTP_OK;
    }

    if (!phoneblock_rate(number, rating, comment)) {
        send_fail_code(req, "502 Bad Gateway", "rate_failed",
                       "PhoneBlock did not accept the rating.");
        return WEB_HTTP_OK;
    }
    pb_log_info(TAG, "rated %s as %s", number, rating);
    // The server put the number on the user's personal blacklist. Show the
    // listed calls in that state, and pull the list now so the next call from
    // this number is already blocked instead of after the daily sync.
    int touched = stats_set_assessment(number, PB_ASSESS_BLACKLIST);
    blocklist_sync_trigger_now();

    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject  (root, "ok", true);
    cJSON_AddStringToObject(root, "rating", rating);
    cJSON_AddBoolToObject  (root, "with_comment", comment[0] != '\0');
    cJSON_AddNumberToObject(root, "calls_updated", touched);
    send_json(req, root);
    return WEB_HTTP_OK;
}

// POST /api/whitelist
// Body (URL-encoded): number=…
//
// Rates a number as legitimate, which puts it on the user's PhoneBlock
// whitelist. Offered on SPAM rows of the call list as the way to rescue a
// false positive when no Fritz!Box contact can be written. Its own route
// rather than a rating code on /api/rate, so a click on "Spam" can never
// turn into a whitelist entry. A personal whitelist entry also overrides
// the user's caller-name patterns (check_invite_caller).
static web_http_error_t handle_whitelist(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    if (strlen(config_phoneblock_token()) == 0) {
        send_fail_code(req, "409 Conflict", "no_token",
                       "No PhoneBlock token — the whitelist needs an account.");
        return WEB_HTTP_OK;
    }
    char body[160];
    if (recv_body(req, body, sizeof(body)) < 0) {
        send_fail_code(req, "400 Bad Request", "invalid",
                       "Body missing or too large.");
        return WEB_HTTP_OK;
    }
    char number[48] = "";
    form_get(body, "number", number, sizeof(number));
    if (!tr064_number_plausible(number)) {
        send_fail_code(req, "400 Bad Request", "invalid_number",
                       "Not a number that can be rated.");
        return WEB_HTTP_OK;
    }
    if (!phoneblock_rate(number, "A_LEGITIMATE", "")) {
        send_fail_code(req, "502 Bad Gateway", "rate_failed",
                       "PhoneBlock did not accept the rating.");
        return WEB_HTTP_OK;
    }
    pb_log_info(TAG, "whitelisted %s", number);
    int touched = stats_set_assessment(number, PB_ASSESS_LEGITIMATE);
    blocklist_sync_trigger_now();

    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject  (root, "ok", true);
    cJSON_AddNumberToObject(root, "calls_updated", touched);
    send_json(req, root);
    return WEB_HTTP_OK;
}

// --- dev resource-bundle upload --------------------------------------

// POST /api/dev/i18n?kind=ui|mail|dialog&lang=<locale>[&speaker=<id>]
//
// Stores a localized resource bundle straight on the device, replacing what
// i18n_sync would otherwise have downloaded. This exists for the firmware
// development loop: a dev build's new UI strings are not in any published
// CDN bundle (i18n_sync keys the bundle path on the *release tag*, so a
// dev build gets the previous release's copy — missing exactly the keys
// under test). Uploading the freshly translated bundle takes the CDN out
// of that loop.
//
// Not user-facing (no UI control), but behind the normal API auth gate. An
// uploaded bundle stays until the next i18n sync — after a reboot, OTA or UI
// language switch — so it survives a test session without any pinning.
//
//   ui, mail      the bundle JSON verbatim — the same stripped shape the CDN
//                 serves (no "@key" description entries). It replaces the
//                 file for the given locale, so it takes effect on the next
//                 page load.
//   dialog        the dialog clip set (#429): one size tier's file exactly as
//                 audio-assets.sh stages it (audio/v<N>/dialog-<lang>-<tier>-
//                 <sha8>.bin), clip table in its header. The index is dropped
//                 first; once the file is in place and its header valid
//                 (dialog_header_parse(), the call path's own check), it is
//                 marked complete — from then on calls get the dialog.
//                 `speaker` (optional) is recorded as whose set it is; any
//                 other set on the device (an own one included) goes.
//
// The dialog kind needs ab_mode=dialog (409 otherwise): a set pushed onto an
// announcement-mode device would only be removed by the next sync.

// --- dialog speakers and the own dialog set (#429) --------------------------

#ifndef CONFIG_PHONEBLOCK_OTA_BASE_URL
#define CONFIG_PHONEBLOCK_OTA_BASE_URL "https://cdn.phoneblock.net/dongle/firmware"
#endif

// Largest dialog set the device takes, own or pushed: its dialog budget
// (the tier this partition downloads; STORAGE_DIALOG_PLAIN_FS_MAX on Linux,
// whose HTTP layer buffers a set whole — the routes taking one name it as
// their max_body, and GET /api/dialog must fit the response buffer).
#ifndef ESP_PLATFORM
_Static_assert(STORAGE_DIALOG_PLAIN_FS_MAX <= WEB_HTTP_MAX_RESPONSE,
               "GET /api/dialog must fit the Linux response buffer");
#endif
static uint64_t dialog_set_cap(void)
{
    return storage_dialog_budget();
}

// GET /api/dialog/speakers — the speakers of the locale's dialog index kept
// on the device (dialog-speakers.json, i18n_sync.c), without a CDN round
// trip:
//   { "selected": "<config dialog_speaker, '' until the first pick>",
//     "max_bytes": <largest set this device takes (own set upload)>,
//     "effective": "<speaker of the CDN set on the device, '' = none / own>",
//     "own": <an own set is on the device>,
//     "speakers": [ { "id", "name", "gender", "preview_url" }, ... ] }
// The first speaker is the locale's default; preview_url is absolute (a
// master on the CDN, played by the browser) or "" when the index has none.
static web_http_error_t handle_dialog_speakers(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    cJSON *root = cJSON_CreateObject();
    cJSON_AddStringToObject(root, "selected", config_dialog_speaker());
    cJSON_AddNumberToObject(root, "max_bytes", (double)dialog_set_cap());
    dialog_set_t *set = malloc(sizeof(*set));
    bool have = set && dialog_store_load(set);
    bool own = have && strcmp(set->lang, DIALOG_OWN_LANG) == 0;
    cJSON_AddStringToObject(root, "effective", have && !own ? set->speaker : "");
    free(set);
    cJSON_AddBoolToObject(root, "own", own || dialog_store_has_own());
    cJSON *list = cJSON_AddArrayToObject(root, "speakers");
    char *text = dialog_store_read_speakers();
    cJSON *index = text ? cJSON_Parse(text) : NULL;
    free(text);
    dialog_speaker_t sp;
    for (int i = 0; dialog_index_speaker(index, i, &sp); i++) {
        cJSON *o = cJSON_CreateObject();
        cJSON_AddStringToObject(o, "id", sp.id);
        cJSON_AddStringToObject(o, "name", sp.name);
        cJSON_AddStringToObject(o, "gender", sp.gender);
        char url[256] = "";
        if (sp.preview[0]) {
            snprintf(url, sizeof(url), "%s/audio/%s", CONFIG_PHONEBLOCK_OTA_BASE_URL,
                     sp.preview);
        }
        cJSON_AddStringToObject(o, "preview_url", url);
        cJSON_AddItemToArray(list, o);
    }
    cJSON_Delete(index);
    send_json(req, root);
    return WEB_HTTP_OK;
}

// GET /api/dialog — the set on the device (own or downloaded) as one file,
// exactly as stored (PBDS header + clips, dialog.h), for the editor in the
// web UI and for "Download set".
static web_http_error_t handle_dialog_get(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    dialog_set_t *set = malloc(sizeof(*set));
    bool have = set && dialog_store_load(set);
    char path[DIALOG_STORE_PATH_CAP];
    char name[64] = "dialog.bin";
    if (have) {
        dialog_store_file_path(path, sizeof(path), set->lang);
        if (set->speaker[0]) {
            snprintf(name, sizeof(name), "dialog-%s-%s.bin", set->lang, set->speaker);
        } else {
            snprintf(name, sizeof(name), "dialog-%s.bin", set->lang);
        }
    }
    free(set);
    FILE *f = have ? fopen(path, "rb") : NULL;
    if (!f) {
        send_fail_code(req, "404 Not Found", "no_set", "No dialog set on the device.");
        return WEB_HTTP_OK;
    }
    char disp[96];
    snprintf(disp, sizeof(disp), "attachment; filename=\"%s\"", name);
    web_http_set_type(req, "application/octet-stream");
    web_http_set_header(req, "Cache-Control", "no-store");
    web_http_set_header(req, "Content-Disposition", disp);
    char *buf = malloc(2048);
    size_t n;
    bool sent = buf != NULL;
    while (sent && (n = fread(buf, 1, 2048, f)) > 0) {
        if (web_http_send_chunk(req, buf, n) != WEB_HTTP_OK) sent = false;
        pb_watchdog_reset();
    }
    free(buf);
    fclose(f);
    if (!sent) return WEB_HTTP_FAIL;   // socket gone (or no memory): abort
    web_http_send_chunk(req, NULL, 0);
    return WEB_HTTP_OK;
}

// Refusals shared by the own set's POSTs: dialog mode only, and never while
// an i18n pass may be replacing the set. Sets the upload flag first and then
// looks at the sync (which sets its running flag first and then looks at the
// upload flag), so the two never run at once. True: go ahead, the caller
// clears the flag with dialog_store_set_upload_active(false).
static bool own_set_begin(web_http_request_t *req, const char *what)
{
    if (!config_ab_dialog()) {
        pb_log_warn(TAG, "%s refused: ab_mode is not 'dialog'", what);
        send_fail_code(req, "409 Conflict", "wrong_mode",
                       "An own dialog set needs the dialog mode.");
        return false;
    }
    if (dialog_store_upload_active()) {
        send_fail_code(req, "409 Conflict", "busy",
                       "Another dialog set upload is running.");
        return false;
    }
    dialog_store_set_upload_active(true);
    i18n_sync_status_t st;
    i18n_sync_snapshot(&st);
    if (st.running) {
        dialog_store_set_upload_active(false);
        send_fail_code(req, "409 Conflict", "busy",
                       "The device is syncing its language and audio files — "
                       "try again in a minute.");
        return false;
    }
    return true;
}

// POST /api/dialog — body: a complete dialog set file (dialog.h), the
// user's own set (built by the editor in the web UI). Refused, with the
// reason, unless dialog_header_parse() accepts it (every phase 1..
// DIALOG_MAX_CLIPS clips, all within the file) and it fits the device's
// dialog budget. The header is checked before anything on flash changes;
// then the current set goes (one set fits) and the file is streamed in. It
// is stored as the set of DIALOG_OWN_LANG, which the daily sync never
// replaces and a ui_lang change keeps; leaving dialog mode deletes it.
static web_http_error_t handle_dialog_post(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    int total = web_http_body_length(req);
    uint64_t cap = dialog_set_cap();
    if (total <= DIALOG_HEADER_MIN) {
        char msg[96];
        snprintf(msg, sizeof(msg), "Not a dialog set: %d bytes, less than "
                 "a set header.", total < 0 ? 0 : total);
        pb_log_warn(TAG, "own dialog set refused: %s", msg);
        send_fail_code(req, "400 Bad Request", "not_a_set", msg);
        return WEB_HTTP_OK;
    }
    if ((uint64_t)total > cap) {
        char msg[96];
        snprintf(msg, sizeof(msg), "The set has %d bytes; this device takes "
                 "at most %llu.", total, (unsigned long long)cap);
        pb_log_warn(TAG, "own dialog set refused: %s", msg);
        send_fail_code(req, "413 Payload Too Large", "too_large", msg);
        return WEB_HTTP_OK;
    }
    if (!own_set_begin(req, "own dialog set upload")) return WEB_HTTP_OK;

    const char *fail = NULL;   // message on failure
    const char *fail_status = "400 Bad Request";
    const char *fail_code = "failed";
    bool touched = false;      // the previous set is gone
    size_t hn = (size_t)total < DIALOG_HEADER_MAX ? (size_t)total : DIALOG_HEADER_MAX;
    uint8_t *hdr = malloc(hn);
    dialog_set_t *set = malloc(sizeof(*set));
    char *buf = malloc(4096);
    char path[DIALOG_STORE_PATH_CAP], tmp[DIALOG_STORE_PATH_CAP + 8];
    dialog_store_file_path(path, sizeof(path), DIALOG_OWN_LANG);
    snprintf(tmp, sizeof(tmp), "%s.part", path);
    storage_file_t f;
    bool open = false;
    int got = 0;
    if (!hdr || !set || !buf) {
        fail = "Out of memory.";
        fail_status = "500 Internal Server Error";
        goto out;
    }
    while ((size_t)got < hn) {
        int n = upload_recv(req, (char *)hdr + got, (int)hn - got, "dialog set", got, total);
        if (n <= 0) { fail = "Upload interrupted."; goto out; }
        got += n;
    }
    if (dialog_header_parse(hdr, hn, (uint64_t)total, set) != DIALOG_HEADER_OK) {
        fail = "Not a valid dialog set: every phase needs 1 to 32 clips and "
               "the clip table must match the file.";
        fail_code = "not_a_set";
        goto out;
    }

    // One set fits on flash: the current one goes before the new one is
    // written.
    touched = true;
    dialog_store_drop_index();
    dialog_store_remove_others(NULL);
    storage_make_room((size_t)total, path);
    if (!storage_file_open(&f, tmp)) {
        fail = "Could not open the set file for writing.";
        fail_status = "500 Internal Server Error";
        goto out;
    }
    open = true;
    if (!storage_file_write(&f, hdr, hn)) {
        fail = "Storage write failed (full?).";
        fail_status = "500 Internal Server Error";
        goto out;
    }
    while (got < total) {
        int want = total - got;
        if (want > 4096) want = 4096;
        int n = upload_recv(req, buf, want, "dialog set", got, total);
        if (n <= 0) { fail = "Upload interrupted."; goto out; }
        if (!storage_file_write(&f, buf, (size_t)n)) {
            fail = "Storage write failed (full?).";
            fail_status = "500 Internal Server Error";
            goto out;
        }
        got += n;
        pb_watchdog_reset();
    }
    open = false;
    if (!storage_file_close(&f)) {
        fail = "Storage write failed (full?).";
        fail_status = "500 Internal Server Error";
        goto out;
    }
    unlink(path);
    if (rename(tmp, path) != 0) {
        fail = "Could not activate the set.";
        fail_status = "500 Internal Server Error";
        goto out;
    }
    if (!dialog_store_read_file(DIALOG_OWN_LANG, set) ||
        dialog_store_write_index(DIALOG_OWN_LANG, NULL) != PB_OK) {
        unlink(path);
        fail = "The stored set does not read back.";
        fail_status = "500 Internal Server Error";
        goto out;
    }
out:
    if (open) storage_file_close(&f);
    free(hdr);
    free(set);
    free(buf);
    if (fail) unlink(tmp);
    dialog_store_set_upload_active(false);
    if (fail) {
        pb_log_warn(TAG, "own dialog set upload failed after %d of %d bytes: %s",
                    got, total, fail);
        // The previous set is gone: the sync brings the speaker's set back.
        if (touched) i18n_sync_trigger_now();
        send_fail_code(req, fail_status, fail_code, fail);
        return WEB_HTTP_OK;
    }
    pb_log_info(TAG, "own dialog set stored: %d bytes", total);
    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject  (root, "ok", true);
    cJSON_AddNumberToObject(root, "bytes", total);
    cJSON_AddStringToObject(root, "message", "Own dialog set saved.");
    send_json(req, root);
    return WEB_HTTP_OK;
}

// POST /api/dialog/reset — drops the own set; the sync triggered at once
// fetches the chosen speaker's set.
static web_http_error_t handle_dialog_reset(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    if (!dialog_store_has_own()) {
        cJSON *root = cJSON_CreateObject();
        cJSON_AddBoolToObject  (root, "ok", true);
        cJSON_AddStringToObject(root, "message", "No own dialog set on the device.");
        send_json(req, root);
        return WEB_HTTP_OK;
    }
    if (!own_set_begin(req, "own dialog set reset")) return WEB_HTTP_OK;
    dialog_store_drop_index();
    dialog_store_remove_others(NULL);
    dialog_store_set_upload_active(false);
    i18n_sync_trigger_now();
    pb_log_info(TAG, "own dialog set deleted — fetching the speaker's set");
    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject  (root, "ok", true);
    cJSON_AddStringToObject(root, "message", "Own dialog set deleted.");
    send_json(req, root);
    return WEB_HTTP_OK;
}

static web_http_error_t handle_dev_i18n_upload(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);

    char query[96] = "";
    char kind[16]  = "";
    char lang[12]  = "";
    char speaker[DIALOG_SPEAKER_CAP + 8] = "";
    if (web_http_get_query_string(req, query, sizeof(query)) == WEB_HTTP_OK) {
        web_http_query_value(query, "kind", kind, sizeof(kind));
        web_http_query_value(query, "lang", lang, sizeof(lang));
        web_http_query_value(query, "speaker", speaker, sizeof(speaker));
    }
    bool is_ui     = strcmp(kind, "ui") == 0;
    bool is_dialog = strcmp(kind, "dialog") == 0;
    if (!is_ui && !is_dialog && strcmp(kind, "mail") != 0) {
        send_fail_code(req, "400 Bad Request", "invalid_kind",
                       "kind must be 'ui', 'mail' or 'dialog'.");
        return WEB_HTTP_OK;
    }
    // The locale becomes part of a file name, so accept only what a locale
    // code may contain — no '.', no '/', nothing that could escape the
    // directory or collide with another asset's name. The dialog kind takes
    // exactly what a stored set's locale may be (dialog_lang_valid()), since
    // the index must name it.
    size_t lang_len = strlen(lang);
    bool lang_ok = lang_len >= 2 && lang_len < sizeof(lang) - 1;
    for (size_t i = 0; lang_ok && i < lang_len; i++) {
        char c = lang[i];
        lang_ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
               || (c >= '0' && c <= '9') || c == '-';
    }
    if (is_dialog && !dialog_lang_valid(lang)) lang_ok = false;
    if (!lang_ok) {
        send_fail_code(req, "400 Bad Request", "invalid_lang",
                       "lang must be a locale code like 'de' or 'zh-Hans' "
                       "(lower case for the dialog kind).");
        return WEB_HTTP_OK;
    }
    if (is_dialog && speaker[0] && !dialog_speaker_valid(speaker)) {
        send_fail_code(req, "400 Bad Request", "invalid_speaker",
                       "speaker must be a speaker id like 'leonie' ([a-z][a-z0-9-]{0,15}).");
        return WEB_HTTP_OK;
    }
    if (is_dialog && !config_ab_dialog()) {
        pb_log_warn(TAG, "dev upload: %s refused — ab_mode is not 'dialog'", kind);
        send_fail_code(req, "409 Conflict", "wrong_mode",
                       "The dialog set can only be pushed in dialog mode "
                       "(ab_mode=dialog); the next sync would remove it otherwise.");
        return WEB_HTTP_OK;
    }

    int total = web_http_body_length(req);

    // Cap well above a real bundle (~30 KB for the UI pack) but far below
    // the shared 640 KB storage of the 4 MB boards, so a runaway upload cannot fill storage
    // that the announcement audio and the blocklist also live in. The
    // dialog set gets the device's dialog budget, as the CDN download it
    // stands in for (dialog_set_cap()).
    const int MAX_BYTES = is_dialog ? (int)dialog_set_cap() : 128 * 1024;
    if (total <= 2 || total > MAX_BYTES) {
        char msg[80];
        snprintf(msg, sizeof(msg), "Bundle missing or over %d bytes.", MAX_BYTES);
        pb_log_warn(TAG, "dev upload: %s refused — %d bytes, at most %d", kind,
                    total, MAX_BYTES);
        if (total > MAX_BYTES) send_fail_code(req, "413 Payload Too Large", "too_large", msg);
        else send_fail_code(req, "400 Bad Request", "invalid", msg);
        return WEB_HTTP_OK;
    }

    char path[DIALOG_STORE_PATH_CAP], tmp[DIALOG_STORE_PATH_CAP + 8];
    if (is_dialog) {
        dialog_store_file_path(path, sizeof(path), lang);
        // Never a half-replaced set that still counts as complete: without
        // the index the call path ignores the file until it is checked below.
        dialog_store_drop_index();
    } else if (!(is_ui ? i18n_sync_ui_path(path, sizeof(path), lang)
                       : i18n_sync_mail_path(path, sizeof(path), lang))) {
        pb_log_warn(TAG, "dev upload: %s refused — data directory path too long", kind);
        send_fail_code(req, "500 Internal Server Error", "path",
                       "The data directory path is too long.");
        return WEB_HTTP_OK;
    }
    snprintf(tmp, sizeof(tmp), "%s.part", path);

    // Streamed through a temp file and renamed, like i18n_sync's own
    // downloads: an interrupted upload then leaves the previous bundle
    // intact instead of a half-written one the parser would choke on. Same
    // write discipline as those downloads, too: precedence over the
    // community cache and the shared write buffer (storage.h).
    storage_make_room((size_t)total, path);
    storage_file_t f;
    if (!storage_file_open(&f, tmp)) {
        send_fail_code(req, "500 Internal Server Error", "storage",
                       "Could not open the bundle file for writing.");
        return WEB_HTTP_OK;
    }
    char *buf = malloc(1024);
    if (!buf) {
        storage_file_close(&f);
        remove(tmp);
        send_fail_code(req, "500 Internal Server Error", "oom", "Out of memory.");
        return WEB_HTTP_OK;
    }
    int  got   = 0;
    bool wrote = true;
    char first = '\0';
    while (got < total && wrote) {
        int want = total - got;
        if (want > 1024) want = 1024;
        int n = upload_recv(req, buf, want, "i18n bundle", got, total);
        if (n <= 0) { wrote = false; break; }
        if (got == 0) first = buf[0];
        if (!storage_file_write(&f, buf, (size_t)n)) wrote = false;
        got += n;
        pb_watchdog_reset();
    }
    free(buf);
    if (!storage_file_close(&f)) wrote = false;

    // Only the cheapest sanity check: a bundle that does not even start as
    // a JSON object is a mistake worth catching here (a wrong file, or an
    // error page), while full validation belongs to the consumer. The
    // dialog set's check is its header, once the file is in place.
    if (wrote && !is_dialog && first != '{') wrote = false;
    if (!wrote) {
        remove(tmp);
        send_fail_code(req, "400 Bad Request", "invalid",
                       is_dialog ? "Upload interrupted."
                                 : "Upload interrupted or not a JSON object.");
        return WEB_HTTP_OK;
    }
    remove(path);
    if (rename(tmp, path) != 0) {
        remove(tmp);
        send_fail_code(req, "500 Internal Server Error", "storage",
                       "Could not activate the uploaded bundle.");
        return WEB_HTTP_OK;
    }
    if (is_dialog) {
        // The call path's own check of the clip table, then the index that
        // makes the set usable. A bad file stays unused (no index); the next
        // sync replaces it.
        dialog_set_t *set = malloc(sizeof(*set));
        bool valid = set && dialog_store_read_file(lang, set);
        free(set);
        if (!valid) {
            send_fail_code(req, "400 Bad Request", "invalid",
                           "Not a dialog clip set: missing or malformed clip "
                           "table header (push a staged dialog-<lang>-<tier>-<sha8>.bin).");
            return WEB_HTTP_OK;
        }
        // One set on the device: an own set or another locale's goes.
        dialog_store_remove_others(lang);
        if (dialog_store_write_index(lang, speaker) != PB_OK) {
            send_fail_code(req, "500 Internal Server Error", "storage",
                           "Could not write the dialog index.");
            return WEB_HTTP_OK;
        }
    }
    pb_log_warn(TAG, "dev upload: %s bundle for '%s' replaced by upload (%d bytes)",
             kind, lang, got);

    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject  (root, "ok", true);
    cJSON_AddStringToObject(root, "path", path);
    cJSON_AddNumberToObject(root, "bytes", got);
    send_json(req, root);
    return WEB_HTTP_OK;
}

// POST /api/token-test
// Runs phoneblock_selftest() against GET /test and reports whether the
// currently stored token is still accepted by the server. Always
// returns HTTP 200; `ok` in the JSON body tells the UI the outcome.
static web_http_error_t handle_token_test(web_http_request_t *req)
{
    REQUIRE_AUTH_API(req);
    // Response shape: { ok: bool, code: "ok"|"fail"|"none", message: ... }
    // `code` is a stable discriminator the UI localises via i18n; the
    // `message` is the English default, only shown if the UI cannot
    // map the code.
    cJSON *root = cJSON_CreateObject();
    if (strlen(config_phoneblock_token()) == 0) {
        cJSON_AddBoolToObject  (root, "ok", false);
        cJSON_AddStringToObject(root, "code", "none");
        cJSON_AddStringToObject(root, "message", "No token configured.");
        send_json(req, root);
        return WEB_HTTP_OK;
    }
    bool ok = phoneblock_selftest(NULL);
    cJSON_AddBoolToObject  (root, "ok", ok);
    cJSON_AddStringToObject(root, "code", ok ? "ok" : "fail");
    cJSON_AddStringToObject(root, "message",
        ok ? "Token valid — PhoneBlock reachable."
           : "Token was rejected. Please request a new one.");
    send_json(req, root);
    return WEB_HTTP_OK;
}

// --- Server lifecycle -----------------------------------------------

// The Linux HTTP layer refused a request body for its size before reading
// it (web_http.h); the client got a JSON 413, this puts it on the log panel.
static void log_body_refused(const char *uri, unsigned long long length, size_t max)
{
    pb_log_warn(TAG, "%s: request body of %llu bytes refused (at most %zu)",
                uri, length, max);
}

static const web_http_route_t URIS[] = {
    { .uri = "/health",               .method = WEB_HTTP_GET,     .handler = handle_health },
    { .uri = "/",                    .method = WEB_HTTP_GET,     .handler = handle_root },
    { .uri = "/favicon.ico",         .method = WEB_HTTP_GET,     .handler = handle_favicon },
    { .uri = "/ab-logo-bot.svg",     .method = WEB_HTTP_GET,     .handler = handle_favicon },
    { .uri = "/api/status",          .method = WEB_HTTP_GET,     .handler = handle_status },
    { .uri = "/api/calls",           .method = WEB_HTTP_GET,     .handler = handle_calls },
    { .uri = "/api/errors",          .method = WEB_HTTP_GET,     .handler = handle_errors },
#ifdef ESP_PLATFORM
    { .uri = "/api/log/stream",      .method = WEB_HTTP_GET,     .handler = handle_log_stream },
#endif
    { .uri = "/api/errors/clear",    .method = WEB_HTTP_POST,    .handler = handle_errors_clear },
    { .uri = "/api/calls/clear",     .method = WEB_HTTP_POST,    .handler = handle_calls_clear },
    { .uri = "/api/factory-reset",   .method = WEB_HTTP_POST,    .handler = handle_factory_reset },
#ifdef CONFIG_CRASH_TEST_ENABLE
    { .uri = "/api/dev/crash",       .method = WEB_HTTP_POST,    .handler = handle_crash_test },
    { .uri = "/api/dev/heap-smash",  .method = WEB_HTTP_POST,    .handler = handle_heap_smash },
#endif
    { .uri = "/api/firmware",           .method = WEB_HTTP_POST, .handler = handle_firmware_upload },
    { .uri = "/api/firmware/check",     .method = WEB_HTTP_POST, .handler = handle_firmware_check },
    { .uri = "/api/firmware/install",   .method = WEB_HTTP_POST, .handler = handle_firmware_install },
    { .uri = "/api/i18n/ui",            .method = WEB_HTTP_GET,  .handler = handle_i18n_ui },
    { .uri = "/api/announcement",       .method = WEB_HTTP_GET,  .handler = handle_announcement_get },
    { .uri = "/api/announcement",       .method = WEB_HTTP_POST, .handler = handle_announcement_post },
    { .uri = "/api/announcement/reset", .method = WEB_HTTP_POST, .handler = handle_announcement_reset },
    { .uri = "/api/dialog",             .method = WEB_HTTP_GET,  .handler = handle_dialog_get },
    // The routes taking a whole dialog set (Linux: buffered up to max_body;
    // the handlers hold it to dialog_set_cap(), the ESP32 streams).
    { .uri = "/api/dialog",             .method = WEB_HTTP_POST, .handler = handle_dialog_post,
      .max_body = STORAGE_DIALOG_PLAIN_FS_MAX },
    { .uri = "/api/dialog/reset",       .method = WEB_HTTP_POST, .handler = handle_dialog_reset },
    { .uri = "/api/dialog/speakers",    .method = WEB_HTTP_GET,  .handler = handle_dialog_speakers },
    { .uri = "/api/sync/run",           .method = WEB_HTTP_POST, .handler = handle_sync_run },
    { .uri = "/api/phonebooks",         .method = WEB_HTTP_GET,  .handler = handle_phonebooks },
    { .uri = "/api/dev/i18n",           .method = WEB_HTTP_POST, .handler = handle_dev_i18n_upload,
      .max_body = STORAGE_DIALOG_PLAIN_FS_MAX },
    { .uri = "/api/phonebook/add",      .method = WEB_HTTP_POST, .handler = handle_phonebook_add },
    { .uri = "/api/rate",               .method = WEB_HTTP_POST, .handler = handle_rate },
    { .uri = "/api/whitelist",          .method = WEB_HTTP_POST, .handler = handle_whitelist },
    { .uri = "/api/blocklist-sync/run", .method = WEB_HTTP_POST, .handler = handle_blocklist_sync_run },
    { .uri = "/api/mail/test",          .method = WEB_HTTP_POST, .handler = handle_mail_test },
    { .uri = "/api/config",             .method = WEB_HTTP_POST, .handler = handle_config_post },
    { .uri = "/api/fritzbox-setup",     .method = WEB_HTTP_POST, .handler = handle_fritzbox_setup },
    { .uri = "/api/fritzbox-2fa-status",.method = WEB_HTTP_GET,  .handler = handle_fritzbox_2fa_status },
    { .uri = "/api/token-test",         .method = WEB_HTTP_POST, .handler = handle_token_test },
    { .uri = "/register-start",         .method = WEB_HTTP_GET,  .handler = handle_register_start },
    { .uri = "/token-callback",         .method = WEB_HTTP_GET,  .handler = handle_token_callback },
#ifndef ESP_PLATFORM
    { .uri = "/api/link/start",         .method = WEB_HTTP_POST, .handler = handle_link_start },
    { .uri = "/api/link/status",        .method = WEB_HTTP_GET,  .handler = handle_link_status },
#endif
    { .uri = "/auth/start",             .method = WEB_HTTP_GET,  .handler = web_auth_handle_start },
    { .uri = "/auth/login-link",        .method = WEB_HTTP_GET,  .handler = web_auth_handle_login_link },
    { .uri = "/auth/callback",          .method = WEB_HTTP_GET,  .handler = web_auth_handle_callback },
    { .uri = "/auth/logout",            .method = WEB_HTTP_POST, .handler = web_auth_handle_logout },
    { .uri = "/auth/disable",           .method = WEB_HTTP_POST, .handler = web_auth_handle_disable },
    // Catch-all OPTIONS handler for the PNA preflight Chrome/Edge sends
    // before navigating from https://phoneblock.net to this dongle's
    // LAN IP. Method-scoped to OPTIONS so it doesn't shadow any of the
    // GET/POST routes above.
    { .uri = "/*",                       .method = WEB_HTTP_OPTIONS, .handler = handle_pna_preflight },
};

#ifdef ESP_PLATFORM
// Task-watchdog feeder for the HTTPD worker. ESP-IDF's esp_http_server
// hides its internal task handle, so we can't call esp_task_wdt_add()
// on it directly from outside. Instead we inject a work item through
// httpd_queue_work() — those callbacks run on the HTTPD task itself,
// which is exactly where we need the subscribe and the periodic reset
// to happen. A handler that wedges (long TR-064 call, blocked TLS
// read, infinite loop) stalls the work queue → reset stops happening
// → CONFIG_ESP_TASK_WDT_TIMEOUT_S elapses → panic + coredump,
// instead of a silently dead web UI.

static esp_timer_handle_t s_httpd_wdt_feeder;

static void httpd_wdt_subscribe_work(void *arg)
{
    (void)arg;
    if (esp_task_wdt_add(NULL) == ESP_OK) {
        pb_log_info(TAG, "HTTPD worker subscribed to task watchdog");
    } else {
        pb_log_warn(TAG, "HTTPD worker task-watchdog subscribe failed");
    }
}

static void httpd_wdt_reset_work(void *arg)
{
    (void)arg;
    esp_task_wdt_reset();
}

static void httpd_wdt_feeder_cb(void *arg)
{
    (void)arg;
    if (s_server) {
        // Best effort. If the queue is full because a handler is
        // already wedged, that's exactly the case where we *want*
        // the watchdog to fire — so a failed enqueue is harmless.
        web_http_server_queue_work(s_server, httpd_wdt_reset_work, NULL);
    }
}
#endif

void web_start(void)
{
    if (s_server) {
        pb_log_warn(TAG, "web_start() called twice — ignoring");
        return;
    }
    generate_reset_nonce();
    web_auth_setup();
#ifndef ESP_PLATFORM
    link_init();
#endif
    web_http_server_config_t cfg = {
        .port = s_web_port,
        .bind_address = s_web_bind_address[0] ? s_web_bind_address : NULL,
        .max_uri_handlers = sizeof(URIS) / sizeof(URIS[0]) + 2,
        // Enables glob matching so the catch-all "/*" OPTIONS preflight
        // entry covers every URI. All other routes use literal paths, so
        // wildcard matching has no effect on them.
        .wildcard_uri_match = true,
        .lru_purge = true,
        .on_body_refused = log_body_refused,
    };
#ifdef ESP_PLATFORM
    // The default 4 KB is too tight once /api/fritzbox-setup → tr064_*
    // nests through call_action → post_soap → esp_http_client. 8 KB
    // gives us headroom for the deep call chain without overflows.
    // ESP32-S3: after the SIP setup through TR-064 only 488 B were left
    // (ESP32-C3: 948 B), the Xtensa register windows make the same calls
    // deeper. The S3 has the heap to spare (~150 KB free), so +2 KB there.
#if CONFIG_IDF_TARGET_ESP32S3
    cfg.worker_stack_size = 10240;
#else
    cfg.worker_stack_size = 8192;
#endif
    // Reclaim the least-recently-used connection when every socket slot
    // is taken, instead of refusing the new one. Browsers keep HTTP/1.1
    // keep-alive sockets open, and a device that simply went away (laptop
    // lid closed, phone left the WLAN) never sends a FIN — its socket
    // lingers. Once max_open_sockets (7) is exhausted by such stale
    // connections the server stops accepting new ones: the UI becomes
    // unreachable from fresh devices while a device that already holds a
    // keep-alive socket keeps polling fine. With LRU purge the oldest
    // idle socket is closed to admit each new client, so the UI stays
    // reachable; an evicted browser transparently reconnects on its next
    // 3 s poll. See issue #328.
    if (web_http_server_start(&s_server, &cfg, URIS,
                              sizeof(URIS) / sizeof(URIS[0])) != WEB_HTTP_OK) {
        pb_log_err(TAG, "httpd_start failed");
        return;
    }
    pb_log_info(TAG, "HTTP server listening on :80");

    // Subscribe the HTTPD worker to the watchdog (runs on its task)
    // and start the periodic reset feeder. Period = TIMEOUT/4 so a
    // single missed feed still leaves three quarters of the budget
    // before the panic fires.
    web_http_server_queue_work(s_server, httpd_wdt_subscribe_work, NULL);
    const esp_timer_create_args_t timer_args = {
        .callback = httpd_wdt_feeder_cb,
        .name     = "httpd_wdt",
    };
    if (esp_timer_create(&timer_args, &s_httpd_wdt_feeder) == ESP_OK) {
        uint64_t period_us =
            (uint64_t)CONFIG_ESP_TASK_WDT_TIMEOUT_S * 1000000ULL / 4ULL;
        esp_timer_start_periodic(s_httpd_wdt_feeder, period_us);
    } else {
        pb_log_warn(TAG, "HTTPD WDT feeder timer create failed");
    }
#else
    if (web_http_server_start(&s_server, &cfg, URIS,
                              sizeof(URIS) / sizeof(URIS[0])) != WEB_HTTP_OK) {
        pb_log_err(TAG, "HTTP server start failed");
        return;
    }
    pb_log_info(TAG, "HTTP server listening on :%u", cfg.port);
#endif
}

void web_configure_listener(uint16_t port, const char *bind_address)
{
    if (s_server) return;
    s_web_port = port;
    if (!bind_address) {
        s_web_bind_address[0] = '\0';
        return;
    }
    snprintf(s_web_bind_address, sizeof(s_web_bind_address), "%s", bind_address);
}

uint16_t web_listening_port(void)
{
    return web_http_server_port(s_server);
}

void web_stop(void)
{
    if (!s_server) return;
    web_http_server_stop(s_server);
    s_server = NULL;
}
