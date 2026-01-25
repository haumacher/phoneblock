// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Modern Greek (`el`).
class AppLocalizationsEl extends AppLocalizations {
  AppLocalizationsEl([String locale = 'el']) : super(locale);

  @override
  String get appTitle => 'Τηλεφωνητής PhoneBlock';

  @override
  String get yourAnswerbots => 'Ο τηλεφωνητής σας';

  @override
  String get loginRequired => 'Απαιτείται εγγραφή';

  @override
  String get login => 'Σύνδεση';

  @override
  String get loadingData => 'Φόρτωση δεδομένων...';

  @override
  String get refreshingData => 'Ενημέρωση δεδομένων...';

  @override
  String get noAnswerbotsYet =>
      'Αν δεν έχετε ακόμα τηλεφωνητή, κάντε κλικ στο κουμπί συν παρακάτω για να δημιουργήσετε έναν τηλεφωνητή PhoneBlock.';

  @override
  String get createAnswerbot => 'Δημιουργία τηλεφωνητή';

  @override
  String answerbotName(String userName) {
    return 'Τηλεφωνητής $userName';
  }

  @override
  String answerbotStats(int newCalls, int talkTimeSeconds, int callsAccepted) {
    return '$newCalls νέες κλήσεις, $callsAccepted κλήσεις, $talkTimeSeconds s συνολικός χρόνος ομιλίας';
  }

  @override
  String get statusActive => 'ενεργό';

  @override
  String get statusConnecting => 'συνδεθείτε...';

  @override
  String get statusDisabled => 'απενεργοποιημένο';

  @override
  String get statusIncomplete => 'ελλιπής';

  @override
  String get deleteAnswerbot => 'Διαγραφή τηλεφωνητή';

  @override
  String get enabled => 'Ενεργοποιημένο';

  @override
  String get minVotes => 'Ελάχιστες ψήφοι';

  @override
  String get minVotesDescription =>
      'Ποιος είναι ο ελάχιστος αριθμός φωνών που απαιτείται για την αποδοχή ενός αριθμού από τον τηλεφωνητή;';

  @override
  String get minVotes2 => '2 - κλειδώστε αμέσως';

  @override
  String get minVotes4 => '4 - Περιμένετε επιβεβαίωση';

  @override
  String get minVotes10 => '10 - μόνο όταν είναι ασφαλές';

  @override
  String get minVotes100 => '100 - μόνο κορυφαίοι spammers';

  @override
  String get cannotChangeWhileEnabled =>
      'Μπορεί να αλλάξει μόνο όταν ο αυτόματος τηλεφωνητής είναι απενεργοποιημένος.';

  @override
  String get saveSettings => 'Αποθήκευση ρυθμίσεων';

  @override
  String get retentionPeriod => 'Χρόνος αποθήκευσης';

  @override
  String get retentionPeriodDescription =>
      'Πόσο καιρό πρέπει να διατηρούνται οι κλήσεις;';

  @override
  String get retentionNever => 'Ποτέ μην διαγράφετε';

  @override
  String get retentionWeek => 'Διαγραφή μετά από 1 εβδομάδα';

  @override
  String get retentionMonth => 'Διαγραφή μετά από 1 μήνα';

  @override
  String get retentionQuarter => 'Διαγραφή μετά από 3 μήνες';

  @override
  String get retentionYear => 'Διαγραφή μετά από 1 έτος';

  @override
  String get saveRetentionSettings => 'Αποθήκευση ρυθμίσεων αποθήκευσης';

  @override
  String get showHelp => 'Εμφάνιση βοήθειας';

  @override
  String get newAnswerbot => 'Νέος τηλεφωνητής';

  @override
  String get usePhoneBlockDynDns => 'Χρήση του PhoneBlock DynDNS';

  @override
  String get dynDnsDescription =>
      'Το PhoneBlock πρέπει να γνωρίζει τη διεύθυνση Internet του Fritz! box σας για να δέχεται κλήσεις.';

  @override
  String get setupPhoneBlockDynDns => 'Ρύθμιση του PhoneBlock DynDNS';

  @override
  String get domainName => 'Όνομα τομέα';

  @override
  String get domainNameHint =>
      'Εισάγετε το όνομα τομέα του Fritz! Εάν το Fritz!Box σας δεν έχει ακόμη όνομα τομέα, ενεργοποιήστε το PhoneBlock-DynDNS.';

