// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Norwegian Bokmål (`nb`).
class AppLocalizationsNb extends AppLocalizations {
  AppLocalizationsNb([String locale = 'nb']) : super(locale);

  @override
  String get appTitle => 'PhoneBlock Mobile';

  @override
  String get settings => 'Innstillinger';

  @override
  String get deleteAll => 'Slett alle';

  @override
  String get noCallsYet => 'Ingen samtaler filtrert ennå';

  @override
  String get noCallsDescription =>
      'PhoneBlock screener automatisk innkommende anrop og blokkerer SPAM-anrop.';

  @override
  String get blocked => 'Sperret';

  @override
  String get accepted => 'Godkjent';

  @override
  String get missed => 'Bommet';

  @override
  String votes(int count) {
    return '$count stemmer';
  }

  @override
  String get viewOnPhoneBlock => 'Vis på PhoneBlock';

  @override
  String get confirmDeleteAll => 'Slette alle filtrerte anrop?';

  @override
  String get confirmDeleteAllMessage =>
      'Denne handlingen kan ikke gjøres ugjort.';

  @override
  String get cancel => 'Avbryt';

  @override
  String get delete => 'Slett';

  @override
  String get settingsTitle => 'Innstillinger';

  @override
  String get callScreening => 'Filtrering av anrop';

  @override
  String get minSpamReports => 'Minimalt med SPAM-meldinger';

  @override
  String minSpamReportsDescription(int count) {
    return 'Numrene er blokkert fra og med $count meldinger';
  }

  @override
  String get blockNumberRanges => 'Blokknummerområder';

  @override
  String get blockNumberRangesDescription =>
      'Blokker områder med mange SPAM-meldinger';

  @override
  String get minSpamReportsInRange => 'Minimalt med SPAM-meldinger i området';

  @override
  String minSpamReportsInRangeDescription(int count) {
    return 'Områder er blokkert fra og med $count meldinger';
  }

  @override
  String get about => 'Om';

  @override
  String get version => 'Versjon';

  @override
  String get developer => 'Utvikler';

  @override
  String get developerName => 'Bernhard Haumacher';

  @override
  String get website => 'Nettsted';

  @override
  String get websiteUrl => 'phoneblock.net';

  @override
  String get sourceCode => 'Kildekode';

  @override
  String get sourceCodeLicense => 'Åpen kildekode (GPL-3.0)';

  @override
  String get aboutDescription =>
      'PhoneBlock er et åpen kildekode-prosjekt uten sporing og uten reklame. Tjenesten finansieres av donasjoner.';

  @override
  String get donate => 'Donasjoner';

