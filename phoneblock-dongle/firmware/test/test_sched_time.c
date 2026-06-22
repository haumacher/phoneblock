// Host test for sched_time.c — the daily "next run" arithmetic the
// scheduler relies on. Pure libc; uses the Europe/Berlin POSIX rule so the
// DST-boundary cases are exercised exactly as on-device.
#define _DEFAULT_SOURCE
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>

#include "sched_time.h"

#define BERLIN_TZ "CET-1CEST,M3.5.0,M10.5.0/3"

// Construct a UTC time_t from calendar components (timegm ignores TZ).
static time_t utc(int y, int mon, int d, int h, int mi, int s)
{
    struct tm tm = {0};
    tm.tm_year = y - 1900;
    tm.tm_mon  = mon - 1;
    tm.tm_mday = d;
    tm.tm_hour = h;
    tm.tm_min  = mi;
    tm.tm_sec  = s;
    return timegm(&tm);
}

int main(void)
{
    setenv("TZ", BERLIN_TZ, 1);
    tzset();

    // Winter (CET = UTC+1). 2026-01-15 09:00 UTC = 10:00 local.
    // Target 12:00 local today -> 2 h away.
    assert(seconds_until_daily(utc(2026, 1, 15, 9, 0, 0), 12, 0) == 2 * 3600);

    // Target already passed today (08:00 local, now 10:00 local) ->
    // tomorrow 08:00 local. 10:00 -> next 08:00 = 22 h.
    assert(seconds_until_daily(utc(2026, 1, 15, 9, 0, 0), 8, 0) == 22 * 3600);

    // Target exactly "now" (10:00 local) -> tomorrow, a normal 24 h day.
    assert(seconds_until_daily(utc(2026, 1, 15, 9, 0, 0), 10, 0) == 24 * 3600);

    // Spring forward: DST starts Sun 2026-03-29 (clocks 02:00->03:00).
    // now 2026-03-28 11:00 UTC = 12:00 local (still CET). Target 12:00 ->
    // tomorrow 2026-03-29 12:00 CEST = 10:00 UTC. The intervening day is
    // 23 h long, so the delta is 23 h, not 24 h.
    assert(seconds_until_daily(utc(2026, 3, 28, 11, 0, 0), 12, 0) == 23 * 3600);

    // Fall back: DST ends Sun 2026-10-25 (clocks 03:00->02:00).
    // now 2026-10-24 10:00 UTC = 12:00 local (CEST). Target 12:00 ->
    // tomorrow 2026-10-25 12:00 CET = 11:00 UTC. That day is 25 h long, so
    // the delta is 25 h.
    assert(seconds_until_daily(utc(2026, 10, 24, 10, 0, 0), 12, 0) == 25 * 3600);

    // Result is always strictly positive.
    assert(seconds_until_daily(utc(2026, 6, 1, 0, 0, 0), 0, 0) > 0);

    printf("test_sched_time: OK\n");
    return 0;
}
