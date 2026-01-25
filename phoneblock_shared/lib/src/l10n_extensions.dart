import 'package:flutter/widgets.dart';
import '../l10n/app_localizations.dart';

/// Extension on BuildContext to provide easy access to answerbot localized strings.
///
/// Instead of writing:
///   AppLocalizations.of(context)!.appTitle
///
/// You can now write:
///   context.answerbotL10n.appTitle
extension AnswerbotLocalizationExtension on BuildContext {
  AppLocalizations get answerbotL10n => AppLocalizations.of(this)!;
}
