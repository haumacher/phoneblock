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
     * @param phoneNumber The phone number that was screened
     * @param wasBlocked true if the call was blocked as SPAM, false if accepted
     * @param votes Number of votes from PhoneBlock database
     */
    public static void reportScreenedCall(String phoneNumber, boolean wasBlocked, int votes) {
        if (_instance != null && _instance._channel != null) {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("phoneNumber", phoneNumber);
            data.put("wasBlocked", wasBlocked);
            data.put("votes", votes);
            data.put("timestamp", System.currentTimeMillis());

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
        }
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