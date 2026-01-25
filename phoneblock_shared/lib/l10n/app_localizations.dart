import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_de.dart';

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
  static const List<Locale> supportedLocales = <Locale>[Locale('de')];

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
  String answerbotStats(int newCalls, int callsAccepted, int talkTimeSeconds);

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
  String cannotLoadInfo(int statusCode, String message);

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
      <String>['de'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'de':
      return AppLocalizationsDe();
  }

  throw FlutterError(
      'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
      'an issue with the localizations generation tool. Please file an issue '
      'on GitHub with a reproducible sample app and the gen-l10n configuration '
      'that was used.');
}
