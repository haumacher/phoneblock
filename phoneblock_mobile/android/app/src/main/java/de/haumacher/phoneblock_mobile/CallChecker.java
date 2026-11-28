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

import de.haumacher.phoneblock_mobile.log.LogContext;
import de.haumacher.phoneblock_mobile.log.LogSanitizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiresApi(api = Build.VERSION_CODES.N)
public class CallChecker extends CallScreeningService {

    private static final Logger LOG = LoggerFactory.getLogger(CallChecker.class);

    ScheduledExecutorService _pool;

    @Override
    public void onCreate() {
        super.onCreate();
        LogContext.init(getApplicationContext());

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

        if (handle == null) {
            Log.d(CallChecker.class.getName(), "onScreenCall: No handle (e.g. withheld number), accepting.");
            acceptCall(callDetails);
            return;
        }

        SharedPreferences prefs = MainActivity.getPreferences(this);
        String authToken = prefs.getString("auth_token", null);
        int minVotes = prefs.getInt("min_votes", 4);
        boolean blockRanges = prefs.getBoolean("block_ranges", true);
        int minRangeVotes = prefs.getInt("min_range_votes", 10);

        if (authToken == null) {
            Log.d(CallChecker.class.getName(), "onScreenCall: No PhoneBlock authorization, cannot screen call.");
            acceptCall(callDetails);
            return;
        }

        String rawNumber = handle.getSchemeSpecificPart();

        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        String countryIso = tm != null ? tm.getNetworkCountryIso() : null;
        String number = PhoneNumberUtils.normalizeToInternationalFormat(rawNumber, countryIso);
        if (number == null) {
            Log.w(CallChecker.class.getName(), "Unable to normalize number: " + rawNumber);
            acceptCall(callDetails);
            return;
        }

        AtomicBoolean responded = new AtomicBoolean();
        final ScheduledFuture<?>[] timeoutFuture = new ScheduledFuture<?>[1];

        // Background thread: query API, fall back to local data, decide once
        ScheduledFuture<?> queryFuture = _pool.schedule(() -> {
            int votes = 0;
            int votesWildcard = 0;
            boolean archived = false;
            String rating = null;
            String label = null;
            String location = null;
            boolean blackListed = false;
            boolean whiteListed = false;

            try {
                JSONObject json = queryPhoneBlock(number, authToken);
                votes = json.getInt("votes");
                votesWildcard = json.optInt("votesWildcard", 0);
                archived = json.getBoolean("archived");
                rating = json.optString("rating", null);
                label = json.optString("label", null);
                location = json.optString("location", null);
                blackListed = json.optBoolean("blackListed", false);
                whiteListed = json.optBoolean("whiteListed", false);
            } catch (Exception e) {
                LOG.error("onScreenCall: API failed, using local data: number={}", LogSanitizer.hashPhone(number), e);
                int localVotes = lookupLocalBlocklist(number);
                if (localVotes > 0) {
                    votes = localVotes;
                }
            }

            final int fVotes = votes;
            final int fVotesWildcard = votesWildcard;
            final boolean fArchived = archived;
            final String fRating = rating;
            final String fLabel = label;
            final String fLocation = location;
            final boolean fBlackListed = blackListed;
            final boolean fWhiteListed = whiteListed;

            if (responded.compareAndSet(false, true)) {
                if (timeoutFuture[0] != null) {
                    timeoutFuture[0].cancel(false);
                }
                List<String> wildcardPrefixes = loadLocalWildcardPrefixes();
                Handler.createAsync(Looper.getMainLooper()).post(() ->
                    decideAndRespond(callDetails, rawNumber, number,
                        fVotes, fVotesWildcard, fArchived, fRating, fLabel, fLocation, fBlackListed, fWhiteListed,
                        minVotes, blockRanges, minRangeVotes, wildcardPrefixes));
            }
        }, 0, TimeUnit.MILLISECONDS);

        // Timeout: use local data only
        timeoutFuture[0] = _pool.schedule(() -> {
            if (responded.compareAndSet(false, true)) {
                queryFuture.cancel(true);
                int localVotes = Math.max(0, lookupLocalBlocklist(number));
                List<String> wildcardPrefixes = loadLocalWildcardPrefixes();

                Handler.createAsync(Looper.getMainLooper()).post(() ->
                    decideAndRespond(callDetails, rawNumber, number,
                        localVotes, 0, false, null, null, null, false, false,
                        minVotes, blockRanges, minRangeVotes, wildcardPrefixes));
            }
        }, 4500, TimeUnit.MILLISECONDS);
    }

