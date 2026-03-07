import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';
import 'package:phoneblock_mobile/api.dart' as api;
import 'package:phoneblock_mobile/state.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_models.dart';

/// Represents a screened call record stored in the database.
/// Unified storage for both mobile screened calls and Fritz!Box calls.
class ScreenedCall {
  final int? id;
  final String phoneNumber;
  final DateTime timestamp;
  final bool wasBlocked; // true if blocked as SPAM, false if accepted as legitimate
  final int votes; // Number of votes from PhoneBlock database
  final int votesWildcard; // Number of range votes (aggregated from similar numbers)
  final Rating? rating; // The type of spam (e.g., PING, POLL, ADVERTISING, etc.)
  final String? label; // Formatted phone number for display (e.g., "(DE) 030 12345678")
  final String? location; // City or region where the call originated (e.g., "Berlin")
  final bool isWildcardBlocked; // true if blocked by a local wildcard prefix rule

  // Unified source tracking
  final CallSource source; // Where the call came from (mobile or fritzbox)

  // Fritz!Box specific fields
  final int? duration; // Call duration in seconds
  final String? device; // Device that handled the call (e.g., "Telefon 1")
  final String? fritzboxId; // Unique ID from Fritz!Box call list
  final FritzBoxCallType? callType; // Type of call (incoming, missed, outgoing, etc.)

  ScreenedCall({
    this.id,
    required this.phoneNumber,
    required this.timestamp,
    required this.wasBlocked,
    required this.votes,
    this.votesWildcard = 0,
    this.rating,
    this.label,
    this.location,
    this.isWildcardBlocked = false,
    this.source = CallSource.mobile,
    this.duration,
    this.device,
    this.fritzboxId,
    this.callType,
  });

  /// Returns true if this call has spam indicators.
  bool get hasSpamIndicators => votes > 0 || votesWildcard > 0;

  /// Returns the display name for this call.
  String get displayName => label ?? phoneNumber;

  /// Converts database map to ScreenedCall object.
  factory ScreenedCall.fromMap(Map<String, dynamic> map) {
    Rating? rating;
    final ratingStr = map['rating'] as String?;
    final isWildcard = ratingStr == 'WILDCARD';
    if (ratingStr != null && !isWildcard) {
      rating = _parseRating(ratingStr);
    }

    // Parse source
    final sourceStr = map['source'] as String?;
    final source = sourceStr == 'fritzbox' ? CallSource.fritzbox : CallSource.mobile;

    // Parse call type for Fritz!Box calls
    FritzBoxCallType? callType;
    final callTypeInt = map['callType'] as int?;
    if (callTypeInt != null) {
      callType = FritzBoxCallType.fromCode(callTypeInt);
    }

    return ScreenedCall(
      id: map['id'] as int?,
      phoneNumber: map['phoneNumber'] as String,
      timestamp: DateTime.fromMillisecondsSinceEpoch(map['timestamp'] as int),
      wasBlocked: (map['wasBlocked'] as int) == 1,
      votes: map['votes'] as int,
      votesWildcard: (map['votesWildcard'] as int?) ?? 0,
      rating: rating,
      label: map['label'] as String?,
      location: map['location'] as String?,
      isWildcardBlocked: isWildcard,
      source: source,
      duration: map['duration'] as int?,
      device: map['device'] as String?,
      fritzboxId: map['fritzboxId'] as String?,
      callType: callType,
    );
  }

