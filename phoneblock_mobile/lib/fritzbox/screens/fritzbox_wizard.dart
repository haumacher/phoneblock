import 'package:flutter/material.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_discovery.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_models.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_service.dart';
import 'package:phoneblock_mobile/l10n/app_localizations.dart';
import 'package:phoneblock_mobile/main.dart' show newCallIds;

/// Wizard steps for Fritz!Box connection.
enum _WizardStep {
  /// Detecting Fritz!Box on the network.
  detection,

  /// Entering login credentials.
  login,
}

/// Connection wizard for Fritz!Box integration.
class FritzBoxWizard extends StatefulWidget {
  const FritzBoxWizard({super.key});

  @override
  State<FritzBoxWizard> createState() => _FritzBoxWizardState();
}

class _FritzBoxWizardState extends State<FritzBoxWizard> {
  _WizardStep _currentStep = _WizardStep.detection;
  bool _isSearching = true;
  bool _isConnecting = false;
  FritzBoxDeviceInfo? _deviceInfo;
  String? _errorMessage;

  final _hostController = TextEditingController();
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _showPassword = false;

  @override
  void initState() {
    super.initState();
    _startDiscovery();
  }

  @override
  void dispose() {
    _hostController.dispose();
    _usernameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _startDiscovery() async {
    setState(() {
      _isSearching = true;
      _errorMessage = null;
      _deviceInfo = null;
    });

    final discovery = FritzBoxDiscovery();
    final deviceInfo = await discovery.discover();

    if (mounted) {
      setState(() {
        _isSearching = false;
        _deviceInfo = deviceInfo;
        if (deviceInfo != null) {
          _hostController.text = deviceInfo.host;
        }
      });
    }
  }

  Future<void> _tryHost(String host) async {
    setState(() {
      _isSearching = true;
      _errorMessage = null;
    });

    final discovery = FritzBoxDiscovery();
    final deviceInfo = await discovery.tryHost(host);

    if (mounted) {
      setState(() {
        _isSearching = false;
        _deviceInfo = deviceInfo;
        if (deviceInfo != null) {
          _hostController.text = deviceInfo.host;
        } else {
          _errorMessage = AppLocalizations.of(context)!.fritzboxNotFound;
        }
      });
    }
  }

  void _proceedToLogin() {
    setState(() {
      _currentStep = _WizardStep.login;
    });
  }

  Future<void> _connect() async {
    final host = _hostController.text.trim();
    final username = _usernameController.text.trim();
    final password = _passwordController.text;

    if (host.isEmpty || password.isEmpty) {
      setState(() {
        _errorMessage = AppLocalizations.of(context)!.fritzboxFillAllFields;
      });
      return;
    }

    setState(() {
      _isConnecting = true;
      _errorMessage = null;
    });

    final success = await FritzBoxService.instance.connect(
      host: host,
      username: username,
      password: password,
    );

    if (mounted) {
      if (success) {
        // Perform initial sync
        final newIds = await FritzBoxService.instance.syncCallList();
        // Track synced calls as new
        newCallIds.addAll(newIds);

        if (mounted) {
          Navigator.pop(context, true);
        }
      } else {
        setState(() {
          _isConnecting = false;
          _errorMessage = AppLocalizations.of(context)!.fritzboxConnectionFailed;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.fritzboxWizardTitle),
      ),
      body: Stepper(
        currentStep: _currentStep.index,
        controlsBuilder: (context, details) {
          // Custom controls
          return const SizedBox.shrink();
        },
        onStepTapped: (step) {
          // Only allow going back to detection step
          if (step == 0 && _currentStep == _WizardStep.login) {
            setState(() {
              _currentStep = _WizardStep.detection;
              _errorMessage = null;
            });
          }
        },
        steps: [
          Step(
            title: Text(l10n.fritzboxStepDetection),
            subtitle: Text(l10n.fritzboxStepDetectionSubtitle),
            isActive: true,
            state: _currentStep == _WizardStep.detection
                ? StepState.indexed
                : StepState.complete,
            content: _buildDetectionStep(context, l10n),
          ),
          Step(
            title: Text(l10n.fritzboxStepLogin),
            subtitle: Text(l10n.fritzboxStepLoginSubtitle),
            isActive: _currentStep == _WizardStep.login,
            state: StepState.indexed,
            content: _buildLoginStep(context, l10n),
          ),
        ],
      ),
    );
  }

  Widget _buildDetectionStep(BuildContext context, AppLocalizations l10n) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (_isSearching) ...[
          const Center(
            child: Padding(
              padding: EdgeInsets.all(32),
              child: Column(
                children: [
                  CircularProgressIndicator(),
                  SizedBox(height: 16),
                ],
              ),
            ),
          ),
          Text(
            l10n.fritzboxSearching,
            textAlign: TextAlign.center,
            style: const TextStyle(color: Colors.grey),
          ),
        ] else if (_deviceInfo != null) ...[
          Card(
            child: ListTile(
              leading: const Icon(Icons.router, size: 40, color: Colors.green),
              title: Text(_deviceInfo!.modelName ?? l10n.fritzboxTitle),
              subtitle: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(_deviceInfo!.host),
                  if (_deviceInfo!.fritzosVersion != null)
                    Text('FRITZ!OS ${_deviceInfo!.fritzosVersion}'),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: ElevatedButton(
                  onPressed: _proceedToLogin,
                  child: Text(l10n.continue_),
                ),
              ),
            ],
          ),
        ] else ...[
          Text(
            l10n.fritzboxNotFoundDescription,
            style: const TextStyle(color: Colors.grey),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _hostController,
            decoration: InputDecoration(
              labelText: l10n.fritzboxHostLabel,
              hintText: 'fritz.box',
              prefixIcon: const Icon(Icons.router),
              border: const OutlineInputBorder(),
            ),
            keyboardType: TextInputType.url,
            textInputAction: TextInputAction.done,
            onSubmitted: (_) => _tryHost(_hostController.text.trim()),
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: _startDiscovery,
                  child: Text(l10n.fritzboxRetrySearch),
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: ElevatedButton(
                  onPressed: () {
                    final host = _hostController.text.trim();
                    if (host.isNotEmpty) {
                      _tryHost(host);
                    }
                  },
                  child: Text(l10n.fritzboxManualConnect),
                ),
              ),
            ],
          ),
        ],
        if (_errorMessage != null) ...[
          const SizedBox(height: 16),
          Text(
            _errorMessage!,
            style: const TextStyle(color: Colors.red),
          ),
        ],
      ],
    );
  }

  Widget _buildLoginStep(BuildContext context, AppLocalizations l10n) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          l10n.fritzboxLoginDescription,
          style: const TextStyle(color: Colors.grey),
        ),
        const SizedBox(height: 16),

        // Host field (read-only)
        TextField(
          controller: _hostController,
          decoration: InputDecoration(
            labelText: l10n.fritzboxHostLabel,
            prefixIcon: const Icon(Icons.router),
            border: const OutlineInputBorder(),
          ),
          enabled: false,
        ),
        const SizedBox(height: 16),

        // Username field
        TextField(
          controller: _usernameController,
          decoration: InputDecoration(
            labelText: l10n.fritzboxUsernameLabel,
            hintText: l10n.fritzboxUsernameHint,
            prefixIcon: const Icon(Icons.person),
            border: const OutlineInputBorder(),
          ),
          textInputAction: TextInputAction.next,
          autocorrect: false,
        ),
        const SizedBox(height: 16),

        // Password field
        TextField(
          controller: _passwordController,
          decoration: InputDecoration(
            labelText: l10n.fritzboxPasswordLabel,
            prefixIcon: const Icon(Icons.lock),
            border: const OutlineInputBorder(),
            suffixIcon: IconButton(
              icon: Icon(_showPassword ? Icons.visibility_off : Icons.visibility),
              onPressed: () {
                setState(() {
                  _showPassword = !_showPassword;
                });
              },
            ),
          ),
          obscureText: !_showPassword,
          textInputAction: TextInputAction.done,
          onSubmitted: (_) => _connect(),
        ),
        const SizedBox(height: 8),

        // Credentials note
        Text(
          l10n.fritzboxCredentialsNote,
          style: TextStyle(
            fontSize: 12,
            color: Colors.grey[600],
          ),
        ),

        if (_errorMessage != null) ...[
          const SizedBox(height: 16),
          Text(
            _errorMessage!,
            style: const TextStyle(color: Colors.red),
          ),
        ],

        const SizedBox(height: 24),

        // Connect button
        Row(
          children: [
            Expanded(
              child: _isConnecting
                  ? const Center(child: CircularProgressIndicator())
                  : ElevatedButton.icon(
                      onPressed: _connect,
                      icon: const Icon(Icons.link),
                      label: Text(l10n.fritzboxTestAndSave),
                    ),
            ),
          ],
        ),
      ],
    );
  }
}
