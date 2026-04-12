# Mobile App Diagnostic Log — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a local diagnostic log to `phoneblock_mobile` that captures crashes and breadcrumb-style events from both the Flutter (Dart) and Android (Java) sides, with an in-app viewer and share-export.

**Architecture:** Single source of truth on the Android side using Logback-Android with a size-based `RollingFileAppender` in `prudent` mode. Dart forwards log calls through a MethodChannel (`phoneblock/log`) into SLF4J; `CallScreeningService` (separate process) writes directly through SLF4J. Dart-originated calls pass their caller `file:line` via an MDC value so the log pattern shows the real Dart source. In-app viewer reads the files directly from `context.getFilesDir()`; export uses `share_plus`.

**Tech Stack:** Flutter/Dart, Java 17, Logback-Android 3.0.0, SLF4J 2.0.x, `share_plus`, Flutter MethodChannel.

**Reference spec:** `docs/superpowers/specs/2026-04-12-mobile-app-log-design.md`.

**Java package root (existing):** `de.haumacher.phoneblock_mobile`. New classes live in a sub-package `de.haumacher.phoneblock_mobile.log`.

---

## File Structure

**New Java files** (`phoneblock_mobile/android/app/src/main/java/de/haumacher/phoneblock_mobile/log/`):
- `LogContext.java` — one-shot initialization: sets `DATA_DIR` system property and installs the uncaught-exception handler. Called from `MainActivity.onCreate` and `CallChecker.onCreate`.
- `NativeCrashHandler.java` — `Thread.UncaughtExceptionHandler` that logs via SLF4J and delegates to the previous handler.
- `LogSanitizer.java` — `hashPhone(String)` returning `sha1:` + 8 hex chars.
- `LogBridge.java` — `MethodChannel` handler for `phoneblock/log` (methods `log`, `getLogDir`, `clear`).

**New resource:**
- `phoneblock_mobile/android/app/src/main/assets/logback.xml`.

**New Dart files** (`phoneblock_mobile/lib/logging/`):
- `caller_source.dart` — `extractCallerSource()` parses `StackTrace.current` and returns `file:line` outside the `lib/logging/` directory.
- `app_logger.dart` — `AppLogger` singleton with pending buffer + MethodChannel calls.
- `crash_handler.dart` — installs `FlutterError.onError`, `PlatformDispatcher.onError`.
- `log_viewer_screen.dart` — settings-subscreen UI.

**New Dart tests** (`phoneblock_mobile/test/logging/`):
- `caller_source_test.dart`
- `app_logger_test.dart`

**Modified files:**
- `phoneblock_mobile/android/app/build.gradle` — add Logback/SLF4J dependencies.
- `phoneblock_mobile/android/app/src/main/java/de/haumacher/phoneblock_mobile/MainActivity.java` — init `LogContext`, register `LogBridge`.
- `phoneblock_mobile/android/app/src/main/java/de/haumacher/phoneblock_mobile/CallChecker.java` — init `LogContext`, replace `android.util.Log.*` in the screening path with SLF4J, log screening decisions with hashed number.
- `phoneblock_mobile/pubspec.yaml` — add `share_plus`.
- `phoneblock_mobile/lib/main.dart` — call `AppLogger.init` + `CrashHandler.install`, wrap `runApp` in `runZonedGuarded`, add "Diagnoseprotokoll" entry in `SettingsScreen`.
- `phoneblock_mobile/lib/api.dart` — breadcrumb logs on API calls + errors.
- `phoneblock_mobile/lib/blocklist_sync_service.dart` — breadcrumb logs on sync.
- `phoneblock_mobile/lib/storage.dart` — breadcrumb logs on DB open/migration/errors.
- `phoneblock_mobile/lib/l10n/app_de.arb` — new strings for viewer/settings.

---

## Task 1: Add Gradle dependencies for Logback-Android and SLF4J

**Files:**
- Modify: `phoneblock_mobile/android/app/build.gradle` (dependencies block at end of file).

- [ ] **Step 1: Add the two dependency lines**

In the `dependencies { … }` block at the bottom of `build.gradle`, add:

```groovy
dependencies {
    implementation 'com.googlecode.libphonenumber:libphonenumber:8.13.27'
    implementation 'org.slf4j:slf4j-api:2.0.13'
    implementation 'com.github.tony19:logback-android:3.0.0'
    testImplementation 'junit:junit:4.13.2'
}
```

- [ ] **Step 2: Verify Gradle sync succeeds**

Run: `cd phoneblock_mobile && flutter pub get && ./gradlew :app:dependencies --configuration releaseRuntimeClasspath | head -40`
Expected: `slf4j-api` and `logback-android` appear in the tree, no resolution errors.

- [ ] **Step 3: Commit**

```bash
git add phoneblock_mobile/android/app/build.gradle
git commit -m "Add Logback-Android and SLF4J dependencies (#282)"
```

---

## Task 2: Create logback.xml configuration

**Files:**
- Create: `phoneblock_mobile/android/app/src/main/assets/logback.xml`

