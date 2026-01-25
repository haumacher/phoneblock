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

  @override
  String get answerbot => 'Antwoordapparaat';

  @override
  String get answerbotSettings => 'Instellingen antwoordapparaat';

  @override
  String get minConfidence => 'Minimaal vertrouwen';

  @override
  String get minConfidenceHelp =>
      'Hoeveel klachten zijn er nodig om een nummer te laten onderscheppen door het antwoordapparaat.';

  @override
  String get blockNumberRanges => 'Bereiken bloknummers';

  @override
  String get blockNumberRangesHelp =>
      'Accepteert de oproep zelfs voor een nummer waarvan nog niet bekend is dat het SPAM is als er reden is om te vermoeden dat het nummer behoort tot een systeemverbinding waarvan SPAM afkomstig is.';

  @override
  String get preferIPv4 => 'Gunstige IPv4-communicatie';

  @override
  String get preferIPv4Help =>
      'Indien beschikbaar, wordt de communicatie met het antwoordapparaat via IPv4 afgehandeld. Er blijken telefoonaansluitingen te zijn waarvoor een spraakverbinding via IPv6 niet mogelijk is, hoewel er een IPv6-adres beschikbaar is.';

  @override
  String get callRetention => 'Behoud van gesprekken';

  @override
  String get automaticDeletion => 'Automatisch verwijderen';

  @override
  String get automaticDeletionHelp =>
      'Na welke tijd moeten oude gesprekslogs automatisch worden verwijderd? Nooit wissen schakelt automatisch wissen uit.';

  @override
  String get dnsSettings => 'DNS-instellingen';

  @override
  String get dnsSetting => 'DNS-instelling';

  @override
  String get phoneBlockDns => 'PhoneBlock DNS';

  @override
  String get otherProviderOrDomain => 'Andere provider of domeinnaam';

  @override
  String get dnsSettingHelp =>
      'Hoe het antwoordapparaat jouw Fritz! box vindt op het internet.';

  @override
  String get updateUrl => 'URL bijwerken';

  @override
  String get updateUrlHelp =>
      'Gebruikersnaam voor de DynDNS-share in uw Fritz!';

  @override
  String get domainNameHelp =>
      'Naam die je Fritz! box ontvangt op het internet.';

  @override
  String get dyndnsUsername => 'DynDNS-gebruikersnaam';

  @override
  String get dyndnsUsernameHelp =>
      'Gebruikersnaam voor de DynDNS-share in uw Fritz!';

  @override
  String get dyndnsPassword => 'DynDNS-wachtwoord';

  @override
  String get dyndnsPasswordHelp =>
      'Het wachtwoord dat u moet gebruiken voor het delen van DynDNS.';

  @override
  String get host => 'Gastheer';

  @override
  String get hostHelp =>
      'De hostnaam via welke je Fritz!Box bereikbaar is vanaf het internet.';

  @override
  String get sipSettings => 'SIP-instellingen';

  @override
  String get user => 'Gebruiker';

  @override
  String get userHelp =>
      'De gebruikersnaam die moet worden ingesteld in de Fritz!Box om toegang te krijgen tot het telefoonapparaat.';

  @override
  String get password => 'Wachtwoord';

  @override
  String get passwordHelp =>
      'Het wachtwoord dat moet worden toegewezen voor toegang tot het telefoontoestel in de Fritz!';

  @override
  String get savingSettings => 'Instellingen opslaan...';

  @override
  String get errorSavingSettings => 'Fout bij het opslaan van de instellingen.';

  @override
  String savingFailed(String message) {
    return 'Opslaan mislukt: $message';
  }

  @override
  String get enableAfterSavingFailed =>
      'Opnieuw inschakelen na opslaan mislukt';

  @override
  String get enablingAnswerbot => 'Antwoordapparaat inschakelen...';

  @override
  String get errorEnablingAnswerbot =>
      'Fout bij het inschakelen van het antwoordapparaat.';

  @override
  String cannotEnable(String message) {
    return 'Kan niet inschakelen: $message';
  }

  @override
  String get enablingFailed => 'Het antwoordapparaat is niet ingeschakeld';

  @override
  String enablingFailedMessage(String message) {
    return 'Inschakelen mislukt: $message';
  }

  @override
  String retryingMessage(String message) {
    return '$message Probeer het opnieuw...';
  }

  @override
  String get savingRetentionSettings => 'Opslaginstellingen opslaan...';

  @override
  String get errorSavingRetentionSettings =>
      'Fout bij het opslaan van de opslaginstellingen.';

  @override
  String get automaticDeletionDisabled =>
      'Automatisch verwijderen gedeactiveerd';

  @override
  String retentionSettingsSaved(String period) {
    return 'Opslaginstellingen opgeslagen ($period)';
  }

  @override
  String get oneWeek => '1 week';

  @override
  String get oneMonth => '1 maand';

  @override
  String get threeMonths => '3 maanden';

  @override
  String get oneYear => '1 jaar';

  @override
  String get never => 'Nooit';

  @override
  String deleteAnswerbotConfirm(String userName) {
    return 'Moet het antwoordapparaat $userName echt worden verwijderd?';
  }

  @override
  String get cancel => 'Annuleren';

  @override
  String get delete => 'Verwijder';

  @override
  String get deletionFailed => 'Verwijderen mislukt';

  @override
  String get answerbotCouldNotBeDeleted =>
      'Het antwoordapparaat kon niet worden verwijderd';

  @override
  String get spamCalls => 'SPAM-oproepen';

  @override
  String get deleteCalls => 'Oproepen verwijderen';

  @override
  String get deletingCallsFailed => 'Verwijderen mislukt';

  @override
  String get deleteRequestFailed =>
      'Het verwijderingsverzoek kon niet worden verwerkt.';

  @override
  String cannotRetrieveCalls(String message, int statusCode) {
    return 'Oproepen kunnen niet worden opgehaald (fout $statusCode): $message';
  }

  @override
  String get noNewCalls => 'Geen nieuwe oproepen.';

  @override
  String duration(int seconds) {
    return 'Duur $seconds s';
  }

  @override
  String today(String time) {
    return 'Vandaag $time';
  }

  @override
  String yesterday(String time) {
    return 'Gisteren $time';
  }

  @override
  String get dynDnsDescriptionLong =>
      'PhoneBlock moet het internetadres van uw Fritz! box kennen om het antwoordapparaat op uw Fritz! box te kunnen registreren. Als u MyFRITZ! of een andere DynDNS-provider al hebt ingesteld, kunt u deze domeinnaam gebruiken. Zo niet, dan kunt u DynDNS gewoon instellen vanuit PhoneBlock en vervolgens deze schakelaar activeren.';

  @override
  String get setupPhoneBlockDynDnsSnackbar => 'PhoneBlock DynDNS instellen.';

  @override
  String get setupFailed => 'Setup mislukt';

  @override
  String cannotSetupDynDns(String message) {
    return 'DynDNS kan niet worden ingesteld: $message';
  }

  @override
  String get domainname => 'Domeinnaam';

  @override
  String get domainNameHintLong =>
      'Domeinnaam van uw Fritz! box (ofwel MyFRITZ! adres of DynDNS domeinnaam)';

  @override
  String get inputCannotBeEmpty => 'De invoer mag niet leeg zijn.';

  @override
  String get invalidDomainName => 'Geen geldige domeinnaam.';

  @override
  String get domainNameTooLong => 'De domeinnaam is te lang.';

  @override
  String get domainNameHintExtended =>
      'Voer de domeinnaam van uw Fritz! Als uw Fritz!Box nog geen domeinnaam heeft, activeer dan PhoneBlock DynDNS. U kunt de domeinnaam van uw Fritz!Box vinden onder (Internet > Aandelen > DynDNS). Als alternatief kunt u ook het MyFRITZ! adres invoeren (Internet > MyFRITZ! account), bijv. z4z...l4n.myfritz.net.';

  @override
  String get checkingDomainName => 'Controleer domeinnamen.';

  @override
  String domainNameNotAccepted(String message) {
    return 'Domeinnaam werd niet geaccepteerd: $message';
  }

  @override
  String get dynDnsInstructionsLong =>
      'Open de pagina Internet > Shares > DynDNS in je Fritz!Box instellingen en voer de hier gegeven informatie in.';

  @override
  String get updateUrlHelp2 =>
      'De URL die uw Fritz! box oproept om PhoneBlock zijn internetadres te geven. Voer de URL precies in zoals hij hier staat. Vervang de waarden in de hoekhaken niet, uw Fritz! box zal dit automatisch doen wanneer u het aanroept. Je kunt het beste de kopieerfunctie gebruiken om de waarden te kopiëren.';

  @override
  String get domainNameHelp2 =>
      'Deze domeinnaam kan later niet publiekelijk worden opgelost. Uw internetadres wordt alleen gedeeld met PhoneBlock.';

  @override
  String get username => 'Gebruikersnaam';

  @override
  String get usernameHelp =>
      'De gebruikersnaam waarmee uw Fritz! box inlogt bij PhoneBlock om zijn internetadres bekend te maken.';

  @override
  String get passwordLabel => 'Wachtwoord';

  @override
  String get passwordHelp2 =>
      'Het wachtwoord waarmee uw Fritz!Box inlogt bij PhoneBlock om zijn internetadres bekend te maken. Om veiligheidsredenen kunt u niet uw eigen wachtwoord invoeren, maar moet u het wachtwoord gebruiken dat veilig door PhoneBlock wordt gegenereerd.';

  @override
  String get checkingDynDns => 'Controleer de DynDNS-instellingen.';

  @override
  String get notRegistered => 'Niet geregistreerd';

  @override
  String fritzBoxNotRegistered(String message) {
    return 'Uw Fritz!Box is nog niet geregistreerd bij PhoneBlock, DynDNS is niet up-to-date: $message';
  }

  @override
  String get sipSetupInstructions =>
      'Stel nu het PhoneBlock antwoordapparaat in als \"Telefoon (met en zonder antwoordapparaat)\". Om er zeker van te zijn dat dit werkt, dient u de onderstaande stappen exact te volgen:';

  @override
  String get sipSetupStep1 =>
      '1. Open de pagina Telefonie > Telefonieapparaten in uw Fritz!Box-instellingen en klik op de knop \"Nieuw apparaat instellen\".';

  @override
  String get sipSetupStep2 =>
      '2. selecteer de optie \"Telefoon (met en zonder antwoordapparaat)\" en klik op \"Volgende\".';

  @override
  String get sipSetupStep3 =>
      '3. Selecteer de optie \"LAN/WLAN (IP-telefoon)\", geef de telefoon de naam \"PhoneBlock\" en klik op \"Volgende\".';

  @override
  String get sipSetupStep4 =>
      '4. Voer nu de volgende gebruikersnaam en wachtwoord voor je antwoordapparaat in en klik op \"Volgende\".';

  @override
  String get usernameHelp2 =>
      'De gebruikersnaam waarmee het PhoneBlock antwoordapparaat inlogt op uw Fritz!';

  @override
  String get passwordHelp3 =>
      'Het wachtwoord dat het PhoneBlock antwoordapparaat gebruikt om in te loggen op uw Fritz! PhoneBlock heeft een veilig wachtwoord voor u gegenereerd.';

  @override
  String get sipSetupStep5 =>
      '5. het opgevraagde telefoonnummer doet er nu niet toe, het PhoneBlock antwoordapparaat belt niet actief, maar accepteert alleen SPAM oproepen. Het telefoonnummer wordt bij stap 9 weer gedeselecteerd. Klik hier gewoon op \"Volgende\".';

  @override
  String get sipSetupStep6 =>
      '6. Selecteer \"Alle oproepen accepteren\" en klik op \"Volgende\". Het PhoneBlock antwoordapparaat accepteert sowieso alleen oproepen als het nummer van de beller op de blokkadelijst staat. Tegelijkertijd accepteert PhoneBlock nooit oproepen van nummers die in uw normale telefoonboek staan.';

  @override
  String get sipSetupStep7 =>
      '7. Je ziet een samenvatting. De instellingen zijn (bijna) voltooid, klik op \"Toepassen\".';

  @override
  String get sipSetupStep8 =>
      '8. Je ziet nu \"PhoneBlock\" in de lijst met telefoonapparaten. Er ontbreken nog een paar instellingen die je pas later kunt maken. Klik daarom op het bewerkingspotlood in de regel van het PhoneBlock antwoordapparaat.';

  @override
  String get sipSetupStep9 =>
      '9. Selecteer de laatste (lege) optie in het veld \"Uitgaande gesprekken\", aangezien PhoneBlock nooit uitgaande gesprekken voert en het antwoordapparaat dus geen nummer nodig heeft voor uitgaande gesprekken.';

  @override
  String get sipSetupStep10 =>
      '10. Selecteer het tabblad \"Aanmeldingsgegevens\". Bevestig het antwoord door op \"Toepassen\" te klikken. Selecteer nu de optie \"Login vanaf internet toestaan\" zodat het PhoneBlock antwoordapparaat uit de PhoneBlock cloud kan inloggen op uw Fritz! U moet het wachtwoord van het antwoordapparaat (zie hierboven) opnieuw invoeren in het veld \"Password\" (Wachtwoord) voordat u op \"Apply\" (Toepassen) klikt. Verwijder eerst de sterretjes in het veld.';

  @override
  String get sipSetupStep11 =>
      '11. er verschijnt een melding die u waarschuwt dat er via internettoegang betaalde verbindingen tot stand kunnen worden gebracht. U kunt dit met een gerust hart bevestigen, ten eerste omdat PhoneBlock nooit actief verbindingen tot stand brengt, ten tweede omdat PhoneBlock een veilig wachtwoord voor u heeft aangemaakt (zie hierboven) zodat niemand anders verbinding kan maken en ten derde omdat u uitgaande verbindingen in stap 9 hebt gedeactiveerd. Afhankelijk van de instellingen van uw Fritz!Box moet u de instelling mogelijk bevestigen op een DECT-telefoon die rechtstreeks is aangesloten op de Fritz!';

  @override
  String get sipSetupStep12 =>
      '12. Nu is alles klaar. Klik op Terug om terug te keren naar de lijst met telefoonapparaten. Je kunt nu je antwoordapparaat activeren met de knop onderaan.';

  @override
  String get tryingToRegisterAnswerbot =>
      'Probeer antwoordapparaat te registreren...';

  @override
  String get answerbotRegistrationFailed =>
      'Registratie antwoordapparaat mislukt';

  @override
  String registrationFailed(String message) {
    return 'Registratie mislukt: $message';
  }

  @override
  String get answerbotRegisteredSuccess =>
      'Uw PhoneBlock antwoordapparaat is succesvol geregistreerd. De volgende spambellers kunnen nu met PhoneBlock praten. Als u het PhoneBlock antwoordapparaat zelf wilt testen, belt u het interne nummer van het \"PhoneBlock\" telefoontoestel dat u hebt ingesteld. Het interne nummer begint meestal met \"**\".';
}
