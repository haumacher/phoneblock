// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Danish (`da`).
class AppLocalizationsDa extends AppLocalizations {
  AppLocalizationsDa([String locale = 'da']) : super(locale);

  @override
  String get appTitle => 'PhoneBlock telefonsvarer';

  @override
  String get yourAnswerbots => 'Din telefonsvarer';

  @override
  String get loginRequired => 'Tilmelding påkrævet';

  @override
  String get login => 'Login';

  @override
  String get loadingData => 'Indlæser data...';

  @override
  String get refreshingData => 'Opdater data...';

  @override
  String get noAnswerbotsYet =>
      'Hvis du ikke har en telefonsvarer endnu, kan du klikke på plus-knappen nedenfor for at oprette en PhoneBlock-telefonsvarer.';

  @override
  String get createAnswerbot => 'Opret en telefonsvarer';

  @override
  String answerbotName(String userName) {
    return 'Telefonsvarer $userName.';
  }

  @override
  String answerbotStats(int newCalls, int talkTimeSeconds, int callsAccepted) {
    return '$newCalls nye opkald, $callsAccepted opkald, $talkTimeSeconds s samlet taletid';
  }

  @override
  String get statusActive => 'aktiv';

  @override
  String get statusConnecting => '...forbinder...';

  @override
  String get statusDisabled => 'slukket';

  @override
  String get statusIncomplete => 'ufuldstændig';

  @override
  String get deleteAnswerbot => 'Slet telefonsvareren';

  @override
  String get enabled => 'Aktiveret';

  @override
  String get minVotes => 'Minimum antal stemmer';

  @override
  String get minVotesDescription =>
      'Hvad er det mindste antal stemmer, der kræves, for at et nummer kan accepteres af telefonsvareren?';

  @override
  String get minVotes2 => '2 - lås med det samme';

  @override
  String get minVotes4 => '4 - Vent på bekræftelse';

  @override
  String get minVotes10 => '10 - kun når det er sikkert';

  @override
  String get minVotes100 => '100 - kun topspammere';

  @override
  String get cannotChangeWhileEnabled =>
      'Kan kun ændres, når telefonsvareren er slukket.';

  @override
  String get saveSettings => 'Gem indstillinger';

  @override
  String get retentionPeriod => 'Opbevaringstid';

  @override
  String get retentionPeriodDescription => 'Hvor længe skal opkald gemmes?';

  @override
  String get retentionNever => 'Slet aldrig';

  @override
  String get retentionWeek => 'Slet efter 1 uge';

  @override
  String get retentionMonth => 'Slet efter 1 måned';

  @override
  String get retentionQuarter => 'Slet efter 3 måneder';

  @override
  String get retentionYear => 'Slet efter 1 år';

  @override
  String get saveRetentionSettings => 'Gem lagringsindstillinger';

  @override
  String get showHelp => 'Vis hjælp';

  @override
  String get newAnswerbot => 'Ny telefonsvarer';

  @override
  String get usePhoneBlockDynDns => 'Brug PhoneBlock DynDNS';

  @override
  String get dynDnsDescription =>
      'PhoneBlock skal kende internetadressen på din Fritz! boks for at kunne modtage opkald.';

  @override
  String get setupPhoneBlockDynDns => 'Opsætning af PhoneBlock DynDNS';

  @override
  String get domainName => 'Domænenavn';

  @override
  String get domainNameHint =>
      'Indtast domænenavnet på din Fritz! Hvis din Fritz!Box endnu ikke har et domænenavn, skal du aktivere PhoneBlock-DynDNS.';

  @override
  String get checkDomainName => 'Tjek domænenavne';

  @override
  String get setupDynDns => 'Sæt DynDNS op';

  @override
  String get dynDnsInstructions =>
      'I dine Fritz!Box-indstillinger skal du åbne siden Internet > Shares > DynDNS og indtaste følgende værdier der:';

  @override
  String get checkDynDns => 'Tjek DynDNS';

  @override
  String get createAnswerbotTitle => 'Opret en telefonsvarer';

  @override
  String get registerAnswerbot => 'Registrer telefonsvareren';

  @override
  String get answerbotRegistered => 'Telefonsvarer registreret';

  @override
  String get close => 'Luk';

  @override
  String get error => 'Fejl';

  @override
  String cannotLoadInfo(String message, int statusCode) {
    return 'Oplysningerne kan ikke hentes (fejl $statusCode): $message.';
  }

  @override
  String wrongContentType(String contentType) {
    return 'Oplysningerne kan ikke hentes (Content-Type: $contentType).';
  }

  @override
  String get callList => 'Opkaldsliste';

  @override
  String get clearCallList => 'Slet opkaldsliste';

  @override
  String get noCalls => 'Ingen opkald endnu';

  @override
  String get answerbot => 'Telefonsvarer';

