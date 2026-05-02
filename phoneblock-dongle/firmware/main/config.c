#include "config.h"

#include <string.h>

#include "esp_log.h"
#include "nvs_flash.h"
#include "nvs.h"

#include "sdkconfig.h"

static const char *TAG  = "config";
static const char *NS   = "phoneblock";

// ESP-IDF NVS keys are at most 15 chars.
#define K_SIP_HOST      "sip_host"
#define K_SIP_PORT      "sip_port"
#define K_SIP_USER      "sip_user"
#define K_SIP_PASS      "sip_pass"
#define K_SIP_EXPIRES   "sip_expires"
#define K_SIP_INT_NUM   "sip_int_num"
#define K_SIP_TRANSP    "sip_transp"
#define K_SIP_AUTHUSER  "sip_authuser"
#define K_SIP_OUTBOUND  "sip_outbound"
#define K_SIP_REALM     "sip_realm"
#define K_SIP_SRTP      "sip_srtp"
#define K_FB_APP_USER   "fb_app_user"
#define K_FB_APP_PASS   "fb_app_pass"
#define K_SYNC_ENABLED  "sync_enabled"
#define K_LOG_KNOWN     "log_known_calls"
#define K_AUTH_ENABLED  "auth_enabled"
#define K_AUTH_USER     "auth_user"
#define K_AUTH_PERSIST  "auth_persist"
#define K_AUTO_UPDATE   "auto_update"
#define K_CONTACT_HOST  "contact_host"
#define K_CONTACT_PORT  "contact_port"
#define K_PB_URL        "pb_url"
#define K_PB_TOKEN      "pb_token"
#define K_MIN_DIRECT    "min_direct"
#define K_MIN_RANGE     "min_range"

// Direct-vote default mirrors DB.MIN_VOTES on the server (the
// confidence floor the public blocklist export already enforces).
#define DEFAULT_MIN_DIRECT_VOTES 4
// Range votes are weaker evidence (numbers in the same 10/100-block
// reported, not the queried number itself). Default at 10 keeps the
// dongle conservative on neighborhood signals; users who want
// aggressive range-blocking can lower it, users who want to disable
// range entirely can set it to 0.
#define DEFAULT_MIN_RANGE_VOTES 10
// Version string of the most recent OTA download that did NOT survive
// to the next successful boot. Set pessimistically before
// esp_https_ota() runs and only cleared once main.c sees the running
// image is the version stored here — i.e. the bootloader did not roll
// back. The auto-update task uses this to break the
// download → brick → rollback → retry-same-bits loop.
#define K_LAST_FAIL_OTA "last_fail_ota"

typedef struct {
    char sip_host[64];
    int  sip_port;
    char sip_user[32];
    char sip_pass[64];
    int  sip_expires;
    char sip_int_num[16];
    char sip_transp[8];      // "udp" | "tcp" | "tls"
    char sip_authuser[32];
    char sip_outbound[80];
    char sip_realm[64];
    char sip_srtp[16];       // "off" | "optional" | "mandatory"
    char fb_app_user[32];
    char fb_app_pass[40];    // spec cap is 32; 40 for NUL + padding
    char sync_enabled[4];    // "1" | "0" (or empty = default off)
    char log_known[4];       // "1" | "0" (or empty = default on)
    char auth_enabled[4];    // "1" | "0" (or empty = default off)
    char auth_user[64];      // pinned PhoneBlock user-name; empty = no pin
    char auth_persist[33];   // 32 hex chars + NUL; empty = nobody is "remembered"
    char auto_update[4];     // "1" | "0" (or empty = default on)
    char contact_host[64];
    int  contact_port;
    char pb_base_url[128];
    char pb_token[64];
    int  min_direct_votes;   // SPAM threshold for direct hits
    int  min_range_votes;    // SPAM threshold for wildcard/range hits; 0 = off
    char last_failed_ota[32];   // semver, plus headroom for "-rcN" suffixes
} config_cache_t;

static config_cache_t s_config;

// --- Helpers --------------------------------------------------------

static void copy_default(char *dst, size_t cap, const char *def)
{
    size_t n = strnlen(def, cap - 1);
    memcpy(dst, def, n);
    dst[n] = '\0';
}

