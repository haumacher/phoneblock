// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Modern Greek (`el`).
class AppLocalizationsEl extends AppLocalizations {
  AppLocalizationsEl([String locale = 'el']) : super(locale);

  @override
  String get appTitle => 'PhoneBlock Mobile';

  @override
  String get settings => 'Ρυθμίσεις';

  @override
  String get deleteAll => 'Διαγραφή όλων';

  @override
  String get noCallsYet => 'Δεν έχουν φιλτραριστεί ακόμη κλήσεις';

  @override
  String get noCallsDescription =>
      'Το PhoneBlock θα ελέγχει αυτόματα τις εισερχόμενες κλήσεις και θα μπλοκάρει τις κλήσεις SPAM.';

  @override
  String get blocked => 'Αποκλεισμένο';

  @override
  String get accepted => 'Αποδεκτό';

  @override
  String votes(int count) {
    return '$count ψήφων';
  }

  @override
  String get viewOnPhoneBlock => 'Εμφάνιση στο PhoneBlock';

  @override
  String get confirmDeleteAll => 'Διαγραφή όλων των φιλτραρισμένων κλήσεων;';

  @override
  String get confirmDeleteAllMessage =>
      'Αυτή η ενέργεια δεν μπορεί να αναιρεθεί.';

  @override
  String get cancel => 'Ακύρωση';

  @override
  String get delete => 'Διαγραφή';

  @override
  String get settingsTitle => 'Ρυθμίσεις';

  @override
  String get callScreening => 'Φιλτράρισμα κλήσεων';

  @override
  String get minSpamReports => 'Ελάχιστα μηνύματα SPAM';

  @override
  String minSpamReportsDescription(int count) {
    return 'Οι αριθμοί μπλοκάρονται από τα μηνύματα $count και μετά';
  }

  @override
  String get blockNumberRanges => 'Περιοχές αριθμών μπλοκ';

  @override
  String get blockNumberRangesDescription =>
      'Αποκλεισμός περιοχών με πολλά μηνύματα SPAM';

  @override
  String get minSpamReportsInRange => 'Ελάχιστα μηνύματα SPAM στην περιοχή';

  @override
  String minSpamReportsInRangeDescription(int count) {
    return 'Οι περιοχές μπλοκάρονται από τα μηνύματα $count και μετά';
  }

  @override
  String get about => 'Σχετικά με το';

  @override
  String get version => 'Έκδοση';

  @override
  String get developer => 'Προγραμματιστής';

  @override
  String get developerName => 'Bernhard Haumacher';

  @override
  String get website => 'Ιστοσελίδα';

  @override
  String get websiteUrl => 'phoneblock.net';

  @override
  String get sourceCode => 'Πηγαίος κώδικας';

  @override
  String get sourceCodeLicense => 'Ανοιχτός κώδικας (GPL-3.0)';

  @override
  String get aboutDescription =>
      'Το PhoneBlock είναι ένα έργο ανοιχτού κώδικα χωρίς παρακολούθηση και χωρίς διαφήμιση. Η υπηρεσία χρηματοδοτείται από δωρεές.';

  @override
  String get donate => 'Δωρεές';

