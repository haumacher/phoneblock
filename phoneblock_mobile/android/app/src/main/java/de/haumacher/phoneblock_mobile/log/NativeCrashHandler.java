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
