import 'package:phoneblock_mobile/api.dart' as api;
import 'package:phoneblock_mobile/logging/app_logger.dart';
import 'package:phoneblock_mobile/main.dart'
    show addWildcardToServer, fetchBlacklist, getAuthToken;
import 'package:phoneblock_mobile/storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// What reconciling the local wildcard store against the server implies.
///
/// Pure data, so the decision can be tested without a database, see
/// [WildcardSyncService.diff].
class WildcardDiff {
  /// Server rules missing from the local store; to be inserted locally.
  final List<api.PersonalizedNumber> toInsert;

  /// Local rules the server no longer has; to be deleted locally.
  final List<WildcardBlock> toDelete;

  /// Local-only rules to be lifted to the server (one-time migration of the app's pre-#377
  /// local wildcards). They are kept locally either way.
  final List<WildcardBlock> toUpload;

  WildcardDiff({required this.toInsert, required this.toDelete, required this.toUpload});

  /// Whether local store and server already agree.
  bool get isEmpty => toInsert.isEmpty && toDelete.isEmpty && toUpload.isEmpty;
}

/// Local side of the wildcard sync: the rules the native call screener reads, plus the
/// one-time migration flag.
abstract class WildcardStore {
  Future<List<WildcardBlock>> loadRules();

  /// Inserts [rule], doing nothing if a rule with the same prefix already exists (another
  /// isolate, e.g. the WorkManager task, may have inserted it concurrently).
  Future<void> insertRule(WildcardBlock rule);

  Future<void> deleteRule(WildcardBlock rule);

  Future<bool> isMigrated();

  Future<void> setMigrated();
}

/// The production [WildcardStore]: the SQLite `wildcard_blocks` table (read directly by
/// `CallChecker` at screening time) and a flag in the Flutter SharedPreferences.
class SqliteWildcardStore implements WildcardStore {
  /// Key of the one-time migration flag: the app had local-only wildcards before the server
  /// learned about them (#377), and those must be lifted up rather than deleted.
  static const String migratedKey = 'wildcards_migrated';

  const SqliteWildcardStore();

  @override
  Future<List<WildcardBlock>> loadRules() => ScreenedCallsDatabase.instance.getAllWildcardBlocks();

  @override
  Future<void> insertRule(WildcardBlock rule) =>
      ScreenedCallsDatabase.instance.insertWildcardBlockIfAbsent(rule);

  @override
  Future<void> deleteRule(WildcardBlock rule) async {
    await ScreenedCallsDatabase.instance.deleteWildcardBlock(rule.id!);
  }

  @override
  Future<bool> isMigrated() async {
    final prefs = await SharedPreferences.getInstance();
    // The flag may have been set by the other isolate (foreground app vs. WorkManager task);
    // a stale cached `false` would re-upload a rule the user meanwhile deleted on the server.
    await prefs.reload();
    return prefs.getBool(migratedKey) ?? false;
  }

  @override
  Future<void> setMigrated() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(migratedKey, true);
  }
}

/// Keeps the user's personal wildcard blocks (#377) in sync between the server and the local
/// SQLite store that the native call screener reads.
///
/// The server is the source of truth: rules created on phoneblock.net or on another device must
/// reach this device's screening, and rules deleted there must disappear from it. The local copy
/// exists so screening works without a server round-trip.
///
/// Runs on app start, with every blocklist sync (manual and the daily WorkManager task in a
/// background isolate) and when the blacklist screen opens. A rule entered on the website used
/// to take effect only after the user opened that screen (#487). Nothing here needs an Activity
/// or a MethodChannel.
class WildcardSyncService {
  static final WildcardSyncService instance = WildcardSyncService();

  final WildcardStore _store;
  final Future<api.NumberList?> Function(String authToken) _fetch;
  final Future<bool> Function(String prefix, String authToken) _upload;
  final Future<String?> Function() _authToken;

  /// Serializes runs within this isolate, so concurrent triggers (app start, manual sync and
  /// the blacklist screen) do not race on the same rows.
  Future<void> _tail = Future.value();

  WildcardSyncService({
    WildcardStore store = const SqliteWildcardStore(),
    Future<api.NumberList?> Function(String authToken) fetch = fetchBlacklist,
    Future<bool> Function(String prefix, String authToken) upload = addWildcardToServer,
    Future<String?> Function() authToken = getAuthToken,
  })  : _store = store,
        _fetch = fetch,
        _upload = upload,
        _authToken = authToken;

