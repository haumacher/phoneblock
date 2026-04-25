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
static bool               s_has_ip;
// Guards the time between esp_wifi_disconnect() and the next
// esp_wifi_wps_start(): during this window the DISCONNECTED event
// must not trigger a plain esp_wifi_connect(), or the STA races
// with the about-to-fire WPS restart and the driver logs
// "STA is connecting, scan are not allowed!".
static bool               s_wps_restart_pending;

// Number of consecutive WIFI_EVENT_STA_DISCONNECTED events without an
// interleaving GOT_IP. Reset by on_got_ip(). Used to detect a stuck
// reconnect loop — typically caused by stored credentials whose
// security profile no longer matches the AP (Reason 210).
static int                s_consecutive_disconnects;

// Once-per-power-cycle latch so a recovery wipe can't flash NVS in a
// runaway loop if the recovered WPS pairing fails for an unrelated
// reason. Cleared only by a power cycle.
static bool               s_recovery_done;

// Trigger threshold: ~30 disconnects ≈ 75 s on a typical router that
// rejects with Reason 210 every ~2.4 s. Long enough to ride out a
// transient AP reboot without burning NVS, short enough that a
// genuinely stuck dongle recovers within a couple of minutes.
#define RECOVERY_DISCONNECT_THRESHOLD 30

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
    s_wps_restart_pending = false;
    start_wps();
    vTaskDelete(NULL);
}

static void schedule_wps_restart(void)
{
    // Stay in "WPS-owned" state until the restart task has actually
    // fired start_wps(); otherwise the DISCONNECTED event from
    // esp_wifi_disconnect() races with esp_wifi_wps_start().
    s_wps_active = false;
    s_wps_restart_pending = true;
    xTaskCreate(wps_restart_task, "wps_restart", 3072, NULL, 3, NULL);
}

// Last-resort recovery for a dongle whose stored credentials no longer
// associate (typical cause: AP firmware update changed the security
// profile, e.g. WPA2 → WPA2/WPA3-Transitional with PMF, and the
// previously stored config has no PMF capability bit set, so the STA
// gets Reason 210 — NO_AP_FOUND_W_COMPATIBLE_SECURITY — on every
// reconnect attempt). Wipes NVS-stored credentials via
// esp_wifi_restore() and restarts WPS pairing so the user can re-pair
// without USB-flashing the device.
//
// Same task-based shape as wps_restart_task: get out of the event-
// handler context, disconnect cleanly, then change driver state.
static void recovery_task(void *arg)
{
    (void)arg;

    ESP_LOGW(TAG, "wiping stored Wi-Fi credentials and restarting WPS pairing");
    esp_wifi_disconnect();
    vTaskDelay(pdMS_TO_TICKS(500));

    // Clears the sta_config that's been getting us nowhere, including
    // SSID, passphrase, and any security flags. Next boot would land
    // in the no-credentials path; we save the user that reboot by
    // jumping straight into WPS here.
    //
    // Side-effect not obvious from the API name: esp_wifi_restore()
    // also stops the WiFi driver and resets the storage mode + STA
    // mode to defaults. esp_wifi_wps_enable() requires the driver to
    // be running in STA mode, so we have to re-establish that
    // sequence before start_wps() — otherwise the next call panics
    // with ESP_ERR_WIFI_STATE.
    esp_err_t err = esp_wifi_restore();
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "esp_wifi_restore: %s", esp_err_to_name(err));
    }
    ESP_ERROR_CHECK(esp_wifi_set_storage(WIFI_STORAGE_FLASH));
    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
    ESP_ERROR_CHECK(esp_wifi_start());

    // Order matters: esp_wifi_start() fires STA_START asynchronously.
    // The on_wifi_event handler treats !s_wps_active && !s_wps_restart_pending
    // as the cue to call esp_wifi_connect(). Keep s_wps_restart_pending
    // true until start_wps() has set s_wps_active = true, so neither
    // window is open.
    s_consecutive_disconnects = 0;
    start_wps();
    s_wps_restart_pending = false;
    vTaskDelete(NULL);
}

static void schedule_recovery(void)
{
    // Same wedge-prevention pattern as schedule_wps_restart(): keep
    // the disconnect handler from firing esp_wifi_connect() while the
    // recovery task is mid-cleanup.
    s_recovery_done = true;
    s_wps_active = false;
    s_wps_restart_pending = true;
    xTaskCreate(recovery_task, "wifi_recovery", 3072, NULL, 3, NULL);
}

