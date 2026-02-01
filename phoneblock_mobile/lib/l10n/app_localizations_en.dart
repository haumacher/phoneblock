// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appTitle => 'PhoneBlock Mobile';

  @override
  String get settings => 'Settings';

  @override
  String get deleteAll => 'Delete all';

  @override
  String get noCallsYet => 'No calls filtered yet';

  @override
  String get noCallsDescription =>
      'PhoneBlock will automatically check incoming calls and block SPAM calls.';

  @override
  String get blocked => 'Blocked';

  @override
  String get accepted => 'Accepted';

  @override
  String votes(int count) {
    return '$count votes';
  }

  @override
  String get viewOnPhoneBlock => 'View on PhoneBlock';

  @override
  String get confirmDeleteAll => 'Delete all filtered calls?';

  @override
  String get confirmDeleteAllMessage => 'This action cannot be undone.';

  @override
  String get cancel => 'Cancel';

  @override
  String get delete => 'Delete';

  @override
  String get settingsTitle => 'Settings';

  @override
  String get callScreening => 'Call Screening';

  @override
  String get minSpamReports => 'Minimum SPAM reports';

  @override
  String minSpamReportsDescription(int count) {
    return 'Numbers will be blocked after $count reports';
  }

  @override
  String get blockNumberRanges => 'Block number ranges';

  @override
  String get blockNumberRangesDescription =>
      'Block ranges with many SPAM reports';

  @override
  String get minSpamReportsInRange => 'Minimum SPAM reports in range';

  @override
  String minSpamReportsInRangeDescription(int count) {
    return 'Ranges will be blocked after $count reports';
  }

  @override
  String get about => 'About';

  @override
  String get version => 'Version';

  @override
  String get developer => 'Developer';

  @override
  String get developerName => 'Bernhard Haumacher';

  @override
  String get website => 'Website';

  @override
  String get websiteUrl => 'phoneblock.net';

  @override
  String get sourceCode => 'Source Code';

  @override
  String get sourceCodeLicense => 'Open Source (GPL-3.0)';

  @override
  String get aboutDescription =>
      'PhoneBlock is an open-source project without tracking and without ads. The service is funded by donations.';

  @override
  String get donate => 'Donate';

  @override
  String pendingCallsNotification(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count new filtered calls',
      one: '1 new filtered call',
    );
    return '$_temp0';
  }

  @override
  String get tapToOpen => 'Tap to open the app';

  @override
  String get setupWelcome => 'Welcome to PhoneBlock Mobile';

  @override
  String get setupPermissionsRequired => 'Required Permissions';

  @override
  String get grantPermission => 'Grant Permission';

  @override
  String get continue_ => 'Continue';

  @override
  String get finish => 'Finish';

  @override
  String get loginRequired => 'PhoneBlock Login';

  @override
  String get loginToPhoneBlock => 'Login to PhoneBlock';

  @override
  String get verifyingLogin => 'Verifying login...';

  @override
  String get loginFailed => 'Login failed';

  @override
  String get loginSuccess => 'Login successful!';

  @override
  String get reportAsLegitimate => 'Report as legitimate';

  @override
  String get reportAsSpam => 'Report as SPAM';

  @override
  String get viewOnPhoneBlockMenu => 'View on PhoneBlock';

  @override
  String get deleteCall => 'Delete';

  @override
  String get report => 'Report';

  @override
  String get notLoggedIn => 'Not logged in. Please log in.';

  @override
  String reportedAsLegitimate(String phoneNumber) {
    return '$phoneNumber reported as legitimate';
  }

  @override
  String reportError(String error) {
    return 'Error reporting: $error';
  }

  @override
  String reportedAsSpam(String phoneNumber) {
    return '$phoneNumber reported as SPAM';
  }

  @override
  String get selectSpamCategory => 'Select SPAM category';

  @override
  String get errorDeletingAllCalls => 'Error deleting all calls';

  @override
  String get errorDeletingCall => 'Error deleting call';

  @override
  String get notLoggedInShort => 'Not logged in';

  @override
  String get errorOpeningPhoneBlock => 'Error opening PhoneBlock.';

  @override
  String get permissionNotGranted => 'Permission was not granted.';

  @override
  String get setupTitle => 'PhoneBlock Mobile - Setup';

  @override
  String get welcome => 'Welcome';

  @override
  String get connectPhoneBlockAccount => 'Connect PhoneBlock account';

  @override
  String get permissions => 'Permissions';

  @override
  String get allowCallFiltering => 'Allow call filtering';

  @override
  String get done => 'Done';

  @override
  String get setupComplete => 'Setup complete';

  @override
  String get minReportsCount => 'Minimum number of reports';

  @override
  String callsBlockedAfterReports(int count) {
    return 'Calls will be blocked after $count reports';
  }

  @override
  String rangesBlockedAfterReports(int count) {
    return 'Ranges will be blocked after $count reports';
  }

  @override
  String get welcomeMessage =>
      'Welcome to PhoneBlock Mobile!\n\nThis app helps you automatically block spam calls. To use it, you need a free account at PhoneBlock.net.\n\nConnect your PhoneBlock account to continue:';

  @override
  String get connectToPhoneBlock => 'Connect to PhoneBlock';

  @override
  String get connectedToPhoneBlock => 'Connected to PhoneBlock';

  @override
  String get accountConnectedSuccessfully => '✓ Account successfully connected';

  @override
  String get permissionsMessage =>
      'To automatically block spam calls, PhoneBlock Mobile needs permission to screen incoming calls.\n\nThis permission is required for the app to work:';

  @override
  String get permissionGranted => 'Permission granted';

  @override
  String get permissionGrantedSuccessfully =>
      '✓ Permission successfully granted';

  @override
  String get setupCompleteMessage =>
      'Setup complete!\n\nPhoneBlock Mobile is now ready to block spam calls. The app automatically checks incoming calls and blocks known spam numbers based on the PhoneBlock database.\n\nPress \"Done\" to go to the main screen.';

  @override
  String get verifyingLoginTitle => 'Verifying Login';

  @override
  String get loginSuccessMessage => 'Login successful!';

  @override
  String get redirectingToSetup => 'Redirecting to setup...';

  @override
  String tokenVerificationFailed(String error) {
    return 'Token verification failed: $error';
  }

  @override
  String get backToSetup => 'Back to setup';

  @override
  String get tokenBeingVerified => 'Token is being verified...';

  @override
  String get failedToOpenPhoneBlock => 'Failed to open PhoneBlock.';

  @override
  String get ratingLegitimate => 'Legitimate';

  @override
  String get ratingAdvertising => 'Advertising';

  @override
  String get ratingSpam => 'SPAM';

  @override
  String get ratingPingCall => 'Ping call';

  @override
  String get ratingGamble => 'Prize draw';

  @override
  String get ratingFraud => 'Fraud';

  @override
  String get ratingPoll => 'Survey';

  @override
  String get noLoginTokenReceived => 'No login token received.';

  @override
  String get settingSaved => 'Setting saved';

  @override
  String get errorSaving => 'Error saving';

  @override
  String ratePhoneNumber(String phoneNumber) {
    return 'Rate $phoneNumber';
  }

  @override
  String reportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count complaints',
      one: '1 Complaint',
    );
    return '$_temp0';
  }

  @override
  String rangeReportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Complaints in the number range',
      one: '1 Complaint in the number range',
    );
    return '$_temp0';
  }

  @override
  String legitimateReportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count legitimate reports',
      one: '1 legitimate report',
    );
    return '$_temp0';
  }

  @override
  String get noReports => 'No reports';

  @override
  String todayTime(String time) {
    return 'Today, $time';
  }

  @override
  String yesterdayTime(String time) {
    return 'Yesterday, $time';
  }

  @override
  String get callHistoryRetention => 'Call History Retention';

  @override
  String retentionPeriodDescription(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: 'Keep calls for $days days',
      one: 'Keep calls for 1 day',
    );
    return '$_temp0';
  }

  @override
  String get retentionInfinite => 'Keep all calls';

  @override
  String retentionDays(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: '$days days',
      one: '1 day',
    );
    return '$_temp0';
  }

  @override
  String get retentionInfiniteOption => 'Infinite';

  @override
  String get addCommentSpam => 'Add Comment (Optional)';

  @override
  String get commentHintSpam =>
      'Why is this spam? What was the call about? Please be respectful.';

  @override
  String get addCommentLegitimate => 'Add Comment (Optional)';

  @override
  String get commentHintLegitimate =>
      'Why is this legitimate? Who called you? Please be respectful.';

  @override
  String get serverSettings => 'Server Settings';

  @override
  String get serverSettingsDescription =>
      'Manage your PhoneBlock account settings';

  @override
  String get searchNumber => 'Search Number';

  @override
  String get searchPhoneNumber => 'Search Phone Number';

  @override
  String get enterPhoneNumber => 'Enter phone number';

  @override
  String get phoneNumberHint => 'e.g. +49 123 456789';

  @override
  String get search => 'Search';

  @override
  String get invalidPhoneNumber => 'Please enter a valid phone number';

  @override
  String get blacklistTitle => 'Blacklist';

  @override
  String get blacklistDescription => 'Numbers you have blocked';

  @override
  String get whitelistTitle => 'Whitelist';

  @override
  String get whitelistDescription => 'Numbers you have marked as legitimate';

  @override
  String get blacklistEmpty => 'Your blacklist is empty';

  @override
  String get whitelistEmpty => 'Your whitelist is empty';

  @override
  String get blacklistEmptyHelp =>
      'Add numbers by reporting unwanted calls as spam.';

  @override
  String get whitelistEmptyHelp =>
      'Add numbers by reporting blocked calls as legitimate.';

  @override
  String get errorLoadingList => 'Error loading list';

  @override
  String get numberRemovedFromList => 'Number removed';

  @override
  String get errorRemovingNumber => 'Error removing number';

  @override
  String get confirmRemoval => 'Confirm Removal';

  @override
  String confirmRemoveFromBlacklist(Object phone) {
    return 'Remove $phone from blacklist?';
  }

  @override
  String confirmRemoveFromWhitelist(Object phone) {
    return 'Remove $phone from whitelist?';
  }

  @override
  String get remove => 'Remove';

  @override
  String get retry => 'Retry';

  @override
  String get editComment => 'Edit Comment';

  @override
  String get commentLabel => 'Comment';

  @override
  String get commentHint => 'Add a note about this number';

  @override
  String get save => 'Save';

  @override
  String get commentUpdated => 'Comment updated';

  @override
  String get errorUpdatingComment => 'Error updating comment';

  @override
  String get appearance => 'Appearance';

  @override
  String get themeMode => 'Design';

  @override
  String get themeModeDescription => 'Choose light or dark design';

  @override
  String get themeModeSystem => 'System standard';

  @override
  String get themeModeLight => 'Light';

  @override
  String get themeModeDark => 'Dark';

  @override
  String get experimentalFeatures => 'Experimental functions';

  @override
  String get answerbotFeature => 'Answering machine (Answerbot)';

  @override
  String get answerbotFeatureDescription =>
      'Experimental: Manage SPAM answering machine for the Fritz!Box in the app';

  @override
  String get answerbotMenuTitle => 'Answering machine';

  @override
  String get answerbotMenuDescription => 'Manage SPAM answering machine';

  @override
  String potentialSpamLabel(String rating) {
    return 'Suspicious: $rating';
  }

  @override
  String get statistics => 'Statistics';

  @override
  String get blockedCallsCount => 'Blocked calls';

  @override
  String get suspiciousCallsCount => 'Suspicious calls';

  @override
  String get fritzboxTitle => 'Fritz!Box';

  @override
  String get fritzboxConnected => 'Connected';

  @override
  String get fritzboxOffline => 'Not available';

  @override
  String get fritzboxError => 'Connection error';

  @override
  String get fritzboxNotConfiguredShort => 'Not set up';

  @override
  String get fritzboxNotConfigured => 'No Fritz!Box set up';

  @override
  String get fritzboxNotConfiguredDescription =>
      'Connect your Fritz!Box to see calls from your landline.';

  @override
  String get fritzboxConnect => 'Connect Fritz!Box';

  @override
  String get fritzboxDisconnect => 'Disconnect Fritz!Box';

  @override
  String get fritzboxDisconnectTitle => 'Disconnect Fritz! box?';

  @override
  String get fritzboxDisconnectMessage =>
      'The saved calls and access data are deleted.';

  @override
  String get fritzboxSyncNow => 'Synchronize now';

  @override
  String get fritzboxSyncDescription =>
      'Retrieving the call list from the Fritz!';

  @override
  String fritzboxSyncComplete(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count new calls synchronized',
      one: '1 new call synchronized',
      zero: 'No new calls',
    );
    return '$_temp0';
  }

  @override
  String get fritzboxSyncError => 'Error during synchronization';

  @override
  String get fritzboxVersion => 'FRITZ!OS version';

  @override
  String get fritzboxHost => 'Address';

  @override
  String get fritzboxCachedCalls => 'Saved calls';

  @override
  String get fritzboxLastSync => 'Last synchronization';

  @override
  String get fritzboxJustNow => 'Just now';

  @override
  String fritzboxMinutesAgo(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Before $count minutes',
      one: '1 minute ago',
    );
    return '$_temp0';
  }

  @override
  String fritzboxHoursAgo(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count hours ago',
      one: '1 hour ago',
    );
    return '$_temp0';
  }

  @override
  String get fritzboxWizardTitle => 'Connect Fritz!Box';

  @override
  String get fritzboxStepDetection => 'Find Fritz!Box';

  @override
  String get fritzboxStepDetectionSubtitle => 'Automatic search in the network';

  @override
  String get fritzboxStepLogin => 'Log in';

  @override
  String get fritzboxStepLoginSubtitle => 'Enter access data';

  @override
  String get fritzboxSearching => 'Search for Fritz!Box...';

  @override
  String get fritzboxNotFound => 'Fritz!Box not found';

  @override
  String get fritzboxNotFoundDescription =>
      'The Fritz!Box could not be found automatically. Please enter the address manually.';

  @override
  String get fritzboxHostLabel => 'Fritz!Box address';

  @override
  String get fritzboxRetrySearch => 'Search again';

  @override
  String get fritzboxManualConnect => 'Connect';

  @override
  String get fritzboxLoginDescription =>
      'Enter your Fritz!Box access data. You can find them in the Fritz!Box user interface under System > Fritz!Box user.';

  @override
  String get fritzboxUsernameLabel => 'User name';

  @override
  String get fritzboxUsernameHint => 'admin or your Fritz!Box user';

  @override
  String get fritzboxPasswordLabel => 'Password';

  @override
  String get fritzboxCredentialsNote =>
      'Your access data is stored securely on your device.';

  @override
  String get fritzboxTestAndSave => 'Testing and saving';

  @override
  String get fritzboxConnectionFailed =>
      'Connection failed. Please check the access data.';

  @override
  String get fritzboxFillAllFields => 'Please fill in all fields.';

  @override
  String get fritzboxOfflineBanner =>
      'Fritz! box not reachable - show saved calls';

  @override
  String get sourceMobile => 'Mobile';

  @override
  String get sourceFritzbox => 'Fritz!Box';
}
