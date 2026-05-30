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
