#include "wifi.h"

#include <stdio.h>
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
static esp_netif_t       *s_sta_netif;
static bool               s_started;
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
// security profile no longer matches the AP (Reason 210), or by an
// AP that's transiently unreachable (router reboot, Wi-Fi outage).
static int                s_consecutive_disconnects;

// Trigger threshold: ~30 disconnects ≈ 75 s on a typical router that
// rejects with Reason 210 every ~2.4 s. Long enough to ride out a
// brief AP hiccup without changing state, short enough that a
// genuinely stuck dongle reaches the failsafe within ~75 s.
#define FAILSAFE_DISCONNECT_THRESHOLD 30

// Failsafe state. Once entered, a dedicated task alternates between a
// WPS-listening phase (so the user can re-pair if the AP genuinely
// changed) and a stored-credentials reconnect phase (so a transient
// AP outage recovers automatically without wiping NVS). Stays active
// until either WPS_ER_SUCCESS persists fresh credentials or GOT_IP
// proves the stored ones still associate.
//
// Crucially, failsafe never calls esp_wifi_restore(): the previously
// flashed credentials remain in NVS throughout the alternation, so a
// router that comes back after 5 minutes brings the dongle back
// without a re-pairing dance.
static bool               s_failsafe_active;
static bool               s_failsafe_in_wps;
static TaskHandle_t       s_failsafe_task;

// Phase length per side of the alternation. 2 min matches the typical
// WPS-PBC walk-time of a router, so a failsafe-WPS phase covers a
// full WPS attempt window; failsafe-connect then has the same
// duration to ride out a transient outage.
#define FAILSAFE_PHASE_MS   (2 * 60 * 1000)
#define FAILSAFE_NOTIF_EXIT 1

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
    // The driver fires WPS_ER_FAILED and WPS_ER_TIMEOUT (and sometimes
    // a duplicate WPS_ER_FAILED on the way out of "run" state) within
    // a few ms of each other. Without this guard each event spawns its
    // own wps_restart_task, the tasks race on
    // esp_wifi_wps_disable / esp_wifi_wps_start, the second one's
    // disable kills the first one's enable, and the driver wedges in
    // "STA is connecting, scan are not allowed!". Skipping further
    // schedules until the in-flight task has fired start_wps()
    // collapses the duplicate events to a single restart.
    if (s_wps_restart_pending) {
        return;
    }
    // Stay in "WPS-owned" state until the restart task has actually
    // fired start_wps(); otherwise the DISCONNECTED event from
    // esp_wifi_disconnect() races with esp_wifi_wps_start().
    s_wps_active = false;
    s_wps_restart_pending = true;
    xTaskCreate(wps_restart_task, "wps_restart", 3072, NULL, 3, NULL);
}

// Failsafe phase transitions. Both reuse s_wps_restart_pending as a
// "transition in progress" gate so the disconnect handler doesn't fire
// a competing esp_wifi_connect() while the driver is mid-state-change.
static void failsafe_to_wps(void)
{
    s_failsafe_in_wps = true;
    s_wps_active = false;
    s_wps_restart_pending = true;
    esp_wifi_disconnect();
    vTaskDelay(pdMS_TO_TICKS(500));
    esp_wifi_wps_disable();  // safe even if not currently enabled
    s_wps_restart_pending = false;
    start_wps();             // sets s_wps_active = true
}

static void failsafe_to_connect(void)
{
    s_failsafe_in_wps = false;
    s_wps_restart_pending = true;
    esp_wifi_wps_disable();
    s_wps_active = false;
    esp_wifi_disconnect();
    vTaskDelay(pdMS_TO_TICKS(500));
    s_wps_restart_pending = false;
    esp_wifi_connect();
}

static void failsafe_task(void *arg)
{
    (void)arg;
    // Enter via WPS phase: at the moment of failsafe entry the stored
    // credentials have just failed FAILSAFE_DISCONNECT_THRESHOLD times
    // in a row, so betting on them again immediately would spin
    // another 75 s of useless retries. Listen for WPS first; if the
    // user doesn't press the button within a phase, swing back to
    // give the credentials a fresh try (the AP may have come back).
    failsafe_to_wps();

    while (s_failsafe_active) {
        uint32_t notif = 0;
        BaseType_t ret = xTaskNotifyWait(0, ULONG_MAX, &notif,
                                         pdMS_TO_TICKS(FAILSAFE_PHASE_MS));
        if (!s_failsafe_active) break;
        if (ret == pdTRUE && notif == FAILSAFE_NOTIF_EXIT) break;

        if (s_failsafe_in_wps) {
            ESP_LOGI(TAG, "failsafe: WPS phase elapsed — "
                          "trying stored credentials for %d s",
                     FAILSAFE_PHASE_MS / 1000);
            failsafe_to_connect();
        } else {
            ESP_LOGI(TAG, "failsafe: stored-credentials phase elapsed — "
                          "back to WPS-listening for %d s",
                     FAILSAFE_PHASE_MS / 1000);
            failsafe_to_wps();
        }
    }

    s_failsafe_in_wps = false;
    s_failsafe_task = NULL;
    vTaskDelete(NULL);
}

