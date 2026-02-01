import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_models.dart';
import 'package:phoneblock_mobile/storage.dart';
import 'package:sqflite/sqflite.dart';

/// Storage keys for secure credential storage.
const _kFritzboxHost = 'fritzbox_host';
const _kFritzboxUsername = 'fritzbox_username';
const _kFritzboxPassword = 'fritzbox_password';

/// Database helper for Fritz!Box configuration and call history.
class FritzBoxStorage {
  static final FritzBoxStorage instance = FritzBoxStorage._init();

  final FlutterSecureStorage _secureStorage = const FlutterSecureStorage(
    aOptions: AndroidOptions(
      encryptedSharedPreferences: true,
    ),
  );

  FritzBoxStorage._init();

  /// Gets the database instance from the main storage.
  Future<Database> get _database async {
    return await ScreenedCallsDatabase.instance.database;
  }

  // ==================== Credential Storage ====================

  /// Saves Fritz!Box credentials securely.
  Future<void> saveCredentials({
    required String host,
    required String username,
    required String password,
  }) async {
    await _secureStorage.write(key: _kFritzboxHost, value: host);
    await _secureStorage.write(key: _kFritzboxUsername, value: username);
    await _secureStorage.write(key: _kFritzboxPassword, value: password);
  }

  /// Retrieves Fritz!Box credentials.
  /// Returns null if credentials are not stored.
  Future<FritzBoxCredentials?> getCredentials() async {
    final host = await _secureStorage.read(key: _kFritzboxHost);
    final username = await _secureStorage.read(key: _kFritzboxUsername);
    final password = await _secureStorage.read(key: _kFritzboxPassword);

    if (host == null || username == null || password == null) {
      return null;
    }

    return FritzBoxCredentials(
      host: host,
      username: username,
      password: password,
    );
  }

  /// Clears all stored credentials.
  Future<void> clearCredentials() async {
    await _secureStorage.delete(key: _kFritzboxHost);
    await _secureStorage.delete(key: _kFritzboxUsername);
    await _secureStorage.delete(key: _kFritzboxPassword);
  }

  /// Returns true if credentials are stored.
  Future<bool> hasCredentials() async {
    final host = await _secureStorage.read(key: _kFritzboxHost);
    return host != null;
  }

  // ==================== Configuration Storage ====================

  /// Gets the current Fritz!Box configuration.
  /// Returns null if no configuration exists.
  Future<FritzBoxConfig?> getConfig() async {
    final db = await _database;
    final result = await db.query(
      'fritzbox_config',
      where: 'id = ?',
      whereArgs: [1],
    );

    if (result.isEmpty) {
      return null;
    }

    return FritzBoxConfig.fromMap(result.first);
  }

  /// Saves or updates the Fritz!Box configuration.
  Future<void> saveConfig(FritzBoxConfig config) async {
    final db = await _database;
    final now = DateTime.now().millisecondsSinceEpoch;
    final map = config.toMap();
    map['updated_at'] = now;

    final existing = await getConfig();
    if (existing == null) {
      map['created_at'] = now;
      await db.insert('fritzbox_config', map);
    } else {
      await db.update(
        'fritzbox_config',
        map,
        where: 'id = ?',
        whereArgs: [1],
      );
    }
  }

  /// Updates specific fields in the configuration.
  Future<void> updateConfig({
    String? host,
    String? fritzosVersion,
    BlocklistMode? blocklistMode,
    bool? answerbotEnabled,
    int? lastFetchTimestamp,
    String? blocklistVersion,
    String? phonebookId,
    String? sipDeviceId,
  }) async {
    final existing = await getConfig();
    final now = DateTime.now().millisecondsSinceEpoch;

    final config = existing?.copyWith(
      host: host,
      fritzosVersion: fritzosVersion,
      blocklistMode: blocklistMode,
      answerbotEnabled: answerbotEnabled,
      lastFetchTimestamp: lastFetchTimestamp,
      blocklistVersion: blocklistVersion,
      phonebookId: phonebookId,
      sipDeviceId: sipDeviceId,
      updatedAt: now,
    ) ?? FritzBoxConfig(
      host: host,
      fritzosVersion: fritzosVersion,
      blocklistMode: blocklistMode ?? BlocklistMode.none,
      answerbotEnabled: answerbotEnabled ?? false,
      lastFetchTimestamp: lastFetchTimestamp,
      blocklistVersion: blocklistVersion,
      phonebookId: phonebookId,
      sipDeviceId: sipDeviceId,
      createdAt: now,
      updatedAt: now,
    );

    await saveConfig(config);
  }

  /// Deletes the Fritz!Box configuration.
  Future<void> deleteConfig() async {
    final db = await _database;
    await db.delete('fritzbox_config', where: 'id = ?', whereArgs: [1]);
  }

  // ==================== Call History Storage ====================

