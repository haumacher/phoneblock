import 'package:flutter/material.dart';
import 'package:fritz_tr064/fritz_tr064.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_models.dart';
import 'package:phoneblock_mobile/l10n/app_localizations.dart';
import 'package:phoneblock_mobile/logging/app_logger.dart';

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

  /// The online phonebooks could not be queried from the Fritz!Box, so
  /// whether a PhoneBlock phonebook exists is not known.
  unknown,
}

/// Result of querying the online phonebooks: the (onlineIndex, info) entries
/// that could be read, and whether all entries could be read.
typedef OnlinePhonebooks = ({
  List<(int, OnlinePhonebookInfo)> entries,
  bool complete,
});

/// Overall Fritz!Box status shown in the menu and on the Fritz!Box page.
enum FritzBoxStatus {
  /// No Fritz!Box is set up.
  notConfigured,

  /// The Fritz!Box is not reachable.
  offline,

  /// Connecting to the Fritz!Box failed.
  error,

  /// Connected, but the PhoneBlock blocklist is not synced to the Fritz!Box.
  connected,

  /// Connected and the PhoneBlock blocklist phonebook is synced.
  protected,
}

/// Decides the Fritz!Box status from the connection state and the CardDAV
/// (blocklist phonebook) status. Only a synced PhoneBlock phonebook counts
/// as protection.
FritzBoxStatus fritzBoxStatus(
    FritzBoxConnectionState connectionState, CardDavStatus cardDavStatus) {
  switch (connectionState) {
    case FritzBoxConnectionState.connected:
      return cardDavStatus == CardDavStatus.synced
          ? FritzBoxStatus.protected
          : FritzBoxStatus.connected;
    case FritzBoxConnectionState.offline:
      return FritzBoxStatus.offline;
    case FritzBoxConnectionState.error:
      return FritzBoxStatus.error;
    case FritzBoxConnectionState.notConfigured:
      return FritzBoxStatus.notConfigured;
  }
}

/// Text for a [FritzBoxStatus].
String fritzBoxStatusText(AppLocalizations l10n, FritzBoxStatus status) {
  switch (status) {
    case FritzBoxStatus.protected:
      return l10n.fritzboxConnected;
    case FritzBoxStatus.connected:
      return l10n.fritzboxConnectedPlain;
    case FritzBoxStatus.offline:
      return l10n.fritzboxOffline;
    case FritzBoxStatus.error:
      return l10n.fritzboxError;
    case FritzBoxStatus.notConfigured:
      return l10n.fritzboxNotConfiguredShort;
  }
}

/// Signal color for a [FritzBoxStatus], or `null` for the neutral
/// "connected" state (callers use the theme's default color).
Color? fritzBoxStatusColor(FritzBoxStatus status) {
  switch (status) {
    case FritzBoxStatus.protected:
      return Colors.green;
    case FritzBoxStatus.connected:
      return null;
    case FritzBoxStatus.offline:
      return Colors.orange;
    case FritzBoxStatus.error:
      return Colors.red;
    case FritzBoxStatus.notConfigured:
      return Colors.grey;
  }
}

/// Determines the [CardDavStatus] from the Fritz!Box online phonebooks.
///
/// [fetchPhonebooks] queries the online phonebooks; if it fails, the cause
/// is logged and [CardDavStatus.unknown] is returned - a failed query says
/// nothing about the state of a PhoneBlock phonebook. The same holds for an
/// incomplete result, unless the PhoneBlock phonebook is among the entries
/// that could be read.
/// [findPhoneBlockIndex] locates the PhoneBlock phonebook in the result.
Future<CardDavStatus> resolveCardDavStatus(
  Future<OnlinePhonebooks> Function() fetchPhonebooks,
  int? Function(List<(int, OnlinePhonebookInfo)>) findPhoneBlockIndex,
) async {
  final OnlinePhonebooks result;
  try {
    result = await fetchPhonebooks();
  } catch (e, s) {
    AppLogger.instance.error('fritzbox',
        'verifyCardDav: Querying the online phonebooks failed, CardDAV status unknown', e, s);
    return CardDavStatus.unknown;
  }

  final phonebooks = result.entries;
  final onlineIndex = findPhoneBlockIndex(phonebooks);
  if (onlineIndex == null) {
    if (!result.complete) {
      AppLogger.instance.error('fritzbox',
          'verifyCardDav: Not all online phonebooks could be read and PhoneBlock is not among the readable ones, CardDAV status unknown');
      return CardDavStatus.unknown;
    }
    return CardDavStatus.notConfigured;
  }

  final info = phonebooks.firstWhere((p) => p.$1 == onlineIndex).$2;
  AppLogger.instance.info('fritzbox',
      'verifyCardDav: [$onlineIndex] enable=${info.enable} status=${info.status} lastConnect="${info.lastConnect}"');
  return cardDavStatusOf(info);
}

/// Interprets the state of an existing PhoneBlock online phonebook.
CardDavStatus cardDavStatusOf(OnlinePhonebookInfo info) {
  if (!info.enable) {
    return CardDavStatus.disabled;
  }

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
}

/// Computes the local blocklist mode matching the Fritz!Box state.
///
/// Returns the new mode, or `null` if [localMode] stays as it is. An
/// [CardDavStatus.unknown] status never changes the mode.
BlocklistMode? reconcileBlocklistMode(
    CardDavStatus cardDavStatus, BlocklistMode localMode) {
  switch (cardDavStatus) {
    case CardDavStatus.unknown:
      return null;
    case CardDavStatus.notConfigured:
      // Fritz!Box has no PhoneBlock phonebook, but local thinks it has.
      return localMode == BlocklistMode.cardDav ? BlocklistMode.none : null;
    case CardDavStatus.disabled:
    case CardDavStatus.synced:
    case CardDavStatus.syncPending:
    case CardDavStatus.error:
      // Fritz!Box has a PhoneBlock phonebook, but local doesn't know.
      return localMode == BlocklistMode.none ? BlocklistMode.cardDav : null;
  }
}
