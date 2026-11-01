#include "wifi.h"

#include <stdio.h>
#include <string.h>

#include "esp_event.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "esp_timer.h"
#include "esp_wifi.h"
#include "esp_wps.h"
#include "freertos/FreeRTOS.h"
#include "freertos/event_groups.h"
#include "freertos/task.h"
#include "sdkconfig.h"
#if CONFIG_ESP_WIFI_11KV_SUPPORT
#include "esp_rrm.h"
#include "esp_wnm.h"
#endif

#include "link_watchdog.h"
#include "rtp.h"
#include "sip_register.h"
#include "ticks_util.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

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

// A blocking wifi_scan() is running. The link watchdog stays out of the way
// meanwhile — a deliberate disconnect would abort the scan.
static volatile bool      s_scan_active;

// Link watchdog (#585, link_watchdog.h): the station holds an IP but is
// stuck on a distant mesh AP. The watchdog task disconnects once; the
// regular DISCONNECTED path below reconnects, and the connect's full-channel
// scan picks the strongest AP. s_wd_reconnect_pending marks that cycle so the
// disconnect is logged as intended (INFO) and the reconnect reports the new
// AP; the old AP is kept for that line.
static volatile bool      s_wd_reconnect_pending;
static uint8_t            s_wd_old_bssid[6];
static int                s_wd_old_rssi;

// 802.11v BSS transition in progress: the AP (Fritz!Box mesh steering) asked
// us to move and the supplicant is connecting to the target itself. The
// tick count of its start lets the watchdog task step in should the move
// never complete (BTM_ROAM_TIMEOUT_S).
static volatile bool      s_btm_roaming;
// For the web UI's link line: when the current association began (0: none)
// and how many established links were lost since boot.
static volatile int64_t   s_assoc_since_us;
static volatile uint32_t  s_link_drops;
static TickType_t         s_btm_roaming_since;
#define BTM_ROAM_TIMEOUT_S 60

// Disconnect reason the driver reports when the supplicant leaves the AP to
// follow a BSS Transition Management request (ESP-IDF specific code 207).
#define REASON_BTM_ROAMING WIFI_REASON_ROAMING

// Network-assisted roaming (802.11k radio measurement + 802.11v BSS
// transition management), so the dongle follows the Fritz!Box's mesh
// steering instead of staying with whichever AP it joined (#585). Needs
// CONFIG_ESP_WIFI_11KV_SUPPORT (sdkconfig.defaults); the supplicant then
// answers BTM requests and moves to the AP's candidate on its own. With BTM
// enabled, the supplicant also clears a BSSID/channel lock from the config
// on the next (dis)connect, which is what lets a later reconnect pick the
// strongest AP again. MBO stays off: it needs the scan cache (RAM) and adds
// nothing the Fritz!Box steering uses.
static void enable_roaming(wifi_sta_config_t *sta)
{
    sta->rm_enabled  = 1;
    sta->btm_enabled = 1;
}

