# Local Blocklist Cache Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a local SQLite blocklist cache to PhoneBlock Mobile so calls can be filtered offline using incremental sync from the server.

**Architecture:** The app syncs the community blocklist to a local SQLite table via `GET /api/blocklist?since=<version>`. The native `CallChecker` queries this table first; on cache miss it falls back to the existing API query. A daily WorkManager task keeps the cache current with randomized sync times.

**Tech Stack:** Flutter/Dart (sqflite, workmanager, http), Android Java (SQLiteDatabase), PhoneBlock REST API

**Design Doc:** `docs/plans/2026-03-07-local-blocklist-cache-design.md`

---

### Task 1: Update API models to include version and lastActivity

The server's `/api/blocklist` response includes `version` (on Blocklist) and `lastActivity` (on BlockListEntry) fields that the Flutter models don't have yet. These are generated from msgbuf protocol definitions in `phoneblock-shared/`.

**Files:**
- Modify: `phoneblock-shared/src/main/proto/phoneblock-api.proto` (or equivalent `.proto` file defining Blocklist/BlockListEntry)
- Regenerate: `phoneblock_mobile/lib/api.dart` (generated code)

**Step 1: Find the proto definition**

Search for the Blocklist/BlockListEntry message definitions:
```bash
grep -rn "Blocklist\|BlockListEntry" phoneblock-shared/src/main/proto/
```

**Step 2: Add missing fields**

Add `version` field (type `long`) to the `Blocklist` message and `lastActivity` field (type `long`) to the `BlockListEntry` message in the proto file.

**Step 3: Regenerate Java and Dart code**

```bash
cd phoneblock-shared
mvn generate-sources
```

Then check if the Flutter `api.dart` is also auto-generated or if it needs manual updates. The Flutter `api.dart` classes (`Blocklist` at line 779, `BlockListEntry` at line 1171) use a custom `_JsonObject`/`jsontool` pattern. If these are generated from the same proto, regeneration handles it. If hand-maintained, add the fields manually:

For `Blocklist` (api.dart ~line 779): add `int version = 0;` field, plus `_readProperty` case for `"version"` and `_writeProperties` entry.

For `BlockListEntry` (api.dart ~line 1171): add `int lastActivity = 0;` field, plus `_readProperty` case for `"lastActivity"` and `_writeProperties` entry.

**Step 4: Verify it compiles**

```bash
cd phoneblock_mobile
flutter pub get && flutter analyze
```

**Step 5: Commit**

```bash
git add -A
git commit -m "feat(#264): add version and lastActivity fields to Blocklist API models"
```

---

### Task 2: Add blocklist and blocklist_sync tables to SQLite

**Files:**
- Modify: `phoneblock_mobile/lib/storage.dart`

**Step 1: Bump DB version from 9 to 10**

In `_initDB` method (line 203), change `version: 9` to `version: 10`.

**Step 2: Add table creation to `_createDB`**

After the `wildcard_blocks` table creation (line 271), add:

```dart
await db.execute('''
  CREATE TABLE blocklist (
    phone TEXT PRIMARY KEY,
    votes INTEGER NOT NULL,
    rating TEXT,
    lastActivity INTEGER NOT NULL DEFAULT 0
  )
''');

await db.execute('''
  CREATE TABLE blocklist_sync (
    id INTEGER PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    lastSyncTime INTEGER NOT NULL DEFAULT 0,
    syncOffset INTEGER NOT NULL DEFAULT 0
  )
''');

await db.execute('''
  INSERT INTO blocklist_sync (id, version, lastSyncTime, syncOffset) VALUES (1, 0, 0, 0)
''');
```

**Step 3: Add migration in `_upgradeDB`**

After the version 9 migration block (line 372), add:

```dart
if (oldVersion < 10) {
  await db.execute('''
    CREATE TABLE blocklist (
      phone TEXT PRIMARY KEY,
      votes INTEGER NOT NULL,
      rating TEXT,
      lastActivity INTEGER NOT NULL DEFAULT 0
    )
  ''');
  await db.execute('''
    CREATE TABLE blocklist_sync (
      id INTEGER PRIMARY KEY,
      version INTEGER NOT NULL DEFAULT 0,
      lastSyncTime INTEGER NOT NULL DEFAULT 0,
      syncOffset INTEGER NOT NULL DEFAULT 0
    )
  ''');
  await db.execute('''
    INSERT INTO blocklist_sync (id, version, lastSyncTime, syncOffset) VALUES (1, 0, 0, 0)
  ''');
}
```

