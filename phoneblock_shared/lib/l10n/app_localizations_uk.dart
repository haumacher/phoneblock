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

  @override
  String get answerbot => 'Автовідповідач';

  @override
  String get answerbotSettings => 'Налаштування автовідповідача';

  @override
  String get minConfidence => 'Мінімальна довіра';

  @override
  String get minConfidenceHelp =>
      'Скільки скарг необхідно для того, щоб номер перехопив автовідповідач.';

  @override
  String get blockNumberRanges => 'Діапазони номерів блоків';

  @override
  String get blockNumberRangesHelp =>
      'Приймає виклик навіть на номер, про який ще не відомо, що він є спамом, якщо є підстави підозрювати, що номер належить до системного з\'єднання, з якого надходить спам.';

  @override
  String get preferIPv4 => 'Надавайте перевагу IPv4-зв\'язку';

  @override
  String get preferIPv4Help =>
      'Якщо доступно, зв\'язок з автовідповідачем здійснюється через IPv4. Існують телефонні з\'єднання, для яких голосове з\'єднання через IPv6 неможливе, хоча IPv6-адреса доступна.';

  @override
  String get callRetention => 'Утримання дзвінків';

  @override
  String get automaticDeletion => 'Автоматичне видалення';

  @override
  String get automaticDeletionHelp =>
      'Через який час старі журнали викликів повинні автоматично видалятися? Ніколи не видаляти деактивує автоматичне видалення.';

  @override
  String get dnsSettings => 'Налаштування DNS';

  @override
  String get dnsSetting => 'Налаштування DNS';

  @override
  String get phoneBlockDns => 'DNS PhoneBlock';

  @override
  String get otherProviderOrDomain => 'Інший провайдер або доменне ім\'я';

  @override
  String get dnsSettingHelp =>
      'Як автовідповідач знаходить вашу скриньку Fritz! в Інтернеті.';

  @override
  String get updateUrl => 'Оновлення URL-адреси';

  @override
  String get updateUrlHelp =>
      'Ім\'я користувача для ресурсу DynDNS у вашому Fritz!';

  @override
  String get domainNameHelp =>
      'Назвіть ім\'я, яке ваша скринька Fritz! отримує в Інтернеті.';

  @override
  String get dyndnsUsername => 'Ім\'я користувача DynDNS';

  @override
  String get dyndnsUsernameHelp =>
      'Ім\'я користувача для ресурсу DynDNS у вашому Fritz!';

  @override
  String get dyndnsPassword => 'Пароль DynDNS';

  @override
  String get dyndnsPasswordHelp =>
      'Пароль, який ви повинні використовувати для спільного доступу до DynDNS.';

  @override
  String get host => 'Ведучий';

  @override
  String get hostHelp =>
      'Ім\'я хоста, за допомогою якого можна отримати доступ до вашого Fritz!Box з Інтернету.';

  @override
  String get sipSettings => 'Налаштування SIP';

  @override
  String get user => 'Користувач';

  @override
  String get userHelp =>
      'Ім\'я користувача, яке необхідно налаштувати у Fritz!Box для доступу до пристрою телефонії.';

  @override
  String get password => 'Пароль';

  @override
  String get passwordHelp =>
      'Пароль, який необхідно призначити для доступу до пристрою телефонії у Fritz!';

  @override
  String get savingSettings => 'Зберегти налаштування...';

  @override
  String get errorSavingSettings => 'Помилка при збереженні налаштувань.';

  @override
  String savingFailed(String message) {
    return 'Помилка збереження: $message';
  }

  @override
  String get enableAfterSavingFailed =>
      'Повторне ввімкнення після невдалого збереження';

  @override
  String get enablingAnswerbot => 'Увімкнути автовідповідач...';

  @override
  String get errorEnablingAnswerbot => 'Помилка при включенні автовідповідача.';

  @override
  String cannotEnable(String message) {
    return 'Не вдається увімкнути: $message';
  }

  @override
  String get enablingFailed => 'Не вдалося увімкнути автовідповідач';

  @override
  String enablingFailedMessage(String message) {
    return 'Не вдалося увімкнути: $message';
  }

  @override
  String retryingMessage(String message) {
    return '$message Спробуйте ще раз...';
  }

  @override
  String get savingRetentionSettings => 'Зберегти налаштування сховища...';

  @override
  String get errorSavingRetentionSettings =>
      'Помилка при збереженні налаштувань сховища.';

  @override
  String get automaticDeletionDisabled => 'Автоматичне видалення вимкнено';

  @override
  String retentionSettingsSaved(String period) {
    return 'Збережено налаштування зберігання ($period)';
  }

  @override
  String get oneWeek => '1 тиждень';

  @override
  String get oneMonth => '1 місяць';

  @override
  String get threeMonths => '3 місяці';

  @override
  String get oneYear => '1 рік';

  @override
  String get never => 'Ніколи.';

  @override
  String deleteAnswerbotConfirm(String userName) {
    return 'Чи дійсно слід видаляти автовідповідач $userName?';
  }

  @override
  String get cancel => 'Скасувати';

  @override
  String get delete => 'Видалити';

  @override
  String get deletionFailed => 'Видалення не вдалося';

  @override
  String get answerbotCouldNotBeDeleted => 'Автовідповідач не вдалося видалити';

  @override
  String get spamCalls => 'СПАМ-дзвінки';

  @override
  String get deleteCalls => 'Видалення дзвінків';

  @override
  String get deletingCallsFailed => 'Видалення не вдалося';

  @override
  String get deleteRequestFailed => 'Запит на видалення не вдалося обробити.';

  @override
  String cannotRetrieveCalls(String message, int statusCode) {
    return 'Дзвінки не можуть бути отримані (помилка $statusCode): $message';
  }

  @override
  String get noNewCalls => 'Ніяких нових дзвінків.';

  @override
  String duration(int seconds) {
    return 'Тривалість $seconds с';
  }

  @override
  String today(String time) {
    return 'Сьогодні $time';
  }

  @override
  String yesterday(String time) {
    return 'Вчора $time';
  }

  @override
  String get dynDnsDescriptionLong =>
      'PhoneBlock повинен знати інтернет-адресу вашої Fritz! скриньки, щоб зареєструвати автовідповідач на вашій Fritz! скриньці. Якщо ви вже налаштували MyFRITZ! або іншого провайдера DynDNS, ви можете використовувати це доменне ім\'я. Якщо ні, ви можете просто налаштувати DynDNS з PhoneBlock, а потім активувати цей перемикач.';

  @override
  String get setupPhoneBlockDynDnsSnackbar => 'Налаштуйте PhoneBlock DynDNS.';

  @override
  String get setupFailed => 'Не вдалося налаштувати';

  @override
  String cannotSetupDynDns(String message) {
    return 'Не вдається налаштувати DynDNS: $message';
  }

  @override
  String get domainname => 'Доменне ім\'я';

  @override
  String get domainNameHintLong =>
      'Доменне ім\'я вашої скриньки Fritz! (адреса MyFRITZ! або доменне ім\'я DynDNS)';

  @override
  String get inputCannotBeEmpty => 'Вхідні дані не повинні бути порожніми.';

  @override
  String get invalidDomainName => 'Немає дійсного доменного імені.';

  @override
  String get domainNameTooLong => 'Доменне ім\'я занадто довге.';

  @override
  String get domainNameHintExtended =>
      'Введіть доменне ім\'я вашого Fritz! Якщо ваш Fritz!Box ще не має доменного імені, активуйте PhoneBlock DynDNS. Доменне ім\'я вашого Fritz!Box можна знайти в розділі \"Інтернет\" > \"Акції\" > \"DynDNS\". Крім того, ви також можете ввести адресу MyFRITZ! (Інтернет > Обліковий запис MyFRITZ!), наприклад, z4z...l4n.myfritz.net.';

  @override
  String get checkingDomainName => 'Перевірте доменні імена.';

  @override
  String domainNameNotAccepted(String message) {
    return 'Доменне ім\'я не прийнято: $message';
  }

  @override
  String get dynDnsInstructionsLong =>
      'У налаштуваннях Fritz!Box відкрийте сторінку \"Інтернет\" > \"Ресурси\" > \"DynDNS\" і введіть наведену тут інформацію.';

  @override
  String get updateUrlHelp2 =>
      'URL-адреса, до якої звертається ваш Fritz! box, щоб надати PhoneBlock свою інтернет-адресу. Введіть URL-адресу точно так, як вона написана тут. Не замінюйте значення в кутових дужках, ваш Fritz! box зробить це автоматично під час виклику. Найкраще використовувати функцію копіювання для копіювання значень.';

  @override
  String get domainNameHelp2 =>
      'Це доменне ім\'я не може бути публічно дозволене пізніше. Ваша інтернет-адреса буде передана тільки PhoneBlock.';

  @override
  String get username => 'Ім\'я користувача';

  @override
  String get usernameHelp =>
      'Ім\'я користувача, з яким ваш Fritz! box входить до PhoneBlock, щоб повідомити свою інтернет-адресу.';

  @override
  String get passwordLabel => 'Пароль';

  @override
  String get passwordHelp2 =>
      'Пароль, за допомогою якого ваш Fritz!Box входить до PhoneBlock, щоб зробити свою інтернет-адресу відомою. З міркувань безпеки ви не можете ввести власний пароль, а повинні використовувати пароль, надійно згенерований PhoneBlock.';

  @override
  String get checkingDynDns => 'Перевірте налаштування DynDNS.';

  @override
  String get notRegistered => 'Не зареєстровано';

  @override
  String fritzBoxNotRegistered(String message) {
    return 'Ваш Fritz!Box ще не зареєстрований у PhoneBlock, DynDNS не актуальний: $message';
  }

  @override
  String get sipSetupInstructions =>
      'Тепер налаштуйте автовідповідач PhoneBlock як \"Телефон (з автовідповідачем і без нього)\". Щоб переконатися, що це працює, будь ласка, точно виконайте наведені нижче кроки:';

  @override
  String get sipSetupStep1 =>
      '1. Відкрийте сторінку \"Телефонія\" > \"Пристрої телефонії\" в налаштуваннях Fritz!Box і натисніть кнопку \"Налаштувати новий пристрій\".';

  @override
  String get sipSetupStep2 =>
      '2. виберіть опцію \"Телефон (з автовідповідачем і без)\" і натисніть \"Далі\".';

  @override
  String get sipSetupStep3 =>
      '3. виберіть опцію \"LAN/WLAN (IP-телефон)\", дайте телефону ім\'я \"PhoneBlock\" і натисніть \"Далі\".';

  @override
  String get sipSetupStep4 =>
      '4. Тепер введіть наступне ім\'я користувача та пароль для автовідповідача, а потім натисніть \"Далі\".';

  @override
  String get usernameHelp2 =>
      'Ім\'я користувача, з яким автовідповідач PhoneBlock входить до вашого Fritz!';

  @override
  String get passwordHelp3 =>
      'Пароль, який автовідповідач PhoneBlock використовує для входу до вашого Fritz! PhoneBlock згенерував для вас надійний пароль.';

  @override
  String get sipSetupStep5 =>
      '5. запитуваний номер телефону тепер не має значення, автовідповідач PhoneBlock не здійснює активних дзвінків, а лише приймає СПАМ-дзвінки. Номер телефону знову буде скасовано на кроці 9. Просто натисніть тут \"Далі\".';

  @override
  String get sipSetupStep6 =>
      '6. виберіть \"Приймати всі дзвінки\" і натисніть \"Далі\". Автовідповідач PhoneBlock приймає дзвінки тільки в тому випадку, якщо номер абонента є в списку блокування. Водночас PhoneBlock ніколи не приймає дзвінки з номерів, які є у вашій звичайній телефонній книзі.';

  @override
  String get sipSetupStep7 =>
      '7. Ви побачите підсумок. Налаштування (майже) завершені, натисніть \"Застосувати\".';

  @override
  String get sipSetupStep8 =>
      '8. Тепер ви побачите \"PhoneBlock\" у списку пристроїв телефонії. Ще не вистачає кількох налаштувань, які ви зможете зробити пізніше. Тому натисніть на олівець для редагування в рядку автовідповідача PhoneBlock.';

  @override
  String get sipSetupStep9 =>
      '9. виберіть останню (порожню) опцію в полі \"Вихідні дзвінки\", оскільки PhoneBlock ніколи не здійснює вихідних дзвінків і тому автовідповідач не вимагає номер для вихідних дзвінків.';

  @override
  String get sipSetupStep10 =>
      '10. оберіть вкладку \"Дані для входу\". Підтвердіть відповідь, натиснувши \"Застосувати\". Тепер виберіть опцію \"Дозволити вхід з інтернету\", щоб автовідповідач PhoneBlock з хмари PhoneBlock міг увійти до вашого Fritz! Ви повинні знову ввести пароль автовідповідача (див. вище) у полі \"Пароль\", перш ніж натиснути \"Застосувати\". Спочатку видаліть зірочки в полі.';

  @override
  String get sipSetupStep11 =>
      '11. з\'явиться повідомлення, яке попереджає вас про те, що через доступ до Інтернету можуть бути встановлені платні з\'єднання. Ви можете впевнено підтвердити це, по-перше, тому що PhoneBlock ніколи активно не встановлює з\'єднання, по-друге, тому що PhoneBlock створив для вас безпечний пароль (див. вище), щоб ніхто інший не зміг підключитися, і по-третє, тому що ви деактивували вихідні з\'єднання на кроці 9. Залежно від налаштувань вашого Fritz!Box, вам може знадобитися підтвердити налаштування на DECT-телефоні, підключеному безпосередньо до Fritz!';

  @override
  String get sipSetupStep12 =>
      '12. тепер все готово. Натисніть на кнопку Назад, щоб повернутися до списку пристроїв телефонії. Тепер ви можете активувати автовідповідач за допомогою кнопки внизу.';

  @override
  String get tryingToRegisterAnswerbot =>
      'Спробуйте зареєструвати автовідповідач...';

  @override
  String get answerbotRegistrationFailed =>
      'Не вдалося зареєструвати автовідповідач';

  @override
  String registrationFailed(String message) {
    return 'Реєстрація не вдалася: $message';
  }

  @override
  String get answerbotRegisteredSuccess =>
      'Ваш автовідповідач PhoneBlock успішно зареєстровано. Наступні абоненти спаму тепер можуть розмовляти з PhoneBlock. Якщо ви хочете перевірити автовідповідач PhoneBlock самостійно, наберіть внутрішній номер налаштованого вами пристрою телефонії \"PhoneBlock\". Внутрішній номер зазвичай починається з \"**\".';
}
