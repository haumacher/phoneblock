#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "stats.h"

// Which calls the status mail reports (issue #556). Pure, so the selection
// is host-tested (test/test_mail_calls.c) apart from the SMTP code in mail.c.

// True for a call the dongle blocked, i.e. took itself instead of letting the
// phones ring: verdict SPAM — community spam (live or from the block list),
// the user's blacklist and caller-name patterns, and a test call to the
// answer path. Legitimate, unknown, suspect-below-threshold and failed
// lookups (which ring through) are not blocked.
bool mail_call_is_blocked(const stats_call_t *call);

// Number of leading entries of `calls` (newest first, as stats_snapshot_calls
// returns them) recorded after `since_us`: the calls new since the last mail.
int mail_count_new_calls(const stats_call_t *calls, int n, int64_t since_us);

// With `blocked_only`, moves the blocked ones of the first `n` calls to the
// front, keeping their order, and returns how many there are; without it
// returns `n` and leaves `calls` alone (every call is reported).
int mail_select_calls(stats_call_t *calls, int n, bool blocked_only);
