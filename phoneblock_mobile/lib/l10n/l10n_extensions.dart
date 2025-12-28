import 'package:flutter/widgets.dart';
import 'app_localizations.dart';

/// Extension on BuildContext to provide easy access to localized strings.
///
/// Instead of writing:
///   AppLocalizations.of(context)!.settingsTitle
///
/// You can now write:
///   context.l10n.settingsTitle
extension LocalizationExtension on BuildContext {
  AppLocalizations get l10n => AppLocalizations.of(this)!;
}
