// Host test for main/time_sync_tz.c: time_sync_set_timezone() changes the
// local time other tasks see, and readers holding the timezone lock never
// observe a half-applied change while it switches back and forth.

#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>

#include "linux_test_util.h"
#include "time_sync.h"

// 2026-01-15T12:00:00Z: winter, so CET is exactly UTC+1.
#define WINTER_NOON_UTC ((time_t)1768478400)

static int local_hour(time_t t)
{
    struct tm lt;
    time_sync_tz_lock();
    localtime_r(&t, &lt);
    time_sync_tz_unlock();
    return lt.tm_hour;
}

static int s_stop;
static int s_bad;

static void *reader(void *arg)
{
    (void)arg;
    while (!__atomic_load_n(&s_stop, __ATOMIC_RELAXED)) {
        int hour = local_hour(WINTER_NOON_UTC);
        if (hour != 12 && hour != 13)
            __atomic_store_n(&s_bad, 1, __ATOMIC_RELAXED);
    }
    return NULL;
}

int main(void)
{
    // Before the first set_timezone() the lock is a no-op.
    time_sync_tz_lock();
    time_sync_tz_unlock();

    time_sync_set_timezone("UTC0");
    REQUIRE(local_hour(WINTER_NOON_UTC) == 12);
    time_sync_set_timezone("CET-1CEST,M3.5.0,M10.5.0/3");
    REQUIRE(local_hour(WINTER_NOON_UTC) == 13);

    // NULL and empty leave the timezone unchanged.
    time_sync_set_timezone(NULL);
    time_sync_set_timezone("");
    REQUIRE(local_hour(WINTER_NOON_UTC) == 13);

    // Readers on other threads while the timezone keeps switching.
    pthread_t threads[4];
    for (int i = 0; i < 4; i++)
        REQUIRE(pthread_create(&threads[i], NULL, reader, NULL) == 0);
    for (int i = 0; i < 2000; i++)
        time_sync_set_timezone(i % 2 ? "UTC0" : "CET-1CEST,M3.5.0,M10.5.0/3");
    __atomic_store_n(&s_stop, 1, __ATOMIC_RELAXED);
    for (int i = 0; i < 4; i++) pthread_join(threads[i], NULL);
    REQUIRE(!__atomic_load_n(&s_bad, __ATOMIC_RELAXED));

    puts("test_time_sync_tz: all tests passed");
    return 0;
}
