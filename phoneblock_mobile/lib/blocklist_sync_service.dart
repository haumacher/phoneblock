import 'dart:math';

import 'package:flutter/foundation.dart';
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

  /// Converts a [api.Rating] enum value to its server-format string
  /// (e.g., `Rating.gFraud` -> `"G_FRAUD"`).
  static String _ratingToString(api.Rating rating) {
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

      await db.applyBlocklistUpdates(
        blocklist.numbers,
        (entry) => _ratingToString(entry.rating),
        (u, d) { upserted = u; deleted = d; },
        blocklist.version,
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
