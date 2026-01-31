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

  @override
  String get answerbot => 'Segreteria telefonica';

  @override
  String get answerbotSettings => 'Impostazioni della segreteria telefonica';

  @override
  String get minConfidence => 'Fiducia minima';

  @override
  String get minConfidenceHelp =>
      'Quanti reclami sono necessari perché un numero venga intercettato dalla segreteria telefonica.';

  @override
  String get blockNumberRanges => 'Intervalli di numeri di blocco';

  @override
  String get blockNumberRangesHelp =>
      'Accetta la chiamata anche per un numero che non è ancora noto come SPAM se c\'è motivo di sospettare che il numero appartenga a una connessione di sistema da cui proviene lo SPAM.';

  @override
  String get preferIPv4 => 'Favorire la comunicazione IPv4';

  @override
  String get preferIPv4Help =>
      'Se disponibile, la comunicazione con la segreteria telefonica avviene tramite IPv4. Sembra che esistano connessioni telefoniche per le quali non è possibile una connessione vocale tramite IPv6, anche se è disponibile un indirizzo IPv6.';

  @override
  String get callRetention => 'Ritenzione delle chiamate';

  @override
  String get automaticDeletion => 'Cancellazione automatica';

  @override
  String get automaticDeletionHelp =>
      'Dopo quanto tempo i vecchi registri delle chiamate devono essere cancellati automaticamente? Mai cancellare disattiva la cancellazione automatica.';

  @override
  String get dnsSettings => 'Impostazioni DNS';

  @override
  String get dnsSetting => 'Impostazione DNS';

  @override
  String get phoneBlockDns => 'PhoneBlock DNS';

  @override
  String get otherProviderOrDomain => 'Altro provider o nome di dominio';

  @override
  String get dnsSettingHelp =>
      'Come la segreteria telefonica trova la vostra casella Fritz! su Internet.';

  @override
  String get updateUrl => 'Aggiornamento URL';

  @override
  String get updateUrlHelp =>
      'Nome utente per la condivisione DynDNS nel Fritz!';

  @override
  String get domainNameHelp =>
      'Nome che il vostro box Fritz! riceve su Internet.';

  @override
  String get dyndnsUsername => 'Nome utente DynDNS';

  @override
  String get dyndnsUsernameHelp =>
      'Nome utente per la condivisione DynDNS nel Fritz!';

  @override
  String get dyndnsPassword => 'Password DynDNS';

  @override
  String get dyndnsPasswordHelp =>
      'La password da utilizzare per la condivisione di DynDNS.';

  @override
  String get host => 'Ospite';

  @override
  String get hostHelp =>
      'Il nome host attraverso il quale il Fritz!Box può essere raggiunto da Internet.';

  @override
  String get sipSettings => 'Impostazioni SIP';

  @override
  String get user => 'Utente';

  @override
  String get userHelp =>
      'Il nome utente che deve essere impostato nel Fritz!Box per accedere al dispositivo di telefonia.';

  @override
  String get password => 'Password';

  @override
  String get passwordHelp =>
      'La password che deve essere assegnata per l\'accesso al dispositivo di telefonia nel Fritz!';

  @override
  String get savingSettings => 'Salvare le impostazioni...';

  @override
  String get errorSavingSettings =>
      'Errore durante il salvataggio delle impostazioni.';

  @override
  String savingFailed(String message) {
    return 'Salvataggio fallito: $message';
  }

  @override
  String get enableAfterSavingFailed =>
      'Riaccensione dopo un salvataggio non riuscito';

  @override
  String get enablingAnswerbot => 'Accendere la segreteria telefonica...';

  @override
  String get errorEnablingAnswerbot =>
      'Errore all\'accensione della segreteria telefonica.';

  @override
  String cannotEnable(String message) {
    return 'Impossibile accendere: $message';
  }

  @override
  String get enablingFailed => 'Impossibile accendere la segreteria telefonica';

  @override
  String enablingFailedMessage(String message) {
    return 'Accensione fallita: $message';
  }

  @override
  String retryingMessage(String message) {
    return '$message Riprova...';
  }

  @override
  String get savingRetentionSettings =>
      'Salvare le impostazioni di memorizzazione...';

  @override
  String get errorSavingRetentionSettings =>
      'Errore durante il salvataggio delle impostazioni di memorizzazione.';

  @override
  String get automaticDeletionDisabled =>
      'Cancellazione automatica disattivata';

  @override
  String retentionSettingsSaved(String period) {
    return 'Impostazioni di memorizzazione salvate ($period)';
  }

  @override
  String get oneWeek => '1 settimana';

  @override
  String get oneMonth => '1 mese';

  @override
  String get threeMonths => '3 mesi';

  @override
  String get oneYear => '1 anno';

  @override
  String get never => 'Mai';

  @override
  String deleteAnswerbotConfirm(String userName) {
    return 'Il $userName della segreteria telefonica deve essere davvero cancellato?';
  }

  @override
  String get cancel => 'Annullamento';

  @override
  String get delete => 'Cancellare';

  @override
  String get deletionFailed => 'Eliminazione fallita';

  @override
  String get answerbotCouldNotBeDeleted =>
      'Non è stato possibile cancellare la segreteria telefonica';

  @override
  String get spamCalls => 'Chiamate SPAM';

  @override
  String get deleteCalls => 'Cancellare le chiamate';

  @override
  String get deletingCallsFailed => 'Eliminazione fallita';

  @override
  String get deleteRequestFailed =>
      'Non è stato possibile elaborare la richiesta di cancellazione.';

  @override
  String cannotRetrieveCalls(String message, int statusCode) {
    return 'Le chiamate non possono essere recuperate (errore $statusCode): $message';
  }

  @override
  String get noNewCalls => 'Nessuna nuova chiamata.';

  @override
  String duration(int seconds) {
    return 'Durata $seconds s';
  }

  @override
  String today(String time) {
    return 'Oggi $time';
  }

  @override
  String yesterday(String time) {
    return 'Ieri $time';
  }

  @override
  String get dynDnsDescriptionLong =>
      'PhoneBlock deve conoscere l\'indirizzo Internet della vostra casella Fritz! per poter registrare la segreteria telefonica sulla vostra casella Fritz! Se avete già configurato MyFRITZ! o un altro provider DynDNS, potete utilizzare questo nome di dominio. In caso contrario, potete semplicemente impostare DynDNS da PhoneBlock, quindi attivare questo interruttore.';

  @override
  String get setupPhoneBlockDynDnsSnackbar => 'Configurare PhoneBlock DynDNS.';

  @override
  String get setupFailed => 'Impostazione non riuscita';

  @override
  String cannotSetupDynDns(String message) {
    return 'Non è possibile impostare DynDNS: $message';
  }

  @override
  String get domainname => 'Nome del dominio';

  @override
  String get domainNameHintLong =>
      'Nome di dominio della vostra casella Fritz! (indirizzo MyFRITZ! o nome di dominio DynDNS)';

  @override
  String get inputCannotBeEmpty => 'L\'ingresso non deve essere vuoto.';

  @override
  String get invalidDomainName => 'Nessun nome di dominio valido.';

  @override
  String get domainNameTooLong => 'Il nome del dominio è troppo lungo.';

  @override
  String get domainNameHintExtended =>
      'Inserite il nome di dominio del vostro Fritz! Se il vostro Fritz!Box non ha ancora un nome di dominio, attivate PhoneBlock DynDNS. Il nome del dominio del vostro Fritz!Box si trova in (Internet > Azioni > DynDNS). In alternativa, potete anche inserire l\'indirizzo MyFRITZ! (Internet > Account MyFRITZ!), ad esempio z4z...l4n.myfritz.net.';

  @override
  String get checkingDomainName => 'Controllare i nomi di dominio.';

  @override
  String domainNameNotAccepted(String message) {
    return 'Il nome di dominio non è stato accettato: $message';
  }

  @override
  String get dynDnsInstructionsLong =>
      'Aprite la pagina Internet > Condivisioni > DynDNS nelle impostazioni del Fritz!Box e inserite le informazioni qui riportate.';

  @override
  String get updateUrlHelp2 =>
      'L\'URL che la vostra casella Fritz! richiama per dare a PhoneBlock il suo indirizzo Internet. Inserite l\'URL esattamente come è scritto qui. Non sostituite i valori nelle parentesi angolari, il vostro Fritz! lo farà automaticamente quando lo chiamerete. È meglio utilizzare la funzione di copia per copiare i valori.';

  @override
  String get domainNameHelp2 =>
      'Questo nome di dominio non può essere risolto pubblicamente in seguito. Il vostro indirizzo Internet sarà condiviso solo con PhoneBlock.';

  @override
  String get username => 'Nome utente';

  @override
  String get usernameHelp =>
      'Il nome utente con cui il vostro Fritz! si collega a PhoneBlock per rendere noto il suo indirizzo Internet.';

  @override
  String get passwordLabel => 'Password';

  @override
  String get passwordHelp2 =>
      'La password con cui il Fritz!Box si collega a PhoneBlock per rendere noto il proprio indirizzo Internet. Per motivi di sicurezza, non è possibile inserire una propria password, ma è necessario utilizzare la password generata in modo sicuro da PhoneBlock.';

  @override
  String get checkingDynDns => 'Controllare l\'impostazione di DynDNS.';

  @override
  String get notRegistered => 'Non registrato';

  @override
  String fritzBoxNotRegistered(String message) {
    return 'Il vostro Fritz!Box non si è ancora registrato con PhoneBlock, il DynDNS non è aggiornato: $message';
  }

  @override
  String get sipSetupInstructions =>
      'Ora impostate la segreteria telefonica PhoneBlock come \"Telefono (con e senza segreteria telefonica)\". Per assicurarvi che funzioni, seguite esattamente i passaggi indicati di seguito:';

  @override
  String get sipSetupStep1 =>
      '1. aprite la pagina Telefonia > Dispositivi di telefonia nelle impostazioni del Fritz!Box e fate clic sul pulsante \"Imposta nuovo dispositivo\".';

  @override
  String get sipSetupStep2 =>
      '2. selezionare l\'opzione \"Telefono (con e senza segreteria telefonica)\" e cliccare su \"Avanti\".';

  @override
  String get sipSetupStep3 =>
      '3. selezionare l\'opzione \"LAN/WLAN (telefono IP)\", assegnare al telefono il nome \"PhoneBlock\" e cliccare su \"Avanti\".';

  @override
  String get sipSetupStep4 =>
      '4. Inserire ora il seguente nome utente e la seguente password per la segreteria telefonica e cliccare su \"Avanti\".';

  @override
  String get usernameHelp2 =>
      'Il nome utente con cui la segreteria telefonica PhoneBlock si collega al Fritz!';

  @override
  String get passwordHelp3 =>
      'La password che la segreteria telefonica PhoneBlock utilizza per accedere al vostro Fritz! PhoneBlock ha generato una password sicura per voi.';

  @override
  String get sipSetupStep5 =>
      '5. il numero di telefono interrogato ora non ha importanza, la segreteria telefonica PhoneBlock non effettua attivamente alcuna chiamata, ma accetta solo chiamate SPAM. Il numero di telefono viene nuovamente deselezionato al punto 9. Qui è sufficiente fare clic su \"Avanti\".';

  @override
  String get sipSetupStep6 =>
      '6. selezionare \"Accetta tutte le chiamate\" e cliccare su \"Avanti\". La segreteria telefonica PhoneBlock accetta comunque le chiamate solo se il numero del chiamante è presente nell\'elenco di blocco. Allo stesso tempo, PhoneBlock non accetta mai chiamate da numeri presenti nella normale rubrica telefonica.';

  @override
  String get sipSetupStep7 =>
      '7. Verrà visualizzato un riepilogo. Le impostazioni sono (quasi) complete, fare clic su \"Applica\".';

  @override
  String get sipSetupStep8 =>
      '8. Ora si vedrà \"PhoneBlock\" nell\'elenco dei dispositivi di telefonia. Mancano ancora alcune impostazioni che potranno essere effettuate solo in seguito. Pertanto, fare clic sulla matita di modifica nella riga della segreteria telefonica PhoneBlock.';

  @override
  String get sipSetupStep9 =>
      '9. selezionare l\'ultima opzione (vuota) nel campo \"Chiamate in uscita\", poiché PhoneBlock non effettua mai chiamate in uscita e quindi la segreteria telefonica non richiede un numero per le chiamate in uscita.';

  @override
  String get sipSetupStep10 =>
      '10. Selezionare la scheda \"Dati di accesso\". Confermare la risposta facendo clic su \"Applica\". Ora selezionate l\'opzione \"Consenti il login da Internet\", in modo che la segreteria telefonica PhoneBlock dalla nuvola PhoneBlock possa accedere al vostro Fritz! È necessario inserire nuovamente la password della segreteria telefonica (vedi sopra) nel campo \"Password\" prima di cliccare su \"Applica\". Cancellare prima gli asterischi nel campo.';

  @override
  String get sipSetupStep11 =>
      '11. Appare un messaggio che vi avverte che potrebbero essere stabilite connessioni a pagamento tramite l\'accesso a Internet. Potete confermarlo con sicurezza, in primo luogo perché PhoneBlock non stabilisce mai attivamente delle connessioni, in secondo luogo perché PhoneBlock ha creato una password sicura per voi (vedi sopra) in modo che nessun altro possa connettersi e in terzo luogo perché avete disattivato le connessioni in uscita al punto 9. A seconda delle impostazioni del Fritz!Box, potrebbe essere necessario confermare l\'impostazione su un telefono DECT collegato direttamente al Fritz!';

  @override
  String get sipSetupStep12 =>
      '12. Ora tutto è pronto. Fare clic su Indietro per tornare all\'elenco dei dispositivi di telefonia. Ora è possibile attivare la segreteria telefonica con il pulsante in basso.';

  @override
  String get tryingToRegisterAnswerbot =>
      'Prova a registrare la segreteria telefonica...';

  @override
  String get answerbotRegistrationFailed =>
      'Registrazione della segreteria telefonica non riuscita';

  @override
  String registrationFailed(String message) {
    return 'Registrazione fallita: $message';
  }

  @override
  String get answerbotRegisteredSuccess =>
      'La vostra segreteria telefonica PhoneBlock è stata registrata con successo. I prossimi chiamanti spam possono ora parlare con PhoneBlock. Se volete testare voi stessi la segreteria telefonica PhoneBlock, componete il numero interno del dispositivo telefonico \"PhoneBlock\" che avete impostato. Il numero interno inizia solitamente con \"**\".';
}
