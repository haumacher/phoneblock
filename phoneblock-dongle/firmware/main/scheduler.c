#include "scheduler.h"

#include <stddef.h>
#include <stdint.h>
#include <time.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include "esp_log.h"
#include "esp_random.h"
#include "esp_timer.h"

#include "blocklist_sync.h"
#include "firmware_update.h"
#include "mail.h"
#include "sched_time.h"
#include "selftest.h"
#include "sync.h"
#include "ticks_util.h"
#include "time_sync.h"

static const char *TAG = "scheduler";

// 24 h between scheduled runs, with ±30 min skew so a fleet-wide power
// blip doesn't line every dongle up onto the same minute forever after.
#define DAY_S       (24 * 3600)
#define JITTER_S    (30 * 60)

// Task-notification bit for an on-demand sync triggered from the web UI.
#define NOTIFY_SYNC      (1u << 0)
// Task-notification bit for an on-demand binary-blocklist download.
#define NOTIFY_BLOCKLIST (1u << 1)
// Task-notification bit raised by time_sync.c once the wall clock is set.
#define NOTIFY_TIME      (1u << 2)

// While the wall clock is not yet set, a daily job cannot know when its
// local time-of-day next falls. Re-check this often so it fires promptly
// once SNTP succeeds (NOTIFY_TIME also wakes us, so this is just a
// backstop for the case the notification is somehow missed).
#define DAILY_CLOCK_WAIT_S  (60 * 60)

static TaskHandle_t s_task = NULL;

// INTERVAL: fire every interval_s (±jitter) off the monotonic clock — the
// original behaviour, robust without a wall clock and spread across a
// fleet. DAILY: fire at a fixed local time-of-day (needs the wall clock).
typedef enum { SCHED_INTERVAL, SCHED_DAILY } sched_kind_t;

typedef struct {
    const char  *name;
    sched_kind_t kind;
    // INTERVAL jobs:
    uint32_t    interval_s;
    uint32_t    jitter_s;
    // First run delay in seconds: 0 = wait a full interval / until the
    // first scheduled time (the default — don't act at boot). A small
    // value makes the first run happen soon after boot regardless of
    // kind, used by the mail job so a post-crash status mail goes out
    // promptly rather than waiting for the next daily slot. Applies to
    // both INTERVAL and DAILY jobs.
    uint32_t    first_delay_s;
    // DAILY jobs: local wall-clock time of day to fire at.
    uint8_t     at_hour;       // 0..23
    uint8_t     at_minute;     // 0..59
    int64_t     next_due_us;   // esp_timer time of the next scheduled run
    void      (*run)(void);
} sched_job_t;

static void run_selftest(void)  { selftest_run(); }
static void run_fw_update(void) { firmware_update_run(); }
static void run_sync(void)      { sync_run(false); }   // scheduled: honour toggle
static void run_mail(void)      { mail_daily_flush(); }
static void run_blocklist(void) { blocklist_sync_run(); }

// First mail evaluation 5 min after boot: long enough for Wi-Fi/DHCP to
// settle, short enough that a crash-reboot's ERROR is mailed promptly.
// Kept even though the mail job is now wall-clock daily — the fixed daily
// slot alone would let a post-crash error wait up to a day.
#define MAIL_FIRST_DELAY_S       (5 * 60)

// Local hour the daily status mail is sent at (08:00). The mail goes
// through the user's own SMTP server, so there is no fleet-wide endpoint
// to spread the load across — a fixed, predictable morning time is what a
// user wants. The send is a no-op unless there is a new error / new spam
// since the last one (see mail_daily_flush), so a quiet day mails nothing.
#define MAIL_DAILY_HOUR          8

// First blocklist download 2 min after boot so a provisioned dongle
// repopulates its local cache without waiting a full day; long enough for
// Wi-Fi/DHCP/TLS to settle. The job short-circuits without a token.
#define BLOCKLIST_FIRST_DELAY_S  (2 * 60)

