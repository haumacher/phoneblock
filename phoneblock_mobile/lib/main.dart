import 'dart:async';
import 'dart:convert';
import 'dart:ui';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:jsontool/jsontool.dart';
import 'package:phoneblock_mobile/state.dart';
import 'package:phoneblock_mobile/storage.dart';
import 'package:phoneblock_mobile/api.dart' as api;
import 'package:url_launcher/url_launcher.dart';
import 'package:http/http.dart' as http;
import 'package:webview_flutter/webview_flutter.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:device_region/device_region.dart';
import 'package:receive_sharing_intent/receive_sharing_intent.dart';
import 'package:phoneblock_shared/phoneblock_shared.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_models.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_service.dart';
import 'package:phoneblock_mobile/fritzbox/screens/fritzbox_settings.dart';
import 'l10n/app_localizations.dart';
import 'l10n/l10n_extensions.dart';

const String contextPath = kDebugMode ? "/pb-test" : "/phoneblock";
const String pbBaseUrl = 'https://phoneblock.net$contextPath';
const String pbApiTest = '$pbBaseUrl/api/test';

/// Gets the device name for use in the token label.
/// Returns a formatted string like "Samsung Galaxy S21" or "Google Pixel 6".
Future<String> _getDeviceName() async {
  final deviceInfo = DeviceInfoPlugin();
  try {
    final androidInfo = await deviceInfo.androidInfo;
    // Try to build a user-friendly device name
    final manufacturer = androidInfo.manufacturer;
    final model = androidInfo.model;

    // Capitalize manufacturer name
    final capitalizedManufacturer = manufacturer.isNotEmpty
        ? manufacturer[0].toUpperCase() + manufacturer.substring(1)
        : manufacturer;

    // If model already contains manufacturer, don't duplicate
    if (model.toLowerCase().startsWith(manufacturer.toLowerCase())) {
      return model;
    }

    return '$capitalizedManufacturer $model';
  } catch (e) {
    return 'Android Device';
  }
}

/// Gets the device's locale as a language tag string.
/// Returns a language tag like "de-DE", "en-US", etc.
///
/// This is suitable for use in the Accept-Language HTTP header.
String getDeviceLocale() {
  final locale = PlatformDispatcher.instance.locale;
  final languageTag = locale.toLanguageTag(); // e.g., "de-DE", "en-US"
  return languageTag;
}

/// Gets the device's locale information including language tag and country code.
/// Returns a map with 'lang' (e.g., "de-DE", "en-US") and 'countryCode' (e.g., "DE", "US").
///
/// Uses the device's SIM country code (from TelephonyManager), which is more accurate
/// than the locale country code since it reflects the actual network operator.
/// The server will convert the country code to the appropriate dial prefix.
/// If country code cannot be determined, it is omitted from the result.
///
/// This is used for syncing account settings with the PhoneBlock server.
Future<Map<String, String>> getDeviceLocaleSettings() async {
  try {
    final languageTag = getDeviceLocale();

    final result = <String, String>{
      'lang': languageTag,
    };

    // Get SIM country code from the telephony manager (more accurate than locale)
    try {
      final simCountryCode = await DeviceRegion.getSIMCountryCode();
      if (simCountryCode != null && simCountryCode.isNotEmpty) {
        // Send country code to server, which will convert to dial prefix
        result['countryCode'] = simCountryCode.toUpperCase();
        if (kDebugMode) {
          print('SIM country code: $simCountryCode');
        }
      } else {
        if (kDebugMode) {
          print('SIM country code not available (possibly simulator or no SIM)');
        }
      }
    } catch (e) {
      if (kDebugMode) {
        print('Could not get SIM country code: $e');
      }
    }

    return result;
  } catch (e) {
    if (kDebugMode) {
      print('Error getting device locale settings: $e');
    }
    // Return default values on error
    return {'lang': 'en'};
  }
}

/// Updates user account settings on the server with device locale information.
/// This is called during initial setup to configure the user's language and country preferences.
/// Errors are logged but do not block the setup flow.
Future<void> updateAccountSettings(String authToken, Map<String, String> settings) async {
  try {
    final url = '$pbBaseUrl/api/account';
    final response = await http.put(
      Uri.parse(url),
      headers: {
        'Authorization': 'Bearer $authToken',
        'Content-Type': 'application/json; charset=UTF-8',
      },
      body: jsonEncode(settings),
    );

    if (response.statusCode == 200) {
      if (kDebugMode) {
        print('Account settings updated successfully: $settings');
      }
    } else {
      if (kDebugMode) {
        print('Failed to update account settings: ${response.statusCode} - ${response.body}');
      }
    }
  } catch (e) {
    // Log error but don't block setup flow
    if (kDebugMode) {
      print('Error updating account settings: $e');
    }
  }
}

/// Account settings returned from the PhoneBlock API.
class AccountSettings {
  /// The user's login name (used for CardDAV URL construction).
  final String? login;

  /// The user's preferred language tag.
  final String? lang;

  /// The user's country dial prefix.
  final String? dialPrefix;

  /// The user's display name.
  final String? displayName;

  /// The user's email address.
  final String? email;

  AccountSettings({
    this.login,
    this.lang,
    this.dialPrefix,
    this.displayName,
    this.email,
  });

  factory AccountSettings.fromJson(Map<String, dynamic> json) {
    return AccountSettings(
      login: json['login'] as String?,
      lang: json['lang'] as String?,
      dialPrefix: json['dialPrefix'] as String?,
      displayName: json['displayName'] as String?,
      email: json['email'] as String?,
    );
  }
}

/// Fetches user account settings from the server.
/// Returns null if the request fails.
Future<AccountSettings?> fetchAccountSettings(String authToken) async {
  try {
    final url = '$pbBaseUrl/api/account';
    final response = await http.get(
      Uri.parse(url),
      headers: {
        'Authorization': 'Bearer $authToken',
        'User-Agent': 'PhoneBlockMobile/$appVersion',
      },
    );

    if (response.statusCode == 200) {
      final data = jsonDecode(response.body) as Map<String, dynamic>;
      return AccountSettings.fromJson(data);
    } else {
      if (kDebugMode) {
        print('Failed to fetch account settings: ${response.statusCode}');
      }
      return null;
    }
  } catch (e) {
    if (kDebugMode) {
      print('Error fetching account settings: $e');
    }
    return null;
  }
}

/// Builds the PhoneBlock login URL with device-specific token label parameter.
Future<String> _getPhoneBlockLoginUrl() async {
  final deviceName = await _getDeviceName();
  final tokenLabel = Uri.encodeComponent('PhoneBlock Mobile on $deviceName');
  return '$pbBaseUrl/mobile/login?tokenLabel=$tokenLabel';
}

/// Builds a PhoneBlock URL with authentication token parameter if available.
/// This allows opening PhoneBlock pages in external browser with automatic login.
/// If no token is available, returns the plain URL.
Future<String> buildPhoneBlockUrlWithToken(String path) async {
  String? authToken = await getAuthToken();
  if (authToken == null) {
    return '$pbBaseUrl$path';
  }

  final encodedToken = Uri.encodeComponent(authToken);
  final separator = path.contains('?') ? '&' : '?';
  return '$pbBaseUrl$path${separator}token=$encodedToken';
}

/// Retention period constant for infinite retention (keep all calls)
const int retentionInfinite = -1;

/// Default retention period in days (3 days for privacy)
const int retentionDefault = 3;

const platform = MethodChannel('de.haumacher.phoneblock_mobile/call_checker');

/// Global stream controller to broadcast when new calls are screened.
/// This allows the UI to react to new screening results in real-time.
final callScreenedStreamController = StreamController<ScreenedCall>.broadcast();

/// Tracks call IDs that are considered "new" (not yet seen by user).
/// Calls are added when synced from background or received while app is open.
/// Calls are removed when user taps on them.
final Set<int> newCallIds = {};

/// Global app version string, initialized at startup from package info.
late String appVersion;

/// Stream subscription for sharing intents (warm start).
StreamSubscription<List<SharedMediaFile>>? _sharingIntentSubscription;

/// Stores shared phone number from cold start for later processing.
String? _pendingSharedNumber;

/// Parses rating string from PhoneBlock service API response.
Rating? _parseRatingFromService(String ratingStr) {
  switch (ratingStr) {
    case 'A_LEGITIMATE': return Rating.aLEGITIMATE;
    case 'B_MISSED': return Rating.uNKNOWN;
    case 'C_PING': return Rating.pING;
    case 'D_POLL': return Rating.pOLL;
    case 'E_ADVERTISING': return Rating.aDVERTISING;
    case 'F_GAMBLE': return Rating.gAMBLE;
    case 'G_FRAUD': return Rating.fRAUD;
    default: return null;
  }
}

/// Extracts phone numbers from shared text.
/// Supports formats: +49 123 456789, (123) 456-7890, 1234567890, etc.
/// Returns first valid number found, or null if none.
String? extractPhoneNumber(String text) {
  final cleaned = text.trim();

  // Patterns for international, formatted, and plain phone numbers
  final patterns = [
    RegExp(r'\+[\d\s\-()]{7,}'), // International with +
    RegExp(r'\([\d]{2,}\)[\d\s\-]{6,}'), // Area code in parentheses
    RegExp(r'[\d]{2,}[\s\-][\d]{2,}[\s\-][\d]{4,}'), // Separated format
    RegExp(r'\b[\d]{10,}\b'), // Plain 10+ digits
  ];

  for (final pattern in patterns) {
    final match = pattern.firstMatch(cleaned);
    if (match != null) {
      return match.group(0);
    }
  }

  // Fallback: if mostly digits with formatting, use cleaned text
  final digitsOnly = cleaned.replaceAll(RegExp(r'[^\d+]'), '');
  if (digitsOnly.length >= 7) {
    return cleaned;
  }

  return null;
}

/// Handles shared phone numbers by storing them for lookup after auth check.
Future<void> handleSharedPhoneNumber(String? sharedText) async {
  if (sharedText == null || sharedText.isEmpty) return;

  if (kDebugMode) {
    print('Received shared text: $sharedText');
  }

  final phoneNumber = extractPhoneNumber(sharedText);
  if (phoneNumber == null) {
    if (kDebugMode) {
      print('No valid phone number found');
    }
    return;
  }

  if (kDebugMode) {
    print('Extracted phone number: $phoneNumber');
  }

  // Store for processing in MainScreen after auth check
  _pendingSharedNumber = phoneNumber;
}

/// Gets the configured retention period in days.
/// Returns the retention period from SharedPreferences, or the default value if not set.
Future<int> getRetentionDays() async {
  return await platform.invokeMethod<int>("getRetentionDays") ?? retentionDefault;
}

/// Gets the theme mode preference.
/// Returns 'system', 'light', or 'dark'. Defaults to 'system' if not set.
Future<String> getThemeMode() async {
  return await platform.invokeMethod<String>("getThemeMode") ?? 'system';
}

/// Sets the theme mode preference.
/// Valid values are 'system', 'light', or 'dark'.
Future<void> setThemeMode(String mode) async {
  await platform.invokeMethod("setThemeMode", mode);
}

/// Gets the total count of blocked calls.
Future<int> getBlockedCallsCount() async {
  return await platform.invokeMethod<int>("getBlockedCallsCount") ?? 0;
}

/// Gets the total count of suspicious calls (had votes but below threshold).
Future<int> getSuspiciousCallsCount() async {
  return await platform.invokeMethod<int>("getInspectedSuspiciousCount") ?? 0;
}

/// Makes an HTTP GET request to the PhoneBlock API with proper User-Agent header.
/// Includes the Authorization header if a token is provided.
Future<http.Response> callPhoneBlockApi(String url, {String? authToken}) async {
  final headers = <String, String>{
    "User-Agent": "PhoneBlockMobile/$appVersion",
  };

  if (authToken != null) {
    headers["Authorization"] = "Bearer $authToken";
  }

  return await http.get(Uri.parse(url), headers: headers);
}

/// Fetches the user's blacklist from the PhoneBlock API.
/// Returns a NumberList containing all phone numbers the user has explicitly blocked.
/// Requires authentication via [authToken].
Future<api.NumberList?> fetchBlacklist(String authToken) async {
  try {
    final response = await callPhoneBlockApi('$pbBaseUrl/api/blacklist', authToken: authToken);

    if (response.statusCode == 200) {
      return api.NumberList.fromString(response.body);
    } else {
      if (kDebugMode) {
        print('Failed to fetch blacklist: ${response.statusCode} - ${response.body}');
      }
      return null;
    }
  } catch (e) {
    if (kDebugMode) {
      print('Error fetching blacklist: $e');
    }
    return null;
  }
}

