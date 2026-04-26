#include "report_queue.h"

#include <string.h>

#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/task.h"

#include "esp_log.h"

#include "api.h"

static const char *TAG = "report_q";

// Phone numbers in the queue are E.164 / "00" form, never longer
// than ~20 chars. 64 leaves slack for unusual entries without
// wasting RAM.
#define REPORT_PHONE_MAX 64

// Queue depth. A SPAM call occupies the SIP dialog for 5–15 s
// (announcement playback) and the worker drains an entry in 1–2 s,
// so the queue is realistically empty whenever a new entry arrives.
// 4 is plenty of headroom; if a burst overflows, we drop and the
// server's call counter is just a touch stale.
#define REPORT_QUEUE_DEPTH 4

typedef struct {
    char phone[REPORT_PHONE_MAX];
} report_entry_t;

static QueueHandle_t s_queue = NULL;
static TaskHandle_t  s_task  = NULL;

static void report_worker_task(void *arg)
{
    (void)arg;
    while (1) {
        report_entry_t e;
        if (xQueueReceive(s_queue, &e, portMAX_DELAY) != pdTRUE) continue;
        ESP_LOGI(TAG, "report-call %s", e.phone);
        // Errors get recorded by phoneblock_report_call itself via
        // stats_record_error — nothing to do here on failure.
        phoneblock_report_call(e.phone);
    }
}

void report_queue_start(void)
{
    if (s_queue) return;
    s_queue = xQueueCreate(REPORT_QUEUE_DEPTH, sizeof(report_entry_t));
    if (!s_queue) {
        ESP_LOGE(TAG, "queue create failed");
        return;
    }
    xTaskCreate(report_worker_task, "report_q", 4096, NULL, 3, &s_task);
}

void report_queue_enqueue(const char *phone)
{
    if (!s_queue || !phone || !*phone) return;

    report_entry_t e;
    size_t n = strlen(phone);
    if (n >= sizeof(e.phone)) n = sizeof(e.phone) - 1;
    memcpy(e.phone, phone, n);
    e.phone[n] = '\0';

    if (xQueueSend(s_queue, &e, 0) != pdTRUE) {
        ESP_LOGW(TAG, "queue full, dropping report for %s", e.phone);
    }
}
