import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_discovery.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_models.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_service.dart';
import 'package:phoneblock_mobile/l10n/app_localizations.dart';
import 'package:phoneblock_mobile/main.dart'
    show newCallIds, getAuthToken, fetchAccountSettings;

/// Wizard steps for Fritz!Box connection.
enum _WizardStep {
  /// Detecting Fritz!Box on the network.
  detection,

  /// Entering login credentials.
  login,

  /// Configuring blocklist (CardDAV).
  blocklist,
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
  bool _isConfiguring = false;
  FritzBoxDeviceInfo? _deviceInfo;
  String? _errorMessage;

  final _hostController = TextEditingController();
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _showPassword = false;
  bool _showUsername = false;

  // Blocklist step state
  bool _supportsCardDav = false;
  BlocklistMode _selectedMode = BlocklistMode.none;

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

  Future<void> _proceedToLogin() async {
    final host = _hostController.text.trim();

    // Discover the default username if the user hasn't entered one yet
    if (_usernameController.text.isEmpty && host.isNotEmpty) {
      final defaultUsername = await FritzBoxService.instance.getDefaultUsername(host);
      if (defaultUsername != null && defaultUsername.isNotEmpty) {
        _usernameController.text = defaultUsername;
      }
    }

    if (mounted) {
      setState(() {
        _currentStep = _WizardStep.login;
      });
    }
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
        // Check if CardDAV is supported
        final supportsCardDav = await FritzBoxService.instance.supportsCardDav();

        setState(() {
          _isConnecting = false;
          _supportsCardDav = supportsCardDav;
          _selectedMode =
              supportsCardDav ? BlocklistMode.cardDav : BlocklistMode.none;
          _currentStep = _WizardStep.blocklist;
        });
      } else {
        setState(() {
          _isConnecting = false;
          _errorMessage =
              AppLocalizations.of(context)!.fritzboxConnectionFailed;
        });
      }
    }
  }

  Future<void> _configureBlocklist() async {
    final l10n = AppLocalizations.of(context)!;

    if (_selectedMode == BlocklistMode.none) {
      // Skip - just finish wizard with initial sync
      await _finishWizard();
      return;
    }

    setState(() {
      _isConfiguring = true;
      _errorMessage = null;
    });

    try {
      if (_selectedMode == BlocklistMode.cardDav) {
        // Get PhoneBlock credentials
        final authToken = await getAuthToken();
        if (authToken == null) {
          setState(() {
            _errorMessage = l10n.fritzboxPhoneBlockNotLoggedIn;
            _isConfiguring = false;
          });
          return;
        }

        // Fetch PhoneBlock username
        final accountSettings = await fetchAccountSettings(authToken);
        if (accountSettings?.login == null) {
          setState(() {
            _errorMessage = l10n.fritzboxCannotGetUsername;
            _isConfiguring = false;
          });
          return;
        }

        // Configure CardDAV
        await FritzBoxService.instance.configureCardDav(
          phoneBlockUsername: accountSettings!.login!,
          phoneBlockToken: authToken,
        );
      }

      await _finishWizard();
    } catch (e, stackTrace) {
      if (kDebugMode) {
        print('CardDAV configuration failed: $e');
        print('Stack trace: $stackTrace');
      }
      if (mounted) {
        setState(() {
          _errorMessage = '${l10n.fritzboxBlocklistConfigFailed}: $e';
          _isConfiguring = false;
        });
      }
    }
  }

  Future<void> _finishWizard() async {
    // Sync local blocklistMode with actual Fritz!Box state, so even if the
    // user chose "skip", the local config reflects any pre-existing CardDAV
    // configuration on the Fritz!Box.
    await FritzBoxService.instance.syncBlocklistMode();

    // Perform initial sync
    final newIds = await FritzBoxService.instance.syncCallList();
    // Track synced calls as new
    newCallIds.addAll(newIds);

    if (mounted) {
      Navigator.pop(context, true);
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
          // Only allow going back to previous steps
          if (step < _currentStep.index) {
            setState(() {
              _currentStep = _WizardStep.values[step];
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
            isActive: _currentStep.index >= _WizardStep.login.index,
            state: _currentStep == _WizardStep.login
                ? StepState.indexed
                : _currentStep.index > _WizardStep.login.index
                    ? StepState.complete
                    : StepState.indexed,
            content: _buildLoginStep(context, l10n),
          ),
          Step(
            title: Text(l10n.fritzboxStepBlocklist),
            subtitle: Text(l10n.fritzboxStepBlocklistSubtitle),
            isActive: _currentStep == _WizardStep.blocklist,
            state: StepState.indexed,
            content: _buildBlocklistStep(context, l10n),
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

        // Password field
        TextField(
          controller: _passwordController,
          decoration: InputDecoration(
            labelText: l10n.fritzboxPasswordLabel,
            prefixIcon: const Icon(Icons.lock),
            border: const OutlineInputBorder(),
            suffixIcon: IconButton(
              icon:
                  Icon(_showPassword ? Icons.visibility_off : Icons.visibility),
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

        // Username toggle and field
        SwitchListTile(
          contentPadding: EdgeInsets.zero,
          title: Text(l10n.fritzboxShowUsername),
          value: _showUsername,
          onChanged: (value) {
            setState(() {
              _showUsername = value;
              if (!value) {
                _usernameController.clear();
              }
            });
          },
        ),
        if (_showUsername) ...[
          TextField(
            controller: _usernameController,
            decoration: InputDecoration(
              labelText: l10n.fritzboxUsernameLabel,
              prefixIcon: const Icon(Icons.person),
              border: const OutlineInputBorder(),
            ),
            textInputAction: TextInputAction.done,
            autocorrect: false,
            onSubmitted: (_) => _connect(),
          ),
          const SizedBox(height: 8),
        ],

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

  Widget _buildBlocklistStep(BuildContext context, AppLocalizations l10n) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          l10n.fritzboxBlocklistDescription,
          style: const TextStyle(color: Colors.grey),
        ),
        const SizedBox(height: 16),

        // CardDAV option (only for FRITZ!OS 7.20+)
        if (_supportsCardDav) ...[
          _buildBlocklistOption(
            title: l10n.fritzboxCardDavTitle,
            subtitle: l10n.fritzboxCardDavDescription,
            icon: Icons.cloud_sync,
            selected: _selectedMode == BlocklistMode.cardDav,
            onTap: () => setState(() => _selectedMode = BlocklistMode.cardDav),
          ),
          const SizedBox(height: 12),
        ],

        // Skip option (configure later)
        _buildBlocklistOption(
          title: l10n.fritzboxSkipBlocklist,
          subtitle: l10n.fritzboxSkipBlocklistDescription,
          icon: Icons.skip_next,
          selected: _selectedMode == BlocklistMode.none,
          onTap: () => setState(() => _selectedMode = BlocklistMode.none),
        ),

        // Version warning for older FRITZ!OS
        if (!_supportsCardDav) ...[
          const SizedBox(height: 16),
          _buildVersionWarning(l10n),
        ],

        if (_errorMessage != null) ...[
          const SizedBox(height: 16),
          Text(
            _errorMessage!,
            style: const TextStyle(color: Colors.red),
          ),
        ],

        const SizedBox(height: 24),

        // Configure button
        Row(
          children: [
            Expanded(
              child: _isConfiguring
                  ? const Center(child: CircularProgressIndicator())
                  : ElevatedButton.icon(
                      onPressed: _configureBlocklist,
                      icon: const Icon(Icons.check),
                      label: Text(l10n.fritzboxFinishSetup),
                    ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildBlocklistOption({
    required String title,
    required String subtitle,
    required IconData icon,
    required bool selected,
    required VoidCallback onTap,
  }) {
    return Card(
      elevation: selected ? 4 : 1,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(
          color: selected ? Theme.of(context).primaryColor : Colors.transparent,
          width: 2,
        ),
      ),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Icon(
                icon,
                size: 32,
                color: selected
                    ? Theme.of(context).primaryColor
                    : Colors.grey[600],
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight:
                            selected ? FontWeight.bold : FontWeight.normal,
                        color: selected
                            ? Theme.of(context).primaryColor
                            : Colors.black87,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      subtitle,
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.grey[600],
                      ),
                    ),
                  ],
                ),
              ),
              if (selected)
                Icon(
                  Icons.check_circle,
                  color: Theme.of(context).primaryColor,
                ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildVersionWarning(AppLocalizations l10n) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.orange.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.orange.withValues(alpha: 0.3)),
      ),
      child: Row(
        children: [
          Icon(Icons.info_outline, color: Colors.orange[700]),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              l10n.fritzboxVersionTooOldForCardDav,
              style: TextStyle(color: Colors.orange[900]),
            ),
          ),
        ],
      ),
    );
  }
}