/// Fetches the user's whitelist (legitimate numbers) from the PhoneBlock API.
/// Returns a NumberList containing all phone numbers the user has explicitly marked as legitimate.
/// Requires authentication via [authToken].
Future<api.NumberList?> fetchWhitelist(String authToken) async {
  try {
    final response = await callPhoneBlockApi('$pbBaseUrl/api/whitelist', authToken: authToken);

    if (response.statusCode == 200) {
      return api.NumberList.fromString(response.body);
    } else {
      if (kDebugMode) {
        print('Failed to fetch whitelist: ${response.statusCode} - ${response.body}');
      }
      return null;
    }
  } catch (e) {
    if (kDebugMode) {
      print('Error fetching whitelist: $e');
    }
    return null;
  }
}

/// Removes a phone number from the user's blacklist.
/// Returns true if the removal was successful, false otherwise.
/// Requires authentication via [authToken].
Future<bool> removeFromBlacklist(String phone, String authToken) async {
  try {
    final headers = <String, String>{
      "User-Agent": "PhoneBlockMobile/$appVersion",
      "Authorization": "Bearer $authToken",
    };

    final response = await http.delete(
      Uri.parse('$pbBaseUrl/api/blacklist/$phone'),
      headers: headers,
    );

    if (response.statusCode == 204) {
      return true;
    } else {
      if (kDebugMode) {
        print('Failed to remove from blacklist: ${response.statusCode} - ${response.body}');
      }
      return false;
    }
  } catch (e) {
    if (kDebugMode) {
      print('Error removing from blacklist: $e');
    }
    return false;
  }
}

/// Removes a phone number from the user's whitelist.
/// Returns true if the removal was successful, false otherwise.
/// Requires authentication via [authToken].
Future<bool> removeFromWhitelist(String phone, String authToken) async {
  try {
    final headers = <String, String>{
      "User-Agent": "PhoneBlockMobile/$appVersion",
      "Authorization": "Bearer $authToken",
    };

    final response = await http.delete(
      Uri.parse('$pbBaseUrl/api/whitelist/$phone'),
      headers: headers,
    );

    if (response.statusCode == 204) {
      return true;
    } else {
      if (kDebugMode) {
        print('Failed to remove from whitelist: ${response.statusCode} - ${response.body}');
      }
      return false;
    }
  } catch (e) {
    if (kDebugMode) {
      print('Error removing from whitelist: $e');
    }
    return false;
  }
}

/// Updates the comment for a phone number in the user's personalized list.
/// Returns true if the update was successful, false otherwise.
/// Requires authentication via [authToken].
Future<bool> _updatePersonalizedComment(PersonalizedListType listType, String phone, String comment, String authToken) async {
  try {
    final listName = listType.name; // 'blacklist' or 'whitelist'

    final headers = <String, String>{
      "User-Agent": "PhoneBlockMobile/$appVersion",
      "Authorization": "Bearer $authToken",
      "Content-Type": "application/json",
    };

    final body = json.encode({
      "phone": phone,
      "comment": comment,
    });

    final response = await http.put(
      Uri.parse('$pbBaseUrl/api/$listName/$phone'),
      headers: headers,
      body: body,
    );

    if (response.statusCode == 204) {
      return true;
    } else {
      if (kDebugMode) {
        print('Failed to update $listName comment: ${response.statusCode} - ${response.body}');
      }
      return false;
    }
  } catch (e) {
    if (kDebugMode) {
      print('Error updating ${listType.name} comment: $e');
    }
    return false;
  }
}

/// Updates the comment for a phone number in the user's blacklist.
/// Returns true if the update was successful, false otherwise.
/// Requires authentication via [authToken].
Future<bool> updateBlacklistComment(String phone, String comment, String authToken) async {
  return _updatePersonalizedComment(PersonalizedListType.blacklist, phone, comment, authToken);
}

/// Updates the comment for a phone number in the user's whitelist.
/// Returns true if the update was successful, false otherwise.
/// Requires authentication via [authToken].
Future<bool> updateWhitelistComment(String phone, String comment, String authToken) async {
  return _updatePersonalizedComment(PersonalizedListType.whitelist, phone, comment, authToken);
}

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Initialize app version from package info
  final packageInfo = await PackageInfo.fromPlatform();
  appVersion = packageInfo.version;

  // Store app version in SharedPreferences for native code to use in User-Agent
  platform.invokeMethod("setAppVersion", appVersion);

  // Set up listener for screening results from CallChecker
  platform.setMethodCallHandler((call) async {
    if (call.method == 'onCallScreened') {
      final args = call.arguments as Map;
      final phoneNumber = args['phoneNumber'] as String;
      final wasBlocked = args['wasBlocked'] as bool;
      final votes = args['votes'] as int;
      final votesWildcard = args['votesWildcard'] as int? ?? 0;
      final timestamp = args['timestamp'] as int;
      final ratingStr = args['rating'] as String?;
      final label = args['label'] as String?;
      final location = args['location'] as String?;

      // Parse rating if available
      Rating? rating;
      if (ratingStr != null) {
        rating = _parseRatingFromService(ratingStr);
      }

      // Store the screened call in database
      final screenedCall = ScreenedCall(
        phoneNumber: phoneNumber,
        timestamp: DateTime.fromMillisecondsSinceEpoch(timestamp),
        wasBlocked: wasBlocked,
        votes: votes,
        votesWildcard: votesWildcard,
        rating: rating,
        label: label,
        location: location,
      );

      final insertedCall = await ScreenedCallsDatabase.instance.insertScreenedCall(screenedCall);

      // Track as new call
      if (insertedCall.id != null) {
        newCallIds.add(insertedCall.id!);
      }

      // Clean up old calls based on retention period when a new call arrives
      final retentionDays = await getRetentionDays();
      await ScreenedCallsDatabase.instance.deleteOldScreenedCalls(retentionDays);

      // Notify any listeners (e.g., MainScreen) about the new call
      callScreenedStreamController.add(insertedCall);

      if (kDebugMode) {
        print('Screened call saved: $phoneNumber (blocked: $wasBlocked, votes: $votes, rangeVotes: $votesWildcard, rating: $ratingStr)');
      }
    }
  });

  // Sync any stored screening results from SharedPreferences to database
  // This handles calls that were screened while the app was not running
  await syncStoredScreeningResults();

  // Set the query URL on every startup to ensure it's updated after app updates
  final queryUrl = '$pbBaseUrl/api/check?sha1={sha1}&format=json';
  platform.invokeMethod("setQueryUrl", queryUrl);

  // Clean up old screened calls based on retention period
  // (This includes both mobile and Fritz!Box calls in the unified table)
  final retentionDays = await getRetentionDays();
  await ScreenedCallsDatabase.instance.deleteOldScreenedCalls(retentionDays);

  // Set up auth provider for shared answerbot components
  setAuthProvider(getAuthToken);

  runApp(const PhoneBlockApp());
}

/// Main application widget with theme support.
class PhoneBlockApp extends StatefulWidget {
  const PhoneBlockApp({super.key});

  @override
  State<PhoneBlockApp> createState() => _PhoneBlockAppState();
}

class _PhoneBlockAppState extends State<PhoneBlockApp> {
  ThemeMode _themeMode = ThemeMode.system;

  @override
  void initState() {
    super.initState();
    _loadThemeMode();
    _initSharingIntent();
  }

  /// Initializes sharing intent listener for warm starts.
  void _initSharingIntent() {
    _sharingIntentSubscription = ReceiveSharingIntent.instance.getMediaStream().listen(
      (List<SharedMediaFile> value) {
        if (value.isEmpty) return;

        final sharedFile = value.first;
        if (sharedFile.type == SharedMediaType.text) {
          handleSharedPhoneNumber(sharedFile.path); // path contains text
          ReceiveSharingIntent.instance.reset();
        }
      },
      onError: (err) {
        if (kDebugMode) {
          print('Error receiving sharing intent: $err');
        }
      },
    );
  }

  @override
  void dispose() {
    _sharingIntentSubscription?.cancel();
    super.dispose();
  }

  /// Loads the theme mode from preferences.
  Future<void> _loadThemeMode() async {
    final mode = await getThemeMode();
    if (!mounted) return;
    setState(() {
      _themeMode = _themeModeFromString(mode);
    });
  }

  /// Updates the theme mode and saves it to preferences.
  Future<void> updateThemeMode(String mode) async {
    await setThemeMode(mode);
    if (!mounted) return;
    setState(() {
      _themeMode = _themeModeFromString(mode);
    });
  }

  /// Converts string to ThemeMode enum.
  ThemeMode _themeModeFromString(String mode) {
    switch (mode) {
      case 'light':
        return ThemeMode.light;
      case 'dark':
        return ThemeMode.dark;
      default:
        return ThemeMode.system;
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      routerConfig: router,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        answerbotLocalizationsDelegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: const [
        Locale('de'),
        Locale('en'),
      ],
      themeMode: _themeMode,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color.fromARGB(255, 0, 209, 178),
          brightness: Brightness.light,
        ),
        appBarTheme: const AppBarTheme(
          backgroundColor: Color.fromARGB(255, 0, 209, 178),
          foregroundColor: Colors.white,
        ),
      ),
      darkTheme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color.fromARGB(255, 0, 209, 178),
          brightness: Brightness.dark,
        ),
      ),
    );
  }
}

/// Syncs screening results stored in SharedPreferences to the SQLite database.
/// This is necessary because CallChecker can screen calls even when the Flutter
/// app is not running, storing results in SharedPreferences for later sync.
Future<void> syncStoredScreeningResults() async {
  try {
    // Get all stored screening results from native side
    final storedCalls = await platform.invokeMethod('getStoredScreeningResults');

    if (storedCalls != null && storedCalls is List) {
      for (var callData in storedCalls) {
        final data = callData as Map;
        final ratingStr = data['rating'] as String?;

        // Parse rating if available
        Rating? rating;
        if (ratingStr != null) {
          rating = _parseRatingFromService(ratingStr);
        }

        final screenedCall = ScreenedCall(
          phoneNumber: data['phoneNumber'] as String,
          timestamp: DateTime.fromMillisecondsSinceEpoch(data['timestamp'] as int),
          wasBlocked: data['wasBlocked'] as bool,
          votes: data['votes'] as int,
          votesWildcard: (data['votesWildcard'] as int?) ?? 0,
          rating: rating,
          label: data['label'] as String?,
          location: data['location'] as String?,
        );

        final insertedCall = await ScreenedCallsDatabase.instance.insertScreenedCall(screenedCall);

        // Track as new call
        if (insertedCall.id != null) {
          newCallIds.add(insertedCall.id!);
        }

        if (kDebugMode) {
          print('Synced stored call: ${screenedCall.phoneNumber} (blocked: ${screenedCall.wasBlocked}, rangeVotes: ${screenedCall.votesWildcard}, rating: $ratingStr)');
        }
      }

      // Clear the stored results from SharedPreferences after syncing
      await platform.invokeMethod('clearStoredScreeningResults');

      if (kDebugMode) {
        if (storedCalls.isNotEmpty) {
          print('Synced ${storedCalls.length} stored screening results');
        }
      }
    }
  } catch (e) {
    if (kDebugMode) {
      print('Error syncing stored screening results: $e');
    }
  }
}

final router = GoRouter(
  routes: [
    GoRoute(
      path: '/',
      builder: (context, state) {
        return const AppLauncher();
      },
      routes: [
        GoRoute(
          path: 'setup',
          builder: (context, state) => const SetupWizard(),
        ),
        GoRoute(
          path: 'main',
          builder: (context, state) => const MainScreen(),
        ),
        GoRoute(
          path: '$contextPath/mobile/response',
          builder: (context, state) {
            var loginToken = state.uri.queryParameters["loginToken"];
            if (kDebugMode) {
              print("Token received (${state.path}): $loginToken");
            }
            if (loginToken == null) {
              return LoginFailed();
            } else {
              return VerifyLogin(loginToken);
            }
          },
        ),
      ],
    ),
  ],
);

// App launcher that checks setup state and routes accordingly
class AppLauncher extends StatefulWidget {
  const AppLauncher({super.key});

  @override
  State<AppLauncher> createState() => _AppLauncherState();
}

class _AppLauncherState extends State<AppLauncher> {
  @override
  void initState() {
    super.initState();
    _checkSetupState();
  }