  @override
  String get checkDomainName => 'Ελέγξτε τα ονόματα τομέων';

  @override
  String get setupDynDns => 'Ρύθμιση του DynDNS';

  @override
  String get dynDnsInstructions =>
      'Στις ρυθμίσεις του Fritz!Box, ανοίξτε τη σελίδα Internet > Κοινόχρηστα > DynDNS και εισαγάγετε εκεί τις ακόλουθες τιμές:';

  @override
  String get checkDynDns => 'Ελέγξτε το DynDNS';

  @override
  String get createAnswerbotTitle => 'Δημιουργία τηλεφωνητή';

  @override
  String get registerAnswerbot => 'Καταχωρήστε τον τηλεφωνητή';

  @override
  String get answerbotRegistered => 'Τηλεφωνητής εγγεγραμμένος';

  @override
  String get close => 'Κλείστε το';

  @override
  String get error => 'Σφάλμα';

  @override
  String cannotLoadInfo(String message, int statusCode) {
    return 'Δεν είναι δυνατή η ανάκτηση πληροφοριών (σφάλμα $statusCode): $message';
  }

  @override
  String wrongContentType(String contentType) {
    return 'Οι πληροφορίες δεν μπορούν να ανακτηθούν (Content-Type: $contentType).';
  }

  @override
  String get callList => 'Λίστα κλήσεων';

  @override
  String get clearCallList => 'Διαγραφή λίστας κλήσεων';

  @override
  String get noCalls => 'Δεν υπάρχουν κλήσεις ακόμα';

  @override
  String get answerbot => 'Τηλεφωνητής';

  @override
  String get answerbotSettings => 'Ρυθμίσεις τηλεφωνητή';

  @override
  String get minConfidence => 'Ελάχιστη εμπιστοσύνη';

  @override
  String get minConfidenceHelp =>
      'Πόσα παράπονα είναι απαραίτητα για να αναχαιτιστεί ένας αριθμός από τον τηλεφωνητή.';

  @override
  String get blockNumberRanges => 'Περιοχές αριθμών μπλοκ';

  @override
  String get blockNumberRangesHelp =>
      'Αποδέχεται την κλήση ακόμη και για έναν αριθμό που δεν είναι ακόμη γνωστό ότι είναι SPAM, εάν υπάρχει λόγος να υποπτεύεται ότι ο αριθμός ανήκει σε σύνδεση συστήματος από την οποία προέρχεται SPAM.';

  @override
  String get preferIPv4 => 'Ευνοεί την επικοινωνία IPv4';

  @override
  String get preferIPv4Help =>
      'Εάν υπάρχει, η επικοινωνία με τον αυτόματο τηλεφωνητή γίνεται μέσω IPv4. Φαίνεται ότι υπάρχουν τηλεφωνικές συνδέσεις για τις οποίες δεν είναι δυνατή η σύνδεση φωνής μέσω IPv6, παρόλο που υπάρχει διαθέσιμη διεύθυνση IPv6.';

  @override
  String get callRetention => 'Διατήρηση κλήσεων';

  @override
  String get automaticDeletion => 'Αυτόματη διαγραφή';

  @override
  String get automaticDeletionHelp =>
      'Μετά από ποιο χρονικό διάστημα θα πρέπει να διαγράφονται αυτόματα τα παλιά αρχεία καταγραφής κλήσεων; Η επιλογή Never delete απενεργοποιεί την αυτόματη διαγραφή.';

  @override
  String get dnsSettings => 'Ρυθμίσεις DNS';

  @override
  String get dnsSetting => 'Ρύθμιση DNS';

  @override
  String get phoneBlockDns => 'PhoneBlock DNS';

  @override
  String get otherProviderOrDomain => 'Άλλος πάροχος ή όνομα τομέα';

  @override
  String get dnsSettingHelp =>
      'Πώς ο τηλεφωνητής βρίσκει το κουτί σας Fritz! στο Διαδίκτυο.';

  @override
  String get updateUrl => 'Ενημέρωση URL';

  @override
  String get updateUrlHelp =>
      'Όνομα χρήστη για την κοινή χρήση DynDNS στο Fritz!';

  @override
  String get domainNameHelp =>
      'Όνομα που λαμβάνει το κουτί Fritz! στο Διαδίκτυο.';

