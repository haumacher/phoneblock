import 'dart:convert';
import 'dart:math';

import 'package:crypto/crypto.dart';
import 'package:flutter/foundation.dart';
import 'package:fritz_tr064/fritz_tr064.dart';
import 'package:http/http.dart' as http;
import 'package:jsontool/jsontool.dart';
import 'package:phoneblock_mobile/fritzbox/call_list_import.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_login.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_models.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_rights.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_status.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_storage.dart';
import 'package:phoneblock_mobile/logging/app_logger.dart';
import 'package:phoneblock_mobile/main.dart';
import 'package:phoneblock_mobile/storage.dart';
import 'package:phoneblock_shared/phoneblock_shared.dart' hide getAuthToken;

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
  } catch (e, s) {
    AppLogger.instance.error('fritzbox', 'Error normalizing phone number', e, s);
    return null;
  }
}

/// Service for communicating with Fritz!Box via TR-064 protocol.
class FritzBoxService {
  static final FritzBoxService instance = FritzBoxService._init();

  Tr64Client? _client;
  FritzBoxConnectionState _connectionState = FritzBoxConnectionState.notConfigured;

  FritzBoxService._init();

  /// Creates a service talking to the given, already connected [client]
  /// without stored credentials or configuration.
  @visibleForTesting
  FritzBoxService.forTest(Tr64Client client)
      : _client = client,
        _connectionState = FritzBoxConnectionState.connected;

  /// Current connection state.
  FritzBoxConnectionState get connectionState => _connectionState;

  /// Returns true if connected to Fritz!Box.
  bool get isConnected => _connectionState == FritzBoxConnectionState.connected;

  /// Returns true if Fritz!Box credentials are stored (configured).
  bool get isConfigured => _connectionState != FritzBoxConnectionState.notConfigured;