  Future<void> _checkSetupState() async {
    // Check for shared intent on cold start
    final initialMedia = await ReceiveSharingIntent.instance.getInitialMedia();
    if (initialMedia.isNotEmpty) {
      final sharedFile = initialMedia.first;
      if (sharedFile.type == SharedMediaType.text) {
        await handleSharedPhoneNumber(sharedFile.path);
        ReceiveSharingIntent.instance.reset();
      }
    }

    // Validate auth token (checks existence and validity)
    bool hasValidToken = await validateAuthToken();

    // Check if permission is granted
    bool hasPermission = await checkPermission();

    // Navigate based on setup state
    if (mounted) {
      if (hasValidToken && hasPermission) {
        context.go('/main');
      } else {
        context.go('/setup');
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: CircularProgressIndicator(),
      ),
    );
  }
}

/// Setup wizard steps for better readability and type safety.
enum SetupStep {
  /// Welcome screen and PhoneBlock account connection via OAuth.
  welcome,

  /// Call screening permission request step.
  permission,

  /// Setup completion confirmation.
  complete;
}

// Setup wizard with multiple steps
class SetupWizard extends StatefulWidget {
  const SetupWizard({super.key});

  @override
  State<SetupWizard> createState() => _SetupWizardState();
}

class _SetupWizardState extends State<SetupWizard> {
  SetupStep _currentStep = SetupStep.welcome;
  bool _hasAuthToken = false;
  bool _hasPermission = false;

  @override
  void initState() {
    super.initState();
    _checkCurrentState();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // Refresh state when widget becomes visible (e.g., after returning from OAuth)
    _checkCurrentState();
  }

  Future<void> _checkCurrentState() async {
    bool hasValidToken = await validateAuthToken();
    bool hasPermission = await checkPermission();

    setState(() {
      _hasAuthToken = hasValidToken;
      _hasPermission = hasPermission;

      // Start at the first incomplete step
      if (!_hasAuthToken) {
        _currentStep = SetupStep.welcome;
      } else if (!_hasPermission) {
        _currentStep = SetupStep.permission;
      } else {
        _currentStep = SetupStep.complete;
      }
    });
  }

  Future<void> _connectToPhoneBlock() async {
    final loginUrl = await _getPhoneBlockLoginUrl();
    bool ok = await launchUrl(Uri.parse(loginUrl));
    if (!ok && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(context.l10n.errorOpeningPhoneBlock)),
      );
    }
    // Note: _hasAuthToken will be updated when user returns via deep link
    // and the SetupWizard rebuilds after VerifyLogin sets the token
  }

  Future<void> _requestPermission() async {
    try {
      bool granted = await platform.invokeMethod("requestPermission");
      setState(() {
        _hasPermission = granted;
      });

      if (granted) {
        setState(() {
          _currentStep = SetupStep.complete;
        });
      } else if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(context.l10n.permissionNotGranted)),
        );
      }
    } catch (e) {
      if (kDebugMode) {
        print("Error requesting permission: $e");
      }
    }
  }

  void _finishSetup() {
    if (_hasAuthToken && _hasPermission) {
      context.go('/main');
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (bool didPop, dynamic result) async {
        if (didPop) {
          return;
        }
        // Exit app when back button/gesture is used on setup screen
        SystemNavigator.pop();
      },
      child: Scaffold(
        appBar: AppBar(
          title: Text(context.l10n.setupTitle),
          automaticallyImplyLeading: false,
        ),
        body: Stepper(
        currentStep: _currentStep.index,
        controlsBuilder: (context, details) {
          // Show "Done" button only on the final step when all prerequisites are met
          if (_currentStep == SetupStep.complete && _hasAuthToken && _hasPermission) {
            return Padding(
              padding: const EdgeInsets.only(top: 16),
              child: ElevatedButton(
                onPressed: _finishSetup,
                child: Text(context.l10n.done),
              ),
            );
          }
          // Hide default stepper controls - users navigate by tapping step content buttons
          return const SizedBox.shrink();
        },
        onStepTapped: (step) {
          final tappedStep = SetupStep.values[step];

          setState(() {
            // If tapping the current step and it's completed, collapse it by moving to next incomplete step
            if (tappedStep == _currentStep) {
              if (tappedStep == SetupStep.welcome && _hasAuthToken) {
                _currentStep = SetupStep.permission;
              } else if (tappedStep == SetupStep.permission && _hasPermission) {
                _currentStep = SetupStep.complete;
              }
            } else {
              // Only allow expanding steps that can be started
              bool canExpand = false;

              if (tappedStep == SetupStep.welcome) {
                // Welcome step can always be expanded
                canExpand = true;
              } else if (tappedStep == SetupStep.permission) {
                // Permission step can only be expanded if auth is complete
                canExpand = _hasAuthToken;
              } else if (tappedStep == SetupStep.complete) {
                // Complete step can only be expanded if permission is granted
                canExpand = _hasAuthToken && _hasPermission;
              }

              if (canExpand) {
                _currentStep = tappedStep;
              }
            }
          });
        },
        steps: [
          Step(
            title: Text(context.l10n.welcome),
            subtitle: Text(context.l10n.connectPhoneBlockAccount),
            isActive: true, // Welcome step is always available
            state: _hasAuthToken ? StepState.complete : StepState.indexed,
            content: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  context.l10n.welcomeMessage,
                  style: const TextStyle(fontSize: 14),
                ),
                const SizedBox(height: 16),
                ElevatedButton.icon(
                  onPressed: _connectToPhoneBlock,
                  icon: Icon(_hasAuthToken ? Icons.check_circle : Icons.link),
                  label: Text(_hasAuthToken
                    ? context.l10n.connectedToPhoneBlock
                    : context.l10n.connectToPhoneBlock),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: _hasAuthToken ? Colors.green : null,
                  ),
                ),
                if (_hasAuthToken)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Text(
                      context.l10n.accountConnectedSuccessfully,
                      style: const TextStyle(color: Colors.green, fontWeight: FontWeight.bold),
                    ),
                  ),
              ],
            ),
          ),
          Step(
            title: Text(context.l10n.permissions),
            subtitle: Text(context.l10n.allowCallFiltering),
            isActive: _hasAuthToken, // Active when auth is complete (prerequisite met)
            state: _hasPermission ? StepState.complete : StepState.indexed,
            content: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  context.l10n.permissionsMessage,
                  style: const TextStyle(fontSize: 14),
                ),
                const SizedBox(height: 16),
                ElevatedButton.icon(
                  onPressed: _requestPermission,
                  icon: Icon(_hasPermission ? Icons.check_circle : Icons.security),
                  label: Text(_hasPermission
                    ? context.l10n.permissionGranted
                    : context.l10n.grantPermission),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: _hasPermission ? Colors.green : null,
                  ),
                ),
                if (_hasPermission)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Text(
                      context.l10n.permissionGrantedSuccessfully,
                      style: const TextStyle(color: Colors.green, fontWeight: FontWeight.bold),
                    ),
                  ),
              ],
            ),
          ),
          Step(
            title: Text(context.l10n.done),
            subtitle: Text(context.l10n.setupComplete),
            isActive: _hasAuthToken && _hasPermission, // Active when both prerequisites met
            state: (_hasAuthToken && _hasPermission) ? StepState.complete : StepState.indexed,
            content: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(Icons.check_circle_outline, size: 64, color: Colors.green),
                const SizedBox(height: 16),
                Text(
                  context.l10n.setupCompleteMessage,
                  style: const TextStyle(fontSize: 14),
                ),
              ],
            ),
          ),
        ],
      ),
      ),
    );
  }
}

// Main screen (placeholder for now)
/// Main screen displaying the list of screened calls.
class MainScreen extends StatefulWidget {
  const MainScreen({super.key});

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  List<ScreenedCall> _screenedCalls = [];
  bool _isLoading = true;
  bool _answerbotEnabled = false;
  FritzBoxConnectionState _fritzboxState = FritzBoxConnectionState.notConfigured;
  CardDavStatus _cardDavStatus = CardDavStatus.notConfigured;
  StreamSubscription<ScreenedCall>? _callScreenedSubscription;
  Offset _lastTapPosition = Offset.zero;

  @override
  void initState() {
    super.initState();
    _loadScreenedCalls();
    _setupCallScreeningListener();
    _checkPendingSharedNumber();
    _loadAnswerbotEnabled();
    _initFritzBox();
  }

  /// Initializes Fritz!Box service and syncs calls.
  Future<void> _initFritzBox() async {
    await FritzBoxService.instance.initialize();
    await _checkFritzBoxConnection();
    // Sync Fritz!Box calls if connected - they'll be loaded with mobile calls
    if (FritzBoxService.instance.isConnected) {
      final newIds = await FritzBoxService.instance.syncCallList();
      // Track synced calls as new
      newCallIds.addAll(newIds);
      // Refresh the call list to include new Fritz!Box calls
      await _loadScreenedCalls();
    }
  }

  /// Checks Fritz!Box connection and CardDAV protection status.
  Future<void> _checkFritzBoxConnection() async {
    await FritzBoxService.instance.checkConnection();
    final connectionState = FritzBoxService.instance.connectionState;

    CardDavStatus cardDavStatus = CardDavStatus.notConfigured;
    if (connectionState == FritzBoxConnectionState.connected) {
      cardDavStatus = await FritzBoxService.instance.syncBlocklistMode();
    }

    if (mounted) {
      setState(() {
        _fritzboxState = connectionState;
        _cardDavStatus = cardDavStatus;
      });
    }
  }

  /// Loads the answerbot enabled setting.
  Future<void> _loadAnswerbotEnabled() async {
    try {
      final enabled = await platform.invokeMethod<bool>("getAnswerbotEnabled");
      setState(() {
        _answerbotEnabled = enabled ?? false;
      });
    } catch (e) {
      if (kDebugMode) {
        print("Error loading answerbot enabled setting: $e");
      }
    }
  }

  /// Checks for pending shared number and opens lookup screen.
  Future<void> _checkPendingSharedNumber() async {
    if (_pendingSharedNumber != null) {
      final number = _pendingSharedNumber!;
      _pendingSharedNumber = null; // Clear it

      if (kDebugMode) {
        print('Processing pending shared number: $number');
      }

      // Brief delay for screen to finish building
      await Future.delayed(const Duration(milliseconds: 300));

      if (context.mounted) {
        _searchPhoneNumber(number); // Reuses existing lookup method
      }
    }
  }

  @override
  void dispose() {
    _callScreenedSubscription?.cancel();
    super.dispose();
  }

  /// Loads screened calls from the database.
  /// This includes both mobile and Fritz!Box calls from the unified table.
  Future<void> _loadScreenedCalls() async {
    setState(() {
      _isLoading = true;
    });

    try {
      final calls = await ScreenedCallsDatabase.instance.getAllScreenedCalls();
      setState(() {
        _screenedCalls = calls;
        _isLoading = false;
      });
    } catch (e) {
      if (kDebugMode) {
        print('Error loading screened calls: $e');
      }
      setState(() {
        _isLoading = false;
      });
    }
  }

