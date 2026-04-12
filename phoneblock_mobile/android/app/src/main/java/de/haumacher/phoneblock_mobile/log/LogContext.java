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
