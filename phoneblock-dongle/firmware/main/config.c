#include "config.h"

#include <string.h>

#include "esp_log.h"
#include "esp_mac.h"
#include "esp_random.h"
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
#define K_CONTACT_HOST  "contact_host"
#define K_CONTACT_PORT  "contact_port"
#define K_PB_URL        "pb_url"
#define K_PB_TOKEN      "pb_token"

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
    char contact_host[64];
    int  contact_port;
    char pb_base_url[128];
    char pb_token[64];
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
        copy_default(s_config.contact_host, sizeof(s_config.contact_host), CONFIG_SIP_CONTACT_HOST_OVERRIDE);
        s_config.contact_port = CONFIG_SIP_CONTACT_PORT_OVERRIDE;
        copy_default(s_config.pb_base_url,  sizeof(s_config.pb_base_url),  CONFIG_PHONEBLOCK_BASE_URL);
        s_config.pb_token[0]  = '\0';
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
    load_str(h, K_CONTACT_HOST, CONFIG_SIP_CONTACT_HOST_OVERRIDE,
             s_config.contact_host, sizeof(s_config.contact_host));
    s_config.contact_port = load_int(h, K_CONTACT_PORT, CONFIG_SIP_CONTACT_PORT_OVERRIDE);
    load_str(h, K_PB_URL,       CONFIG_PHONEBLOCK_BASE_URL,
             s_config.pb_base_url,  sizeof(s_config.pb_base_url));
    load_str(h, K_PB_TOKEN,     "",
             s_config.pb_token,     sizeof(s_config.pb_token));
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
const char *config_contact_host_override(void) { return s_config.contact_host; }
int         config_contact_port_override(void) { return s_config.contact_port; }
const char *config_phoneblock_base_url(void) { return s_config.pb_base_url; }
const char *config_phoneblock_token(void)    { return s_config.pb_token; }

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

#define K_DONGLE_USER   "dongle_user"

void config_dongle_username(char *out, size_t cap)
{
    if (!out || cap == 0) return;
    out[0] = '\0';

    nvs_handle_t h;
    esp_err_t err = nvs_open(NS, NVS_READWRITE, &h);
    if (err != ESP_OK) {
        // Fall back to a fresh suffix just this once — NVS failure
        // should never drop us into an empty username.
        uint32_t r = esp_random();
        snprintf(out, cap, "phoneblock-%02x%02x%02x",
                 (unsigned)(r & 0xff), (unsigned)((r >> 8) & 0xff),
                 (unsigned)((r >> 16) & 0xff));
        return;
    }

    size_t len = cap;
    if (nvs_get_str(h, K_DONGLE_USER, out, &len) == ESP_OK && out[0]) {
        nvs_close(h);
        return;
    }

    // Not stored yet. Build from the eFuse base MAC; on QEMU that's
    // often zeroed, use esp_random to still get something distinct.
    uint8_t mac[6] = {0};
    esp_read_mac(mac, ESP_MAC_BASE);
    if (mac[3] == 0 && mac[4] == 0 && mac[5] == 0) {
        uint32_t r = esp_random();
        mac[3] = (uint8_t)(r);
        mac[4] = (uint8_t)(r >> 8);
        mac[5] = (uint8_t)(r >> 16);
    }
    snprintf(out, cap, "phoneblock-%02x%02x%02x", mac[3], mac[4], mac[5]);

    if (nvs_set_str(h, K_DONGLE_USER, out) == ESP_OK) {
        nvs_commit(h);
        ESP_LOGI(TAG, "dongle username generated and persisted: %s", out);
    }
    nvs_close(h);
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
    if (err == ESP_OK) err = set_str_if(h, K_PB_URL, u->phoneblock_base_url,
                                        s_config.pb_base_url, sizeof(s_config.pb_base_url));
    if (err == ESP_OK) err = set_str_if(h, K_PB_TOKEN, u->phoneblock_token,
                                        s_config.pb_token, sizeof(s_config.pb_token));

    if (err == ESP_OK) err = nvs_commit(h);
    nvs_close(h);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "config_update: %s", esp_err_to_name(err));
    } else {
        ESP_LOGI(TAG, "config_update: committed");
    }
    return err;
}
