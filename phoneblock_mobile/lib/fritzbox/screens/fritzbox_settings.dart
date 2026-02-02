import 'package:flutter/material.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_models.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_service.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_storage.dart';
import 'package:phoneblock_mobile/fritzbox/screens/fritzbox_wizard.dart';
import 'package:phoneblock_mobile/l10n/app_localizations.dart';
import 'package:phoneblock_mobile/main.dart'
    show newCallIds, getAuthToken, fetchAccountSettings;
import 'package:phoneblock_mobile/storage.dart';

/// Settings screen for Fritz!Box integration.
class FritzBoxSettingsScreen extends StatefulWidget {
  const FritzBoxSettingsScreen({super.key});

  @override
  State<FritzBoxSettingsScreen> createState() => _FritzBoxSettingsScreenState();
}

class _FritzBoxSettingsScreenState extends State<FritzBoxSettingsScreen> {
  bool _isLoading = true;
  bool _isSyncing = false;
  bool _isConfiguringCardDav = false;
  FritzBoxConfig? _config;
  FritzBoxConnectionState _connectionState =
      FritzBoxConnectionState.notConfigured;
  FritzBoxDeviceInfo? _deviceInfo;
  int _callCount = 0;
  CardDavStatus _cardDavStatus = CardDavStatus.notConfigured;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    setState(() {
      _isLoading = true;
    });

    try {
      final config = await FritzBoxStorage.instance.getConfig();
      final callCount =
          await ScreenedCallsDatabase.instance.getFritzBoxCallsCount();

      // Check connection state
      await FritzBoxService.instance.checkConnection();
      final connectionState = FritzBoxService.instance.connectionState;

      FritzBoxDeviceInfo? deviceInfo;
      CardDavStatus cardDavStatus = CardDavStatus.notConfigured;

      if (connectionState == FritzBoxConnectionState.connected) {
        deviceInfo = await FritzBoxService.instance.getDeviceInfo();

        // Check CardDAV status if configured
        if (config?.blocklistMode == BlocklistMode.cardDav) {
          cardDavStatus = await FritzBoxService.instance.verifyCardDav();
        }
      }

      if (mounted) {
        setState(() {
          _config = config;
          _connectionState = connectionState;
          _deviceInfo = deviceInfo;
          _callCount = callCount;
          _cardDavStatus = cardDavStatus;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _syncNow() async {
    if (_isSyncing) return;

    setState(() {
      _isSyncing = true;
    });

    try {
      final newIds = await FritzBoxService.instance.syncCallList();
      // Track synced calls as new
      newCallIds.addAll(newIds);
      final callCount =
          await ScreenedCallsDatabase.instance.getFritzBoxCallsCount();

      if (mounted) {
        setState(() {
          _callCount = callCount;
          _isSyncing = false;
        });

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
                AppLocalizations.of(context)!.fritzboxSyncComplete(newIds.length)),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isSyncing = false;
        });

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppLocalizations.of(context)!.fritzboxSyncError),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  Future<void> _disconnect() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(AppLocalizations.of(context)!.fritzboxDisconnectTitle),
        content: Text(AppLocalizations.of(context)!.fritzboxDisconnectMessage),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(AppLocalizations.of(context)!.cancel),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(
              AppLocalizations.of(context)!.fritzboxDisconnect,
              style: const TextStyle(color: Colors.red),
            ),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      await FritzBoxService.instance.disconnect();
      if (mounted) {
        await _loadData();
      }
    }
  }

  Future<void> _openWizard() async {
    final result = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (context) => const FritzBoxWizard()),
    );

