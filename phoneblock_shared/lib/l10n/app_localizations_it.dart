// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Italian (`it`).
class AppLocalizationsIt extends AppLocalizations {
  AppLocalizationsIt([String locale = 'it']) : super(locale);

  @override
  String get appTitle => 'Segreteria telefonica PhoneBlock';

  @override
  String get yourAnswerbots => 'La segreteria telefonica';

  @override
  String get loginRequired => 'Registrazione obbligatoria';

  @override
  String get login => 'Accesso';

  @override
  String get loadingData => 'Caricamento dati...';

  @override
  String get refreshingData => 'Aggiornare i dati...';

  @override
  String get noAnswerbotsYet =>
      'Se non avete ancora una segreteria telefonica, fate clic sul pulsante più in basso per creare una segreteria telefonica PhoneBlock.';

  @override
  String get createAnswerbot => 'Creare una segreteria telefonica';

  @override
  String answerbotName(String userName) {
    return 'Segreteria telefonica $userName';
  }

  @override
  String answerbotStats(int newCalls, int talkTimeSeconds, int callsAccepted) {
    return '$newCalls nuove chiamate, $callsAccepted chiamate, $talkTimeSeconds s tempo totale di conversazione';
  }

  @override
  String get statusActive => 'attivo';

  @override
  String get statusConnecting => 'connettersi...';

  @override
  String get statusDisabled => 'spento';

  @override
  String get statusIncomplete => 'incompleto';

  @override
  String get deleteAnswerbot => 'Cancellare la segreteria telefonica';

  @override
  String get enabled => 'Attivato';

  @override
  String get minVotes => 'Voti minimi';

  @override
  String get minVotesDescription =>
      'Qual è il numero minimo di voci richiesto perché un numero venga accettato dalla segreteria telefonica?';

  @override
  String get minVotes2 => '2 - bloccare immediatamente';

  @override
  String get minVotes4 => '4 - Attendere la conferma';

  @override
  String get minVotes10 => '10 - solo quando è sicuro';

  @override
  String get minVotes100 => '100 - solo i top spammers';

  @override
  String get cannotChangeWhileEnabled =>
      'Può essere modificato solo quando la segreteria telefonica è spenta.';

  @override
  String get saveSettings => 'Salva le impostazioni';

  @override
  String get retentionPeriod => 'Tempo di conservazione';

  @override
  String get retentionPeriodDescription =>
      'Per quanto tempo devono essere conservate le chiamate?';

  @override
  String get retentionNever => 'Non cancellare mai';

  @override
  String get retentionWeek => 'Cancellare dopo 1 settimana';

  @override
  String get retentionMonth => 'Cancellare dopo 1 mese';

  @override
  String get retentionQuarter => 'Cancellare dopo 3 mesi';

  @override
  String get retentionYear => 'Cancellare dopo 1 anno';

  @override
  String get saveRetentionSettings =>
      'Salvare le impostazioni di memorizzazione';

  @override
  String get showHelp => 'Mostra aiuto';

  @override
  String get newAnswerbot => 'Nuova segreteria telefonica';

  @override
  String get usePhoneBlockDynDns => 'Utilizzare PhoneBlock DynDNS';

  @override
  String get dynDnsDescription =>
      'PhoneBlock deve conoscere l\'indirizzo Internet della vostra casella Fritz! per poter accettare le chiamate.';

  @override
  String get setupPhoneBlockDynDns => 'Configurare PhoneBlock DynDNS';

  @override
  String get domainName => 'Nome del dominio';

  @override
  String get domainNameHint =>
      'Inserite il nome di dominio del vostro Fritz! Se il vostro Fritz!Box non ha ancora un nome di dominio, attivate PhoneBlock-DynDNS.';

  @override
  String get checkDomainName => 'Controllare i nomi di dominio';

  @override
  String get setupDynDns => 'Impostazione di DynDNS';

  @override
  String get dynDnsInstructions =>
      'Nelle impostazioni del Fritz!Box, aprite la pagina Internet > Condivisioni > DynDNS e inserite i seguenti valori:';

  @override
  String get checkDynDns => 'Controllare DynDNS';

  @override
  String get createAnswerbotTitle => 'Creare una segreteria telefonica';

  @override
  String get registerAnswerbot => 'Registrazione della segreteria telefonica';

  @override
  String get answerbotRegistered => 'Segreteria telefonica registrata';

  @override
  String get close => 'Chiudere';

  @override
  String get error => 'Errore';

  @override
  String cannotLoadInfo(String message, int statusCode) {
    return 'Non è possibile recuperare le informazioni (errore $statusCode): $message';
  }

  @override
  String wrongContentType(String contentType) {
    return 'Le informazioni non possono essere recuperate (Content-Type: $contentType).';
  }

  @override
  String get callList => 'Elenco chiamate';

  @override
  String get clearCallList => 'Cancellare l\'elenco delle chiamate';

  @override
  String get noCalls => 'Ancora nessuna chiamata';
}
