import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:path/path.dart' as p;
import 'package:phoneblock_mobile/fritzbox/call_list_filter.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_models.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_storage.dart';
import 'package:phoneblock_mobile/fritzbox/screens/call_list_filter_screen.dart';
import 'package:phoneblock_mobile/l10n/app_localizations.dart';
import 'package:phoneblock_mobile/logging/app_logger.dart';
import 'package:phoneblock_mobile/main.dart' show MainScreen;
import 'package:phoneblock_mobile/storage.dart';

import 'support/fake_fritzbox.dart';
import 'support/sqlite_channel.dart';

/// Issue #294: filter the call list by called number and answering device.
///
/// All numbers and names are synthetic.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  ScreenedCall mobileCall({String label = 'Handy-Anruf'}) => ScreenedCall(
        phoneNumber: '+4930111111',
        timestamp: DateTime(2026, 11, 28, 9),
        wasBlocked: false,
        votes: 0,
        label: label,
      );

  ScreenedCall boxCall({
    String? called = '987654',
    String? device = 'Wohnzimmer',
    FritzBoxCallType type = FritzBoxCallType.incoming,
    String label = 'Box-Anruf',
    String? fritzboxId,
  }) =>
      ScreenedCall(
        phoneNumber: '+4930222222',
        timestamp: DateTime(2026, 11, 28, 10),
        wasBlocked: type == FritzBoxCallType.rejected,
        votes: 0,
        label: label,
        source: CallSource.fritzbox,
        device: device,
        callType: type,
        calledNumber: called,
        fritzboxId: fritzboxId,
      );

  group('predicate', () {
    test('no selection shows every call', () {
      const f = CallListFilter.all;
      expect(f.isActive, isFalse);
      expect(f.matches(mobileCall()), isTrue);
      expect(f.matches(boxCall()), isTrue);
      expect(f.matches(boxCall(called: null, device: null)), isTrue);
    });

    test('entries of a group are alternatives', () {
      const f = CallListFilter(byDevices: {'Wohnzimmer', 'PhoneBlock'});
      expect(f.matches(boxCall(device: 'Wohnzimmer')), isTrue);
      expect(f.matches(boxCall(device: 'PhoneBlock')), isTrue);
      expect(f.matches(boxCall(device: 'Tablet')), isFalse);

      const g = CallListFilter(toNumbers: {'987654', '987655'});
      expect(g.matches(boxCall(called: '987654')), isTrue);
      expect(g.matches(boxCall(called: '987655')), isTrue);
      expect(g.matches(boxCall(called: '987656')), isFalse);
    });

    test('both groups must match', () {
      const f = CallListFilter(toNumbers: {'987654'}, byDevices: {'Wohnzimmer'});
      expect(f.matches(boxCall(called: '987654', device: 'Wohnzimmer')), isTrue);
      expect(f.matches(boxCall(called: '987655', device: 'Wohnzimmer')), isFalse);
      expect(f.matches(boxCall(called: '987654', device: 'Tablet')), isFalse);
    });

    test('an empty group matches every call', () {
      const f = CallListFilter(byDevices: {'Wohnzimmer'});
      expect(f.toAll, isTrue);
      expect(f.matches(boxCall(called: '111111')), isTrue);
      expect(f.matches(boxCall(called: null)), isTrue);
    });

    test('"Verpasst" selects missed and rejected calls', () {
      const f = CallListFilter(byMissed: true);
      expect(f.matches(boxCall(type: FritzBoxCallType.missed, device: null)), isTrue);
      expect(f.matches(boxCall(type: FritzBoxCallType.rejected)), isTrue);
      expect(f.matches(boxCall(type: FritzBoxCallType.incoming)), isFalse);
      expect(f.matches(mobileCall()), isFalse);
      // A missed call is "Verpasst", not taken by the device it rang on.
      const g = CallListFilter(byDevices: {'Wohnzimmer'});
      expect(g.matches(boxCall(type: FritzBoxCallType.missed)), isFalse);
    });

    test('"Handy" selects the calls screened on the phone in both groups', () {
      const to = CallListFilter(toMobile: true);
      expect(to.matches(mobileCall()), isTrue);
      expect(to.matches(boxCall()), isFalse);
      const by = CallListFilter(byMobile: true);
      expect(by.matches(mobileCall()), isTrue);
      expect(by.matches(boxCall()), isFalse);
      const numberOnly = CallListFilter(toNumbers: {'987654'});
      expect(numberOnly.matches(mobileCall()), isFalse);
    });

    test('a call without called number matches "Anrufe an" only when empty', () {
      final old = boxCall(called: null);
      expect(const CallListFilter(toNumbers: {'987654'}).matches(old), isFalse);
      expect(const CallListFilter(toMobile: true).matches(old), isFalse);
      expect(const CallListFilter(byDevices: {'Wohnzimmer'}).matches(old), isTrue);
    });

    test('stored form round-trips, "all" is stored as null', () {
      expect(CallListFilter.all.encode(), isNull);
      expect(CallListFilter.decode(null).isActive, isFalse);
      expect(CallListFilter.decode('garbage').isActive, isFalse);
      const f = CallListFilter(
          toMobile: true, toNumbers: {'987654'}, byMissed: true, byDevices: {'Tablet'});
      final back = CallListFilter.decode(f.encode());
      expect(back.toMobile, isTrue);
      expect(back.toNumbers, {'987654'});
      expect(back.byMobile, isFalse);
      expect(back.byMissed, isTrue);
      expect(back.byDevices, {'Tablet'});
    });
  });

  group('normalization', () {
    test('the type tag of a phone port name is dropped', () {
      expect(normalizeDeviceName('DECT: Wohnzimmer'), 'Wohnzimmer');
      expect(normalizeDeviceName('ISDN: DECT Rundruf'), 'DECT Rundruf');
      expect(normalizeDeviceName('FON1: Telefon'), 'Telefon');
      expect(normalizeDeviceName('Wohnzimmer'), 'Wohnzimmer');
      expect(normalizeDeviceName('Oma: Mobil'), 'Oma: Mobil');
      expect(deviceKey('DECT: Wohnzimmer'), deviceKey('wohnzimmer'));
    });

    test('a phone port name matches the call list device', () {
      const f = CallListFilter(byDevices: {'DECT: Wohnzimmer'});
      expect(f.matches(boxCall(device: 'Wohnzimmer')), isTrue);
    });

    test('own numbers match with and without area or country code', () {
      expect(normalizeOwnNumber('SIP: 987654'), '987654');
      expect(normalizeOwnNumber('  '), isNull);
      expect(sameOwnNumber('987654', 'SIP: 987654'), isTrue);
      expect(sameOwnNumber('987654', '02123987654'), isTrue);
      expect(sameOwnNumber('+492123987654', '02123987654'), isTrue);
      expect(sameOwnNumber('987654', '987655'), isFalse);
      expect(sameOwnNumber('54', '987654'), isFalse, reason: 'too short');
    });
  });

  group('selection entries from the box', () {
    const logChannel = MethodChannel('phoneblock/log');
    setUp(() async {
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(logChannel, (call) async => null);
      AppLogger.resetForTest();
      await AppLogger.instance.init();
    });

    test('box answers: numbers, phones until 713, SIP devices, answering machines',
        () async {
      final box = FakeFritzBox(boxActions());
      final choices = await box.run(queryCallFilterChoices);

      expect(choices.complete, isTrue);
      expect(choices.numbers.map((n) => n.label),
          ['987654', '987655', 'Büro (987656)']);
      expect(choices.devices,
          ['DECT Rundruf', 'Wohnzimmer', 'PhoneBlock', 'Tablet', 'Anrufbeantworter']);
      expect(box.actionCalls.where((a) => a == 'X_AVM-DE_GetPhonePort'), hasLength(3));
    });

    test('box refuses with 606: reason and entries from imported calls', () async {
      final box = FakeFritzBox({
        ...boxActions(),
        'X_AVM-DE_GetNumbers': (_) => const SoapFault(606, 'Action Not Authorized'),
      });
      final choices = await box.run(queryCallFilterChoices);
      expect(choices.complete, isFalse);
      expect(choices.refused, isTrue);
      expect(choices.numbers, isEmpty);
      expect(choices.devices, contains('Wohnzimmer'), reason: 'other queries still run');

      final options = buildFilterOptions(
        choices: choices,
        storedNumbers: ['987654', 'SIP: 987657'],
        storedDevices: ['Wohnzimmer', 'Arbeitszimmer'],
        filter: const CallListFilter(toNumbers: {'987658'}),
      );
      expect(options.numbers.map((o) => o.value), ['987654', '987657', '987658']);
      expect(options.numbers.any((o) => o.gone), isFalse,
          reason: 'nothing is known about the selection');
      expect(options.devices.map((o) => o.value), contains('Arbeitszimmer'));
      expect(options.devices.where((o) => o.value == 'Wohnzimmer'), hasLength(1));
    });

    test('a selected entry the box no longer has stays visible, marked', () async {
      final choices = await FakeFritzBox(boxActions()).run(queryCallFilterChoices);
      final options = buildFilterOptions(
        choices: choices,
        storedNumbers: ['987657'],
        storedDevices: ['Arbeitszimmer'],
        filter: const CallListFilter(
            toNumbers: {'02123987654', '987659'}, byDevices: {'Küche', 'Wohnzimmer'}),
      );
      expect(options.numbers.map((o) => o.value),
          ['987654', '987655', '987656', '987659'],
          reason: '02123987654 is 987654; stored entries only as fallback');
      expect(options.numbers.last.gone, isTrue);
      expect(options.numbers.where((o) => o.gone), hasLength(1));
      expect(options.devices.where((o) => o.gone).map((o) => o.value), ['Küche']);
    });

    test('box not reachable', () async {
      final choices = await FakeFritzBox(boxActions(), unreachable: true)
          .run(queryCallFilterChoices);
      expect(choices.complete, isFalse);
      expect(choices.refused, isFalse);
    });
  });

  group('with database', () {
    late Directory dir;
    late SqliteChannel sqlite;
    const logChannel = MethodChannel('phoneblock/log');
    const secureStorageChannel =
        MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
    const callChecker = MethodChannel('de.haumacher.phoneblock_mobile/call_checker');

    // One directory for all tests: sqflite caches the databases path.
    setUpAll(() => dir = Directory.systemTemp.createTempSync('pb-294-'));
    tearDownAll(() => dir.deleteSync(recursive: true));

    setUp(() async {
      sqlite = SqliteChannel(dir)..install();
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(logChannel, (call) async => null);
      messenger.setMockMethodCallHandler(secureStorageChannel, (call) async => null);
      messenger.setMockMethodCallHandler(callChecker, (call) async => null);
      AppLogger.resetForTest();
      await AppLogger.instance.init();
    });

    tearDown(() async {
      await ScreenedCallsDatabase.resetForTest();
      sqlite.uninstall();
      for (final f in dir.listSync()) {
        f.deleteSync(recursive: true);
      }
    });

    test('schema 15 -> 16 keeps the calls, the filter defaults to "all"', () async {
      final file = p.join(dir.path, 'screened_calls.db');
      SqliteChannel.runOnFile(file, [
        ...schema15,
        "INSERT INTO screened_calls (phoneNumber, timestamp, wasBlocked, votes, source) "
            "VALUES ('+4930111111', 1795000000000, 0, 0, 'mobile')",
        "INSERT INTO screened_calls (phoneNumber, timestamp, wasBlocked, votes, source, "
            "device, fritzboxId, callType) "
            "VALUES ('+4930222222', 1795000060000, 0, 0, 'fritzbox', 'Wohnzimmer', '101', 1)",
        "INSERT INTO fritzbox_config (id, host, last_call_id, log_known_callers, "
            "created_at, updated_at) VALUES (1, 'fritz.box', 101, 0, 1, 1)",
        'PRAGMA user_version = 15',
      ]);

      final calls = await ScreenedCallsDatabase.instance.getAllScreenedCalls();
      expect(calls, hasLength(2));
      expect(calls.map((c) => c.calledNumber), everyElement(isNull));
      expect(calls.first.device, 'Wohnzimmer');

      final config = await FritzBoxStorage.instance.getConfig();
      expect(config!.callListFilter.isActive, isFalse);
      expect(config.logKnownCallers, isFalse);
      expect(config.lastCallId, 101);

      expect(SqliteChannel.queryFile(file, 'PRAGMA user_version').single.values.single, 16);

      // The new columns are written and read.
      await ScreenedCallsDatabase.instance
          .insertScreenedCall(boxCall(called: '987654', fritzboxId: '102'));
      expect(await ScreenedCallsDatabase.instance.getFritzBoxCalledNumbers(), ['987654']);
      await FritzBoxStorage.instance
          .updateConfig(callListFilter: const CallListFilter(byMissed: true));
      expect((await FritzBoxStorage.instance.getConfig())!.callListFilter.byMissed, isTrue);
      expect((await FritzBoxStorage.instance.getConfig())!.logKnownCallers, isFalse);
    });

    Widget app(Widget home) => MaterialApp(
          locale: const Locale('de'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: home,
        );

    void bigView(WidgetTester tester) {
      tester.view.physicalSize = const Size(1200, 2400);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
    }

    Future<void> seed(WidgetTester tester, {CallListFilter? filter}) async {
      await tester.runAsync(() async {
        final db = ScreenedCallsDatabase.instance;
        await db.insertScreenedCall(mobileCall(label: 'Anruf Handy'));
        await db.insertScreenedCall(boxCall(
            called: '987654', device: 'Wohnzimmer', label: 'Anruf A', fritzboxId: '1'));
        await db.insertScreenedCall(boxCall(
            called: '987655', device: 'Arbeitszimmer', label: 'Anruf B', fritzboxId: '2'));
        await db.insertScreenedCall(boxCall(
            called: '987654',
            device: null,
            type: FritzBoxCallType.missed,
            label: 'Anruf C',
            fritzboxId: '3'));
        await db.insertScreenedCall(boxCall(
            called: null, device: 'Wohnzimmer', label: 'Anruf Alt', fritzboxId: '4'));
        await FritzBoxStorage.instance
            .updateConfig(host: 'fritz.box', callListFilter: filter);
      });
    }

    Future<void> settle(WidgetTester tester) async {
      for (var i = 0; i < 5; i++) {
        await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 20)));
        await tester.pump();
      }
    }

    testWidgets('filter screen: refused box shows the reason and stored entries',
        (tester) async {
      bigView(tester);
      await seed(tester, filter: const CallListFilter(byDevices: {'Küche'}));
      final choices = await tester.runAsync(() => FakeFritzBox({
            ...boxActions(),
            'X_AVM-DE_GetNumbers': (_) => const SoapFault(606, 'Action Not Authorized'),
          }).run(queryCallFilterChoices));

      await tester.pumpWidget(app(CallListFilterScreen(loadChoices: () async => choices!)));
      await settle(tester);

      expect(find.text('Die Fritz!Box hat die Abfrage der Rufnummern und Geräte abgelehnt.'),
          findsOneWidget);
      expect(find.text('Angeboten werden die Einträge aus bisherigen Anrufen.'), findsOneWidget);
      // Called numbers from imported calls, devices from the box and the calls.
      expect(find.text('987654'), findsOneWidget);
      expect(find.text('987655'), findsOneWidget);
      expect(find.text('Arbeitszimmer'), findsOneWidget);
      expect(find.text('PhoneBlock'), findsOneWidget);
      expect(find.text('Küche'), findsOneWidget);
      expect(find.text('Nicht mehr in der Fritz!Box'), findsNothing);
      expect(find.text('Handy'), findsNWidgets(2));
      expect(find.text('Verpasst'), findsOneWidget);

      // A selection is stored.
      await tester.tap(find.text('987655'));
      await settle(tester);
      final config = await tester.runAsync(FritzBoxStorage.instance.getConfig);
      expect(config!.callListFilter.toNumbers, {'987655'});
      expect(config.callListFilter.byDevices, {'Küche'});
    });

    testWidgets('filter screen: a selected device gone from the box is marked',
        (tester) async {
      bigView(tester);
      await seed(tester, filter: const CallListFilter(byDevices: {'Küche'}));
      final choices =
          await tester.runAsync(() => FakeFritzBox(boxActions()).run(queryCallFilterChoices));

      await tester.pumpWidget(app(CallListFilterScreen(loadChoices: () async => choices!)));
      await settle(tester);

      expect(find.byIcon(Icons.warning_amber), findsNothing);
      expect(find.text('Büro (987656)'), findsOneWidget);
      expect(find.text('Arbeitszimmer'), findsNothing, reason: 'no fallback needed');
      expect(find.text('Küche'), findsOneWidget);
      expect(find.text('Nicht mehr in der Fritz!Box'), findsOneWidget);

      await tester.tap(find.text('Küche'));
      await settle(tester);
      final config = await tester.runAsync(FritzBoxStorage.instance.getConfig);
      expect(config!.callListFilter.isActive, isFalse);
    });

    testWidgets('call list shows only matching calls and the filter indicator',
        (tester) async {
      bigView(tester);
      await seed(tester, filter: const CallListFilter(toNumbers: {'987654'}));

      await tester.pumpWidget(app(const MainScreen()));
      await settle(tester);

      expect(find.text('Anruf A'), findsOneWidget);
      expect(find.text('Anruf C'), findsOneWidget);
      expect(find.text('Anruf B'), findsNothing);
      expect(find.text('Anruf Handy'), findsNothing);
      expect(find.text('Anruf Alt'), findsNothing, reason: 'no called number stored');
      expect(find.text('Gefiltert · Filter ändern'), findsOneWidget);

      // The indicator leads to the filter screen; the box is not reachable.
      await tester.tap(find.text('Gefiltert · Filter ändern'));
      await settle(tester);
      expect(find.text('Anrufliste filtern'), findsOneWidget);
      expect(
          find.text('Rufnummern und Geräte der Fritz!Box konnten nicht abgefragt werden.'),
          findsOneWidget);
    });

    testWidgets('a filter hiding every call says so', (tester) async {
      bigView(tester);
      await seed(tester, filter: const CallListFilter(byDevices: {'Küche'}));

      await tester.pumpWidget(app(const MainScreen()));
      await settle(tester);

      expect(find.text('Keine Anrufe für diesen Filter.'), findsOneWidget);
      expect(find.text('Gefiltert · Filter ändern'), findsOneWidget);
      expect(find.text('Anruf A'), findsNothing);
    });

    testWidgets('without filter the call list shows every call and no indicator',
        (tester) async {
      bigView(tester);
      await seed(tester);

      await tester.pumpWidget(app(const MainScreen()));
      await settle(tester);

      for (final label in ['Anruf Handy', 'Anruf A', 'Anruf B', 'Anruf C', 'Anruf Alt']) {
        expect(find.text(label), findsOneWidget, reason: label);
      }
      expect(find.text('Gefiltert · Filter ändern'), findsNothing);
    });
  });
}