- [ ] **Step 1: Create the assets directory if missing and write the config**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <property name="LOG_DIR" value="${DATA_DIR:-/data/local/tmp}"/>

    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_DIR}/app.log</file>
        <prudent>true</prudent>

        <rollingPolicy class="ch.qos.logback.core.rolling.FixedWindowRollingPolicy">
            <fileNamePattern>${LOG_DIR}/app.log.%i</fileNamePattern>
            <minIndex>1</minIndex>
            <maxIndex>1</maxIndex>
        </rollingPolicy>

        <triggeringPolicy class="ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy">
            <maxFileSize>512KB</maxFileSize>
        </triggeringPolicy>

        <encoder>
            <pattern>%d{ISO8601} %-5level [%X{src:-%logger{20}:%L}] - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

- [ ] **Step 2: Commit**

```bash
git add phoneblock_mobile/android/app/src/main/assets/logback.xml
git commit -m "Add logback.xml with rolling prudent appender (#282)"
```

---

## Task 3: Create `LogSanitizer` with `hashPhone`

**Files:**
- Create: `phoneblock_mobile/android/app/src/main/java/de/haumacher/phoneblock_mobile/log/LogSanitizer.java`
- Test: `phoneblock_mobile/android/app/src/test/java/de/haumacher/phoneblock_mobile/log/LogSanitizerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package de.haumacher.phoneblock_mobile.log;

import org.junit.Test;
import static org.junit.Assert.*;

public class LogSanitizerTest {
    @Test
    public void hashPhone_returnsSha1PrefixPlusEightHex() {
        String hashed = LogSanitizer.hashPhone("+4930123456");
        assertTrue(hashed.startsWith("sha1:"));
        assertEquals(5 + 8, hashed.length());
        assertTrue(hashed.substring(5).matches("[0-9a-f]{8}"));
    }

    @Test
    public void hashPhone_isDeterministic() {
        assertEquals(
            LogSanitizer.hashPhone("+4930123456"),
            LogSanitizer.hashPhone("+4930123456"));
    }

    @Test
    public void hashPhone_nullReturnsPlaceholder() {
        assertEquals("sha1:-", LogSanitizer.hashPhone(null));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd phoneblock_mobile/android && ./gradlew :app:testDebugUnitTest --tests "*LogSanitizerTest*"`
Expected: FAIL (class `LogSanitizer` missing).

- [ ] **Step 3: Implement `LogSanitizer`**

```java
package de.haumacher.phoneblock_mobile.log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Helpers for sanitising values before they hit the diagnostic log.
 */
public final class LogSanitizer {

    private LogSanitizer() {}

    /**
     * Returns a short, non-reversible marker for a phone number so that
     * call flows can be correlated in the log without leaking the raw number.
     *
     * @param phone The raw phone number, may be null.
     * @return A string of the form {@code sha1:xxxxxxxx}. Returns {@code sha1:-}
     *         when {@code phone} is null.
     */
    public static String hashPhone(String phone) {
        if (phone == null) {
            return "sha1:-";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(phone.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("sha1:");
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "sha1:?";
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd phoneblock_mobile/android && ./gradlew :app:testDebugUnitTest --tests "*LogSanitizerTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add phoneblock_mobile/android/app/src/main/java/de/haumacher/phoneblock_mobile/log/LogSanitizer.java \
        phoneblock_mobile/android/app/src/test/java/de/haumacher/phoneblock_mobile/log/LogSanitizerTest.java
git commit -m "Add LogSanitizer.hashPhone (#282)"
```

---

## Task 4: Create `NativeCrashHandler`

**Files:**
- Create: `phoneblock_mobile/android/app/src/main/java/de/haumacher/phoneblock_mobile/log/NativeCrashHandler.java`

- [ ] **Step 1: Implement the handler**

```java
package de.haumacher.phoneblock_mobile.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs uncaught exceptions through SLF4J and then delegates to the
 * previously installed handler so the Android system still shows its
 * crash dialog / terminates the process.
 */
public final class NativeCrashHandler {

    private static final Logger LOG = LoggerFactory.getLogger(NativeCrashHandler.class);

    private static boolean installed = false;

    private NativeCrashHandler() {}

    /**
     * Installs the handler. Safe to call multiple times; only the first
     * invocation takes effect.
     */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        final Thread.UncaughtExceptionHandler previous =
            Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                LOG.error("Uncaught exception in thread {}", thread.getName(), throwable);
            } catch (Throwable ignored) {
                // Must never mask the original crash.
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
        installed = true;
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd phoneblock_mobile/android && ./gradlew :app:compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add phoneblock_mobile/android/app/src/main/java/de/haumacher/phoneblock_mobile/log/NativeCrashHandler.java
git commit -m "Add NativeCrashHandler with delegation to previous handler (#282)"
```

---

## Task 5: Create `LogContext` one-shot initializer

**Files:**
- Create: `phoneblock_mobile/android/app/src/main/java/de/haumacher/phoneblock_mobile/log/LogContext.java`

- [ ] **Step 1: Implement**

