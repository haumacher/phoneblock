// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Swedish (`sv`).
class AppLocalizationsSv extends AppLocalizations {
  AppLocalizationsSv([String locale = 'sv']) : super(locale);

  @override
  String get appTitle => 'PhoneBlock telefonsvarare';

  @override
  String get yourAnswerbots => 'Din telefonsvarare';

  @override
  String get loginRequired => 'Registrering krävs';

  @override
  String get login => 'Logga in';

  @override
  String get loadingData => 'Laddar data...';

  @override
  String get refreshingData => 'Uppdatera data...';

  @override
  String get noAnswerbotsYet =>
      'Om du inte har någon telefonsvarare ännu, klicka på plusknappen nedan för att skapa en PhoneBlock-svarare.';

  @override
  String get createAnswerbot => 'Skapa telefonsvarare';

  @override
  String answerbotName(String userName) {
    return 'Telefonsvarare $userName';
  }

  @override
  String answerbotStats(int newCalls, int talkTimeSeconds, int callsAccepted) {
    return '$newCalls nya samtal, $callsAccepted samtal, $talkTimeSeconds s total samtalstid';
  }

  @override
  String get statusActive => 'aktiv';

  @override
  String get statusConnecting => '...ansluta...';

  @override
  String get statusDisabled => 'avstängd';

  @override
  String get statusIncomplete => 'ofullständig';

  @override
  String get deleteAnswerbot => 'Radera telefonsvarare';

  @override
  String get enabled => 'Aktiverad';

  @override
  String get minVotes => 'Minimiantal röster';

  @override
  String get minVotesDescription =>
      'Vilket är det minsta antal röster som krävs för att ett nummer ska accepteras av telefonsvararen?';

  @override
  String get minVotes2 => '2 - lås omedelbart';

  @override
  String get minVotes4 => '4 - Vänta på bekräftelse';

  @override
  String get minVotes10 => '10 - endast när det är säkert';

  @override
  String get minVotes100 => '100 - endast de bästa spammarna';

  @override
  String get cannotChangeWhileEnabled =>
      'Kan endast ändras när telefonsvararen är avstängd.';

  @override
  String get saveSettings => 'Spara inställningar';

  @override
  String get retentionPeriod => 'Lagringstid';

  @override
  String get retentionPeriodDescription => 'Hur länge ska samtalen sparas?';

  @override
  String get retentionNever => 'Ta aldrig bort';

  @override
  String get retentionWeek => 'Radera efter 1 vecka';

  @override
  String get retentionMonth => 'Radera efter 1 månad';

  @override
  String get retentionQuarter => 'Radera efter 3 månader';

  @override
  String get retentionYear => 'Raderas efter 1 år';

  @override
  String get saveRetentionSettings => 'Spara lagringsinställningar';

  @override
  String get showHelp => 'Visa hjälp';

  @override
  String get newAnswerbot => 'Ny telefonsvarare';

  @override
  String get usePhoneBlockDynDns => 'Använda PhoneBlock DynDNS';

  @override
  String get dynDnsDescription =>
      'PhoneBlock behöver veta internetadressen till Fritz!-boxen för att kunna ta emot samtal.';

  @override
  String get setupPhoneBlockDynDns => 'Konfigurera PhoneBlock DynDNS';

  @override
  String get domainName => 'Domännamn';

  @override
  String get domainNameHint =>
      'Ange domännamnet för din Fritz!Box. Om din Fritz!Box ännu inte har något domännamn aktiverar du PhoneBlock-DynDNS.';

  @override
  String get checkDomainName => 'Kontrollera domännamn';

  @override
  String get setupDynDns => 'Konfigurera DynDNS';

  @override
  String get dynDnsInstructions =>
      'I Fritz!Box-inställningarna öppnar du sidan Internet > Aktier > DynDNS och anger följande värden där:';

  @override
  String get checkDynDns => 'Kontrollera DynDNS';

  @override
  String get createAnswerbotTitle => 'Skapa telefonsvarare';

  @override
  String get registerAnswerbot => 'Registrera telefonsvarare';

  @override
  String get answerbotRegistered => 'Registrerad telefonsvarare';

  @override
  String get close => 'Nära';

  @override
  String get error => 'Fel';

  @override
  String cannotLoadInfo(String message, int statusCode) {
    return 'Information kan inte hämtas (fel $statusCode): $message.';
  }

  @override
  String wrongContentType(String contentType) {
    return 'Information kan inte hämtas (Content-Type: $contentType).';
  }

  @override
  String get callList => 'Samtalslista';

  @override
  String get clearCallList => 'Radera samtalslista';

  @override
  String get noCalls => 'Inga samtal ännu';
}
