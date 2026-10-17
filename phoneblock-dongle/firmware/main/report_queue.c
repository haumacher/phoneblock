#include "report_queue.h"

#include <string.h>

#include "api.h"
#include "platform.h"
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
#define REPORT_QUEUE_EVENT 1u

typedef struct {
    char phone[REPORT_PHONE_MAX];
} report_entry_t;

static report_entry_t s_queue[REPORT_QUEUE_DEPTH];
static unsigned s_head;
static unsigned s_count;
static pb_mutex_t *s_lock;
static pb_event_t *s_event;
static bool s_running;
static bool s_attempted;

static void report_worker_task(void *arg)
{
    (void)arg;
    while (1) {
        report_entry_t entry;
        pb_mutex_lock(s_lock);
        if (s_count == 0) {
            pb_mutex_unlock(s_lock);
            // Events are latched, so an enqueue between the empty check and
            // this wait is not lost. Zero is a poll on both platforms.
            pb_event_wait_s(s_event, 3600);
            continue;
        }
        entry = s_queue[s_head];
        s_head = (s_head + 1) % REPORT_QUEUE_DEPTH;
        s_count--;
        pb_mutex_unlock(s_lock);

        // Defer the report's TLS handshake until any in-progress
        // announcement has finished. Running a second TLS session next to
        // the SRTP media stream exhausts the ESP32 heap (RTP sendto fails
        // with ENOMEM, mbedTLS cert parse OOMs, httpd-task watchdog
        // reboot). The report is enqueued at SPAM detection, before the
        // ACK that starts streaming, so first give the stream up to ~2 s
        // to start (a quick no-announcement call falls through), then wait
        // it out — capped so a stuck flag can't wedge the worker forever.
        for (int i = 0; i < 40 && !rtp_streaming_active(); i++) {
            pb_task_sleep_ms(50);
        }
        for (int i = 0; i < 300 && rtp_streaming_active(); i++) {
            pb_task_sleep_ms(100);
        }

        pb_log_info(TAG, "report-call %s", entry.phone);
        // phoneblock_report_call logs its own failures (ERROR), which the
        // log hook mirrors to the web UI — nothing to do here on failure.
        phoneblock_report_call(entry.phone);
    }
}

void report_queue_start(void)
{
    if (s_attempted) return;
    s_attempted = true;
    s_event = pb_event_create();
    if (!s_event) {
        pb_log_err(TAG, "queue event creation failed");
        return;
    }
    s_lock = pb_mutex_create();
    if (!s_lock) {
        pb_log_err(TAG, "queue mutex creation failed");
        pb_event_destroy(s_event);
        s_event = NULL;
        return;
    }
    if (pb_task_create(report_worker_task, NULL, "report_q", 4096,
                       PB_PRIO_BACKGROUND) != PB_OK) {
        pb_log_err(TAG, "worker task creation failed");
        pb_event_destroy(s_event);
        s_event = NULL;
        return;
    }
    s_running = true;
}

void report_queue_enqueue(const char *phone)
{
    if (!s_running || !phone || !*phone) return;

    report_entry_t e;
    size_t n = strlen(phone);
    if (n >= sizeof(e.phone)) n = sizeof(e.phone) - 1;
    memcpy(e.phone, phone, n);
    e.phone[n] = '\0';

    pb_mutex_lock(s_lock);
    if (s_count == REPORT_QUEUE_DEPTH) {
        pb_mutex_unlock(s_lock);
        pb_log_warn(TAG, "queue full, dropping report for %s", e.phone);
        return;
    }
    unsigned tail = (s_head + s_count) % REPORT_QUEUE_DEPTH;
    s_queue[tail] = e;
    s_count++;
    pb_mutex_unlock(s_lock);
    pb_event_set(s_event, REPORT_QUEUE_EVENT);
}
