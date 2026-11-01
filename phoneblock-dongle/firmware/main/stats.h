#pragma once

#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>

#include "api.h"  // verdict_t
#include "call_rec_cfg.h"  // PB_CALL_RECORDING

#ifdef ESP_PLATFORM
#include "sdkconfig.h"   // CONFIG_ESPTOOLPY_FLASHSIZE_16MB
#endif

// Runtime statistics, the recent-calls list and the log ring. All accessors
// are thread-safe; the underlying state lives behind a mutex. Snapshots
// copy the current state into caller-owned buffers so the mutex is held
// only briefly.
//
// The recent-calls list lives in a file (issue #589), not in RAM, so it
// survives a reboot or a power cut: STATS_CALLS_FILE in the data directory
// (/spiffs/calls.bin on the device, <PHONEBLOCK_DATA_DIR>/calls.bin on
// Linux), opened with stats_calls_open(). Fixed-size records in a ring, so
// an update of a listed call (duration, name, assessment) is one seek and
// one write. Until the file is open (or when it cannot be created) calls
// are counted but not listed.

#define STATS_CALLS_FILE "calls.bin"

// Calls the file holds. The 4 MB boards (classic ESP32, C3) share a 640 KB
// partition with the dialog set's M tier, which leaves room for 3 blocks:
// 50 calls (STATS_CALLS_FILE_BYTES(50) = 12032 B, storage_budget.h). The
// 16 MB S3 (~9.6 MB partition) and the Linux port keep 200 (12 blocks on
// the S3) — several weeks of calls for a household.
#define STATS_CALL_CAPACITY_SMALL 50
#define STATS_CALL_CAPACITY_LARGE 200
// (A host test may predefine it: test_stats_small runs with the 4 MB one.)
#ifndef STATS_CALL_CAPACITY
#if defined(ESP_PLATFORM) && !defined(CONFIG_ESPTOOLPY_FLASHSIZE_16MB)
#define STATS_CALL_CAPACITY STATS_CALL_CAPACITY_SMALL
#else
#define STATS_CALL_CAPACITY STATS_CALL_CAPACITY_LARGE
#endif
#endif

// On-flash layout: a header, then STATS_CALL_CAPACITY records of
// STATS_CALL_RECORD_BYTES (stats.c asserts both against its structs).
#define STATS_CALL_HEADER_BYTES 32
#define STATS_CALL_RECORD_BYTES 240
#define STATS_CALLS_FILE_BYTES(capacity) \
    (STATS_CALL_HEADER_BYTES + (capacity) * STATS_CALL_RECORD_BYTES)

// The log ring. It is fed by a global log hook (log_capture.c) that mirrors
// every WARN/ERROR line — our own and the ESP-IDF libraries' — so a single
// failing operation can emit several related entries; a deep ring keeps the
// root cause from scrolling off before the user looks. 47 = the 32 it had
// plus what the former 10-entry RAM call ring freed (10 x 240 B = 2400 B,
// 15 entries of 160 B), so the RAM use stays as it was. Consumers copy it
// to the heap, not the stack (47 x 160 B = 7.5 KB).
#define STATS_MAX_ERRORS     47
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

