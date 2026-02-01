import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:fritz_tr064/fritz_tr064.dart';
import 'package:http/http.dart' as http;
import 'package:phoneblock_mobile/fritzbox/fritzbox_models.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_storage.dart';
import 'package:phoneblock_mobile/main.dart';
import 'package:phoneblock_mobile/state.dart';

/// Service for communicating with Fritz!Box via TR-064 protocol.
class FritzBoxService {
  static final FritzBoxService instance = FritzBoxService._init();

  Tr64Client? _client;
  FritzBoxConnectionState _connectionState = FritzBoxConnectionState.notConfigured;

  FritzBoxService._init();

  /// Current connection state.
  FritzBoxConnectionState get connectionState => _connectionState;

  /// Returns true if connected to Fritz!Box.
  bool get isConnected => _connectionState == FritzBoxConnectionState.connected;

  /// Initializes the service by loading stored credentials.
  Future<void> initialize() async {
    final credentials = await FritzBoxStorage.instance.getCredentials();
    if (credentials == null) {
      _connectionState = FritzBoxConnectionState.notConfigured;
      return;
    }

    // Try to connect with stored credentials
    await _connect(credentials);
  }

  /// Connects to Fritz!Box with the given credentials.
  ///
  /// Returns true if connection was successful.
  Future<bool> connect({
    required String host,
    required String username,
    required String password,
  }) async {
    final credentials = FritzBoxCredentials(
      host: host,
      username: username,
      password: password,
    );

    final success = await _connect(credentials);
    if (success) {
      // Store credentials on successful connection
      await FritzBoxStorage.instance.saveCredentials(
        host: host,
        username: username,
        password: password,
      );
    }

    return success;
  }

  /// Internal connect method.
  Future<bool> _connect(FritzBoxCredentials credentials) async {
    try {
      // Close existing client if any
      _client?.close();

      _client = Tr64Client(
        host: credentials.host,
        username: credentials.username,
        password: credentials.password,
      );

      // Connect to fetch device description
      await _client!.connect();

      // Test connection by getting device info
      final deviceInfo = await getDeviceInfo();
      if (deviceInfo != null) {
        _connectionState = FritzBoxConnectionState.connected;

        // Update config with connection info
        await FritzBoxStorage.instance.updateConfig(
          host: credentials.host,
          fritzosVersion: deviceInfo.fritzosVersion,
        );

        return true;
      }

      _connectionState = FritzBoxConnectionState.error;
      return false;
    } catch (e) {
      if (kDebugMode) {
        print('Fritz!Box connection error: $e');
      }
      _connectionState = FritzBoxConnectionState.offline;
      return false;
    }
  }

  /// Disconnects from Fritz!Box and clears stored credentials.
  Future<void> disconnect() async {
    _client?.close();
    _client = null;
    _connectionState = FritzBoxConnectionState.notConfigured;
    await FritzBoxStorage.instance.clearCredentials();
    await FritzBoxStorage.instance.deleteConfig();
    await FritzBoxStorage.instance.deleteAllCalls();
  }

  /// Gets device information from Fritz!Box.
  Future<FritzBoxDeviceInfo?> getDeviceInfo() async {
    if (_client == null) return null;

    try {
      final deviceInfoService = _client!.deviceInfo();
      if (deviceInfoService == null) {
        if (kDebugMode) {
          print('DeviceInfo service not available');
        }
        return null;
      }
      final info = await deviceInfoService.getInfo();
      return FritzBoxDeviceInfo(
        host: _client!.host,
        modelName: info.modelName,
        fritzosVersion: info.softwareVersion,
        serialNumber: info.serialNumber,
      );
    } catch (e) {
      if (kDebugMode) {
        print('Error getting device info: $e');
      }
      return null;
    }
  }

  /// Gets the call list from Fritz!Box.
  ///
  /// Filters calls according to spec:
  /// - Include calls with no name (unknown caller)
  /// - Include calls with name starting with "SPAM: " (CardDAV blocklist)
  /// - Exclude other named contacts
  Future<List<FritzBoxCall>> getCallList({DateTime? since, int? days}) async {
    if (_client == null) return [];

    try {
      // Get call list entries using the onTel service
      final onTelService = _client!.onTel();
      if (onTelService == null) {
        if (kDebugMode) {
          print('OnTel service not available');
        }
        return [];
      }

      // Get call list entries (already parsed), limited by days if specified
      final entries = await onTelService.getCallListEntries(days: days);
      if (entries.isEmpty) {
        if (kDebugMode) {
          print('No call list entries returned');
        }
        return [];
      }

      // Convert to FritzBoxCall objects
      final calls = _convertCallListEntries(entries, since);

      return calls;
    } catch (e) {
      if (kDebugMode) {
        print('Error getting call list: $e');
      }
      return [];
    }
  }

