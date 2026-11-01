#include "mail_calls.h"

#include <string.h>

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

bool mail_call_is_blocked(const stats_call_t *call)
{
    return call->verdict == VERDICT_SPAM;
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

int mail_calls_read(const mail_calls_window_t *w, uint32_t *cursor,
                    stats_call_t *out, int max)
{
    if (w->through_seq == 0 || max <= 0) return 0;
    uint32_t before = *cursor ? *cursor : w->through_seq + 1;
    int kept = 0;
    while (kept < max && before > w->after_seq + 1) {
        int n = stats_read_calls(before, out + kept, max - kept);
        if (n == 0) break;
        // The page may reach below the window: cut it there.
        int in = 0;
        while (in < n && out[kept + in].seq > w->after_seq) in++;
        before = out[kept + n - 1].seq;
        kept += mail_select_calls(out + kept, in, w->blocked_only);
        if (in < n) { before = w->after_seq + 1; break; }
    }
    *cursor = before;
    return kept;
}

void mail_calls_window(bool blocked_only, bool whole_list, mail_calls_window_t *w)
{
    stats_calls_info_t info;
    stats_calls_info(&info);
    memset(w, 0, sizeof(*w));
    w->blocked_only = blocked_only;
    w->through_seq  = info.newest_seq;

    // Calls up to floor_seq - 1 were cleared from the list: neither reported
    // nor counted as missing.
    uint32_t mark = info.mailed_seq;
    if (mark + 1 < info.floor_seq) mark = info.floor_seq - 1;
    if (whole_list) mark = info.oldest_seq ? info.oldest_seq - 1 : mark;
    w->after_seq = mark;
    if (info.newest_seq <= mark) {
        w->through_seq = 0;
        return;
    }
    // The listed part of the window ends at the newest call; what lies
    // between the mark and the oldest listed call dropped off the list.
    if (info.count > 0 && info.oldest_seq > mark + 1) {
        w->not_listed = (int)(info.oldest_seq - mark - 1);
    }

    stats_call_t page[4];
    uint32_t cursor = 0;
    int n;
    while ((n = mail_calls_read(w, &cursor, page, 4)) > 0) w->new_calls += n;
}

void mail_calls_mark_sent(const mail_calls_window_t *w)
{
    if (w->through_seq) stats_set_mailed_seq(w->through_seq);
}
