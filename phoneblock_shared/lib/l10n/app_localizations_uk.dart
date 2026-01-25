// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Ukrainian (`uk`).
class AppLocalizationsUk extends AppLocalizations {
  AppLocalizationsUk([String locale = 'uk']) : super(locale);

  @override
  String get appTitle => 'Автовідповідач PhoneBlock';

  @override
  String get yourAnswerbots => 'Ваш автовідповідач';

  @override
  String get loginRequired => 'Реєстрація обов\'язкова';

  @override
  String get login => 'Логін';

  @override
  String get loadingData => 'Завантаження даних...';

  @override
  String get refreshingData => 'Оновити дані...';

  @override
  String get noAnswerbotsYet =>
      'Якщо у вас ще немає автовідповідача, натисніть кнопку \"плюс\" нижче, щоб створити автовідповідач PhoneBlock.';

  @override
  String get createAnswerbot => 'Створення автовідповідача';

  @override
  String answerbotName(String userName) {
    return 'Автовідповідач $userName';
  }

  @override
  String answerbotStats(int newCalls, int talkTimeSeconds, int callsAccepted) {
    return '$newCalls нові виклики, $callsAccepted виклики, $talkTimeSeconds загальний час розмови';
  }

  @override
  String get statusActive => 'активний';

  @override
  String get statusConnecting => 'з\'єднати...';

  @override
  String get statusDisabled => 'вимкнено';

  @override
  String get statusIncomplete => 'неповний';

  @override
  String get deleteAnswerbot => 'Видалити автовідповідач';

  @override
  String get enabled => 'Активовано';

  @override
  String get minVotes => 'Мінімальна кількість голосів';

  @override
  String get minVotesDescription =>
      'Яка мінімальна кількість голосів необхідна для того, щоб номер був прийнятий автовідповідачем?';

  @override
  String get minVotes2 => '2 - негайно заблокувати';

  @override
  String get minVotes4 => '4 - Дочекайтеся підтвердження';

  @override
  String get minVotes10 => '10 - тільки коли це безпечно';

  @override
  String get minVotes100 => '100 - тільки топові спамери';

  @override
  String get cannotChangeWhileEnabled =>
      'Можна змінити лише при вимкненому автовідповідачі.';

  @override
  String get saveSettings => 'Зберегти налаштування';

  @override
  String get retentionPeriod => 'Час зберігання';

  @override
  String get retentionPeriodDescription => 'Як довго слід зберігати дзвінки?';

  @override
  String get retentionNever => 'Ніколи не видаляйте';

  @override
  String get retentionWeek => 'Видалити через 1 тиждень';

  @override
  String get retentionMonth => 'Видалити через 1 місяць';

  @override
  String get retentionQuarter => 'Видалити через 3 місяці';

  @override
  String get retentionYear => 'Видалити через 1 рік';

  @override
  String get saveRetentionSettings => 'Збереження налаштувань сховища';

  @override
  String get showHelp => 'Показати допомогу';

  @override
  String get newAnswerbot => 'Новий автовідповідач';

  @override
  String get usePhoneBlockDynDns => 'Використовуйте PhoneBlock DynDNS';

  @override
  String get dynDnsDescription =>
      'PhoneBlock повинен знати інтернет-адресу вашої Fritz! скриньки, щоб приймати дзвінки.';

  @override
  String get setupPhoneBlockDynDns => 'Налаштування PhoneBlock DynDNS';

  @override
  String get domainName => 'Доменне ім\'я';

  @override
  String get domainNameHint =>
      'Введіть доменне ім\'я вашого Fritz! Якщо ваш Fritz!Box ще не має доменного імені, активуйте PhoneBlock-DynDNS.';

  @override
  String get checkDomainName => 'Перевірка доменних імен';

  @override
  String get setupDynDns => 'Налаштування DynDNS';

  @override
  String get dynDnsInstructions =>
      'У налаштуваннях Fritz!Box відкрийте сторінку Інтернет > Ресурси > DynDNS і введіть наступні значення:';

  @override
  String get checkDynDns => 'Перевірте DynDNS';

  @override
  String get createAnswerbotTitle => 'Створення автовідповідача';

  @override
  String get registerAnswerbot => 'Зареєструвати автовідповідач';

  @override
  String get answerbotRegistered => 'Автовідповідач зареєстровано';

  @override
  String get close => 'Закрити';

  @override
  String get error => 'Помилка.';

  @override
  String cannotLoadInfo(String message, int statusCode) {
    return 'Інформація не може бути отримана (помилка $statusCode): $message';
  }

  @override
  String wrongContentType(String contentType) {
    return 'Інформація не може бути отримана (Content-Type: $contentType).';
  }

  @override
  String get callList => 'Список дзвінків';

  @override
  String get clearCallList => 'Видалення списку викликів';

  @override
  String get noCalls => 'Дзвінків ще не було.';
}
