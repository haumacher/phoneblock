#include "web_auth.h"

#include <string.h>

#include "esp_log.h"
#include "esp_random.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"

#include "api.h"
#include "config.h"

static const char *TAG = "web_auth";

// --- Session store --------------------------------------------------

#define SESSION_ID_HEX 32                  // 16 bytes of entropy
#define MAX_SESSIONS   4                   // small fleet of trusted devices

typedef struct {
    char    id[SESSION_ID_HEX + 1];        // empty string = free slot
    int64_t last_used_us;
} session_t;

static session_t      s_sessions[MAX_SESSIONS];
static SemaphoreHandle_t s_mutex;

// CSRF nonce for the in-flight login round-trip. We only ever have
// one active login at a time per dongle, so a single slot is enough;
// concurrent activations from two browsers would simply race, and the
// later one wins, which matches the user's expectation.
static char s_login_nonce[SESSION_ID_HEX + 1];
// True while /auth/start was invoked with `activate=1` — the
// callback will then persist auth_enabled=1 if verification succeeds.
static bool s_login_activates;

static void lock(void)   { xSemaphoreTake(s_mutex, portMAX_DELAY); }
static void unlock(void) { xSemaphoreGive(s_mutex); }

void web_auth_setup(void)
{
    s_mutex = xSemaphoreCreateMutex();
    memset(s_sessions, 0, sizeof(s_sessions));
    s_login_nonce[0] = '\0';
    s_login_activates = false;
}

// --- Helpers --------------------------------------------------------

static void random_hex(char *out, int hex_chars)
{
    static const char H[] = "0123456789abcdef";
    for (int i = 0; i < hex_chars; i++) {
        out[i] = H[esp_random() & 0x0f];
    }
    out[hex_chars] = '\0';
}

// Constant-time compare for fixed-length null-terminated hex strings —
// avoids leaking how many leading bytes of an attacker's guess matched.
static bool hex_eq(const char *a, const char *b, size_t n)
{
    int diff = 0;
    for (size_t i = 0; i < n; i++) diff |= (a[i] ^ b[i]);
    return diff == 0;
}

// Find session by ID. Caller must hold the mutex. Returns NULL when
// no slot matches.
static session_t *find_session(const char *id)
{
    if (!id || !id[0]) return NULL;
    if (strlen(id) != SESSION_ID_HEX) return NULL;
    for (int i = 0; i < MAX_SESSIONS; i++) {
        if (s_sessions[i].id[0] && hex_eq(s_sessions[i].id, id, SESSION_ID_HEX)) {
            return &s_sessions[i];
        }
    }
    return NULL;
}

// Insert into a free slot, or evict the least-recently-used entry.
// Caller must hold the mutex.
static void store_session(const char *id)
{
    int oldest = 0;
    for (int i = 0; i < MAX_SESSIONS; i++) {
        if (!s_sessions[i].id[0]) {
            oldest = i;
            break;
        }
        if (s_sessions[i].last_used_us < s_sessions[oldest].last_used_us) {
            oldest = i;
        }
    }
    memcpy(s_sessions[oldest].id, id, SESSION_ID_HEX + 1);
    s_sessions[oldest].last_used_us = esp_timer_get_time();
}

// Extract the value of cookie name `name` from a Cookie header.
// Returns true and writes into `out` on success. Cookies are simple
// "k=v; k=v" lists — we don't need full RFC 6265 parsing.
static bool cookie_get(const char *header, const char *name,
                      char *out, size_t out_cap)
{
    size_t name_len = strlen(name);
    const char *p = header;
    while (p && *p) {
        while (*p == ' ' || *p == ';') p++;
        if (!*p) break;
        if (strncmp(p, name, name_len) == 0 && p[name_len] == '=') {
            const char *v = p + name_len + 1;
            const char *end = strchr(v, ';');
            size_t vlen = end ? (size_t)(end - v) : strlen(v);
            if (vlen >= out_cap) return false;
            memcpy(out, v, vlen);
            out[vlen] = '\0';
            return true;
        }
        const char *next = strchr(p, ';');
        if (!next) break;
        p = next + 1;
    }
    return false;
}

