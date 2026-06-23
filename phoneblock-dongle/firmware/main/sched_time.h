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
