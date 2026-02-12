import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';
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
    if (ratingStr != null) {
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
      'rating': rating != null ? _ratingToString(rating!) : null,
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
      version: 7,
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
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL
      )
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

  /// Closes the database.
  Future<void> close() async {
    final db = await database;
    await db.close();
  }
}
