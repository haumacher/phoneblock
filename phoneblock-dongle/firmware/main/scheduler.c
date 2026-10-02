#include "scheduler.h"

#include <stddef.h>
#include <stdint.h>
#include <time.h>

#ifdef ESP_PLATFORM
#include "sdkconfig.h"
#endif
#ifndef CONFIG_MAIL_DAILY_HOUR
#define CONFIG_MAIL_DAILY_HOUR 23
#endif

#include "blocklist_sync.h"
#include "firmware_update.h"
#include "i18n_sync.h"
#include "mail.h"
#include "sched_time.h"
#include "selftest.h"
#include "sync.h"
#include "platform.h"
#include "time_sync.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

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
// Task-notification bit for an on-demand status-mail test from the web UI.
// The send runs here, on the scheduler task's stack, so the httpd handler
// never blocks on the SMTP/TLS conversation (see scheduler_request_mail_test).
#define NOTIFY_MAIL_TEST (1u << 3)
// Task-notification bit for an on-demand localized-asset download, raised
// when the user switches the UI language (see i18n_sync_trigger_now).
#define NOTIFY_I18N      (1u << 4)

// While the wall clock is not yet set, a daily job cannot know when its
// local time-of-day next falls. Re-check this often so it fires promptly
// once SNTP succeeds (NOTIFY_TIME also wakes us, so this is just a
// backstop for the case the notification is somehow missed).
#define DAILY_CLOCK_WAIT_S  (60 * 60)

// Backoff bounds for a RETRY job's failed attempts: the first retry 5 min
// after a failure, doubling up to one try a day.
#define RETRY_MIN_S  (5 * 60)
#define RETRY_MAX_S  DAY_S

// next_due_us of a RETRY job that has succeeded: it only runs again on a
// manual trigger.
#define NEVER_US     INT64_MAX

static pb_event_t *s_events;

// INTERVAL: fire every interval_s (±jitter) off the monotonic clock — the
// original behaviour, robust without a wall clock and spread across a
// fleet. DAILY: fire at a fixed local time-of-day (needs the wall clock).
// RETRY: fire once after first_delay_s and repeat with backoff only until
// an attempt succeeds; after that, only a manual trigger runs it again.
typedef enum { SCHED_INTERVAL, SCHED_DAILY, SCHED_RETRY } sched_kind_t;

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
    // INTERVAL / DAILY jobs:
    void      (*run)(void);
    // RETRY jobs: returns whether the attempt succeeded. backoff_s is the
    // delay before the next retry, doubled after each failure.
    bool      (*attempt)(void);
    uint32_t    backoff_s;
} sched_job_t;

static void run_selftest(void)  { selftest_run(); }
static void run_fw_update(void) { firmware_update_run(); }
static void run_sync(void)      { sync_run(false); }   // scheduled: honour toggle
// Send the one-shot firmware-update notice first (no-op unless an OTA just
// completed this boot), then the daily error/spam evaluation. Both send over
// the scheduler task's 16 KB stack. The mail job first fires
// MAIL_FIRST_DELAY_S after boot, so the update notice goes out within minutes.
static void run_mail(void)      { mail_report_update(); mail_daily_flush(); }
static void run_blocklist(void) { blocklist_sync_run(); }

// First mail evaluation 5 min after boot: long enough for Wi-Fi/DHCP to
// settle, short enough that a crash-reboot's ERROR is mailed promptly.
// Kept even though the mail job is now wall-clock daily — the fixed daily
// slot alone would let a post-crash error wait up to a day.
#define MAIL_FIRST_DELAY_S       (5 * 60)

// Local hour the daily status mail is sent at (build-time configurable,
// CONFIG_MAIL_DAILY_HOUR, default 23:00 to flush the day's spam reports /
// errors at day's end). The mail goes through the user's own SMTP server,
// so there is no fleet-wide endpoint to spread the load across — a fixed,
// predictable time is what a user wants. The send is a no-op unless there
// is a new error / new spam since the last one (see mail_daily_flush), so
// a quiet day mails nothing.
#define MAIL_DAILY_HOUR          CONFIG_MAIL_DAILY_HOUR

// First blocklist download 2 min after boot so a provisioned dongle
// repopulates its local cache without waiting a full day; long enough for
// Wi-Fi/DHCP/TLS to settle. The job short-circuits without a token.
#define BLOCKLIST_FIRST_DELAY_S  (2 * 60)

