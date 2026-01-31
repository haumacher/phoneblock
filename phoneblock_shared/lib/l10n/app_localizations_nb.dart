// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Norwegian Bokmål (`nb`).
class AppLocalizationsNb extends AppLocalizations {
  AppLocalizationsNb([String locale = 'nb']) : super(locale);

  @override
  String get appTitle => 'PhoneBlock telefonsvarer';

  @override
  String get yourAnswerbots => 'Telefonsvareren din';

  @override
  String get loginRequired => 'Registrering kreves';

  @override
  String get login => 'Logg inn';

  @override
  String get loadingData => 'Laster inn data...';

  @override
  String get refreshingData => 'Oppdater data...';

  @override
  String get noAnswerbotsYet =>
      'Hvis du ikke har en telefonsvarer ennå, kan du klikke på plussknappen nedenfor for å opprette en PhoneBlock-telefonsvarer.';

  @override
  String get createAnswerbot => 'Opprett telefonsvarer';

  @override
  String answerbotName(String userName) {
    return 'Telefonsvarer $userName';
  }

  @override
  String answerbotStats(int newCalls, int talkTimeSeconds, int callsAccepted) {
    return '$newCalls nye anrop, $callsAccepted anrop, $talkTimeSeconds s total taletid';
  }

  @override
  String get statusActive => 'aktiv';

  @override
  String get statusConnecting => '...koble til...';

  @override
  String get statusDisabled => 'slått av';

  @override
  String get statusIncomplete => 'ufullstendig';

  @override
  String get deleteAnswerbot => 'Slett telefonsvareren';

  @override
  String get enabled => 'Aktivert';

  @override
  String get minVotes => 'Minimum antall stemmer';

  @override
  String get minVotesDescription =>
      'Hva er det minste antallet stemmer som kreves for at et nummer skal aksepteres av telefonsvareren?';

  @override
  String get minVotes2 => '2 - lås umiddelbart';

  @override
  String get minVotes4 => '4 - Vent på bekreftelse';

  @override
  String get minVotes10 => '10 - bare når det er trygt';

  @override
  String get minVotes100 => '100 - bare de beste spammerne';

  @override
  String get cannotChangeWhileEnabled =>
      'Kan bare endres når telefonsvareren er slått av.';

  @override
  String get saveSettings => 'Lagre innstillinger';

  @override
  String get retentionPeriod => 'Lagringstid';

  @override
  String get retentionPeriodDescription =>
      'Hvor lenge skal samtalene oppbevares?';

  @override
  String get retentionNever => 'Aldri slette';

  @override
  String get retentionWeek => 'Slett etter 1 uke';

  @override
  String get retentionMonth => 'Slett etter 1 måned';

  @override
  String get retentionQuarter => 'Slett etter 3 måneder';

  @override
  String get retentionYear => 'Slett etter 1 år';

  @override
  String get saveRetentionSettings => 'Lagre lagringsinnstillinger';

  @override
  String get showHelp => 'Vis hjelp';

  @override
  String get newAnswerbot => 'Ny telefonsvarer';

  @override
  String get usePhoneBlockDynDns => 'Bruk PhoneBlock DynDNS';

  @override
  String get dynDnsDescription =>
      'PhoneBlock må vite Internett-adressen til Fritz!-boksen for å kunne ta imot anrop.';

  @override
  String get setupPhoneBlockDynDns => 'Konfigurer PhoneBlock DynDNS';

  @override
  String get domainName => 'Domenenavn';

  @override
  String get domainNameHint =>
      'Skriv inn domenenavnet til din Fritz! Hvis Fritz!Boxen ennå ikke har et domenenavn, aktiverer du PhoneBlock-DynDNS.';

  @override
  String get checkDomainName => 'Sjekk domenenavn';

  @override
  String get setupDynDns => 'Konfigurer DynDNS';

  @override
  String get dynDnsInstructions =>
      'I Fritz!Box-innstillingene åpner du siden Internett > Delinger > DynDNS og angir følgende verdier der:';

  @override
  String get checkDynDns => 'Sjekk DynDNS';

  @override
  String get createAnswerbotTitle => 'Opprett telefonsvarer';

  @override
  String get registerAnswerbot => 'Registrer telefonsvareren';

  @override
  String get answerbotRegistered => 'Telefonsvarer registrert';

  @override
  String get close => 'Lukk';

  @override
  String get error => 'Feil';

  @override
  String cannotLoadInfo(String message, int statusCode) {
    return 'Informasjon kan ikke hentes (feil $statusCode): $message.';
  }

  @override
  String wrongContentType(String contentType) {
    return 'Informasjon kan ikke hentes (Content-Type: $contentType).';
  }

  @override
  String get callList => 'Anropsliste';

  @override
  String get clearCallList => 'Slett anropsliste';

