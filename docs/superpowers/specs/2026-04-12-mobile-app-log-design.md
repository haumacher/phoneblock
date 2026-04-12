# Diagnoseprotokoll für die PhoneBlock-Mobile-App

**Issue:** #282
**Datum:** 2026-04-12
**Scope:** `phoneblock_mobile/` (Android-only)

## Ziel

Lokales Diagnoseprotokoll, das Nutzer bei Problemen per E-Mail an den Support anhängen können. Optionales automatisches Remote-Reporting an phoneblock.net ist **nicht** Teil dieses Designs und wird später separat spezifiziert.

## Anforderungen

- Breadcrumb-Stil: unbehandelte Fehler/Crashes + gezielte App-Events (Login, API-Calls, CallScreening-Entscheidungen, Sync-Läufe, DB-Migrationen).
- Einheitliches Log über Dart- und Android-Prozessgrenzen hinweg (App-UI + CallScreeningService).
- Sichtbarer Einstiegspunkt in den App-Einstellungen: "Diagnoseprotokoll" mit Textansicht, Teilen- und Löschen-Aktion.
- Rotation auf zwei Dateien à 512 KB, damit beim Support-Fall auch Kontext vor dem eigentlichen Fehler erhalten bleibt.
- Keine Telefonnummern im Klartext — nur SHA-1-Präfixe.
- Keine externen Dienste, keine Netzwerkkommunikation.

## Architektur

**Single-Source-of-Truth auf Android-Seite.** Alles Logging läuft durch Logback-Android im Java-Prozess. Dart leitet seine Logs über einen MethodChannel dorthin weiter. CallScreeningService schreibt direkt (da in eigenem Prozess, ohne aktive Flutter-Engine). Logbacks `prudent`-Modus nutzt `FileChannel.lock()` und garantiert konfliktfreien Multi-Prozess-Schreibzugriff auf dieselben Dateien.

### Komponenten

**Dart (`phoneblock_mobile/lib/logging/`):**
- `AppLogger` — Singleton mit `info/warn/error(tag, msg, [error, stack])`. MethodChannel-Wrapper mit Pending-Puffer (max. 50) für Aufrufe vor `init`.
- `CrashHandler` — installiert `FlutterError.onError`, `PlatformDispatcher.instance.onError`, sowie die Error-Callback für `runZonedGuarded`.
- `LogViewerScreen` — scrollbare Textansicht; AppBar-Aktionen „Teilen“, „Löschen“, „Aktualisieren“.

**Android (`phoneblock_mobile/android/app/src/main/java/.../`):**
- `LogBridge` — MethodChannel-Handler `phoneblock/log` mit Methoden `log`, `getLogDir`, `clear`. Delegiert an SLF4J-Logger. Umschließt alles in `try/catch(Throwable)`.
- `NativeCrashHandler` — installiert `Thread.setDefaultUncaughtExceptionHandler`, loggt via SLF4J `ERROR` und ruft den vorherigen Handler auf.
- `LogSanitizer.hashPhone(String)` — SHA-1 mit Präfix `sha1:` + 8 Hex-Zeichen.
- Anpassungen in `MainActivity` und `CallChecker`: `DATA_DIR`-System-Property setzen (`getFilesDir().getAbsolutePath()`) **vor** dem ersten Logger-Aufruf; Crash-Handler installieren.

**Konfiguration (`phoneblock_mobile/android/app/src/main/assets/logback.xml`):**
- `RollingFileAppender` mit `prudent=true`, `file=${DATA_DIR}/app.log`.
- `FixedWindowRollingPolicy`, `fileNamePattern=${DATA_DIR}/app.log.%i`, `minIndex=1`, `maxIndex=1`.
- `SizeBasedTriggeringPolicy`, `maxFileSize=512KB`.
- `PatternLayout`: `%d{ISO8601} %-5level %logger{20} - %msg%n`.
- Root-Level: `INFO` (Release), `DEBUG` (Debug-Build).

## Datenfluss