/// TR-064 answers of a box with three own numbers, two phone ports, two SIP
/// devices and one configured answering machine.
Map<String, Object Function(Map<String, String>)> boxActions() => {
      'X_AVM-DE_GetNumbers': (_) => {
            'NewNumberList': '<?xml version="1.0" encoding="utf-8"?><List>'
                '<Item><Number>987654</Number><Type>eVoIP</Type><Index>0</Index><Name></Name></Item>'
                '<Item><Number>987655</Number><Type>eVoIP</Type><Index>1</Index><Name></Name></Item>'
                '<Item><Number>987656</Number><Type>eVoIP</Type><Index>2</Index><Name>Büro</Name></Item>'
                '</List>',
          },
      'X_AVM-DE_GetPhonePort': (args) {
        final names = {'1': 'ISDN: DECT Rundruf', '2': 'DECT: Wohnzimmer'};
        final name = names[args['NewIndex']];
        if (name == null) return const SoapFault(713, 'SpecifiedArrayIndexInvalid');
        return {'NewX_AVM-DE_PhoneName': name};
      },
      'X_AVM-DE_GetClients': (_) => {
            'NewX_AVM-DE_ClientList': '<?xml version="1.0" encoding="utf-8"?><List>'
                '<Item><X_AVM-DE_ClientIndex>0</X_AVM-DE_ClientIndex>'
                '<X_AVM-DE_ClientUsername>pbbot</X_AVM-DE_ClientUsername>'
                '<X_AVM-DE_PhoneName>PhoneBlock</X_AVM-DE_PhoneName>'
                '<X_AVM-DE_InternalNumber>620</X_AVM-DE_InternalNumber></Item>'
                '<Item><X_AVM-DE_ClientIndex>1</X_AVM-DE_ClientIndex>'
                '<X_AVM-DE_ClientUsername>tablet</X_AVM-DE_ClientUsername>'
                '<X_AVM-DE_PhoneName>Tablet</X_AVM-DE_PhoneName>'
                '<X_AVM-DE_InternalNumber>621</X_AVM-DE_InternalNumber></Item>'
                '</List>',
          },
      'GetList': (_) => {
            'NewTAMList': '<?xml version="1.0" encoding="utf-8"?><List>'
                '<TAMRunning>1</TAMRunning><Stick>0</Stick><Status>0</Status><Capacity>0</Capacity>'
                '<Item><Index>0</Index><Display>1</Display><Enable>1</Enable><Name>Anrufbeantworter</Name></Item>'
                '<Item><Index>1</Index><Display>0</Display><Enable>0</Enable><Name>Anrufbeantworter 2</Name></Item>'
                '</List>',
          },
    };

