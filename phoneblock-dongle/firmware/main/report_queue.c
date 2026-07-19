#include "report_queue.h"

#include <string.h>

#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/task.h"

#include "esp_log.h"

#include "api.h"
#include "rtp.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

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

        // Defer the report's TLS handshake until any in-progress
        // announcement has finished. Running a second TLS session next to
        // the SRTP media stream exhausts the ESP32 heap (RTP sendto fails
        // with ENOMEM, mbedTLS cert parse OOMs, httpd-task watchdog
        // reboot). The report is enqueued at SPAM detection, before the
        // ACK that starts streaming, so first give the stream up to ~2 s
        // to start (a quick no-announcement call falls through), then wait
        // it out — capped so a stuck flag can't wedge the worker forever.
        for (int i = 0; i < 40 && !rtp_streaming_active(); i++) {
            vTaskDelay(pdMS_TO_TICKS(50));
        }
        for (int i = 0; i < 300 && rtp_streaming_active(); i++) {
            vTaskDelay(pdMS_TO_TICKS(100));
        }

        ESP_LOGI(TAG, "report-call %s", e.phone);
        // phoneblock_report_call logs its own failures (ERROR), which the
        // log hook mirrors to the web UI — nothing to do here on failure.
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
