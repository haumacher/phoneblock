#include "http_util.h"

#include <stdio.h>
#include <string.h>

#include "esp_app_desc.h"

#include "config.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static char s_user_agent[120] = "";

const char *http_util_user_agent(void)
{
    if (s_user_agent[0]) return s_user_agent;
    const esp_app_desc_t *app = esp_app_get_description();
    const char *v = (app && app->version[0]) ? app->version : "0.0.0";
    // Embed the stable device id so the server can attribute token
    // renewals (and recognise obsolete tokens) to the same physical
    // dongle. config_device_id() is set once at boot, before any HTTP
    // call, so it is populated by the time this lazily builds the string.
    const char *id = config_device_id();
    if (id && id[0]) {
        snprintf(s_user_agent, sizeof(s_user_agent),
                 "PhoneBlock-Dongle/%s (%s)", v, id);
    } else {
        snprintf(s_user_agent, sizeof(s_user_agent),
                 "PhoneBlock-Dongle/%s", v);
    }
    return s_user_agent;
}

void http_util_set_user_agent(esp_http_client_handle_t client)
{
    esp_http_client_set_header(client, "User-Agent", http_util_user_agent());
}