// The server-facing housekeeping jobs stay interval-based: they are
// best-effort and deliberately spread across the fleet by boot time +
// jitter, so a fleet-wide power blip doesn't align every dongle onto the
// same minute hammering phoneblock.net / the CDN. The status mail is
// wall-clock daily instead — it goes through the user's own SMTP (no
// shared endpoint to spread) and a fixed morning time is what a user
// wants. Daily jobs fall back to a retry until the clock is set and keep
// their boot-relative first run via first_delay_s.
static sched_job_t s_jobs[] = {
    { .name = "selftest",  .kind = SCHED_INTERVAL, .interval_s = DAY_S, .jitter_s = JITTER_S, .run = run_selftest },
    { .name = "fw_update", .kind = SCHED_INTERVAL, .interval_s = DAY_S, .jitter_s = JITTER_S, .run = run_fw_update },
    { .name = "sync",      .kind = SCHED_INTERVAL, .interval_s = DAY_S, .jitter_s = JITTER_S, .run = run_sync },
    { .name = "mail",      .kind = SCHED_DAILY,    .at_hour = MAIL_DAILY_HOUR, .first_delay_s = MAIL_FIRST_DELAY_S, .run = run_mail },
    { .name = "blocklist", .kind = SCHED_INTERVAL, .interval_s = DAY_S, .jitter_s = JITTER_S, .first_delay_s = BLOCKLIST_FIRST_DELAY_S, .run = run_blocklist },
};
#define JOB_COUNT (sizeof(s_jobs) / sizeof(s_jobs[0]))

static int64_t next_due_interval(const sched_job_t *j, int64_t now)
{
    uint32_t jitter  = esp_random() % (2u * j->jitter_s);
    uint32_t delay_s = j->interval_s - j->jitter_s + jitter;
    return now + (int64_t)delay_s * 1000000;
}

// Next-due time for a daily job, re-anchored onto the esp_timer axis the
// wait loop sleeps on. Until the wall clock is set, park on a short retry
// (NOTIFY_TIME wakes us the moment SNTP succeeds; this is the backstop).
static int64_t next_due_daily(const sched_job_t *j, int64_t now_us)
{
    if (!time_sync_valid())
        return now_us + (int64_t)DAILY_CLOCK_WAIT_S * 1000000;
    long secs = seconds_until_daily(time(NULL), j->at_hour, j->at_minute);
    return now_us + (int64_t)secs * 1000000;
}

static int64_t next_due(const sched_job_t *j, int64_t now)
{
    return j->kind == SCHED_DAILY ? next_due_daily(j, now)
                                  : next_due_interval(j, now);
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

        // On-demand blocklist download from the web UI / token-set
        // handler, then reset its scheduled slot so the daily run doesn't
        // fire again right behind it.
        if (notify & NOTIFY_BLOCKLIST) {
            ESP_LOGI(TAG, "manual blocklist trigger");
            blocklist_sync_run();
            for (size_t i = 0; i < JOB_COUNT; i++)
                if (s_jobs[i].run == run_blocklist)
                    s_jobs[i].next_due_us = next_due(&s_jobs[i],
                                                     esp_timer_get_time());
        }

        // The wall clock just became valid (or stepped to a new time).
        // Daily jobs parked on the clock-wait retry — or computed against
        // a now-stale time — must recompute against real local time.
        if (notify & NOTIFY_TIME) {
            ESP_LOGI(TAG, "wall clock synced — rescheduling daily jobs");
            for (size_t i = 0; i < JOB_COUNT; i++)
                if (s_jobs[i].kind == SCHED_DAILY)
                    s_jobs[i].next_due_us =
                        next_due_daily(&s_jobs[i], esp_timer_get_time());
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

bool scheduler_request_blocklist_sync(void)
{
    if (!s_task) return false;
    xTaskNotify(s_task, NOTIFY_BLOCKLIST, eSetBits);
    return true;
}

void scheduler_notify_time_synced(void)
{
    // No-op before the task exists: scheduler_task() computes daily due
    // times from the live clock when it starts, so an early clock-set
    // needs no notification.
    if (s_task) xTaskNotify(s_task, NOTIFY_TIME, eSetBits);
}

void scheduler_start(void)
{
    if (s_task) return;
    // Create the sync / blocklist status mutexes before the task (or any
    // web-UI snapshot) can touch them.
    sync_init();
    blocklist_sync_init();
    // 8 KB: sized for the heaviest job, the OTA install path
    // (esp_https_ota + cert-chain verify + SHA-256 over the 1.4 MB
    // image), which the standalone fw_update task also ran at 8 KB. The
    // TLS self-test and the TR-064 sync fit comfortably inside that.
    xTaskCreate(scheduler_task, "scheduler", 8192, NULL, 3, &s_task);
}