```
Dart-Code ──► AppLogger.info/warn/error
                │ MethodChannel "phoneblock/log" (fire-and-forget)
                ▼
Java LogBridge ─► SLF4J Logger.info/warn/error
                     │
                     ▼
             Logback RollingFileAppender (prudent)
                     │
                     ▼
              app.log / app.log.1  in context.getFilesDir()

Crash-Quellen
  Dart:    FlutterError.onError, PlatformDispatcher.onError,
           runZonedGuarded ──► AppLogger.error
  Android: Thread.UncaughtExceptionHandler ──► SLF4J.error

Viewer (Dart): dart:io liest app.log.1 + app.log
Export (Dart): share_plus mit beiden Dateien + generiertem Header
```

## Initialisierung

1. **`MainActivity.onCreate`** (und analog `CallChecker.onCreate`):
   - `System.setProperty("DATA_DIR", getFilesDir().getAbsolutePath())`.
   - Ersten SLF4J-Logger abrufen (triggert Laden von `logback.xml`).
   - `NativeCrashHandler.install()`.
   - In `MainActivity` zusätzlich: `LogBridge` am MethodChannel `phoneblock/log` registrieren.
2. **Dart `main()`**:
   - `WidgetsFlutterBinding.ensureInitialized()`.
   - `await AppLogger.init()` — holt Log-Verzeichnis per `getLogDir`, leert Pending-Puffer.
   - `CrashHandler.install()`.
   - `runZonedGuarded(() => runApp(...), AppLogger.logZoneError)`.

## Instrumentierung (Breadcrumbs)

**Dart:**
- `api.dart` — Start/Ende jedes API-Calls mit Statuscode, Fehler.
- `main.dart` — App-Start, Login/Logout, Deep-Link-Empfang.
- `blocklist_sync_service.dart` — Sync-Start, Dauer, Ergebnis (Anzahl neuer Einträge), Fehler.
- `storage.dart` — DB-Öffnen, Migrationen, Fehler.

**Android:**
- `CallChecker` — Screening-Entscheidung: `decision=block|allow`, `reason=…`, Nummer als `LogSanitizer.hashPhone(number)`.
- `MainActivity` — Lebenszyklus-Fehler, MethodChannel-Fehler.

**Sanitisierung:** Telefonnummern dürfen nur als `sha1:xxxxxxxx` geloggt werden. Helper (`AppLogger.hashPhone` / `LogSanitizer.hashPhone`) sind bereitzustellen und explizit aufzurufen. Kein automatisches Abfangen — explizites Hashen ist Pflicht.

## UI

Neuer Eintrag in den App-Einstellungen: **„Diagnoseprotokoll“**. Öffnet `LogViewerScreen`:

- Monospace-Textansicht, neueste Zeilen unten, initial zum Ende gescrollt.
- AppBar-Aktionen:
  - **Teilen**: via `share_plus` (`Share.shareXFiles`) mit beiden Log-Dateien und einer temporären Header-Datei (`phoneblock-log-YYYYMMDD-HHMM.txt`) mit App-Version (`package_info_plus`), OS-Version und Device-Modell (`device_info_plus`), Locale. Subject: „PhoneBlock Diagnoseprotokoll“.
  - **Löschen**: Bestätigungsdialog → `channel.invokeMethod('clear')`.
  - **Aktualisieren**: lädt die Dateien neu.
- l10n: alle neuen Strings in `phoneblock_mobile/lib/l10n/app_de.arb` (Quellsprache). `./gradlew translateArb` + `flutter gen-l10n` nach jeder Änderung.

## Fehlerbehandlung & Edge Cases

