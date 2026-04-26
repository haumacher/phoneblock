#include "selftest.h"

#include <string.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include "esp_log.h"
#include "esp_random.h"

#include "api.h"
#include "config.h"

static const char *TAG = "selftest";

// 24 h between scheduled runs — same cadence as the sync task.
#define SELFTEST_INTERVAL_MS    (24 * 3600 * 1000)
// ±30 min skew so a fleet-wide power blip doesn't line every dongle
// up to the same minute on /api/test forever after.
#define SELFTEST_JITTER_MS      (30 * 60 * 1000)

static TaskHandle_t s_task = NULL;

static void selftest_task(void *arg)
{
    (void)arg;
    // The boot-time check runs synchronously from app_main, so the
    // first scheduled iteration happens one full interval later.
    while (1) {
        uint32_t jitter = esp_random() % (2u * SELFTEST_JITTER_MS);
        uint32_t delay  = SELFTEST_INTERVAL_MS - SELFTEST_JITTER_MS + jitter;
        vTaskDelay(pdMS_TO_TICKS(delay));
        if (strlen(config_phoneblock_token()) == 0) continue;
        ESP_LOGI(TAG, "scheduled token self-test");
        phoneblock_selftest();
    }
}

void selftest_start(void)
{
    if (s_task) return;
    xTaskCreate(selftest_task, "selftest", 4096, NULL, 3, &s_task);
}
