import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';

/// Represents a screened call record stored in the database.
class ScreenedCall {
  final int? id;
  final String phoneNumber;
  final DateTime timestamp;
  final bool wasBlocked; // true if blocked as SPAM, false if accepted as legitimate
  final int votes; // Number of votes from PhoneBlock database

  ScreenedCall({
    this.id,
    required this.phoneNumber,
    required this.timestamp,
    required this.wasBlocked,
    required this.votes,
  });

  /// Converts database map to ScreenedCall object.
  factory ScreenedCall.fromMap(Map<String, dynamic> map) {
    return ScreenedCall(
      id: map['id'] as int?,
      phoneNumber: map['phoneNumber'] as String,
      timestamp: DateTime.fromMillisecondsSinceEpoch(map['timestamp'] as int),
      wasBlocked: (map['wasBlocked'] as int) == 1,
      votes: map['votes'] as int,
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
    };
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
      version: 1,
      onCreate: _createDB,
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
        votes INTEGER NOT NULL
      )
    ''');

    // Create index on timestamp for efficient sorting
    await db.execute('''
      CREATE INDEX idx_screened_calls_timestamp
      ON screened_calls(timestamp DESC)
    ''');
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