/// The tables of schema version 15 touched by the migration to 16.
const schema15 = [
  '''CREATE TABLE screened_calls (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        phoneNumber TEXT NOT NULL,
        timestamp INTEGER NOT NULL,
        wasBlocked INTEGER NOT NULL,
        votes INTEGER NOT NULL,
        votesWildcard INTEGER NOT NULL DEFAULT 0,
        rating TEXT,
        label TEXT,
        location TEXT,
        source TEXT NOT NULL DEFAULT 'mobile',
        duration INTEGER,
        device TEXT,
        fritzboxId TEXT UNIQUE,
        callType INTEGER,
        matchedWildcard TEXT,
        isPersonallyBlocked INTEGER NOT NULL DEFAULT 0
      )''',
  '''CREATE TABLE fritzbox_config (
        id INTEGER PRIMARY KEY CHECK (id = 1),
        host TEXT,
        fritzos_version TEXT,
        username TEXT,
        password TEXT,
        blocklist_mode TEXT DEFAULT 'none',
        answerbot_enabled INTEGER DEFAULT 0,
        answerbot_id INTEGER,
        last_fetch_timestamp INTEGER,
        blocklist_version TEXT,
        phonebook_id TEXT,
        sip_device_id TEXT,
        sip_username TEXT,
        country_code TEXT DEFAULT '49',
        intl_prefix TEXT DEFAULT '00',
        trunk_prefix TEXT DEFAULT '0',
        last_call_id INTEGER,
        log_known_callers INTEGER DEFAULT 1,
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL
      )''',
];