static void format_bssid(const uint8_t *b, char *out, size_t cap)
{
    snprintf(out, cap, "%02x:%02x:%02x:%02x:%02x:%02x",
             b[0], b[1], b[2], b[3], b[4], b[5]);
}

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
    // ERROR, not WARN: the dongle is effectively offline (it cannot
    // associate with stored credentials) and needs operator attention.
    // Logging at ERROR also makes the log-report beacon ship it — a lone
    // transient disconnect (WARN) would not warrant that, but persistent
    // failure that drove us into failsafe does. Fires once per entry
    // (guarded by s_failsafe_active above).
    ESP_LOGE(TAG, "stored credentials not associating — entering failsafe "
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

        case WIFI_EVENT_STA_CONNECTED: {
            wifi_event_sta_connected_t *c = data;
            char bssid[18] = "?";
            if (c) format_bssid(c->bssid, bssid, sizeof(bssid));
            s_assoc_since_us = esp_timer_get_time();
            wifi_ap_record_t ap;
            int rssi = esp_wifi_sta_get_ap_info(&ap) == ESP_OK ? ap.rssi : 0;
            if (s_wd_reconnect_pending) {
                char old[18];
                format_bssid(s_wd_old_bssid, old, sizeof(old));
                ESP_LOGI(TAG, "link watchdog: reconnected to BSSID %s (RSSI %d dBm), "
                              "was %s (RSSI %d dBm)",
                         bssid, rssi, old, s_wd_old_rssi);
                s_wd_reconnect_pending = false;
            } else if (s_btm_roaming) {
                ESP_LOGI(TAG, "roamed to BSSID %s (RSSI %d dBm) on the AP's "
                              "802.11v request", bssid, rssi);
            }
            s_btm_roaming = false;
#if CONFIG_ESP_WIFI_11KV_SUPPORT
            // Whether this AP offers mesh steering to us at all — the field
            // evidence for #585 (serial / log stream only).
            ESP_LOGI(TAG, "associated with BSSID %s, channel %d, RSSI %d dBm "
                          "(AP supports 802.11v BTM: %s, 802.11k: %s)",
                     bssid, c ? c->channel : 0, rssi,
                     esp_wnm_is_btm_supported_connection() ? "yes" : "no",
                     esp_rrm_is_rrm_supported_connection() ? "yes" : "no");
#else
            ESP_LOGI(TAG, "associated with BSSID %s, channel %d, RSSI %d dBm",
                     bssid, c ? c->channel : 0, rssi);
#endif
            break;
        }

        case WIFI_EVENT_STA_DISCONNECTED: {
            wifi_event_sta_disconnected_t *d = data;
            int reason = d ? d->reason : -1;
            s_has_ip = false;
            // An established link lost — not our own watchdog reconnect, not
            // an AP-requested move, not a failed connect attempt.
            if (s_assoc_since_us > 0 && reason != REASON_BTM_ROAMING
                    && !(s_wd_reconnect_pending && reason == WIFI_REASON_ASSOC_LEAVE)
                    && !s_wps_active && !s_wps_restart_pending) {
                s_link_drops++;
            }
            s_assoc_since_us = 0;

            if (reason == REASON_BTM_ROAMING) {
                // The supplicant follows the AP's BSS transition request and
                // connects to the target itself: a competing esp_wifi_connect()
                // here would abort that, and the move is no failure to count.
                char from[18] = "?";
                if (d) format_bssid(d->bssid, from, sizeof(from));
                ESP_LOGI(TAG, "leaving BSSID %s for another AP (802.11v BSS transition)",
                         from);
                s_btm_roaming_since = xTaskGetTickCount();
                s_btm_roaming = true;
                break;
            }
            s_btm_roaming = false;
            if (s_wd_reconnect_pending && reason == WIFI_REASON_ASSOC_LEAVE) {
                // Our own disconnect from the link watchdog, already
                // announced with a WARN.
                ESP_LOGI(TAG, "disconnected by link watchdog — reconnecting");
            } else {
                ESP_LOGW(TAG, "disconnected (reason %d)", reason);
            }

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
            // For a single credential set, the WPS state machine
            // (wps_finish() in esp_wps.c, IDF v5.3) has already updated
            // the driver's sta_config by read-modify-write: it reads the
            // current config, overwrites only SSID, passphrase and BSSID
            // (bssid_set = 0) and forces sae_pwe_h2e = 0. WPS carries no
            // security policy, so threshold.authmode and pmf_cfg are
            // whatever the config held before pairing — on a fresh
            // dongle the driver defaults. That is fine: an unset
            // threshold with a WPA2-length passphrase is treated as
            // WPA2 (which admits WPA3), and IDF v5.x always uses PMF
            // when the AP offers it (pmf_cfg.capable is deprecated).
            //
            // We read-modify-write that driver config in turn: get it,
            // change only the fields we must, set it back. Earlier code
            // instead built a fresh wifi_config_t = {0} and copied only
            // SSID/passphrase into it; that build got Reason 210 on every
            // reconnect against PMF-enabled APs (see the recovery path
            // above for the user-visible symptom). Keeping the driver's
            // config avoids dropping anything the driver did set.
            //
            // Two things we override:
            //  - scan_method → WIFI_ALL_CHANNEL_SCAN, so that if the AP
            //    is hidden, the stored-credentials reconnect on the next
            //    boot can still find it (a hidden SSID is invisible to
            //    the default fast scan — see wifi_set_credentials() and
            //    issue #458). The immediate post-WPS connect below is
            //    unaffected; this matters for later reboots.
            //  - for the rare multi-AP case (ap_cred_cnt > 1), the
            //    SSID/passphrase, to pick one of the offered sets.
            //  - rm_enabled / btm_enabled, for mesh roaming (see
            //    enable_roaming()).
            wifi_event_sta_wps_er_success_t *evt = data;
            wifi_config_t cfg;
            if (esp_wifi_get_config(WIFI_IF_STA, &cfg) == ESP_OK) {
                if (evt && evt->ap_cred_cnt > 1) {
                    memset(cfg.sta.ssid,     0, sizeof(cfg.sta.ssid));
                    memset(cfg.sta.password, 0, sizeof(cfg.sta.password));
                    memcpy(cfg.sta.ssid,
                           evt->ap_cred[0].ssid,
                           sizeof(cfg.sta.ssid));
                    memcpy(cfg.sta.password,
                           evt->ap_cred[0].passphrase,
                           sizeof(cfg.sta.password));
                    ESP_LOGI(TAG, "WPS success — picked SSID=%s of %d offered",
                             cfg.sta.ssid, evt->ap_cred_cnt);
                } else {
                    ESP_LOGI(TAG, "WPS success — credentials auto-persisted");
                }
                cfg.sta.scan_method = WIFI_ALL_CHANNEL_SCAN;
                enable_roaming(&cfg.sta);
                ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &cfg));
            } else {
                ESP_LOGW(TAG, "esp_wifi_get_config failed — "
                              "trusting driver-internal cfg");
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
    // A hidden AP omits its SSID from beacons, so the default
    // WIFI_FAST_SCAN — which stops at the first beaconed SSID that
    // matches — never locates it. WIFI_ALL_CHANNEL_SCAN runs a full
    // active scan (directed probe requests across all channels), which
    // is the only way to associate with a hidden SSID. There is no
    // dedicated "hidden" flag in wifi_sta_config_t; scan_method is the
    // lever. sort_method stays at its zero-init default
    // (WIFI_CONNECT_AP_BY_SIGNAL). See issue #458.
    cfg.sta.scan_method        = WIFI_ALL_CHANNEL_SCAN;
    enable_roaming(&cfg.sta);

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
    s_scan_active = true;
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
    s_scan_active = false;
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
    // The WPS path does not set these either: it keeps the driver's
    // existing config and replaces only SSID/passphrase/BSSID (see the
    // WIFI_EVENT_STA_WPS_ER_SUCCESS handler). Here we hand in a freshly
    // zero-initialised struct, so we state the baseline explicitly.
    cfg.sta.threshold.authmode = WIFI_AUTH_WPA2_PSK;
    cfg.sta.pmf_cfg.capable    = true;
    cfg.sta.pmf_cfg.required   = false;
    // Full active scan so a hidden SSID can be associated with — see the
    // rationale in wifi_set_credentials() and issue #458.
    cfg.sta.scan_method        = WIFI_ALL_CHANNEL_SCAN;
    enable_roaming(&cfg.sta);

    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &cfg));
}

