import 'dart:convert';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:fritz_tr064/fritz_tr064.dart';
import 'package:http/http.dart' as http;
import 'package:jsontool/jsontool.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_models.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_storage.dart';
import 'package:phoneblock_mobile/main.dart';
import 'package:phoneblock_mobile/state.dart';
import 'package:phoneblock_mobile/storage.dart';
import 'package:phoneblock_shared/phoneblock_shared.dart' hide getAuthToken;

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

  /// Discovers the default username for the Fritz!Box at the given host.
  ///
  /// Connects anonymously (without credentials) and queries the
  /// LANConfigSecurity service for the default login username.
  /// Returns null if the username cannot be determined.
  Future<String?> getDefaultUsername(String host) async {
    final anonClient = Tr64Client(
      host: host,
      username: '',
      password: '',
    );

    try {
      await anonClient.connect();

      final security = anonClient.lanConfigSecurity();
      if (security == null) {
        if (kDebugMode) {
          print('LANConfigSecurity service not available');
        }
        return null;
      }

      return await security.getDefaultUsername();
    } catch (e) {
      if (kDebugMode) {
        print('Error discovering default username: $e');
      }
      return null;
    } finally {
      anonClient.close();
    }
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
  /// Returns a list of (onlineIndex, info) tuples for all online phonebooks.
  /// The online index is 1-based and used with index-based methods like
  /// [OnTelService.getInfoByIndex], [OnTelService.setConfigByIndex], and
  /// [OnTelService.deleteByIndex].
  ///
  /// Note: Online phonebook indices are separate from phonebook IDs.
  /// See [OnTelService] documentation for details.
  Future<List<(int, OnlinePhonebookInfo)>> _getOnlinePhonebooks() async {
    final onTelService = _client?.onTel();
    if (onTelService == null) {
      throw Exception('OnTel service not available');
    }

    final numberOfEntries = await onTelService.getNumberOfEntries();
    if (kDebugMode) {
      print('_getOnlinePhonebooks: numberOfEntries=$numberOfEntries');
    }

    // Online phonebook indices are 1-based
    final List<(int, OnlinePhonebookInfo)> phonebooks = [];
    for (int index = 1; index <= numberOfEntries; index++) {
      try {
        final info = await onTelService.getInfoByIndex(index);
        if (kDebugMode) {
          print(
              '_getOnlinePhonebooks: [$index] name="${info.name}" url="${info.url}" serviceId="${info.serviceId}" status=${info.status}');
        }
        phonebooks.add((index, info));
      } catch (e) {
        if (kDebugMode) {
          print('_getOnlinePhonebooks: [$index] error: $e');
        }
      }
    }
    return phonebooks;
  }

  /// Finds an existing PhoneBlock CardDAV configuration on the Fritz!Box.
  ///
  /// Matches by URL including context path (test vs prod environment).
  /// Returns the online phonebook index if found, null otherwise.
  int? _findExistingCardDavConfig(List<(int, OnlinePhonebookInfo)> phonebooks) {
    // Match URL pattern: https://phoneblock.net{contextPath}/contacts/
    final urlPattern = 'phoneblock.net$contextPath/contacts/';
    for (final (onlineIndex, info) in phonebooks) {
      if (info.url.contains(urlPattern)) {
        if (kDebugMode) {
          print('_findExistingCardDavConfig: Found online index $onlineIndex');
        }
        return onlineIndex;
      }
    }
    if (kDebugMode) {
      print('_findExistingCardDavConfig: Not found (pattern: $urlPattern)');
    }
    return null;
  }

  /// Configures CardDAV online phonebook on Fritz!Box.
  ///
  /// Creates or updates an online phonebook that syncs with PhoneBlock's
  /// CardDAV server for spam contact blocklist.
  ///
  /// To create a new online phonebook, uses [OnTelService.setConfigByIndex]
  /// with index = numberOfEntries + 1. To update an existing one, reuses
  /// its online index.
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

    // Check for existing PhoneBlock configuration
    final phonebooks = await _getOnlinePhonebooks();
    final existingIndex = _findExistingCardDavConfig(phonebooks);

    // Determine which online phonebook index to use
    int onlineIndex;
    if (existingIndex != null) {
      onlineIndex = existingIndex;
      if (kDebugMode) {
        print('configureCardDav: Reusing existing online index $onlineIndex');
      }
    } else {
      // Create new online phonebook: use numberOfEntries + 1
      final numberOfEntries = await onTelService.getNumberOfEntries();
      onlineIndex = numberOfEntries + 1;
      if (kDebugMode) {
        print('configureCardDav: Creating new online phonebook at index $onlineIndex');
      }
    }

    // Build CardDAV URL using the app's context path
    final carddavUrl =
        'https://phoneblock.net$contextPath/contacts/addresses/$phoneBlockUsername/';

    if (kDebugMode) {
      print('configureCardDav: Setting config for online index $onlineIndex');
      print('  url: $carddavUrl');
      print('  serviceId: ${OnlinePhonebookServiceId.cardDav}');
      print('  username: $phoneBlockUsername');
    }

    // Configure the online phonebook as CardDAV phonebook
    try {
      await onTelService.setConfigByIndex(
        index: onlineIndex,
        enable: true,
        url: carddavUrl,
        serviceId: OnlinePhonebookServiceId.cardDav,
        username: phoneBlockUsername,
        password: phoneBlockToken,
        name: kDebugMode ? 'PhoneBlock SPAM (Test)' : 'PhoneBlock SPAM',
      );
      if (kDebugMode) {
        print('configureCardDav: setConfigByIndex succeeded');
      }
    } catch (e, stackTrace) {
      if (kDebugMode) {
        print('configureCardDav: setConfigByIndex FAILED for index $onlineIndex');
        print('  Error: $e');
        print('  Stack: $stackTrace');
      }
      rethrow;
    }

    // Save configuration to local storage
    await FritzBoxStorage.instance.updateConfig(
      blocklistMode: BlocklistMode.cardDav,
    );

    if (kDebugMode) {
      print('configureCardDav: Configuration complete');
    }
  }

  /// Removes CardDAV configuration from Fritz!Box.
  Future<void> removeCardDav() async {
    final config = await FritzBoxStorage.instance.getConfig();
    if (config?.blocklistMode != BlocklistMode.cardDav) {
      // Not configured for CardDAV
      return;
    }

    if (isConnected) {
      final onTelService = _client?.onTel();
      if (onTelService != null) {
        try {
          // Find the PhoneBlock online phonebook by URL
          final phonebooks = await _getOnlinePhonebooks();
          final onlineIndex = _findExistingCardDavConfig(phonebooks);

          if (onlineIndex != null) {
            await onTelService.deleteByIndex(onlineIndex);
            if (kDebugMode) {
              print('Removed CardDAV phonebook at online index $onlineIndex');
            }
          } else {
            if (kDebugMode) {
              print('PhoneBlock phonebook not found, nothing to delete');
            }
          }
        } catch (e) {
          if (kDebugMode) {
            print('Error removing CardDAV config: $e');
          }
          // Continue anyway to clear local config
        }
      }
    }

    // Clear local configuration
    await FritzBoxStorage.instance.updateConfig(
      blocklistMode: BlocklistMode.none,
    );
  }

  /// Syncs the local blocklistMode config with the actual Fritz!Box state.
  ///
  /// Calls [verifyCardDav] and updates local config bidirectionally:
  /// - If CardDAV is active on Fritz!Box but local config says [BlocklistMode.none],
  ///   updates local config to [BlocklistMode.cardDav].
  /// - If CardDAV is not configured on Fritz!Box but local config says
  ///   [BlocklistMode.cardDav], updates local config to [BlocklistMode.none].
  ///
  /// Returns the [CardDavStatus] from the Fritz!Box.
  Future<CardDavStatus> syncBlocklistMode() async {
    final cardDavStatus = await verifyCardDav();
    final config = await FritzBoxStorage.instance.getConfig();
    final localMode = config?.blocklistMode ?? BlocklistMode.none;

    if (cardDavStatus == CardDavStatus.notConfigured &&
        localMode == BlocklistMode.cardDav) {
      // Fritz!Box says not configured, but local thinks it is → reset local
      await FritzBoxStorage.instance.updateConfig(
        blocklistMode: BlocklistMode.none,
      );
    } else if (cardDavStatus != CardDavStatus.notConfigured &&
        localMode == BlocklistMode.none) {
      // Fritz!Box has CardDAV configured, but local doesn't know → update local
      await FritzBoxStorage.instance.updateConfig(
        blocklistMode: BlocklistMode.cardDav,
      );
    }

    return cardDavStatus;
  }

  /// Verifies CardDAV configuration status on Fritz!Box.
  ///
  /// Checks Fritz!Box directly for PhoneBlock phonebook (may have been
  /// configured manually via Fritz!Box UI).
  /// Returns the current sync status.
  Future<CardDavStatus> verifyCardDav() async {
    if (!isConnected) {
      return CardDavStatus.notConfigured;
    }

    try {
      // Find PhoneBlock phonebook by URL (may be configured manually)
      final phonebooks = await _getOnlinePhonebooks();
      final onlineIndex = _findExistingCardDavConfig(phonebooks);

      if (onlineIndex == null) {
        return CardDavStatus.notConfigured;
      }

      final info = phonebooks.firstWhere((p) => p.$1 == onlineIndex).$2;

      if (kDebugMode) {
        print('verifyCardDav: [$onlineIndex] enable=${info.enable} status=${info.status} lastConnect="${info.lastConnect}"');
      }

      if (!info.enable) {
        return CardDavStatus.disabled;
      }

      // Use OnlinePhonebookStatus constants for status interpretation
      if (info.status == OnlinePhonebookStatus.ok) {
        return CardDavStatus.synced;
      } else if (info.status == OnlinePhonebookStatus.synchronizing) {
        return CardDavStatus.syncPending;
      } else if (info.status > 0) {
        // Positive non-zero status is an error code
        // (e.g. authenticationFailure=11, resourceNotFound=20)
        return CardDavStatus.error;
      } else if (info.lastConnect.isEmpty) {
        // No status yet and no lastConnect means sync hasn't happened
        return CardDavStatus.syncPending;
      } else {
        // Has lastConnect but no definitive status - consider it synced
        return CardDavStatus.synced;
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
  /// Checks Fritz!Box directly for PhoneBlock phonebook (may have been
  /// configured manually via Fritz!Box UI).
  /// Returns null if not found or not connected.
  Future<OnlinePhonebookInfo?> getCardDavInfo() async {
    if (!isConnected) {
      return null;
    }

    try {
      final phonebooks = await _getOnlinePhonebooks();
      final onlineIndex = _findExistingCardDavConfig(phonebooks);

      if (onlineIndex == null) {
        return null;
      }

      return phonebooks.firstWhere((p) => p.$1 == onlineIndex).$2;
    } catch (e) {
      if (kDebugMode) {
        print('Error getting CardDAV info: $e');
      }
      return null;
    }
  }

  // -- Second Factor Authentication --

  /// UPnP error code for "second factor authentication required".
  static const String _secondFactorErrorCode = '866';

  /// The auth service used during an active 2FA challenge, if any.
  AuthService? _activeAuthService;

  /// Cancels an active second factor authentication challenge.
  ///
  /// The polling loop in [_withSecondFactor] will see the [SecondFactorState.stopped]
  /// state and abort the setup.
  Future<void> cancelSecondFactor() async {
    if (kDebugMode) {
      print('cancelSecondFactor: Stopping active 2FA challenge');
    }
    await _activeAuthService?.setConfig('stop');
  }

  /// Executes a Fritz!Box TR-064 action with second factor authentication
  /// retry.
  ///
  /// If the action fails with UPnP error 866, starts the 2FA challenge,
  /// reports [AnswerbotSetupStep.confirmingSecondFactor] via [onProgress],
  /// waits for the user to confirm on the Fritz!Box, and retries the action.
  Future<T> _withSecondFactor<T>({
    required Future<T> Function() action,
    required void Function(AnswerbotSetupStep) onProgress,
    void Function(List<AuthMethod>)? onSecondFactorMethods,
  }) async {
    try {
      return await action();
    } on SoapFaultException catch (e) {
      if (e.detail == null || !e.detail!.contains(_secondFactorErrorCode)) {
        rethrow;
      }

      // Start 2FA process
      if (kDebugMode) {
        print('_withSecondFactor: Error 866 detected, starting 2FA challenge');
        print('  faultCode=${e.faultCode} faultString=${e.faultString} detail=${e.detail}');
      }

      final authService = _client!.auth();
      if (authService == null) {
        if (kDebugMode) {
          print('_withSecondFactor: Auth service not available');
        }
        rethrow;
      }

      onProgress(AnswerbotSetupStep.confirmingSecondFactor);
      _activeAuthService = authService;
      try {
        final config = await authService.setConfig('start');
        if (kDebugMode) {
          print('_withSecondFactor: 2FA started, token=${config.token} state=${config.state.name} methods=${config.methods}');
        }

        // Include 2FA token in all subsequent SOAP requests per AVM spec section 6.4
        _client!.secondFactorToken = config.token;

        onSecondFactorMethods?.call(config.methods);

        // Poll for authentication (up to 2 minutes)
        for (int i = 0; i < 60; i++) {
          await Future.delayed(const Duration(seconds: 2));
          final state = await authService.getState();
          if (kDebugMode) {
            print('_withSecondFactor: Poll #$i state=${state.name}');
          }
          if (state == SecondFactorState.authenticated) {
            if (kDebugMode) {
              print('_withSecondFactor: 2FA confirmed, retrying action');
            }
            return await action();
          }
          if (state == SecondFactorState.stopped ||
              state == SecondFactorState.blocked ||
              state == SecondFactorState.failure) {
            throw Exception('Second factor authentication failed: ${state.name}');
          }
        }
        throw Exception('Second factor authentication timed out');
      } finally {
        _client!.secondFactorToken = null;
        _activeAuthService = null;
      }
    }
  }

  // -- Answer Bot Setup Methods --

  /// Phone name used for the SIP device on Fritz!Box.
  static const String _answerbotPhoneName = 'PhoneBlock Answerbot';

  /// Sets up the PhoneBlock answer bot on the connected Fritz!Box.
  ///
  /// This automates the full setup flow:
  /// 1. Creates a new bot on the PhoneBlock server
  /// 2. Detects or configures external access (MyFritz or PhoneBlock DynDNS)
  /// 3. Registers a SIP device on Fritz!Box
  /// 4. Enables the bot and waits for SIP registration
  ///
  /// [onProgress] is called with each setup step for progress UI.
  /// Throws on failure; cleans up the server-side bot if partially created.
  Future<void> setupAnswerBot({
    required void Function(AnswerbotSetupStep) onProgress,
    void Function(List<AuthMethod>)? onSecondFactorMethods,
  }) async {
    if (!isConnected || _client == null) {
      throw Exception('Not connected to Fritz!Box');
    }

    int? createdBotId;

    try {
      // Step 1: Create bot on server
      onProgress(AnswerbotSetupStep.creatingBot);
      final createResponse = await sendRequest(CreateAnswerBot());
      if (createResponse.statusCode != 200) {
        throw Exception('Failed to create answer bot: ${createResponse.body}');
      }
      final creation = CreateAnswerbotResponse.read(
        JsonReader.fromString(createResponse.body),
      );
      createdBotId = creation.id;

      // Step 2: Detect external access via MyFritz
      onProgress(AnswerbotSetupStep.detectingAccess);
      bool hostConfigured = false;

      try {
        final myFritzService = _client!.myFritz();
        if (myFritzService != null) {
          final myFritzInfo = await myFritzService.getInfo();
          if (myFritzInfo.enabled && myFritzInfo.dynDNSName.isNotEmpty) {
            // Use MyFritz DynDNS name
            final enterHostResponse = await sendRequest(
              EnterHostName(id: creation.id, hostName: myFritzInfo.dynDNSName),
            );
            if (enterHostResponse.statusCode == 200) {
              hostConfigured = true;
              if (kDebugMode) {
                print('setupAnswerBot: Using MyFritz domain: ${myFritzInfo.dynDNSName}');
              }
            }
          }
        }
      } catch (e) {
        if (kDebugMode) {
          print('setupAnswerBot: MyFritz detection failed: $e');
        }
      }

      // Step 3: Fallback to PhoneBlock DynDNS
      if (!hostConfigured) {
        onProgress(AnswerbotSetupStep.configuringDynDns);

        final dynDnsResponse = await sendRequest(
          SetupDynDns(id: creation.id),
        );
        if (dynDnsResponse.statusCode != 200) {
          throw Exception('Failed to setup DynDNS: ${dynDnsResponse.body}');
        }
        final dynDns = SetupDynDnsResponse.read(
          JsonReader.fromString(dynDnsResponse.body),
        );

        // Configure Fritz!Box DynDNS via TR-064
        final remoteAccessService = _client!.remoteAccess();
        if (remoteAccessService == null) {
          throw Exception('RemoteAccess service not available on Fritz!Box');
        }

        final updateUrl = '$basePath/api/dynip?user=<username>&passwd=<passwd>&ip4=<ipaddr>&ip6=<ip6addr>';
        await remoteAccessService.setDDNSConfig(
          enabled: true,
          providerName: 'Benutzerdefiniert',
          updateURL: updateUrl,
          serverIPv4: '',
          serverIPv6: '',
          domain: dynDns.dyndnsDomain,
          username: dynDns.dyndnsUser,
          password: dynDns.dyndnsPassword,
          mode: DDNSMode.both,
        );

        // Wait for DynDNS registration
        onProgress(AnswerbotSetupStep.waitingForDynDns);
        bool dynDnsReady = false;
        for (int i = 0; i < 12; i++) {
          await Future.delayed(const Duration(milliseconds: 2500));
          final checkResponse = await sendRequest(
            CheckDynDns(id: creation.id),
          );
          if (checkResponse.statusCode == 200) {
            dynDnsReady = true;
            break;
          }
        }
        if (!dynDnsReady) {
          throw Exception('DynDNS registration timed out');
        }
      }

      // Step 4: Register SIP device on Fritz!Box
      onProgress(AnswerbotSetupStep.registeringSipDevice);
      final voipService = _client!.voip();
      if (voipService == null) {
        throw Exception('VoIP service not available on Fritz!Box');
      }

      // Find next available client index
      final numberOfClients = await voipService.getNumberOfClients();
      final clientIndex = numberOfClients; // 0-based, next slot

      final internalNumber = await _withSecondFactor(
        onProgress: onProgress,
        onSecondFactorMethods: onSecondFactorMethods,
        action: () => voipService.setClient4(
          clientIndex: clientIndex,
          password: creation.password,
          clientUsername: creation.userName,
          phoneName: _answerbotPhoneName,
          clientId: '',
          outGoingNumber: '',
          inComingNumbers: '',
        ),
      );

      if (kDebugMode) {
        print('setupAnswerBot: SIP device registered with internal number: $internalNumber');
      }

      // Step 5: Enable bot and wait for SIP registration
      onProgress(AnswerbotSetupStep.enablingBot);
      final enableResponse = await sendRequest(
        EnableAnswerBot(id: creation.id),
      );
      if (enableResponse.statusCode != 200) {
        throw Exception('Failed to enable answer bot: ${enableResponse.body}');
      }

      onProgress(AnswerbotSetupStep.waitingForRegistration);
      const int maxAttempts = 20;
      for (int n = 0; n < maxAttempts; n++) {
        final checkResponse = await sendRequest(
          CheckAnswerBot(id: creation.id),
        );
        if (checkResponse.statusCode == 200) {
          break;
        }
        if (checkResponse.statusCode != 409 || n == maxAttempts - 1) {
          throw Exception('Answer bot registration failed: ${checkResponse.body}');
        }
        await Future.delayed(const Duration(milliseconds: 2500));
      }

      // Step 6: Save config
      onProgress(AnswerbotSetupStep.complete);
      await FritzBoxStorage.instance.updateConfig(
        answerbotEnabled: true,
        answerbotId: creation.id,
        sipDeviceId: internalNumber,
      );
    } catch (e) {
      // Clean up server-side bot on failure
      if (createdBotId != null) {
        try {
          await sendRequest(DeleteAnswerBot(id: createdBotId));
        } catch (_) {
          // Best effort cleanup
        }
      }
      rethrow;
    }
  }

  /// Removes the PhoneBlock answer bot from Fritz!Box and the server.
  Future<void> removeAnswerBot() async {
    final config = await FritzBoxStorage.instance.getConfig();
    final botId = config?.answerbotId;

    // Disable and delete on server
    if (botId != null) {
      try {
        await sendRequest(DisableAnswerBot(id: botId));
      } catch (e) {
        if (kDebugMode) {
          print('removeAnswerBot: Failed to disable bot on server: $e');
        }
      }
      try {
        await sendRequest(DeleteAnswerBot(id: botId));
      } catch (e) {
        if (kDebugMode) {
          print('removeAnswerBot: Failed to delete bot on server: $e');
        }
      }
    }

    // Remove SIP device from Fritz!Box
    if (isConnected && _client != null) {
      final voipService = _client!.voip();
      if (voipService != null) {
        try {
          final numberOfClients = await voipService.getNumberOfClients();
          for (int i = 0; i < numberOfClients; i++) {
            try {
              final client = await voipService.getClient3(i);
              if (client.phoneName == _answerbotPhoneName) {
                await voipService.deleteClient(i);
                if (kDebugMode) {
                  print('removeAnswerBot: Removed SIP device at index $i');
                }
                break;
              }
            } catch (e) {
              if (kDebugMode) {
                print('removeAnswerBot: Error checking client $i: $e');
              }
            }
          }
        } catch (e) {
          if (kDebugMode) {
            print('removeAnswerBot: Error listing SIP clients: $e');
          }
        }
      }
    }

    // Clear local config
    final existing = await FritzBoxStorage.instance.getConfig();
    if (existing != null) {
      await FritzBoxStorage.instance.saveConfig(FritzBoxConfig(
        id: existing.id,
        host: existing.host,
        fritzosVersion: existing.fritzosVersion,
        username: existing.username,
        password: existing.password,
        blocklistMode: existing.blocklistMode,
        answerbotEnabled: false,
        answerbotId: null,
        lastFetchTimestamp: existing.lastFetchTimestamp,
        blocklistVersion: existing.blocklistVersion,
        phonebookId: existing.phonebookId,
        sipDeviceId: null,
        createdAt: existing.createdAt,
        updatedAt: DateTime.now().millisecondsSinceEpoch,
      ));
    }
  }
}

/// Progress steps for the answer bot setup flow.
enum AnswerbotSetupStep {
  /// Creating the bot on the server.
  creatingBot,

  /// Detecting external access (MyFritz).
  detectingAccess,

  /// Configuring DynDNS on the Fritz!Box.
  configuringDynDns,

  /// Waiting for DynDNS registration to propagate.
  waitingForDynDns,

  /// Registering SIP device on the Fritz!Box.
  registeringSipDevice,

  /// Waiting for second factor authentication on the Fritz!Box.
  confirmingSecondFactor,

  /// Enabling the bot on the server.
  enablingBot,

  /// Waiting for SIP registration to complete.
  waitingForRegistration,

  /// Setup completed successfully.
  complete,
}