// Localized-asset sync 3 min after boot — after Wi-Fi/DHCP/TLS settle, and
// staggered a minute behind the blocklist download so the two CDN pulls
// don't contend. No periodic re-run: a release's bundle only changes with a
// new firmware version, and the boot after its OTA fetches it. A failed pass
// (offline, CDN hiccup) is retried, since a missing announcement means the
// device answers spam calls silently.
#define I18N_FIRST_DELAY_S       (3 * 60)

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
    { .name = "i18n",      .kind = SCHED_RETRY,    .first_delay_s = I18N_FIRST_DELAY_S, .attempt = i18n_sync_run, .backoff_s = RETRY_MIN_S },
};
#define JOB_COUNT (sizeof(s_jobs) / sizeof(s_jobs[0]))

static int64_t next_due_interval(const sched_job_t *j, int64_t now)
{
    uint32_t jitter  = pb_random_u32() % (2u * j->jitter_s);
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

// Run one attempt of a RETRY job and schedule the next: never again once
// it succeeded, otherwise after the current backoff, which then doubles.
static void run_retry(sched_job_t *j)
{
    if (j->attempt()) {
        j->next_due_us = NEVER_US;
        j->backoff_s   = RETRY_MIN_S;
        return;
    }
    pb_log_warn(TAG, "%s failed, retry in %u s", j->name, (unsigned)j->backoff_s);
    j->next_due_us = (int64_t)pb_monotonic_us() + (int64_t)j->backoff_s * 1000000;
    j->backoff_s   = j->backoff_s >= RETRY_MAX_S / 2 ? RETRY_MAX_S
                                                     : j->backoff_s * 2;
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
    int64_t now = (int64_t)pb_monotonic_us();
    for (size_t i = 0; i < JOB_COUNT; i++)
        s_jobs[i].next_due_us = s_jobs[i].first_delay_s
            ? now + (int64_t)s_jobs[i].first_delay_s * 1000000
            : next_due(&s_jobs[i], now);

    while (1) {
        now = (int64_t)pb_monotonic_us();
        int64_t earliest = s_jobs[0].next_due_us;
        for (size_t i = 1; i < JOB_COUNT; i++)
            if (s_jobs[i].next_due_us < earliest) earliest = s_jobs[i].next_due_us;

        // Sleep until the nearest due time, or until a manual trigger
        // wakes us. Sub-second precision is irrelevant for daily jobs;
        // round a positive remainder up to 1 s so the loop never busy-spins.
        int64_t  wait_us = earliest - now;
        uint32_t wait_s  = wait_us <= 0 ? 0 : (uint32_t)(wait_us / 1000000);
        if (wait_us > 0 && wait_s == 0) wait_s = 1;

        uint32_t wait_ms = wait_s * 1000u;
        uint32_t notify = pb_event_wait(s_events, wait_ms);

        now = (int64_t)pb_monotonic_us();

        // On-demand sync from the web UI: run it regardless of the
        // auto-sync toggle (manual intent), then reset its scheduled slot
        // so the daily run doesn't fire again right behind it.
        if (notify & NOTIFY_SYNC) {
            pb_log_info(TAG, "manual sync trigger");
            sync_run(true);
            for (size_t i = 0; i < JOB_COUNT; i++)
                if (s_jobs[i].run == run_sync)
                    s_jobs[i].next_due_us = next_due(&s_jobs[i],
                                                     (int64_t)pb_monotonic_us());
        }

        // On-demand blocklist download from the web UI / token-set
        // handler, then reset its scheduled slot so the daily run doesn't
        // fire again right behind it.
        if (notify & NOTIFY_BLOCKLIST) {
            pb_log_info(TAG, "manual blocklist trigger");
            blocklist_sync_run();
            for (size_t i = 0; i < JOB_COUNT; i++)
                if (s_jobs[i].run == run_blocklist)
                    s_jobs[i].next_due_us = next_due(&s_jobs[i],
                                                     (int64_t)pb_monotonic_us());
        }

        // On-demand status-mail test from the web UI. Runs the blocking
        // SMTP/TLS send here rather than in the httpd handler, which must
        // return promptly (its task watchdog panics a wedged handler — see
        // web.c). Fire-and-forget: mail_send_test() logs the outcome at
        // INFO/WARN, which the web UI's log panel surfaces.
        if (notify & NOTIFY_MAIL_TEST) {
            pb_log_info(TAG, "manual mail-test trigger");
            mail_send_test();
        }

        // On-demand localized-asset download (user switched UI language).
        // Restarts the job's retry cycle from the shortest backoff, and
        // replaces any retry that was still pending for the old locale.
        if (notify & NOTIFY_I18N) {
            pb_log_info(TAG, "manual i18n trigger");
            for (size_t i = 0; i < JOB_COUNT; i++)
                if (s_jobs[i].attempt == i18n_sync_run) {
                    s_jobs[i].backoff_s = RETRY_MIN_S;
                    run_retry(&s_jobs[i]);
                }
        }

        // The wall clock just became valid (or stepped to a new time).
        // Daily jobs parked on the clock-wait retry — or computed against
        // a now-stale time — must recompute against real local time.
        if (notify & NOTIFY_TIME) {
            pb_log_info(TAG, "wall clock synced — rescheduling daily jobs");
            for (size_t i = 0; i < JOB_COUNT; i++)
                if (s_jobs[i].kind == SCHED_DAILY)
                    s_jobs[i].next_due_us =
                        next_due_daily(&s_jobs[i],
                                       (int64_t)pb_monotonic_us());
        }

        // Fire every job whose scheduled time has come, then reschedule
        // it. The monotonic clock is re-read after each run so a job's
        // own duration counts against its next interval.
        for (size_t i = 0; i < JOB_COUNT; i++) {
            if (now >= s_jobs[i].next_due_us) {
                if (s_jobs[i].kind == SCHED_RETRY) {
                    run_retry(&s_jobs[i]);
                } else {
                    s_jobs[i].run();
                    s_jobs[i].next_due_us = next_due(&s_jobs[i],
                                                     (int64_t)pb_monotonic_us());
                }

            }
        }
    }
}

bool scheduler_request_sync(void)
{
    if (!s_events) return false;
    pb_event_set(s_events, NOTIFY_SYNC);
    return true;
}

bool scheduler_request_blocklist_sync(void)
{
    if (!s_events) return false;
    pb_event_set(s_events, NOTIFY_BLOCKLIST);
    return true;
}

bool scheduler_request_mail_test(void)
{
    if (!s_events) return false;
    pb_event_set(s_events, NOTIFY_MAIL_TEST);
    return true;
}

bool scheduler_request_i18n_sync(void)
{
    if (!s_events) return false;
    pb_event_set(s_events, NOTIFY_I18N);
    return true;
}

void scheduler_notify_time_synced(void)
{
    // No-op before the task exists: scheduler_task() computes daily due
    // times from the live clock when it starts, so an early clock-set
    // needs no notification.
    if (s_events) pb_event_set(s_events, NOTIFY_TIME);
}

void scheduler_start(bool i18n_refresh_soon)
{
    if (s_events) return;
    // Create the sync / blocklist status mutexes before the task (or any
    // web-UI snapshot) can touch them.
    sync_init();
    blocklist_sync_init();
    i18n_sync_init();
    // First boot after a firmware update: pull this version's localized assets
    // ~30 s after boot (enough for Wi-Fi/DHCP to come up) instead of the usual
    // 3 min, so the new release's announcement / mail / UI refresh promptly.
    if (i18n_refresh_soon) {
        for (size_t i = 0; i < JOB_COUNT; i++)
            if (s_jobs[i].attempt == i18n_sync_run) s_jobs[i].first_delay_s = 30;
    }
    // 16 KB: sized for the heaviest job, the status-mail send. Its SMTP
    // client runs the mbedTLS handshake with full cert-chain verification
    // inline on this stack (esp_crt_bundle + ssl_config/entropy/drbg as
    // stack locals) — that overflowed an 8 KB stack in the field (mail.c
    // mbedtls_ssl_handshake → X.509 verify), the same way the SIP TLS path
    // needed 12 KB back in firmware 1.0.10. The OTA install, TLS self-test
    // and TR-064 sync fit comfortably below this.
    s_events = pb_event_create();
    if (!s_events) {
        pb_log_err(TAG, "event creation failed");
        return;
    }
    if (!pb_task_create(scheduler_task, NULL, "scheduler", 16384,
                        PB_PRIO_BACKGROUND)) {
        pb_event_destroy(s_events);
        s_events = NULL;
        pb_log_err(TAG, "task creation failed");
    }
}