static bool get_cookie_session(httpd_req_t *req, char *out, size_t out_cap)
{
    char header[256];
    if (httpd_req_get_hdr_value_str(req, "Cookie", header, sizeof(header)) != ESP_OK) {
        return false;
    }
    return cookie_get(header, "pb_session", out, out_cap);
}

// --- Public API -----------------------------------------------------

bool web_auth_session_valid(httpd_req_t *req)
{
    char id[SESSION_ID_HEX + 1];
    if (!get_cookie_session(req, id, sizeof(id))) return false;
    bool ok = false;
    lock();
    session_t *s = find_session(id);
    if (s) {
        s->last_used_us = esp_timer_get_time();
        ok = true;
    }
    unlock();
    return ok;
}

bool web_auth_is_logged_in(httpd_req_t *req)
{
    return web_auth_session_valid(req);
}

bool web_auth_required(httpd_req_t *req, bool is_api)
{
    // Open access while the gate is off — the typical setup case.
    if (!config_auth_enabled()) return true;
    if (web_auth_session_valid(req)) return true;

    if (is_api) {
        httpd_resp_set_status(req, "401 Unauthorized");
        httpd_resp_set_type(req, "application/json");
        httpd_resp_sendstr(req, "{\"ok\":false,\"reason\":\"login_required\"}");
    } else {
        // Send the user back to the SPA; index.html itself decides
        // whether to render the dashboard or the in-page login state
        // based on the result of /api/status.
        httpd_resp_set_status(req, "302 Found");
        httpd_resp_set_hdr(req, "Location", "/");
        httpd_resp_send(req, NULL, 0);
    }
    return false;
}

// --- HTTP handlers --------------------------------------------------

esp_err_t web_auth_handle_start(httpd_req_t *req)
{
    // The "activate=1" query flag persists across the round-trip via
    // s_login_activates; only the dongle UI passes it. The CSRF
    // nonce is stored alongside so we can match it on the callback.
    char query[128];
    bool activate = false;
    if (httpd_req_get_url_query_str(req, query, sizeof(query)) == ESP_OK) {
        char val[8];
        if (httpd_query_key_value(query, "activate", val, sizeof(val)) == ESP_OK
            && strcmp(val, "1") == 0) {
            activate = true;
        }
    }

    lock();
    random_hex(s_login_nonce, SESSION_ID_HEX);
    s_login_activates = activate;
    char nonce_copy[SESSION_ID_HEX + 1];
    memcpy(nonce_copy, s_login_nonce, sizeof(nonce_copy));
    unlock();

    // Build the loopback callback URL using whatever Host the
    // browser used to reach us — same trick as /register-start.
    char host[64] = "answerbot";
    httpd_req_get_hdr_value_str(req, "Host", host, sizeof(host));

    char callback[160];
    snprintf(callback, sizeof(callback), "http://%s/auth/callback", host);

    // URL-encode the callback before passing it to the server. We
    // can reuse the simple alphanumeric/-_./ rule since LAN host
    // names and our fixed path won't contain anything else.
    char callback_enc[256];
    {
        const char *src = callback;
        char *dst = callback_enc;
        size_t cap = sizeof(callback_enc);
        while (*src && (size_t)(dst - callback_enc) + 4 < cap) {
            unsigned c = (unsigned char)*src++;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
                (c >= '0' && c <= '9') || c == '-' || c == '_' ||
                c == '.' || c == '~') {
                *dst++ = (char)c;
            } else {
                static const char H[] = "0123456789ABCDEF";
                *dst++ = '%';
                *dst++ = H[(c >> 4) & 0xF];
                *dst++ = H[c & 0xF];
            }
        }
        *dst = '\0';
    }

    char url[512];
    snprintf(url, sizeof(url),
        "%s/auth-gate?callback=%s&state=%s",
        config_phoneblock_base_url(), callback_enc, nonce_copy);

    ESP_LOGI(TAG, "auth/start → redirect to %s (activate=%d)", url, (int)activate);
    httpd_resp_set_status(req, "302 Found");
    httpd_resp_set_hdr(req, "Location", url);
    httpd_resp_send(req, NULL, 0);
    return ESP_OK;
}