  /// Inserts a new Fritz!Box call record.
  /// Returns the inserted call with its ID.
  Future<FritzBoxCall> insertCall(FritzBoxCall call) async {
    final db = await _database;
    final id = await db.insert(
      'fritzbox_calls',
      call.toMap(),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
    return FritzBoxCall(
      id: id,
      fritzboxId: call.fritzboxId,
      phoneNumber: call.phoneNumber,
      name: call.name,
      timestamp: call.timestamp,
      duration: call.duration,
      callType: call.callType,
      device: call.device,
      votes: call.votes,
      votesWildcard: call.votesWildcard,
      rating: call.rating,
      label: call.label,
      location: call.location,
      syncedAt: call.syncedAt,
    );
  }

  /// Inserts multiple calls in a batch.
  Future<void> insertCalls(List<FritzBoxCall> calls) async {
    final db = await _database;
    final batch = db.batch();
    for (final call in calls) {
      batch.insert(
        'fritzbox_calls',
        call.toMap(),
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
    }
    await batch.commit(noResult: true);
  }

  /// Gets all Fritz!Box calls, sorted by timestamp descending.
  Future<List<FritzBoxCall>> getAllCalls() async {
    final db = await _database;
    final result = await db.query(
      'fritzbox_calls',
      orderBy: 'timestamp DESC',
    );
    return result.map((map) => FritzBoxCall.fromMap(map)).toList();
  }

  /// Gets Fritz!Box calls with pagination.
  Future<List<FritzBoxCall>> getCalls({int limit = 50, int offset = 0}) async {
    final db = await _database;
    final result = await db.query(
      'fritzbox_calls',
      orderBy: 'timestamp DESC',
      limit: limit,
      offset: offset,
    );
    return result.map((map) => FritzBoxCall.fromMap(map)).toList();
  }

  /// Gets calls after a specific timestamp (for incremental sync).
  Future<List<FritzBoxCall>> getCallsSince(DateTime since) async {
    final db = await _database;
    final result = await db.query(
      'fritzbox_calls',
      where: 'timestamp > ?',
      whereArgs: [since.millisecondsSinceEpoch],
      orderBy: 'timestamp DESC',
    );
    return result.map((map) => FritzBoxCall.fromMap(map)).toList();
  }

  /// Updates a call's spam information.
  Future<void> updateCallSpamInfo(
    int callId, {
    int? votes,
    int? votesWildcard,
    String? rating,
    String? label,
    String? location,
  }) async {
    final db = await _database;
    final updates = <String, dynamic>{};

    if (votes != null) updates['votes'] = votes;
    if (votesWildcard != null) updates['votes_wildcard'] = votesWildcard;
    if (rating != null) updates['rating'] = rating;
    if (label != null) updates['label'] = label;
    if (location != null) updates['location'] = location;

    if (updates.isNotEmpty) {
      await db.update(
        'fritzbox_calls',
        updates,
        where: 'id = ?',
        whereArgs: [callId],
      );
    }
  }

  /// Deletes a call by ID.
  Future<int> deleteCall(int id) async {
    final db = await _database;
    return await db.delete(
      'fritzbox_calls',
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  /// Deletes all Fritz!Box calls.
  Future<int> deleteAllCalls() async {
    final db = await _database;
    return await db.delete('fritzbox_calls');
  }

  /// Deletes Fritz!Box calls older than the retention period.
  ///
  /// [retentionDays]: Number of days to keep calls. Use retentionInfinite to keep all.
  /// Returns the number of deleted calls.
  Future<int> deleteOldCalls(int retentionDays) async {
    // Import retentionInfinite constant value (999999)
    if (retentionDays >= 999999) {
      return 0; // Keep all calls
    }

    final db = await _database;
    final cutoffTime = DateTime.now()
        .subtract(Duration(days: retentionDays))
        .millisecondsSinceEpoch;

    return await db.delete(
      'fritzbox_calls',
      where: 'timestamp < ?',
      whereArgs: [cutoffTime],
    );
  }

  /// Gets the count of Fritz!Box calls.
  Future<int> getCallsCount() async {
    final db = await _database;
    final result = await db.rawQuery('SELECT COUNT(*) as count FROM fritzbox_calls');
    return Sqflite.firstIntValue(result) ?? 0;
  }

  /// Gets the most recent call timestamp.
  Future<DateTime?> getMostRecentCallTimestamp() async {
    final db = await _database;
    final result = await db.rawQuery(
      'SELECT MAX(timestamp) as max_ts FROM fritzbox_calls',
    );
    final maxTs = result.first['max_ts'] as int?;
    if (maxTs == null) return null;
    return DateTime.fromMillisecondsSinceEpoch(maxTs);
  }

  /// Checks if a call with the given Fritz!Box ID exists.
  Future<bool> callExists(String fritzboxId) async {
    final db = await _database;
    final result = await db.query(
      'fritzbox_calls',
      columns: ['id'],
      where: 'fritzbox_id = ?',
      whereArgs: [fritzboxId],
    );
    return result.isNotEmpty;
  }
}

/// Represents Fritz!Box login credentials.
class FritzBoxCredentials {
  final String host;
  final String username;
  final String password;

  FritzBoxCredentials({
    required this.host,
    required this.username,
    required this.password,
  });
}
