#pragma once

#include <stdbool.h>
#include <stddef.h>

#include "esp_err.h"
#include "esp_wifi_types.h"

// Bring up WiFi in STA mode and block until the device has an IP.
//
// Credential sources, tried in this order:
//
//   1. Persisted credentials in the WiFi NVS namespace — set by a
//      previous successful WPS pairing, or by a previous boot that
//      seeded from the baked-in values below.
//   2. Compile-time baked credentials from CONFIG_EXAMPLE_WIFI_SSID /
//      CONFIG_EXAMPLE_WIFI_PASSWORD (the dev-workflow path via
//      sdkconfig.defaults.local). Seeded into NVS on first use so the
//      device keeps working after the baked values are cleared.
//   3. WPS-PBC: the caller presses the router's WPS/„Neues-Gerät"
//      button within ~120 s. On success the ESP-IDF WPS state machine
//      persists the handed-over SSID + passphrase into NVS and we
//      connect. On failure/timeout WPS restarts automatically.
//
// Must be called after nvs_flash_init + esp_netif_init +
// esp_event_loop_create_default.
esp_err_t wifi_connect(void);

// True while WPS-PBC is active (either the initial run on a
// credential-less first boot or an auto-restart after timeout).
// Used by the status LED to show the "press the router button"
// blink pattern.
bool wifi_is_wps_active(void);

// True once the station has received an IP address. Cleared again
// when the link drops and we fall back to reconnect attempts.
bool wifi_has_ip(void);

// True once esp_wifi_start() has run inside wifi_connect(). The Improv
// task starts before wifi_connect() (which blocks until the first IP),
// so it must not call the credential/scan entry points below until
// this turns true.
bool wifi_sta_started(void);

// Replace the stored credentials and reconnect — the Improv serial
// provisioning path (issue #372: setup without WPS). Safe to call from
// any pairing state: tears down an active WPS round and the failsafe
// alternation first, mirroring the WPS_ER_SUCCESS exit path. The new
// credentials are persisted via WIFI_STORAGE_FLASH, so they survive
// reboots exactly like WPS-provided ones. The connection attempt
// itself is asynchronous — poll wifi_has_ip() for the outcome.
esp_err_t wifi_set_credentials(const char *ssid, const char *password);

// Blocking scan for visible APs. Suspends an active WPS round for the
// duration (and resumes it afterwards) because the driver refuses to
// scan while WPS owns the STA. Returns the number of records written
// to records, or -1 when scanning is impossible right now (e.g. the
// STA is mid-association) — callers report "no networks" then.
int wifi_scan(wifi_ap_record_t *records, int max_records);

// Format the station's current IPv4 address into buf ("192.168.2.7").
// False while the device has no IP.
bool wifi_get_ip_str(char *buf, size_t cap);
