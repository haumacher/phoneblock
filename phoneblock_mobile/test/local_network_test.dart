import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_discovery.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_models.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_rights.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_service.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_status.dart';
import 'package:phoneblock_mobile/fritzbox/local_network_access.dart';
import 'package:phoneblock_mobile/fritzbox/screens/fritzbox_wizard.dart';
import 'package:phoneblock_mobile/l10n/app_localizations.dart';
import 'package:phoneblock_mobile/l10n/app_localizations_de.dart';
import 'package:phoneblock_mobile/logging/app_logger.dart';

import 'support/fake_fritzbox.dart';
import 'support/fake_local_network.dart';

/// Issue #660 (Android 17 local network permission, mDNS discovery) and
/// issue #819 (manual Fritz!Box address).
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const logChannel = MethodChannel('phoneblock/log');
  const nativeChannel =
      MethodChannel('de.haumacher.phoneblock_mobile/call_checker');
  const secureStorageChannel =
      MethodChannel('plugins.it_nomads.com/flutter_secure_storage');

  final logs = <Map<Object?, Object?>>[];
  final l10n = AppLocalizationsDe();
  late FakeLocalNetworkAccess access;

  TestDefaultBinaryMessenger messenger() =>
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  setUp(() async {
    logs.clear();
    messenger().setMockMethodCallHandler(logChannel, (call) async {
      if (call.method == 'log') logs.add(call.arguments as Map<Object?, Object?>);
      return null;
    });
    AppLogger.resetForTest();
    await AppLogger.instance.init();
    access = FakeLocalNetworkAccess(granted: false);
    LocalNetworkAccess.instance = access;
  });

  tearDown(() {
    messenger().setMockMethodCallHandler(nativeChannel, null);
    messenger().setMockMethodCallHandler(secureStorageChannel, null);
    LocalNetworkAccess.instance = LocalNetworkAccess();
  });

  List<String> infoLogs() => logs
      .where((l) => l['level'] == 'info')
      .map((l) => l['msg'] as String)
      .toList();

  group('LocalNetworkAccess over the method channel', () {
    setUp(() => LocalNetworkAccess.instance = LocalNetworkAccess());

    test('reports what the native side reports', () async {
      var nativeGranted = false;
      final calls = <String>[];
      messenger().setMockMethodCallHandler(nativeChannel, (call) async {
        calls.add(call.method);
        switch (call.method) {
          case 'checkLocalNetworkPermission':
            return nativeGranted;
          case 'requestLocalNetworkPermission':
            nativeGranted = true;
            return true;
        }
        return null;
      });

      final access = LocalNetworkAccess.instance;
      expect(await access.isGranted(), isFalse);
      await expectLater(access.ensureGranted(),
          throwsA(isA<LocalNetworkAccessDeniedException>()));
      expect(await access.request(), isTrue);
      expect(await access.isGranted(), isTrue);
      await access.ensureGranted();
      await access.openAppSettings();
      expect(calls, contains('openAppSettings'));
    });

    test('without the native side (background isolate) not granted', () async {
      expect(await LocalNetworkAccess.instance.isGranted(), isFalse);
    });

    test('denial message', () {
      expect(refusalMessage(l10n, LocalNetworkAccessDeniedException()),
          'Zugriff aufs Heimnetz nicht erlaubt');
      expect(refusalMessage(l10n, Exception('timeout')), isNull);
    });
  });

  group('FritzBoxService', () {
    late Map<String, String> secure;
    late List<String> secureDeletes;

    setUp(() {
      secure = {
        'fritzbox_host': '10.0.0.4',
        'fritzbox_username': 'pb',
        'fritzbox_password': 'secret',
      };
      secureDeletes = [];
      messenger().setMockMethodCallHandler(secureStorageChannel, (call) async {
        final key = (call.arguments as Map)['key'] as String?;
        switch (call.method) {
          case 'read':
            return secure[key];
          case 'delete':
            secureDeletes.add(key!);
            return null;
        }
        return null;
      });
    });

    test('denied: no LAN call, typed state, stored host kept', () async {
      final requests = <Uri>[];
      final service = FritzBoxService.instance;

      await http.runWithClient(() async {
        await service.initialize();
        expect(service.connectionState, FritzBoxConnectionState.noLocalNetworkAccess);
        expect(await service.checkConnection(), isFalse);
        expect(service.connectionState, FritzBoxConnectionState.noLocalNetworkAccess);
        expect(service.isConfigured, isTrue);
        expect(await service.getDefaultUsername('10.0.0.4'), isNull);
        expect(await service.syncCallList(), isEmpty);
      }, () => MockClient((request) async {
            requests.add(request.url);
            return http.Response('', 404);
          }));

      expect(requests, isEmpty, reason: 'no connection attempt into the LAN');
      expect(secureDeletes, isEmpty, reason: 'the stored Fritz!Box stays');
      expect(infoLogs(), contains('Call list sync skipped: no access to the local network'));
      expect(fritzBoxStatus(service.connectionState, CardDavStatus.notConfigured),
          FritzBoxStatus.noLocalNetworkAccess);
      expect(fritzBoxStatusText(l10n, FritzBoxStatus.noLocalNetworkAccess),
          'Zugriff aufs Heimnetz nicht erlaubt');

      // Granted later: the stored host is used as is, no new search.
      access.granted = true;
      await http.runWithClient(() => service.checkConnection(),
          () => MockClient((request) async {
                requests.add(request.url);
                return http.Response('', 404);
              }));
      expect(requests.map((u) => u.host).toSet(), {'10.0.0.4'});
      expect(service.connectionState, FritzBoxConnectionState.offline);
    });

    test('revoked after connecting: actions refused with the typed error', () async {
      access.granted = true;
      final box = FakeFritzBox({});
      await box.run((client) async {
        final service = FritzBoxService.forTest(client);
        expect(service.isConnected, isTrue);

        access.granted = false;
        final error = await captureError(() => service.deleteSipClient('pb-bot'));
        expect(error, isA<LocalNetworkAccessDeniedException>());
        expect(refusalMessage(l10n, error!), l10n.localNetworkDenied);
        expect(box.actionCalls, isEmpty);

        expect(await service.checkConnection(), isFalse);
        expect(service.connectionState, FritzBoxConnectionState.noLocalNetworkAccess);
        expect(await service.syncCallList(), isEmpty);
      });
    });
  });

  group('discovery', () {
    FritzBoxDeviceInfo box(String host, String serial, [String model = 'FRITZ!Box 6670 Cable']) =>
        FritzBoxDeviceInfo(host: host, modelName: model, serialNumber: serial);

    test('mDNS hosts first, then fixed candidates; one box under several addresses listed once',
        () async {
      final probed = <String>[];
      final discovery = FritzBoxDiscovery(
        browseMdns: () async => [
          ['192.168.178.1', 'fritz.box'],
        ],
        probe: (host) async {
          probed.add(host);
          return box(host, 'AABBCCDDEEFF');
        },
      );

      final found = await discovery.discoverAll();

      expect(probed, ['192.168.178.1', 'fritz.box'], reason: 'no host probed twice');
      expect(found.map((d) => d.host), ['192.168.178.1']);
    });

    test('mesh: Fritz!Box and repeater are both offered', () async {
      final discovery = FritzBoxDiscovery(
        browseMdns: () async => [
          ['10.0.0.4'],
          ['10.0.0.9'],
        ],
        probe: (host) async => switch (host) {
          '10.0.0.4' || 'fritz.box' => box(host, 'AABBCCDDEEFF'),
          '10.0.0.9' => box(host, '112233445566', 'FRITZ!Repeater 1200'),
          _ => null,
        },
      );

      final found = await discovery.discoverAll();

      expect(found.map((d) => d.host), ['10.0.0.4', '10.0.0.9']);
    });

    test('without mDNS the fixed candidates are tried', () async {
      final probed = <String>[];
      final discovery = FritzBoxDiscovery(
        browseMdns: () async => const [],
        probe: (host) async {
          probed.add(host);
          return host == 'fritz.box' ? box(host, 'AABBCCDDEEFF') : null;
        },
      );

      final found = await discovery.discoverAll();

      expect(probed, FritzBoxDiscovery.fixedHosts);
      expect(found.single.host, 'fritz.box');
    });

    test('device without serial number is identified by its host', () async {
      final discovery = FritzBoxDiscovery(
        browseMdns: () async => const [],
        probe: (host) async => FritzBoxDeviceInfo(host: host),
      );

      expect((await discovery.discoverAll()).map((d) => d.host),
          FritzBoxDiscovery.fixedHosts);
    });

    test('mDNS search over the method channel', () async {
      messenger().setMockMethodCallHandler(nativeChannel, (call) async {
        expect(call.method, 'discoverFritzBoxes');
        return [
          {'name': '192-168-178-1', 'hosts': ['192.168.178.1', 'fritz.box']},
          {'name': 'empty', 'hosts': <String>[]},
        ];
      });
      expect(await browseMdnsNative(), [
        ['192.168.178.1', 'fritz.box'],
      ]);
    });

    test('failed mDNS search yields nothing', () async {
      messenger().setMockMethodCallHandler(nativeChannel, (call) async {
        throw PlatformException(code: 'nsd');
      });
      expect(await browseMdnsNative(), isEmpty);
    });
  });

  group('manual address', () {
    test('accepted forms', () {
      expect(normalizeHostInput('10.0.0.4'), '10.0.0.4');
      expect(normalizeHostInput(' 192.168.178.1 '), '192.168.178.1');
      expect(normalizeHostInput('fritz.box'), 'fritz.box');
      expect(normalizeHostInput('my-router'), 'my-router');
      expect(normalizeHostInput('fd00::4'), '[fd00::4]');
      expect(normalizeHostInput('[fd00::4]'), '[fd00::4]');
      expect(normalizeHostInput('2001:db8::1'), '[2001:db8::1]');
    });

    test('rejected input', () {
      for (final input in [
        '',
        '   ',
        '10.0.4',
        '999.0.0.1',
        '10.0.0.4:49000',
        'http://fritz.box',
        'fritz box',
        'fritz..box',
        '-fritz.box',
        'fritz.box/',
        '[fd00::4',
        'fd00:::4:::1',
        '[fritz.box]',
        'fe80::1%wlan0',
        'ä.box',
      ]) {
        expect(normalizeHostInput(input), isNull, reason: input);
      }
    });
  });

  group('wizard', () {
    late List<String> probed;
    late int searches;

    FritzBoxDiscovery discoveryOf(Map<String, FritzBoxDeviceInfo> devices,
        {List<List<String>> mdns = const []}) {
      return FritzBoxDiscovery(
        browseMdns: () async {
          searches++;
          return mdns;
        },
        probe: (host) async {
          probed.add(host);
          return devices[host];
        },
      );
    }

    setUp(() {
      probed = [];
      searches = 0;
    });

    /// The app goes to the background (system settings) and comes back.
    void returnToApp(WidgetTester tester) {
      for (final state in [
        AppLifecycleState.inactive,
        AppLifecycleState.hidden,
        AppLifecycleState.paused,
        AppLifecycleState.hidden,
        AppLifecycleState.inactive,
        AppLifecycleState.resumed,
      ]) {
        tester.binding.handleAppLifecycleStateChanged(state);
      }
    }

    Widget wizard(FritzBoxDiscovery discovery) => MaterialApp(
          locale: const Locale('de'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: FritzBoxWizard(discovery: discovery),
        );

    final oneBox = {
      '192.168.178.1': FritzBoxDeviceInfo(
          host: '192.168.178.1', modelName: 'FRITZ!Box 6670 Cable', serialNumber: 'AABBCCDDEEFF'),
    };

    testWidgets('asks once with a rationale, then searches', (tester) async {
      access.grantOnRequest = true;
      await tester.pumpWidget(wizard(discoveryOf(oneBox)));
      await tester.pumpAndSettle();

      expect(find.text(l10n.localNetworkRationale), findsOneWidget);
      expect(searches, 0, reason: 'no search before the access is granted');
      expect(access.requests, 0, reason: 'the system dialog only on request');

      await tester.tap(find.text(l10n.localNetworkAllow));
      await tester.pumpAndSettle();

      expect(access.requests, 1);
      expect(searches, 1);
      expect(find.text('FRITZ!Box 6670 Cable'), findsOneWidget);
      expect(find.text(l10n.localNetworkRationale), findsNothing);
    });

    testWidgets('denied: names the reason and offers the settings', (tester) async {
      await tester.pumpWidget(wizard(discoveryOf(oneBox)));
      await tester.pumpAndSettle();

      await tester.tap(find.text(l10n.localNetworkAllow));
      await tester.pumpAndSettle();

      expect(find.text(l10n.localNetworkDenied), findsOneWidget);
      expect(searches, 0);

      await tester.tap(find.text(l10n.localNetworkOpenSettings));
      await tester.pumpAndSettle();
      expect(access.settingsOpened, 1);

      // Back from the system settings without granting: still denied.
      returnToApp(tester);
      await tester.pumpAndSettle();
      expect(searches, 0);
      expect(find.text(l10n.localNetworkDenied), findsOneWidget);

      // Granted in the system settings: the search starts on return.
      access.granted = true;
      returnToApp(tester);
      await tester.pumpAndSettle();
      expect(searches, 1);
      expect(find.text('FRITZ!Box 6670 Cable'), findsOneWidget);
      expect(access.requests, 1);
    });

    testWidgets('granted: no question, search right away', (tester) async {
      access.granted = true;
      await tester.pumpWidget(wizard(discoveryOf(oneBox)));
      await tester.pumpAndSettle();

      expect(find.text(l10n.localNetworkRationale), findsNothing);
      expect(access.requests, 0);
      expect(searches, 1);
      expect(find.text('FRITZ!Box 6670 Cable'), findsOneWidget);
      expect(find.text(l10n.fritzboxEnterAddressManually), findsOneWidget);
    });

    testWidgets('two devices: the user picks one', (tester) async {
      access.granted = true;
      await tester.pumpWidget(wizard(discoveryOf({
        '10.0.0.4': FritzBoxDeviceInfo(
            host: '10.0.0.4', modelName: 'FRITZ!Box 7590', serialNumber: 'AABBCCDDEEFF'),
        '10.0.0.9': FritzBoxDeviceInfo(
            host: '10.0.0.9', modelName: 'FRITZ!Repeater 1200', serialNumber: '112233445566'),
      }, mdns: [
        ['10.0.0.4'],
        ['10.0.0.9'],
      ])));
      await tester.pumpAndSettle();

      expect(find.text(l10n.fritzboxChooseDevice), findsOneWidget);
      expect(find.text('FRITZ!Box 7590'), findsOneWidget);
      expect(find.text('FRITZ!Repeater 1200'), findsOneWidget);

      ElevatedButton continueButton() => tester.widget<ElevatedButton>(
          find.widgetWithText(ElevatedButton, l10n.continue_));
      expect(continueButton().onPressed, isNull, reason: 'nothing chosen yet');

      await tester.tap(find.text('FRITZ!Repeater 1200'));
      await tester.pumpAndSettle();
      expect(continueButton().onPressed, isNotNull);
      expect(
          find.descendant(
              of: find.widgetWithText(ListTile, 'FRITZ!Repeater 1200'),
              matching: find.byIcon(Icons.check_circle)),
          findsOneWidget);
    });

    testWidgets('nothing found: manual entry with validation', (tester) async {
      access.granted = true;
      await tester.pumpWidget(wizard(discoveryOf({
        '10.0.0.4': FritzBoxDeviceInfo(host: '10.0.0.4', modelName: 'FRITZ!Box 7530'),
      })));
      await tester.pumpAndSettle();

      expect(find.text(l10n.fritzboxNotFoundDescription), findsOneWidget);
      expect(probed, FritzBoxDiscovery.fixedHosts);

      final field = find.byKey(FritzBoxWizard.hostInputKey);
      await tester.enterText(field, 'http://10.0.0.4');
      await tester.tap(find.text(l10n.fritzboxManualConnect));
      await tester.pumpAndSettle();
      expect(find.text(l10n.fritzboxInvalidAddress), findsOneWidget);
      expect(probed, FritzBoxDiscovery.fixedHosts, reason: 'invalid input is not probed');

      await tester.enterText(field, '10.0.0.5');
      await tester.tap(find.text(l10n.fritzboxManualConnect));
      await tester.pumpAndSettle();
      // The error is shown in the active step (the Stepper builds all steps).
      expect(find.text(l10n.fritzboxNotFound), findsWidgets);

      await tester.enterText(field, '10.0.0.4');
      await tester.tap(find.text(l10n.fritzboxManualConnect));
      await tester.pumpAndSettle();
      expect(probed.last, '10.0.0.4');
      expect(find.text('FRITZ!Box 7530'), findsOneWidget);
      expect(find.text(l10n.fritzboxInvalidAddress), findsNothing);
    });

    testWidgets('manual entry reachable although a box was found', (tester) async {
      access.granted = true;
      await tester.pumpWidget(wizard(discoveryOf(oneBox)));
      await tester.pumpAndSettle();

      await tester.tap(find.text(l10n.fritzboxEnterAddressManually));
      await tester.pumpAndSettle();

      expect(find.byKey(FritzBoxWizard.hostInputKey), findsOneWidget);
      expect(find.text(l10n.fritzboxNotFoundDescription), findsNothing);
    });
  });
}