**Step 4: Add CRUD methods for blocklist**

Add these methods to `ScreenedCallsDatabase`:

```dart
/// Returns the number of entries in the local blocklist cache.
Future<int> getBlocklistCount() async {
  final db = await database;
  final result = await db.rawQuery('SELECT COUNT(*) as cnt FROM blocklist');
  return Sqflite.firstIntValue(result) ?? 0;
}

/// Looks up a phone number in the local blocklist.
/// Returns the entry if found, null otherwise.
Future<Map<String, dynamic>?> lookupBlocklist(String phone) async {
  final db = await database;
  final results = await db.query('blocklist',
    where: 'phone = ?',
    whereArgs: [phone],
    limit: 1,
  );
  return results.isEmpty ? null : results.first;
}

/// Inserts or updates a blocklist entry.
Future<void> upsertBlocklistEntry(String phone, int votes, String? rating, int lastActivity) async {
  final db = await database;
  await db.insert('blocklist', {
    'phone': phone,
    'votes': votes,
    'rating': rating,
    'lastActivity': lastActivity,
  }, conflictAlgorithm: ConflictAlgorithm.replace);
}

/// Removes a phone number from the local blocklist.
Future<void> deleteBlocklistEntry(String phone) async {
  final db = await database;
  await db.delete('blocklist', where: 'phone = ?', whereArgs: [phone]);
}

/// Clears all entries from the local blocklist cache.
Future<void> clearBlocklist() async {
  final db = await database;
  await db.delete('blocklist');
}

/// Returns the current blocklist sync metadata.
Future<Map<String, dynamic>> getBlocklistSyncInfo() async {
  final db = await database;
  final results = await db.query('blocklist_sync', where: 'id = 1');
  if (results.isEmpty) {
    return {'version': 0, 'lastSyncTime': 0, 'syncOffset': 0};
  }
  return results.first;
}

/// Updates the blocklist sync metadata after a successful sync.
Future<void> updateBlocklistSyncInfo(int version, int lastSyncTime) async {
  final db = await database;
  await db.update('blocklist_sync', {
    'version': version,
    'lastSyncTime': lastSyncTime,
  }, where: 'id = 1');
}

/// Sets the random sync offset (only on first sync registration).
Future<void> setBlocklistSyncOffset(int offsetMs) async {
  final db = await database;
  await db.update('blocklist_sync', {
    'syncOffset': offsetMs,
  }, where: 'id = 1');
}
```

**Step 5: Verify it compiles**

```bash
cd phoneblock_mobile
flutter analyze
```

**Step 6: Commit**

```bash
git add phoneblock_mobile/lib/storage.dart
git commit -m "feat(#264): add blocklist and blocklist_sync tables to SQLite schema"
```

---

### Task 3: Create BlocklistSyncService

**Files:**
- Create: `phoneblock_mobile/lib/blocklist_sync_service.dart`

**Step 1: Create the sync service**