```java
package de.haumacher.phoneblock_mobile.log;

import android.content.Context;
import android.util.Log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One-shot initializer that must be called at the start of every Android
 * process that wants to produce log output. Sets the {@code DATA_DIR}
 * system property that {@code logback.xml} resolves, installs the native
 * crash handler and forces Logback to load.
 *
 * <p>Safe to call multiple times per process; only the first call takes
 * effect.
 */
public final class LogContext {

    private static boolean initialized = false;

    private LogContext() {}

    public static synchronized void init(Context context) {
        if (initialized) {
            return;
        }
        try {
            String dir = context.getFilesDir().getAbsolutePath();
            System.setProperty("DATA_DIR", dir);
            // Force Logback initialization so any errors surface early.
            Logger bootstrap = LoggerFactory.getLogger(LogContext.class);
            bootstrap.info("Logging initialized, DATA_DIR={}", dir);
            NativeCrashHandler.install();
            initialized = true;
        } catch (Throwable t) {
            Log.e("LogContext", "Failed to initialize logging", t);
        }
    }

    /** Returns the log directory, or {@code null} if {@link #init} has not run. */
    public static String getLogDir() {
        return System.getProperty("DATA_DIR");
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd phoneblock_mobile/android && ./gradlew :app:compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add phoneblock_mobile/android/app/src/main/java/de/haumacher/phoneblock_mobile/log/LogContext.java
git commit -m "Add LogContext initializer (#282)"
```

---

## Task 6: Wire `LogContext` into `MainActivity` and `CallChecker`

**Files:**
- Modify: `phoneblock_mobile/android/app/src/main/java/de/haumacher/phoneblock_mobile/MainActivity.java` (add import + call in `configureFlutterEngine`, early in the method)
- Modify: `phoneblock_mobile/android/app/src/main/java/de/haumacher/phoneblock_mobile/CallChecker.java` (add import + call in `onCreate`)

- [ ] **Step 1: Modify `MainActivity`**

At the top of the file, alongside existing imports:

```java
import de.haumacher.phoneblock_mobile.log.LogContext;
```

At the very start of `configureFlutterEngine` (before `super.configureFlutterEngine`):

```java
    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        LogContext.init(getApplicationContext());
        super.configureFlutterEngine(flutterEngine);
        // … existing body …
```

- [ ] **Step 2: Modify `CallChecker.onCreate`**

Add import:

```java
import de.haumacher.phoneblock_mobile.log.LogContext;
```

Update `onCreate`:

```java
    @Override
    public void onCreate() {
        super.onCreate();
        LogContext.init(getApplicationContext());
        _pool = Executors.newScheduledThreadPool(1);
    }
```

- [ ] **Step 3: Verify the app still builds**

Run: `cd phoneblock_mobile && flutter build apk --debug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add phoneblock_mobile/android/app/src/main/java/de/haumacher/phoneblock_mobile/MainActivity.java \
        phoneblock_mobile/android/app/src/main/java/de/haumacher/phoneblock_mobile/CallChecker.java
git commit -m "Initialize LogContext in MainActivity and CallChecker (#282)"
```

---

## Task 7: Create `LogBridge` MethodChannel handler

**Files:**
- Create: `phoneblock_mobile/android/app/src/main/java/de/haumacher/phoneblock_mobile/log/LogBridge.java`

- [ ] **Step 1: Implement**

```java
package de.haumacher.phoneblock_mobile.log;

import android.content.Context;
import android.util.Log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;

import java.io.File;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

/**
 * Bridges Flutter log calls into SLF4J. Registered on the Flutter engine
 * in {@code MainActivity}. Never throws back into Flutter.
 */
public final class LogBridge implements MethodChannel.MethodCallHandler {

    public static final String CHANNEL = "phoneblock/log";

    private final Context appContext;

    public LogBridge(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static void register(BinaryMessenger messenger, Context context) {
        MethodChannel channel = new MethodChannel(messenger, CHANNEL);
        channel.setMethodCallHandler(new LogBridge(context));
    }

    @Override
    public void onMethodCall(MethodCall call, MethodChannel.Result result) {
        try {
            switch (call.method) {
                case "log":
                    handleLog(call);
                    result.success(null);
                    break;
                case "getLogDir":
                    result.success(appContext.getFilesDir().getAbsolutePath());
                    break;
                case "clear":
                    result.success(clearLogs());
                    break;
                default:
                    result.notImplemented();
            }
        } catch (Throwable t) {
            Log.e("LogBridge", "log call failed", t);
            // Never propagate - logging must not crash the app.
            result.success(null);
        }
    }

    private void handleLog(MethodCall call) {
        String levelStr = call.argument("level");
        String tag = call.argument("tag");
        String msg = call.argument("msg");
        String src = call.argument("src");
        String error = call.argument("error");
        String stack = call.argument("stack");

        if (tag == null) tag = "dart";
        Logger log = LoggerFactory.getLogger(tag);

        Level level = parseLevel(levelStr);
        String composed = compose(msg, error, stack);

        if (src != null) {
            MDC.put("src", src);
        }
        try {
            switch (level) {
                case ERROR: log.error(composed); break;
                case WARN:  log.warn(composed); break;
                case INFO:  log.info(composed); break;
                case DEBUG: log.debug(composed); break;
                default:    log.trace(composed);
            }
        } finally {
            MDC.remove("src");
        }
    }

    private static Level parseLevel(String s) {
        if (s == null) return Level.INFO;
        switch (s) {
            case "error": return Level.ERROR;
            case "warn":  return Level.WARN;
            case "info":  return Level.INFO;
            case "debug": return Level.DEBUG;
            case "trace": return Level.TRACE;
            default:      return Level.INFO;
        }
    }

    private static String compose(String msg, String error, String stack) {
        StringBuilder sb = new StringBuilder(msg == null ? "" : msg);
        if (error != null) sb.append(" | error: ").append(error);
        if (stack != null) sb.append('\n').append(stack);
        return sb.toString();
    }

    private boolean clearLogs() {
        File dir = appContext.getFilesDir();
        boolean ok = true;
        ok &= new File(dir, "app.log").delete() || !new File(dir, "app.log").exists();
        ok &= new File(dir, "app.log.1").delete() || !new File(dir, "app.log.1").exists();
        return ok;
    }
}
```

