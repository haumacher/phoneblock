import 'package:fritz_tr064/fritz_tr064.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

/// Test double of a Fritz!Box below the network: TR-064 over a [MockClient].

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

  /// After the device description, every request fails without an answer
  /// (box gone from the network).
  final bool unreachable;

  final List<String> actionCalls = [];
  int descriptionFetches = 0;

  FakeFritzBox(this.actions, {this.wrongPassword = false, this.unreachable = false});

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

    if (unreachable) throw http.ClientException('No route to host', request.url);

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
        : handler(_arguments(request.body, action));
    final header = _challenge('NextChallenge', 'Authenticated');
    if (result is SoapFault) {
      return _soap(500, header, _faultBody(result.code, result.description));
    }
    final args = (result as Map<String, String>)
        .entries
        .map((e) => '<${e.key}>${_escape(e.value)}</${e.key}>')
        .join();
    return _soap(200, header,
        '<u:${action}Response xmlns:u="urn:dslforum-org:service:X:1">$args</u:${action}Response>');
  }

  /// The arguments of [action] in the SOAP request [body].
  static Map<String, String> _arguments(String body, String action) {
    final element = RegExp('<u:$action[^>]*>(.*?)</u:$action>', dotAll: true)
        .firstMatch(body)
        ?.group(1);
    if (element == null) return const {};
    return {
      for (final m in RegExp(r'<([\w-]+)>([^<]*)</\1>').allMatches(element))
        m.group(1)!: _unescape(m.group(2)!),
    };
  }

  static String _escape(String s) =>
      s.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');

  static String _unescape(String s) =>
      s.replaceAll('&lt;', '<').replaceAll('&gt;', '>').replaceAll('&amp;', '&');

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
<serviceType>urn:dslforum-org:service:X_AVM-DE_TAM:1</serviceType>
<serviceId>urn:X_AVM-DE_TAM-com:serviceId:X_AVM-DE_TAM1</serviceId>
<controlURL>/upnp/control/x_tam</controlURL>
<eventSubURL>/upnp/control/x_tam</eventSubURL>
<SCPDURL>/x_tamSCPD.xml</SCPDURL>
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
