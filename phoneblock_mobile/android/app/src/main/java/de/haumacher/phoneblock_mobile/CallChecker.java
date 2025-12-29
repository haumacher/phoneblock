package de.haumacher.phoneblock_mobile;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.telephony.TelephonyManager;
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
        boolean blockRanges = prefs.getBoolean("block_ranges", false);
        int minRangeVotes = prefs.getInt("min_range_votes", 10);

        if (authToken == null) {
            // Not logged in, no screening possible.
            Log.d(CallChecker.class.getName(), "onScreenCall: No PhoneBlock authorization, cannot screen call.");
            acceptCall(callDetails);
            return;
        }

        String rawNumber = handle.getSchemeSpecificPart();

        // Get country code for normalization
        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        String countryIso = tm != null ? tm.getNetworkCountryIso() : null;

        // Normalize to international format for consistent hashing
        String number = PhoneNumberUtils.normalizeToInternationalFormat(rawNumber, countryIso);
        if (number == null) {
            // Unable to normalize, accept the call
            Log.w(CallChecker.class.getName(), "Unable to normalize number: " + rawNumber);
            acceptCall(callDetails);
            return;
        }

        AtomicBoolean canceled = new AtomicBoolean();

        // Array to hold timeout future reference (needs to be final for lambda access)
        final ScheduledFuture<?>[] timeoutFuture = new ScheduledFuture<?>[1];

        ScheduledFuture<?> queryFuture = _pool.schedule(() -> {
            try {
                JSONObject json = queryPhoneBlock(number, authToken);
                boolean archived = json.getBoolean("archived");
                int votes = json.getInt("votes");
                int votesWildcard = json.optInt("votesWildcard", 0);
                String rating = json.optString("rating", null);

                // Check if number should be blocked based on direct votes or range votes
                final boolean shouldBlock;
                final String blockReason;

                if (votes >= minVotes && !archived) {
                    shouldBlock = true;
                    blockReason = votes + " votes";
                } else if (blockRanges && votesWildcard >= minRangeVotes) {
                    shouldBlock = true;
                    blockReason = votesWildcard + " range votes";
                } else {
                    shouldBlock = false;
                    blockReason = "";
                }

                if (shouldBlock) {
                    if (canceled.compareAndSet(false, true)) {
                        // Cancel timeout since we got a result
                        if (timeoutFuture[0] != null) {
                            timeoutFuture[0].cancel(false);
                        }
                        Handler.createAsync(Looper.getMainLooper()).post(() -> {
                            Log.d(CallChecker.class.getName(), "onScreenCall: Blocking SPAM call: " + number + " (" + blockReason + ", rating: " + rating + ")");
                            respondToCall(callDetails, new CallResponse.Builder().setDisallowCall(true).setRejectCall(true).build());
                            // Report blocked call (persists even when app is not running)
                            MainActivity.reportScreenedCall(CallChecker.this, number, true, votes, rating);
                        });
                    }
                    return;
                } else {
                    if (canceled.compareAndSet(false, true)) {
                        // Cancel timeout since we got a result
                        if (timeoutFuture[0] != null) {
                            timeoutFuture[0].cancel(false);
                        }
                        Handler.createAsync(Looper.getMainLooper()).post(() -> {
                            Log.d(CallChecker.class.getName(), "onScreenCall: Letting call pass: " + number + " (" + votes + " votes)");
                            acceptCall(callDetails);
                            // Report accepted call (persists even when app is not running)
                            MainActivity.reportScreenedCall(CallChecker.this, number, false, votes, rating);
                        });
                    }
                    return;
                }
            } catch (MalformedURLException e) {
                Log.d(CallChecker.class.getName(), "onScreenCall: Invalid PhoneBlock URL, cannot screen call: " + number);
            } catch (IOException ex) {
                Log.d(CallChecker.class.getName(), "onScreenCall: failed to query PhoneBlock, cannot screen call: " + number, ex);
            } catch (JSONException ex) {
                Log.d(CallChecker.class.getName(), "onScreenCall: Invalid PhoneBlock result, cannot screen call: " + number, ex);
            }

            if (canceled.compareAndSet(false, true)) {
                // Cancel timeout since we're handling the error
                if (timeoutFuture[0] != null) {
                    timeoutFuture[0].cancel(false);
                }
                Handler.createAsync(Looper.getMainLooper()).post(() -> {
                    acceptCall(callDetails);
                });
            }
        }, 0, TimeUnit.MILLISECONDS);

        timeoutFuture[0] = _pool.schedule(() -> {
            if (canceled.compareAndSet(false, true)) {
                Handler.createAsync(Looper.getMainLooper()).post(() -> {
                    Log.d(CallChecker.class.getName(), "onScreenCall: PhoneBlock query timeout, cannot screen call: " + number);
                    acceptCall(callDetails);
                });
            }
            queryFuture.cancel(true);
        }, 4500, TimeUnit.MILLISECONDS);
    }

    private @NonNull JSONObject queryPhoneBlock(String number, String authToken) throws IOException, JSONException {
        SharedPreferences prefs = MainActivity.getPreferences(this);
        String queryUrl = prefs.getString("query_url", "https://phoneblock.net/phoneblock/api/check?sha1={sha1}&format=json");

        // Compute SHA1 hash of the phone number for privacy
        String sha1Hash = PhoneNumberUtils.computeSHA1(number);

        URL url = new URL(queryUrl.replace("{sha1}", sha1Hash));
        URLConnection connection = url.openConnection();
        connection.setRequestProperty("Authorization", "Bearer " + authToken);
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
        } catch (IOException e) {
            // Log HTTP error details if available
            if (connection instanceof java.net.HttpURLConnection) {
                java.net.HttpURLConnection httpConn = (java.net.HttpURLConnection) connection;
                int responseCode = httpConn.getResponseCode();
                String responseMessage = httpConn.getResponseMessage();
                Log.e(CallChecker.class.getName(), "HTTP error: " + responseCode + " " + responseMessage + " for URL: " + connection.getURL());
            }
            throw e;
        }
        return result.toString();
    }

    private void acceptCall(@NonNull Call.Details callDetails) {
        respondToCall(callDetails, new CallResponse.Builder().build());
    }

}
