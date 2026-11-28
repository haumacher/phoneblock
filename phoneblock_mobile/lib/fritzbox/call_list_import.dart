import 'package:fritz_tr064/fritz_tr064.dart';
import 'package:phoneblock_mobile/fritzbox/call_list_filter.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_models.dart';
import 'package:phoneblock_mobile/fritzbox/phone_number_utils.dart' as phone_utils;
import 'package:phoneblock_mobile/storage.dart';

/// Prefix the PhoneBlock CardDAV phonebook puts in front of every entry name.
///
/// A call whose Fritz!Box name starts with it is a blocklist hit, not a known
/// contact.
const String spamNamePrefix = 'SPAM: ';

/// Why a Fritz!Box call list entry was not imported.
enum CallSkipReason {
  /// Outgoing (or still active outgoing) call - cannot be spam.
  outgoing,

  /// Incoming call still in progress - imported on a later sync with its final
  /// type and duration.
  stillActive,

  /// No caller number (anonymous call).
  noNumber,

  /// The caller number cannot be converted to international form.
  invalidNumber,

  /// The date cannot be parsed.
  invalidDate,

  /// A call with this Fritz!Box ID is already stored in the app.
  alreadyImported,

  /// The caller has a phonebook name and "log known callers" is switched off.
  knownContact,
}

/// A skipped call list entry with the reason for skipping it.
class SkippedCallListEntry {
  /// The Fritz!Box call ID.
  final int id;

  /// Why the entry was skipped.
  final CallSkipReason reason;

  /// The caller number with all but the last three digits masked, safe for
  /// the user-visible log.
  final String maskedNumber;

  SkippedCallListEntry(this.id, this.reason, this.maskedNumber);

  @override
  String toString() => 'call $id ($maskedNumber): ${reason.name}';
}

/// A call to be stored in the app.
class CallToImport {
  /// The call, with its number in international form.
  final ScreenedCall call;

  /// Whether the call should be looked up in the PhoneBlock database before it
  /// is stored.
  ///
  /// False for known contacts: their number is in the user's own phonebook,
  /// so nothing is sent to the server for them (privacy) - there is nothing
  /// to learn about them.
  final bool lookup;

  CallToImport(this.call, {required this.lookup});
}

/// Result of [planCallListImport].
class CallListImportPlan {
  /// The calls to store.
  final List<CallToImport> toImport;

  /// The skipped entries above the watermark, with their reason.
  final List<SkippedCallListEntry> skipped;

  /// Number of entries at or below the watermark, i.e. already handled by an
  /// earlier sync (not listed individually in [skipped]).
  final int belowWatermark;

  /// The highest Fritz!Box call ID that is completely handled, to be stored
  /// for the next sync. Null if there was neither a watermark nor any entry.
  final int? watermark;

  /// True if the stored watermark was dropped because the Fritz!Box list
  /// restarted (cleared list, factory reset).
  final bool listReset;

  CallListImportPlan({
    required this.toImport,
    required this.skipped,
    required this.belowWatermark,
    required this.watermark,
    required this.listReset,
  });
}