  /// Sets up listener to update list when new calls are screened.
  void _setupCallScreeningListener() {
    _callScreenedSubscription = callScreenedStreamController.stream.listen((screenedCall) {
      // Add the new call to the beginning of the list (most recent first)
      setState(() {
        _screenedCalls.insert(0, screenedCall);
      });

      if (kDebugMode) {
        print('MainScreen received new screened call: ${screenedCall.phoneNumber}');
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (bool didPop, dynamic result) async {
        if (didPop) {
          return;
        }
        // Exit app when back button/gesture is used on main screen
        SystemNavigator.pop();
      },
      child: Scaffold(
        appBar: AppBar(
          title: Text(context.l10n.appTitle),
          leading: Builder(
            builder: (context) => IconButton(
              icon: const Icon(Icons.menu),
              onPressed: () {
                Scaffold.of(context).openDrawer();
              },
            ),
          ),
          actions: [
            IconButton(
              icon: const Icon(Icons.search),
              tooltip: context.l10n.searchNumber,
              onPressed: () {
                _showSearchDialog(context);
              },
            ),
          ],
        ),
        drawer: _buildDrawer(context),
        body: _isLoading
            ? const Center(child: CircularProgressIndicator())
            : Column(
                children: [
                  // Fritz!Box offline banner
                  if (_fritzboxState == FritzBoxConnectionState.offline)
                    _buildFritzBoxOfflineBanner(context),
                  // Calls list
                  Expanded(
                    child: _screenedCalls.isEmpty
                        ? _buildEmptyState()
                        : _buildCallsList(),
                  ),
                ],
              ),
        floatingActionButton: _screenedCalls.isNotEmpty
            ? FloatingActionButton(
                onPressed: _deleteAllCalls,
                tooltip: context.l10n.deleteAll,
                child: const Icon(Icons.delete_sweep),
              )
            : null,
      ),
    );
  }

  /// Builds the Fritz!Box offline banner.
  Widget _buildFritzBoxOfflineBanner(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      color: Colors.orange.withValues(alpha: 0.1),
      child: Row(
        children: [
          const Icon(Icons.cloud_off, size: 18, color: Colors.orange),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              AppLocalizations.of(context)!.fritzboxOfflineBanner,
              style: const TextStyle(fontSize: 12, color: Colors.orange),
            ),
          ),
        ],
      ),
    );
  }

  /// Builds the calls list with source indicators.
  Widget _buildCallsList() {
    return ListView.builder(
      itemCount: _screenedCalls.length,
      itemBuilder: (context, index) {
        final call = _screenedCalls[index];
        return _buildCallListItem(call);
      },
    );
  }

  /// Builds the empty state when no calls have been screened yet.
  Widget _buildEmptyState() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.phone_disabled,
              size: 80,
              color: Colors.grey[400],
            ),
            const SizedBox(height: 24),
            Text(
              context.l10n.noCallsYet,
              style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                    color: Colors.grey[600],
                  ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 12),
            Text(
              context.l10n.noCallsDescription,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: Colors.grey[600],
                  ),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }

  /// Builds a single call list item.
  Widget _buildCallListItem(ScreenedCall call) {
    final wasBlocked = call.wasBlocked;
    final source = call.source;

    // Check if call is new (not yet seen by user)
    final bool isNew = call.id != null && newCallIds.contains(call.id);

    // Determine the actual rating to display
    // Use API rating if available, otherwise use generic labels
    final bool hasApiRating = call.rating != null && call.rating != Rating.uNKNOWN;
    final bool hasSpamIndicators = call.votes > 0 || call.votesWildcard > 0;
    // Show as potential spam if not blocked but has spam indicators (votes)
    final bool isPotentialSpam = !wasBlocked && hasSpamIndicators;

    final Rating displayRating;
    if (hasApiRating) {
      // Use actual rating from API
      displayRating = call.rating!;
    } else if (wasBlocked || hasSpamIndicators) {
      // Blocked or has spam indicators but no specific rating - generic SPAM
      displayRating = Rating.uNKNOWN;
    } else {
      // Not blocked and no spam indicators - legitimate
      displayRating = Rating.aLEGITIMATE;
    }

    final color = bgColor(displayRating);
    final String ratingText = (label(context, displayRating) as Text).data!;

    // Build the label text
    final String labelText;
    if (isPotentialSpam) {
      // Show "{rating} ?" for potential spam (e.g., "SPAM ?")
      labelText = '$ratingText ?';
    } else if (wasBlocked) {
      // Show the rating for blocked calls
      labelText = ratingText;
    } else {
      // Show "Legitim" for non-blocked calls without spam indicators
      labelText = context.l10n.ratingLegitimate;
    }

    return Dismissible(
      key: Key('call_${call.id}'),
      background: Container(
        alignment: Alignment.centerLeft,
        padding: const EdgeInsets.only(left: 20),
        color: Colors.red,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.start,
          children: [
            Icon(
              Icons.delete,
              color: Colors.white,
              size: 32,
            ),
            const SizedBox(width: 12),
            Text(
              context.l10n.delete,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 16,
                fontWeight: FontWeight.bold,
              ),
            ),
          ],
        ),
      ),
      secondaryBackground: Container(
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.only(right: 20),
        color: wasBlocked ? Colors.green : Colors.orange,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            Text(
              wasBlocked ? context.l10n.reportAsLegitimate : context.l10n.reportAsSpam,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 16,
                fontWeight: FontWeight.bold,
              ),
            ),
            SizedBox(width: 12),
            Icon(
              wasBlocked ? Icons.check_circle : Icons.report,
              color: Colors.white,
              size: 32,
            ),
          ],
        ),
      ),
      confirmDismiss: (direction) async {
        if (direction == DismissDirection.endToStart) {
          // Swipe left to report
          if (wasBlocked) {
            // SPAM number - report as legitimate
            await _reportAsLegitimate(context, call);
          } else {
            // Legitimate number - report as SPAM
            await _reportAsSpam(context, call);
          }
          return false; // Don't dismiss, just report
        } else {
          // Swipe right to delete
          return true; // Allow dismissal
        }
      },
      onDismissed: (direction) {
        _deleteCall(call);
      },
      child: Card(
        margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        child: InkWell(
          onLongPress: () {},
          onTapDown: (TapDownDetails details) {
            _lastTapPosition = details.globalPosition;
          },
          child: ListTile(
            leading: Stack(
              clipBehavior: Clip.none,
              children: [
                buildRatingAvatar(displayRating),
                if (isNew)
                  Positioned(
                    right: -2,
                    top: -2,
                    child: Container(
                      width: 12,
                      height: 12,
                      decoration: BoxDecoration(
                        color: Colors.blue,
                        shape: BoxShape.circle,
                        border: Border.all(color: Colors.white, width: 2),
                      ),
                    ),
                  ),
                // Mobile source indicator (shown when Fritz!Box calls are also in timeline)
                if (source == CallSource.mobile && _fritzboxState != FritzBoxConnectionState.notConfigured)
                  Positioned(
                    right: -4,
                    bottom: -4,
                    child: Container(
                      padding: const EdgeInsets.all(2),
                      decoration: BoxDecoration(
                        color: Theme.of(context).cardColor,
                        shape: BoxShape.circle,
                      ),
                      child: Icon(
                        Icons.phone_android,
                        size: 14,
                        color: Colors.grey[600],
                      ),
                    ),
                  ),
                // Fritz!Box source indicator
                if (source == CallSource.fritzbox)
                  Positioned(
                    right: -4,
                    bottom: -4,
                    child: Container(
                      padding: const EdgeInsets.all(2),
                      decoration: BoxDecoration(
                        color: Theme.of(context).cardColor,
                        shape: BoxShape.circle,
                      ),
                      child: Icon(
                        Icons.router,
                        size: 14,
                        color: Colors.grey[600],
                      ),
                    ),
                  ),
              ],
            ),
            title: Row(
              children: [
                Expanded(
                  child: Text(
                    call.label ?? call.phoneNumber,
                    style: const TextStyle(
                      fontWeight: FontWeight.bold,
                      fontSize: 16,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: color.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: color, width: 1),
                  ),
                  child: Text(
                    labelText,
                    style: TextStyle(
                      color: color,
                      fontWeight: FontWeight.bold,
                      fontSize: 11,
                    ),
                  ),
                ),
              ],
            ),
            subtitle: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (call.location != null) ...[
                  const SizedBox(height: 2),
                  Row(
                    children: [
                      Icon(
                        Icons.location_on,
                        size: 14,
                        color: Colors.grey[600],
                      ),
                      const SizedBox(width: 4),
                      Flexible(
                        child: Text(
                          call.location!,
                          style: TextStyle(
                            fontSize: 12,
                            color: Colors.grey[600],
                          ),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                ],
                const SizedBox(height: 4),
                Row(
                  children: [
                    // Fritz!Box source badge
                    if (source == CallSource.fritzbox) ...[
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
                        decoration: BoxDecoration(
                          color: Colors.grey.withValues(alpha: 0.1),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(Icons.router, size: 10, color: Colors.grey[600]),
                            const SizedBox(width: 2),
                            Text(
                              AppLocalizations.of(context)!.sourceFritzbox,
                              style: TextStyle(fontSize: 10, color: Colors.grey[600]),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 8),
                    ],
                    // Call status icon and label
                    ..._buildCallStatusBadge(context, call, source, wasBlocked),
                    const SizedBox(width: 8),
                    Text(
                      _formatTimestamp(call.timestamp),
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.grey[600],
                      ),
                    ),
                    // Duration for Fritz!Box calls
                    if (call.duration != null && call.duration! > 0) ...[
                      const SizedBox(width: 8),
                      Text(
                        _formatDuration(call.duration!),
                        style: TextStyle(fontSize: 12, color: Colors.grey[600]),
                      ),
                    ],
                  ],
                ),
                const SizedBox(height: 4),
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        _buildReportsText(call),
                        style: TextStyle(
                          fontSize: 12,
                          color: Colors.grey[600],
                        ),
                      ),
                    ),
                    Builder(
                      builder: (buttonContext) {
                        return InkWell(
                          onTap: () {
                            final RenderBox button = buttonContext.findRenderObject() as RenderBox;
                            _lastTapPosition = button.localToGlobal(Offset.zero);
                            _showCallOptions(context, call);
                          },
                          borderRadius: BorderRadius.circular(12),
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                            child: Icon(
                              Icons.more_vert,
                              size: 18,
                              color: Colors.grey[600],
                            ),
                          ),
                        );
                      },
                    ),
                  ],
                ),
              ],
            ),
            onTap: () {
              _markCallAsSeen(call);
              _viewOnPhoneBlock(call);
            },
            onLongPress: () => _showCallOptions(context, call),
          ),
        ),
      ),
    );
  }

  /// Shows a context menu with options for a call.
  void _showCallOptions(BuildContext context, ScreenedCall call) {
    final RenderBox overlay = Overlay.of(context).context.findRenderObject() as RenderBox;

    final items = <PopupMenuEntry<dynamic>>[];

    // Always show both reporting options for all calls
    items.add(
      PopupMenuItem(
        child: Row(
          children: [
            Icon(Icons.report, color: Colors.orange),
            SizedBox(width: 12),
            Text(context.l10n.reportAsSpam),
          ],
        ),
        onTap: () {
          Future.delayed(Duration.zero, () {
            if (context.mounted) {
              _reportAsSpam(context, call);
            }
          });
        },
      ),
    );
    items.add(
      PopupMenuItem(
        child: Row(
          children: [
            Icon(Icons.check_circle, color: Colors.green),
            SizedBox(width: 12),
            Text(context.l10n.reportAsLegitimate),
          ],
        ),
        onTap: () {
          Future.delayed(Duration.zero, () {
            if (context.mounted) {
              _reportAsLegitimate(context, call);
            }
          });
        },
      ),
    );

    // View on PhoneBlock option
    items.add(
      PopupMenuItem(
        child: Row(
          children: [
            Icon(Icons.open_in_browser, color: Colors.blue),
            SizedBox(width: 12),
            Text(context.l10n.viewOnPhoneBlockMenu),
          ],
        ),
        onTap: () {
          Future.delayed(Duration.zero, () => _viewOnPhoneBlock(call));
        },
      ),
    );

    // Always show delete option
    items.add(
      PopupMenuItem(
        child: Row(
          children: [
            Icon(Icons.delete, color: Colors.red),
            SizedBox(width: 12),
            Text(context.l10n.deleteCall),
          ],
        ),
        onTap: () {
          // Need to delay deletion slightly to allow menu to close
          Future.delayed(Duration.zero, () => _deleteCall(call));
        },
      ),
    );

    showMenu(
      context: context,
      position: RelativeRect.fromRect(
        _lastTapPosition & const Size(40, 40),
        Offset.zero & overlay.size,
      ),
      items: items,
    );
  }

  /// Reports a call as legitimate.
  Future<void> _reportAsLegitimate(BuildContext context, ScreenedCall call) async {
    // Show comment dialog
    final comment = await _showCommentDialog(
      context,
      title: context.l10n.addCommentLegitimate,
      hint: context.l10n.commentHintLegitimate,
    );

    if (comment == null) {
      return; // User cancelled
    }

    try {
      String? token = await getAuthToken();
      if (token == null) {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(context.l10n.notLoggedIn),
              backgroundColor: Colors.red,
            ),
          );
        }
        return;
      }

      // Create RateRequest with LEGITIMATE rating
      final rateRequest = api.RateRequest(
        phone: call.phoneNumber,
        rating: api.Rating.aLegitimate,
        comment: comment,
      );

      // Serialize to JSON
      final buffer = StringBuffer();
      final jsonWriter = jsonStringWriter(buffer);
      rateRequest.writeContent(jsonWriter);
      final jsonBody = buffer.toString();

      // Call the rate API
      final response = await http.post(
        Uri.parse('$pbBaseUrl/api/rate'),
        headers: {
          'Authorization': 'Bearer $token',
          'Content-Type': 'application/json',
        },
        body: jsonBody,
      );

      if (context.mounted) {
        if (response.statusCode == 200) {
          // Update all calls with this phone number to mark as legitimate
          await ScreenedCallsDatabase.instance.updateCallsByPhoneNumber(
            call.phoneNumber,
            false, // Mark as not blocked/legitimate
            rating: Rating.aLEGITIMATE,
          );

          // Reload the calls list to reflect the change
          await _loadScreenedCalls();

          if (context.mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(context.l10n.reportedAsLegitimate(call.phoneNumber)),
                backgroundColor: Colors.green,
              ),
            );
          }
        } else {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(context.l10n.reportError(response.statusCode.toString())),
              backgroundColor: Colors.red,
            ),
          );
        }
      }
    } catch (e) {
      if (kDebugMode) {
        print('Error reporting legitimate: $e');
      }

      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.reportError(e.toString())),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  /// Shows rating selection dialog and reports the call as spam.
  Future<void> _reportAsSpam(BuildContext context, ScreenedCall call) async {
    final rating = await _showRatingDialog(context);

    if (rating == null) {
      return; // User cancelled
    }

    // Show comment dialog after rating is selected
    if (!context.mounted) return;
    final comment = await _showCommentDialog(
      context,
      title: context.l10n.addCommentSpam,
      hint: context.l10n.commentHintSpam,
    );

    if (comment == null) {
      return; // User cancelled
    }

    try {
      String? token = await getAuthToken();
      if (token == null) {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(context.l10n.notLoggedIn),
              backgroundColor: Colors.red,
            ),
          );
        }
        return;
      }

      // Create RateRequest with proper Rating enum from api.dart
      final rateRequest = api.RateRequest(
        phone: call.phoneNumber,
        rating: _convertRating(rating),
        comment: comment,
      );

      // Serialize to JSON using writeContent() (not toString() which includes type info)
      final buffer = StringBuffer();
      final jsonWriter = jsonStringWriter(buffer);
      rateRequest.writeContent(jsonWriter);
      final jsonBody = buffer.toString();

      // Call the rate API
      final response = await http.post(
        Uri.parse('$pbBaseUrl/api/rate'),
        headers: {
          'Authorization': 'Bearer $token',
          'Content-Type': 'application/json',
        },
        body: jsonBody,
      );

      if (context.mounted) {
        if (response.statusCode == 200) {
          // Update all calls with this phone number to mark as blocked with the rating
          await ScreenedCallsDatabase.instance.updateCallsByPhoneNumber(
            call.phoneNumber,
            true, // Mark as blocked/SPAM
            rating: rating, // Store the rating type
          );

          // Reload the calls list to reflect the change
          await _loadScreenedCalls();

          if (context.mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(context.l10n.reportedAsSpam(call.phoneNumber)),
                backgroundColor: Colors.green,
              ),
            );
          }
        } else {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(context.l10n.reportError(response.statusCode.toString())),
              backgroundColor: Colors.red,
            ),
          );
        }
      }
    } catch (e) {
      if (kDebugMode) {
        print('Error reporting spam: $e');
      }

      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.reportError(e.toString())),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  /// Shows a dialog to select spam rating.
  Future<Rating?> _showRatingDialog(BuildContext context) async {
    return showDialog<Rating>(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: Text(context.l10n.selectSpamCategory),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: Rating.values
                  .where((r) => r != Rating.aLEGITIMATE) // Exclude legitimate
                  .map((rating) => ListTile(
                        leading: icon(rating),
                        title: label(context, rating),
                        tileColor: bgColor(rating).withValues(alpha: 0.1),
                        onTap: () {
                          Navigator.of(context).pop(rating);
                        },
                      ))
                  .toList(),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: Text(context.l10n.cancel),
            ),
          ],
        );
      },
    );
  }

  /// Shows a dialog to optionally enter a comment/reason for the rating.
  /// Returns the comment text or null if cancelled.
  Future<String?> _showCommentDialog(BuildContext context, {required String title, required String hint}) async {
    final controller = TextEditingController();

    return showDialog<String>(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: Text(title),
          content: TextField(
            controller: controller,
            decoration: InputDecoration(
              hintText: hint,
              border: const OutlineInputBorder(),
            ),
            maxLines: 3,
            maxLength: 500,
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(null),
              child: Text(context.l10n.cancel),
            ),
            TextButton(
              onPressed: () => Navigator.of(context).pop(controller.text),
              child: Text(context.l10n.report),
            ),
          ],
        );
      },
    );
  }

  /// Converts state.Rating enum to api.Rating enum.
  api.Rating _convertRating(Rating rating) {
    switch (rating) {
      case Rating.aLEGITIMATE: return api.Rating.aLegitimate;
      case Rating.uNKNOWN: return api.Rating.bMissed;
      case Rating.pING: return api.Rating.cPing;
      case Rating.pOLL: return api.Rating.dPoll;
      case Rating.aDVERTISING: return api.Rating.eAdvertising;
      case Rating.gAMBLE: return api.Rating.fGamble;
      case Rating.fRAUD: return api.Rating.gFraud;
    }
  }

  /// Deletes all calls from the database and updates the UI.
  Future<void> _deleteAllCalls() async {
    // Clear UI immediately
    setState(() {
      _screenedCalls.clear();
    });

    try {
      // Delete all from database
      await ScreenedCallsDatabase.instance.deleteAllScreenedCalls();

      if (kDebugMode) {
        print('Deleted all calls');
      }
    } catch (e) {
      if (kDebugMode) {
        print('Error deleting all calls: $e');
      }

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.errorDeletingAllCalls),
            backgroundColor: Colors.red,
          ),
        );
      }

      // Reload calls from database if deletion failed
      await _loadScreenedCalls();
    }
  }

  /// Deletes a call from the database and updates the UI.
  Future<void> _deleteCall(ScreenedCall call) async {
    // Remove from UI immediately
    setState(() {
      _screenedCalls.remove(call);
    });

    try {
      // Delete from database
      if (call.id != null) {
        await ScreenedCallsDatabase.instance.deleteScreenedCall(call.id!);
      }

      if (kDebugMode) {
        print('Deleted call: ${call.phoneNumber}');
      }
    } catch (e) {
      if (kDebugMode) {
        print('Error deleting call: $e');
      }

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.errorDeletingCall),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  /// Shows a dialog to search for a phone number.
  void _showSearchDialog(BuildContext context) {
    final TextEditingController controller = TextEditingController();

    showDialog(
      context: context,
      builder: (BuildContext dialogContext) {
        return AlertDialog(
          title: Text(context.l10n.searchPhoneNumber),
          content: TextField(
            controller: controller,
            keyboardType: TextInputType.phone,
            decoration: InputDecoration(
              labelText: context.l10n.enterPhoneNumber,
              hintText: context.l10n.phoneNumberHint,
              border: OutlineInputBorder(),
            ),
            autofocus: true,
            onSubmitted: (value) {
              Navigator.of(dialogContext).pop();
              _searchPhoneNumber(value.trim());
            },
          ),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.of(dialogContext).pop();
              },
              child: Text(context.l10n.cancel),
            ),
            TextButton(
              onPressed: () {
                Navigator.of(dialogContext).pop();
                _searchPhoneNumber(controller.text.trim());
              },
              child: Text(context.l10n.search),
            ),
          ],
        );
      },
    );
  }

  /// Searches for a phone number and opens it in the PhoneBlock WebView.
  Future<void> _searchPhoneNumber(String phoneNumber) async {
    if (phoneNumber.isEmpty) {
      return;
    }

    final cleanedNumber = phoneNumber.trim();

    if (cleanedNumber.isEmpty) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.invalidPhoneNumber),
            backgroundColor: Colors.red,
          ),
        );
      }
      return;
    }

    String? token = await getAuthToken();
    if (token == null) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.notLoggedInShort),
            backgroundColor: Colors.red,
          ),
        );
      }
      return;
    }

    if (mounted) {
      Navigator.push(
        context,
        MaterialPageRoute(
          builder: (context) => PhoneBlockWebView(
            title: cleanedNumber,
            path: '/nums/$cleanedNumber',
            authToken: token,
          ),
        ),
      );
    }
  }

  /// Builds the navigation drawer with menu items.
  Widget _buildDrawer(BuildContext context) {
    return Drawer(
      child: ListView(
        padding: EdgeInsets.zero,
        children: [
          DrawerHeader(
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.primary,
            ),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Image.asset(
                  'assets/images/spam_icon.png',
                  width: 80,
                  height: 80,
                ),
                const SizedBox(height: 8),
                Text(
                  context.l10n.appTitle,
                  style: TextStyle(
                    color: Theme.of(context).colorScheme.onPrimary,
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
          ),
          // Fritz!Box menu item
          ListTile(
            leading: _buildFritzBoxIcon(),
            title: Text(AppLocalizations.of(context)!.fritzboxTitle),
            subtitle: Text(_getFritzBoxStatusText(context)),
            onTap: () async {
              Navigator.pop(context); // Close drawer
              await Navigator.push(
                context,
                MaterialPageRoute(builder: (context) => const FritzBoxSettingsScreen()),
              );
              // Reload Fritz!Box state when returning from settings
              if (mounted) {
                await _checkFritzBoxConnection();
                await _loadScreenedCalls();
              }
            },
          ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.block),
            title: Text(context.l10n.blacklistTitle),
            subtitle: Text(context.l10n.blacklistDescription),
            onTap: () async {
              Navigator.pop(context); // Close drawer

              String? token = await getAuthToken();
              if (token == null) {
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text(context.l10n.notLoggedInShort),
                      backgroundColor: Colors.red,
                    ),
                  );
                }
                return;
              }

              if (context.mounted) {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (context) => BlacklistScreen(authToken: token),
                  ),
                );
              }
            },
          ),
          ListTile(
            leading: const Icon(Icons.check_circle),
            title: Text(context.l10n.whitelistTitle),
            subtitle: Text(context.l10n.whitelistDescription),
            onTap: () async {
              Navigator.pop(context); // Close drawer

              String? token = await getAuthToken();
              if (token == null) {
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text(context.l10n.notLoggedInShort),
                      backgroundColor: Colors.red,
                    ),
                  );
                }
                return;
              }

              if (context.mounted) {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (context) => WhitelistScreen(authToken: token),
                  ),
                );
              }
            },
          ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.settings),
            title: Text(context.l10n.settings),
            onTap: () async {
              Navigator.pop(context); // Close drawer
              await Navigator.push(
                context,
                MaterialPageRoute(builder: (context) => const SettingsScreen()),
              );
              // Reload answerbot enabled state when returning from settings
              if (context.mounted) {
                await _loadAnswerbotEnabled();
              }
            },
          ),
          if (_answerbotEnabled)
            ListTile(
              leading: const Icon(Icons.phone_callback),
              title: Text(context.l10n.answerbotMenuTitle),
              subtitle: Text(context.l10n.answerbotMenuDescription),
              onTap: () {
                Navigator.pop(context); // Close drawer
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (context) => const AnswerBotList()),
                );
              },
            ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.favorite, color: Colors.orange),
            title: Text(context.l10n.donate),
            subtitle: Text(context.l10n.aboutDescription),
            onTap: () async {
              final title = context.l10n.donate;
              Navigator.pop(context); // Close drawer

              String? token = await getAuthToken();
              if (token == null) {
                return;
              }

              if (context.mounted) {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (context) => PhoneBlockWebView(
                      title: title,
                      path: '/support',
                      authToken: token,
                    ),
                  ),
                );
              }
            },
          ),
          ListTile(
            leading: const Icon(Icons.info_outline),
            title: Text(context.l10n.about),
            onTap: () {
              Navigator.pop(context); // Close drawer
              Navigator.push(
                context,
                MaterialPageRoute(builder: (context) => const AboutScreen()),
              );
            },
          ),
        ],
      ),
    );
  }

  /// Opens the phone number on PhoneBlock website in a WebView.
  Future<void> _viewOnPhoneBlock(ScreenedCall call) async {
    String? token = await getAuthToken();
    if (token == null) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.notLoggedInShort),
            backgroundColor: Colors.red,
          ),
        );
      }
      return;
    }

    if (mounted) {
      Navigator.push(
        context,
        MaterialPageRoute(
          builder: (context) => PhoneBlockWebView(
            title: call.phoneNumber,
            path: '/nums/${call.phoneNumber}',
            authToken: token,
          ),
        ),
      );
    }
  }

  /// Marks a call as seen (removes from new calls set).
  void _markCallAsSeen(ScreenedCall call) {
    if (call.id != null && newCallIds.contains(call.id)) {
      setState(() {
        newCallIds.remove(call.id);
      });
    }
  }

  /// Gets the icon color for Fritz!Box based on connection state.
  /// Builds the Fritz!Box drawer icon with shield badge for protection status.
  Widget _buildFritzBoxIcon() {
    final routerColor = _getFritzBoxIconColor();
    final isProtected = _cardDavStatus == CardDavStatus.synced;
    final isConnected = _fritzboxState == FritzBoxConnectionState.connected;

    if (isConnected) {
      return Stack(
        clipBehavior: Clip.none,
        children: [
          Icon(Icons.router, color: routerColor),
          Positioned(
            right: -6,
            bottom: -6,
            child: Container(
              decoration: BoxDecoration(
                color: Theme.of(context).scaffoldBackgroundColor,
                shape: BoxShape.circle,
              ),
              padding: const EdgeInsets.all(1),
              child: Icon(
                isProtected ? Icons.shield : Icons.shield_outlined,
                size: 16,
                color: isProtected ? Colors.green : Colors.grey,
              ),
            ),
          ),
        ],
      );
    }

    return Icon(Icons.router, color: routerColor);
  }

  Color _getFritzBoxIconColor() {
    switch (_fritzboxState) {
      case FritzBoxConnectionState.connected:
        return _cardDavStatus == CardDavStatus.synced ? Colors.green : Colors.orange;
      case FritzBoxConnectionState.offline:
        return Colors.orange;
      case FritzBoxConnectionState.error:
        return Colors.red;
      case FritzBoxConnectionState.notConfigured:
        return Colors.grey;
    }
  }

  /// Gets the status text for Fritz!Box.
  String _getFritzBoxStatusText(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    switch (_fritzboxState) {
      case FritzBoxConnectionState.connected:
        if (_cardDavStatus == CardDavStatus.synced) {
          return l10n.fritzboxConnected;
        }
        return l10n.fritzboxConnectedNotProtected;
      case FritzBoxConnectionState.offline:
        return l10n.fritzboxOffline;
      case FritzBoxConnectionState.error:
        return l10n.fritzboxError;
      case FritzBoxConnectionState.notConfigured:
        return l10n.fritzboxNotConfiguredShort;
    }
  }

  /// Builds the reports text showing votes and range votes.
  String _buildReportsText(ScreenedCall call) {
    final parts = <String>[];

    if (call.votes > 0) {
      parts.add(context.l10n.reportsCount(call.votes));
    } else if (call.votes < 0) {
      parts.add(context.l10n.legitimateReportsCount(call.votes.abs()));
    }

    if (call.votesWildcard > call.votes) {
      parts.add(context.l10n.rangeReportsCount(call.votesWildcard));
    }

    if (parts.isEmpty) {
      return context.l10n.noReports;
    }

    return parts.join(', ');
  }

  /// Formats the timestamp for display.
  String _formatTimestamp(DateTime timestamp) {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final yesterday = today.subtract(const Duration(days: 1));
    final callDate = DateTime(timestamp.year, timestamp.month, timestamp.day);

    final timeFormat = DateFormat('HH:mm');
    final dateFormat = DateFormat('dd.MM.yyyy');

    if (callDate == today) {
      return context.l10n.todayTime(timeFormat.format(timestamp));
    } else if (callDate == yesterday) {
      return context.l10n.yesterdayTime(timeFormat.format(timestamp));
    } else {
      return '${dateFormat.format(timestamp)}, ${timeFormat.format(timestamp)}';
    }
  }

  /// Formats duration in seconds to a readable string (M:SS).
  String _formatDuration(int seconds) {
    final minutes = seconds ~/ 60;
    final secs = seconds % 60;
    return '$minutes:${secs.toString().padLeft(2, '0')}';
  }

  /// Builds the call status badge (blocked/accepted/missed) based on source and call type.
  List<Widget> _buildCallStatusBadge(
    BuildContext context,
    ScreenedCall call,
    CallSource source,
    bool wasBlocked,
  ) {
    IconData icon;
    String label;
    Color color;

    if (source == CallSource.fritzbox) {
      // Fritz!Box calls: show based on actual call type
      if (wasBlocked) {
        // Rejected by call barring
        icon = Icons.block;
        label = context.l10n.blocked;
        color = Colors.red[400]!;
      } else if (call.callType == FritzBoxCallType.missed) {
        // Missed call
        icon = Icons.phone_missed;
        label = context.l10n.missed;
        color = Colors.orange[400]!;
      } else {
        // Answered call
        icon = Icons.phone_callback;
        label = context.l10n.accepted;
        color = Colors.green[400]!;
      }
    } else {
      // Mobile calls: blocked/accepted by call screening
      if (wasBlocked) {
        icon = Icons.block;
        label = context.l10n.blocked;
        color = Colors.red[400]!;
      } else {
        icon = Icons.check_circle_outline;
        label = context.l10n.accepted;
        color = Colors.green[400]!;
      }
    }

    return [
      Icon(icon, size: 14, color: color),
      const SizedBox(width: 4),
      Text(
        label,
        style: TextStyle(
          fontSize: 12,
          color: color,
          fontWeight: FontWeight.w500,
        ),
      ),
    ];
  }
}

