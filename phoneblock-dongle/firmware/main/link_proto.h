#pragma once

// Device-code link to a PhoneBlock account (#621): the protocol half, pure C.
//
// Behind Home Assistant ingress the redirect sign-in (/register-start →
// phoneblock.net → /token-callback) cannot complete: phoneblock.net refuses
// most ingress callback hosts and ingress rejects the cross-site redirect
// back (#619). The Linux service therefore links in the style of RFC 8628:
// it asks phoneblock.net for a code pair (POST /api/link/start), the user
// confirms the short user code on phoneblock.net, and the service collects
// the token server to server (POST /api/link/poll). Nothing travels back
// through the browser.
//
// This unit parses the two server answers and runs the state machine of one
// link. It does no I/O, starts no task and reads no clock — link.c (Linux
// only) feeds it the responses and the time — so it is host-tested in
// test/test_link_proto.c. The ESP32 keeps the redirect flow and carries
// neither file.

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// Field capacities, NUL included. The device code is 43 base64url chars
// (256 bit); the user code "XXXX-XXXX"; the token must fit the config's
// pb_token field (config.c), else it would be stored truncated.
#define LINK_DEVICE_CODE_CAP 128
#define LINK_USER_CODE_CAP   16
#define LINK_VERIFY_URL_CAP  256
#define LINK_TOKEN_CAP       64
#define LINK_MESSAGE_CAP     160

// RFC 8628 §3.2: 5 s when the server names no interval; §3.5: slow_down
// adds 5 s. The bounds keep a broken answer from turning the poll into a
// busy loop or into a wait past any sensible code lifetime.
#define LINK_DEFAULT_INTERVAL_S 5
#define LINK_SLOW_DOWN_STEP_S   5
#define LINK_MIN_INTERVAL_S     1
#define LINK_MAX_INTERVAL_S     60
#define LINK_MAX_EXPIRES_S      1800

// State of the link as /api/link/status reports it. Only PENDING polls;
// the others are terminal (IDLE: no link started since boot).
typedef enum {
    LINK_IDLE = 0,
    LINK_PENDING,
    LINK_APPROVED,
    LINK_DENIED,
    LINK_EXPIRED,
    LINK_ERROR,
} link_state_t;

// "idle", "pending", "approved", "denied", "expired", "error".
const char *link_state_name(link_state_t state);

// The answer of POST /api/link/start.
typedef struct {
    char     device_code[LINK_DEVICE_CODE_CAP];
    char     user_code[LINK_USER_CODE_CAP];
    char     verify_url[LINK_VERIFY_URL_CAP];
    uint32_t interval_s;     // clamped to [LINK_MIN_INTERVAL_S, LINK_MAX_INTERVAL_S]
    uint32_t expires_in_s;   // clamped to LINK_MAX_EXPIRES_S
} link_start_resp_t;

// Parses the start answer. Returns true with `out` filled, or false with an
// English reason in `reason` (never the device code). Requires deviceCode
// (base64url), userCode (letters, digits, '-'), verifyUrl (http/https) and
// expiresIn > 0; a missing interval means LINK_DEFAULT_INTERVAL_S.
bool link_parse_start(const char *json, link_start_resp_t *out,
                      char *reason, size_t reason_cap);

typedef enum {
    LINK_POLL_PENDING = 0,
    LINK_POLL_SLOW_DOWN,
    LINK_POLL_APPROVED,
    LINK_POLL_DENIED,
    LINK_POLL_EXPIRED,
    LINK_POLL_ERROR,        // malformed answer, see `reason`
} link_poll_status_t;

// The answer of POST /api/link/poll.
typedef struct {
    link_poll_status_t status;
    char token[LINK_TOKEN_CAP];         // APPROVED only
    char reason[LINK_MESSAGE_CAP];      // ERROR only
} link_poll_resp_t;

// Parses a poll answer. Malformed JSON, an unknown status, or "approved"
// without a usable token give LINK_POLL_ERROR with a reason.
void link_parse_poll(const char *json, link_poll_resp_t *out);

// The poll interval after an answer: +LINK_SLOW_DOWN_STEP_S on slow_down
// (capped at LINK_MAX_INTERVAL_S), otherwise unchanged.
uint32_t link_next_interval(uint32_t interval_s, link_poll_status_t status);

// One link. `deadline_us` is on the caller's monotonic clock.
typedef struct {
    link_state_t state;
    uint32_t     interval_s;
    uint64_t     deadline_us;
    char         message[LINK_MESSAGE_CAP];   // English detail for the UI
} link_machine_t;

// A fresh link from a parsed start answer: PENDING until now + expiresIn.
void link_machine_begin(link_machine_t *m, const link_start_resp_t *start,
                        uint64_t now_us);

// Ends the link in `state` with `message` (copied, may be NULL).
void link_machine_fail(link_machine_t *m, link_state_t state, const char *message);

// The local expiry: a PENDING link past its deadline turns EXPIRED.
// Returns true when this call ended it.
bool link_machine_check_expiry(link_machine_t *m, uint64_t now_us);

// Applies a poll answer to a PENDING link and returns the new state:
// pending/slow_down stay PENDING (slow_down raising the interval), approved
// → APPROVED, denied → DENIED, expired → EXPIRED, error → ERROR. A link that
// is not PENDING is left as it is. The caller stores the token of an
// APPROVED answer before it reports the state.
link_state_t link_machine_apply(link_machine_t *m, const link_poll_resp_t *resp);