  /// Initializes the service by loading stored credentials.
  Future<void> initialize() async {
    final credentials = await FritzBoxStorage.instance.getCredentials();
    if (credentials == null) {
      _connectionState = FritzBoxConnectionState.notConfigured;
      return;
    }

    // Mark as configured (offline) before the slow connection attempt,
    // so callers can distinguish "not configured" from "connecting".
    _connectionState = FritzBoxConnectionState.offline;

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

      // Verify the login and read model and FRITZ!OS version. Works for a
      // Fritz!Box user that only has the call list right (issue #268).
      final deviceInfo = await verifyLogin(_client!);
      if (deviceInfo == null) {
        _connectionState = FritzBoxConnectionState.error;
        return false;
      }

      _connectionState = FritzBoxConnectionState.connected;

      // Query Fritz!Box country and area code for number normalization
      String? countryCode;
      String? intlPrefix;
      String? trunkPrefix;
      try {
        final voipService = _client!.voip();
        if (voipService != null) {
          final cc = await voipService.getVoIPCommonCountryCode();
          countryCode = cc.lkz;
          intlPrefix = cc.lkzPrefix;

          final ac = await voipService.getVoIPCommonAreaCode();
          trunkPrefix = ac.okzPrefix;
        }
      } catch (e, s) {
        AppLogger.instance.error('fritzbox', 'Fritz!Box country code query failed', e, s);
        // Non-fatal: defaults will be used
      }

      await FritzBoxStorage.instance.updateConfig(
        host: credentials.host,
        fritzosVersion: deviceInfo.fritzosVersion,
        countryCode: countryCode,
        intlPrefix: intlPrefix,
        trunkPrefix: trunkPrefix,
      );

      return true;
    } catch (e, s) {
      AppLogger.instance.error('fritzbox', 'Fritz!Box connection error', e, s);
      _connectionState = FritzBoxConnectionState.offline;
      return false;
    }
  }

  /// Executes a TR-064 action, reconnecting once if the connection is stale.
  ///
  /// If [action] throws (e.g. due to an HTTP 503 from a stale session),
  /// re-establishes the TR-064 connection and retries the action once.
  ///
  /// A refusal because the Fritz!Box user lacks the right "FRITZ!Box
  /// Einstellungen" is thrown as [FritzBoxInsufficientRightsException]
  /// without reconnecting - a new connection would be refused the same way.
  Future<T> _withReconnect<T>(Future<T> Function() action) async {
    try {
      return await guardRights(action);
    } on FritzBoxInsufficientRightsException catch (e) {
      AppLogger.instance.warn('fritzbox', '_withReconnect: Refused, $e');
      rethrow;
    } catch (e, s) {
      AppLogger.instance.error('fritzbox', '_withReconnect: Action failed, attempting reconnect', e, s);
      final credentials = await FritzBoxStorage.instance.getCredentials();
      if (credentials == null) rethrow;
      final success = await _connect(credentials);
      if (!success) rethrow;
      return await guardRights(action);
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
        AppLogger.instance.warn('fritzbox', 'LANConfigSecurity service not available');
        return null;
      }

      return await security.getDefaultUsername();
    } catch (e, s) {
      AppLogger.instance.error('fritzbox', 'Error discovering default username', e, s);
      return null;
    } finally {
      anonClient.close();
    }
  }

  /// Gets device information from Fritz!Box (see [readDeviceInfo]).
  ///
  /// Returns null if the Fritz!Box could not be queried.
  Future<FritzBoxDeviceInfo?> getDeviceInfo() async {
    if (_client == null) return null;

    try {
      return await readDeviceInfo(_client!);
    } catch (e, s) {
      AppLogger.instance.error('fritzbox', 'Error getting device info', e, s);
      return null;
    }
  }

  /// Fetches the parsed call list from the Fritz!Box, limited to the last
  /// [days] days (all entries if null).
  ///
  /// Returns null if the list could not be fetched.
  Future<List<CallListEntry>?> _fetchCallListEntries({int? days}) async {
    if (_client == null) return null;

    try {
      final onTelService = _client!.onTel();
      if (onTelService == null) {
        AppLogger.instance.warn('fritzbox', 'OnTel service not available');
        return null;
      }
      return await onTelService.getCallListEntries(days: days);
    } catch (e, s) {
      AppLogger.instance.error('fritzbox', 'Error getting call list', e, s);
      return null;
    }
  }

  /// Syncs call list from Fritz!Box with spam enrichment from PhoneBlock API.
  ///
  /// New calls are recognized by their Fritz!Box call ID (see
  /// [planCallListImport]), not by time: a call still running during a sync
  /// and minute-rounded start times must not get lost (issue #361).
  ///
  /// Returns the list of newly inserted call IDs.
  Future<List<int>> syncCallList() async {
    if (!isConnected) return [];

    final retentionDays = await getRetentionDays();

    final config = await FritzBoxStorage.instance.getConfig();
    final countryCode = config?.countryCode ?? '49';
    final intlPrefix = config?.intlPrefix ?? '00';
    final trunkPrefix = config?.trunkPrefix ?? '0';
    final watermark = config?.lastCallId;
    final logKnownCallers = config?.logKnownCallers ?? true;

    // Fetch calls from Fritz!Box, limited by retention period
    // Use null for days if retention is infinite
    final days = retentionDays == retentionInfinite ? null : retentionDays;
    final entries = await _fetchCallListEntries(days: days);
    if (entries == null) return [];

    final plan = planCallListImport(
      entries,
      watermark: watermark,
      logKnownCallers: logKnownCallers,
      importedFritzboxIds:
          await _existingFritzboxIds(entries.map((e) => e.id.toString())),
      countryCode: countryCode,
      intlPrefix: intlPrefix,
      trunkPrefix: trunkPrefix,
    );

    final log = AppLogger.instance;
    if (plan.listReset) {
      log.info('fritzbox', 'Call list on the Fritz!Box was reset '
          '(all IDs below last handled ID $watermark), importing anew');
    } else if (watermark == null && entries.isNotEmpty) {
      log.info('fritzbox', 'No call ID watermark yet, '
          'deduplicating ${entries.length} entries by Fritz!Box ID');
    }
    log.info('fritzbox', 'Call list: ${entries.length} entries, '
        '${plan.belowWatermark} already handled, '
        '${plan.toImport.length} to import, ${plan.skipped.length} skipped');
    for (final skipped in plan.skipped) {
      log.info('fritzbox', 'Skipping $skipped');
    }

    final authToken = await getAuthToken();
    final db = ScreenedCallsDatabase.instance;

    final insertedIds = <int>[];
    for (final item in plan.toImport) {
      final call = item.lookup
          ? await _enrichCallWithSpamInfo(item.call, authToken)
          : item.call;
      final insertedCall = await db.insertScreenedCall(call);
      if (insertedCall.id != null) {
        insertedIds.add(insertedCall.id!);
      }
    }

    await FritzBoxStorage.instance.updateConfig(
      lastFetchTimestamp: DateTime.now().millisecondsSinceEpoch,
      lastCallId: plan.watermark,
    );

    // Clean up old calls based on retention period
    await db.deleteOldScreenedCalls(retentionDays);

    return insertedIds;
  }

  /// Returns those of the given Fritz!Box call IDs that are already stored.
  Future<Set<String>> _existingFritzboxIds(Iterable<String> fritzboxIds) async {
    final ids = fritzboxIds.toList();
    if (ids.isEmpty) return {};
    final db = await ScreenedCallsDatabase.instance.database;
    final result = <String>{};
    // Stay below SQLite's limit of bound parameters per statement.
    const chunkSize = 500;
    for (var i = 0; i < ids.length; i += chunkSize) {
      final chunk = ids.sublist(i, min(i + chunkSize, ids.length));
      final rows = await db.query(
        'screened_calls',
        columns: ['fritzboxId'],
        where: 'fritzboxId IN (${List.filled(chunk.length, '?').join(',')})',
        whereArgs: chunk,
      );
      for (final row in rows) {
        result.add(row['fritzboxId'] as String);
      }
    }
    return result;
  }

  /// Enriches a call with spam information from PhoneBlock API.
  Future<ScreenedCall> _enrichCallWithSpamInfo(ScreenedCall call, String? authToken) async {
    if (authToken == null) return call;

    try {
      final sha1Hash = sha1.convert(utf8.encode(call.phoneNumber)).toString().toUpperCase();
      final url = '$pbBaseUrl/api/check?sha1=$sha1Hash&format=json';
      final response = await http.get(
        Uri.parse(url),
        headers: {
          'Authorization': 'Bearer $authToken',
          'User-Agent': 'PhoneBlockMobile/$appVersion',
        },
      );

      if (response.statusCode == 200) {
        final data = json.decode(response.body) as Map<String, dynamic>;
        return call.copyWithApiResponse(data);
      }
    } catch (e, s) {
      AppLogger.instance.error('fritzbox', 'Error enriching call with spam info', e, s);
    }

    return call;
  }

  /// Checks if currently on the home network (can reach Fritz!Box).
  ///
  /// If the existing connection is stale (e.g. HTTP 503), attempts to
  /// re-establish it before returning false.
  Future<bool> checkConnection() async {
    final credentials = await FritzBoxStorage.instance.getCredentials();
    if (credentials == null) {
      _connectionState = FritzBoxConnectionState.notConfigured;
      return false;
    }

    if (_client != null) {
      try {
        final deviceInfo = await getDeviceInfo();
        if (deviceInfo != null) {
          _connectionState = FritzBoxConnectionState.connected;
          return true;
        }
      } catch (_) {
        // Connection stale, fall through to reconnect
      }
    }

    // No client yet, or existing connection is stale — (re)connect
    return await _connect(credentials);
  }

  // -- CardDAV Configuration Methods --

  /// Checks if the connected Fritz!Box supports CardDAV (FRITZ!OS 7.20+).
  Future<bool> supportsCardDav() async {
    final config = await FritzBoxStorage.instance.getConfig();
    if (config?.fritzosVersion == null) return false;
    return supportsCardDavVersion(config!.fritzosVersion!);
  }

  /// Gets all configured online phonebooks from Fritz!Box.
  ///
  /// Returns the (onlineIndex, info) tuples of all online phonebooks that
  /// could be read; `complete` is false if reading any entry failed.
  /// The online index is 1-based and used with index-based methods like
  /// [OnTelService.getInfoByIndex], [OnTelService.setConfigByIndex], and
  /// [OnTelService.deleteByIndex].
  ///
  /// Note: Online phonebook indices are separate from phonebook IDs.
  /// See [OnTelService] documentation for details.
  Future<OnlinePhonebooks> _getOnlinePhonebooks() async {
    final onTelService = _client?.onTel();
    if (onTelService == null) {
      throw Exception('OnTel service not available');
    }

    final numberOfEntries = await onTelService.getNumberOfEntries();
    AppLogger.instance.info('fritzbox', '_getOnlinePhonebooks: numberOfEntries=$numberOfEntries');

    // Online phonebook indices are 1-based
    final List<(int, OnlinePhonebookInfo)> phonebooks = [];
    var complete = true;
    for (int index = 1; index <= numberOfEntries; index++) {
      try {
        final info = await onTelService.getInfoByIndex(index);
        AppLogger.instance.info('fritzbox', '_getOnlinePhonebooks: [$index] name="${info.name}" url="${info.url}" serviceId="${info.serviceId}" status=${info.status}');
        phonebooks.add((index, info));
      } catch (e, s) {
        AppLogger.instance.error('fritzbox', '_getOnlinePhonebooks: [$index] error', e, s);
        complete = false;
      }
    }
    return (entries: phonebooks, complete: complete);
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
        AppLogger.instance.info('fritzbox', '_findExistingCardDavConfig: Found online index $onlineIndex');
        return onlineIndex;
      }
    }
    AppLogger.instance.info('fritzbox', '_findExistingCardDavConfig: Not found (pattern: $urlPattern)');
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
    AppLogger.instance.info('fritzbox', 'configureCardDav: Starting configuration for user $phoneBlockUsername');

    if (!isConnected) {
      throw Exception('Not connected to Fritz!Box');
    }

    await _withReconnect(() async {
      final onTelService = _client?.onTel();
      if (onTelService == null) {
        throw Exception('OnTel service not available');
      }

      // Check for existing PhoneBlock configuration
      final phonebooks = await _getOnlinePhonebooks();
      final existingIndex = _findExistingCardDavConfig(phonebooks.entries);
      if (existingIndex == null && !phonebooks.complete) {
        // An unreadable entry may be the PhoneBlock phonebook; creating a
        // new one could duplicate it.
        throw Exception('Not all online phonebooks could be read');
      }

      // Determine which online phonebook index to use
      int onlineIndex;
      if (existingIndex != null) {
        onlineIndex = existingIndex;
        AppLogger.instance.info('fritzbox', 'configureCardDav: Reusing existing online index $onlineIndex');
      } else {
        // Create new online phonebook: use numberOfEntries + 1
        final numberOfEntries = await onTelService.getNumberOfEntries();
        onlineIndex = numberOfEntries + 1;
        AppLogger.instance.info('fritzbox', 'configureCardDav: Creating new online phonebook at index $onlineIndex');
      }

      // Build CardDAV URL using the app's context path
      final carddavUrl =
          'https://phoneblock.net$contextPath/contacts/addresses/$phoneBlockUsername/';

      AppLogger.instance.info('fritzbox', 'configureCardDav: Setting config for online index $onlineIndex, url: $carddavUrl, serviceId: ${OnlinePhonebookServiceId.cardDav}, username: $phoneBlockUsername');

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
        AppLogger.instance.info('fritzbox', 'configureCardDav: setConfigByIndex succeeded');
      } catch (e, stackTrace) {
        AppLogger.instance.error('fritzbox', 'configureCardDav: setConfigByIndex FAILED for index $onlineIndex', e, stackTrace);
        rethrow;
      }
    });

    // Save configuration to local storage
    await FritzBoxStorage.instance.updateConfig(
      blocklistMode: BlocklistMode.cardDav,
    );

    AppLogger.instance.info('fritzbox', 'configureCardDav: Configuration complete');
  }

  /// Removes CardDAV configuration from Fritz!Box.
  ///
  /// Throws [FritzBoxInsufficientRightsException] if the Fritz!Box refuses
  /// to delete the phonebook; the local configuration is kept then, since
  /// the phonebook is still on the Fritz!Box.
  Future<void> removeCardDav() async {
    final config = await FritzBoxStorage.instance.getConfig();
    if (config?.blocklistMode != BlocklistMode.cardDav) {
      // Not configured for CardDAV
      return;
    }

    if (isConnected && _client != null) {
      try {
        await _withReconnect(() async {
          final onTelService = _client?.onTel();
          if (onTelService == null) return;

          // Find the PhoneBlock online phonebook by URL
          final phonebooks = await _getOnlinePhonebooks();
          final onlineIndex = _findExistingCardDavConfig(phonebooks.entries);

          if (onlineIndex != null) {
            await onTelService.deleteByIndex(onlineIndex);
            AppLogger.instance.info('fritzbox', 'Removed CardDAV phonebook at online index $onlineIndex');
          } else if (!phonebooks.complete) {
            throw Exception('Not all online phonebooks could be read, PhoneBlock phonebook not removed');
          } else {
            AppLogger.instance.info('fritzbox', 'PhoneBlock phonebook not found, nothing to delete');
          }
        });
      } on FritzBoxInsufficientRightsException {
        rethrow;
      } catch (e, s) {
        AppLogger.instance.error('fritzbox', 'Error removing CardDAV config', e, s);
        // Continue anyway to clear local config
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
  /// - If the online phonebooks could not be queried, the local config is
  ///   left unchanged.
  ///
  /// Returns the [CardDavStatus] from the Fritz!Box.
  Future<CardDavStatus> syncBlocklistMode() async {
    final cardDavStatus = await verifyCardDav();
    final config = await FritzBoxStorage.instance.getConfig();
    final localMode = config?.blocklistMode ?? BlocklistMode.none;

    final newMode = reconcileBlocklistMode(cardDavStatus, localMode);
    if (newMode != null) {
      await FritzBoxStorage.instance.updateConfig(blocklistMode: newMode);
    }

    return cardDavStatus;
  }

  /// Verifies CardDAV configuration status on Fritz!Box.
  ///
  /// Checks Fritz!Box directly for PhoneBlock phonebook (may have been
  /// configured manually via Fritz!Box UI).
  /// Returns the current sync status, [CardDavStatus.unknown] if the online
  /// phonebooks could not be queried.
  Future<CardDavStatus> verifyCardDav() async {
    if (!isConnected) {
      return CardDavStatus.notConfigured;
    }

    return resolveCardDavStatus(_getOnlinePhonebooks, _findExistingCardDavConfig);
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
      final phonebooks = (await _getOnlinePhonebooks()).entries;
      final onlineIndex = _findExistingCardDavConfig(phonebooks);

      if (onlineIndex == null) {
        return null;
      }

      return phonebooks.firstWhere((p) => p.$1 == onlineIndex).$2;
    } catch (e, s) {
      AppLogger.instance.error('fritzbox', 'Error getting CardDAV info', e, s);
      return null;
    }
  }

  // -- Second Factor Authentication --

  /// The web API two-factor handler used during an active 2FA challenge, if any.
  WebTwoFactor? _activeWebTwoFactor;

  /// Cancels an active second factor authentication challenge.
  Future<void> cancelSecondFactor() async {
    AppLogger.instance.info('fritzbox', 'cancelSecondFactor: Stopping active 2FA challenge');
    await _activeWebTwoFactor?.cancel();
  }

  /// Handles a [FormResult] from a web API operation, including 2FA flow.
  ///
  /// If the result is [FormOk], returns immediately.
  /// If the result is [FormValError], throws an exception.
  /// If the result is [FormTwoFactor], reports the step, invokes the
  /// [onSecondFactorMethods] callback, polls for confirmation, and calls
  /// [onConfirmed] to re-submit the form.
  Future<void> _handleWebApi2FA({
    required FormResult result,
    required WebTwoFactor twoFactor,
    required void Function(AnswerbotSetupStep) onProgress,
    required Future<FormResult> Function() onConfirmed,
    void Function(List<AuthMethod>)? onSecondFactorMethods,
  }) async {
    switch (result) {
      case FormOk():
        return;
      case FormValError():
        throw Exception('Fritz!Box validation error: ${result.alert}');
      case FormTwoFactor():
        onProgress(AnswerbotSetupStep.confirmingSecondFactor);
        final methods = twoFactor.parseMethods(result.methods);
        _activeWebTwoFactor = twoFactor;
        try {
          onSecondFactorMethods?.call(methods);

          // Poll for authentication (up to 2 minutes)
          for (int i = 0; i < 60; i++) {
            await Future.delayed(const Duration(seconds: 2));
            final poll = await twoFactor.poll();
            AppLogger.instance.info('fritzbox', '_handleWebApi2FA: Poll #$i done=${poll.done} active=${poll.active}');
            if (poll.done) {
              if (poll.active == true) {
                AppLogger.instance.info('fritzbox', '_handleWebApi2FA: 2FA confirmed, re-submitting form');
                final confirmResult = await onConfirmed();
                if (confirmResult is FormValError) {
                  throw Exception('Fritz!Box validation error after 2FA: ${confirmResult.alert}');
                }
                return;
              } else {
                throw Exception('Second factor authentication was rejected');
              }
            }
          }
          throw Exception('Second factor authentication timed out');
        } finally {
          _activeWebTwoFactor = null;
        }
    }
  }

  // -- Answer Bot Setup Methods --

  /// Phone name used for the SIP device on Fritz!Box.
  static final String _answerbotPhoneName =
      kDebugMode ? 'PhoneBlock (Test)' : 'PhoneBlock';

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
        final myFritzInfo = await _withReconnect(() async {
          final svc = _client!.myFritz();
          return svc != null ? await svc.getInfo() : null;
        });
        if (myFritzInfo != null && myFritzInfo.enabled && myFritzInfo.dynDNSName.isNotEmpty) {
          // Use MyFritz DynDNS name
          final enterHostResponse = await sendRequest(
            EnterHostName(id: creation.id, hostName: myFritzInfo.dynDNSName),
          );
          if (enterHostResponse.statusCode == 200) {
            hostConfigured = true;
            AppLogger.instance.info('fritzbox', 'setupAnswerBot: Using MyFritz domain: ${myFritzInfo.dynDNSName}');
          }
        }
      } catch (e, s) {
        AppLogger.instance.error('fritzbox', 'setupAnswerBot: MyFritz detection failed', e, s);
      }

      // Step 2b: Check existing DynDNS configuration
      if (!hostConfigured) {
        try {
          final ddnsInfo = await _withReconnect(() async {
            final svc = _client!.remoteAccess();
            return svc != null ? await svc.getDDNSInfo() : null;
          });
          if (ddnsInfo != null && ddnsInfo.enabled && ddnsInfo.domain.isNotEmpty) {
            final enterHostResponse = await sendRequest(
              EnterHostName(id: creation.id, hostName: ddnsInfo.domain),
            );
            if (enterHostResponse.statusCode == 200) {
              hostConfigured = true;
              AppLogger.instance.info('fritzbox', 'setupAnswerBot: Using existing DynDNS domain: ${ddnsInfo.domain} (provider: ${ddnsInfo.providerName})');
            } else {
              AppLogger.instance.warn('fritzbox', 'setupAnswerBot: Existing DynDNS domain ${ddnsInfo.domain} not accepted (status: ${enterHostResponse.statusCode})');
            }
          }
        } catch (e, s) {
          AppLogger.instance.error('fritzbox', 'setupAnswerBot: DynDNS detection failed', e, s);
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
        final updateUrl = '$basePath/api/dynip?user=<username>&passwd=<passwd>&ip4=<ipaddr>&ip6=<ip6addr>';
        await _withReconnect(() async {
          final remoteAccessService = _client!.remoteAccess();
          if (remoteAccessService == null) {
            throw Exception('RemoteAccess service not available on Fritz!Box');
          }
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
        });

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

      // Step 4: Register SIP device on Fritz!Box via web API
      onProgress(AnswerbotSetupStep.registeringSipDevice);

      final credentials = await FritzBoxStorage.instance.getCredentials();
      if (credentials == null) {
        throw Exception('Fritz!Box credentials not available');
      }

      final webClient = FritzWebClient(
        host: credentials.host,
        username: credentials.username,
        password: credentials.password,
      );
      await webClient.login();

      String? internalNumber;
      try {
        // Creating a SIP device needs the right "FRITZ!Box Einstellungen",
        // which the web session lists (issue #268).
        final session = await webClient.httpClient.get(Uri.parse(
            'http://${credentials.host}/login_sid.lua?version=2&sid=${webClient.sid}'));
        checkWebSessionRights(session.body);

        final ipPhone = IpPhoneService(webClient);
        final twoFactor = WebTwoFactor(webClient);

        // Create SIP device via web wizard
        var createResult = await ipPhone.createIpPhone(
          name: _answerbotPhoneName,
          username: creation.userName,
          password: creation.password,
          outgoingNumber: -1,
        );

        await _handleWebApi2FA(
          result: createResult,
          twoFactor: twoFactor,
          onProgress: onProgress,
          onConfirmed: () => ipPhone.confirmCreate(),
          onSecondFactorMethods: onSecondFactorMethods,
        );

        AppLogger.instance.info('fritzbox', 'setupAnswerBot: SIP device created via web API');

        // Enable internet access for external registration
        onProgress(AnswerbotSetupStep.enablingInternetAccess);
        final ipIdx = await ipPhone.findIpIdx(creation.userName);
        if (ipIdx == null) {
          throw Exception('Could not find created SIP device');
        }

        var saveResult = await ipPhone.saveCredentials(
          ipIdx: ipIdx,
          username: creation.userName,
          password: creation.password,
          fromInet: true,
        );

        await _handleWebApi2FA(
          result: saveResult,
          twoFactor: twoFactor,
          onProgress: onProgress,
          onConfirmed: () => ipPhone.confirmAndSave(
            ipIdx: ipIdx,
            username: creation.userName,
            password: creation.password,
            fromInet: true,
          ),
          onSecondFactorMethods: onSecondFactorMethods,
        );

        AppLogger.instance.info('fritzbox', 'setupAnswerBot: Internet access enabled via web API');

        // Look up the internal number via TR-064 (needed for config)
        internalNumber = await _withReconnect(() async {
          final voipService = _client!.voip();
          if (voipService == null) return null;
          final numberOfClients = await voipService.getNumberOfClients();
          for (int i = 0; i < numberOfClients; i++) {
            final client = await voipService.getClient3(i);
            if (client.clientUsername == creation.userName) {
              AppLogger.instance.info('fritzbox', 'setupAnswerBot: Found SIP device at index $i, internal number: ${client.internalNumber}');
              return client.internalNumber;
            }
          }
          return null;
        });
      } finally {
        await webClient.close();
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
        sipUsername: creation.userName,
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

  /// Detects a pre-existing PhoneBlock answerbot on the connected Fritz!Box.
  ///
  /// Lists VoIP clients via TR-064 and matches their usernames against
  /// bots from the PhoneBlock API. If a match is found and no answerbot
  /// is currently configured locally, auto-adopts the bot.
  Future<void> syncAnswerbotState() async {
    final config = await FritzBoxStorage.instance.getConfig();
    if (!isConnected || _client == null) return;
    if (config?.answerbotEnabled ?? false) return;

    try {
      // Step 1: List VoIP clients from Fritz!Box via TR-064.
      final voipClients = await _withReconnect(() async {
        final voipService = _client!.voip();
        if (voipService == null) return <({String username, String? internalNumber})>[];

        final numberOfClients = await voipService.getNumberOfClients();
        final clients = <({String username, String? internalNumber})>[];
        for (int i = 0; i < numberOfClients; i++) {
          final client = await voipService.getClient3(i);
          clients.add((username: client.clientUsername, internalNumber: client.internalNumber));
        }
        return clients;
      });

      if (voipClients.isEmpty) return;

      // Step 2: Fetch bots from PhoneBlock API.
      final headers = await apiHeaders();
      final response = await http.get(
        Uri.parse('$basePath/ab/list'),
        headers: headers,
      );
      if (response.statusCode != 200) return;

      final listResponse = ListAnswerbotResponse.read(
        JsonReader.fromString(response.body),
      );

      // Step 3: Match by username and auto-adopt.
      for (final bot in listResponse.bots) {
        for (final voipClient in voipClients) {
          if (bot.userName == voipClient.username) {
            await FritzBoxStorage.instance.updateConfig(
              answerbotEnabled: true,
              answerbotId: bot.id,
              sipUsername: bot.userName,
              sipDeviceId: voipClient.internalNumber,
            );
            AppLogger.instance.info('fritzbox', 'syncAnswerbotState: Adopted bot ${bot.id} (username: ${bot.userName}, device: ${voipClient.internalNumber})');
            return;
          }
        }
      }
    } catch (e, s) {
      AppLogger.instance.error('fritzbox', 'syncAnswerbotState: Error detecting answerbot', e, s);
    }
  }

  /// Removes the PhoneBlock answer bot from Fritz!Box and the server.
  ///
  /// Throws [FritzBoxInsufficientRightsException] after the cleanup if the
  /// Fritz!Box refused to delete the SIP device.
  Future<void> removeAnswerBot() async {
    final config = await FritzBoxStorage.instance.getConfig();
    final botId = config?.answerbotId;

    // Disable and delete on server
    if (botId != null) {
      try {
        await sendRequest(DisableAnswerBot(id: botId));
      } catch (e, s) {
        AppLogger.instance.error('fritzbox', 'removeAnswerBot: Failed to disable bot on server', e, s);
      }
      try {
        await sendRequest(DeleteAnswerBot(id: botId));
      } catch (e, s) {
        AppLogger.instance.error('fritzbox', 'removeAnswerBot: Failed to delete bot on server', e, s);
      }
    }

    // Remove SIP device from Fritz!Box
    final sipUsername = config?.sipUsername;
    FritzBoxInsufficientRightsException? refused;
    if (isConnected && _client != null && sipUsername != null) {
      try {
        await deleteSipClient(sipUsername);
      } on FritzBoxInsufficientRightsException catch (e) {
        refused = e;
      } catch (e, s) {
        AppLogger.instance.error('fritzbox', 'removeAnswerBot: Error removing SIP device', e, s);
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
        lastCallId: existing.lastCallId,
        logKnownCallers: existing.logKnownCallers,
        blocklistVersion: existing.blocklistVersion,
        phonebookId: existing.phonebookId,
        sipDeviceId: null,
        sipUsername: null,
        countryCode: existing.countryCode,
        intlPrefix: existing.intlPrefix,
        trunkPrefix: existing.trunkPrefix,
        createdAt: existing.createdAt,
        updatedAt: DateTime.now().millisecondsSinceEpoch,
      ));
    }

    if (refused != null) throw refused;
  }

  /// Deletes the SIP device with the given user name from the Fritz!Box.
  ///
  /// Throws [FritzBoxInsufficientRightsException] if the Fritz!Box user may
  /// not change the telephony configuration.
  @visibleForTesting
  Future<void> deleteSipClient(String sipUsername) async {
    await _withReconnect(() async {
      final voipService = _client!.voip();
      if (voipService == null) return;
      final numberOfClients = await voipService.getNumberOfClients();
      for (int i = 0; i < numberOfClients; i++) {
        final client = await voipService.getClient3(i);
        if (client.clientUsername == sipUsername) {
          await voipService.deleteClient(i);
          AppLogger.instance.info('fritzbox', 'removeAnswerBot: Removed SIP device at index $i (username: $sipUsername)');
          break;
        }
      }
    });
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

  /// Enabling internet access for the SIP device.
  enablingInternetAccess,

  /// Enabling the bot on the server.
  enablingBot,

  /// Waiting for SIP registration to complete.
  waitingForRegistration,

  /// Setup completed successfully.
  complete,
}
