import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_de.dart';
import 'app_localizations_en.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
      : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations? of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations);
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
    delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
  ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('de'),
    Locale('en')
  ];

  /// Application title
  ///
  /// In de, this message translates to:
  /// **'PhoneBlock Mobile'**
  String get appTitle;

  /// Settings menu item
  ///
  /// In de, this message translates to:
  /// **'Einstellungen'**
  String get settings;

  /// Delete all button tooltip
  ///
  /// In de, this message translates to:
  /// **'Alle löschen'**
  String get deleteAll;

  /// Empty state title
  ///
  /// In de, this message translates to:
  /// **'Noch keine Anrufe gefiltert'**
  String get noCallsYet;

  /// Empty state description
  ///
  /// In de, this message translates to:
  /// **'PhoneBlock wird eingehende Anrufe automatisch überprüfen und SPAM-Anrufe blockieren.'**
  String get noCallsDescription;

  /// Label for blocked calls
  ///
  /// In de, this message translates to:
  /// **'Blockiert'**
  String get blocked;

  /// Label for accepted calls
  ///
  /// In de, this message translates to:
  /// **'Angenommen'**
  String get accepted;

  /// Number of votes for a phone number
  ///
  /// In de, this message translates to:
  /// **'{count} Stimmen'**
  String votes(int count);

  /// Menu item to view number on PhoneBlock website
  ///
  /// In de, this message translates to:
  /// **'Auf PhoneBlock anzeigen'**
  String get viewOnPhoneBlock;

  /// Confirmation dialog title
  ///
  /// In de, this message translates to:
  /// **'Alle gefilterten Anrufe löschen?'**
  String get confirmDeleteAll;

  /// Confirmation dialog message
  ///
  /// In de, this message translates to:
  /// **'Diese Aktion kann nicht rückgängig gemacht werden.'**
  String get confirmDeleteAllMessage;

  /// Cancel button
  ///
  /// In de, this message translates to:
  /// **'Abbrechen'**
  String get cancel;

  /// Delete button
  ///
  /// In de, this message translates to:
  /// **'Löschen'**
  String get delete;

  /// Settings screen title
  ///
  /// In de, this message translates to:
  /// **'Einstellungen'**
  String get settingsTitle;

  /// Call screening section header
  ///
  /// In de, this message translates to:
  /// **'Anruffilterung'**
  String get callScreening;

  /// Minimum spam reports setting
  ///
  /// In de, this message translates to:
  /// **'Minimale SPAM-Meldungen'**
  String get minSpamReports;

  /// Description for minimum spam reports
  ///
  /// In de, this message translates to:
  /// **'Nummern werden ab {count} Meldungen blockiert'**
  String minSpamReportsDescription(int count);

  /// Block number ranges toggle
  ///
  /// In de, this message translates to:
  /// **'Nummernbereiche blockieren'**
  String get blockNumberRanges;

  /// Description for block number ranges
  ///
  /// In de, this message translates to:
  /// **'Blockiere Bereiche mit vielen SPAM-Meldungen'**
  String get blockNumberRangesDescription;

  /// Minimum spam reports in range setting
  ///
  /// In de, this message translates to:
  /// **'Minimale SPAM-Meldungen im Bereich'**
  String get minSpamReportsInRange;

  /// Description for minimum spam reports in range
  ///
  /// In de, this message translates to:
  /// **'Bereiche werden ab {count} Meldungen blockiert'**
  String minSpamReportsInRangeDescription(int count);

  /// About section header
  ///
  /// In de, this message translates to:
  /// **'Über'**
  String get about;

  /// Version label
  ///
  /// In de, this message translates to:
  /// **'Version'**
  String get version;

  /// Developer label
  ///
  /// In de, this message translates to:
  /// **'Entwickler'**
  String get developer;

  /// Developer name
  ///
  /// In de, this message translates to:
  /// **'Bernhard Haumacher'**
  String get developerName;

  /// Website label
  ///
  /// In de, this message translates to:
  /// **'Website'**
  String get website;

  /// Website URL
  ///
  /// In de, this message translates to:
  /// **'phoneblock.net'**
  String get websiteUrl;

  /// Source code label
  ///
  /// In de, this message translates to:
  /// **'Quellcode'**
  String get sourceCode;

  /// Source code license
  ///
  /// In de, this message translates to:
  /// **'Open Source (GPL-3.0)'**
  String get sourceCodeLicense;

  /// About description text
  ///
  /// In de, this message translates to:
  /// **'PhoneBlock ist ein Open-Source Projekt ohne Tracking und ohne Werbung. Der Dienst wird durch Spenden finanziert.'**
  String get aboutDescription;

  /// Donate button
  ///
  /// In de, this message translates to:
  /// **'Spenden'**
  String get donate;

  /// Notification title for pending filtered calls
  ///
  /// In de, this message translates to:
  /// **'{count, plural, =1{1 neuer gefilterter Anruf} other{{count} neue gefilterte Anrufe}}'**
  String pendingCallsNotification(int count);

  /// Notification text to tap to open
  ///
  /// In de, this message translates to:
  /// **'Tippen zum Öffnen der App'**
  String get tapToOpen;

  /// Setup wizard welcome message
  ///
  /// In de, this message translates to:
  /// **'Willkommen bei PhoneBlock Mobile'**
  String get setupWelcome;

  /// Setup permissions required header
  ///
  /// In de, this message translates to:
  /// **'Erforderliche Berechtigungen'**
  String get setupPermissionsRequired;

  /// Grant permission button
  ///
  /// In de, this message translates to:
  /// **'Berechtigung erteilen'**
  String get grantPermission;

  /// Continue button
  ///
  /// In de, this message translates to:
  /// **'Weiter'**
  String get continue_;

  /// Finish button
  ///
  /// In de, this message translates to:
  /// **'Fertig'**
  String get finish;

  /// Login required header
  ///
  /// In de, this message translates to:
  /// **'PhoneBlock Anmeldung'**
  String get loginRequired;

  /// Login button text
  ///
  /// In de, this message translates to:
  /// **'Bei PhoneBlock anmelden'**
  String get loginToPhoneBlock;

  /// Verifying login message
  ///
  /// In de, this message translates to:
  /// **'Anmeldung wird überprüft...'**
  String get verifyingLogin;

  /// Login failed message
  ///
  /// In de, this message translates to:
  /// **'Anmeldung fehlgeschlagen'**
  String get loginFailed;

  /// Login success message
  ///
  /// In de, this message translates to:
  /// **'Anmeldung erfolgreich!'**
  String get loginSuccess;

  /// Menu item to report as legitimate
  ///
  /// In de, this message translates to:
  /// **'Als legitim melden'**
  String get reportAsLegitimate;

  /// Menu item to report as spam
  ///
  /// In de, this message translates to:
  /// **'Als SPAM melden'**
  String get reportAsSpam;

  /// Menu item to view on PhoneBlock
  ///
  /// In de, this message translates to:
  /// **'Auf PhoneBlock ansehen'**
  String get viewOnPhoneBlockMenu;

  /// Menu item to delete call
  ///
  /// In de, this message translates to:
  /// **'Löschen'**
  String get deleteCall;

  /// Confirm report as legitimate dialog title
  ///
  /// In de, this message translates to:
  /// **'Als legitim melden?'**
  String get confirmReportLegitimate;

  /// Confirm report as legitimate message
  ///
  /// In de, this message translates to:
  /// **'Möchten Sie {phoneNumber} wirklich als legitime Nummer melden?'**
  String confirmReportLegitimateMessage(String phoneNumber);

  /// Report button
  ///
  /// In de, this message translates to:
  /// **'Melden'**
  String get report;

  /// Not logged in error message
  ///
  /// In de, this message translates to:
  /// **'Nicht angemeldet. Bitte melden Sie sich an.'**
  String get notLoggedIn;

  /// Successfully reported as legitimate
  ///
  /// In de, this message translates to:
  /// **'{phoneNumber} als legitim gemeldet'**
  String reportedAsLegitimate(String phoneNumber);

  /// Error reporting number
  ///
  /// In de, this message translates to:
  /// **'Fehler beim Melden: {error}'**
  String reportError(String error);

  /// Successfully reported as spam
  ///
  /// In de, this message translates to:
  /// **'{phoneNumber} als SPAM gemeldet'**
  String reportedAsSpam(String phoneNumber);

  /// Select spam category dialog title
  ///
  /// In de, this message translates to:
  /// **'SPAM-Kategorie wählen'**
  String get selectSpamCategory;

  /// Error deleting all calls
  ///
  /// In de, this message translates to:
  /// **'Fehler beim Löschen aller Anrufe'**
  String get errorDeletingAllCalls;

  /// Error deleting single call
  ///
  /// In de, this message translates to:
  /// **'Fehler beim Löschen des Anrufs'**
  String get errorDeletingCall;

  /// Not logged in - short version
  ///
  /// In de, this message translates to:
  /// **'Nicht angemeldet'**
  String get notLoggedInShort;

  /// Error opening PhoneBlock website
  ///
  /// In de, this message translates to:
  /// **'Fehler beim Öffnen von PhoneBlock.'**
  String get errorOpeningPhoneBlock;

  /// Permission not granted message
  ///
  /// In de, this message translates to:
  /// **'Berechtigung wurde nicht erteilt.'**
  String get permissionNotGranted;

  /// Setup wizard title
  ///
  /// In de, this message translates to:
  /// **'PhoneBlock Mobile - Einrichtung'**
  String get setupTitle;

  /// Welcome header
  ///
  /// In de, this message translates to:
  /// **'Willkommen'**
  String get welcome;

  /// Connect PhoneBlock account subtitle
  ///
  /// In de, this message translates to:
  /// **'PhoneBlock-Konto verbinden'**
  String get connectPhoneBlockAccount;

  /// Permissions header
  ///
  /// In de, this message translates to:
  /// **'Berechtigungen'**
  String get permissions;

  /// Allow call filtering subtitle
  ///
  /// In de, this message translates to:
  /// **'Anrufe filtern erlauben'**
  String get allowCallFiltering;

  /// Done header
  ///
  /// In de, this message translates to:
  /// **'Fertig'**
  String get done;

  /// Setup complete subtitle
  ///
  /// In de, this message translates to:
  /// **'Einrichtung abgeschlossen'**
  String get setupComplete;

  /// Minimum reports count label
  ///
  /// In de, this message translates to:
  /// **'Minimale Anzahl Meldungen'**
  String get minReportsCount;

  /// Description for calls blocked after reports
  ///
  /// In de, this message translates to:
  /// **'Anrufe werden ab {count} Meldungen blockiert'**
  String callsBlockedAfterReports(int count);

  /// Description for ranges blocked after reports
  ///
  /// In de, this message translates to:
  /// **'Bereiche werden ab {count} Meldungen blockiert'**
  String rangesBlockedAfterReports(int count);

  /// Welcome message in setup wizard
  ///
  /// In de, this message translates to:
  /// **'Willkommen bei PhoneBlock Mobile!\n\nDiese App hilft Ihnen, Spam-Anrufe automatisch zu blockieren. Dazu benötigen Sie ein kostenloses Konto bei PhoneBlock.net.\n\nVerbinden Sie Ihr PhoneBlock-Konto, um fortzufahren:'**
  String get welcomeMessage;

  /// Button to connect to PhoneBlock
  ///
  /// In de, this message translates to:
  /// **'Mit PhoneBlock verbinden'**
  String get connectToPhoneBlock;

  /// Status message when connected
  ///
  /// In de, this message translates to:
  /// **'Mit PhoneBlock verbunden'**
  String get connectedToPhoneBlock;

  /// Success message for account connection
  ///
  /// In de, this message translates to:
  /// **'✓ Konto erfolgreich verbunden'**
  String get accountConnectedSuccessfully;

  /// Permissions explanation message
  ///
  /// In de, this message translates to:
  /// **'Um Spam-Anrufe automatisch zu blockieren, benötigt PhoneBlock Mobile die Berechtigung, eingehende Anrufe zu prüfen.\n\nDiese Berechtigung ist erforderlich, damit die App funktioniert:'**
  String get permissionsMessage;

  /// Status when permission is granted
  ///
  /// In de, this message translates to:
  /// **'Berechtigung erteilt'**
  String get permissionGranted;

  /// Success message for permission granted
  ///
  /// In de, this message translates to:
  /// **'✓ Berechtigung erfolgreich erteilt'**
  String get permissionGrantedSuccessfully;

  /// Setup complete message
  ///
  /// In de, this message translates to:
  /// **'Einrichtung abgeschlossen!\n\nPhoneBlock Mobile ist jetzt bereit, Spam-Anrufe zu blockieren. Die App prüft automatisch eingehende Anrufe und blockiert bekannte Spam-Nummern basierend auf der PhoneBlock-Datenbank.\n\nDrücken Sie \"Fertig\", um zur Hauptansicht zu gelangen.'**
  String get setupCompleteMessage;

  /// Title for login verification screen
  ///
  /// In de, this message translates to:
  /// **'Überprüfe Login'**
  String get verifyingLoginTitle;

  /// Login success message
  ///
  /// In de, this message translates to:
  /// **'Login erfolgreich!'**
  String get loginSuccessMessage;

  /// Redirecting to setup message
  ///
  /// In de, this message translates to:
  /// **'Weiterleitung zur Einrichtung...'**
  String get redirectingToSetup;

  /// Token verification failed message
  ///
  /// In de, this message translates to:
  /// **'Token-Überprüfung fehlgeschlagen: {error}'**
  String tokenVerificationFailed(String error);

  /// Back to setup button
  ///
  /// In de, this message translates to:
  /// **'Zurück zur Einrichtung'**
  String get backToSetup;

  /// Token being verified message
  ///
  /// In de, this message translates to:
  /// **'Token wird überprüft...'**
  String get tokenBeingVerified;

  /// Failed to open PhoneBlock error
  ///
  /// In de, this message translates to:
  /// **'PhoneBlock konnte nicht geöffnet werden.'**
  String get failedToOpenPhoneBlock;

  /// Legitimate rating label
  ///
  /// In de, this message translates to:
  /// **'Legitim'**
  String get ratingLegitimate;

  /// Advertising rating label
  ///
  /// In de, this message translates to:
  /// **'Werbung'**
  String get ratingAdvertising;

  /// SPAM rating label
  ///
  /// In de, this message translates to:
  /// **'SPAM'**
  String get ratingSpam;

  /// Ping call rating label
  ///
  /// In de, this message translates to:
  /// **'Ping-Anruf'**
  String get ratingPingCall;

  /// Gamble/Prize draw rating label
  ///
  /// In de, this message translates to:
  /// **'Gewinnspiel'**
  String get ratingGamble;

  /// Fraud rating label
  ///
  /// In de, this message translates to:
  /// **'Betrug'**
  String get ratingFraud;

  /// Poll/Survey rating label
  ///
  /// In de, this message translates to:
  /// **'Umfrage'**
  String get ratingPoll;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['de', 'en'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'de':
      return AppLocalizationsDe();
    case 'en':
      return AppLocalizationsEn();
  }

  throw FlutterError(
      'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
      'an issue with the localizations generation tool. Please file an issue '
      'on GitHub with a reproducible sample app and the gen-l10n configuration '
      'that was used.');
}