class VerifyLogin extends StatefulWidget {
  final String token;

  const VerifyLogin(this.token, {super.key});

  @override
  State<StatefulWidget> createState() => VerifyLoginState();
}

class VerifyLoginState extends State<VerifyLogin> {
  late Future<http.Response> checkResult;

  @override
  void initState() {
    super.initState();

    var token = widget.token;
    setAuthToken(token);

    // Verify token and update account settings with device locale
    checkResult = callPhoneBlockApi(pbApiTest, authToken: token).then((response) async {
      if (response.statusCode == 200) {
        // Token is valid, update account settings with device locale
        final localeSettings = await getDeviceLocaleSettings();
        await updateAccountSettings(token, localeSettings);
      }
      return response;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(context.l10n.verifyingLoginTitle),
      ),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Center(
            child: FutureBuilder(
              future: checkResult,
              builder: (context, state) {
                if (state.hasData) {
                  // Login successful, redirect to setup wizard
                  WidgetsBinding.instance.addPostFrameCallback((_) {
                    context.go('/setup');
                  });
                  return Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(Icons.check_circle, color: Colors.green, size: 64),
                      const SizedBox(height: 16),
                      Text(context.l10n.loginSuccessMessage, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                      const SizedBox(height: 8),
                      Text(context.l10n.redirectingToSetup),
                    ],
                  );
                } else if (state.hasError) {
                  return Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(Icons.error, color: Colors.red, size: 64),
                      const SizedBox(height: 16),
                      Text(context.l10n.tokenVerificationFailed(state.error.toString())),
                      const SizedBox(height: 16),
                      ElevatedButton(
                        onPressed: () => context.go('/setup'),
                        child: Text(context.l10n.backToSetup),
                      ),
                    ],
                  );
                } else {
                  return Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const CircularProgressIndicator(),
                      const SizedBox(height: 16),
                      Text(context.l10n.tokenBeingVerified),
                    ],
                  );
                }
              }),
          ),
        ],
      ),
    );
  }
}

