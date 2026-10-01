#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// --- Logging -----------------------------------------------------------
//
// On ESP-IDF these are the ESP_LOGx macros themselves: they add the
// "I (1234) tag: " prefix and the newline, and main/log_capture.c relies on
// that prefix to mirror WARN/ERROR lines into the web UI's "Protokoll"
// panel. They also keep the compile-time LOG_LOCAL_LEVEL filtering and
// printf format checking. (esp_log_writev() alone does neither prefix nor
// newline.)
#ifdef ESP_PLATFORM
#include "esp_log.h"
#define pb_log_info(tag, ...) ESP_LOGI(tag, __VA_ARGS__)
#define pb_log_warn(tag, ...) ESP_LOGW(tag, __VA_ARGS__)
#define pb_log_err(tag, ...)  ESP_LOGE(tag, __VA_ARGS__)
#else
void pb_log_info(const char *tag, const char *fmt, ...)
    __attribute__((format(printf, 2, 3)));
void pb_log_warn(const char *tag, const char *fmt, ...)
    __attribute__((format(printf, 2, 3)));
void pb_log_err(const char *tag, const char *fmt, ...)
    __attribute__((format(printf, 2, 3)));
#endif

// --- Time, randomness, watchdog -----------------------------------------

uint64_t pb_monotonic_us(void);
uint32_t pb_random_u32(void);
void pb_random_fill(void *out, size_t len);
int pb_watchdog_is_subscribed(void);
void pb_watchdog_subscribe(void);
void pb_watchdog_reset(void);

// --- Tasks ---------------------------------------------------------------

typedef enum {
    PB_PRIO_NORMAL,  // protocol / background work (SIP task)
    PB_PRIO_MEDIA,   // real-time media pacing, above PB_PRIO_NORMAL (RTP)
} pb_task_prio_t;

// Starts fn(arg) as a detached task that ends when fn returns.
//
// stack_bytes is the ESP32 stack budget in bytes, tuned on the hardware.
// The ESP32 implementation passes it straight through (ESP-IDF's FreeRTOS
// counts stack depth in bytes). Linux treats it as a hint only and applies a
// generous floor: frames are 64-bit, glibc/musl and their libraries need far
// more stack, PTHREAD_STACK_MIN differs per target, and musl's default thread
// stack is small — while an unused Linux stack costs only virtual memory.
//
// Returns false if the task could not be created. There is deliberately no
// handle: a task may finish before the creator could store one. Callers that
// need a "running" flag set it before creating and clear it in the task.
bool pb_task_create(void (*fn)(void *), void *arg, const char *name,
                    size_t stack_bytes, pb_task_prio_t prio);

// Sleeps at least `ms` milliseconds. On the ESP32 the sleep is rounded to
// whole FreeRTOS ticks (10 ms at the project's 100 Hz): a request below one
// tick becomes a plain yield, so don't build a polling loop on short sleeps.
void pb_task_sleep_ms(uint32_t ms);
void pb_task_yield(void);

// Fixed-rate pacing without drift (RTP frames). Opaque: FreeRTOS ticks on the
// ESP32 (vTaskDelayUntil, tick-exact), CLOCK_MONOTONIC nanoseconds on Linux.
// Like vTaskDelayUntil, a missed deadline returns immediately and the next
// one still advances by one interval.
typedef struct {
    uint64_t at;
} pb_deadline_t;

void pb_deadline_init(pb_deadline_t *deadline);
void pb_task_delay_until(pb_deadline_t *deadline, uint32_t interval_ms);

// --- Mutex ---------------------------------------------------------------

typedef struct pb_mutex pb_mutex_t;

pb_mutex_t *pb_mutex_create(void);
void pb_mutex_lock(pb_mutex_t *mutex);
void pb_mutex_unlock(pb_mutex_t *mutex);

// --- Identity ------------------------------------------------------------

// MAC address identifying this device. On Linux: the globally administered
// (burned-in) address with the lowest interface index, skipping loopback.
// Locally administered addresses (Docker bridges, veth, VPN, randomized Wi-Fi
// MACs) only count when no burned-in one exists, so the result doesn't change
// when such interfaces come and go. Returns 0 on success, -1 if none found.
int pb_get_mac(uint8_t mac[6]);
