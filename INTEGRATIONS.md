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

```kotlin
// Android example using EncryptedSharedPreferences
val sharedPreferences = EncryptedSharedPreferences.create(
    "phoneblock_prefs",
    masterKey,
    context,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)

sharedPreferences.edit()
    .putString("auth_token", token)
    .apply()
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

```kotlin
class PhoneBlockService(private val context: Context) {
    private val baseUrl = "https://phoneblock.net/phoneblock/api"
    private var authToken: String? = null

    init {
        // Load stored token
        authToken = context.getSharedPreferences("phoneblock", Context.MODE_PRIVATE)
            .getString("auth_token", null)
    }

    suspend fun checkIncomingCall(phoneNumber: String): CallScreeningDecision {
        // Normalize to E.164 format
        val normalized = normalizePhoneNumber(phoneNumber)

        // Hash for privacy
        val hash = hashPhoneNumber(normalized)

        // Query PhoneBlock API
        val response = httpClient.get("$baseUrl/check?sha1=$hash") {
            headers {
                append("Authorization", "Bearer $authToken")
                append("User-Agent", "MyCallBlocker/1.0.0")
            }
        }

        if (response.status.value == 200) {
            val info = response.body<PhoneInfo>()

            // Apply your blocking logic
            return when {
                info.rating == "G_FRAUD" -> CallScreeningDecision.REJECT
                info.rating == "F_GAMBLE" -> CallScreeningDecision.REJECT
                info.votes > 10 && info.rating in listOf("E_ADVERTISING", "D_POLL") ->
                    CallScreeningDecision.SILENCE
                info.rating == "A_LEGITIMATE" -> CallScreeningDecision.ALLOW
                else -> CallScreeningDecision.SCREEN
            }
        }

        // Default: allow call if API fails
        return CallScreeningDecision.ALLOW
    }

    fun normalizePhoneNumber(phone: String): String {
        // Remove all non-digit characters except leading +
        val cleaned = phone.replace(Regex("[^+\\d]"), "")

        // Add country code if missing (using device SIM country)
        return if (cleaned.startsWith("+")) {
            cleaned
        } else {
            val dialPrefix = getDialPrefixFromDevice()
            "$dialPrefix${cleaned.trimStart('0')}"
        }
    }

    fun hashPhoneNumber(phone: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(phone.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02X".format(it) }
    }
}
```

### Example 2: SMS Spam Filter

Integration for SMS spam detection:

```kotlin
class SmsSpamFilter(private val phoneBlockService: PhoneBlockService) {

    suspend fun checkSmsSpam(sender: String, message: String): Boolean {
        val phoneInfo = phoneBlockService.checkNumber(sender)

        // High-confidence spam detection
        if (phoneInfo.votes > 20 &&
            phoneInfo.rating in listOf("G_FRAUD", "F_GAMBLE", "E_ADVERTISING")) {
            return true
        }

        // Additional heuristics based on message content
        val spamKeywords = listOf("winner", "prize", "free", "click here", "verify account")
        val hasSpamKeywords = spamKeywords.any { message.lowercase().contains(it) }

        if (hasSpamKeywords && phoneInfo.votes > 5) {
            // Report as spam
            phoneBlockService.reportSpam(
                phone = sender,
                rating = "E_ADVERTISING",
                comment = "SMS spam with suspicious keywords"
            )
            return true
        }

        return false
    }
}
```

### Example 3: Contact Manager Integration

Show spam warnings in contact details:

```kotlin
class ContactDetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val phoneNumber = intent.getStringExtra("phone")

        lifecycleScope.launch {
            val spamInfo = phoneBlockService.checkNumber(phoneNumber)

            if (spamInfo.votes > 0) {
                showSpamWarning(
                    rating = spamInfo.rating,
                    votes = spamInfo.votes,
                    onReport = {
                        // Allow user to add their own report
                        showReportDialog(phoneNumber)
                    }
                )
            }
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

```kotlin
suspend fun checkNumber(phone: String): PhoneInfo? {
    return try {
        val response = httpClient.get("$baseUrl/check?sha1=${hashPhone(phone)}") {
            timeout {
                requestTimeoutMillis = 5000
                socketTimeoutMillis = 5000
            }
            headers {
                append("Authorization", "Bearer $authToken")
                append("User-Agent", userAgent)
            }
        }

        when (response.status.value) {
            200 -> response.body<PhoneInfo>()
            401 -> {
                // Token expired or invalid - re-authenticate
                authToken = null
                null
            }
            429 -> {
                // Rate limited - back off
                delay(60000)
                null
            }
            else -> {
                Log.e(TAG, "API error: ${response.status}")
                null
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to check number", e)
        // Fail open - allow call by default
        null
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

```kotlin
fun normalizeToE164(phone: String, defaultDialPrefix: String): String {
    // Remove all formatting
    val cleaned = phone.replace(Regex("[^+\\d]"), "")

    return when {
        // Already in international format
        cleaned.startsWith("+") -> cleaned

        // National format with leading zero
        cleaned.startsWith("0") -> {
            defaultDialPrefix + cleaned.substring(1)
        }

        // National format without leading zero
        else -> defaultDialPrefix + cleaned
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

```kotlin
class PhoneBlockApi(
    private val isProduction: Boolean = false
) {
    private val baseUrl = if (isProduction) {
        "https://phoneblock.net/phoneblock/api"
    } else {
        "https://phoneblock.net/pb-test/api"
    }

    // ... implementation
}
```

### 10. Token Refresh

Tokens are long-lived but can expire. Implement token refresh:

```kotlin
suspend fun ensureValidToken(): Boolean {
    if (authToken == null) {
        return false
    }

    // Test token validity
    val isValid = testConnection()

    if (!isValid) {
        // Clear invalid token and prompt re-authentication
        authToken = null
        showAuthenticationPrompt()
        return false
    }

    return true
}
```

## OpenAPI Specification

The complete OpenAPI specification is available at:

```
https://phoneblock.net/phoneblock/api/phoneblock.json
```

You can use this to generate client libraries:

```bash
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
