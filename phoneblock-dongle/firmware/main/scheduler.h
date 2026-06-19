#pragma once

#include <stdbool.h>

// Single background task that owns every recurring ~24 h housekeeping
// job: the token self-test (+ log-report flush), the firmware
// auto-update check, and the Fritz!Box blocklist sync. It replaces the
// three separate per-feature tasks that each idled in a vTaskDelay loop
// — consolidating them reclaims ~14 KB of task stack and keeps the
// daily cadence (interval + ±30 min jitter) in one place.
//
// Each job carries its own next-due time and is fired independently when
// that time passes; the jobs run sequentially on this one task, so a
// long OTA download can delay the others by minutes. That is acceptable
// because all three are daily and best-effort. Each job applies its own
// "is the device provisioned / did the user enable this" gates.

// Spawn the scheduler task (idempotent). Initialises the sync subsystem's
// status mutex before starting, so a web-UI snapshot is safe immediately.
void scheduler_start(void);

// Ask the scheduler to run the blocklist sync as soon as possible,
// bypassing the auto-sync toggle (manual intent from the web UI). Wakes
// the task via a notification; the request coalesces if one is already
// pending. Returns false if the scheduler task is not running.
//
// The "is a sync already in progress" guard lives in sync_trigger_now(),
// which is the public entry point the web UI calls.
bool scheduler_request_sync(void);
