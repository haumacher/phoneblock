#pragma once

#include <stdint.h>

#include "freertos/FreeRTOS.h"

// pdMS_TO_TICKS multiplies its argument by configTICK_RATE_HZ in 32-bit
// TickType_t and overflows for daily intervals (86_400_000 ms * 100 Hz
// wraps to ~8.3 min). The fix is the unit, not wider arithmetic: in
// seconds, even a 24 h delay stays comfortably inside uint32_t
// (86_400 * 100 = 8_640_000). At 100 Hz, TickType_t itself caps out
// around 497 days, so any input that would overflow this multiplication
// is unrepresentable as a tick count anyway.
//
// Background: FreeRTOS-Kernel PR #866 widened pdMS_TO_TICKS to uint64_t
// upstream and shipped in kernel V11.0.0 (Dec 2023). ESP-IDF v5.3 still
// bundles V10.5.1 with the 32-bit macro, which is why this helper is
// needed today. When we eventually move to ESP-IDF >= v5.4 (kernel V11+),
// plain pdMS_TO_TICKS becomes safe again and the helpers in this header
// can be retired in favour of it.
static inline TickType_t seconds_to_ticks(uint32_t seconds)
{
    return (TickType_t)(seconds * configTICK_RATE_HZ);
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