// The pairing machinery or a scan owns the station: the watchdog must not
// disconnect underneath it (see the s_wps_restart_pending comments above).
static bool pairing_busy(void)
{
    return s_wps_active || s_wps_restart_pending || s_failsafe_active
        || s_scan_active || s_wd_reconnect_pending || s_btm_roaming;
}

// Act on a watchdog verdict: one deliberate disconnect. The DISCONNECTED
// handler reconnects through the regular path (the same esp_wifi_connect()
// that every other drop uses, counted against the failsafe threshold like
// any reconnect), so there is no second connect to race it. Should pairing
// start in the instant between the check and the disconnect, the pairing
// code gates the handler itself and the cycle stays harmless.
static void watchdog_reconnect(link_wd_action_t action, const link_wd_input_t *in,
                               const wifi_ap_record_t *ap, uint32_t gap_s)
{
    if (pairing_busy() || !s_has_ip) return;
    // Re-checked like pairing: a call may have been answered since the sample.
    if (sip_register_call_answered() || rtp_streaming_active()) return;

    char bssid[18] = "?";
    if (in->rssi_valid) {
        memcpy(s_wd_old_bssid, ap->bssid, sizeof(s_wd_old_bssid));
        format_bssid(ap->bssid, bssid, sizeof(bssid));
    } else {
        memset(s_wd_old_bssid, 0, sizeof(s_wd_old_bssid));
    }
    s_wd_old_rssi = in->rssi_valid ? in->rssi : 0;

    // One WARN per action (at most every LINK_WD_BACKOFF_MIN_S), so it shows
    // up in the web UI log without flooding it.
    if (action == LINK_WD_RECONNECT_REGISTRAR) {
        ESP_LOGW(TAG, "link watchdog: SIP registrar unanswered for %u s although "
                      "Wi-Fi is up (BSSID %s, RSSI %d dBm) — reconnecting to pick "
                      "the strongest AP; next attempt in %u min at the earliest",
                 (unsigned)in->registrar_unreachable_s, bssid, s_wd_old_rssi,
                 (unsigned)(gap_s / 60));
    } else {
        ESP_LOGW(TAG, "link watchdog: Wi-Fi signal below %d dBm for %u min "
                      "(BSSID %s, RSSI %d dBm) — reconnecting to pick the "
                      "strongest AP; next attempt in %u min at the earliest",
                 LINK_WD_RSSI_LOW_DBM, (unsigned)(LINK_WD_RSSI_LOW_S / 60),
                 bssid, s_wd_old_rssi, (unsigned)(gap_s / 60));
    }

    s_wd_reconnect_pending = true;
    esp_err_t err = esp_wifi_disconnect();
    if (err != ESP_OK) {
        s_wd_reconnect_pending = false;
        ESP_LOGW(TAG, "link watchdog: esp_wifi_disconnect: %s", esp_err_to_name(err));
    }
}

