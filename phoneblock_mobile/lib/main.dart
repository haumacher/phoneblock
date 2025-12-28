import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_contacts/flutter_contacts.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:phoneblock_mobile/state.dart';
import 'package:phoneblock_mobile/storage.dart';
import 'package:phoneblock_mobile/api.dart' as api;
import 'package:url_launcher/url_launcher.dart';
import 'package:http/http.dart' as http;

const String contextPath = kDebugMode ? "/pb-test" : "/phoneblock";
const String pbBaseUrl = 'https://phoneblock.net$contextPath';
const String pbLoginUrl = '$pbBaseUrl/mobile/login';
const String pbApiTest = '$pbBaseUrl/api/test';

const platform = MethodChannel('de.haumacher.phoneblock_mobile/call_checker');

/// Global stream controller to broadcast when new calls are screened.
/// This allows the UI to react to new screening results in real-time.
final callScreenedStreamController = StreamController<ScreenedCall>.broadcast();

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Set up listener for screening results from CallChecker
  platform.setMethodCallHandler((call) async {
    if (call.method == 'onCallScreened') {
      final args = call.arguments as Map;
      final phoneNumber = args['phoneNumber'] as String;
      final wasBlocked = args['wasBlocked'] as bool;
      final votes = args['votes'] as int;
      final timestamp = args['timestamp'] as int;

      // Store the screened call in database
      final screenedCall = ScreenedCall(
        phoneNumber: phoneNumber,
        timestamp: DateTime.fromMillisecondsSinceEpoch(timestamp),
        wasBlocked: wasBlocked,
        votes: votes,
      );

      await ScreenedCallsDatabase.instance.insertScreenedCall(screenedCall);

      // Notify any listeners (e.g., MainScreen) about the new call
      callScreenedStreamController.add(screenedCall);

      if (kDebugMode) {
        print('Screened call saved: $phoneNumber (blocked: $wasBlocked, votes: $votes)');
      }
    }
  });

  // Sync any stored screening results from SharedPreferences to database
  // This handles calls that were screened while the app was not running
  await syncStoredScreeningResults();

  runApp(MaterialApp.router(
      routerConfig: router,
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
        final screenedCall = ScreenedCall(
          phoneNumber: data['phoneNumber'] as String,
          timestamp: DateTime.fromMillisecondsSinceEpoch(data['timestamp'] as int),
          wasBlocked: data['wasBlocked'] as bool,
          votes: data['votes'] as int,
        );

        await ScreenedCallsDatabase.instance.insertScreenedCall(screenedCall);

        if (kDebugMode) {
          print('Synced stored call: ${screenedCall.phoneNumber} (blocked: ${screenedCall.wasBlocked})');
        }
      }

      // Clear the stored results from SharedPreferences after syncing
      await platform.invokeMethod('clearStoredScreeningResults');

      if (kDebugMode && storedCalls.isNotEmpty) {
        print('Synced ${storedCalls.length} stored screening results');
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
        const SnackBar(content: Text('Fehler beim Öffnen von PhoneBlock.')),
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
          const SnackBar(content: Text('Berechtigung wurde nicht erteilt.')),
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
    return Scaffold(
      appBar: AppBar(
        title: const Text('PhoneBlock Mobile - Einrichtung'),
      ),
      body: Stepper(
        currentStep: _currentStep.index,
        controlsBuilder: (context, details) {
          // Only show "Fertig" button on the final step when setup is complete
          if (_currentStep == SetupStep.complete && _hasAuthToken && _hasPermission) {
            return Padding(
              padding: const EdgeInsets.only(top: 16),
              child: ElevatedButton(
                onPressed: _finishSetup,
                child: const Text('Fertig'),
              ),
            );
          }
          // No buttons for other steps
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
            title: const Text('Willkommen'),
            subtitle: const Text('PhoneBlock-Konto verbinden'),
            isActive: true, // Welcome step is always available
            state: _hasAuthToken ? StepState.complete : StepState.indexed,
            content: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Willkommen bei PhoneBlock Mobile!\n\n'
                  'Diese App hilft Ihnen, Spam-Anrufe automatisch zu blockieren. '
                  'Dazu benötigen Sie ein kostenloses Konto bei PhoneBlock.net.\n\n'
                  'Verbinden Sie Ihr PhoneBlock-Konto, um fortzufahren:',
                  style: TextStyle(fontSize: 14),
                ),
                const SizedBox(height: 16),
                ElevatedButton.icon(
                  onPressed: _connectToPhoneBlock,
                  icon: Icon(_hasAuthToken ? Icons.check_circle : Icons.link),
                  label: Text(_hasAuthToken
                    ? 'Mit PhoneBlock verbunden'
                    : 'Mit PhoneBlock verbinden'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: _hasAuthToken ? Colors.green : null,
                  ),
                ),
                if (_hasAuthToken)
                  const Padding(
                    padding: EdgeInsets.only(top: 8),
                    child: Text(
                      '✓ Konto erfolgreich verbunden',
                      style: TextStyle(color: Colors.green, fontWeight: FontWeight.bold),
                    ),
                  ),
              ],
            ),
          ),
          Step(
            title: const Text('Berechtigungen'),
            subtitle: const Text('Anrufe filtern erlauben'),
            isActive: _hasAuthToken, // Active when auth is complete (prerequisite met)
            state: _hasPermission ? StepState.complete : StepState.indexed,
            content: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Um Spam-Anrufe automatisch zu blockieren, benötigt '
                  'PhoneBlock Mobile die Berechtigung, eingehende Anrufe zu prüfen.\n\n'
                  'Diese Berechtigung ist erforderlich, damit die App funktioniert:',
                  style: TextStyle(fontSize: 14),
                ),
                const SizedBox(height: 16),
                ElevatedButton.icon(
                  onPressed: _requestPermission,
                  icon: Icon(_hasPermission ? Icons.check_circle : Icons.security),
                  label: Text(_hasPermission
                    ? 'Berechtigung erteilt'
                    : 'Berechtigung erteilen'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: _hasPermission ? Colors.green : null,
                  ),
                ),
                if (_hasPermission)
                  const Padding(
                    padding: EdgeInsets.only(top: 8),
                    child: Text(
                      '✓ Berechtigung erfolgreich erteilt',
                      style: TextStyle(color: Colors.green, fontWeight: FontWeight.bold),
                    ),
                  ),
              ],
            ),
          ),
          Step(
            title: const Text('Fertig'),
            subtitle: const Text('Einrichtung abgeschlossen'),
            isActive: _hasAuthToken && _hasPermission, // Active when both prerequisites met
            state: (_hasAuthToken && _hasPermission) ? StepState.complete : StepState.indexed,
            content: const Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(Icons.check_circle_outline, size: 64, color: Colors.green),
                SizedBox(height: 16),
                Text(
                  'Einrichtung abgeschlossen!\n\n'
                  'PhoneBlock Mobile ist jetzt bereit, Spam-Anrufe zu blockieren. '
                  'Die App prüft automatisch eingehende Anrufe und blockiert bekannte '
                  'Spam-Nummern basierend auf der PhoneBlock-Datenbank.\n\n'
                  'Drücken Sie "Fertig", um zur Hauptansicht zu gelangen.',
                  style: TextStyle(fontSize: 14),
                ),
              ],
            ),
          ),
        ],
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
    return Scaffold(
      appBar: AppBar(
        title: const Text('PhoneBlock Mobile'),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _screenedCalls.isEmpty
              ? _buildEmptyState()
              : _buildCallsList(),
      floatingActionButton: _screenedCalls.isNotEmpty
          ? FloatingActionButton(
              onPressed: _deleteAllCalls,
              tooltip: 'Alle löschen',
              child: const Icon(Icons.delete_sweep),
            )
          : null,
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
              'Noch keine Anrufe gefiltert',
              style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                    color: Colors.grey[600],
                  ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 12),
            Text(
              'Eingehende Anrufe werden automatisch auf Spam geprüft und hier angezeigt.',
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
    final color = isSpam ? Colors.red : Colors.green;
    final icon = isSpam ? Icons.block : Icons.check_circle;
    final label = isSpam ? 'SPAM' : 'Legitim';

    return Dismissible(
      key: Key('call_${call.id}'),
      direction: DismissDirection.endToStart,
      background: Container(
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.only(right: 20),
        color: Colors.red,
        child: const Icon(
          Icons.delete,
          color: Colors.white,
          size: 32,
        ),
      ),
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
              backgroundColor: color.withOpacity(0.1),
              child: Icon(icon, color: color),
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
                  '${call.votes} ${call.votes == 1 ? "Meldung" : "Meldungen"}',
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
                color: color.withOpacity(0.1),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: color, width: 1.5),
              ),
              child: Text(
                label,
                style: TextStyle(
                  color: color,
                  fontWeight: FontWeight.bold,
                  fontSize: 12,
                ),
              ),
            ),
            onLongPress: () => _showCallOptions(context, call),
          ),
        ),
      ),
    );
  }

  /// Shows a context menu with options for a call.
  void _showCallOptions(BuildContext context, ScreenedCall call) {
    final RenderBox overlay = Overlay.of(context).context.findRenderObject() as RenderBox;

    showMenu(
      context: context,
      position: RelativeRect.fromRect(
        _lastTapPosition & const Size(40, 40),
        Offset.zero & overlay.size,
      ),
      items: [
        PopupMenuItem(
          child: Row(
            children: [
              Icon(Icons.report, color: Colors.orange),
              SizedBox(width: 12),
              Text('Als SPAM melden'),
            ],
          ),
          onTap: () {
            Future.delayed(Duration.zero, () => _reportAsSpam(context, call));
          },
        ),
        PopupMenuItem(
          child: Row(
            children: [
              Icon(Icons.delete, color: Colors.red),
              SizedBox(width: 12),
              Text('Löschen'),
            ],
          ),
          onTap: () {
            // Need to delay deletion slightly to allow menu to close
            Future.delayed(Duration.zero, () => _deleteCall(call));
          },
        ),
      ],
    );
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
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Nicht angemeldet. Bitte melden Sie sich an.'),
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

      // Call the rate API using the RateRequest's built-in JSON serialization
      final response = await http.post(
        Uri.parse('$pbBaseUrl/api/rate'),
        headers: {
          'Authorization': 'Bearer $token',
          'Content-Type': 'application/json',
        },
        body: rateRequest.toString(),
      );

      if (mounted) {
        if (response.statusCode == 200) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('${call.phoneNumber} als SPAM gemeldet'),
              backgroundColor: Colors.green,
            ),
          );
        } else {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Fehler beim Melden: ${response.statusCode}'),
              backgroundColor: Colors.red,
            ),
          );
        }
      }
    } catch (e) {
      if (kDebugMode) {
        print('Error reporting spam: $e');
      }

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Fehler beim Melden: $e'),
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
          title: const Text('SPAM-Kategorie wählen'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: Rating.values
                  .where((r) => r != Rating.aLEGITIMATE) // Exclude legitimate
                  .map((rating) => ListTile(
                        leading: icon(rating),
                        title: label(rating),
                        tileColor: bgColor(rating).withOpacity(0.1),
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
              child: const Text('Abbrechen'),
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
          const SnackBar(
            content: Text('Fehler beim Löschen aller Anrufe'),
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
          const SnackBar(
            content: Text('Fehler beim Löschen des Anrufs'),
            backgroundColor: Colors.red,
          ),
        );
      }
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
      return 'Heute, ${timeFormat.format(timestamp)}';
    } else if (callDate == yesterday) {
      return 'Gestern, ${timeFormat.format(timestamp)}';
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
        title: const Text('Überprüfe Login'),
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
                  return const Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.check_circle, color: Colors.green, size: 64),
                      SizedBox(height: 16),
                      Text("Login erfolgreich!", style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                      SizedBox(height: 8),
                      Text("Weiterleitung zur Einrichtung..."),
                    ],
                  );
                } else if (state.hasError) {
                  return Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(Icons.error, color: Colors.red, size: 64),
                      const SizedBox(height: 16),
                      Text("Token-Überprüfung fehlgeschlagen: ${state.error}"),
                      const SizedBox(height: 16),
                      ElevatedButton(
                        onPressed: () => context.go('/setup'),
                        child: const Text('Zurück zur Einrichtung'),
                      ),
                    ],
                  );
                } else {
                  return const Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      CircularProgressIndicator(),
                      SizedBox(height: 16),
                      Text("Token wird überprüft..."),
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
        title: const Text('Login fehlgeschlagen'),
      ),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Center(
            child: Text("No login token received."),
          ),
        ],
      ),
    );
  }
}

