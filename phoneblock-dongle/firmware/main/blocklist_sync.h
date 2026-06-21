// Daily download of the binary blocklist files from the server.
//
// Two SPIFFS files are kept:
//   /spiffs/community.bin   — community list (shared across users with the
//                             same minVotes; depends only on that setting)
//   /spiffs/personal.bin    — user's personal black/white overrides
//
// The download runs once per day from the shared scheduler task (see
// scheduler.c) and can be triggered on demand from the web UI via
// blocklist_sync_trigger_now(). Each file is streamed to a `.tmp`
// sibling, then atomic-renamed into place — same pattern announcement.c
// uses. A lookup that races with the rename either still sees the old
// file (open during rename) or the new file (open after rename); it
// never sees a half-written one.
//
// The dongle's "wildcards on/off" preference is evaluated locally at
// lookup time, not at sync time. The community file always carries the
// prefix section; the user's setting only decides whether to consult it.
#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "blocklist_lookup.h"

#define BLOCKLIST_COMMUNITY_PATH "/spiffs/community.bin"
#define BLOCKLIST_PERSONAL_PATH  "/spiffs/personal.bin"

// Initialise the sync status mutex and prime the cached file sizes.
// Called once by scheduler_start() before the scheduler task (or any
// web-UI snapshot) can run; no-op on duplicate calls.
void blocklist_sync_init(void);

// Perform one download run (community + personal). Invoked by the shared
// scheduler task, never inline on a caller's thread. Skips silently when
// no PhoneBlock token is configured yet.
void blocklist_sync_run(void);

// Ask the scheduler to run a download as soon as possible. Returns false
// if the scheduler is not running or a run is already in progress.
bool blocklist_sync_trigger_now(void);

// Combined verdict using the user's personal list first, then the
// community list. The `consult_wildcards` flag (the user's
// "wildcards on/off" preference) gates the prefix-section search in
// both lists.
//
// Returns BLOCKLIST_UNKNOWN when neither file is available or when the
// query matches no entry. The caller decides the UNKNOWN-vs-LEGIT
// default policy.
blocklist_verdict_t blocklist_sync_check(const char *digits,
                                         bool consult_wildcards);

// Status snapshot for the dashboard.
typedef struct {
    bool    have_community;
    bool    have_personal;
    int     community_size;     // total records (exact + prefix)
    int     personal_size;
    bool    ever_ran;           // at least one attempt since boot
    bool    last_ok;            // last attempt finished without error
    bool    running;            // a sync is in progress
    int64_t last_at_us;         // esp_timer when the last attempt finished
    char    last_error[64];
} blocklist_sync_status_t;

void blocklist_sync_snapshot(blocklist_sync_status_t *out);