static void link_watchdog_task(void *arg)
{
    (void)arg;
    link_wd_t wd;
    link_wd_init(&wd);
    for (;;) {
        vTaskDelay(seconds_to_ticks(LINK_WD_TICK_S));

        // Safety net for the 802.11v path: the DISCONNECTED handler leaves
        // the reconnect to the supplicant during a BSS transition. Should
        // that never end in an association, take over with a regular
        // connect instead of staying offline.
        if (s_btm_roaming && !s_has_ip
                && xTaskGetTickCount() - s_btm_roaming_since
                       >= seconds_to_ticks(BTM_ROAM_TIMEOUT_S)) {
            s_btm_roaming = false;
            ESP_LOGW(TAG, "802.11v BSS transition did not complete within %d s — "
                          "reconnecting", BTM_ROAM_TIMEOUT_S);
            esp_wifi_connect();
        }

        link_wd_input_t in = { 0 };
        wifi_ap_record_t ap;
        in.has_ip         = s_has_ip;
        in.pairing_active = pairing_busy();
        in.rssi_valid     = esp_wifi_sta_get_ap_info(&ap) == ESP_OK;
        in.rssi           = in.rssi_valid ? ap.rssi : 0;
        in.sip_configured = sip_register_active();
        in.registrar_unreachable_s = sip_register_unreachable_s();
        // A call the dongle answered: the SIP dialog slot, or media still
        // streaming (announcement / interactive dialog) around it.
        in.call_active    = sip_register_call_answered() || rtp_streaming_active();

        uint32_t now_s = (uint32_t)(esp_timer_get_time() / 1000000LL);
        link_wd_action_t action = link_wd_tick(&wd, now_s, &in);
        if (action != LINK_WD_NONE) {
            watchdog_reconnect(action, &in, &ap, link_wd_last_gap_s(&wd));
        }
    }
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
        // Credentials persisted by firmware that predates hidden-SSID
        // support carry scan_method = WIFI_FAST_SCAN, which can't locate
        // a hidden AP. Upgrade the persisted config in place so a device
        // paired on old firmware still connects if its SSID is hidden
        // after the update. Idempotent: only rewritten when it differs,
        // so a config already on all-channel scan isn't touched. See
        // issue #458. Same for the mesh-roaming flags (#585), which
        // configs persisted before 1.7.0 lack.
        if (stored.sta.scan_method != WIFI_ALL_CHANNEL_SCAN
                || !stored.sta.rm_enabled || !stored.sta.btm_enabled) {
            stored.sta.scan_method = WIFI_ALL_CHANNEL_SCAN;
            enable_roaming(&stored.sta);
            esp_err_t err = esp_wifi_set_config(WIFI_IF_STA, &stored);
            if (err != ESP_OK) {
                ESP_LOGW(TAG, "failed to upgrade stored STA config: %s",
                         esp_err_to_name(err));
            }
        }
    } else if (have_baked) {
        ESP_LOGI(TAG, "seeding from baked credentials (SSID=%s)", BAKED_SSID);
        seed_baked_creds();
    } else {
        ESP_LOGI(TAG, "no credentials — WPS-PBC will start after WiFi init");
    }

    ESP_ERROR_CHECK(esp_wifi_start());
    s_started = true;

    // Disable WiFi modem power-save. The ESP-IDF default is
    // WIFI_PS_MIN_MODEM: the radio sleeps between DTIM beacons, which on
    // some APs (incl. Fritz!Box) loses enough downlink ACKs that the AP
    // disassociates the station with reason 34 (DISASSOC_LOW_ACK). Each
    // such drop tears down the long-lived TLS socket to the Telekom SIP
    // registrar, surfacing as a "Host is unreachable" / re-REGISTER storm
    // even though the registration itself is fine. The dongle is mains-
    // powered, so there is nothing to save by sleeping — keep the radio on.
    ESP_ERROR_CHECK(esp_wifi_set_ps(WIFI_PS_NONE));

    if (!have_stored && !have_baked) {
        start_wps();
    }

    xEventGroupWaitBits(s_events, WIFI_CONNECTED_BIT,
                        pdFALSE, pdTRUE, portMAX_DELAY);

    // Started only now: the watchdog never acts before the first connection
    // completed.
    if (xTaskCreate(link_watchdog_task, "link_wd", 3072, NULL, 3, NULL) != pdPASS) {
        ESP_LOGE(TAG, "link watchdog task creation failed");
    }
    return ESP_OK;
}

bool wifi_link_status(wifi_link_t *l)
{
    memset(l, 0, sizeof(*l));
    l->drops = s_link_drops;
    int8_t qdbm;
    if (esp_wifi_get_max_tx_power(&qdbm) == ESP_OK) l->tx_dbm = qdbm / 4.0f;
    wifi_ap_record_t ap;
    if (esp_wifi_sta_get_ap_info(&ap) != ESP_OK) return false;
    l->rssi = ap.rssi;
    l->channel = ap.primary;
    snprintf(l->ssid, sizeof(l->ssid), "%.32s", (const char *)ap.ssid);
    format_bssid(ap.bssid, l->bssid, sizeof(l->bssid));
    int64_t since = s_assoc_since_us;
    if (since > 0) l->connected_s = (uint32_t)((esp_timer_get_time() - since) / 1000000LL);
    return true;
}
