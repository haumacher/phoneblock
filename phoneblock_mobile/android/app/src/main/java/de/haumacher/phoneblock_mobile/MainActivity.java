package de.haumacher.phoneblock_mobile;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
     * If Flutter is active, sends directly via MethodChannel for immediate storage.
     * If Flutter is NOT active, stores in SharedPreferences for later sync on app launch.
     * This ensures no screening results are lost regardless of app state.
     *
     * @param context The context (usually the CallChecker service)
     * @param phoneNumber The phone number that was screened
     * @param wasBlocked true if the call was blocked as SPAM, false if accepted
     * @param votes Number of votes from PhoneBlock database
     */
    public static void reportScreenedCall(Context context, String phoneNumber, boolean wasBlocked, int votes) {
        long timestamp = System.currentTimeMillis();

        // Check if Flutter is active
        if (_instance != null && _instance._channel != null) {
            // Flutter is running - send directly via MethodChannel
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("phoneNumber", phoneNumber);
            data.put("wasBlocked", wasBlocked);
            data.put("votes", votes);
            data.put("timestamp", timestamp);

            _instance._channel.invokeMethod("onCallScreened", data);
        } else {
            // Flutter is NOT running - store in SharedPreferences for later sync
            try {
                SharedPreferences prefs = getPreferences(context);

                // Get existing array of stored calls
                String storedCallsJson = prefs.getString("pending_screened_calls", "[]");
                JSONArray callsArray = new JSONArray(storedCallsJson);

                // Create new call JSON object
                JSONObject callJson = new JSONObject();
                callJson.put("phoneNumber", phoneNumber);
                callJson.put("wasBlocked", wasBlocked);
                callJson.put("votes", votes);
                callJson.put("timestamp", timestamp);

                // Add to array
                callsArray.put(callJson);

                // Save back to SharedPreferences
                prefs.edit().putString("pending_screened_calls", callsArray.toString()).apply();

                Log.d(MainActivity.class.getName(), "Stored screening result for later sync: " + phoneNumber);
            } catch (JSONException e) {
                Log.e(MainActivity.class.getName(), "Error storing screening result in SharedPreferences", e);
            }
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

        try {
            SharedPreferences prefs = getPreferences(this);
            String storedCallsJson = prefs.getString("pending_screened_calls", "[]");
            JSONArray callsArray = new JSONArray(storedCallsJson);

            for (int i = 0; i < callsArray.length(); i++) {
                JSONObject callJson = callsArray.getJSONObject(i);

                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put("phoneNumber", callJson.getString("phoneNumber"));
                data.put("wasBlocked", callJson.getBoolean("wasBlocked"));
                data.put("votes", callJson.getInt("votes"));
                data.put("timestamp", callJson.getLong("timestamp"));
                results.add(data);
            }
        } catch (JSONException e) {
            Log.e(MainActivity.class.getName(), "Error reading stored screening results", e);
        }

        return results;
    }

    /**
     * Clears all stored screening results from SharedPreferences.
     * Called after syncing to the Flutter database.
     */
    private void clearStoredScreeningResults() {
        SharedPreferences prefs = getPreferences(this);
        prefs.edit().remove("pending_screened_calls").apply();
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