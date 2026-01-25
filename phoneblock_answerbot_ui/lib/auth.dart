import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Gets the stored auth token from SharedPreferences.
Future<String?> getStoredAuthToken() async {
  final prefs = await SharedPreferences.getInstance();
  return prefs.getString("auth-token");
}

/// Stores the auth token in SharedPreferences.
Future<void> storeAuthToken(String authToken) async {
  final prefs = await SharedPreferences.getInstance();
  await prefs.setString("auth-token", authToken);

  if (kDebugMode) {
    debugPrint("Stored auth token: $authToken");
  }
}
