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
https://phoneblock.net/phoneblock/mobile/login?appId=YourAppId&label=YourAppName%20on%20Device
```

**URL Parameters:**
- `appId` (required): Your registered app identifier (obtained through registration)
- `label` (optional): A user-visible label for the token that will appear in the user's settings page
  - Example: `"MyApp SMS Blocker on Samsung S23"`
  - Will be URL-encoded: `MyApp%20SMS%20Blocker%20on%20Samsung%20S23`

**Important:** You must register your app with PhoneBlock before integration (see Step 4 below for registration process).

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

appId=YourAppId&tokenLabel=YourAppName+on+Device
```

**Parameters:**
- `appId` (required): Your registered app identifier (e.g., "PhoneSpamBlocker")
- `tokenLabel` (optional): User-visible label for the token in settings

**Response:**
```http
HTTP/1.1 302 Found
Location: YourAppScheme://auth?loginToken=<generated-token>
```

The redirect URL is determined by your registered `appId`. Each registered app has a configured redirect URI that PhoneBlock uses after token creation.

### Step 4: Register Your App with PhoneBlock

Before integrating, you need to register your app with PhoneBlock to receive an `appId` and configure your redirect URI.

**Registration Process:**

1. **Contact PhoneBlock** via GitHub issues (https://github.com/haumacher/phoneblock/issues)
2. **Provide the following information:**
   - App name (user-visible)
   - App identifier/package name (e.g., `com.yourcompany.yourapp`)
   - Proposed `appId` (short identifier, e.g., "YourAppName")
   - Redirect URI scheme (e.g., `yourapp://auth`)
   - Brief description of your app's purpose
   - Contact information for support

3. **Receive your credentials:**
   - `appId`: Your registered app identifier
   - Configured redirect URI that PhoneBlock will use

**Current Registered Apps:**
- `PhoneBlockMobile` → Redirects to `PhoneBlockMobile://auth`
- `PhoneSpamBlocker` → Redirects to `PhoneSpamBlocker://auth`

### Step 5: Token Reception

After token creation, PhoneBlock redirects to your registered redirect URI with the token:

```
YourAppScheme://auth?loginToken=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Implement Custom URL Scheme in Your App:**

For Android, declare the intent filter in your `AndroidManifest.xml`:

```xml
<activity android:name=".AuthCallbackActivity">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="yourapp" android:host="auth" />
    </intent-filter>
</activity>
```

Then handle the intent in your activity:

```java
public class AuthCallbackActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Uri data = intent.getData();

        if (data != null && "auth".equals(data.getHost())) {
            String loginToken = data.getQueryParameter("loginToken");
            if (loginToken != null) {
                // Store token securely
                storeToken(loginToken);
                // Navigate to main app
                startMainActivity();
            }
        }

        finish();
    }
}
```

### Step 6: Store Token Securely

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
- `prefix10` (optional): SHA1 hash of the phone number with the last digit removed (for range-based spam detection)
- `prefix100` (optional): SHA1 hash of the phone number with the last two digits removed (for range-based spam detection)
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
GET /phoneblock/api/check?sha1=3D1D76F0C3664E1E818C6ECCFD8843AD1F4091CC&prefix10=A1B2...&prefix100=C3D4...&format=json HTTP/1.1
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
  "whiteListed": false,
  "blackListed": false,
  "archived": false,
  "dateAdded": 1704067200000,
  "lastUpdate": 1704153600000,
  "label": "(DE) 0176 50642602",
  "location": "Mobilfunk"
}
```

**Response Fields:**
- `phone` - The phone number (or `"unknown"` if only matched by prefix hash)
- `votes` - Direct community votes for this exact number
- `votesWildcard` - Combined votes from the number's spam range (can be non-zero even when `votes` is 0)
- `rating` - Community rating (see values below)
- `whiteListed` - `true` if this number is on the global whitelist (cannot receive votes)
- `blackListed` - `true` if the authenticated user has personally blocked this number. When set, the client should always block the call regardless of vote count.
- `archived` - `true` if this number was removed from the active blocklist due to inactivity
- `dateAdded` - Timestamp (ms since epoch) when first reported
- `lastUpdate` - Timestamp (ms since epoch) of the last report
- `label` - Locale-formatted display string (e.g., `"(DE) 030 12345678"`), may be `null`
- `location` - City or region (e.g., `"Berlin"`), may be `null`

**Rating Values:**
- `A_LEGITIMATE` - Verified legitimate number
- `B_MISSED` - Missed call (potentially spam)
- `C_PING` - Ping call (hangs up immediately)
- `D_POLL` - Survey/poll call
- `E_ADVERTISING` - Advertising/marketing call
- `F_GAMBLE` - Gambling/lottery scam
- `G_FRAUD` - Fraud/scam call

**Range-Based Spam Detection:**

The `prefix10` and `prefix100` parameters enable detection of spam ranges — groups of consecutive phone numbers used by the same spam operation. When the exact number is not found in the database, PhoneBlock checks if the number's prefix matches a known spam range and returns the aggregated votes in `votesWildcard`.

