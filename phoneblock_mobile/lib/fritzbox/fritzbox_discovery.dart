import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:phoneblock_mobile/fritzbox/fritzbox_models.dart';
import 'package:xml/xml.dart';

/// Service for discovering Fritz!Box devices on the network.
class FritzBoxDiscovery {
  /// Default hostnames to try when discovering Fritz!Box.
  static const _defaultHosts = ['fritz.box', '192.168.178.1'];

  /// Connection timeout for discovery requests.
  static const _timeout = Duration(seconds: 5);

  /// Attempts to discover a Fritz!Box on the local network.
  ///
  /// Returns device info if found, null otherwise.
  Future<FritzBoxDeviceInfo?> discover() async {
    for (final host in _defaultHosts) {
      final deviceInfo = await _tryHost(host);
      if (deviceInfo != null) {
        return deviceInfo;
      }
    }
    return null;
  }

  /// Tries to connect to a specific host and retrieve device info.
  ///
  /// Returns device info if successful, null otherwise.
  Future<FritzBoxDeviceInfo?> tryHost(String host) async {
    return await _tryHost(host);
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
    } catch (e) {
      if (kDebugMode) {
        print('Discovery error for $host: $e');
      }
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
    } catch (e) {
      if (kDebugMode) {
        print('TR-064 discovery failed for $host: $e');
      }
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
    } catch (e) {
      if (kDebugMode) {
        print('UPnP discovery failed for $host: $e');
      }
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
    } catch (e) {
      if (kDebugMode) {
        print('Error parsing device description: $e');
      }
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

  /// Validates a host address format.
  bool isValidHost(String host) {
    // Check if it's a valid hostname or IP address
    final hostnameRegex = RegExp(
      r'^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)*$',
    );
    final ipRegex = RegExp(
      r'^(\d{1,3}\.){3}\d{1,3}$',
    );

    return hostnameRegex.hasMatch(host) || ipRegex.hasMatch(host);
  }
}
