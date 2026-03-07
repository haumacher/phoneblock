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

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

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
        boolean blockRanges = prefs.getBoolean("block_ranges", true);
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

        // Check local wildcard blocking rules
        String matchedPrefix = null;
        String wildcardPrefixesJson = prefs.getString("wildcard_prefixes", "[]");
        try {
            org.json.JSONArray wildcardPrefixes = new org.json.JSONArray(wildcardPrefixesJson);
            for (int i = 0; i < wildcardPrefixes.length(); i++) {
                String prefix = wildcardPrefixes.getString(i);
                if (number.startsWith(prefix)) {
                    matchedPrefix = prefix;
                    break;
                }
            }
        } catch (org.json.JSONException e) {
            Log.w(CallChecker.class.getName(), "Failed to parse wildcard prefixes", e);
        }

        // Check local blocklist cache
        int localVotes = lookupLocalBlocklist(number);

        // Determine if we should block immediately based on local data
        final boolean locallyBlocked = matchedPrefix != null || localVotes >= minVotes;

        if (locallyBlocked) {
            Log.d(CallChecker.class.getName(), "onScreenCall: Blocking locally: " + number
                + (matchedPrefix != null ? " (wildcard " + matchedPrefix + "*)" : " (" + localVotes + " local votes)"));
            respondToCall(callDetails, new CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(true)
                .setSkipNotification(true)
                .build());
        }

        // No local spam indicators at all — accept without querying the API
        if (!locallyBlocked && localVotes <= 0) {
            Log.d(CallChecker.class.getName(), "onScreenCall: No local spam data, accepting: " + number);
            acceptCall(callDetails);
            return;
        }

        // Query the API (single location):
        // - If locally blocked: for server tracking + enriching the call report
        // - If local votes > 0 but below threshold: for a definitive blocking decision
        final String blockedPrefix = matchedPrefix;
        final int blockedLocalVotes = localVotes;
        AtomicBoolean canceled = new AtomicBoolean();
        final ScheduledFuture<?>[] timeoutFuture = new ScheduledFuture<?>[1];

        ScheduledFuture<?> queryFuture = _pool.schedule(() -> {
            try {
                JSONObject json = queryPhoneBlock(number, authToken);
                boolean archived = json.getBoolean("archived");
                int votes = json.getInt("votes");
                int votesWildcard = json.optInt("votesWildcard", 0);
                String rating = json.optString("rating", null);
                String label = json.optString("label", null);
                String location = json.optString("location", null);

                if (locallyBlocked) {
                    // Already responded — just report with enriched API data
                    Handler.createAsync(Looper.getMainLooper()).post(() -> {
                        if (blockedPrefix != null) {
                            MainActivity.reportScreenedCall(CallChecker.this, rawNumber, true, votes, votesWildcard, "WILDCARD", label, location, blockedPrefix);
                        } else {
                            MainActivity.reportScreenedCall(CallChecker.this, rawNumber, true, votes, votesWildcard, rating, label, location);
                        }
                    });
                    return;
                }

                // Not locally blocked — decide based on API response
                final boolean shouldBlock = (votes >= minVotes && !archived)
                    || (blockRanges && votesWildcard >= minRangeVotes);

                if (canceled.compareAndSet(false, true)) {
                    if (timeoutFuture[0] != null) {
                        timeoutFuture[0].cancel(false);
                    }
                    Handler.createAsync(Looper.getMainLooper()).post(() -> {
                        if (shouldBlock) {
                            Log.d(CallChecker.class.getName(), "onScreenCall: Blocking by API: " + number + " (" + votes + " votes, " + votesWildcard + " range votes)");
                            respondToCall(callDetails, new CallResponse.Builder()
                                .setDisallowCall(true)
                                .setRejectCall(true)
                                .setSkipCallLog(true)
                                .setSkipNotification(true)
                                .build());
                        } else {
                            Log.d(CallChecker.class.getName(), "onScreenCall: Accepting by API: " + number + " (" + votes + " votes, " + votesWildcard + " range votes)");
                            acceptCall(callDetails);
                        }
                        MainActivity.reportScreenedCall(CallChecker.this, rawNumber, shouldBlock, votes, votesWildcard, rating, label, location);
                    });
                }
            } catch (Exception e) {
                Log.d(CallChecker.class.getName(), "onScreenCall: API query failed: " + number, e);

                if (locallyBlocked) {
                    // Already responded — report with local data only
                    Handler.createAsync(Looper.getMainLooper()).post(() -> {
                        if (blockedPrefix != null) {
                            MainActivity.reportScreenedCall(CallChecker.this, rawNumber, true, 0, 0, "WILDCARD", null, null, blockedPrefix);
                        } else {
                            MainActivity.reportScreenedCall(CallChecker.this, rawNumber, true, blockedLocalVotes, 0, null, null, null);
                        }
                    });
                    return;
                }

                if (canceled.compareAndSet(false, true)) {
                    if (timeoutFuture[0] != null) {
                        timeoutFuture[0].cancel(false);
                    }
                    Handler.createAsync(Looper.getMainLooper()).post(() -> acceptCall(callDetails));
                }
            }
        }, 0, TimeUnit.MILLISECONDS);

        // Timeout only needed when the API decides (not locally blocked)
        if (!locallyBlocked) {
            timeoutFuture[0] = _pool.schedule(() -> {
                if (canceled.compareAndSet(false, true)) {
                    Handler.createAsync(Looper.getMainLooper()).post(() -> {
                        Log.d(CallChecker.class.getName(), "onScreenCall: API timeout, accepting: " + number);
                        acceptCall(callDetails);
                    });
                }
                queryFuture.cancel(true);
            }, 4500, TimeUnit.MILLISECONDS);
        }
    }

    private @NonNull JSONObject queryPhoneBlock(String number, String authToken) throws IOException, JSONException {
        SharedPreferences prefs = MainActivity.getPreferences(this);
        String queryUrl = prefs.getString("query_url", "https://phoneblock.net/phoneblock/api/check?sha1={sha1}&format=json");
        String appVersion = prefs.getString("app_version", "unknown");

        // Compute SHA1 hash of the phone number for privacy
        String sha1Hash = PhoneNumberUtils.computeSHA1(number);

        String urlStr = queryUrl.replace("{sha1}", sha1Hash);

        // Append prefix hashes for range-based spam detection
        if (number.length() > 2) {
            String prefix10Hash = PhoneNumberUtils.computeSHA1(number.substring(0, number.length() - 1));
            String prefix100Hash = PhoneNumberUtils.computeSHA1(number.substring(0, number.length() - 2));
            urlStr += "&prefix10=" + prefix10Hash + "&prefix100=" + prefix100Hash;
        }

        URL url = new URL(urlStr);
        URLConnection connection = url.openConnection();
        connection.setRequestProperty("Authorization", "Bearer " + authToken);
        connection.setRequestProperty("User-Agent", "PhoneBlockMobile/" + appVersion);
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

    /**
     * Looks up a phone number in the local blocklist cache.
     *
     * @param number The normalized phone number in E.164 format.
     * @return The vote count if found, -1 if not found or on error.
     */
    private int lookupLocalBlocklist(String number) {
        try {
            java.io.File dbFile = getDatabasePath("screened_calls.db");
            if (!dbFile.exists()) {
                return -1;
            }

            SQLiteDatabase db = SQLiteDatabase.openDatabase(
                dbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            try {
                Cursor cursor = db.rawQuery(
                    "SELECT votes FROM blocklist WHERE phone = ?",
                    new String[]{number});
                try {
                    if (cursor.moveToFirst()) {
                        return cursor.getInt(0);
                    }
                    return -1;
                } finally {
                    cursor.close();
                }
            } finally {
                db.close();
            }
        } catch (Exception e) {
            Log.w(CallChecker.class.getName(), "Error looking up local blocklist", e);
            return -1;
        }
    }

}
