// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Polish (`pl`).
class AppLocalizationsPl extends AppLocalizations {
  AppLocalizationsPl([String locale = 'pl']) : super(locale);

  @override
  String get appTitle => 'Automatyczna sekretarka PhoneBlock';

  @override
  String get yourAnswerbots => 'Automatyczna sekretarka';

  @override
  String get loginRequired => 'Wymagana rejestracja';

  @override
  String get login => 'Logowanie';

  @override
  String get loadingData => 'Ładowanie danych...';

  @override
  String get refreshingData => 'Aktualizacja danych...';

  @override
  String get noAnswerbotsYet =>
      'Nie masz jeszcze automatycznej sekretarki, kliknij przycisk plus poniżej, aby utworzyć automatyczną sekretarkę PhoneBlock.';

  @override
  String get createAnswerbot => 'Tworzenie automatycznej sekretarki';

  @override
  String answerbotName(String userName) {
    return 'Automatyczna sekretarka $userName';
  }

  @override
  String answerbotStats(int newCalls, int talkTimeSeconds, int callsAccepted) {
    return '$newCalls nowe połączenia, $callsAccepted połączenia, $talkTimeSeconds całkowity czas rozmów';
  }

  @override
  String get statusActive => 'aktywny';

  @override
  String get statusConnecting => 'połączyć...';

  @override
  String get statusDisabled => 'wyłączony';

  @override
  String get statusIncomplete => 'niekompletny';

  @override
  String get deleteAnswerbot => 'Usuń automatyczną sekretarkę';

  @override
  String get enabled => 'Aktywowany';

  @override
  String get minVotes => 'Minimalna liczba głosów';

  @override
  String get minVotesDescription =>
      'Jaka jest minimalna liczba głosów wymagana do zaakceptowania numeru przez automatyczną sekretarkę?';

  @override
  String get minVotes2 => '2 - natychmiastowa blokada';

  @override
  String get minVotes4 => '4 - Poczekaj na potwierdzenie';

  @override
  String get minVotes10 => '10 - tylko wtedy, gdy jest to bezpieczne';

  @override
  String get minVotes100 => '100 - tylko najlepsi spamerzy';

  @override
  String get cannotChangeWhileEnabled =>
      'Można ją zmienić tylko wtedy, gdy automatyczna sekretarka jest wyłączona.';

  @override
  String get saveSettings => 'Zapisz ustawienia';

  @override
  String get retentionPeriod => 'Czas przechowywania';

  @override
  String get retentionPeriodDescription =>
      'Jak długo należy przechowywać połączenia?';

  @override
  String get retentionNever => 'Nigdy nie usuwaj';

  @override
  String get retentionWeek => 'Usuń po 1 tygodniu';

  @override
  String get retentionMonth => 'Usuń po 1 miesiącu';

  @override
  String get retentionQuarter => 'Usuń po 3 miesiącach';

  @override
  String get retentionYear => 'Usuń po 1 roku';

  @override
  String get saveRetentionSettings => 'Zapisywanie ustawień pamięci masowej';

  @override
  String get showHelp => 'Pokaż pomoc';

  @override
  String get newAnswerbot => 'Nowa automatyczna sekretarka';

  @override
  String get usePhoneBlockDynDns => 'Użyj PhoneBlock DynDNS';

  @override
  String get dynDnsDescription =>
      'PhoneBlock musi znać adres internetowy skrzynki Fritz!, aby móc odbierać połączenia.';

  @override
  String get setupPhoneBlockDynDns => 'Konfiguracja PhoneBlock DynDNS';

  @override
  String get domainName => 'Nazwa domeny';

  @override
  String get domainNameHint =>
      'Wprowadź nazwę domeny urządzenia Fritz! Jeśli Fritz!Box nie ma jeszcze nazwy domeny, aktywuj PhoneBlock-DynDNS.';

  @override
  String get checkDomainName => 'Sprawdź nazwy domen';

  @override
  String get setupDynDns => 'Konfiguracja DynDNS';

  @override
  String get dynDnsInstructions =>
      'W ustawieniach Fritz!Box otwórz stronę Internet > Shares > DynDNS i wprowadź tam następujące wartości:';

