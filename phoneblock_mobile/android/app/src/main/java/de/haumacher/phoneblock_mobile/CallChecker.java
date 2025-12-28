package de.haumacher.phoneblock_mobile;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiresApi(api = Build.VERSION_CODES.N)
public class CallChecker extends CallScreeningService {

    ScheduledExecutorService _pool;

    @Override
    public void onCreate() {
        super.onCreate();

        _pool = Executors.newScheduledThreadPool(1);
    }

    @Override
    public void onDestroy() {
        if (_pool != null) {
            _pool.shutdown();
            _pool = null;
        }

        super.onDestroy();
    }

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

        String number = handle.getSchemeSpecificPart();

        AtomicBoolean canceled = new AtomicBoolean();

        ScheduledFuture<?> future = _pool.schedule(() -> {
            try {
                JSONObject json = queryPhoneBlock(number, authToken);
                boolean archived = json.getBoolean("archived");
                int votes = json.getInt("votes");

                if (votes >= minVotes && !archived) {
                    if (canceled.compareAndSet(false, true)) {
                        Handler.createAsync(Looper.getMainLooper()).post(() -> {
                            Log.d(CallChecker.class.getName(), "onScreenCall: Blocking SPAM call: " + number + " (" + votes + " votes)");
                            respondToCall(callDetails, new CallResponse.Builder().setDisallowCall(true).setRejectCall(true).build());
                            // Report blocked call to Flutter
                            MainActivity.reportScreenedCall(number, true, votes);
                        });
                    }
                    return;
                } else {
                    Log.d(CallChecker.class.getName(), "onScreenCall: Letting call pass: " + number + " (" + votes + " votes)");
                    // Report accepted call to Flutter
                    MainActivity.reportScreenedCall(number, false, votes);
                }
            } catch (MalformedURLException e) {
                Log.d(CallChecker.class.getName(), "onScreenCall: Invalid PhoneBlock URL, cannot screen call: " + number);
            } catch (IOException ex) {
                Log.d(CallChecker.class.getName(), "onScreenCall: Failed to query PhoneBlock, cannot screen call: " + number, ex);
            } catch (JSONException ex) {
                Log.d(CallChecker.class.getName(), "onScreenCall: Invalid PhoneBlock result, cannot screen call: " + number, ex);
            }

            if (canceled.compareAndSet(false, true)) {
                Handler.createAsync(Looper.getMainLooper()).post(() -> {
                    acceptCall(callDetails);
                });
            }
        }, 0, TimeUnit.MILLISECONDS);

        _pool.schedule(() -> {
            if (canceled.compareAndSet(false, true)) {
                Handler.createAsync(Looper.getMainLooper()).post(() -> {
                    Log.d(CallChecker.class.getName(), "onScreenCall: PhoneBlock query timeout, cannot screen call: " + number);
                    acceptCall(callDetails);
                });
            }
            future.cancel(true);
        }, 4500, TimeUnit.MILLISECONDS);
    }

    private @NonNull JSONObject queryPhoneBlock(String number, String authToken) throws IOException, JSONException {
        SharedPreferences prefs = MainActivity.getPreferences(this);
        String queryUrl = prefs.getString("query_url", "https://phoneblock.net/phoneblock/api/num/{num}?format=json");

        URL url = new URL(queryUrl.replace("{num}", number));
        URLConnection connection = url.openConnection();
        connection.setRequestProperty("Authorization", "Bearer: " + authToken);
        connection.setRequestProperty("User-Agent", "PhoneBlock mobile");
        return new JSONObject(readTextContent(connection));
    }

    private static @NonNull String readTextContent(URLConnection connection) throws IOException {
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
        return result.toString();
    }

    private void acceptCall(@NonNull Call.Details callDetails) {
        respondToCall(callDetails, new CallResponse.Builder().build());
    }

}
