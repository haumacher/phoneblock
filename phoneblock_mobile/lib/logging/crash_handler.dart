import 'package:flutter/foundation.dart';

import 'app_logger.dart';

/// Wires Flutter framework and platform-dispatcher errors into [AppLogger].
class CrashHandler {
  static bool _installed = false;

  static void install() {
    if (_installed) return;
    _installed = true;

    FlutterError.onError = (FlutterErrorDetails details) {
      AppLogger.instance.error(
        'flutter',
        details.exceptionAsString(),
        details.exception,
        details.stack,
      );
      FlutterError.presentError(details);
    };

    PlatformDispatcher.instance.onError = (Object error, StackTrace stack) {
      AppLogger.instance.error('platform', error.toString(), error, stack);
      return true;
    };
  }
}
