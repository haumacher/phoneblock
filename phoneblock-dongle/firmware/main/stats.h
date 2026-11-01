#pragma once

#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>

#include "api.h"  // verdict_t

// Runtime statistics and recent-events ring buffer. All accessors are
// thread-safe; the underlying state lives behind a mutex. Snapshots
// copy the current state into caller-owned buffers so the mutex is
// held only briefly.

#define STATS_MAX_CALLS      10
// Sized generously: the ring is now fed by a global log hook
// (log_capture.c) that mirrors every WARN/ERROR line — our own and the
// ESP-IDF libraries' — so a single failing operation can emit several
// related entries. A deeper ring keeps the root cause from scrolling
// off before the user looks.
#define STATS_MAX_ERRORS     32
#define STATS_NUMBER_LEN     48
// Wide enough for the labels a Fritz!Box phonebook imported from a spam-list
// provider carries ("tellows Score 8 Aggressive advertising", 38 chars). The
// call list is where a user reads the name they then write a caller-name
// pattern for (issue #502), so clipping it at 31 chars hid exactly the text
// they need.
#define STATS_DISPLAY_LEN    48
#define STATS_LABEL_LEN      32
#define STATS_LOCATION_LEN   80
#define STATS_ERROR_TAG_LEN  16
#define STATS_ERROR_MSG_LEN  128

// Levels of the log ring, numerically equal to ESP-IDF's esp_log_level_t
// (log_capture.c asserts that), so the ESP32 hook can store those directly.
enum {
    STATS_LOG_LEVEL_ERROR = 1,
    STATS_LOG_LEVEL_WARN = 2,
    STATS_LOG_LEVEL_INFO = 3,
};

typedef struct {
    int64_t   at_us;                           // esp_timer_get_time() at event
    char      number[STATS_NUMBER_LEN];        // raw From-user or normalized
    char      display[STATS_DISPLAY_LEN];      // Fritz!Box display name, may be empty
    verdict_t verdict;                         // decision taken by the dongle
    // Populated only for API-checked entries (stats_record_call_checked);
    // empty for phone-book / non-dialable entries. The web UI keys on
    // a non-empty `label` to switch to the rich rendering.
    char      label[STATS_LABEL_LEN];          // PhoneBlock shortcut, e.g. "(DE) 015735…"
    char      location[STATS_LOCATION_LEN];    // operator / area, e.g. "Telefónica …"
    pb_assessment_t assessment;                // log characterisation (see pb_assessment_t)
    int       direct_votes;                    // direct community votes against the number
    int       range_votes;                     // raw neighbourhood (range) votes
    bool      wildcard;                         // local-cache hit was a range/prefix, not exact
    // Seconds from the dongle's answer to the hang-up, by either side (a
    // spam caller may hang up before the announcement ends). 0 for calls
    // the dongle does not answer and while an answered call is still on.
    uint16_t  duration_s;
} stats_call_t;

typedef struct {
    int64_t at_us;
    int     level;                             // STATS_LOG_LEVEL_WARN | ERROR
    char    tag[STATS_ERROR_TAG_LEN];          // e.g. "sip", "api", "tr064"
    char    message[STATS_ERROR_MSG_LEN];
} stats_error_t;

typedef struct {
    bool     sip_registered;
    int64_t  sip_registered_since_us;          // last successful REGISTER
    uint32_t total_calls;
    uint32_t spam_blocked;
    uint32_t legitimate;
    uint32_t errors;                           // internal/API errors
    // Latency of the last *successful* API call (0 / !valid before the
    // first one). A failed call never lands here: its "latency" is just how
    // long it took to give up, typically the timeout (issue #551).
    int64_t  last_api_duration_us;             // total latency of last successful API call
    api_phases_t last_api_phases;              // phase breakdown of last successful API call
    // The most recent failed call, kept until the next failure overwrites
    // it. last_api_failed says whether it is also the most recent call
    // overall, i.e. no call has succeeded since.
    bool     last_api_failed;
    api_failure_t last_api_failure;            // kind == API_FAIL_NONE: none yet
} stats_counters_t;

// Renamed from `stats_init` to avoid colliding with lwip's
// stats_init() macro (components/lwip/lwip/src/include/lwip/stats.h).
void stats_setup(void);

// --- Event hooks (called from the SIP / API code) -------------------

void stats_record_call(const char *number, const char *display, verdict_t verdict);

// Like stats_record_call but with an explicit log characterisation, for
// decisions the verdict alone does not describe. A PB_ASSESS_TEST entry is
// listed but bumps no counter: a test call is neither spam nor a real call. Used by the caller-name
// filter (issue #502): the verdict is SPAM, but the reason is the user's own
// name pattern rather than any community signal, so the entry must not read as
// "SPAM" from the block list.
void stats_record_call_assessed(const char *number, const char *display,
                                verdict_t verdict, pb_assessment_t assessment);

