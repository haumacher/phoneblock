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
#define STATS_MAX_ERRORS     10
#define STATS_NUMBER_LEN     48
#define STATS_DISPLAY_LEN    32
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
    int       votes;                           // count to display next to verdict
    bool      suspected;                       // votes>0 but below SPAM threshold
    bool      white_listed;                    // user-personal whitelist hit (hard override)
    bool      black_listed;                    // user-personal blacklist hit (hard override)
} stats_call_t;

typedef struct {
    int64_t at_us;
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
    int64_t  last_api_duration_us;             // latency of last API call
} stats_counters_t;

// Renamed from `stats_init` to avoid colliding with lwip's
// stats_init() macro (components/lwip/lwip/src/include/lwip/stats.h).
void stats_setup(void);

// --- Event hooks (called from the SIP / API code) -------------------

void stats_record_call(const char *number, const char *display, verdict_t verdict);

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
void stats_record_error(const char *tag, const char *message);
void stats_record_sip_state(bool registered);
void stats_record_api_duration(int64_t duration_us);

// --- Snapshots (for the web UI) -------------------------------------

void stats_snapshot_counters(stats_counters_t *out);

// Fill up to `max` entries into `out`, newest first. Returns the
// number of entries actually written (<= max and <= STATS_MAX_*).
int stats_snapshot_calls(stats_call_t *out, int max);
int stats_snapshot_errors(stats_error_t *out, int max);

// Drop all buffered error entries.
void stats_clear_errors(void);

// Drop all buffered call entries (counters are kept).
void stats_clear_calls(void);
