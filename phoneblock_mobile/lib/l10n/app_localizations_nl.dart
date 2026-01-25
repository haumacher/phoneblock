// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Dutch Flemish (`nl`).
class AppLocalizationsNl extends AppLocalizations {
  AppLocalizationsNl([String locale = 'nl']) : super(locale);

  @override
  String get appTitle => 'PhoneBlock Mobiel';

  @override
  String get settings => 'Instellingen';

  @override
  String get deleteAll => 'Alles verwijderen';

  @override
  String get noCallsYet => 'Nog geen oproepen gefilterd';

  @override
  String get noCallsDescription =>
      'PhoneBlock screent automatisch inkomende gesprekken en blokkeert SPAM-oproepen.';

  @override
  String get blocked => 'Geblokkeerd';

  @override
  String get accepted => 'Geaccepteerd';

  @override
  String votes(int count) {
    return '$count';
  }

  @override
  String get viewOnPhoneBlock => 'Tonen op PhoneBlock';

  @override
  String get confirmDeleteAll => 'Alle gefilterde oproepen verwijderen?';

  @override
  String get confirmDeleteAllMessage =>
      'Deze actie kan niet ongedaan worden gemaakt.';

  @override
  String get cancel => 'Annuleren';

  @override
  String get delete => 'Verwijder';

  @override
  String get settingsTitle => 'Instellingen';

  @override
  String get callScreening => 'Oproepen filteren';

  @override
  String get minSpamReports => 'Minimale SPAM-berichten';

  @override
  String minSpamReportsDescription(int count) {
    return 'Nummers worden geblokkeerd vanaf $count berichten';
  }

  @override
  String get blockNumberRanges => 'Bereiken bloknummers';

  @override
  String get blockNumberRangesDescription =>
      'Gebieden met veel SPAM-berichten blokkeren';

  @override
  String get minSpamReportsInRange =>
      'Minimale SPAM-berichten op het gebied van';

  @override
  String minSpamReportsInRangeDescription(int count) {
    return 'Gebieden worden geblokkeerd vanaf $count berichten';
  }

  @override
  String get about => 'Over';

  @override
  String get version => 'Versie';

  @override
  String get developer => 'Ontwikkelaar';

  @override
  String get developerName => 'Bernhard Haumacher';

  @override
  String get website => 'Website';

  @override
  String get websiteUrl => 'telefoonblok.net';

  @override
  String get sourceCode => 'Broncode';

  @override
  String get sourceCodeLicense => 'Open Source (GPL-3.0)';

  @override
  String get aboutDescription =>
      'PhoneBlock is een open source project zonder tracking en zonder reclame. De service wordt gefinancierd door donaties.';

  @override
  String get donate => 'Donaties';

