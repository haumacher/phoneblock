// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appTitle => 'PhoneBlock answering machine';

  @override
  String get yourAnswerbots => 'Your answering machine';

  @override
  String get loginRequired => 'Registration required';

  @override
  String get login => 'Login';

  @override
  String get loadingData => 'Loading data...';

  @override
  String get refreshingData => 'Update data...';

  @override
  String get noAnswerbotsYet =>
      'You don\'t have an answering machine yet, click the plus button below to create a PhoneBlock answering machine.';

  @override
  String get createAnswerbot => 'Creating an answering machine';

  @override
  String answerbotName(String userName) {
    return 'Answering machine $userName';
  }

  @override
  String answerbotStats(int newCalls, int talkTimeSeconds, int callsAccepted) {
    return '$newCalls new calls, $callsAccepted calls, $talkTimeSeconds s total talk time';
  }

  @override
  String get statusActive => 'active';

  @override
  String get statusConnecting => 'connect...';

  @override
  String get statusDisabled => 'switched off';

  @override
  String get statusIncomplete => 'incomplete';

  @override
  String get deleteAnswerbot => 'Delete answering machine';

  @override
  String get enabled => 'Activated';

  @override
  String get minVotes => 'Minimum votes';

  @override
  String get minVotesDescription =>
      'What is the minimum number of voices required for a number to be accepted by the answering machine?';

  @override
  String get minVotes2 => '2 - lock immediately';

  @override
  String get minVotes4 => '4 - Wait for confirmation';

  @override
  String get minVotes10 => '10 - only when safe';

  @override
  String get minVotes100 => '100 - only top spammers';

  @override
  String get cannotChangeWhileEnabled =>
      'Can only be changed when the answering machine is switched off.';

  @override
  String get saveSettings => 'Save settings';

  @override
  String get retentionPeriod => 'Storage time';

  @override
  String get retentionPeriodDescription => 'How long should calls be kept?';

  @override
  String get retentionNever => 'Never delete';

  @override
  String get retentionWeek => 'Delete after 1 week';

  @override
  String get retentionMonth => 'Delete after 1 month';

  @override
  String get retentionQuarter => 'Delete after 3 months';

  @override
  String get retentionYear => 'Delete after 1 year';

  @override
  String get saveRetentionSettings => 'Save storage settings';

  @override
  String get showHelp => 'Show help';

  @override
  String get newAnswerbot => 'New answering machine';

  @override
  String get usePhoneBlockDynDns => 'Use PhoneBlock DynDNS';

  @override
  String get dynDnsDescription =>
      'PhoneBlock needs to know the Internet address of your Fritz! box in order to accept calls.';

  @override
  String get setupPhoneBlockDynDns => 'Set up PhoneBlock DynDNS';

  @override
  String get domainName => 'Domain name';

  @override
  String get domainNameHint =>
      'Enter the domain name of your Fritz! If your Fritz!Box does not yet have a domain name, activate PhoneBlock-DynDNS.';

  @override
  String get checkDomainName => 'Check domain names';

  @override
  String get setupDynDns => 'Set up DynDNS';

  @override
  String get dynDnsInstructions =>
      'In your Fritz!Box settings, open the page Internet > Shares > DynDNS and enter the following values there:';

  @override
  String get checkDynDns => 'Check DynDNS';

  @override
  String get createAnswerbotTitle => 'Create answering machine';

  @override
  String get registerAnswerbot => 'Register answering machine';

  @override
  String get answerbotRegistered => 'Answering machine registered';

  @override
  String get close => 'Close';

  @override
  String get error => 'Error';

  @override
  String cannotLoadInfo(String message, int statusCode) {
    return 'Information cannot be retrieved (error $statusCode): $message';
  }

  @override
  String wrongContentType(String contentType) {
    return 'Information cannot be retrieved (Content-Type: $contentType).';
  }

  @override
  String get callList => 'Call list';

  @override
  String get clearCallList => 'Delete call list';

  @override
  String get noCalls => 'No calls yet';
}