  @override
  String get noCalls => 'Ingen samtaler ennå';

  @override
  String get answerbot => 'Telefonsvarer';

  @override
  String get answerbotSettings => 'Innstillinger for telefonsvareren';

  @override
  String get minConfidence => 'Minimum tillit';

  @override
  String get minConfidenceHelp =>
      'Hvor mange klager er nødvendig for at et nummer skal avlyttes av telefonsvareren.';

  @override
  String get blockNumberRanges => 'Blokknummerområder';

  @override
  String get blockNumberRangesHelp =>
      'Godtar anropet selv for et nummer som ennå ikke er kjent for å være SPAM, hvis det er grunn til å mistenke at nummeret tilhører en systemtilkobling som SPAM stammer fra.';

  @override
  String get preferIPv4 => 'Favoriserer IPv4-kommunikasjon';

  @override
  String get preferIPv4Help =>
      'Kommunikasjonen med telefonsvareren håndteres via IPv4, hvis den er tilgjengelig. Det ser ut til å være telefonforbindelser der det ikke er mulig å opprette en taletilkobling via IPv6, selv om en IPv6-adresse er tilgjengelig.';

  @override
  String get callRetention => 'Oppbevaring av samtaler';

  @override
  String get automaticDeletion => 'Automatisk sletting';

  @override
  String get automaticDeletionHelp =>
      'Etter hvor lang tid skal gamle samtalelogger slettes automatisk? Slett aldri deaktiverer automatisk sletting.';

  @override
  String get dnsSettings => 'DNS-innstillinger';

  @override
  String get dnsSetting => 'DNS-innstilling';

  @override
  String get phoneBlockDns => 'PhoneBlock DNS';

  @override
  String get otherProviderOrDomain => 'Annen leverandør eller domenenavn';

  @override
  String get dnsSettingHelp =>
      'Hvordan telefonsvareren finner Fritz!-boksen din på Internett.';

  @override
  String get updateUrl => 'Oppdater URL';

  @override
  String get updateUrlHelp => 'Brukernavn for DynDNS-delingen i Fritz!';

  @override
  String get domainNameHelp =>
      'Navn som Fritz! boksen din mottar på Internett.';

  @override
  String get dyndnsUsername => 'DynDNS-brukernavn';

  @override
  String get dyndnsUsernameHelp => 'Brukernavn for DynDNS-delingen i Fritz!';

  @override
  String get dyndnsPassword => 'DynDNS-passord';

  @override
  String get dyndnsPasswordHelp => 'Passordet du må bruke for DynDNS-deling.';

  @override
  String get host => 'Vert';

  @override
  String get hostHelp =>
      'Vertsnavnet som Fritz!Boxen din kan nås via fra Internett.';

  @override
  String get sipSettings => 'SIP-innstillinger';

  @override
  String get user => 'Bruker';

  @override
  String get userHelp =>
      'Brukernavnet som må settes opp i Fritz!Box for å få tilgang til telefonienheten.';

  @override
  String get password => 'Passord';

  @override
  String get passwordHelp =>
      'Passordet som må tildeles for å få tilgang til telefonienheten i Fritz!';

  @override
  String get savingSettings => 'Lagre innstillinger...';

  @override
  String get errorSavingSettings => 'Feil ved lagring av innstillingene.';

  @override
  String savingFailed(String message) {
    return 'Lagre mislyktes: $message';
  }

  @override
  String get enableAfterSavingFailed => 'Slå på igjen etter mislykket lagring';

  @override
  String get enablingAnswerbot => 'Slå på telefonsvareren...';

  @override
  String get errorEnablingAnswerbot =>
      'Feil ved innkobling av telefonsvareren.';

  @override
  String cannotEnable(String message) {
    return 'Kan ikke slå på: $message';
  }

  @override
  String get enablingFailed => 'Kunne ikke slå på telefonsvareren';

  @override
  String enablingFailedMessage(String message) {
    return 'Slå på mislyktes: $message';
  }

  @override
  String retryingMessage(String message) {
    return '$message Prøv igjen...';
  }

  @override
  String get savingRetentionSettings => 'Lagre lagringsinnstillinger...';

  @override
  String get errorSavingRetentionSettings =>
      'Feil ved lagring av lagringsinnstillingene.';

  @override
  String get automaticDeletionDisabled => 'Automatisk sletting deaktivert';

  @override
  String retentionSettingsSaved(String period) {
    return 'Lagringsinnstillinger lagret ($period)';
  }

  @override
  String get oneWeek => '1 uke';

  @override
  String get oneMonth => '1 måned';

  @override
  String get threeMonths => '3 måneder';

  @override
  String get oneYear => '1 år';

  @override
  String get never => 'Aldri';

  @override
  String deleteAnswerbotConfirm(String userName) {
    return 'Bør telefonsvareren $userName virkelig slettes?';
  }

