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
}