  @override
  String get checkDynDns => 'Sprawdź DynDNS';

  @override
  String get createAnswerbotTitle => 'Tworzenie automatycznej sekretarki';

  @override
  String get registerAnswerbot => 'Zarejestruj automatyczną sekretarkę';

  @override
  String get answerbotRegistered => 'Automatyczna sekretarka zarejestrowana';

  @override
  String get close => 'Zamknij';

  @override
  String get error => 'Błąd';

  @override
  String cannotLoadInfo(String message, int statusCode) {
    return 'Nie można pobrać informacji (błąd $statusCode): $message';
  }

  @override
  String wrongContentType(String contentType) {
    return 'Nie można pobrać informacji (Content-Type: $contentType).';
  }

  @override
  String get callList => 'Lista połączeń';

  @override
  String get clearCallList => 'Usuwanie listy połączeń';

  @override
  String get noCalls => 'Brak połączeń';

  @override
  String get answerbot => 'Automatyczna sekretarka';

  @override
  String get answerbotSettings => 'Ustawienia automatycznej sekretarki';

  @override
  String get minConfidence => 'Minimalne zaufanie';

  @override
  String get minConfidenceHelp =>
      'Ile skarg jest potrzebnych, aby numer został przechwycony przez automatyczną sekretarkę.';

  @override
  String get blockNumberRanges => 'Zakresy numerów bloków';

  @override
  String get blockNumberRangesHelp =>
      'Akceptuje połączenie nawet dla numeru, który nie jest jeszcze znany jako SPAM, jeśli istnieje powód, aby podejrzewać, że numer należy do połączenia systemowego, z którego pochodzi SPAM.';

  @override
  String get preferIPv4 => 'Korzystaj z komunikacji IPv4';

  @override
  String get preferIPv4Help =>
      'Jeśli jest dostępna, komunikacja z automatyczną sekretarką jest obsługiwana przez IPv4. Wydaje się, że istnieją połączenia telefoniczne, dla których połączenie głosowe przez IPv6 nie jest możliwe, mimo że adres IPv6 jest dostępny.';

  @override
  String get callRetention => 'Zatrzymywanie połączeń';

  @override
  String get automaticDeletion => 'Automatyczne usuwanie';

  @override
  String get automaticDeletionHelp =>
      'Po jakim czasie stare dzienniki połączeń powinny być automatycznie usuwane? Opcja Nigdy nie usuwaj wyłącza automatyczne usuwanie.';

  @override
  String get dnsSettings => 'Ustawienia DNS';

  @override
  String get dnsSetting => 'Ustawienie DNS';

  @override
  String get phoneBlockDns => 'PhoneBlock DNS';

  @override
  String get otherProviderOrDomain => 'Inny dostawca lub nazwa domeny';

  @override
  String get dnsSettingHelp =>
      'Jak automatyczna sekretarka znajduje skrzynkę Fritz! w Internecie.';

  @override
  String get updateUrl => 'Zaktualizuj adres URL';

  @override
  String get updateUrlHelp => 'Nazwa użytkownika dla udziału DynDNS w Fritz!';

  @override
  String get domainNameHelp =>
      'Nazwa, którą Fritz! Box otrzymuje w Internecie.';

  @override
  String get dyndnsUsername => 'Nazwa użytkownika DynDNS';

  @override
  String get dyndnsUsernameHelp =>
      'Nazwa użytkownika dla udziału DynDNS w Fritz!';

  @override
  String get dyndnsPassword => 'Hasło DynDNS';

  @override
  String get dyndnsPasswordHelp =>
      'Hasło, którego należy użyć do udostępniania DynDNS.';

  @override
  String get host => 'Gospodarz';

  @override
  String get hostHelp =>
      'Nazwa hosta, za pośrednictwem którego można uzyskać dostęp do urządzenia Fritz!Box z Internetu.';

  @override
  String get sipSettings => 'Ustawienia SIP';

  @override
  String get user => 'Użytkownik';

  @override
  String get userHelp =>
      'Nazwa użytkownika, którą należy skonfigurować w aplikacji Fritz!Box, aby uzyskać dostęp do urządzenia telefonicznego.';

