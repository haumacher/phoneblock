# Periodic Full Blocklist Resync Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Ensure the local blocklist cache is fully resynced approximately once a month (every 40 incremental syncs) to correct any accumulated inconsistencies.

**Architecture:** Add a `syncCount` column to `blocklist_sync`. Increment on each sync. When `syncCount >= 40`, clear local data and reset version to 0 before syncing, which triggers a full resync via `?since=0`.

**Tech Stack:** Dart, SQLite (sqflite), Flutter

---

### Task 1: Add `syncCount` column to DB schema and migration

**Files:**
- Modify: `phoneblock_mobile/lib/storage.dart:208` (bump version to 12)
- Modify: `phoneblock_mobile/lib/storage.dart:290-301` (`_createDB` — add `syncCount` to table definition and INSERT)
- Modify: `phoneblock_mobile/lib/storage.dart:428-431` (`_upgradeDB` — add `if (oldVersion < 12)` migration block)

**Step 1: Update DB version**

In `storage.dart` line 208, change:
```dart
      version: 11,
```
to:
```dart
      version: 12,
```

**Step 2: Update `_createDB` table definition**

In `storage.dart` lines 290-301, change the `blocklist_sync` CREATE TABLE and INSERT to:
```dart
    // Blocklist sync metadata table (single row)
    await db.execute('''
      CREATE TABLE blocklist_sync (
        id INTEGER PRIMARY KEY,
        version INTEGER NOT NULL DEFAULT 0,
        lastSyncTime INTEGER NOT NULL DEFAULT 0,
        syncOffset INTEGER NOT NULL DEFAULT 0,
        syncCount INTEGER NOT NULL DEFAULT 0
      )
    ''');

    await db.execute('''
      INSERT INTO blocklist_sync (id, version, lastSyncTime, syncOffset, syncCount) VALUES (1, 0, 0, 0, 0)
    ''');
```

**Step 3: Add migration for existing users**

In `storage.dart` after line 430 (end of `if (oldVersion < 11)` block), add:
```dart
    if (oldVersion < 12) {
      await db.execute('ALTER TABLE blocklist_sync ADD COLUMN syncCount INTEGER NOT NULL DEFAULT 0');
    }
```

**Step 4: Commit**

```bash
git add phoneblock_mobile/lib/storage.dart
git commit -m "Add syncCount column to blocklist_sync for periodic full resync tracking"
```

---

### Task 2: Add `resetForFullSync()` method to storage

**Files:**
- Modify: `phoneblock_mobile/lib/storage.dart:669-672` (after `clearBlocklist()`)

**Step 1: Add `resetForFullSync()` method**

In `storage.dart` after the `clearBlocklist()` method (after line 672), add:
```dart

  /// Resets the blocklist for a full resync.
  ///
  /// Clears all cached entries and resets version and syncCount to 0,
  /// so the next sync fetches the complete blocklist from the server.
  Future<void> resetForFullSync() async {
    final db = await database;
    await db.transaction((txn) async {
      await txn.delete('blocklist');
      await txn.update('blocklist_sync', {
        'version': 0,
        'syncCount': 0,
      }, where: 'id = 1');
    });
  }
```

**Step 2: Commit**

```bash
git add phoneblock_mobile/lib/storage.dart
git commit -m "Add resetForFullSync() to clear local cache for periodic full resync"
```

---

### Task 3: Increment `syncCount` in `applyBlocklistUpdates`

**Files:**
- Modify: `phoneblock_mobile/lib/storage.dart:732-735` (inside `applyBlocklistUpdates` transaction)

**Step 1: Add syncCount increment**

In `storage.dart` lines 732-735, change:
```dart
      await txn.update('blocklist_sync', {
        'version': newVersion,
        'lastSyncTime': DateTime.now().millisecondsSinceEpoch,
      }, where: 'id = 1');
```
to:
```dart
      await txn.rawUpdate(
        'UPDATE blocklist_sync SET version = ?, lastSyncTime = ?, syncCount = syncCount + 1 WHERE id = 1',
        [newVersion, DateTime.now().millisecondsSinceEpoch],
      );
```

Note: Using `rawUpdate` with `syncCount = syncCount + 1` to atomically increment the counter within the same transaction as the data update.

**Step 2: Commit**

```bash
git add phoneblock_mobile/lib/storage.dart
git commit -m "Increment syncCount on each successful blocklist sync"
```

---

### Task 4: Trigger full resync in `BlocklistSyncService.sync()`

**Files:**
- Modify: `phoneblock_mobile/lib/blocklist_sync_service.dart:39-57` (the `sync()` method)

**Step 1: Add full resync logic**

In `blocklist_sync_service.dart`, replace lines 49-57 with:
```dart
      final db = ScreenedCallsDatabase.instance;
      final syncInfo = await db.getBlocklistSyncInfo();
      final currentVersion = syncInfo['version'] as int;
      final syncCount = syncInfo['syncCount'] as int;

      // Trigger a full resync every 40 syncs to correct accumulated drift.
      final needsFullResync = syncCount >= 40;
      if (needsFullResync) {
        if (kDebugMode) {
          print('BlocklistSync: syncCount=$syncCount, triggering full resync');
        }
        await db.resetForFullSync();
      }

      final version = needsFullResync ? 0 : currentVersion;

      if (kDebugMode) {
        print('BlocklistSync: Starting sync from version $version');
      }

      final url = '$pbBaseUrl/api/blocklist?since=$version';
```

**Step 2: Commit**

```bash
git add phoneblock_mobile/lib/blocklist_sync_service.dart
git commit -m "Trigger full blocklist resync every 40 syncs to fix accumulated inconsistencies"
```

---

### Task 5: Verify with `flutter analyze`

**Step 1: Run static analysis**

Run: `cd phoneblock_mobile && flutter analyze`
Expected: No new warnings or errors

**Step 2: If issues found, fix them and commit**
