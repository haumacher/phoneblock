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
}