static void schedule_failsafe(void)
{
    if (s_failsafe_active) return;
    s_failsafe_active = true;
    s_consecutive_disconnects = 0;
    ESP_LOGW(TAG, "stored credentials not associating — entering failsafe "
                  "(alternating WPS-listening and stored-credentials "
                  "retry every %d s; credentials remain in NVS)",
             FAILSAFE_PHASE_MS / 1000);
    xTaskCreate(failsafe_task, "wifi_failsafe", 3072, NULL, 3, &s_failsafe_task);
}

static void exit_failsafe(void)
{
    if (!s_failsafe_active) return;
    ESP_LOGI(TAG, "exiting failsafe");
    s_failsafe_active = false;
    TaskHandle_t t = s_failsafe_task;
    if (t) {
        xTaskNotify(t, FAILSAFE_NOTIF_EXIT, eSetValueWithOverwrite);
    }
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
                if (s_failsafe_active) {
                    // Failsafe-connect phase: keep retrying with
                    // stored credentials. The phase timer drives the
                    // next state change; don't escalate further.
                    esp_wifi_connect();
                } else {
                    s_consecutive_disconnects++;

                    if (s_consecutive_disconnects >= FAILSAFE_DISCONNECT_THRESHOLD) {
                        ESP_LOGW(TAG,
                            "no successful association after %d disconnects "
                            "(last reason %d) — entering failsafe",
                            s_consecutive_disconnects, d ? d->reason : -1);
                        schedule_failsafe();
                    } else {
                        esp_wifi_connect();
                    }
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
            // If we're here via the failsafe-WPS phase, the user just
            // re-paired and the new credentials are persisted. Tear
            // down the alternation so the connect phase doesn't kick
            // in 2 minutes later and disrupt the fresh association.
            exit_failsafe();
            esp_wifi_connect();
            break;
        }

        case WIFI_EVENT_STA_WPS_ER_FAILED:
            if (s_failsafe_active) {
                // Failsafe drives WPS via its own phase timer. Ignore
                // the WPS-internal failure so the failsafe task and
                // schedule_wps_restart don't race on driver state.
                ESP_LOGI(TAG, "WPS failed inside failsafe — phase timer drives the next state");
            } else {
                ESP_LOGW(TAG, "WPS failed — restarting pairing mode");
                schedule_wps_restart();
            }
            break;

        case WIFI_EVENT_STA_WPS_ER_TIMEOUT:
            if (s_failsafe_active) {
                ESP_LOGI(TAG, "WPS timed out inside failsafe — phase timer drives the next state");
            } else {
                ESP_LOGW(TAG, "WPS timed out — restarting pairing mode");
                schedule_wps_restart();
            }
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
    // A single successful association proves the credentials
    // currently in NVS are usable. Reset the disconnect counter so a
    // later transient outage doesn't spuriously trigger another
    // failsafe entry, and stop the failsafe alternation if it had
    // kicked in (the AP came back, the stored creds work again — no
    // need to keep cycling into WPS).
    s_consecutive_disconnects = 0;
    exit_failsafe();
    xEventGroupSetBits(s_events, WIFI_CONNECTED_BIT);
}

bool wifi_is_wps_active(void) { return s_wps_active || s_wps_restart_pending; }
bool wifi_has_ip(void)        { return s_has_ip; }
bool wifi_sta_started(void)   { return s_started; }

bool wifi_get_ip_str(char *buf, size_t cap)
{
    if (s_sta_netif == NULL || !s_has_ip) {
        return false;
    }
    esp_netif_ip_info_t info;
    if (esp_netif_get_ip_info(s_sta_netif, &info) != ESP_OK) {
        return false;
    }
    snprintf(buf, cap, IPSTR, IP2STR(&info.ip));
    return true;
}

