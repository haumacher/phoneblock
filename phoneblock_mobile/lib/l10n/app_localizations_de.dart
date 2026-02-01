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

  @override
  String get reportAsLegitimate => 'Als legitim melden';

  @override
  String get reportAsSpam => 'Als SPAM melden';

  @override
  String get viewOnPhoneBlockMenu => 'Auf PhoneBlock ansehen';

  @override
  String get deleteCall => 'Löschen';

  @override
  String get report => 'Melden';

  @override
  String get notLoggedIn => 'Nicht angemeldet. Bitte melden Sie sich an.';

  @override
  String reportedAsLegitimate(String phoneNumber) {
    return '$phoneNumber als legitim gemeldet';
  }

  @override
  String reportError(String error) {
    return 'Fehler beim Melden: $error';
  }

  @override
  String reportedAsSpam(String phoneNumber) {
    return '$phoneNumber als SPAM gemeldet';
  }

  @override
  String get selectSpamCategory => 'SPAM-Kategorie wählen';

  @override
  String get errorDeletingAllCalls => 'Fehler beim Löschen aller Anrufe';

  @override
  String get errorDeletingCall => 'Fehler beim Löschen des Anrufs';

  @override
  String get notLoggedInShort => 'Nicht angemeldet';

  @override
  String get errorOpeningPhoneBlock => 'Fehler beim Öffnen von PhoneBlock.';

  @override
  String get permissionNotGranted => 'Berechtigung wurde nicht erteilt.';

  @override
  String get setupTitle => 'PhoneBlock Mobile - Einrichtung';

  @override
  String get welcome => 'Willkommen';

  @override
  String get connectPhoneBlockAccount => 'PhoneBlock-Konto verbinden';

  @override
  String get permissions => 'Berechtigungen';

  @override
  String get allowCallFiltering => 'Anrufe filtern erlauben';

  @override
  String get done => 'Fertig';

  @override
  String get setupComplete => 'Einrichtung abgeschlossen';

  @override
  String get minReportsCount => 'Minimale Anzahl Meldungen';

  @override
  String callsBlockedAfterReports(int count) {
    return 'Anrufe werden ab $count Meldungen blockiert';
  }

  @override
  String rangesBlockedAfterReports(int count) {
    return 'Bereiche werden ab $count Meldungen blockiert';
  }

  @override
  String get welcomeMessage =>
      'Willkommen bei PhoneBlock Mobile!\n\nDiese App hilft Ihnen, Spam-Anrufe automatisch zu blockieren. Dazu benötigen Sie ein kostenloses Konto bei PhoneBlock.net.\n\nVerbinden Sie Ihr PhoneBlock-Konto, um fortzufahren:';

  @override
  String get connectToPhoneBlock => 'Mit PhoneBlock verbinden';

  @override
  String get connectedToPhoneBlock => 'Mit PhoneBlock verbunden';

  @override
  String get accountConnectedSuccessfully => '✓ Konto erfolgreich verbunden';

  @override
  String get permissionsMessage =>
      'Um Spam-Anrufe automatisch zu blockieren, benötigt PhoneBlock Mobile die Berechtigung, eingehende Anrufe zu prüfen.\n\nDiese Berechtigung ist erforderlich, damit die App funktioniert:';

  @override
  String get permissionGranted => 'Berechtigung erteilt';

  @override
  String get permissionGrantedSuccessfully =>
      '✓ Berechtigung erfolgreich erteilt';

  @override
  String get setupCompleteMessage =>
      'Einrichtung abgeschlossen!\n\nPhoneBlock Mobile ist jetzt bereit, Spam-Anrufe zu blockieren. Die App prüft automatisch eingehende Anrufe und blockiert bekannte Spam-Nummern basierend auf der PhoneBlock-Datenbank.\n\nDrücken Sie \"Fertig\", um zur Hauptansicht zu gelangen.';

  @override
  String get verifyingLoginTitle => 'Überprüfe Login';

  @override
  String get loginSuccessMessage => 'Login erfolgreich!';

  @override
  String get redirectingToSetup => 'Weiterleitung zur Einrichtung...';

  @override
  String tokenVerificationFailed(String error) {
    return 'Token-Überprüfung fehlgeschlagen: $error';
  }

  @override
  String get backToSetup => 'Zurück zur Einrichtung';

  @override
  String get tokenBeingVerified => 'Token wird überprüft...';

  @override
  String get failedToOpenPhoneBlock =>
      'PhoneBlock konnte nicht geöffnet werden.';

  @override
  String get ratingLegitimate => 'Legitim';

  @override
  String get ratingAdvertising => 'Werbung';

  @override
  String get ratingSpam => 'SPAM';

  @override
  String get ratingPingCall => 'Ping-Anruf';

  @override
  String get ratingGamble => 'Gewinnspiel';

  @override
  String get ratingFraud => 'Betrug';

  @override
  String get ratingPoll => 'Umfrage';

  @override
  String get noLoginTokenReceived => 'Kein Login-Token empfangen.';

  @override
  String get settingSaved => 'Einstellung gespeichert';

  @override
  String get errorSaving => 'Fehler beim Speichern';

  @override
  String ratePhoneNumber(String phoneNumber) {
    return '$phoneNumber bewerten';
  }

  @override
  String reportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Beschwerden',
      one: '1 Beschwerde',
    );
    return '$_temp0';
  }

  @override
  String rangeReportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Beschwerden im Nummernbereich',
      one: '1 Beschwerde im Nummernbereich',
    );
    return '$_temp0';
  }

  @override
  String legitimateReportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Legitim-Meldungen',
      one: '1 Legitim-Meldung',
    );
    return '$_temp0';
  }

  @override
  String get noReports => 'Keine Meldungen';

  @override
  String todayTime(String time) {
    return 'Heute, $time';
  }

  @override
  String yesterdayTime(String time) {
    return 'Gestern, $time';
  }

  @override
  String get callHistoryRetention => 'Anrufverlauf-Speicherung';

  @override
  String retentionPeriodDescription(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: 'Anrufe $days Tage behalten',
      one: 'Anrufe 1 Tag behalten',
    );
    return '$_temp0';
  }

  @override
  String get retentionInfinite => 'Alle Anrufe behalten';

  @override
  String retentionDays(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: '$days Tage',
      one: '1 Tag',
    );
    return '$_temp0';
  }

  @override
  String get retentionInfiniteOption => 'Unbegrenzt';

  @override
  String get addCommentSpam => 'Kommentar hinzufügen (Optional)';

  @override
  String get commentHintSpam =>
      'Warum ist das Spam? Worum ging es bei dem Anruf? Bitte höflich bleiben.';

  @override
  String get addCommentLegitimate => 'Kommentar hinzufügen (Optional)';

  @override
  String get commentHintLegitimate =>
      'Warum ist das legitim? Wer hat Sie angerufen? Bitte höflich bleiben.';

  @override
  String get serverSettings => 'Server-Einstellungen';

  @override
  String get serverSettingsDescription =>
      'Verwalten Sie Ihre PhoneBlock-Kontoeinstellungen';

  @override
  String get searchNumber => 'Nummer suchen';

  @override
  String get searchPhoneNumber => 'Telefonnummer suchen';

  @override
  String get enterPhoneNumber => 'Telefonnummer eingeben';

  @override
  String get phoneNumberHint => 'z.B. +49 123 456789';

  @override
  String get search => 'Suchen';

  @override
  String get invalidPhoneNumber =>
      'Bitte geben Sie eine gültige Telefonnummer ein';

  @override
  String get blacklistTitle => 'Blacklist';

  @override
  String get blacklistDescription => 'Von Ihnen blockierte Nummern';

  @override
  String get whitelistTitle => 'Whitelist';

  @override
  String get whitelistDescription => 'Von Ihnen als legitim markierte Nummern';

  @override
  String get blacklistEmpty => 'Ihre Blacklist ist leer';

  @override
  String get whitelistEmpty => 'Ihre Whitelist ist leer';

  @override
  String get blacklistEmptyHelp =>
      'Fügen Sie Nummern hinzu, indem Sie unerwünschte Anrufe als Spam melden.';

  @override
  String get whitelistEmptyHelp =>
      'Fügen Sie Nummern hinzu, indem Sie blockierte Anrufe als legitim melden.';

  @override
  String get errorLoadingList => 'Fehler beim Laden der Liste';

  @override
  String get numberRemovedFromList => 'Nummer entfernt';

  @override
  String get errorRemovingNumber => 'Fehler beim Entfernen der Nummer';

  @override
  String get confirmRemoval => 'Entfernen bestätigen';

  @override
  String confirmRemoveFromBlacklist(Object phone) {
    return '$phone von der Blacklist entfernen?';
  }

  @override
  String confirmRemoveFromWhitelist(Object phone) {
    return '$phone von der Whitelist entfernen?';
  }

  @override
  String get remove => 'Entfernen';

  @override
  String get retry => 'Erneut versuchen';

  @override
  String get editComment => 'Kommentar bearbeiten';

  @override
  String get commentLabel => 'Kommentar';

  @override
  String get commentHint => 'Notiz zu dieser Nummer hinzufügen';

  @override
  String get save => 'Speichern';

  @override
  String get commentUpdated => 'Kommentar aktualisiert';

  @override
  String get errorUpdatingComment => 'Fehler beim Aktualisieren des Kommentars';

  @override
  String get appearance => 'Erscheinungsbild';

  @override
  String get themeMode => 'Design';

  @override
  String get themeModeDescription => 'Helles oder dunkles Design wählen';

  @override
  String get themeModeSystem => 'Systemstandard';

  @override
  String get themeModeLight => 'Hell';

  @override
  String get themeModeDark => 'Dunkel';

  @override
  String get experimentalFeatures => 'Experimentelle Funktionen';

  @override
  String get answerbotFeature => 'Anrufbeantworter (Answerbot)';

  @override
  String get answerbotFeatureDescription =>
      'Experimentell: SPAM-Anrufbeantworter für die Fritz!Box in der App verwalten';

  @override
  String get answerbotMenuTitle => 'Anrufbeantworter';

  @override
  String get answerbotMenuDescription => 'SPAM-Anrufbeantworter verwalten';

  @override
  String potentialSpamLabel(String rating) {
    return 'Verdächtig: $rating';
  }

  @override
  String get statistics => 'Statistik';

  @override
  String get blockedCallsCount => 'Blockierte Anrufe';

  @override
  String get suspiciousCallsCount => 'Verdächtige Anrufe';

  @override
  String get fritzboxTitle => 'Fritz!Box';

  @override
  String get fritzboxConnected => 'Verbunden';

  @override
  String get fritzboxOffline => 'Nicht erreichbar';

  @override
  String get fritzboxError => 'Verbindungsfehler';

  @override
  String get fritzboxNotConfiguredShort => 'Nicht eingerichtet';

  @override
  String get fritzboxNotConfigured => 'Keine Fritz!Box eingerichtet';

  @override
  String get fritzboxNotConfiguredDescription =>
      'Verbinden Sie Ihre Fritz!Box, um Anrufe aus Ihrem Festnetz zu sehen.';

  @override
  String get fritzboxConnect => 'Fritz!Box verbinden';

  @override
  String get fritzboxDisconnect => 'Fritz!Box trennen';

  @override
  String get fritzboxDisconnectTitle => 'Fritz!Box trennen?';

  @override
  String get fritzboxDisconnectMessage =>
      'Die gespeicherten Anrufe und Zugangsdaten werden gelöscht.';

  @override
  String get fritzboxSyncNow => 'Jetzt synchronisieren';

  @override
  String get fritzboxSyncDescription => 'Anrufliste von der Fritz!Box abrufen';

  @override
  String fritzboxSyncComplete(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count neue Anrufe synchronisiert',
      one: '1 neuer Anruf synchronisiert',
      zero: 'Keine neuen Anrufe',
    );
    return '$_temp0';
  }

  @override
  String get fritzboxSyncError => 'Fehler beim Synchronisieren';

  @override
  String get fritzboxVersion => 'FRITZ!OS Version';

  @override
  String get fritzboxHost => 'Adresse';

  @override
  String get fritzboxCachedCalls => 'Gespeicherte Anrufe';

  @override
  String get fritzboxLastSync => 'Letzte Synchronisierung';

  @override
  String get fritzboxJustNow => 'Gerade eben';

  @override
  String fritzboxMinutesAgo(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Vor $count Minuten',
      one: 'Vor 1 Minute',
    );
    return '$_temp0';
  }

  @override
  String fritzboxHoursAgo(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Vor $count Stunden',
      one: 'Vor 1 Stunde',
    );
    return '$_temp0';
  }

  @override
  String get fritzboxWizardTitle => 'Fritz!Box verbinden';

  @override
  String get fritzboxStepDetection => 'Fritz!Box finden';

  @override
  String get fritzboxStepDetectionSubtitle => 'Automatische Suche im Netzwerk';

  @override
  String get fritzboxStepLogin => 'Anmelden';

  @override
  String get fritzboxStepLoginSubtitle => 'Zugangsdaten eingeben';

  @override
  String get fritzboxSearching => 'Suche nach Fritz!Box...';

  @override
  String get fritzboxNotFound => 'Fritz!Box nicht gefunden';

  @override
  String get fritzboxNotFoundDescription =>
      'Die Fritz!Box konnte nicht automatisch gefunden werden. Bitte geben Sie die Adresse manuell ein.';

  @override
  String get fritzboxHostLabel => 'Fritz!Box Adresse';

  @override
  String get fritzboxRetrySearch => 'Erneut suchen';

  @override
  String get fritzboxManualConnect => 'Verbinden';

  @override
  String get fritzboxLoginDescription =>
      'Geben Sie Ihre Fritz!Box Zugangsdaten ein. Sie finden diese in der Fritz!Box Benutzeroberfläche unter System > Fritz!Box-Benutzer.';

  @override
  String get fritzboxUsernameLabel => 'Benutzername';

  @override
  String get fritzboxUsernameHint => 'admin oder Ihr Fritz!Box-Benutzer';

  @override
  String get fritzboxPasswordLabel => 'Kennwort';

  @override
  String get fritzboxCredentialsNote =>
      'Ihre Zugangsdaten werden sicher auf Ihrem Gerät gespeichert.';

  @override
  String get fritzboxTestAndSave => 'Testen und Speichern';

  @override
  String get fritzboxConnectionFailed =>
      'Verbindung fehlgeschlagen. Bitte überprüfen Sie die Zugangsdaten.';

  @override
  String get fritzboxFillAllFields => 'Bitte füllen Sie alle Felder aus.';

  @override
  String get fritzboxOfflineBanner =>
      'Fritz!Box nicht erreichbar - zeige gespeicherte Anrufe';

  @override
  String get sourceMobile => 'Mobil';

  @override
  String get sourceFritzbox => 'Fritz!Box';
}
