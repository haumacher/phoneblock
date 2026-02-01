import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_ar.dart';
import 'app_localizations_da.dart';
import 'app_localizations_de.dart';
import 'app_localizations_el.dart';
import 'app_localizations_en.dart';
import 'app_localizations_es.dart';
import 'app_localizations_fr.dart';
import 'app_localizations_it.dart';
import 'app_localizations_nb.dart';
import 'app_localizations_nl.dart';
import 'app_localizations_pl.dart';
import 'app_localizations_sv.dart';
import 'app_localizations_uk.dart';
import 'app_localizations_zh.dart';

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
    Locale('ar'),
    Locale('da'),
    Locale('de'),
    Locale('el'),
    Locale('en'),
    Locale('es'),
    Locale('fr'),
    Locale('it'),
    Locale('nb'),
    Locale('nl'),
    Locale('pl'),
    Locale('sv'),
    Locale('uk'),
    Locale('zh')
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

  /// Error message when no login token is received
  ///
  /// In de, this message translates to:
  /// **'Kein Login-Token empfangen.'**
  String get noLoginTokenReceived;

  /// Success message when setting is saved
  ///
  /// In de, this message translates to:
  /// **'Einstellung gespeichert'**
  String get settingSaved;

  /// Error message when saving fails
  ///
  /// In de, this message translates to:
  /// **'Fehler beim Speichern'**
  String get errorSaving;

  /// Title for rating screen
  ///
  /// In de, this message translates to:
  /// **'{phoneNumber} bewerten'**
  String ratePhoneNumber(String phoneNumber);

  /// Number of reports for a phone number
  ///
  /// In de, this message translates to:
  /// **'{count, plural, =1{1 Beschwerde} other{{count} Beschwerden}}'**
  String reportsCount(int count);

  /// Number of range reports for a phone number (aggregated from similar numbers)
  ///
  /// In de, this message translates to:
  /// **'{count, plural, =1{1 Beschwerde im Nummernbereich} other{{count} Beschwerden im Nummernbereich}}'**
  String rangeReportsCount(int count);

  /// Number of legitimate reports for a phone number
  ///
  /// In de, this message translates to:
  /// **'{count, plural, =1{1 Legitim-Meldung} other{{count} Legitim-Meldungen}}'**
  String legitimateReportsCount(int count);

  /// No reports available for this number
  ///
  /// In de, this message translates to:
  /// **'Keine Meldungen'**
  String get noReports;

  /// Today with time
  ///
  /// In de, this message translates to:
  /// **'Heute, {time}'**
  String todayTime(String time);

  /// Yesterday with time
  ///
  /// In de, this message translates to:
  /// **'Gestern, {time}'**
  String yesterdayTime(String time);

  /// Call history retention setting title
  ///
  /// In de, this message translates to:
  /// **'Anrufverlauf-Speicherung'**
  String get callHistoryRetention;

  /// Retention period description with days
  ///
  /// In de, this message translates to:
  /// **'{days, plural, =1{Anrufe 1 Tag behalten} other{Anrufe {days} Tage behalten}}'**
  String retentionPeriodDescription(int days);

  /// Keep all calls option
  ///
  /// In de, this message translates to:
  /// **'Alle Anrufe behalten'**
  String get retentionInfinite;

  /// Number of days for retention period
  ///
  /// In de, this message translates to:
  /// **'{days, plural, =1{1 Tag} other{{days} Tage}}'**
  String retentionDays(int days);

  /// Infinite retention option
  ///
  /// In de, this message translates to:
  /// **'Unbegrenzt'**
  String get retentionInfiniteOption;

  /// Add comment dialog title for spam
  ///
  /// In de, this message translates to:
  /// **'Kommentar hinzufügen (Optional)'**
  String get addCommentSpam;

  /// Comment text field hint for spam
  ///
  /// In de, this message translates to:
  /// **'Warum ist das Spam? Worum ging es bei dem Anruf? Bitte höflich bleiben.'**
  String get commentHintSpam;

  /// Add comment dialog title for legitimate
  ///
  /// In de, this message translates to:
  /// **'Kommentar hinzufügen (Optional)'**
  String get addCommentLegitimate;

  /// Comment text field hint for legitimate
  ///
  /// In de, this message translates to:
  /// **'Warum ist das legitim? Wer hat Sie angerufen? Bitte höflich bleiben.'**
  String get commentHintLegitimate;

  /// Server settings menu item
  ///
  /// In de, this message translates to:
  /// **'Server-Einstellungen'**
  String get serverSettings;

  /// Description for server settings
  ///
  /// In de, this message translates to:
  /// **'Verwalten Sie Ihre PhoneBlock-Kontoeinstellungen'**
  String get serverSettingsDescription;

  /// Search number button tooltip
  ///
  /// In de, this message translates to:
  /// **'Nummer suchen'**
  String get searchNumber;

  /// Search phone number dialog title
  ///
  /// In de, this message translates to:
  /// **'Telefonnummer suchen'**
  String get searchPhoneNumber;

  /// Enter phone number label
  ///
  /// In de, this message translates to:
  /// **'Telefonnummer eingeben'**
  String get enterPhoneNumber;

  /// Phone number hint text
  ///
  /// In de, this message translates to:
  /// **'z.B. +49 123 456789'**
  String get phoneNumberHint;

  /// Search button
  ///
  /// In de, this message translates to:
  /// **'Suchen'**
  String get search;

  /// Error message for invalid phone number
  ///
  /// In de, this message translates to:
  /// **'Bitte geben Sie eine gültige Telefonnummer ein'**
  String get invalidPhoneNumber;

  /// Blacklist screen title
  ///
  /// In de, this message translates to:
  /// **'Blacklist'**
  String get blacklistTitle;

  /// Blacklist description in settings
  ///
  /// In de, this message translates to:
  /// **'Von Ihnen blockierte Nummern'**
  String get blacklistDescription;

  /// Whitelist screen title
  ///
  /// In de, this message translates to:
  /// **'Whitelist'**
  String get whitelistTitle;

  /// Whitelist description in settings
  ///
  /// In de, this message translates to:
  /// **'Von Ihnen als legitim markierte Nummern'**
  String get whitelistDescription;

  /// Empty blacklist message
  ///
  /// In de, this message translates to:
  /// **'Ihre Blacklist ist leer'**
  String get blacklistEmpty;

  /// Empty whitelist message
  ///
  /// In de, this message translates to:
  /// **'Ihre Whitelist ist leer'**
  String get whitelistEmpty;

  /// Help text explaining how to add numbers to blacklist
  ///
  /// In de, this message translates to:
  /// **'Fügen Sie Nummern hinzu, indem Sie unerwünschte Anrufe als Spam melden.'**
  String get blacklistEmptyHelp;

  /// Help text explaining how to add numbers to whitelist
  ///
  /// In de, this message translates to:
  /// **'Fügen Sie Nummern hinzu, indem Sie blockierte Anrufe als legitim melden.'**
  String get whitelistEmptyHelp;

  /// Error loading list message
  ///
  /// In de, this message translates to:
  /// **'Fehler beim Laden der Liste'**
  String get errorLoadingList;

  /// Success message when number is removed
  ///
  /// In de, this message translates to:
  /// **'Nummer entfernt'**
  String get numberRemovedFromList;

  /// Error removing number message
  ///
  /// In de, this message translates to:
  /// **'Fehler beim Entfernen der Nummer'**
  String get errorRemovingNumber;

  /// Confirm removal dialog title
  ///
  /// In de, this message translates to:
  /// **'Entfernen bestätigen'**
  String get confirmRemoval;

  /// Confirm remove from blacklist message
  ///
  /// In de, this message translates to:
  /// **'{phone} von der Blacklist entfernen?'**
  String confirmRemoveFromBlacklist(Object phone);

  /// Confirm remove from whitelist message
  ///
  /// In de, this message translates to:
  /// **'{phone} von der Whitelist entfernen?'**
  String confirmRemoveFromWhitelist(Object phone);

  /// Remove button text
  ///
  /// In de, this message translates to:
  /// **'Entfernen'**
  String get remove;

  /// Retry button text
  ///
  /// In de, this message translates to:
  /// **'Erneut versuchen'**
  String get retry;

  /// No description provided for @editComment.
  ///
  /// In de, this message translates to:
  /// **'Kommentar bearbeiten'**
  String get editComment;

  /// No description provided for @commentLabel.
  ///
  /// In de, this message translates to:
  /// **'Kommentar'**
  String get commentLabel;

  /// No description provided for @commentHint.
  ///
  /// In de, this message translates to:
  /// **'Notiz zu dieser Nummer hinzufügen'**
  String get commentHint;

  /// No description provided for @save.
  ///
  /// In de, this message translates to:
  /// **'Speichern'**
  String get save;

  /// No description provided for @commentUpdated.
  ///
  /// In de, this message translates to:
  /// **'Kommentar aktualisiert'**
  String get commentUpdated;

  /// No description provided for @errorUpdatingComment.
  ///
  /// In de, this message translates to:
  /// **'Fehler beim Aktualisieren des Kommentars'**
  String get errorUpdatingComment;

  /// Appearance section header
  ///
  /// In de, this message translates to:
  /// **'Erscheinungsbild'**
  String get appearance;

  /// Theme mode setting label
  ///
  /// In de, this message translates to:
  /// **'Design'**
  String get themeMode;

  /// Theme mode description
  ///
  /// In de, this message translates to:
  /// **'Helles oder dunkles Design wählen'**
  String get themeModeDescription;

  /// System theme mode option
  ///
  /// In de, this message translates to:
  /// **'Systemstandard'**
  String get themeModeSystem;

  /// Light theme mode option
  ///
  /// In de, this message translates to:
  /// **'Hell'**
  String get themeModeLight;

  /// Dark theme mode option
  ///
  /// In de, this message translates to:
  /// **'Dunkel'**
  String get themeModeDark;

  /// Experimental features section header
  ///
  /// In de, this message translates to:
  /// **'Experimentelle Funktionen'**
  String get experimentalFeatures;

  /// Answerbot feature toggle title
  ///
  /// In de, this message translates to:
  /// **'Anrufbeantworter (Answerbot)'**
  String get answerbotFeature;

  /// Answerbot feature toggle description
  ///
  /// In de, this message translates to:
  /// **'Experimentell: SPAM-Anrufbeantworter für die Fritz!Box in der App verwalten'**
  String get answerbotFeatureDescription;

  /// Answerbot menu item title
  ///
  /// In de, this message translates to:
  /// **'Anrufbeantworter'**
  String get answerbotMenuTitle;

  /// Answerbot menu item description
  ///
  /// In de, this message translates to:
  /// **'SPAM-Anrufbeantworter verwalten'**
  String get answerbotMenuDescription;

  /// Label for calls that weren't blocked but have spam votes
  ///
  /// In de, this message translates to:
  /// **'Verdächtig: {rating}'**
  String potentialSpamLabel(String rating);

  /// Statistics section header
  ///
  /// In de, this message translates to:
  /// **'Statistik'**
  String get statistics;

  /// Label for total blocked calls count
  ///
  /// In de, this message translates to:
  /// **'Blockierte Anrufe'**
  String get blockedCallsCount;

  /// Label for total suspicious calls count
  ///
  /// In de, this message translates to:
  /// **'Verdächtige Anrufe'**
  String get suspiciousCallsCount;

  /// Fritz!Box menu title
  ///
  /// In de, this message translates to:
  /// **'Fritz!Box'**
  String get fritzboxTitle;

  /// Fritz!Box connected status
  ///
  /// In de, this message translates to:
  /// **'Verbunden'**
  String get fritzboxConnected;

  /// Fritz!Box offline status
  ///
  /// In de, this message translates to:
  /// **'Nicht erreichbar'**
  String get fritzboxOffline;

  /// Fritz!Box error status
  ///
  /// In de, this message translates to:
  /// **'Verbindungsfehler'**
  String get fritzboxError;

  /// Fritz!Box not configured - short
  ///
  /// In de, this message translates to:
  /// **'Nicht eingerichtet'**
  String get fritzboxNotConfiguredShort;

  /// Fritz!Box not configured title
  ///
  /// In de, this message translates to:
  /// **'Keine Fritz!Box eingerichtet'**
  String get fritzboxNotConfigured;

  /// Fritz!Box not configured description
  ///
  /// In de, this message translates to:
  /// **'Verbinden Sie Ihre Fritz!Box, um Anrufe aus Ihrem Festnetz zu sehen.'**
  String get fritzboxNotConfiguredDescription;

  /// Connect Fritz!Box button
  ///
  /// In de, this message translates to:
  /// **'Fritz!Box verbinden'**
  String get fritzboxConnect;

  /// Disconnect Fritz!Box button
  ///
  /// In de, this message translates to:
  /// **'Fritz!Box trennen'**
  String get fritzboxDisconnect;

  /// Disconnect confirmation title
  ///
  /// In de, this message translates to:
  /// **'Fritz!Box trennen?'**
  String get fritzboxDisconnectTitle;

  /// Disconnect confirmation message
  ///
  /// In de, this message translates to:
  /// **'Die gespeicherten Anrufe und Zugangsdaten werden gelöscht.'**
  String get fritzboxDisconnectMessage;

  /// Sync now button
  ///
  /// In de, this message translates to:
  /// **'Jetzt synchronisieren'**
  String get fritzboxSyncNow;

  /// Sync description
  ///
  /// In de, this message translates to:
  /// **'Anrufliste von der Fritz!Box abrufen'**
  String get fritzboxSyncDescription;

  /// Sync complete message
  ///
  /// In de, this message translates to:
  /// **'{count, plural, =0{Keine neuen Anrufe} =1{1 neuer Anruf synchronisiert} other{{count} neue Anrufe synchronisiert}}'**
  String fritzboxSyncComplete(int count);

  /// Sync error message
  ///
  /// In de, this message translates to:
  /// **'Fehler beim Synchronisieren'**
  String get fritzboxSyncError;

  /// Fritz!OS version label
  ///
  /// In de, this message translates to:
  /// **'FRITZ!OS Version'**
  String get fritzboxVersion;

  /// Fritz!Box host address label
  ///
  /// In de, this message translates to:
  /// **'Adresse'**
  String get fritzboxHost;

  /// Cached calls count label
  ///
  /// In de, this message translates to:
  /// **'Gespeicherte Anrufe'**
  String get fritzboxCachedCalls;

  /// Last sync label
  ///
  /// In de, this message translates to:
  /// **'Letzte Synchronisierung'**
  String get fritzboxLastSync;

  /// Just now time label
  ///
  /// In de, this message translates to:
  /// **'Gerade eben'**
  String get fritzboxJustNow;

  /// Minutes ago label
  ///
  /// In de, this message translates to:
  /// **'{count, plural, =1{Vor 1 Minute} other{Vor {count} Minuten}}'**
  String fritzboxMinutesAgo(int count);

  /// Hours ago label
  ///
  /// In de, this message translates to:
  /// **'{count, plural, =1{Vor 1 Stunde} other{Vor {count} Stunden}}'**
  String fritzboxHoursAgo(int count);

  /// Wizard title
  ///
  /// In de, this message translates to:
  /// **'Fritz!Box verbinden'**
  String get fritzboxWizardTitle;

  /// Detection step title
  ///
  /// In de, this message translates to:
  /// **'Fritz!Box finden'**
  String get fritzboxStepDetection;

  /// Detection step subtitle
  ///
  /// In de, this message translates to:
  /// **'Automatische Suche im Netzwerk'**
  String get fritzboxStepDetectionSubtitle;

  /// Login step title
  ///
  /// In de, this message translates to:
  /// **'Anmelden'**
  String get fritzboxStepLogin;

  /// Login step subtitle
  ///
  /// In de, this message translates to:
  /// **'Zugangsdaten eingeben'**
  String get fritzboxStepLoginSubtitle;

  /// Searching message
  ///
  /// In de, this message translates to:
  /// **'Suche nach Fritz!Box...'**
  String get fritzboxSearching;

  /// Not found message
  ///
  /// In de, this message translates to:
  /// **'Fritz!Box nicht gefunden'**
  String get fritzboxNotFound;

  /// Not found description
  ///
  /// In de, this message translates to:
  /// **'Die Fritz!Box konnte nicht automatisch gefunden werden. Bitte geben Sie die Adresse manuell ein.'**
  String get fritzboxNotFoundDescription;

  /// Host field label
  ///
  /// In de, this message translates to:
  /// **'Fritz!Box Adresse'**
  String get fritzboxHostLabel;

  /// Retry search button
  ///
  /// In de, this message translates to:
  /// **'Erneut suchen'**
  String get fritzboxRetrySearch;

  /// Manual connect button
  ///
  /// In de, this message translates to:
  /// **'Verbinden'**
  String get fritzboxManualConnect;

  /// Login description
  ///
  /// In de, this message translates to:
  /// **'Geben Sie Ihre Fritz!Box Zugangsdaten ein. Sie finden diese in der Fritz!Box Benutzeroberfläche unter System > Fritz!Box-Benutzer.'**
  String get fritzboxLoginDescription;

  /// Username field label
  ///
  /// In de, this message translates to:
  /// **'Benutzername'**
  String get fritzboxUsernameLabel;

  /// Username field hint
  ///
  /// In de, this message translates to:
  /// **'Leer für Standardbenutzer'**
  String get fritzboxUsernameHint;

  /// Password field label
  ///
  /// In de, this message translates to:
  /// **'Kennwort'**
  String get fritzboxPasswordLabel;

  /// Credentials security note
  ///
  /// In de, this message translates to:
  /// **'Ihre Zugangsdaten werden sicher auf Ihrem Gerät gespeichert.'**
  String get fritzboxCredentialsNote;

  /// Test and save button
  ///
  /// In de, this message translates to:
  /// **'Testen und Speichern'**
  String get fritzboxTestAndSave;

  /// Connection failed message
  ///
  /// In de, this message translates to:
  /// **'Verbindung fehlgeschlagen. Bitte überprüfen Sie die Zugangsdaten.'**
  String get fritzboxConnectionFailed;

  /// Fill all fields message
  ///
  /// In de, this message translates to:
  /// **'Bitte füllen Sie alle Felder aus.'**
  String get fritzboxFillAllFields;

  /// Offline banner message
  ///
  /// In de, this message translates to:
  /// **'Fritz!Box nicht erreichbar - zeige gespeicherte Anrufe'**
  String get fritzboxOfflineBanner;

  /// Mobile source label
  ///
  /// In de, this message translates to:
  /// **'Mobil'**
  String get sourceMobile;

  /// Fritz!Box source label
  ///
  /// In de, this message translates to:
  /// **'Fritz!Box'**
  String get sourceFritzbox;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) => <String>[
        'ar',
        'da',
        'de',
        'el',
        'en',
        'es',
        'fr',
        'it',
        'nb',
        'nl',
        'pl',
        'sv',
        'uk',
        'zh'
      ].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'ar':
      return AppLocalizationsAr();
    case 'da':
      return AppLocalizationsDa();
    case 'de':
      return AppLocalizationsDe();
    case 'el':
      return AppLocalizationsEl();
    case 'en':
      return AppLocalizationsEn();
    case 'es':
      return AppLocalizationsEs();
    case 'fr':
      return AppLocalizationsFr();
    case 'it':
      return AppLocalizationsIt();
    case 'nb':
      return AppLocalizationsNb();
    case 'nl':
      return AppLocalizationsNl();
    case 'pl':
      return AppLocalizationsPl();
    case 'sv':
      return AppLocalizationsSv();
    case 'uk':
      return AppLocalizationsUk();
    case 'zh':
      return AppLocalizationsZh();
  }

  throw FlutterError(
      'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
      'an issue with the localizations generation tool. Please file an issue '
      'on GitHub with a reproducible sample app and the gen-l10n configuration '
      'that was used.');
}
