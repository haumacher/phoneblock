#pragma once

#include <stdbool.h>
#include <stddef.h>

typedef enum {
    VERDICT_LEGITIMATE,
    VERDICT_SPAM,
    VERDICT_ERROR,
} verdict_t;

// Query the PhoneBlock API for the given phone number.
// The number is passed through unmodified — the server normalizes.
// Returns VERDICT_SPAM, VERDICT_LEGITIMATE, or VERDICT_ERROR on
// transport/parse problems.
verdict_t phoneblock_check(const char *phone_number);

// Verify API reachability + token validity via GET /test (expects
// HTTP 200 body "ok"). Returns true on success, false on any failure.
// Logs the outcome and records a stats error on failure.
bool phoneblock_selftest(void);

// Submit a spam rating for `phone` with the given rating code (e.g.
// "B_MISSED", "E_ADVERTISING"). Optional short comment (NULL for
// none). Returns true when the server accepts the rating (HTTP 200).
bool phoneblock_rate(const char *phone, const char *rating, const char *comment);

// Reports a confirmed SPAM call to the server (POST /api/report-call/{phone}).
// Called after phoneblock_check() returns VERDICT_SPAM via the privacy-
// preserving k-anonymity endpoint, so the server can keep its space-
// constrained blocklists tailored. Phone is in international form
// ("+49..."); we send it in the legacy "00"-prefix form to avoid
// URL-encoding the leading "+". Returns true on HTTP 204.
bool phoneblock_report_call(const char *phone);

// Verifies an identity-assertion JWT against the public
// /auth/verify-code endpoint. On success, returns true and writes
// the JWT subject (PhoneBlock user-name) into `user_out` (NUL-
// terminated, truncated to user_cap). Returns false on any failure.
//
// Intentionally NOT bearer-authenticated: the auth gate must keep
// working even if the user later deletes the dongle's API token on
// phoneblock.net. Lockout protection lives on the dongle, which
// pins the owner name at first activation and refuses any
// subsequent JWT whose subject does not match.
bool phoneblock_verify_auth_code(const char *code, const char *state,
                                 char *user_out, size_t user_cap);