- **Logging-Fehler dürfen nie die App abstürzen lassen.** `AppLogger` und `LogBridge` umschließen jeden Schreibpfad in `try/catch`. Bei Pending-Puffer-Overflow wird der älteste Eintrag verworfen.
- **Logback-Init-Fehler** (XML defekt, `DATA_DIR` nicht beschreibbar): Logback fällt intern auf Konsole zurück. `MainActivity` prüft nach Init einmalig, ob ein File-Appender aktiv ist; wenn nicht, einmalige `android.util.Log.w`-Warnung.
- **UncaughtExceptionHandler-Kette:** Vorherigen Handler speichern und nach dem Logging aufrufen, damit Android-System-Crash-Dialog weiter funktioniert.
- **CallScreeningService in separatem Prozess:** `DATA_DIR` und Crash-Handler müssen in `CallChecker.onCreate` gesetzt werden, bevor der erste SLF4J-Call erfolgt. Logback lädt sich pro Prozess unabhängig neu. `prudent`-Modus verhindert Datenkorruption zwischen den Prozessen.
- **Storage voll / Schreibfehler:** Logback schlägt intern fehl, App bleibt unbeeinträchtigt.
- **Viewer liest während parallelem Schreibzugriff:** Einmaliges Retry nach 100 ms bei `IOException`; danach Platzhaltertext, Export-Button bleibt nutzbar.
- **Löschen:** Java schließt File-Appender (`LoggerContext.reset()`), löscht `app.log` und `app.log.1`, initialisiert Logback neu.

## Testing

**Dart-Unit-Tests (`test/logging/`):**
- `AppLogger` mit Mock-MethodChannel: Pending-Puffer vor `init`, Weiterleitung nach `init`, Puffer-Overflow verwirft ältesten Eintrag, Channel-Fehler wirft nicht.
- `LogViewer`-Widget-Test mit gefakter Log-Datei: korrekte Sortierung, leere Datei rendert ohne Fehler.
- `hashPhone()`: deterministisch, nie leer.

**Android-Instrumented-Tests (`androidTest/`):**
- Nach Init existiert `app.log`.
- Nach Schreiben von 600 KB: `app.log.1` vorhanden, `app.log` < 512 KB.
- Parallel-Schreiben aus zwei Threads (prudent-Modus) — keine Datenkorruption; alle Zeilen wiederauffindbar.

**Manuell:**
- Test-Button in Debug-Build wirft Exception → nach Restart Stacktrace im Viewer sichtbar.
- Screening-Entscheidung mit Test-Nummer → SHA-1 statt Rohnummer im Log.
- Export-Flow End-to-End an GMail, Anhang auf Desktop öffnen.

## Dependencies

**Android (`build.gradle`):**
- `com.github.tony19:logback-android:3.0.0`
- `org.slf4j:slf4j-api:2.0.x`

**Flutter (`pubspec.yaml`):**
- `share_plus` — nur diese eine neue Dart-Dependency.

## Nicht enthalten (bewusst ausgeklammert)

- Automatisches Remote-Crash-Reporting an phoneblock.net — eigenes Design später.
- iOS-Port — Android-only, `NativeLogger` und Logback-Konfiguration sind Android-spezifisch.
- Log-Verschlüsselung — lokal im App-Sandbox-Storage, ausreichend geschützt durch Android-Userrechte.

## Checkliste für die Implementierung

- [ ] `build.gradle`: `logback-android`, `slf4j-api`.
- [ ] `pubspec.yaml`: `share_plus`.
- [ ] `android/app/src/main/assets/logback.xml`.
- [ ] `LogBridge.java`.
- [ ] `NativeCrashHandler.java`, `LogSanitizer.java`.
- [ ] `MainActivity.onCreate`: `DATA_DIR`, `LogBridge`, Crash-Handler.
- [ ] `CallChecker.onCreate`: `DATA_DIR`, Crash-Handler, Screening-Logs.
- [ ] Dart `lib/logging/app_logger.dart`, `crash_handler.dart`, `log_viewer_screen.dart`.
- [ ] Dart `main.dart`: Initialisierung + `runZonedGuarded`.
- [ ] Instrumentierung in `api.dart`, `blocklist_sync_service.dart`, `storage.dart`, `main.dart`.
- [ ] Einstellungen: Eintrag „Diagnoseprotokoll“ + Routing.
- [ ] l10n: `app_de.arb` aktualisiert, `translateArb` + `gen-l10n` ausgeführt.
- [ ] Tests wie oben.