  @override
  String pendingCallsNotification(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count νέες φιλτραρισμένες κλήσεις',
      one: '1 νέα φιλτραρισμένη κλήση',
    );
    return '$_temp0';
  }

  @override
  String get tapToOpen => 'Πατήστε για να ανοίξετε την εφαρμογή';

  @override
  String get setupWelcome => 'Καλώς ήρθατε στο PhoneBlock Mobile';

  @override
  String get setupPermissionsRequired => 'Απαιτούμενες άδειες';

  @override
  String get grantPermission => 'Χορήγηση άδειας';

  @override
  String get continue_ => 'Περαιτέρω';

  @override
  String get finish => 'Έτοιμο';

  @override
  String get loginRequired => 'Εγγραφή PhoneBlock';

  @override
  String get loginToPhoneBlock => 'Εγγραφή στο PhoneBlock';

  @override
  String get verifyingLogin => 'Η εγγραφή θα ελεγχθεί...';

  @override
  String get loginFailed => 'Η σύνδεση απέτυχε';

  @override
  String get loginSuccess => 'Επιτυχής εγγραφή!';

  @override
  String get reportAsLegitimate => 'Αναφορά ως νόμιμη';

  @override
  String get reportAsSpam => 'Αναφορά ως SPAM';

  @override
  String get viewOnPhoneBlockMenu => 'Προβολή στο PhoneBlock';

  @override
  String get deleteCall => 'Διαγραφή';

  @override
  String get report => 'Αναφορά';

  @override
  String get notLoggedIn => 'Δεν έχει καταχωρηθεί. Παρακαλώ συνδεθείτε.';

  @override
  String reportedAsLegitimate(String phoneNumber) {
    return '$phoneNumber αναφέρθηκε ως νόμιμος';
  }

  @override
  String reportError(String error) {
    return 'Σφάλμα κατά την αναφορά: $error';
  }

  @override
  String reportedAsSpam(String phoneNumber) {
    return '$phoneNumber αναφέρθηκε ως SPAM';
  }

  @override
  String get selectSpamCategory => 'Επιλέξτε την κατηγορία SPAM';

  @override
  String get errorDeletingAllCalls =>
      'Σφάλμα κατά τη διαγραφή όλων των κλήσεων';

  @override
  String get errorDeletingCall => 'Σφάλμα κατά την ακύρωση της κλήσης';

  @override
  String get notLoggedInShort => 'Δεν έχει καταχωρηθεί';

  @override
  String get errorOpeningPhoneBlock => 'Σφάλμα ανοίγματος του PhoneBlock.';

  @override
  String get permissionNotGranted => 'Η άδεια δεν έχει χορηγηθεί.';

  @override
  String get setupTitle => 'PhoneBlock Mobile - Εγκατάσταση';

  @override
  String get welcome => 'Καλώς ήρθατε στην';

  @override
  String get connectPhoneBlockAccount => 'Σύνδεση λογαριασμού PhoneBlock';

  @override
  String get permissions => 'Άδειες';

  @override
  String get allowCallFiltering => 'Επιτρέψτε το φιλτράρισμα των κλήσεων';

  @override
  String get done => 'Έτοιμο';

  @override
  String get setupComplete => 'Η εγκατάσταση ολοκληρώθηκε';

  @override
  String get minReportsCount => 'Ελάχιστος αριθμός μηνυμάτων';

  @override
  String callsBlockedAfterReports(int count) {
    return 'Οι κλήσεις μπλοκάρονται από $count μηνύματα';
  }

  @override
  String rangesBlockedAfterReports(int count) {
    return 'Οι περιοχές μπλοκάρονται από τα μηνύματα $count και μετά';
  }

  @override
  String get welcomeMessage =>
      'Καλώς ήρθατε στο PhoneBlock Mobile!\n\nΑυτή η εφαρμογή σας βοηθά να αποκλείσετε αυτόματα τις κλήσεις spam. Χρειάζεστε έναν δωρεάν λογαριασμό στο PhoneBlock.net.\n\nΣυνδέστε το λογαριασμό σας PhoneBlock για να συνεχίσετε:';

  @override
  String get connectToPhoneBlock => 'Συνδεθείτε με το PhoneBlock';

  @override
  String get connectedToPhoneBlock => 'Σύνδεση με το PhoneBlock';

  @override
  String get accountConnectedSuccessfully =>
      '✓ Ο λογαριασμός συνδέθηκε επιτυχώς';

  @override
  String get permissionsMessage =>
      'Για τον αυτόματο αποκλεισμό των κλήσεων spam, το PhoneBlock Mobile απαιτεί εξουσιοδότηση για τον έλεγχο των εισερχόμενων κλήσεων.\n\nΑυτή η εξουσιοδότηση απαιτείται για να λειτουργήσει η εφαρμογή:';

  @override
  String get permissionGranted => 'Χορήγηση άδειας';

  @override
  String get permissionGrantedSuccessfully => '✓ Άδεια χορηγήθηκε επιτυχώς';

  @override
  String get setupCompleteMessage =>
      'Η εγκατάσταση ολοκληρώθηκε!\n\nΤο PhoneBlock Mobile είναι τώρα έτοιμο να αποκλείσει τις κλήσεις spam. Η εφαρμογή ελέγχει αυτόματα τις εισερχόμενες κλήσεις και μπλοκάρει γνωστούς αριθμούς spam με βάση τη βάση δεδομένων του PhoneBlock.\n\nΠατήστε \"Έγινε\" για να μεταβείτε στην κύρια προβολή.';

  @override
  String get verifyingLoginTitle => 'Έλεγχος σύνδεσης';

  @override
  String get loginSuccessMessage => 'Σύνδεση επιτυχής!';

  @override
  String get redirectingToSetup => 'Προώθηση στην εγκατάσταση...';

  @override
  String tokenVerificationFailed(String error) {
    return 'Η επαλήθευση του Token απέτυχε: $error';
  }

  @override
  String get backToSetup => 'Επιστροφή στις εγκαταστάσεις';

  @override
  String get tokenBeingVerified => 'Το Token ελέγχεται...';

  @override
  String get failedToOpenPhoneBlock => 'Το PhoneBlock δεν μπόρεσε να ανοίξει.';

  @override
  String get ratingLegitimate => 'Νόμιμο';

  @override
  String get ratingAdvertising => 'Διαφήμιση';

  @override
  String get ratingSpam => 'SPAM';

  @override
  String get ratingPingCall => 'Κλήση Ping';

  @override
  String get ratingGamble => 'Διαγωνισμός';

  @override
  String get ratingFraud => 'Απάτη';

  @override
  String get ratingPoll => 'Έρευνα';

  @override
  String get noLoginTokenReceived => 'Δεν ελήφθη κουπόνι σύνδεσης.';

  @override
  String get settingSaved => 'Ρύθμιση αποθηκευμένη';

  @override
  String get errorSaving => 'Σφάλμα κατά την αποθήκευση';

  @override
  String ratePhoneNumber(String phoneNumber) {
    return 'Βαθμολογία $phoneNumber';
  }

  @override
  String reportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count μηνυμάτων',
      one: '1 μήνυμα',
    );
    return '$_temp0';
  }

  @override
  String legitimateReportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Νόμιμα μηνύματα',
      one: '1 Νόμιμο μήνυμα',
    );
    return '$_temp0';
  }

  @override
  String get noReports => 'Δεν υπάρχουν μηνύματα';

  @override
  String todayTime(String time) {
    return 'Σήμερα, $time';
  }

  @override
  String yesterdayTime(String time) {
    return 'Χθες, $time';
  }

  @override
  String get callHistoryRetention => 'Αποθήκευση ιστορικού κλήσεων';

  @override
  String retentionPeriodDescription(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: 'Διατήρηση κλήσεων $days ημέρες',
      one: 'Διατήρηση κλήσεων 1 ημέρα',
    );
    return '$_temp0';
  }

  @override
  String get retentionInfinite => 'Κρατήστε όλες τις κλήσεις';

  @override
  String retentionDays(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: '$days ημέρες',
      one: '1 ημέρα',
    );
    return '$_temp0';
  }

  @override
  String get retentionInfiniteOption => 'Απεριόριστα';

  @override
  String get addCommentSpam => 'Προσθέστε σχόλιο (προαιρετικό)';

  @override
  String get commentHintSpam =>
      'Γιατί αυτό είναι spam; Για ποιο λόγο έγινε η κλήση; Παρακαλώ παραμείνετε ευγενικοί.';

  @override
  String get addCommentLegitimate => 'Προσθέστε σχόλιο (προαιρετικό)';

  @override
  String get commentHintLegitimate =>
      'Γιατί αυτό είναι νόμιμο; Ποιος σας τηλεφώνησε; Παρακαλώ παραμείνετε ευγενικοί.';

  @override
  String get serverSettings => 'Ρυθμίσεις διακομιστή';

  @override
  String get serverSettingsDescription =>
      'Διαχείριση των ρυθμίσεων του λογαριασμού σας PhoneBlock';

  @override
  String get searchNumber => 'Αριθμός αναζήτησης';

  @override
  String get searchPhoneNumber => 'Αναζήτηση αριθμού τηλεφώνου';

  @override
  String get enterPhoneNumber => 'Εισάγετε τον αριθμό τηλεφώνου';

  @override
  String get phoneNumberHint => 'π.χ. +49 123 456789';

  @override
  String get search => 'Αναζήτηση';

  @override
  String get invalidPhoneNumber =>
      'Παρακαλώ εισάγετε έναν έγκυρο αριθμό τηλεφώνου';

  @override
  String get blacklistTitle => 'Μαύρη λίστα';

  @override
  String get blacklistDescription => 'Αριθμοί που έχουν μπλοκαριστεί από εσάς';

  @override
  String get whitelistTitle => 'Λευκή λίστα';

  @override
  String get whitelistDescription => 'Αριθμοί που έχετε επισημάνει ως νόμιμους';

  @override
  String get blacklistEmpty => 'Η μαύρη λίστα σας είναι άδεια';

  @override
  String get whitelistEmpty => 'Η λευκή σας λίστα είναι άδεια';

  @override
  String get blacklistEmptyHelp =>
      'Προσθέστε αριθμούς αναφέροντας ανεπιθύμητες κλήσεις ως spam.';

  @override
  String get whitelistEmptyHelp =>
      'Προσθέστε αριθμούς αναφέροντας αποκλεισμένες κλήσεις ως νόμιμες.';

  @override
  String get errorLoadingList => 'Σφάλμα φόρτωσης της λίστας';

  @override
  String get numberRemovedFromList => 'Αριθμός που αφαιρέθηκε';

  @override
  String get errorRemovingNumber => 'Σφάλμα κατά την αφαίρεση του αριθμού';

  @override
  String get confirmRemoval => 'Επιβεβαίωση αφαίρεσης';

  @override
  String confirmRemoveFromBlacklist(Object phone) {
    return 'Αφαίρεση του $phone από τη μαύρη λίστα;';
  }

  @override
  String confirmRemoveFromWhitelist(Object phone) {
    return 'Αφαίρεση του $phone από τη λευκή λίστα;';
  }

  @override
  String get remove => 'Αφαιρέστε το';

  @override
  String get retry => 'Δοκιμάστε ξανά';

  @override
  String get editComment => 'Επεξεργασία σχολίου';

  @override
  String get commentLabel => 'Σχόλιο';

  @override
  String get commentHint => 'Προσθέστε μια σημείωση σε αυτόν τον αριθμό';

  @override
  String get save => 'Αποθήκευση';

  @override
  String get commentUpdated => 'Σχόλιο ενημερωμένο';

  @override
  String get errorUpdatingComment => 'Σφάλμα κατά την ενημέρωση του σχολίου';

  @override
  String get appearance => 'Εμφάνιση';

  @override
  String get themeMode => 'Σχεδιασμός';

  @override
  String get themeModeDescription => 'Επιλέξτε ένα ανοιχτό ή σκούρο σχέδιο';

  @override
  String get themeModeSystem => 'Πρότυπο συστήματος';

  @override
  String get themeModeLight => 'Φως';

  @override
  String get themeModeDark => 'Σκούρο';

  @override
  String get experimentalFeatures => 'Πειραματικές λειτουργίες';

  @override
  String get answerbotFeature => 'Αυτόματος τηλεφωνητής (Answerbot)';

  @override
  String get answerbotFeatureDescription =>
      'Πειραματική λειτουργία: Διαχείριση τηλεφωνητών SPAM που μιλούν αυτόματα στους καλούντες spam';
}
