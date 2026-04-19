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
#define K_CONTACT_HOST  "contact_host"
#define K_CONTACT_PORT  "contact_port"
#define K_PB_URL        "pb_url"
#define K_PB_TOKEN      "pb_token"
#define K_PB_TEST       "pb_test"

typedef struct {
    char sip_host[64];
    int  sip_port;
    char sip_user[32];
    char sip_pass[64];
    int  sip_expires;
    char contact_host[64];
    int  contact_port;
    char pb_base_url[128];
    char pb_token[64];
    char pb_test[32];
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
        s_config.sip_host[0]  = '\0';
        s_config.sip_port     = CONFIG_SIP_REGISTRAR_PORT;
        s_config.sip_user[0]  = '\0';
        s_config.sip_pass[0]  = '\0';
        s_config.sip_expires  = CONFIG_SIP_EXPIRES;
        copy_default(s_config.contact_host, sizeof(s_config.contact_host), CONFIG_SIP_CONTACT_HOST_OVERRIDE);
        s_config.contact_port = CONFIG_SIP_CONTACT_PORT_OVERRIDE;
        copy_default(s_config.pb_base_url,  sizeof(s_config.pb_base_url),  CONFIG_PHONEBLOCK_BASE_URL);
        s_config.pb_token[0]  = '\0';
        copy_default(s_config.pb_test,      sizeof(s_config.pb_test),      CONFIG_PHONEBLOCK_TEST_NUMBER);
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
    load_str(h, K_CONTACT_HOST, CONFIG_SIP_CONTACT_HOST_OVERRIDE,
             s_config.contact_host, sizeof(s_config.contact_host));
    s_config.contact_port = load_int(h, K_CONTACT_PORT, CONFIG_SIP_CONTACT_PORT_OVERRIDE);
    load_str(h, K_PB_URL,       CONFIG_PHONEBLOCK_BASE_URL,
             s_config.pb_base_url,  sizeof(s_config.pb_base_url));
    load_str(h, K_PB_TOKEN,     "",
             s_config.pb_token,     sizeof(s_config.pb_token));
    load_str(h, K_PB_TEST,      CONFIG_PHONEBLOCK_TEST_NUMBER,
             s_config.pb_test,      sizeof(s_config.pb_test));
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
const char *config_contact_host_override(void) { return s_config.contact_host; }
int         config_contact_port_override(void) { return s_config.contact_port; }
const char *config_phoneblock_base_url(void) { return s_config.pb_base_url; }
const char *config_phoneblock_token(void)    { return s_config.pb_token; }
const char *config_phoneblock_test_number(void) { return s_config.pb_test; }

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