```dart
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:phoneblock_mobile/api.dart' as api;
import 'package:phoneblock_mobile/main.dart' show callPhoneBlockApi, getAuthToken, pbBaseUrl;
import 'package:phoneblock_mobile/storage.dart';

/// Service for syncing the community blocklist to the local SQLite cache.
///
/// Uses incremental sync via `GET /api/blocklist?since=<version>` to minimize
/// data transfer. Entries with `votes > 0` are upserted, entries with
/// `votes == 0` are deleted (server signals removal).
class BlocklistSyncService {
  static final BlocklistSyncService instance = BlocklistSyncService._();

  BlocklistSyncService._();

  /// Performs an incremental blocklist sync.
  ///
  /// Reads the current version from `blocklist_sync`, fetches changes from
  /// the server, applies them to the local `blocklist` table, and updates
  /// the sync metadata.
  ///
  /// Returns `true` if sync succeeded, `false` otherwise.
  Future<bool> sync() async {
    try {
      final authToken = await getAuthToken();
      if (authToken == null || authToken.isEmpty) {
        if (kDebugMode) {
          print('BlocklistSync: No auth token, skipping sync');
        }
        return false;
      }

      final db = ScreenedCallsDatabase.instance;
      final syncInfo = await db.getBlocklistSyncInfo();
      final currentVersion = syncInfo['version'] as int;

      if (kDebugMode) {
        print('BlocklistSync: Starting sync from version $currentVersion');
      }

      final url = '$pbBaseUrl/api/blocklist?since=$currentVersion';
      final response = await callPhoneBlockApi(url, authToken: authToken);

      if (response.statusCode != 200) {
        if (kDebugMode) {
          print('BlocklistSync: Server returned ${response.statusCode}');
        }
        return false;
      }

      final blocklist = api.Blocklist.fromString(response.body);
      if (blocklist == null) {
        if (kDebugMode) {
          print('BlocklistSync: Failed to parse response');
        }
        return false;
      }

      int upserted = 0;
      int deleted = 0;

      for (final entry in blocklist.numbers) {
        if (entry.votes > 0) {
          await db.upsertBlocklistEntry(
            entry.phone,
            entry.votes,
            entry.rating.name,
            entry.lastActivity,
          );
          upserted++;
        } else {
          await db.deleteBlocklistEntry(entry.phone);
          deleted++;
        }
      }

      await db.updateBlocklistSyncInfo(
        blocklist.version,
        DateTime.now().millisecondsSinceEpoch,
      );

      if (kDebugMode) {
        print('BlocklistSync: Done. Upserted $upserted, deleted $deleted. '
            'New version: ${blocklist.version}');
      }

      return true;
    } catch (e) {
      if (kDebugMode) {
        print('BlocklistSync: Error during sync: $e');
      }
      return false;
    }
  }

  /// Generates a random sync offset between 0 and 24 hours in milliseconds.
  static int generateRandomOffset() {
    return Random().nextInt(24 * 60 * 60 * 1000);
  }
}
```

**Step 2: Verify it compiles**

Note: The `api.Blocklist` class needs `version` field and `BlockListEntry` needs `lastActivity` field (from Task 1). The `rating.name` getter should return the enum name string. Verify that `api.Rating` enum values map correctly to the server's rating strings (e.g., `Rating.gFraud` → `"G_FRAUD"`). If the enum `.name` gives camelCase, use the serialization method instead.

```bash
cd phoneblock_mobile
flutter analyze
```

**Step 3: Commit**

```bash
git add phoneblock_mobile/lib/blocklist_sync_service.dart
git commit -m "feat(#264): add BlocklistSyncService for incremental blocklist sync"
```

---

### Task 4: Add workmanager dependency and background sync registration

**Files:**
- Modify: `phoneblock_mobile/pubspec.yaml`
- Modify: `phoneblock_mobile/lib/main.dart`

**Step 1: Add workmanager dependency**

In `pubspec.yaml`, add to the `dependencies` section:

```yaml
workmanager: ^0.5.2
```

Run:
```bash
cd phoneblock_mobile
flutter pub get
```

**Step 2: Add WorkManager initialization and callback in main.dart**

At the top of `main.dart`, add import:

```dart
import 'package:workmanager/workmanager.dart';
import 'package:phoneblock_mobile/blocklist_sync_service.dart';
```

Before the `main()` function, add the top-level callback dispatcher (WorkManager requirement):

```dart
/// WorkManager background task callback.
///
/// Must be a top-level function. Called by Android WorkManager when
/// a scheduled background task fires.
@pragma('vm:entry-point')
void callbackDispatcher() {
  Workmanager().executeTask((task, inputData) async {
    if (task == 'blocklistSync') {
      return await BlocklistSyncService.instance.sync();
    }
    return Future.value(true);
  });
}
```

In the `main()` function (around line 534), after the WidgetsFlutterBinding initialization, add WorkManager initialization:

```dart
await Workmanager().initialize(callbackDispatcher, isInDebugMode: kDebugMode);
```

**Step 3: Register periodic sync after login**

Add a helper function (near the other top-level utility functions):

```dart
/// Registers the daily blocklist sync task with WorkManager.
///
/// Uses a randomized initial delay (0-24h) to distribute server load
/// across all clients. The offset is persisted so the same device
/// always syncs at roughly the same time of day.
Future<void> registerBlocklistSync() async {
  final db = ScreenedCallsDatabase.instance;
  final syncInfo = await db.getBlocklistSyncInfo();
  var offset = syncInfo['syncOffset'] as int;

  if (offset == 0) {
    offset = BlocklistSyncService.generateRandomOffset();
    await db.setBlocklistSyncOffset(offset);
  }

  await Workmanager().registerPeriodicTask(
    'blocklistSync',
    'blocklistSync',
    frequency: const Duration(hours: 24),
    initialDelay: Duration(milliseconds: offset),
    constraints: Constraints(networkType: NetworkType.connected),
    existingWorkPolicy: ExistingWorkPolicy.keep,
  );
}
```

