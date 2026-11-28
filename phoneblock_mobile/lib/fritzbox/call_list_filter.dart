import 'dart:convert';

import 'package:fritz_tr064/fritz_tr064.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_models.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_rights.dart';
import 'package:phoneblock_mobile/logging/app_logger.dart';
import 'package:phoneblock_mobile/storage.dart';

/// UPnP error code with which the Fritz!Box answers `X_AVM-DE_GetPhonePort`
/// past the last phone port.
const int upnpArrayIndexInvalid = 713;

/// Upper bound for the phone port scan, in case a box never reports 713.
const int _maxPhonePorts = 32;

/// Which calls the call list shows (issue #294).
///
/// Two groups: "Anrufe an" (the called number) and "Angenommen von" (the
/// device that took the call). Within a group the entries are alternatives,
/// both groups must match. A group with nothing selected matches every call.
///
/// The filter only decides what the call list shows, never what is imported,
/// stored or reported.
class CallListFilter {
  /// The filter that shows every call.
  static const CallListFilter all = CallListFilter();

  /// "Anrufe an: Handy" - calls screened on the phone itself.
  final bool toMobile;

  /// "Anrufe an": own numbers of the Fritz!Box, as the box reports them.
  final Set<String> toNumbers;

  /// "Angenommen von: Handy" - calls screened on the phone itself.
  final bool byMobile;

  /// "Angenommen von: Verpasst" - Fritz!Box calls no device took (missed and
  /// rejected).
  final bool byMissed;

  /// "Angenommen von": Fritz!Box devices, by their [normalizeDeviceName].
  final Set<String> byDevices;

  const CallListFilter({
    this.toMobile = false,
    this.toNumbers = const {},
    this.byMobile = false,
    this.byMissed = false,
    this.byDevices = const {},
  });

  /// Whether nothing is selected in the group "Anrufe an".
  bool get toAll => !toMobile && toNumbers.isEmpty;

  /// Whether nothing is selected in the group "Angenommen von".
  bool get byAll => !byMobile && !byMissed && byDevices.isEmpty;

  /// Whether the filter hides any call.
  bool get isActive => !toAll || !byAll;

  /// Whether the call list shows [call].
  bool matches(ScreenedCall call) => _matchesTo(call) && _matchesBy(call);

  bool _matchesTo(ScreenedCall call) {
    if (toAll) return true;
    if (call.source == CallSource.mobile) return toMobile;
    final called = call.calledNumber;
    // Calls imported before the called number was stored cannot be assigned.
    if (called == null) return false;
    return toNumbers.any((n) => sameOwnNumber(n, called));
  }

  bool _matchesBy(ScreenedCall call) {
    if (byAll) return true;
    if (call.source == CallSource.mobile) return byMobile;
    if (isMissedType(call.callType)) return byMissed;
    final device = call.device;
    if (device == null) return false;
    final key = deviceKey(device);
    return byDevices.any((d) => deviceKey(d) == key);
  }

  /// Applies the filter to [calls].
  List<ScreenedCall> apply(List<ScreenedCall> calls) =>
      isActive ? calls.where(matches).toList() : calls;

  CallListFilter copyWith({
    bool? toMobile,
    Set<String>? toNumbers,
    bool? byMobile,
    bool? byMissed,
    Set<String>? byDevices,
  }) {
    return CallListFilter(
      toMobile: toMobile ?? this.toMobile,
      toNumbers: toNumbers ?? this.toNumbers,
      byMobile: byMobile ?? this.byMobile,
      byMissed: byMissed ?? this.byMissed,
      byDevices: byDevices ?? this.byDevices,
    );
  }

  /// The stored form, null for [all].
  String? encode() {
    if (!isActive) return null;
    return jsonEncode({
      'toMobile': toMobile,
      'toNumbers': toNumbers.toList(),
      'byMobile': byMobile,
      'byMissed': byMissed,
      'byDevices': byDevices.toList(),
    });
  }

  /// Reads the stored form; null or unreadable means [all].
  static CallListFilter decode(String? value) {
    if (value == null || value.isEmpty) return all;
    try {
      final map = jsonDecode(value) as Map<String, dynamic>;
      Set<String> strings(Object? list) =>
          (list as List<dynamic>? ?? const []).cast<String>().toSet();
      return CallListFilter(
        toMobile: map['toMobile'] == true,
        toNumbers: strings(map['toNumbers']),
        byMobile: map['byMobile'] == true,
        byMissed: map['byMissed'] == true,
        byDevices: strings(map['byDevices']),
      );
    } catch (e, s) {
      AppLogger.instance.error('fritzbox', 'Unreadable call list filter', e, s);
      return all;
    }
  }
}