class LoginFailed extends StatelessWidget {
  const LoginFailed({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(context.l10n.loginFailed),
      ),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Center(
            child: Text(context.l10n.noLoginTokenReceived),
          ),
        ],
      ),
    );
  }
}

void setAuthToken(String token) {
  platform.invokeMethod("setAuthToken", token);
}

Future<String?> getAuthToken() async {
  return platform.invokeMethod("getAuthToken");
}

Future<bool> checkPermission() async {
  try {
    return await platform.invokeMethod("checkPermission");
  } catch (e) {
    if (kDebugMode) {
      print("Error checking permission: $e");
    }
    return false;
  }
}

/// Validates that the auth token is valid by testing it against the API.
/// Returns true if token exists and is valid, false otherwise.
Future<bool> validateAuthToken() async {
  try {
    String? token = await getAuthToken();
    if (token == null) {
      return false;
    }

    final response = await callPhoneBlockApi(pbApiTest, authToken: token);
    return response.statusCode == 200;
  } catch (e) {
    if (kDebugMode) {
      print("Error validating auth token: $e");
    }
    return false;
  }
}

void registerPhoneBlock(BuildContext context) async {
  final loginUrl = await _getPhoneBlockLoginUrl();
  bool ok = await launchUrl(Uri.parse(loginUrl));
  if (!ok && context.mounted) {
    final snackBar = SnackBar(content: Text(context.l10n.failedToOpenPhoneBlock));
    ScaffoldMessenger.of(context).showSnackBar(snackBar);
  }
}

Widget label(BuildContext context, Rating rating) {
  switch (rating) {
    case Rating.aLEGITIMATE: return Text(context.l10n.ratingLegitimate, style: const TextStyle(color: Colors.white));
    case Rating.aDVERTISING: return Text(context.l10n.ratingAdvertising, style: const TextStyle(color: Color.fromRGBO(0,0,0,.7)));
    case Rating.uNKNOWN: return Text(context.l10n.ratingSpam, style: const TextStyle(color: Colors.white));
    case Rating.pING: return Text(context.l10n.ratingPingCall, style: const TextStyle(color: Colors.white));
    case Rating.gAMBLE: return Text(context.l10n.ratingGamble, style: const TextStyle(color: Colors.white));
    case Rating.fRAUD: return Text(context.l10n.ratingFraud, style: const TextStyle(color: Colors.white));
    case Rating.pOLL: return Text(context.l10n.ratingPoll, style: const TextStyle(color: Colors.white));
  }
}

Color bgColor(Rating rating) {
  switch (rating) {
    case Rating.aLEGITIMATE: return const Color.fromRGBO(72, 199, 142, 1);
    case Rating.uNKNOWN: return const Color.fromRGBO(255, 152, 0, 1); // Orange for unknown spam
    case Rating.pING: return const Color.fromRGBO(31, 94, 220, 1);
    case Rating.pOLL: return const Color.fromRGBO(157, 31, 220, 1);
    case Rating.aDVERTISING: return const Color.fromRGBO(255, 152, 0, 1); // Darker orange for better contrast
    case Rating.gAMBLE: return const Color.fromRGBO(241, 122, 70, 1);
    case Rating.fRAUD: return const Color.fromRGBO(241, 70, 104, 1);
  }
}

Icon icon(Rating rating) {
  switch (rating) {
    case Rating.aLEGITIMATE: return const Icon(Icons.check);
    case Rating.uNKNOWN: return const Icon(Icons.question_mark);
    case Rating.pING: return const Icon(Icons.block);
    case Rating.pOLL: return const Icon(Icons.query_stats);
    case Rating.aDVERTISING: return const Icon(Icons.ondemand_video);
    case Rating.gAMBLE: return const Icon(Icons.videogame_asset_off);
    case Rating.fRAUD: return const Icon(Icons.warning);
  }
}

/// Converts api.Rating enum to state.Rating enum.
Rating _convertApiRating(api.Rating rating) {
  switch (rating) {
    case api.Rating.aLegitimate: return Rating.aLEGITIMATE;
    case api.Rating.bMissed: return Rating.uNKNOWN;
    case api.Rating.cPing: return Rating.pING;
    case api.Rating.dPoll: return Rating.pOLL;
    case api.Rating.eAdvertising: return Rating.aDVERTISING;
    case api.Rating.fGamble: return Rating.gAMBLE;
    case api.Rating.gFraud: return Rating.fRAUD;
  }
}

/// Creates a CircleAvatar with rating-specific icon and colors.
Widget buildRatingAvatar(Rating rating) {
  final color = bgColor(rating);
  return CircleAvatar(
    backgroundColor: color.withValues(alpha: 0.1),
    child: Icon(icon(rating).icon!, color: color),
  );
}

class RateScreen extends StatelessWidget {
  final Call call;

  const RateScreen(this.call, {super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(context.l10n.ratePhoneNumber(call.phone)),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: Rating.values.map((rating) => ratingButton(context, rating, call)).toList()
        )
      ),
    );
  }

  Widget ratingButton(BuildContext context, Rating rating, Call call) {
    return Container(
      margin: const EdgeInsets.all(10),
      child: ElevatedButton(
        style: ElevatedButton.styleFrom(
          backgroundColor: bgColor(rating),
          shadowColor: Colors.blueGrey,
          elevation: 3,
          shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(32.0)),
          minimumSize: const Size(200, 60),
          maximumSize: const Size(200, 60),
        ),
        onPressed: () {
          Navigator.pop(context);
        },
        child: Row(mainAxisSize: MainAxisSize.max, children: [
          Padding(padding: const EdgeInsets.only(right: 10), child: icon(rating)),
          label(context, rating)
        ]),
      ),
    );
  }

}

/// Common WebView screen for displaying PhoneBlock web pages.
///
/// This widget provides a standardized WebView implementation for displaying
/// PhoneBlock service pages within the mobile app. It handles:
/// - Authentication via Bearer token
/// - Loading indicators
/// - Proper User-Agent headers
/// - JavaScript support
class PhoneBlockWebView extends StatefulWidget {
  /// The title to display in the app bar.
  final String title;

  /// The path to load (relative to pbBaseUrl), e.g., '/settings' or '/nums/+1234567890'.
  final String path;

  /// The authentication token for API access.
  final String authToken;

