#include "improv.h"

#include <stdio.h>
#include <string.h>

#include "driver/uart.h"
#include "driver/uart_vfs.h"
#include "esp_app_desc.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "sdkconfig.h"

#include "improv_proto.h"
#include "wifi.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static const char *TAG = "improv";

#define IMPROV_UART CONFIG_ESP_CONSOLE_UART_NUM

// The provisioning dialog (esp-web-tools) gives the device 30 s to
// answer a send-wifi-settings command; report failure before the
// client-side timeout turns it into an unspecific error.
#define CONNECT_TIMEOUT_MS (25 * 1000)

// Boot race: improv_start() runs before wifi_connect() has initialized
// the driver. A user cannot click "connect" that fast, but the guard
// costs nothing.
#define WIFI_STARTUP_GRACE_MS (5 * 1000)

#define SCAN_MAX_APS 20

// Single consumer (the improv task) — keeping the scan results off
// its stack is cheaper than sizing the stack for 20 ap records.
static wifi_ap_record_t s_scan_records[SCAN_MAX_APS];

static bool s_provisioning;

// Every packet is framed in '\n'…'\n': the browser-side parser only
// recognizes the IMPROV header directly after a newline, and the
// trailing one keeps the next log line from gluing onto the packet.
// One uart_write_bytes call per packet — writes are serialized by the
// driver, so log output never lands mid-packet.
static void send_packet(const uint8_t *pkt, size_t len)
{
    uint8_t framed[IMPROV_MAX_PACKET + 2];
    framed[0] = '\n';
    memcpy(&framed[1], pkt, len);
    framed[1 + len] = '\n';
    uart_write_bytes(IMPROV_UART, framed, len + 2);
}

static uint8_t current_state(void)
{
    if (s_provisioning) {
        return IMPROV_STATE_PROVISIONING;
    }
    return wifi_has_ip() ? IMPROV_STATE_PROVISIONED : IMPROV_STATE_READY;
}

static void send_state(void)
{
    uint8_t pkt[IMPROV_MAX_PACKET];
    size_t n = improv_build_current_state(current_state(), pkt, sizeof(pkt));
    send_packet(pkt, n);
}

static void send_error(uint8_t error)
{
    uint8_t pkt[IMPROV_MAX_PACKET];
    size_t n = improv_build_error(error, pkt, sizeof(pkt));
    send_packet(pkt, n);
}

static void send_rpc_result(uint8_t command,
                            const char *const *strings, size_t n_strings)
{
    uint8_t pkt[IMPROV_MAX_PACKET];
    size_t n = improv_build_rpc_result(command, strings, n_strings,
                                       pkt, sizeof(pkt));
    if (n == 0) {
        ESP_LOGW(TAG, "RPC result for cmd 0x%02x does not fit a packet",
                 command);
        return;
    }
    send_packet(pkt, n);
}

// "http://192.168.2.7/" — the dialog offers this as a "visit device"
// link after provisioning, which drops the user straight into the
// dongle's web UI for the remaining setup (token, SIP).
static bool redirect_url(char *buf, size_t cap)
{
    char ip[16];
    if (!wifi_get_ip_str(ip, sizeof(ip))) {
        return false;
    }
    snprintf(buf, cap, "http://%s/", ip);
    return true;
}

static void handle_wifi_settings(const uint8_t *payload, uint8_t payload_len)
{
    char ssid[33];
    char pass[65];
    if (!improv_parse_wifi_settings(payload, payload_len,
                                    ssid, sizeof(ssid),
                                    pass, sizeof(pass))) {
        send_error(IMPROV_ERROR_INVALID_RPC);
        return;
    }

    for (int waited = 0; !wifi_sta_started(); waited += 100) {
        if (waited >= WIFI_STARTUP_GRACE_MS) {
            ESP_LOGW(TAG, "Wi-Fi driver not up yet — rejecting provisioning");
            send_error(IMPROV_ERROR_UNABLE_TO_CONNECT);
            return;
        }
        vTaskDelay(pdMS_TO_TICKS(100));
    }

    ESP_LOGI(TAG, "provisioning via Improv (SSID=%s)", ssid);
    s_provisioning = true;
    send_state();

    esp_err_t err = wifi_set_credentials(ssid, pass);
    bool connected = false;
    if (err == ESP_OK) {
        for (int waited = 0; waited < CONNECT_TIMEOUT_MS; waited += 250) {
            if (wifi_has_ip()) {
                connected = true;
                break;
            }
            vTaskDelay(pdMS_TO_TICKS(250));
        }
    } else {
        ESP_LOGW(TAG, "wifi_set_credentials: %s", esp_err_to_name(err));
    }

    s_provisioning = false;
    if (connected) {
        send_state();  // PROVISIONED
        char url[32];
        const char *urls[] = { url };
        send_rpc_result(IMPROV_CMD_WIFI_SETTINGS, urls,
                        redirect_url(url, sizeof(url)) ? 1 : 0);
    } else {
        // Wrong passphrase and absent AP look identical here. The
        // credentials stay in NVS; a corrected retry simply overwrites
        // them, and the regular failsafe machinery keeps WPS available
        // as the alternative path.
        ESP_LOGW(TAG, "no IP within %d s — reporting unable-to-connect",
                 CONNECT_TIMEOUT_MS / 1000);
        send_error(IMPROV_ERROR_UNABLE_TO_CONNECT);
        send_state();  // back to READY (or PROVISIONED if a late IP won)
    }
}