  @override
  String get answerbotSettings => 'Indstillinger for telefonsvarer';

  @override
  String get minConfidence => 'Minimum tillid';

  @override
  String get minConfidenceHelp =>
      'Hvor mange klager skal der til, for at et nummer bliver aflyttet af telefonsvareren?';

  @override
  String get blockNumberRanges => 'Bloknummer-intervaller';

  @override
  String get blockNumberRangesHelp =>
      'Accepterer opkaldet selv for et nummer, der endnu ikke er kendt for at være SPAM, hvis der er grund til at mistænke, at nummeret tilhører en systemforbindelse, hvorfra SPAM stammer.';

  @override
  String get preferIPv4 => 'Begunstig IPv4-kommunikation';

  @override
  String get preferIPv4Help =>
      'Hvis det er muligt, håndteres kommunikationen med telefonsvareren via IPv4. Der ser ud til at være telefonforbindelser, hvor en taleforbindelse via IPv6 ikke er mulig, selv om en IPv6-adresse er tilgængelig.';

  @override
  String get callRetention => 'Fastholdelse af opkald';

  @override
  String get automaticDeletion => 'Automatisk sletning';

  @override
  String get automaticDeletionHelp =>
      'Efter hvor lang tid skal gamle opkaldslister slettes automatisk? Slet aldrig deaktiverer automatisk sletning.';

  @override
  String get dnsSettings => 'DNS-indstillinger';

  @override
  String get dnsSetting => 'DNS-indstilling';

  @override
  String get phoneBlockDns => 'PhoneBlock DNS';

  @override
  String get otherProviderOrDomain => 'Anden udbyder eller domænenavn';

  @override
  String get dnsSettingHelp =>
      'Hvordan telefonsvareren finder din Fritz! boks på internettet.';

  @override
  String get updateUrl => 'Opdater URL';

  @override
  String get updateUrlHelp => 'Brugernavn til DynDNS-delen i din Fritz!';

  @override
  String get domainNameHelp =>
      'Navn, som din Fritz! boks modtager på internettet.';

  @override
  String get dyndnsUsername => 'DynDNS-brugernavn';

  @override
  String get dyndnsUsernameHelp => 'Brugernavn til DynDNS-delen i din Fritz!';

  @override
  String get dyndnsPassword => 'DynDNS-adgangskode';

  @override
  String get dyndnsPasswordHelp =>
      'Den adgangskode, du skal bruge til DynDNS-deling.';

  @override
  String get host => 'Vært';

  @override
  String get hostHelp =>
      'Det værtsnavn, som din Fritz!Box kan nås via fra internettet.';

  @override
  String get sipSettings => 'SIP-indstillinger';

  @override
  String get user => 'Bruger';

  @override
  String get userHelp =>
      'Det brugernavn, der skal oprettes i Fritz!Box for at få adgang til telefonienheden.';

  @override
  String get password => 'Adgangskode';

  @override
  String get passwordHelp =>
      'Den adgangskode, der skal tildeles for at få adgang til telefonienheden i Fritz!';

  @override
  String get savingSettings => 'Gemme indstillinger...';

  @override
  String get errorSavingSettings => 'Fejl ved lagring af indstillingerne.';

  @override
  String savingFailed(String message) {
    return 'Gem mislykkedes: $message.';
  }

  @override
  String get enableAfterSavingFailed => 'Tænd igen efter mislykket lagring';

  @override
  String get enablingAnswerbot => 'Tænd for telefonsvareren...';

  @override
  String get errorEnablingAnswerbot => 'Fejl ved tænding af telefonsvareren.';

  @override
  String cannotEnable(String message) {
    return 'Kan ikke tænde: $message.';
  }

  @override
  String get enablingFailed => 'Kunne ikke tænde for telefonsvareren';

  @override
  String enablingFailedMessage(String message) {
    return 'Tændingen mislykkedes: $message.';
  }

  @override
  String retryingMessage(String message) {
    return '$message Prøv igen...';
  }

  @override
  String get savingRetentionSettings => 'Gemme lagringsindstillinger...';

  @override
  String get errorSavingRetentionSettings =>
      'Fejl ved lagring af lagringsindstillingerne.';

  @override
  String get automaticDeletionDisabled => 'Automatisk sletning deaktiveret';

  @override
  String retentionSettingsSaved(String period) {
    return 'Gemte lagringsindstillinger ($period)';
  }

  @override
  String get oneWeek => '1 uge';

  @override
  String get oneMonth => '1 måned';

  @override
  String get threeMonths => '3 måneder';

  @override
  String get oneYear => '1 år';

  @override
  String get never => 'Aldrig';

  @override
  String deleteAnswerbotConfirm(String userName) {
    return 'Skal telefonsvareren $userName virkelig slettes?';
  }

