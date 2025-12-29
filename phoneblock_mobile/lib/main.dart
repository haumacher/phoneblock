import 'dart:async';

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
import 'l10n/app_localizations.dart';
import 'l10n/l10n_extensions.dart';

const String contextPath = kDebugMode ? "/pb-test" : "/phoneblock";
const String pbBaseUrl = 'https://phoneblock.net$contextPath';
const String pbLoginUrl = '$pbBaseUrl/mobile/login';
const String pbApiTest = '$pbBaseUrl/api/test';

/// Retention period constant for infinite retention (keep all calls)
const int retentionInfinite = -1;

/// Default retention period in days (3 days for privacy)
const int retentionDefault = 3;

const platform = MethodChannel('de.haumacher.phoneblock_mobile/call_checker');

/// Global stream controller to broadcast when new calls are screened.
/// This allows the UI to react to new screening results in real-time.
final callScreenedStreamController = StreamController<ScreenedCall>.broadcast();

/// Global app version string, initialized at startup from package info.
late String appVersion;

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

/// Gets the configured retention period in days.
/// Returns the retention period from SharedPreferences, or the default value if not set.
Future<int> getRetentionDays() async {
  return await platform.invokeMethod<int>("getRetentionDays") ?? retentionDefault;
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
      final timestamp = args['timestamp'] as int;
      final ratingStr = args['rating'] as String?;

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
        rating: rating,
      );

      await ScreenedCallsDatabase.instance.insertScreenedCall(screenedCall);

      // Clean up old calls based on retention period when a new call arrives
      final retentionDays = await getRetentionDays();
      await ScreenedCallsDatabase.instance.deleteOldScreenedCalls(retentionDays);

      // Notify any listeners (e.g., MainScreen) about the new call
      callScreenedStreamController.add(screenedCall);

      if (kDebugMode) {
        print('Screened call saved: $phoneNumber (blocked: $wasBlocked, votes: $votes, rating: $ratingStr)');
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
  final retentionDays = await getRetentionDays();
  await ScreenedCallsDatabase.instance.deleteOldScreenedCalls(retentionDays);

  runApp(MaterialApp.router(
      routerConfig: router,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: const [
        Locale('de'),
        Locale('en'),
      ],
      theme: ThemeData(
        appBarTheme: AppBarTheme(
          backgroundColor: const Color.fromARGB(255, 0, 209, 178),
          foregroundColor: Colors.white,
        ),
      ),
  ));
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
          rating: rating,
        );

        await ScreenedCallsDatabase.instance.insertScreenedCall(screenedCall);

        if (kDebugMode) {
          print('Synced stored call: ${screenedCall.phoneNumber} (blocked: ${screenedCall.wasBlocked}, rating: $ratingStr)');
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
    bool ok = await launchUrl(Uri.parse(pbLoginUrl));
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
  StreamSubscription<ScreenedCall>? _callScreenedSubscription;
  Offset _lastTapPosition = Offset.zero;

  @override
  void initState() {
    super.initState();
    _loadScreenedCalls();
    _setupCallScreeningListener();
  }

  @override
  void dispose() {
    _callScreenedSubscription?.cancel();
    super.dispose();
  }

  /// Loads screened calls from the database.
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
          automaticallyImplyLeading: false,
          actions: [
            IconButton(
              icon: const Icon(Icons.settings),
              onPressed: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (context) => const SettingsScreen()),
                );
              },
            ),
          ],
        ),
        body: _isLoading
            ? const Center(child: CircularProgressIndicator())
            : _screenedCalls.isEmpty
                ? _buildEmptyState()
                : _buildCallsList(),
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

  /// Builds the list of screened calls.
  Widget _buildCallsList() {
    return ListView.builder(
      itemCount: _screenedCalls.length,
      itemBuilder: (context, index) {
        final call = _screenedCalls[index];
        return _buildCallListItem(call);
      },
    );
  }

  /// Builds a single call list item.
  Widget _buildCallListItem(ScreenedCall call) {
    final isSpam = call.wasBlocked;

    // Use rating-specific color and icon if available, otherwise default colors
    Color color;
    IconData iconData;
    String labelText;

    if (call.rating != null && call.rating != Rating.uNKNOWN && isSpam) {
      // Use rating-specific styling
      color = bgColor(call.rating!);
      iconData = icon(call.rating!).icon!;
      labelText = (label(context, call.rating!) as Text).data!;
    } else {
      // Default styling (also used for Rating.uNKNOWN)
      color = isSpam ? Colors.red : Colors.green;
      iconData = isSpam ? Icons.block : Icons.check_circle;
      labelText = isSpam ? context.l10n.ratingSpam : context.l10n.ratingLegitimate;
    }

    return Dismissible(
      key: Key('call_${call.id}'),
      background: Container(
        alignment: Alignment.centerLeft,
        padding: const EdgeInsets.only(left: 20),
        color: isSpam ? Colors.green : Colors.orange,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.start,
          children: [
            Icon(
              isSpam ? Icons.check_circle : Icons.report,
              color: Colors.white,
              size: 32,
            ),
            const SizedBox(width: 12),
            Text(
              isSpam ? context.l10n.reportAsLegitimate : context.l10n.reportAsSpam,
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
        color: Colors.red,
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
            SizedBox(width: 12),
            Icon(
              Icons.delete,
              color: Colors.white,
              size: 32,
            ),
          ],
        ),
      ),
      confirmDismiss: (direction) async {
        if (direction == DismissDirection.startToEnd) {
          // Swipe right
          if (isSpam) {
            // SPAM number - report as legitimate
            await _reportAsLegitimate(context, call);
          } else {
            // Legitimate number - report as SPAM
            await _reportAsSpam(context, call);
          }
          return false; // Don't dismiss, just report
        } else {
          // Swipe left to delete
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
            leading: CircleAvatar(
              backgroundColor: color.withValues(alpha: 0.1),
              child: Icon(iconData, color: color),
            ),
            title: Text(
              call.phoneNumber,
              style: const TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 16,
              ),
            ),
            subtitle: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const SizedBox(height: 4),
                Text(
                  _formatTimestamp(call.timestamp),
                  style: TextStyle(
                    fontSize: 12,
                    color: Colors.grey[600],
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  context.l10n.reportsCount(call.votes),
                  style: TextStyle(
                    fontSize: 12,
                    color: Colors.grey[600],
                  ),
                ),
              ],
            ),
            trailing: Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: color, width: 1.5),
              ),
              child: Text(
                labelText,
                style: TextStyle(
                  color: color,
                  fontWeight: FontWeight.bold,
                  fontSize: 12,
                ),
              ),
            ),
            onTap: () => _viewOnPhoneBlock(call),
            onLongPress: () => _showCallOptions(context, call),
          ),
        ),
      ),
    );
  }

  /// Shows a context menu with options for a call.
  void _showCallOptions(BuildContext context, ScreenedCall call) {
    final RenderBox overlay = Overlay.of(context).context.findRenderObject() as RenderBox;
    final isSpam = call.wasBlocked;

    final items = <PopupMenuEntry<dynamic>>[];

    // Show appropriate reporting option based on current status
    if (isSpam) {
      // SPAM call - offer to mark as legitimate
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
    } else {
      // Legitimate call - offer to report as SPAM
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
    }

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
    // Show confirmation dialog
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: Text(context.l10n.confirmReportLegitimate),
          content: Text(
            context.l10n.confirmReportLegitimateMessage(call.phoneNumber),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(false),
              child: Text(context.l10n.cancel),
            ),
            TextButton(
              onPressed: () => Navigator.of(context).pop(true),
              style: TextButton.styleFrom(foregroundColor: Colors.green),
              child: Text(context.l10n.report),
            ),
          ],
        );
      },
    );

    // User cancelled
    if (confirmed != true) {
      return;
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
        comment: '',
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
        comment: '', // No comment for now
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
            phoneNumber: call.phoneNumber,
            authToken: token,
          ),
        ),
      );
    }
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

    checkResult = http.get(Uri.parse(pbApiTest),
      headers: {
        "Authorization": "Bearer $token"
      }
    );
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

    final response = await http.get(
      Uri.parse(pbApiTest),
      headers: {"Authorization": "Bearer $token"},
    );

    return response.statusCode == 200;
  } catch (e) {
    if (kDebugMode) {
      print("Error validating auth token: $e");
    }
    return false;
  }
}