static void load_str(nvs_handle_t h, const char *key, const char *def,
                     char *dst, size_t cap)
{
    size_t len = cap;
    if (nvs_get_str(h, key, dst, &len) != ESP_OK) {
        copy_default(dst, cap, def);
    }
}

static int load_int(nvs_handle_t h, const char *key, int def)
{
    int32_t v;
    return nvs_get_i32(h, key, &v) == ESP_OK ? (int)v : def;
}

// --- Public API -----------------------------------------------------

void config_load(void)
{
    nvs_handle_t h;
    esp_err_t err = nvs_open(NS, NVS_READONLY, &h);
    if (err == ESP_ERR_NVS_NOT_FOUND) {
        // Namespace not yet created. SIP credentials + PhoneBlock
        // token stay empty — the setup wizard fills them. Other
        // fields keep their Kconfig defaults (port, expiry, contact
        // overrides for QEMU, API base URL).
        ESP_LOGI(TAG, "NVS namespace '%s' empty, using Kconfig defaults", NS);
        s_config.sip_host[0]    = '\0';
        s_config.sip_port       = CONFIG_SIP_REGISTRAR_PORT;
        s_config.sip_user[0]    = '\0';
        s_config.sip_pass[0]    = '\0';
        s_config.sip_expires    = CONFIG_SIP_EXPIRES;
        s_config.sip_int_num[0]   = '\0';
        copy_default(s_config.sip_transp, sizeof(s_config.sip_transp), "udp");
        s_config.sip_authuser[0]  = '\0';
        s_config.sip_outbound[0]  = '\0';
        s_config.sip_realm[0]     = '\0';
        copy_default(s_config.sip_srtp,   sizeof(s_config.sip_srtp),   "off");
        s_config.fb_app_user[0]   = '\0';
        s_config.fb_app_pass[0]   = '\0';
        s_config.sync_enabled[0]  = '\0';
        s_config.log_known[0]     = '\0';
        s_config.auth_enabled[0]  = '\0';
        s_config.auth_user[0]     = '\0';
        s_config.auth_persist[0]  = '\0';
        s_config.auto_update[0]   = '\0';
        copy_default(s_config.contact_host, sizeof(s_config.contact_host), CONFIG_SIP_CONTACT_HOST_OVERRIDE);
        s_config.contact_port = CONFIG_SIP_CONTACT_PORT_OVERRIDE;
        copy_default(s_config.pb_base_url,  sizeof(s_config.pb_base_url),  CONFIG_PHONEBLOCK_BASE_URL);
        s_config.pb_token[0]  = '\0';
        s_config.min_direct_votes = DEFAULT_MIN_DIRECT_VOTES;
        s_config.min_range_votes  = DEFAULT_MIN_RANGE_VOTES;
        s_config.last_failed_ota[0] = '\0';
        return;
    }
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "nvs_open: %s — all defaults", esp_err_to_name(err));
        return;
    }

    load_str(h, K_SIP_HOST,     "",
             s_config.sip_host,     sizeof(s_config.sip_host));
    s_config.sip_port     = load_int(h, K_SIP_PORT,     CONFIG_SIP_REGISTRAR_PORT);
    load_str(h, K_SIP_USER,     "",
             s_config.sip_user,     sizeof(s_config.sip_user));
    load_str(h, K_SIP_PASS,     "",
             s_config.sip_pass,     sizeof(s_config.sip_pass));
    s_config.sip_expires  = load_int(h, K_SIP_EXPIRES,  CONFIG_SIP_EXPIRES);
    load_str(h, K_SIP_INT_NUM,  "",
             s_config.sip_int_num,  sizeof(s_config.sip_int_num));
    load_str(h, K_SIP_TRANSP,   "udp",
             s_config.sip_transp,   sizeof(s_config.sip_transp));
    load_str(h, K_SIP_AUTHUSER, "",
             s_config.sip_authuser, sizeof(s_config.sip_authuser));
    load_str(h, K_SIP_OUTBOUND, "",
             s_config.sip_outbound, sizeof(s_config.sip_outbound));
    load_str(h, K_SIP_REALM,    "",
             s_config.sip_realm,    sizeof(s_config.sip_realm));
    load_str(h, K_SIP_SRTP,     "off",
             s_config.sip_srtp,     sizeof(s_config.sip_srtp));
    load_str(h, K_FB_APP_USER,  "",
             s_config.fb_app_user,  sizeof(s_config.fb_app_user));
    load_str(h, K_FB_APP_PASS,  "",
             s_config.fb_app_pass,  sizeof(s_config.fb_app_pass));
    load_str(h, K_SYNC_ENABLED, "",
             s_config.sync_enabled, sizeof(s_config.sync_enabled));
    load_str(h, K_LOG_KNOWN, "",
             s_config.log_known, sizeof(s_config.log_known));
    load_str(h, K_AUTH_ENABLED, "",
             s_config.auth_enabled, sizeof(s_config.auth_enabled));
    load_str(h, K_AUTH_USER, "",
             s_config.auth_user, sizeof(s_config.auth_user));
    load_str(h, K_AUTH_PERSIST, "",
             s_config.auth_persist, sizeof(s_config.auth_persist));
    load_str(h, K_AUTO_UPDATE, "",
             s_config.auto_update, sizeof(s_config.auto_update));
    load_str(h, K_CONTACT_HOST, CONFIG_SIP_CONTACT_HOST_OVERRIDE,
             s_config.contact_host, sizeof(s_config.contact_host));
    s_config.contact_port = load_int(h, K_CONTACT_PORT, CONFIG_SIP_CONTACT_PORT_OVERRIDE);
    load_str(h, K_PB_URL,       CONFIG_PHONEBLOCK_BASE_URL,
             s_config.pb_base_url,  sizeof(s_config.pb_base_url));
    load_str(h, K_PB_TOKEN,     "",
             s_config.pb_token,     sizeof(s_config.pb_token));
    s_config.min_direct_votes = load_int(h, K_MIN_DIRECT, DEFAULT_MIN_DIRECT_VOTES);
    s_config.min_range_votes  = load_int(h, K_MIN_RANGE,  DEFAULT_MIN_RANGE_VOTES);
    load_str(h, K_LAST_FAIL_OTA, "",
             s_config.last_failed_ota, sizeof(s_config.last_failed_ota));
    nvs_close(h);

    ESP_LOGI(TAG, "loaded config: sip=%s@%s:%d, pb=%s",
             s_config.sip_user, s_config.sip_host, s_config.sip_port,
             s_config.pb_base_url);
}

