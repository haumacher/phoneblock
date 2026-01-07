// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Polish (`pl`).
class AppLocalizationsPl extends AppLocalizations {
  AppLocalizationsPl([String locale = 'pl']) : super(locale);

  @override
  String get appTitle => 'PhoneBlock Mobile';

  @override
  String get settings => 'Ustawienia';

  @override
  String get deleteAll => 'Usuń wszystko';

  @override
  String get noCallsYet => 'Nie przefiltrowano jeszcze żadnych połączeń';

  @override
  String get noCallsDescription =>
      'PhoneBlock automatycznie sprawdza połączenia przychodzące i blokuje połączenia SPAM.';

  @override
  String get blocked => 'Zablokowany';

  @override
  String get accepted => 'Przyjęte';

  @override
  String votes(Object count) {
    return '$count głosów';
  }

  @override
  String get viewOnPhoneBlock => 'Pokaż na PhoneBlock';

  @override
  String get confirmDeleteAll => 'Usunąć wszystkie odfiltrowane połączenia?';

  @override
  String get confirmDeleteAllMessage => 'Tego działania nie można cofnąć.';

  @override
  String get cancel => 'Anuluj';

  @override
  String get delete => 'Usuń';

  @override
  String get settingsTitle => 'Ustawienia';

  @override
  String get callScreening => 'Filtrowanie połączeń';

  @override
  String get minSpamReports => 'Minimalna ilość wiadomości SPAM';

  @override
  String minSpamReportsDescription(Object count) {
    return 'Numery są blokowane począwszy od wiadomości $count.';
  }

  @override
  String get blockNumberRanges => 'Zakresy numerów bloków';

  @override
  String get blockNumberRangesDescription =>
      'Blokowanie obszarów z wieloma wiadomościami SPAM';

  @override
  String get minSpamReportsInRange =>
      'Minimalna ilość wiadomości SPAM w obszarze';

  @override
  String minSpamReportsInRangeDescription(Object count) {
    return 'Obszary są blokowane od wiadomości $count.';
  }

  @override
  String get about => 'O';

  @override
  String get version => 'Wersja';

  @override
  String get developer => 'Deweloper';

  @override
  String get developerName => 'Bernhard Haumacher';

  @override
  String get website => 'Strona internetowa';

  @override
  String get websiteUrl => 'phoneblock.net';

  @override
  String get sourceCode => 'Kod źródłowy';

  @override
  String get sourceCodeLicense => 'Open Source (GPL-3.0)';

  @override
  String get aboutDescription =>
      'PhoneBlock to projekt open-source bez śledzenia i bez reklam. Usługa jest finansowana z darowizn.';

  @override
  String get donate => 'Darowizny';

