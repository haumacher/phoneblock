// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Italian (`it`).
class AppLocalizationsIt extends AppLocalizations {
  AppLocalizationsIt([String locale = 'it']) : super(locale);

  @override
  String get appTitle => 'PhoneBlock Mobile';

  @override
  String get settings => 'Impostazioni';

  @override
  String get deleteAll => 'Cancellare tutti';

  @override
  String get noCallsYet => 'Nessuna chiamata filtrata';

  @override
  String get noCallsDescription =>
      'PhoneBlock controlla automaticamente le chiamate in entrata e blocca le chiamate SPAM.';

  @override
  String get blocked => 'Bloccato';

  @override
  String get accepted => 'Accettato';

  @override
  String votes(int count) {
    return '$count dei voti';
  }

  @override
  String get viewOnPhoneBlock => 'Mostra su PhoneBlock';

  @override
  String get confirmDeleteAll => 'Cancellare tutte le chiamate filtrate?';

  @override
  String get confirmDeleteAllMessage =>
      'Questa azione non può essere annullata.';

  @override
  String get cancel => 'Annullamento';

  @override
  String get delete => 'Cancellare';

  @override
  String get settingsTitle => 'Impostazioni';

  @override
  String get callScreening => 'Filtraggio delle chiamate';

  @override
  String get minSpamReports => 'Messaggi SPAM ridotti al minimo';

  @override
  String minSpamReportsDescription(int count) {
    return 'I numeri sono bloccati a partire da $count dei messaggi';
  }

  @override
  String get blockNumberRanges => 'Intervalli di numeri di blocco';

  @override
  String get blockNumberRangesDescription =>
      'Bloccare le aree con molti messaggi SPAM';

  @override
  String get minSpamReportsInRange => 'Messaggi SPAM minimi nell\'area di';

  @override
  String minSpamReportsInRangeDescription(int count) {
    return 'Le aree sono bloccate a partire dai messaggi $count.';
  }

  @override
  String get about => 'Circa';

  @override
  String get version => 'Versione';

  @override
  String get developer => 'Sviluppatore';

  @override
  String get developerName => 'Bernhard Haumacher';

  @override
  String get website => 'Sito web';

  @override
  String get websiteUrl => 'phoneblock.net';

  @override
  String get sourceCode => 'Codice sorgente';

  @override
  String get sourceCodeLicense => 'Open Source (GPL-3.0)';

  @override
  String get aboutDescription =>
      'PhoneBlock è un progetto open source senza tracciamento e senza pubblicità. Il servizio è finanziato dalle donazioni.';

  @override
  String get donate => 'Donazioni';

