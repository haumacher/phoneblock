#include "selftest.h"

#include <string.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include "esp_log.h"
#include "esp_random.h"

#include "api.h"
#include "config.h"
#include "logreport.h"
#include "ticks_util.h"

static const char *TAG = "selftest";

// 24 h between scheduled runs — same cadence as the sync task.
#define SELFTEST_INTERVAL_S     (24 * 3600)
// ±30 min skew so a fleet-wide power blip doesn't line every dongle
// up to the same minute on /api/test forever after.
#define SELFTEST_JITTER_S       (30 * 60)

static TaskHandle_t s_task = NULL;

static void selftest_task(void *arg)
{
    (void)arg;
    // The boot-time check runs synchronously from app_main, so the
    // first scheduled iteration happens one full interval later.
    while (1) {
        uint32_t jitter  = esp_random() % (2u * SELFTEST_JITTER_S);
        uint32_t delay_s = SELFTEST_INTERVAL_S - SELFTEST_JITTER_S + jitter;
        vTaskDelay(seconds_to_ticks(delay_s));
        if (strlen(config_phoneblock_token()) == 0) continue;
        ESP_LOGI(TAG, "scheduled token self-test");
        phoneblock_selftest(NULL);
        // Piggyback the daily wakeup: ship any new WARN/ERROR log lines
        // so a running-but-misbehaving dongle (e.g. one that lost SIP
        // registration without crashing) becomes visible server-side
        // instead of only on the local web UI. No-op when there's
        // nothing new or the user opted out.
        logreport_flush();
    }
}

void selftest_start(void)
{
    if (s_task) return;
    // TLS handshake + getaddrinfo in phoneblock_selftest() blow past
    // 4 KB and smash the adjacent heap block, corrupting the TLSF
    // free-list (crashes show up as heap asserts in random other tasks:
    // tiT, wifi, mdns, ...). The same selftest runs fine synchronously
    // on the 8 KB main task at boot, so match that proven size here.
    xTaskCreate(selftest_task, "selftest", 8192, NULL, 3, &s_task);
}
