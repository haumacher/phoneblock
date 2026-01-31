import 'package:flutter/foundation.dart';
import 'auth_provider.dart';

/// Gets API headers including authorization if available.
Future<Map<String, String>> apiHeaders() async {
  if (kIsWeb) {
    // Authorization is done by authorization cookie.
    return {};
  }

  return await createAuthHeaders();
}