#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include "esp_app_desc.h"
#include "esp_log.h"
#include "esp_event.h"
#include "esp_netif.h"
#include "esp_ota_ops.h"
#include "mdns.h"
#include "nvs_flash.h"

#include "protocol_examples_common.h"

#include "announcement.h"
#include "api.h"
#include "config.h"
#include "crashreport.h"
#include "improv.h"
#include "log_capture.h"
#include "mail.h"
#include "report_queue.h"
#include "scheduler.h"
#include "sip_register.h"
#include "stats.h"
#include "status_led.h"
#include "time_sync.h"
#include "web.h"
#include "wifi.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static const char *TAG = "phoneblock";

// Announce the dongle on the LAN as "answerbot" via mDNS. The matching
// DHCP host-name (option 12) is set earlier in wifi_connect(), before
// the first DHCPREQUEST goes out — otherwise the Fritz!Box pins the
// Heimnetz name to the lwIP default "espressif".
// Reachable as http://answerbot/, http://answerbot.local/, or by IP.
static void setup_mdns(void)
{
    static const char *HOSTNAME = "answerbot";

    ESP_ERROR_CHECK(mdns_init());
    ESP_ERROR_CHECK(mdns_hostname_set(HOSTNAME));
    ESP_ERROR_CHECK(mdns_instance_name_set("PhoneBlock Dongle"));
    mdns_service_add(NULL, "_http", "_tcp", 80, NULL, 0);
}

void app_main(void)
{
    ESP_ERROR_CHECK(nvs_flash_init());
    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());

    stats_setup();
    // Install the log hook right after the stats ring exists: from here
    // on, every WARN/ERROR line (ours and the libraries') is mirrored to
    // the web UI's log panel. Earlier boot lines (nvs/netif init) predate
    // it, which is fine — those are pre-config and visible on serial.
    log_capture_start();
    config_load();
    announcement_init();
    // Start the LED early so the user sees "CONNECTING" (or
    // "PAIRING" a few hundred ms later) before WiFi blocks us.
    status_led_start();

    // Set up SNTP + timezone before the network comes up: the connect
    // calls below block until the first IP, and time_sync starts the
    // clock from that GOT_IP event — registering its handler afterwards
    // would miss it. Needs config_load() (timezone) and the event loop,
    // both already done above.
    time_sync_start();

    // On real hardware we drive WiFi ourselves so first-boot pairing
    // via WPS-PBC works without baked credentials. example_connect
    // is kept for the QEMU/Ethernet path, where WPS does not apply.
#if CONFIG_EXAMPLE_CONNECT_WIFI
    // Improv serial provisioning must listen *before* wifi_connect()
    // blocks: Wi-Fi setup without WPS (issue #372) happens exactly
    // while the device is still waiting in pairing mode.
    improv_start();
    ESP_ERROR_CHECK(wifi_connect());
#else
    ESP_ERROR_CHECK(example_connect());
#endif
    setup_mdns();

    // Web UI comes up unconditionally so the setup wizards are reachable
    // even on a fresh device. SIP registration and the API self-test
    // only run when their respective NVS fields are populated.
    web_start();

    // Reaching this point means: WiFi is up, the web server is
    // listening — minimum viable "this firmware works". Mark the
    // running image valid so the bootloader does not roll back on
    // the next reset. A fresh USB-flash lands in ota_0 in state
    // ESP_OTA_IMG_VALID (no rollback active); OTA uploads land in
    // ESP_OTA_IMG_PENDING_VERIFY and need this confirmation.
    const esp_partition_t *running = esp_ota_get_running_partition();
    esp_ota_img_states_t ota_state;
    bool fw_just_updated = false;
    if (running != NULL
            && esp_ota_get_state_partition(running, &ota_state) == ESP_OK
            && ota_state == ESP_OTA_IMG_PENDING_VERIFY) {
        ESP_LOGI(TAG, "marking running firmware (%s) valid — rollback cancelled",
                 running->label);
        esp_ota_mark_app_valid_cancel_rollback();
        // PENDING_VERIFY is the first boot of a freshly OTA-flashed image —
        // whether via CDN auto-update or a manual POST /api/firmware upload;
        // a USB factory flash lands VALID and is skipped. This is the true
        // "the firmware was just updated" signal, so latch a one-shot
        // notification mail for the scheduler to send once Wi-Fi/SMTP are up
        // (see mail_report_update), and refresh this version's localized
        // assets promptly rather than at the usual 3 min slot.
        fw_just_updated = true;
        const esp_app_desc_t *app = esp_app_get_description();
        if (app != NULL) mail_note_update(app->version);
    }

    // Clear the auto-update guard if we successfully booted into the
    // version that was last attempted. If the marker names a *different*
    // version, we got here via rollback — leave it in place so the
    // background task does not try the same broken bits again.
    {
        const char *failed = config_last_failed_ota();
        const esp_app_desc_t *app = esp_app_get_description();
        const char *current = app ? app->version : "";
        if (failed[0] != '\0') {
            if (strcmp(failed, current) == 0) {
                ESP_LOGI(TAG, "running version %s matches last_failed_ota — "
                              "marker cleared (boot survived)", current);
                config_set_last_failed_ota(NULL);
            } else {
                // The marker names a version we are NOT running: the OTA
                // to it installed, booted badly, and the bootloader
                // rolled us back. The marker is set only after a complete
                // install (every download/verify failure clears it), so
                // this reliably means "a firmware update failed to boot".
                // ERROR (→ web UI log, and shipped by the log-report
                // beacon): a bricked-and-rolled-back update is a real
                // failure an operator should see, not routine noise. Leave
                // the marker so the updater skips the same broken bits.
                // Recurs once per boot until a newer build supersedes it.
                ESP_LOGE(TAG, "firmware update to %s failed to boot — "
                              "rolled back to %s", failed, current);
            }
        }
    }

    bool token_set = strlen(config_phoneblock_token()) > 0;
    bool sip_set   = strlen(config_sip_host()) > 0;

    // If the previous boot panicked, a core dump is sitting in the
    // dedicated partition. Kick off a one-shot task that uploads it
    // to the PhoneBlock backend and then erases the slot. Best-effort:
    // failures (no token yet, no network, server has no storage)
    // leave the dump in place for a later boot to retry.
    crashreport_upload_async();

    // Async /api/report-call worker — keeps the second TLS handshake
    // off the SIP critical path. Drains a small queue at its own
    // pace; SPAM verdicts enqueue here instead of POSTing inline.
    report_queue_start();

    // Create the shared state for the session-resuming spam-lookup HTTP
    // client before any task that calls phoneblock_check() (the SIP
    // answering path, the scheduler jobs) is started.
    phoneblock_api_init();

    if (token_set) {
        ESP_LOGI(TAG, "initial self-test");
        phoneblock_selftest(NULL);
    } else {
        ESP_LOGI(TAG, "PhoneBlock token not configured yet — set via web UI");
    }

    if (sip_set) {
        sip_register_start();
    } else {
        ESP_LOGI(TAG, "SIP not configured yet — set via web UI");
    }

    // Shared scheduler task — owns all recurring ~24 h housekeeping:
    // the token self-test (+ log-report flush), the firmware
    // auto-update check, the Fritz!Box blocklist sync, the status mail,
    // and the local binary-blocklist download. Replaces the separate
    // per-feature tasks (task stack reclaimed). Safe to start before the
    // device is provisioned; each job applies its own
    // skip-until-configured / user-toggle gates, and the daily
    // last_failed_ota guard (cleared above on a healthy boot) still
    // keeps a brick-and-rollback build from being re-tried in a loop.
    scheduler_start(fw_just_updated);
}
