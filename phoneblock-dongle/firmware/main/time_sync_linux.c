#include "time_sync.h"

#include <stdlib.h>
#include <time.h>

#include "config.h"
#include "platform.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static const char *TAG = "time_sync";

// On Linux the host keeps the wall clock (systemd-timesyncd, chrony, ...),
// so there is no SNTP client here. Hosts without an RTC (a Raspberry Pi)
// boot at a stale time until their NTP client steps the clock, so "valid"
// means "plausible": not before 2026-01-01T00:00:00Z. Until then the
// scheduler parks its daily jobs on the hourly clock-wait backstop.
#define TIME_SYNC_PLAUSIBLE_EPOCH 1767225600

void time_sync_set_timezone(const char *posix_tz)
{
    if (!posix_tz || !posix_tz[0]) return;
    setenv("TZ", posix_tz, 1);
    tzset();
    pb_log_info(TAG, "timezone set: %s", posix_tz);
}

// Applies the persisted timezone; without one the host's zone stays in
// effect (TZ / /etc/localtime).
void time_sync_start(void)
{
    time_sync_set_timezone(config_timezone());
}

bool time_sync_valid(void)
{
    return time(NULL) >= TIME_SYNC_PLAUSIBLE_EPOCH;
}

int64_t time_sync_now_epoch(void)
{
    if (!time_sync_valid()) return 0;
    return (int64_t)time(NULL);
}