  @override
  String get password => 'Hasło';

  @override
  String get passwordHelp =>
      'Hasło, które należy przypisać w celu uzyskania dostępu do urządzenia telefonicznego w aplikacji Fritz!';

  @override
  String get savingSettings => 'Zapisz ustawienia...';

  @override
  String get errorSavingSettings => 'Błąd podczas zapisywania ustawień.';

  @override
  String savingFailed(String message) {
    return 'Zapis nie powiódł się: $message';
  }

  @override
  String get enableAfterSavingFailed => 'Włącz ponownie po nieudanym zapisie';

  @override
  String get enablingAnswerbot => 'Włącz automatyczną sekretarkę...';

  @override
  String get errorEnablingAnswerbot =>
      'Błąd podczas włączania automatycznej sekretarki.';

  @override
  String cannotEnable(String message) {
    return 'Nie można włączyć: $message';
  }

  @override
  String get enablingFailed => 'Nie udało się włączyć automatycznej sekretarki';

  @override
  String enablingFailedMessage(String message) {
    return 'Włączanie nie powiodło się: $message';
  }

  @override
  String retryingMessage(String message) {
    return '$message Spróbuj ponownie...';
  }

  @override
  String get savingRetentionSettings => 'Zapisz ustawienia pamięci...';

  @override
  String get errorSavingRetentionSettings =>
      'Błąd podczas zapisywania ustawień pamięci masowej.';

  @override
  String get automaticDeletionDisabled => 'Automatyczne usuwanie wyłączone';

  @override
  String retentionSettingsSaved(String period) {
    return 'Zapisane ustawienia pamięci ($period)';
  }

  @override
  String get oneWeek => '1 tydzień';

  @override
  String get oneMonth => '1 miesiąc';

  @override
  String get threeMonths => '3 miesiące';

  @override
  String get oneYear => '1 rok';

  @override
  String get never => 'Nigdy';

  @override
  String deleteAnswerbotConfirm(String userName) {
    return 'Czy automatyczna sekretarka $userName naprawdę powinna zostać usunięta?';
  }

  @override
  String get cancel => 'Anuluj';

  @override
  String get delete => 'Usuń';

  @override
  String get deletionFailed => 'Usunięcie nie powiodło się';

  @override
  String get answerbotCouldNotBeDeleted =>
      'Nie można usunąć automatycznej sekretarki';

  @override
  String get spamCalls => 'Połączenia SPAM';

  @override
  String get deleteCalls => 'Usuwanie połączeń';

  @override
  String get deletingCallsFailed => 'Usunięcie nie powiodło się';

  @override
  String get deleteRequestFailed =>
      'Żądanie usunięcia nie mogło zostać przetworzone.';

  @override
  String cannotRetrieveCalls(String message, int statusCode) {
    return 'Nie można pobrać wywołań (błąd $statusCode): $message';
  }

  @override
  String get noNewCalls => 'Brak nowych połączeń.';

  @override
  String duration(int seconds) {
    return 'Czas trwania $seconds s';
  }

  @override
  String today(String time) {
    return 'Dzisiaj $time';
  }

  @override
  String yesterday(String time) {
    return 'Wczoraj $time';
  }

  @override
  String get dynDnsDescriptionLong =>
      'PhoneBlock musi znać adres internetowy skrzynki Fritz!, aby zarejestrować automatyczną sekretarkę na skrzynce Fritz! Jeśli skonfigurowałeś już MyFRITZ! lub innego dostawcę DynDNS, możesz użyć tej nazwy domeny. Jeśli nie, możesz po prostu skonfigurować DynDNS z PhoneBlock, a następnie aktywować ten przełącznik.';

  @override
  String get setupPhoneBlockDynDnsSnackbar => 'Konfiguracja PhoneBlock DynDNS.';

  @override
  String get setupFailed => 'Konfiguracja nie powiodła się';

  @override
  String cannotSetupDynDns(String message) {
    return 'Nie można skonfigurować DynDNS: $message';
  }

  @override
  String get domainname => 'Nazwa domeny';

  @override
  String get domainNameHintLong =>
      'Nazwa domeny skrzynki Fritz! (adres MyFRITZ! lub nazwa domeny DynDNS)';