/// Whether a Fritz!Box call of [type] was taken by no device.
bool isMissedType(FritzBoxCallType? type) =>
    type == FritzBoxCallType.missed || type == FritzBoxCallType.rejected;

/// A leading type tag such as `DECT: `, `ISDN: `, `FON1: ` or `SIP: `
/// (upper case, so a name like `Oma: Mobil` stays intact).
final RegExp _typePrefix = RegExp(r'^[A-Z][A-Z0-9]{1,7}:\s*');

/// The device name as the call list reports it (`<Device>`).
///
/// `X_AVM-DE_GetPhonePort` reports a phone as `DECT: Wohnzimmer`, the call
/// list as `Wohnzimmer`: the type tag is dropped.
String normalizeDeviceName(String name) =>
    name.trim().replaceFirst(_typePrefix, '').trim();

/// Comparison key of a device name: [normalizeDeviceName], case-insensitive.
String deviceKey(String name) => normalizeDeviceName(name).toLowerCase();

/// The own number without a type tag (`SIP: 987654` becomes `987654`), or
/// null if nothing remains.
String? normalizeOwnNumber(String? called) {
  if (called == null) return null;
  final number = called.trim().replaceFirst(_typePrefix, '').trim();
  return number.isEmpty ? null : number;
}

/// Minimum digits for a suffix match of two own numbers.
const int _minSuffixDigits = 4;

/// Whether [a] and [b] denote the same own number of the Fritz!Box.
///
/// The box reports an own number with or without area code or country code
/// depending on the place (`987654`, `02123987654`, `+492123987654`): the
/// digits must agree, the shorter one may lack a leading part.
bool sameOwnNumber(String a, String b) {
  String digits(String s) => (normalizeOwnNumber(s) ?? '')
      .replaceAll(RegExp(r'[^0-9]'), '')
      .replaceFirst(RegExp(r'^0+'), '');
  final da = digits(a);
  final db = digits(b);
  if (da.isEmpty || db.isEmpty) return false;
  if (da == db) return true;
  final shorter = da.length < db.length ? da : db;
  final longer = da.length < db.length ? db : da;
  return shorter.length >= _minSuffixDigits && longer.endsWith(shorter);
}

/// An own number of the Fritz!Box for the filter selection.
class OwnNumberChoice {
  /// The number as the box reports it.
  final String number;

  /// The name the box gives the number, empty if none.
  final String name;

  const OwnNumberChoice(this.number, [this.name = '']);

  /// The label of the selection entry.
  String get label => name.isEmpty ? number : '$name ($number)';
}

/// The selection entries read from the Fritz!Box for the call list filter.
class CallFilterChoices {
  /// The own numbers (`X_AVM-DE_GetNumbers`).
  final List<OwnNumberChoice> numbers;

  /// The phones, SIP devices and answering machines, by their
  /// [normalizeDeviceName].
  final List<String> devices;

  /// Why the entries are incomplete, null if every query succeeded.
  final Object? error;

  const CallFilterChoices({
    this.numbers = const [],
    this.devices = const [],
    this.error,
  });

  /// The box could not be reached.
  factory CallFilterChoices.unreachable() =>
      CallFilterChoices(error: StateError('Fritz!Box not connected'));

  /// Whether the box answered every query.
  bool get complete => error == null;

  /// Whether the box refused a query for missing rights.
  bool get refused => error != null && isInsufficientRights(error!);
}

