package de.haumacher.phoneblock_mobile;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;

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
    private static final String NOTIFICATION_CHANNEL_ID = "pending_calls";
    private static final int NOTIFICATION_ID = 1;

    /** Constant for infinite retention period (keep all calls) */
    public static final int RETENTION_INFINITE = -1;

    /** Default retention period in days (3 days for privacy) */
    public static final int RETENTION_DEFAULT = 3;

    private MethodChannel _channel;
    private static MainActivity _instance;

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);

        _instance = this;
        _channel = new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CALL_CHECKER_CHANNEL);
        _channel.setMethodCallHandler(this::processMessage);

        // Create notification channel for pending calls
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.notification_channel_description));
            channel.setShowBadge(true);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Shows or updates a notification displaying the count of pending screened calls.
     * @param context Application context
     * @param count Number of pending calls
     */
    private static void updatePendingCallsNotification(Context context, int count) {
        NotificationManager notificationManager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (count == 0) {
            // No pending calls - remove notification
            notificationManager.cancel(NOTIFICATION_ID);
            return;
        }

        // Create intent to launch app when notification is tapped
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        // Build notification with localized strings
        String title = context.getResources().getQuantityString(
            R.plurals.pending_calls_notification_title,
            count,
            count
        );
        String text = context.getString(R.string.notification_tap_to_open);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(title)
            .setContentText(text)
            .setNumber(count)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)  // Make it persistent
            .setAutoCancel(false)  // Don't dismiss on tap
            .setContentIntent(pendingIntent);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
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
     * @param rating The rating/category of the call (e.g., "C_PING", "E_ADVERTISING", null for legitimate)
     */
    public static void reportScreenedCall(Context context, String phoneNumber, boolean wasBlocked, int votes, String rating) {
        long timestamp = System.currentTimeMillis();

        // Check if Flutter is active
        if (_instance != null && _instance._channel != null) {
            // Flutter is running - send directly via MethodChannel
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("phoneNumber", phoneNumber);
            data.put("wasBlocked", wasBlocked);
            data.put("votes", votes);
            data.put("timestamp", timestamp);
            if (rating != null) {
                data.put("rating", rating);
            }

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
                if (rating != null) {
                    callJson.put("rating", rating);
                }

                // Add to array
                callsArray.put(callJson);

                // Save back to SharedPreferences
                prefs.edit().putString("pending_screened_calls", callsArray.toString()).apply();

                // Only show notification for blocked SPAM calls
                // Regular calls are already notified by the phone app
                if (wasBlocked) {
                    // Count only blocked calls for notification
                    int blockedCount = 0;
                    for (int i = 0; i < callsArray.length(); i++) {
                        if (callsArray.getJSONObject(i).getBoolean("wasBlocked")) {
                            blockedCount++;
                        }
                    }
                    updatePendingCallsNotification(context, blockedCount);
                    Log.d(MainActivity.class.getName(), "Stored blocked SPAM call for later sync: " + phoneNumber + " (blocked count: " + blockedCount + ")");
                } else {
                    Log.d(MainActivity.class.getName(), "Stored non-blocked call for later sync: " + phoneNumber);
                }
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

            case "setQueryUrl":
                setQueryUrl((String) methodCall.arguments);
                result.success(null);
                break;

            case "getStoredScreeningResults":
                result.success(getStoredScreeningResults());
                break;

            case "clearStoredScreeningResults":
                clearStoredScreeningResults();
                result.success(null);
                break;

            case "getMinVotes":
                result.success(getMinVotes());
                break;

            case "setMinVotes":
                setMinVotes((Integer) methodCall.arguments);
                result.success(null);
                break;

            case "getBlockRanges":
                result.success(getBlockRanges());
                break;

            case "setBlockRanges":
                setBlockRanges((Boolean) methodCall.arguments);
                result.success(null);
                break;

            case "getMinRangeVotes":
                result.success(getMinRangeVotes());
                break;

            case "setMinRangeVotes":
                setMinRangeVotes((Integer) methodCall.arguments);
                result.success(null);
                break;

            case "getRetentionDays":
                result.success(getRetentionDays());
                break;

            case "setRetentionDays":
                setRetentionDays((Integer) methodCall.arguments);
                result.success(null);
                break;

            case "setAppVersion":
                setAppVersion((String) methodCall.arguments);
                result.success(null);
                break;

            case "getThemeMode":
                result.success(getThemeMode());
                break;

            case "setThemeMode":
                setThemeMode((String) methodCall.arguments);
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
                if (callJson.has("rating")) {
                    data.put("rating", callJson.getString("rating"));
                }
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

        // Clear the notification since all pending calls have been synced
        updatePendingCallsNotification(this, 0);
        Log.d(MainActivity.class.getName(), "Cleared pending calls notification after syncing");
    }

    private String getAuthToken() {
        SharedPreferences prefs = getPreferences(this);
        return prefs.getString("auth_token", null);
    }

    private void setAuthToken(String authToken) {
        SharedPreferences prefs = getPreferences(this);
        prefs.edit().putString("auth_token", authToken).apply();
    }

    private void setQueryUrl(String queryUrl) {
        SharedPreferences prefs = getPreferences(this);
        prefs.edit().putString("query_url", queryUrl).apply();
        Log.d(MainActivity.class.getName(), "setQueryUrl: " + queryUrl);
    }

    private int getMinVotes() {
        SharedPreferences prefs = getPreferences(this);
        int value = prefs.getInt("min_votes", 4);
        Log.d(MainActivity.class.getName(), "getMinVotes: " + value);
        return value;
    }

    private void setMinVotes(int minVotes) {
        SharedPreferences prefs = getPreferences(this);
        prefs.edit().putInt("min_votes", minVotes).apply();
        Log.d(MainActivity.class.getName(), "setMinVotes: " + minVotes);
    }

    private boolean getBlockRanges() {
        SharedPreferences prefs = getPreferences(this);
        boolean value = prefs.getBoolean("block_ranges", true);
        Log.d(MainActivity.class.getName(), "getBlockRanges: " + value);
        return value;
    }

    private void setBlockRanges(boolean blockRanges) {
        SharedPreferences prefs = getPreferences(this);
        prefs.edit().putBoolean("block_ranges", blockRanges).apply();
        Log.d(MainActivity.class.getName(), "setBlockRanges: " + blockRanges);
    }

    private int getMinRangeVotes() {
        SharedPreferences prefs = getPreferences(this);
        int value = prefs.getInt("min_range_votes", 10);
        Log.d(MainActivity.class.getName(), "getMinRangeVotes: " + value);
        return value;
    }

    private void setMinRangeVotes(int minRangeVotes) {
        SharedPreferences prefs = getPreferences(this);
        prefs.edit().putInt("min_range_votes", minRangeVotes).apply();
        Log.d(MainActivity.class.getName(), "setMinRangeVotes: " + minRangeVotes);
    }

    private int getRetentionDays() {
        SharedPreferences prefs = getPreferences(this);
        int value = prefs.getInt("retention_days", RETENTION_DEFAULT);
        Log.d(MainActivity.class.getName(), "getRetentionDays: " + value);
        return value;
    }

    private void setRetentionDays(int retentionDays) {
        SharedPreferences prefs = getPreferences(this);
        prefs.edit().putInt("retention_days", retentionDays).apply();
        Log.d(MainActivity.class.getName(), "setRetentionDays: " + retentionDays);
    }

    private void setAppVersion(String appVersion) {
        SharedPreferences prefs = getPreferences(this);
        prefs.edit().putString("app_version", appVersion).apply();
        Log.d(MainActivity.class.getName(), "setAppVersion: " + appVersion);
    }

    /**
     * Gets the theme mode preference.
     * @return The theme mode: "system", "light", or "dark". Defaults to "system".
     */
    private String getThemeMode() {
        SharedPreferences prefs = getPreferences(this);
        String value = prefs.getString("theme_mode", "system");
        Log.d(MainActivity.class.getName(), "getThemeMode: " + value);
        return value;
    }

    /**
     * Sets the theme mode preference.
     * @param themeMode The theme mode to set: "system", "light", or "dark".
     */
    private void setThemeMode(String themeMode) {
        SharedPreferences prefs = getPreferences(this);
        prefs.edit().putString("theme_mode", themeMode).apply();
        Log.d(MainActivity.class.getName(), "setThemeMode: " + themeMode);
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