  @override
  String get dyndnsUsername => 'Όνομα χρήστη DynDNS';

  @override
  String get dyndnsUsernameHelp =>
      'Όνομα χρήστη για την κοινή χρήση DynDNS στο Fritz!';

  @override
  String get dyndnsPassword => 'Κωδικός πρόσβασης DynDNS';

  @override
  String get dyndnsPasswordHelp =>
      'Ο κωδικός πρόσβασης που πρέπει να χρησιμοποιήσετε για την κοινή χρήση του DynDNS.';

  @override
  String get host => 'Υποδοχής';

  @override
  String get hostHelp =>
      'Το όνομα κεντρικού υπολογιστή μέσω του οποίου μπορεί να επιτευχθεί η πρόσβαση στο Fritz!Box σας από το Διαδίκτυο.';

  @override
  String get sipSettings => 'Ρυθμίσεις SIP';

  @override
  String get user => 'Χρήστης';

  @override
  String get userHelp =>
      'Το όνομα χρήστη που πρέπει να ρυθμιστεί στο Fritz!Box για την πρόσβαση στη συσκευή τηλεφωνίας.';

  @override
  String get password => 'Κωδικός πρόσβασης';

  @override
  String get passwordHelp =>
      'Ο κωδικός πρόσβασης που πρέπει να εκχωρηθεί για την πρόσβαση στην τηλεφωνική συσκευή στο σύστημα Fritz!';

  @override
  String get savingSettings => 'Αποθήκευση ρυθμίσεων...';

  @override
  String get errorSavingSettings => 'Σφάλμα κατά την αποθήκευση των ρυθμίσεων.';

  @override
  String savingFailed(String message) {
    return 'Η αποθήκευση απέτυχε: $message';
  }

  @override
  String get enableAfterSavingFailed =>
      'Επανενεργοποίηση μετά από αποτυχημένη αποθήκευση';

  @override
  String get enablingAnswerbot => 'Ενεργοποιήστε τον τηλεφωνητή...';

  @override
  String get errorEnablingAnswerbot =>
      'Σφάλμα κατά την ενεργοποίηση του αυτόματου τηλεφωνητή.';

  @override
  String cannotEnable(String message) {
    return 'Δεν είναι δυνατή η ενεργοποίηση: $message';
  }

  @override
  String get enablingFailed => 'Απέτυχε να ενεργοποιηθεί ο τηλεφωνητής';

  @override
  String enablingFailedMessage(String message) {
    return 'Η ενεργοποίηση απέτυχε: $message';
  }

  @override
  String retryingMessage(String message) {
    return '$message Δοκιμάστε ξανά...';
  }

  @override
  String get savingRetentionSettings => 'Αποθήκευση ρυθμίσεων αποθήκευσης...';

  @override
  String get errorSavingRetentionSettings =>
      'Σφάλμα κατά την αποθήκευση των ρυθμίσεων αποθήκευσης.';

  @override
  String get automaticDeletionDisabled => 'Αυτόματη διαγραφή απενεργοποιημένη';

  @override
  String retentionSettingsSaved(String period) {
    return 'Αποθήκευση ρυθμίσεων αποθήκευσης ($period)';
  }

  @override
  String get oneWeek => '1 εβδομάδα';

  @override
  String get oneMonth => '1 μήνα';

  @override
  String get threeMonths => '3 μήνες';

  @override
  String get oneYear => '1 έτος';

  @override
  String get never => 'Ποτέ';

  @override
  String deleteAnswerbotConfirm(String userName) {
    return 'Θα πρέπει πραγματικά να διαγραφεί ο τηλεφωνητής $userName;';
  }

  @override
  String get cancel => 'Ακύρωση';

  @override
  String get delete => 'Διαγραφή';

  @override
  String get deletionFailed => 'Αποτυχημένη διαγραφή';

  @override
  String get answerbotCouldNotBeDeleted =>
      'Ο τηλεφωνητής δεν μπόρεσε να διαγραφεί';

  @override
  String get spamCalls => 'Κλήσεις SPAM';

  @override
  String get deleteCalls => 'Διαγραφή κλήσεων';

  @override
  String get deletingCallsFailed => 'Αποτυχημένη διαγραφή';

  @override
  String get deleteRequestFailed =>
      'Η αίτηση διαγραφής δεν μπόρεσε να διεκπεραιωθεί.';