  /// Computes what to change, given the server's rules and the local ones.
  ///
  /// Before the migration has completed, local-only rules are uploaded and kept; afterwards a
  /// rule missing on the server means the user deleted it somewhere, so it goes.
  static WildcardDiff diff({
    required List<api.PersonalizedNumber> server,
    required List<WildcardBlock> local,
    required bool migrated,
  }) {
    final serverPrefixes = server.map((w) => w.phone).toSet();
    final localPrefixes = local.map((w) => w.prefix).toSet();

    final toInsert = <api.PersonalizedNumber>[];
    final seen = <String>{};
    for (final w in server) {
      if (!localPrefixes.contains(w.phone) && seen.add(w.phone)) {
        toInsert.add(w);
      }
    }
    final localOnly = local.where((w) => !serverPrefixes.contains(w.prefix)).toList();

    return WildcardDiff(
      toInsert: toInsert,
      toDelete: migrated ? localOnly : [],
      toUpload: migrated ? [] : localOnly,
    );
  }

  /// Fetches the user's blacklist and reconciles its wildcard rules into the local store.
  ///
  /// Returns `true` if the reconciliation ran. Failures are logged, never thrown.
  Future<bool> sync() => _serialized(_sync);

  Future<bool> _sync() async {
    try {
      final authToken = await _authToken();
      if (authToken == null || authToken.isEmpty) {
        AppLogger.instance.info('sync', 'wildcard sync: no auth token, skipping');
        return false;
      }
      final numberList = await _fetch(authToken);
      if (numberList == null) {
        AppLogger.instance.warn('sync', 'wildcard sync: blacklist unavailable, keeping local rules');
        return false;
      }
      await _reconcile(numberList.numbers.where((n) => n.wildcard).toList(), authToken);
      return true;
    } catch (e, s) {
      AppLogger.instance.error('sync', 'wildcard sync failed', e, s);
      return false;
    }
  }

  /// Reconciles the given server wildcard rules into the local store.
  ///
  /// Returns the local rules after reconciliation, ready for display.
  Future<List<WildcardBlock>> reconcile(
      List<api.PersonalizedNumber> serverWildcards, String authToken) =>
      _serialized(() => _reconcile(serverWildcards, authToken));

  /// Runs [body] after all previously started runs have finished.
  ///
  /// [sync] fetches inside the critical section: a server snapshot taken while another run is
  /// still uploading migrated rules would lack them and, once that run marks the migration
  /// done, cause their local deletion.
  Future<T> _serialized<T>(Future<T> Function() body) {
    final result = _tail.then((_) => body());
    _tail = result.then((_) {}, onError: (_) {});
    return result;
  }

  Future<List<WildcardBlock>> _reconcile(
      List<api.PersonalizedNumber> serverWildcards, String authToken) async {
    final migrated = await _store.isMigrated();

    final plan = diff(
      server: serverWildcards,
      local: await _store.loadRules(),
      migrated: migrated,
    );

    for (final w in plan.toInsert) {
      await _store.insertRule(WildcardBlock(
        prefix: w.phone,
        comment: null,
        created: w.created > 0 ? DateTime.fromMillisecondsSinceEpoch(w.created) : DateTime.now(),
      ));
    }
    for (final w in plan.toDelete) {
      await _store.deleteRule(w);
    }

    // The migration is only complete once every local-only rule is on the server. A rule whose
    // upload failed stays local and is retried on the next run: deleting it (which is what a
    // completed migration implies for a rule the server lacks) would silently lose it.
    var allUploaded = true;
    for (final w in plan.toUpload) {
      if (!await _upload(w.prefix, authToken)) {
        allUploaded = false;
        AppLogger.instance.warn('sync', 'wildcard migration: ${w.prefix}* not uploaded, retrying on next sync');
      }
    }
    if (!migrated && allUploaded) {
      await _store.setMigrated();
      AppLogger.instance.info('sync', 'wildcard migration complete (uploaded=${plan.toUpload.length})');
    }

    if (!plan.isEmpty) {
      AppLogger.instance.info('sync',
          'wildcard sync done (added=${plan.toInsert.length}, removed=${plan.toDelete.length}, uploaded=${plan.toUpload.length})');
    }
    return _store.loadRules();
  }
}
