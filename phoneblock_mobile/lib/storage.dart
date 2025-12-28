import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';
import 'package:phoneblock_mobile/state.dart';

/// Represents a screened call record stored in the database.
class ScreenedCall {
  final int? id;
  final String phoneNumber;
  final DateTime timestamp;
  final bool wasBlocked; // true if blocked as SPAM, false if accepted as legitimate
  final int votes; // Number of votes from PhoneBlock database
  final Rating? rating; // The type of spam (e.g., PING, POLL, ADVERTISING, etc.)

  ScreenedCall({
    this.id,
    required this.phoneNumber,
    required this.timestamp,
    required this.wasBlocked,
    required this.votes,
    this.rating,
  });

  /// Converts database map to ScreenedCall object.
  factory ScreenedCall.fromMap(Map<String, dynamic> map) {
    Rating? rating;
    final ratingStr = map['rating'] as String?;
    if (ratingStr != null) {
      rating = _parseRating(ratingStr);
    }

    return ScreenedCall(
      id: map['id'] as int?,
      phoneNumber: map['phoneNumber'] as String,
      timestamp: DateTime.fromMillisecondsSinceEpoch(map['timestamp'] as int),
      wasBlocked: (map['wasBlocked'] as int) == 1,
      votes: map['votes'] as int,
      rating: rating,
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
      'rating': rating != null ? _ratingToString(rating!) : null,
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
      version: 2,
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
        rating TEXT
      )
    ''');

    // Create index on timestamp for efficient sorting
    await db.execute('''
      CREATE INDEX idx_screened_calls_timestamp
      ON screened_calls(timestamp DESC)
    ''');
  }

  /// Upgrades the database schema.
  Future<void> _upgradeDB(Database db, int oldVersion, int newVersion) async {
    if (oldVersion < 2) {
      // Add rating column in version 2
      await db.execute('ALTER TABLE screened_calls ADD COLUMN rating TEXT');
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

  /// Gets the count of screened calls.
  Future<int> getScreenedCallsCount() async {
    final db = await database;
    final result = await db.rawQuery('SELECT COUNT(*) as count FROM screened_calls');
    return Sqflite.firstIntValue(result) ?? 0;
  }

  /// Closes the database.
  Future<void> close() async {
    final db = await database;
    await db.close();
  }
}