/// Reads the selection entries of the call list filter from the Fritz!Box:
/// own numbers, phones (until the box answers 713), SIP devices and
/// answering machines.
///
/// A refused or failing query does not stop the others; its error is kept in
/// [CallFilterChoices.error]. A failure without a SOAP fault (box not
/// reachable) ends the queries.
Future<CallFilterChoices> queryCallFilterChoices(Tr64Client client) async {
  final numbers = <OwnNumberChoice>[];
  final devices = <String>[];
  Object? error;

  void addDevice(String name) {
    final normalized = normalizeDeviceName(name);
    if (normalized.isEmpty) return;
    final key = normalized.toLowerCase();
    if (devices.any((d) => d.toLowerCase() == key)) return;
    devices.add(normalized);
  }

  /// Runs [query]; returns false if the box is not reachable.
  Future<bool> run(String what, Future<void> Function() query) async {
    try {
      await query();
      return true;
    } on SoapFaultException catch (e) {
      AppLogger.instance.warn('fritzbox', 'Call filter: $what refused: $e');
      error ??= e;
      return true;
    } catch (e, s) {
      AppLogger.instance.error('fritzbox', 'Call filter: $what failed', e, s);
      error ??= e;
      return false;
    }
  }

  final voip = client.voip();
  if (voip != null) {
    final reachable = await run('GetNumbers', () async {
      for (final n in await voip.getNumbers()) {
        if (n.number.isEmpty) continue;
        if (numbers.any((c) => sameOwnNumber(c.number, n.number))) continue;
        numbers.add(OwnNumberChoice(n.number, n.name));
      }
    }) &&
        await run('GetPhonePort', () async {
          for (var index = 1; index <= _maxPhonePorts; index++) {
            final String name;
            try {
              name = await voip.getPhonePort(index);
            } on SoapFaultException catch (e) {
              if (upnpErrorCode(e) == upnpArrayIndexInvalid) break;
              rethrow;
            }
            addDevice(name);
          }
        }) &&
        await run('GetClients', () async {
          for (final name in parseClientPhoneNames(await voip.getClients())) {
            addDevice(name);
          }
        });
    if (!reachable) {
      return CallFilterChoices(numbers: numbers, devices: devices, error: error);
    }
  }

  final tam = client.tam();
  if (tam != null) {
    await run('TAM GetList', () async {
      final list = await tam.getTAMList();
      for (final item in list.items) {
        if (item.display || item.enable) addDevice(item.name);
      }
    });
  }

  return CallFilterChoices(numbers: numbers, devices: devices, error: error);
}

/// The phone names in the client list of `X_AVM-DE_GetClients`
/// (`<Item>...<X_AVM-DE_PhoneName>PhoneBlock</X_AVM-DE_PhoneName>...`).
List<String> parseClientPhoneNames(String xml) {
  return [
    for (final m in RegExp(r'<(?:[\w-]+_)?PhoneName>([^<]*)</', dotAll: true)
        .allMatches(xml))
      _unescapeXml(m.group(1)!.trim()),
  ].where((name) => name.isNotEmpty).toList();
}

String _unescapeXml(String s) => s
    .replaceAll('&lt;', '<')
    .replaceAll('&gt;', '>')
    .replaceAll('&quot;', '"')
    .replaceAll('&apos;', "'")
    .replaceAll('&amp;', '&');

/// One selection entry of a filter group.
class FilterOption {
  /// The value stored in the filter.
  final String value;

  /// The label shown.
  final String label;

  /// Selected, but the Fritz!Box no longer has it.
  final bool gone;

  const FilterOption(this.value, this.label, {this.gone = false});
}

/// The selection entries of both groups (without "Handy" and "Verpasst").
class FilterOptions {
  final List<FilterOption> numbers;
  final List<FilterOption> devices;

  const FilterOptions(this.numbers, this.devices);
}

/// Combines the entries from the Fritz!Box with those known from imported
/// calls and the current selection.
///
/// If the box answered completely, its entries are offered and a selected
/// entry it no longer has is marked [FilterOption.gone]. Otherwise the
/// entries from [storedNumbers] and [storedDevices] (called numbers and
/// devices of imported calls) are added, and selected entries are offered
/// unmarked - nothing is known about them.
FilterOptions buildFilterOptions({
  required CallFilterChoices choices,
  required Iterable<String> storedNumbers,
  required Iterable<String> storedDevices,
  required CallListFilter filter,
}) {
  final numbers = <FilterOption>[
    for (final n in choices.numbers) FilterOption(n.number, n.label),
  ];
  final devices = <FilterOption>[
    for (final d in choices.devices) FilterOption(d, d),
  ];

  bool hasNumber(String n) => numbers.any((o) => sameOwnNumber(o.value, n));
  bool hasDevice(String d) =>
      devices.any((o) => deviceKey(o.value) == deviceKey(d));

  if (!choices.complete) {
    for (final n in storedNumbers) {
      final number = normalizeOwnNumber(n);
      if (number != null && !hasNumber(number)) {
        numbers.add(FilterOption(number, number));
      }
    }
    for (final d in storedDevices) {
      final device = normalizeDeviceName(d);
      if (device.isNotEmpty && !hasDevice(device)) {
        devices.add(FilterOption(device, device));
      }
    }
  }

  final gone = choices.complete;
  for (final n in filter.toNumbers) {
    if (!hasNumber(n)) numbers.add(FilterOption(n, n, gone: gone));
  }
  for (final d in filter.byDevices) {
    if (!hasDevice(d)) devices.add(FilterOption(d, d, gone: gone));
  }

  return FilterOptions(numbers, devices);
}
