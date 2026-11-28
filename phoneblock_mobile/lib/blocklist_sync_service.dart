import 'dart:async';
import 'dart:math';

import 'package:http/http.dart' as http;
import 'package:phoneblock_mobile/api.dart' as api;
import 'package:phoneblock_mobile/logging/app_logger.dart';
import 'package:phoneblock_mobile/main.dart' show callPhoneBlockApi, getAuthToken, pbBaseUrl;
import 'package:phoneblock_mobile/storage.dart';
import 'package:phoneblock_mobile/wildcard_sync_service.dart';

/// Local side of the blocklist sync: the `blocklist` cache the native call screener reads, and
/// the `blocklist_sync` metadata.
abstract class BlocklistStore {
  /// The sync metadata, see [ScreenedCallsDatabase.getBlocklistSyncInfo].
  Future<Map<String, dynamic>> getSyncInfo();

  /// Applies [entries] and records [version] as the new local version, atomically.
  ///
  /// With [replace], the cache is first cleared and the sync counter restarts (full resync).
  /// Returns the number of upserted and deleted entries.
  Future<(int upserted, int deleted)> apply(List<api.BlockListEntry> entries, int version,
      {required bool replace});

  /// Restarts the count towards the next full resync, leaving cache and version alone.
  Future<void> restartFullResyncCycle();
}

/// The production [BlocklistStore] backed by [ScreenedCallsDatabase].
class SqliteBlocklistStore implements BlocklistStore {
  const SqliteBlocklistStore();

  @override
  Future<Map<String, dynamic>> getSyncInfo() => ScreenedCallsDatabase.instance.getBlocklistSyncInfo();

  @override
  Future<(int, int)> apply(List<api.BlockListEntry> entries, int version, {required bool replace}) async {
    int upserted = 0;
    int deleted = 0;
    await ScreenedCallsDatabase.instance.applyBlocklistUpdates(
      entries,
      (entry) => BlocklistSyncService.ratingToString(entry.rating),
      (u, d) { upserted = u; deleted = d; },
      version,
      replace: replace,
    );
    return (upserted, deleted);
  }

  @override
  Future<void> restartFullResyncCycle() => ScreenedCallsDatabase.instance.resetBlocklistSyncCount();
}

/// Fetches `GET /api/blocklist?since=<since>` with the given bearer token.
typedef BlocklistFetch = Future<http.Response> Function(int since, String authToken);

Future<http.Response> _fetchBlocklist(int since, String authToken) =>
    callPhoneBlockApi('$pbBaseUrl/api/blocklist?since=$since', authToken: authToken);

/// Service for syncing the community blocklist to the local SQLite cache.
///
/// Uses incremental sync via `GET /api/blocklist?since=<version>` to minimize
/// data transfer. Entries with `votes > 0` are upserted, entries with
/// `votes == 0` are deleted (server signals removal).
class BlocklistSyncService {
  static final BlocklistSyncService instance = BlocklistSyncService();

  /// Number of incremental syncs after which a full resync corrects accumulated drift.
  static const int fullResyncInterval = 40;

  final BlocklistStore _store;
  final BlocklistFetch _fetch;
  final Future<String?> Function() _authToken;
  final Future<bool> Function() _wildcards;

  /// The sync currently running in this isolate, joined by every further [sync] call.
  Future<bool>? _running;

  final StreamController<void> _synced = StreamController<void>.broadcast();

  /// Creates a service; the parameters replace the production store, server access and
  /// wildcard sync in tests.
  BlocklistSyncService({
    BlocklistStore store = const SqliteBlocklistStore(),
    BlocklistFetch fetch = _fetchBlocklist,
    Future<String?> Function() authToken = getAuthToken,
    Future<bool> Function()? wildcards,
  })  : _store = store,
        _fetch = fetch,
        _authToken = authToken,
        _wildcards = wildcards ?? WildcardSyncService.instance.sync;

  /// Fires after each successful community blocklist sync of this isolate, whoever triggered
  /// it (update button, login, catch-up), so screens showing the sync date/version can refresh.
  Stream<void> get onSynced => _synced.stream;

