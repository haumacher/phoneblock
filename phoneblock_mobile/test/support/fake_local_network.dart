import 'package:phoneblock_mobile/fritzbox/local_network_access.dart';

/// Test double of the Android local network permission (issue #660).
class FakeLocalNetworkAccess extends LocalNetworkAccess {
  /// Whether the access is currently granted.
  bool granted;

  /// What the system dialog answers.
  bool grantOnRequest;

  int checks = 0;
  int requests = 0;
  int settingsOpened = 0;

  FakeLocalNetworkAccess({this.granted = true, this.grantOnRequest = false});

  @override
  Future<bool> isGranted() async {
    checks++;
    return granted;
  }

  @override
  Future<bool> request() async {
    requests++;
    if (grantOnRequest) granted = true;
    return granted;
  }

  @override
  Future<void> openAppSettings() async {
    settingsOpened++;
  }
}
