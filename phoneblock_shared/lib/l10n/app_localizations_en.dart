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

  @override
  String get answerbot => 'Answering machine';

  @override
  String get answerbotSettings => 'Answering machine settings';

  @override
  String get minConfidence => 'Minimum confidence';

  @override
  String get minConfidenceHelp =>
      'How many complaints are necessary for a number to be intercepted by the answering machine.';

  @override
  String get blockNumberRanges => 'Blocking number ranges';

  @override
  String get blockNumberRangesHelp =>
      'Accepts the call even for a number that is not yet known to be SPAM if there is reason to suspect that the number belongs to a system connection from which SPAM originates.';

  @override
  String get preferIPv4 => 'Prefer IPv4 communication';

  @override
  String get preferIPv4Help =>
      'If available, communication with the answering machine is handled via IPv4. There appear to be telephone connections for which a voice connection via IPv6 is not possible, although an IPv6 address is available.';

  @override
  String get callRetention => 'Call retention';

  @override
  String get automaticDeletion => 'Automatic deletion';

  @override
  String get automaticDeletionHelp =>
      'After what time should old call logs be deleted automatically? Never delete deactivates automatic deletion.';

  @override
  String get dnsSettings => 'DNS Settings';

  @override
  String get dnsSetting => 'DNS setting';

  @override
  String get phoneBlockDns => 'PhoneBlock DNS';

  @override
  String get otherProviderOrDomain => 'Other provider or domain name';

  @override
  String get dnsSettingHelp =>
      'How the answering machine finds your Fritz! box on the Internet.';

  @override
  String get updateUrl => 'Update URL';

  @override
  String get updateUrlHelp => 'User name for the DynDNS share in your Fritz!';

  @override
  String get domainNameHelp =>
      'Name that your Fritz! box receives on the Internet.';

  @override
  String get dyndnsUsername => 'DynDNS user name';

  @override
  String get dyndnsUsernameHelp =>
      'User name for the DynDNS share in your Fritz!';

  @override
  String get dyndnsPassword => 'DynDNS password';

  @override
  String get dyndnsPasswordHelp =>
      'The password that you must use for DynDNS sharing.';

  @override
  String get host => 'Host';

  @override
  String get hostHelp =>
      'The host name via which your Fritz!Box can be reached from the Internet.';

  @override
  String get sipSettings => 'SIP Settings';

  @override
  String get user => 'User';

  @override
  String get userHelp =>
      'The user name that must be set up in the Fritz!Box to access the telephony device.';

  @override
  String get password => 'Password';

  @override
  String get passwordHelp =>
      'The password that must be assigned for access to the telephony device in the Fritz!';

  @override
  String get savingSettings => 'Save settings...';

  @override
  String get errorSavingSettings => 'Error when saving the settings.';

  @override
  String savingFailed(String message) {
    return 'Save failed: $message';
  }

  @override
  String get enableAfterSavingFailed => 'Restart after saving failed';

  @override
  String get enablingAnswerbot => 'Switch on answering machine...';

  @override
  String get errorEnablingAnswerbot =>
      'Error when switching on the answering machine.';

  @override
  String cannotEnable(String message) {
    return 'Cannot switch on: $message';
  }

  @override
  String get enablingFailed => 'Failed to switch on the answering machine';

  @override
  String enablingFailedMessage(String message) {
    return 'Switch on failed: $message';
  }

  @override
  String retryingMessage(String message) {
    return '$message Try again...';
  }

  @override
  String get savingRetentionSettings => 'Save storage settings...';

  @override
  String get errorSavingRetentionSettings =>
      'Error when saving the storage settings.';

  @override
  String get automaticDeletionDisabled => 'Automatic deletion deactivated';

  @override
  String retentionSettingsSaved(String period) {
    return 'Storage settings saved ($period)';
  }

  @override
  String get oneWeek => '1 week';

  @override
  String get oneMonth => '1 month';

  @override
  String get threeMonths => '3 months';

  @override
  String get oneYear => '1 year';

  @override
  String get never => 'Never';

  @override
  String deleteAnswerbotConfirm(String userName) {
    return 'Should the answering machine $userName really be deleted?';
  }

  @override
  String get cancel => 'Cancel';

  @override
  String get delete => 'Delete';

  @override
  String get deletionFailed => 'Delete failed';

  @override
  String get answerbotCouldNotBeDeleted =>
      'The answering machine could not be deleted';

  @override
  String get spamCalls => 'SPAM calls';

  @override
  String get deleteCalls => 'Delete calls';

  @override
  String get deletingCallsFailed => 'Delete failed';

  @override
  String get deleteRequestFailed =>
      'The deletion request could not be processed.';

  @override
  String cannotRetrieveCalls(String message, int statusCode) {
    return 'Calls cannot be retrieved (error $statusCode): $message';
  }

  @override
  String get noNewCalls => 'No new calls.';

  @override
  String duration(int seconds) {
    return 'Duration $seconds s';
  }

  @override
  String today(String time) {
    return 'Today $time';
  }

  @override
  String yesterday(String time) {
    return 'Yesterday $time';
  }

  @override
  String get dynDnsDescriptionLong =>
      'PhoneBlock needs to know the Internet address of your Fritz! box in order to register the answering machine on your Fritz! box. If you have already set up MyFRITZ! or another DynDNS provider, you can use this domain name. If not, you can simply set up DynDNS from PhoneBlock, then activate this switch.';

  @override
  String get setupPhoneBlockDynDnsSnackbar => 'Set up PhoneBlock DynDNS.';

  @override
  String get setupFailed => 'Setup failed';

  @override
  String cannotSetupDynDns(String message) {
    return 'DynDNS cannot be set up: $message';
  }

  @override
  String get domainname => 'Domain name';

  @override
  String get domainNameHintLong =>
      'Domain name of your Fritz! box (either MyFRITZ! address, or DynDNS domain name)';

  @override
  String get inputCannotBeEmpty => 'Input must not be empty.';

  @override
  String get invalidDomainName => 'No valid domain name.';

  @override
  String get domainNameTooLong => 'The domain name is too long.';

  @override
  String get domainNameHintExtended =>
      'Enter the domain name of your Fritz! If your Fritz!Box does not yet have a domain name, activate PhoneBlock-DynDNS. You can find the domain name of your Fritz! box under (Under Internet > Shares > DynDNS). Alternatively, you can also enter the MyFRITZ! address (Internet > MyFRITZ! account), e.g. z4z...l4n.myfritz.net.';

  @override
  String get checkingDomainName => 'Check domain names.';

  @override
  String domainNameNotAccepted(String message) {
    return 'Domain name was not accepted: $message';
  }

  @override
  String get dynDnsInstructionsLong =>
      'Open the Internet > Shares > DynDNS page in your Fritz!Box settings and enter the information given here.';

  @override
  String get updateUrlHelp2 =>
      'The URL that your Fritz! box calls up to give PhoneBlock its Internet address. Enter the URL exactly as it is written here. Do not replace the values in the angle brackets, your Fritz! box will do this automatically. It is best to use the copy function to copy the values.';

  @override
  String get domainNameHelp2 =>
      'This domain name cannot be publicly resolved later. Your Internet address will only be shared with PhoneBlock.';

  @override
  String get username => 'User name';

  @override
  String get usernameHelp =>
      'The user name with which your Fritz!Box logs in to PhoneBlock to make its Internet address known.';

  @override
  String get passwordLabel => 'Password';

  @override
  String get passwordHelp2 =>
      'The password with which your Fritz!Box logs in to PhoneBlock to make its Internet address known. For security reasons, you cannot enter your own password, but must use the password securely generated by PhoneBlock.';

  @override
  String get checkingDynDns => 'Check DynDNS setup.';

  @override
  String get notRegistered => 'Not registered';

  @override
  String fritzBoxNotRegistered(String message) {
    return 'Your Fritz!Box has not yet registered with PhoneBlock, DynDNS is not up to date: $message';
  }

  @override
  String get sipSetupInstructions =>
      'Now set up the PhoneBlock answering machine as \"Telephone (with and without answering machine)\". To ensure that this works, please follow the steps below exactly:';

  @override
  String get sipSetupStep1 =>
      '1. open the Telephony > Telephony devices page in your Fritz!Box settings and click on the \"Set up new device\" button.';

  @override
  String get sipSetupStep2 =>
      '2. select the option \"Telephone (with and without answering machine)\" and click on \"Next\".';

  @override
  String get sipSetupStep3 =>
      '3. select the option \"LAN/WLAN (IP phone)\", give the phone the name \"PhoneBlock\" and click on \"Next\".';

  @override
  String get sipSetupStep4 =>
      '4. now enter the following user name and password for your answering machine and then click on \"Next\".';

  @override
  String get usernameHelp2 =>
      'The user name with which the PhoneBlock answering machine logs on to your Fritz!';

  @override
  String get passwordHelp3 =>
      'The password that the PhoneBlock answering machine uses to log on to your Fritz! PhoneBlock has generated a secure password for you.';

  @override
  String get sipSetupStep5 =>
      '5. the phone number queried now does not matter, the PhoneBlock answering machine does not actively make any calls, but only accepts SPAM calls. The phone number is deselected again in step 9. Simply click on \"Next\" here.';

  @override
  String get sipSetupStep6 =>
      '6. select \"Accept all calls\" and click on \"Next\". The PhoneBlock answering machine only accepts calls anyway if the caller\'s number is on the block list. At the same time, PhoneBlock never accepts calls from numbers that are in your normal phone book.';

  @override
  String get sipSetupStep7 =>
      '7. you will see a summary. The settings are (almost) complete, click on \"Apply\".';

  @override
  String get sipSetupStep8 =>
      '8. you will now see \"PhoneBlock\" in the list of telephony devices. There are still a few settings missing that you can only make later. Therefore, click on the edit pencil in the line of the PhoneBlock answering machine.';

  @override
  String get sipSetupStep9 =>
      '9. select the last (empty) option in the \"Outgoing calls\" field, as PhoneBlock never makes outgoing calls and therefore the answering machine does not require a number for outgoing calls.';

  @override
  String get sipSetupStep10 =>
      '10. select the \"Login data\" tab. Confirm the response by clicking on \"Apply\". Now select the option \"Allow login from the Internet\" so that the PhoneBlock answering machine from the PhoneBlock Cloud can log in to your Fritz! You must enter the answering machine password (see above) again in the \"Password\" field before you click on \"Apply\". First delete the asterisks in the field.';

  @override
  String get sipSetupStep11 =>
      '11. a message appears warning you that chargeable connections could be established via Internet access. You can confidently confirm this, firstly because PhoneBlock never actively establishes connections, secondly because PhoneBlock has created a secure password for you (see above) so that nobody else can connect and thirdly because you deactivated the outgoing connections in step 9. Depending on the settings of your Fritz!Box, you may need to confirm the setting on a DECT phone connected directly to the Fritz!';

  @override
  String get sipSetupStep12 =>
      '12. now everything is done. Click on Back to return to the list of telephony devices. You can now activate your answering machine with the button at the bottom.';

  @override
  String get tryingToRegisterAnswerbot =>
      'Try to register answering machine...';

  @override
  String get answerbotRegistrationFailed =>
      'Answering machine registration failed';

  @override
  String registrationFailed(String message) {
    return 'Registration failed: $message';
  }

  @override
  String get answerbotRegisteredSuccess =>
      'Your PhoneBlock answering machine has been successfully registered. The next spam callers can now talk to PhoneBlock. If you want to test the PhoneBlock answering machine yourself, dial the internal number of the \"PhoneBlock\" telephony device you have set up. The internal number usually begins with \"**\".';
}
