import 'package:flutter/material.dart';
import 'package:phoneblock_mobile/fritzbox/call_list_filter.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_service.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_storage.dart';
import 'package:phoneblock_mobile/l10n/app_localizations.dart';
import 'package:phoneblock_mobile/logging/app_logger.dart';
import 'package:phoneblock_mobile/storage.dart';

/// Short description of [filter] for the Fritz!Box settings entry.
String callListFilterSummary(AppLocalizations l10n, CallListFilter filter) {
  if (!filter.isActive) return l10n.fritzboxCallFilterAll;
  final parts = <String>[
    if (!filter.toAll)
      '${l10n.fritzboxCallFilterTo}: ${[
        if (filter.toMobile) l10n.fritzboxCallFilterMobile,
        ...filter.toNumbers,
      ].join(', ')}',
    if (!filter.byAll)
      '${l10n.fritzboxCallFilterBy}: ${[
        if (filter.byMobile) l10n.fritzboxCallFilterMobile,
        ...filter.byDevices,
        if (filter.byMissed) l10n.fritzboxCallFilterMissed,
      ].join(', ')}',
  ];
  return parts.join(' · ');
}

/// Selects which calls the call list shows (issue #294).
///
/// The own numbers and devices are read from the Fritz!Box when the screen
/// opens. If that fails, the entries known from imported calls are offered.
class CallListFilterScreen extends StatefulWidget {
  /// Reads the selection entries from the Fritz!Box; defaults to
  /// [FritzBoxService.loadCallFilterChoices].
  final Future<CallFilterChoices> Function()? loadChoices;

  const CallListFilterScreen({super.key, this.loadChoices});

  @override
  State<CallListFilterScreen> createState() => _CallListFilterScreenState();
}

class _CallListFilterScreenState extends State<CallListFilterScreen> {
  CallListFilter _filter = CallListFilter.all;
  CallFilterChoices? _choices;
  List<String> _storedNumbers = const [];
  List<String> _storedDevices = const [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final config = await FritzBoxStorage.instance.getConfig();
    final db = ScreenedCallsDatabase.instance;
    final storedNumbers = await db.getFritzBoxCalledNumbers();
    final storedDevices = await db.getFritzBoxDevices();
    if (!mounted) return;
    setState(() {
      _filter = config?.callListFilter ?? CallListFilter.all;
      _storedNumbers = storedNumbers;
      _storedDevices = storedDevices;
    });

    final load =
        widget.loadChoices ?? FritzBoxService.instance.loadCallFilterChoices;
    CallFilterChoices choices;
    try {
      choices = await load();
    } catch (e) {
      choices = CallFilterChoices(error: e);
    }
    if (!mounted) return;
    setState(() {
      _choices = choices;
    });
  }

  /// The pending writes, in the order of the changes.
  Future<void> _saving = Future.value();

  void _update(CallListFilter filter) {
    setState(() {
      _filter = filter;
    });
    _saving = _saving.then((_) async {
      try {
        await FritzBoxStorage.instance.updateConfig(callListFilter: filter);
      } catch (e, s) {
        AppLogger.instance.error('fritzbox', 'Saving call list filter failed', e, s);
      }
    });
  }

  void _toggleNumber(String number, bool selected) {
    final numbers = _filter.toNumbers
        .where((n) => !sameOwnNumber(n, number))
        .toSet();
    if (selected) numbers.add(number);
    _update(_filter.copyWith(toNumbers: numbers));
  }

  void _toggleDevice(String device, bool selected) {
    final key = deviceKey(device);
    final devices =
        _filter.byDevices.where((d) => deviceKey(d) != key).toSet();
    if (selected) devices.add(device);
    _update(_filter.copyWith(byDevices: devices));
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final choices = _choices;
    final options = buildFilterOptions(
      // While loading: only the current selection, nothing marked.
      choices: choices ?? CallFilterChoices(error: StateError('loading')),
      storedNumbers: choices == null ? const [] : _storedNumbers,
      storedDevices: choices == null ? const [] : _storedDevices,
      filter: _filter,
    );
    final theme = Theme.of(context);

    Widget header(String text) => Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
          child: Text(
            text,
            style: theme.textTheme.titleSmall
                ?.copyWith(color: theme.colorScheme.primary),
          ),
        );

    Widget option(FilterOption o, bool value, void Function(bool) onChanged) =>
        CheckboxListTile(
          title: Text(o.label),
          subtitle: o.gone ? Text(l10n.fritzboxCallFilterGone) : null,
          value: value,
          onChanged: (v) => onChanged(v ?? false),
        );

    return Scaffold(
      appBar: AppBar(title: Text(l10n.fritzboxCallFilter)),
      body: ListView(
        children: [
          if (choices == null) const LinearProgressIndicator(),
          if (choices != null && !choices.complete)
            ListTile(
              leading: const Icon(Icons.warning_amber, color: Colors.orange),
              title: Text(choices.refused
                  ? l10n.fritzboxCallFilterRefused
                  : l10n.fritzboxCallFilterFailed),
              subtitle: Text(l10n.fritzboxCallFilterFallback),
            ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
            child: Text(l10n.fritzboxCallFilterHint,
                style: theme.textTheme.bodySmall),
          ),
          header(l10n.fritzboxCallFilterTo),
          CheckboxListTile(
            title: Text(l10n.fritzboxCallFilterMobile),
            value: _filter.toMobile,
            onChanged: (v) => _update(_filter.copyWith(toMobile: v ?? false)),
          ),
          for (final o in options.numbers)
            option(o, _filter.toNumbers.any((n) => sameOwnNumber(n, o.value)),
                (v) => _toggleNumber(o.value, v)),
          header(l10n.fritzboxCallFilterBy),
          CheckboxListTile(
            title: Text(l10n.fritzboxCallFilterMobile),
            value: _filter.byMobile,
            onChanged: (v) => _update(_filter.copyWith(byMobile: v ?? false)),
          ),
          for (final o in options.devices)
            option(
                o,
                _filter.byDevices
                    .any((d) => deviceKey(d) == deviceKey(o.value)),
                (v) => _toggleDevice(o.value, v)),
          CheckboxListTile(
            title: Text(l10n.fritzboxCallFilterMissed),
            value: _filter.byMissed,
            onChanged: (v) => _update(_filter.copyWith(byMissed: v ?? false)),
          ),
        ],
      ),
    );
  }
}