- [ ] **Step 2: Register in `MainActivity.configureFlutterEngine`**

In `MainActivity.java`, add import:

```java
import de.haumacher.phoneblock_mobile.log.LogBridge;
```

After the existing `_channel` setup in `configureFlutterEngine`, add:

```java
        LogBridge.register(flutterEngine.getDartExecutor().getBinaryMessenger(), this);
```

- [ ] **Step 3: Verify build**

Run: `cd phoneblock_mobile && flutter build apk --debug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add phoneblock_mobile/android/app/src/main/java/de/haumacher/phoneblock_mobile/log/LogBridge.java \
        phoneblock_mobile/android/app/src/main/java/de/haumacher/phoneblock_mobile/MainActivity.java
git commit -m "Add LogBridge MethodChannel handler (#282)"
```

---

## Task 8: Add `share_plus` dependency

**Files:**
- Modify: `phoneblock_mobile/pubspec.yaml`

- [ ] **Step 1: Add dependency**

In `dependencies:` (alphabetically near the existing packages):

```yaml
  share_plus: ^10.0.0
```

- [ ] **Step 2: Fetch**

Run: `cd phoneblock_mobile && flutter pub get`
Expected: resolves without conflicts.

- [ ] **Step 3: Commit**

```bash
git add phoneblock_mobile/pubspec.yaml phoneblock_mobile/pubspec.lock
git commit -m "Add share_plus dependency (#282)"
```

---

## Task 9: Dart `caller_source.dart` + test

**Files:**
- Create: `phoneblock_mobile/lib/logging/caller_source.dart`
- Test: `phoneblock_mobile/test/logging/caller_source_test.dart`

- [ ] **Step 1: Write the failing test**

```dart
import 'package:flutter_test/flutter_test.dart';
import 'package:phoneblock_mobile/logging/caller_source.dart';

void main() {
  test('extractCallerSource returns file:line from first frame outside logging/', () {
    final trace = StackTrace.fromString('''
#0      AppLogger.info (package:phoneblock_mobile/logging/app_logger.dart:42:5)
#1      fetchNumber (package:phoneblock_mobile/api.dart:123:7)
#2      main (package:phoneblock_mobile/main.dart:17:3)
''');
    final src = extractCallerSource(trace);
    expect(src, 'api.dart:123');
  });

  test('returns null when no non-logging frame exists', () {
    final trace = StackTrace.fromString('''
#0      AppLogger.info (package:phoneblock_mobile/logging/app_logger.dart:42:5)
''');
    expect(extractCallerSource(trace), isNull);
  });

  test('skips caller_source.dart itself', () {
    final trace = StackTrace.fromString('''
#0      extractCallerSource (package:phoneblock_mobile/logging/caller_source.dart:9:3)
#1      AppLogger.info (package:phoneblock_mobile/logging/app_logger.dart:42:5)
#2      doWork (package:phoneblock_mobile/x.dart:10:1)
''');
    expect(extractCallerSource(trace), 'x.dart:10');
  });
}
```

- [ ] **Step 2: Run to confirm failure**

Run: `cd phoneblock_mobile && flutter test test/logging/caller_source_test.dart`
Expected: FAIL (file not found).

- [ ] **Step 3: Implement**

```dart
/// Returns `file:line` of the first stack frame not inside this package's
/// `lib/logging/` directory. Returns `null` if no such frame is found.
String? extractCallerSource([StackTrace? trace]) {
  final lines = (trace ?? StackTrace.current).toString().split('\n');
  final frame = RegExp(r'\(package:([^/]+)/(.+\.dart):(\d+)(?::\d+)?\)');
  for (final line in lines) {
    final m = frame.firstMatch(line);
    if (m == null) continue;
    final path = m.group(2)!;
    if (path.startsWith('logging/')) continue;
    final file = path.split('/').last;
    return '$file:${m.group(3)}';
  }
  return null;
}
```

- [ ] **Step 4: Run tests**

Run: `cd phoneblock_mobile && flutter test test/logging/caller_source_test.dart`
Expected: PASS (all 3).

- [ ] **Step 5: Commit**

```bash
git add phoneblock_mobile/lib/logging/caller_source.dart \
        phoneblock_mobile/test/logging/caller_source_test.dart
git commit -m "Add caller source extraction helper (#282)"
```

---

## Task 10: Dart `AppLogger` + test

**Files:**
- Create: `phoneblock_mobile/lib/logging/app_logger.dart`
- Test: `phoneblock_mobile/test/logging/app_logger_test.dart`

- [ ] **Step 1: Write the failing test**