static void handle_get_state(void)
{
    send_state();
    if (current_state() == IMPROV_STATE_PROVISIONED) {
        // The client expects the redirect URL as an RPC result
        // whenever it asks an already-provisioned device for its
        // state (improv-wifi-serial-sdk awaits both packets).
        char url[32];
        const char *urls[] = { url };
        send_rpc_result(IMPROV_CMD_GET_STATE, urls,
                        redirect_url(url, sizeof(url)) ? 1 : 0);
    }
}

static void handle_get_info(void)
{
    const esp_app_desc_t *app = esp_app_get_description();
    const char *strings[] = {
        "PhoneBlock Dongle",            // firmware name
        app ? app->version : "?",       // firmware version
        "ESP32",                        // chip family
        "PhoneBlock Dongle",            // device name
    };
    send_rpc_result(IMPROV_CMD_GET_INFO, strings, 4);
}

static void handle_scan(void)
{
    int n = wifi_scan(s_scan_records, SCAN_MAX_APS);

    int sent = 0;
    for (int i = 0; i < n; i++) {
        const char *ssid = (const char *)s_scan_records[i].ssid;
        if (ssid[0] == '\0') {
            continue;  // hidden network — "join other" covers it
        }
        // Mesh setups (Fritz!Repeater & co) report one record per
        // BSSID with identical SSIDs; the records are RSSI-sorted, so
        // keeping the first occurrence keeps the strongest.
        bool dup = false;
        for (int j = 0; j < i && !dup; j++) {
            dup = strcmp(ssid, (const char *)s_scan_records[j].ssid) == 0;
        }
        if (dup) {
            continue;
        }

        char rssi[8];
        snprintf(rssi, sizeof(rssi), "%d", s_scan_records[i].rssi);
        const char *strings[] = {
            ssid,
            rssi,
            s_scan_records[i].authmode == WIFI_AUTH_OPEN ? "NO" : "YES",
        };
        send_rpc_result(IMPROV_CMD_SCAN_NETWORKS, strings, 3);
        sent++;
    }
    ESP_LOGI(TAG, "scan: %d networks reported", sent);

    // Empty result terminates the list (also the "scan impossible
    // right now" answer — the dialog falls back to manual entry).
    send_rpc_result(IMPROV_CMD_SCAN_NETWORKS, NULL, 0);
}

static void handle_rpc(const uint8_t *data, uint8_t data_len)
{
    // RPC layout: [command, payload_len, payload…]
    if (data_len < 2 || data[1] != data_len - 2) {
        send_error(IMPROV_ERROR_INVALID_RPC);
        return;
    }
    uint8_t command = data[0];

    // Spec: receiving an RPC command resets the error state to 0 for
    // any listening client.
    send_error(IMPROV_ERROR_NONE);

    switch (command) {
        case IMPROV_CMD_WIFI_SETTINGS:
            handle_wifi_settings(&data[2], data_len - 2);
            break;
        case IMPROV_CMD_GET_STATE:
            handle_get_state();
            break;
        case IMPROV_CMD_GET_INFO:
            handle_get_info();
            break;
        case IMPROV_CMD_SCAN_NETWORKS:
            handle_scan();
            break;
        default:
            send_error(IMPROV_ERROR_UNKNOWN_RPC);
            break;
    }
}

static void improv_task(void *arg)
{
    (void)arg;

    improv_parser_t parser;
    improv_parser_reset(&parser);

    // Devices announce their state on boot so an already-listening
    // client updates without polling.
    send_state();

    uint8_t rx[64];
    for (;;) {
        // Short timeout instead of portMAX_DELAY: uart_read_bytes
        // blocks until *length* bytes arrived, not until *some* byte
        // arrived — a full-buffer wait would stall single packets.
        int n = uart_read_bytes(IMPROV_UART, rx, sizeof(rx),
                                pdMS_TO_TICKS(100));
        for (int i = 0; i < n; i++) {
            uint8_t type, payload_len;
            const uint8_t *payload;
            if (improv_parser_feed(&parser, rx[i],
                                   &type, &payload, &payload_len)
                    && type == IMPROV_TYPE_RPC_COMMAND) {
                handle_rpc(payload, payload_len);
            }
        }
    }
}

void improv_start(void)
{
    // The console UART runs driver-less by default; logs are written
    // byte-wise straight into the FIFO. Install the driver and route
    // the log/stdout VFS through it so Improv packets (one
    // uart_write_bytes call each) cannot be interleaved mid-packet by
    // a concurrent log line.
    esp_err_t err = uart_driver_install(IMPROV_UART, 1024, 1024, 0, NULL, 0);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "uart_driver_install: %s — Improv disabled",
                 esp_err_to_name(err));
        return;
    }
    uart_vfs_dev_use_driver(IMPROV_UART);

    xTaskCreate(improv_task, "improv", 4096, NULL, 3, NULL);
}
