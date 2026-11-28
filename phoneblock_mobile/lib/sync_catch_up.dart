import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:phoneblock_mobile/blocklist_sync_service.dart';
import 'package:phoneblock_mobile/logging/app_logger.dart';
import 'package:phoneblock_mobile/main.dart' show getAuthToken;
import 'package:phoneblock_mobile/storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Age of the last successful blocklist sync above which the app catches up in the
/// foreground.
///
/// Well above the 24 h period of the WorkManager task, so a device whose task runs reliably
/// never catches up, and well above the server's limit of one incremental sync per day.
const Duration catchUpAge = Duration(hours: 36);

/// Minimum distance between a failed catch-up and the next one.
const Duration catchUpRetryDelay = Duration(minutes: 15);

/// Whether the app should sync the blocklist itself because the WorkManager task did not.
///
/// [lastSuccess] is the time of the last successful community sync (`null`: never),
/// [lastFailedAttempt] the time of the last failed catch-up (`null`: none). A timestamp in
/// the future (clock was set back) is treated as unknown.
bool shouldCatchUp({
  required bool loggedIn,
  required DateTime? lastSuccess,
  required DateTime? lastFailedAttempt,
  required DateTime now,
}) {
  if (!loggedIn) return false;
  if (lastSuccess != null && !lastSuccess.isAfter(now) && now.difference(lastSuccess) <= catchUpAge) {
    return false;
  }
  if (lastFailedAttempt != null &&
      !lastFailedAttempt.isAfter(now) &&
      now.difference(lastFailedAttempt) < catchUpRetryDelay) {
    return false;
  }
  return true;
}

/// Catches up on the daily blocklist sync when the app starts or returns to the foreground.
///
/// Android may defer the periodic WorkManager task for days (#503). This runs the same sync as
/// the update button ([BlocklistSyncService.sync]) when the last success is older than
/// [catchUpAge], silently and without UI of its own.
class SyncCatchUp {
  static final SyncCatchUp instance = SyncCatchUp();

  /// SharedPreferences key of the time (ms since epoch) of the last failed catch-up.
  static const String failedAtKey = 'blocklist_catch_up_failed_at';

  final Future<bool> Function() _loggedIn;
  final Future<DateTime?> Function() _lastSuccess;
  final Future<DateTime?> Function() _lastFailedAttempt;
  final Future<void> Function(DateTime? time) _setLastFailedAttempt;
  final Future<bool> Function() _sync;
  final DateTime Function() _now;

  Future<void>? _running;

  SyncCatchUp({
    Future<bool> Function()? loggedIn,
    Future<DateTime?> Function()? lastSuccess,
    Future<DateTime?> Function()? lastFailedAttempt,
    Future<void> Function(DateTime? time)? setLastFailedAttempt,
    Future<bool> Function()? sync,
    DateTime Function()? now,
  })  : _loggedIn = loggedIn ?? _hasAuthToken,
        _lastSuccess = lastSuccess ?? _readLastSuccess,
        _lastFailedAttempt = lastFailedAttempt ?? _readLastFailedAttempt,
        _setLastFailedAttempt = setLastFailedAttempt ?? _writeLastFailedAttempt,
        _sync = sync ?? BlocklistSyncService.instance.sync,
        _now = now ?? DateTime.now;

  /// The catch-up sync currently running, if any.
  @visibleForTesting
  Future<void>? get running => _running;

  /// Starts a catch-up sync in the background if one is due; [trigger] names the occasion for
  /// the log.
  ///
  /// Returns whether a sync was started. The returned future completes once the decision is
  /// made, not when the sync is done. Never throws.
  Future<bool> check(String trigger) async {
    if (_running != null) return false;
    try {
      final now = _now();
      final lastSuccess = await _lastSuccess();
      if (!shouldCatchUp(
        loggedIn: await _loggedIn(),
        lastSuccess: lastSuccess,
        lastFailedAttempt: await _lastFailedAttempt(),
        now: now,
      )) {
        return false;
      }
      // A concurrent check may have decided the same while this one was awaiting.
      if (_running != null) return false;

      final age = lastSuccess == null ? 'never' : '${now.difference(lastSuccess).inHours} h ago';
      AppLogger.instance.info('sync', 'catch-up sync on $trigger: last successful sync $age');
      _running = _run().whenComplete(() => _running = null);
      return true;
    } catch (e, s) {
      AppLogger.instance.error('sync', 'catch-up check failed', e, s);
      return false;
    }
  }

  Future<void> _run() async {
    var success = false;
    try {
      success = await _sync();
    } catch (e, s) {
      AppLogger.instance.error('sync', 'catch-up sync crashed', e, s);
    }
    try {
      if (success) {
        await _setLastFailedAttempt(null);
        AppLogger.instance.info('sync', 'catch-up sync succeeded');
      } else {
        await _setLastFailedAttempt(_now());
        AppLogger.instance.warn('sync',
            'catch-up sync failed, next attempt in ${catchUpRetryDelay.inMinutes} min at the earliest');
      }
    } catch (e, s) {
      AppLogger.instance.error('sync', 'catch-up: recording the outcome failed', e, s);
    }
  }

  static Future<bool> _hasAuthToken() async {
    final token = await getAuthToken();
    return token != null && token.isNotEmpty;
  }

  static Future<DateTime?> _readLastSuccess() async {
    // Written only together with the data of a successful sync, see applyBlocklistUpdates().
    final lastSyncTime = (await ScreenedCallsDatabase.instance.getBlocklistSyncInfo())['lastSyncTime'] as int;
    return lastSyncTime == 0 ? null : DateTime.fromMillisecondsSinceEpoch(lastSyncTime);
  }

  static Future<DateTime?> _readLastFailedAttempt() async {
    final prefs = await SharedPreferences.getInstance();
    final millis = prefs.getInt(failedAtKey);
    return millis == null ? null : DateTime.fromMillisecondsSinceEpoch(millis);
  }

  static Future<void> _writeLastFailedAttempt(DateTime? time) async {
    final prefs = await SharedPreferences.getInstance();
    if (time == null) {
      await prefs.remove(failedAtKey);
    } else {
      await prefs.setInt(failedAtKey, time.millisecondsSinceEpoch);
    }
  }
}
