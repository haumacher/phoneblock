import 'package:flutter/widgets.dart';
import '../l10n/app_localizations.dart';

/// Extension on BuildContext to provide easy access to localized strings.
///
/// Instead of writing:
///   AppLocalizations.of(context)!.appTitle
///
/// You can now write:
///   context.l10n.appTitle
extension LocalizationExtension on BuildContext {
  AppLocalizations get l10n => AppLocalizations.of(this)!;
}
