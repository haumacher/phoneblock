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
  String votes(Object count) {
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
  String minSpamReportsDescription(Object count) {
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
  String minSpamReportsInRangeDescription(Object count) {
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
  String pendingCallsNotification(num count) {
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
  String reportedAsLegitimate(Object phoneNumber) {
    return '$phoneNumber rapportert som legitimt';
  }

  @override
  String reportError(Object error) {
    return 'Feil ved rapportering: $error';
  }

  @override
  String reportedAsSpam(Object phoneNumber) {
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
  String callsBlockedAfterReports(Object count) {
    return 'Anrop er blokkert fra $count meldinger';
  }

  @override
  String rangesBlockedAfterReports(Object count) {
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
  String tokenVerificationFailed(Object error) {
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
  String ratePhoneNumber(Object phoneNumber) {
    return 'Takst $phoneNumber';
  }

  @override
  String reportsCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count meldinger',
      one: '1 Melding',
    );
    return '$_temp0';
  }

  @override
  String legitimateReportsCount(num count) {
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
  String todayTime(Object time) {
    return 'I dag, $time';
  }

  @override
  String yesterdayTime(Object time) {
    return 'I går, $time';
  }

  @override
  String get callHistoryRetention => 'Lagring av anropshistorikk';

  @override
  String retentionPeriodDescription(num days) {
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
  String retentionDays(num days) {
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
      'Fügen Sie Nummern hinzu, indem Sie unerwünschte Anrufe als Spam melden.';

  @override
  String get whitelistEmptyHelp =>
      'Fügen Sie Nummern hinzu, indem Sie blockierte Anrufe als legitim melden.';

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
}
