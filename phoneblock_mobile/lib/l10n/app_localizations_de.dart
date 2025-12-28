// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for German (`de`).
class AppLocalizationsDe extends AppLocalizations {
  AppLocalizationsDe([String locale = 'de']) : super(locale);

  @override
  String get appTitle => 'PhoneBlock Mobile';

  @override
  String get settings => 'Einstellungen';

  @override
  String get deleteAll => 'Alle löschen';

  @override
  String get noCallsYet => 'Noch keine Anrufe gefiltert';

  @override
  String get noCallsDescription =>
      'PhoneBlock wird eingehende Anrufe automatisch überprüfen und SPAM-Anrufe blockieren.';

  @override
  String get blocked => 'Blockiert';

  @override
  String get accepted => 'Angenommen';

  @override
  String votes(int count) {
    return '$count Stimmen';
  }

  @override
  String get viewOnPhoneBlock => 'Auf PhoneBlock anzeigen';

  @override
  String get confirmDeleteAll => 'Alle gefilterten Anrufe löschen?';

  @override
  String get confirmDeleteAllMessage =>
      'Diese Aktion kann nicht rückgängig gemacht werden.';

  @override
  String get cancel => 'Abbrechen';

  @override
  String get delete => 'Löschen';

  @override
  String get settingsTitle => 'Einstellungen';

  @override
  String get callScreening => 'Anruffilterung';

  @override
  String get minSpamReports => 'Minimale SPAM-Meldungen';

  @override
  String minSpamReportsDescription(int count) {
    return 'Nummern werden ab $count Meldungen blockiert';
  }

  @override
  String get blockNumberRanges => 'Nummernbereiche blockieren';

  @override
  String get blockNumberRangesDescription =>
      'Blockiere Bereiche mit vielen SPAM-Meldungen';

  @override
  String get minSpamReportsInRange => 'Minimale SPAM-Meldungen im Bereich';

  @override
  String minSpamReportsInRangeDescription(int count) {
    return 'Bereiche werden ab $count Meldungen blockiert';
  }

  @override
  String get about => 'Über';

  @override
  String get version => 'Version';

  @override
  String get developer => 'Entwickler';

  @override
  String get developerName => 'Bernhard Haumacher';

  @override
  String get website => 'Website';

  @override
  String get websiteUrl => 'phoneblock.net';

  @override
  String get sourceCode => 'Quellcode';

  @override
  String get sourceCodeLicense => 'Open Source (GPL-3.0)';

  @override
  String get aboutDescription =>
      'PhoneBlock ist ein Open-Source Projekt ohne Tracking und ohne Werbung. Der Dienst wird durch Spenden finanziert.';

  @override
  String get donate => 'Spenden';

  @override
  String pendingCallsNotification(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count neue gefilterte Anrufe',
      one: '1 neuer gefilterter Anruf',
    );
    return '$_temp0';
  }

  @override
  String get tapToOpen => 'Tippen zum Öffnen der App';

  @override
  String get setupWelcome => 'Willkommen bei PhoneBlock Mobile';

  @override
  String get setupPermissionsRequired => 'Erforderliche Berechtigungen';

  @override
  String get grantPermission => 'Berechtigung erteilen';

  @override
  String get continue_ => 'Weiter';

  @override
  String get finish => 'Fertig';

  @override
  String get loginRequired => 'PhoneBlock Anmeldung';

  @override
  String get loginToPhoneBlock => 'Bei PhoneBlock anmelden';

  @override
  String get verifyingLogin => 'Anmeldung wird überprüft...';

  @override
  String get loginFailed => 'Anmeldung fehlgeschlagen';

  @override
  String get loginSuccess => 'Anmeldung erfolgreich!';
}
