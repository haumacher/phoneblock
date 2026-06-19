#pragma once

#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>

// Blocklist sync — uploads the Fritz!Box's call-barring entries to
// PhoneBlock as contributions, then deletes them from the box so the
// user's phonebook stays as a simple submission form and the answer
// bot picks up every future caller.
//
// Runs once per day from the shared scheduler task (see scheduler.c) and
// can be triggered on demand from the web UI via sync_trigger_now().

// Initialise the sync status mutex. Called once by scheduler_start()
// before the scheduler task (or any web-UI snapshot) can run; no-op on
// duplicate calls.
void sync_init(void);

// Perform one sync run. `manual` bypasses the config_sync_enabled()
// toggle (a manual trigger always runs); a scheduled run honours it.
// Invoked by the scheduler task, never inline on a caller's thread.
void sync_run(bool manual);

// Ask the scheduler to run a sync as soon as possible. Returns false if
// the scheduler is not running or a run is already in progress.
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
