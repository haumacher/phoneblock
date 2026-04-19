#pragma once

#include "esp_http_client.h"

// Return a stable User-Agent string for all outbound HTTP(S) calls,
// formatted "PhoneBlock-Dongle/<version>". The version comes from
// esp_app_get_description() at first call and is then cached.
const char *http_util_user_agent(void);

// Convenience: set the User-Agent header on an esp_http_client handle.
void http_util_set_user_agent(esp_http_client_handle_t client);