  const PhoneBlockWebView({
    super.key,
    required this.title,
    required this.path,
    required this.authToken,
  });

  @override
  State<PhoneBlockWebView> createState() => _PhoneBlockWebViewState();
}

class _PhoneBlockWebViewState extends State<PhoneBlockWebView> {
  late final WebViewController _controller;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();

    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setUserAgent('PhoneBlockMobile/$appVersion')
      ..setNavigationDelegate(
        NavigationDelegate(
          onProgress: (int progress) {
            // Use progress callback for reliable loading state tracking.
            // loadRequest() with custom headers may not trigger onPageStarted/onPageFinished reliably.
            setState(() {
              _isLoading = progress < 100;
            });
          },
          onNavigationRequest: (NavigationRequest request) {
            // Intercept all navigation requests to add custom headers
            if (request.url.startsWith(pbBaseUrl)) {
              // This is a PhoneBlock page - load it with our custom headers
              _loadPageWithHeaders(request.url);
            } else {
              // External link - open in system browser instead of WebView
              _launchExternalUrl(request.url);
            }
            // Prevent default navigation since we're handling it ourselves
            return NavigationDecision.prevent;
          },
        ),
      );

    _loadPageWithHeaders('$pbBaseUrl${widget.path}');
  }

  /// Loads a page with custom headers (Authorization and Accept-Language).
  Future<void> _loadPageWithHeaders(String url) async {
    // Show loading indicator immediately
    setState(() {
      _isLoading = true;
    });

    // Get device locale for Accept-Language header
    final languageTag = getDeviceLocale();

    if (kDebugMode) {
      print("Loading with language '$languageTag': $url");
    }

    await _controller.loadRequest(
      Uri.parse(url),
      headers: {
        'Authorization': 'Bearer ${widget.authToken}',
        'Accept-Language': languageTag,
      },
    );
  }

  /// Opens an external URL in the system's default browser.
  Future<void> _launchExternalUrl(String url) async {
    final uri = Uri.parse(url);
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    } else {
      if (kDebugMode) {
        print('Could not launch external URL: $url');
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title),
      ),
      body: Stack(
        children: [
          WebViewWidget(controller: _controller),
          if (_isLoading)
            Container(
              color: Colors.white.withValues(alpha: 0.8),
              child: const Center(
                child: CircularProgressIndicator(),
              ),
            ),
        ],
      ),
    );
  }
}

/// Settings screen for app configuration.
class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  int _minVotes = 4;
  bool _blockRanges = true;
  int _minRangeVotes = 10;
  int _retentionDays = retentionDefault;
  String _themeMode = 'system';
  bool _answerbotEnabled = false;
  bool _isLoading = true;
  int _blockedCallsCount = 0;
  int _suspiciousCallsCount = 0;

  @override
  void initState() {
    super.initState();
    _loadSettings();
  }

  @override
  void dispose() {
    super.dispose();
  }

  /// Load settings from SharedPreferences.
  Future<void> _loadSettings() async {
    try {
      final minVotesResult = await platform.invokeMethod("getMinVotes");
      final blockRangesResult = await platform.invokeMethod("getBlockRanges");
      final minRangeVotesResult = await platform.invokeMethod("getMinRangeVotes");
      final retentionDaysResult = await platform.invokeMethod("getRetentionDays");
      final themeModeResult = await getThemeMode();
      final answerbotEnabledResult = await platform.invokeMethod("getAnswerbotEnabled");
      final blockedCallsCountResult = await platform.invokeMethod("getBlockedCallsCount");
      final suspiciousCallsCountResult = await platform.invokeMethod("getInspectedSuspiciousCount");

      if (kDebugMode) {
        print("Loaded settings - minVotes: $minVotesResult, blockRanges: $blockRangesResult, minRangeVotes: $minRangeVotesResult, retentionDays: $retentionDaysResult, themeMode: $themeModeResult, answerbotEnabled: $answerbotEnabledResult");
      }

      setState(() {
        _minVotes = minVotesResult ?? 4;
        _blockRanges = blockRangesResult ?? true;
        _minRangeVotes = minRangeVotesResult ?? 10;
        _retentionDays = retentionDaysResult ?? retentionDefault;
        _themeMode = themeModeResult;
        _answerbotEnabled = answerbotEnabledResult ?? false;
        _blockedCallsCount = blockedCallsCountResult ?? 0;
        _suspiciousCallsCount = suspiciousCallsCountResult ?? 0;
        _isLoading = false;
      });

      if (kDebugMode) {
        print("Set state - minVotes: $_minVotes, blockRanges: $_blockRanges, minRangeVotes: $_minRangeVotes, themeMode: $_themeMode, answerbotEnabled: $_answerbotEnabled");
      }
    } catch (e) {
      if (kDebugMode) {
        print("Error loading settings: $e");
      }
      setState(() {
        _isLoading = false;
      });
    }
  }

  /// Save minimum votes setting.
  Future<void> _saveMinVotes(int value) async {
    if (kDebugMode) {
      print("Saving minVotes: $value");
    }
    try {
      await platform.invokeMethod("setMinVotes", value);
      if (kDebugMode) {
        print("Successfully saved minVotes to SharedPreferences: $value");
      }
      setState(() {
        _minVotes = value;
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.settingSaved),
            duration: const Duration(seconds: 2),
          ),
        );
      }
    } catch (e) {
      if (kDebugMode) {
        print("Error saving min votes: $e");
      }
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.errorSaving),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  /// Save block ranges setting.
  Future<void> _saveBlockRanges(bool value) async {
    if (kDebugMode) {
      print("Saving blockRanges: $value");
    }
    try {
      await platform.invokeMethod("setBlockRanges", value);
      if (kDebugMode) {
        print("Successfully saved blockRanges to SharedPreferences: $value");
      }
      setState(() {
        _blockRanges = value;
      });
    } catch (e) {
      if (kDebugMode) {
        print("Error saving block ranges: $e");
      }
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.errorSaving),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  /// Save minimum range votes setting.
  Future<void> _saveMinRangeVotes(int value) async {
    if (kDebugMode) {
      print("Saving minRangeVotes: $value");
    }
    try {
      await platform.invokeMethod("setMinRangeVotes", value);
      if (kDebugMode) {
        print("Successfully saved minRangeVotes to SharedPreferences: $value");
      }
      setState(() {
        _minRangeVotes = value;
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.settingSaved),
            duration: const Duration(seconds: 2),
          ),
        );
      }
    } catch (e) {
      if (kDebugMode) {
        print("Error saving min range votes: $e");
      }
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.errorSaving),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  /// Save retention days setting.
  Future<void> _saveRetentionDays(int value) async {
    if (kDebugMode) {
      print("Saving retentionDays: $value");
    }
    try {
      await platform.invokeMethod("setRetentionDays", value);
      if (kDebugMode) {
        print("Successfully saved retentionDays to SharedPreferences: $value");
      }
      setState(() {
        _retentionDays = value;
      });

      // Clean up old calls immediately when retention period changes
      // (This handles both mobile and Fritz!Box calls in the unified table)
      await ScreenedCallsDatabase.instance.deleteOldScreenedCalls(value);

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.settingSaved),
            duration: const Duration(seconds: 2),
          ),
        );
      }
    } catch (e) {
      if (kDebugMode) {
        print("Error saving retention days: $e");
      }
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.errorSaving),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  /// Save theme mode setting.
  Future<void> _saveThemeMode(String value) async {
    if (kDebugMode) {
      print("Saving themeMode: $value");
    }
    try {
      await setThemeMode(value);
      if (kDebugMode) {
        print("Successfully saved themeMode to SharedPreferences: $value");
      }
      setState(() {
        _themeMode = value;
      });

      // Update the app's theme mode
      if (context.mounted) {
        final appState = context.findAncestorStateOfType<_PhoneBlockAppState>();
        appState?.updateThemeMode(value);

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.settingSaved),
            duration: const Duration(seconds: 2),
          ),
        );
      }
    } catch (e) {
      if (kDebugMode) {
        print("Error saving theme mode: $e");
      }
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.errorSaving),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  /// Save answerbot enabled setting.
  Future<void> _saveAnswerbotEnabled(bool value) async {
    if (kDebugMode) {
      print("Saving answerbotEnabled: $value");
    }
    try {
      await platform.invokeMethod("setAnswerbotEnabled", value);
      if (kDebugMode) {
        print("Successfully saved answerbotEnabled to SharedPreferences: $value");
      }
      setState(() {
        _answerbotEnabled = value;
      });
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.settingSaved),
            duration: const Duration(seconds: 2),
          ),
        );
      }
    } catch (e) {
      if (kDebugMode) {
        print("Error saving answerbot enabled: $e");
      }
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.errorSaving),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(context.l10n.settingsTitle),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              children: [
                Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Text(
                    context.l10n.callScreening,
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.bold,
                      color: Colors.grey,
                    ),
                  ),
                ),
                ListTile(
                  title: Text(context.l10n.minReportsCount),
                  subtitle: Text(
                    context.l10n.callsBlockedAfterReports(_minVotes),
                    style: TextStyle(fontSize: 12, color: Colors.grey[600]),
                  ),
                  trailing: DropdownButton<int>(
                    value: _minVotes,
                    items: const [
                      DropdownMenuItem(value: 2, child: Text('2')),
                      DropdownMenuItem(value: 4, child: Text('4')),
                      DropdownMenuItem(value: 10, child: Text('10')),
                      DropdownMenuItem(value: 20, child: Text('20')),
                      DropdownMenuItem(value: 50, child: Text('50')),
                      DropdownMenuItem(value: 100, child: Text('100')),
                    ],
                    onChanged: (value) {
                      if (value != null) {
                        _saveMinVotes(value);
                      }
                    },
                  ),
                ),
                const Divider(),

                // Range blocking toggle
                SwitchListTile(
                  title: Text(context.l10n.blockNumberRanges),
                  subtitle: Text(context.l10n.blockNumberRangesDescription),
                  value: _blockRanges,
                  onChanged: (value) {
                    _saveBlockRanges(value);
                  },
                ),

                // Range threshold (only shown when toggle is active)
                if (_blockRanges)
                  ListTile(
                    title: Text(context.l10n.minSpamReportsInRange),
                    subtitle: Text(
                      context.l10n.rangesBlockedAfterReports(_minRangeVotes),
                      style: TextStyle(fontSize: 12, color: Colors.grey[600]),
                    ),
                    trailing: DropdownButton<int>(
                      value: _minRangeVotes,
                      items: const [
                        DropdownMenuItem(value: 10, child: Text('10')),
                        DropdownMenuItem(value: 20, child: Text('20')),
                        DropdownMenuItem(value: 50, child: Text('50')),
                        DropdownMenuItem(value: 100, child: Text('100')),
                        DropdownMenuItem(value: 500, child: Text('500')),
                      ],
                      onChanged: (value) {
                        if (value != null) {
                          _saveMinRangeVotes(value);
                        }
                      },
                    ),
                  ),
                const Divider(),
                // Retention period setting
                ListTile(
                  title: Text(context.l10n.callHistoryRetention),
                  subtitle: Text(
                    _retentionDays == retentionInfinite
                        ? context.l10n.retentionInfinite
                        : context.l10n.retentionPeriodDescription(_retentionDays),
                    style: TextStyle(fontSize: 12, color: Colors.grey[600]),
                  ),
                  trailing: DropdownButton<int>(
                    value: _retentionDays,
                    items: [
                      DropdownMenuItem(value: 1, child: Text(context.l10n.retentionDays(1))),
                      DropdownMenuItem(value: 3, child: Text(context.l10n.retentionDays(3))),
                      DropdownMenuItem(value: 7, child: Text(context.l10n.retentionDays(7))),
                      DropdownMenuItem(value: retentionInfinite, child: Text(context.l10n.retentionInfiniteOption)),
                    ],
                    onChanged: (value) {
                      if (value != null) {
                        _saveRetentionDays(value);
                      }
                    },
                  ),
                ),
                const Divider(),
                Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Text(
                    context.l10n.appearance,
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.bold,
                      color: Colors.grey,
                    ),
                  ),
                ),
                ListTile(
                  title: Text(context.l10n.themeMode),
                  subtitle: Text(context.l10n.themeModeDescription),
                  trailing: DropdownButton<String>(
                    value: _themeMode,
                    items: [
                      DropdownMenuItem(
                        value: 'system',
                        child: Text(context.l10n.themeModeSystem),
                      ),
                      DropdownMenuItem(
                        value: 'light',
                        child: Text(context.l10n.themeModeLight),
                      ),
                      DropdownMenuItem(
                        value: 'dark',
                        child: Text(context.l10n.themeModeDark),
                      ),
                    ],
                    onChanged: (value) {
                      if (value != null) {
                        _saveThemeMode(value);
                      }
                    },
                  ),
                ),
                const Divider(),
                Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Text(
                    context.l10n.serverSettings,
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.bold,
                      color: Colors.grey,
                    ),
                  ),
                ),
                ListTile(
                  title: Text(context.l10n.serverSettings),
                  subtitle: Text(context.l10n.serverSettingsDescription),
                  onTap: () async {
                    final uri = Uri.parse('$pbBaseUrl/settings');
                    if (await canLaunchUrl(uri)) {
                      await launchUrl(uri, mode: LaunchMode.externalApplication);
                    }
                  },
                ),
                const Divider(),
                Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Text(
                    context.l10n.experimentalFeatures,
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.bold,
                      color: Colors.grey,
                    ),
                  ),
                ),
                SwitchListTile(
                  title: Text(context.l10n.answerbotFeature),
                  subtitle: Text(context.l10n.answerbotFeatureDescription),
                  value: _answerbotEnabled,
                  onChanged: (value) {
                    _saveAnswerbotEnabled(value);
                  },
                ),
                const Divider(),
                Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Text(
                    context.l10n.statistics,
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.bold,
                      color: Colors.grey,
                    ),
                  ),
                ),
                ListTile(
                  leading: const Icon(Icons.block, color: Colors.red),
                  title: Text(context.l10n.blockedCallsCount),
                  trailing: Text(
                    '$_blockedCallsCount',
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
                ListTile(
                  leading: const Icon(Icons.warning, color: Colors.orange),
                  title: Text(context.l10n.suspiciousCallsCount),
                  trailing: Text(
                    '$_suspiciousCallsCount',
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ],
            ),
    );
  }
}

