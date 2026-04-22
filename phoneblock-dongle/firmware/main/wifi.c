#include "wifi.h"

#include <string.h>

#include "esp_event.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "esp_wifi.h"
#include "esp_wps.h"
#include "freertos/FreeRTOS.h"
#include "freertos/event_groups.h"
#include "freertos/task.h"
#include "sdkconfig.h"

static const char *TAG = "wifi";

#define WIFI_CONNECTED_BIT BIT0

#ifdef CONFIG_EXAMPLE_WIFI_SSID
    #define BAKED_SSID     CONFIG_EXAMPLE_WIFI_SSID
    #define BAKED_PASSWORD CONFIG_EXAMPLE_WIFI_PASSWORD
#else
    #define BAKED_SSID     ""
    #define BAKED_PASSWORD ""
#endif

static EventGroupHandle_t s_events;
static bool               s_wps_active;

static void start_wps(void)
{
    esp_wps_config_t cfg = WPS_CONFIG_INIT_DEFAULT(WPS_TYPE_PBC);
    ESP_LOGI(TAG, "starting WPS-PBC — press the WPS/pairing button on the router now");
    s_wps_active = true;
    ESP_ERROR_CHECK(esp_wifi_wps_enable(&cfg));
    ESP_ERROR_CHECK(esp_wifi_wps_start(0));
}

// WPS retry runs on a throwaway task rather than from the Wi-Fi
// event handler: calling esp_wifi_wps_enable right after a timeout
// while the STA was still mid-association produced
// "STA is connecting, scan are not allowed!" and left the driver in
// a wedged state. Disconnecting first + a short grace period lets
// the driver settle before the next WPS round begins.
static void wps_restart_task(void *arg)
{
    (void)arg;
    esp_wifi_disconnect();
    vTaskDelay(pdMS_TO_TICKS(500));
    esp_wifi_wps_disable();
    start_wps();
    vTaskDelete(NULL);
}

static void schedule_wps_restart(void)
{
    s_wps_active = false;
    xTaskCreate(wps_restart_task, "wps_restart", 3072, NULL, 3, NULL);
}

static void on_wifi_event(void *arg, esp_event_base_t base, int32_t id, void *data)
{
    switch (id) {
        case WIFI_EVENT_STA_START:
            if (!s_wps_active) {
                esp_wifi_connect();
            }
            break;

        case WIFI_EVENT_STA_DISCONNECTED: {
            wifi_event_sta_disconnected_t *d = data;
            ESP_LOGW(TAG, "disconnected (reason %d)", d ? d->reason : -1);
            if (!s_wps_active) {
                esp_wifi_connect();
            }
            break;
        }

        case WIFI_EVENT_STA_WPS_ER_SUCCESS: {
            // Most routers hand us a single credential set here, and
            // the WPS driver also pushes it into the default
            // sta_config internally. The explicit copy covers
            // multi-AP scenarios where the event carries ap_cred_cnt
            // > 0 and we need to pick one.
            wifi_event_sta_wps_er_success_t *evt = data;
            if (evt && evt->ap_cred_cnt > 0) {
                wifi_config_t cfg = { 0 };
                memcpy(cfg.sta.ssid,
                       evt->ap_cred[0].ssid,
                       sizeof(cfg.sta.ssid));
                memcpy(cfg.sta.password,
                       evt->ap_cred[0].passphrase,
                       sizeof(cfg.sta.password));
                ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &cfg));
                ESP_LOGI(TAG, "WPS success — SSID=%s (persisted)", cfg.sta.ssid);
            } else {
                ESP_LOGI(TAG, "WPS success — credentials auto-persisted");
            }
            ESP_ERROR_CHECK(esp_wifi_wps_disable());
            s_wps_active = false;
            esp_wifi_connect();
            break;
        }

        case WIFI_EVENT_STA_WPS_ER_FAILED:
            ESP_LOGW(TAG, "WPS failed — restarting pairing mode");
            schedule_wps_restart();
            break;

        case WIFI_EVENT_STA_WPS_ER_TIMEOUT:
            ESP_LOGW(TAG, "WPS timed out — restarting pairing mode");
            schedule_wps_restart();
            break;

        default:
            break;
    }
}

static void on_got_ip(void *arg, esp_event_base_t base, int32_t id, void *data)
{
    ip_event_got_ip_t *e = data;
    ESP_LOGI(TAG, "got IP " IPSTR, IP2STR(&e->ip_info.ip));
    xEventGroupSetBits(s_events, WIFI_CONNECTED_BIT);
}

static bool load_persisted_creds(wifi_config_t *out)
{
    if (esp_wifi_get_config(WIFI_IF_STA, out) != ESP_OK) {
        return false;
    }
    return out->sta.ssid[0] != '\0';
}

static void seed_baked_creds(void)
{
    wifi_config_t cfg = { 0 };
    strncpy((char *)cfg.sta.ssid,     BAKED_SSID,     sizeof(cfg.sta.ssid) - 1);
    strncpy((char *)cfg.sta.password, BAKED_PASSWORD, sizeof(cfg.sta.password) - 1);
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &cfg));
}

esp_err_t wifi_connect(void)
{
    s_events = xEventGroupCreate();

    esp_netif_create_default_wifi_sta();

    wifi_init_config_t init_cfg = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&init_cfg));

    ESP_ERROR_CHECK(esp_event_handler_register(
            WIFI_EVENT, ESP_EVENT_ANY_ID, on_wifi_event, NULL));
    ESP_ERROR_CHECK(esp_event_handler_register(
            IP_EVENT, IP_EVENT_STA_GOT_IP, on_got_ip, NULL));

    // WIFI_STORAGE_FLASH lets the driver persist the sta_config across
    // reboots automatically, so after one successful WPS pairing the
    // dongle comes up connected on every subsequent boot.
    ESP_ERROR_CHECK(esp_wifi_set_storage(WIFI_STORAGE_FLASH));
    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));

    wifi_config_t stored;
    bool have_stored = load_persisted_creds(&stored);
    bool have_baked  = BAKED_SSID[0] != '\0';

    if (have_stored) {
        ESP_LOGI(TAG, "using stored credentials (SSID=%s)", stored.sta.ssid);
    } else if (have_baked) {
        ESP_LOGI(TAG, "seeding from baked credentials (SSID=%s)", BAKED_SSID);
        seed_baked_creds();
    } else {
        ESP_LOGI(TAG, "no credentials — WPS-PBC will start after WiFi init");
    }

    ESP_ERROR_CHECK(esp_wifi_start());

    if (!have_stored && !have_baked) {
        start_wps();
    }

    xEventGroupWaitBits(s_events, WIFI_CONNECTED_BIT,
                        pdFALSE, pdTRUE, portMAX_DELAY);
    return ESP_OK;
}