  @override
  String get cancel => 'Avbryt';

  @override
  String get delete => 'Slett';

  @override
  String get deletionFailed => 'Slett mislyktes';

  @override
  String get answerbotCouldNotBeDeleted => 'Telefonsvareren kunne ikke slettes';

  @override
  String get spamCalls => 'SPAM-anrop';

  @override
  String get deleteCalls => 'Slett anrop';

  @override
  String get deletingCallsFailed => 'Slett mislyktes';

  @override
  String get deleteRequestFailed =>
      'Forespørselen om sletting kunne ikke behandles.';

  @override
  String cannotRetrieveCalls(String message, int statusCode) {
    return 'Anrop kan ikke hentes (feil $statusCode): $message.';
  }

  @override
  String get noNewCalls => 'Ingen nye samtaler.';

  @override
  String duration(int seconds) {
    return 'Varighet $seconds s';
  }

  @override
  String today(String time) {
    return 'I dag $time';
  }

  @override
  String yesterday(String time) {
    return 'I går $time';
  }

  @override
  String get dynDnsDescriptionLong =>
      'PhoneBlock trenger å vite Internett-adressen til Fritz!-boksen din for å kunne registrere telefonsvareren på Fritz!-boksen. Hvis du allerede har konfigurert MyFRITZ! eller en annen DynDNS-leverandør, kan du bruke dette domenenavnet. Hvis ikke, kan du bare sette opp DynDNS fra PhoneBlock og deretter aktivere denne bryteren.';

  @override
  String get setupPhoneBlockDynDnsSnackbar => 'Konfigurer PhoneBlock DynDNS.';

  @override
  String get setupFailed => 'Oppsettet mislyktes';

  @override
  String cannotSetupDynDns(String message) {
    return 'DynDNS kan ikke settes opp: $message';
  }

  @override
  String get domainname => 'Domenenavn';

  @override
  String get domainNameHintLong =>
      'Domenenavnet til Fritz!-boksen (enten MyFRITZ!-adressen eller DynDNS-domenenavnet)';

  @override
  String get inputCannotBeEmpty => 'Inndataene må ikke være tomme.';

  @override
  String get invalidDomainName => 'Ikke noe gyldig domenenavn.';

  @override
  String get domainNameTooLong => 'Domenenavnet er for langt.';

  @override
  String get domainNameHintExtended =>
      'Skriv inn domenenavnet til din Fritz! Hvis Fritz!Boxen din ennå ikke har et domenenavn, må du aktivere PhoneBlock DynDNS. Du finner domenenavnet til Fritz!Boxen din under (under Internett > Aksjer > DynDNS). Alternativt kan du også skrive inn MyFRITZ!-adressen (Internett > MyFRITZ!-konto), f.eks. z4z...l4n.myfritz.net.';

  @override
  String get checkingDomainName => 'Sjekk domenenavn.';

  @override
  String domainNameNotAccepted(String message) {
    return 'Domenenavnet ble ikke akseptert: $message';
  }

  @override
  String get dynDnsInstructionsLong =>
      'Åpne siden Internett > Delinger > DynDNS i Fritz!Box-innstillingene, og skriv inn informasjonen som er oppgitt her.';

  @override
  String get updateUrlHelp2 =>
      'URL-adressen som Fritz!-boksen din ringer opp for å gi PhoneBlock sin Internett-adresse. Skriv inn URL-adressen nøyaktig slik den står her. Ikke bytt ut verdiene i vinkelparentesene, det gjør Fritz! boksen automatisk når du kaller den opp. Det beste er å bruke kopieringsfunksjonen for å kopiere verdiene.';

  @override
  String get domainNameHelp2 =>
      'Dette domenenavnet kan ikke løses opp offentlig senere. Internett-adressen din vil bare bli delt med PhoneBlock.';

  @override
  String get username => 'Brukernavn';

  @override
  String get usernameHelp =>
      'Brukernavnet som Fritz!-boksen logger seg på PhoneBlock med for å gjøre Internett-adressen kjent.';

  @override
  String get passwordLabel => 'Passord';

  @override
  String get passwordHelp2 =>
      'Passordet som Fritz!Box logger seg på PhoneBlock med for å gjøre Internett-adressen kjent. Av sikkerhetsgrunner kan du ikke skrive inn ditt eget passord, men må bruke passordet som PhoneBlock genererer på en sikker måte.';

  @override
  String get checkingDynDns => 'Kontroller DynDNS-konfigurasjonen.';

  @override
  String get notRegistered => 'Ikke registrert';

  @override
  String fritzBoxNotRegistered(String message) {
    return 'Fritz!Boxen din er ennå ikke registrert hos PhoneBlock, DynDNS er ikke oppdatert: $message';
  }

