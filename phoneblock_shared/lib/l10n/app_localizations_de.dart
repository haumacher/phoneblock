// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for German (`de`).
class AppLocalizationsDe extends AppLocalizations {
  AppLocalizationsDe([String locale = 'de']) : super(locale);

  @override
  String get appTitle => 'PhoneBlock Anrufbeantworter';

  @override
  String get yourAnswerbots => 'Deine Anrufbeantworter';

  @override
  String get loginRequired => 'Anmeldung erforderlich';

  @override
  String get login => 'Login';

  @override
  String get loadingData => 'Lade Daten...';

  @override
  String get refreshingData => 'Aktualisiere Daten...';

  @override
  String get noAnswerbotsYet =>
      'Du hast noch keinen Anrufbeantworter, klicke den Plus-Knopf unten, um einen PhoneBlock-Anrufbeantworter anzulegen.';

  @override
  String get createAnswerbot => 'Anrufbeantworter anlegen';

  @override
  String answerbotName(String userName) {
    return 'Anrufbeantworter $userName';
  }

  @override
  String answerbotStats(int newCalls, int callsAccepted, int talkTimeSeconds) {
    return '$newCalls neue Anrufe, $callsAccepted Anrufe, $talkTimeSeconds s Gesprächszeit gesamt';
  }

  @override
  String get statusActive => 'aktiv';

  @override
  String get statusConnecting => 'verbinde...';

  @override
  String get statusDisabled => 'ausgeschaltet';

  @override
  String get statusIncomplete => 'unvollständig';

  @override
  String get deleteAnswerbot => 'Anrufbeantworter löschen';

  @override
  String get enabled => 'Aktiviert';

  @override
  String get minVotes => 'Mindest-Stimmen';

  @override
  String get minVotesDescription =>
      'Wie viele Stimmen muss eine Nummer mindestens haben, damit sie vom Anrufbeantworter angenommen wird?';

  @override
  String get minVotes2 => '2 - sofort sperren';

  @override
  String get minVotes4 => '4 - Bestätigung abwarten';

  @override
  String get minVotes10 => '10 - erst wenn sicher';

  @override
  String get minVotes100 => '100 - nur Top-Spammer';

  @override
  String get cannotChangeWhileEnabled =>
      'Kann nur bei ausgeschaltetem Anrufbeantworter geändert werden.';

  @override
  String get saveSettings => 'Einstellungen speichern';

  @override
  String get retentionPeriod => 'Aufbewahrungszeit';

  @override
  String get retentionPeriodDescription =>
      'Wie lange sollen Anrufe aufbewahrt werden?';

  @override
  String get retentionNever => 'Niemals löschen';

  @override
  String get retentionWeek => 'Nach 1 Woche löschen';

  @override
  String get retentionMonth => 'Nach 1 Monat löschen';

  @override
  String get retentionQuarter => 'Nach 3 Monaten löschen';

  @override
  String get retentionYear => 'Nach 1 Jahr löschen';

  @override
  String get saveRetentionSettings => 'Aufbewahrungseinstellungen speichern';

  @override
  String get showHelp => 'Hilfe anzeigen';

  @override
  String get newAnswerbot => 'Neuer Anrufbeantworter';

  @override
  String get usePhoneBlockDynDns => 'PhoneBlock-DynDNS benutzen';

  @override
  String get dynDnsDescription =>
      'PhoneBlock muss die Internet-Adresse Deiner Fritz!Box kennen, um Anrufe annehmen zu können.';

  @override
  String get setupPhoneBlockDynDns => 'PhoneBlock-DynDNS einrichten';

  @override
  String get domainName => 'Domain-Name';

  @override
  String get domainNameHint =>
      'Gib den Domain-Namen Deiner Fritz!Box an. Wenn Deine Fritz!Box noch keinen Domain-Namen hat, aktiviere PhoneBlock-DynDNS.';

  @override
  String get checkDomainName => 'Domainnamen überprüfen';

  @override
  String get setupDynDns => 'DynDNS einrichten';

  @override
  String get dynDnsInstructions =>
      'Öffne in Deinen Fritz!Box-Einstellungen die Seite die Seite Internet > Freigaben > DynDNS und trage dort die folgenden Werte ein:';

  @override
  String get checkDynDns => 'DynDNS überprüfen';

  @override
  String get createAnswerbotTitle => 'Anrufbeantworter erstellen';

  @override
  String get registerAnswerbot => 'Anrufbeantworter anmelden';

  @override
  String get answerbotRegistered => 'Anrufbeantworter angemeldet';

  @override
  String get close => 'Schließen';

  @override
  String get error => 'Fehler';

  @override
  String cannotLoadInfo(int statusCode, String message) {
    return 'Informationen können nicht abgerufen werden (Fehler $statusCode): $message';
  }

  @override
  String wrongContentType(String contentType) {
    return 'Informationen können nicht abgerufen werden (Content-Type: $contentType).';
  }

  @override
  String get callList => 'Anrufliste';

  @override
  String get clearCallList => 'Anrufliste löschen';

  @override
  String get noCalls => 'Noch keine Anrufe';
}
