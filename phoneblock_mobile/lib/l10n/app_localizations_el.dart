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
  String get missed => 'Missed';

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
      other: '$count καταγγελιών',
      one: '1 Καταγγελία',
    );
    return '$_temp0';
  }

  @override
  String rangeReportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Καταγγελίες στην περιοχή αριθμών',
      one: '1 Καταγγελία στην περιοχή αριθμών',
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
      'Πειραματικό: Διαχείριση του τηλεφωνητή SPAM για το Fritz!Box στην εφαρμογή';

  @override
  String get answerbotMenuTitle => 'Τηλεφωνητής';

  @override
  String get answerbotMenuDescription => 'Διαχείριση του τηλεφωνητή SPAM';

  @override
  String potentialSpamLabel(String rating) {
    return 'Ύποπτο: $rating';
  }

  @override
  String get statistics => 'Στατιστικά στοιχεία';

  @override
  String get blockedCallsCount => 'Αποκλεισμένες κλήσεις';

  @override
  String get suspiciousCallsCount => 'Ύποπτες κλήσεις';

  @override
  String get fritzboxTitle => 'Fritz!Box';

  @override
  String get fritzboxConnected => 'Συνδεδεμένο';

  @override
  String get fritzboxConnectedNotProtected => 'Συνδεδεμένο, όχι προστατευμένο';

  @override
  String get fritzboxOffline => 'Δεν είναι διαθέσιμο';

  @override
  String get fritzboxError => 'Σφάλμα σύνδεσης';

  @override
  String get fritzboxNotConfiguredShort => 'Δεν έχει ρυθμιστεί';

  @override
  String get fritzboxNotConfigured => 'Δεν έχει ρυθμιστεί το Fritz!Box';

  @override
  String get fritzboxNotConfiguredDescription =>
      'Συνδέστε το Fritz!Box για να βλέπετε κλήσεις από το σταθερό σας τηλέφωνο.';

  @override
  String get fritzboxConnect => 'Συνδέστε το Fritz!Box';

  @override
  String get fritzboxDisconnect => 'Αποσυνδέστε το Fritz!Box';

  @override
  String get fritzboxDisconnectTitle => 'Αποσυνδέστε το κουτί Fritz!';

  @override
  String get fritzboxDisconnectMessage =>
      'Οι αποθηκευμένες κλήσεις και τα δεδομένα πρόσβασης διαγράφονται.';

  @override
  String get fritzboxSyncNow => 'Συγχρονισμός τώρα';

  @override
  String get fritzboxSyncDescription =>
      'Ανάκτηση της λίστας κλήσεων από το Fritz!';

  @override
  String fritzboxSyncComplete(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count συγχρονισμένων νέων κλήσεων',
      one: '1 νέα συγχρονισμένη κλήση',
      zero: 'Δεν υπάρχουν νέες κλήσεις',
    );
    return '$_temp0';
  }

  @override
  String get fritzboxSyncError => 'Σφάλμα κατά τον συγχρονισμό';

  @override
  String get fritzboxVersion => 'Έκδοση FRITZ!OS';

  @override
  String get fritzboxHost => 'Διεύθυνση';

  @override
  String get fritzboxCachedCalls => 'Αποθηκευμένες κλήσεις';

  @override
  String get fritzboxLastSync => 'Τελευταίος συγχρονισμός';

  @override
  String get fritzboxJustNow => 'Μόλις τώρα';

  @override
  String fritzboxMinutesAgo(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Πριν από $count λεπτά',
      one: '1 λεπτό πριν',
    );
    return '$_temp0';
  }

  @override
  String fritzboxHoursAgo(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count ώρες πριν',
      one: '1 ώρα πριν',
    );
    return '$_temp0';
  }

  @override
  String get fritzboxWizardTitle => 'Συνδέστε το Fritz!Box';

  @override
  String get fritzboxStepDetection => 'Βρείτε το Fritz!Box';

  @override
  String get fritzboxStepDetectionSubtitle => 'Αυτόματη αναζήτηση στο δίκτυο';

  @override
  String get fritzboxStepLogin => 'Συνδεθείτε';

  @override
  String get fritzboxStepLoginSubtitle => 'Εισαγωγή δεδομένων πρόσβασης';

  @override
  String get fritzboxSearching => 'Αναζήτηση για Fritz!Box...';

  @override
  String get fritzboxNotFound => 'Fritz!Box δεν βρέθηκε';

  @override
  String get fritzboxNotFoundDescription =>
      'Το Fritz!Box δεν βρέθηκε αυτόματα. Παρακαλούμε εισάγετε τη διεύθυνση χειροκίνητα.';

  @override
  String get fritzboxHostLabel => 'Διεύθυνση Fritz!Box';

  @override
  String get fritzboxRetrySearch => 'Αναζήτηση ξανά';

  @override
  String get fritzboxManualConnect => 'Συνδέστε το';

  @override
  String get fritzboxLoginDescription =>
      'Εισάγετε τα δεδομένα πρόσβασης του Fritz!Box. Μπορείτε να τα βρείτε στο περιβάλλον εργασίας χρήστη του Fritz!Box στην ενότητα Σύστημα > Fritz!Box user.';

  @override
  String get fritzboxShowUsername => 'Εισάγετε το όνομα χρήστη';

  @override
  String get fritzboxShowUsernameHint =>
      'Συνήθως χρησιμοποιείται ο προεπιλεγμένος χρήστης';

  @override
  String get fritzboxUsernameLabel => 'Όνομα χρήστη';

  @override
  String get fritzboxPasswordLabel => 'Κωδικός πρόσβασης';

  @override
  String get fritzboxCredentialsNote =>
      'Τα δεδομένα πρόσβασής σας αποθηκεύονται με ασφάλεια στη συσκευή σας.';

  @override
  String get fritzboxTestAndSave => 'Δοκιμές και αποθήκευση';

  @override
  String get fritzboxConnectionFailed =>
      'Η σύνδεση απέτυχε. Ελέγξτε τα δεδομένα πρόσβασης.';

  @override
  String get fritzboxFillAllFields => 'Παρακαλούμε συμπληρώστε όλα τα πεδία.';

  @override
  String get fritzboxOfflineBanner =>
      'Fritz! κουτί δεν είναι προσβάσιμο - εμφάνιση αποθηκευμένων κλήσεων';

  @override
  String get sourceMobile => 'Κινητό';

  @override
  String get sourceFritzbox => 'Fritz!Box';

  @override
  String get fritzboxStepBlocklist => 'Προστασία από spam';

  @override
  String get fritzboxStepBlocklistSubtitle => 'Ρύθμιση λίστας μπλοκαρίσματος';

  @override
  String get fritzboxBlocklistDescription =>
      'Επιλέξτε τον τρόπο με τον οποίο το Fritz!Box σας θα πρέπει να προστατεύεται από κλήσεις spam.';

  @override
  String get fritzboxCardDavTitle => 'Λίστα αποκλεισμού CardDAV';

  @override
  String get fritzboxCardDavDescription =>
      'Το Fritz!Box συγχρονίζει τη λίστα αποκλεισμού απευθείας με το PhoneBlock. Συνιστάται για το FRITZ!OS 7.20+.';

  @override
  String get fritzboxSkipBlocklist => 'Ρύθμιση αργότερα';

  @override
  String get fritzboxSkipBlocklistDescription =>
      'Μπορείτε να ενεργοποιήσετε την προστασία από ανεπιθύμητη αλληλογραφία αργότερα στις ρυθμίσεις.';

  @override
  String get fritzboxVersionTooOldForCardDav =>
      'Το CardDAV απαιτεί FRITZ!OS 7.20 ή νεότερη έκδοση. Το Fritz!Box σας έχει παλαιότερη έκδοση.';

  @override
  String get fritzboxFinishSetup => 'Οριστικοποίηση της εγκατάστασης';

  @override
  String get fritzboxPhoneBlockNotLoggedIn =>
      'Συνδεθείτε πρώτα στο PhoneBlock.';

  @override
  String get fritzboxCannotGetUsername =>
      'Το όνομα χρήστη PhoneBlock δεν μπόρεσε να ανακτηθεί.';

  @override
  String get fritzboxBlocklistConfigFailed =>
      'Η λίστα αποκλεισμού δεν μπόρεσε να δημιουργηθεί.';

  @override
  String get fritzboxCardDavStatus => 'Κατάσταση CardDAV';

  @override
  String get fritzboxCardDavStatusSynced => 'Συγχρονισμένο';

  @override
  String get fritzboxCardDavStatusPending => 'Εκκρεμεί ο συγχρονισμός';

  @override
  String get fritzboxCardDavStatusError => 'Σφάλμα συγχρονισμού';

  @override
  String get fritzboxCardDavStatusDisabled => 'Απενεργοποιημένο';

  @override
  String get fritzboxCardDavNote =>
      'Το Fritz!Box συγχρονίζει τον τηλεφωνικό κατάλογο μία φορά την ημέρα τα μεσάνυχτα.';

  @override
  String get fritzboxBlocklistMode => 'Λειτουργία προστασίας από spam';

  @override
  String get fritzboxBlocklistModeCardDav => 'CardDAV (αυτόματος συγχρονισμός)';

  @override
  String get fritzboxBlocklistModeNone => 'Δεν έχει ενεργοποιηθεί';

  @override
  String get fritzboxEnableCardDav => 'Ενεργοποίηση CardDAV';

  @override
  String get fritzboxEnableCardDavDescription =>
      'Συγχρονισμός της λίστας αποκλεισμού spam απευθείας με το Fritz!Box';

  @override
  String get fritzboxCardDavEnabled =>
      'Ενεργοποιημένη λίστα αποκλεισμού CardDAV';

  @override
  String get fritzboxDisableCardDav => 'Απενεργοποίηση CardDAV';

  @override
  String get fritzboxDisableCardDavTitle => 'Απενεργοποίηση του CardDAV;';

  @override
  String get fritzboxDisableCardDavMessage =>
      'Η λίστα αποκλεισμού CardDAV αφαιρείται από το Fritz!';

  @override
  String get fritzboxDisable => 'Απενεργοποίηση';

  @override
  String get fritzboxCardDavDisabled =>
      'Απενεργοποιημένη λίστα αποκλεισμού CardDAV';

  @override
  String get fritzboxAnswerbotTitle => 'Anrufbeantworter';

  @override
  String get fritzboxAnswerbotActive => 'Anrufbeantworter aktiv';

  @override
  String get fritzboxAnswerbotDescription =>
      'SPAM-Anrufe werden automatisch vom PhoneBlock-Anrufbeantworter beantwortet.';

  @override
  String get fritzboxEnableAnswerbot => 'Anrufbeantworter aktivieren';

  @override
  String get fritzboxEnableAnswerbotDescription =>
      'SPAM-Anrufe automatisch vom PhoneBlock-Anrufbeantworter beantworten lassen';

  @override
  String get fritzboxDisableAnswerbot => 'Anrufbeantworter deaktivieren';

  @override
  String get fritzboxDisableAnswerbotTitle => 'Anrufbeantworter deaktivieren?';

  @override
  String get fritzboxDisableAnswerbotMessage =>
      'Der PhoneBlock-Anrufbeantworter wird von der Fritz!Box entfernt und auf dem Server deaktiviert.';

  @override
  String get fritzboxAnswerbotEnabled => 'Anrufbeantworter aktiviert';

  @override
  String get fritzboxAnswerbotDisabled => 'Anrufbeantworter deaktiviert';

  @override
  String get fritzboxAnswerbotSetupFailed =>
      'Anrufbeantworter konnte nicht eingerichtet werden.';

  @override
  String get fritzboxAnswerbotStepCreating => 'Erstelle Anrufbeantworter...';

  @override
  String get fritzboxAnswerbotStepDetecting => 'Prüfe externen Zugang...';

  @override
  String get fritzboxAnswerbotStepDynDns => 'Konfiguriere DynDNS...';

  @override
  String get fritzboxAnswerbotStepWaitingDynDns =>
      'Warte auf DynDNS-Registrierung...';

  @override
  String get fritzboxAnswerbotStepSip => 'Registriere SIP-Gerät...';

  @override
  String get fritzboxAnswerbotStepEnabling => 'Aktiviere Anrufbeantworter...';

  @override
  String get fritzboxAnswerbotStepWaiting => 'Warte auf Registrierung...';
}
