#include "mail_calls.h"

#include <string.h>

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

bool mail_call_is_blocked(const stats_call_t *call)
{
    return call->verdict == VERDICT_SPAM;
}

int mail_count_new_calls(const stats_call_t *calls, int n, int64_t since_us)
{
    // at_us is monotonic and the list newest first, so the new calls are
    // the leading run above the mark.
    int count = 0;
    while (count < n && calls[count].at_us > since_us) count++;
    return count;
}

int mail_select_calls(stats_call_t *calls, int n, bool blocked_only)
{
    if (!blocked_only) return n;
    int kept = 0;
    for (int i = 0; i < n; i++) {
        if (!mail_call_is_blocked(&calls[i])) continue;
        if (kept != i) memcpy(&calls[kept], &calls[i], sizeof(calls[i]));
        kept++;
    }
    return kept;
}