  /// Converts CallListEntry objects to FritzBoxCall objects with filtering.
  List<FritzBoxCall> _convertCallListEntries(List<CallListEntry> entries, DateTime? since) {
    final calls = <FritzBoxCall>[];
    final now = DateTime.now().millisecondsSinceEpoch;

    for (final entry in entries) {
      // Determine call type
      final callType = _convertCallType(entry.type);

      // Determine phone number based on call direction
      final phoneNumber = callType == FritzBoxCallType.outgoing ||
              callType == FritzBoxCallType.activeOutgoing
          ? entry.called
          : entry.caller;

      // Skip if no phone number
      if (phoneNumber.isEmpty) continue;

      // Apply filtering rules per spec:
      // - No name -> Include (unknown caller)
      // - Name starts with "SPAM: " -> Include (CardDAV blocklist entry)
      // - Other name -> Exclude (known contact)
      final name = entry.name;
      if (name.isNotEmpty && !name.startsWith('SPAM: ')) {
        continue;
      }

      // Parse date (format: DD.MM.YY HH:MM)
      final timestamp = _parseGermanDate(entry.date);
      if (timestamp == null) continue;

      // Filter by since date if provided
      if (since != null && timestamp.isBefore(since)) continue;

      // Parse duration (format: H:MM or M:SS)
      final duration = _parseDuration(entry.duration);

      calls.add(FritzBoxCall(
        fritzboxId: entry.id.toString(),
        phoneNumber: phoneNumber,
        name: name.isEmpty ? null : name,
        timestamp: timestamp,
        duration: duration,
        callType: callType,
        device: entry.device.isEmpty ? null : entry.device,
        syncedAt: now,
      ));
    }

    return calls;
  }

  /// Converts fritz_tr064 CallType to FritzBoxCallType.
  FritzBoxCallType _convertCallType(CallType? type) {
    if (type == null) return FritzBoxCallType.missed;
    switch (type) {
      case CallType.incoming:
        return FritzBoxCallType.incoming;
      case CallType.missed:
        return FritzBoxCallType.missed;
      case CallType.outgoing:
        return FritzBoxCallType.outgoing;
      case CallType.activeIncoming:
        return FritzBoxCallType.activeIncoming;
      case CallType.rejected:
        return FritzBoxCallType.rejected;
      case CallType.activeOutgoing:
        return FritzBoxCallType.activeOutgoing;
    }
  }

  /// Parses German date format (DD.MM.YY HH:MM).
  DateTime? _parseGermanDate(String dateStr) {
    try {
      // Format: DD.MM.YY HH:MM
      final parts = dateStr.split(' ');
      if (parts.length != 2) return null;

      final dateParts = parts[0].split('.');
      if (dateParts.length != 3) return null;

      final timeParts = parts[1].split(':');
      if (timeParts.length != 2) return null;

      final day = int.parse(dateParts[0]);
      final month = int.parse(dateParts[1]);
      var year = int.parse(dateParts[2]);

      // Convert 2-digit year to 4-digit
      if (year < 100) {
        year += 2000;
      }

      final hour = int.parse(timeParts[0]);
      final minute = int.parse(timeParts[1]);

      return DateTime(year, month, day, hour, minute);
    } catch (e) {
      return null;
    }
  }

  /// Parses duration string (H:MM or M:SS) to seconds.
  int _parseDuration(String durationStr) {
    try {
      final parts = durationStr.split(':');
      if (parts.length != 2) return 0;

      final first = int.parse(parts[0]);
      final second = int.parse(parts[1]);

      // Fritz!Box uses H:MM format for longer calls, M:SS for shorter
      // We'll assume M:SS format (minutes:seconds)
      return first * 60 + second;
    } catch (e) {
      return 0;
    }
  }

