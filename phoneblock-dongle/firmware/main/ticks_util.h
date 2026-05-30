#pragma once

#include <stdint.h>

#include "freertos/FreeRTOS.h"

// pdMS_TO_TICKS multiplies its argument by configTICK_RATE_HZ in 32-bit
// TickType_t and overflows for daily intervals (86_400_000 ms * 100 Hz
// wraps to ~8.3 min). Express long delays in seconds and convert with
// this helper, which does the multiplication in 64-bit.
static inline TickType_t seconds_to_ticks(uint32_t seconds)
{
    return (TickType_t)((uint64_t)seconds * configTICK_RATE_HZ);
}

// Drop-in replacement for pdMS_TO_TICKS that refuses to compile when a
// constant millisecond value would overflow TickType_t at the current
// tick rate. Catches the most common form of the gotcha — a literal
// like `pb_ms_to_ticks(24 * 3600 * 1000)` — at build time. For runtime
// values where the upper bound is unknown, use seconds_to_ticks
// instead.
#define pb_ms_to_ticks(ms) __extension__ ({ \
    _Static_assert((uint64_t)(ms) * configTICK_RATE_HZ <= UINT32_MAX, \
        "pb_ms_to_ticks: value overflows TickType_t at this tick rate " \
        "— use seconds_to_ticks for long intervals"); \
    pdMS_TO_TICKS(ms); \
})
