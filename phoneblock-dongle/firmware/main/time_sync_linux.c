#include "time_sync.h"

#include <stdlib.h>
#include <time.h>

#include "config.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

// On Linux the host keeps the wall clock (systemd-timesyncd, chrony, ...),
// so there is no SNTP client here. Hosts without an RTC (a Raspberry Pi)
// boot at a stale time until their NTP client steps the clock, so "valid"
// means "plausible": not before 2026-01-01T00:00:00Z. Until then the
// scheduler parks its daily jobs on the hourly clock-wait backstop.
#define TIME_SYNC_PLAUSIBLE_EPOCH 1767225600

// Applies the persisted timezone; without one the host's zone stays in
// effect (TZ from the environment, else /etc/localtime). Runs before any
// other thread starts.
void time_sync_start(void)
{
    time_sync_set_timezone(config_timezone());
    // Make sure TZ exists from here on. A later change from the web UI then
    // replaces this entry in place instead of growing the environment,
    // which other threads may be scanning (getenv, e.g. curl's proxy
    // lookup). ":/etc/localtime" is the host zone, the same that an unset
    // TZ means on glibc and musl.
    if (!getenv("TZ")) {
        setenv("TZ", ":/etc/localtime", 1);
        tzset();
    }
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