  @override
  String get inputCannotBeEmpty => 'Wejście nie może być puste.';

  @override
  String get invalidDomainName => 'Brak prawidłowej nazwy domeny.';

  @override
  String get domainNameTooLong => 'Nazwa domeny jest zbyt długa.';

  @override
  String get domainNameHintExtended =>
      'Wprowadź nazwę domeny urządzenia Fritz! Jeśli Fritz!Box nie ma jeszcze nazwy domeny, aktywuj PhoneBlock DynDNS. Nazwę domeny urządzenia Fritz!Box można znaleźć w sekcji (Internet > Shares > DynDNS). Alternatywnie można również wprowadzić adres MyFRITZ! (Internet > Konto MyFRITZ!), np. z4z...l4n.myfritz.net.';

  @override
  String get checkingDomainName => 'Sprawdź nazwy domen.';

  @override
  String domainNameNotAccepted(String message) {
    return 'Nazwa domeny nie została zaakceptowana: $message';
  }

  @override
  String get dynDnsInstructionsLong =>
      'Otwórz stronę Internet > Shares > DynDNS w ustawieniach Fritz!Box i wprowadź podane tutaj informacje.';

  @override
  String get updateUrlHelp2 =>
      'Adres URL, który Fritz! Box wywołuje, aby nadać PhoneBlock adres internetowy. Wprowadź adres URL dokładnie tak, jak jest tutaj zapisany. Nie zastępuj wartości w nawiasach kątowych, Fritz! Box zrobi to automatycznie po wywołaniu. Najlepiej jest użyć funkcji kopiowania, aby skopiować wartości.';

  @override
  String get domainNameHelp2 =>
      'Ta nazwa domeny nie może być później publicznie rozwiązana. Twój adres internetowy zostanie udostępniony tylko PhoneBlock.';

  @override
  String get username => 'Nazwa użytkownika';

  @override
  String get usernameHelp =>
      'Nazwa użytkownika, za pomocą której Fritz! Box loguje się do PhoneBlock, aby podać swój adres internetowy.';

  @override
  String get passwordLabel => 'Hasło';

  @override
  String get passwordHelp2 =>
      'Hasło, za pomocą którego Fritz!Box loguje się do PhoneBlock, aby podać swój adres internetowy. Ze względów bezpieczeństwa nie można wprowadzić własnego hasła, ale należy użyć hasła bezpiecznie wygenerowanego przez PhoneBlock.';

  @override
  String get checkingDynDns => 'Sprawdź konfigurację DynDNS.';

  @override
  String get notRegistered => 'Nie zarejestrowany';

  @override
  String fritzBoxNotRegistered(String message) {
    return 'Twój Fritz!Box nie został jeszcze zarejestrowany w PhoneBlock, DynDNS nie jest aktualny: $message';
  }

  @override
  String get sipSetupInstructions =>
      'Teraz skonfiguruj automatyczną sekretarkę PhoneBlock jako \"Telefon (z automatyczną sekretarką i bez)\". Aby upewnić się, że to działa, wykonaj dokładnie poniższe kroki:';

  @override
  String get sipSetupStep1 =>
      '1. otwórz stronę Telefonia > Urządzenia telefoniczne w ustawieniach Fritz!Box i kliknij przycisk \"Skonfiguruj nowe urządzenie\".';

  @override
  String get sipSetupStep2 =>
      '2. wybierz opcję \"Telefon (z automatyczną sekretarką lub bez)\" i kliknij \"Dalej\".';

  @override
  String get sipSetupStep3 =>
      '3. wybierz opcję \"LAN/WLAN (telefon IP)\", nadaj telefonowi nazwę \"PhoneBlock\" i kliknij \"Next\".';

  @override
  String get sipSetupStep4 =>
      '4. Teraz wprowadź następującą nazwę użytkownika i hasło dla automatycznej sekretarki, a następnie kliknij \"Dalej\".';

  @override
  String get usernameHelp2 =>
      'Nazwa użytkownika, za pomocą której automatyczna sekretarka PhoneBlock loguje się do Fritz!';