  /// Converts ScreenedCall object to database map.
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'phoneNumber': phoneNumber,
      'timestamp': timestamp.millisecondsSinceEpoch,
      'wasBlocked': wasBlocked ? 1 : 0,
      'votes': votes,
      'votesWildcard': votesWildcard,
      'rating': isWildcardBlocked ? 'WILDCARD' : (rating != null ? _ratingToString(rating!) : null),
      'label': label,
      'location': location,
      'source': source.name,
      'duration': duration,
      'device': device,
      'fritzboxId': fritzboxId,
      'callType': callType?.code,
    };
  }

  /// Converts Rating enum to string for storage.
  static String _ratingToString(Rating rating) {
    switch (rating) {
      case Rating.aLEGITIMATE: return 'A_LEGITIMATE';
      case Rating.uNKNOWN: return 'UNKNOWN';
      case Rating.pING: return 'PING';
      case Rating.pOLL: return 'POLL';
      case Rating.aDVERTISING: return 'ADVERTISING';
      case Rating.gAMBLE: return 'GAMBLE';
      case Rating.fRAUD: return 'FRAUD';
    }
  }

  /// Parses string from database to Rating enum.
  static Rating? _parseRating(String ratingStr) {
    switch (ratingStr) {
      case 'A_LEGITIMATE': return Rating.aLEGITIMATE;
      case 'UNKNOWN': return Rating.uNKNOWN;
      case 'PING': return Rating.pING;
      case 'POLL': return Rating.pOLL;
      case 'ADVERTISING': return Rating.aDVERTISING;
      case 'GAMBLE': return Rating.gAMBLE;
      case 'FRAUD': return Rating.fRAUD;
      default: return null;
    }
  }
}

/// A locally stored wildcard blocking rule.
///
/// Blocks all phone numbers starting with [prefix] during call screening.
/// Stored only on the device (not synced to the server).
class WildcardBlock {
  final int? id;

  /// International format prefix, e.g. "+43", "+491234".
  final String prefix;

  /// Optional user comment explaining the rule.
  final String? comment;

  /// When this rule was created (Unix milliseconds).
  final DateTime created;

  WildcardBlock({
    this.id,
    required this.prefix,
    this.comment,
    required this.created,
  });

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'prefix': prefix,
      'comment': comment,
      'created': created.millisecondsSinceEpoch,
    };
  }

  factory WildcardBlock.fromMap(Map<String, dynamic> map) {
    return WildcardBlock(
      id: map['id'] as int?,
      prefix: map['prefix'] as String,
      comment: map['comment'] as String?,
      created: DateTime.fromMillisecondsSinceEpoch(map['created'] as int),
    );
  }
}

/// Database helper for storing and retrieving screened calls.
class ScreenedCallsDatabase {
  static final ScreenedCallsDatabase instance = ScreenedCallsDatabase._init();
  static Database? _database;

  ScreenedCallsDatabase._init();

