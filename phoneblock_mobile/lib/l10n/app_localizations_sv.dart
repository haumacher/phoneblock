// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Swedish (`sv`).
class AppLocalizationsSv extends AppLocalizations {
  AppLocalizationsSv([String locale = 'sv']) : super(locale);

  @override
  String get appTitle => 'TelefonBlock Mobil';

  @override
  String get settings => 'Inställningar';

  @override
  String get deleteAll => 'Radera alla';

  @override
  String get noCallsYet => 'Inga samtal filtrerade ännu';

  @override
  String get noCallsDescription =>
      'PhoneBlock screenar automatiskt inkommande samtal och blockerar SPAM-samtal.';

  @override
  String get blocked => 'Blockerad';

  @override
  String get accepted => 'Accepterad';

  @override
  String votes(int count) {
    return '$count röster';
  }

  @override
  String get viewOnPhoneBlock => 'Visa på PhoneBlock';

  @override
  String get confirmDeleteAll => 'Radera alla filtrerade samtal?';

  @override
  String get confirmDeleteAllMessage => 'Denna åtgärd kan inte ångras.';

  @override
  String get cancel => 'Avbryt';

  @override
  String get delete => 'Radera';

  @override
  String get settingsTitle => 'Inställningar';

  @override
  String get callScreening => 'Filtrering av samtal';

  @override
  String get minSpamReports => 'Minimalt med SPAM-meddelanden';

  @override
  String minSpamReportsDescription(int count) {
    return 'Numren är blockerade från och med $count meddelanden';
  }

  @override
  String get blockNumberRanges => 'Blocknummerintervall';

  @override
  String get blockNumberRangesDescription =>
      'Blockera områden med många SPAM-meddelanden';

  @override
  String get minSpamReportsInRange =>
      'Minimalt antal SPAM-meddelanden i området';

  @override
  String minSpamReportsInRangeDescription(int count) {
    return 'Områden blockeras från $count meddelanden och framåt';
  }

  @override
  String get about => 'Om';

  @override
  String get version => 'Version';

  @override
  String get developer => 'Utvecklare';

  @override
  String get developerName => 'Bernhard Haumacher';

  @override
  String get website => 'Webbplats';

  @override
  String get websiteUrl => 'phoneblock.net';

  @override
  String get sourceCode => 'Källkod';

  @override
  String get sourceCodeLicense => 'Öppen källkod (GPL-3.0)';

  @override
  String get aboutDescription =>
      'PhoneBlock är ett öppen källkodsprojekt utan spårning och utan reklam. Tjänsten finansieras genom donationer.';

  @override
  String get donate => 'Donationer';

