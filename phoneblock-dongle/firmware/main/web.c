#include "web.h"

#include <stdio.h>
#include <string.h>

#include "esp_log.h"
#include "esp_random.h"
#include "esp_timer.h"
#include "esp_app_desc.h"
#include "esp_netif.h"
#include "esp_http_server.h"
#include "esp_ota_ops.h"
#include "esp_partition.h"
#include "esp_system.h"       // esp_restart
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "lwip/sockets.h"     // INET_ADDRSTRLEN
#include "cJSON.h"

#include "announcement.h"
#include "api.h"
#include "config.h"
#include "sip_register.h"
#include "stats.h"
#include "sync.h"
#include "tr064.h"

static const char *TAG = "web";

static httpd_handle_t s_server = NULL;

// In-flight OAuth provisioning state: a random nonce set by
// /register-start, consumed once by /token-callback to defeat CSRF.
// Single entry — only one browser tab can provision at a time, which
// matches the "user sets up the dongle once" UX.
static char s_oauth_nonce[33] = "";

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

// Embedded via EMBED_FILES in CMakeLists.txt; the linker emits
// _binary_<file>_<ext>_{start,end} symbols which we alias to friendlier
// C names.
extern const uint8_t index_html_start[] asm("_binary_index_html_start");
extern const uint8_t index_html_end[]   asm("_binary_index_html_end");

extern const uint8_t ab_logo_bot_svg_start[] asm("_binary_ab_logo_bot_svg_start");
extern const uint8_t ab_logo_bot_svg_end[]   asm("_binary_ab_logo_bot_svg_end");

// --- Helpers --------------------------------------------------------

static void send_json(httpd_req_t *req, cJSON *root)
{
    char *body = cJSON_PrintUnformatted(root);
    httpd_resp_set_type(req, "application/json; charset=utf-8");
    httpd_resp_set_hdr(req, "Cache-Control", "no-store");
    if (body) {
        httpd_resp_send(req, body, HTTPD_RESP_USE_STRLEN);
        cJSON_free(body);
    } else {
        httpd_resp_send(req, "{}", 2);
    }
    cJSON_Delete(root);
}

static const char *verdict_string(verdict_t v)
{
    switch (v) {
        case VERDICT_SPAM:       return "SPAM";
        case VERDICT_LEGITIMATE: return "LEGITIMATE";
        default:                 return "ERROR";
    }
}

static void local_ip_str(char *out, size_t cap)
{
    out[0] = '\0';
    esp_netif_t *netif = esp_netif_get_default_netif();
    if (!netif) return;
    esp_netif_ip_info_t info;
    if (esp_netif_get_ip_info(netif, &info) != ESP_OK) return;
    esp_ip4addr_ntoa(&info.ip, out, cap);
}

// --- Handlers -------------------------------------------------------

static esp_err_t handle_favicon(httpd_req_t *req)
{
    httpd_resp_set_type(req, "image/svg+xml");
    httpd_resp_set_hdr(req, "Cache-Control", "public, max-age=31536000, immutable");
    return httpd_resp_send(req, (const char *)ab_logo_bot_svg_start,
                           ab_logo_bot_svg_end - ab_logo_bot_svg_start);
}

static esp_err_t handle_root(httpd_req_t *req)
{
    httpd_resp_set_type(req, "text/html; charset=utf-8");
    httpd_resp_send(req, (const char *)index_html_start,
                    index_html_end - index_html_start);
    return ESP_OK;
}

static esp_err_t handle_status(httpd_req_t *req)
{
    stats_counters_t c;
    stats_snapshot_counters(&c);

    int64_t now_us   = esp_timer_get_time();
    int64_t uptime_s = now_us / 1000000;
    int64_t registered_for_s =
        c.sip_registered ? (now_us - c.sip_registered_since_us) / 1000000 : 0;

    const esp_app_desc_t *app = esp_app_get_description();

    char ip[INET_ADDRSTRLEN] = "";
    local_ip_str(ip, sizeof(ip));

    cJSON *root = cJSON_CreateObject();
    cJSON_AddStringToObject(root, "firmware_version", app ? app->version : "");
    cJSON_AddStringToObject(root, "project_name",     app ? app->project_name : "");
    cJSON_AddNumberToObject(root, "uptime_s",         (double)uptime_s);
    cJSON_AddStringToObject(root, "ip_address",       ip);

    cJSON *sip = cJSON_AddObjectToObject(root, "sip");
    cJSON_AddBoolToObject  (sip,  "registered",        c.sip_registered);
    cJSON_AddNumberToObject(sip,  "registered_for_s",  (double)registered_for_s);
    cJSON_AddStringToObject(sip,  "host",              config_sip_host());
    cJSON_AddNumberToObject(sip,  "port",              config_sip_port());
    cJSON_AddStringToObject(sip,  "user",              config_sip_user());
    cJSON_AddStringToObject(sip,  "internal_number",   config_sip_internal_number());
    cJSON_AddStringToObject(sip,  "transport",         config_sip_transport());
    cJSON_AddStringToObject(sip,  "auth_user",         config_sip_auth_user());
    cJSON_AddStringToObject(sip,  "outbound",          config_sip_outbound());
    cJSON_AddStringToObject(sip,  "realm",             config_sip_realm());
    cJSON_AddStringToObject(sip,  "srtp",              config_sip_srtp());

    cJSON *pb = cJSON_AddObjectToObject(root, "phoneblock");
    cJSON_AddStringToObject(pb,   "base_url",           config_phoneblock_base_url());
    cJSON_AddBoolToObject  (pb,   "token_set",          strlen(config_phoneblock_token()) > 0);
    cJSON_AddNumberToObject(pb,   "last_api_ms",        (double)(c.last_api_duration_us / 1000));

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
    cJSON_AddStringToObject(syn, "last_error", ss.last_error);

    cJSON *ann = cJSON_AddObjectToObject(root, "announcement");
    cJSON_AddBoolToObject  (ann,  "custom",  announcement_is_custom());
    cJSON_AddNumberToObject(ann,  "bytes",   (double)announcement_length());
    cJSON_AddNumberToObject(ann,  "max_bytes", (double)ANNOUNCEMENT_MAX_BYTES);

    cJSON *cnt = cJSON_AddObjectToObject(root, "counters");
    cJSON_AddNumberToObject(cnt,  "total",        c.total_calls);
    cJSON_AddNumberToObject(cnt,  "spam_blocked", c.spam_blocked);
    cJSON_AddNumberToObject(cnt,  "legitimate",   c.legitimate);
    cJSON_AddNumberToObject(cnt,  "errors",       c.errors);

    send_json(req, root);
    return ESP_OK;
}