  @override
  String get passwordHelp3 =>
      'Hasło, którego automatyczna sekretarka PhoneBlock używa do logowania się do Fritz! PhoneBlock wygenerował dla Ciebie bezpieczne hasło.';

  @override
  String get sipSetupStep5 =>
      '5. Numer telefonu, którego dotyczy zapytanie, nie ma teraz znaczenia, automatyczna sekretarka PhoneBlock nie wykonuje aktywnie żadnych połączeń, a jedynie odbiera połączenia SPAM. Numer telefonu jest odznaczany ponownie w kroku 9. Wystarczy kliknąć \"Dalej\".';

  @override
  String get sipSetupStep6 =>
      '6. Wybierz \"Akceptuj wszystkie połączenia\" i kliknij \"Dalej\". Automatyczna sekretarka PhoneBlock i tak odbiera połączenia tylko wtedy, gdy numer dzwoniącego znajduje się na liście blokowanych. Jednocześnie PhoneBlock nigdy nie akceptuje połączeń z numerów, które znajdują się w normalnej książce telefonicznej.';

  @override
  String get sipSetupStep7 =>
      '7. pojawi się podsumowanie. Ustawienia są (prawie) kompletne, kliknij \"Zastosuj\".';

  @override
  String get sipSetupStep8 =>
      '8. Na liście urządzeń telefonicznych pojawi się teraz \"PhoneBlock\". Nadal brakuje kilku ustawień, które można wprowadzić dopiero później. Dlatego kliknij ołówek edycji w wierszu automatycznej sekretarki PhoneBlock.';

  @override
  String get sipSetupStep9 =>
      '9. wybierz ostatnią (pustą) opcję w polu \"Połączenia wychodzące\", ponieważ PhoneBlock nigdy nie wykonuje połączeń wychodzących, a zatem automatyczna sekretarka nie wymaga numeru dla połączeń wychodzących.';

  @override
  String get sipSetupStep10 =>
      '10. Wybierz zakładkę \"Dane logowania\". Potwierdź odpowiedź, klikając \"Zastosuj\". Teraz wybierz opcję \"Zezwalaj na logowanie z Internetu\", aby automatyczna sekretarka PhoneBlock z chmury PhoneBlock mogła zalogować się do telefonu Fritz! Przed kliknięciem przycisku \"Zastosuj\" należy ponownie wprowadzić hasło automatycznej sekretarki (patrz wyżej) w polu \"Hasło\". Najpierw usuń gwiazdki w polu.';

  @override
  String get sipSetupStep11 =>
      '11. Pojawi się komunikat ostrzegający, że płatne połączenia mogą być nawiązywane przez dostęp do Internetu. Po pierwsze, PhoneBlock nigdy aktywnie nie nawiązuje połączeń, po drugie, PhoneBlock utworzył bezpieczne hasło (patrz wyżej), aby nikt inny nie mógł się połączyć, a po trzecie, połączenia wychodzące zostały dezaktywowane w kroku 9. W zależności od ustawień Fritz!Box, może być konieczne potwierdzenie ustawień na telefonie DECT podłączonym bezpośrednio do Fritz!';

  @override
  String get sipSetupStep12 =>
      '12. Teraz wszystko jest gotowe. Kliknij przycisk Back, aby powrócić do listy urządzeń telefonicznych. Możesz teraz aktywować automatyczną sekretarkę za pomocą przycisku na dole.';

  @override
  String get tryingToRegisterAnswerbot =>
      'Spróbuj zarejestrować automatyczną sekretarkę...';

  @override
  String get answerbotRegistrationFailed =>
      'Rejestracja automatycznej sekretarki nie powiodła się';

  @override
  String registrationFailed(String message) {
    return 'Rejestracja nie powiodła się: $message';
  }

  @override
  String get answerbotRegisteredSuccess =>
      'Twoja automatyczna sekretarka PhoneBlock została pomyślnie zarejestrowana. Następni spamerzy mogą teraz rozmawiać z PhoneBlock. Jeśli chcesz samodzielnie przetestować automatyczną sekretarkę PhoneBlock, wybierz numer wewnętrzny skonfigurowanego urządzenia telefonicznego \"PhoneBlock\". Numer wewnętrzny zwykle zaczyna się od \"**\".';
}
