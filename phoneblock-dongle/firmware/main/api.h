#pragma once

#include <stdbool.h>
#include <stddef.h>

typedef enum {
    VERDICT_LEGITIMATE,
    VERDICT_SPAM,
    VERDICT_ERROR,
} verdict_t;

// Display + count info lifted from the /api/check-prefix response.
// Populated by phoneblock_check() when called with a non-NULL `out`.
//
// `label` and `location` are non-empty only if the queried number had
// an active row in the NUMBERS table. Archived rows are filtered out
// by the server (`AND s.ACTIVE`) and produce empty strings here.
//
// `votes` is the count to surface in the UI:
//   - direct + effective wildcard votes when verdict == SPAM
//   - max(raw range10, raw range100) when verdict == LEGITIMATE but
//     `suspected` is set (votes exist in the bucket aggregations but
//     didn't pass the cnt threshold to count as SPAM)
//   - 0 otherwise
//
// `suspected` is true exactly when verdict == LEGITIMATE and any range
// votes were present in the response — i.e. there is a soft signal
// that the number is in a SPAM neighborhood without enough evidence
// to block.
//
// `white_listed` / `black_listed` reflect the per-user BLOCKLIST entry
// the server attaches to a matching numbers[] row, when the API
// request was authenticated with a token belonging to a user who has
// personalized this number. They are *hard* overrides:
//
//   white_listed → verdict = LEGITIMATE  (regardless of votes)
//   black_listed → verdict = SPAM        (regardless of votes)
//   white_listed wins over black_listed if both are somehow set.
//
// When an override is in effect, `votes` reflects the underlying
// community signal (so the UI can still show "you whitelisted this
// despite N reports"), but `suspected` is forced to false — the
// override is final, the soft-signal label would just confuse.
typedef struct {
    verdict_t verdict;
    int       votes;
    bool      suspected;
    bool      white_listed;
    bool      black_listed;
    char      label[32];
    char      location[80];
} pb_check_result_t;

// Query the PhoneBlock API for the given phone number.
// The number is passed through unmodified — the server normalizes.
// Returns VERDICT_SPAM, VERDICT_LEGITIMATE, or VERDICT_ERROR on
// transport/parse problems.
//
// If `out` is non-NULL it is populated with display fields and vote
// counts (see pb_check_result_t). Pass NULL when the caller only
// needs the verdict.
verdict_t phoneblock_check(const char *phone_number, pb_check_result_t *out);

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

// Exchanges the dongle's API token for a short-lived one-shot login
// ticket bound to `next` (a server-relative path on phoneblock.net).
// On success, writes the full redemption URL
// "<base-url>/auth/login-ticket?t=<jwt>" to `url_out` and returns
// true. Caller hands `url_out` to the browser via a 302; the long-
// lived API token never leaves the dongle.
bool phoneblock_mint_login_ticket(const char *next, char *url_out, size_t url_cap);
