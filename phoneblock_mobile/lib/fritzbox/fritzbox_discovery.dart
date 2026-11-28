import 'dart:io';

import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import 'package:phoneblock_mobile/fritzbox/fritzbox_models.dart';
import 'package:phoneblock_mobile/logging/app_logger.dart';
import 'package:xml/xml.dart';

/// Searches the local network via mDNS for announced Fritz!Box devices and
/// returns the hosts of each found device, best first.
typedef MdnsBrowser = Future<List<List<String>>> Function();

/// Reads the device information of the Fritz!Box at a host, `null` if none
/// answers there.
typedef HostProbe = Future<FritzBoxDeviceInfo?> Function(String host);

/// Native channel providing the mDNS search ([MainActivity]).
const MethodChannel _nativeChannel =
    MethodChannel('de.haumacher.phoneblock_mobile/call_checker');

/// Time the native side browses for `_tr064._tcp` services.
const int _mdnsBrowseMillis = 3000;

/// Searches via the native NsdManager for services `_tr064._tcp`, as which a
/// Fritz!Box announces itself (issue #660).
///
/// Returns an empty list if the search is not possible.
Future<List<List<String>>> browseMdnsNative() async {
  try {
    final services = await _nativeChannel.invokeMethod<List<Object?>>(
            'discoverFritzBoxes', {'timeoutMs': _mdnsBrowseMillis}) ??
        const [];
    final result = <List<String>>[];
    for (final service in services) {
      if (service is! Map) continue;
      final hosts = (service['hosts'] as List<Object?>?)
              ?.whereType<String>()
              .toList() ??
          const <String>[];
      AppLogger.instance.info('fritzbox-discovery',
          'mDNS: service "${service['name']}" at ${hosts.join(', ')}');
      if (hosts.isNotEmpty) result.add(hosts);
    }
    return result;
  } catch (e, s) {
    AppLogger.instance.error('fritzbox-discovery', 'mDNS search failed', e, s);
    return const [];
  }
}

/// Normalizes a Fritz!Box address entered by the user (issue #819).
///
/// Accepts an IPv4 address (`10.0.0.4`), an IPv6 address bare or in brackets
/// (`fd00::1`, `[fd00::1]`) and a host name (`fritz.box`). An IPv6 address is
/// returned in brackets, ready to be used in a URL. Returns `null` for
/// anything else, e.g. a URL, a port, or an incomplete IPv4 address.
String? normalizeHostInput(String input) {
  final host = input.trim();
  if (host.isEmpty || host.length > 253) return null;

  if (host.startsWith('[') || host.contains(':')) {
    final inner = host.startsWith('[') && host.endsWith(']')
        ? host.substring(1, host.length - 1)
        : host;
    if (inner.isEmpty || inner.contains('[') || inner.contains(']')) {
      return null;
    }
    try {
      Uri.parseIPv6Address(inner);
      return '[$inner]';
    } on FormatException {
      return null;
    }
  }

  if (RegExp(r'^[\d.]+$').hasMatch(host)) {
    try {
      Uri.parseIPv4Address(host);
      return host;
    } on FormatException {
      return null;
    }
  }

  final label = RegExp(r'^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$');
  if (host.split('.').every(label.hasMatch)) return host;
  return null;
}

/// Service for discovering Fritz!Box devices on the network.
class FritzBoxDiscovery {
  /// Hosts tried in addition to the devices found via mDNS: the default
  /// address of a Fritz!Box and its name in its own DNS.
  static const fixedHosts = ['192.168.178.1', 'fritz.box'];

  /// Connection timeout for discovery requests.
  static const _timeout = Duration(seconds: 5);

  final MdnsBrowser _browseMdns;
  final HostProbe? _probe;

  /// Creates a discovery; tests replace the mDNS search and the probe of a
  /// host.
  FritzBoxDiscovery({MdnsBrowser? browseMdns, HostProbe? probe})
      : _browseMdns = browseMdns ?? browseMdnsNative,
        _probe = probe;

  /// Searches all Fritz!Box devices (Fritz!Box, mesh repeaters) in the local
  /// network.
  ///
  /// Tries the hosts announced via mDNS first, then [fixedHosts]. A device
  /// answering under several addresses is listed once, under the first one.
  /// Requires the access to the local network.
  Future<List<FritzBoxDeviceInfo>> discoverAll() async {
    final candidates = <String>{};
    for (final hosts in await _browseMdns()) {
      candidates.addAll(hosts);
    }
    candidates.addAll(fixedHosts);

    final probed = await Future.wait(candidates.map(tryHost));

    final seen = <String>{};
    final result = <FritzBoxDeviceInfo>[];
    for (final info in probed) {
      if (info == null) continue;
      if (seen.add(_identity(info))) result.add(info);
    }
    AppLogger.instance.info('fritzbox-discovery',
        'Found ${result.length} device(s) among ${candidates.join(', ')}');
    return result;
  }