const char *config_sip_host(void)            { return s_config.sip_host; }
int         config_sip_port(void)            { return s_config.sip_port; }
const char *config_sip_user(void)            { return s_config.sip_user; }
const char *config_sip_pass(void)            { return s_config.sip_pass; }
int         config_sip_expires(void)         { return s_config.sip_expires; }
const char *config_sip_internal_number(void) { return s_config.sip_int_num; }
const char *config_sip_transport(void)       { return s_config.sip_transp[0] ? s_config.sip_transp : "udp"; }
const char *config_sip_auth_user(void)       { return s_config.sip_authuser; }
const char *config_sip_outbound(void)        { return s_config.sip_outbound; }
const char *config_sip_realm(void)           { return s_config.sip_realm; }
const char *config_sip_srtp(void)            { return s_config.sip_srtp[0] ? s_config.sip_srtp : "off"; }
const char *config_fritzbox_app_user(void)   { return s_config.fb_app_user; }
const char *config_fritzbox_app_pass(void)   { return s_config.fb_app_pass; }
bool        config_sync_enabled(void)
{
    // Stored as "0"/"1"; anything empty / unrecognised means "off".
    // The sync feature is fairly invasive (mass-submits numbers to
    // PhoneBlock as spam on the user's behalf and then deletes them
    // from the Fritz!Box), so the user has to opt in explicitly
    // before anything happens automatically.
    return s_config.sync_enabled[0] == '1';
}
bool        config_log_known_calls(void)
{
    // Default on (empty / unrecognised → log) so new users see every
    // call the dongle handled. Only an explicit "0" disables listing.
    return s_config.log_known[0] != '0';
}
bool        config_auth_enabled(void)
{
    // Default off (empty / unrecognised → open) so the LAN-local UI
    // stays reachable during initial setup. Only "1" closes it.
    return s_config.auth_enabled[0] == '1';
}
const char *config_auth_user(void)        { return s_config.auth_user; }
const char *config_auth_persist(void)     { return s_config.auth_persist; }
bool        config_auto_update_enabled(void)
{
    // Default on (empty / unrecognised → enabled) so a fresh device
    // tracks the released stream. Only an explicit "0" freezes it,
    // typically after a manual firmware upload from the web UI.
    return s_config.auto_update[0] != '0';
}
const char *config_contact_host_override(void) { return s_config.contact_host; }
int         config_contact_port_override(void) { return s_config.contact_port; }
const char *config_phoneblock_base_url(void) { return s_config.pb_base_url; }
const char *config_phoneblock_token(void)    { return s_config.pb_token; }
int         config_min_direct_votes(void)     { return s_config.min_direct_votes; }
int         config_min_range_votes(void)      { return s_config.min_range_votes; }
const char *config_last_failed_ota(void)     { return s_config.last_failed_ota; }