  @override
  String pendingCallsNotification(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count nya filtrerade samtal',
      one: '1 nytt filtrerat samtal',
    );
    return '$_temp0';
  }

  @override
  String get tapToOpen => 'Tryck för att öppna appen';

  @override
  String get setupWelcome => 'Välkommen till PhoneBlock Mobile';

  @override
  String get setupPermissionsRequired => 'Erforderliga tillstånd';

  @override
  String get grantPermission => 'Beviljande av tillstånd';

  @override
  String get continue_ => 'Ytterligare';

  @override
  String get finish => 'Redo';

  @override
  String get loginRequired => 'Registrering av PhoneBlock';

  @override
  String get loginToPhoneBlock => 'Registrera dig med PhoneBlock';

  @override
  String get verifyingLogin => 'Registreringen kommer att kontrolleras...';

  @override
  String get loginFailed => 'Inloggning misslyckades';

  @override
  String get loginSuccess => 'Registrering framgångsrik!';

  @override
  String get reportAsLegitimate => 'Rapportera som legitimt';

  @override
  String get reportAsSpam => 'Rapportera som SPAM';

  @override
  String get viewOnPhoneBlockMenu => 'Visa på PhoneBlock';

  @override
  String get deleteCall => 'Radera';

  @override
  String get report => 'Rapport';

  @override
  String get notLoggedIn => 'Inte registrerad. Vänligen logga in.';

  @override
  String reportedAsLegitimate(String phoneNumber) {
    return '$phoneNumber rapporteras som legitimt';
  }

  @override
  String reportError(String error) {
    return 'Fel vid rapportering: $error';
  }

  @override
  String reportedAsSpam(String phoneNumber) {
    return '$phoneNumber rapporterat som SPAM';
  }

  @override
  String get selectSpamCategory => 'Välj SPAM-kategori';

  @override
  String get errorDeletingAllCalls => 'Fel vid borttagning av alla samtal';

  @override
  String get errorDeletingCall => 'Fel vid avbrytande av samtal';

  @override
  String get notLoggedInShort => 'Ej registrerad';

  @override
  String get errorOpeningPhoneBlock => 'Fel vid öppning av PhoneBlock.';

  @override
  String get permissionNotGranted => 'Tillstånd har inte beviljats.';

  @override
  String get setupTitle => 'PhoneBlock Mobile - Inställning';

  @override
  String get welcome => 'Välkommen till';

  @override
  String get connectPhoneBlockAccount => 'Anslut PhoneBlock-konto';

  @override
  String get permissions => 'Behörigheter';

  @override
  String get allowCallFiltering => 'Tillåt att samtal filtreras';

  @override
  String get done => 'Redo';

  @override
  String get setupComplete => 'Installationen slutförd';

  @override
  String get minReportsCount => 'Minsta antal meddelanden';

  @override
  String callsBlockedAfterReports(int count) {
    return 'Samtal blockeras från $count meddelanden';
  }

  @override
  String rangesBlockedAfterReports(int count) {
    return 'Områden blockeras från $count meddelanden och framåt';
  }

  @override
  String get welcomeMessage =>
      'Välkommen till PhoneBlock Mobile!\n\nDen här appen hjälper dig att blockera spam-samtal automatiskt. Du behöver ett gratis konto hos PhoneBlock.net.\n\nAnslut ditt PhoneBlock-konto för att fortsätta:';

  @override
  String get connectToPhoneBlock => 'Anslut med PhoneBlock';

  @override
  String get connectedToPhoneBlock => 'Ansluten med PhoneBlock';

  @override
  String get accountConnectedSuccessfully => '✓ Konto framgångsrikt anslutet';

  @override
  String get permissionsMessage =>
      'För att automatiskt blockera spam-samtal kräver PhoneBlock Mobile behörighet att kontrollera inkommande samtal.\n\nDenna behörighet krävs för att appen ska fungera:';

  @override
  String get permissionGranted => 'Beviljat tillstånd';

  @override
  String get permissionGrantedSuccessfully =>
      '✓ Godkännande framgångsrikt beviljat';

  @override
  String get setupCompleteMessage =>
      'Installationen är klar!\n\nPhoneBlock Mobile är nu redo att blockera spam-samtal. Appen screenar automatiskt inkommande samtal och blockerar kända spamnummer baserat på PhoneBlocks databas.\n\nTryck på \"Done\" för att gå till huvudvyn.';

  @override
  String get verifyingLoginTitle => 'Kontrollera inloggning';

  @override
  String get loginSuccessMessage => 'Inloggningen lyckades!';

  @override
  String get redirectingToSetup => 'Vidarebefordran till anläggningen...';

  @override
  String tokenVerificationFailed(String error) {
    return 'Tokenverifiering misslyckades: $error';
  }

  @override
  String get backToSetup => 'Tillbaka till anläggningen';

  @override
  String get tokenBeingVerified => 'Token är kontrollerad...';

  @override
  String get failedToOpenPhoneBlock => 'PhoneBlock kunde inte öppnas.';

  @override
  String get ratingLegitimate => 'Legitim';

  @override
  String get ratingAdvertising => 'Annonsering';

  @override
  String get ratingSpam => 'SPAM';

  @override
  String get ratingPingCall => 'Ping-samtal';

  @override
  String get ratingGamble => 'Konkurrens';

  @override
  String get ratingFraud => 'Bedrägeri';

  @override
  String get ratingPoll => 'Undersökning';

  @override
  String get noLoginTokenReceived => 'Ingen inloggningstoken mottagen.';

  @override
  String get settingSaved => 'Inställning sparad';

  @override
  String get errorSaving => 'Fel när du sparar';

  @override
  String ratePhoneNumber(String phoneNumber) {
    return 'Pris $phoneNumber';
  }

  @override
  String reportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count klagomål',
      one: '1 Klagomål',
    );
    return '$_temp0';
  }

  @override
  String rangeReportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Klagomål i nummerintervallet',
      one: '1 Klagomål i nummerintervallet',
    );
    return '$_temp0';
  }

  @override
  String legitimateReportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Legitima meddelanden',
      one: '1 Legitimt meddelande',
    );
    return '$_temp0';
  }

  @override
  String get noReports => 'Inga meddelanden';

  @override
  String todayTime(String time) {
    return 'Idag, $time';
  }

  @override
  String yesterdayTime(String time) {
    return 'Igår, $time';
  }

  @override
  String get callHistoryRetention => 'Lagring av samtalshistorik';

  @override
  String retentionPeriodDescription(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: 'Behåll samtal $days dagar',
      one: 'Behåll samtal 1 dag',
    );
    return '$_temp0';
  }

  @override
  String get retentionInfinite => 'Behåll alla samtal';

  @override
  String retentionDays(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: '$days dagar',
      one: '1 dag',
    );
    return '$_temp0';
  }

  @override
  String get retentionInfiniteOption => 'Obegränsad';

  @override
  String get addCommentSpam => 'Lägg till kommentar (valfritt)';

  @override
  String get commentHintSpam =>
      'Varför är det här skräppost? Vad handlade samtalet om? Var vänlig och var artig.';

  @override
  String get addCommentLegitimate => 'Lägg till kommentar (valfritt)';

  @override
  String get commentHintLegitimate =>
      'Varför är det här legitimt? Vem ringde dig? Var vänlig och var artig.';

  @override
  String get serverSettings => 'Serverinställningar';

  @override
  String get serverSettingsDescription =>
      'Hantera inställningarna för ditt PhoneBlock-konto';

  @override
  String get searchNumber => 'Sök nummer';

  @override
  String get searchPhoneNumber => 'Sök telefonnummer';

  @override
  String get enterPhoneNumber => 'Ange telefonnummer';

  @override
  String get phoneNumberHint => 't.ex. +49 123 456789';

  @override
  String get search => 'Sök';

  @override
  String get invalidPhoneNumber => 'Vänligen ange ett giltigt telefonnummer';

  @override
  String get blacklistTitle => 'Svarta listan';

  @override
  String get blacklistDescription => 'Nummer som blockerats av dig';

  @override
  String get whitelistTitle => 'Vitlista';

  @override
  String get whitelistDescription => 'Nummer som du har markerat som legitima';

  @override
  String get blacklistEmpty => 'Din svarta lista är tom';

  @override
  String get whitelistEmpty => 'Din vitlista är tom';

  @override
  String get blacklistEmptyHelp =>
      'Lägg till nummer genom att rapportera oönskade samtal som skräppost.';

  @override
  String get whitelistEmptyHelp =>
      'Lägg till nummer genom att rapportera blockerade samtal som legitima.';

  @override
  String get errorLoadingList => 'Fel vid laddning av listan';

  @override
  String get numberRemovedFromList => 'Antal borttagna';

  @override
  String get errorRemovingNumber => 'Fel när du tar bort numret';

  @override
  String get confirmRemoval => 'Bekräfta borttagning';

  @override
  String confirmRemoveFromBlacklist(Object phone) {
    return 'Ta bort $phone från den svarta listan?';
  }

  @override
  String confirmRemoveFromWhitelist(Object phone) {
    return 'Ta bort $phone från vitlistan?';
  }

  @override
  String get remove => 'Ta bort';

  @override
  String get retry => 'Försök igen';

  @override
  String get editComment => 'Redigera kommentar';

  @override
  String get commentLabel => 'Kommentar';

  @override
  String get commentHint => 'Lägg till en anteckning till detta nummer';

  @override
  String get save => 'Spara';

  @override
  String get commentUpdated => 'Kommentar uppdaterad';

  @override
  String get errorUpdatingComment => 'Fel vid uppdatering av kommentaren';

  @override
  String get appearance => 'Utseende';

  @override
  String get themeMode => 'Design';

  @override
  String get themeModeDescription => 'Välj en ljus eller mörk design';

  @override
  String get themeModeSystem => 'Systemstandard';

  @override
  String get themeModeLight => 'Ljus';

  @override
  String get themeModeDark => 'Mörk';

  @override
  String get experimentalFeatures => 'Experimentella funktioner';

  @override
  String get answerbotFeature => 'Telefonsvarare (Answerbot)';

  @override
  String get answerbotFeatureDescription =>
      'Experiment: Hantera SPAM-svarare för Fritz!Box i appen';

  @override
  String get answerbotMenuTitle => 'Telefonsvarare';

  @override
  String get answerbotMenuDescription => 'Hantera telefonsvarare för SPAM';

  @override
  String potentialSpamLabel(String rating) {
    return 'Misstänkt: $rating';
  }

  @override
  String get statistics => 'Statistik';

  @override
  String get blockedCallsCount => 'Blockerade samtal';

  @override
  String get suspiciousCallsCount => 'Misstänkta samtal';
}
