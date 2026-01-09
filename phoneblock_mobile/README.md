# PhoneBlock Mobile

PhoneBlock Mobile is an Android app that automatically screens incoming calls and blocks known spam numbers using the PhoneBlock community database.

## Features

### Call Screening
- **Automatic SPAM Detection**: Screens incoming calls in real-time against the PhoneBlock database
- **Privacy-Focused**: Uses SHA-1 hashed phone numbers for lookups
- **Configurable Thresholds**: Set minimum spam reports (2, 4, 10, 20, 50, 100) before blocking
- **Number Range Blocking**: Optional blocking of entire number ranges with many spam reports
- **Configurable Range Thresholds**: Set minimum reports for range blocking (10, 20, 50, 100, 500)

### Call History
- **Filtered Call List**: View all screened calls (blocked and accepted)
- **Rating Display**: Shows spam category (Ping, Poll, Advertising, Gamble, Fraud)
- **Vote Counts**: Displays number of community reports for each number
- **Configurable Retention**: Keep call history for 1, 3, 7 days, or infinite
- **Privacy-by-Default**: 3-day retention period by default
- **Auto-Cleanup**: Automatically removes old calls based on retention setting

### Community Reporting
- **Report as SPAM**: Select category and optionally add comment about the call
- **Report as Legitimate**: Help improve accuracy by reporting false positives
- **Comment Support**: Add context with guided prompts and civility reminders
- **Direct PhoneBlock Links**: View full details on phoneblock.net

### PhoneBlock Account Integration
- **Secure Login**: OAuth-style login via phoneblock.net
- **Token Validation**: Automatic verification on startup
- **Easy Setup**: Step-by-step wizard for first-time users

### Notifications
- **Background Tracking**: Records blocked calls even when app is not running
- **Persistent Notification**: Shows count of pending blocked calls
- **Auto-Sync**: Syncs offline screening results when app opens

### Localization
- **Multi-Language Support**: English and German translations
- **Pluralization**: Proper handling of singular/plural forms
- **Date Formatting**: Localized timestamps (Today, Yesterday)

## Technical Details

### Architecture
- **Flutter/Dart**: Cross-platform UI framework
- **Native Android**: CallScreeningService for call interception
- **SQLite**: Local database for call history
- **SharedPreferences**: Settings persistence
- **Method Channels**: Flutter ↔ Native communication

### Privacy & Security
- **SHA-1 Hashing**: Phone numbers are hashed before API queries
- **No Tracking**: Open-source, no analytics or ads
- **Local Storage**: Call history stored only on device
- **Authorization**: Bearer token authentication with PhoneBlock API

### API Integration
- **User-Agent**: Identifies as `PhoneBlockMobile/<version>`
- **REST API**: JSON-based communication with PhoneBlock backend
- **Rate Limiting**: Respectful API usage with 4.5s timeout
- **Error Handling**: Graceful fallback when API is unavailable

## Requirements

- **Android**: API 24+ (Android 7.0 Nougat or higher)
- **Permissions**: Call screening permission (ROLE_CALL_SCREENING)
- **Account**: Free PhoneBlock.net account

## Getting Started

### Prerequisites
- Flutter SDK 3.5.0 or higher
- Android SDK with API level 24+
- A PhoneBlock.net account

### Building
```bash
flutter pub get
flutter build apk
```

### Development
```bash
flutter run
```

### Testing
```bash
flutter test
flutter analyze
```

## Project Structure

```
lib/
  ├── main.dart              # App entry point, UI, routing
  ├── storage.dart           # SQLite database layer
  ├── state.dart             # App state management
  ├── api.dart               # PhoneBlock API client (generated)
  └── l10n/                  # Localization files (ARB format)

android/
  └── app/src/main/java/de/haumacher/phoneblock_mobile/
      ├── MainActivity.java    # Flutter activity & settings
      ├── CallChecker.java     # Call screening service
      └── PhoneNumberUtils.java # Number normalization & hashing
```

## Settings

- **Minimum SPAM Reports**: 2, 4 (default), 10, 20, 50, 100
- **Block Number Ranges**: Enabled by default
- **Minimum Range Reports**: 10 (default), 20, 50, 100, 500
- **Call History Retention**: 1, 3 (default), 7 days, or infinite

## License

GPL-3.0

## Links

- **PlayStore**: https://play.google.com/store/apps/details?id=de.haumacher.phoneblock_mobile
- **PhoneBlock Website**: https://phoneblock.net
- **Source Code**: https://github.com/haumacher/phoneblock
- **Issues**: https://github.com/haumacher/phoneblock/issues

## Contributing

PhoneBlock is an open-source community project. Contributions are welcome!

## Support

PhoneBlock is funded by donations. Please consider [supporting the project](https://phoneblock.net/phoneblock/support) at phoneblock.net.