  @override
  String pendingCallsNotification(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count nieuwe gefilterde oproepen',
      one: '1 nieuwe gefilterde oproep',
    );
    return '$_temp0';
  }

  @override
  String get tapToOpen => 'Tik om de app te openen';

  @override
  String get setupWelcome => 'Welkom bij PhoneBlock Mobile';

  @override
  String get setupPermissionsRequired => 'Vereiste machtigingen';

  @override
  String get grantPermission => 'Toestemming verlenen';

  @override
  String get continue_ => 'Verder';

  @override
  String get finish => 'Afgewerkt';

  @override
  String get loginRequired => 'PhoneBlock registratie';

  @override
  String get loginToPhoneBlock => 'Registreren bij PhoneBlock';

  @override
  String get verifyingLogin => 'Registratie wordt gecontroleerd...';

  @override
  String get loginFailed => 'Inloggen mislukt';

  @override
  String get loginSuccess => 'Registratie geslaagd!';

  @override
  String get reportAsLegitimate => 'Rapporteer als legitiem';

  @override
  String get reportAsSpam => 'Rapporteren als SPAM';

  @override
  String get viewOnPhoneBlockMenu => 'Bekijk op PhoneBlock';

  @override
  String get deleteCall => 'Verwijder';

  @override
  String get report => 'Rapporteer';

  @override
  String get notLoggedIn => 'Niet geregistreerd. Log in.';

  @override
  String reportedAsLegitimate(String phoneNumber) {
    return '$phoneNumber gerapporteerd als legitiem';
  }

  @override
  String reportError(String error) {
    return 'Fout bij rapportage: $error';
  }

  @override
  String reportedAsSpam(String phoneNumber) {
    return '$phoneNumber gemeld als SPAM';
  }

  @override
  String get selectSpamCategory => 'Selecteer SPAM-categorie';

  @override
  String get errorDeletingAllCalls =>
      'Fout bij het verwijderen van alle oproepen';

  @override
  String get errorDeletingCall => 'Fout bij het annuleren van de oproep';

  @override
  String get notLoggedInShort => 'Niet geregistreerd';

  @override
  String get errorOpeningPhoneBlock => 'Fout bij het openen van PhoneBlock.';

  @override
  String get permissionNotGranted => 'Er is geen toestemming verleend.';

  @override
  String get setupTitle => 'PhoneBlock Mobile - Instellen';

  @override
  String get welcome => 'Welkom bij';

  @override
  String get connectPhoneBlockAccount => 'PhoneBlock-account aansluiten';

  @override
  String get permissions => 'Autorisaties';

  @override
  String get allowCallFiltering => 'Oproepen laten filteren';

  @override
  String get done => 'Afgewerkt';

  @override
  String get setupComplete => 'Installatie voltooid';

  @override
  String get minReportsCount => 'Minimumaantal berichten';

  @override
  String callsBlockedAfterReports(int count) {
    return 'Oproepen worden geblokkeerd van $count berichten';
  }

  @override
  String rangesBlockedAfterReports(int count) {
    return 'Gebieden worden geblokkeerd vanaf $count berichten';
  }

  @override
  String get welcomeMessage =>
      'Welkom bij PhoneBlock Mobile!\n\nDeze app helpt u om spamoproepen automatisch te blokkeren. U hebt een gratis account nodig bij PhoneBlock.net.\n\nMaak verbinding met uw PhoneBlock-account om verder te gaan:';

  @override
  String get connectToPhoneBlock => 'Verbinding maken met PhoneBlock';

  @override
  String get connectedToPhoneBlock => 'Verbonden met PhoneBlock';

  @override
  String get accountConnectedSuccessfully => '✓ Account succesvol verbonden';

  @override
  String get permissionsMessage =>
      'Om spamoproepen automatisch te blokkeren, heeft PhoneBlock Mobile autorisatie nodig om inkomende oproepen te controleren.\n\nDeze autorisatie is vereist om de app te laten werken:';

  @override
  String get permissionGranted => 'Toestemming verleend';

  @override
  String get permissionGrantedSuccessfully => '✓ Vergunning succesvol verleend';

  @override
  String get setupCompleteMessage =>
      'Installatie voltooid!\n\nPhoneBlock Mobile is nu klaar om spamoproepen te blokkeren. De app screent automatisch inkomende oproepen en blokkeert bekende spornummers op basis van de PhoneBlock database.\n\nDruk op \"Gereed\" om naar de hoofdweergave te gaan.';

  @override
  String get verifyingLoginTitle => 'Aanmelding controleren';

  @override
  String get loginSuccessMessage => 'Inloggen gelukt!';

  @override
  String get redirectingToSetup => 'Doorsturen naar de faciliteit...';

  @override
  String tokenVerificationFailed(String error) {
    return 'Token verificatie mislukt: $error';
  }

  @override
  String get backToSetup => 'Terug naar de faciliteit';

  @override
  String get tokenBeingVerified => 'Token is gecontroleerd...';

  @override
  String get failedToOpenPhoneBlock => 'PhoneBlock kon niet worden geopend.';

  @override
  String get ratingLegitimate => 'Legitiem';

  @override
  String get ratingAdvertising => 'Reclame';

  @override
  String get ratingSpam => 'SPAM';

  @override
  String get ratingPingCall => 'Ping-oproep';

  @override
  String get ratingGamble => 'Concurrentie';

  @override
  String get ratingFraud => 'Fraude';

  @override
  String get ratingPoll => 'Enquête';

  @override
  String get noLoginTokenReceived => 'Geen inlogtoken ontvangen.';

  @override
  String get settingSaved => 'Opgeslagen instelling';

  @override
  String get errorSaving => 'Fout bij opslaan';

  @override
  String ratePhoneNumber(String phoneNumber) {
    return 'Tarief $phoneNumber';
  }

  @override
  String reportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count berichten',
      one: '1 Bericht',
    );
    return '$_temp0';
  }

  @override
  String legitimateReportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Legitieme berichten',
      one: '1 Legitiem bericht',
    );
    return '$_temp0';
  }

  @override
  String get noReports => 'Geen berichten';

  @override
  String todayTime(String time) {
    return 'Vandaag, $time';
  }

  @override
  String yesterdayTime(String time) {
    return 'Gisteren, $time';
  }

  @override
  String get callHistoryRetention => 'Opslag van gesprekshistorie';

  @override
  String retentionPeriodDescription(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: 'Houd oproepen $days dagen',
      one: 'Gesprekken 1 dag bewaren',
    );
    return '$_temp0';
  }

  @override
  String get retentionInfinite => 'Houd alle oproepen';

  @override
  String retentionDays(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: '$days dagen',
      one: '1 dag',
    );
    return '$_temp0';
  }

  @override
  String get retentionInfiniteOption => 'Onbeperkt';

  @override
  String get addCommentSpam => 'Opmerking toevoegen (optioneel)';

  @override
  String get commentHintSpam =>
      'Waarom is dit spam? Waar ging het gesprek over? Blijf beleefd.';

  @override
  String get addCommentLegitimate => 'Opmerking toevoegen (optioneel)';

  @override
  String get commentHintLegitimate =>
      'Waarom is dit legitiem? Wie heeft u gebeld? Blijf beleefd.';

  @override
  String get serverSettings => 'Serverinstellingen';

  @override
  String get serverSettingsDescription =>
      'Uw PhoneBlock-accountinstellingen beheren';

  @override
  String get searchNumber => 'Zoeknummer';

  @override
  String get searchPhoneNumber => 'Telefoonnummer zoeken';

  @override
  String get enterPhoneNumber => 'Telefoonnummer invoeren';

  @override
  String get phoneNumberHint => 'bijv. +49 123 456789';

  @override
  String get search => 'Zoek op';

  @override
  String get invalidPhoneNumber => 'Voer een geldig telefoonnummer in';

  @override
  String get blacklistTitle => 'Zwarte lijst';

  @override
  String get blacklistDescription => 'Geblokkeerde nummers';

  @override
  String get whitelistTitle => 'Whitelist';

  @override
  String get whitelistDescription =>
      'Nummers die je als legitiem hebt gemarkeerd';

  @override
  String get blacklistEmpty => 'Je zwarte lijst is leeg';

  @override
  String get whitelistEmpty => 'Uw witte lijst is leeg';

  @override
  String get blacklistEmptyHelp =>
      'Nummers toevoegen door ongewenste oproepen als spam te melden.';

  @override
  String get whitelistEmptyHelp =>
      'Nummers toevoegen door geblokkeerde oproepen als legitiem te melden.';

  @override
  String get errorLoadingList => 'Fout bij het laden van de lijst';

  @override
  String get numberRemovedFromList => 'Aantal verwijderd';

  @override
  String get errorRemovingNumber => 'Fout bij het verwijderen van het nummer';

  @override
  String get confirmRemoval => 'Verwijdering bevestigen';

  @override
  String confirmRemoveFromBlacklist(Object phone) {
    return '$phone van de zwarte lijst verwijderen?';
  }

  @override
  String confirmRemoveFromWhitelist(Object phone) {
    return '$phone verwijderen uit de witte lijst?';
  }

  @override
  String get remove => 'Verwijder';

  @override
  String get retry => 'Probeer het opnieuw';

  @override
  String get editComment => 'Opmerking bewerken';

  @override
  String get commentLabel => 'Opmerking';

  @override
  String get commentHint => 'Voeg een notitie toe aan dit nummer';

  @override
  String get save => 'Sla';

  @override
  String get commentUpdated => 'Commentaar bijgewerkt';

  @override
  String get errorUpdatingComment => 'Fout bij het bijwerken van de opmerking';

  @override
  String get appearance => 'Uiterlijk';

  @override
  String get themeMode => 'Ontwerp';

  @override
  String get themeModeDescription => 'Kies een licht of donker ontwerp';

  @override
  String get themeModeSystem => 'Systeemstandaard';

  @override
  String get themeModeLight => 'Licht';

  @override
  String get themeModeDark => 'Donker';

  @override
  String get experimentalFeatures => 'Experimentele functies';

  @override
  String get answerbotFeature => 'Antwoordapparaat (Answerbot)';

  @override
  String get answerbotFeatureDescription =>
      'Experimentele functie: Beheer SPAM antwoordapparaten die automatisch met spam bellers praten';

  @override
  String get answerbotMenuTitle => 'Antwoordapparaat';

  @override
  String get answerbotMenuDescription => 'SPAM antwoordapparaat beheren';
}
