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

  @override
  String get answerbot => 'Telefonsvarare';

  @override
  String get answerbotSettings => 'Inställningar för telefonsvarare';

  @override
  String get minConfidence => 'Lägsta förtroende';

  @override
  String get minConfidenceHelp =>
      'Hur många klagomål krävs det för att ett nummer ska avlyssnas av telefonsvararen.';

  @override
  String get blockNumberRanges => 'Blocknummerintervall';

  @override
  String get blockNumberRangesHelp =>
      'Accepterar samtalet även för ett nummer som ännu inte är känt för att vara SPAM om det finns anledning att misstänka att numret tillhör en systemanslutning från vilken SPAM härrör.';

  @override
  String get preferIPv4 => 'Gynna IPv4-kommunikation';

  @override
  String get preferIPv4Help =>
      'Om det finns tillgängligt hanteras kommunikationen med telefonsvararen via IPv4. Det verkar finnas telefonanslutningar där röstanslutning via IPv6 inte är möjlig, trots att en IPv6-adress finns tillgänglig.';

  @override
  String get callRetention => 'Behålla samtal';

  @override
  String get automaticDeletion => 'Automatisk radering';

  @override
  String get automaticDeletionHelp =>
      'Efter vilken tid ska gamla samtalsloggar raderas automatiskt? Aldrig radera avaktiverar automatisk radering.';

  @override
  String get dnsSettings => 'DNS-inställningar';

  @override
  String get dnsSetting => 'DNS-inställning';

  @override
  String get phoneBlockDns => 'PhoneBlock DNS';

  @override
  String get otherProviderOrDomain => 'Annan leverantör eller domännamn';

  @override
  String get dnsSettingHelp =>
      'Hur telefonsvararen hittar din Fritz!-låda på Internet.';

  @override
  String get updateUrl => 'Uppdatera URL';

  @override
  String get updateUrlHelp => 'Användarnamn för DynDNS-delningen i din Fritz!';

  @override
  String get domainNameHelp => 'Namn som din Fritz! box tar emot på Internet.';

  @override
  String get dyndnsUsername => 'DynDNS användarnamn';

  @override
  String get dyndnsUsernameHelp =>
      'Användarnamn för DynDNS-delningen i din Fritz!';

  @override
  String get dyndnsPassword => 'DynDNS-lösenord';

  @override
  String get dyndnsPasswordHelp =>
      'Det lösenord som du måste använda för DynDNS-delning.';

  @override
  String get host => 'Värd';

  @override
  String get hostHelp =>
      'Det värdnamn som Fritz!Box kan nås via från Internet.';

  @override
  String get sipSettings => 'SIP-inställningar';

  @override
  String get user => 'Användare';

  @override
  String get userHelp =>
      'Det användarnamn som måste ställas in i Fritz!Box för att få åtkomst till telefonienheten.';

  @override
  String get password => 'Lösenord';

  @override
  String get passwordHelp =>
      'Det lösenord som måste tilldelas för åtkomst till telefonienheten i Fritz!';

  @override
  String get savingSettings => 'Spara inställningar...';

  @override
  String get errorSavingSettings => 'Fel vid lagring av inställningarna.';

  @override
  String savingFailed(String message) {
    return 'Spara misslyckades: $message';
  }

  @override
  String get enableAfterSavingFailed => 'Slå på igen efter misslyckad sparning';

  @override
  String get enablingAnswerbot => 'Sätt på telefonsvararen...';

  @override
  String get errorEnablingAnswerbot => 'Fel vid påslagning av telefonsvararen.';

  @override
  String cannotEnable(String message) {
    return 'Det går inte att slå på: $message';
  }

  @override
  String get enablingFailed => 'Misslyckades med att slå på telefonsvararen';

  @override
  String enablingFailedMessage(String message) {
    return 'Påslagning misslyckades: $message';
  }

  @override
  String retryingMessage(String message) {
    return '$message Försök igen...';
  }

  @override
  String get savingRetentionSettings => 'Spara lagringsinställningar...';

  @override
  String get errorSavingRetentionSettings =>
      'Fel när lagringsinställningarna sparas.';

  @override
  String get automaticDeletionDisabled => 'Automatisk radering avaktiverad';

  @override
  String retentionSettingsSaved(String period) {
    return 'Lagringsinställningar sparade ($period)';
  }

  @override
  String get oneWeek => '1 vecka';

  @override
  String get oneMonth => '1 månad';

  @override
  String get threeMonths => '3 månader';

  @override
  String get oneYear => '1 år';

  @override
  String get never => 'Aldrig';

  @override
  String deleteAnswerbotConfirm(String userName) {
    return 'Ska telefonsvararen $userName verkligen raderas?';
  }

  @override
  String get cancel => 'Avbryt';

  @override
  String get delete => 'Radera';

  @override
  String get deletionFailed => 'Radering misslyckades';

  @override
  String get answerbotCouldNotBeDeleted => 'Telefonsvararen kunde inte raderas';

  @override
  String get spamCalls => 'SPAM-samtal';

  @override
  String get deleteCalls => 'Radera samtal';

  @override
  String get deletingCallsFailed => 'Radering misslyckades';

  @override
  String get deleteRequestFailed => 'Begäran om radering kunde inte behandlas.';

  @override
  String cannotRetrieveCalls(String message, int statusCode) {
    return 'Samtal kan inte hämtas (fel $statusCode): $message.';
  }

  @override
  String get noNewCalls => 'Inga nya samtal.';

  @override
  String duration(int seconds) {
    return 'Varaktighet $seconds s';
  }

  @override
  String today(String time) {
    return 'Idag $time';
  }

  @override
  String yesterday(String time) {
    return 'Igår $time';
  }

  @override
  String get dynDnsDescriptionLong =>
      'PhoneBlock behöver veta internetadressen till din Fritz! box för att kunna registrera telefonsvararen på din Fritz! box. Om du redan har konfigurerat MyFRITZ! eller en annan DynDNS-leverantör kan du använda det här domännamnet. Om inte, kan du helt enkelt konfigurera DynDNS från PhoneBlock och sedan aktivera den här omkopplaren.';

  @override
  String get setupPhoneBlockDynDnsSnackbar => 'Konfigurera PhoneBlock DynDNS.';

  @override
  String get setupFailed => 'Installationen misslyckades';

  @override
  String cannotSetupDynDns(String message) {
    return 'DynDNS kan inte ställas in: $message';
  }

  @override
  String get domainname => 'Domännamn';

  @override
  String get domainNameHintLong =>
      'Domännamn för din Fritz!-box (antingen MyFRITZ!-adress eller DynDNS-domännamn)';

  @override
  String get inputCannotBeEmpty => 'Inmatningen får inte vara tom.';

  @override
  String get invalidDomainName => 'Inget giltigt domännamn.';

  @override
  String get domainNameTooLong => 'Domännamnet är för långt.';

  @override
  String get domainNameHintExtended =>
      'Ange domännamnet för din Fritz!Box. Om din Fritz!Box ännu inte har något domännamn aktiverar du PhoneBlock DynDNS. Du hittar domännamnet för din Fritz!Box under (under Internet > Aktier > DynDNS). Alternativt kan du också ange MyFRITZ!-adressen (Internet > MyFRITZ!-konto), t.ex. z4z...l4n.myfritz.net.';

  @override
  String get checkingDomainName => 'Kontrollera domännamn.';

  @override
  String domainNameNotAccepted(String message) {
    return 'Domännamnet accepterades inte: $message';
  }

  @override
  String get dynDnsInstructionsLong =>
      'Öppna sidan Internet > Aktier > DynDNS i Fritz!Box-inställningarna och ange den information som anges här.';

  @override
  String get updateUrlHelp2 =>
      'Den URL som Fritz!-boxen ringer upp för att ge PhoneBlock dess Internetadress. Skriv in URL:en exakt som den står här. Byt inte ut värdena i vinkelparenteserna, det gör Fritz!-boxen automatiskt när du anropar den. Det bästa är att använda kopieringsfunktionen för att kopiera värdena.';

  @override
  String get domainNameHelp2 =>
      'Detta domännamn kan inte lösas offentligt senare. Din Internetadress kommer endast att delas med PhoneBlock.';

  @override
  String get username => 'Användarens namn';

  @override
  String get usernameHelp =>
      'Det användarnamn med vilket Fritz!-boxen loggar in på PhoneBlock för att göra sin Internetadress känd.';

  @override
  String get passwordLabel => 'Lösenord';

  @override
  String get passwordHelp2 =>
      'Det lösenord med vilket Fritz!Box loggar in på PhoneBlock för att göra sin Internetadress känd. Av säkerhetsskäl kan du inte ange ditt eget lösenord, utan du måste använda det lösenord som PhoneBlock genererar på ett säkert sätt.';

  @override
  String get checkingDynDns => 'Kontrollera DynDNS-konfigurationen.';

  @override
  String get notRegistered => 'Ej registrerad';

  @override
  String fritzBoxNotRegistered(String message) {
    return 'Din Fritz!Box har ännu inte registrerats med PhoneBlock, DynDNS är inte uppdaterat: $message';
  }

  @override
  String get sipSetupInstructions =>
      'Ställ nu in PhoneBlocks telefonsvarare som \"Telefon (med och utan telefonsvarare)\". För att säkerställa att detta fungerar ska du följa stegen nedan exakt:';

  @override
  String get sipSetupStep1 =>
      '1. Öppna sidan Telefoni > Telefonienheter i dina Fritz!Box-inställningar och klicka på knappen \"Konfigurera ny enhet\".';

  @override
  String get sipSetupStep2 =>
      '2. Välj alternativet \"Telefon (med och utan telefonsvarare)\" och klicka på \"Nästa\".';

  @override
  String get sipSetupStep3 =>
      '3. Välj alternativet \"LAN/WLAN (IP-telefon)\", ge telefonen namnet \"PhoneBlock\" och klicka på \"Nästa\".';

  @override
  String get sipSetupStep4 =>
      '4. Ange nu följande användarnamn och lösenord för din telefonsvarare och klicka sedan på \"Nästa\".';

  @override
  String get usernameHelp2 =>
      'Det användarnamn med vilket PhoneBlock-svararen loggar in på din Fritz!';

  @override
  String get passwordHelp3 =>
      'Det lösenord som PhoneBlocks telefonsvarare använder för att logga in på din Fritz! PhoneBlock har genererat ett säkert lösenord åt dig.';

  @override
  String get sipSetupStep5 =>
      '5. Det telefonnummer som efterfrågas nu spelar ingen roll, PhoneBlocks telefonsvarare ringer inte aktivt några samtal, utan accepterar bara SPAM-samtal. Telefonnumret väljs bort igen i steg 9. Klicka bara på \"Nästa\" här.';

  @override
  String get sipSetupStep6 =>
      '6. Välj \"Acceptera alla samtal\" och klicka på \"Nästa\". PhoneBlocks telefonsvarare accepterar ändå bara samtal om den uppringande personens nummer finns med i spärrlistan. Samtidigt tar PhoneBlock aldrig emot samtal från nummer som finns i din vanliga telefonbok.';

  @override
  String get sipSetupStep7 =>
      '7. Du kommer att se en sammanfattning. Inställningarna är (nästan) klara, klicka på \"Apply\".';

  @override
  String get sipSetupStep8 =>
      '8. Nu ser du \"PhoneBlock\" i listan över telefonienheter. Det saknas fortfarande några inställningar som du bara kan göra senare. Klicka därför på redigeringspennan i raden för PhoneBlocks telefonsvarare.';

  @override
  String get sipSetupStep9 =>
      '9. Välj det sista (tomma) alternativet i fältet \"Utgående samtal\", eftersom PhoneBlock aldrig ringer utgående samtal och telefonsvararen därför inte behöver något nummer för utgående samtal.';

  @override
  String get sipSetupStep10 =>
      '10. Välj fliken \"Inloggningsdata\". Bekräfta svaret genom att klicka på \"Apply\". Välj nu alternativet \"Tillåt inloggning från Internet\" så att PhoneBlock-svararen från PhoneBlock-molnet kan logga in på din Fritz! Du måste ange lösenordet för telefonsvararen (se ovan) igen i fältet \"Password\" innan du klickar på \"Apply\". Ta först bort asteriskerna i fältet.';

  @override
  String get sipSetupStep11 =>
      '11. ett meddelande visas som varnar dig för att avgiftsbelagda anslutningar kan upprättas via Internetåtkomst. Du kan med säkerhet bekräfta detta, för det första eftersom PhoneBlock aldrig aktivt upprättar anslutningar, för det andra eftersom PhoneBlock har skapat ett säkert lösenord för dig (se ovan) så att ingen annan kan ansluta och för det tredje eftersom du avaktiverade de utgående anslutningarna i steg 9. Beroende på inställningarna i din Fritz!Box kan du behöva bekräfta inställningen på en DECT-telefon som är ansluten direkt till Fritz!';

  @override
  String get sipSetupStep12 =>
      '12. Nu är allt klart. Klicka på Tillbaka för att återgå till listan över telefonapparater. Du kan nu aktivera din telefonsvarare med knappen längst ned.';

  @override
  String get tryingToRegisterAnswerbot =>
      'Försök att registrera telefonsvararen...';

  @override
  String get answerbotRegistrationFailed =>
      'Registreringen av telefonsvararen misslyckades';

  @override
  String registrationFailed(String message) {
    return 'Registreringen misslyckades: $message';
  }

  @override
  String get answerbotRegisteredSuccess =>
      'Din telefonsvarare PhoneBlock har registrerats framgångsrikt. Nästa spamanropare kan nu prata med PhoneBlock. Om du själv vill testa PhoneBlocks telefonsvarare ringer du internnumret till den \"PhoneBlock\"-telefonapparat som du har installerat. Det interna numret börjar vanligtvis med \"**\".';
}
