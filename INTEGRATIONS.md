# PhoneBlock API Integration Guide

This guide explains how to integrate third-party applications (e.g., SMS apps, call blockers, contact managers) with the PhoneBlock spam detection service.

## Table of Contents

- [Overview](#overview)
- [Authentication Flow](#authentication-flow)
- [API Endpoints](#api-endpoints)
- [Integration Examples](#integration-examples)
- [Best Practices](#best-practices)

## Overview

PhoneBlock provides a RESTful API that allows third-party applications to:

- **Check phone numbers** for spam status using SHA1 hashes (privacy-preserving) or direct lookups
- **Report spam numbers** with ratings and optional comments
- **Manage personal black/whitelists** for authenticated users
- **Access community ratings** and spam statistics

The API supports both anonymous access and authenticated access using bearer tokens.

## Authentication Flow

### Step 1: Redirect User to PhoneBlock Login

To create an authentication token for your app, redirect the user to PhoneBlock's mobile login page:

```
https://phoneblock.net/phoneblock/mobile/login?label=YourAppName%20on%20Device
```

**URL Parameters:**
- `label` (optional): A user-visible label for the token that will appear in the user's settings page
  - Example: `"MyApp SMS Blocker on Samsung S23"`
  - Will be URL-encoded: `MyApp%20SMS%20Blocker%20on%20Samsung%20S23`

### Step 2: User Authentication

The user will be presented with login options:
- **Google OAuth** - Single sign-on with Google account
- **Email/Password** - Traditional login or account creation
- **Email Verification Link** - Passwordless login via email

The login page (`/mobile/login`) checks if the user is already authenticated via:
1. Active session cookie
2. Persistent login cookie (`pb-login`)

### Step 3: Token Creation

Once authenticated, the user sees a button to link their PhoneBlock account with your app. When clicked:

**Request:**
```http
POST /phoneblock/create-token HTTP/1.1
Host: phoneblock.net
Content-Type: application/x-www-form-urlencoded

tokenLabel=YourAppName+on+Device
```

**Response:**
```http
HTTP/1.1 302 Found
Location: /phoneblock/mobile/response?loginToken=<generated-token>
```

### Step 4: Token Reception

Your app should intercept the redirect to `/mobile/response` and extract the `loginToken` parameter:

```
/phoneblock/mobile/response?loginToken=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Implementation Options:**

#### Option A: Custom URL Scheme (Recommended for Native Apps)

TODO: Not available, the redirection URL currently cannot be configured.

Register a custom URL scheme in your app (e.g., `yourapp://`) and configure PhoneBlock to redirect there:

```
yourapp://auth?loginToken=<token>
```

#### Option B: Deep Link
Configure your app to handle `https://phoneblock.net/phoneblock/mobile/response` as a deep link.

TODO: This URL is reserved for PhoneBlock Mobile. Ask for a custom integration URL.

### Step 5: Store Token Securely

Store the token in your app's secure storage:

```java
// Android example using EncryptedSharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

MasterKey masterKey = new MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build();

SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(
    context,
    "phoneblock_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
);

sharedPreferences.edit()
    .putString("auth_token", token)
    .apply();
```

### Token Properties

- **Revocable**: Users can revoke tokens from their PhoneBlock settings page
- **Long-lived**: Tokens are valid forever (TODO: Implement token rotation protocol).
- **Rotatable**: Tokens are automatically rotated on each use when sent via Cookie (enhances security) (TODO: Implement for app integration tokens.)
- **User-Agent tracking**: Token usage tracks the User-Agent header for security monitoring - displayed in the user's settings page.
- **Label tracking**: Tokens display the device label in user settings for easy identification

## API Endpoints

### Base URLs

- **Production**: `https://phoneblock.net/phoneblock/api`
- **Test**: `https://phoneblock.net/pb-test/api` (use this for development!)

### Authentication Methods

All API endpoints support multiple Bearer Token authentication:

1. **Bearer Token** (Recommended for apps):
   ```http
   Authorization: Bearer <your-token>
   ```

2. **Anonymous** (Some APIs can be accessed anonymously):
   - No authentication header required

### 1. Check Phone Number (Privacy-Preserving)

**Endpoint:** `GET /api/check?sha1={hash}`

Check if a phone number is spam using its SHA1 hash.

**Parameters:**
- `sha1` (required): SHA1 hash of the phone number in international format (40 hex digits)
- `format` (optional): Response format - `json` (default) or `xml`

**Hash Computation:**
```python
import hashlib

def hash_phone_number(phone):
    """
    Hash a phone number for privacy-preserving lookup.

    Args:
        phone: Phone number in international format (e.g., '+4917650642602')

    Returns:
        Uppercase hex string (40 characters)
    """
    phone_bytes = phone.encode('utf-8')
    return hashlib.sha1(phone_bytes).hexdigest().upper()

# Example
hash_phone_number('+4917650642602')
# Returns: '3D1D76F0C3664E1E818C6ECCFD8843AD1F4091CC'
```

**Request Example:**
```http
GET /phoneblock/api/check?sha1=3D1D76F0C3664E1E818C6ECCFD8843AD1F4091CC&format=json HTTP/1.1
Host: phoneblock.net
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
User-Agent: MyApp/1.0.0
```

**Response:**
```json
{
  "phone": "+4917650642602",
  "votes": 42,
  "votesWildcard": 15,
  "rating": "E_ADVERTISING",
  "archived": false,
  "dateAdded": 1704067200000,
  "lastUpdate": 1704153600000
}
```

**Rating Values:**
- `A_LEGITIMATE` - Verified legitimate number
- `B_MISSED` - Missed call (potentially spam)
- `C_PING` - Ping call (hangs up immediately)
- `D_POLL` - Survey/poll call
- `E_ADVERTISING` - Advertising/marketing call
- `F_GAMBLE` - Gambling/lottery scam
- `G_FRAUD` - Fraud/scam call

**Privacy Note:** The SHA1 hash ensures that PhoneBlock never receives the actual phone number, only its hash. PhoneBlock only maintains a lookup table for hash values of numbers with SPAM reports. This is the recommended method for call screening apps to protect user privacy.

### 2. Check Phone Number (Direct Lookup)

**Endpoint:** `GET /api/num/{phone}`

Check a phone number directly (less private than hash-based lookup).

**Parameters:**
- `phone` (path): Phone number in any format (e.g., `+4917650642602`, `017650642602`, `0176 506 426 02`)
- `format` (query, optional): Response format - `json` (default) or `xml`

**Request Example:**
```http
GET /phoneblock/api/num/+4917650642602?format=json HTTP/1.1
Host: phoneblock.net
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
User-Agent: MyApp/1.0.0
```

**Response:** Same as `/api/check` endpoint

### 3. Report Phone Number

**Endpoint:** `POST /api/rate`

Submit a spam report or mark a number as legitimate.

**Authentication:** Required (Bearer token or Basic auth)

**Request Body:**
```json
{
  "phone": "+4917650642602",
  "rating": "E_ADVERTISING",
  "comment": "Called multiple times offering insurance products."
}
```

**Fields:**
- `phone` (required): Phone number in any format
- `rating` (required): One of the rating values listed above
- `comment` (optional): User comment describing the call (max 1000 characters, be civil)

**Request Example:**
```http
POST /phoneblock/api/rate HTTP/1.1
Host: phoneblock.net
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
User-Agent: MyApp/1.0.0

{
  "phone": "+4917650642602",
  "rating": "E_ADVERTISING",
  "comment": "Persistent telemarketing, called 3 times today."
}
```

**Response:**
```http
HTTP/1.1 200 OK
Content-Type: text/plain

Rating recorded.
```

### 4. Personal Blacklist Management

**Endpoint:** `GET /api/blacklist`

Retrieve user's personal blacklist (blocked numbers).

**Authentication:** Required

**Request Example:**
```http
GET /phoneblock/api/blacklist HTTP/1.1
Host: phoneblock.net
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
User-Agent: MyApp/1.0.0
```

**Response:**
```json
{
  "numbers": [
    {
      "phone": "+4917650642602",
      "label": "0176 506 426 02",
      "comment": "Telemarketing - insurance",
      "rating": "E_ADVERTISING"
    },
    {
      "phone": "+491234567890",
      "label": "0123 456 7890",
      "comment": null,
      "rating": "G_FRAUD"
    }
  ]
}
```

**Endpoint:** `DELETE /api/blacklist/{phone}`

Remove a number from the blacklist.

**Request Example:**
```http
DELETE /phoneblock/api/blacklist/+4917650642602 HTTP/1.1
Host: phoneblock.net
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response:**
```http
HTTP/1.1 200 OK
Content-Type: text/plain

Number removed from blacklist.
```

### 5. Personal Whitelist Management

**Endpoint:** `GET /api/whitelist`

Retrieve user's personal whitelist (legitimate numbers).

**Endpoint:** `DELETE /api/whitelist/{phone}`

Remove a number from the whitelist.

Both endpoints work identically to the blacklist endpoints but for numbers marked as legitimate.

### 6. Account Management

**Endpoint:** `GET /api/account`

Retrieve account settings including dial prefix for number normalization.

**Request Example:**
```http
GET /phoneblock/api/account HTTP/1.1
Host: phoneblock.net
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response:**
```json
{
  "dialPrefix": "+49",
  "language": "de"
}
```

**Endpoint:** `PUT /api/account`

Update account settings (e.g., sync device locale).

**Request Body:**
```json
{
  "dialPrefix": "+1",
  "language": "en"
}
```

### 7. Test Connectivity

**Endpoint:** `GET /api/test-connect`

Test API connectivity and authentication.

**Request Example:**
```http
GET /phoneblock/api/test-connect HTTP/1.1
Host: phoneblock.net
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response:**
```json
{
  "status": "ok",
  "authenticated": true,
  "userName": "user@example.com"
}
```

## Integration Examples

### Example 1: Call Screening App

A typical call screening app integration:

```java
import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.json.JSONObject;

public class PhoneBlockService {
    private static final String BASE_URL = "https://phoneblock.net/phoneblock/api";
    private final Context context;
    private String authToken;

    public PhoneBlockService(Context context) {
        this.context = context;
        // Load stored token
        SharedPreferences prefs = context.getSharedPreferences("phoneblock", Context.MODE_PRIVATE);
        this.authToken = prefs.getString("auth_token", null);
    }

    public CallScreeningDecision checkIncomingCall(String phoneNumber) {
        try {
            // Normalize to E.164 format
            String normalized = normalizePhoneNumber(phoneNumber);

            // Hash for privacy
            String hash = hashPhoneNumber(normalized);

            // Query PhoneBlock API
            PhoneInfo info = queryApi(hash);

            if (info != null) {
                // Apply your blocking logic
                if ("G_FRAUD".equals(info.rating) || "F_GAMBLE".equals(info.rating)) {
                    return CallScreeningDecision.REJECT;
                }

                if (info.votes > 10 &&
                    ("E_ADVERTISING".equals(info.rating) || "D_POLL".equals(info.rating))) {
                    return CallScreeningDecision.SILENCE;
                }

                if ("A_LEGITIMATE".equals(info.rating)) {
                    return CallScreeningDecision.ALLOW;
                }

                return CallScreeningDecision.SCREEN;
            }
        } catch (Exception e) {
            // Log error but fail open
            android.util.Log.e("PhoneBlock", "Failed to check number", e);
        }

        // Default: allow call if API fails
        return CallScreeningDecision.ALLOW;
    }

    private PhoneInfo queryApi(String hash) throws IOException {
        URL url = new URL(BASE_URL + "/check?sha1=" + hash);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
            conn.setRequestProperty("User-Agent", "MyCallBlocker/1.0.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                // Read and parse JSON response
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(response.toString());
                return PhoneInfo.fromJson(json);
            }
        } finally {
            conn.disconnect();
        }

        return null;
    }

    public String normalizePhoneNumber(String phone) {
        // Remove all non-digit characters except leading +
        String cleaned = phone.replaceAll("[^+\\d]", "");

        // Add country code if missing (using device SIM country)
        if (cleaned.startsWith("+")) {
            return cleaned;
        } else {
            String dialPrefix = getDialPrefixFromDevice();
            String withoutLeadingZero = cleaned.replaceFirst("^0+", "");
            return dialPrefix + withoutLeadingZero;
        }
    }

    public String hashPhoneNumber(String phone) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hash = digest.digest(phone.getBytes(StandardCharsets.UTF_8));

        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            hexString.append(String.format("%02X", b));
        }
        return hexString.toString();
    }

    private String getDialPrefixFromDevice() {
        // Get dial prefix from device SIM country
        android.telephony.TelephonyManager tm =
            (android.telephony.TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String countryIso = tm.getSimCountryIso();

        // Map country ISO to dial prefix (simplified example)
        switch (countryIso.toUpperCase()) {
            case "DE": return "+49";
            case "US": return "+1";
            case "GB": return "+44";
            case "FR": return "+33";
            // Add more countries as needed
            default: return "+49"; // Default to Germany
        }
    }

    public enum CallScreeningDecision {
        ALLOW, REJECT, SILENCE, SCREEN
    }

    public static class PhoneInfo {
        public String phone;
        public int votes;
        public int votesWildcard;
        public String rating;
        public boolean archived;
        public long dateAdded;
        public long lastUpdate;

        public static PhoneInfo fromJson(JSONObject json) throws org.json.JSONException {
            PhoneInfo info = new PhoneInfo();
            info.phone = json.optString("phone");
            info.votes = json.optInt("votes", 0);
            info.votesWildcard = json.optInt("votesWildcard", 0);
            info.rating = json.optString("rating");
            info.archived = json.optBoolean("archived", false);
            info.dateAdded = json.optLong("dateAdded", 0);
            info.lastUpdate = json.optLong("lastUpdate", 0);
            return info;
        }
    }
}
```

### Example 2: SMS Spam Filter

Integration for SMS spam detection:

```java
import java.util.Arrays;
import java.util.List;

public class SmsSpamFilter {
    private final PhoneBlockService phoneBlockService;
    private static final List<String> SPAM_KEYWORDS = Arrays.asList(
        "winner", "prize", "free", "click here", "verify account"
    );

    public SmsSpamFilter(PhoneBlockService phoneBlockService) {
        this.phoneBlockService = phoneBlockService;
    }

    public boolean checkSmsSpam(String sender, String message) {
        PhoneBlockService.PhoneInfo phoneInfo = phoneBlockService.checkNumber(sender);

        if (phoneInfo == null) {
            return false;
        }

        // High-confidence spam detection
        if (phoneInfo.votes > 20 &&
            ("G_FRAUD".equals(phoneInfo.rating) ||
             "F_GAMBLE".equals(phoneInfo.rating) ||
             "E_ADVERTISING".equals(phoneInfo.rating))) {
            return true;
        }

        // Additional heuristics based on message content
        boolean hasSpamKeywords = hasSpamKeywords(message);

        if (hasSpamKeywords && phoneInfo.votes > 5) {
            // Report as spam
            phoneBlockService.reportSpam(
                sender,
                "E_ADVERTISING",
                "SMS spam with suspicious keywords"
            );
            return true;
        }

        return false;
    }

    private boolean hasSpamKeywords(String message) {
        String lowerMessage = message.toLowerCase();
        for (String keyword : SPAM_KEYWORDS) {
            if (lowerMessage.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
```

### Example 3: Contact Manager Integration

Show spam warnings in contact details:

```java
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ContactDetailsActivity extends AppCompatActivity {
    private PhoneBlockService phoneBlockService;
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_details);

        phoneBlockService = new PhoneBlockService(this);
        executorService = Executors.newSingleThreadExecutor();

        String phoneNumber = getIntent().getStringExtra("phone");

        // Check spam status in background thread
        executorService.execute(() -> {
            PhoneBlockService.PhoneInfo spamInfo = phoneBlockService.checkNumber(phoneNumber);

            if (spamInfo != null && spamInfo.votes > 0) {
                // Update UI on main thread
                runOnUiThread(() -> {
                    showSpamWarning(
                        spamInfo.rating,
                        spamInfo.votes,
                        () -> {
                            // Allow user to add their own report
                            showReportDialog(phoneNumber);
                        }
                    );
                });
            }
        });
    }

    private void showSpamWarning(String rating, int votes, Runnable onReportAction) {
        // Display spam warning UI with rating and votes
        // Implementation depends on your UI framework
    }

    private void showReportDialog(String phoneNumber) {
        // Show dialog to allow user to report the number
        // Implementation depends on your UI framework
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
```

## Best Practices

### 1. User-Agent Header

Always include a descriptive User-Agent header:

```http
User-Agent: YourAppName/1.0.0 (Android 13; Model SM-G991B)
```

Format: `AppName/Version (Platform; Device Info)`

This helps PhoneBlock:
- Track API usage by app
- Detect security issues
- Provide better support
- Monitor token usage patterns

### 2. Error Handling

Implement robust error handling:

```java
public PhoneInfo checkNumber(String phone) {
    HttpURLConnection conn = null;
    try {
        String hash = hashPhoneNumber(phone);
        URL url = new URL(baseUrl + "/check?sha1=" + hash);
        conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + authToken);
        conn.setRequestProperty("User-Agent", userAgent);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        int responseCode = conn.getResponseCode();

        switch (responseCode) {
            case 200:
                // Success - parse response
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(response.toString());
                return PhoneInfo.fromJson(json);

            case 401:
                // Token expired or invalid - re-authenticate
                Log.w(TAG, "Token expired, re-authentication required");
                authToken = null;
                return null;

            case 429:
                // Rate limited - back off
                Log.w(TAG, "Rate limited, backing off");
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                return null;

            default:
                Log.e(TAG, "API error: HTTP " + responseCode);
                return null;
        }
    } catch (IOException | JSONException | NoSuchAlgorithmException e) {
        Log.e(TAG, "Failed to check number", e);
        // Fail open - allow call by default
        return null;
    } finally {
        if (conn != null) {
            conn.disconnect();
        }
    }
}
```

### 3. Privacy by Default

- **Always use SHA1 hashing** (`/api/check`) for call screening
- Never log phone numbers in plain text
- Store minimal data locally

### 4. Rate Limiting

- Use authenticated requests to avoid rate limits
- Cache responses for frequently checked numbers
- Implement exponential backoff on errors
- Respect HTTP 429 (Too Many Requests) responses

### 5. Offline Handling

- Cache recent lookups locally
- Fail open (allow calls) when offline or API unavailable
- Sync reports when connectivity is restored
- Show offline status to users

### 6. User Experience

- Make authentication optional but recommended
- Explain benefits of creating an account (personal lists, reporting)
- Show spam confidence levels, not just binary block/allow
- Allow users to override decisions
- Provide feedback mechanism for false positives

### 7. Number Normalization

Always normalize phone numbers to E.164 format before hashing:

```java
public String normalizeToE164(String phone, String defaultDialPrefix) {
    // Remove all formatting
    String cleaned = phone.replaceAll("[^+\\d]", "");

    if (cleaned.startsWith("+")) {
        // Already in international format
        return cleaned;
    } else if (cleaned.startsWith("0")) {
        // National format with leading zero
        return defaultDialPrefix + cleaned.substring(1);
    } else {
        // National format without leading zero
        return defaultDialPrefix + cleaned;
    }
}

// Examples:
// normalizeToE164("0176 506 426 02", "+49") -> "+4917650642602"
// normalizeToE164("+49 176 506 426 02", "+49") -> "+4917650642602"
// normalizeToE164("17650642602", "+49") -> "+4917650642602"
```

### 8. Battery and Performance

- Batch API requests when possible
- Use background processing for non-critical checks
- Implement intelligent caching
- Minimize network usage on metered connections

### 9. Testing

Always test against the test server first:

```java
public class PhoneBlockApi {
    private final String baseUrl;

    public PhoneBlockApi(boolean isProduction) {
        this.baseUrl = isProduction
            ? "https://phoneblock.net/phoneblock/api"
            : "https://phoneblock.net/pb-test/api";
    }

    // ... implementation
}
```

### 10. Token Refresh

Tokens are long-lived but can expire. Implement token refresh:

```java
public boolean ensureValidToken() {
    if (authToken == null) {
        return false;
    }

    // Test token validity
    boolean isValid = testConnection();

    if (!isValid) {
        // Clear invalid token and prompt re-authentication
        authToken = null;
        showAuthenticationPrompt();
        return false;
    }

    return true;
}

private boolean testConnection() {
    try {
        URL url = new URL(baseUrl + "/test-connect");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + authToken);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        int responseCode = conn.getResponseCode();
        conn.disconnect();

        return responseCode == 200;
    } catch (IOException e) {
        Log.e(TAG, "Failed to test connection", e);
        return false;
    }
}

private void showAuthenticationPrompt() {
    // Redirect user to login page to get new token
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.setData(Uri.parse("https://phoneblock.net/phoneblock/mobile/login?label=" +
        Uri.encode("MyApp on " + android.os.Build.MODEL)));
    context.startActivity(intent);
}
```

## OpenAPI Specification

The complete OpenAPI specification is available at:

```
https://phoneblock.net/phoneblock/api/phoneblock.json
```

You can use this to generate client libraries:

```bash
# Generate Java client
openapi-generator generate \
  -i https://phoneblock.net/phoneblock/api/phoneblock.json \
  -g java \
  -o phoneblock-client-java

# Generate Kotlin client
openapi-generator generate \
  -i https://phoneblock.net/phoneblock/api/phoneblock.json \
  -g kotlin \
  -o phoneblock-client-kotlin

# Generate Python client
openapi-generator generate \
  -i https://phoneblock.net/phoneblock/api/phoneblock.json \
  -g python \
  -o phoneblock-client-python
```

## Support and Resources

- **API Documentation**: https://phoneblock.net/phoneblock/api
- **Swagger UI**: https://phoneblock.net/phoneblock/api/ (interactive documentation)
- **GitHub Issues**: https://github.com/haumacher/phoneblock/issues
- **Email Support**: contact via GitHub

## Legal and Compliance

- **Privacy**: PhoneBlock processes phone numbers and spam reports. Use SHA1 hashing for privacy-preserving lookups.
- **GDPR**: PhoneBlock is GDPR compliant. Users can delete their data from settings.
- **Terms of Service**: Available at https://phoneblock.net/phoneblock/impressum
- **Rate Limits**: Fair use policy - reasonable rate limits apply to prevent abuse

## Example Projects

### PhoneBlock Mobile (Official Reference Implementation)

The official PhoneBlock Mobile app is the best reference for integration:

- **Repository**: `phoneblock_mobile/` in the main repository
- **Technology**: Flutter/Dart for Android
- **Features**: Full integration example including:
  - OAuth authentication flow
  - Token management
  - SHA1-based call screening
  - Personal list management
  - Spam reporting with comments
  - Offline caching
  - Background processing

**Key Files:**
- `lib/main.dart` - OAuth flow and UI
- `lib/storage.dart` - SQLite caching
- `android/app/src/main/java/.../CallChecker.java` - CallScreeningService implementation

---

For questions or support, please open an issue on GitHub or contact the PhoneBlock team through the website.