void registerPhoneBlock(BuildContext context) async {
  bool ok = await launchUrl(Uri.parse(pbLoginUrl));
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
    case Rating.uNKNOWN: return const Color.fromRGBO(170, 172, 170, 1);
    case Rating.pING: return const Color.fromRGBO(31, 94, 220, 1);
    case Rating.pOLL: return const Color.fromRGBO(157, 31, 220, 1);
    case Rating.aDVERTISING: return const Color.fromRGBO(255, 224, 138, 1);
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

/// WebView screen for displaying PhoneBlock phone number details.
class PhoneBlockWebView extends StatefulWidget {
  final String phoneNumber;
  final String authToken;

  const PhoneBlockWebView({
    super.key,
    required this.phoneNumber,
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
          onPageStarted: (String url) {
            setState(() {
              _isLoading = true;
            });
          },
          onPageFinished: (String url) {
            setState(() {
              _isLoading = false;
            });
          },
        ),
      )
      ..loadRequest(
        Uri.parse('$pbBaseUrl/nums/${widget.phoneNumber}'),
        headers: {
          'Authorization': 'Bearer ${widget.authToken}',
        },
      );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.phoneNumber),
      ),
      body: Stack(
        children: [
          WebViewWidget(controller: _controller),
          if (_isLoading)
            const Center(
              child: CircularProgressIndicator(),
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
  bool _isLoading = true;

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

      if (kDebugMode) {
        print("Loaded settings - minVotes: $minVotesResult, blockRanges: $blockRangesResult, minRangeVotes: $minRangeVotesResult, retentionDays: $retentionDaysResult");
      }

      setState(() {
        _minVotes = minVotesResult ?? 4;
        _blockRanges = blockRangesResult ?? true;
        _minRangeVotes = minRangeVotesResult ?? 10;
        _retentionDays = retentionDaysResult ?? retentionDefault;
        _isLoading = false;
      });

      if (kDebugMode) {
        print("Set state - minVotes: $_minVotes, blockRanges: $_blockRanges, minRangeVotes: $_minRangeVotes");
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
                  padding: const EdgeInsets.fromLTRB(16.0, 16.0, 16.0, 4.0),
                  child: Text(
                    context.l10n.about,
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.bold,
                      color: Colors.grey,
                    ),
                  ),
                ),
                ListTile(
                  dense: true,
                  visualDensity: VisualDensity.compact,
                  title: Text(context.l10n.version),
                  subtitle: Text(appVersion),
                ),
                ListTile(
                  dense: true,
                  visualDensity: VisualDensity.compact,
                  title: Text(context.l10n.developer),
                  subtitle: Text(context.l10n.developerName),
                ),
                ListTile(
                  dense: true,
                  visualDensity: VisualDensity.compact,
                  title: Text(context.l10n.website),
                  subtitle: Text(context.l10n.websiteUrl),
                  onTap: () async {
                    final url = Uri.parse('https://phoneblock.net');
                    if (await canLaunchUrl(url)) {
                      await launchUrl(url, mode: LaunchMode.externalApplication);
                    }
                  },
                ),
                ListTile(
                  dense: true,
                  visualDensity: VisualDensity.compact,
                  title: Text(context.l10n.sourceCode),
                  subtitle: Text(context.l10n.sourceCodeLicense),
                  onTap: () async {
                    final url = Uri.parse('https://github.com/haumacher/phoneblock');
                    if (await canLaunchUrl(url)) {
                      await launchUrl(url, mode: LaunchMode.externalApplication);
                    }
                  },
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
                  child: Text(
                    context.l10n.aboutDescription,
                    style: const TextStyle(
                      fontSize: 12,
                      color: Colors.grey,
                      fontStyle: FontStyle.italic,
                    ),
                    textAlign: TextAlign.center,
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
                  child: ElevatedButton.icon(
                    onPressed: () async {
                      final url = Uri.parse('https://phoneblock.net/phoneblock/support');
                      if (await canLaunchUrl(url)) {
                        await launchUrl(url, mode: LaunchMode.externalApplication);
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
