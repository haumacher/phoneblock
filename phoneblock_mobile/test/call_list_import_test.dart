import 'package:flutter_test/flutter_test.dart';
import 'package:fritz_tr064/fritz_tr064.dart';
import 'package:phoneblock_mobile/fritzbox/call_list_import.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_models.dart';
import 'package:phoneblock_mobile/fritzbox/phone_number_utils.dart';

/// Issue #361: calls missing from the call list after the Fritz!Box sync.
///
/// The fixtures are synthetic call lists with the element structure of a real
/// Fritz!Box list, parsed by the library's own parser.
void main() {
  /// One `<Call>` element. [type]: 1 incoming, 2 missed, 3 outgoing,
  /// 9 active incoming, 10 rejected, 11 active outgoing.
  String call(
    int id, {
    int type = 2,
    String caller = '030123456',
    String name = '',
    String date = '28.11.26 10:00',
    String duration = '0:00',
    String device = 'Telefon 1',
  }) {
    final outgoing = type == 3 || type == 11;
    final number = outgoing ? '' : caller;
    return '''
<Call>
<Id>$id</Id>
<Type>$type</Type>
<Caller>$number</Caller>
<Called>${outgoing ? caller : 'SIP: 987654'}</Called>
${outgoing ? '<CalledNumber>$caller</CalledNumber>' : '<CallerNumber>$number</CallerNumber>'}
<Name>$name</Name>
<Numbertype>sip</Numbertype>
<Device>$device</Device>
<Port>10</Port>
<Date>$date</Date>
<Duration>$duration</Duration>
<Count></Count>
<Path />
</Call>''';
  }

  /// Parses a call list through the fritz_tr064 fetch path.
  Future<List<CallListEntry>> parse(List<String> calls) {
    final xml = '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<root><timestamp>1795000000</timestamp>\n'
        '${calls.reversed.join('\n')}\n</root>';
    final service = OnTelService(
      description: ServiceDescription(
        serviceType: 'urn:dslforum-org:service:X_AVM-DE_OnTel:1',
        serviceId: 'urn:X_AVM-DE_OnTel-com:serviceId:X_AVM-DE_OnTel1',
        controlUrl: '/upnp/control/x_contact',
        scpdUrl: '/x_contactSCPD.xml',
      ),
      callAction: (serviceType, controlUrl, action, args) async =>
          {'NewCallListURL': 'http://fritz.box:49000/calllist.lua?sid=x'},
      fetchUrl: (url) async => xml,
    );
    return service.getCallListEntries();
  }

  CallListImportPlan plan(
    List<CallListEntry> entries, {
    int? watermark,
    bool logKnownCallers = true,
    Set<String> imported = const {},
  }) =>
      planCallListImport(
        entries,
        watermark: watermark,
        logKnownCallers: logKnownCallers,
        importedFritzboxIds: imported,
        countryCode: '49',
        intlPrefix: '00',
        trunkPrefix: '0',
      );

  List<String> importedIds(CallListImportPlan p) =>
      p.toImport.map((c) => c.call.fritzboxId!).toList();

  test('imports the entries above the watermark', () async {
    final entries = await parse([
      for (var id = 98; id <= 103; id++) call(id, caller: '03012345$id'),
    ]);
    final p = plan(entries, watermark: 100);
    expect(importedIds(p), ['101', '102', '103']);
    expect(p.watermark, 103);
    expect(p.belowWatermark, 3);
    expect(p.listReset, isFalse);
    expect(p.toImport.first.call.phoneNumber, '+493012345101');
    expect(p.toImport.first.call.label, '03012345101');
    expect(p.toImport.every((c) => c.lookup), isTrue);
  });

  test('a call still active at the sync is imported later with final data',
      () async {
    // Sync 1: call 101 started 10:00 and is still running, call 102 started
    // 10:05 and was missed (has ended).
    final sync1 = await parse([
      call(100, date: '28.11.26 09:00'),
      call(101, type: 9, caller: '030111111', date: '28.11.26 10:00'),
      call(102, type: 2, caller: '030222222', date: '28.11.26 10:05'),
    ]);
    final p1 = plan(sync1, watermark: 100);
    expect(importedIds(p1), ['102']);
    expect(p1.watermark, 100);
    expect(p1.skipped.single.id, 101);
    expect(p1.skipped.single.reason, CallSkipReason.stillActive);

    // Sync 2: call 101 has ended.
    final sync2 = await parse([
      call(100, date: '28.11.26 09:00'),
      call(101,
          type: 1, caller: '030111111', date: '28.11.26 10:00', duration: '0:21'),
      call(102, type: 2, caller: '030222222', date: '28.11.26 10:05'),
    ]);
    final p2 = plan(sync2, watermark: p1.watermark, imported: {'102'});
    expect(importedIds(p2), ['101']);
    final imported = p2.toImport.single.call;
    expect(imported.callType, FritzBoxCallType.incoming);
    expect(imported.duration, 21 * 60);
    expect(imported.timestamp, DateTime(2026, 11, 28, 10, 0));
    expect(p2.skipped.single.id, 102);
    expect(p2.skipped.single.reason, CallSkipReason.alreadyImported);
    expect(p2.watermark, 102);
  });

  test('a call in the minute of the previous sync is imported', () async {
    // The previous sync ran at 10:00:40 and handled call 100 (10:00); call 101
    // also started at 10:00, after the sync. No time filter applies anymore.
    final entries = await parse([
      call(100, date: '28.11.26 10:00'),
      call(101, caller: '030777777', date: '28.11.26 10:00'),
    ]);
    final p = plan(entries, watermark: 100);
    expect(importedIds(p), ['101']);
    expect(p.watermark, 101);
  });

  group('known caller', () {
    test('imported with its name as label and without lookup when logged',
        () async {
      final entries = await parse([call(101, name: 'Erika Muster')]);
      final p = plan(entries, watermark: 100, logKnownCallers: true);
      final imported = p.toImport.single;
      expect(imported.call.label, 'Erika Muster');
      expect(imported.call.displayName, 'Erika Muster');
      expect(imported.lookup, isFalse);
      expect(p.watermark, 101);
    });

    test('skipped with reason when not logged', () async {
      final entries = await parse([call(101, name: 'Erika Muster')]);
      final p = plan(entries, watermark: 100, logKnownCallers: false);
      expect(p.toImport, isEmpty);
      expect(p.skipped.single.reason, CallSkipReason.knownContact);
      expect(p.skipped.single.maskedNumber, '******456');
      expect(p.watermark, 101);
    });
  });

  test('a SPAM: name is imported and looked up regardless of the option',
      () async {
    for (final logKnown in [true, false]) {
      final entries = await parse([call(101, name: 'SPAM: Werbung')]);
      final p = plan(entries, watermark: 100, logKnownCallers: logKnown);
      final imported = p.toImport.single;
      expect(imported.lookup, isTrue);
      expect(imported.call.label, isNot('SPAM: Werbung'));
    }
  });

  test('a list reset on the box drops the watermark', () async {
    final entries = await parse([
      for (var id = 1; id <= 5; id++) call(id, caller: '03098765$id'),
    ]);
    final p = plan(entries, watermark: 1471);
    expect(p.listReset, isTrue);
    expect(importedIds(p), ['1', '2', '3', '4', '5']);
    expect(p.watermark, 5);
  });

  test('an empty list keeps the watermark', () async {
    final p = plan(await parse([]), watermark: 1471);
    expect(p.listReset, isFalse);
    expect(p.watermark, 1471);
  });

  test('upgrade without watermark deduplicates by Fritz!Box ID', () async {
    final entries = await parse([
      call(200, caller: '030100200'),
      call(201, caller: '030100201'),
      call(202, caller: '030100202'),
    ]);
    final p = plan(entries, watermark: null, imported: {'200', '201'});
    expect(importedIds(p), ['202']);
    expect(p.skipped.map((s) => s.reason),
        everyElement(CallSkipReason.alreadyImported));
    expect(p.watermark, 202);
  });

  test('outgoing calls are never imported and do not hold the watermark',
      () async {
    final entries = await parse([
      call(101, type: 3),
      call(102, type: 11),
      call(103, type: 2, caller: '030333333'),
      call(104, type: 3),
    ]);
    final p = plan(entries, watermark: 100);
    expect(importedIds(p), ['103']);
    expect(
        p.skipped.where((s) => s.reason == CallSkipReason.outgoing).length, 3);
    expect(p.watermark, 104);
  });

  test('anonymous calls are skipped with reason', () async {
    final entries = await parse(
        [call(101, caller: '', name: 'Unbekannt', device: 'Anonym')]);
    final p = plan(entries, watermark: 100);
    expect(p.toImport, isEmpty);
    expect(p.skipped.single.reason, CallSkipReason.noNumber);
    expect(p.watermark, 101);
  });

  test('rejected calls are marked as blocked', () async {
    final p = plan(await parse([call(101, type: 10)]), watermark: 100);
    expect(p.toImport.single.call.wasBlocked, isTrue);
    expect(p.toImport.single.call.callType, FritzBoxCallType.rejected);
  });

  test('phone numbers are masked for the log', () {
    expect(maskPhoneNumber('030123456'), '******456');
    expect(maskPhoneNumber('+4930123456'), '+*******456');
    expect(maskPhoneNumber('12'), '12');
    expect(maskPhoneNumber(''), '(none)');
  });
  test('probe: several syncs with active, outgoing, known and spam calls import each incoming call once', () async {
    int? watermark = 99;
    final imported = <String, CallToImport>{};
    Future<void> sync(List<String> calls) async {
      final p = plan(await parse(calls), watermark: watermark, imported: imported.keys.toSet());
      for (final c in p.toImport) {
        expect(imported.containsKey(c.call.fritzboxId), isFalse, reason: 'duplicate ${c.call.fritzboxId}');
        imported[c.call.fritzboxId!] = c;
      }
      if (watermark != null && !p.listReset) {
        expect(p.watermark! >= watermark!, isTrue, reason: 'watermark must not go backwards');
      }
      watermark = p.watermark;
    }

    await sync([
      call(100, type: 2, caller: '030100100'),
      call(101, type: 9, caller: '030101101', date: '28.11.26 10:00'),
      call(102, type: 3, caller: '030102102'),
      call(103, type: 11, caller: '030103103'),
      call(104, type: 1, caller: '030104104', name: 'Erika Muster'),
    ]);
    expect(imported.keys.toSet(), {'100', '104'});

    await sync([
      call(100, type: 2, caller: '030100100'),
      call(101, type: 1, caller: '030101101', date: '28.11.26 10:00', duration: '0:21'),
      call(102, type: 3, caller: '030102102'),
      call(103, type: 3, caller: '030103103'),
      call(104, type: 1, caller: '030104104', name: 'Erika Muster'),
      call(105, type: 10, caller: '030105105', name: 'SPAM: Werbung'),
      call(106, type: 9, caller: '030106106'),
    ]);
    expect(imported.keys.toSet(), {'100', '101', '104', '105'});
    expect(imported['101']!.call.callType, FritzBoxCallType.incoming);
    expect(imported['104']!.lookup, isFalse);
    expect(imported['105']!.lookup, isTrue);
    expect(imported['105']!.call.wasBlocked, isTrue);

    await sync([
      call(105, type: 10, caller: '030105105', name: 'SPAM: Werbung'),
      call(106, type: 1, caller: '030106106'),
      call(107, type: 3, caller: '030107107'),
    ]);
    expect(imported.keys.toSet(), {'100', '101', '104', '105', '106'});
    expect(watermark, 107);
  });
}