To compute the prefix hashes:
```python
number = "+4917650642602"
prefix10 = number[:-1]   # "+491765064260" — last digit removed
prefix100 = number[:-2]  # "+49176506426"  — last two digits removed

sha1_prefix10 = hashlib.sha1(prefix10.encode()).hexdigest().upper()
sha1_prefix100 = hashlib.sha1(prefix100.encode()).hexdigest().upper()
```

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

### 7. Community Blocklist Download

**Endpoint:** `GET /api/blocklist`

Download the community-maintained blocklist for offline filtering. This is primarily intended for routers, PBX systems, or apps that need offline blocklist capabilities.

**Important Notes:**
- For real-time call screening in mobile apps, use the `/check` or `/num` endpoints instead. Only use the blocklist endpoint if you need offline blocking capabilities.
- **CRITICAL: Randomize synchronization times** - Never use fixed sync times (e.g., daily at 3am). Use randomized intervals (e.g., 23-25 hours) to distribute server load evenly throughout the day. Fixed sync times create load spikes that harm service availability for all users.
- **This endpoint returns ONLY community data** - it does NOT include user-specific personalizations (personal blacklist/whitelist). For complete call filtering, you must also retrieve and check the user's personal lists via `/api/blacklist` and `/api/whitelist`.
- **Personal whitelist overrides community blocklist** - Always allow calls from numbers on the user's whitelist, even if they appear in the community blocklist.
- **Personal blacklist blocks regardless of community rating** - Block calls from numbers on the user's blacklist, even if the community has not flagged them.

**Rate Limits:**
- **Full synchronization** (without `since` parameter): Maximum once per month
- **Incremental synchronization** (with `since` parameter): Maximum once per day
- Clients exceeding these limits may be subject to rate limiting

**Parameters:**
- `since` (optional): Version number for incremental sync. Returns only changes since that version.
- `format` (optional): Response format - `json` (default) or `xml`

**Request Example (Full Sync):**
```http
GET /phoneblock/api/blocklist?format=json HTTP/1.1
Host: phoneblock.net
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
User-Agent: MyApp/1.0.0
```

**Response:**
```json
{
  "numbers": [
    {
      "phone": "+49123456789",
      "rating": "G_FRAUD",
      "votes": 4
    },
    {
      "phone": "+390456789123",
      "rating": "F_GAMBLE",
      "votes": 10
    }
  ],
  "version": 42
}
```

**Vote Normalization:**
Vote counts are normalized to threshold values (2, 4, 10, 20, 50, 100) to ensure consistency. For example, a number with 5-9 votes is transmitted as having 4 votes. Clients should filter by one of these threshold values.

