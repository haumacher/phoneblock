#include "sched_time.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

long seconds_until_daily(time_t now, int at_hour, int at_minute)
{
    struct tm lt;
    localtime_r(&now, &lt);

    struct tm tgt = lt;
    tgt.tm_hour  = at_hour;
    tgt.tm_min   = at_minute;
    tgt.tm_sec   = 0;
    tgt.tm_isdst = -1;          // let mktime resolve DST for the target

    time_t when = mktime(&tgt);
    if (when <= now) {
        // Already reached today — aim for tomorrow. Bump the day and let
        // mktime renormalise (handles month/year rollover and a DST jump
        // on the boundary day, which a naive +86400 would get wrong).
        tgt.tm_mday += 1;
        tgt.tm_isdst = -1;
        when = mktime(&tgt);
    }

    long delta = (long)(when - now);
    return delta > 0 ? delta : 1;   // never hand back a non-positive delay
}

uint32_t sched_retry_delay_s(const sched_retry_t *job, bool ok,
                             uint32_t *backoff_s, uint32_t rnd)
{
    if (ok) {
        *backoff_s = job->min_s;
        if (job->recheck_s == 0) return SCHED_NEVER_S;
        if (job->jitter_s == 0 || job->jitter_s >= job->recheck_s) return job->recheck_s;
        return job->recheck_s - job->jitter_s + rnd % (2u * job->jitter_s);
    }
    uint32_t delay = *backoff_s < job->min_s ? job->min_s : *backoff_s;
    if (delay > job->max_s) delay = job->max_s;
    *backoff_s = delay >= job->max_s / 2 ? job->max_s : delay * 2;
    return delay;
}
