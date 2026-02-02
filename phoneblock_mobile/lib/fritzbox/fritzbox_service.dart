import 'dart:convert';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:fritz_tr064/fritz_tr064.dart';
import 'package:http/http.dart' as http;
import 'package:phoneblock_mobile/fritzbox/fritzbox_models.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_storage.dart';
import 'package:phoneblock_mobile/main.dart';
import 'package:phoneblock_mobile/state.dart';
import 'package:phoneblock_mobile/storage.dart';

/// Status of CardDAV synchronization.
enum CardDavStatus {
  /// CardDAV is not configured.
  notConfigured,

  /// CardDAV is configured but disabled.
  disabled,

  /// CardDAV is synced successfully.
  synced,

  /// CardDAV sync is pending.
  syncPending,

  /// CardDAV sync failed.
  error,
}

/// Normalizes a phone number to international E.164 format.
/// Uses the native Android libphonenumber library via MethodChannel.
/// Returns null if normalization fails.
Future<String?> normalizePhoneNumber(String phoneNumber, {String? countryCode}) async {
  try {
    final result = await platform.invokeMethod<String>('normalizePhoneNumber', {
      'phoneNumber': phoneNumber,
      'countryCode': countryCode ?? 'DE', // Default to Germany for Fritz!Box
    });
    return result;
  } catch (e) {
    if (kDebugMode) {
      print('Error normalizing phone number: $e');
    }
    return null;
  }
}

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
      if (deviceInfo == null || (deviceInfo.modelName?.isEmpty ?? true)) {
        if (kDebugMode) {
          print('Fritz!Box auth failed: no device info returned');
        }
        _connectionState = FritzBoxConnectionState.error;
        return false;
      }

      // Verify authentication by making a call that requires auth
      // Getting the call list URL requires authentication
      final onTelService = _client!.onTel();
      if (onTelService != null) {
        try {
          // This call will fail with SoapFaultException if auth is invalid
          await onTelService.getCallList();
        } catch (e) {
          if (kDebugMode) {
            print('Fritz!Box auth verification failed: $e');
          }
          _connectionState = FritzBoxConnectionState.error;
          return false;
        }
      }

      _connectionState = FritzBoxConnectionState.connected;

      // Update config with connection info
      await FritzBoxStorage.instance.updateConfig(
        host: credentials.host,
        fritzosVersion: deviceInfo.fritzosVersion,
      );

      return true;
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
    // Delete all Fritz!Box calls from the unified call database
    await ScreenedCallsDatabase.instance.deleteFritzBoxCalls();
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
  Future<List<ScreenedCall>> getCallList({DateTime? since, int? days}) async {
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

      // Convert to ScreenedCall objects
      final calls = _convertCallListEntries(entries, since);

      return calls;
    } catch (e) {
      if (kDebugMode) {
        print('Error getting call list: $e');
      }
      return [];
    }
  }

  /// Converts CallListEntry objects to ScreenedCall objects with filtering.
  List<ScreenedCall> _convertCallListEntries(List<CallListEntry> entries, DateTime? since) {
    final calls = <ScreenedCall>[];

    for (final entry in entries) {
      // Determine call type
      final callType = _convertCallType(entry.type);

      // Skip outgoing calls - they cannot be spam
      if (callType == FritzBoxCallType.outgoing ||
          callType == FritzBoxCallType.activeOutgoing) {
        continue;
      }

      // Get caller phone number (incoming calls only at this point)
      final phoneNumber = entry.caller;

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

      // Determine if call was blocked (only rejected calls are actually blocked)
      // missed = not answered, rejected = refused by call barring
      final wasBlocked = callType == FritzBoxCallType.rejected;

      calls.add(ScreenedCall(
        phoneNumber: phoneNumber,
        timestamp: timestamp,
        wasBlocked: wasBlocked,
        votes: 0, // Will be enriched later
        source: CallSource.fritzbox,
        duration: duration,
        device: entry.device.isEmpty ? null : entry.device,
        fritzboxId: entry.id.toString(),
        callType: callType,
        label: name.isEmpty ? null : name,
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
  /// Returns the list of newly inserted call IDs.
  Future<List<int>> syncCallList() async {
    if (!isConnected) return [];

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
    if (calls.isEmpty) return [];

    // Get auth token for PhoneBlock API
    final authToken = await getAuthToken();

    // Get database instance
    final db = ScreenedCallsDatabase.instance;

    // Enrich calls with spam info from PhoneBlock
    final enrichedCalls = <ScreenedCall>[];
    for (final call in calls) {
      // Check if call already exists by fritzboxId
      final exists = await _callExistsByFritzboxId(call.fritzboxId!);
      if (exists) continue;

      // Normalize phone number to international format for API lookup
      final normalizedNumber = await normalizePhoneNumber(call.phoneNumber);
      if (normalizedNumber == null) {
        // If normalization fails, skip this call (invalid number)
        if (kDebugMode) {
          print('Skipping call with invalid phone number: ${call.phoneNumber}');
        }
        continue;
      }

      // Create call with normalized phone number
      final normalizedCall = ScreenedCall(
        id: call.id,
        phoneNumber: normalizedNumber,
        timestamp: call.timestamp,
        wasBlocked: call.wasBlocked,
        votes: call.votes,
        votesWildcard: call.votesWildcard,
        rating: call.rating,
        label: call.label,
        location: call.location,
        source: call.source,
        duration: call.duration,
        device: call.device,
        fritzboxId: call.fritzboxId,
        callType: call.callType,
      );

      // Enrich with spam info using normalized number
      final enriched = await _enrichCallWithSpamInfo(normalizedCall, authToken);
      enrichedCalls.add(enriched);
    }

    if (enrichedCalls.isEmpty) return [];

    // Store calls in database and collect inserted IDs
    final insertedIds = <int>[];
    for (final call in enrichedCalls) {
      final insertedCall = await db.insertScreenedCall(call);
      if (insertedCall.id != null) {
        insertedIds.add(insertedCall.id!);
      }
    }

    // Update last fetch timestamp
    await FritzBoxStorage.instance.updateConfig(
      lastFetchTimestamp: DateTime.now().millisecondsSinceEpoch,
    );

    // Clean up old calls based on retention period
    await db.deleteOldScreenedCalls(retentionDays);

    return insertedIds;
  }

  /// Checks if a call with the given Fritz!Box ID already exists.
  Future<bool> _callExistsByFritzboxId(String fritzboxId) async {
    final db = await ScreenedCallsDatabase.instance.database;
    final result = await db.query(
      'screened_calls',
      columns: ['id'],
      where: 'fritzboxId = ?',
      whereArgs: [fritzboxId],
    );
    return result.isNotEmpty;
  }

  /// Enriches a call with spam information from PhoneBlock API.
  Future<ScreenedCall> _enrichCallWithSpamInfo(ScreenedCall call, String? authToken) async {
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

        // Keep the original blocked status - for Fritz!Box calls, blocking
        // already happened (or didn't) based on the CardDAV blocklist
        final wasBlocked = call.wasBlocked;

        return ScreenedCall(
          id: call.id,
          phoneNumber: call.phoneNumber,
          timestamp: call.timestamp,
          wasBlocked: wasBlocked,
          votes: votes,
          votesWildcard: votesWildcard,
          rating: rating,
          label: label ?? call.label,
          location: location,
          source: call.source,
          duration: call.duration,
          device: call.device,
          fritzboxId: call.fritzboxId,
          callType: call.callType,
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

  // -- CardDAV Configuration Methods --

  /// Minimum FRITZ!OS version required for CardDAV support.
  static const String _minCardDavVersion = '7.20';

  /// Checks if the connected Fritz!Box supports CardDAV (FRITZ!OS 7.20+).
  Future<bool> supportsCardDav() async {
    final config = await FritzBoxStorage.instance.getConfig();
    if (config?.fritzosVersion == null) return false;
    return _compareVersion(config!.fritzosVersion!, _minCardDavVersion) >= 0;
  }

  /// Compares two version strings (e.g., "7.50" vs "7.20").
  ///
  /// Returns negative if v1 < v2, 0 if equal, positive if v1 > v2.
  int _compareVersion(String v1, String v2) {
    final parts1 = v1.split('.').map((e) => int.tryParse(e) ?? 0).toList();
    final parts2 = v2.split('.').map((e) => int.tryParse(e) ?? 0).toList();

    for (int i = 0; i < max(parts1.length, parts2.length); i++) {
      final p1 = i < parts1.length ? parts1[i] : 0;
      final p2 = i < parts2.length ? parts2[i] : 0;
      if (p1 != p2) return p1 - p2;
    }
    return 0;
  }

  /// Gets all configured online phonebooks from Fritz!Box.
  ///
  /// Returns a list of (index, info) tuples for all existing online phonebooks.
  Future<List<(int, OnlinePhonebookInfo)>> _getOnlinePhonebooks() async {
    final onTelService = _client?.onTel();
    if (onTelService == null) {
      throw Exception('OnTel service not available');
    }

    final numberOfEntries = await onTelService.getNumberOfEntries();
    if (kDebugMode) {
      print('_getOnlinePhonebooks: numberOfEntries=$numberOfEntries');
    }

    // Online phonebook indices are 1-based (1 to numberOfEntries)
    final List<(int, OnlinePhonebookInfo)> phonebooks = [];
    for (int index = 1; index <= numberOfEntries; index++) {
      if (kDebugMode) {
        print('_getOnlinePhonebooks: fetching index $index...');
      }
      try {
        final info = await onTelService.getInfoByIndex(index);
        if (kDebugMode) {
          print(
              '_getOnlinePhonebooks: [$index] name="${info.name}" url="${info.url}" serviceId="${info.serviceId}"');
        }
        phonebooks.add((index, info));
      } catch (e) {
        if (kDebugMode) {
          print('_getOnlinePhonebooks: ERROR at index $index: $e');
        }
        rethrow;
      }
    }
    return phonebooks;
  }

  /// Finds an existing PhoneBlock CardDAV configuration on the Fritz!Box.
  ///
  /// Returns the index if found, null otherwise.
  Future<int?> _findExistingCardDavConfig(
      List<(int, OnlinePhonebookInfo)> phonebooks) async {
    for (final (index, info) in phonebooks) {
      if (info.url.contains('phoneblock.net') ||
          info.name.toLowerCase().contains('phoneblock')) {
        if (kDebugMode) {
          print('_findExistingCardDavConfig: Found PhoneBlock config at index $index');
        }
        return index;
      }
    }
    if (kDebugMode) {
      print('_findExistingCardDavConfig: No existing PhoneBlock config found');
    }
    return null;
  }

  /// Gets the next available online phonebook index.
  ///
  /// Fritz!Box supports up to 10 online phonebook accounts.
  /// Indices are 1-based. To create a new entry, use numberOfEntries + 1.
  int _getNextOnlinePhonebookIndex(List<(int, OnlinePhonebookInfo)> phonebooks) {
    // Look for empty slot (URL is empty)
    for (final (index, info) in phonebooks) {
      if (info.url.isEmpty) {
        if (kDebugMode) {
          print('_getNextOnlinePhonebookIndex: Found empty slot at index $index');
        }
        return index;
      }
    }

    // No empty slot - use numberOfEntries + 1 to create new entry (1-based indices)
    final nextIndex = phonebooks.length + 1;
    if (kDebugMode) {
      print('_getNextOnlinePhonebookIndex: Using next index $nextIndex');
    }
    return nextIndex;
  }

  /// Configures CardDAV online phonebook on Fritz!Box.
  ///
  /// Creates or updates an online phonebook that syncs with PhoneBlock's
  /// CardDAV server for spam contact blocklist.
  ///
  /// [phoneBlockUsername] - The user's PhoneBlock login name for the CardDAV URL.
  /// [phoneBlockToken] - The user's PhoneBlock API token for authentication.
  Future<void> configureCardDav({
    required String phoneBlockUsername,
    required String phoneBlockToken,
  }) async {
    if (kDebugMode) {
      print('configureCardDav: Starting configuration for user $phoneBlockUsername');
    }

    if (!isConnected) {
      throw Exception('Not connected to Fritz!Box');
    }

    final onTelService = _client?.onTel();
    if (onTelService == null) {
      throw Exception('OnTel service not available');
    }

    // Get all existing online phonebooks
    final phonebooks = await _getOnlinePhonebooks();

    // Check for existing PhoneBlock configuration
    int? existingIndex = await _findExistingCardDavConfig(phonebooks);

    // Determine which index to use
    int index;
    if (existingIndex != null) {
      index = existingIndex;
      if (kDebugMode) {
        print('configureCardDav: Reusing existing slot at index $index');
      }
    } else {
      index = _getNextOnlinePhonebookIndex(phonebooks);
      if (kDebugMode) {
        print('configureCardDav: Using new slot at index $index');
      }
    }

    // Build CardDAV URL using the app's context path
    final carddavUrl =
        'https://phoneblock.net$contextPath/contacts/addresses/$phoneBlockUsername/';

    if (kDebugMode) {
      print('configureCardDav: Setting config at index $index');
      print('  url: $carddavUrl');
      print('  serviceId: carddav.generic');
      print('  username: $phoneBlockUsername');
      print('  name: PhoneBlock SPAM');
    }

    // Configure online phonebook
    // serviceId identifies the provider type: 'carddav.generic' = CardDAV-Anbieter
    try {
      await onTelService.setConfigByIndex(
        index: index,
        enable: true,
        url: carddavUrl,
        serviceId: 'carddav.generic',
        username: phoneBlockUsername,
        password: phoneBlockToken,
        name: 'PhoneBlock SPAM',
      );
      if (kDebugMode) {
        print('configureCardDav: setConfigByIndex succeeded');
      }
    } catch (e, stackTrace) {
      if (kDebugMode) {
        print('configureCardDav: setConfigByIndex FAILED at index $index');
        print('  Error: $e');
        print('  Stack: $stackTrace');
      }
      rethrow;
    }

    // Save configuration to local storage
    await FritzBoxStorage.instance.updateConfig(
      blocklistMode: BlocklistMode.cardDav,
      phonebookId: index.toString(),
    );

    if (kDebugMode) {
      print('configureCardDav: Configuration complete, saved phonebookId=$index');
    }
  }

  /// Removes CardDAV configuration from Fritz!Box.
  Future<void> removeCardDav() async {
    final config = await FritzBoxStorage.instance.getConfig();
    if (config?.blocklistMode != BlocklistMode.cardDav) {
      // Not configured for CardDAV
      return;
    }

    if (isConnected && config?.phonebookId != null) {
      final onTelService = _client?.onTel();
      if (onTelService != null) {
        final index = int.tryParse(config!.phonebookId!);
        if (index != null) {
          try {
            await onTelService.deleteByIndex(index);
            if (kDebugMode) {
              print('Removed CardDAV online phonebook at index $index');
            }
          } catch (e) {
            if (kDebugMode) {
              print('Error removing CardDAV config: $e');
            }
            // Continue anyway to clear local config
          }
        }
      }
    }

    // Clear local configuration
    await FritzBoxStorage.instance.updateConfig(
      blocklistMode: BlocklistMode.none,
      phonebookId: null,
    );
  }

  /// Verifies CardDAV configuration status on Fritz!Box.
  ///
  /// Returns the current sync status.
  Future<CardDavStatus> verifyCardDav() async {
    final config = await FritzBoxStorage.instance.getConfig();
    if (config?.blocklistMode != BlocklistMode.cardDav ||
        config?.phonebookId == null) {
      return CardDavStatus.notConfigured;
    }

    if (!isConnected) {
      // Can't verify when offline, assume it's pending
      return CardDavStatus.syncPending;
    }

    final onTelService = _client?.onTel();
    if (onTelService == null) {
      return CardDavStatus.error;
    }

    final index = int.tryParse(config!.phonebookId!);
    if (index == null) {
      return CardDavStatus.error;
    }

    try {
      final info = await onTelService.getInfoByIndex(index);

      if (kDebugMode) {
        print('verifyCardDav: index=$index enable=${info.enable} status="${info.status}" lastConnect="${info.lastConnect}"');
      }

      if (!info.enable) {
        return CardDavStatus.disabled;
      }

      // Fritz!Box returns "0" for success
      if (info.status == '0') {
        return CardDavStatus.synced;
      } else if (info.lastConnect.isNotEmpty) {
        return CardDavStatus.synced;
      } else {
        return CardDavStatus.syncPending;
      }
    } catch (e) {
      if (kDebugMode) {
        print('verifyCardDav: ERROR $e');
      }
      return CardDavStatus.error;
    }
  }

  /// Gets detailed information about the current CardDAV configuration.
  ///
  /// Returns null if CardDAV is not configured or not connected.
  Future<OnlinePhonebookInfo?> getCardDavInfo() async {
    final config = await FritzBoxStorage.instance.getConfig();
    if (config?.blocklistMode != BlocklistMode.cardDav ||
        config?.phonebookId == null) {
      return null;
    }

    if (!isConnected) {
      return null;
    }

    final onTelService = _client?.onTel();
    if (onTelService == null) {
      return null;
    }

    final index = int.tryParse(config!.phonebookId!);
    if (index == null) {
      return null;
    }

    try {
      return await onTelService.getInfoByIndex(index);
    } catch (e) {
      if (kDebugMode) {
        print('Error getting CardDAV info: $e');
      }
      return null;
    }
  }
}
