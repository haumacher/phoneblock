// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Danish (`da`).
class AppLocalizationsDa extends AppLocalizations {
  AppLocalizationsDa([String locale = 'da']) : super(locale);

  @override
  String get appTitle => 'PhoneBlock Mobil';

  @override
  String get settings => 'Indstillinger';

  @override
  String get deleteAll => 'Slet alle';

  @override
  String get noCallsYet => 'Ingen opkald filtreret endnu';

  @override
  String get noCallsDescription =>
      'PhoneBlock screener automatisk indgående opkald og blokerer SPAM-opkald.';

  @override
  String get blocked => 'Blokeret';

  @override
  String get accepted => 'Accepteret';

  @override
  String get missed => 'Ubesvaret';

  @override
  String votes(int count) {
    return '$count stemmer';
  }

  @override
  String get viewOnPhoneBlock => 'Vis på PhoneBlock';

  @override
  String get confirmDeleteAll => 'Slet alle filtrerede opkald?';

  @override
  String get confirmDeleteAllMessage => 'Denne handling kan ikke fortrydes.';

  @override
  String get cancel => 'Annuller';

  @override
  String get delete => 'Sletning';

  @override
  String get settingsTitle => 'Indstillinger';

  @override
  String get callScreening => 'Filtrering af opkald';

  @override
  String get minSpamReports => 'Minimalt med SPAM-beskeder';

  @override
  String minSpamReportsDescription(int count) {
    return 'Numre blokeres fra $count beskeder og fremefter';
  }

  @override
  String get blockNumberRanges => 'Bloknummer-intervaller';

  @override
  String get blockNumberRangesDescription =>
      'Bloker områder med mange SPAM-beskeder';

  @override
  String get minSpamReportsInRange => 'Minimale SPAM-beskeder i området';

  @override
  String minSpamReportsInRangeDescription(int count) {
    return 'Områder er blokeret fra $count beskeder og fremefter';
  }

  @override
  String get about => 'Omkring';

  @override
  String get version => 'Version';

  @override
  String get developer => 'Udvikler';

  @override
  String get developerName => 'Bernhard Haumacher';

  @override
  String get website => 'Hjemmeside';

  @override
  String get websiteUrl => 'phoneblock.net';

  @override
  String get sourceCode => 'Kildekode';

  @override
  String get sourceCodeLicense => 'Open Source (GPL-3.0)';

  @override
  String get aboutDescription =>
      'PhoneBlock er et open source-projekt uden sporing og uden reklamer. Tjenesten er finansieret af donationer.';

  @override
  String get donate => 'Donationer';

