// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Dutch Flemish (`nl`).
class AppLocalizationsNl extends AppLocalizations {
  AppLocalizationsNl([String locale = 'nl']) : super(locale);

  @override
  String get appTitle => 'PhoneBlock antwoordapparaat';

  @override
  String get yourAnswerbots => 'Uw antwoordapparaat';

  @override
  String get loginRequired => 'Registratie vereist';

  @override
  String get login => 'Inloggen';

  @override
  String get loadingData => 'Gegevens laden...';

  @override
  String get refreshingData => 'Gegevens bijwerken...';

  @override
  String get noAnswerbotsYet =>
      'Hebt u nog geen antwoordapparaat, klik dan op de plusknop hieronder om een PhoneBlock antwoordapparaat aan te maken.';

  @override
  String get createAnswerbot => 'Antwoordapparaat maken';

  @override
  String answerbotName(String userName) {
    return 'Antwoordapparaat $userName';
  }

  @override
  String answerbotStats(int newCalls, int talkTimeSeconds, int callsAccepted) {
    return '$newCalls nieuwe oproepen, $callsAccepted oproepen, $talkTimeSeconds s totale gesprekstijd';
  }

  @override
  String get statusActive => 'actief';

  @override
  String get statusConnecting => 'verbinden...';

  @override
  String get statusDisabled => 'uitgeschakeld';

  @override
  String get statusIncomplete => 'onvolledig';

  @override
  String get deleteAnswerbot => 'Antwoordapparaat verwijderen';

  @override
  String get enabled => 'Geactiveerd';

  @override
  String get minVotes => 'Minimum aantal stemmen';

  @override
  String get minVotesDescription =>
      'Wat is het minimumaantal stemmen dat nodig is om een nummer door het antwoordapparaat te laten accepteren?';

  @override
  String get minVotes2 => '2 - onmiddellijk vergrendelen';

  @override
  String get minVotes4 => '4 - Wacht op bevestiging';

  @override
  String get minVotes10 => '10 - alleen als het veilig is';

  @override
  String get minVotes100 => '100 - alleen topspammers';

  @override
  String get cannotChangeWhileEnabled =>
      'Kan alleen worden gewijzigd als het antwoordapparaat is uitgeschakeld.';

  @override
  String get saveSettings => 'Instellingen opslaan';

  @override
  String get retentionPeriod => 'Opslagtijd';

  @override
  String get retentionPeriodDescription =>
      'Hoe lang moeten gesprekken worden bewaard?';

  @override
  String get retentionNever => 'Nooit verwijderen';

  @override
  String get retentionWeek => 'Verwijderen na 1 week';

  @override
  String get retentionMonth => 'Verwijderen na 1 maand';

  @override
  String get retentionQuarter => 'Verwijderen na 3 maanden';

  @override
  String get retentionYear => 'Verwijderen na 1 jaar';

  @override
  String get saveRetentionSettings => 'Opslaginstellingen opslaan';

  @override
  String get showHelp => 'Hulp tonen';

  @override
  String get newAnswerbot => 'Nieuw antwoordapparaat';

  @override
  String get usePhoneBlockDynDns => 'Gebruik PhoneBlock DynDNS';

  @override
  String get dynDnsDescription =>
      'PhoneBlock moet het internetadres van uw Fritz! box kennen om oproepen te kunnen accepteren.';

  @override
  String get setupPhoneBlockDynDns => 'PhoneBlock DynDNS instellen';

  @override
  String get domainName => 'Domeinnaam';

  @override
  String get domainNameHint =>
      'Voer de domeinnaam van uw Fritz! Als uw Fritz!Box nog geen domeinnaam heeft, activeer dan PhoneBlock-DynDNS.';

  @override
  String get checkDomainName => 'Domeinnamen controleren';

  @override
  String get setupDynDns => 'DynDNS instellen';

  @override
  String get dynDnsInstructions =>
      'Open in je Fritz!Box instellingen de pagina Internet > Shares > DynDNS en voer daar de volgende waarden in:';

  @override
  String get checkDynDns => 'Controleer DynDNS';

  @override
  String get createAnswerbotTitle => 'Antwoordapparaat maken';

  @override
  String get registerAnswerbot => 'Antwoordapparaat registreren';

  @override
  String get answerbotRegistered => 'Antwoordapparaat geregistreerd';

  @override
  String get close => 'Sluit';

  @override
  String get error => 'Fout';

  @override
  String cannotLoadInfo(String message, int statusCode) {
    return 'Informatie kan niet worden opgehaald (fout $statusCode): $message';
  }

  @override
  String wrongContentType(String contentType) {
    return 'Informatie kan niet worden opgehaald (Content-Type: $contentType).';
  }

  @override
  String get callList => 'Bellijst';

  @override
  String get clearCallList => 'Bellijst verwijderen';

  @override
  String get noCalls => 'Nog geen oproepen';
}
