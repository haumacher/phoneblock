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
    // Set once the user has submitted a spam rating for this number from
    // the call list (stats_mark_reported). Purely a UI cue — the vote
    // itself lives on the server; this stops the list from offering the
    // same one-click vote again and shows that it went through.
    bool      reported;
} stats_call_t;

typedef struct {
    int64_t at_us;
    int     level;                             // ESP_LOG_WARN | ESP_LOG_ERROR
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
    int64_t  last_api_duration_us;             // total latency of last API call
    api_phases_t last_api_phases;              // phase breakdown of last API call
} stats_counters_t;

// Renamed from `stats_init` to avoid colliding with lwip's
// stats_init() macro (components/lwip/lwip/src/include/lwip/stats.h).
void stats_setup(void);

// --- Event hooks (called from the SIP / API code) -------------------

void stats_record_call(const char *number, const char *display, verdict_t verdict);

// Like stats_record_call but with an explicit log characterisation, for
// decisions the verdict alone does not describe. Used by the caller-name
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

// Bumps counters for a call without adding it to the recent-calls
// ring buffer. Used by sip_register.c when the user has opted out of
// listing "known" calls (phone-book matches, internal Fritz!Box
// codes) but still wants the dashboard counters (total/legitimate)
// to reflect that a call happened.
void stats_record_call_counters_only(verdict_t verdict);
// Append a WARN/ERROR entry to the displayed log ring. `level` is an
// esp_log_level_t (ESP_LOG_WARN / ESP_LOG_ERROR). Normally called only
// by the global log hook in log_capture.c, which mirrors every WARN /
// ERROR line into this ring.
void stats_record_error(int level, const char *tag, const char *message);
void stats_record_sip_state(bool registered);

// Records the latency breakdown of the last API call (total + phases).
// Feeds the dashboard's API-latency display; see api_phases_t.
void stats_record_api_phases(const api_phases_t *p);

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
// A listed call is a record of what happened, but two of its fields are
// lookup results the user can change *after* the fact from the call list:
// the caller's name (once they write a Fritz!Box phonebook entry) and
// whether they have rated the number. Without updating the ring, the row
// would keep saying "no name" / keep offering the same vote, and the
// action would look like it had no effect.

// Fill in `display` for every listed call with this number that has no
// name yet. Returns how many entries were updated.
int stats_set_display(const char *number, const char *display);

// Mark every listed call with this number as rated by the user.
// Returns how many entries were updated.
int stats_mark_reported(const char *number);

// Copy the first non-empty name recorded for this number into `out`.
// Returns false when the number is not listed or none of its entries carry
// a name. Lets the rating path apply the "a contact is never spam" rule
// without a Fritz!Box round-trip: the name the box announced is already in
// the list.
bool stats_display_for_number(const char *number, char *out, size_t cap);

// Drop all buffered call entries (counters are kept).
void stats_clear_calls(void);
