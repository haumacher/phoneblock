# Local Blocklist Cache for PhoneBlock Mobile

## Problem

The mobile app currently queries the PhoneBlock API for every incoming call with a 4.5-second timeout. If the API call times out or there is no internet connection, calls are always accepted (fail-open). This means spam calls get through when the user is offline, in poor network conditions, or when the server is slow.

## Solution

Maintain a local SQLite copy of the PhoneBlock community blocklist, enabling offline call filtering with a local-first lookup pattern.

## Architecture

```
┌──────────────┐     1. lookup     ┌──────────────┐
│  CallChecker │────────────────►  │ Local SQLite  │
│  (native)    │                   │ blocklist tbl │
│              │  2. cache miss    └──────────────┘
│              │─────────────────► PhoneBlock API
└──────────────┘                   (existing flow)

┌──────────────┐   daily sync      ┌──────────────┐
│  WorkManager │────────────────►  │ PhoneBlock    │
│  (background)│   incremental     │ /api/blocklist│
└──────────────┘                   └──────────────┘
```

- **Call screening:** CallChecker normalizes the number to E.164, checks wildcard prefixes (unchanged), then queries the local blocklist table. On cache hit with sufficient votes: block immediately without any network call. On cache miss: fall back to the existing API query with 4.5s timeout.
- **Sync:** A daily background sync (via Android WorkManager with `workmanager` Flutter package) calls `GET /api/blocklist?since=<version>` to apply incremental updates to the local cache.
- **Initial sync:** Uses `since=0` which triggers a full sync on the server. Same code path as incremental sync.

## Database Schema

Add to the existing `screened_calls.db` (version bump from 9 to 10):

### `blocklist` table

| Column | Type | Description |
|--------|------|-------------|
| `phone` | TEXT PRIMARY KEY | Phone number in international format (e.g., `+49123456789`) |
| `votes` | INTEGER NOT NULL | Community vote count |
| `rating` | TEXT | Spam category (`C_PING`, `E_ADVERTISING`, `G_FRAUD`, etc.) |
| `lastActivity` | INTEGER NOT NULL | Last activity timestamp (ms since epoch) |

### `blocklist_sync` table (single-row metadata)

| Column | Type | Description |
|--------|------|-------------|
| `id` | INTEGER PRIMARY KEY | Always 1 |
| `version` | INTEGER NOT NULL | Last synced blocklist version (starts at 0) |
| `lastSyncTime` | INTEGER NOT NULL | Timestamp of last successful sync (ms) |
| `syncOffset` | INTEGER NOT NULL | Random offset in ms (0–24h) for staggering sync times across clients |

## CallChecker Changes (Native Java)

Updated call screening flow in `CallChecker.java`:

1. Normalize number to E.164
2. Check wildcard prefixes (unchanged)
3. **NEW:** Query local SQLite blocklist by phone number
   - Open Flutter's `screened_calls.db` read-only via `SQLiteDatabase.openDatabase()` with `OPEN_READONLY` flag
   - DB path: `context.getDatabasePath("screened_calls.db")`
   - Query: `SELECT votes, rating FROM blocklist WHERE phone = ?`
   - If `votes >= minVotes`: block immediately, report as screened call
4. On cache miss: fall back to existing API query (unchanged, with 4.5s timeout)

## Dart-side Sync Service

New `BlocklistSyncService` class:

```
BlocklistSyncService.sync()
  → read version from blocklist_sync table (default 0)
  → GET /api/blocklist?since={version}
  → for each entry in response:
      votes > 0  → INSERT OR REPLACE into blocklist
      votes == 0 → DELETE from blocklist
  → update blocklist_sync.version to response.version
  → update blocklist_sync.lastSyncTime to now
```

## Background Sync Scheduling

- Use the `workmanager` Flutter package to register a periodic background task
- **Frequency:** Once per day (matches API rate limit)
- **Initial delay:** Random offset between 0 and 24 hours, persisted in `blocklist_sync.syncOffset` so the same device always syncs at roughly the same time of day. This distributes server load evenly.
- **Constraints:** Requires network connectivity (WorkManager handles this)
- **Retry:** WorkManager's built-in exponential backoff on failure

## Initial Sync on Setup / Migration

- **New user setup:** After setup completes and the auth token is stored, trigger an immediate first sync (`since=0`). The sync runs in the background — the user can already use the app while it populates.
- **App migration (DB upgrade to v10):** Creates the new tables. On next app start (user is already logged in), triggers an immediate sync to populate the cache.

In both cases the user is already authenticated when the sync fires.

## Settings UI

Add a "Blocklist Cache" section to the existing settings screen:

- **Cached entries:** Number of entries in the local blocklist (e.g., "12,345 numbers")
- **Last sync:** Relative time since last sync (e.g., "2 hours ago" or "Never")
- **Blocklist version:** Current version number (e.g., "v423")
- **Manual sync button:** Triggers an immediate sync (useful for testing and debugging)

## Summary of Changes

| Component | Change |
|-----------|--------|
| `storage.dart` | DB version 10, add `blocklist` + `blocklist_sync` tables, CRUD methods |
| New `blocklist_sync_service.dart` | Dart class for incremental sync logic |
| `CallChecker.java` | Add local SQLite lookup before API fallback |
| `pubspec.yaml` | Add `workmanager` dependency |
| `AndroidManifest.xml` | WorkManager initialization (if needed) |
| `main.dart` | Register WorkManager periodic task after login, trigger initial sync |
| Settings screen | Show cache info (entries, last sync, version) and manual sync button |