**Step 4: Trigger initial sync + register periodic sync after login completes**

In `VerifyLoginState.initState()` (line 2696), after the token verification succeeds (inside the `.then()` at line 2703), add the initial sync trigger:

```dart
checkResult = callPhoneBlockApi(pbApiTest, authToken: token).then((response) async {
  if (response.statusCode == 200) {
    final localeSettings = await getDeviceLocaleSettings();
    await updateAccountSettings(token, localeSettings);

    // Trigger initial blocklist sync and register periodic sync
    BlocklistSyncService.instance.sync();
    registerBlocklistSync();
  }
  return response;
});
```

**Step 5: Trigger sync on app start for migration case**

In `_AppLauncherState._checkSetupState()` (line 838), after determining the user has a valid token (line 857), trigger sync if needed:

```dart
if (hasValidToken && hasPermission) {
  // Trigger blocklist sync if cache is empty (migration or first run)
  final syncInfo = await ScreenedCallsDatabase.instance.getBlocklistSyncInfo();
  if ((syncInfo['version'] as int) == 0) {
    BlocklistSyncService.instance.sync();
    registerBlocklistSync();
  }
  context.go('/main');
}
```

**Step 6: Verify it compiles**

```bash
cd phoneblock_mobile
flutter analyze
```

**Step 7: Commit**

```bash
git add phoneblock_mobile/pubspec.yaml phoneblock_mobile/pubspec.lock phoneblock_mobile/lib/main.dart
git commit -m "feat(#264): add WorkManager background sync for blocklist cache"
```

---

### Task 5: Add local blocklist lookup to CallChecker.java

**Files:**
- Modify: `phoneblock_mobile/android/app/src/main/java/de/haumacher/phoneblock_mobile/CallChecker.java`

**Step 1: Add SQLite import**

Add at the top of CallChecker.java (after existing imports):

```java
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
```

**Step 2: Add blocklist lookup method**

Add a new method to `CallChecker` (after the `acceptCall` method at line 270):

```java
/**
 * Looks up a phone number in the local blocklist cache.
 *
 * @param number The normalized phone number in E.164 format.
 * @return The vote count if found, -1 if not found or on error.
 */
private int lookupLocalBlocklist(String number) {
    try {
        java.io.File dbFile = getDatabasePath("screened_calls.db");
        if (!dbFile.exists()) {
            return -1;
        }

        SQLiteDatabase db = SQLiteDatabase.openDatabase(
            dbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        try {
            Cursor cursor = db.rawQuery(
                "SELECT votes FROM blocklist WHERE phone = ?",
                new String[]{number});
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getInt(0);
                }
                return -1;
            } finally {
                cursor.close();
            }
        } finally {
            db.close();
        }
    } catch (Exception e) {
        Log.w(CallChecker.class.getName(), "Error looking up local blocklist", e);
        return -1;
    }
}
```

**Step 3: Insert local blocklist check after wildcard check**

After the wildcard prefix loop ends (line 115, after the catch block), insert the local blocklist lookup before the API query:

```java
// Check local blocklist cache before server query
int localVotes = lookupLocalBlocklist(number);
if (localVotes >= minVotes) {
    Log.d(CallChecker.class.getName(), "onScreenCall: Blocking call by local blocklist: " + number + " with " + localVotes + " votes");
    respondToCall(callDetails, new CallResponse.Builder()
        .setDisallowCall(true)
        .setRejectCall(true)
        .setSkipCallLog(true)
        .setSkipNotification(true)
        .build());
    MainActivity.reportScreenedCall(CallChecker.this, rawNumber, true, localVotes, 0, null, null, null);
    return;
}
```

**Step 4: Verify it compiles**

```bash
cd phoneblock_mobile
flutter build apk --debug 2>&1 | tail -5
```

**Step 5: Commit**

```bash
git add phoneblock_mobile/android/app/src/main/java/de/haumacher/phoneblock_mobile/CallChecker.java
git commit -m "feat(#264): add local blocklist lookup in CallChecker before API fallback"
```

---

