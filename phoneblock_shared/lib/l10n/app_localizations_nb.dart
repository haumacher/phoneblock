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
}
