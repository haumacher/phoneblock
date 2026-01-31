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
  /// **'PhoneBlock Anrufbeantworter'**
  String get appTitle;

  /// Title for answerbot list screen
  ///
  /// In de, this message translates to:
  /// **'Deine Anrufbeantworter'**
  String get yourAnswerbots;

  /// Login required message
  ///
  /// In de, this message translates to:
  /// **'Anmeldung erforderlich'**
  String get loginRequired;

  /// Login button label
  ///
  /// In de, this message translates to:
  /// **'Login'**
  String get login;

  /// Loading message
  ///
  /// In de, this message translates to:
  /// **'Lade Daten...'**
  String get loadingData;

  /// Refreshing message
  ///
  /// In de, this message translates to:
  /// **'Aktualisiere Daten...'**
  String get refreshingData;

  /// Message when no answerbots exist
  ///
  /// In de, this message translates to:
  /// **'Du hast noch keinen Anrufbeantworter, klicke den Plus-Knopf unten, um einen PhoneBlock-Anrufbeantworter anzulegen.'**
  String get noAnswerbotsYet;

  /// Tooltip for create answerbot button
  ///
  /// In de, this message translates to:
  /// **'Anrufbeantworter anlegen'**
  String get createAnswerbot;

  /// Answerbot name with username
  ///
  /// In de, this message translates to:
  /// **'Anrufbeantworter {userName}'**
  String answerbotName(String userName);

  /// Answerbot statistics
  ///
  /// In de, this message translates to:
  /// **'{newCalls} neue Anrufe, {callsAccepted} Anrufe, {talkTimeSeconds} s Gesprächszeit gesamt'**
  String answerbotStats(int newCalls, int talkTimeSeconds, int callsAccepted);

  /// Status: active
  ///
  /// In de, this message translates to:
  /// **'aktiv'**
  String get statusActive;

  /// Status: connecting
  ///
  /// In de, this message translates to:
  /// **'verbinde...'**
  String get statusConnecting;

  /// Status: disabled
  ///
  /// In de, this message translates to:
  /// **'ausgeschaltet'**
  String get statusDisabled;

  /// Status: incomplete setup
  ///
  /// In de, this message translates to:
  /// **'unvollständig'**
  String get statusIncomplete;

  /// Delete answerbot button
  ///
  /// In de, this message translates to:
  /// **'Anrufbeantworter löschen'**
  String get deleteAnswerbot;

  /// Enabled label
  ///
  /// In de, this message translates to:
  /// **'Aktiviert'**
  String get enabled;

  /// Minimum votes setting
  ///
  /// In de, this message translates to:
  /// **'Mindest-Stimmen'**
  String get minVotes;

  /// Minimum votes description
  ///
  /// In de, this message translates to:
  /// **'Wie viele Stimmen muss eine Nummer mindestens haben, damit sie vom Anrufbeantworter angenommen wird?'**
  String get minVotesDescription;

  /// 2 votes option
  ///
  /// In de, this message translates to:
  /// **'2 - sofort sperren'**
  String get minVotes2;

  /// 4 votes option
  ///
  /// In de, this message translates to:
  /// **'4 - Bestätigung abwarten'**
  String get minVotes4;

  /// 10 votes option
  ///
  /// In de, this message translates to:
  /// **'10 - erst wenn sicher'**
  String get minVotes10;

  /// 100 votes option
  ///
  /// In de, this message translates to:
  /// **'100 - nur Top-Spammer'**
  String get minVotes100;

  /// Warning when trying to change settings while enabled
  ///
  /// In de, this message translates to:
  /// **'Kann nur bei ausgeschaltetem Anrufbeantworter geändert werden.'**
  String get cannotChangeWhileEnabled;

  /// Save settings button
  ///
  /// In de, this message translates to:
  /// **'Einstellungen speichern'**
  String get saveSettings;

  /// Retention period label
  ///
  /// In de, this message translates to:
  /// **'Aufbewahrungszeit'**
  String get retentionPeriod;

  /// Retention period description
  ///
  /// In de, this message translates to:
  /// **'Wie lange sollen Anrufe aufbewahrt werden?'**
  String get retentionPeriodDescription;

  /// Never delete option
  ///
  /// In de, this message translates to:
  /// **'Niemals löschen'**
  String get retentionNever;

  /// Delete after 1 week
  ///
  /// In de, this message translates to:
  /// **'Nach 1 Woche löschen'**
  String get retentionWeek;

  /// Delete after 1 month
  ///
  /// In de, this message translates to:
  /// **'Nach 1 Monat löschen'**
  String get retentionMonth;

  /// Delete after 3 months
  ///
  /// In de, this message translates to:
  /// **'Nach 3 Monaten löschen'**
  String get retentionQuarter;

  /// Delete after 1 year
  ///
  /// In de, this message translates to:
  /// **'Nach 1 Jahr löschen'**
  String get retentionYear;

  /// Save retention settings button
  ///
  /// In de, this message translates to:
  /// **'Aufbewahrungseinstellungen speichern'**
  String get saveRetentionSettings;

  /// Show help tooltip
  ///
  /// In de, this message translates to:
  /// **'Hilfe anzeigen'**
  String get showHelp;

  /// New answerbot title
  ///
  /// In de, this message translates to:
  /// **'Neuer Anrufbeantworter'**
  String get newAnswerbot;

  /// Use PhoneBlock DynDNS checkbox
  ///
  /// In de, this message translates to:
  /// **'PhoneBlock-DynDNS benutzen'**
  String get usePhoneBlockDynDns;

  /// DynDNS description
  ///
  /// In de, this message translates to:
  /// **'PhoneBlock muss die Internet-Adresse Deiner Fritz!Box kennen, um Anrufe annehmen zu können.'**
  String get dynDnsDescription;

  /// Setup PhoneBlock DynDNS button
  ///
  /// In de, this message translates to:
  /// **'PhoneBlock-DynDNS einrichten'**
  String get setupPhoneBlockDynDns;

  /// Domain name label
  ///
  /// In de, this message translates to:
  /// **'Domain-Name'**
  String get domainName;

  /// Domain name input hint
  ///
  /// In de, this message translates to:
  /// **'Gib den Domain-Namen Deiner Fritz!Box an. Wenn Deine Fritz!Box noch keinen Domain-Namen hat, aktiviere PhoneBlock-DynDNS.'**
  String get domainNameHint;

  /// Check domain name button
  ///
  /// In de, this message translates to:
  /// **'Domainnamen überprüfen'**
  String get checkDomainName;

  /// Setup DynDNS title
  ///
  /// In de, this message translates to:
  /// **'DynDNS einrichten'**
  String get setupDynDns;

  /// DynDNS setup instructions
  ///
  /// In de, this message translates to:
  /// **'Öffne in Deinen Fritz!Box-Einstellungen die Seite die Seite Internet > Freigaben > DynDNS und trage dort die folgenden Werte ein:'**
  String get dynDnsInstructions;

  /// Check DynDNS button
  ///
  /// In de, this message translates to:
  /// **'DynDNS überprüfen'**
  String get checkDynDns;

  /// Create answerbot title
  ///
  /// In de, this message translates to:
  /// **'Anrufbeantworter erstellen'**
  String get createAnswerbotTitle;

  /// Register answerbot button
  ///
  /// In de, this message translates to:
  /// **'Anrufbeantworter anmelden'**
  String get registerAnswerbot;

  /// Answerbot registered title
  ///
  /// In de, this message translates to:
  /// **'Anrufbeantworter angemeldet'**
  String get answerbotRegistered;

  /// Close button
  ///
  /// In de, this message translates to:
  /// **'Schließen'**
  String get close;

  /// Error title
  ///
  /// In de, this message translates to:
  /// **'Fehler'**
  String get error;

  /// Cannot load information error
  ///
  /// In de, this message translates to:
  /// **'Informationen können nicht abgerufen werden (Fehler {statusCode}): {message}'**
  String cannotLoadInfo(String message, int statusCode);

  /// Wrong content type error
  ///
  /// In de, this message translates to:
  /// **'Informationen können nicht abgerufen werden (Content-Type: {contentType}).'**
  String wrongContentType(String contentType);

  /// Call list title
  ///
  /// In de, this message translates to:
  /// **'Anrufliste'**
  String get callList;

  /// Clear call list button
  ///
  /// In de, this message translates to:
  /// **'Anrufliste löschen'**
  String get clearCallList;

  /// No calls message
  ///
  /// In de, this message translates to:
  /// **'Noch keine Anrufe'**
  String get noCalls;

  /// Answerbot label
  ///
  /// In de, this message translates to:
  /// **'Anrufbeantworter'**
  String get answerbot;

  /// Answerbot settings group title
  ///
  /// In de, this message translates to:
  /// **'Anrufbeantworter-Einstellungen'**
  String get answerbotSettings;

  /// Minimum confidence label
  ///
  /// In de, this message translates to:
  /// **'Mindestkonfidenz'**
  String get minConfidence;

  /// Minimum confidence help text
  ///
  /// In de, this message translates to:
  /// **'Wie viele Beschwerden notwendig sind, damit eine Nummer durch den Anrufbeantworter abgefangen wird.'**
  String get minConfidenceHelp;

  /// Block number ranges label
  ///
  /// In de, this message translates to:
  /// **'Nummernbereiche sperren'**
  String get blockNumberRanges;

  /// Block number ranges help text
  ///
  /// In de, this message translates to:
  /// **'Nimmt das Gespräch auch für einen Nummer an, die selbst noch nicht als SPAM bekannt ist, wenn die Vermutung naheliegt, dass die Nummer zu einem Anlagenanschluss gehört, von dem SPAM ausgeht.'**
  String get blockNumberRangesHelp;

  /// Prefer IPv4 label
  ///
  /// In de, this message translates to:
  /// **'IPv4 Kommunikation bevorzugen'**
  String get preferIPv4;

  /// Prefer IPv4 help text
  ///
  /// In de, this message translates to:
  /// **'Wenn verfügbar wird die Kommunikation mit dem Anrufbeantworter über IPv4 abgewickelt. Es scheint Telefonanschlüsse zu geben, bei denen eine Sprachverbindung über IPv6 nicht möglich ist, obwohl eine IPv6 Adresse verfügbar ist.'**
  String get preferIPv4Help;

  /// Call retention group title
  ///
  /// In de, this message translates to:
  /// **'Anruf-Aufbewahrung'**
  String get callRetention;

  /// Automatic deletion label
  ///
  /// In de, this message translates to:
  /// **'Automatische Löschung'**
  String get automaticDeletion;

  /// Automatic deletion help text
  ///
  /// In de, this message translates to:
  /// **'Nach welcher Zeit sollen alte Anrufprotokolle automatisch gelöscht werden? \'Niemals löschen\' deaktiviert die automatische Löschung.'**
  String get automaticDeletionHelp;

  /// DNS settings group title
  ///
  /// In de, this message translates to:
  /// **'DNS Settings'**
  String get dnsSettings;

  /// DNS setting label
  ///
  /// In de, this message translates to:
  /// **'DNS-Einstellung'**
  String get dnsSetting;

  /// PhoneBlock DNS option
  ///
  /// In de, this message translates to:
  /// **'PhoneBlock-DNS'**
  String get phoneBlockDns;

  /// Other provider or domain option
  ///
  /// In de, this message translates to:
  /// **'Anderer Anbieter oder Domainname'**
  String get otherProviderOrDomain;

  /// DNS setting help text
  ///
  /// In de, this message translates to:
  /// **'Wie der Anrufbeantworter Deine Fritz!Box im Internet findet.'**
  String get dnsSettingHelp;

  /// Update URL label
  ///
  /// In de, this message translates to:
  /// **'Update-URL'**
  String get updateUrl;

  /// Update URL help text
  ///
  /// In de, this message translates to:
  /// **'Nutzername für die DynDNS-Freige in Deiner Fritz!Box.'**
  String get updateUrlHelp;

  /// Domain name help text
  ///
  /// In de, this message translates to:
  /// **'Name den Deine Fritz!Box im Internet erhält.'**
  String get domainNameHelp;

  /// DynDNS username label
  ///
  /// In de, this message translates to:
  /// **'DynDNS-Benutzername'**
  String get dyndnsUsername;

  /// DynDNS username help text
  ///
  /// In de, this message translates to:
  /// **'Nutzername für die DynDNS-Freige in Deiner Fritz!Box.'**
  String get dyndnsUsernameHelp;

  /// DynDNS password label
  ///
  /// In de, this message translates to:
  /// **'DynDNS-Kennwort'**
  String get dyndnsPassword;

  /// DynDNS password help text
  ///
  /// In de, this message translates to:
  /// **'Das Kennwort, dass Du für die DynDNS-Freigabe verwenden musst.'**
  String get dyndnsPasswordHelp;

  /// Host label
  ///
  /// In de, this message translates to:
  /// **'Host'**
  String get host;

  /// Host help text
  ///
  /// In de, this message translates to:
  /// **'Der Host-Name, über den Deine Fritz!Box aus dem Internet erreichbar ist.'**
  String get hostHelp;

  /// SIP settings group title
  ///
  /// In de, this message translates to:
  /// **'SIP Settings'**
  String get sipSettings;

  /// User label
  ///
  /// In de, this message translates to:
  /// **'User'**
  String get user;

  /// User help text
  ///
  /// In de, this message translates to:
  /// **'Der Nutzername, der in der Fritz!Box für den Zugriff auf das Telefoniegerät eingerichtet sein muss.'**
  String get userHelp;

  /// Password label
  ///
  /// In de, this message translates to:
  /// **'Password'**
  String get password;

  /// Password help text
  ///
  /// In de, this message translates to:
  /// **'Das Passwort, dass für den Zugriff auf das Telefoniegerät in der Fritz!Box vergeben sein muss.'**
  String get passwordHelp;

  /// Saving settings progress message
  ///
  /// In de, this message translates to:
  /// **'Speichere Einstellungen...'**
  String get savingSettings;

  /// Error saving settings message
  ///
  /// In de, this message translates to:
  /// **'Fehler beim Speichern der Einstellungen.'**
  String get errorSavingSettings;

  /// Saving failed message
  ///
  /// In de, this message translates to:
  /// **'Speichern fehlgeschlagen: {message}'**
  String savingFailed(String message);

  /// Enable after saving failed message
  ///
  /// In de, this message translates to:
  /// **'Neu einschalten nach Speichern fehlgeschlagen'**
  String get enableAfterSavingFailed;

  /// Enabling answerbot progress message
  ///
  /// In de, this message translates to:
  /// **'Schalte Anrufbeantworter ein...'**
  String get enablingAnswerbot;

  /// Error enabling answerbot message
  ///
  /// In de, this message translates to:
  /// **'Fehler beim Einschalten des Anrufbeantworters.'**
  String get errorEnablingAnswerbot;

  /// Cannot enable message
  ///
  /// In de, this message translates to:
  /// **'Kann nicht einschalten: {message}'**
  String cannotEnable(String message);

  /// Enabling failed message
  ///
  /// In de, this message translates to:
  /// **'Einschalten des Anrufbeantworters fehlgeschlagen'**
  String get enablingFailed;

  /// Enabling failed with error message
  ///
  /// In de, this message translates to:
  /// **'Einschalten fehlgeschlagen: {message}'**
  String enablingFailedMessage(String message);

  /// Retrying with error message
  ///
  /// In de, this message translates to:
  /// **'{message} Versuche erneut...'**
  String retryingMessage(String message);

  /// Saving retention settings progress message
  ///
  /// In de, this message translates to:
  /// **'Speichere Aufbewahrungseinstellungen...'**
  String get savingRetentionSettings;

  /// Error saving retention settings message
  ///
  /// In de, this message translates to:
  /// **'Fehler beim Speichern der Aufbewahrungseinstellungen.'**
  String get errorSavingRetentionSettings;

  /// Automatic deletion disabled message
  ///
  /// In de, this message translates to:
  /// **'Automatische Löschung deaktiviert'**
  String get automaticDeletionDisabled;

  /// Retention settings saved message
  ///
  /// In de, this message translates to:
  /// **'Aufbewahrungseinstellungen gespeichert ({period})'**
  String retentionSettingsSaved(String period);

  /// One week period
  ///
  /// In de, this message translates to:
  /// **'1 Woche'**
  String get oneWeek;

  /// One month period
  ///
  /// In de, this message translates to:
  /// **'1 Monat'**
  String get oneMonth;

  /// Three months period
  ///
  /// In de, this message translates to:
  /// **'3 Monate'**
  String get threeMonths;

  /// One year period
  ///
  /// In de, this message translates to:
  /// **'1 Jahr'**
  String get oneYear;

  /// Never period
  ///
  /// In de, this message translates to:
  /// **'Niemals'**
  String get never;

  /// Delete answerbot confirmation message
  ///
  /// In de, this message translates to:
  /// **'Soll der Anrufbeantworter {userName} wirklich gelöscht werden?'**
  String deleteAnswerbotConfirm(String userName);

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

  /// Deletion failed title
  ///
  /// In de, this message translates to:
  /// **'Löschen Fehlgeschlagen'**
  String get deletionFailed;

  /// Answerbot could not be deleted message
  ///
  /// In de, this message translates to:
  /// **'Der Anrufbeantworter konnte nicht gelöscht werden'**
  String get answerbotCouldNotBeDeleted;

  /// Spam calls title
  ///
  /// In de, this message translates to:
  /// **'SPAM Anrufe'**
  String get spamCalls;

  /// Delete calls tooltip
  ///
  /// In de, this message translates to:
  /// **'Anrufe löschen'**
  String get deleteCalls;

  /// Deleting calls failed title
  ///
  /// In de, this message translates to:
  /// **'Löschen fehlgeschlagen'**
  String get deletingCallsFailed;

  /// Delete request failed message
  ///
  /// In de, this message translates to:
  /// **'Die Löschanforderung konnte nicht bearbeitet werden.'**
  String get deleteRequestFailed;

  /// Cannot retrieve calls error
  ///
  /// In de, this message translates to:
  /// **'Anrufe können nicht abgerufen werden (Fehler {statusCode}): {message}'**
  String cannotRetrieveCalls(String message, int statusCode);

  /// No new calls message
  ///
  /// In de, this message translates to:
  /// **'Keine neuen Anrufe.'**
  String get noNewCalls;

  /// Call duration
  ///
  /// In de, this message translates to:
  /// **'Dauer {seconds} s'**
  String duration(int seconds);

  /// Today with time
  ///
  /// In de, this message translates to:
  /// **'Heute {time}'**
  String today(String time);

  /// Yesterday with time
  ///
  /// In de, this message translates to:
  /// **'Gestern {time}'**
  String yesterday(String time);

  /// Long DynDNS description
  ///
  /// In de, this message translates to:
  /// **'PhoneBlock muss die Internet-Adresse Deiner Fritz!Box kennen, um den Anrufbeantworter an Deiner Fritz!Box anmelden zu können. Wenn Du schon MyFRITZ! oder einen anderen DynDNS-Anbieter eingerichtet hast, kannst Du diesen Domain-Namen verwenden. Wenn nicht kannst Du ganz einfach DynDNS von PhoneBlock einrichten, aktiviere dann diesen Schalter.'**
  String get dynDnsDescriptionLong;

  /// Setup PhoneBlock DynDNS snackbar message
  ///
  /// In de, this message translates to:
  /// **'Richte PhoneBlock DynDNS ein.'**
  String get setupPhoneBlockDynDnsSnackbar;

  /// Setup failed title
  ///
  /// In de, this message translates to:
  /// **'Einrichtung Fehlgeschlagen'**
  String get setupFailed;

  /// Cannot setup DynDNS message
  ///
  /// In de, this message translates to:
  /// **'DynDNS kann nicht eingerichtet werden: {message}'**
  String cannotSetupDynDns(String message);

  /// Domain name label (lowercase)
  ///
  /// In de, this message translates to:
  /// **'Domainname'**
  String get domainname;

  /// Long domain name hint
  ///
  /// In de, this message translates to:
  /// **'Domainname Deiner Fritz!Box (entweder MyFRITZ!-Adresse, oder DynDNS Domainname)'**
  String get domainNameHintLong;

  /// Input cannot be empty validation
  ///
  /// In de, this message translates to:
  /// **'Eingabe darf nicht leer sein.'**
  String get inputCannotBeEmpty;

  /// Invalid domain name validation
  ///
  /// In de, this message translates to:
  /// **'Kein gültiger Domain-Name.'**
  String get invalidDomainName;

  /// Domain name too long validation
  ///
  /// In de, this message translates to:
  /// **'Der Domain-Name ist zu lang.'**
  String get domainNameTooLong;

  /// Extended domain name hint
  ///
  /// In de, this message translates to:
  /// **'Gib den Domain-Namen Deiner Fritz!Box an. Wenn Deine Fritz!Box noch keinen Domain-Namen hat, aktiviere PhoneBlock-DynDNS. Den Domain-Namen Deiner Deiner Fritz!Box findest Du unter (Unter Internet > Freigaben > DynDNS). Alternativ kannst Du auch die MyFRITZ!-Adresse angeben (Internet > MyFRITZ!-Konto), z.B. z4z...l4n.myfritz.net.'**
  String get domainNameHintExtended;

  /// Checking domain name snackbar
  ///
  /// In de, this message translates to:
  /// **'Überprüfe Domainnamen.'**
  String get checkingDomainName;

  /// Domain name not accepted message
  ///
  /// In de, this message translates to:
  /// **'Domainname wurde nicht akzeptiert: {message}'**
  String domainNameNotAccepted(String message);

  /// Long DynDNS instructions
  ///
  /// In de, this message translates to:
  /// **'Öffne in Deinen Fritz!Box-Einstellungen die Seite die Internet > Freigaben > DynDNS und trage die hier angegebenen Informationen ein.'**
  String get dynDnsInstructionsLong;

  /// Update URL help text (detailed)
  ///
  /// In de, this message translates to:
  /// **'Die URL, die Deine Fritz!Box aufruft, um PhoneBlock ihre Internetadresse bekannt zu geben. Gib die URL genau so ein, wie sie hier geschrieben ist. Ersetze nicht die Werte in den spitzen Klammern, das macht Deine Fritz!Box beim Aufruf automatisch. Nutze am besten die Kopierfuntion, um die Werte zu übernehmen.'**
  String get updateUrlHelp2;

  /// Domain name help text 2
  ///
  /// In de, this message translates to:
  /// **'Dieser Domainname kann später nicht öffentlich aufgelöst werden. Deine Internetadresse wird ausschließlich mit PhoneBlock geteilt.'**
  String get domainNameHelp2;

  /// Username label
  ///
  /// In de, this message translates to:
  /// **'Benutzername'**
  String get username;

  /// Username help text
  ///
  /// In de, this message translates to:
  /// **'Der Benutzername, mit dem sich Deine Fritz!Box bei PhoneBlock anmeldet, um ihre Internetadresse bekannt zu geben.'**
  String get usernameHelp;

  /// Password label (Kennwort)
  ///
  /// In de, this message translates to:
  /// **'Kennwort'**
  String get passwordLabel;

  /// Password help text 2
  ///
  /// In de, this message translates to:
  /// **'Das Kennwort, mit dem sich Deine Fritz!Box bei PhoneBlock anmeldet, um ihre Internetadresse bekannt zu geben. Aus Sicherheitsgründen kannst Du kein eigenes Kennwort eingeben, sondern musst das von PhoneBlock sicher generierte Kennwort verwenden.'**
  String get passwordHelp2;

  /// Checking DynDNS snackbar
  ///
  /// In de, this message translates to:
  /// **'Überprüfe DynDNS Einrichtung.'**
  String get checkingDynDns;

  /// Not registered title
  ///
  /// In de, this message translates to:
  /// **'Nicht registriert'**
  String get notRegistered;

  /// Fritz!Box not registered message
  ///
  /// In de, this message translates to:
  /// **'Deine Fritz!Box hat sich bei PhoneBlock noch nicht angemeldet, DynDNS ist nicht aktuell: {message}'**
  String fritzBoxNotRegistered(String message);

  /// SIP setup instructions intro
  ///
  /// In de, this message translates to:
  /// **'Richte jetzt den PhoneBlock-Anrufbeantworter als \"Telefon (mit und ohne Anrufbeantworter)\" ein. Damit das auch klappt, halte Dich bitte genau an die folgenden Schritte:'**
  String get sipSetupInstructions;

  /// SIP setup step 1
  ///
  /// In de, this message translates to:
  /// **'1. Öffne in Deinen Fritz!Box-Einstellungen die Seite die Telefonie > Telefoniegeräte und klicke auf den Knopf \"Neues Gerät einrichten\".'**
  String get sipSetupStep1;

  /// SIP setup step 2
  ///
  /// In de, this message translates to:
  /// **'2. Wähle die Option \"Telefon (mit und ohne Anrufbeantworter)\" und klicke auf \"Weiter\".'**
  String get sipSetupStep2;

  /// SIP setup step 3
  ///
  /// In de, this message translates to:
  /// **'3. Wähle die Option \"LAN/WLAN (IP-Telefon)\", gib dem Telefon den Namen \"PhoneBlock\" und klicke auf \"Weiter\".'**
  String get sipSetupStep3;

  /// SIP setup step 4
  ///
  /// In de, this message translates to:
  /// **'4. Vergib jetzt den folgenden Benutzernamen und das Kennwort für deinen Anrufbeantworter und klicke dann auf \"Weiter\".'**
  String get sipSetupStep4;

  /// Username help text 2
  ///
  /// In de, this message translates to:
  /// **'Der Benutzername, mit dem sich der PhoneBlock-Anrufbeantworter an Deiner Fritz!Box anmeldet.'**
  String get usernameHelp2;

  /// Password help text 3
  ///
  /// In de, this message translates to:
  /// **'Das Kennwort, das der PhoneBlock-Anrufbeantworter nutzt, um sich an Deiner Fritz!Box anzumelden. PhoneBlock hat für Dich ein sicheres Kennwort generiert.'**
  String get passwordHelp3;

  /// SIP setup step 5
  ///
  /// In de, this message translates to:
  /// **'5. Die jetzt abgefragte Rufnummer ist egal, der PhoneBlock-Anrufbeantworter führt aktiv keine Gespräche, sondern nimmt nur SPAM-Anrufe entgegen. Die Rufnummer wird in Schritt 9 wieder abgewählt. Klicke hier einfach auf \"Weiter\".'**
  String get sipSetupStep5;

  /// SIP setup step 6
  ///
  /// In de, this message translates to:
  /// **'6. Wähle \"alle Anrufe annehmen\" und klicke auf \"Weiter\". Der PhoneBlock-Anrufbeantworter nimmt sowieso nur Gespräche an, wenn die Nummer des Anrufers auf der Blockliste steht. Gleichzeitig nimmt PhoneBlock nie Gespräche von Nummern an, die in Deinem normalen Telefonbuch stehen.'**
  String get sipSetupStep6;

  /// SIP setup step 7
  ///
  /// In de, this message translates to:
  /// **'7. Du siehst eine Zusammenfassung. Die Einstellungen sind (fast) fertig, klicke auf \"Übernehmen\".'**
  String get sipSetupStep7;

  /// SIP setup step 8
  ///
  /// In de, this message translates to:
  /// **'8. In der Liste der Telefoniegeräte siehst Du jetzt \"PhoneBlock\". Es fehlen noch ein paar Einstellungen, die man erst nachträglich machen kann. Klicke daher auf den Bearbeiten-Stift in der Zeile des PhoneBlock-Anrufbeantworters.'**
  String get sipSetupStep8;

  /// SIP setup step 9
  ///
  /// In de, this message translates to:
  /// **'9. In dem Feld \"Ausgehende Anrufe\" wähle die letzte (leere) Option, da PhoneBlock nie ausgehende Anrufe tätigt und daher der Anrufbeantworter keine Nummer für ausgehende Anrufe benötigt.'**
  String get sipSetupStep9;

  /// SIP setup step 10
  ///
  /// In de, this message translates to:
  /// **'10. Selektiere den Reiter \"Anmeldedaten\". Bestätige dabei die Rückfage mit Klick auf \"Übernehmen\". Wähle jetzt die Option \"Anmeldung aus dem Internet erlauben\", damit sich der PhoneBlock-Anrufbeantworter aus der PhoneBlock-Cloud an Deiner Fritz!Box anmelden kann. Du musst das Kennwort des Anrufbeantworters (siehe oben) nocheinmal in das Feld \"Kennwort\" eingeben, bevor Du auf \"Übernehmen\" klickst. Lösche hierzu vorher die im Feld befindlichen Sternchen.'**
  String get sipSetupStep10;

  /// SIP setup step 11
  ///
  /// In de, this message translates to:
  /// **'11. Es erscheint eine Nachricht, die vor darauf hinweist, dass über den Internetzugriff kostenpflichtige Verbindungen aufgebaut werden könnten. Du kannst Das getrost bestätigen, da erstens PhoneBlock nie aktiv Verbindungen aufbaut, zweitens PhoneBlock für Dich ein sicheres Passwort erzeugthat (siehe oben), so dass sich niemand anderes verbinden kann und drittens Du in Schritt 9 die ausgehenden Verbindungen deaktiviert hast. Je nach Einstellungen Deiner Fritz!Box musst Du die Einstellung noch an einem direkt an der Fritz!Box angeschlossenen DECT-Telefon bestätigen.'**
  String get sipSetupStep11;

  /// SIP setup step 12
  ///
  /// In de, this message translates to:
  /// **'12. Jetzt ist alles erledigt. Klicke auf Zurück, um wieder in die Liste der Telefoniegeräte zu springen. Du kannst jetzt mit dem Knopf unten Deinen Anrufbeantworter aktivieren.'**
  String get sipSetupStep12;

  /// Trying to register answerbot progress
  ///
  /// In de, this message translates to:
  /// **'Versuche Anrufbeantworter anzumelden...'**
  String get tryingToRegisterAnswerbot;

  /// Answerbot registration failed title
  ///
  /// In de, this message translates to:
  /// **'Anmeldung des Anrufbeantworters fehlgeschlagen'**
  String get answerbotRegistrationFailed;

  /// Registration failed message
  ///
  /// In de, this message translates to:
  /// **'Registrierung fehlgeschlagen: {message}'**
  String registrationFailed(String message);

  /// Answerbot registered success message
  ///
  /// In de, this message translates to:
  /// **'Dein PhoneBlock-Anrufbeantworter ist erfolgreich angemeldet. Die nächsten Spam-Anrufer können sich jetzt ausgibig mit PhoneBlock unterhalten. Wenn Du den PhoneBlock-Anrufbeantworter selber testen möchtest,dann wähle die interne Rufnummer des von Dir eingerichteten Telefoniegerätes \"PhoneBlock\". Die interne Nummer beginnt i.d.R. mit \"**\".'**
  String get answerbotRegisteredSuccess;
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