  /// Converts a [api.Rating] enum value to its server-format string
  /// (e.g., `Rating.gFraud` -> `"G_FRAUD"`).
  static String ratingToString(api.Rating rating) {
    switch (rating) {
      case api.Rating.aLegitimate: return "A_LEGITIMATE";
      case api.Rating.bMissed: return "B_MISSED";
      case api.Rating.cPing: return "C_PING";
      case api.Rating.dPoll: return "D_POLL";
      case api.Rating.eAdvertising: return "E_ADVERTISING";
      case api.Rating.fGamble: return "F_GAMBLE";
      case api.Rating.gFraud: return "G_FRAUD";
    }
  }

  /// Performs an incremental blocklist sync, followed by the sync of the user's personal
  /// wildcard rules.
  ///
  /// Reads the current version from `blocklist_sync`, fetches changes from
  /// the server, applies them to the local `blocklist` table, and updates
  /// the sync metadata. Every [fullResyncInterval] syncs, the complete list is fetched
  /// instead and replaces the cache - only once it has arrived completely. A failed full
  /// fetch leaves cache and version untouched and restarts the count, so the next attempt is
  /// incremental again.
  ///
  /// The wildcard rules (#377) ride along on the same schedule, since
  /// screening needs them too (#487); they are synced even if the community sync failed,
  /// and their outcome does not affect the result.
  ///
  /// A call while a sync is already running (e.g. the update button during a catch-up sync)
  /// joins that run instead of sending a second request.
  ///
  /// Returns `true` if the community blocklist sync succeeded, `false` otherwise.
  Future<bool> sync() {
    return _running ??= _sync().whenComplete(() => _running = null);
  }

  Future<bool> _sync() async {
    final result = await _syncCommunity();
    if (result) {
      _synced.add(null);
    }
    await _wildcards();
    return result;
  }

  Future<bool> _syncCommunity() async {
    AppLogger.instance.info('sync', 'blocklist sync started');
    var needsFullResync = false;
    try {
      final authToken = await _authToken();
      if (authToken == null || authToken.isEmpty) {
        AppLogger.instance.info('sync', 'BlocklistSync: No auth token, skipping sync');
        return false;
      }

      final syncInfo = await _store.getSyncInfo();
      final currentVersion = syncInfo['version'] as int;
      final syncCount = syncInfo['syncCount'] as int;

      // Trigger a full resync every 40 syncs to correct accumulated drift.
      needsFullResync = syncCount >= fullResyncInterval;
      if (needsFullResync) {
        AppLogger.instance.info('sync', 'BlocklistSync: syncCount=$syncCount, triggering full resync');
      }

      final version = needsFullResync ? 0 : currentVersion;

      AppLogger.instance.info('sync', 'BlocklistSync: Starting sync from version $version');

      final response = await _fetch(version, authToken);

      if (response.statusCode != 200) {
        AppLogger.instance.error('sync', 'BlocklistSync: Server returned ${response.statusCode}');
        return await _fullResyncFailed(needsFullResync);
      }

      final blocklist = api.Blocklist.fromString(response.body);
      if (blocklist == null) {
        AppLogger.instance.error('sync', 'BlocklistSync: Failed to parse response');
        return await _fullResyncFailed(needsFullResync);
      }

      final (upserted, deleted) = await _store.apply(blocklist.numbers, blocklist.version,
          replace: needsFullResync);

      AppLogger.instance.info('sync', 'blocklist sync done (${needsFullResync ? 'full, ' : ''}upserted=$upserted, deleted=$deleted, version=${blocklist.version})');
      return true;
    } catch (e, s) {
      AppLogger.instance.error('sync', 'blocklist sync failed', e, s);
      return await _fullResyncFailed(needsFullResync);
    }
  }

  /// After a failed full resync, the next attempts are incremental again: a full download is
  /// only due after another [fullResyncInterval] syncs, so retries (the catch-up, the update
  /// button) never repeat the expensive full fetch. Returns `false`, the sync's result.
  Future<bool> _fullResyncFailed(bool fullResync) async {
    if (!fullResync) return false;
    try {
      await _store.restartFullResyncCycle();
      AppLogger.instance.info('sync', 'BlocklistSync: full resync failed, keeping the cached list, next full resync in $fullResyncInterval syncs');
    } catch (e, s) {
      AppLogger.instance.error('sync', 'BlocklistSync: restarting the full resync cycle failed', e, s);
    }
    return false;
  }

  /// Generates a random sync offset between 0 and 24 hours in milliseconds.
  static int generateRandomOffset() {
    return Random().nextInt(24 * 60 * 60 * 1000);
  }
}