class TestPhoneBlockMobile extends StatelessWidget {
  const TestPhoneBlockMobile({super.key});

  @override
  Widget build(BuildContext context) {
    return SetupPage();
  }
}

class SetupPage extends StatefulWidget {
  const SetupPage({super.key});

  @override
  State<StatefulWidget> createState() {
    return SetupState();
  }
}

class SetupState extends State<SetupPage> {

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("PhoneBlock Mobile: Setup"),),
      body: Center(
        child: Column(
          children: [
            ElevatedButton(
                onPressed: () {
                  registerPhoneBlock(context);
                },
                child: const Text("Mit PhoneBlock verbinden")),
            ElevatedButton(
                onPressed: requestPermission,
                child: const Text("Berechtigung erteilen um Anrufe zu filtern")),
          ])
      ),
    );
  }

  void requestPermission() async {
    await platform.invokeMethod("requestPermission");
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
  if (!ok) {
    const snackBar = SnackBar(content: Text('Failed to open PhoneBlock.'));
    ScaffoldMessenger.of(context).showSnackBar(snackBar);
  }
}

class MyHomePage extends StatefulWidget {
  final AppState state;

  const MyHomePage(this.state, {super.key});

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  _MyHomePageState();

  @override
  Widget build(BuildContext context) {
    // This method is rerun every time setState is called, for instance as done
    // by the _incrementCounter method above.
    //
    // The Flutter framework has been optimized to make rerunning build methods
    // fast, so that you can just rebuild anything that needs updating rather
    // than having to individually change instances of widgets.
    return Scaffold(
      appBar: AppBar(
        title: const Text("PhoneBlock mobile"),
        actions: [
          PopupMenuButton(
            icon: const Icon(Icons.menu),
              itemBuilder: (context){
                return [
                  const PopupMenuItem<int>(
                    value: 0,
                    child: Text("Login"),
                  ),

                  const PopupMenuItem<int>(
                    value: 1,
                    child: Text("My Account"),
                  ),

                  const PopupMenuItem<int>(
                    value: 2,
                    child: Text("Settings"),
                  ),

                  const PopupMenuItem<int>(
                    value: 3,
                    child: Text("Logout"),
                  ),
                ];
              },
              onSelected:(value){
                if (value == 0) {
                  if (kDebugMode) {
                    print("Login is selected.");
                  }

                  registerPhoneBlock(context);
                } else if (value == 1){
                  if (kDebugMode) {
                    print("My account menu is selected.");
                  }
                }else if(value == 2){
                  if (kDebugMode) {
                    print("Settings menu is selected.");
                  }
                }else if(value == 3){
                  if (kDebugMode) {
                    print("Logout menu is selected.");
                  }
                }
              }
          ),
      ],
      ),
      body: ListView(
        children: widget.state.calls.map((call) =>
          Dismissible(key: Key(call.phone), child:
            ListTile(
              leading: icon(call.type),
              title : Row(mainAxisSize: MainAxisSize.min, children: [
                hint(call.rating),
                Text(call.label ?? call.phone)
              ]),
              subtitle: duration(call),
              trailing: action(call),
            ),
            onDismissed: (direction) => {debugPrint("Dismissed: ${call.phone}") },
          )
        ).toList(),
      ),
      floatingActionButton: Row(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          FloatingActionButton(
            onPressed: fetchBlocklist,
            tooltip: 'Update Blocklist',
            child: const Icon(Icons.cloud_download),
          ),
        ],
      ) // This trailing comma makes auto-formatting nicer for build methods.
    );
  }

  Widget duration(Call call) {
    if (call.started == 0) {
      return const Text("");
    }
    var date = DateTime.fromMillisecondsSinceEpoch(call.started);

    DateFormat format = createFormat(date);
    return Text(format.format(date));
  }

  DateFormat createFormat(DateTime date) {
    var now = DateTime.now();
    var today = DateTime(now.year, now.month, now.day);
    var yesterday = today.subtract(const Duration(days: 1));
    var thisYear = DateTime(today.year);
    
    DateFormat format;
    if (date.isBefore(thisYear)) {
      format = DateFormat('hh:mm dd.MM.yyyy');
    } else if (date.isBefore(yesterday)) {
      format = DateFormat('hh:mm dd.MM.');
    } else if (date.isBefore(today)) {
      format = DateFormat('hh:mm gestern');
    } else {
      format = DateFormat('hh:mm heute');
    }
    return format;
  }

  Widget action(Call call) {
    switch (call.type) {
      case Type.iNCOMING:
        return IconButton(icon: const Icon(Icons.block, color: Colors.redAccent,),
            onPressed: () => Navigator.push(context,
              MaterialPageRoute(builder: (context) => RateScreen(call)),
            )
        );
      case Type.mISSED:
        return IconButton(
            icon: const Icon(Icons.manage_search, color: Colors.blueAccent,),
            onPressed: () {
              if (kDebugMode) {
                print('Report as spam: ${call.phone}');
              }
            });
      case Type.bLOCKED:
        return IconButton(
            icon: const Icon(Icons.playlist_add, color: Colors.redAccent,),
            onPressed: () {
              if (kDebugMode) {
                print('Record call: ${call.phone}');
              }
            });
      case Type.oUTGOING:
        return const SizedBox.shrink();
    }
  }

  Widget hint(Rating rating) {
    if (rating == Rating.uNKNOWN || rating == Rating.aLEGITIMATE) {
      return const SizedBox.shrink();
    }
    return Padding(padding: const EdgeInsets.only(right: 5),
        child: Container(
          decoration: BoxDecoration(
              color: bgColor(rating),
              borderRadius: const BorderRadius.all(Radius.circular(10))
          ),
          child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
              child: label(rating)),
        ));
    }

  Icon icon(Type type) {
    switch (type) {
      case Type.bLOCKED: return const Icon(Icons.phone_disabled, color: Colors.redAccent);
      case Type.oUTGOING: return const Icon(Icons.phone_forwarded, color: Colors.green);
      case Type.mISSED: return const Icon(Icons.phone_missed, color: Colors.blueAccent);
      case Type.iNCOMING: return const Icon(Icons.phone_callback, color: Colors.green);
    }
  }

  void fetchBlocklist() async {
    if (await FlutterContacts.requestPermission()) {
      if (kDebugMode) {
        print("Permission OK");
      }
      var allGroups = await FlutterContacts.getGroups();
      if (kDebugMode) {
        print("Groups: $allGroups");
      }

      List<Group> spamGroups = [];
      for (var group in allGroups) {
        if (group.name == "SPAM") {
          spamGroups.add(group);
        }
      }

      if (kDebugMode) {
        print("SPAM groups: $spamGroups");
      }

      var allContacts = await FlutterContacts.getContacts(withGroups: true);
      var spamContacts = allContacts.where((contact) => containsAny(spamGroups, contact.groups));
      for (var contact in spamContacts) {
        if (kDebugMode) {
          for (var num in contact.phones) {
            print("Found SPAM number: $num");
          }
        }
        await contact.delete();
      }

      while (spamGroups.length > 1) {
        Group duplicate = spamGroups.removeLast();
        await FlutterContacts.deleteGroup(duplicate);
      }

      Group spamGroup = spamGroups.firstOrNull ?? await createSpamGroup();

      var photo = Uint8List.sublistView(await rootBundle.load("assets/images/spam_icon.png"));

      var pb = Contact(
        name: Name(last: "ZZ SPAM"),
        phones: [
          Phone("012345679", label: PhoneLabel.work),
          Phone("0234567891", label: PhoneLabel.work),
        ],
        groups: [spamGroup],
        photo: photo,
      );
      pb = await pb.insert();

      if (kDebugMode) {
        print("Created contact $pb");
      }

      if (kDebugMode) {
        print("Update OK");
      }
    } else {
      if (kDebugMode) {
        print("Permission denied");
      }
    }
  }

  /// Whether [all] contains any element of [some].
  bool containsAny(List all, List some) {
    for (Object x in some) {
      if (all.contains(x)) {
        return true;
      }
    }
    return false;
  }

  Future<Group> createSpamGroup() async {
    Group spamGroup = Group("phoneblock-spam", "SPAM");
    spamGroup = await FlutterContacts.insertGroup(spamGroup);
    if (kDebugMode) {
      print("Created SPAM group: $spamGroup");
    }
    return spamGroup;
  }

}

Widget label(Rating rating) {
  switch (rating) {
    case Rating.aLEGITIMATE: return const Text("Legitim", style: TextStyle(color: Colors.white));
    case Rating.aDVERTISING: return const Text("Werbung", style: TextStyle(color: Color.fromRGBO(0,0,0,.7)));
    case Rating.uNKNOWN: return const Text("Anderer Grund", style: TextStyle(color: Colors.white));
    case Rating.pING: return const Text("Ping-Anruf", style: TextStyle(color: Colors.white));
    case Rating.gAMBLE: return const Text("Gewinnspiel", style: TextStyle(color: Colors.white));
    case Rating.fRAUD: return const Text("Betrug", style: TextStyle(color: Colors.white));
    case Rating.pOLL: return const Text("Umfrage", style: TextStyle(color: Colors.white));
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
        title: Text('Rate ${call.phone}'),
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
          label(rating)
        ]),
      ),
    );
  }

}