  @override
  String pendingCallsNotification(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count nowych przefiltrowanych połączeń',
      one: '1 nowe filtrowane połączenie',
    );
    return '$_temp0';
  }

  @override
  String get tapToOpen => 'Stuknij, aby otworzyć aplikację';

  @override
  String get setupWelcome => 'Witamy w PhoneBlock Mobile';

  @override
  String get setupPermissionsRequired => 'Wymagane zezwolenia';

  @override
  String get grantPermission => 'Udzielenie zezwolenia';

  @override
  String get continue_ => 'Dalej';

  @override
  String get finish => 'Zakończony';

  @override
  String get loginRequired => 'Rejestracja w PhoneBlock';

  @override
  String get loginToPhoneBlock => 'Zarejestruj się w PhoneBlock';

  @override
  String get verifyingLogin => 'Rejestracja zostanie sprawdzona...';

  @override
  String get loginFailed => 'Logowanie nie powiodło się';

  @override
  String get loginSuccess => 'Rejestracja zakończona sukcesem!';

  @override
  String get reportAsLegitimate => 'Zgłoś jako uzasadniony';

  @override
  String get reportAsSpam => 'Zgłoś jako SPAM';

  @override
  String get viewOnPhoneBlockMenu => 'Wyświetl w PhoneBlock';

  @override
  String get deleteCall => 'Usuń';

  @override
  String get report => 'Raport';

  @override
  String get notLoggedIn => 'Nie zarejestrowano. Zaloguj się.';

  @override
  String reportedAsLegitimate(Object phoneNumber) {
    return '$phoneNumber zgłoszony jako prawidłowy';
  }

  @override
  String reportError(Object error) {
    return 'Błąd podczas raportowania: $error';
  }

  @override
  String reportedAsSpam(Object phoneNumber) {
    return '$phoneNumber zgłoszony jako SPAM';
  }

  @override
  String get selectSpamCategory => 'Wybierz kategorię SPAM';

  @override
  String get errorDeletingAllCalls =>
      'Błąd podczas usuwania wszystkich połączeń';

  @override
  String get errorDeletingCall => 'Błąd podczas anulowania połączenia';

  @override
  String get notLoggedInShort => 'Nie zarejestrowany';

  @override
  String get errorOpeningPhoneBlock => 'Błąd podczas otwierania PhoneBlock.';

  @override
  String get permissionNotGranted => 'Zezwolenie nie zostało udzielone.';

  @override
  String get setupTitle => 'PhoneBlock Mobile - konfiguracja';

  @override
  String get welcome => 'Witamy w';

  @override
  String get connectPhoneBlockAccount => 'Podłącz konto PhoneBlock';

  @override
  String get permissions => 'Zezwolenia';

  @override
  String get allowCallFiltering => 'Zezwalaj na filtrowanie połączeń';

  @override
  String get done => 'Zakończony';

  @override
  String get setupComplete => 'Instalacja zakończona';

  @override
  String get minReportsCount => 'Minimalna liczba wiadomości';

  @override
  String callsBlockedAfterReports(Object count) {
    return 'Połączenia są blokowane od wiadomości $count';
  }

  @override
  String rangesBlockedAfterReports(Object count) {
    return 'Obszary są blokowane od wiadomości $count.';
  }

  @override
  String get welcomeMessage =>
      'Witamy w PhoneBlock Mobile!\n\nTa aplikacja pomaga automatycznie blokować połączenia spamowe. Potrzebne jest bezpłatne konto w serwisie PhoneBlock.net.\n\nPołącz swoje konto PhoneBlock, aby kontynuować:';

  @override
  String get connectToPhoneBlock => 'Połącz się z PhoneBlock';

  @override
  String get connectedToPhoneBlock => 'Połączony z PhoneBlock';

  @override
  String get accountConnectedSuccessfully =>
      'Konto zostało pomyślnie połączone';

  @override
  String get permissionsMessage =>
      'Aby automatycznie blokować połączenia spamowe, PhoneBlock Mobile wymaga autoryzacji do sprawdzania połączeń przychodzących.\n\nAutoryzacja ta jest wymagana do działania aplikacji:';

  @override
  String get permissionGranted => 'Udzielone zezwolenie';

  @override
  String get permissionGrantedSuccessfully =>
      '✓ Autoryzacja została pomyślnie przyznana';

  @override
  String get setupCompleteMessage =>
      'Instalacja zakończona!\n\nPhoneBlock Mobile jest teraz gotowy do blokowania połączeń spamowych. Aplikacja automatycznie sprawdza połączenia przychodzące i blokuje znane numery spamowe w oparciu o bazę danych PhoneBlock.\n\nNaciśnij \"Gotowe\", aby przejść do widoku głównego.';

  @override
  String get verifyingLoginTitle => 'Sprawdź logowanie';

  @override
  String get loginSuccessMessage => 'Logowanie powiodło się!';

  @override
  String get redirectingToSetup => 'Przekazywanie do obiektu...';

  @override
  String tokenVerificationFailed(Object error) {
    return 'Weryfikacja tokena nie powiodła się: $error';
  }

  @override
  String get backToSetup => 'Powrót do obiektu';

  @override
  String get tokenBeingVerified => 'Token jest sprawdzany...';

  @override
  String get failedToOpenPhoneBlock => 'Nie można otworzyć PhoneBlock.';

  @override
  String get ratingLegitimate => 'Legalny';

  @override
  String get ratingAdvertising => 'Reklama';

  @override
  String get ratingSpam => 'SPAM';

  @override
  String get ratingPingCall => 'Połączenie ping';

  @override
  String get ratingGamble => 'Konkurencja';

  @override
  String get ratingFraud => 'Oszustwo';

  @override
  String get ratingPoll => 'Ankieta';

  @override
  String get noLoginTokenReceived => 'Nie otrzymano tokenu logowania.';

  @override
  String get settingSaved => 'Zapisane ustawienie';

  @override
  String get errorSaving => 'Błąd podczas zapisywania';

  @override
  String ratePhoneNumber(Object phoneNumber) {
    return 'Oceń $phoneNumber';
  }

  @override
  String reportsCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count wiadomości',
      one: '1 Wiadomość',
    );
    return '$_temp0';
  }

  @override
  String legitimateReportsCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Legalne wiadomości',
      one: '1 Legalna wiadomość',
    );
    return '$_temp0';
  }

  @override
  String get noReports => 'Brak wiadomości';

  @override
  String todayTime(Object time) {
    return 'Dzisiaj, $time';
  }

  @override
  String yesterdayTime(Object time) {
    return 'Wczoraj, $time';
  }

  @override
  String get callHistoryRetention => 'Przechowywanie historii połączeń';

  @override
  String retentionPeriodDescription(num days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: 'Zachowaj połączenia $days dni',
      one: 'Utrzymywanie połączeń przez 1 dzień',
    );
    return '$_temp0';
  }

  @override
  String get retentionInfinite => 'Zachowaj wszystkie połączenia';

  @override
  String retentionDays(num days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: '$days dni',
      one: '1 dzień',
    );
    return '$_temp0';
  }

  @override
  String get retentionInfiniteOption => 'Bez ograniczeń';

  @override
  String get addCommentSpam => 'Dodaj komentarz (opcjonalnie)';

  @override
  String get commentHintSpam =>
      'Dlaczego jest to spam? Czego dotyczyła rozmowa? Prosimy o zachowanie uprzejmości.';

  @override
  String get addCommentLegitimate => 'Dodaj komentarz (opcjonalnie)';

  @override
  String get commentHintLegitimate =>
      'Dlaczego jest to uzasadnione? Kto dzwonił? Prosimy o zachowanie uprzejmości.';

  @override
  String get serverSettings => 'Ustawienia serwera';

  @override
  String get serverSettingsDescription =>
      'Zarządzanie ustawieniami konta PhoneBlock';

  @override
  String get searchNumber => 'Numer wyszukiwania';

  @override
  String get searchPhoneNumber => 'Wyszukaj numer telefonu';

  @override
  String get enterPhoneNumber => 'Wprowadź numer telefonu';

  @override
  String get phoneNumberHint => 'np. +49 123 456789';

  @override
  String get search => 'Wyszukiwanie';

  @override
  String get invalidPhoneNumber => 'Wprowadź prawidłowy numer telefonu';

  @override
  String get blacklistTitle => 'Czarna lista';

  @override
  String get blacklistDescription => 'Zablokowane numery';

  @override
  String get whitelistTitle => 'Biała lista';

  @override
  String get whitelistDescription => 'Numery oznaczone jako legalne';

  @override
  String get blacklistEmpty => 'Twoja czarna lista jest pusta';

  @override
  String get whitelistEmpty => 'Biała lista jest pusta';

  @override
  String get blacklistEmptyHelp =>
      'Fügen Sie Nummern hinzu, indem Sie unerwünschte Anrufe als Spam melden.';

  @override
  String get whitelistEmptyHelp =>
      'Fügen Sie Nummern hinzu, indem Sie blockierte Anrufe als legitim melden.';

  @override
  String get errorLoadingList => 'Błąd ładowania listy';

  @override
  String get numberRemovedFromList => 'Usunięta liczba';

  @override
  String get errorRemovingNumber => 'Błąd podczas usuwania numeru';

  @override
  String get confirmRemoval => 'Potwierdzenie usunięcia';

  @override
  String confirmRemoveFromBlacklist(Object phone) {
    return 'Usunąć $phone z czarnej listy?';
  }

  @override
  String confirmRemoveFromWhitelist(Object phone) {
    return 'Usunąć $phone z białej listy?';
  }

  @override
  String get remove => 'Usunąć';

  @override
  String get retry => 'Spróbuj ponownie';

  @override
  String get editComment => 'Edytuj komentarz';

  @override
  String get commentLabel => 'Komentarz';

  @override
  String get commentHint => 'Dodaj notatkę do tego numeru';

  @override
  String get save => 'Zapisz';

  @override
  String get commentUpdated => 'Komentarz zaktualizowany';

  @override
  String get errorUpdatingComment => 'Błąd podczas aktualizacji komentarza';
}
