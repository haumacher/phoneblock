import 'dart:convert';
import 'dart:ffi';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite/sqflite.dart';

/// Runs the sqflite plugin channel against the system SQLite library
/// (`libsqlite3.so.0`) through `dart:ffi`, so that tests use the app's real
/// database code - schema, migrations and queries - on a real database file.
///
/// Supports the plugin methods the app uses: open/close, execute, query,
/// insert, update, batch and the databases path.
class SqliteChannel {
  static const _channel = MethodChannel('com.tekartik.sqflite');

  /// Directory for the database files of this channel.
  final Directory dir;

  final Map<int, Pointer<Void>> _dbs = {};
  int _nextId = 1;

  SqliteChannel(this.dir);

  /// Installs the channel handler.
  void install() {
    // Plugins are not registered in tests: use the plugin factory, which
    // talks to the channel.
    if (databaseFactoryOrNull != databaseFactorySqflitePlugin) {
      databaseFactory = databaseFactorySqflitePlugin;
    }
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_channel, _handle);
  }

  /// Removes the channel handler and closes all databases.
  void uninstall() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_channel, null);
    for (final db in _dbs.values) {
      _lib.close(db);
    }
    _dbs.clear();
  }

  /// Runs [sql] on the database file [path] outside the app, e.g. to build a
  /// database of an older schema version.
  static void runOnFile(String path, List<String> statements) {
    final db = _lib.open(path);
    try {
      for (final sql in statements) {
        _lib.run(db, sql, const []);
      }
    } finally {
      _lib.close(db);
    }
  }

  /// Runs a query on the database file [path] outside the app.
  static List<Map<String, Object?>> queryFile(String path, String sql) {
    final db = _lib.open(path);
    try {
      final r = _lib.run(db, sql, const []);
      return [
        for (final row in r.rows)
          {for (var i = 0; i < r.columns.length; i++) r.columns[i]: row[i]},
      ];
    } finally {
      _lib.close(db);
    }
  }

  Future<Object?> _handle(MethodCall call) async {
    final args = (call.arguments as Map?)?.cast<String, Object?>() ?? const {};
    switch (call.method) {
      case 'getDatabasesPath':
        return dir.path;
      case 'options':
      case 'debugMode':
        return null;
      case 'databaseExists':
        return File(args['path'] as String).existsSync();
      case 'deleteDatabase':
        final f = File(args['path'] as String);
        if (f.existsSync()) f.deleteSync();
        return null;
      case 'openDatabase':
        final id = _nextId++;
        _dbs[id] = _lib.open(args['path'] as String);
        return {'id': id};
      case 'closeDatabase':
        final db = _dbs.remove(args['id']);
        if (db != null) _lib.close(db);
        return null;
      case 'batch':
        final db = _db(args);
        final results = <Object?>[];
        for (final op in (args['operations'] as List).cast<Map>()) {
          final opArgs = op.cast<String, Object?>();
          results.add({'result': _run(db, opArgs['method'] as String, opArgs)});
        }
        return args['noResult'] == true ? null : results;
      default:
        return _run(_db(args), call.method, args);
    }
  }

  Pointer<Void> _db(Map<String, Object?> args) {
    final db = _dbs[args['id']];
    if (db == null) throw PlatformException(code: 'sqlite_error', message: 'database closed');
    return db;
  }

  Object? _run(Pointer<Void> db, String method, Map<String, Object?> args) {
    final sql = args['sql'] as String;
    final params = (args['arguments'] as List?) ?? const [];
    final _Result r;
    try {
      r = _lib.run(db, sql, params);
    } on _SqliteError catch (e) {
      throw PlatformException(
          code: 'sqlite_error', message: '${e.message} ($sql)');
    }
    switch (method) {
      case 'query':
        return {'columns': r.columns, 'rows': r.rows};
      case 'insert':
        return r.changes == 0 ? null : _lib.lastInsertRowid(db);
      case 'update':
        return r.changes;
      default:
        return null;
    }
  }
}

class _SqliteError implements Exception {
  final String message;
  _SqliteError(this.message);

  @override
  String toString() => 'SQLite: $message';
}

class _Result {
  final List<String> columns;
  final List<List<Object?>> rows;
  final int changes;
  _Result(this.columns, this.rows, this.changes);
}

final _Sqlite _lib = _Sqlite();

typedef _OpenC = Int32 Function(Pointer<Uint8>, Pointer<Pointer<Void>>);
typedef _OpenD = int Function(Pointer<Uint8>, Pointer<Pointer<Void>>);
typedef _DbC = Int32 Function(Pointer<Void>);
typedef _DbD = int Function(Pointer<Void>);
typedef _PrepareC = Int32 Function(Pointer<Void>, Pointer<Uint8>, Int32,
    Pointer<Pointer<Void>>, Pointer<Pointer<Uint8>>);
typedef _PrepareD = int Function(Pointer<Void>, Pointer<Uint8>, int,
    Pointer<Pointer<Void>>, Pointer<Pointer<Uint8>>);
