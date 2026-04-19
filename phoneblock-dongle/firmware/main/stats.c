#include "stats.h"

#include <string.h>

#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "esp_timer.h"

// All mutable state lives here and is guarded by s_mutex. Callers
// either record an event (briefly lock, mutate, unlock) or take a
// snapshot (briefly lock, memcpy, unlock). Snapshots mean the web
// layer never holds the mutex while talking to its HTTP client.

static SemaphoreHandle_t s_mutex;

static stats_counters_t s_counters;

// Ring buffers: s_head points at the next slot to be written, so the
// newest entry is at (s_head - 1) mod N, the oldest at s_head if
// s_full, else slot 0.
static stats_call_t  s_calls[STATS_MAX_CALLS];
static int           s_calls_head;
static int           s_calls_count;

static stats_error_t s_errors[STATS_MAX_ERRORS];
static int           s_errors_head;
static int           s_errors_count;

static void lock(void)   { xSemaphoreTake(s_mutex, portMAX_DELAY); }
static void unlock(void) { xSemaphoreGive(s_mutex); }

void stats_setup(void)
{
    s_mutex = xSemaphoreCreateMutex();
    memset(&s_counters, 0, sizeof(s_counters));
    memset(s_calls, 0, sizeof(s_calls));
    memset(s_errors, 0, sizeof(s_errors));
    s_calls_head = s_calls_count = 0;
    s_errors_head = s_errors_count = 0;
}

// --- Event hooks ----------------------------------------------------

static void copy_trim(char *dst, size_t cap, const char *src)
{
    if (!src) { dst[0] = '\0'; return; }
    size_t n = strnlen(src, cap - 1);
    memcpy(dst, src, n);
    dst[n] = '\0';
}

void stats_record_call(const char *number, const char *display, verdict_t verdict)
{
    lock();

    stats_call_t *slot = &s_calls[s_calls_head];
    slot->at_us   = esp_timer_get_time();
    slot->verdict = verdict;
    copy_trim(slot->number,  sizeof(slot->number),  number);
    copy_trim(slot->display, sizeof(slot->display), display);

    s_calls_head = (s_calls_head + 1) % STATS_MAX_CALLS;
    if (s_calls_count < STATS_MAX_CALLS) s_calls_count++;

    s_counters.total_calls++;
    switch (verdict) {
        case VERDICT_SPAM:       s_counters.spam_blocked++; break;
        case VERDICT_LEGITIMATE: s_counters.legitimate++;   break;
        case VERDICT_ERROR:      s_counters.errors++;       break;
    }

    unlock();
}

void stats_record_error(const char *tag, const char *message)
{
    lock();

    stats_error_t *slot = &s_errors[s_errors_head];
    slot->at_us = esp_timer_get_time();
    copy_trim(slot->tag,     sizeof(slot->tag),     tag);
    copy_trim(slot->message, sizeof(slot->message), message);

    s_errors_head = (s_errors_head + 1) % STATS_MAX_ERRORS;
    if (s_errors_count < STATS_MAX_ERRORS) s_errors_count++;

    unlock();
}

void stats_record_sip_state(bool registered)
{
    lock();
    if (registered && !s_counters.sip_registered) {
        s_counters.sip_registered_since_us = esp_timer_get_time();
    }
    s_counters.sip_registered = registered;
    unlock();
}

void stats_record_api_duration(int64_t duration_us)
{
    lock();
    s_counters.last_api_duration_us = duration_us;
    unlock();
}

// --- Snapshots ------------------------------------------------------

void stats_snapshot_counters(stats_counters_t *out)
{
    lock();
    *out = s_counters;
    unlock();
}

// Iterate the ring buffer from newest to oldest.
static int snapshot_ring(const void *buffer, size_t slot_size,
                         int head, int count, int ring_n,
                         void *out, int max)
{
    int n = count < max ? count : max;
    for (int i = 0; i < n; i++) {
        int src = (head - 1 - i + ring_n) % ring_n;
        memcpy((char *)out + (size_t)i * slot_size,
               (const char *)buffer + (size_t)src * slot_size,
               slot_size);
    }
    return n;
}

int stats_snapshot_calls(stats_call_t *out, int max)
{
    lock();
    int n = snapshot_ring(s_calls, sizeof(*s_calls),
                          s_calls_head, s_calls_count, STATS_MAX_CALLS,
                          out, max);
    unlock();
    return n;
}

int stats_snapshot_errors(stats_error_t *out, int max)
{
    lock();
    int n = snapshot_ring(s_errors, sizeof(*s_errors),
                          s_errors_head, s_errors_count, STATS_MAX_ERRORS,
                          out, max);
    unlock();
    return n;
}

void stats_clear_errors(void)
{
    lock();
    s_errors_head = 0;
    s_errors_count = 0;
    unlock();
}