  /// Gets the database instance, creating it if necessary.
  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _initDB('screened_calls.db');
    return _database!;
  }

  /// Initializes the database and creates tables.
  Future<Database> _initDB(String filePath) async {
    final dbPath = await getDatabasesPath();
    final path = join(dbPath, filePath);

    return await openDatabase(
      path,
      version: 10,
      onCreate: _createDB,
      onUpgrade: _upgradeDB,
    );
  }

  /// Creates the database schema.
  Future<void> _createDB(Database db, int version) async {
    await db.execute('''
      CREATE TABLE screened_calls (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        phoneNumber TEXT NOT NULL,
        timestamp INTEGER NOT NULL,
        wasBlocked INTEGER NOT NULL,
        votes INTEGER NOT NULL,
        votesWildcard INTEGER NOT NULL DEFAULT 0,
        rating TEXT,
        label TEXT,
        location TEXT,
        source TEXT NOT NULL DEFAULT 'mobile',
        duration INTEGER,
        device TEXT,
        fritzboxId TEXT UNIQUE,
        callType INTEGER
      )
    ''');

    // Create index on timestamp for efficient sorting
    await db.execute('''
      CREATE INDEX idx_screened_calls_timestamp
      ON screened_calls(timestamp DESC)
    ''');

    // Create index on fritzboxId for duplicate checking
    await db.execute('''
      CREATE INDEX idx_screened_calls_fritzbox_id
      ON screened_calls(fritzboxId)
    ''');

    // Fritz!Box configuration table (single row)
    await db.execute('''
      CREATE TABLE fritzbox_config (
        id INTEGER PRIMARY KEY CHECK (id = 1),
        host TEXT,
        fritzos_version TEXT,
        username TEXT,
        password TEXT,
        blocklist_mode TEXT DEFAULT 'none',
        answerbot_enabled INTEGER DEFAULT 0,
        answerbot_id INTEGER,
        last_fetch_timestamp INTEGER,
        blocklist_version TEXT,
        phonebook_id TEXT,
        sip_device_id TEXT,
        sip_username TEXT,
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL
      )
    ''');

    // Wildcard blocking rules table (local only)
    await db.execute('''
      CREATE TABLE wildcard_blocks (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        prefix TEXT NOT NULL UNIQUE,
        comment TEXT,
        created INTEGER NOT NULL
      )
    ''');

    // Local blocklist cache table
    await db.execute('''
      CREATE TABLE blocklist (
        phone TEXT PRIMARY KEY,
        votes INTEGER NOT NULL,
        rating TEXT,
        lastActivity INTEGER NOT NULL DEFAULT 0
      )
    ''');

    // Blocklist sync metadata table (single row)
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

  /// Upgrades the database schema.
  Future<void> _upgradeDB(Database db, int oldVersion, int newVersion) async {
    if (oldVersion < 2) {
      // Add rating column in version 2
      await db.execute('ALTER TABLE screened_calls ADD COLUMN rating TEXT');
    }
    if (oldVersion < 3) {
      // Add votesWildcard column in version 3
      await db.execute('ALTER TABLE screened_calls ADD COLUMN votesWildcard INTEGER NOT NULL DEFAULT 0');
    }
    if (oldVersion < 4) {
      // Add label and location columns in version 4
      await db.execute('ALTER TABLE screened_calls ADD COLUMN label TEXT');
      await db.execute('ALTER TABLE screened_calls ADD COLUMN location TEXT');
    }
    if (oldVersion < 5) {
      // Add Fritz!Box config table in version 5
      await db.execute('''
        CREATE TABLE fritzbox_config (
          id INTEGER PRIMARY KEY CHECK (id = 1),
          host TEXT,
          fritzos_version TEXT,
          username TEXT,
          password TEXT,
          blocklist_mode TEXT DEFAULT 'none',
          answerbot_enabled INTEGER DEFAULT 0,
          last_fetch_timestamp INTEGER,
          blocklist_version TEXT,
          phonebook_id TEXT,
          sip_device_id TEXT,
          created_at INTEGER NOT NULL,
          updated_at INTEGER NOT NULL
        )
      ''');

      // Note: In v5, fritzbox_calls was created but is now unified in v6
      // For users upgrading from v4, we skip creating fritzbox_calls
      // and go directly to the unified schema in v6
    }
    if (oldVersion < 6) {
      // Version 6: Unify call storage - add Fritz!Box fields to screened_calls
      await db.execute('ALTER TABLE screened_calls ADD COLUMN source TEXT NOT NULL DEFAULT \'mobile\'');
      await db.execute('ALTER TABLE screened_calls ADD COLUMN duration INTEGER');
      await db.execute('ALTER TABLE screened_calls ADD COLUMN device TEXT');
      await db.execute('ALTER TABLE screened_calls ADD COLUMN fritzboxId TEXT');
      await db.execute('ALTER TABLE screened_calls ADD COLUMN callType INTEGER');

      // Create unique index on fritzboxId for duplicate checking
      // (SQLite doesn't allow UNIQUE constraint in ALTER TABLE ADD COLUMN)
      await db.execute('''
        CREATE UNIQUE INDEX idx_screened_calls_fritzbox_id
        ON screened_calls(fritzboxId)
        WHERE fritzboxId IS NOT NULL
      ''');

      // Migrate data from fritzbox_calls if it exists (users upgrading from v5)
      try {
        final tables = await db.rawQuery(
          "SELECT name FROM sqlite_master WHERE type='table' AND name='fritzbox_calls'"
        );
        if (tables.isNotEmpty) {
          // Migrate existing Fritz!Box calls to screened_calls
          await db.execute('''
            INSERT INTO screened_calls (
              phoneNumber, timestamp, wasBlocked, votes, votesWildcard,
              rating, label, location, source, duration, device, fritzboxId, callType
            )
            SELECT
              phone_number, timestamp, 0, votes, votes_wildcard,
              rating, label, location, 'fritzbox', duration, device, fritzbox_id, call_type
            FROM fritzbox_calls
          ''');

          // Drop the old fritzbox_calls table
          await db.execute('DROP TABLE fritzbox_calls');
        }
      } catch (e) {
        // Table doesn't exist, nothing to migrate
      }
    }
    if (oldVersion < 7) {
      // Add answerbot_id column for tracking server-side bot ID
      await db.execute('ALTER TABLE fritzbox_config ADD COLUMN answerbot_id INTEGER');
    }
    if (oldVersion < 8) {
      // Add sip_username column for identifying the SIP device by username
      await db.execute('ALTER TABLE fritzbox_config ADD COLUMN sip_username TEXT');
    }
    if (oldVersion < 9) {
      // Add wildcard blocking rules table
      await db.execute('''
        CREATE TABLE wildcard_blocks (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          prefix TEXT NOT NULL UNIQUE,
          comment TEXT,
          created INTEGER NOT NULL
        )
      ''');
    }
    if (oldVersion < 10) {
      // Add local blocklist cache table
      await db.execute('''
        CREATE TABLE blocklist (
          phone TEXT PRIMARY KEY,
          votes INTEGER NOT NULL,
          rating TEXT,
          lastActivity INTEGER NOT NULL DEFAULT 0
        )
      ''');

      // Add blocklist sync metadata table
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
  }

  /// Inserts a new screened call record.
  Future<ScreenedCall> insertScreenedCall(ScreenedCall call) async {
    final db = await database;
    final id = await db.insert('screened_calls', call.toMap());
    return ScreenedCall(
      id: id,
      phoneNumber: call.phoneNumber,
      timestamp: call.timestamp,
      wasBlocked: call.wasBlocked,
      votes: call.votes,
      votesWildcard: call.votesWildcard,
      rating: call.rating,
      label: call.label,
      location: call.location,
      isWildcardBlocked: call.isWildcardBlocked,
      source: call.source,
      duration: call.duration,
      device: call.device,
      fritzboxId: call.fritzboxId,
      callType: call.callType,
    );
  }

  /// Retrieves all screened calls, sorted by most recent first.
  Future<List<ScreenedCall>> getAllScreenedCalls() async {
    final db = await database;
    final result = await db.query(
      'screened_calls',
      orderBy: 'timestamp DESC',
    );

    return result.map((map) => ScreenedCall.fromMap(map)).toList();
  }

  /// Retrieves screened calls with pagination.
  Future<List<ScreenedCall>> getScreenedCalls({int limit = 50, int offset = 0}) async {
    final db = await database;
    final result = await db.query(
      'screened_calls',
      orderBy: 'timestamp DESC',
      limit: limit,
      offset: offset,
    );

    return result.map((map) => ScreenedCall.fromMap(map)).toList();
  }

  /// Updates a screened call.
  Future<int> updateScreenedCall(ScreenedCall call) async {
    final db = await database;
    return await db.update(
      'screened_calls',
      call.toMap(),
      where: 'id = ?',
      whereArgs: [call.id],
    );
  }

  /// Updates all screened calls with the given phone number to mark as blocked.
  Future<int> updateCallsByPhoneNumber(String phoneNumber, bool wasBlocked, {Rating? rating}) async {
    final db = await database;
    final updateData = <String, dynamic>{
      'wasBlocked': wasBlocked ? 1 : 0,
    };

    if (rating != null) {
      updateData['rating'] = ScreenedCall._ratingToString(rating);
    }

    return await db.update(
      'screened_calls',
      updateData,
      where: 'phoneNumber = ?',
      whereArgs: [phoneNumber],
    );
  }

  /// Deletes a screened call by ID.
  Future<int> deleteScreenedCall(int id) async {
    final db = await database;
    return await db.delete(
      'screened_calls',
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  /// Deletes all screened calls.
  Future<int> deleteAllScreenedCalls() async {
    final db = await database;
    return await db.delete('screened_calls');
  }

  /// Deletes screened calls older than the specified retention period.
  /// retentionDays: Number of days to keep calls. Use retentionInfinite constant to keep all.
  /// Returns the number of deleted rows.
  Future<int> deleteOldScreenedCalls(int retentionDays) async {
    const retentionInfinite = -1; // Infinite retention constant
    if (retentionDays == retentionInfinite) {
      // Infinite retention, don't delete anything
      return 0;
    }

    final db = await database;
    final cutoffTime = DateTime.now().subtract(Duration(days: retentionDays)).millisecondsSinceEpoch;

    return await db.delete(
      'screened_calls',
      where: 'timestamp < ?',
      whereArgs: [cutoffTime],
    );
  }

  /// Gets the count of screened calls.
  Future<int> getScreenedCallsCount() async {
    final db = await database;
    final result = await db.rawQuery('SELECT COUNT(*) as count FROM screened_calls');
    return Sqflite.firstIntValue(result) ?? 0;
  }

  /// Gets the count of Fritz!Box calls.
  Future<int> getFritzBoxCallsCount() async {
    final db = await database;
    final result = await db.rawQuery(
      "SELECT COUNT(*) as count FROM screened_calls WHERE source = 'fritzbox'"
    );
    return Sqflite.firstIntValue(result) ?? 0;
  }

  /// Deletes all Fritz!Box calls.
  Future<int> deleteFritzBoxCalls() async {
    final db = await database;
    return await db.delete(
      'screened_calls',
      where: "source = ?",
      whereArgs: ['fritzbox'],
    );
  }

  // --- Wildcard blocking rules ---

  /// Inserts a new wildcard blocking rule.
  ///
  /// Returns the inserted [WildcardBlock] with its generated ID.
  /// Throws if a rule with the same [WildcardBlock.prefix] already exists.
  Future<WildcardBlock> insertWildcardBlock(WildcardBlock block) async {
    final db = await database;
    final id = await db.insert('wildcard_blocks', block.toMap(),
        conflictAlgorithm: ConflictAlgorithm.abort);
    return WildcardBlock(
      id: id,
      prefix: block.prefix,
      comment: block.comment,
      created: block.created,
    );
  }

  /// Returns all wildcard blocking rules sorted by prefix.
  Future<List<WildcardBlock>> getAllWildcardBlocks() async {
    final db = await database;
    final maps = await db.query('wildcard_blocks', orderBy: 'prefix ASC');
    return maps.map((m) => WildcardBlock.fromMap(m)).toList();
  }

  /// Returns all wildcard prefixes as a list of strings.
  Future<List<String>> getWildcardPrefixes() async {
    final db = await database;
    final maps = await db.query('wildcard_blocks', columns: ['prefix'], orderBy: 'prefix ASC');
    return maps.map((m) => m['prefix'] as String).toList();
  }

  /// Updates the comment for a wildcard blocking rule.
  Future<int> updateWildcardBlockComment(int id, String comment) async {
    final db = await database;
    return db.update(
      'wildcard_blocks',
      {'comment': comment},
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  /// Deletes a wildcard blocking rule by ID.
  Future<int> deleteWildcardBlock(int id) async {
    final db = await database;
    return db.delete('wildcard_blocks', where: 'id = ?', whereArgs: [id]);
  }

  /// Checks whether a wildcard prefix already exists.
  Future<bool> wildcardPrefixExists(String prefix) async {
    final db = await database;
    final result = await db.query('wildcard_blocks',
        where: 'prefix = ?', whereArgs: [prefix], limit: 1);
    return result.isNotEmpty;
  }

  // --- Blocklist cache ---

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

  /// Applies a batch of blocklist updates in a single transaction.
  ///
  /// Entries with `votes > 0` are upserted, entries with `votes == 0` are
  /// deleted. The sync metadata is updated atomically with the data changes.
  Future<void> applyBlocklistUpdates(
    List<api.BlockListEntry> entries,
    String Function(api.BlockListEntry) ratingToString,
    void Function(int upserted, int deleted) onComplete,
    int newVersion,
  ) async {
    final db = await database;
    int upserted = 0;
    int deleted = 0;

    await db.transaction((txn) async {
      for (final entry in entries) {
        if (entry.votes > 0) {
          await txn.insert('blocklist', {
            'phone': entry.phone,
            'votes': entry.votes,
            'rating': ratingToString(entry),
            'lastActivity': entry.lastActivity,
          }, conflictAlgorithm: ConflictAlgorithm.replace);
          upserted++;
        } else {
          await txn.delete('blocklist',
            where: 'phone = ?', whereArgs: [entry.phone]);
          deleted++;
        }
      }

      await txn.update('blocklist_sync', {
        'version': newVersion,
        'lastSyncTime': DateTime.now().millisecondsSinceEpoch,
      }, where: 'id = 1');
    });

    onComplete(upserted, deleted);
  }

  /// Closes the database.
  Future<void> close() async {
    final db = await database;
    await db.close();
  }
}