esp_err_t config_set_last_failed_ota(const char *version)
{
    nvs_handle_t h;
    esp_err_t err = nvs_open(NS, NVS_READWRITE, &h);
    if (err != ESP_OK) return err;
    if (version == NULL || version[0] == '\0') {
        // Tolerate "key never written" — that's the steady state.
        err = nvs_erase_key(h, K_LAST_FAIL_OTA);
        if (err == ESP_ERR_NVS_NOT_FOUND) err = ESP_OK;
        s_config.last_failed_ota[0] = '\0';
    } else {
        err = nvs_set_str(h, K_LAST_FAIL_OTA, version);
        if (err == ESP_OK) {
            copy_default(s_config.last_failed_ota,
                         sizeof(s_config.last_failed_ota), version);
        }
    }
    if (err == ESP_OK) err = nvs_commit(h);
    nvs_close(h);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "config_set_last_failed_ota: %s", esp_err_to_name(err));
    }
    return err;
}

// --- Updating -------------------------------------------------------

static esp_err_t set_str_if(nvs_handle_t h, const char *key, const char *val,
                            char *cache, size_t cap)
{
    if (!val) return ESP_OK;
    esp_err_t err = nvs_set_str(h, key, val);
    if (err == ESP_OK) copy_default(cache, cap, val);
    return err;
}

static esp_err_t set_int_if(nvs_handle_t h, const char *key, int val, int *cache)
{
    if (val == 0) return ESP_OK;
    esp_err_t err = nvs_set_i32(h, key, val);
    if (err == ESP_OK) *cache = val;
    return err;
}

// Like set_int_if, but the caller decides explicitly via `has_value`
// whether to write — for fields where 0 is a meaningful value (e.g.
// "range-block disabled") and can't share the "0 = unchanged" shortcut.
static esp_err_t set_int_explicit(nvs_handle_t h, const char *key,
                                  bool has_value, int val, int *cache)
{
    if (!has_value) return ESP_OK;
    esp_err_t err = nvs_set_i32(h, key, val);
    if (err == ESP_OK) *cache = val;
    return err;
}

esp_err_t config_erase(void)
{
    nvs_handle_t h;
    esp_err_t err = nvs_open(NS, NVS_READWRITE, &h);
    if (err != ESP_OK) return err;
    err = nvs_erase_all(h);
    if (err == ESP_OK) err = nvs_commit(h);
    nvs_close(h);
    ESP_LOGI(TAG, "config_erase: %s", esp_err_to_name(err));
    return err;
}

void config_dongle_username(char *out, size_t cap)
{
    if (!out || cap == 0) return;
    strncpy(out, "phoneblock-dongle", cap - 1);
    out[cap - 1] = '\0';
}

