import 'package:flutter/widgets.dart';
import '../l10n/app_localizations.dart' as answerbot_l10n;

/// Re-export of the answerbot AppLocalizations delegate with a unique name
/// to avoid conflicts with consuming apps' own AppLocalizations.
const LocalizationsDelegate<answerbot_l10n.AppLocalizations> answerbotLocalizationsDelegate =
    answerbot_l10n.AppLocalizations.delegate;

/// Supported locales for answerbot localizations.
const List<Locale> answerbotSupportedLocales = [
  Locale('de'),
  // Add more locales here when translations are generated
  // Locale('en'),
  // Locale('es'),
  // etc.
];
