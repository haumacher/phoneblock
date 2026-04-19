#pragma once

#include <stdbool.h>

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
