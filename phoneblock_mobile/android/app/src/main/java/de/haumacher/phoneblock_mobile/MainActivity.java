package de.haumacher.phoneblock_mobile;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

public class MainActivity extends FlutterActivity {

    public static final String CALL_CHECKER_CHANNEL = "de.haumacher.phoneblock_mobile/call_checker";

    private MethodChannel _channel;
    private static MainActivity _instance;

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);

        _instance = this;
        _channel = new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CALL_CHECKER_CHANNEL);
        _channel.setMethodCallHandler(this::processMessage);
    }

    @Override
    protected void onDestroy() {
        _instance = null;
        super.onDestroy();
    }

    /**
     * Reports a screened call result to Flutter.
     * Called by CallChecker when a call is screened.
     *
     * This method stores the screening result in SharedPreferences first (so it persists
     * even when the app is not running), then notifies Flutter if it's active.
     *
     * @param context The context (usually the CallChecker service)
     * @param phoneNumber The phone number that was screened
     * @param wasBlocked true if the call was blocked as SPAM, false if accepted
     * @param votes Number of votes from PhoneBlock database
     */
    public static void reportScreenedCall(Context context, String phoneNumber, boolean wasBlocked, int votes) {
        long timestamp = System.currentTimeMillis();

        // Store in SharedPreferences for persistence (works even when app is not running)
        SharedPreferences prefs = getPreferences(context);
        String key = "screened_call_" + timestamp;
        String value = phoneNumber + "|" + wasBlocked + "|" + votes + "|" + timestamp;
        prefs.edit().putString(key, value).apply();

        // Also notify Flutter if it's active
        if (_instance != null && _instance._channel != null) {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("phoneNumber", phoneNumber);
            data.put("wasBlocked", wasBlocked);
            data.put("votes", votes);
            data.put("timestamp", timestamp);

            _instance._channel.invokeMethod("onCallScreened", data);
        }
    }

    private void processMessage(MethodCall methodCall, MethodChannel.Result result) {
        switch (methodCall.method) {
            case "requestPermission":
                requestPermission(result);
                break;

            case "checkPermission":
                result.success(checkPermission());
                break;

            case "setAuthToken":
                setAuthToken((String) methodCall.arguments);
                result.success(null);
                break;

            case "getAuthToken":
                result.success(getAuthToken());
                break;

            case "getStoredScreeningResults":
                result.success(getStoredScreeningResults());
                break;

            case "clearStoredScreeningResults":
                clearStoredScreeningResults();
                result.success(null);
                break;
        }
    }

    /**
     * Retrieves all stored screening results from SharedPreferences.
     * These are calls that were screened while the Flutter app was not running.
     *
     * @return List of maps containing screening result data
     */
    private java.util.List<java.util.Map<String, Object>> getStoredScreeningResults() {
        java.util.List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
        SharedPreferences prefs = getPreferences(this);

        java.util.Map<String, ?> all = prefs.getAll();
        for (java.util.Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getKey().startsWith("screened_call_") && entry.getValue() instanceof String) {
                String value = (String) entry.getValue();
                String[] parts = value.split("\\|");

                if (parts.length == 4) {
                    java.util.Map<String, Object> data = new java.util.HashMap<>();
                    data.put("phoneNumber", parts[0]);
                    data.put("wasBlocked", Boolean.parseBoolean(parts[1]));
                    data.put("votes", Integer.parseInt(parts[2]));
                    data.put("timestamp", Long.parseLong(parts[3]));
                    results.add(data);
                }
            }
        }

        return results;
    }

    /**
     * Clears all stored screening results from SharedPreferences.
     * Called after syncing to the Flutter database.
     */
    private void clearStoredScreeningResults() {
        SharedPreferences prefs = getPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();

        java.util.Map<String, ?> all = prefs.getAll();
        for (String key : all.keySet()) {
            if (key.startsWith("screened_call_")) {
                editor.remove(key);
            }
        }

        editor.apply();
    }

    private String getAuthToken() {
        SharedPreferences prefs = getPreferences(this);
        return prefs.getString("auth_token", null);
    }

    private void setAuthToken(String authToken) {
        SharedPreferences prefs = getPreferences(this);
        prefs.edit().putString("auth_token", authToken).apply();
    }

    public static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences("de.haumacher.phoneblock_mobile.Preferences", Context.MODE_PRIVATE);
    }

    private static final int REQUEST_PERMISSION_ID = 1;
    private MethodChannel.Result _requestPermissionResult;

    public boolean checkPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            RoleManager roleManager = (RoleManager) getSystemService(ROLE_SERVICE);
            return roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
        } else {
            // Older Android versions don't require this permission
            return true;
        }
    }

    public void requestPermission(MethodChannel.Result result) {
        Log.d(MainActivity.class.getName(), "requestPermission");

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            _requestPermissionResult = result;
            RoleManager roleManager = (RoleManager) getSystemService(ROLE_SERVICE);
            Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING);
            startActivityForResult(intent, REQUEST_PERMISSION_ID);
        } else {
            result.success(Boolean.TRUE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PERMISSION_ID) {
            if (_requestPermissionResult != null) {
                if (resultCode == android.app.Activity.RESULT_OK) {
                    Log.d(MainActivity.class.getName(), "Permission granted.");
                    _requestPermissionResult.success(Boolean.TRUE);
                } else {
                    Log.d(MainActivity.class.getName(), "Permission denied.");
                    _requestPermissionResult.success(Boolean.FALSE);
                }
                _requestPermissionResult = null;
            } else {
                Log.w(MainActivity.class.getName(), "Permission result received but no callback registered. " +
                        "This can happen if permission was granted to a different app or activity was recreated.");
            }
        }
    }
}