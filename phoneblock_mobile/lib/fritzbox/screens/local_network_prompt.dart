import 'package:flutter/material.dart';
import 'package:phoneblock_mobile/fritzbox/local_network_access.dart';
import 'package:phoneblock_mobile/l10n/app_localizations.dart';

/// Asks for access to the local network (Android 17, issue #660).
///
/// First explains why the access is needed and offers a button that opens
/// the system dialog. If the user refuses, it names the reason and offers
/// the app's system settings instead. When the access is granted - by the
/// dialog or in the system settings, noticed when the app returns to the
/// foreground - [onGranted] is called once.
class LocalNetworkAccessPrompt extends StatefulWidget {
  /// Called once the access is granted.
  final VoidCallback onGranted;

  const LocalNetworkAccessPrompt({super.key, required this.onGranted});

  @override
  State<LocalNetworkAccessPrompt> createState() =>
      _LocalNetworkAccessPromptState();
}

class _LocalNetworkAccessPromptState extends State<LocalNetworkAccessPrompt>
    with WidgetsBindingObserver {
  bool _denied = false;
  bool _requesting = false;
  bool _granted = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // The system dialog pauses the app, too; its result is handled by
    // _request().
    if (state == AppLifecycleState.resumed && !_requesting) {
      _recheck();
    }
  }

  Future<void> _recheck() async {
    if (await LocalNetworkAccess.instance.isGranted()) _notifyGranted();
  }

  Future<void> _request() async {
    setState(() => _requesting = true);
    final granted = await LocalNetworkAccess.instance.request();
    if (!mounted) return;
    setState(() {
      _requesting = false;
      _denied = !granted;
    });
    if (granted) _notifyGranted();
  }

  void _notifyGranted() {
    if (_granted || !mounted) return;
    _granted = true;
    widget.onGranted();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final theme = Theme.of(context);

    return Card(
      margin: const EdgeInsets.symmetric(vertical: 8),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.lan,
                    color: _denied ? Colors.orange : theme.colorScheme.primary),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    _denied ? l10n.localNetworkDenied : l10n.localNetworkRationale,
                    style: _denied
                        ? theme.textTheme.titleSmall?.copyWith(color: Colors.orange[800])
                        : null,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              child: _denied
                  ? ElevatedButton.icon(
                      onPressed: LocalNetworkAccess.instance.openAppSettings,
                      icon: const Icon(Icons.settings),
                      label: Text(l10n.localNetworkOpenSettings),
                    )
                  : ElevatedButton(
                      onPressed: _requesting ? null : _request,
                      child: Text(l10n.localNetworkAllow),
                    ),
            ),
          ],
        ),
      ),
    );
  }
}
