#include "time_sync.h"

#include <stdlib.h>
#include <time.h>

#include "esp_event.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "esp_netif_sntp.h"
#include "esp_sntp.h"

#include "config.h"
#include "scheduler.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static const char *TAG = "time_sync";

// Static SNTP servers we register: the default gateway and pool.ntp.org.
#define TIME_SYNC_STATIC_SERVERS 2

// Total SNTP slots lwIP must hold: the two static servers above plus the
// DHCP option-42 server that fills index 0 (see the config below). lwIP is
// built with a fixed-size server array (CONFIG_LWIP_SNTP_MAX_SERVERS); ask
// esp_netif_sntp_init() for more than fit and it fails at runtime with
// ESP_ERR_INVALID_ARG and SNTP never starts. A stale sdkconfig that
// predated CONFIG_LWIP_SNTP_MAX_SERVERS=3 (sdkconfig.defaults) shipped
// exactly that crash to the field — silently, because the failure is only
// visible in the log. Turn it into a build failure so a too-small value
// (e.g. a stale sdkconfig that didn't pick up the default) can't ship:
// run `idf.py fullclean` so sdkconfig.defaults takes effect, or raise it.
#if !defined(CONFIG_LWIP_SNTP_MAX_SERVERS) || \
    CONFIG_LWIP_SNTP_MAX_SERVERS < (TIME_SYNC_STATIC_SERVERS + 1)
#error "CONFIG_LWIP_SNTP_MAX_SERVERS must be >= 3 for time_sync's server list; run 'idf.py fullclean' so sdkconfig.defaults applies, or raise it."
#endif

// Set true once SNTP has stepped the clock at least once. Written from the
// SNTP sync callback (lwIP's SNTP task context), read everywhere — a plain
// bool is fine for a one-way latch on a 32-bit MCU.
static volatile bool s_valid = false;

// Guards the one-shot esp_netif_sntp_start() in the GOT_IP handler.
static bool s_started = false;

// Backs the "default gateway" slot in the SNTP server list. lwIP stores
// the pointer we hand it and resolves the name lazily at request time, so
// rewriting this buffer in place once we learn the gateway (before the
// first request) is enough — no re-registration needed. Pre-seeded with
// the internet fallback so a request before the first GOT_IP still has a
// usable name.
static char s_gw_server[40] = "pool.ntp.org";

static void on_time_set(struct timeval *tv)
{
    (void)tv;
    bool first = !s_valid;
    s_valid = true;

    time_t now = time(NULL);
    struct tm lt;
    localtime_r(&now, &lt);
    char buf[32];
    strftime(buf, sizeof(buf), "%Y-%m-%d %H:%M:%S", &lt);
    ESP_LOGI(TAG, "clock set: %s (local)", buf);

    if (first) {
        // Daily jobs parked on the "clock not valid yet" retry need to
        // recompute against real local time now that we have it.
        scheduler_notify_time_synced();
    }
}

static void on_got_ip(void *arg, esp_event_base_t base, int32_t id, void *data)
{
    (void)arg; (void)base; (void)id;
    ip_event_got_ip_t *e = data;
    // Point the gateway slot at the router we just learned. esp_ip4addr_ntoa
    // writes into the static buffer the SNTP server list already references.
    esp_ip4addr_ntoa(&e->ip_info.gw, s_gw_server, sizeof(s_gw_server));

    if (!s_started) {
        s_started = true;
        esp_err_t err = esp_netif_sntp_start();
        // INVALID_STATE just means it is already running (e.g. the renew
        // handler beat us to it) — not an error worth surfacing.
        if (err != ESP_OK && err != ESP_ERR_INVALID_STATE) {
            ESP_LOGW(TAG, "esp_netif_sntp_start: %s", esp_err_to_name(err));
        }
    }
}

void time_sync_set_timezone(const char *posix_tz)
{
    if (!posix_tz || !posix_tz[0]) return;
    setenv("TZ", posix_tz, 1);
    tzset();
    ESP_LOGI(TAG, "timezone set: %s", posix_tz);
}

void time_sync_start(void)
{
    static bool inited = false;
    if (inited) return;
    inited = true;

    // Apply the persisted timezone up front so the very first clock-set
    // already lands in local time.
    time_sync_set_timezone(config_timezone());

    // Static list [gateway, pool.ntp.org]; with server_from_dhcp the
    // DHCP-supplied servers are inserted at index 0, pushing these to 1/2.
    // Effective order: DHCP option 42 -> gateway -> pool.ntp.org.
    esp_sntp_config_t config =
        ESP_NETIF_SNTP_DEFAULT_CONFIG_MULTIPLE(TIME_SYNC_STATIC_SERVERS,
            ESP_SNTP_SERVER_LIST(s_gw_server, "pool.ntp.org"));
    config.start                      = false;  // wait for the first IP
    config.server_from_dhcp           = true;   // adopt option-42 servers
    config.index_of_first_server      = 1;      // DHCP fills index 0
    config.renew_servers_after_new_IP = true;   // refresh on reconnect
    config.ip_event_to_renew          = IP_EVENT_STA_GOT_IP;
    config.sync_cb                    = on_time_set;
    config.smooth_sync                = false;  // step, don't slew

    esp_err_t err = esp_netif_sntp_init(&config);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "esp_netif_sntp_init: %s", esp_err_to_name(err));
        return;
    }

    // Learn the gateway and kick off SNTP on the first IP. Register before
    // the network comes up so the (blocking) connect's GOT_IP is not
    // missed. Cover the Wi-Fi (production) and Ethernet (QEMU) paths; both
    // carry ip_event_got_ip_t.
    ESP_ERROR_CHECK(esp_event_handler_register(
            IP_EVENT, IP_EVENT_STA_GOT_IP, on_got_ip, NULL));
    ESP_ERROR_CHECK(esp_event_handler_register(
            IP_EVENT, IP_EVENT_ETH_GOT_IP, on_got_ip, NULL));
}

bool time_sync_valid(void)
{
    return s_valid;
}

int64_t time_sync_now_epoch(void)
{
    if (!s_valid) return 0;
    return (int64_t)time(NULL);
}