  @override
  String cannotRetrieveCalls(String message, int statusCode) {
    return 'Οι κλήσεις δεν μπορούν να ανακτηθούν (σφάλμα $statusCode): $message';
  }

  @override
  String get noNewCalls => 'Δεν υπάρχουν νέες κλήσεις.';

  @override
  String duration(int seconds) {
    return 'Διάρκεια $seconds s';
  }

  @override
  String today(String time) {
    return 'Σήμερα $time';
  }

  @override
  String yesterday(String time) {
    return 'Χθες $time';
  }

  @override
  String get dynDnsDescriptionLong =>
      'Το PhoneBlock πρέπει να γνωρίζει τη διεύθυνση Internet του Fritz! box σας για να καταχωρήσει τον τηλεφωνητή στο Fritz! box σας. Εάν έχετε ήδη ρυθμίσει το MyFRITZ! ή έναν άλλο πάροχο DynDNS, μπορείτε να χρησιμοποιήσετε αυτό το όνομα τομέα. Εάν όχι, μπορείτε απλώς να ρυθμίσετε το DynDNS από το PhoneBlock και, στη συνέχεια, να ενεργοποιήσετε αυτόν τον διακόπτη.';

  @override
  String get setupPhoneBlockDynDnsSnackbar => 'Ρυθμίστε το PhoneBlock DynDNS.';

  @override
  String get setupFailed => 'Η ρύθμιση απέτυχε';

  @override
  String cannotSetupDynDns(String message) {
    return 'Το DynDNS δεν μπορεί να ρυθμιστεί: $message';
  }

  @override
  String get domainname => 'Όνομα τομέα';

  @override
  String get domainNameHintLong =>
      'Όνομα τομέα του Fritz! box σας (είτε η διεύθυνση MyFRITZ! είτε το όνομα τομέα DynDNS)';

  @override
  String get inputCannotBeEmpty => 'Η είσοδος δεν πρέπει να είναι κενή.';

  @override
  String get invalidDomainName => 'Δεν υπάρχει έγκυρο όνομα τομέα.';

  @override
  String get domainNameTooLong => 'Το όνομα τομέα είναι πολύ μεγάλο.';

  @override
  String get domainNameHintExtended =>
      'Εισάγετε το όνομα τομέα του Fritz! Αν το Fritz!Box σας δεν έχει ακόμη όνομα τομέα, ενεργοποιήστε το PhoneBlock DynDNS. Μπορείτε να βρείτε το όνομα τομέα του Fritz!Box σας στην ενότητα (Στην ενότητα Internet > Κοινόχρηστα > DynDNS). Εναλλακτικά, μπορείτε επίσης να εισαγάγετε τη διεύθυνση MyFRITZ! (Internet > MyFRITZ! account), π.χ. z4z...l4n.myfritz.net.';

  @override
  String get checkingDomainName => 'Ελέγξτε τα ονόματα τομέων.';

  @override
  String domainNameNotAccepted(String message) {
    return 'Το όνομα τομέα δεν έγινε αποδεκτό: $message';
  }

  @override
  String get dynDnsInstructionsLong =>
      'Ανοίξτε τη σελίδα Internet > Shares > DynDNS στις ρυθμίσεις του Fritz!Box και εισαγάγετε τις πληροφορίες που δίνονται εδώ.';

  @override
  String get updateUrlHelp2 =>
      'Η διεύθυνση URL που καλεί το Fritz! για να δώσει στο PhoneBlock τη διεύθυνσή του στο Internet. Εισάγετε τη διεύθυνση URL ακριβώς όπως γράφεται εδώ. Μην αντικαταστήσετε τις τιμές στις αγκύλες, το Fritz! θα το κάνει αυτόματα όταν το καλέσετε. Είναι καλύτερο να χρησιμοποιήσετε τη λειτουργία αντιγραφής για να αντιγράψετε τις τιμές.';

  @override
  String get domainNameHelp2 =>
      'Αυτό το όνομα τομέα δεν μπορεί να επιλυθεί δημοσίως αργότερα. Η διεύθυνση διαδικτύου σας θα κοινοποιηθεί μόνο στο PhoneBlock.';

  @override
  String get username => 'Όνομα χρήστη';

  @override
  String get usernameHelp =>
      'Το όνομα χρήστη με το οποίο το Fritz! συνδέεται στο PhoneBlock για να κάνει γνωστή τη διεύθυνση Internet.';

