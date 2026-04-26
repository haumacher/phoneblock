#pragma once

#include <stdbool.h>
#include <stdint.h>

// Token health-check — keeps a continuously refreshed picture of
// whether the PhoneBlock API still accepts the configured bearer
// token, so the dashboard can warn before the next call hits a 401.
//
// On boot main.c runs one immediate check via selftest_run_now() so
// the existing latency/log behaviour is preserved. selftest_start()
// then spawns a background task that re-runs the check once every
// 24 hours and on demand from the web UI.

// Spawn the background self-test task. Safe to call from app_main;
// no-op on duplicate calls. The task blocks on a semaphore most of
// the time, waking for the daily timer or selftest_trigger_now().
void selftest_start(void);

// Run a self-test synchronously and update the snapshot. Returns
// the outcome of phoneblock_selftest(), or false if no token is
// configured or another run is already in progress.
bool selftest_run_now(void);

// Ask the background task to run a check immediately. Returns false
// if the task is not running or a run is already in progress.
bool selftest_trigger_now(void);

typedef struct {
    bool    ever_ran;        // at least one attempt done since boot
    bool    last_ok;         // last attempt accepted by the server
    bool    running;         // a self-test is in progress right now
    int64_t last_at_us;      // esp_timer when the last attempt finished
    char    last_error[64];  // short diagnostic, empty on success
} selftest_status_t;

void selftest_snapshot(selftest_status_t *out);