  /// Syncs call list from Fritz!Box with spam enrichment from PhoneBlock API.
  ///
  /// Returns the number of new calls synced.
  Future<int> syncCallList() async {
    if (!isConnected) return 0;

    // Get retention days setting
    final retentionDays = await getRetentionDays();

    // Get last sync timestamp
    final config = await FritzBoxStorage.instance.getConfig();
    final lastFetch = config?.lastFetchTimestamp;
    DateTime? since;
    if (lastFetch != null) {
      since = DateTime.fromMillisecondsSinceEpoch(lastFetch);
    }

    // Fetch calls from Fritz!Box, limited by retention period
    // Use null for days if retention is infinite
    final days = retentionDays == retentionInfinite ? null : retentionDays;
    final calls = await getCallList(since: since, days: days);
    if (calls.isEmpty) return 0;

    // Get auth token for PhoneBlock API
    final authToken = await getAuthToken();

    // Enrich calls with spam info from PhoneBlock
    final enrichedCalls = <FritzBoxCall>[];
    for (final call in calls) {
      // Check if call already exists
      final exists = await FritzBoxStorage.instance.callExists(call.fritzboxId);
      if (exists) continue;

      // Enrich with spam info
      final enriched = await _enrichCallWithSpamInfo(call, authToken);
      enrichedCalls.add(enriched);
    }

    if (enrichedCalls.isEmpty) return 0;

    // Store calls in database
    await FritzBoxStorage.instance.insertCalls(enrichedCalls);

    // Update last fetch timestamp
    await FritzBoxStorage.instance.updateConfig(
      lastFetchTimestamp: DateTime.now().millisecondsSinceEpoch,
    );

    // Clean up old calls based on retention period
    await FritzBoxStorage.instance.deleteOldCalls(retentionDays);

    return enrichedCalls.length;
  }

  /// Enriches a call with spam information from PhoneBlock API.
  Future<FritzBoxCall> _enrichCallWithSpamInfo(FritzBoxCall call, String? authToken) async {
    if (authToken == null) return call;

    try {
      final url = '$pbBaseUrl/api/num/${call.phoneNumber}?format=json';
      final response = await http.get(
        Uri.parse(url),
        headers: {
          'Authorization': 'Bearer $authToken',
          'User-Agent': 'PhoneBlockMobile/$appVersion',
        },
      );

      if (response.statusCode == 200) {
        final data = json.decode(response.body) as Map<String, dynamic>;

        final votes = data['votes'] as int? ?? 0;
        final votesWildcard = data['votesWildcard'] as int? ?? 0;
        final ratingStr = data['rating'] as String?;
        final label = data['label'] as String?;
        final location = data['location'] as String?;

        Rating? rating;
        if (ratingStr != null) {
          rating = _parseRating(ratingStr);
        }

        return FritzBoxCall(
          id: call.id,
          fritzboxId: call.fritzboxId,
          phoneNumber: call.phoneNumber,
          name: call.name,
          timestamp: call.timestamp,
          duration: call.duration,
          callType: call.callType,
          device: call.device,
          votes: votes,
          votesWildcard: votesWildcard,
          rating: rating,
          label: label,
          location: location,
          syncedAt: call.syncedAt,
        );
      }
    } catch (e) {
      if (kDebugMode) {
        print('Error enriching call with spam info: $e');
      }
    }

    return call;
  }

  /// Parses rating string from API response.
  Rating? _parseRating(String ratingStr) {
    switch (ratingStr) {
      case 'A_LEGITIMATE':
        return Rating.aLEGITIMATE;
      case 'B_MISSED':
        return Rating.uNKNOWN;
      case 'C_PING':
        return Rating.pING;
      case 'D_POLL':
        return Rating.pOLL;
      case 'E_ADVERTISING':
        return Rating.aDVERTISING;
      case 'F_GAMBLE':
        return Rating.gAMBLE;
      case 'G_FRAUD':
        return Rating.fRAUD;
      default:
        return null;
    }
  }

  /// Checks if currently on the home network (can reach Fritz!Box).
  Future<bool> checkConnection() async {
    if (_client == null) {
      final credentials = await FritzBoxStorage.instance.getCredentials();
      if (credentials == null) {
        _connectionState = FritzBoxConnectionState.notConfigured;
        return false;
      }

      return await _connect(credentials);
    }

    try {
      final deviceInfo = await getDeviceInfo();
      if (deviceInfo != null) {
        _connectionState = FritzBoxConnectionState.connected;
        return true;
      }
      _connectionState = FritzBoxConnectionState.offline;
      return false;
    } catch (e) {
      _connectionState = FritzBoxConnectionState.offline;
      return false;
    }
  }
}
