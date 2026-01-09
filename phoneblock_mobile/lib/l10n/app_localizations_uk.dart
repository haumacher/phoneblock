// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Ukrainian (`uk`).
class AppLocalizationsUk extends AppLocalizations {
  AppLocalizationsUk([String locale = 'uk']) : super(locale);

  @override
  String get appTitle => 'PhoneBlock Mobile';

  @override
  String get settings => 'Налаштування';

  @override
  String get deleteAll => 'Видалити все';

  @override
  String get noCallsYet => 'Ще немає відфільтрованих дзвінків';

  @override
  String get noCallsDescription =>
      'PhoneBlock автоматично перевірятиме вхідні дзвінки та блокуватиме СПАМ-дзвінки.';

  @override
  String get blocked => 'Заблоковано.';

  @override
  String get accepted => 'Прийнято';

  @override
  String votes(int count) {
    return '$count голоси';
  }

  @override
  String get viewOnPhoneBlock => 'Показати на PhoneBlock';

  @override
  String get confirmDeleteAll => 'Видалити всі відфільтровані дзвінки?';

  @override
  String get confirmDeleteAllMessage => 'Цю дію не можна скасувати.';

  @override
  String get cancel => 'Скасувати';

  @override
  String get delete => 'Видалити';

  @override
  String get settingsTitle => 'Налаштування';

  @override
  String get callScreening => 'Фільтрація дзвінків';

  @override
  String get minSpamReports => 'Мінімум СПАМ-повідомлень';

  @override
  String minSpamReportsDescription(int count) {
    return 'Номери блокуються, починаючи з $count повідомлень і далі';
  }

  @override
  String get blockNumberRanges => 'Діапазони номерів блоків';

  @override
  String get blockNumberRangesDescription =>
      'Блокувати області з великою кількістю спам-повідомлень';

  @override
  String get minSpamReportsInRange => 'Мінімум СПАМ-повідомлень в області';

  @override
  String minSpamReportsInRangeDescription(int count) {
    return 'Області блокуються, починаючи з $count повідомлень і далі';
  }

  @override
  String get about => 'Про';

  @override
  String get version => 'Версія';

  @override
  String get developer => 'Розробник';

  @override
  String get developerName => 'Бернхард Гаумахер';

  @override
  String get website => 'Веб-сайт';

  @override
  String get websiteUrl => 'phoneblock.net';

  @override
  String get sourceCode => 'Вихідний код';

  @override
  String get sourceCodeLicense => 'Відкритий вихідний код (GPL-3.0)';

  @override
  String get aboutDescription =>
      'PhoneBlock - це проект з відкритим вихідним кодом, без трекінгу і без реклами. Сервіс фінансується за рахунок пожертвувань.';

  @override
  String get donate => 'Пожертви';