```dart
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:phoneblock_mobile/logging/app_logger.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('phoneblock/log');
  final calls = <MethodCall>[];

  setUp(() {
    calls.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      if (call.method == 'getLogDir') return '/tmp/test';
      return null;
    });
    AppLogger.resetForTest();
  });

  test('buffers calls before init, flushes after init', () async {
    AppLogger.instance.info('tag', 'before');
    expect(calls.where((c) => c.method == 'log'), isEmpty);

    await AppLogger.instance.init();
    expect(calls.where((c) => c.method == 'log').length, 1);
    final args = calls.firstWhere((c) => c.method == 'log').arguments
        as Map<Object?, Object?>;
    expect(args['msg'], 'before');
    expect(args['tag'], 'tag');
    expect(args['level'], 'info');
  });

  test('forwards calls directly after init', () async {
    await AppLogger.instance.init();
    AppLogger.instance.warn('tagX', 'hello');
    final logs = calls.where((c) => c.method == 'log').toList();
    expect(logs.length, 1);
    final args = logs.first.arguments as Map<Object?, Object?>;
    expect(args['level'], 'warn');
    expect(args['msg'], 'hello');
    expect(args['tag'], 'tagX');
  });

  test('pending buffer drops oldest beyond 50', () async {
    for (var i = 0; i < 60; i++) {
      AppLogger.instance.info('t', 'msg$i');
    }
    await AppLogger.instance.init();
    final msgs = calls
        .where((c) => c.method == 'log')
        .map((c) => (c.arguments as Map)['msg'])
        .toList();
    expect(msgs.length, 50);
    expect(msgs.first, 'msg10');
    expect(msgs.last, 'msg59');
  });

  test('channel error does not throw', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      if (call.method == 'getLogDir') return '/tmp/test';
      throw PlatformException(code: 'boom');
    });
    await AppLogger.instance.init();
    expect(() => AppLogger.instance.error('t', 'x'), returnsNormally);
  });
}
```

- [ ] **Step 2: Confirm failure**

Run: `cd phoneblock_mobile && flutter test test/logging/app_logger_test.dart`
Expected: FAIL.

- [ ] **Step 3: Implement**

```dart
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

  /// Resolves the native log directory and flushes any buffered entries.
  /// Must be awaited in `main()` before `runApp`.
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

  /// Returns the directory containing `app.log` and `app.log.1`, or null
  /// before [init] has resolved it.
  String? get logDir => _logDir;

  void info(String tag, String msg) => _log('info', tag, msg);
  void warn(String tag, String msg) => _log('warn', tag, msg);
  void error(String tag, String msg, [Object? error, StackTrace? stack]) =>
      _log('error', tag, msg, error: error, stack: stack);

  /// Intended as the error callback for `runZonedGuarded`.
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
    try {
      _channel.invokeMethod<void>('log', {
        'level': e.level,
        'tag': e.tag,
        'msg': e.msg,
        if (e.src != null) 'src': e.src,
        if (e.error != null) 'error': e.error,
        if (e.stack != null) 'stack': e.stack,
      });
    } catch (err) {
      debugPrint('AppLogger._send failed: $err');
    }
  }

  /// For tests only.
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
```

- [ ] **Step 4: Run tests**

Run: `cd phoneblock_mobile && flutter test test/logging/app_logger_test.dart`
Expected: PASS (all 4).

- [ ] **Step 5: Commit**

```bash
git add phoneblock_mobile/lib/logging/app_logger.dart \
        phoneblock_mobile/test/logging/app_logger_test.dart
git commit -m "Add AppLogger singleton with pending buffer (#282)"
```

---

## Task 11: Dart `CrashHandler`

**Files:**
- Create: `phoneblock_mobile/lib/logging/crash_handler.dart`

- [ ] **Step 1: Implement**

```dart
import 'dart:ui';
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
```

- [ ] **Step 2: Compile check**

Run: `cd phoneblock_mobile && flutter analyze lib/logging/crash_handler.dart`
Expected: No issues.

- [ ] **Step 3: Commit**

```bash
git add phoneblock_mobile/lib/logging/crash_handler.dart
git commit -m "Add Dart CrashHandler (#282)"
```

---

## Task 12: Initialize logging in `main.dart`

**Files:**
- Modify: `phoneblock_mobile/lib/main.dart` (imports + top of `main()`)

- [ ] **Step 1: Add imports**

Near the other top-level imports in `main.dart`:

```dart
import 'dart:async';
import 'package:phoneblock_mobile/logging/app_logger.dart';
import 'package:phoneblock_mobile/logging/crash_handler.dart';
```

(If `dart:async` is already imported, skip that line.)

- [ ] **Step 2: Replace the body of `main()`**

Find the existing `void main()` (or `Future<void> main()`) and change its body so the entire runtime lives inside `runZonedGuarded`:

```dart
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await AppLogger.instance.init();
  CrashHandler.install();

  runZonedGuarded(() {
    // … existing initialization + runApp(...) body moves here unchanged …
  }, AppLogger.instance.logZoneError);
}
```

Move the entire previous body of `main()` (everything after `WidgetsFlutterBinding.ensureInitialized();`, if it was there, otherwise the whole body) into the `runZonedGuarded` callback.

- [ ] **Step 3: Smoke test**

Run: `cd phoneblock_mobile && flutter analyze && flutter build apk --debug`
Expected: No analyzer issues, build succeeds.

- [ ] **Step 4: Commit**

```bash
git add phoneblock_mobile/lib/main.dart
git commit -m "Initialize AppLogger and CrashHandler in main() (#282)"
```