/// Decides which Fritz!Box call list entries to import.
///
/// [entries] is the parsed call list. [watermark] is the highest Fritz!Box
/// call ID handled by the previous sync, or null on the first sync (and after
/// an upgrade from the time-based sync). Fritz!Box call IDs are persistent and
/// increase in the order calls start, so only entries above the watermark are
/// new.
///
/// [importedFritzboxIds] are the Fritz!Box IDs of the fetched entries that are
/// already stored in the app. They are never imported again - this covers the
/// first sync without a watermark, calls re-checked because an active call held
/// the watermark back, and a list reset.
///
/// [logKnownCallers]: true imports calls whose Fritz!Box name is a phonebook
/// name (not "SPAM: ...") with that name as label and without a PhoneBlock
/// lookup; false skips them.
///
/// The returned watermark never passes a still active incoming call, so that
/// call is imported with its final type and duration once it has ended.
CallListImportPlan planCallListImport(
  List<CallListEntry> entries, {
  required int? watermark,
  required bool logKnownCallers,
  required Set<String> importedFritzboxIds,
  required String countryCode,
  required String intlPrefix,
  required String trunkPrefix,
}) {
  var listReset = false;
  var effectiveWatermark = watermark;
  if (effectiveWatermark != null && entries.isNotEmpty) {
    final maxId = entries.map((e) => e.id).reduce((a, b) => a > b ? a : b);
    if (maxId < effectiveWatermark) {
      // All IDs below the last handled one: the list on the box restarted.
      listReset = true;
      effectiveWatermark = null;
    }
  }

  final sorted = [...entries]..sort((a, b) => a.id.compareTo(b.id));

  final toImport = <CallToImport>[];
  final skipped = <SkippedCallListEntry>[];
  var belowWatermark = 0;
  int? lowestActive;
  int? highestId = effectiveWatermark;

  for (final entry in sorted) {
    if (highestId == null || entry.id > highestId) {
      highestId = entry.id;
    }

    if (effectiveWatermark != null && entry.id <= effectiveWatermark) {
      belowWatermark++;
      continue;
    }

    final phoneNumber = entry.caller;
    void skip(CallSkipReason reason) {
      skipped.add(SkippedCallListEntry(
          entry.id, reason, phone_utils.maskPhoneNumber(phoneNumber)));
    }

    final callType = convertCallType(entry.type);
    if (callType == FritzBoxCallType.outgoing ||
        callType == FritzBoxCallType.activeOutgoing) {
      // Does not hold the watermark back: outgoing calls are never imported.
      skip(CallSkipReason.outgoing);
      continue;
    }

    if (callType == FritzBoxCallType.activeIncoming) {
      lowestActive ??= entry.id;
      skip(CallSkipReason.stillActive);
      continue;
    }

    if (phoneNumber.isEmpty) {
      skip(CallSkipReason.noNumber);
      continue;
    }

    if (importedFritzboxIds.contains(entry.id.toString())) {
      skip(CallSkipReason.alreadyImported);
      continue;
    }

    final name = entry.name;
    final isKnownContact = name.isNotEmpty && !name.startsWith(spamNamePrefix);
    if (isKnownContact && !logKnownCallers) {
      skip(CallSkipReason.knownContact);
      continue;
    }

    final timestamp = parseFritzBoxDate(entry.date);
    if (timestamp == null) {
      skip(CallSkipReason.invalidDate);
      continue;
    }

    final normalizedNumber = phone_utils.toInternationalForm(
      phoneNumber, countryCode, intlPrefix, trunkPrefix,
    );
    if (normalizedNumber == null) {
      skip(CallSkipReason.invalidNumber);
      continue;
    }

    final String? label;
    if (isKnownContact) {
      label = name;
    } else if (phoneNumber != normalizedNumber) {
      // Keep the number as the Fritz!Box shows it for display.
      label = phoneNumber;
    } else {
      label = null;
    }

    toImport.add(CallToImport(
      ScreenedCall(
        phoneNumber: normalizedNumber,
        timestamp: timestamp,
        // Only rejected calls are actually blocked (call barring); missed
        // means not answered.
        wasBlocked: callType == FritzBoxCallType.rejected,
        votes: 0,
        source: CallSource.fritzbox,
        duration: parseFritzBoxDuration(entry.duration),
        device: entry.device.isEmpty ? null : entry.device,
        fritzboxId: entry.id.toString(),
        callType: callType,
        label: label,
        calledNumber: normalizeOwnNumber(entry.called),
      ),
      lookup: !isKnownContact,
    ));
  }

  final int? newWatermark =
      lowestActive != null ? lowestActive - 1 : highestId;

  return CallListImportPlan(
    toImport: toImport,
    skipped: skipped,
    belowWatermark: belowWatermark,
    watermark: newWatermark,
    listReset: listReset,
  );
}

/// Converts the fritz_tr064 call type to [FritzBoxCallType].
FritzBoxCallType convertCallType(CallType? type) {
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

/// Parses the Fritz!Box call list date (`DD.MM.YY HH:MM`, box local time).
DateTime? parseFritzBoxDate(String dateStr) {
  try {
    final parts = dateStr.split(' ');
    if (parts.length != 2) return null;

    final dateParts = parts[0].split('.');
    if (dateParts.length != 3) return null;

    final timeParts = parts[1].split(':');
    if (timeParts.length != 2) return null;

    final day = int.parse(dateParts[0]);
    final month = int.parse(dateParts[1]);
    var year = int.parse(dateParts[2]);
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

/// Parses the Fritz!Box call list duration (`H:MM`, minutes rounded up) to
/// seconds.
int parseFritzBoxDuration(String durationStr) {
  try {
    final parts = durationStr.split(':');
    if (parts.length != 2) return 0;

    final hours = int.parse(parts[0]);
    final minutes = int.parse(parts[1]);
    return (hours * 60 + minutes) * 60;
  } catch (e) {
    return 0;
  }
}
