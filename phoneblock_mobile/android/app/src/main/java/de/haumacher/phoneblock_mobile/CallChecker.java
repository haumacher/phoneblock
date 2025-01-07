package de.haumacher.phoneblock_mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

public class CallChecker extends CallScreeningService {

    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int direction = callDetails.getCallDirection();
            if (direction != Call.Details.DIRECTION_INCOMING) {
                acceptCall(callDetails);
                return;
            }
        }

        Uri handle = callDetails.getHandle();
        Log.d(CallChecker.class.getName(), "onScreenCall: " + handle);

        SharedPreferences prefs = MainActivity.getPreferences(this);
        String authToken = prefs.getString("auth_token", null);
        int minVotes = prefs.getInt("min_votes", 4);

        if (authToken == null) {
            // Not logged in, no screening possible.
            Log.d(CallChecker.class.getName(), "onScreenCall: No PhoneBlock authorization, cannot screen call.");
            acceptCall(callDetails);
            return;
        }

        String number = handle.getHost();

        try {
            JSONObject json = queryPhoneBlock(number, authToken);
            boolean archived = json.getBoolean("archived");
            int votes = json.getInt("votes");

            if (votes >= minVotes && !archived) {
                Log.d(CallChecker.class.getName(), "onScreenCall: Blocking SPAM call: " + number + " (" + votes + " votes)");
                respondToCall(callDetails, new CallResponse.Builder().setRejectCall(true).build());
                return;
            } else {
                Log.d(CallChecker.class.getName(), "onScreenCall: Letting call pass: " + number + " (" + votes + " votes)");
            }
        } catch (MalformedURLException e) {
            Log.d(CallChecker.class.getName(), "onScreenCall: Invalid PhoneBlock URL, cannot screen call: " + number);
        } catch (IOException ex) {
            Log.d(CallChecker.class.getName(), "onScreenCall: Failed to query PhoneBlock, cannot screen call: " + number, ex);
        } catch (JSONException ex) {
            Log.d(CallChecker.class.getName(), "onScreenCall: Invalid PhoneBlock result, cannot screen call: " + number, ex);
        }

        acceptCall(callDetails);
    }

    private static @NonNull JSONObject queryPhoneBlock(String number, String authToken) throws IOException, JSONException {
        URL url = new URL("https://phoneblock.net/phoneblock/api/num/" + number + "?format=json");
        URLConnection connection = url.openConnection();
        connection.setRequestProperty("Authorization", "Bearer: " + authToken);

        StringBuilder result = new StringBuilder();
        try (InputStream in = connection.getInputStream()) {
            try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                char[] buffer = new char[1024];
                while (true) {
                    int direct = r.read(buffer);
                    if (direct < 0) {
                        break;
                    }
                    result.append(buffer, 0, direct);
                }
            }
        }
        JSONObject json = new JSONObject(result.toString());
        return json;
    }

    private void acceptCall(@NonNull Call.Details callDetails) {
        respondToCall(callDetails, new CallResponse.Builder().build());
    }

}