  @override
  String get cancel => 'Annuller';

  @override
  String get delete => 'Sletning';

  @override
  String get deletionFailed => 'Sletning mislykkedes';

  @override
  String get answerbotCouldNotBeDeleted => 'Telefonsvareren kunne ikke slettes';

  @override
  String get spamCalls => 'SPAM-opkald';

  @override
  String get deleteCalls => 'Slet opkald';

  @override
  String get deletingCallsFailed => 'Sletning mislykkedes';

  @override
  String get deleteRequestFailed =>
      'Anmodningen om sletning kunne ikke behandles.';

  @override
  String cannotRetrieveCalls(String message, int statusCode) {
    return 'Opkald kan ikke hentes (fejl $statusCode): $message.';
  }

  @override
  String get noNewCalls => 'Ingen nye opkald.';

  @override
  String duration(int seconds) {
    return 'Varighed $seconds s';
  }

  @override
  String today(String time) {
    return 'I dag $time.';
  }

  @override
  String yesterday(String time) {
    return 'I går $time';
  }

  @override
  String get dynDnsDescriptionLong =>
      'PhoneBlock skal kende internetadressen på din Fritz!-boks for at kunne registrere telefonsvareren på din Fritz!-boks. Hvis du allerede har opsat MyFRITZ! eller en anden DynDNS-udbyder, kan du bruge dette domænenavn. Hvis ikke, kan du blot opsætte DynDNS fra PhoneBlock og derefter aktivere denne switch.';

  @override
  String get setupPhoneBlockDynDnsSnackbar => 'Sæt PhoneBlock DynDNS op.';

  @override
  String get setupFailed => 'Opsætning mislykkedes';

  @override
  String cannotSetupDynDns(String message) {
    return 'DynDNS kan ikke sættes op: $message.';
  }

  @override
  String get domainname => 'Domænenavn';

  @override
  String get domainNameHintLong =>
      'Domænenavn på din Fritz! boks (enten MyFRITZ! adresse eller DynDNS domænenavn)';

  @override
  String get inputCannotBeEmpty => 'Input må ikke være tomt.';

  @override
  String get invalidDomainName => 'Intet gyldigt domænenavn.';

  @override
  String get domainNameTooLong => 'Domænenavnet er for langt.';

  @override
  String get domainNameHintExtended =>
      'Indtast domænenavnet på din Fritz! Hvis din Fritz!Box endnu ikke har et domænenavn, skal du aktivere PhoneBlock DynDNS. Du kan finde domænenavnet på din Fritz!Box under (under Internet > Aktier > DynDNS). Alternativt kan du også indtaste MyFRITZ!-adressen (Internet > MyFRITZ!-konto), f.eks. z4z...l4n.myfritz.net.';

  @override
  String get checkingDomainName => 'Tjek domænenavne.';

  @override
  String domainNameNotAccepted(String message) {
    return 'Domænenavnet blev ikke accepteret: $message.';
  }

  @override
  String get dynDnsInstructionsLong =>
      'Åbn siden Internet > Shares > DynDNS i dine Fritz!Box-indstillinger, og indtast de oplysninger, der er angivet her.';

  @override
  String get updateUrlHelp2 =>
      'Den URL, som din Fritz! boks kalder op for at give PhoneBlock sin internetadresse. Indtast URL\'en præcis som den står her. Du skal ikke erstatte værdierne i vinkelparenteserne, det gør din Fritz! boks automatisk, når du kalder den. Det er bedst at bruge kopieringsfunktionen til at kopiere værdierne.';

  @override
  String get domainNameHelp2 =>
      'Dette domænenavn kan ikke løses offentligt senere. Din internetadresse vil kun blive delt med PhoneBlock.';

  @override
  String get username => 'Brugernavn';

  @override
  String get usernameHelp =>
      'Det brugernavn, som din Fritz!-boks logger ind på PhoneBlock med for at gøre sin internetadresse kendt.';

  @override
  String get passwordLabel => 'Adgangskode';

  @override
  String get passwordHelp2 =>
      'Den adgangskode, hvormed din Fritz!Box logger ind på PhoneBlock for at gøre sin internetadresse kendt. Af sikkerhedsmæssige årsager kan du ikke indtaste din egen adgangskode, men skal bruge den adgangskode, der genereres sikkert af PhoneBlock.';

  @override
  String get checkingDynDns => 'Tjek DynDNS-opsætningen.';

  @override
  String get notRegistered => 'Ikke registreret';

  @override
  String fritzBoxNotRegistered(String message) {
    return 'Din Fritz!Box er endnu ikke registreret hos PhoneBlock, DynDNS er ikke opdateret: $message.';
  }

  @override
  String get sipSetupInstructions =>
      'Konfigurer nu PhoneBlock-telefonsvareren som \"Telefon (med og uden telefonsvarer)\". For at sikre, at dette fungerer, skal du følge nedenstående trin nøjagtigt:';

