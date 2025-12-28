# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PhoneBlock Mobile is a Flutter-based Android application that integrates with the PhoneBlock service (phoneblock.net) to identify and block spam phone calls. The app uses Android's CallScreeningService to intercept incoming calls and check them against the PhoneBlock spam database.

## Development Commands

### Flutter Commands
- `flutter pub get` - Install/update dependencies
- `flutter run` - Run the app in debug mode
- `flutter build apk` - Build a release APK
- `flutter build appbundle` - Build an Android App Bundle for Play Store
- `flutter test` - Run tests
- `flutter analyze` - Run static analysis

### Android Build
- Release builds require a `key.properties` file in the android directory with signing configuration
- The app uses signing configuration for release builds defined in `android/app/build.gradle`

## Architecture

### Flutter/Dart Layer (lib/)

**main.dart** - Main application entry point containing:
- **Routing**: Uses `go_router` package for navigation with two main routes:
  - Root (`/`) - Shows SetupPage
  - Response handler (`/pb-test/mobile/response` or `/phoneblock/mobile/response`) - Handles OAuth callback with loginToken parameter
- **Setup Flow**: SetupPage provides buttons to connect with PhoneBlock and request call screening permissions
- **Authentication**: Mobile OAuth flow where user opens PhoneBlock web login, completes authentication, and gets redirected back to the app with a token
- **API Integration**: Token is verified against `pbApiTest` endpoint
- **UI Components**:
  - MyHomePage: Main call log viewer with dismissible list items
  - RateScreen: Spam rating interface for manual classification
  - Call list display with icons, labels, timestamps, and action buttons
- **Contact Management**: fetchBlocklist() manages SPAM contact groups using flutter_contacts

**state.dart** - JSON-serializable data models using jsontool package:
- `AppState`: Root application state containing call list
- `Call`: Individual call records with type, rating, phone, label, timestamps
- `Type` enum: MISSED, BLOCKED, INCOMING, OUTGOING
- `Rating` enum: A_LEGITIMATE, UNKNOWN, PING, POLL, ADVERTISING, GAMBLE, FRAUD
- `GetReports`/`Reports`/`Report`: Models for syncing blocklist from server
- All models extend `_JsonObject` for serialization

### Android Native Layer (android/app/src/main/java/)

**MainActivity.java** - Flutter activity with MethodChannel bridge:
- **Channel**: `de.haumacher.phoneblock_mobile/call_checker`
- **Methods**:
  - `requestPermission()` - Requests ROLE_CALL_SCREENING permission (API 29+)
  - `setAuthToken(String)` - Stores OAuth token in SharedPreferences
  - `getAuthToken()` - Retrieves stored auth token
- **Storage**: Uses SharedPreferences with key "de.haumacher.phoneblock_mobile.Preferences"

**CallChecker.java** - CallScreeningService implementation:
- Intercepts incoming calls and queries PhoneBlock API for spam status
- Uses bearer token authentication from SharedPreferences
- Blocks calls if votes >= minVotes (default 4) and not archived
- Query timeout: 4.5 seconds - accepts call if query doesn't complete
- Query URL configurable via SharedPreferences (defaults to `https://phoneblock.net/phoneblock/api/num/{num}?format=json`)
- Only screens incoming calls (API 29+)

### Configuration

**Environment-specific URLs**: Defined in main.dart:
- Debug mode: Uses `/pb-test` context path
- Production: Uses `/phoneblock` context path
- Base URL: `https://phoneblock.net`

**AndroidManifest.xml**:
- Deep link handling for both test and production OAuth response URLs
- CallScreeningService declaration with BIND_SCREENING_SERVICE permission
- Requires READ_CONTACTS, WRITE_CONTACTS, and INTERNET permissions

**Signing**: Release builds use key.properties file (not in repo) containing:
- keyAlias
- keyPassword
- storeFile
- storePassword

## Key Dependencies

- `jsontool` - JSON serialization for data models
- `go_router` - Declarative routing
- `flutter_contacts` - Contact and group management for SPAM blocklist
- `url_launcher` - Opening PhoneBlock web login
- `http` - API requests
- `intl` - Date formatting
- `shared_preferences` - Persistent storage (bridged from native)

## Development Notes

### Git Commit Messages
**IMPORTANT**: When creating commits, always include the user's original prompt/request in the commit message. This provides context for the changes and helps track the reasoning behind implementation decisions.

Example format:
```
Brief summary of changes

[User's original request/prompt explaining what they asked for]

Details about the implementation approach and any relevant notes.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
```

### Platform Channel Communication
The app uses Flutter MethodChannel to communicate between Dart and Android native code. The channel name is `de.haumacher.phoneblock_mobile/call_checker` and is defined in both MainActivity.java and main.dart.

### Call Screening Flow
1. User grants ROLE_CALL_SCREENING permission via Android system dialog
2. Incoming calls trigger CallChecker.onScreenCall()
3. CallChecker queries PhoneBlock API with auth token
4. If spam criteria met (votes >= threshold, not archived), call is rejected
5. Query has 4.5 second timeout to avoid delaying legitimate calls

### OAuth Integration
The app uses app link deep linking (android:autoVerify="true") to handle OAuth callbacks from the PhoneBlock web service. The token is passed as a query parameter and stored locally for API authentication.
