#include "selftest.h"

#include <string.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/semphr.h"

#include "esp_log.h"
#include "esp_timer.h"

#include "api.h"
#include "config.h"

static const char *TAG = "selftest";

// 24 h between scheduled runs — same cadence the sync task uses.
#define SELFTEST_INTERVAL_US    (24LL * 3600LL * 1000000LL)

static TaskHandle_t      s_task    = NULL;
static SemaphoreHandle_t s_trigger = NULL;   // binary, manual fire
static SemaphoreHandle_t s_lock    = NULL;   // guards s_status
static SemaphoreHandle_t s_run_mtx = NULL;   // serialises run_now()
static selftest_status_t s_status;

static void set_result(bool ok, const char *err)
{
    xSemaphoreTake(s_lock, portMAX_DELAY);
    s_status.ever_ran    = true;
    s_status.last_ok     = ok;
    s_status.running     = false;
    s_status.last_at_us  = esp_timer_get_time();
    if (err) {
        strncpy(s_status.last_error, err, sizeof(s_status.last_error) - 1);
        s_status.last_error[sizeof(s_status.last_error) - 1] = '\0';
    } else {
        s_status.last_error[0] = '\0';
    }
    xSemaphoreGive(s_lock);
}

bool selftest_run_now(void)
{
    if (!s_run_mtx) return false;
    if (xSemaphoreTake(s_run_mtx, 0) != pdTRUE) {
        // Another caller is mid-flight — let them update the snapshot.
        return false;
    }

    xSemaphoreTake(s_lock, portMAX_DELAY);
    s_status.running = true;
    xSemaphoreGive(s_lock);

    bool ok = false;
    if (strlen(config_phoneblock_token()) == 0) {
        set_result(false, "no token configured");
    } else {
        ok = phoneblock_selftest();
        set_result(ok, ok ? NULL : "token rejected or unreachable");
    }

    xSemaphoreGive(s_run_mtx);
    return ok;
}

bool selftest_trigger_now(void)
{
    if (!s_task || !s_trigger) return false;
    xSemaphoreTake(s_lock, portMAX_DELAY);
    bool running = s_status.running;
    xSemaphoreGive(s_lock);
    if (running) return false;
    xSemaphoreGive(s_trigger);
    return true;
}

void selftest_snapshot(selftest_status_t *out)
{
    if (!out) return;
    if (!s_lock) { memset(out, 0, sizeof(*out)); return; }
    xSemaphoreTake(s_lock, portMAX_DELAY);
    *out = s_status;
    xSemaphoreGive(s_lock);
}

static void selftest_task(void *arg)
{
    (void)arg;
    TickType_t timeout = pdMS_TO_TICKS(SELFTEST_INTERVAL_US / 1000);
    // The boot-time call runs synchronously from app_main, so the
    // first scheduled iteration happens one full interval later. A
    // manual trigger (web UI, token callback) can fire sooner.
    while (1) {
        bool manual = (xSemaphoreTake(s_trigger, timeout) == pdTRUE);
        if (strlen(config_phoneblock_token()) == 0) {
            // Nothing to check — wait for the next tick. Manual
            // triggers from the UI on a tokenless device still go
            // through run_now() so the snapshot reflects the state.
            (void)manual;
            continue;
        }
        ESP_LOGI(TAG, "%s self-test", manual ? "manual" : "scheduled");
        selftest_run_now();
    }
}

void selftest_start(void)
{
    if (s_task) return;
    memset(&s_status, 0, sizeof(s_status));
    s_lock    = xSemaphoreCreateMutex();
    s_trigger = xSemaphoreCreateBinary();
    s_run_mtx = xSemaphoreCreateMutex();
    xTaskCreate(selftest_task, "selftest", 4096, NULL, 3, &s_task);
}