// 302 the browser back to the SPA's login state with a machine-readable
// `?login_error=<code>` query the JS side maps to a localized banner.
// Keeps this handler from rendering its own (untranslated, unstyled)
// error pages.
static esp_err_t redirect_login_error(httpd_req_t *req, const char *code)
{
    char location[64];
    snprintf(location, sizeof(location), "/?login_error=%s", code);
    httpd_resp_set_status(req, "302 Found");
    httpd_resp_set_hdr(req, "Location", location);
    httpd_resp_send(req, NULL, 0);
    return ESP_OK;
}

esp_err_t web_auth_handle_callback(httpd_req_t *req)
{
    char query[1024];
    if (httpd_req_get_url_query_str(req, query, sizeof(query)) != ESP_OK) {
        ESP_LOGW(TAG, "auth/callback: missing query");
        return redirect_login_error(req, "missing");
    }
    char code[768] = "";
    char state[64] = "";
    httpd_query_key_value(query, "code",  code,  sizeof(code));
    httpd_query_key_value(query, "state", state, sizeof(state));

    lock();
    bool nonce_ok = s_login_nonce[0] && strlen(state) == SESSION_ID_HEX
                    && hex_eq(s_login_nonce, state, SESSION_ID_HEX);
    bool was_activate = s_login_activates;
    s_login_nonce[0] = '\0';     // single-use
    s_login_activates = false;
    unlock();

    if (!nonce_ok) {
        ESP_LOGW(TAG, "auth/callback: bad CSRF state");
        return redirect_login_error(req, "expired");
    }
    if (!code[0]) {
        ESP_LOGW(TAG, "auth/callback: missing code");
        return redirect_login_error(req, "missing");
    }

    // Round-trip to phoneblock.net: server validates JWT signature
    // and expiry, returns the subject (PhoneBlock user-name).
    char verified_user[64];
    if (!phoneblock_verify_auth_code(code, state,
                                     verified_user, sizeof(verified_user))) {
        ESP_LOGW(TAG, "auth/callback: server rejected verification");
        return redirect_login_error(req, "rejected");
    }

    if (was_activate) {
        // Trust-on-first-use: pin the verified user as the dongle's
        // owner. From here on, only this PhoneBlock account can log
        // in to the gate, regardless of what happens to the API
        // token on the server side.
        config_update_t u = {
            .auth_enabled = "1",
            .auth_user    = verified_user,
        };
        if (config_update(&u) != ESP_OK) {
            ESP_LOGE(TAG, "auth/callback: failed to persist activation");
            return redirect_login_error(req, "persist");
        }
        ESP_LOGI(TAG, "auth/callback: activated for user '%s'", verified_user);
    } else {
        // Normal login: the JWT must be for the same user the
        // dongle was activated for. Without this check, anyone with
        // a PhoneBlock account could log in to anyone's dongle.
        const char *expected = config_auth_user();
        if (!expected[0] || strcmp(verified_user, expected) != 0) {
            ESP_LOGW(TAG, "auth/callback: user mismatch (got '%s', want '%s')",
                     verified_user, expected);
            return redirect_login_error(req, "mismatch");
        }
    }

    char id[SESSION_ID_HEX + 1];
    random_hex(id, SESSION_ID_HEX);
    lock();
    store_session(id);
    unlock();

    char cookie[128];
    snprintf(cookie, sizeof(cookie),
        "pb_session=%s; Path=/; HttpOnly; SameSite=Lax", id);

    ESP_LOGI(TAG, "auth/callback: session created%s",
             was_activate ? " (and gate activated)" : "");
    httpd_resp_set_status(req, "302 Found");
    httpd_resp_set_hdr(req, "Location", "/");
    httpd_resp_set_hdr(req, "Set-Cookie", cookie);
    httpd_resp_send(req, NULL, 0);
    return ESP_OK;
}

