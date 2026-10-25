#include "time_sync.h"

#include <stdlib.h>
#include <time.h>

#include "platform.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static const char *TAG = "time_sync";

// The timezone lives in the process environment (TZ), and setenv()+tzset()
// are not safe against a concurrent localtime_r()/mktime() in another task:
// glibc and musl read TZ inside those calls. The web UI applies a new
// timezone at runtime, so every local-time reader takes this lock (see
// time_sync_tz_lock()). On the ESP32 the race is practically harmless, but
// the shared code follows the same rule on both builds.
//
// Created by the first time_sync_set_timezone(), which time_sync_start()
// always makes during startup, before other tasks run.
static pb_mutex_t *s_tz_lock;

void time_sync_tz_lock(void)
{
    if (s_tz_lock) pb_mutex_lock(s_tz_lock);
}

void time_sync_tz_unlock(void)
{
    if (s_tz_lock) pb_mutex_unlock(s_tz_lock);
}

void time_sync_set_timezone(const char *posix_tz)
{
    if (!s_tz_lock) s_tz_lock = pb_mutex_create();
    if (!posix_tz || !posix_tz[0]) return;
    time_sync_tz_lock();
    setenv("TZ", posix_tz, 1);
    tzset();
    time_sync_tz_unlock();
    pb_log_info(TAG, "timezone set: %s", posix_tz);
}
