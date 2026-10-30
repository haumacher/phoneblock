#include "report_queue.h"

#include <string.h>

#include "api.h"
#include "platform.h"
#include "rtp.h"
#include "webhook.h"

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

// Worker stack, most of it for the TLS handshake of the report POST. On the
// ESP32-S3 the POST peaked at 4228 B (measured with 8 KB: 3964 B free), so
// 4 KB overflowed on the first reported spam call and rebooted the dongle.
// The C3 fits into 4 KB (RISC-V frames are smaller), and the ESP32 fleet's
// heap is too tight to give more where it is not needed; the S3 has more
// than 100 KB free.
#if defined(CONFIG_IDF_TARGET_ESP32S3)
#define REPORT_TASK_STACK 8192
#else
#define REPORT_TASK_STACK 4096
#endif

// The call webhook shares this worker instead of a task of its own (a
// second 4 KB stack is a lot on the ESP32): its entries carry the call's
// sequence number in the stats ring, not a copy of the call.
typedef enum {
    REPORT_KIND_REPORT,
    REPORT_KIND_WEBHOOK,
} report_kind_t;

typedef struct {
    report_kind_t kind;
    uint32_t      call_seq;                    // REPORT_KIND_WEBHOOK
    char          phone[REPORT_PHONE_MAX];     // REPORT_KIND_REPORT
} report_entry_t;

static pb_queue_t *s_queue = NULL;

void report_queue_wait_announcement(void)
{
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
}

static void report_worker_task(void *arg)
{
    (void)arg;
    while (1) {
        report_entry_t e;
        pb_queue_receive(s_queue, &e);

        if (e.kind == REPORT_KIND_WEBHOOK) {
            // Plain http in the home network; webhook_deliver() waits out
            // an announcement itself, but only for spam calls, so a
            // legitimate call is posted while the phone still rings.
            webhook_deliver(e.call_seq);
            continue;
        }

        report_queue_wait_announcement();

        pb_log_info(TAG, "report-call %s", e.phone);
        // phoneblock_report_call logs its own failures (ERROR), which the
        // log hook mirrors to the web UI — nothing to do here on failure.
        phoneblock_report_call(e.phone);
    }
}

void report_queue_start(void)
{
    if (s_queue) return;
    s_queue = pb_queue_create(REPORT_QUEUE_DEPTH, sizeof(report_entry_t));
    if (!s_queue) {
        pb_log_err(TAG, "queue create failed");
        return;
    }
    if (pb_task_create(report_worker_task, NULL, "report_q", REPORT_TASK_STACK,
                       PB_PRIO_BACKGROUND) != PB_OK) {
        pb_log_err(TAG, "worker task create failed");
    }
}

void report_queue_enqueue(const char *phone)
{
    if (!s_queue || !phone || !*phone) return;

    report_entry_t e;
    memset(&e, 0, sizeof(e));
    e.kind = REPORT_KIND_REPORT;
    size_t n = strlen(phone);
    if (n >= sizeof(e.phone)) n = sizeof(e.phone) - 1;
    memcpy(e.phone, phone, n);
    e.phone[n] = '\0';

    if (!pb_queue_send(s_queue, &e)) {
        pb_log_warn(TAG, "queue full, dropping report for %s", e.phone);
    }
}

void report_queue_enqueue_webhook(uint32_t call_seq)
{
    if (!s_queue || call_seq == 0) return;

    report_entry_t e;
    memset(&e, 0, sizeof(e));
    e.kind = REPORT_KIND_WEBHOOK;
    e.call_seq = call_seq;

    if (!pb_queue_send(s_queue, &e)) {
        pb_log_warn(TAG, "queue full, dropping webhook for call %u",
                    (unsigned)call_seq);
    }
}
