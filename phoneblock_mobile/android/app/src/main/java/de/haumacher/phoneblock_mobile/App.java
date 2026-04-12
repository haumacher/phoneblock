package de.haumacher.phoneblock_mobile;

import android.app.Application;

import de.haumacher.phoneblock_mobile.log.LogContext;

/**
 * Application subclass that initializes Logback once per Android process,
 * so Logger instances are ready before any Flutter engine starts.
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        LogContext.init(this);
    }
}