  @override
  String pendingCallsNotification(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count nye filtrerede opkald',
      one: '1 nyt filtreret opkald',
    );
    return '$_temp0.';
  }

  @override
  String get tapToOpen => 'Tryk for at åbne appen';

  @override
  String get setupWelcome => 'Velkommen til PhoneBlock Mobile';

  @override
  String get setupPermissionsRequired => 'Nødvendige tilladelser';

  @override
  String get grantPermission => 'Giv tilladelse';

  @override
  String get continue_ => 'Yderligere';

  @override
  String get finish => 'Færdig';

  @override
  String get loginRequired => 'Registrering af PhoneBlock';

  @override
  String get loginToPhoneBlock => 'Registrer dig med PhoneBlock';

  @override
  String get verifyingLogin => 'Registreringen vil blive kontrolleret...';

  @override
  String get loginFailed => 'Login mislykkedes';

  @override
  String get loginSuccess => 'Registrering vellykket!';

  @override
  String get reportAsLegitimate => 'Rapporter som legitim';

  @override
  String get reportAsSpam => 'Rapporter som SPAM';

  @override
  String get viewOnPhoneBlockMenu => 'Se på PhoneBlock';

  @override
  String get deleteCall => 'Sletning';

  @override
  String get report => 'Rapport';

  @override
  String get notLoggedIn => 'Ikke registreret. Log venligst ind.';

  @override
  String reportedAsLegitimate(String phoneNumber) {
    return '$phoneNumber rapporteret som legitimt';
  }

  @override
  String reportError(String error) {
    return 'Fejl ved rapportering: $error.';
  }

  @override
  String reportedAsSpam(String phoneNumber) {
    return '$phoneNumber rapporteret som SPAM';
  }

  @override
  String get selectSpamCategory => 'Vælg SPAM-kategori';

  @override
  String get errorDeletingAllCalls => 'Fejl ved sletning af alle kald';

  @override
  String get errorDeletingCall => 'Fejl ved annullering af opkald';

  @override
  String get notLoggedInShort => 'Ikke registreret';

  @override
  String get errorOpeningPhoneBlock => 'Fejl ved åbning af PhoneBlock.';

  @override
  String get permissionNotGranted => 'Der er ikke givet tilladelse.';

  @override
  String get setupTitle => 'PhoneBlock Mobile - opsætning';

  @override
  String get welcome => 'Velkommen til';

  @override
  String get connectPhoneBlockAccount => 'Tilslut PhoneBlock-konto';

  @override
  String get permissions => 'Tilladelser';

  @override
  String get allowCallFiltering => 'Gør det muligt at filtrere opkald';

  @override
  String get done => 'Færdig';

  @override
  String get setupComplete => 'Installation afsluttet';

  @override
  String get minReportsCount => 'Minimum antal beskeder';

  @override
  String callsBlockedAfterReports(int count) {
    return 'Opkald er blokeret fra $count beskeder';
  }

  @override
  String rangesBlockedAfterReports(int count) {
    return 'Områder er blokeret fra $count beskeder og fremefter';
  }

  @override
  String get welcomeMessage =>
      'Velkommen til PhoneBlock Mobile!\n\nDenne app hjælper dig med at blokere spamopkald automatisk. Du skal have en gratis konto hos PhoneBlock.net.\n\nOpret forbindelse til din PhoneBlock-konto for at fortsætte:';

  @override
  String get connectToPhoneBlock => 'Opret forbindelse med PhoneBlock';

  @override
  String get connectedToPhoneBlock => 'Forbundet med PhoneBlock';

  @override
  String get accountConnectedSuccessfully => '✓ Konto tilsluttet med succes';

  @override
  String get permissionsMessage =>
      'For automatisk at blokere spamopkald kræver PhoneBlock Mobile tilladelse til at kontrollere indgående opkald.\n\nDenne tilladelse er nødvendig, for at appen kan fungere:';

  @override
  String get permissionGranted => 'Tilladelse givet';

  @override
  String get permissionGrantedSuccessfully => '✓ Godkendelse givet med succes';

  @override
  String get setupCompleteMessage =>
      'Installationen er færdig!\n\nPhoneBlock Mobile er nu klar til at blokere spamopkald. Appen screener automatisk indgående opkald og blokerer kendte spamnumre baseret på PhoneBlock-databasen.\n\nTryk på \"Done\" for at gå til hovedvisningen.';

  @override
  String get verifyingLoginTitle => 'Tjek login';

  @override
  String get loginSuccessMessage => 'Login vellykket!';

  @override
  String get redirectingToSetup => 'Videresendelse til anlægget...';

  @override
  String tokenVerificationFailed(String error) {
    return 'Token-verifikation mislykkedes: $error.';
  }

  @override
  String get backToSetup => 'Tilbage til anlægget';

  @override
  String get tokenBeingVerified => 'Token er tjekket...';

  @override
  String get failedToOpenPhoneBlock => 'PhoneBlock kunne ikke åbnes.';

  @override
  String get ratingLegitimate => 'Legitim';

  @override
  String get ratingAdvertising => 'Reklame';

  @override
  String get ratingSpam => 'SPAM';

  @override
  String get ratingPingCall => 'Ping-opkald';

  @override
  String get ratingGamble => 'Konkurrence';

  @override
  String get ratingFraud => 'Bedrageri';

  @override
  String get ratingPoll => 'Undersøgelse';

  @override
  String get noLoginTokenReceived => 'Intet login-token modtaget.';

  @override
  String get settingSaved => 'Indstilling gemt';

  @override
  String get errorSaving => 'Fejl ved lagring';

  @override
  String ratePhoneNumber(String phoneNumber) {
    return 'Bedøm $phoneNumber.';
  }

  @override
  String reportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count klager',
      one: '1 klage',
    );
    return '$_temp0.';
  }

  @override
  String rangeReportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Klager i talområdet',
      one: '1 klage i nummerrækken',
    );
    return '$_temp0.';
  }

  @override
  String legitimateReportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Legitime beskeder',
      one: '1 Legitim besked',
    );
    return '$_temp0.';
  }

  @override
  String get noReports => 'Ingen beskeder';

  @override
  String todayTime(String time) {
    return 'I dag, $time.';
  }

  @override
  String yesterdayTime(String time) {
    return 'I går, $time.';
  }

  @override
  String get callHistoryRetention => 'Lagring af opkaldshistorik';

  @override
  String retentionPeriodDescription(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: 'Behold opkald $days dage',
      one: 'Behold opkald 1 dag',
    );
    return '$_temp0.';
  }

  @override
  String get retentionInfinite => 'Behold alle opkald';

  @override
  String retentionDays(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: '$days dage',
      one: '1 dag',
    );
    return '$_temp0.';
  }

  @override
  String get retentionInfiniteOption => 'Ubegrænset';

  @override
  String get addCommentSpam => 'Tilføj kommentar (valgfrit)';

  @override
  String get commentHintSpam =>
      'Hvorfor er det spam? Hvad drejede opkaldet sig om? Vær venlig at være høflig.';

  @override
  String get addCommentLegitimate => 'Tilføj kommentar (valgfrit)';

  @override
  String get commentHintLegitimate =>
      'Hvorfor er dette legitimt? Hvem har ringet til dig? Vær venlig at være høflig.';

  @override
  String get serverSettings => 'Serverindstillinger';

  @override
  String get serverSettingsDescription =>
      'Administrer indstillingerne for din PhoneBlock-konto';

  @override
  String get searchNumber => 'Søg nummer';

  @override
  String get searchPhoneNumber => 'Søg efter telefonnummer';

  @override
  String get enterPhoneNumber => 'Indtast telefonnummer';

  @override
  String get phoneNumberHint => 'f.eks. +49 123 456789';

  @override
  String get search => 'Søg efter';

  @override
  String get invalidPhoneNumber => 'Indtast venligst et gyldigt telefonnummer';

  @override
  String get blacklistTitle => 'Sortliste';

  @override
  String get blacklistDescription => 'Numre, der er blokeret af dig';

  @override
  String get whitelistTitle => 'Hvidliste';

  @override
  String get whitelistDescription => 'Numre, du har markeret som legitime';

  @override
  String get blacklistEmpty => 'Din sortliste er tom';

  @override
  String get whitelistEmpty => 'Din hvidliste er tom';

  @override
  String get blacklistEmptyHelp =>
      'Tilføj numre ved at rapportere uønskede opkald som spam.';

  @override
  String get whitelistEmptyHelp =>
      'Tilføj numre ved at rapportere blokerede opkald som legitime.';

  @override
  String get errorLoadingList => 'Fejl ved indlæsning af listen';

  @override
  String get numberRemovedFromList => 'Antal fjernet';

  @override
  String get errorRemovingNumber => 'Fejl ved fjernelse af nummer';

  @override
  String get confirmRemoval => 'Bekræft fjernelse';

  @override
  String confirmRemoveFromBlacklist(Object phone) {
    return 'Fjerne $phone fra den sorte liste?';
  }

  @override
  String confirmRemoveFromWhitelist(Object phone) {
    return 'Fjerne $phone fra hvidlisten?';
  }

  @override
  String get remove => 'Fjerne';

  @override
  String get retry => 'Prøv igen';

  @override
  String get editComment => 'Rediger kommentar';

  @override
  String get commentLabel => 'Kommentar';

  @override
  String get commentHint => 'Tilføj en note til dette nummer';

  @override
  String get save => 'Gemme';

  @override
  String get commentUpdated => 'Kommentar opdateret';

  @override
  String get errorUpdatingComment => 'Fejl ved opdatering af kommentar';

  @override
  String get appearance => 'Udseende';

  @override
  String get themeMode => 'Design';

  @override
  String get themeModeDescription => 'Vælg et lyst eller mørkt design';

  @override
  String get themeModeSystem => 'Systemets standard';

  @override
  String get themeModeLight => 'Lys';

  @override
  String get themeModeDark => 'Mørk';

  @override
  String get experimentalFeatures => 'Eksperimentelle funktioner';

  @override
  String get answerbotFeature => 'Telefonsvarer (Answerbot)';

  @override
  String get answerbotFeatureDescription =>
      'Eksperimentelt: Administrer SPAM-telefonsvareren til Fritz!Box i appen';

  @override
  String get answerbotMenuTitle => 'Telefonsvarer';

  @override
  String get answerbotMenuDescription => 'Administrer SPAM-telefonsvareren';

  @override
  String potentialSpamLabel(String rating) {
    return 'Mistænkelig: $rating.';
  }

  @override
  String get statistics => 'Statistik';

  @override
  String get blockedCallsCount => 'Blokerede opkald';

  @override
  String get suspiciousCallsCount => 'Mistænkelige opkald';

  @override
  String get fritzboxTitle => 'Fritz!Box';

  @override
  String get fritzboxConnected => 'Forbundet';

  @override
  String get fritzboxConnectedNotProtected => 'Forbundet, ikke beskyttet';

  @override
  String get fritzboxOffline => 'Ikke tilgængelig';

  @override
  String get fritzboxError => 'Forbindelsesfejl';

  @override
  String get fritzboxNotConfiguredShort => 'Ikke sat op';

  @override
  String get fritzboxNotConfigured => 'Ingen Fritz!Box sat op';

  @override
  String get fritzboxNotConfiguredDescription =>
      'Tilslut din Fritz!Box for at se opkald fra din fastnettelefon.';

  @override
  String get fritzboxConnect => 'Tilslut Fritz!Box';

  @override
  String get fritzboxDisconnect => 'Afbryd forbindelsen til Fritz!Box';

  @override
  String get fritzboxDisconnectTitle => 'Afbryd Fritz! boksen?';

  @override
  String get fritzboxDisconnectMessage =>
      'De gemte opkald og adgangsdata slettes.';

  @override
  String get fritzboxSyncNow => 'Synkroniser nu';

  @override
  String get fritzboxSyncDescription => 'Hentning af opkaldslisten fra Fritz!';

  @override
  String fritzboxSyncComplete(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count nye opkald synkroniseret',
      one: '1 nyt opkald synkroniseret',
      zero: 'Ingen nye opkald',
    );
    return '$_temp0.';
  }

  @override
  String get fritzboxSyncError => 'Fejl under synkronisering';

  @override
  String get fritzboxVersion => 'FRITZ!OS-version';

  @override
  String get fritzboxHost => 'Adresse';

  @override
  String get fritzboxCachedCalls => 'Gemte opkald';

  @override
  String get fritzboxLastSync => 'Sidste synkronisering';

  @override
  String get fritzboxJustNow => 'Lige nu';

  @override
  String fritzboxMinutesAgo(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Før $count minutter',
      one: '1 minut siden',
    );
    return '$_temp0.';
  }

  @override
  String fritzboxHoursAgo(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count timer siden',
      one: '1 time siden',
    );
    return '$_temp0.';
  }

  @override
  String get fritzboxWizardTitle => 'Tilslut Fritz!Box';

  @override
  String get fritzboxStepDetection => 'Find Fritz!Box';

  @override
  String get fritzboxStepDetectionSubtitle => 'Automatisk søgning i netværket';

  @override
  String get fritzboxStepLogin => 'Log ind';

  @override
  String get fritzboxStepLoginSubtitle => 'Indtast adgangsdata';

  @override
  String get fritzboxSearching => 'Søg efter Fritz!Box...';

  @override
  String get fritzboxNotFound => 'Fritz!Box ikke fundet';

  @override
  String get fritzboxNotFoundDescription =>
      'Fritz!Box blev ikke fundet automatisk. Indtast venligst adressen manuelt.';

  @override
  String get fritzboxHostLabel => 'Fritz!Box-adresse';

  @override
  String get fritzboxRetrySearch => 'Søg igen';

  @override
  String get fritzboxManualConnect => 'Opret forbindelse';

  @override
  String get fritzboxLoginDescription =>
      'Indtast dine Fritz!Box-adgangsdata. Du kan finde dem i Fritz!Box-brugergrænsefladen under System > Fritz!Box-bruger.';

  @override
  String get fritzboxShowUsername => 'Indtast brugernavn';

  @override
  String get fritzboxShowUsernameHint => 'Standardbrugeren bruges normalt';

  @override
  String get fritzboxUsernameLabel => 'Brugernavn';

  @override
  String get fritzboxPasswordLabel => 'Adgangskode';

  @override
  String get fritzboxCredentialsNote =>
      'Dine adgangsdata gemmes sikkert på din enhed.';

  @override
  String get fritzboxTestAndSave => 'Testning og lagring';

  @override
  String get fritzboxConnectionFailed =>
      'Forbindelsen mislykkedes. Kontroller venligst adgangsdataene.';

  @override
  String get fritzboxFillAllFields => 'Udfyld venligst alle felter.';

  @override
  String get fritzboxOfflineBanner =>
      'Fritz! boksen kan ikke nås - vis gemte opkald';

  @override
  String get sourceMobile => 'Mobil';

  @override
  String get sourceFritzbox => 'Fritz!Box';

  @override
  String get fritzboxStepBlocklist => 'Beskyttelse mod spam';

  @override
  String get fritzboxStepBlocklistSubtitle => 'Opsæt blokeringsliste';

  @override
  String get fritzboxBlocklistDescription =>
      'Vælg, hvordan din Fritz!Box skal beskyttes mod spamopkald.';

  @override
  String get fritzboxCardDavTitle => 'CardDAV-blokeringsliste';

  @override
  String get fritzboxCardDavDescription =>
      'Fritz!Box synkroniserer blokeringslisten direkte med PhoneBlock. Anbefales til FRITZ!OS 7.20+.';

  @override
  String get fritzboxSkipBlocklist => 'Sæt op senere';

  @override
  String get fritzboxSkipBlocklistDescription =>
      'Du kan aktivere spambeskyttelse senere i indstillingerne.';

  @override
  String get fritzboxVersionTooOldForCardDav =>
      'CardDAV kræver FRITZ!OS 7.20 eller nyere. Din Fritz!Box har en ældre version.';

  @override
  String get fritzboxFinishSetup => 'Færdiggør opsætning';

  @override
  String get fritzboxPhoneBlockNotLoggedIn =>
      'Log venligst ind på PhoneBlock først.';

  @override
  String get fritzboxCannotGetUsername =>
      'PhoneBlock-brugernavnet kunne ikke hentes.';

  @override
  String get fritzboxBlocklistConfigFailed =>
      'Blokeringslisten kunne ikke oprettes.';

  @override
  String get fritzboxCardDavStatus => 'CardDAV-status';

  @override
  String get fritzboxCardDavStatusSynced => 'Synkroniseret';

  @override
  String get fritzboxCardDavStatusPending => 'Synkronisering afventer';

  @override
  String get fritzboxCardDavStatusError => 'Synkroniseringsfejl';

  @override
  String get fritzboxCardDavStatusDisabled => 'Deaktiveret';

  @override
  String get fritzboxCardDavNote =>
      'Fritz!Box synkroniserer telefonbogen en gang om dagen ved midnat.';

  @override
  String get fritzboxBlocklistMode => 'Beskyttelse mod spam';

  @override
  String get fritzboxBlocklistModeCardDav =>
      'CardDAV (automatisk synkronisering)';

  @override
  String get fritzboxBlocklistModeNone => 'Ikke aktiveret';

  @override
  String get fritzboxEnableCardDav => 'Aktiver CardDAV';

  @override
  String get fritzboxEnableCardDavDescription =>
      'Synkroniser spam-blokeringslisten direkte med Fritz!Box';

  @override
  String get fritzboxCardDavEnabled => 'CardDAV-blokeringsliste aktiveret';

  @override
  String get fritzboxDisableCardDav => 'Deaktiver CardDAV';

  @override
  String get fritzboxDisableCardDavTitle => 'Deaktivere CardDAV?';

  @override
  String get fritzboxDisableCardDavMessage =>
      'CardDAV-blokeringslisten er fjernet fra Fritz!';

  @override
  String get fritzboxDisable => 'Deaktiver';

  @override
  String get fritzboxCardDavDisabled => 'CardDAV-blokeringsliste deaktiveret';

  @override
  String get fritzboxAnswerbotTitle => 'Telefonsvarer';

  @override
  String get fritzboxAnswerbotActive => 'Telefonsvarer aktiv';

  @override
  String get fritzboxAnswerbotDescription =>
      'SPAM-opkald besvares automatisk af PhoneBlocks telefonsvarer.';

  @override
  String get fritzboxEnableAnswerbot => 'Aktivér telefonsvareren';

  @override
  String get fritzboxEnableAnswerbotDescription =>
      'Få SPAM-opkald automatisk besvaret af PhoneBlock-svareren';

  @override
  String get fritzboxDisableAnswerbot => 'Deaktiver telefonsvareren';

  @override
  String get fritzboxDisableAnswerbotTitle => 'Deaktivere telefonsvareren?';

  @override
  String get fritzboxDisableAnswerbotMessage =>
      'PhoneBlock-telefonsvareren fjernes fra Fritz!Boxen og deaktiveres på serveren.';

  @override
  String get fritzboxAnswerbotEnabled => 'Telefonsvarer aktiveret';

  @override
  String get fritzboxAnswerbotDisabled => 'Telefonsvarer deaktiveret';

  @override
  String get fritzboxAnswerbotSetupFailed =>
      'Telefonsvareren kunne ikke sættes op.';

  @override
  String get fritzboxAnswerbotStepCreating => 'Opret en telefonsvarer...';

  @override
  String get fritzboxAnswerbotStepDetecting => 'Tjek den eksterne adgang.';

  @override
  String get fritzboxAnswerbotStepDynDns => 'Konfigurer DynDNS...';

  @override
  String get fritzboxAnswerbotStepWaitingDynDns =>
      'Venter på DynDNS-registrering...';

  @override
  String get fritzboxAnswerbotStepSip => 'Registrer SIP-enhed...';

  @override
  String get fritzboxAnswerbotStepInternetAccess => 'Aktivér internetadgang...';

  @override
  String get fritzboxAnswerbotStepEnabling => 'Aktivér telefonsvareren...';

  @override
  String get fritzboxAnswerbotStepWaiting =>
      'Venter på at blive registreret...';

  @override
  String get fritzboxAnswerbotSetupTitle => 'Opsætning af en telefonsvarer';

  @override
  String get fritzboxAnswerbotSetupSuccess =>
      'Telefonsvareren er blevet sat op og er nu aktiv.';

  @override
  String fritzboxAnswerbotSetupErrorDetail(String error) {
    return 'Fejl: $error.';
  }

  @override
  String get fritzboxAnswerbotStepSecondFactor =>
      'Bekræft venligst adgang på din Fritz!Box...';

  @override
  String get fritzboxSecondFactorButton =>
      'Tryk på en vilkårlig knap på Fritz!Box';

  @override
  String fritzboxSecondFactorDtmf(String code) {
    return 'Eller ring $code på en tilsluttet telefon';
  }
}
