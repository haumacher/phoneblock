#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "stats.h"

// Which calls the status mail reports (issues #556, #589). Apart from the
// SMTP code in mail.c, so the selection is host-tested
// (test/test_mail_calls.c) against the real call list.

// True for a call the dongle blocked, i.e. took itself instead of letting the
// phones ring: verdict SPAM — community spam (live or from the block list),
// the user's blacklist and caller-name patterns, and a test call to the
// answer path. Legitimate, unknown, suspect-below-threshold and failed
// lookups (which ring through) are not blocked.
bool mail_call_is_blocked(const stats_call_t *call);

// With `blocked_only`, moves the blocked ones of the first `n` calls to the
// front, keeping their order, and returns how many there are; without it
// returns `n` and leaves `calls` alone (every call is reported).
int mail_select_calls(stats_call_t *calls, int n, bool blocked_only);

// The calls one mail reports: those with a sequence number in
// (after_seq, through_seq].
typedef struct {
    uint32_t after_seq;     // the mark: calls above it are reported
    uint32_t through_seq;   // the newest call covered (0: none)
    bool     blocked_only;  // config_mail_spam_only()
    int      new_calls;     // calls of the window the mail lists
    // Calls of the window that are no longer in the call list: more calls
    // came in since the last mail than the list holds (STATS_CALL_CAPACITY).
    int      not_listed;
} mail_calls_window_t;

// The window of the daily mail: every call since the last confirmed mail
// (the persisted mark, stats_set_mailed_seq), or, with `whole_list` (the
// test mail), every listed call. Counts new_calls with one pass over the
// list in pages of a few calls on the stack.
void mail_calls_window(bool blocked_only, bool whole_list, mail_calls_window_t *w);

// Up to `max` calls of the window (the blocked ones with blocked_only),
// newest first, older than `*cursor` (0: from the newest). Advances *cursor
// past what it read; returns how many it wrote, 0 at the end.
int mail_calls_read(const mail_calls_window_t *w, uint32_t *cursor,
                    stats_call_t *out, int max);

// The mail of `w` was sent: its calls are not reported again, also after a
// reboot.
void mail_calls_mark_sent(const mail_calls_window_t *w);