**Incremental Sync:**
```http
GET /phoneblock/api/blocklist?since=42&format=json HTTP/1.1
Host: phoneblock.net
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response (Incremental):**
```json
{
  "numbers": [
    {
      "phone": "+49987654321",
      "rating": "E_ADVERTISING",
      "votes": 10
    },
    {
      "phone": "+49111222333",
      "rating": "A_LEGITIMATE",
      "votes": 0
    }
  ],
  "version": 43
}
```

**Implementation Guide:**

1. **Initial Sync:**
   ```java
   // Perform full sync once
   BlocklistResponse response = api.getBlocklist(null);
   localDb.storeBlocklist(response.numbers);
   localDb.saveVersion(response.version);
   ```

2. **Incremental Updates:**
   ```java
   // Daily incremental sync
   long savedVersion = localDb.getVersion();
   BlocklistResponse updates = api.getBlocklist(savedVersion);

   for (BlocklistEntry entry : updates.numbers) {
       if (entry.votes > 0) {
           // Add or update entry
           localDb.upsertNumber(entry.phone, entry.rating, entry.votes);
       } else {
           // votes=0 means remove from blocklist
           localDb.removeNumber(entry.phone);
       }
   }

   localDb.saveVersion(updates.version);
   ```

3. **Apply Threshold Filtering:**
   ```java
   // Example: Block numbers with 10+ votes
   int minVotes = 10;
   List<String> blockedNumbers = localDb.getNumbersAboveThreshold(minVotes);
   ```

4. **Combine with Personal Lists for Call Filtering:**
   ```java
   public boolean shouldBlockCall(String phoneNumber) {
       // 1. Check personal whitelist first - always allow
       if (personalWhitelist.contains(phoneNumber)) {
           return false; // Allow call
       }

       // 2. Check personal blacklist - always block
       if (personalBlacklist.contains(phoneNumber)) {
           return true; // Block call
       }

       // 3. Check community blocklist with threshold
       BlocklistEntry entry = communityBlocklist.get(phoneNumber);
       if (entry != null && entry.votes >= userThreshold) {
           return true; // Block based on community votes
       }

       // 4. Default: allow call
       return false;
   }

   // Sync personal lists separately
   public void syncPersonalLists() {
       // Fetch personal blacklist
       BlacklistResponse blacklist = api.getBlacklist();
       localDb.storePersonalBlacklist(blacklist.numbers);

       // Fetch personal whitelist
       WhitelistResponse whitelist = api.getWhitelist();
       localDb.storePersonalWhitelist(whitelist.numbers);
   }
   ```

**Best Practices:**
- **Future-proof hybrid approach (RECOMMENDED):** Combine dynamic lookups with local blocklist fallback for best results:
  - When online: Use `/check` endpoint for real-time lookups - this provides the most current data and preserves community feedback loops
  - When offline: Fall back to locally cached blocklist for basic protection
  - Why this matters: Local-only blocking eliminates automatic feedback as numbers accumulate more votes, potentially harming list quality. If community feedback becomes critical, PhoneBlock may limit future blocklist downloads to only high-confidence numbers. Apps using the hybrid approach will continue to access the full dataset via dynamic lookups while maintaining offline functionality.
- Store the blocklist locally in a database (SQLite, etc.)
- Schedule incremental syncs daily with randomized timing (e.g., 23-25 hour intervals when device is charging)
- Schedule full syncs monthly to catch any missed updates
- Apply appropriate threshold filtering based on your use case
- Handle `votes=0` entries as deletions from your local blocklist
- **Always sync and check personal black/whitelist separately** - personal lists take precedence over community data
- Update personal lists when users add/remove numbers via your app's UI

### 8. Test Connectivity

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

### Example 1: Call Screening

```
function onIncomingCall(rawNumber):
    number = normalizeToE164(rawNumber, deviceDialPrefix)

    // Compute hashes for privacy-preserving lookup
    sha1       = SHA1(number).toUpperHex()
    prefix10   = SHA1(number without last digit).toUpperHex()
    prefix100  = SHA1(number without last 2 digits).toUpperHex()

    // Query API
    info = GET /api/check?sha1={sha1}&prefix10={prefix10}&prefix100={prefix100}
           with Authorization: Bearer {token}
           with User-Agent: MyApp/1.0.0

    if info is null:
        return ALLOW  // fail open

    // 1. Personal lists take priority
    if info.blackListed:  return BLOCK
    if info.whiteListed:  return ALLOW

    // 2. Community votes (exact number)
    if info.votes >= minVotes and not info.archived:
        return BLOCK

    // 3. Range-based detection (spam ranges)
    if info.votesWildcard >= minRangeVotes:
        return BLOCK

    return ALLOW
```

### Example 2: Reporting Spam

```
function reportNumber(phoneNumber, rating, comment):
    POST /api/rate
        with Authorization: Bearer {token}
        with body: { "phone": phoneNumber, "rating": rating, "comment": comment }

    // rating is one of: A_LEGITIMATE, B_MISSED, C_PING, D_POLL,
    //                    E_ADVERTISING, F_GAMBLE, G_FRAUD
    // Use A_LEGITIMATE to whitelist, any other to blacklist
```

### Example 3: Contact Manager Integration

```
function showContactDetails(phoneNumber):
    // Query in background
    info = GET /api/num/{phoneNumber}?format=json
           with Authorization: Bearer {token}

    if info is not null and info.votes > 0:
        showSpamWarning(info.rating, info.votes, info.location)
        offerReportButton(phoneNumber)
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

```
function checkNumber(phone):
    response = GET /api/check?sha1={hash}
    switch response.status:
        200: return parsePhoneInfo(response.body)
        401: clearToken(); promptReAuthentication(); return null
        429: backoff(60 seconds); return null
        else: log("API error: " + response.status); return null
    on network error:
        return null  // fail open — allow call by default
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
- **Blocklist downloads:** Limit full sync to once per month, incremental sync to once per day

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

```
function normalizeToE164(phone, defaultDialPrefix):
    cleaned = remove all characters except digits and leading "+"

    if cleaned starts with "+":  return cleaned          // already international
    if cleaned starts with "0":  return defaultDialPrefix + cleaned[1:]  // national
    else:                        return defaultDialPrefix + cleaned      // no prefix

// Examples:
// normalizeToE164("0176 506 426 02", "+49") → "+4917650642602"
// normalizeToE164("+49 176 506 426 02", "+49") → "+4917650642602"
// normalizeToE164("17650642602", "+49") → "+4917650642602"
```

### 8. Battery and Performance

- Batch API requests when possible
- Use background processing for non-critical checks
- Implement intelligent caching
- Minimize network usage on metered connections

### 9. Testing

Always test against the test server first:

- **Test**: `https://phoneblock.net/pb-test/api`
- **Production**: `https://phoneblock.net/phoneblock/api`

### 10. Token Refresh

Tokens are long-lived but can be revoked. Validate on startup:

```
function ensureValidToken():
    if token is null: return false

    response = GET /api/test with Authorization: Bearer {token}
    if response.status == 200: return true

    // Token invalid — redirect user to re-authenticate
    clearToken()
    openBrowser("https://phoneblock.net/phoneblock/mobile/login?appId={appId}&label={deviceLabel}")
    return false
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
