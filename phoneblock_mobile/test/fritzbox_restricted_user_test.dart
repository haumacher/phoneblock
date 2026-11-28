import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fritz_tr064/fritz_tr064.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_login.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_rights.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_service.dart';
import 'package:phoneblock_mobile/l10n/app_localizations_de.dart';
import 'package:phoneblock_mobile/logging/app_logger.dart';

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

SoapFaultException fault(int code, String description) {
  // The library flattens <detail> to its text content.
  return SoapFaultException(
    faultCode: 's:Client',
    faultString: 'UPnPError',
    detail: '\n$code\n$description\n',
  );
}

Future<Object?> captureError(Future<void> Function() action) async {
  try {
    await action();
    return null;
  } catch (e) {
    return e;
  }
}

/// A UPnP fault the fake Fritz!Box answers an action with.
class SoapFault {
  final int code;
  final String description;
  const SoapFault(this.code, this.description);
}

/// A Fritz!Box speaking TR-064 over a [MockClient]: serves the device
/// description, runs the digest login handshake and answers the actions in
/// [actions] with response arguments or a [SoapFault].
class FakeFritzBox {
  final Map<String, Object Function(Map<String, String>)> actions;
  final bool wrongPassword;

  final List<String> actionCalls = [];
  int descriptionFetches = 0;

  FakeFritzBox(this.actions, {this.wrongPassword = false});

  /// Connects a [Tr64Client] to this box and runs [body] with it.
  Future<T> run<T>(Future<T> Function(Tr64Client client) body) {
    return http.runWithClient(() async {
      final client = Tr64Client(host: 'fritz.box', username: 'pb', password: 'secret');
      await client.connect();
      try {
        return await body(client);
      } finally {
        client.close();
      }
    }, () => MockClient(_handle));
  }

  Future<http.Response> _handle(http.Request request) async {
    if (request.method == 'GET' && request.url.path == '/tr64desc.xml') {
      descriptionFetches++;
      return http.Response(tr64Description, 200);
    }

    final soapAction = request.headers['SoapAction'] ?? '';
    final action = soapAction.substring(soapAction.indexOf('#') + 1);

    if (request.body.contains('InitChallenge')) {
      return _soap(500, _challenge('Challenge', 'Unauthenticated'),
          _faultBody(503, 'Auth. failed'));
    }
    if (wrongPassword) {
      return _soap(500, _challenge('Challenge', 'Unauthenticated'),
          _faultBody(503, 'Auth. failed'));
    }

    actionCalls.add(action);
    final handler = actions[action];
    final result = handler == null
        ? const SoapFault(401, 'Invalid Action')
        : handler(const {});
    final header = _challenge('NextChallenge', 'Authenticated');
    if (result is SoapFault) {
      return _soap(500, header, _faultBody(result.code, result.description));
    }
    final args = (result as Map<String, String>)
        .entries
        .map((e) => '<${e.key}>${e.value}</${e.key}>')
        .join();
    return _soap(200, header,
        '<u:${action}Response xmlns:u="urn:dslforum-org:service:X:1">$args</u:${action}Response>');
  }

  static String _challenge(String element, String status) =>
      '<h:$element xmlns:h="http://soap-authentication.org/digest/2001/10/" s:mustUnderstand="1">'
      '<Nonce>0123456789ABCDEF</Nonce><Realm>F!Box SOAP-Auth</Realm><Status>$status</Status>'
      '</h:$element>';

  static String _faultBody(int code, String description) =>
      '<s:Fault><faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring>'
      '<detail><UPnPError xmlns="urn:dslforum-org:control-1-0">'
      '<errorCode>$code</errorCode><errorDescription>$description</errorDescription>'
      '</UPnPError></detail></s:Fault>';

  static http.Response _soap(int status, String header, String body) => http.Response(
        '<?xml version="1.0"?>\n'
        '<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" '
        's:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">'
        '<s:Header>$header</s:Header><s:Body>$body</s:Body></s:Envelope>',
        status,
      );
}

/// Minimal `tr64desc.xml` with the structure of a FRITZ!Box 6670 Cable on
/// FRITZ!OS 8.25 (synthetic serial number and UDN).
const tr64Description = '''<?xml version="1.0"?>
<root xmlns="urn:dslforum-org:device-1-0">
<specVersion>
<major>1</major>
<minor>0</minor>
</specVersion>
<systemVersion>
<HW>284</HW>
<Major>284</Major>
<Minor>8</Minor>
<Patch>25</Patch>
<Buildnumber>132295</Buildnumber>
<Display>284.08.25</Display>
</systemVersion>
<device>
<deviceType>urn:dslforum-org:device:InternetGatewayDevice:1</deviceType>
<friendlyName>FRITZ!Box 6670 Cable</friendlyName>
<manufacturer>FRITZ! GmbH</manufacturer>
<manufacturerURL>https://fritz.com</manufacturerURL>
<modelDescription>FRITZ!Box 6670 Cable</modelDescription>
<modelName>FRITZ!Box 6670 Cable</modelName>
<modelNumber>6670 - avm</modelNumber>
<modelURL>https://fritz.com</modelURL>
<UDN>uuid:00000000-0000-0000-0000-aabbccddeeff</UDN>
<serialNumber>AABBCCDDEEFF</serialNumber>
<originUDN></originUDN>
<serviceList>
<service>
<serviceType>urn:dslforum-org:service:DeviceInfo:1</serviceType>
<serviceId>urn:DeviceInfo-com:serviceId:DeviceInfo1</serviceId>
<controlURL>/upnp/control/deviceinfo</controlURL>
<eventSubURL>/upnp/control/deviceinfo</eventSubURL>
<SCPDURL>/deviceinfoSCPD.xml</SCPDURL>
</service>
<service>
<serviceType>urn:dslforum-org:service:X_AVM-DE_OnTel:1</serviceType>
<serviceId>urn:X_AVM-DE_OnTel-com:serviceId:X_AVM-DE_OnTel1</serviceId>
<controlURL>/upnp/control/x_contact</controlURL>
<eventSubURL>/upnp/control/x_contact</eventSubURL>
<SCPDURL>/x_contactSCPD.xml</SCPDURL>
</service>
<service>
<serviceType>urn:dslforum-org:service:X_VoIP:1</serviceType>
<serviceId>urn:X_VoIP-com:serviceId:X_VoIP1</serviceId>
<controlURL>/upnp/control/x_voip</controlURL>
<eventSubURL>/upnp/control/x_voip</eventSubURL>
<SCPDURL>/x_voipSCPD.xml</SCPDURL>
</service>
</serviceList>
</device>
</root>
''';