  @override
  String pendingCallsNotification(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count nye filtrerte anrop',
      one: '1 nytt filtrert anrop',
    );
    return '$_temp0';
  }

  @override
  String get tapToOpen => 'Trykk for å åpne appen';

  @override
  String get setupWelcome => 'Velkommen til PhoneBlock Mobile';

  @override
  String get setupPermissionsRequired => 'Nødvendige autorisasjoner';

  @override
  String get grantPermission => 'Gi tillatelse';

  @override
  String get continue_ => 'Videre';

  @override
  String get finish => 'Klar';

  @override
  String get loginRequired => 'PhoneBlock-registrering';

  @override
  String get loginToPhoneBlock => 'Registrer deg med PhoneBlock';

  @override
  String get verifyingLogin => 'Registreringen vil bli sjekket...';

  @override
  String get loginFailed => 'Innlogging mislyktes';

  @override
  String get loginSuccess => 'Registrering vellykket!';

  @override
  String get reportAsLegitimate => 'Rapporter som legitim';

  @override
  String get reportAsSpam => 'Rapporter som SPAM';

  @override
  String get viewOnPhoneBlockMenu => 'Vis på PhoneBlock';

  @override
  String get deleteCall => 'Slett';

  @override
  String get report => 'Rapport';

  @override
  String get notLoggedIn => 'Ikke registrert. Vennligst logg inn.';

  @override
  String reportedAsLegitimate(String phoneNumber) {
    return '$phoneNumber rapportert som legitimt';
  }

  @override
  String reportError(String error) {
    return 'Feil ved rapportering: $error';
  }

  @override
  String reportedAsSpam(String phoneNumber) {
    return '$phoneNumber rapportert som SPAM';
  }

  @override
  String get selectSpamCategory => 'Velg SPAM-kategori';

  @override
  String get errorDeletingAllCalls => 'Feil ved sletting av alle anrop';

  @override
  String get errorDeletingCall => 'Feil ved avbrytelse av anropet';

  @override
  String get notLoggedInShort => 'Ikke registrert';

  @override
  String get errorOpeningPhoneBlock => 'Feil ved åpning av PhoneBlock.';

  @override
  String get permissionNotGranted => 'Autorisasjon er ikke gitt.';

  @override
  String get setupTitle => 'PhoneBlock Mobile - Oppsett';

  @override
  String get welcome => 'Velkommen til';

  @override
  String get connectPhoneBlockAccount => 'Koble til PhoneBlock-konto';

  @override
  String get permissions => 'Autorisasjoner';

  @override
  String get allowCallFiltering => 'Tillat at anrop filtreres';

  @override
  String get done => 'Klar';

  @override
  String get setupComplete => 'Installasjonen er fullført';

  @override
  String get minReportsCount => 'Minimum antall meldinger';

  @override
  String callsBlockedAfterReports(int count) {
    return 'Anrop er blokkert fra $count meldinger';
  }

  @override
  String rangesBlockedAfterReports(int count) {
    return 'Områder er blokkert fra og med $count meldinger';
  }

  @override
  String get welcomeMessage =>
      'Velkommen til PhoneBlock Mobile!\n\nDenne appen hjelper deg med å blokkere spam-anrop automatisk. Du trenger en gratis konto hos PhoneBlock.net.\n\nKoble til PhoneBlock-kontoen din for å fortsette:';

  @override
  String get connectToPhoneBlock => 'Koble deg til PhoneBlock';

  @override
  String get connectedToPhoneBlock => 'Tilkoblet med PhoneBlock';

  @override
  String get accountConnectedSuccessfully => '✓ Konto vellykket tilkoblet';

  @override
  String get permissionsMessage =>
      'For å automatisk blokkere spam-anrop krever PhoneBlock Mobile autorisasjon til å sjekke innkommende anrop.\n\nDenne autorisasjonen er nødvendig for at appen skal fungere:';

  @override
  String get permissionGranted => 'Tillatelse gitt';

  @override
  String get permissionGrantedSuccessfully =>
      '✓ Autorisasjon vellykket innvilget';

  @override
  String get setupCompleteMessage =>
      'Installasjonen er fullført!\n\nPhoneBlock Mobile er nå klar til å blokkere spam-anrop. Appen screener automatisk innkommende anrop og blokkerer kjente spamnumre basert på PhoneBlock-databasen.\n\nTrykk på \"Ferdig\" for å gå til hovedvisningen.';

  @override
  String get verifyingLoginTitle => 'Sjekk innlogging';

  @override
  String get loginSuccessMessage => 'Innlogging vellykket!';

  @override
  String get redirectingToSetup => 'Videresending til anlegget...';

  @override
  String tokenVerificationFailed(String error) {
    return 'Token-verifisering mislyktes: $error';
  }

  @override
  String get backToSetup => 'Tilbake til anlegget';

  @override
  String get tokenBeingVerified => 'Token er sjekket...';

  @override
  String get failedToOpenPhoneBlock => 'PhoneBlock kunne ikke åpnes.';

  @override
  String get ratingLegitimate => 'Legitime';

  @override
  String get ratingAdvertising => 'Reklame';

  @override
  String get ratingSpam => 'SPAM';

  @override
  String get ratingPingCall => 'Ping-anrop';

  @override
  String get ratingGamble => 'Konkurranse';

  @override
  String get ratingFraud => 'Bedrageri';

  @override
  String get ratingPoll => 'Spørreundersøkelse';

  @override
  String get noLoginTokenReceived => 'Ingen påloggingstoken mottatt.';

  @override
  String get settingSaved => 'Innstilling lagret';

  @override
  String get errorSaving => 'Feil ved lagring';

  @override
  String ratePhoneNumber(String phoneNumber) {
    return 'Takst $phoneNumber';
  }

  @override
  String reportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count klager',
      one: '1 klage',
    );
    return '$_temp0';
  }

  @override
  String rangeReportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Klager i tallområdet',
      one: '1 Klage i nummerserien',
    );
    return '$_temp0';
  }

  @override
  String legitimateReportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Legitime meldinger',
      one: '1 Legitim melding',
    );
    return '$_temp0';
  }

  @override
  String get noReports => 'Ingen meldinger';

  @override
  String todayTime(String time) {
    return 'I dag, $time';
  }

  @override
  String yesterdayTime(String time) {
    return 'I går, $time';
  }

  @override
  String get callHistoryRetention => 'Lagring av anropshistorikk';

  @override
  String retentionPeriodDescription(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: 'Behold samtaler $days dager',
      one: 'Behold samtaler 1 dag',
    );
    return '$_temp0';
  }

  @override
  String get retentionInfinite => 'Behold alle samtaler';

  @override
  String retentionDays(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: '$days dager',
      one: '1 dag',
    );
    return '$_temp0';
  }

  @override
  String get retentionInfiniteOption => 'Ubegrenset';

  @override
  String get addCommentSpam => 'Legg til kommentar (valgfritt)';

  @override
  String get commentHintSpam =>
      'Hvorfor er dette spam? Hva handlet samtalen om? Vennligst vær høflig.';

  @override
  String get addCommentLegitimate => 'Legg til kommentar (valgfritt)';

  @override
  String get commentHintLegitimate =>
      'Hvorfor er dette legitimt? Hvem ringte deg? Vennligst vær høflig.';

  @override
  String get serverSettings => 'Serverinnstillinger';

  @override
  String get serverSettingsDescription =>
      'Administrer innstillingene for PhoneBlock-kontoen din';

  @override
  String get searchNumber => 'Søk nummer';

  @override
  String get searchPhoneNumber => 'Søk etter telefonnummer';

  @override
  String get enterPhoneNumber => 'Skriv inn telefonnummer';

  @override
  String get phoneNumberHint => 'f.eks. +49 123 456789';

  @override
  String get search => 'Søk';

  @override
  String get invalidPhoneNumber =>
      'Vennligst skriv inn et gyldig telefonnummer';

  @override
  String get blacklistTitle => 'Svarteliste';

  @override
  String get blacklistDescription => 'Nummer som er blokkert av deg';

  @override
  String get whitelistTitle => 'Hviteliste';

  @override
  String get whitelistDescription => 'Numre du har merket som legitime';

  @override
  String get blacklistEmpty => 'Svartelisten din er tom';

  @override
  String get whitelistEmpty => 'Hvitelisten din er tom';

  @override
  String get blacklistEmptyHelp =>
      'Legg til numre ved å rapportere uønskede anrop som søppelpost.';

  @override
  String get whitelistEmptyHelp =>
      'Legg til numre ved å rapportere blokkerte anrop som legitime.';

  @override
  String get errorLoadingList => 'Feil ved innlasting av listen';

  @override
  String get numberRemovedFromList => 'Antall fjernet';

  @override
  String get errorRemovingNumber => 'Feil ved fjerning av nummeret';

  @override
  String get confirmRemoval => 'Bekreft fjerning';

  @override
  String confirmRemoveFromBlacklist(Object phone) {
    return 'Fjerne $phone fra svartelisten?';
  }

  @override
  String confirmRemoveFromWhitelist(Object phone) {
    return 'Fjerne $phone fra hvitelisten?';
  }

  @override
  String get remove => 'Fjern';

  @override
  String get retry => 'Prøv igjen';

  @override
  String get editComment => 'Rediger kommentar';

  @override
  String get commentLabel => 'Kommentar';

  @override
  String get commentHint => 'Legg til en merknad til dette nummeret';

  @override
  String get save => 'Spar';

  @override
  String get commentUpdated => 'Kommentar oppdatert';

  @override
  String get errorUpdatingComment => 'Feil ved oppdatering av kommentaren';

  @override
  String get appearance => 'Utseende';

  @override
  String get themeMode => 'Design';

  @override
  String get themeModeDescription => 'Velg et lyst eller mørkt design';

  @override
  String get themeModeSystem => 'Systemstandard';

  @override
  String get themeModeLight => 'Lys';

  @override
  String get themeModeDark => 'Mørk';

  @override
  String get experimentalFeatures => 'Eksperimentelle funksjoner';

  @override
  String get answerbotFeature => 'Telefonsvarer (Answerbot)';

  @override
  String get answerbotFeatureDescription =>
      'Eksperimentelt: Administrer SPAM-svareren for Fritz!Box i appen';

  @override
  String get answerbotMenuTitle => 'Telefonsvarer';

  @override
  String get answerbotMenuDescription => 'Administrer SPAM-telefonsvarer';

  @override
  String potentialSpamLabel(String rating) {
    return 'Mistenkelig: $rating';
  }

  @override
  String get statistics => 'Statistikk';

  @override
  String get blockedCallsCount => 'Blokkerte anrop';

  @override
  String get suspiciousCallsCount => 'Mistenkelige samtaler';

  @override
  String get fritzboxTitle => 'Fritz!Box';

  @override
  String get fritzboxConnected => 'Tilkoblet';

  @override
  String get fritzboxConnectedNotProtected => 'Tilkoblet, ikke beskyttet';

  @override
  String get fritzboxOffline => 'Ikke tilgjengelig';

  @override
  String get fritzboxError => 'Feil i tilkoblingen';

  @override
  String get fritzboxNotConfiguredShort => 'Ikke satt opp';

  @override
  String get fritzboxNotConfigured => 'Ingen Fritz!Box satt opp';

  @override
  String get fritzboxNotConfiguredDescription =>
      'Koble til Fritz!Box for å se samtaler fra fasttelefonen din.';

  @override
  String get fritzboxConnect => 'Koble til Fritz!Box';

  @override
  String get fritzboxDisconnect => 'Koble fra Fritz!Box';

  @override
  String get fritzboxDisconnectTitle => 'Koble fra Fritz!-boksen?';

  @override
  String get fritzboxDisconnectMessage =>
      'Lagrede anrop og tilgangsdata slettes.';

  @override
  String get fritzboxSyncNow => 'Synkroniser nå';

  @override
  String get fritzboxSyncDescription => 'Hente samtalelisten fra Fritz!';

  @override
  String fritzboxSyncComplete(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count nye anrop synkronisert',
      one: '1 ny samtale synkronisert',
      zero: 'Ingen nye samtaler',
    );
    return '$_temp0';
  }

  @override
  String get fritzboxSyncError => 'Feil under synkronisering';

  @override
  String get fritzboxVersion => 'FRITZ!OS-versjon';

  @override
  String get fritzboxHost => 'Adresse';

  @override
  String get fritzboxCachedCalls => 'Lagrede anrop';

  @override
  String get fritzboxLastSync => 'Siste synkronisering';

  @override
  String get fritzboxJustNow => 'Akkurat nå';

  @override
  String fritzboxMinutesAgo(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Før $count minutter',
      one: '1 minutt siden',
    );
    return '$_temp0';
  }

  @override
  String fritzboxHoursAgo(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count timer siden',
      one: '1 time siden',
    );
    return '$_temp0';
  }

  @override
  String get fritzboxWizardTitle => 'Koble til Fritz!Box';

  @override
  String get fritzboxStepDetection => 'Finn Fritz!Box';

  @override
  String get fritzboxStepDetectionSubtitle => 'Automatisk søk i nettverket';

  @override
  String get fritzboxStepLogin => 'Logg inn';

  @override
  String get fritzboxStepLoginSubtitle => 'Angi tilgangsdata';

  @override
  String get fritzboxSearching => 'Søk etter Fritz!Box...';

  @override
  String get fritzboxNotFound => 'Fritz!Box ble ikke funnet';

  @override
  String get fritzboxNotFoundDescription =>
      'Fritz!Box ble ikke funnet automatisk. Vennligst skriv inn adressen manuelt.';

  @override
  String get fritzboxHostLabel => 'Fritz!Box-adresse';

  @override
  String get fritzboxRetrySearch => 'Søk igjen';

  @override
  String get fritzboxManualConnect => 'Koble til';

  @override
  String get fritzboxLoginDescription =>
      'Skriv inn dine Fritz!Box-tilgangsdata. Du finner dem i Fritz!Box-brukergrensesnittet under System > Fritz!Box-bruker.';

  @override
  String get fritzboxShowUsername => 'Skriv inn brukernavn';

  @override
  String get fritzboxShowUsernameHint => 'Standardbrukeren brukes normalt';

  @override
  String get fritzboxUsernameLabel => 'Brukernavn';

  @override
  String get fritzboxPasswordLabel => 'Passord';

  @override
  String get fritzboxCredentialsNote =>
      'Tilgangsdataene dine lagres sikkert på enheten din.';

  @override
  String get fritzboxTestAndSave => 'Testing og lagring';

  @override
  String get fritzboxConnectionFailed =>
      'Tilkoblingen mislyktes. Vennligst sjekk tilgangsdataene.';

  @override
  String get fritzboxFillAllFields => 'Vennligst fyll ut alle feltene.';

  @override
  String get fritzboxOfflineBanner =>
      'Fritz!-boksen er ikke tilgjengelig - vis lagrede anrop';

  @override
  String get sourceMobile => 'Mobil';

  @override
  String get sourceFritzbox => 'Fritz!Box';

  @override
  String get fritzboxStepBlocklist => 'Beskyttelse mot søppelpost';

  @override
  String get fritzboxStepBlocklistSubtitle => 'Sett opp blokkeringsliste';

  @override
  String get fritzboxBlocklistDescription =>
      'Velg hvordan Fritz!Box skal beskyttes mot spam-anrop.';

  @override
  String get fritzboxCardDavTitle => 'CardDAV-blokkliste';

  @override
  String get fritzboxCardDavDescription =>
      'Fritz!Box synkroniserer blokkeringslisten direkte med PhoneBlock. Anbefales for FRITZ!OS 7.20+.';

  @override
  String get fritzboxSkipBlocklist => 'Sette opp senere';

  @override
  String get fritzboxSkipBlocklistDescription =>
      'Du kan aktivere spam-beskyttelse senere i innstillingene.';

  @override
  String get fritzboxVersionTooOldForCardDav =>
      'CardDAV krever FRITZ!OS 7.20 eller nyere. Fritz!Boxen din har en eldre versjon.';

  @override
  String get fritzboxFinishSetup => 'Fullfør oppsettet';

  @override
  String get fritzboxPhoneBlockNotLoggedIn =>
      'Vennligst logg inn på PhoneBlock først.';

  @override
  String get fritzboxCannotGetUsername =>
      'PhoneBlock-brukernavnet kunne ikke hentes.';

  @override
  String get fritzboxBlocklistConfigFailed =>
      'Blokkliste kunne ikke settes opp.';

  @override
  String get fritzboxCardDavStatus => 'CardDAV-status';

  @override
  String get fritzboxCardDavStatusSynced => 'Synkronisert';

  @override
  String get fritzboxCardDavStatusPending => 'Synkronisering avventes';

  @override
  String get fritzboxCardDavStatusError => 'Synkroniseringsfeil';

  @override
  String get fritzboxCardDavStatusDisabled => 'Deaktivert';

  @override
  String get fritzboxCardDavNote =>
      'Fritz!Box synkroniserer telefonkatalogen én gang i døgnet ved midnatt.';

  @override
  String get fritzboxBlocklistMode => 'Modus for beskyttelse mot spam';

  @override
  String get fritzboxBlocklistModeCardDav =>
      'CardDAV (automatisk synkronisering)';

  @override
  String get fritzboxBlocklistModeNone => 'Ikke aktivert';

  @override
  String get fritzboxEnableCardDav => 'Aktiver CardDAV';

  @override
  String get fritzboxEnableCardDavDescription =>
      'Synkroniser spamblokklisten direkte med Fritz!Box';

  @override
  String get fritzboxCardDavEnabled => 'CardDAV-blokkliste aktivert';

  @override
  String get fritzboxDisableCardDav => 'Deaktiver CardDAV';

  @override
  String get fritzboxDisableCardDavTitle => 'Deaktivere CardDAV?';

  @override
  String get fritzboxDisableCardDavMessage =>
      'CardDAV-blokklisten er fjernet fra Fritz!';

  @override
  String get fritzboxDisable => 'Deaktiver';

  @override
  String get fritzboxCardDavDisabled => 'CardDAV-blokkliste deaktivert';

  @override
  String get fritzboxAnswerbotTitle => 'Telefonsvarer';

  @override
  String get fritzboxAnswerbotActive => 'Telefonsvarer aktiv';

  @override
  String get fritzboxAnswerbotDescription =>
      'SPAM-anrop besvares automatisk av PhoneBlock-telefonsvareren.';

  @override
  String get fritzboxEnableAnswerbot => 'Aktiver telefonsvareren';

  @override
  String get fritzboxEnableAnswerbotDescription =>
      'Få SPAM-anrop automatisk besvart av PhoneBlock-telefonsvareren';

  @override
  String get fritzboxDisableAnswerbot => 'Deaktiver telefonsvareren';

  @override
  String get fritzboxDisableAnswerbotTitle => 'Deaktivere telefonsvareren?';

  @override
  String get fritzboxDisableAnswerbotMessage =>
      'PhoneBlock-telefonsvareren fjernes fra Fritz!Box og deaktiveres på serveren.';

  @override
  String get fritzboxAnswerbotEnabled => 'Telefonsvareren er aktivert';

  @override
  String get fritzboxAnswerbotDisabled => 'Telefonsvareren er deaktivert';

  @override
  String get fritzboxAnswerbotSetupFailed =>
      'Telefonsvareren kunne ikke settes opp.';

  @override
  String get fritzboxAnswerbotStepCreating => 'Opprett telefonsvarer...';

  @override
  String get fritzboxAnswerbotStepDetecting => 'Sjekk ekstern tilgang...';

  @override
  String get fritzboxAnswerbotStepDynDns => 'Konfigurere DynDNS ...';

  @override
  String get fritzboxAnswerbotStepWaitingDynDns =>
      'Venter på DynDNS-registrering...';

  @override
  String get fritzboxAnswerbotStepSip => 'Registrer SIP-enhet...';

  @override
  String get fritzboxAnswerbotStepEnabling => 'Aktiver telefonsvareren...';

  @override
  String get fritzboxAnswerbotStepWaiting => 'Venter på registrering...';

  @override
  String get fritzboxAnswerbotSetupTitle => 'Sette opp en telefonsvarer';

  @override
  String get fritzboxAnswerbotSetupSuccess =>
      'Telefonsvareren er nå satt opp og er aktiv.';

  @override
  String fritzboxAnswerbotSetupErrorDetail(String error) {
    return 'Feil: $error';
  }
}
