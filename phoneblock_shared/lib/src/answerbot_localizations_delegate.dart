import 'package:flutter/widgets.dart';
import '../l10n/app_localizations.dart' as answerbot_l10n;

/// Re-export of the answerbot AppLocalizations delegate with a unique name
/// to avoid conflicts with consuming apps' own AppLocalizations.
const LocalizationsDelegate<answerbot_l10n.AppLocalizations> answerbotLocalizationsDelegate =
    answerbot_l10n.AppLocalizations.delegate;

/// Supported locales for answerbot localizations.
const List<Locale> answerbotSupportedLocales = [
  Locale('ar'),
  Locale('da'),
  Locale('de'),
  Locale('el'),
  Locale('en'),
  Locale('es'),
  Locale('fr'),
  Locale('it'),
  Locale('nb'),
  Locale('nl'),
  Locale('pl'),
  Locale('sv'),
  Locale('uk'),
  Locale('zh'),
];