  @override
  String get sipSetupStep1 =>
      '1. Åbn siden Telefoni > Telefonienheder i dine Fritz!Box-indstillinger, og klik på knappen \"Opsæt ny enhed\".';

  @override
  String get sipSetupStep2 =>
      '2. Vælg indstillingen \"Telefon (med og uden telefonsvarer)\", og klik på \"Næste\".';

  @override
  String get sipSetupStep3 =>
      '3. Vælg indstillingen \"LAN/WLAN (IP-telefon)\", giv telefonen navnet \"PhoneBlock\", og klik på \"Næste\".';

  @override
  String get sipSetupStep4 =>
      '4. Indtast nu følgende brugernavn og adgangskode til din telefonsvarer, og klik derefter på \"Næste\".';

  @override
  String get usernameHelp2 =>
      'Det brugernavn, som PhoneBlock-telefonsvareren bruger til at logge på din Fritz!';

  @override
  String get passwordHelp3 =>
      'Den adgangskode, som PhoneBlock-telefonsvareren bruger til at logge på din Fritz! PhoneBlock har genereret en sikker adgangskode til dig.';

  @override
  String get sipSetupStep5 =>
      '5. Det telefonnummer, der spørges efter nu, er ligegyldigt, PhoneBlock-telefonsvareren foretager ikke aktivt nogen opkald, men accepterer kun SPAM-opkald. Telefonnummeret fravælges igen i trin 9. Klik blot på \"Næste\" her.';

  @override
  String get sipSetupStep6 =>
      '6. Vælg \"Accepter alle opkald\", og klik på \"Næste\". PhoneBlock-telefonsvareren accepterer alligevel kun opkald, hvis opkaldsnummeret er på blokeringslisten. Samtidig accepterer PhoneBlock aldrig opkald fra numre, der står i din normale telefonbog.';

  @override
  String get sipSetupStep7 =>
      '7. Du vil se en oversigt. Indstillingerne er (næsten) færdige, klik på \"Anvend\".';

  @override
  String get sipSetupStep8 =>
      '8. Du vil nu se \"PhoneBlock\" på listen over telefonienheder. Der mangler stadig et par indstillinger, som du først kan foretage senere. Klik derfor på redigeringsblyanten i linjen for PhoneBlock-telefonsvareren.';

  @override
  String get sipSetupStep9 =>
      '9. Vælg den sidste (tomme) mulighed i feltet \"Udgående opkald\", da PhoneBlock aldrig foretager udgående opkald, og telefonsvareren derfor ikke har brug for et nummer til udgående opkald.';

  @override
  String get sipSetupStep10 =>
      '10. Vælg fanen \"Login data\". Bekræft svaret ved at klikke på \"Anvend\". Vælg nu indstillingen \"Tillad login fra internettet\", så PhoneBlock-telefonsvareren fra PhoneBlock-skyen kan logge ind på din Fritz! Du skal indtaste telefonsvarerens adgangskode (se ovenfor) igen i feltet \"Password\", før du klikker på \"Apply\". Slet først stjernerne i feltet.';

  @override
  String get sipSetupStep11 =>
      '11. Der vises en meddelelse, som advarer dig om, at der kan oprettes betalbare forbindelser via internetadgang. Du kan trygt bekræfte dette, for det første fordi PhoneBlock aldrig aktivt opretter forbindelser, for det andet fordi PhoneBlock har oprettet en sikker adgangskode til dig (se ovenfor), så ingen andre kan oprette forbindelse, og for det tredje fordi du deaktiverede de udgående forbindelser i trin 9. Afhængigt af indstillingerne på din Fritz!Box kan det være nødvendigt at bekræfte indstillingen på en DECT-telefon, der er tilsluttet direkte til Fritz!';

  @override
  String get sipSetupStep12 =>
      '12. Nu er alting klaret. Klik på Tilbage for at vende tilbage til listen over telefonienheder. Du kan nu aktivere din telefonsvarer med knappen nederst.';

  @override
  String get tryingToRegisterAnswerbot =>
      'Prøv at registrere telefonsvareren...';

  @override
  String get answerbotRegistrationFailed =>
      'Registrering af telefonsvarer mislykkedes';

  @override
  String registrationFailed(String message) {
    return 'Registrering mislykkedes: $message.';
  }

  @override
  String get answerbotRegisteredSuccess =>
      'Din PhoneBlock-telefonsvarer er blevet registreret. De næste spamopkald kan nu tale med PhoneBlock. Hvis du selv vil teste PhoneBlock-telefonsvareren, skal du ringe til det interne nummer på den \"PhoneBlock\"-telefonienhed, du har sat op. Det interne nummer begynder normalt med \"**\".';
}
