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
#define STATS_ERROR_TAG_LEN  16
#define STATS_ERROR_MSG_LEN  128

typedef struct {
    int64_t   at_us;                           // esp_timer_get_time() at event
    char      number[STATS_NUMBER_LEN];        // raw From-user or normalized
    char      display[STATS_DISPLAY_LEN];      // Fritz!Box display name, may be empty
    verdict_t verdict;                         // decision taken by the dongle
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
void stats_record_error(const char *tag, const char *message);
void stats_record_sip_state(bool registered);
void stats_record_api_duration(int64_t duration_us);

// --- Snapshots (for the web UI) -------------------------------------

void stats_snapshot_counters(stats_counters_t *out);

// Fill up to `max` entries into `out`, newest first. Returns the
// number of entries actually written (<= max and <= STATS_MAX_*).
int stats_snapshot_calls(stats_call_t *out, int max);
int stats_snapshot_errors(stats_error_t *out, int max);
