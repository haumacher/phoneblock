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
  String get missed => 'Gemist';

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
      other: '$count',
      one: '1 Klacht',
    );
    return '$_temp0';
  }

  @override
  String rangeReportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Klachten in het nummerbereik',
      one: '1 Klacht in de nummerreeks',
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
      'Experimenteel: Beheer SPAM antwoordapparaat voor de Fritz!Box in de app';

  @override
  String get answerbotMenuTitle => 'Antwoordapparaat';

  @override
  String get answerbotMenuDescription => 'SPAM antwoordapparaat beheren';

  @override
  String potentialSpamLabel(String rating) {
    return 'Verdacht: $rating';
  }

  @override
  String get statistics => 'Statistieken';

  @override
  String get blockedCallsCount => 'Geblokkeerde oproepen';

  @override
  String get suspiciousCallsCount => 'Verdachte telefoontjes';

  @override
  String get fritzboxTitle => 'Fritz!Box';

  @override
  String get fritzboxConnected => 'Aangesloten';

  @override
  String get fritzboxConnectedNotProtected => 'Aangesloten, niet beschermd';

  @override
  String get fritzboxOffline => 'Niet beschikbaar';

  @override
  String get fritzboxError => 'Fout bij verbinding';

  @override
  String get fritzboxNotConfiguredShort => 'Niet ingesteld';

  @override
  String get fritzboxNotConfigured => 'Geen Fritz!Box opgezet';

  @override
  String get fritzboxNotConfiguredDescription =>
      'Sluit je Fritz!Box aan om oproepen van je vaste lijn te zien.';

  @override
  String get fritzboxConnect => 'Fritz!Box aansluiten';

  @override
  String get fritzboxDisconnect => 'Ontkoppel Fritz!Box';

  @override
  String get fritzboxDisconnectTitle => 'Fritz! doos loskoppelen?';

  @override
  String get fritzboxDisconnectMessage =>
      'De opgeslagen gesprekken en toegangsgegevens worden verwijderd.';

  @override
  String get fritzboxSyncNow => 'Nu synchroniseren';

  @override
  String get fritzboxSyncDescription => 'De bellijst ophalen uit de Fritz!';

  @override
  String fritzboxSyncComplete(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count nieuwe gesynchroniseerde oproepen',
      one: '1 nieuwe oproep gesynchroniseerd',
      zero: 'Geen nieuwe oproepen',
    );
    return '$_temp0';
  }

  @override
  String get fritzboxSyncError => 'Fout tijdens synchronisatie';

  @override
  String get fritzboxVersion => 'FRITZ!OS-versie';

  @override
  String get fritzboxHost => 'Adres';

  @override
  String get fritzboxCachedCalls => 'Opgeslagen oproepen';

  @override
  String get fritzboxLastSync => 'Laatste synchronisatie';

  @override
  String get fritzboxJustNow => 'Zojuist';

  @override
  String fritzboxMinutesAgo(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Voor $count minuten',
      one: '1 minuut geleden',
    );
    return '$_temp0';
  }

  @override
  String fritzboxHoursAgo(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count uren geleden',
      one: '1 uur geleden',
    );
    return '$_temp0';
  }

  @override
  String get fritzboxWizardTitle => 'Fritz!Box aansluiten';

  @override
  String get fritzboxStepDetection => 'Vind Fritz!Box';

  @override
  String get fritzboxStepDetectionSubtitle =>
      'Automatisch zoeken in het netwerk';

  @override
  String get fritzboxStepLogin => 'Inloggen';

  @override
  String get fritzboxStepLoginSubtitle => 'Toegangsgegevens invoeren';

  @override
  String get fritzboxSearching => 'Zoeken naar Fritz!Box...';

  @override
  String get fritzboxNotFound => 'Fritz! Box niet gevonden';

  @override
  String get fritzboxNotFoundDescription =>
      'De Fritz!Box kon niet automatisch worden gevonden. Voer het adres handmatig in.';

  @override
  String get fritzboxHostLabel => 'Fritz!Box adres';

  @override
  String get fritzboxRetrySearch => 'Opnieuw zoeken';

  @override
  String get fritzboxManualConnect => 'Maak verbinding met';

  @override
  String get fritzboxLoginDescription =>
      'Voer uw Fritz!Box toegangsgegevens in. Je vindt ze in de Fritz!Box gebruikersinterface onder Systeem > Fritz!Box gebruiker.';

  @override
  String get fritzboxShowUsername => 'Gebruikersnaam invoeren';

  @override
  String get fritzboxShowUsernameHint =>
      'Normaal wordt de standaardgebruiker gebruikt';

  @override
  String get fritzboxUsernameLabel => 'Gebruikersnaam';

  @override
  String get fritzboxPasswordLabel => 'Wachtwoord';

  @override
  String get fritzboxCredentialsNote =>
      'Je toegangsgegevens worden veilig opgeslagen op je apparaat.';

  @override
  String get fritzboxTestAndSave => 'Testen en opslaan';

  @override
  String get fritzboxConnectionFailed =>
      'Verbinding mislukt. Controleer de toegangsgegevens.';

  @override
  String get fritzboxFillAllFields => 'Vul alle velden in.';

  @override
  String get fritzboxOfflineBanner =>
      'Fritz! box niet bereikbaar - opgeslagen oproepen tonen';

  @override
  String get sourceMobile => 'Mobiel';

  @override
  String get sourceFritzbox => 'Fritz!Box';

  @override
  String get fritzboxStepBlocklist => 'Bescherming tegen spam';

  @override
  String get fritzboxStepBlocklistSubtitle => 'Blokkadelijst instellen';

  @override
  String get fritzboxBlocklistDescription =>
      'Selecteer hoe je Fritz!Box moet worden beschermd tegen spamoproepen.';

  @override
  String get fritzboxCardDavTitle => 'CardDAV-blokkeringslijst';

  @override
  String get fritzboxCardDavDescription =>
      'Fritz!Box synchroniseert de blokkadelijst rechtstreeks met PhoneBlock. Aanbevolen voor FRITZ!OS 7.20+.';

  @override
  String get fritzboxSkipBlocklist => 'Later instellen';

  @override
  String get fritzboxSkipBlocklistDescription =>
      'U kunt spambeveiliging later activeren in de instellingen.';

  @override
  String get fritzboxVersionTooOldForCardDav =>
      'CardDAV vereist FRITZ!OS 7.20 of nieuwer. Uw Fritz!Box heeft een oudere versie.';

  @override
  String get fritzboxFinishSetup => 'Installatie afronden';

  @override
  String get fritzboxPhoneBlockNotLoggedIn => 'Log eerst in bij PhoneBlock.';

  @override
  String get fritzboxCannotGetUsername =>
      'PhoneBlock-gebruikersnaam kon niet worden opgehaald.';

  @override
  String get fritzboxBlocklistConfigFailed =>
      'Blokkadelijst kon niet worden ingesteld.';

  @override
  String get fritzboxCardDavStatus => 'CardDAV status';

  @override
  String get fritzboxCardDavStatusSynced => 'Gesynchroniseerd';

  @override
  String get fritzboxCardDavStatusPending => 'Synchronisatie in afwachting';

  @override
  String get fritzboxCardDavStatusError => 'Synchronisatiefout';

  @override
  String get fritzboxCardDavStatusDisabled => 'Gedeactiveerd';

  @override
  String get fritzboxCardDavNote =>
      'De Fritz!Box synchroniseert het telefoonboek één keer per dag om middernacht.';

  @override
  String get fritzboxBlocklistMode => 'Spambeschermingsmodus';

  @override
  String get fritzboxBlocklistModeCardDav =>
      'CardDAV (automatische synchronisatie)';

  @override
  String get fritzboxBlocklistModeNone => 'Niet geactiveerd';

  @override
  String get fritzboxEnableCardDav => 'CardDAV activeren';

  @override
  String get fritzboxEnableCardDavDescription =>
      'Spamblocklist direct synchroniseren met Fritz!Box';

  @override
  String get fritzboxCardDavEnabled => 'CardDAV-blokkeringslijst geactiveerd';

  @override
  String get fritzboxDisableCardDav => 'CardDAV deactiveren';

  @override
  String get fritzboxDisableCardDavTitle => 'CardDAV uitschakelen?';

  @override
  String get fritzboxDisableCardDavMessage =>
      'De CardDAV-blokkeringslijst is verwijderd uit de Fritz!';

  @override
  String get fritzboxDisable => 'Deactiveer';

  @override
  String get fritzboxCardDavDisabled => 'CardDAV blokkadelijst gedeactiveerd';

  @override
  String get fritzboxAnswerbotTitle => 'Antwoordapparaat';

  @override
  String get fritzboxAnswerbotActive => 'Antwoordapparaat actief';

  @override
  String get fritzboxAnswerbotDescription =>
      'SPAM-oproepen worden automatisch beantwoord door het PhoneBlock antwoordapparaat.';

  @override
  String get fritzboxEnableAnswerbot => 'Antwoordapparaat activeren';

  @override
  String get fritzboxEnableAnswerbotDescription =>
      'SPAM-oproepen automatisch laten beantwoorden door het PhoneBlock antwoordapparaat';

  @override
  String get fritzboxDisableAnswerbot => 'Antwoordapparaat uitschakelen';

  @override
  String get fritzboxDisableAnswerbotTitle => 'Antwoordapparaat uitschakelen?';

  @override
  String get fritzboxDisableAnswerbotMessage =>
      'Het PhoneBlock antwoordapparaat wordt verwijderd uit de Fritz!Box en gedeactiveerd op de server.';

  @override
  String get fritzboxAnswerbotEnabled => 'Antwoordapparaat geactiveerd';

  @override
  String get fritzboxAnswerbotDisabled => 'Antwoordapparaat uitgeschakeld';

  @override
  String get fritzboxAnswerbotSetupFailed =>
      'Antwoordapparaat kon niet worden ingesteld.';

  @override
  String get fritzboxAnswerbotStepCreating => 'Antwoordapparaat maken...';

  @override
  String get fritzboxAnswerbotStepDetecting => 'Controleer externe toegang...';

  @override
  String get fritzboxAnswerbotStepDynDns => 'DynDNS configureren...';

  @override
  String get fritzboxAnswerbotStepWaitingDynDns =>
      'Wachten op DynDNS-registratie...';

  @override
  String get fritzboxAnswerbotStepSip => 'SIP-apparaat registreren...';

  @override
  String get fritzboxAnswerbotStepEnabling => 'Antwoordapparaat activeren...';

  @override
  String get fritzboxAnswerbotStepWaiting => 'Wachten op registratie...';

  @override
  String get fritzboxAnswerbotSetupTitle => 'Een antwoordapparaat instellen';

  @override
  String get fritzboxAnswerbotSetupSuccess =>
      'Het antwoordapparaat is met succes ingesteld en is nu actief.';

  @override
  String fritzboxAnswerbotSetupErrorDetail(String error) {
    return 'Fout: $error';
  }

  @override
  String get fritzboxAnswerbotStepSecondFactor =>
      'Bevestig toegang op uw Fritz!Box...';

  @override
  String get fritzboxSecondFactorButton =>
      'Druk op een willekeurige knop op de Fritz!Box';

  @override
  String fritzboxSecondFactorDtmf(String code) {
    return 'Of kies $code op een aangesloten telefoon.';
  }
}
