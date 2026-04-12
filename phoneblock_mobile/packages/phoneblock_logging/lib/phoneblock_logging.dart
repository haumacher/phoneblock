/// Local Flutter plugin that bridges Dart log calls into the native
/// Logback-Android logger. The plugin is Dart-API-less: Dart-side callers
/// talk directly to the MethodChannel `phoneblock/log`. The only reason
/// this plugin exists is to get the native-side `LogBridge` registered
/// automatically on every FlutterEngine created in the Android process
/// (main activity, CallScreeningService, Workmanager background isolate).
library phoneblock_logging;
