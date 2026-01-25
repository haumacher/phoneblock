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
}
