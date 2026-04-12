package de.haumacher.phoneblock_logging;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;

import java.io.File;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

/**
 * Flutter plugin: bridges Dart log calls on channel {@code phoneblock/log}
 * into SLF4J. Auto-registered on every FlutterEngine created in the
 * Android process via {@code GeneratedPluginRegistrant}, which means it
 * works in the main isolate AND in Workmanager background isolates
 * without any manual wiring.
 */
public final class LogBridge implements FlutterPlugin, MethodChannel.MethodCallHandler {

    public static final String CHANNEL = "phoneblock/log";

    private MethodChannel channel;
    private Context appContext;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        appContext = binding.getApplicationContext();
        channel = new MethodChannel(binding.getBinaryMessenger(), CHANNEL);
        channel.setMethodCallHandler(this);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        if (channel != null) {
            channel.setMethodCallHandler(null);
            channel = null;
        }
        appContext = null;
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
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
        File f1 = new File(dir, "app.log");
        File f2 = new File(dir, "app.log.1");
        ok &= f1.delete() || !f1.exists();
        ok &= f2.delete() || !f2.exists();
        return ok;
    }
}