// Like stats_record_call but for entries that went through
// /api/check-prefix. Copies label/location/votes/suspected from the
// API result onto the stored entry so the UI can render the
// PhoneBlock-side display variant, location, and "SPAM (n Votes)" /
// "SPAM-VERDACHT (n Votes)" labels.
void stats_record_call_checked(const char *number, const char *display,
                               const pb_check_result_t *result);

// Called with the sequence number of every call added to the recent-calls
// ring (stats_record_call*, not stats_record_call_counters_only), after the
// stats lock is released, on the recording task. A spam call is answered
// by the dongle, so it is announced only when it ends (stats_call_ended()),
// with its duration; every other call when it is recorded. The call
// webhook (webhook.c) uses it to queue the call; it must not block. One
// listener; NULL removes it.
typedef void (*stats_call_listener_t)(uint32_t seq);
void stats_set_call_listener(stats_call_listener_t listener);

// Sequence number of the call most recently added to the ring, 0 before
// the first. sip_register.c compares it before and after screening a call
// to learn whether, and as which number, the call was listed.
uint32_t stats_last_call_seq(void);

// The answered spam call `seq` has ended after `duration_us`: stores the
// duration and announces the call to the listener. Ignored for a call that
// has left the ring or is not a spam call.
void stats_call_ended(uint32_t seq, int64_t duration_us);

// Bumps counters for a call without adding it to the recent-calls
// ring buffer. Used by sip_register.c when the user has opted out of
// listing "known" calls (phone-book matches, internal Fritz!Box
// codes) but still wants the dashboard counters (total/legitimate)
// to reflect that a call happened. The caller's number, name and
// assessment still go to the display (T-Dongle-S3), which shows every call.
void stats_record_call_counters_only(const char *number, const char *display,
                                     verdict_t verdict, pb_assessment_t assessment);
// Append a WARN/ERROR entry to the displayed log ring. `level` is
// STATS_LOG_LEVEL_WARN / STATS_LOG_LEVEL_ERROR. Normally called only
// by the global log hook in log_capture.c (log_capture_linux.c on Linux),
// which mirrors every WARN / ERROR line into this ring.
void stats_record_error(int level, const char *tag, const char *message);
void stats_record_sip_state(bool registered);

// Records the outcome of one timed API call. `failure` NULL (or of kind
// API_FAIL_NONE) is a success: `p` becomes the shown latency and the failure
// flag clears. Otherwise the latency of the last success is kept and
// `failure` is stored, stamped with the current time (its at_us is ignored).
// Feeds the dashboard's API-latency / API-error display.
void stats_record_api_result(const api_phases_t *p, const api_failure_t *failure);

// --- Snapshots (for the web UI) -------------------------------------

void stats_snapshot_counters(stats_counters_t *out);

// Fill up to `max` entries into `out`, newest first. Returns the
// number of entries actually written (<= max and <= STATS_MAX_*).
int stats_snapshot_calls(stats_call_t *out, int max);
int stats_snapshot_errors(stats_error_t *out, int max);

// Drop all buffered error entries.
void stats_clear_errors(void);

// --- retroactive updates to listed calls ----------------------------
//
// A listed call shows the caller's *current* state, and two of its fields
// change when the user acts on it from the call list: the caller's name
// (once they write a Fritz!Box phonebook entry) and the assessment (once they
// rate the number as spam or legitimate). The row then shows the new state,
// and the actions it offers follow from that.

// Fill in `display` for every listed call with this number that has no
// name yet. Returns how many entries were updated.
int stats_set_display(const char *number, const char *display);

// Set the assessment of every listed call with this number, as an exact-number
// decision (clears `wildcard` and the vote counts). Returns how many entries
// were updated.
int stats_set_assessment(const char *number, pb_assessment_t assessment);

// Assessment of the newest listed call with this number. Returns false when
// the number is not listed.
bool stats_assessment_for_number(const char *number, pb_assessment_t *out);

// Copy the first non-empty name recorded for this number into `out`.
// Returns false when the number is not listed or none of its entries carry
// a name. Lets the rating path apply the "a contact is never spam" rule
// without a Fritz!Box round-trip: the name the box announced is already in
// the list.
bool stats_display_for_number(const char *number, char *out, size_t cap);

// Drop all buffered call entries (counters are kept).
void stats_clear_calls(void);

// Copy the listed call with this sequence number (see
// stats_set_call_listener) into `out`. False once it has left the ring
// (more than STATS_MAX_CALLS newer calls, or stats_clear_calls()).
bool stats_get_call(uint32_t seq, stats_call_t *out);

// Number of listed calls for which `match` returns true (called under the
// stats lock: `match` must not call back into stats). Used by the display
// to count the blocked spam calls for its badge.
int stats_count_calls(bool (*match)(const stats_call_t *call));