### Task 6: Add i18n strings for blocklist cache settings

**Files:**
- Modify: `phoneblock_mobile/lib/l10n/app_de.arb` (source language only)

**Step 1: Add German strings**

Add the following entries to `app_de.arb` (before the closing `}`):

```json
"blocklistCache": "Blocklist-Cache",
"@blocklistCache": {
  "description": "Blocklist cache section header in settings"
},
"blocklistCachedEntries": "{count} Nummern",
"@blocklistCachedEntries": {
  "description": "Number of cached blocklist entries",
  "placeholders": {
    "count": {
      "type": "int"
    }
  }
},
"blocklistLastSync": "Letzte Synchronisierung",
"@blocklistLastSync": {
  "description": "Label for last sync time"
},
"blocklistLastSyncNever": "Nie",
"@blocklistLastSyncNever": {
  "description": "Shown when blocklist has never been synced"
},
"blocklistLastSyncAgo": "vor {timeAgo}",
"@blocklistLastSyncAgo": {
  "description": "Relative time since last blocklist sync",
  "placeholders": {
    "timeAgo": {
      "type": "String"
    }
  }
},
"blocklistVersion": "Version",
"@blocklistVersion": {
  "description": "Label for blocklist version number"
},
"blocklistSyncNow": "Jetzt synchronisieren",
"@blocklistSyncNow": {
  "description": "Button to trigger manual blocklist sync"
},
"blocklistSyncing": "Synchronisiere...",
"@blocklistSyncing": {
  "description": "Shown while blocklist sync is in progress"
},
"blocklistSyncSuccess": "Blocklist-Synchronisierung erfolgreich",
"@blocklistSyncSuccess": {
  "description": "Snackbar message after successful manual sync"
},
"blocklistSyncFailed": "Blocklist-Synchronisierung fehlgeschlagen",
"@blocklistSyncFailed": {
  "description": "Snackbar message after failed manual sync"
}
```

**Step 2: Generate Dart localization code**

```bash
cd phoneblock_mobile
flutter gen-l10n
```

**Step 3: Translate to other languages**

```bash
cd phoneblock_mobile
./gradlew translateArb
flutter gen-l10n
```

If `translateArb` requires a DeepL API key that isn't configured, skip this step — the translations can be generated later.

**Step 4: Verify it compiles**

```bash
cd phoneblock_mobile
flutter analyze
```

**Step 5: Commit**

```bash
git add phoneblock_mobile/lib/l10n/
git commit -m "feat(#264): add i18n strings for blocklist cache settings"
```

---

### Task 7: Add blocklist cache section to settings screen

**Files:**
- Modify: `phoneblock_mobile/lib/main.dart` (SettingsScreen, starting at line 3078)

**Step 1: Add state variables**

In `_SettingsScreenState` (line 3085), add new state variables:

```dart
int _blocklistCount = 0;
int _blocklistVersion = 0;
int _blocklistLastSync = 0;
bool _isSyncing = false;
```

**Step 2: Load blocklist cache info in `_loadSettings`**

At the end of the `try` block in `_loadSettings()` (before `setState`, around line 3123), add:

```dart
final syncInfo = await ScreenedCallsDatabase.instance.getBlocklistSyncInfo();
final blocklistCount = await ScreenedCallsDatabase.instance.getBlocklistCount();
```

Then inside the `setState` call, add:

```dart
_blocklistCount = blocklistCount;
_blocklistVersion = syncInfo['version'] as int;
_blocklistLastSync = syncInfo['lastSyncTime'] as int;
```

**Step 3: Add manual sync method**

Add to `_SettingsScreenState`:

```dart
/// Triggers a manual blocklist sync and updates the UI.
Future<void> _syncBlocklist(BuildContext context) async {
  setState(() {
    _isSyncing = true;
  });

  final success = await BlocklistSyncService.instance.sync();

  if (!context.mounted) return;

  if (success) {
    final syncInfo = await ScreenedCallsDatabase.instance.getBlocklistSyncInfo();
    final blocklistCount = await ScreenedCallsDatabase.instance.getBlocklistCount();
    setState(() {
      _blocklistCount = blocklistCount;
      _blocklistVersion = syncInfo['version'] as int;
      _blocklistLastSync = syncInfo['lastSyncTime'] as int;
      _isSyncing = false;
    });
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(context.l10n.blocklistSyncSuccess)),
    );
  } else {
    setState(() {
      _isSyncing = false;
    });
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(context.l10n.blocklistSyncFailed),
        backgroundColor: Colors.red,
      ),
    );
  }
}
```

