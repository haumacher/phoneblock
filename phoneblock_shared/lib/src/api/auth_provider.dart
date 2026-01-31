/// Auth token provider function type.
/// Returns the auth token to use for API requests, or null if not authenticated.
typedef AuthTokenProvider = Future<String?> Function();

/// Global auth token provider that can be set by the app.
AuthTokenProvider? _globalAuthProvider;

/// Sets the global auth token provider.
void setAuthProvider(AuthTokenProvider provider) {
  _globalAuthProvider = provider;
}

/// Gets the current auth token using the configured provider.
Future<String?> getAuthToken() async {
  if (_globalAuthProvider == null) {
    return null;
  }
  return await _globalAuthProvider!();
}

/// Creates authorization headers for API requests.
Future<Map<String, String>> createAuthHeaders() async {
  final token = await getAuthToken();
  if (token == null || token.isEmpty) {
    return {};
  }

  return {
    'Authorization': 'Bearer $token',
  };
}