  @override
  String get passwordLabel => 'Κωδικός πρόσβασης';

  @override
  String get passwordHelp2 =>
      'Ο κωδικός πρόσβασης με τον οποίο το Fritz!Box συνδέεται στο PhoneBlock για να γνωστοποιήσει τη διεύθυνση Internet. Για λόγους ασφαλείας, δεν μπορείτε να εισαγάγετε τον δικό σας κωδικό πρόσβασης, αλλά πρέπει να χρησιμοποιήσετε τον κωδικό πρόσβασης που δημιουργείται με ασφάλεια από το PhoneBlock.';

  @override
  String get checkingDynDns => 'Ελέγξτε τη ρύθμιση DynDNS.';

  @override
  String get notRegistered => 'Δεν έχει καταχωρηθεί';

  @override
  String fritzBoxNotRegistered(String message) {
    return 'Το Fritz!Box σας δεν έχει ακόμη εγγραφεί στο PhoneBlock, το DynDNS δεν είναι ενημερωμένο: $message';
  }

  @override
  String get sipSetupInstructions =>
      'Τώρα ρυθμίστε τον τηλεφωνητή PhoneBlock ως \"Τηλέφωνο (με και χωρίς τηλεφωνητή)\". Για να βεβαιωθείτε ότι αυτό λειτουργεί, ακολουθήστε ακριβώς τα παρακάτω βήματα:';

  @override
  String get sipSetupStep1 =>
      '1. Ανοίξτε τη σελίδα Τηλεφωνία > Συσκευές τηλεφωνίας στις ρυθμίσεις του Fritz!Box και κάντε κλικ στο κουμπί \"Ρύθμιση νέας συσκευής\".';

  @override
  String get sipSetupStep2 =>
      '2. Επιλέξτε την επιλογή \"Τηλέφωνο (με και χωρίς αυτόματο τηλεφωνητή)\" και κάντε κλικ στο \"Επόμενο\".';

  @override
  String get sipSetupStep3 =>
      '3. Επιλέξτε την επιλογή \"LAN/WLAN (τηλέφωνο IP)\", δώστε στο τηλέφωνο το όνομα \"PhoneBlock\" και κάντε κλικ στο \"Next\".';

  @override
  String get sipSetupStep4 =>
      '4. Εισάγετε τώρα το ακόλουθο όνομα χρήστη και τον κωδικό πρόσβασης για τον τηλεφωνητή σας και, στη συνέχεια, κάντε κλικ στο \"Επόμενο\".';

  @override
  String get usernameHelp2 =>
      'Το όνομα χρήστη με το οποίο ο τηλεφωνητής PhoneBlock συνδέεται στο Fritz!';

  @override
  String get passwordHelp3 =>
      'Ο κωδικός πρόσβασης που χρησιμοποιεί ο τηλεφωνητής PhoneBlock για να συνδεθεί στο Fritz! Το PhoneBlock έχει δημιουργήσει έναν ασφαλή κωδικό πρόσβασης για εσάς.';

  @override
  String get sipSetupStep5 =>
      '5. Ο αριθμός τηλεφώνου που ζητείται τώρα δεν έχει σημασία, ο τηλεφωνητής PhoneBlock δεν πραγματοποιεί ενεργά καμία κλήση, αλλά δέχεται μόνο κλήσεις SPAM. Ο αριθμός τηλεφώνου καταργείται και πάλι στο βήμα 9. Απλά κάντε κλικ στο \"Επόμενο\" εδώ.';

  @override
  String get sipSetupStep6 =>
      '6. επιλέξτε \"Αποδοχή όλων των κλήσεων\" και κάντε κλικ στο \"Επόμενο\". Ο τηλεφωνητής PhoneBlock δέχεται ούτως ή άλλως κλήσεις μόνο εάν ο αριθμός του καλούντος βρίσκεται στη λίστα αποκλεισμού. Ταυτόχρονα, το PhoneBlock δεν δέχεται ποτέ κλήσεις από αριθμούς που βρίσκονται στον κανονικό τηλεφωνικό σας κατάλογο.';

  @override
  String get sipSetupStep7 =>
      '7. θα δείτε μια περίληψη. Οι ρυθμίσεις έχουν (σχεδόν) ολοκληρωθεί, κάντε κλικ στο \"Apply\" (Εφαρμογή).';

