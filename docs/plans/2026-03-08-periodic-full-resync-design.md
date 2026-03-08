# Periodic Full Blocklist Resync

## Problem

The local blocklist cache syncs fully once (version=0), then only incrementally via `?since=<version>`. If inconsistencies accumulate (missed deletions, partial network failures), they are never corrected.

## Solution

Add a `syncCount` column to `blocklist_sync`. Increment on each successful sync. When `syncCount >= 40`, reset version to 0 and clear the local blocklist before the next sync, forcing a full resync.

Since `?since=0` is equivalent to a full sync on the server, the sync URL logic stays the same — only the version and local data are reset.

## Changes

### 1. DB Migration (v12)

Add `syncCount INTEGER NOT NULL DEFAULT 0` to `blocklist_sync`.

### 2. `storage.dart`

- Update schema creation (v12 table definition) and `_onUpgrade` migration
- `applyBlocklistUpdates`: increment `syncCount` alongside version/lastSyncTime
- New `resetForFullSync()`: clear `blocklist` table, reset `version` and `syncCount` to 0 in `blocklist_sync`

### 3. `blocklist_sync_service.dart`

- Read `syncCount` from sync metadata
- If `syncCount >= 40`: call `resetForFullSync()` before syncing (version becomes 0, triggering full sync)
- Otherwise: proceed as today (incremental)

## Behavior

- Daily syncs mean full resync roughly every 40 days (~monthly)
- Full sync replaces all local data, correcting accumulated drift
- Existing users get `syncCount=0` on migration; first forced full resync after 40 incremental syncs