    if (result == true && mounted) {
      await _loadData();
    }
  }

  Future<void> _enableCardDav() async {
    final l10n = AppLocalizations.of(context)!;

    setState(() {
      _isConfiguringCardDav = true;
    });

    try {
      // Get PhoneBlock credentials
      final authToken = await getAuthToken();
      if (authToken == null) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(l10n.fritzboxPhoneBlockNotLoggedIn),
              backgroundColor: Colors.red,
            ),
          );
        }
        return;
      }

      // Fetch PhoneBlock username
      final accountSettings = await fetchAccountSettings(authToken);
      if (accountSettings?.login == null) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(l10n.fritzboxCannotGetUsername),
              backgroundColor: Colors.red,
            ),
          );
        }
        return;
      }

      // Configure CardDAV
      await FritzBoxService.instance.configureCardDav(
        phoneBlockUsername: accountSettings!.login!,
        phoneBlockToken: authToken,
      );

      if (mounted) {
        await _loadData();
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(l10n.fritzboxCardDavEnabled)),
          );
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(l10n.fritzboxBlocklistConfigFailed),
            backgroundColor: Colors.red,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() {
          _isConfiguringCardDav = false;
        });
      }
    }
  }

  Future<void> _disableCardDav() async {
    final l10n = AppLocalizations.of(context)!;

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.fritzboxDisableCardDavTitle),
        content: Text(l10n.fritzboxDisableCardDavMessage),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(l10n.cancel),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(l10n.fritzboxDisable),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      await FritzBoxService.instance.removeCardDav();
      if (mounted) {
        await _loadData();
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(l10n.fritzboxCardDavDisabled)),
          );
        }
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.fritzboxTitle),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _buildContent(context, l10n),
    );
  }

  Widget _buildContent(BuildContext context, AppLocalizations l10n) {
    if (_connectionState == FritzBoxConnectionState.notConfigured) {
      return _buildNotConfigured(context, l10n);
    }

    return ListView(
      children: [
        // Connection status card
        _buildConnectionCard(context, l10n),

        // CardDAV/Blocklist section
        const Divider(),
        _buildBlocklistSection(context, l10n),

        // Sync section
        if (_connectionState == FritzBoxConnectionState.connected) ...[
          const Divider(),
          _buildSyncSection(context, l10n),
        ],

        // Disconnect option
        const Divider(),
        ListTile(
          leading: const Icon(Icons.link_off, color: Colors.red),
          title: Text(
            l10n.fritzboxDisconnect,
            style: const TextStyle(color: Colors.red),
          ),
          onTap: _disconnect,
        ),
      ],
    );
  }

  Widget _buildNotConfigured(BuildContext context, AppLocalizations l10n) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.router,
              size: 80,
              color: Colors.grey[400],
            ),
            const SizedBox(height: 24),
            Text(
              l10n.fritzboxNotConfigured,
              style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                    color: Colors.grey[600],
                  ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 12),
            Text(
              l10n.fritzboxNotConfiguredDescription,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: Colors.grey[600],
                  ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              onPressed: _openWizard,
              icon: const Icon(Icons.add),
              label: Text(l10n.fritzboxConnect),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildConnectionCard(BuildContext context, AppLocalizations l10n) {
    final isConnected = _connectionState == FritzBoxConnectionState.connected;
    final statusColor = isConnected ? Colors.green : Colors.orange;
    final statusIcon = isConnected ? Icons.check_circle : Icons.cloud_off;

    return Card(
      margin: const EdgeInsets.all(16),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.router, size: 40, color: statusColor),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        _deviceInfo?.modelName ??
                            _config?.host ??
                            l10n.fritzboxTitle,
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          Icon(statusIcon, size: 16, color: statusColor),
                          const SizedBox(width: 4),
                          Text(
                            isConnected
                                ? l10n.fritzboxConnected
                                : l10n.fritzboxOffline,
                            style: TextStyle(color: statusColor),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
            if (_deviceInfo?.fritzosVersion != null) ...[
              const Divider(height: 24),
              _buildInfoRow(l10n.fritzboxVersion, _deviceInfo!.fritzosVersion!),
            ],
            if (_config?.host != null) ...[
              _buildInfoRow(l10n.fritzboxHost, _config!.host!),
            ],
            _buildInfoRow(l10n.fritzboxCachedCalls, _callCount.toString()),
            if (_config?.lastFetchTimestamp != null) ...[
              _buildInfoRow(
                l10n.fritzboxLastSync,
                _formatTimestamp(_config!.lastFetchTimestamp!),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildInfoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(color: Colors.grey)),
          Text(value),
        ],
      ),
    );
  }

  Widget _buildBlocklistSection(BuildContext context, AppLocalizations l10n) {
    final isCardDavEnabled = _config?.blocklistMode == BlocklistMode.cardDav;

    return Column(
      children: [
        ListTile(
          leading: Icon(
            Icons.shield,
            color: isCardDavEnabled ? Colors.green : Colors.grey,
          ),
          title: Text(l10n.fritzboxBlocklistMode),
          subtitle: Text(
            isCardDavEnabled
                ? l10n.fritzboxBlocklistModeCardDav
                : l10n.fritzboxBlocklistModeNone,
          ),
        ),
        if (isCardDavEnabled) ...[
          // Show CardDAV status
          _buildCardDavStatusTile(context, l10n),
        ] else ...[
          // Show option to enable CardDAV
          if (_connectionState == FritzBoxConnectionState.connected)
            FutureBuilder<bool>(
              future: FritzBoxService.instance.supportsCardDav(),
              builder: (context, snapshot) {
                final supportsCardDav = snapshot.data ?? false;

                if (!supportsCardDav) {
                  return ListTile(
                    leading: const Icon(Icons.info_outline, color: Colors.orange),
                    title: Text(l10n.fritzboxVersionTooOldForCardDav),
                    dense: true,
                  );
                }

                return ListTile(
                  leading: _isConfiguringCardDav
                      ? const SizedBox(
                          width: 24,
                          height: 24,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.cloud_sync),
                  title: Text(l10n.fritzboxEnableCardDav),
                  subtitle: Text(l10n.fritzboxEnableCardDavDescription),
                  onTap: _isConfiguringCardDav ? null : _enableCardDav,
                );
              },
            ),
        ],
      ],
    );
  }

  Widget _buildCardDavStatusTile(BuildContext context, AppLocalizations l10n) {
    final statusIcon = _getCardDavStatusIcon();
    final statusColor = _getCardDavStatusColor();
    final statusText = _getCardDavStatusText(l10n);

    return Column(
      children: [
        ListTile(
          leading: Icon(statusIcon, color: statusColor),
          title: Text(l10n.fritzboxCardDavStatus),
          subtitle: Text(statusText),
          trailing: _cardDavStatus == CardDavStatus.error
              ? IconButton(
                  icon: const Icon(Icons.refresh),
                  onPressed: _loadData,
                )
              : null,
        ),
        // Note about sync frequency
        ListTile(
          leading: const Icon(Icons.info_outline, color: Colors.grey),
          title: Text(
            l10n.fritzboxCardDavNote,
            style: const TextStyle(fontSize: 14),
          ),
          dense: true,
        ),
        // Disable option
        ListTile(
          leading: const Icon(Icons.remove_circle_outline, color: Colors.orange),
          title: Text(l10n.fritzboxDisableCardDav),
          onTap: _disableCardDav,
        ),
      ],
    );
  }

  IconData _getCardDavStatusIcon() {
    switch (_cardDavStatus) {
      case CardDavStatus.synced:
        return Icons.check_circle;
      case CardDavStatus.syncPending:
        return Icons.pending;
      case CardDavStatus.error:
        return Icons.error;
      case CardDavStatus.disabled:
        return Icons.pause_circle;
      case CardDavStatus.notConfigured:
        return Icons.help_outline;
    }
  }

  Color _getCardDavStatusColor() {
    switch (_cardDavStatus) {
      case CardDavStatus.synced:
        return Colors.green;
      case CardDavStatus.syncPending:
        return Colors.orange;
      case CardDavStatus.error:
        return Colors.red;
      case CardDavStatus.disabled:
        return Colors.grey;
      case CardDavStatus.notConfigured:
        return Colors.grey;
    }
  }

  String _getCardDavStatusText(AppLocalizations l10n) {
    switch (_cardDavStatus) {
      case CardDavStatus.synced:
        return l10n.fritzboxCardDavStatusSynced;
      case CardDavStatus.syncPending:
        return l10n.fritzboxCardDavStatusPending;
      case CardDavStatus.error:
        return l10n.fritzboxCardDavStatusError;
      case CardDavStatus.disabled:
        return l10n.fritzboxCardDavStatusDisabled;
      case CardDavStatus.notConfigured:
        return l10n.fritzboxBlocklistModeNone;
    }
  }

  Widget _buildSyncSection(BuildContext context, AppLocalizations l10n) {
    return ListTile(
      leading: _isSyncing
          ? const SizedBox(
              width: 24,
              height: 24,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : const Icon(Icons.sync),
      title: Text(l10n.fritzboxSyncNow),
      subtitle: Text(l10n.fritzboxSyncDescription),
      onTap: _isSyncing ? null : _syncNow,
    );
  }

  String _formatTimestamp(int timestamp) {
    final date = DateTime.fromMillisecondsSinceEpoch(timestamp);
    final now = DateTime.now();
    final diff = now.difference(date);

    if (diff.inMinutes < 1) {
      return AppLocalizations.of(context)!.fritzboxJustNow;
    } else if (diff.inHours < 1) {
      return AppLocalizations.of(context)!.fritzboxMinutesAgo(diff.inMinutes);
    } else if (diff.inDays < 1) {
      return AppLocalizations.of(context)!.fritzboxHoursAgo(diff.inHours);
    } else {
      return '${date.day}.${date.month}.${date.year} ${date.hour}:${date.minute.toString().padLeft(2, '0')}';
    }
  }
}
