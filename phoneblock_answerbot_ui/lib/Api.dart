import 'dart:io';

import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter/foundation.dart';

const debugging = true;

String get basePath => debugging ? "https://phoneblock.net/pb-test/" : "https://phoneblock.net/phoneblock/";

Future<String?> _createAuthHeader() async {
  final prefs = await SharedPreferences.getInstance();
  var authToken = prefs.getString("auth-token");

  if (authToken == null) {
    return null;
  }

  return 'Bearer $authToken';
}

Future<void> storeAuthToken(String authToken) async {
  final prefs = await SharedPreferences.getInstance();
  await prefs.setString("auth-token", authToken);

  if (kDebugMode) {
    debugPrint("Stored auth token: $authToken");
  }
}

Future<Map<String, String>> apiHeaders() async {
  if (kIsWeb) {
    // Authorization is done by authorization cookie.
    return {};
  }

  var authHeader = await _createAuthHeader();

  if (kDebugMode) {
    debugPrint("Creating auth header: $authHeader");
  }

  if (authHeader == null) {
    return {};
  }

  return {
    'Authorization': authHeader,
  };
}