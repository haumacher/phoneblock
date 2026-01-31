import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:phoneblock_shared/phoneblock_shared.dart';
import 'package:url_launcher/url_launcher.dart';
import 'auth.dart';
import 'LoginScreen.dart';

/// Home screen for the standalone answerbot app with login handling.
class AnswerbotHome extends StatelessWidget {
  const AnswerbotHome({super.key});

  @override
  Widget build(BuildContext context) {
    return AnswerBotList(
      onLoginRequired: _handleLogin,
    );
  }

  /// Handles login for the answerbot app.
  Future<bool> _handleLogin(BuildContext context) async {
    if (kIsWeb) {
      // Web version redirects to PhoneBlock login page
      const basePath = String.fromEnvironment('BASE_PATH', defaultValue: 'https://phoneblock.net/phoneblock');
      launchUrl(Uri.parse("$basePath/login?locationAfterLogin=/ab/"), webOnlyWindowName: "_self");
      return false; // Can't determine success for web redirect
    } else {
      // Native app shows LoginScreen and stores token
      try {
        String? authToken = await Navigator.push(
          context,
          MaterialPageRoute(builder: (context) => const LoginScreen()),
        );

        if (authToken != null && authToken.isNotEmpty) {
          await storeAuthToken(authToken);
          return true;
        }
        return false;
      } catch (e) {
        if (kDebugMode) {
          debugPrint("Login error: $e");
        }
        return false;
      }
    }
  }
}