  @override
  String pendingCallsNotification(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count delle nuove chiamate filtrate',
      one: '1 nuova chiamata filtrata',
    );
    return '$_temp0';
  }

  @override
  String get tapToOpen => 'Toccare per aprire l\'applicazione';

  @override
  String get setupWelcome => 'Benvenuti a PhoneBlock Mobile';

  @override
  String get setupPermissionsRequired => 'Autorizzazioni richieste';

  @override
  String get grantPermission => 'Concessione dell\'autorizzazione';

  @override
  String get continue_ => 'Ulteriori';

  @override
  String get finish => 'Finito';

  @override
  String get loginRequired => 'Registrazione PhoneBlock';

  @override
  String get loginToPhoneBlock => 'Registrarsi con PhoneBlock';

  @override
  String get verifyingLogin => 'La registrazione sarà controllata...';

  @override
  String get loginFailed => 'Accesso non riuscito';

  @override
  String get loginSuccess => 'Registrazione riuscita!';

  @override
  String get reportAsLegitimate => 'Segnala come legittimo';

  @override
  String get reportAsSpam => 'Segnala come SPAM';

  @override
  String get viewOnPhoneBlockMenu => 'Vista su PhoneBlock';

  @override
  String get deleteCall => 'Cancellare';

  @override
  String get report => 'Rapporto';

  @override
  String get notLoggedIn => 'Non è registrato. Effettuare il login.';

  @override
  String reportedAsLegitimate(String phoneNumber) {
    return '$phoneNumber segnalato come legittimo';
  }

  @override
  String reportError(String error) {
    return 'Errore durante la segnalazione: $error';
  }

  @override
  String reportedAsSpam(String phoneNumber) {
    return '$phoneNumber segnalato come SPAM';
  }

  @override
  String get selectSpamCategory => 'Selezionare la categoria SPAM';

  @override
  String get errorDeletingAllCalls =>
      'Errore durante l\'eliminazione di tutte le chiamate';

  @override
  String get errorDeletingCall =>
      'Errore durante l\'annullamento della chiamata';

  @override
  String get notLoggedInShort => 'Non registrato';

  @override
  String get errorOpeningPhoneBlock => 'Errore nell\'apertura di PhoneBlock.';

  @override
  String get permissionNotGranted => 'L\'autorizzazione non è stata concessa.';

  @override
  String get setupTitle => 'PhoneBlock Mobile - Installazione';

  @override
  String get welcome => 'Benvenuti a';

  @override
  String get connectPhoneBlockAccount => 'Collegare l\'account PhoneBlock';

  @override
  String get permissions => 'Autorizzazioni';

  @override
  String get allowCallFiltering => 'Consentire il filtraggio delle chiamate';

  @override
  String get done => 'Finito';

  @override
  String get setupComplete => 'Installazione completata';

  @override
  String get minReportsCount => 'Numero minimo di messaggi';

  @override
  String callsBlockedAfterReports(int count) {
    return 'Le chiamate sono bloccate da $count dei messaggi';
  }

  @override
  String rangesBlockedAfterReports(int count) {
    return 'Le aree sono bloccate a partire dai messaggi $count.';
  }

  @override
  String get welcomeMessage =>
      'Benvenuti a PhoneBlock Mobile!\n\nQuesta applicazione vi aiuta a bloccare automaticamente le chiamate di spam. È necessario un account gratuito con PhoneBlock.net.\n\nCollegate il vostro account PhoneBlock per continuare:';

  @override
  String get connectToPhoneBlock => 'Connettersi con PhoneBlock';

  @override
  String get connectedToPhoneBlock => 'Collegato con PhoneBlock';

  @override
  String get accountConnectedSuccessfully =>
      '✓ L\'account è stato collegato con successo';

  @override
  String get permissionsMessage =>
      'Per bloccare automaticamente le chiamate spam, PhoneBlock Mobile richiede l\'autorizzazione a controllare le chiamate in entrata.\n\nQuesta autorizzazione è necessaria per il funzionamento dell\'applicazione:';

  @override
  String get permissionGranted => 'Autorizzazione concessa';

  @override
  String get permissionGrantedSuccessfully =>
      '✓ Autorizzazione concessa con successo';

  @override
  String get setupCompleteMessage =>
      'Installazione completata!\n\nPhoneBlock Mobile è ora pronto a bloccare le chiamate di spam. L\'applicazione analizza automaticamente le chiamate in entrata e blocca i numeri di spam conosciuti in base al database di PhoneBlock.\n\nPremere \"Fatto\" per passare alla visualizzazione principale.';

  @override
  String get verifyingLoginTitle => 'Controllare il login';

  @override
  String get loginSuccessMessage => 'Accesso riuscito!';

  @override
  String get redirectingToSetup => 'Inoltro alla struttura...';

  @override
  String tokenVerificationFailed(String error) {
    return 'Verifica del token fallita: $error';
  }

  @override
  String get backToSetup => 'Torna alla struttura';

  @override
  String get tokenBeingVerified => 'Il gettone è controllato...';

  @override
  String get failedToOpenPhoneBlock =>
      'Non è stato possibile aprire PhoneBlock.';

  @override
  String get ratingLegitimate => 'Legittimo';

  @override
  String get ratingAdvertising => 'Pubblicità';

  @override
  String get ratingSpam => 'SPAM';

  @override
  String get ratingPingCall => 'Chiamata ping';

  @override
  String get ratingGamble => 'Concorso';

  @override
  String get ratingFraud => 'Frode';

  @override
  String get ratingPoll => 'Sondaggio';

  @override
  String get noLoginTokenReceived => 'Nessun token di accesso ricevuto.';

  @override
  String get settingSaved => 'Impostazione salvata';

  @override
  String get errorSaving => 'Errore durante il salvataggio';

  @override
  String ratePhoneNumber(String phoneNumber) {
    return 'Tariffa $phoneNumber';
  }

  @override
  String reportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count dei messaggi',
      one: '1 Messaggio',
    );
    return '$_temp0';
  }

  @override
  String legitimateReportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Messaggi legittimi',
      one: '1 Messaggio legittimo',
    );
    return '$_temp0';
  }

  @override
  String get noReports => 'Nessun messaggio';

  @override
  String todayTime(String time) {
    return 'Oggi, $time';
  }

  @override
  String yesterdayTime(String time) {
    return 'Ieri, $time';
  }

  @override
  String get callHistoryRetention =>
      'Memorizzazione della cronologia delle chiamate';

  @override
  String retentionPeriodDescription(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: 'Mantenere le chiamate $days giorni',
      one: 'Mantenere le chiamate 1 giorno',
    );
    return '$_temp0';
  }

  @override
  String get retentionInfinite => 'Mantenere tutte le chiamate';

  @override
  String retentionDays(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: '$days giorni',
      one: '1 giorno',
    );
    return '$_temp0';
  }

  @override
  String get retentionInfiniteOption => 'Illimitato';

  @override
  String get addCommentSpam => 'Aggiungere un commento (facoltativo)';

  @override
  String get commentHintSpam =>
      'Perché si tratta di spam? A cosa si riferiva la chiamata? Si prega di rimanere educati.';

  @override
  String get addCommentLegitimate => 'Aggiungere un commento (facoltativo)';

  @override
  String get commentHintLegitimate =>
      'Perché è legittimo? Chi l\'ha chiamata? La prego di rimanere educato.';

  @override
  String get serverSettings => 'Impostazioni del server';

  @override
  String get serverSettingsDescription =>
      'Gestione delle impostazioni dell\'account PhoneBlock';

  @override
  String get searchNumber => 'Numero di ricerca';

  @override
  String get searchPhoneNumber => 'Ricerca numero di telefono';

  @override
  String get enterPhoneNumber => 'Inserire il numero di telefono';

  @override
  String get phoneNumberHint => 'ad esempio +49 123 456789';

  @override
  String get search => 'Ricerca';

  @override
  String get invalidPhoneNumber => 'Inserire un numero di telefono valido';

  @override
  String get blacklistTitle => 'Lista nera';

  @override
  String get blacklistDescription => 'Numeri bloccati dall\'utente';

  @override
  String get whitelistTitle => 'Lista bianca';

  @override
  String get whitelistDescription =>
      'Numeri che avete contrassegnato come legittimi';

  @override
  String get blacklistEmpty => 'La lista nera è vuota';

  @override
  String get whitelistEmpty => 'La whitelist è vuota';

  @override
  String get blacklistEmptyHelp =>
      'Aggiungere numeri segnalando le chiamate indesiderate come spam.';

  @override
  String get whitelistEmptyHelp =>
      'Aggiungere numeri segnalando le chiamate bloccate come legittime.';

  @override
  String get errorLoadingList => 'Errore nel caricamento dell\'elenco';

  @override
  String get numberRemovedFromList => 'Numero rimosso';

  @override
  String get errorRemovingNumber => 'Errore durante la rimozione del numero';

  @override
  String get confirmRemoval => 'Confermare la rimozione';

  @override
  String confirmRemoveFromBlacklist(Object phone) {
    return 'Rimuovere $phone dalla lista nera?';
  }

  @override
  String confirmRemoveFromWhitelist(Object phone) {
    return 'Rimuovere $phone dalla whitelist?';
  }

  @override
  String get remove => 'Rimuovere';

  @override
  String get retry => 'Riprova';

  @override
  String get editComment => 'Modifica commento';

  @override
  String get commentLabel => 'Commento';

  @override
  String get commentHint => 'Aggiungere una nota a questo numero';

  @override
  String get save => 'Risparmiare';

  @override
  String get commentUpdated => 'Commento aggiornato';

  @override
  String get errorUpdatingComment =>
      'Errore durante l\'aggiornamento del commento';

  @override
  String get appearance => 'Aspetto';

  @override
  String get themeMode => 'Design';

  @override
  String get themeModeDescription => 'Scegliere un design chiaro o scuro';

  @override
  String get themeModeSystem => 'Sistema standard';

  @override
  String get themeModeLight => 'Luce';

  @override
  String get themeModeDark => 'Scuro';

  @override
  String get experimentalFeatures => 'Funzioni sperimentali';

  @override
  String get answerbotFeature => 'Segreteria telefonica (Answerbot)';

  @override
  String get answerbotFeatureDescription =>
      'Funzione sperimentale: gestione delle segreterie telefoniche SPAM che parlano automaticamente con i chiamanti spam';

  @override
  String get answerbotMenuTitle => 'Segreteria telefonica';

  @override
  String get answerbotMenuDescription =>
      'Gestire la segreteria telefonica SPAM';
}