esp_err_t web_auth_handle_logout(httpd_req_t *req)
{
    char id[SESSION_ID_HEX + 1];
    if (get_cookie_session(req, id, sizeof(id))) {
        lock();
        session_t *s = find_session(id);
        if (s) s->id[0] = '\0';
        unlock();
    }
    httpd_resp_set_status(req, "302 Found");
    httpd_resp_set_hdr(req, "Location", "/");
    httpd_resp_set_hdr(req, "Set-Cookie",
        "pb_session=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
    httpd_resp_send(req, NULL, 0);
    return ESP_OK;
}

esp_err_t web_auth_handle_login_link(httpd_req_t *req)
{
    if (!web_auth_required(req, false)) return ESP_OK;

    char query[256];
    if (httpd_req_get_url_query_str(req, query, sizeof(query)) != ESP_OK) {
        httpd_resp_set_status(req, "400 Bad Request");
        httpd_resp_set_type(req, "text/html; charset=utf-8");
        httpd_resp_sendstr(req,
            "<p>Missing 'next' parameter. <a href=\"/\">Back</a>.</p>");
        return ESP_OK;
    }
    char next[192];
    if (httpd_query_key_value(query, "next", next, sizeof(next)) != ESP_OK
            || !next[0]) {
        httpd_resp_set_status(req, "400 Bad Request");
        httpd_resp_set_type(req, "text/html; charset=utf-8");
        httpd_resp_sendstr(req,
            "<p>Missing 'next' parameter. <a href=\"/\">Back</a>.</p>");
        return ESP_OK;
    }
    // Reject anything that isn't a server-relative path. Without this
    // a hostile link inside the dongle UI could trick the user into a
    // login flow that lands on a third-party site.
    if (next[0] != '/' || next[1] == '/') {
        ESP_LOGW(TAG, "auth/login-link: refusing 'next' = %s", next);
        httpd_resp_set_status(req, "400 Bad Request");
        httpd_resp_set_type(req, "text/html; charset=utf-8");
        httpd_resp_sendstr(req,
            "<p>'next' must be a server-relative path. "
            "<a href=\"/\">Back</a>.</p>");
        return ESP_OK;
    }

    char redirect_url[512];
    if (!phoneblock_mint_login_ticket(next, redirect_url, sizeof(redirect_url))) {
        httpd_resp_set_status(req, "502 Bad Gateway");
        httpd_resp_set_type(req, "text/html; charset=utf-8");
        httpd_resp_sendstr(req,
            "<p>Konnte Anmelde-Ticket nicht erzeugen. "
            "<a href=\"/\">Zur&uuml;ck</a>.</p>");
        return ESP_OK;
    }

    ESP_LOGI(TAG, "auth/login-link: 302 → %s", next);
    httpd_resp_set_status(req, "302 Found");
    httpd_resp_set_hdr(req, "Location", redirect_url);
    httpd_resp_send(req, NULL, 0);
    return ESP_OK;
}

esp_err_t web_auth_handle_disable(httpd_req_t *req)
{
    if (!web_auth_required(req, true)) return ESP_OK;

    // Clear both flag and pinned user — re-enabling later goes
    // through trust-on-first-use again, allowing the (potentially
    // changed) PhoneBlock account name to take over cleanly.
    config_update_t u = {
        .auth_enabled = "0",
        .auth_user    = "",
    };
    if (config_update(&u) != ESP_OK) {
        httpd_resp_set_status(req, "500 Internal Server Error");
        httpd_resp_set_type(req, "application/json");
        httpd_resp_sendstr(req, "{\"ok\":false,\"reason\":\"nvs\"}");
        return ESP_OK;
    }

    // Drop every active session — leaving stale cookies behind would
    // be harmless (the gate is off now) but invites surprise on the
    // next re-enable.
    lock();
    for (int i = 0; i < MAX_SESSIONS; i++) s_sessions[i].id[0] = '\0';
    unlock();

    httpd_resp_set_type(req, "application/json");
    httpd_resp_sendstr(req, "{\"ok\":true}");
    return ESP_OK;
}