  @override
  String get sipSetupInstructions =>
      'Konfigurer nå PhoneBlock-telefonsvareren som \"Telefon (med og uten telefonsvarer)\". For å sikre at dette fungerer, må du følge trinnene nedenfor nøyaktig:';

  @override
  String get sipSetupStep1 =>
      '1. Åpne siden Telefoni > Telefonienheter i Fritz!Box-innstillingene, og klikk på knappen \"Konfigurer ny enhet\".';

  @override
  String get sipSetupStep2 =>
      '2. Velg alternativet \"Telefon (med og uten telefonsvarer)\", og klikk på \"Neste\".';

  @override
  String get sipSetupStep3 =>
      '3. Velg alternativet \"LAN/WLAN (IP-telefon)\", gi telefonen navnet \"PhoneBlock\" og klikk på \"Neste\".';

  @override
  String get sipSetupStep4 =>
      '4. Skriv inn følgende brukernavn og passord for telefonsvareren, og klikk deretter på \"Neste\".';

  @override
  String get usernameHelp2 =>
      'Brukernavnet som PhoneBlock-telefonsvareren logger seg på Fritz!';

  @override
  String get passwordHelp3 =>
      'Passordet som PhoneBlock-telefonsvareren bruker for å logge på Fritz! PhoneBlock har generert et sikkert passord for deg.';

  @override
  String get sipSetupStep5 =>
      '5. Telefonnummeret som spørres etter nå, spiller ingen rolle, PhoneBlock-telefonsvareren ringer ikke aktivt, men tar bare imot SPAM-anrop. Telefonnummeret velges bort igjen i trinn 9. Her klikker du bare på \"Neste\".';

  @override
  String get sipSetupStep6 =>
      '6. Velg \"Godta alle anrop\", og klikk på \"Neste\". PhoneBlock-telefonsvareren tar uansett bare imot anrop hvis nummeret til den som ringer står på blokkeringslisten. Samtidig aksepterer PhoneBlock aldri anrop fra numre som finnes i den vanlige telefonboken.';

  @override
  String get sipSetupStep7 =>
      '7. Du vil se en oppsummering. Innstillingene er (nesten) fullført, klikk på \"Apply\".';

  @override
  String get sipSetupStep8 =>
      '8. Du vil nå se \"PhoneBlock\" i listen over telefonienheter. Det mangler fortsatt noen innstillinger som du først kan gjøre senere. Klikk derfor på redigeringsblyanten i linjen for PhoneBlock-telefonsvareren.';

  @override
  String get sipSetupStep9 =>
      '9. Velg det siste (tomme) alternativet i feltet \"Utgående anrop\", ettersom PhoneBlock aldri foretar utgående anrop, og telefonsvareren derfor ikke trenger et nummer for utgående anrop.';

  @override
  String get sipSetupStep10 =>
      '10. Velg fanen \"Innloggingsdata\". Bekreft svaret ved å klikke på \"Bruk\". Velg nå alternativet \"Tillat innlogging fra Internett\", slik at telefonsvareren fra PhoneBlock-skyen kan logge inn på Fritz! Du må skrive inn passordet for telefonsvareren (se ovenfor) på nytt i feltet \"Password\" før du klikker på \"Apply\". Slett først stjernene i feltet.';

  @override
  String get sipSetupStep11 =>
      '11. Det vises en melding som advarer deg om at det kan opprettes avgiftspliktige forbindelser via Internett-tilgang. Du kan trygt bekrefte dette, for det første fordi PhoneBlock aldri aktivt oppretter forbindelser, for det andre fordi PhoneBlock har opprettet et sikkert passord for deg (se ovenfor) slik at ingen andre kan koble seg til, og for det tredje fordi du deaktiverte utgående forbindelser i trinn 9. Avhengig av innstillingene på Fritz!Boxen din, kan det hende du må bekrefte innstillingen på en DECT-telefon som er koblet direkte til Fritz!';

  @override
  String get sipSetupStep12 =>
      '12. Nå er alt ferdig. Klikk på Tilbake for å gå tilbake til listen over telefonienheter. Du kan nå aktivere telefonsvareren med knappen nederst.';

  @override
  String get tryingToRegisterAnswerbot =>
      'Prøv å registrere telefonsvareren...';

  @override
  String get answerbotRegistrationFailed =>
      'Registrering av telefonsvarer mislyktes';

  @override
  String registrationFailed(String message) {
    return 'Registrering mislyktes: $message';
  }

  @override
  String get answerbotRegisteredSuccess =>
      'Telefonsvareren din har blitt registrert hos PhoneBlock. De neste som ringer inn spam, kan nå snakke med PhoneBlock. Hvis du vil teste PhoneBlock-telefonsvareren selv, kan du ringe internnummeret til \"PhoneBlock\"-telefonienheten du har satt opp. Internnummeret begynner vanligvis med \"**\".';
}
