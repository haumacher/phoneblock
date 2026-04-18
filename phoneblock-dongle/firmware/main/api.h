#pragma once

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
