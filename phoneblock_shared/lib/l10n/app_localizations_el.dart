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
}
