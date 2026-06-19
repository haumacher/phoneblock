#include "scheduler.h"

#include <stddef.h>
#include <stdint.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include "esp_log.h"
#include "esp_random.h"
#include "esp_timer.h"

#include "firmware_update.h"
#include "mail.h"
#include "selftest.h"
#include "sync.h"
#include "ticks_util.h"

static const char *TAG = "scheduler";

// 24 h between scheduled runs, with ±30 min skew so a fleet-wide power
// blip doesn't line every dongle up onto the same minute forever after.
#define DAY_S       (24 * 3600)
#define JITTER_S    (30 * 60)

// Task-notification bit for an on-demand sync triggered from the web UI.
#define NOTIFY_SYNC (1u << 0)

static TaskHandle_t s_task = NULL;

typedef struct {
    const char *name;
    uint32_t    interval_s;
    uint32_t    jitter_s;
    // First run delay in seconds: 0 = a full (jittered) interval out
    // (the default — don't act at boot). A small value makes the first
    // run happen soon after boot, used by the mail job so a post-crash
    // status mail goes out promptly once the network is up rather than
    // up to a day later.
    uint32_t    first_delay_s;
    int64_t     next_due_us;   // esp_timer time of the next scheduled run
    void      (*run)(void);
} sched_job_t;

static void run_selftest(void)  { selftest_run(); }
static void run_fw_update(void) { firmware_update_run(); }
static void run_sync(void)      { sync_run(false); }   // scheduled: honour toggle
static void run_mail(void)      { mail_daily_flush(); }

// First mail evaluation 5 min after boot: long enough for Wi-Fi/DHCP to
// settle, short enough that a crash-reboot's ERROR is mailed promptly.
#define MAIL_FIRST_DELAY_S  (5 * 60)

static sched_job_t s_jobs[] = {
    { "selftest",  DAY_S, JITTER_S, 0,                  0, run_selftest },
    { "fw_update", DAY_S, JITTER_S, 0,                  0, run_fw_update },
    { "sync",      DAY_S, JITTER_S, 0,                  0, run_sync },
    { "mail",      DAY_S, JITTER_S, MAIL_FIRST_DELAY_S, 0, run_mail },
};
#define JOB_COUNT (sizeof(s_jobs) / sizeof(s_jobs[0]))

static int64_t next_due(const sched_job_t *j, int64_t now)
{
    uint32_t jitter  = esp_random() % (2u * j->jitter_s);
    uint32_t delay_s = j->interval_s - j->jitter_s + jitter;
    return now + (int64_t)delay_s * 1000000;
}

static void scheduler_task(void *arg)
{
    (void)arg;

    // First scheduled run of each job is one full (jittered) interval
    // out — never at boot. Matches the old per-task behaviour: the user
    // may still be in the middle of setup, and the boot path already ran
    // the synchronous self-test / validated the running image. A job with
    // first_delay_s set instead fires soon after boot (see the mail job).
    int64_t now = esp_timer_get_time();
    for (size_t i = 0; i < JOB_COUNT; i++)
        s_jobs[i].next_due_us = s_jobs[i].first_delay_s
            ? now + (int64_t)s_jobs[i].first_delay_s * 1000000
            : next_due(&s_jobs[i], now);

    while (1) {
        now = esp_timer_get_time();
        int64_t earliest = s_jobs[0].next_due_us;
        for (size_t i = 1; i < JOB_COUNT; i++)
            if (s_jobs[i].next_due_us < earliest) earliest = s_jobs[i].next_due_us;

        // Sleep until the nearest due time, or until a manual trigger
        // wakes us. Convert the wait to whole seconds first:
        // seconds_to_ticks() can't overflow for any interval that fits a
        // TickType_t, while a raw millisecond conversion of a ~24 h delay
        // would (see #348 / ticks_util.h). Sub-second precision is
        // irrelevant for daily jobs; round a positive remainder up to 1 s
        // so the loop never busy-spins.
        int64_t  wait_us = earliest - now;
        uint32_t wait_s  = wait_us <= 0 ? 0 : (uint32_t)(wait_us / 1000000);
        if (wait_us > 0 && wait_s == 0) wait_s = 1;

        uint32_t notify = 0;
        xTaskNotifyWait(0, UINT32_MAX, &notify,
                        wait_s ? seconds_to_ticks(wait_s) : 0);

        now = esp_timer_get_time();

        // On-demand sync from the web UI: run it regardless of the
        // auto-sync toggle (manual intent), then reset its scheduled slot
        // so the daily run doesn't fire again right behind it.
        if (notify & NOTIFY_SYNC) {
            ESP_LOGI(TAG, "manual sync trigger");
            sync_run(true);
            for (size_t i = 0; i < JOB_COUNT; i++)
                if (s_jobs[i].run == run_sync)
                    s_jobs[i].next_due_us = next_due(&s_jobs[i],
                                                     esp_timer_get_time());
        }

        // Fire every job whose scheduled time has come, then reschedule
        // it. esp_timer_get_time() is re-read after each run so a job's
        // own duration counts against its next interval.
        for (size_t i = 0; i < JOB_COUNT; i++) {
            if (now >= s_jobs[i].next_due_us) {
                s_jobs[i].run();
                s_jobs[i].next_due_us = next_due(&s_jobs[i],
                                                 esp_timer_get_time());
            }
        }
    }
}

bool scheduler_request_sync(void)
{
    if (!s_task) return false;
    xTaskNotify(s_task, NOTIFY_SYNC, eSetBits);
    return true;
}

void scheduler_start(void)
{
    if (s_task) return;
    // Create the sync status mutex before the task (or any web-UI
    // snapshot) can touch it.
    sync_init();
    // 8 KB: sized for the heaviest job, the OTA install path
    // (esp_https_ota + cert-chain verify + SHA-256 over the 1.4 MB
    // image), which the standalone fw_update task also ran at 8 KB. The
    // TLS self-test and the TR-064 sync fit comfortably inside that.
    xTaskCreate(scheduler_task, "scheduler", 8192, NULL, 3, &s_task);
}