typedef _StmtIntC = Int32 Function(Pointer<Void>, Int32);
typedef _StmtIntD = int Function(Pointer<Void>, int);
typedef _BindInt64C = Int32 Function(Pointer<Void>, Int32, Int64);
typedef _BindInt64D = int Function(Pointer<Void>, int, int);
typedef _BindDoubleC = Int32 Function(Pointer<Void>, Int32, Double);
typedef _BindDoubleD = int Function(Pointer<Void>, int, double);
typedef _BindTextC = Int32 Function(
    Pointer<Void>, Int32, Pointer<Uint8>, Int32, IntPtr);
typedef _BindTextD = int Function(Pointer<Void>, int, Pointer<Uint8>, int, int);
typedef _ColInt64C = Int64 Function(Pointer<Void>, Int32);
typedef _ColInt64D = int Function(Pointer<Void>, int);
typedef _ColDoubleC = Double Function(Pointer<Void>, Int32);
typedef _ColDoubleD = double Function(Pointer<Void>, int);
typedef _ColPtrC = Pointer<Uint8> Function(Pointer<Void>, Int32);
typedef _ColPtrD = Pointer<Uint8> Function(Pointer<Void>, int);
typedef _ErrmsgC = Pointer<Uint8> Function(Pointer<Void>);
typedef _Int64DbC = Int64 Function(Pointer<Void>);
typedef _Int64DbD = int Function(Pointer<Void>);
typedef _MallocC = Pointer<Void> Function(IntPtr);
typedef _MallocD = Pointer<Void> Function(int);
typedef _FreeC = Void Function(Pointer<Void>);
typedef _FreeD = void Function(Pointer<Void>);

const int _sqliteRow = 100;
const int _sqliteDone = 101;
const int _typeInteger = 1;
const int _typeFloat = 2;
const int _typeText = 3;
const int _typeBlob = 4;

/// SQLITE_TRANSIENT: SQLite copies bound text immediately.
const int _transient = -1;

class _Sqlite {
  final DynamicLibrary _so = DynamicLibrary.open('libsqlite3.so.0');
  final DynamicLibrary _c = DynamicLibrary.process();

  late final _OpenD _open = _so.lookupFunction<_OpenC, _OpenD>('sqlite3_open');
  late final _DbD _close = _so.lookupFunction<_DbC, _DbD>('sqlite3_close_v2');
  late final _PrepareD _prepare =
      _so.lookupFunction<_PrepareC, _PrepareD>('sqlite3_prepare_v2');
  late final _DbD _step = _so.lookupFunction<_DbC, _DbD>('sqlite3_step');
  late final _DbD _finalize = _so.lookupFunction<_DbC, _DbD>('sqlite3_finalize');
  late final _DbD _columnCount =
      _so.lookupFunction<_DbC, _DbD>('sqlite3_column_count');
  late final _ColPtrD _columnName =
      _so.lookupFunction<_ColPtrC, _ColPtrD>('sqlite3_column_name');
  late final _StmtIntD _columnType =
      _so.lookupFunction<_StmtIntC, _StmtIntD>('sqlite3_column_type');
  late final _ColInt64D _columnInt64 =
      _so.lookupFunction<_ColInt64C, _ColInt64D>('sqlite3_column_int64');
  late final _ColDoubleD _columnDouble =
      _so.lookupFunction<_ColDoubleC, _ColDoubleD>('sqlite3_column_double');
  late final _ColPtrD _columnText =
      _so.lookupFunction<_ColPtrC, _ColPtrD>('sqlite3_column_text');
  late final _ColPtrD _columnBlob =
      _so.lookupFunction<_ColPtrC, _ColPtrD>('sqlite3_column_blob');
  late final _StmtIntD _columnBytes =
      _so.lookupFunction<_StmtIntC, _StmtIntD>('sqlite3_column_bytes');
  late final _StmtIntD _bindNull =
      _so.lookupFunction<_StmtIntC, _StmtIntD>('sqlite3_bind_null');
  late final _BindInt64D _bindInt64 =
      _so.lookupFunction<_BindInt64C, _BindInt64D>('sqlite3_bind_int64');
  late final _BindDoubleD _bindDouble =
      _so.lookupFunction<_BindDoubleC, _BindDoubleD>('sqlite3_bind_double');
  late final _BindTextD _bindText =
      _so.lookupFunction<_BindTextC, _BindTextD>('sqlite3_bind_text');
  late final _BindTextD _bindBlob =
      _so.lookupFunction<_BindTextC, _BindTextD>('sqlite3_bind_blob');
  late final Pointer<Uint8> Function(Pointer<Void>) _errmsg =
      _so.lookupFunction<_ErrmsgC, _ErrmsgC>('sqlite3_errmsg');
  late final _DbD _changes = _so.lookupFunction<_DbC, _DbD>('sqlite3_changes');
  late final _Int64DbD _lastInsertRowid =
      _so.lookupFunction<_Int64DbC, _Int64DbD>('sqlite3_last_insert_rowid');
  late final _MallocD _malloc = _c.lookupFunction<_MallocC, _MallocD>('malloc');
  late final _FreeD _free = _c.lookupFunction<_FreeC, _FreeD>('free');

