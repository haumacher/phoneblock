#pragma once

#include <stdbool.h>

// Single background task that owns every recurring ~24 h housekeeping
// job: the token self-test (+ log-report flush), the firmware
// auto-update check, the Fritz!Box blocklist sync, the status mail, and
// the local binary-blocklist download. It replaces the separate
// per-feature tasks that each idled in a vTaskDelay loop — consolidating
// them reclaims task stack and keeps the daily cadence (interval + ±30
// min jitter) in one place.
//
// Each job carries its own next-due time and is fired independently when
// that time passes; the jobs run sequentially on this one task, so a
// long OTA download can delay the others by minutes. That is acceptable
// because all three are daily and best-effort. Each job applies its own
// "is the device provisioned / did the user enable this" gates.

// Spawn the scheduler task (idempotent). Initialises the sync subsystem's
// status mutex before starting, so a web-UI snapshot is safe immediately.
// Start the scheduler task. Pass i18n_refresh_soon=true when this boot is the
// first after a firmware update (see main.c): the localized-asset sync then
// runs shortly after boot instead of at its usual ~3 min slot, so a new
// release picks up its version's announcement / mail / UI packs promptly.
void scheduler_start(bool i18n_refresh_soon);

// Ask the scheduler to run the blocklist sync as soon as possible,
// bypassing the auto-sync toggle (manual intent from the web UI). Wakes
// the task via a notification; the request coalesces if one is already
// pending. Returns false if the scheduler task is not running.
//
// The "is a sync already in progress" guard lives in sync_trigger_now(),
// which is the public entry point the web UI calls.
bool scheduler_request_sync(void);

// Ask the scheduler to run the local binary-blocklist download as soon as
// possible (manual intent from the web UI / token-set handler). Wakes the
// task via a notification; the request coalesces if one is already
// pending. Returns false if the scheduler task is not running.
//
// The "is a download already in progress" guard lives in
// blocklist_sync_trigger_now(), the public entry point the web UI calls.
bool scheduler_request_blocklist_sync(void);

// Ask the scheduler to send a status-mail test as soon as possible (the
// web UI's "send test email" button). The blocking SMTP/TLS send runs on
// the scheduler task, not the caller's: the httpd handler must return
// promptly or its task watchdog panics the wedged worker. Fire-and-forget
// — the outcome is logged (and surfaced in the web UI's log panel), not
// returned. Returns false if the scheduler task is not running.
bool scheduler_request_mail_test(void);

// Ask the scheduler to download the localized device assets (announcement
// audio / mail pack) for the current ui_lang as soon as possible — raised
// when the user switches the UI language. Runs the blocking HTTPS/SPIFFS
// work on the scheduler task, not the caller's httpd thread. The "already
// running" guard lives in i18n_sync_trigger_now(). Returns false if the
// scheduler task is not running.
bool scheduler_request_i18n_sync(void);

// Notify the scheduler that the wall clock has just been set (or stepped),
// so any time-of-day ("daily") jobs recompute their next run against real
// local time instead of the placeholder retry they parked on while the
// clock was still unknown. Called from time_sync.c. Safe to call before
// the scheduler task exists — it is then a no-op (the task computes due
// times from the live clock when it starts).
void scheduler_notify_time_synced(void);
