#pragma once

#ifdef ESP_PLATFORM
#include "esp_http_client.h"
#endif

// Return a stable User-Agent string for all outbound HTTP(S) calls,
// formatted "PhoneBlock-Dongle/<version> (<device id>)". The version comes
// from pb_firmware_version() at first call and is then cached. Callers of
// pb_http_request() pass it as a "User-Agent" header (the platform layer
// can't reach config / this module).
const char *http_util_user_agent(void);

#ifdef ESP_PLATFORM
// Convenience: set the User-Agent header on an esp_http_client handle
// (crashreport.c, which streams from flash and stays on esp_http_client).
void http_util_set_user_agent(esp_http_client_handle_t client);
#endif