---

## Task 13: Add l10n strings for the log viewer (German source)

**Files:**
- Modify: `phoneblock_mobile/lib/l10n/app_de.arb`

- [ ] **Step 1: Add entries**

Add these keys to `app_de.arb` (alphabetical position within the file):

```json
  "diagnosticLog": "Diagnoseprotokoll",
  "@diagnosticLog": { "description": "Settings entry and viewer title for the diagnostic log." },

  "diagnosticLogShare": "Teilen",
  "@diagnosticLogShare": { "description": "Share action in the log viewer." },

  "diagnosticLogClear": "Löschen",
  "@diagnosticLogClear": { "description": "Clear action in the log viewer." },

  "diagnosticLogRefresh": "Aktualisieren",
  "@diagnosticLogRefresh": { "description": "Refresh action in the log viewer." },

  "diagnosticLogEmpty": "Das Protokoll ist leer.",
  "@diagnosticLogEmpty": { "description": "Placeholder when no log content is available." },

  "diagnosticLogReadError": "(Protokolldatei gerade nicht lesbar)",
  "@diagnosticLogReadError": { "description": "Shown when a log file cannot be read due to concurrent access." },

  "diagnosticLogClearConfirmTitle": "Protokoll löschen?",
  "@diagnosticLogClearConfirmTitle": { "description": "Confirmation dialog title." },

  "diagnosticLogClearConfirmBody": "Alle Einträge des Diagnoseprotokolls werden entfernt.",
  "@diagnosticLogClearConfirmBody": { "description": "Confirmation dialog body." },

  "diagnosticLogShareSubject": "PhoneBlock Diagnoseprotokoll",
  "@diagnosticLogShareSubject": { "description": "Subject line when sharing the log." }
```

- [ ] **Step 2: Run the translation tool**

Run: `cd phoneblock_mobile && ./gradlew translateArb`
Expected: all other `.arb` files updated with the new keys; no errors.

- [ ] **Step 3: Regenerate Dart localization code**

Run: `cd phoneblock_mobile && flutter gen-l10n`
Expected: `app_localizations*.dart` updated.

- [ ] **Step 4: Commit**

```bash
git add phoneblock_mobile/lib/l10n phoneblock_mobile/lib/l10n/*.dart 2>/dev/null; true
git add -u phoneblock_mobile/lib/l10n
git commit -m "Add l10n strings for diagnostic log viewer (#282)"
```

---

## Task 14: Create `LogViewerScreen`

**Files:**
- Create: `phoneblock_mobile/lib/logging/log_viewer_screen.dart`

- [ ] **Step 1: Implement**

```dart
import 'dart:io';

import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/material.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:path/path.dart' as p;
import 'package:share_plus/share_plus.dart';

import 'package:phoneblock_mobile/l10n/app_localizations.dart';
import 'package:phoneblock_mobile/logging/app_logger.dart';

/// Subscreen under Settings that shows the current diagnostic log and lets
/// the user share or clear it.
class LogViewerScreen extends StatefulWidget {
  const LogViewerScreen({super.key});

  @override
  State<LogViewerScreen> createState() => _LogViewerScreenState();
}

class _LogViewerScreenState extends State<LogViewerScreen> {
  String _content = '';
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final dir = AppLogger.instance.logDir;
    String text = '';
    if (dir != null) {
      text = await _readPair(dir);
    }
    if (!mounted) return;
    setState(() {
      _content = text;
      _loading = false;
    });
  }

  Future<String> _readPair(String dir) async {
    final buffer = StringBuffer();
    for (final name in const ['app.log.1', 'app.log']) {
      final file = File(p.join(dir, name));
      if (!await file.exists()) continue;
      try {
        buffer.writeln(await file.readAsString());
      } on IOException {
        await Future<void>.delayed(const Duration(milliseconds: 100));
        try {
          buffer.writeln(await file.readAsString());
        } on IOException {
          final l10n = AppLocalizations.of(context)!;
          buffer.writeln(l10n.diagnosticLogReadError);
        }
      }
    }
    return buffer.toString();
  }

  Future<void> _share() async {
    final dir = AppLogger.instance.logDir;
    if (dir == null) return;
    final headerFile = await _writeHeader(dir);
    final files = <XFile>[
      if (await File(p.join(dir, 'app.log.1')).exists())
        XFile(p.join(dir, 'app.log.1')),
      if (await File(p.join(dir, 'app.log')).exists())
        XFile(p.join(dir, 'app.log')),
      XFile(headerFile.path),
    ];
    if (!mounted) return;
    final l10n = AppLocalizations.of(context)!;
    await Share.shareXFiles(files, subject: l10n.diagnosticLogShareSubject);
  }

  Future<File> _writeHeader(String dir) async {
    final info = await PackageInfo.fromPlatform();
    final androidInfo = await DeviceInfoPlugin().androidInfo;
    final header = '''
App:      ${info.appName} ${info.version}+${info.buildNumber}
OS:       Android ${androidInfo.version.release} (SDK ${androidInfo.version.sdkInt})
Device:   ${androidInfo.manufacturer} ${androidInfo.model}
Locale:   ${WidgetsBinding.instance.platformDispatcher.locale}
Time:     ${DateTime.now().toIso8601String()}
''';
    final ts = DateTime.now().toIso8601String().replaceAll(RegExp(r'[:.]'), '-');
    final file = File(p.join(dir, 'phoneblock-log-$ts.txt'));
    await file.writeAsString(header);
    return file;
  }

  Future<void> _confirmClear() async {
    final l10n = AppLocalizations.of(context)!;
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: Text(l10n.diagnosticLogClearConfirmTitle),
        content: Text(l10n.diagnosticLogClearConfirmBody),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false),
              child: Text(MaterialLocalizations.of(c).cancelButtonLabel)),
          TextButton(onPressed: () => Navigator.pop(c, true),
              child: Text(l10n.diagnosticLogClear)),
        ],
      ),
    );
    if (ok == true) {
      await AppLogger.instance.clear();
      await _load();
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.diagnosticLog),
        actions: [
          IconButton(icon: const Icon(Icons.refresh),
              tooltip: l10n.diagnosticLogRefresh, onPressed: _load),
          IconButton(icon: const Icon(Icons.share),
              tooltip: l10n.diagnosticLogShare, onPressed: _share),
          IconButton(icon: const Icon(Icons.delete_outline),
              tooltip: l10n.diagnosticLogClear, onPressed: _confirmClear),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _content.trim().isEmpty
              ? Center(child: Text(l10n.diagnosticLogEmpty))
              : Scrollbar(
                  child: SingleChildScrollView(
                    reverse: true,
                    padding: const EdgeInsets.all(12),
                    child: SelectableText(
                      _content,
                      style: const TextStyle(
                          fontFamily: 'monospace', fontSize: 12),
                    ),
                  ),
                ),
    );
  }
}
```