**Step 4: Add helper for relative time formatting**

Add to `_SettingsScreenState`:

```dart
/// Formats a timestamp as a human-readable relative time string.
String _formatLastSync(BuildContext context, int timestampMs) {
  if (timestampMs == 0) return context.l10n.blocklistLastSyncNever;
  final now = DateTime.now();
  final syncTime = DateTime.fromMillisecondsSinceEpoch(timestampMs);
  final diff = now.difference(syncTime);

  String timeAgo;
  if (diff.inDays > 0) {
    timeAgo = '${diff.inDays}d';
  } else if (diff.inHours > 0) {
    timeAgo = '${diff.inHours}h';
  } else if (diff.inMinutes > 0) {
    timeAgo = '${diff.inMinutes}min';
  } else {
    timeAgo = '${diff.inSeconds}s';
  }
  return context.l10n.blocklistLastSyncAgo(timeAgo);
}
```

**Step 5: Add blocklist cache section to build method**

In the `build()` method's `ListView` children (line 3374), add a new section **before** the Statistics section (before line 3552). Insert before `const Divider()` at line 3551:

```dart
const Divider(),
Padding(
  padding: const EdgeInsets.all(16.0),
  child: Text(
    context.l10n.blocklistCache,
    style: const TextStyle(
      fontSize: 14,
      fontWeight: FontWeight.bold,
      color: Colors.grey,
    ),
  ),
),
ListTile(
  leading: const Icon(Icons.storage),
  title: Text(context.l10n.blocklistCachedEntries(_blocklistCount)),
  subtitle: Text('${context.l10n.blocklistLastSync}: ${_formatLastSync(context, _blocklistLastSync)}'),
  trailing: _blocklistVersion > 0 ? Text('v$_blocklistVersion') : null,
),
ListTile(
  leading: _isSyncing
    ? const SizedBox(width: 24, height: 24, child: CircularProgressIndicator(strokeWidth: 2))
    : const Icon(Icons.sync),
  title: Text(_isSyncing ? context.l10n.blocklistSyncing : context.l10n.blocklistSyncNow),
  onTap: _isSyncing ? null : () => _syncBlocklist(context),
),
```

**Step 6: Add required imports at the top of main.dart**

Ensure `blocklist_sync_service.dart` is imported:

```dart
import 'package:phoneblock_mobile/blocklist_sync_service.dart';
```

**Step 7: Verify it compiles**

```bash
cd phoneblock_mobile
flutter analyze
```

**Step 8: Commit**

```bash
git add phoneblock_mobile/lib/main.dart
git commit -m "feat(#264): add blocklist cache info section to settings screen"
```

---

### Task 8: Manual testing

**Step 1: Run the app**

```bash
cd phoneblock_mobile
flutter run
```

**Step 2: Test initial sync**

- If logged in: Open Settings, verify the Blocklist Cache section shows "0 Nummern" and "Nie"
- Tap "Jetzt synchronisieren" — verify it syncs and updates the count, version, and last sync time
- Close and reopen Settings — verify the cached values persist

**Step 3: Test fresh login flow**

- Clear app data or log out
- Go through the setup wizard and log in
- Navigate to Settings — verify a sync was triggered automatically (count > 0)

**Step 4: Test offline call screening**

- Enable airplane mode (or disconnect from network)
- Call the test device from a known spam number that was in the blocklist
- Verify the call is blocked using the local cache
- Re-enable network and verify the call shows up in the call log

**Step 5: Verify background sync**

- Check WorkManager registration via Android debug tools:
  ```bash
  adb shell dumpsys jobscheduler | grep blocklistSync
  ```

---

### Task 9: Final review and cleanup

**Step 1: Run full analysis**

```bash
cd phoneblock_mobile
flutter analyze
```

**Step 2: Check for `use_build_context_synchronously` warnings**

Ensure all async methods that use `context` after `await` have proper `context.mounted` checks (per CLAUDE.md guidelines).

**Step 3: Verify no hardcoded strings**

All user-visible strings should use `context.l10n.*` — no hardcoded English or German in the Dart code.

**Step 4: Final commit if needed**

```bash
git add -A
git commit -m "fix(#264): address review feedback for blocklist cache"
```
