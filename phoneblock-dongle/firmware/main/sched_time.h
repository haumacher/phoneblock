#pragma once

#include <time.h>

// Whole seconds from `now` (a UTC time_t) until the next occurrence of
// the local wall-clock time at_hour:at_minute:00. Always returns a value
// > 0 — if the target is "right now", the next occurrence is tomorrow.
//
// Honours the active timezone (set via setenv("TZ")+tzset()), including
// DST transitions, through localtime_r/mktime. A day that loses or gains
// an hour at a DST boundary therefore yields 23 h / 25 h, not a naive 24 h.
//
// Pure libc, no ESP-IDF dependency, so the scheduler's hardest bit of
// arithmetic is exercised by the host test harness (test_sched_time.c).
long seconds_until_daily(time_t now, int at_hour, int at_minute);

#include <stdbool.h>
#include <stdint.h>

// A RETRY job of the scheduler (scheduler.c): retried with a doubling
// backoff from min_s up to max_s until an attempt succeeds, then — with
// recheck_s set — run again recheck_s ± jitter_s later (the i18n job: the
// audio channel changes without a firmware update), otherwise never.
typedef struct {
    uint32_t min_s;
    uint32_t max_s;
    uint32_t recheck_s;   // 0: no run after a success
    uint32_t jitter_s;    // < recheck_s
} sched_retry_t;

// sched_retry_delay_s() for "not again".
#define SCHED_NEVER_S UINT32_MAX

// Seconds until the job's next run after an attempt that succeeded (`ok`)
// or failed. *backoff_s is the job's current backoff: a failure waits that
// long and doubles it (capped at max_s), a success resets it to min_s.
// `rnd` is a random number, spreading a fleet's rechecks over the jitter
// window.
uint32_t sched_retry_delay_s(const sched_retry_t *job, bool ok,
                             uint32_t *backoff_s, uint32_t rnd);