- [ ] **Step 2: Compile check**

Run: `cd phoneblock_mobile && flutter analyze lib/logging/log_viewer_screen.dart`
Expected: No issues. (If `path` package is not yet a direct dependency, add `path:` to `pubspec.yaml` — see Step 3.)

- [ ] **Step 3: Ensure `path` and `device_info_plus` are listed as direct deps**

Check `pubspec.yaml` — both are already listed (see repo state). If missing, add them. Run `flutter pub get`.

- [ ] **Step 4: Commit**

```bash
git add phoneblock_mobile/lib/logging/log_viewer_screen.dart
git commit -m "Add LogViewerScreen with share and clear (#282)"
```

---

## Task 15: Add "Diagnoseprotokoll" entry to Settings

**Files:**
- Modify: `phoneblock_mobile/lib/main.dart` (inside `_SettingsScreenState.build`, near other ListTile entries)

- [ ] **Step 1: Add import**

```dart
import 'package:phoneblock_mobile/logging/log_viewer_screen.dart';
```

- [ ] **Step 2: Add a `ListTile` inside the settings `ListView`/`Column`**

Locate `_SettingsScreenState.build` (near `main.dart:3115` and following). After the last existing settings entry, insert:

```dart
          ListTile(
            leading: const Icon(Icons.bug_report_outlined),
            title: Text(AppLocalizations.of(context)!.diagnosticLog),
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (c) => const LogViewerScreen()),
              );
            },
          ),
```

- [ ] **Step 3: Smoke test**

Run: `cd phoneblock_mobile && flutter analyze && flutter build apk --debug`
Expected: clean analyze, build succeeds.

- [ ] **Step 4: Commit**

```bash
git add phoneblock_mobile/lib/main.dart
git commit -m "Add diagnostic log entry in settings (#282)"
```

---

## Task 16: Breadcrumb instrumentation in Dart

Cover the four files listed in the spec. Each change is a small, localized edit — keep the same commit.

**Files:**
- Modify: `phoneblock_mobile/lib/api.dart`
- Modify: `phoneblock_mobile/lib/blocklist_sync_service.dart`
- Modify: `phoneblock_mobile/lib/storage.dart`
- Modify: `phoneblock_mobile/lib/main.dart`

- [ ] **Step 1: `api.dart`**

Add at the top:

```dart
import 'package:phoneblock_mobile/logging/app_logger.dart';
```

For every function that issues an HTTP request (e.g. `updateAccountSettings`, `fetchAccountSettings`, `pbApiTest`), wrap the call:

```dart
AppLogger.instance.info('api', 'GET $url');
try {
  final response = await http.get(Uri.parse(url));
  AppLogger.instance.info('api', 'GET $url -> ${response.statusCode}');
  // existing handling
} catch (e, s) {
  AppLogger.instance.error('api', 'GET $url failed', e, s);
  rethrow;
}
```

Do this for at least: `pbApiTest`, `updateAccountSettings`, `fetchAccountSettings`, and the blocklist fetch if present. Replace existing `print(...)` calls in these functions with the appropriate `AppLogger` level (`error` for failures, `info` for success).

- [ ] **Step 2: `blocklist_sync_service.dart`**

Add import, then log start/end/error of each sync run:

```dart
AppLogger.instance.info('sync', 'blocklist sync started');
try {
  // existing body
  AppLogger.instance.info('sync', 'blocklist sync done, added=$count');
} catch (e, s) {
  AppLogger.instance.error('sync', 'blocklist sync failed', e, s);
  rethrow;
}
```

- [ ] **Step 3: `storage.dart`**