static esp_err_t handle_calls(httpd_req_t *req)
{
    stats_call_t calls[STATS_MAX_CALLS];
    int n = stats_snapshot_calls(calls, STATS_MAX_CALLS);
    int64_t now_us = esp_timer_get_time();

    cJSON *root = cJSON_CreateObject();
    cJSON *arr  = cJSON_AddArrayToObject(root, "calls");
    for (int i = 0; i < n; i++) {
        cJSON *o = cJSON_CreateObject();
        cJSON_AddNumberToObject(o, "age_s",   (double)((now_us - calls[i].at_us) / 1000000));
        cJSON_AddStringToObject(o, "number",  calls[i].number);
        cJSON_AddStringToObject(o, "display", calls[i].display);
        cJSON_AddStringToObject(o, "verdict", verdict_string(calls[i].verdict));
        cJSON_AddItemToArray(arr, o);
    }
    send_json(req, root);
    return ESP_OK;
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
static void url_decode(const char *src, int src_len, char *dst, size_t cap)
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

static esp_err_t handle_config_post(httpd_req_t *req)
{
    int total = req->content_len;
    if (total <= 0 || total > 1024) {
        httpd_resp_send_err(req, HTTPD_400_BAD_REQUEST, "body missing or too large");
        return ESP_OK;
    }
    char *body = malloc(total + 1);
    if (!body) {
        httpd_resp_send_err(req, HTTPD_500_INTERNAL_SERVER_ERROR, "oom");
        return ESP_OK;
    }
    int got = 0;
    while (got < total) {
        int n = httpd_req_recv(req, body + got, total - got);
        if (n <= 0) {
            free(body);
            httpd_resp_send_err(req, HTTPD_500_INTERNAL_SERVER_ERROR, "recv");
            return ESP_OK;
        }
        got += n;
    }
    body[got] = '\0';

    char sip_host[64], sip_user[32], sip_pass[64];
    char pb_url[128],  pb_token[64];
    char sip_port_s[8] = "", sip_exp_s[8] = "";
    char sip_transp[8]    = "";
    char sip_authuser[32] = "";
    char sip_outbound[80] = "";
    char sip_realm[64]    = "";
    char sip_srtp[16]     = "";
    char sync_en_s[4]     = "";

    bool have_sip_host  = form_get(body, "sip_host",  sip_host,  sizeof(sip_host));
    bool have_sip_user  = form_get(body, "sip_user",  sip_user,  sizeof(sip_user));
    bool have_sip_pass  = form_get(body, "sip_pass",  sip_pass,  sizeof(sip_pass));
    bool have_sip_port  = form_get(body, "sip_port",  sip_port_s, sizeof(sip_port_s));
    bool have_sip_exp   = form_get(body, "sip_expires", sip_exp_s, sizeof(sip_exp_s));
    bool have_sip_trans = form_get(body, "sip_transport", sip_transp, sizeof(sip_transp));
    bool have_sip_auth  = form_get(body, "sip_auth_user", sip_authuser, sizeof(sip_authuser));
    bool have_sip_out   = form_get(body, "sip_outbound",  sip_outbound, sizeof(sip_outbound));
    bool have_sip_realm = form_get(body, "sip_realm",     sip_realm,    sizeof(sip_realm));
    bool have_sip_srtp  = form_get(body, "sip_srtp",      sip_srtp,     sizeof(sip_srtp));
    bool have_sync_en   = form_get(body, "sync_enabled",  sync_en_s,    sizeof(sync_en_s));
    bool have_pb_url    = form_get(body, "pb_url",    pb_url,    sizeof(pb_url));
    bool have_pb_token  = form_get(body, "pb_token",  pb_token,  sizeof(pb_token));
    free(body);

    const char *new_host = have_sip_host && sip_host[0] ? sip_host : NULL;
    // A changed registrar (provider / manual edit moving to a different
    // box) invalidates the Fritz!Box-supplied extension number — blank
    // it out explicitly so the dashboard does not keep showing it.
    const char *clear_int_num = NULL;
    if (new_host && strcmp(new_host, config_sip_host()) != 0) {
        clear_int_num = "";
    }

    config_update_t u = {
        .sip_host   = new_host,
        .sip_port   = have_sip_port && sip_port_s[0] ? atoi(sip_port_s) : 0,
        .sip_user   = have_sip_user && sip_user[0]  ? sip_user  : NULL,
        // Empty password submitted = keep existing (so user doesn't have to
        // re-type it when editing other fields).
        .sip_pass   = have_sip_pass && sip_pass[0]  ? sip_pass  : NULL,
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
        .sync_enabled  = have_sync_en   ? sync_en_s    : NULL,
        .phoneblock_base_url = have_pb_url   && pb_url[0]   ? pb_url   : NULL,
        .phoneblock_token    = have_pb_token && pb_token[0] ? pb_token : NULL,
    };

    esp_err_t err = config_update(&u);
    if (err != ESP_OK) {
        httpd_resp_send_err(req, HTTPD_500_INTERNAL_SERVER_ERROR, "nvs write failed");
        return ESP_OK;
    }

    // Signal the SIP task to re-register with the new credentials.
    sip_register_request_reload();

    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject(root, "ok", true);
    cJSON_AddStringToObject(root, "message", "Saved, re-registering.");
    send_json(req, root);
    return ESP_OK;
}

// Receive up to `cap` bytes from the request body into `buf`.
// Returns bytes read, or a negative error code. NUL-terminates.
static int recv_body(httpd_req_t *req, char *buf, int cap)
{
    int total = req->content_len;
    if (total <= 0 || total > cap - 1) return -1;
    int got = 0;
    while (got < total) {
        int n = httpd_req_recv(req, buf + got, total - got);
        if (n <= 0) return -1;
        got += n;
    }
    buf[got] = '\0';
    return got;
}

static void send_fail(httpd_req_t *req, const char *message)
{
    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject(root, "ok", false);
    cJSON_AddStringToObject(root, "message", message);
    httpd_resp_set_status(req, "400 Bad Request");
    send_json(req, root);
}

// POST /api/fritzbox-setup
// Body (URL-encoded): fritz_host=…&fritz_user=…&fritz_pass=…&phone_name=…
// Runs tr064_provision_sip_client; on success, stores the generated
// SIP credentials (plus the registrar host) via config_update() and
// triggers sip_register_request_reload() so the dongle registers
// with the new identity right away.
static esp_err_t handle_fritzbox_setup(httpd_req_t *req)
{
    char body[512];
    if (recv_body(req, body, sizeof(body)) < 0) {
        send_fail(req, "Body missing or too large.");
        return ESP_OK;
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
    if (!phone_name[0]) strncpy(phone_name, "Answerbot", sizeof(phone_name) - 1);

    // Username is optional — mirror the Fritz!Box web UI that only
    // asks for a password and falls back to the default (last-used)
    // account. Probe LANConfigSecurity:X_AVM-DE_GetUserList for the
    // preferred name before running the provisioning flow.
    if (!fritz_user[0] && fritz_pass[0]) {
        int code = 0;
        char detail[128] = "";
        esp_err_t lerr = tr064_get_default_username(
            fritz_host, 49000, "", fritz_pass,
            fritz_user, sizeof(fritz_user),
            &code, detail, sizeof(detail));
        if (lerr != ESP_OK || !fritz_user[0]) {
            char msg[240];
            snprintf(msg, sizeof(msg),
                "Could not auto-detect the Fritz!Box username (%s). "
                "Please enter it explicitly.",
                detail[0] ? detail : "no details");
            send_fail(req, msg);
            return ESP_OK;
        }
    }
    if (!fritz_user[0]) strncpy(fritz_user, "admin", sizeof(fritz_user) - 1);
    if (!fritz_pass[0]) {
        send_fail(req, "Fritz!Box password missing.");
        return ESP_OK;
    }

    tr064_sip_result_t res;
    memset(&res, 0, sizeof(res));
    esp_err_t err = tr064_provision_sip_client(
        fritz_host, 49000, fritz_user, fritz_pass, phone_name, NULL, &res);
    if (err != ESP_OK && res.error_code == 866) {
        // Fritz!Box requires 2FA — start the handshake, stash the
        // credentials + phone name for the retry, and tell the web UI
        // to show the "press a button" instructions + start polling.
        memset(&s_2fa, 0, sizeof(s_2fa));
        esp_err_t aerr = tr064_auth_start(fritz_host, 49000,
            fritz_user, fritz_pass,
            s_2fa.token,   sizeof(s_2fa.token),
            s_2fa.state,   sizeof(s_2fa.state),
            s_2fa.methods, sizeof(s_2fa.methods));
        if (aerr != ESP_OK || !s_2fa.token[0]) {
            send_fail(req, "2FA start failed (X_AVM-DE_Auth unreachable?)");
            return ESP_OK;
        }
        s_2fa.active      = true;
        s_2fa.started_us  = esp_timer_get_time();
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
        return ESP_OK;
    }
    if (err != ESP_OK) {
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
        return ESP_OK;
    }

    // Best-effort register a dedicated app instance on the box. Gives
    // the sync task its own Phone-rights-only credentials so we don't
    // have to persist the admin password. Failure (e.g. older Fritz!OS
    // without AppSetup, or rate-limit) is logged but does not fail the
    // whole setup — SIP still works without it, the sync feature just
    // stays disabled.
    char app_user[32] = "";
    char app_pass[40] = "";
    int  app_err_code = 0;
    char app_err_msg[128] = "";
    esp_err_t app_err = tr064_register_dongle_app(
        fritz_host, 49000, fritz_user, fritz_pass, NULL,
        app_user, sizeof(app_user),
        app_pass, sizeof(app_pass),
        &app_err_code, app_err_msg, sizeof(app_err_msg));
    if (app_err != ESP_OK) {
        ESP_LOGW(TAG,
            "RegisterApp failed (code %d, %s) — sync feature disabled",
            app_err_code, app_err_msg);
        app_user[0] = '\0';
        app_pass[0] = '\0';
    }

    // Commit generated SIP credentials + registrar + (optional) app
    // credentials to NVS.
    config_update_t u = {
        .sip_host = fritz_host,
        .sip_port = 5060,
        .sip_user = res.sip_user,
        .sip_pass = res.sip_pass,
        .sip_internal_number = res.internal_number,
        .fritzbox_app_user = app_user,
        .fritzbox_app_pass = app_pass,
    };
    if (config_update(&u) != ESP_OK) {
        send_fail(req, "NVS write failed.");
        return ESP_OK;
    }
    sip_register_request_reload();

    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject(root, "ok", true);
    cJSON_AddStringToObject(root, "message",
        "Extension created. The dongle is registering now — "
        "see the status bar above for the current state.");
    cJSON_AddStringToObject(root, "sip_user", res.sip_user);
    cJSON_AddStringToObject(root, "internal_number", res.internal_number);
    cJSON_AddBoolToObject  (root, "app_registered", app_user[0] != '\0');
    send_json(req, root);
    return ESP_OK;
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
static esp_err_t handle_register_start(httpd_req_t *req)
{
    // Random 32-hex-char nonce.
    static const char hex[] = "0123456789abcdef";
    for (int i = 0; i < 32; i++) {
        s_oauth_nonce[i] = hex[esp_random() & 0x0f];
    }
    s_oauth_nonce[32] = '\0';

    // Callback URL uses whatever Host the browser reached us under
    // (answerbot / fritz.box-local-name / raw IP) so phoneblock.net
    // redirects back to the same origin the user came from.
    char host[64] = "answerbot";
    httpd_req_get_hdr_value_str(req, "Host", host, sizeof(host));

    char callback_plain[128];
    snprintf(callback_plain, sizeof(callback_plain),
             "http://%s/token-callback", host);

    char callback_enc[256];
    url_encode(callback_plain, callback_enc, sizeof(callback_enc));

    // Reuse the existing mobile-login flow on phoneblock.net via its
    // /mobile/login endpoint. `appId=PhoneBlockDongle` switches the
    // /create-token servlet into the dynamic-callback branch, where it
    // echoes our CSRF state back and redirects to our callback URL.
    // Web context path is at the site root for *.phoneblock.net/pb or
    // the main deployment; config_phoneblock_base_url() points at the
    // /api slice, so strip that suffix.
    char base_site[128];
    strncpy(base_site, config_phoneblock_base_url(), sizeof(base_site) - 1);
    base_site[sizeof(base_site) - 1] = '\0';
    char *api_slash = strstr(base_site, "/api");
    if (api_slash) *api_slash = '\0';

    char url[512];
    snprintf(url, sizeof(url),
             "%s/mobile/login?appId=PhoneBlockDongle&tokenLabel=%s"
             "&callback=%s&state=%s",
             base_site, "PhoneBlock-Dongle",
             callback_enc, s_oauth_nonce);

    ESP_LOGI(TAG, "register-start → redirect to %s", url);
    httpd_resp_set_status(req, "302 Found");
    httpd_resp_set_hdr(req, "Location", url);
    httpd_resp_send(req, NULL, 0);
    return ESP_OK;
}

// GET /token-callback?token=…&state=<nonce>
// phoneblock.net redirects the user's browser here after successful
// login. We verify the CSRF nonce, commit the token to NVS, and show
// the user a friendly success page that links back to the main UI.
static esp_err_t handle_token_callback(httpd_req_t *req)
{
    char query[512];
    if (httpd_req_get_url_query_str(req, query, sizeof(query)) != ESP_OK) {
        httpd_resp_set_type(req, "text/html; charset=utf-8");
        httpd_resp_send(req,
            "<p>Missing query parameters. "
            "<a href=\"/\">Back to configuration</a>.</p>",
            HTTPD_RESP_USE_STRLEN);
        return ESP_OK;
    }
    char token_raw[128] = "";
    char token[128]     = "";
    char state_raw[64]  = "";
    char state[64]      = "";
    // Server's /create-token uses `loginToken` as the parameter name;
    // accept `token` too for robustness if we ever change the server.
    if (httpd_query_key_value(query, "loginToken", token_raw, sizeof(token_raw)) != ESP_OK) {
        httpd_query_key_value(query, "token", token_raw, sizeof(token_raw));
    }
    httpd_query_key_value(query, "state", state_raw, sizeof(state_raw));
    // httpd_query_key_value does *not* unescape %XX sequences. A token
    // containing e.g. '+' or '/' arrives here as "%2B"/"%2F" and, if
    // stored raw, the server never recognises it — length mismatch
    // (35 vs 37 chars) was the tell. Decode both values.
    url_decode(token_raw, strlen(token_raw), token, sizeof(token));
    url_decode(state_raw, strlen(state_raw), state, sizeof(state));

    if (!s_oauth_nonce[0] || strcmp(state, s_oauth_nonce) != 0) {
        s_oauth_nonce[0] = '\0';
        ESP_LOGW(TAG, "token-callback: bad or missing CSRF state");
        httpd_resp_set_status(req, "400 Bad Request");
        httpd_resp_set_type(req, "text/html; charset=utf-8");
        httpd_resp_send(req,
            "<p>Invalid or expired CSRF state. "
            "<a href=\"/\">Please start over</a>.</p>",
            HTTPD_RESP_USE_STRLEN);
        return ESP_OK;
    }
    s_oauth_nonce[0] = '\0';  // single-use

    if (!token[0]) {
        httpd_resp_set_status(req, "400 Bad Request");
        httpd_resp_set_type(req, "text/html; charset=utf-8");
        httpd_resp_send(req,
            "<p>No token in the redirect. "
            "<a href=\"/\">Back</a>.</p>",
            HTTPD_RESP_USE_STRLEN);
        return ESP_OK;
    }

    config_update_t u = { .phoneblock_token = token };
    if (config_update(&u) != ESP_OK) {
        httpd_resp_send_err(req, HTTPD_500_INTERNAL_SERVER_ERROR, "nvs");
        return ESP_OK;
    }

    ESP_LOGI(TAG, "token-callback: stored new PhoneBlock token (%d chars)",
             (int)strlen(token));

    // Immediately exercise the new token so the dashboard has an API
    // latency to show on the first poll, instead of an empty "–".
    phoneblock_selftest();

    // Hand the user straight back to the status landing — the pill
    // there flips to green on the next 3s poll.
    httpd_resp_set_status(req, "302 Found");
    httpd_resp_set_hdr(req, "Location", "/");
    httpd_resp_send(req, NULL, 0);
    return ESP_OK;
}

// GET /api/fritzbox-2fa-status
// Polled by the web UI while a 2FA handshake is pending. Reports the
// current X_AVM-DE_Auth state. When the box reports "authenticated",
// retries SetClient4 with the stashed credentials + token, stores the
// resulting SIP credentials in NVS, and returns ok=true with the same
// shape as a direct success. On "stopped"/"blocked" or on a timeout
// (2 minutes) the pending state is wiped and an error returned.
static esp_err_t handle_fritzbox_2fa_status(httpd_req_t *req)
{
    if (!s_2fa.active) {
        send_fail(req, "No 2FA request in progress.");
        return ESP_OK;
    }
    // 2-minute hard cap, mirroring the Fritz!Box's own timeout.
    int64_t age_us = esp_timer_get_time() - s_2fa.started_us;
    if (age_us > 120LL * 1000 * 1000) {
        s_2fa.active = false;
        send_fail(req, "Timed out. Please start over.");
        return ESP_OK;
    }

    char state[32] = "";
    esp_err_t err = tr064_auth_get_state(s_2fa.fritz_host, 49000,
        s_2fa.fritz_user, s_2fa.fritz_pass, s_2fa.token,
        state, sizeof(state));
    if (err != ESP_OK) {
        send_fail(req, "GetState failed.");
        return ESP_OK;
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
        return ESP_OK;
    }

    // 2FA done → retry SetClient4 with the token.
    tr064_sip_result_t res;
    memset(&res, 0, sizeof(res));
    err = tr064_provision_sip_client(s_2fa.fritz_host, 49000,
        s_2fa.fritz_user, s_2fa.fritz_pass, s_2fa.phone_name,
        s_2fa.token, &res);

    // Register the dongle app instance using the same 2FA token (still
    // valid for the whole auth session). Best effort — see the direct
    // setup path for why a failure here is non-fatal.
    char app_user[32] = "";
    char app_pass[40] = "";
    if (err == ESP_OK) {
        int  app_err_code = 0;
        char app_err_msg[128] = "";
        esp_err_t app_err = tr064_register_dongle_app(
            s_2fa.fritz_host, 49000,
            s_2fa.fritz_user, s_2fa.fritz_pass, s_2fa.token,
            app_user, sizeof(app_user),
            app_pass, sizeof(app_pass),
            &app_err_code, app_err_msg, sizeof(app_err_msg));
        if (app_err != ESP_OK) {
            ESP_LOGW(TAG,
                "RegisterApp after 2FA failed (code %d, %s) — "
                "sync feature disabled",
                app_err_code, app_err_msg);
            app_user[0] = '\0';
            app_pass[0] = '\0';
        }
    }

    // Whatever the outcome, the 2FA round-trip is over; wipe the
    // cached admin password ASAP.
    memset(s_2fa.fritz_pass, 0, sizeof(s_2fa.fritz_pass));
    s_2fa.active = false;

    if (err != ESP_OK) {
        char msg[200];
        snprintf(msg, sizeof(msg),
            "Still failing after 2FA — error %d: %s",
            res.error_code,
            res.error_message[0] ? res.error_message : "(no details)");
        send_fail(req, msg);
        return ESP_OK;
    }

    // Commit SIP credentials, re-register.
    config_update_t u = {
        .sip_host = s_2fa.fritz_host,
        .sip_port = 5060,
        .sip_user = res.sip_user,
        .sip_pass = res.sip_pass,
        .sip_internal_number = res.internal_number,
        .fritzbox_app_user = app_user,
        .fritzbox_app_pass = app_pass,
    };
    if (config_update(&u) != ESP_OK) {
        send_fail(req, "NVS write failed.");
        return ESP_OK;
    }
    sip_register_request_reload();

    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject  (root, "ok",              true);
    cJSON_AddStringToObject(root, "message",
        "Extension created. The dongle is registering now — "
        "see the status bar above for the current state.");
    cJSON_AddStringToObject(root, "sip_user",        res.sip_user);
    cJSON_AddStringToObject(root, "internal_number", res.internal_number);
    send_json(req, root);
    return ESP_OK;
}

static esp_err_t handle_errors(httpd_req_t *req)
{
    stats_error_t errs[STATS_MAX_ERRORS];
    int n = stats_snapshot_errors(errs, STATS_MAX_ERRORS);
    int64_t now_us = esp_timer_get_time();

    cJSON *root = cJSON_CreateObject();
    cJSON *arr  = cJSON_AddArrayToObject(root, "errors");
    for (int i = 0; i < n; i++) {
        cJSON *o = cJSON_CreateObject();
        cJSON_AddNumberToObject(o, "age_s",   (double)((now_us - errs[i].at_us) / 1000000));
        cJSON_AddStringToObject(o, "tag",     errs[i].tag);
        cJSON_AddStringToObject(o, "message", errs[i].message);
        cJSON_AddItemToArray(arr, o);
    }
    send_json(req, root);
    return ESP_OK;
}

// GET /api/announcement — streams the currently active A-law audio.
// Content-Type: audio/basic (the IANA-registered type for G.711
// µ-law, close enough to A-law for a "audio payload" hint; browsers
// don't decode either natively, the UI wraps it in a WAV header).
static esp_err_t handle_announcement_get(httpd_req_t *req)
{
    const uint8_t *buf = NULL;
    size_t len = 0;
    if (announcement_get(&buf, &len) != ESP_OK || len == 0) {
        httpd_resp_send_err(req, HTTPD_404_NOT_FOUND, "no announcement");
        return ESP_OK;
    }
    httpd_resp_set_type(req, "audio/basic");
    httpd_resp_set_hdr(req, "Cache-Control", "no-store");
    httpd_resp_send(req, (const char *)buf, len);
    return ESP_OK;
}

// POST /api/announcement — replaces the current announcement with the
// request body. Body is raw A-law bytes (8 kHz mono, no header); the
// UI does the WAV/MP3/OGG → A-law conversion via the browser's Web
// Audio API before uploading. Streamed straight into SPIFFS via the
// announcement_write_* API so we don't hold the full 240 KB on heap.
static esp_err_t handle_announcement_post(httpd_req_t *req)
{
    int total = req->content_len;
    if (total <= 0 || total > (int)ANNOUNCEMENT_MAX_BYTES) {
        char msg[64];
        snprintf(msg, sizeof(msg),
            "Audio missing or over %u bytes cap.",
            (unsigned)ANNOUNCEMENT_MAX_BYTES);
        send_fail(req, msg);
        return ESP_OK;
    }

    int64_t t0 = esp_timer_get_time();
    esp_err_t err = announcement_write_begin((size_t)total);
    if (err != ESP_OK) {
        send_fail(req, "Could not open upload target.");
        return ESP_OK;
    }

    // 8 KB chunks on the heap: bigger batches keep SPIFFS flushes
    // coarse and the httpd-worker stack uncluttered.
    const int CHUNK = 8192;
    char *chunk = malloc(CHUNK);
    if (!chunk) {
        announcement_write_abort();
        send_fail(req, "Out of memory.");
        return ESP_OK;
    }
    int64_t recv_us = 0;
    int64_t write_us = 0;
    int got = 0;
    while (got < total) {
        int want = total - got;
        if (want > CHUNK) want = CHUNK;
        int64_t rs = esp_timer_get_time();
        int n = httpd_req_recv(req, chunk, want);
        recv_us += esp_timer_get_time() - rs;
        if (n <= 0) {
            free(chunk);
            announcement_write_abort();
            send_fail(req, "Upload interrupted.");
            return ESP_OK;
        }
        int64_t ws = esp_timer_get_time();
        if (announcement_write_append((const uint8_t *)chunk, n) != ESP_OK) {
            free(chunk);
            announcement_write_abort();
            send_fail(req, "SPIFFS write failed.");
            return ESP_OK;
        }
        write_us += esp_timer_get_time() - ws;
        got += n;
    }
    free(chunk);

    int64_t cs = esp_timer_get_time();
    esp_err_t cerr = ESP_OK;
    (void)cerr;
    // Commit measured separately from the per-chunk write time.

    if (announcement_write_commit() != ESP_OK) {
        send_fail(req, "Commit failed.");
        return ESP_OK;
    }
    int64_t commit_us = esp_timer_get_time() - cs;
    int64_t total_us  = esp_timer_get_time() - t0;
    ESP_LOGI(TAG,
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
    return ESP_OK;
}

// POST /api/announcement/reset — drop the custom announcement, fall
// back to the embedded default.
static esp_err_t handle_announcement_reset(httpd_req_t *req)
{
    announcement_reset();
    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject  (root, "ok", true);
    cJSON_AddStringToObject(root, "message", "Announcement reset to default.");
    send_json(req, root);
    return ESP_OK;
}

// POST /api/firmware — upload a new firmware binary. Streams the
// request body into the inactive OTA slot (ota_0/ota_1), verifies
// the image, flips the boot partition, and reboots. The new image
// comes up in "pending verify" state; unless the boot path reaches
// esp_ota_mark_app_valid_cancel_rollback() (in app_main after
// web_start), the bootloader rolls back on the next reset.
static void firmware_reboot_task(void *arg)
{
    (void)arg;
    vTaskDelay(pdMS_TO_TICKS(500));
    ESP_LOGW(TAG, "firmware upload complete — restarting");
    esp_restart();
}

static esp_err_t handle_firmware_upload(httpd_req_t *req)
{
    int total = req->content_len;
    if (total <= 0) {
        send_fail(req, "Upload body missing.");
        return ESP_OK;
    }

    const esp_partition_t *target = esp_ota_get_next_update_partition(NULL);
    if (!target) {
        send_fail(req, "No OTA slot configured.");
        return ESP_OK;
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
        ESP_LOGE(TAG, "esp_ota_begin: %s", esp_err_to_name(err));
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
    int64_t t0 = esp_timer_get_time();
    int got = 0;
    while (got < total) {
        int want = total - got;
        if (want > CHUNK) want = CHUNK;
        int n = httpd_req_recv(req, buf, want);
        if (n <= 0) {
            free(buf);
            esp_ota_abort(ota);
            send_fail(req, "Upload interrupted.");
            return ESP_OK;
        }
        err = esp_ota_write(ota, buf, n);
        if (err != ESP_OK) {
            ESP_LOGE(TAG, "esp_ota_write: %s", esp_err_to_name(err));
            free(buf);
            esp_ota_abort(ota);
            send_fail(req, "Flash write failed.");
            return ESP_OK;
        }
        got += n;
    }
    free(buf);

    err = esp_ota_end(ota);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "esp_ota_end: %s", esp_err_to_name(err));
        send_fail(req, "Image verification failed.");
        return ESP_OK;
    }
    err = esp_ota_set_boot_partition(target);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "esp_ota_set_boot_partition: %s", esp_err_to_name(err));
        send_fail(req, "Could not activate new partition.");
        return ESP_OK;
    }

    int64_t total_us = esp_timer_get_time() - t0;
    ESP_LOGI(TAG, "firmware upload: %d bytes to %s in %lld ms",
             got, target->label, (long long)(total_us / 1000));

    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject  (root, "ok", true);
    cJSON_AddNumberToObject(root, "bytes", got);
    cJSON_AddStringToObject(root, "partition", target->label);
    cJSON_AddStringToObject(root, "message", "Firmware gespeichert — Neustart folgt.");
    send_json(req, root);

    xTaskCreate(firmware_reboot_task, "fw_reboot", 2048, NULL, 5, NULL);
    return ESP_OK;
}

// POST /api/factory-reset — erases our NVS namespace and reboots.
// Answer the HTTP request before rebooting so the browser sees the
// JSON confirmation; the actual esp_restart() runs from a short-
// lived task so the httpd worker has time to finish sending.
static void factory_reset_task(void *arg)
{
    (void)arg;
    vTaskDelay(pdMS_TO_TICKS(300));
    ESP_LOGW(TAG, "factory-reset: restarting now");
    esp_restart();
}

static esp_err_t handle_factory_reset(httpd_req_t *req)
{
    esp_err_t err = config_erase();
    // Also drop any uploaded announcement, so the factory-reset
    // return-to-defaults promise really includes the audio.
    announcement_reset();
    cJSON *root = cJSON_CreateObject();
    if (err != ESP_OK) {
        cJSON_AddBoolToObject  (root, "ok", false);
        cJSON_AddStringToObject(root, "message",
            "NVS erase failed.");
        send_json(req, root);
        return ESP_OK;
    }
    cJSON_AddBoolToObject  (root, "ok", true);
    cJSON_AddStringToObject(root, "message",
        "Configuration erased — rebooting.");
    send_json(req, root);
    xTaskCreate(factory_reset_task, "factory_reset", 2048, NULL, 5, NULL);
    return ESP_OK;
}

// POST /api/sync/run — trigger an immediate blocklist sync.
static esp_err_t handle_sync_run(httpd_req_t *req)
{
    bool triggered = sync_trigger_now();
    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject  (root, "ok", triggered);
    cJSON_AddStringToObject(root, "message",
        triggered ? "Sync triggered." : "Sync is already running.");
    send_json(req, root);
    return ESP_OK;
}

// POST /api/errors/clear — drops all buffered error entries.
static esp_err_t handle_errors_clear(httpd_req_t *req)
{
    stats_clear_errors();
    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject(root, "ok", true);
    send_json(req, root);
    return ESP_OK;
}

// POST /api/token-test
// Runs phoneblock_selftest() against GET /test and reports whether the
// currently stored token is still accepted by the server. Always
// returns HTTP 200; `ok` in the JSON body tells the UI the outcome.
static esp_err_t handle_token_test(httpd_req_t *req)
{
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
        return ESP_OK;
    }
    bool ok = phoneblock_selftest();
    cJSON_AddBoolToObject  (root, "ok", ok);
    cJSON_AddStringToObject(root, "code", ok ? "ok" : "fail");
    cJSON_AddStringToObject(root, "message",
        ok ? "Token valid — PhoneBlock reachable."
           : "Token was rejected. Please request a new one.");
    send_json(req, root);
    return ESP_OK;
}

