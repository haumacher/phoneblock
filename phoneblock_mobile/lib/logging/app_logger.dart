import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'caller_source.dart';

/// Singleton wrapper around the native log channel. Safe to use from
/// anywhere, including before [init] has completed (calls are buffered
/// until the channel is ready).
class AppLogger {
  AppLogger._();
  static AppLogger instance = AppLogger._();

  static const _channel = MethodChannel('phoneblock/log');
  static const _maxPending = 50;

  final List<_PendingEntry> _pending = [];
  bool _ready = false;
  String? _logDir;

  Future<void> init() async {
    try {
      _logDir = await _channel.invokeMethod<String>('getLogDir');
    } catch (e) {
      debugPrint('AppLogger.init: getLogDir failed: $e');
    }
    _ready = true;
    for (final entry in _pending) {
      _send(entry);
    }
    _pending.clear();
  }

  String? get logDir => _logDir;

  void info(String tag, String msg) => _log('info', tag, msg);
  void warn(String tag, String msg) => _log('warn', tag, msg);
  void error(String tag, String msg, [Object? error, StackTrace? stack]) =>
      _log('error', tag, msg, error: error, stack: stack);

  void logZoneError(Object error, StackTrace stack) {
    _log('error', 'zone', error.toString(), error: error, stack: stack);
  }

  Future<bool> clear() async {
    try {
      return (await _channel.invokeMethod<bool>('clear')) ?? false;
    } catch (e) {
      debugPrint('AppLogger.clear failed: $e');
      return false;
    }
  }

  void _log(String level, String tag, String msg,
      {Object? error, StackTrace? stack}) {
    final entry = _PendingEntry(
      level: level,
      tag: tag,
      msg: msg,
      src: extractCallerSource(),
      error: error?.toString(),
      stack: stack?.toString(),
    );
    if (!_ready) {
      _pending.add(entry);
      while (_pending.length > _maxPending) {
        _pending.removeAt(0);
      }
      return;
    }
    _send(entry);
  }

  void _send(_PendingEntry e) {
    _channel.invokeMethod<void>('log', {
      'level': e.level,
      'tag': e.tag,
      'msg': e.msg,
      if (e.src != null) 'src': e.src,
      if (e.error != null) 'error': e.error,
      if (e.stack != null) 'stack': e.stack,
    }).catchError((Object err) {
      debugPrint('AppLogger._send failed: $err');
    });
  }

  @visibleForTesting
  static void resetForTest() {
    instance = AppLogger._();
  }
}

class _PendingEntry {
  final String level;
  final String tag;
  final String msg;
  final String? src;
  final String? error;
  final String? stack;
  _PendingEntry({
    required this.level,
    required this.tag,
    required this.msg,
    this.src,
    this.error,
    this.stack,
  });
}