// One listed call as the readers get it (a copy; the file holds a compact
// record of it).
typedef struct {
    // Sequence number (stats_set_call_listener): unique across reboots,
    // counting up from 1. 0 for a call that is not listed (the display's
    // copy of a counters-only call).
    uint32_t  seq;
    // Wall-clock time of the call, UTC seconds; 0 when it is not known: a
    // call recorded before the clock was set, in an earlier boot. (A call
    // of this boot gets its time once the clock is set.)
    int64_t   epoch_s;
    // pb_monotonic_us() at the call — meaningful only while `this_boot`.
    int64_t   at_us;
    bool      this_boot;                       // recorded since this boot's stats_calls_open()
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

// --- The call file ----------------------------------------------------

// Opens the call list at `path` (normally STATS_CALLS_FILE in the data
// directory), creating it when it does not exist. A file of another format
// (magic, version, record size, capacity) or a truncated or inconsistent one
// is discarded with one WARN and replaced by an empty list. Calls recorded
// from now on are "of this boot" (stats_call_t.this_boot). False (logged at
// ERROR) if the file cannot be created; calls are then counted, not listed.
bool stats_calls_open(const char *path);

// Forgets the open call file (the file stays). For the host tests: close +
// open is a reboot of the list.
void stats_calls_close(void);

// The wall clock the call records are stamped with: UTC seconds, 0 while it
// is not set (time_sync_now_epoch()). NULL (the default) stamps nothing.
void stats_set_clock(int64_t (*now_epoch)(void));

// The clock has just been set: calls of this boot recorded before it get
// their time (epoch = now - their age). Calls of earlier boots without a
// time keep 0. Also done on the next write if this is never called (the
// Linux host clock has no "set" event).
void stats_time_synced(void);

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
// list (stats_record_call*, not stats_record_call_counters_only), after the
// stats lock is released, on the recording task. A spam call is answered
// by the dongle, so it is announced only when it ends (stats_call_ended()),
// with its duration; every other call when it is recorded. The call
// webhook (webhook.c) uses it to queue the call; it must not block. One
// listener; NULL removes it.
typedef void (*stats_call_listener_t)(uint32_t seq);
void stats_set_call_listener(stats_call_listener_t listener);

#if PB_CALL_RECORDING
// Called (outside the stats lock, on the recording task) whenever calls may
// have left the list: after a call was listed (the ring may have wrapped)
// and after stats_clear_calls(). Every call below `keep_from` is gone. The
// call recordings (call_rec.c, #663) delete theirs. One hook; NULL removes
// it.
typedef void (*stats_drop_hook_t)(uint32_t keep_from);
void stats_set_drop_hook(stats_drop_hook_t hook);
#endif

// Sequence number of the call most recently added to the list, 0 before
// the first (since the file was created). sip_register.c compares it before and after screening a call
// to learn whether, and as which number, the call was listed.
uint32_t stats_last_call_seq(void);

// The answered spam call `seq` has ended after `duration_us`: stores the
// duration and announces the call to the listener. Ignored for a call that
// has left the list or is not a spam call.
void stats_call_ended(uint32_t seq, int64_t duration_us);

// Bumps counters for a call without adding it to the recent-calls
// list. Used by sip_register.c when the user has opted out of
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
// number of entries actually written (<= max and <= STATS_MAX_ERRORS).
int stats_snapshot_errors(stats_error_t *out, int max);

// Up to `max` listed calls older than `before_seq` (0: from the newest),
// newest first, into `out`. Returns how many. Page through the whole list
// with before_seq = the seq of the last call of the previous page; fewer
// than `max` means the end. One pass over the file under the lock, so a
// page is consistent; between pages new calls may come in (they are newer,
// a page never repeats or skips a call that is still listed).
int stats_read_calls(uint32_t before_seq, stats_call_t *out, int max);

// The first page: the `max` newest calls.
int stats_snapshot_calls(stats_call_t *out, int max);

typedef struct {
    int      count;        // calls listed (<= STATS_CALL_CAPACITY)
    uint32_t newest_seq;   // seq of the newest listed call, 0 if none
    uint32_t oldest_seq;   // seq of the oldest listed call, 0 if none
    // Every call up to here was cleared (stats_clear_calls) or never
    // existed: calls between floor_seq and oldest_seq dropped off the list.
    uint32_t floor_seq;
    uint32_t mailed_seq;   // stats_set_mailed_seq()
} stats_calls_info_t;

// The list's shape, from RAM (no file access).
void stats_calls_info(stats_calls_info_t *out);

// The daily mail's high-water mark (mail.c): the newest call seq already
// reported. Kept in the file, so calls not yet mailed before a reboot are
// mailed afterwards.
void stats_set_mailed_seq(uint32_t seq);

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

// Drop all listed calls (counters are kept): the file is emptied. Sequence
// numbers continue.
void stats_clear_calls(void);

// Copy the listed call with this sequence number (see
// stats_set_call_listener) into `out`. False once it has left the list
// (more than STATS_CALL_CAPACITY newer calls, or stats_clear_calls()).
bool stats_get_call(uint32_t seq, stats_call_t *out);

// Number of the `newest` most recent listed calls for which `match` returns
// true (called under the stats lock: `match` must not call back into
// stats). Used by the display to count the blocked spam calls for its
// badge.
int stats_count_calls(bool (*match)(const stats_call_t *call), int newest);