  @override
  String pendingCallsNotification(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count нових відфільтрованих викликів',
      one: '1 новий відфільтрований дзвінок',
    );
    return '$_temp0';
  }

  @override
  String get tapToOpen => 'Натисніть, щоб відкрити програму';

  @override
  String get setupWelcome => 'Ласкаво просимо до PhoneBlock Mobile';

  @override
  String get setupPermissionsRequired => 'Необхідні дозволи';

  @override
  String get grantPermission => 'Авторизація гранту';

  @override
  String get continue_ => 'Далі';

  @override
  String get finish => 'Готово.';

  @override
  String get loginRequired => 'Реєстрація в PhoneBlock';

  @override
  String get loginToPhoneBlock => 'Зареєструйтеся в PhoneBlock';

  @override
  String get verifyingLogin => 'Реєстрація буде перевірена...';

  @override
  String get loginFailed => 'Не вдалося увійти в систему';

  @override
  String get loginSuccess => 'Реєстрацію успішно завершено!';

  @override
  String get reportAsLegitimate => 'Звітуйте як легітимні';

  @override
  String get reportAsSpam => 'Повідомити про спам';

  @override
  String get viewOnPhoneBlockMenu => 'Переглянути на PhoneBlock';

  @override
  String get deleteCall => 'Видалити';

  @override
  String get report => 'Звіт';

  @override
  String get notLoggedIn => 'Ви не зареєстровані. Будь ласка, увійдіть.';

  @override
  String reportedAsLegitimate(String phoneNumber) {
    return '$phoneNumber повідомлено як легітимний';
  }

  @override
  String reportError(String error) {
    return 'Помилка під час звітування: $error';
  }

  @override
  String reportedAsSpam(String phoneNumber) {
    return '$phoneNumber повідомлено як СПАМ';
  }

  @override
  String get selectSpamCategory => 'Виберіть категорію СПАМу';

  @override
  String get errorDeletingAllCalls => 'Помилка при видаленні всіх викликів';

  @override
  String get errorDeletingCall => 'Помилка при скасуванні виклику';

  @override
  String get notLoggedInShort => 'Не зареєстровано';

  @override
  String get errorOpeningPhoneBlock => 'Помилка при відкритті PhoneBlock.';

  @override
  String get permissionNotGranted => 'Дозвіл не надано.';

  @override
  String get setupTitle => 'PhoneBlock Mobile - Налаштування';

  @override
  String get welcome => 'Ласкаво просимо до';

  @override
  String get connectPhoneBlockAccount =>
      'Підключіть обліковий запис PhoneBlock';

  @override
  String get permissions => 'Дозволи';

  @override
  String get allowCallFiltering => 'Дозвольте фільтрувати дзвінки';

  @override
  String get done => 'Готово.';

  @override
  String get setupComplete => 'Встановлення завершено';

  @override
  String get minReportsCount => 'Мінімальна кількість повідомлень';

  @override
  String callsBlockedAfterReports(int count) {
    return 'Дзвінки блокуються з повідомлень $count';
  }

  @override
  String rangesBlockedAfterReports(int count) {
    return 'Області блокуються, починаючи з $count повідомлень і далі';
  }

  @override
  String get welcomeMessage =>
      'Ласкаво просимо до PhoneBlock Mobile!\n\nЦя програма допоможе вам автоматично блокувати спам-дзвінки. Вам потрібен безкоштовний обліковий запис на PhoneBlock.net.\n\nПідключіть свій обліковий запис PhoneBlock, щоб продовжити:';

  @override
  String get connectToPhoneBlock => 'Підключіться до PhoneBlock';

  @override
  String get connectedToPhoneBlock => 'Підключено до PhoneBlock';

  @override
  String get accountConnectedSuccessfully =>
      '✓ Обліковий запис успішно підключено';

  @override
  String get permissionsMessage =>
      'Щоб автоматично блокувати спам-дзвінки, PhoneBlock Mobile вимагає авторизації для перевірки вхідних дзвінків.\n\nЦя авторизація необхідна для роботи програми:';

  @override
  String get permissionGranted => 'Дозвіл надано';

  @override
  String get permissionGrantedSuccessfully => '✓ Авторизація успішно надана';

  @override
  String get setupCompleteMessage =>
      'Встановлення завершено!\n\nPhoneBlock Mobile тепер готовий до блокування спам-дзвінків. Додаток автоматично перевіряє вхідні дзвінки та блокує відомі спам-номера на основі бази даних PhoneBlock.\n\nНатисніть \"Готово\", щоб перейти до головного вікна.';

  @override
  String get verifyingLoginTitle => 'Перевірте логін';

  @override
  String get loginSuccessMessage => 'Успішний вхід!';

  @override
  String get redirectingToSetup => 'Пересилання на об\'єкт...';

  @override
  String tokenVerificationFailed(String error) {
    return 'Не вдалося перевірити токен: $error';
  }

  @override
  String get backToSetup => 'Повертаємося на об\'єкт';

  @override
  String get tokenBeingVerified => 'Токен перевірено...';

  @override
  String get failedToOpenPhoneBlock => 'Не вдалося відкрити PhoneBlock.';

  @override
  String get ratingLegitimate => 'Легітимний';

  @override
  String get ratingAdvertising => 'Реклама';

  @override
  String get ratingSpam => 'СПАМ';

  @override
  String get ratingPingCall => 'Пінг-дзвінок';

  @override
  String get ratingGamble => 'Конкуренція';

  @override
  String get ratingFraud => 'Шахрайство';

  @override
  String get ratingPoll => 'Опитування';

  @override
  String get noLoginTokenReceived => 'Токен для входу не отримано.';

  @override
  String get settingSaved => 'Налаштування збережено';

  @override
  String get errorSaving => 'Помилка при збереженні';

  @override
  String ratePhoneNumber(String phoneNumber) {
    return 'Оцініть $phoneNumber';
  }

  @override
  String reportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count повідомлень',
      one: '1 Повідомлення',
    );
    return '$_temp0';
  }

  @override
  String legitimateReportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Легітимні повідомлення',
      one: '1 Легітимне повідомлення',
    );
    return '$_temp0';
  }

  @override
  String get noReports => 'Немає повідомлень';

  @override
  String todayTime(String time) {
    return 'Сьогодні, $time';
  }

  @override
  String yesterdayTime(String time) {
    return 'Вчора, $time';
  }

  @override
  String get callHistoryRetention => 'Зберігання історії дзвінків';

  @override
  String retentionPeriodDescription(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: 'Зберігати дзвінки $days днів',
      one: 'Зберігайте дзвінки 1 день',
    );
    return '$_temp0';
  }

  @override
  String get retentionInfinite => 'Тримайте всі дзвінки';

  @override
  String retentionDays(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: '$days дні',
      one: '1 день',
    );
    return '$_temp0';
  }

  @override
  String get retentionInfiniteOption => 'Необмежена';

  @override
  String get addCommentSpam => 'Додати коментар (необов\'язково)';

  @override
  String get commentHintSpam =>
      'Чому це спам? З якого приводу дзвонили? Будь ласка, залишайтеся ввічливими.';

  @override
  String get addCommentLegitimate => 'Додати коментар (необов\'язково)';

  @override
  String get commentHintLegitimate =>
      'Чому це законно? Хто вам дзвонив? Будь ласка, будьте ввічливі.';

  @override
  String get serverSettings => 'Налаштування сервера';

  @override
  String get serverSettingsDescription =>
      'Керування налаштуваннями облікового запису PhoneBlock';

  @override
  String get searchNumber => 'Номер пошуку';

  @override
  String get searchPhoneNumber => 'Пошук за номером телефону';

  @override
  String get enterPhoneNumber => 'Введіть номер телефону';

  @override
  String get phoneNumberHint => 'наприклад, +49 123 456789';

  @override
  String get search => 'Пошук';

  @override
  String get invalidPhoneNumber => 'Будь ласка, введіть дійсний номер телефону';

  @override
  String get blacklistTitle => 'Чорний список';

  @override
  String get blacklistDescription => 'Номери, заблоковані вами';

  @override
  String get whitelistTitle => 'Білий список';

  @override
  String get whitelistDescription => 'Номери, які ви позначили як легітимні';

  @override
  String get blacklistEmpty => 'Ваш чорний список порожній';

  @override
  String get whitelistEmpty => 'Ваш білий список порожній';

  @override
  String get blacklistEmptyHelp =>
      'Додайте номери, повідомляючи про небажані дзвінки як про спам.';

  @override
  String get whitelistEmptyHelp =>
      'Додавайте номери, повідомляючи про заблоковані дзвінки як легітимні.';

  @override
  String get errorLoadingList => 'Помилка завантаження списку';

  @override
  String get numberRemovedFromList => 'Номер видалено';

  @override
  String get errorRemovingNumber => 'Помилка при видаленні номера';

  @override
  String get confirmRemoval => 'Підтвердити видалення';

  @override
  String confirmRemoveFromBlacklist(Object phone) {
    return 'Видалити $phone з чорного списку?';
  }

  @override
  String confirmRemoveFromWhitelist(Object phone) {
    return 'Видалити $phone з білого списку?';
  }

  @override
  String get remove => 'Видалити';

  @override
  String get retry => 'Спробуйте ще раз.';

  @override
  String get editComment => 'Редагувати коментар';

  @override
  String get commentLabel => 'Коментар';

  @override
  String get commentHint => 'Додайте примітку до цього номера';

  @override
  String get save => 'Зберегти';

  @override
  String get commentUpdated => 'Коментар оновлено';

  @override
  String get errorUpdatingComment => 'Помилка при оновленні коментаря';

  @override
  String get appearance => 'Зовнішній вигляд';

  @override
  String get themeMode => 'Дизайн';

  @override
  String get themeModeDescription => 'Виберіть світлий або темний дизайн';

  @override
  String get themeModeSystem => 'Системний стандарт';

  @override
  String get themeModeLight => 'Світло';

  @override
  String get themeModeDark => 'Темнота.';
}
