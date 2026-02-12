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
  String get missed => 'Missad';

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

  @override
  String get fritzboxTitle => 'Fritz!Box';

  @override
  String get fritzboxConnected => 'Ansluten';

  @override
  String get fritzboxConnectedNotProtected => 'Ansluten, men inte skyddad';

  @override
  String get fritzboxOffline => 'Ej tillgänglig';

  @override
  String get fritzboxError => 'Fel i anslutningen';

  @override
  String get fritzboxNotConfiguredShort => 'Inte konfigurerad';

  @override
  String get fritzboxNotConfigured => 'Ingen Fritz!Box installerad';

  @override
  String get fritzboxNotConfiguredDescription =>
      'Anslut din Fritz!Box för att ta emot samtal från din fasta telefon.';

  @override
  String get fritzboxConnect => 'Anslut Fritz!Box';

  @override
  String get fritzboxDisconnect => 'Koppla bort Fritz!Box';

  @override
  String get fritzboxDisconnectTitle => 'Koppla bort Fritz! boxen?';

  @override
  String get fritzboxDisconnectMessage =>
      'De sparade samtalen och åtkomstdata raderas.';

  @override
  String get fritzboxSyncNow => 'Synkronisera nu';

  @override
  String get fritzboxSyncDescription => 'Hämtar samtalslistan från Fritz!';

  @override
  String fritzboxSyncComplete(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count nya samtal synkroniserade',
      one: '1 nytt samtal synkroniserat',
      zero: 'Inga nya samtal',
    );
    return '$_temp0';
  }

  @override
  String get fritzboxSyncError => 'Fel under synkronisering';

  @override
  String get fritzboxVersion => 'FRITZ!OS version';

  @override
  String get fritzboxHost => 'Adress';

  @override
  String get fritzboxCachedCalls => 'Sparade samtal';

  @override
  String get fritzboxLastSync => 'Senaste synkronisering';

  @override
  String get fritzboxJustNow => 'Just nu';

  @override
  String fritzboxMinutesAgo(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Före $count minuter',
      one: '1 minut sedan',
    );
    return '$_temp0';
  }

  @override
  String fritzboxHoursAgo(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count timmar sedan',
      one: '1 timme sedan',
    );
    return '$_temp0';
  }

  @override
  String get fritzboxWizardTitle => 'Anslut Fritz!Box';

  @override
  String get fritzboxStepDetection => 'Hitta Fritz!Box';

  @override
  String get fritzboxStepDetectionSubtitle => 'Automatisk sökning i nätverket';

  @override
  String get fritzboxStepLogin => 'Logga in';

  @override
  String get fritzboxStepLoginSubtitle => 'Ange åtkomstdata';

  @override
  String get fritzboxSearching => 'Sök efter Fritz!Box...';

  @override
  String get fritzboxNotFound => 'Fritz!Box hittades inte';

  @override
  String get fritzboxNotFoundDescription =>
      'Fritz!Box kunde inte hittas automatiskt. Vänligen ange adressen manuellt.';

  @override
  String get fritzboxHostLabel => 'Fritz!Boxadress';

  @override
  String get fritzboxRetrySearch => 'Sök igen';

  @override
  String get fritzboxManualConnect => 'Anslut';

  @override
  String get fritzboxLoginDescription =>
      'Ange dina Fritz!Box-åtkomstuppgifter. Du hittar dem i Fritz!Box användargränssnitt under System > Fritz!Box-användare.';

  @override
  String get fritzboxShowUsername => 'Ange användarnamn';

  @override
  String get fritzboxShowUsernameHint => 'Standardanvändaren används normalt';

  @override
  String get fritzboxUsernameLabel => 'Användarens namn';

  @override
  String get fritzboxPasswordLabel => 'Lösenord';

  @override
  String get fritzboxCredentialsNote =>
      'Dina åtkomstuppgifter lagras på ett säkert sätt på din enhet.';

  @override
  String get fritzboxTestAndSave => 'Testning och sparande';

  @override
  String get fritzboxConnectionFailed =>
      'Anslutningen misslyckades. Vänligen kontrollera åtkomstdata.';

  @override
  String get fritzboxFillAllFields => 'Vänligen fyll i alla fält.';

  @override
  String get fritzboxOfflineBanner =>
      'Fritz! box ej nåbar - visa sparade samtal';

  @override
  String get sourceMobile => 'Mobil';

  @override
  String get sourceFritzbox => 'Fritz!Box';

  @override
  String get fritzboxStepBlocklist => 'Skydd mot skräppost';

  @override
  String get fritzboxStepBlocklistSubtitle => 'Upprätta blocklista';

  @override
  String get fritzboxBlocklistDescription =>
      'Välj hur din Fritz!Box ska skyddas mot spam-samtal.';

  @override
  String get fritzboxCardDavTitle => 'CardDAV blocklista';

  @override
  String get fritzboxCardDavDescription =>
      'Fritz!Box synkroniserar blockeringslistan direkt med PhoneBlock. Rekommenderas för FRITZ!OS 7.20+.';

  @override
  String get fritzboxSkipBlocklist => 'Ställ in senare';

  @override
  String get fritzboxSkipBlocklistDescription =>
      'Du kan aktivera skräppostskyddet senare i inställningarna.';

  @override
  String get fritzboxVersionTooOldForCardDav =>
      'CardDAV kräver FRITZ!OS 7.20 eller nyare. Din Fritz!Box har en äldre version.';

  @override
  String get fritzboxFinishSetup => 'Slutföra installationen';

  @override
  String get fritzboxPhoneBlockNotLoggedIn =>
      'Vänligen logga in på PhoneBlock först.';

  @override
  String get fritzboxCannotGetUsername =>
      'PhoneBlock-användarnamnet kunde inte hämtas.';

  @override
  String get fritzboxBlocklistConfigFailed =>
      'Blocklistan kunde inte sättas upp.';

  @override
  String get fritzboxCardDavStatus => 'CardDAV-status';

  @override
  String get fritzboxCardDavStatusSynced => 'Synkroniserad';

  @override
  String get fritzboxCardDavStatusPending => 'Synkronisering pågår';

  @override
  String get fritzboxCardDavStatusError => 'Synkroniseringsfel';

  @override
  String get fritzboxCardDavStatusDisabled => 'Avaktiverad';

  @override
  String get fritzboxCardDavNote =>
      'Fritz!Box synkroniserar telefonboken en gång om dagen vid midnatt.';

  @override
  String get fritzboxBlocklistMode => 'Läge för skydd mot skräppost';

  @override
  String get fritzboxBlocklistModeCardDav =>
      'CardDAV (automatisk synkronisering)';

  @override
  String get fritzboxBlocklistModeNone => 'Ej aktiverad';

  @override
  String get fritzboxEnableCardDav => 'Aktivera CardDAV';

  @override
  String get fritzboxEnableCardDavDescription =>
      'Synkronisera blockeringslistan för skräppost direkt med Fritz!Box';

  @override
  String get fritzboxCardDavEnabled => 'CardDAV blocklista aktiverad';

  @override
  String get fritzboxDisableCardDav => 'Avaktivera CardDAV';

  @override
  String get fritzboxDisableCardDavTitle => 'Avaktivera CardDAV?';

  @override
  String get fritzboxDisableCardDavMessage =>
      'Blocklistan CardDAV har tagits bort från Fritz!';

  @override
  String get fritzboxDisable => 'Avaktivera';

  @override
  String get fritzboxCardDavDisabled => 'CardDAV blocklista avaktiverad';

  @override
  String get fritzboxAnswerbotTitle => 'Telefonsvarare';

  @override
  String get fritzboxAnswerbotActive => 'Telefonsvarare aktiv';

  @override
  String get fritzboxAnswerbotDescription =>
      'SPAM-samtal besvaras automatiskt av PhoneBlocks telefonsvarare.';

  @override
  String get fritzboxEnableAnswerbot => 'Aktivera telefonsvarare';

  @override
  String get fritzboxEnableAnswerbotDescription =>
      'Få SPAM-samtal automatiskt besvarade av PhoneBlocks telefonsvarare';

  @override
  String get fritzboxDisableAnswerbot => 'Avaktivera telefonsvararen';

  @override
  String get fritzboxDisableAnswerbotTitle => 'Avaktivera telefonsvararen?';

  @override
  String get fritzboxDisableAnswerbotMessage =>
      'Telefonsvararen PhoneBlock tas bort från Fritz!Box och avaktiveras på servern.';

  @override
  String get fritzboxAnswerbotEnabled => 'Telefonsvarare aktiverad';

  @override
  String get fritzboxAnswerbotDisabled => 'Telefonsvarare avaktiverad';

  @override
  String get fritzboxAnswerbotSetupFailed =>
      'Telefonsvararen kunde inte ställas in.';

  @override
  String get fritzboxAnswerbotStepCreating => 'Skapa en telefonsvarare...';

  @override
  String get fritzboxAnswerbotStepDetecting => 'Kontrollera extern åtkomst...';

  @override
  String get fritzboxAnswerbotStepDynDns => 'Konfigurera DynDNS ...';

  @override
  String get fritzboxAnswerbotStepWaitingDynDns =>
      'Väntar på DynDNS-registrering...';

  @override
  String get fritzboxAnswerbotStepSip => 'Registrera SIP-enhet...';

  @override
  String get fritzboxAnswerbotStepInternetAccess =>
      'Aktivera internetåtkomst...';

  @override
  String get fritzboxAnswerbotStepEnabling => 'Aktivera telefonsvararen...';

  @override
  String get fritzboxAnswerbotStepWaiting => 'Väntar på registrering...';

  @override
  String get fritzboxAnswerbotSetupTitle => 'Konfigurera en telefonsvarare';

  @override
  String get fritzboxAnswerbotSetupSuccess =>
      'Telefonsvararen har konfigurerats framgångsrikt och är nu aktiv.';

  @override
  String fritzboxAnswerbotSetupErrorDetail(String error) {
    return 'Fel: $error';
  }

  @override
  String get fritzboxAnswerbotStepSecondFactor =>
      'Vänligen bekräfta åtkomst på din Fritz!Box...';

  @override
  String get fritzboxSecondFactorButton => 'Tryck på valfri knapp på Fritz!Box';

  @override
  String fritzboxSecondFactorDtmf(String code) {
    return 'Eller slå $code på en ansluten telefon';
  }
}