  /// Identity of a device independent of the address it was reached under.
  static String _identity(FritzBoxDeviceInfo info) {
    final serial = info.serialNumber;
    return (serial != null && serial.isNotEmpty) ? 'serial:$serial' : 'host:${info.host}';
  }

  /// Tries to connect to a specific host and retrieve device info.
  ///
  /// Returns device info if successful, null otherwise.
  Future<FritzBoxDeviceInfo?> tryHost(String host) {
    return (_probe ?? _tryHost)(host);
  }

  /// Internal method to try connecting to a host.
  Future<FritzBoxDeviceInfo?> _tryHost(String host) async {
    try {
      // First try to get the TR-064 device description
      final tr064Info = await _getTr064DeviceInfo(host);
      if (tr064Info != null) {
        return tr064Info;
      }

      // Fallback: try UPnP discovery
      final upnpInfo = await _getUpnpDeviceInfo(host);
      if (upnpInfo != null) {
        return upnpInfo;
      }

      // Last resort: just check if port 49000 responds
      final isReachable = await _checkPort(host, 49000);
      if (isReachable) {
        return FritzBoxDeviceInfo(host: host);
      }

      return null;
    } catch (e, s) {
      AppLogger.instance.error('fritzbox-discovery', 'Discovery error for $host', e, s);
      return null;
    }
  }

  /// Gets device info via TR-064 device description.
  Future<FritzBoxDeviceInfo?> _getTr064DeviceInfo(String host) async {
    try {
      final url = 'http://$host:49000/tr64desc.xml';
      final response = await http.get(Uri.parse(url)).timeout(_timeout);

      if (response.statusCode == 200) {
        return _parseDeviceDescription(host, response.body);
      }
      return null;
    } catch (e, s) {
      AppLogger.instance.error('fritzbox-discovery', 'TR-064 discovery failed for $host', e, s);
      return null;
    }
  }

  /// Gets device info via UPnP device description.
  Future<FritzBoxDeviceInfo?> _getUpnpDeviceInfo(String host) async {
    try {
      final url = 'http://$host:49000/igddesc.xml';
      final response = await http.get(Uri.parse(url)).timeout(_timeout);

      if (response.statusCode == 200) {
        return _parseDeviceDescription(host, response.body);
      }
      return null;
    } catch (e, s) {
      AppLogger.instance.error('fritzbox-discovery', 'UPnP discovery failed for $host', e, s);
      return null;
    }
  }

  /// Parses device description XML to extract device info.
  FritzBoxDeviceInfo? _parseDeviceDescription(String host, String xml) {
    try {
      final document = XmlDocument.parse(xml);
      final deviceElement = document.findAllElements('device').firstOrNull;

      if (deviceElement == null) {
        return FritzBoxDeviceInfo(host: host);
      }

      String? modelName;
      String? fritzosVersion;
      String? serialNumber;

      // Try to find friendlyName (usually contains model name)
      final friendlyName = deviceElement.findElements('friendlyName').firstOrNull?.innerText;
      if (friendlyName != null) {
        modelName = friendlyName;
      }

      // Try to find modelName
      final modelElement = deviceElement.findElements('modelName').firstOrNull?.innerText;
      if (modelElement != null && modelElement.isNotEmpty) {
        modelName = modelElement;
      }

      // Try to find modelNumber (contains FRITZ!OS version)
      final modelNumber = deviceElement.findElements('modelNumber').firstOrNull?.innerText;
      if (modelNumber != null) {
        // Fritz!Box model numbers are like "FRITZ!Box 7590" or just version numbers
        fritzosVersion = modelNumber;
      }

      // Try to find serial number
      final serial = deviceElement.findElements('serialNumber').firstOrNull?.innerText;
      if (serial != null) {
        serialNumber = serial;
      }

      return FritzBoxDeviceInfo(
        host: host,
        modelName: modelName,
        fritzosVersion: fritzosVersion,
        serialNumber: serialNumber,
      );
    } catch (e, s) {
      AppLogger.instance.error('fritzbox-discovery', 'Error parsing device description', e, s);
      return FritzBoxDeviceInfo(host: host);
    }
  }

  /// Checks if a specific port is reachable on the host.
  Future<bool> _checkPort(String host, int port) async {
    try {
      final socket = await Socket.connect(
        host,
        port,
        timeout: _timeout,
      );
      await socket.close();
      return true;
    } catch (e) {
      return false;
    }
  }
}
