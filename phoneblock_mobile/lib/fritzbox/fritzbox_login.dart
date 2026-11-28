import 'package:fritz_tr064/fritz_tr064.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_models.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_rights.dart';
import 'package:phoneblock_mobile/logging/app_logger.dart';
import 'package:xml/xml.dart';

/// Extracts model name, FRITZ!OS version and serial number from the TR-064
/// device description (`tr64desc.xml`).
///
/// The version is taken from `<systemVersion><Display>`, which has the same
/// format as `NewSoftwareVersion` of DeviceInfo `GetInfo` (e.g. `284.08.25`).
FritzBoxDeviceInfo parseTr64Description(String host, String xml) {
  final root = XmlDocument.parse(xml).rootElement;

  String? childText(XmlElement? parent, String name) {
    final text = parent?.childElements
        .where((e) => e.localName == name)
        .firstOrNull
        ?.innerText
        .trim();
    return (text == null || text.isEmpty) ? null : text;
  }

  final systemVersion =
      root.childElements.where((e) => e.localName == 'systemVersion').firstOrNull;
  final device =
      root.childElements.where((e) => e.localName == 'device').firstOrNull;

  return FritzBoxDeviceInfo(
    host: host,
    modelName: childText(device, 'modelName') ?? childText(device, 'friendlyName'),
    fritzosVersion: childText(systemVersion, 'Display'),
    serialNumber: childText(device, 'serialNumber'),
  );
}

/// Reads the device information of a connected Fritz!Box.
///
/// Uses DeviceInfo `GetInfo`. A Fritz!Box user without the right "FRITZ!Box
/// Einstellungen" is refused this action (UPnP error 606, issue #268); then
/// model and version come from the device description, which needs no
/// login. A 606 still proves that the login itself was accepted - a wrong
/// password is answered with 503 "Auth. failed".
///
/// All other errors are thrown.
Future<FritzBoxDeviceInfo> readDeviceInfo(Tr64Client client) async {
  final service = client.deviceInfo();
  if (service != null) {
    try {
      final info = await service.getInfo();
      return FritzBoxDeviceInfo(
        host: client.host,
        modelName: info.modelName,
        fritzosVersion: info.softwareVersion,
        serialNumber: info.serialNumber,
      );
    } on SoapFaultException catch (e) {
      if (!isInsufficientRights(e)) rethrow;
      AppLogger.instance.info('fritzbox',
          'DeviceInfo not readable for this Fritz!Box user (${e.detail?.trim()}), using the device description');
    }
  } else {
    AppLogger.instance.warn('fritzbox', 'DeviceInfo service not available, using the device description');
  }

  final scheme = client.useHttps ? 'https' : 'http';
  final xml = await client.fetchUrl('$scheme://${client.host}:${client.port}/tr64desc.xml');
  return parseTr64Description(client.host, xml);
}

/// Verifies the login on a Fritz!Box whose device description has been
/// fetched ([Tr64Client.connect]) and reads its device information.
///
/// The login is verified by `GetCallList`, which every Fritz!Box user with
/// access to the call list may call. Returns `null` (after logging the
/// cause) if the login is not accepted.
Future<FritzBoxDeviceInfo?> verifyLogin(Tr64Client client) async {
  final FritzBoxDeviceInfo deviceInfo;
  try {
    deviceInfo = await readDeviceInfo(client);
  } catch (e, s) {
    AppLogger.instance.error('fritzbox', 'Fritz!Box auth failed: no device info returned', e, s);
    return null;
  }

  final onTelService = client.onTel();
  if (onTelService != null) {
    try {
      // This call fails with a SoapFaultException if auth is invalid.
      await onTelService.getCallList();
    } catch (e, s) {
      AppLogger.instance.error('fritzbox', 'Fritz!Box auth verification failed', e, s);
      return null;
    }
  }

  return deviceInfo;
}

/// Whether a Fritz!Box with the given FRITZ!OS version supports CardDAV
/// online phonebooks (FRITZ!OS 7.20 and later).
///
/// The Fritz!Box reports its version as `<hw>.<major>.<minor>`, e.g.
/// `284.08.25` for FRITZ!OS 8.25 or `113.07.21` for 7.21; a plain
/// `<major>.<minor>` is accepted as well. An unparseable version does not
/// block the setup.
bool supportsCardDavVersion(String version) {
  final parts = version.trim().split('.').map(int.tryParse).toList();
  final List<int?> majorMinor;
  if (parts.length == 3) {
    majorMinor = parts.sublist(1);
  } else if (parts.length == 2) {
    majorMinor = parts;
  } else {
    majorMinor = const [null, null];
  }
  final major = majorMinor[0];
  final minor = majorMinor[1];
  if (major == null || minor == null) {
    AppLogger.instance.info('fritzbox',
        'Unknown FRITZ!OS version format "$version", assuming CardDAV support');
    return true;
  }
  return major > 7 || (major == 7 && minor >= 20);
}
