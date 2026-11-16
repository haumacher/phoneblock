import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';

import 'l10n/l10n_extensions.dart';
import 'logging/app_logger.dart';

/// Builds a dialer URI without interpreting the service code as a fragment.
Uri mailboxDialerUri(String code) => Uri(scheme: 'tel', path: code);

/// Accepts digits with an optional leading international prefix.
bool isValidMailboxNumber(String number) =>
    RegExp(r'^\+?[0-9]+$').hasMatch(number);

/// Help and dialer actions for carrier busy forwarding.
class MailboxForwardingScreen extends StatefulWidget {
  const MailboxForwardingScreen({super.key});

  @override
  State<MailboxForwardingScreen> createState() =>
      _MailboxForwardingScreenState();
}

class _MailboxForwardingScreenState extends State<MailboxForwardingScreen> {
  static const _numberKey = 'mailbox_forwarding_number';
  final _number = TextEditingController();
  SharedPreferences? _preferences;
  Future<void> _pendingSave = Future.value();
  bool _invalidNumber = false;

  @override
  void initState() {
    super.initState();
    _loadNumber();
  }

  Future<void> _loadNumber() async {
    try {
      final preferences = await SharedPreferences.getInstance();
      if (!mounted) return;
      setState(() {
        _preferences = preferences;
        _number.text = preferences.getString(_numberKey) ?? '';
      });
    } catch (e, s) {
      AppLogger.instance.error(
        'settings',
        'Error loading mailbox number',
        e,
        s,
      );
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(context.l10n.mailboxStorageError)));
    }
  }

  void _saveNumber(BuildContext context, String number) {
    final preferences = _preferences;
    if (preferences == null) return;
    setState(() => _invalidNumber = false);
    _pendingSave = _pendingSave.then((_) async {
      try {
        final saved = isValidMailboxNumber(number)
            ? await preferences.setString(_numberKey, number)
            : await preferences.remove(_numberKey);
        if (!saved) throw StateError('Failed to save mailbox number');
      } catch (e, s) {
        AppLogger.instance.error(
          'settings',
          'Error saving mailbox number',
          e,
          s,
        );
        if (!context.mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(context.l10n.mailboxStorageError)),
        );
      }
    });
  }

  Future<void> _open(
    BuildContext context,
    Uri uri, {
    required bool dialer,
  }) async {
    var opened = false;
    try {
      opened = await launchUrl(uri, mode: LaunchMode.externalApplication);
    } on PlatformException catch (e, s) {
      AppLogger.instance.error(
        'settings',
        'Error opening external application',
        e,
        s,
      );
    }
    if (!context.mounted || opened) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          dialer
              ? context.l10n.mailboxDialerError
              : context.l10n.mailboxLinkError,
        ),
      ),
    );
  }

  @override
  void dispose() {
    _number.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.mailboxForwardingTitle)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(l10n.mailboxForwardingExplanation),
          const SizedBox(height: 12),
          Text(l10n.mailboxForwardingTradeoff),
          const SizedBox(height: 12),
          Text(l10n.mailboxForwardingReminder),
          const SizedBox(height: 16),
          OutlinedButton(
            onPressed: () =>
                _open(context, mailboxDialerUri('*#67#'), dialer: true),
            child: Text(l10n.mailboxCheckStatus),
          ),
          OutlinedButton(
            onPressed: () =>
                _open(context, mailboxDialerUri('#67#'), dialer: true),
            child: Text(l10n.mailboxDisableBusy),
          ),
          OutlinedButton(
            onPressed: () =>
                _open(context, mailboxDialerUri('*67#'), dialer: true),
            child: Text(l10n.mailboxEnableBusy),
          ),
          ExpansionTile(
            title: Text(l10n.mailboxFallbackTitle),
            children: [
              TextField(
                controller: _number,
                enabled: _preferences != null,
                keyboardType: TextInputType.phone,
                decoration: InputDecoration(
                  labelText: l10n.mailboxNumberHint,
                  errorText: _invalidNumber ? l10n.mailboxNumberInvalid : null,
                  errorMaxLines: 3,
                ),
                onChanged: (value) => _saveNumber(context, value),
              ),
              OutlinedButton(
                onPressed: _preferences == null
                    ? null
                    : () {
                        final number = _number.text;
                        if (!isValidMailboxNumber(number)) {
                          setState(() => _invalidNumber = true);
                          return;
                        }
                        _open(
                          context,
                          mailboxDialerUri('**67*$number#'),
                          dialer: true,
                        );
                      },
                child: Text(l10n.mailboxEnableWithNumber),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Text(l10n.mailboxCarrierHint),
          const SizedBox(height: 12),
          Text(l10n.mailboxSmsHint),
          TextButton(
            onPressed: () => _open(
              context,
              Uri.parse('https://phoneblock.net/phoneblock/faq#mailbox'),
              dialer: false,
            ),
            child: Text(l10n.mailboxFaq),
          ),
        ],
      ),
    );
  }
}