/// About screen showing app information, credits, and links.
class AboutScreen extends StatelessWidget {
  const AboutScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(context.l10n.about),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16.0),
        children: [
          // App logo
          Center(
            child: Image.asset(
              'assets/images/spam_icon.png',
              width: 120,
              height: 120,
            ),
          ),
          const SizedBox(height: 16),
          // App title
          Center(
            child: Text(
              context.l10n.appTitle,
              style: const TextStyle(
                fontSize: 24,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
          const SizedBox(height: 8),
          // Version
          Center(
            child: Text(
              '${context.l10n.version} $appVersion',
              style: TextStyle(
                fontSize: 14,
                color: Colors.grey[600],
              ),
            ),
          ),
          const SizedBox(height: 32),
          // About description
          Text(
            context.l10n.aboutDescription,
            style: const TextStyle(fontSize: 16),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 32),
          // Developer
          ListTile(
            leading: const Icon(Icons.person),
            title: Text(context.l10n.developer),
            subtitle: Text(context.l10n.developerName),
          ),
          // Website
          ListTile(
            leading: const Icon(Icons.language),
            title: Text(context.l10n.website),
            subtitle: Text(context.l10n.websiteUrl),
            onTap: () async {
              final url = await buildPhoneBlockUrlWithToken('/');
              final uri = Uri.parse(url);
              if (await canLaunchUrl(uri)) {
                await launchUrl(uri, mode: LaunchMode.externalApplication);
              }
            },
          ),
          // Source code
          ListTile(
            leading: const Icon(Icons.code),
            title: Text(context.l10n.sourceCode),
            subtitle: Text(context.l10n.sourceCodeLicense),
            onTap: () async {
              final url = Uri.parse('https://github.com/haumacher/phoneblock');
              if (await canLaunchUrl(url)) {
                await launchUrl(url, mode: LaunchMode.externalApplication);
              }
            },
          ),
          const SizedBox(height: 32),
          // Donate button
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16.0),
            child: ElevatedButton.icon(
              onPressed: () async {
                String? token = await getAuthToken();
                if (token == null) {
                  return;
                }

                if (context.mounted) {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (context) => PhoneBlockWebView(
                        title: context.l10n.donate,
                        path: '/support',
                        authToken: token,
                      ),
                    ),
                  );
                }
              },
              icon: const Icon(Icons.favorite),
              label: Text(context.l10n.donate),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.orange,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 12.0),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Type of personalized number list.
enum PersonalizedListType {
  blacklist,
  whitelist,
}

/// Screen displaying the user's personalized number lists (blacklist or whitelist).
class PersonalizedNumberListScreen extends StatefulWidget {
  final String authToken;
  final PersonalizedListType listType;

  const PersonalizedNumberListScreen({
    super.key,
    required this.authToken,
    required this.listType,
  });

  @override
  State<PersonalizedNumberListScreen> createState() => _PersonalizedNumberListScreenState();
}

class _PersonalizedNumberListScreenState extends State<PersonalizedNumberListScreen> {
  List<api.PersonalizedNumber> _numbers = [];
  bool _isLoading = true;
  String? _errorMessage;

  bool get _isBlacklist => widget.listType == PersonalizedListType.blacklist;

  @override
  void initState() {
    super.initState();
    _loadNumbers();
  }

  /// Load the number list from the API.
  Future<void> _loadNumbers() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final numberList = _isBlacklist
          ? await fetchBlacklist(widget.authToken)
          : await fetchWhitelist(widget.authToken);

      if (numberList != null) {
        setState(() {
          _numbers = numberList.numbers;
          _isLoading = false;
        });
      } else {
        setState(() {
          _errorMessage = context.l10n.errorLoadingList;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (kDebugMode) {
        print('Error loading ${_isBlacklist ? 'blacklist' : 'whitelist'}: $e');
      }
      setState(() {
        _errorMessage = context.l10n.errorLoadingList;
        _isLoading = false;
      });
    }
  }

  /// Edit the comment for a number in the list.
  Future<void> _editComment(api.PersonalizedNumber personalizedNumber) async {
    final scaffold = ScaffoldMessenger.of(context);
    final localizations = context.l10n;
    final textController = TextEditingController(text: personalizedNumber.comment ?? '');

    final newComment = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(localizations.editComment),
        content: TextField(
          controller: textController,
          decoration: InputDecoration(
            labelText: localizations.commentLabel,
            hintText: localizations.commentHint,
          ),
          maxLines: 3,
          autofocus: true,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: Text(localizations.cancel),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(textController.text),
            child: Text(localizations.save),
          ),
        ],
      ),
    );

    if (newComment != null) {
      final success = _isBlacklist
          ? await updateBlacklistComment(personalizedNumber.phone, newComment, widget.authToken)
          : await updateWhitelistComment(personalizedNumber.phone, newComment, widget.authToken);

      if (success) {
        setState(() {
          // Update the comment in the local list
          personalizedNumber.comment = newComment;
        });
        scaffold.showSnackBar(
          SnackBar(
            content: Text(localizations.commentUpdated),
            duration: const Duration(seconds: 2),
          ),
        );
      } else {
        scaffold.showSnackBar(
          SnackBar(
            content: Text(localizations.errorUpdatingComment),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final title = _isBlacklist ? context.l10n.blacklistTitle : context.l10n.whitelistTitle;
    final emptyMessage = _isBlacklist ? context.l10n.blacklistEmpty : context.l10n.whitelistEmpty;
    final emptyHelpText = _isBlacklist ? context.l10n.blacklistEmptyHelp : context.l10n.whitelistEmptyHelp;
    final confirmRemoveMessage = _isBlacklist
        ? (String phone) => context.l10n.confirmRemoveFromBlacklist(phone)
        : (String phone) => context.l10n.confirmRemoveFromWhitelist(phone);
    final defaultIcon = _isBlacklist
        ? const Icon(Icons.block, color: Colors.red)
        : const Icon(Icons.check_circle, color: Colors.green);

    return Scaffold(
      appBar: AppBar(
        title: Text(title),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _errorMessage != null
              ? Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(Icons.error_outline, size: 48, color: Colors.red),
                      const SizedBox(height: 16),
                      Text(_errorMessage!),
                      const SizedBox(height: 16),
                      ElevatedButton(
                        onPressed: _loadNumbers,
                        child: Text(context.l10n.retry),
                      ),
                    ],
                  ),
                )
              : _numbers.isEmpty
                  ? Center(
                      child: Padding(
                        padding: const EdgeInsets.all(24.0),
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            const Icon(Icons.check_circle_outline, size: 48, color: Colors.green),
                            const SizedBox(height: 16),
                            Text(
                              emptyMessage,
                              style: const TextStyle(fontSize: 16),
                            ),
                            const SizedBox(height: 12),
                            Text(
                              emptyHelpText,
                              textAlign: TextAlign.center,
                              style: TextStyle(
                                fontSize: 14,
                                color: Colors.grey[600],
                              ),
                            ),
                          ],
                        ),
                      ),
                    )
                  : RefreshIndicator(
                      onRefresh: _loadNumbers,
                      child: ListView.builder(
                        itemCount: _numbers.length,
                        itemBuilder: (context, index) {
                          final personalizedNumber = _numbers[index];
                          final phone = personalizedNumber.phone;
                          // Use localized label for display, fallback to international format
                          final displayPhone = personalizedNumber.label ?? phone;
                          return Dismissible(
                            key: Key(phone),
                            direction: DismissDirection.endToStart,
                            background: Container(
                              color: Colors.red,
                              alignment: Alignment.centerRight,
                              padding: const EdgeInsets.only(right: 20),
                              child: Row(
                                mainAxisAlignment: MainAxisAlignment.end,
                                children: [
                                  Text(
                                    context.l10n.delete,
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontSize: 16,
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                  const SizedBox(width: 12),
                                  const Icon(Icons.delete, color: Colors.white, size: 32),
                                ],
                              ),
                            ),
                            confirmDismiss: (direction) async {
                              // Show confirmation dialog
                              final confirmed = await showDialog<bool>(
                                context: context,
                                builder: (context) => AlertDialog(
                                  title: Text(context.l10n.confirmRemoval),
                                  content: Text(confirmRemoveMessage(displayPhone)),
                                  actions: [
                                    TextButton(
                                      onPressed: () => Navigator.of(context).pop(false),
                                      child: Text(context.l10n.cancel),
                                    ),
                                    TextButton(
                                      onPressed: () => Navigator.of(context).pop(true),
                                      child: Text(context.l10n.remove),
                                    ),
                                  ],
                                ),
                              );

                              // If user cancelled, don't dismiss
                              if (confirmed != true) {
                                return false;
                              }

                              // User confirmed - attempt API call
                              final success = _isBlacklist
                                  ? await removeFromBlacklist(personalizedNumber.phone, widget.authToken)
                                  : await removeFromWhitelist(personalizedNumber.phone, widget.authToken);

                              // Show appropriate feedback
                              if (context.mounted) {
                                ScaffoldMessenger.of(context).showSnackBar(
                                  SnackBar(
                                    content: Text(
                                      success
                                          ? context.l10n.numberRemovedFromList
                                          : context.l10n.errorRemovingNumber,
                                    ),
                                    backgroundColor: success ? null : Colors.red,
                                    duration: const Duration(seconds: 2),
                                  ),
                                );
                              }

                              // Only dismiss if API call succeeded
                              return success;
                            },
                            onDismissed: (direction) {
                              // Update local state after successful dismissal
                              setState(() {
                                _numbers.remove(personalizedNumber);
                              });
                            },
                            child: ListTile(
                              leading: personalizedNumber.rating != null
                                  ? buildRatingAvatar(_convertApiRating(personalizedNumber.rating!))
                                  : defaultIcon,
                              title: Text(displayPhone),
                              subtitle: personalizedNumber.comment != null && personalizedNumber.comment!.isNotEmpty
                                  ? Text(
                                      personalizedNumber.comment!,
                                      style: TextStyle(
                                        color: Colors.grey[600],
                                        fontSize: 14,
                                      ),
                                    )
                                  : null,
                              trailing: IconButton(
                                icon: const Icon(Icons.edit_outlined),
                                onPressed: () => _editComment(personalizedNumber),
                              ),
                            ),
                          );
                        },
                      ),
                    ),
    );
  }
}

/// Convenience widget for blacklist screen.
class BlacklistScreen extends StatelessWidget {
  final String authToken;

  const BlacklistScreen({super.key, required this.authToken});

  @override
  Widget build(BuildContext context) {
    return PersonalizedNumberListScreen(
      authToken: authToken,
      listType: PersonalizedListType.blacklist,
    );
  }
}

/// Convenience widget for whitelist screen.
class WhitelistScreen extends StatelessWidget {
  final String authToken;

  const WhitelistScreen({super.key, required this.authToken});

  @override
  Widget build(BuildContext context) {
    return PersonalizedNumberListScreen(
      authToken: authToken,
      listType: PersonalizedListType.whitelist,
    );
  }
}
