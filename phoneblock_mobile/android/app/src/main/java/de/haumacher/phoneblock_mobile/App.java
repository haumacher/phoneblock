package de.haumacher.phoneblock_mobile;

import android.app.Application;

import de.haumacher.phoneblock_mobile.log.LogContext;

/**
 * Application subclass that initializes the diagnostic-log infrastructure
 * (Logback) once per Android process, so the {@link CallChecker} service,
 * the main {@code FlutterActivity}, and any WorkManager background worker
 * share a ready Logback setup.
 *
 * <p><strong>Note on background-isolate logging:</strong>
 * WorkManager 0.9.x ({@code workmanager_android 0.9.0+2}) removed the
 * {@code WorkmanagerPlugin.setPluginRegistrantCallback} / {@code
 * PluginRegistry.PluginRegistrantCallback} API that existed in the v1
 * Flutter embedding.  The {@code BackgroundWorker} in that version creates
 * its own {@link io.flutter.embedding.engine.FlutterEngine} privately and
 * provides no hook to register additional method-channel handlers on it.
 * As a result {@link de.haumacher.phoneblock_mobile.log.LogBridge} cannot be
 * wired into the background engine from this class.
 *
 * <p>The Dart-side {@code AppLogger} already guards every channel call with
 * {@code try/catch} and {@code .catchError()}, so the background isolate
 * degrades gracefully: log calls are silently swallowed rather than throwing
 * {@code MissingPluginException}.
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize Logback once for the whole Android process.
        // This ensures CallChecker and other native components can log
        // immediately, before any Flutter engine is started.
        LogContext.init(this);
    }
}
