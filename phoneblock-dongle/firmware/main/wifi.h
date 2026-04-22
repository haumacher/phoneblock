#pragma once

#include "esp_err.h"

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