esp_err_t config_update(const config_update_t *u)
{
    nvs_handle_t h;
    esp_err_t err = nvs_open(NS, NVS_READWRITE, &h);
    if (err != ESP_OK) return err;

    err = set_str_if(h, K_SIP_HOST, u->sip_host,
                     s_config.sip_host, sizeof(s_config.sip_host));
    if (err == ESP_OK) err = set_int_if(h, K_SIP_PORT, u->sip_port, &s_config.sip_port);
    if (err == ESP_OK) err = set_str_if(h, K_SIP_USER, u->sip_user,
                                        s_config.sip_user, sizeof(s_config.sip_user));
    if (err == ESP_OK) err = set_str_if(h, K_SIP_PASS, u->sip_pass,
                                        s_config.sip_pass, sizeof(s_config.sip_pass));
    if (err == ESP_OK) err = set_int_if(h, K_SIP_EXPIRES, u->sip_expires, &s_config.sip_expires);
    if (err == ESP_OK) err = set_str_if(h, K_SIP_INT_NUM, u->sip_internal_number,
                                        s_config.sip_int_num, sizeof(s_config.sip_int_num));
    if (err == ESP_OK) err = set_str_if(h, K_SIP_TRANSP, u->sip_transport,
                                        s_config.sip_transp, sizeof(s_config.sip_transp));
    if (err == ESP_OK) err = set_str_if(h, K_SIP_AUTHUSER, u->sip_auth_user,
                                        s_config.sip_authuser, sizeof(s_config.sip_authuser));
    if (err == ESP_OK) err = set_str_if(h, K_SIP_OUTBOUND, u->sip_outbound,
                                        s_config.sip_outbound, sizeof(s_config.sip_outbound));
    if (err == ESP_OK) err = set_str_if(h, K_SIP_REALM, u->sip_realm,
                                        s_config.sip_realm, sizeof(s_config.sip_realm));
    if (err == ESP_OK) err = set_str_if(h, K_SIP_SRTP, u->sip_srtp,
                                        s_config.sip_srtp, sizeof(s_config.sip_srtp));
    if (err == ESP_OK) err = set_str_if(h, K_FB_APP_USER, u->fritzbox_app_user,
                                        s_config.fb_app_user, sizeof(s_config.fb_app_user));
    if (err == ESP_OK) err = set_str_if(h, K_FB_APP_PASS, u->fritzbox_app_pass,
                                        s_config.fb_app_pass, sizeof(s_config.fb_app_pass));
    if (err == ESP_OK) err = set_str_if(h, K_SYNC_ENABLED, u->sync_enabled,
                                        s_config.sync_enabled, sizeof(s_config.sync_enabled));
    if (err == ESP_OK) err = set_str_if(h, K_LOG_KNOWN, u->log_known_calls,
                                        s_config.log_known, sizeof(s_config.log_known));
    if (err == ESP_OK) err = set_str_if(h, K_AUTH_ENABLED, u->auth_enabled,
                                        s_config.auth_enabled, sizeof(s_config.auth_enabled));
    if (err == ESP_OK) err = set_str_if(h, K_AUTH_USER, u->auth_user,
                                        s_config.auth_user, sizeof(s_config.auth_user));
    if (err == ESP_OK) err = set_str_if(h, K_AUTH_PERSIST, u->auth_persist,
                                        s_config.auth_persist, sizeof(s_config.auth_persist));
    if (err == ESP_OK) err = set_str_if(h, K_AUTO_UPDATE, u->auto_update,
                                        s_config.auto_update, sizeof(s_config.auto_update));
    if (err == ESP_OK) err = set_str_if(h, K_PB_URL, u->phoneblock_base_url,
                                        s_config.pb_base_url, sizeof(s_config.pb_base_url));
    if (err == ESP_OK) err = set_str_if(h, K_PB_TOKEN, u->phoneblock_token,
                                        s_config.pb_token, sizeof(s_config.pb_token));
    if (err == ESP_OK) err = set_int_if(h, K_MIN_DIRECT, u->min_direct_votes,
                                        &s_config.min_direct_votes);
    if (err == ESP_OK) err = set_int_explicit(h, K_MIN_RANGE,
                                              u->has_min_range_votes,
                                              u->min_range_votes,
                                              &s_config.min_range_votes);

    if (err == ESP_OK) err = nvs_commit(h);
    nvs_close(h);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "config_update: %s", esp_err_to_name(err));
    } else {
        ESP_LOGI(TAG, "config_update: committed");
    }
    return err;
}