    /**
     * Single decision point: checks votes, range votes, and wildcard filters,
     * then either blocks or accepts the call and reports the result.
     */
    private void decideAndRespond(@NonNull Call.Details callDetails, String rawNumber, String number,
            int votes, int votesWildcard, boolean archived, String rating, String label, String location, boolean blackListed,
            boolean whiteListed, int minVotes, boolean blockRanges, int minRangeVotes, List<String> wildcardPrefixes) {

        ScreeningDecision decision = ScreeningDecision.decide(number, votes, votesWildcard, archived, blackListed, whiteListed,
            minVotes, blockRanges, minRangeVotes, wildcardPrefixes);
        boolean block = decision.block;
        String matchedPrefix = decision.matchedPrefix;

        LOG.info("decision={} reason={} number={}",
            block ? "block" : "allow",
            rating != null ? rating : (matchedPrefix != null ? "WILDCARD" : ""),
            LogSanitizer.hashPhone(number));

        if (block) {
            Log.d(CallChecker.class.getName(), "onScreenCall: Blocking: " + LogSanitizer.hashPhone(number)
                + " (votes=" + votes + ", rangeVotes=" + votesWildcard
                + (matchedPrefix != null ? ", wildcard=*" : "") + ")");
            respondToCall(callDetails, new CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(true)
                .setSkipNotification(true)
                .build());
        } else {
            Log.d(CallChecker.class.getName(), "onScreenCall: Accepting: " + LogSanitizer.hashPhone(number)
                + " (votes=" + votes + ", rangeVotes=" + votesWildcard + ")");
            acceptCall(callDetails);
        }

        if (matchedPrefix != null) {
            MainActivity.reportScreenedCall(this, rawNumber, block, votes, votesWildcard, "WILDCARD", label, location, matchedPrefix, blackListed);
        } else {
            MainActivity.reportScreenedCall(this, rawNumber, block, votes, votesWildcard, rating, label, location, null, blackListed);
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
            SQLiteDatabase db = openLocalDatabase();
            if (db == null) {
                return -1;
            }
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

    /**
     * Loads the user's personal wildcard prefixes from the local SQLite store.
     *
     * <p>Read from the {@code wildcard_blocks} table at screening time, the table the Flutter side
     * reconciles with the server (also from the WorkManager background task, which has no
     * Activity and no MethodChannel). A rule synced in the background therefore blocks without
     * the app being opened (#487).</p>
     *
     * @return The international-format prefixes, empty if none are stored or on error.
     */
    private List<String> loadLocalWildcardPrefixes() {
        List<String> prefixes = new ArrayList<>();
        try {
            SQLiteDatabase db = openLocalDatabase();
            if (db == null) {
                return prefixes;
            }
            try {
                Cursor cursor = db.rawQuery("SELECT prefix FROM wildcard_blocks", null);
                try {
                    while (cursor.moveToNext()) {
                        prefixes.add(cursor.getString(0));
                    }
                } finally {
                    cursor.close();
                }
            } finally {
                db.close();
            }
        } catch (Exception e) {
            LOG.error("Error loading local wildcard prefixes", e);
        }
        return prefixes;
    }

    /**
     * Opens the app's SQLite database (written by the Flutter side) read-only.
     *
     * @return The database, or {@code null} if it does not exist yet.
     */
    private SQLiteDatabase openLocalDatabase() {
        java.io.File dbFile = getDatabasePath("screened_calls.db");
        if (!dbFile.exists()) {
            return null;
        }
        return SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);
    }

}
