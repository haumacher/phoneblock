import 'package:phoneblock_mobile/api.dart' as api;
import 'package:phoneblock_mobile/logging/app_logger.dart';
import 'package:phoneblock_mobile/main.dart'
    show addWildcardToServer, fetchBlacklist, syncWildcardPrefixesToNative;
import 'package:phoneblock_mobile/storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// What reconciling the local wildcard store against the server implies.
///
/// Pure data so the decision can be tested without a database — see
/// [WildcardSyncService.diff].
class WildcardDiff {
  /// Server rules missing from the local store; to be inserted.
  final List<api.PersonalizedNumber> toInsert;

  /// Local rules the server no longer has; to be deleted.
  final List<WildcardBlock> toDelete;

  /// Local-only rules to be lifted to the server (one-time migration of the
  /// app's pre-#377 local wildcards). They are kept locally either way.
  final List<WildcardBlock> toUpload;

  WildcardDiff({required this.toInsert, required this.toDelete, required this.toUpload});
}

/// Keeps the user's personal wildcard blocks (#377) in sync between the server, the local
/// SQLite store and the native call-screening service.
///
/// The server is the source of truth: rules created on phoneblock.net or on another device
/// must reach this device's screening service, and rules deleted there must disappear from it.
/// The local copy exists only so screening works offline.
///
/// Runs on every blocklist sync and when the app starts, not only when the blacklist screen
/// happens to be opened — a rule the user entered on the website used to have no effect until
/// they navigated to that screen (#487).
class WildcardSyncService {
  static final WildcardSyncService instance = WildcardSyncService._();

  WildcardSyncService._();

  /// Key of the one-time migration flag: the app had local-only wildcards before the server
  /// learned about them (#377), and those must be lifted up rather than deleted.
  static const String migratedKey = 'wildcards_migrated';

  /// Computes what to change locally, given the server's rules and the local ones.
  ///
  /// Before the migration has run, local-only rules are uploaded and kept; afterwards a rule
  /// missing on the server means the user deleted it somewhere, so it goes.
  static WildcardDiff diff({
    required List<api.PersonalizedNumber> server,
    required List<WildcardBlock> local,
    required bool migrated,
  }) {
    final serverPrefixes = server.map((w) => w.phone).toSet();
    final localPrefixes = local.map((w) => w.prefix).toSet();

    return WildcardDiff(
      toInsert: server.where((w) => !localPrefixes.contains(w.phone)).toList(),
      toDelete: migrated ? local.where((w) => !serverPrefixes.contains(w.prefix)).toList() : [],
      toUpload: migrated ? [] : local.where((w) => !serverPrefixes.contains(w.prefix)).toList(),
    );
  }

  /// Fetches the user's blacklist and reconciles its wildcard rules.
  ///
  /// Returns `true` if the reconciliation ran (the list could be fetched).
  Future<bool> sync(String authToken) async {
    try {
      final numberList = await fetchBlacklist(authToken);
      if (numberList == null) {
        AppLogger.instance.info('sync', 'wildcard sync: blacklist unavailable, skipping');
        return false;
      }
      await reconcile(numberList.numbers.where((n) => n.wildcard).toList(), authToken);
      return true;
    } catch (e, s) {
      AppLogger.instance.error('sync', 'wildcard sync failed', e, s);
      return false;
    }
  }

  /// Reconciles the given server wildcard rules into the local store and hands the resulting
  /// prefixes to the native screening service.
  ///
  /// Returns the local rules after reconciliation, ready for display.
  Future<List<WildcardBlock>> reconcile(
      List<api.PersonalizedNumber> serverWildcards, String authToken) async {
    final db = ScreenedCallsDatabase.instance;
    final prefs = await SharedPreferences.getInstance();
    final migrated = prefs.getBool(migratedKey) ?? false;

    final plan = WildcardSyncService.diff(
      server: serverWildcards,
      local: await db.getAllWildcardBlocks(),
      migrated: migrated,
    );

    for (final w in plan.toInsert) {
      await db.insertWildcardBlock(WildcardBlock(
        prefix: w.phone,
        comment: null,
        created: w.created > 0
            ? DateTime.fromMillisecondsSinceEpoch(w.created)
            : DateTime.now(),
      ));
    }
    for (final w in plan.toDelete) {
      await db.deleteWildcardBlock(w.id!);
    }

    // The migration is only complete once every local-only rule is on the server. A rule whose
    // upload failed stays local and is retried on the next run — deleting it (which is what a
    // completed migration implies for a server-less rule) would silently lose it.
    bool allUploaded = true;
    for (final w in plan.toUpload) {
      if (!await addWildcardToServer(w.prefix, authToken)) {
        allUploaded = false;
        AppLogger.instance.info('sync', 'wildcard ${w.prefix} could not be uploaded, retrying later');
      }
    }
    if (!migrated && allUploaded) {
      await prefs.setBool(migratedKey, true);
    }

    await syncWildcardPrefixesToNative();
    return db.getAllWildcardBlocks();
  }
}