  @override
  String get sipSetupStep8 =>
      '8. Τώρα θα δείτε το \"PhoneBlock\" στη λίστα των συσκευών τηλεφωνίας. Ακόμα λείπουν μερικές ρυθμίσεις που μπορείτε να κάνετε μόνο αργότερα. Επομένως, κάντε κλικ στο μολύβι επεξεργασίας στη γραμμή του τηλεφωνητή PhoneBlock.';

  @override
  String get sipSetupStep9 =>
      '9. επιλέξτε την τελευταία (κενή) επιλογή στο πεδίο \"Εξερχόμενες κλήσεις\", καθώς το PhoneBlock δεν πραγματοποιεί ποτέ εξερχόμενες κλήσεις και επομένως ο τηλεφωνητής δεν απαιτεί αριθμό για εξερχόμενες κλήσεις.';

  @override
  String get sipSetupStep10 =>
      '10. επιλέξτε την καρτέλα \"Δεδομένα σύνδεσης\". Επιβεβαιώστε την απάντηση κάνοντας κλικ στην επιλογή \"Apply\" (Εφαρμογή). Τώρα επιλέξτε την επιλογή \"Επιτρέψτε τη σύνδεση από το Internet\", ώστε ο τηλεφωνητής PhoneBlock από το cloud του PhoneBlock να μπορεί να συνδεθεί στο Fritz! Πρέπει να εισαγάγετε ξανά τον κωδικό πρόσβασης του τηλεφωνητή (βλ. παραπάνω) στο πεδίο \"Password\" (Κωδικός πρόσβασης) πριν κάνετε κλικ στο κουμπί \"Apply\" (Εφαρμογή). Διαγράψτε πρώτα τους αστερίσκους στο πεδίο.';

  @override
  String get sipSetupStep11 =>
      '11. Εμφανίζεται ένα μήνυμα που σας προειδοποιεί ότι ενδέχεται να δημιουργηθούν χρεώσιμες συνδέσεις μέσω πρόσβασης στο Διαδίκτυο. Μπορείτε να το επιβεβαιώσετε αυτό με σιγουριά, πρώτον επειδή το PhoneBlock δεν δημιουργεί ποτέ ενεργά συνδέσεις, δεύτερον επειδή το PhoneBlock έχει δημιουργήσει έναν ασφαλή κωδικό πρόσβασης για εσάς (βλ. παραπάνω), ώστε να μην μπορεί να συνδεθεί κανείς άλλος και τρίτον επειδή απενεργοποιήσατε τις εξερχόμενες συνδέσεις στο βήμα 9. Ανάλογα με τις ρυθμίσεις του Fritz!Box σας, ενδέχεται να χρειαστεί να επιβεβαιώσετε τη ρύθμιση σε ένα τηλέφωνο DECT που είναι συνδεδεμένο απευθείας στο Fritz!';

  @override
  String get sipSetupStep12 =>
      '12. τώρα όλα είναι έτοιμα. Κάντε κλικ στο Back (Επιστροφή) για να επιστρέψετε στη λίστα των συσκευών τηλεφωνίας. Μπορείτε τώρα να ενεργοποιήσετε τον τηλεφωνητή σας με το κουμπί στο κάτω μέρος.';

  @override
  String get tryingToRegisterAnswerbot =>
      'Προσπαθήστε να καταχωρήσετε τον τηλεφωνητή...';

  @override
  String get answerbotRegistrationFailed =>
      'Η εγγραφή του αυτόματου τηλεφωνητή απέτυχε';

  @override
  String registrationFailed(String message) {
    return 'Η εγγραφή απέτυχε: $message';
  }

  @override
  String get answerbotRegisteredSuccess =>
      'Ο τηλεφωνητής σας PhoneBlock έχει καταχωρηθεί με επιτυχία. Οι επόμενοι καλούντες spam μπορούν τώρα να μιλήσουν στο PhoneBlock. Αν θέλετε να δοκιμάσετε τον τηλεφωνητή PhoneBlock μόνοι σας, καλέστε τον εσωτερικό αριθμό της τηλεφωνικής συσκευής \"PhoneBlock\" που έχετε ρυθμίσει. Ο εσωτερικός αριθμός αρχίζει συνήθως με \"**\".';
}