Add import. Log on DB open, migrations, and exceptions — pick the natural call sites (typically `_open`, `onCreate`, `onUpgrade`, and any existing catch blocks that currently use `print`):

```dart
AppLogger.instance.info('db', 'opening database at $path');
// …
AppLogger.instance.info('db', 'migration $oldV -> $newV');
// …
AppLogger.instance.error('db', 'sql error', e, s);
```

- [ ] **Step 4: `main.dart`**

Add log lines for lifecycle events. Near the OAuth callback handler (search for `loginToken`), deep-link reception, and login/logout code paths:

```dart
AppLogger.instance.info('app', 'oauth callback received');
AppLogger.instance.info('app', 'login successful');
AppLogger.instance.info('app', 'logout');
```

Replace existing `print(...)` calls in these lifecycle paths with `AppLogger.instance.info` / `.error` as appropriate. Do **not** change unrelated `print` calls in this task.

- [ ] **Step 5: Analyze**

Run: `cd phoneblock_mobile && flutter analyze`
Expected: no new warnings.

- [ ] **Step 6: Commit**

```bash
git add phoneblock_mobile/lib/api.dart \
        phoneblock_mobile/lib/blocklist_sync_service.dart \
        phoneblock_mobile/lib/storage.dart \
        phoneblock_mobile/lib/main.dart
git commit -m "Instrument Dart layer with breadcrumb logs (#282)"
```

---

## Task 17: Breadcrumb instrumentation in `CallChecker`

**Files:**
- Modify: `phoneblock_mobile/android/app/src/main/java/de/haumacher/phoneblock_mobile/CallChecker.java`

- [ ] **Step 1: Add SLF4J logger field and imports**

Top of file alongside existing imports:

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock_mobile.log.LogSanitizer;
```

Inside the class, near the top:

```java
    private static final Logger LOG = LoggerFactory.getLogger(CallChecker.class);
```

- [ ] **Step 2: Log the screening decision**

In `onScreenCall` (and any helper where the block/allow decision is finalized), at the point where the service currently knows the outcome (right before calling `respondToCall` or the equivalent), add:

```java
    LOG.info("decision={} reason={} number={}",
        wasBlocked ? "block" : "allow",
        reason,     // existing local variable describing why; if not present, pass "" or the rating string
        LogSanitizer.hashPhone(phoneNumber));
```

Pick the actual local variable names that already hold the block decision and number. Where the existing code uses `android.util.Log.d/e` in the screening path, replace those calls with `LOG.info`/`LOG.error`. Leave `android.util.Log` calls outside the screening path untouched.

- [ ] **Step 3: Add error logging on API failures**

Wherever the screening path currently swallows an `IOException` or `JSONException` and logs via `android.util.Log.e`, replace with:

```java
    LOG.error("screening query failed for {}", LogSanitizer.hashPhone(phoneNumber), e);
```

- [ ] **Step 4: Build**

Run: `cd phoneblock_mobile && flutter build apk --debug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add phoneblock_mobile/android/app/src/main/java/de/haumacher/phoneblock_mobile/CallChecker.java
git commit -m "Log screening decisions via SLF4J with hashed numbers (#282)"
```

---

## Task 18: Manual end-to-end verification

No code changes — execute these checks on a debug build and record results.

- [ ] **Step 1: Install debug APK**

Run: `cd phoneblock_mobile && flutter run --debug -d <device-id>`

- [ ] **Step 2: Open the viewer**

Navigate to Settings → "Diagnoseprotokoll". Verify:
- Screen opens without error.
- Some log lines are present (app start, API calls).
- Header pattern shows `[file.dart:NN]` for Dart lines and `[ClassName:NN]` for Java lines.

- [ ] **Step 3: Trigger a forced crash**

Temporarily add a debug-only button or invoke `throw Exception("Diagnose-Test")` from a dev hook. After the crash, restart the app, reopen the viewer and verify the stack trace appears with level `ERROR` and tag `flutter` or `zone`.
Revert the test crash afterwards.

- [ ] **Step 4: Trigger a screening decision**

Call the test device with a known number. Verify a line of the form `decision=block reason=… number=sha1:xxxxxxxx` appears. The raw number must NOT be present anywhere in either log file.

- [ ] **Step 5: Share export**

Tap "Teilen" → choose GMail (or any mail app). Verify both `app.log(.1)` files and the generated header `.txt` are attached. Open on desktop and confirm readability.

- [ ] **Step 6: Clear**

Tap "Löschen" → confirm. Verify the viewer shows the empty placeholder. Trigger one more event (e.g. pull-to-refresh a screen that uses the API) and verify new entries start appearing again.

- [ ] **Step 7: Rotation sanity check (optional)**

Either via a debug hook or by scripting `adb shell run-as de.haumacher.phoneblock_mobile ls -l files/`, verify after sufficient usage that `app.log.1` is created and `app.log` stays under 512 KB.

- [ ] **Step 8: Commit any fixes found during testing**

If manual testing revealed bugs, fix them with small focused commits referencing #282. No commit needed if everything passed.

---

## Done criteria

- All automated tests (`flutter test`, `./gradlew :app:testDebugUnitTest`) pass.
- Manual test checklist in Task 18 all pass.
- `flutter analyze` is clean.
- Spec sections covered: architecture, caller identification via MDC, data flow, initialization order, instrumentation, UI, error handling, testing, dependencies.