  Pointer<Uint8> _bytes(List<int> data, {bool nul = false}) {
    final p = _malloc(data.length + (nul ? 1 : 0) + 1).cast<Uint8>();
    p.asTypedList(data.length).setAll(0, data);
    if (nul) p[data.length] = 0;
    return p;
  }

  Pointer<Uint8> _cString(String s) => _bytes(utf8.encode(s), nul: true);

  String _readCString(Pointer<Uint8> p) {
    if (p == nullptr) return '';
    var n = 0;
    while (p[n] != 0) {
      n++;
    }
    return utf8.decode(p.asTypedList(n));
  }

  Pointer<Void> open(String path) {
    final name = _cString(path);
    final out = _malloc(sizeOf<Pointer<Void>>()).cast<Pointer<Void>>();
    try {
      final rc = _open(name, out);
      final db = out.value;
      if (rc != 0) {
        final msg = _readCString(_errmsg(db));
        _close(db);
        throw _SqliteError('open $path: $msg');
      }
      return db;
    } finally {
      _free(name.cast());
      _free(out.cast());
    }
  }

  void close(Pointer<Void> db) => _close(db);

  int lastInsertRowid(Pointer<Void> db) => _lastInsertRowid(db);

  /// Runs all statements in [sql] (binding [params] to the first one) and
  /// returns the rows of the last statement that produced columns.
  _Result run(Pointer<Void> db, String sql, List<Object?> params) {
    final sqlBytes = utf8.encode(sql);
    final start = _bytes(sqlBytes, nul: true);
    final stmtOut = _malloc(sizeOf<Pointer<Void>>()).cast<Pointer<Void>>();
    final tailOut = _malloc(sizeOf<Pointer<Uint8>>()).cast<Pointer<Uint8>>();
    final toFree = <Pointer<Void>>[];
    var columns = <String>[];
    var rows = <List<Object?>>[];
    var changes = 0;
    var first = true;
    try {
      var cursor = start;
      while (true) {
        final remaining = sqlBytes.length - (cursor.address - start.address);
        if (remaining <= 0) break;
        if (_prepare(db, cursor, remaining, stmtOut, tailOut) != 0) {
          throw _SqliteError(_readCString(_errmsg(db)));
        }
        final stmt = stmtOut.value;
        cursor = tailOut.value;
        if (stmt == nullptr) continue; // whitespace or comment
        try {
          if (first) {
            for (var i = 0; i < params.length; i++) {
              _bind(stmt, i + 1, params[i], toFree);
            }
            first = false;
          }
          final count = _columnCount(stmt);
          final names = [
            for (var i = 0; i < count; i++) _readCString(_columnName(stmt, i)),
          ];
          final stmtRows = <List<Object?>>[];
          while (true) {
            final rc = _step(stmt);
            if (rc == _sqliteDone) break;
            if (rc != _sqliteRow) throw _SqliteError(_readCString(_errmsg(db)));
            stmtRows.add([for (var i = 0; i < count; i++) _column(stmt, i)]);
          }
          if (count > 0) {
            columns = names;
            rows = stmtRows;
          }
          changes = _changes(db);
        } finally {
          _finalize(stmt);
        }
      }
      return _Result(columns, rows, changes);
    } finally {
      _free(start.cast());
      _free(stmtOut.cast());
      _free(tailOut.cast());
      for (final p in toFree) {
        _free(p);
      }
    }
  }

  void _bind(Pointer<Void> stmt, int index, Object? value, List<Pointer<Void>> toFree) {
    if (value == null) {
      _bindNull(stmt, index);
    } else if (value is bool) {
      _bindInt64(stmt, index, value ? 1 : 0);
    } else if (value is int) {
      _bindInt64(stmt, index, value);
    } else if (value is double) {
      _bindDouble(stmt, index, value);
    } else if (value is Uint8List) {
      final p = _bytes(value);
      toFree.add(p.cast());
      _bindBlob(stmt, index, p, value.length, _transient);
    } else {
      final bytes = utf8.encode(value.toString());
      final p = _bytes(bytes);
      toFree.add(p.cast());
      _bindText(stmt, index, p, bytes.length, _transient);
    }
  }

  Object? _column(Pointer<Void> stmt, int i) {
    switch (_columnType(stmt, i)) {
      case _typeInteger:
        return _columnInt64(stmt, i);
      case _typeFloat:
        return _columnDouble(stmt, i);
      case _typeText:
        final p = _columnText(stmt, i);
        return utf8.decode(p.asTypedList(_columnBytes(stmt, i)));
      case _typeBlob:
        final p = _columnBlob(stmt, i);
        final n = _columnBytes(stmt, i);
        return n == 0 ? Uint8List(0) : Uint8List.fromList(p.asTypedList(n));
      default:
        return null;
    }
  }
}