static void on_wifi_event(void *arg, esp_event_base_t base, int32_t id, void *data)
{
    switch (id) {
        case WIFI_EVENT_STA_START:
            if (!s_wps_active && !s_wps_restart_pending) {
                esp_wifi_connect();
            }
            break;

        case WIFI_EVENT_STA_DISCONNECTED: {
            wifi_event_sta_disconnected_t *d = data;
            ESP_LOGW(TAG, "disconnected (reason %d)", d ? d->reason : -1);
            s_has_ip = false;

            // Only count disconnects that happen during normal
            // reconnect attempts. WPS-driven disconnects are noise
            // for this counter — they reset on every WPS restart.
            if (!s_wps_active && !s_wps_restart_pending) {
                s_consecutive_disconnects++;

                if (!s_recovery_done
                        && s_consecutive_disconnects >= RECOVERY_DISCONNECT_THRESHOLD) {
                    ESP_LOGW(TAG,
                        "no successful association after %d disconnects "
                        "(last reason %d) — wiping stored credentials and "
                        "falling back to WPS pairing",
                        s_consecutive_disconnects, d ? d->reason : -1);
                    schedule_recovery();
                } else {
                    esp_wifi_connect();
                }
            }
            break;
        }

        case WIFI_EVENT_STA_WPS_ER_SUCCESS: {
            // The WPS state machine has already populated the driver's
            // internal sta_config with everything derived from the
            // M2/M8 exchange — SSID, passphrase, AND the security
            // fields (threshold.authmode, pmf_cfg, sae_pwe_h2e, …)
            // needed to associate with the AP we just paired with.
            //
            // For the single-AP case (ap_cred_cnt <= 1) we therefore
            // do NOT touch sta_config — leaving the driver-populated
            // version in place, which esp_wifi_set_storage(FLASH)
            // also persists to NVS for the next boot. Earlier code
            // here built a fresh wifi_config_t = {0} and copied only
            // SSID/passphrase into it; that wiped the security
            // fields, so on the next boot the STA loaded a config
            // with no PMF capability, no auth-mode threshold, and
            // got Reason 210 on every reconnect against any modern
            // PMF-enabled AP. See recovery path above for the
            // user-visible symptom.
            //
            // For the rare multi-AP case (ap_cred_cnt > 1) we still
            // need to pick one credential set. We then read-modify-
            // write: get the driver's cfg first, override only the
            // SSID/passphrase fields, set it back. Security fields
            // stay intact.
            wifi_event_sta_wps_er_success_t *evt = data;
            if (evt && evt->ap_cred_cnt > 1) {
                wifi_config_t cfg;
                if (esp_wifi_get_config(WIFI_IF_STA, &cfg) == ESP_OK) {
                    memset(cfg.sta.ssid,     0, sizeof(cfg.sta.ssid));
                    memset(cfg.sta.password, 0, sizeof(cfg.sta.password));
                    memcpy(cfg.sta.ssid,
                           evt->ap_cred[0].ssid,
                           sizeof(cfg.sta.ssid));
                    memcpy(cfg.sta.password,
                           evt->ap_cred[0].passphrase,
                           sizeof(cfg.sta.password));
                    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &cfg));
                    ESP_LOGI(TAG, "WPS success — picked SSID=%s of %d offered",
                             cfg.sta.ssid, evt->ap_cred_cnt);
                } else {
                    ESP_LOGW(TAG, "esp_wifi_get_config failed — "
                                  "trusting driver-internal cfg");
                }
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
    s_has_ip = true;
    // A single successful association proves the stored credentials
    // are usable. Reset the recovery counter so a later transient
    // outage doesn't spuriously trigger a credential wipe.
    s_consecutive_disconnects = 0;
    xEventGroupSetBits(s_events, WIFI_CONNECTED_BIT);
}

bool wifi_is_wps_active(void) { return s_wps_active || s_wps_restart_pending; }
bool wifi_has_ip(void)        { return s_has_ip; }

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

    // Defensive baseline for any STA config we build from scratch.
    // Without these the driver would not associate with WPA2/WPA3-
    // Transitional APs that have PMF "capable" — symptom is Reason
    // 210 (NO_AP_FOUND_W_COMPATIBLE_SECURITY) on every connect.
    // The WPS-driven path goes through the driver-populated cfg and
    // doesn't need this; only here, where we hand in a freshly
    // zero-initialised struct, do we have to fill the defaults.
    cfg.sta.threshold.authmode = WIFI_AUTH_WPA2_PSK;
    cfg.sta.pmf_cfg.capable    = true;
    cfg.sta.pmf_cfg.required   = false;

    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &cfg));
}

esp_err_t wifi_connect(void)
{
    s_events = xEventGroupCreate();

    esp_netif_t *sta_netif = esp_netif_create_default_wifi_sta();

    // DHCP option 12 (host-name) is built from the netif's hostname at
    // the time DHCPDISCOVER/REQUEST is sent. Setting it later (e.g.
    // after GOT_IP) is too late: the Fritz!Box has already cached the
    // lease under the lwIP default "espressif" and pins the Heimnetz
    // name to that first value. Set it before esp_wifi_start() so the
    // very first DHCP exchange announces "answerbot".
    if (sta_netif) {
        esp_err_t err = esp_netif_set_hostname(sta_netif, "answerbot");
        if (err != ESP_OK) {
            ESP_LOGW(TAG, "esp_netif_set_hostname: %s", esp_err_to_name(err));
        }
    }

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
