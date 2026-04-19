#pragma once

#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>

// Blocklist sync — uploads the Fritz!Box's call-barring entries to
// PhoneBlock as contributions, then deletes them from the box so the
// user's phonebook stays as a simple submission form and the answer
// bot picks up every future caller.
//
// Runs once per day from a background task and can be triggered on
// demand from the web UI via sync_trigger_now().

// Spawn the sync task. Safe to call from app_main; no-op on duplicate
// calls. The task blocks on a semaphore most of the time, waking
// for the daily timer or sync_trigger_now().
void sync_start(void);

// Ask the sync task to run immediately. Returns false if the task is
// not running or a run is already in progress.
bool sync_trigger_now(void);

// Snapshot of the most recent sync attempt, for the dashboard.
typedef struct {
    bool    ever_ran;        // at least one attempt done since boot
    bool    last_ok;         // last attempt finished without a transport error
    bool    running;         // a sync is in progress right now
    int64_t last_at_us;      // esp_timer when the last attempt finished
    int     last_pushed;     // count of entries successfully submitted
    int     last_failed;     // count of entries that errored on push or delete
    char    last_error[64];  // short diagnostic, empty on success
} sync_status_t;

void sync_snapshot(sync_status_t *out);
