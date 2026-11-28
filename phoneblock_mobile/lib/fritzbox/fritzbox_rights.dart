import 'package:fritz_tr064/fritz_tr064.dart';
import 'package:phoneblock_mobile/l10n/app_localizations.dart';

/// UPnP error code with which the Fritz!Box refuses an action the logged-in
/// user has no right for ("Action Not Authorized").
const int upnpActionNotAuthorized = 606;

/// Name of the Fritz!Box web session right "FRITZ!Box Einstellungen".
const String _boxAdminRight = 'BoxAdmin';

/// Thrown when the Fritz!Box refuses an action because the Fritz!Box user
/// lacks the right "FRITZ!Box Einstellungen" (issue #268).
///
/// A user with only the right "Sprachnachrichten, Faxnachrichten, FRITZ!App
/// Fon und Anrufliste" can read the call list, but every change to the
/// Fritz!Box configuration (blocklist phonebook, answer bot SIP device,
/// DynDNS) is refused.
class FritzBoxInsufficientRightsException implements Exception {
  /// The refusal reported by the Fritz!Box.
  final Object cause;

  FritzBoxInsufficientRightsException(this.cause);

  @override
  String toString() =>
      'Fritz!Box user lacks the right "FRITZ!Box Einstellungen": $cause';
}

/// The UPnP error code of a TR-064 SOAP fault, or `null` if the fault has
/// none.
///
/// The Fritz!Box reports the code in the fault detail
/// (`<UPnPError><errorCode>606</errorCode><errorDescription>...`), which the
/// library flattens to its text, e.g. `606Action Not Authorized`.
int? upnpErrorCode(SoapFaultException fault) {
  final match = RegExp(r'^\s*(\d+)').firstMatch(fault.detail ?? '');
  return match == null ? null : int.parse(match.group(1)!);
}

/// Whether [error] is a refusal because the Fritz!Box user lacks the
/// required right.
bool isInsufficientRights(Object error) =>
    error is FritzBoxInsufficientRightsException ||
    (error is SoapFaultException &&
        upnpErrorCode(error) == upnpActionNotAuthorized);

/// Runs [action] and reports a refusal for missing rights as
/// [FritzBoxInsufficientRightsException]; all other errors pass unchanged.
Future<T> guardRights<T>(Future<T> Function() action) async {
  try {
    return await action();
  } catch (e) {
    if (e is SoapFaultException && isInsufficientRights(e)) {
      throw FritzBoxInsufficientRightsException(e);
    }
    rethrow;
  }
}

/// Checks the rights of a Fritz!Box web session, given the response of
/// `login_sid.lua` for that session.
///
/// Throws [FritzBoxInsufficientRightsException] if the session lists its
/// rights and "FRITZ!Box Einstellungen" (`BoxAdmin`) is not among them with
/// write access. A response without a rights list is not judged.
void checkWebSessionRights(String loginSidXml) {
  final rights = RegExp(r'<Rights>(.*?)</Rights>', dotAll: true)
      .firstMatch(loginSidXml)
      ?.group(1);
  if (rights == null) return;

  final access = <String, int>{};
  for (final m in RegExp(
          r'<Name>\s*([^<]*?)\s*</Name>\s*<Access>\s*(\d+)\s*</Access>')
      .allMatches(rights)) {
    access[m.group(1)!] = int.parse(m.group(2)!);
  }
  if ((access[_boxAdminRight] ?? 0) < 2) {
    throw FritzBoxInsufficientRightsException(
        'Web session rights: ${access.keys.join(', ')}');
  }
}

/// The message for [error] if it is a refusal for missing rights, `null`
/// otherwise.
String? insufficientRightsMessage(AppLocalizations l10n, Object error) =>
    isInsufficientRights(error) ? l10n.fritzboxNeedsSettingsRight : null;
