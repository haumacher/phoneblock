import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fritz_tr064/fritz_tr064.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_login.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_rights.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_service.dart';
import 'package:phoneblock_mobile/fritzbox/local_network_access.dart';
import 'package:phoneblock_mobile/l10n/app_localizations_de.dart';
import 'package:phoneblock_mobile/logging/app_logger.dart';

import 'support/fake_fritzbox.dart';
import 'support/fake_local_network.dart';

/// Issue #268: the app must work with a Fritz!Box user that only has the
/// right "Sprachnachrichten, Faxnachrichten, FRITZ!App Fon und Anrufliste",
/// and must name the missing right when the Fritz!Box refuses a change.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const logChannel = MethodChannel('phoneblock/log');
  const secureStorageChannel =
      MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  final logs = <Map<Object?, Object?>>[];

  setUp(() async {
    logs.clear();
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(logChannel, (call) async {
      if (call.method == 'log') {
        logs.add(call.arguments as Map<Object?, Object?>);
      }
      return null;
    });
    // No stored credentials: a reconnect attempt gives up immediately.
    messenger.setMockMethodCallHandler(secureStorageChannel, (call) async => null);
    AppLogger.resetForTest();
    await AppLogger.instance.init();
    LocalNetworkAccess.instance = FakeLocalNetworkAccess();
  });

  List<Map<Object?, Object?>> errorLogs() =>
      logs.where((l) => l['level'] == 'error').toList();

  final l10n = AppLocalizationsDe();

  group('connect', () {
    test('restricted user: DeviceInfo refused with 606, call list readable', () async {
      final box = FakeFritzBox({
        'GetInfo': (_) => const SoapFault(606, 'Action Not Authorized'),
        'GetCallList': (_) => {'NewCallListURL': 'http://fritz.box:49000/calllist.lua?sid=0'},
      });

      final info = await box.run((client) => verifyLogin(client));

      expect(info, isNotNull, reason: 'connected');
      expect(info!.modelName, 'FRITZ!Box 6670 Cable');
      expect(info.fritzosVersion, '284.08.25');
      expect(info.serialNumber, 'AABBCCDDEEFF');
      expect(box.descriptionFetches, 2,
          reason: 'Tr64Client.connect() plus the fallback read');
      expect(errorLogs(), isEmpty, reason: 'a 606 on DeviceInfo is no error');
    });

    test('full rights: device info from DeviceInfo, description not read again', () async {
      final box = FakeFritzBox({
        'GetInfo': (_) => {
              'NewModelName': 'FRITZ!Box 7590',
              'NewSoftwareVersion': '154.08.02',
              'NewSerialNumber': '112233445566',
            },
        'GetCallList': (_) => {'NewCallListURL': 'http://fritz.box:49000/calllist.lua?sid=0'},
      });

      final info = await box.run((client) => verifyLogin(client));

      expect(info!.modelName, 'FRITZ!Box 7590');
      expect(info.fritzosVersion, '154.08.02');
      expect(info.serialNumber, '112233445566');
      expect(box.descriptionFetches, 1);
      expect(errorLogs(), isEmpty);
    });

    test('call list refused as auth failure: not connected', () async {
      final box = FakeFritzBox({
        'GetInfo': (_) => const SoapFault(606, 'Action Not Authorized'),
        'GetCallList': (_) => const SoapFault(503, 'Auth. failed'),
      });

      final info = await box.run((client) => verifyLogin(client));

      expect(info, isNull);
      expect(errorLogs(), isNotEmpty);
    });

    test('wrong password: not connected', () async {
      final box = FakeFritzBox({}, wrongPassword: true);

      final info = await box.run((client) => verifyLogin(client));

      expect(info, isNull);
      expect(box.actionCalls, isNot(contains('GetCallList')));
    });
  });

  group('write actions', () {
    final phonebookReads = <String, Object Function(Map<String, String>)>{
      'GetCallList': (_) => {'NewCallListURL': ''},
      'GetNumberOfEntries': (_) => {'NewOnTelNumberOfEntries': '0'},
    };

    test('CardDAV setup refused with 606: insufficient rights reach the UI', () async {
      final box = FakeFritzBox({
        ...phonebookReads,
        'SetConfigByIndex': (_) => const SoapFault(606, 'Action Not Authorized'),
      });

      final error = await box.run((client) => captureError(() =>
          FritzBoxService.forTest(client).configureCardDav(
            phoneBlockUsername: 'user',
            phoneBlockToken: 'token',
          )));

      expect(error, isA<FritzBoxInsufficientRightsException>());
      expect(insufficientRightsMessage(l10n, error!),
          'Dafür braucht der Fritz!Box-Benutzer das Recht „FRITZ!Box Einstellungen“.');
      expect(box.actionCalls.where((a) => a == 'SetConfigByIndex'), hasLength(1),
          reason: 'no reconnect and retry on a refusal');
    });

    test('CardDAV setup with another fault keeps the existing error', () async {
      final box = FakeFritzBox({
        ...phonebookReads,
        'SetConfigByIndex': (_) => const SoapFault(402, 'Invalid Args'),
      });

      final error = await box.run((client) => captureError(() =>
          FritzBoxService.forTest(client).configureCardDav(
            phoneBlockUsername: 'user',
            phoneBlockToken: 'token',
          )));

      expect(error, isA<SoapFaultException>());
      expect(isInsufficientRights(error!), isFalse);
      expect(insufficientRightsMessage(l10n, error), isNull);
    });

    test('answer bot SIP device removal refused with 606', () async {
      final box = FakeFritzBox({
        'X_AVM-DE_GetNumberOfClients': (_) => {'NewX_AVM-DE_NumberOfClients': '1'},
        'X_AVM-DE_GetClient3': (_) => {
              'NewX_AVM-DE_ClientIndex': '0',
              'NewX_AVM-DE_ClientUsername': 'pb-bot',
            },
        'X_AVM-DE_DeleteClient': (_) => const SoapFault(606, 'Action Not Authorized'),
      });

      final error = await box.run((client) => captureError(
          () => FritzBoxService.forTest(client).deleteSipClient('pb-bot')));

      expect(error, isA<FritzBoxInsufficientRightsException>());
    });
  });

  group('CardDAV version check', () {
    test('hardware prefix is dropped before comparing with 7.20', () {
      expect(supportsCardDavVersion('284.08.25'), isTrue);
      expect(supportsCardDavVersion('113.07.21'), isTrue);
      expect(supportsCardDavVersion('113.07.20'), isTrue);
      expect(supportsCardDavVersion('113.07.10'), isFalse);
      expect(supportsCardDavVersion('113.06.83'), isFalse);
    });

    test('plain major.minor is accepted', () {
      expect(supportsCardDavVersion('7.20'), isTrue);
      expect(supportsCardDavVersion('8.25'), isTrue);
      expect(supportsCardDavVersion('7.10'), isFalse);
    });

    test('unparseable version counts as supported', () {
      expect(supportsCardDavVersion('garbage'), isTrue);
      expect(supportsCardDavVersion(''), isTrue);
      expect(errorLogs(), isEmpty);
    });
  });

  group('rights detection', () {
    test('UPnP error code from the fault detail', () {
      expect(upnpErrorCode(fault(606, 'Action Not Authorized')), 606);
      expect(upnpErrorCode(fault(503, 'Auth. failed')), 503);
      expect(upnpErrorCode(SoapFaultException(faultCode: 's:Client', faultString: 'x')), isNull);
      expect(isInsufficientRights(fault(606, 'Action Not Authorized')), isTrue);
      expect(isInsufficientRights(fault(503, 'Auth. failed')), isFalse);
      expect(isInsufficientRights(Exception('606')), isFalse);
    });

    test('web session without "FRITZ!Box Einstellungen" is refused', () {
      expect(
          () => checkWebSessionRights('<SessionInfo><SID>1234567890abcdef</SID>'
              '<Rights><Name>Phone</Name><Access>2</Access>'
              '<Name>Dial</Name><Access>2</Access></Rights></SessionInfo>'),
          throwsA(isA<FritzBoxInsufficientRightsException>()));
    });

    test('web session with "FRITZ!Box Einstellungen" passes', () {
      checkWebSessionRights('<SessionInfo><SID>1234567890abcdef</SID>'
          '<Rights><Name>Dial</Name><Access>2</Access>'
          '<Name>BoxAdmin</Name><Access>2</Access>'
          '<Name>Phone</Name><Access>2</Access></Rights></SessionInfo>');
      // Without a rights list there is nothing to judge.
      checkWebSessionRights('<SessionInfo><SID>1234567890abcdef</SID></SessionInfo>');
    });
  });
}