// --- Server lifecycle -----------------------------------------------

static const httpd_uri_t URIS[] = {
    { .uri = "/",                .method = HTTP_GET,  .handler = handle_root,        .user_ctx = NULL },
    { .uri = "/favicon.ico",     .method = HTTP_GET,  .handler = handle_favicon,     .user_ctx = NULL },
    { .uri = "/ab-logo-bot.svg", .method = HTTP_GET,  .handler = handle_favicon,     .user_ctx = NULL },
    { .uri = "/api/status",  .method = HTTP_GET,  .handler = handle_status,      .user_ctx = NULL },
    { .uri = "/api/calls",   .method = HTTP_GET,  .handler = handle_calls,       .user_ctx = NULL },
    { .uri = "/api/errors",  .method = HTTP_GET,  .handler = handle_errors,      .user_ctx = NULL },
    { .uri = "/api/errors/clear",    .method = HTTP_POST, .handler = handle_errors_clear,   .user_ctx = NULL },
    { .uri = "/api/factory-reset",   .method = HTTP_POST, .handler = handle_factory_reset,  .user_ctx = NULL },
    { .uri = "/api/firmware",        .method = HTTP_POST, .handler = handle_firmware_upload, .user_ctx = NULL },
    { .uri = "/api/announcement",    .method = HTTP_GET,  .handler = handle_announcement_get,   .user_ctx = NULL },
    { .uri = "/api/announcement",    .method = HTTP_POST, .handler = handle_announcement_post,  .user_ctx = NULL },
    { .uri = "/api/announcement/reset", .method = HTTP_POST, .handler = handle_announcement_reset, .user_ctx = NULL },
    { .uri = "/api/sync/run",        .method = HTTP_POST, .handler = handle_sync_run,       .user_ctx = NULL },
    { .uri = "/api/config",          .method = HTTP_POST, .handler = handle_config_post,    .user_ctx = NULL },
    { .uri = "/api/fritzbox-setup",      .method = HTTP_POST, .handler = handle_fritzbox_setup,      .user_ctx = NULL },
    { .uri = "/api/fritzbox-2fa-status", .method = HTTP_GET,  .handler = handle_fritzbox_2fa_status, .user_ctx = NULL },
    { .uri = "/api/token-test",          .method = HTTP_POST, .handler = handle_token_test,          .user_ctx = NULL },
    { .uri = "/register-start",      .method = HTTP_GET,  .handler = handle_register_start, .user_ctx = NULL },
    { .uri = "/token-callback",      .method = HTTP_GET,  .handler = handle_token_callback, .user_ctx = NULL },
};

void web_start(void)
{
    if (s_server) {
        ESP_LOGW(TAG, "web_start() called twice — ignoring");
        return;
    }
    httpd_config_t cfg = HTTPD_DEFAULT_CONFIG();
    cfg.server_port = 80;
    cfg.max_uri_handlers = sizeof(URIS) / sizeof(URIS[0]) + 2;
    // The default 4 KB is too tight once /api/fritzbox-setup → tr064_*
    // nests through call_action → post_soap → esp_http_client. 8 KB
    // gives us headroom for the deep call chain without overflows.
    cfg.stack_size = 8192;
    if (httpd_start(&s_server, &cfg) != ESP_OK) {
        ESP_LOGE(TAG, "httpd_start failed");
        return;
    }
    for (size_t i = 0; i < sizeof(URIS) / sizeof(URIS[0]); i++) {
        httpd_register_uri_handler(s_server, &URIS[i]);
    }
    ESP_LOGI(TAG, "HTTP server listening on :80");
}
