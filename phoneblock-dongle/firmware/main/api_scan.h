#pragma once

#include <stdbool.h>

// Streaming scanner for the JSON response of /api/check-prefix.
//
// /api/check-prefix returns:
//   {"numbers":[{...},{...}],"range10":[{...}],"range100":[{...}]}
//
// At a 4-hex-char prefix the response is small in the typical case but
// has no upper bound — a hot bucket could be tens of KB. Buffering
// the whole response and trusting the buffer size would silently
// misclassify SPAM whenever the bucket exceeded the buffer.
//
// This scanner feeds bytes through a depth-tracking state machine,
// isolates each per-entry JSON object, and parses each entry
// independently with cJSON on a small fixed-size per-entry buffer.
// Memory cost is O(1) in the number of entries.
//
// A per-entry overflow (one PhoneInfo > ENTRY_BUF_SIZE bytes — never
// plausible for the schema) sets `error` so the caller produces an
// explicit error verdict instead of a silent miss.

#define API_SCAN_ENTRY_BUF_SIZE 2048
#define API_SCAN_KEY_BUF_SIZE   16
#define API_SCAN_LABEL_LEN      32
#define API_SCAN_LOCATION_LEN   80

typedef enum {
    API_SCAN_ARR_NONE,
    API_SCAN_ARR_NUMBERS,
    API_SCAN_ARR_RANGE10,
    API_SCAN_ARR_RANGE100,
} api_scan_array_t;

typedef struct {
    // Input — phone number to match (international form, e.g. "+49…").
    const char *phone;
    int phone_len;

    // Top-level JSON-scanner state.
    bool in_string;
    bool escape_next;
    int  brace_depth;     // counts {} only
    int  bracket_depth;   // counts [] only
    api_scan_array_t current_array;

    // Most recent string seen at brace_depth==1, bracket_depth==0 —
    // identifies which top-level array follows.
    bool collecting_key;
    char key_buf[API_SCAN_KEY_BUF_SIZE];
    int  key_len;

    // Per-entry object buffer.
    bool collecting_obj;
    char obj_buf[API_SCAN_ENTRY_BUF_SIZE];
    int  obj_len;
    bool obj_overflow;

    // Accumulated results. Last-write-wins on duplicate matches (the
    // server should never produce duplicates, but the scanner is
    // forgiving).
    int direct_votes;
    int v10, c10;
    int v100, c100;

    // Display fields lifted from the matching numbers[] entry. Empty
    // when the number was not in the bucket (e.g. archived rows that
    // /api/check-prefix omits because of `AND s.ACTIVE`).
    char label[API_SCAN_LABEL_LEN];
    char location[API_SCAN_LOCATION_LEN];

    // Per-user personalization flags lifted from the matching
    // numbers[] entry. The server emits these on top of the community
    // rating when the authenticated user has the number on their own
    // BLOCKLIST table (whitelist or blacklist). Hard overrides — see
    // phoneblock_check() for the precedence rules.
    bool white_listed;
    bool black_listed;

    // Set on per-entry overflow or per-entry cJSON parse failure. The
    // caller must surface this and refrain from acting on votes —
    // the scan is incomplete.
    bool error;
    // Static literal describing the cause; NULL when error is false.
    const char *error_reason;
} api_scan_t;

// Initialises an api_scan_t for a given phone number. The caller-
// owned struct is zeroed; phone is borrowed and must outlive the scan.
void api_scan_init(api_scan_t *s, const char *phone);

// Feeds `len` bytes of response payload into the scanner. Safe to call
// repeatedly for chunked HTTP delivery — internal state survives chunk
// boundaries (including boundaries inside strings, escape sequences,
// objects, and arrays).
void api_scan_feed(api_scan_t *s, const char *data, int len);