// Take the STA away from the pairing machinery (WPS round, failsafe
// alternation) so a direct esp_wifi_connect() or scan can run without
// racing it. Mirrors the transition pattern of failsafe_to_connect():
// s_wps_restart_pending gates the disconnect handler for the duration.
static void teardown_pairing(void)
{
    exit_failsafe();
    // The failsafe task may be mid-transition (inside a 500 ms
    // vTaskDelay in failsafe_to_wps) and would re-enable WPS right
    // after we disable it below — wait for it to drain first. Same
    // for an in-flight wps_restart_task.
    for (int i = 0; i < 30 && s_failsafe_task != NULL; i++) {
        vTaskDelay(pdMS_TO_TICKS(100));
    }
    for (int i = 0; i < 20 && s_wps_restart_pending; i++) {
        vTaskDelay(pdMS_TO_TICKS(100));
    }

    s_wps_restart_pending = true;
    esp_wifi_wps_disable();  // safe even if not currently enabled
    s_wps_active = false;
    esp_wifi_disconnect();
    vTaskDelay(pdMS_TO_TICKS(500));
    s_wps_restart_pending = false;
}

esp_err_t wifi_set_credentials(const char *ssid, const char *password)
{
    wifi_config_t cfg = { 0 };
    size_t ssid_len = strlen(ssid);
    size_t pass_len = strlen(password);
    if (ssid_len == 0 || ssid_len > sizeof(cfg.sta.ssid)
            || pass_len > sizeof(cfg.sta.password) - 1) {
        return ESP_ERR_INVALID_ARG;
    }
    if (!s_started) {
        return ESP_ERR_WIFI_NOT_STARTED;
    }

    ESP_LOGI(TAG, "switching to provisioned credentials (SSID=%s)", ssid);
    teardown_pairing();

    memcpy(cfg.sta.ssid, ssid, ssid_len);
    memcpy(cfg.sta.password, password, pass_len);
    // Same defensive security baseline as seed_baked_creds() — without
    // PMF capability the STA gets Reason 210 against any modern
    // WPA2/WPA3-transitional AP. Open networks need the OPEN
    // threshold, or the driver refuses to associate without a key.
    cfg.sta.threshold.authmode = pass_len ? WIFI_AUTH_WPA2_PSK
                                          : WIFI_AUTH_OPEN;
    cfg.sta.pmf_cfg.capable    = true;
    cfg.sta.pmf_cfg.required   = false;

    // WIFI_STORAGE_FLASH persists this to NVS for the next boot.
    esp_err_t err = esp_wifi_set_config(WIFI_IF_STA, &cfg);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "esp_wifi_set_config: %s", esp_err_to_name(err));
        return err;
    }

    // Fresh credentials get a fresh failsafe budget.
    s_consecutive_disconnects = 0;
    return esp_wifi_connect();
}

int wifi_scan(wifi_ap_record_t *records, int max_records)
{
    if (!s_started) {
        return -1;
    }

    // Scanning coexists with a normal (connected or reconnecting)
    // station, but not with an active WPS round — the driver rejects
    // it. Suspend WPS for the scan and resume afterwards, so closing
    // the provisioning dialog without sending credentials leaves the
    // device in the same pairing mode it was in before.
    bool resume_wps = s_wps_active || s_wps_restart_pending
            || (s_failsafe_active && s_failsafe_in_wps);
    if (resume_wps) {
        teardown_pairing();
    }

    int result = -1;
    bool resume_connect = false;
    esp_err_t err = esp_wifi_scan_start(NULL, true /* block */);
    if (err == ESP_ERR_WIFI_STATE && !s_has_ip && !resume_wps) {
        // The STA is mid-association — typically the reconnect loop on
        // stored credentials that no longer work (e.g. Reason 210),
        // which is exactly the situation provisioning gets the user
        // out of. Pause the loop for the scan and resume afterwards.
        teardown_pairing();
        resume_connect = true;
        err = esp_wifi_scan_start(NULL, true);
    }
    if (err == ESP_OK) {
        uint16_t n = (uint16_t)max_records;
        // Also frees the driver-internal scan list.
        err = esp_wifi_scan_get_ap_records(&n, records);
        result = (err == ESP_OK) ? n : -1;
    }
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "scan failed: %s", esp_err_to_name(err));
    }

    if (resume_wps) {
        start_wps();
    } else if (resume_connect) {
        esp_wifi_connect();
    }
    return result;
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
    s_sta_netif = sta_netif;

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
    s_started = true;

    if (!have_stored && !have_baked) {
        start_wps();
    }

    xEventGroupWaitBits(s_events, WIFI_CONNECTED_BIT,
                        pdFALSE, pdTRUE, portMAX_DELAY);
    return ESP_OK;
}
