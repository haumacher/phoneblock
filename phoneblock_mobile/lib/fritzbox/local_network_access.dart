import 'package:flutter/services.dart';
import 'package:phoneblock_mobile/l10n/app_localizations.dart';
import 'package:phoneblock_mobile/logging/app_logger.dart';

/// Access of the app to devices in the local network, like the Fritz!Box
/// (issue #660).
///
/// From Android 17 on (target SDK 37), every connection into the local
/// network needs the runtime permission `ACCESS_LOCAL_NETWORK`; without it,
/// TCP connections time out. Before Android 17 the native side reports the
/// access as granted.
///
/// The permission is only requested when the user sets up or opens the
/// Fritz!Box features; users of the mobile call screening alone are never
/// asked.
class LocalNetworkAccess {
  /// The channel to the native side ([MainActivity]).
  static const MethodChannel _channel =
      MethodChannel('de.haumacher.phoneblock_mobile/call_checker');

  /// The shared instance.
  static LocalNetworkAccess instance = LocalNetworkAccess();

  /// Whether the app may currently talk to devices in the local network.
  ///
  /// Without the native side (e.g. in a background isolate) the access
  /// cannot be checked and is reported as not granted, so that no LAN call
  /// runs into a timeout.
  Future<bool> isGranted() async {
    try {
      return await _channel.invokeMethod<bool>('checkLocalNetworkPermission') ??
          false;
    } on MissingPluginException {
      AppLogger.instance.info('fritzbox',
          'Local network access cannot be checked without the activity, assuming not granted');
      return false;
    }
  }

  /// Shows the system dialog asking for local network access and reports
  /// whether it is granted afterwards.
  ///
  /// If the user has refused the access permanently, the system shows no
  /// dialog and the result is `false` immediately; then only the app's
  /// system settings ([openAppSettings]) can grant it.
  Future<bool> request() async {
    final granted =
        await _channel.invokeMethod<bool>('requestLocalNetworkPermission') ??
            false;
    AppLogger.instance.info(
        'fritzbox', 'Local network access ${granted ? 'granted' : 'denied'}');
    return granted;
  }

  /// Opens the system settings page of the app.
  Future<void> openAppSettings() => _channel.invokeMethod('openAppSettings');

  /// Throws [LocalNetworkAccessDeniedException] if the app may not talk to
  /// devices in the local network.
  ///
  /// The central check before every connection to the Fritz!Box.
  Future<void> ensureGranted() async {
    if (!await isGranted()) {
      throw LocalNetworkAccessDeniedException();
    }
  }
}

/// Thrown instead of a connection timeout when the app may not access the
/// local network (Android 17, permission `ACCESS_LOCAL_NETWORK` missing).
class LocalNetworkAccessDeniedException implements Exception {
  @override
  String toString() => 'No access to the local network (ACCESS_LOCAL_NETWORK not granted)';
}

/// The message for [error] if it is a refused local network access, `null`
/// otherwise.
String? localNetworkDeniedMessage(AppLocalizations l10n, Object error) =>
    error is LocalNetworkAccessDeniedException ? l10n.localNetworkDenied : null;
