// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Arabic (`ar`).
class AppLocalizationsAr extends AppLocalizations {
  AppLocalizationsAr([String locale = 'ar']) : super(locale);

  @override
  String get appTitle => 'جهاز الرد على المكالمات الهاتفية';

  @override
  String get yourAnswerbots => 'جهاز الرد الآلي الخاص بك';

  @override
  String get loginRequired => 'التسجيل مطلوب';

  @override
  String get login => 'تسجيل الدخول';

  @override
  String get loadingData => 'تحميل البيانات...';

  @override
  String get refreshingData => 'تحديث البيانات...';

  @override
  String get noAnswerbotsYet =>
      'ليس لديك جهاز رد آلي حتى الآن، انقر على زر علامة الجمع أدناه لإنشاء جهاز رد آلي للرد على الهاتف.';

  @override
  String get createAnswerbot => 'إنشاء جهاز الرد على المكالمات';

  @override
  String answerbotName(String userName) {
    return 'جهاز الرد الآلي $userName';
  }

  @override
  String answerbotStats(int newCalls, int talkTimeSeconds, int callsAccepted) {
    return '$newCalls مكالمات جديدة، $callsAccepted مكالمات، $talkTimeSeconds إجمالي وقت التحدث';
  }

  @override
  String get statusActive => 'نشط';

  @override
  String get statusConnecting => 'الاتصال...';

  @override
  String get statusDisabled => 'تم إيقاف التشغيل';

  @override
  String get statusIncomplete => 'غير مكتمل';

  @override
  String get deleteAnswerbot => 'حذف جهاز الرد على المكالمات';

  @override
  String get enabled => 'مفعل';

  @override
  String get minVotes => 'الحد الأدنى من الأصوات';

  @override
  String get minVotesDescription =>
      'ما هو الحد الأدنى لعدد الأصوات المطلوبة لقبول الرقم من قبل جهاز الرد الآلي؟';

  @override
  String get minVotes2 => '2 - القفل على الفور';

  @override
  String get minVotes4 => '4 - انتظر التأكيد';

  @override
  String get minVotes10 => '10 - فقط في حالة الأمان';

  @override
  String get minVotes100 => '100 - كبار مرسلي البريد العشوائي فقط';

  @override
  String get cannotChangeWhileEnabled =>
      'لا يمكن تغييره إلا عند إيقاف تشغيل جهاز الرد الآلي.';

  @override
  String get saveSettings => 'حفظ الإعدادات';

  @override
  String get retentionPeriod => 'وقت التخزين';

  @override
  String get retentionPeriodDescription => 'إلى متى يجب الاحتفاظ بالمكالمات؟';

  @override
  String get retentionNever => 'لا تحذف أبدًا';

  @override
  String get retentionWeek => 'حذف بعد 1 أسبوع';

  @override
  String get retentionMonth => 'حذف بعد 1 شهر';

  @override
  String get retentionQuarter => 'حذف بعد 3 أشهر';

  @override
  String get retentionYear => 'الحذف بعد 1 سنة';

  @override
  String get saveRetentionSettings => 'حفظ إعدادات التخزين';

  @override
  String get showHelp => 'عرض المساعدة';

  @override
  String get newAnswerbot => 'جهاز الرد الآلي الجديد';

  @override
  String get usePhoneBlockDynDns => 'استخدام PhoneBlock DynDNS';

  @override
  String get dynDnsDescription =>
      'يحتاج PhoneBlock إلى معرفة عنوان الإنترنت الخاص بصندوق Fritz! الخاص بك من أجل قبول المكالمات.';

  @override
  String get setupPhoneBlockDynDns => 'إعداد PhoneBlock DynDNS';

  @override
  String get domainName => 'اسم النطاق';

  @override
  String get domainNameHint =>
      'أدخل اسم المجال الخاص بـ Fritz! إذا لم يكن لدى Fritz!Box الخاص بك اسم مجال، فقم بتفعيل PhoneBlock-DynDNS.';

  @override
  String get checkDomainName => 'التحقق من أسماء النطاقات';

  @override
  String get setupDynDns => 'إعداد DynDNS';

  @override
  String get dynDnsInstructions =>
      'في إعدادات Fritz!Box، افتح الصفحة الإنترنت > المشاركات > DynDNS وأدخل القيم التالية هناك:';

  @override
  String get checkDynDns => 'تحقق من DynDNS';

  @override
  String get createAnswerbotTitle => 'إنشاء جهاز الرد على المكالمات';

  @override
  String get registerAnswerbot => 'تسجيل جهاز الرد الآلي';

  @override
  String get answerbotRegistered => 'جهاز الرد الآلي المسجل';

  @override
  String get close => 'إغلاق';

  @override
  String get error => 'خطأ';

  @override
  String cannotLoadInfo(String message, int statusCode) {
    return 'لا يمكن استرداد المعلومات (الخطأ $statusCode): <x2>الرسالة<x2>';
  }

  @override
  String wrongContentType(String contentType) {
    return 'لا يمكن استرجاع المعلومات (نوع المحتوى: <x1> نوع المحتوى<x1>).';
  }

  @override
  String get callList => 'قائمة المكالمات';

  @override
  String get clearCallList => 'حذف قائمة المكالمات';

  @override
  String get noCalls => 'لا توجد مكالمات حتى الآن';

  @override
  String get answerbot => 'جهاز الرد الآلي';

  @override
  String get answerbotSettings => 'إعدادات جهاز الرد على المكالمات';

  @override
  String get minConfidence => 'الحد الأدنى من الثقة';

  @override
  String get minConfidenceHelp =>
      'كم عدد الشكاوى اللازمة لكي يتم اعتراض الرقم من قبل جهاز الرد الآلي.';

  @override
  String get blockNumberRanges => 'نطاقات أرقام المربع';

  @override
  String get blockNumberRangesHelp =>
      'قبول المكالمة حتى بالنسبة للرقم الذي لم يُعرف بعد أنه رسالة اقتحامية إذا كان هناك سبب للاشتباه في أن الرقم ينتمي إلى اتصال نظام تنشأ منه الرسائل الاقتحامية.';

  @override
  String get preferIPv4 => 'تفضيل اتصال IPv4';

  @override
  String get preferIPv4Help =>
      'في حالة توفره، يتم التعامل مع الاتصال بجهاز الرد الآلي عبر IPv4. يبدو أن هناك اتصالات هاتفية لا يمكن الاتصال الصوتي بها عبر IPv6 على الرغم من توفر عنوان IPv6.';

  @override
  String get callRetention => 'الاحتفاظ بالمكالمات';

  @override
  String get automaticDeletion => 'الحذف التلقائي';

  @override
  String get automaticDeletionHelp =>
      'بعد أي وقت يجب حذف سجلات المكالمات القديمة تلقائياً؟ عدم الحذف مطلقاً يعطل الحذف التلقائي.';

  @override
  String get dnsSettings => 'إعدادات DNS';

  @override
  String get dnsSetting => 'إعداد DNS';

  @override
  String get phoneBlockDns => 'PhoneBlock DNS';

  @override
  String get otherProviderOrDomain => 'مزود آخر أو اسم نطاق آخر';

  @override
  String get dnsSettingHelp =>
      'كيف يجد جهاز الرد على المكالمات الهاتفية صندوق فريتز!';

  @override
  String get updateUrl => 'تحديث عنوان URL';

  @override
  String get updateUrlHelp => 'اسم المستخدم لمشاركة DynDNS في Fritz الخاص بك!';

  @override
  String get domainNameHelp => 'الاسم الذي يستقبله صندوق Fritz! على الإنترنت.';

  @override
  String get dyndnsUsername => 'اسم مستخدم DynDNS';

  @override
  String get dyndnsUsernameHelp =>
      'اسم المستخدم لمشاركة DynDNS في Fritz الخاص بك!';

  @override
  String get dyndnsPassword => 'كلمة مرور DynDNS';

  @override
  String get dyndnsPasswordHelp =>
      'كلمة المرور التي يجب أن تستخدمها لمشاركة DynDNS.';

  @override
  String get host => 'المضيف';

  @override
  String get hostHelp =>
      'اسم المضيف الذي يمكن من خلاله الوصول إلى Fritz!Box الخاص بك من الإنترنت.';

  @override
  String get sipSettings => 'إعدادات SIP';

  @override
  String get user => 'المستخدم';

  @override
  String get userHelp =>
      'اسم المستخدم الذي يجب إعداده في Fritz!Box للوصول إلى الجهاز الهاتفي.';

  @override
  String get password => 'كلمة المرور';

  @override
  String get passwordHelp =>
      'كلمة المرور التي يجب تعيينها للدخول إلى الجهاز الهاتفي في Fritz!';

  @override
  String get savingSettings => 'حفظ الإعدادات...';

  @override
  String get errorSavingSettings => 'خطأ عند حفظ الإعدادات.';

  @override
  String savingFailed(String message) {
    return 'فشل الحفظ: $message';
  }

  @override
  String get enableAfterSavingFailed => 'تم التشغيل مرة أخرى بعد فشل الحفظ';

  @override
  String get enablingAnswerbot => 'قم بتشغيل جهاز الرد الآلي...';

  @override
  String get errorEnablingAnswerbot => 'خطأ عند تشغيل جهاز الرد الآلي.';

  @override
  String cannotEnable(String message) {
    return 'لا يمكن تشغيل: <x1>رسالة<x1>';
  }

  @override
  String get enablingFailed => 'فشل تشغيل جهاز الرد الآلي';

  @override
  String enablingFailedMessage(String message) {
    return 'فشل التشغيل: <x1>رسالة<x1>';
  }

  @override
  String retryingMessage(String message) {
    return '$message حاول مرة أخرى...';
  }

  @override
  String get savingRetentionSettings => 'حفظ إعدادات التخزين...';

  @override
  String get errorSavingRetentionSettings => 'خطأ عند حفظ إعدادات التخزين.';

  @override
  String get automaticDeletionDisabled => 'تم إلغاء تنشيط الحذف التلقائي';

  @override
  String retentionSettingsSaved(String period) {
    return 'إعدادات التخزين المحفوظة (<x1>الفترة <x1>)';
  }

  @override
  String get oneWeek => '1 أسبوع';

  @override
  String get oneMonth => '1 شهر';

  @override
  String get threeMonths => '3 أشهر';

  @override
  String get oneYear => '1 سنة';

  @override
  String get never => 'أبداً';

  @override
  String deleteAnswerbotConfirm(String userName) {
    return 'هل يجب بالفعل حذف جهاز الرد الآلي $userName؟';
  }

  @override
  String get cancel => 'إلغاء';

  @override
  String get delete => 'حذف';

  @override
  String get deletionFailed => 'فشل الحذف';

  @override
  String get answerbotCouldNotBeDeleted => 'تعذر حذف جهاز الرد الآلي';

  @override
  String get spamCalls => 'مكالمات الرسائل الاقتحامية';

  @override
  String get deleteCalls => 'حذف المكالمات';

  @override
  String get deletingCallsFailed => 'فشل الحذف';

  @override
  String get deleteRequestFailed => 'تعذرت معالجة طلب الحذف.';

  @override
  String cannotRetrieveCalls(String message, int statusCode) {
    return 'لا يمكن استرداد المكالمات (الخطأ $statusCode): <x2>الرسالة<x2>';
  }

  @override
  String get noNewCalls => 'لا توجد مكالمات جديدة.';

  @override
  String duration(int seconds) {
    return 'المدة $seconds ثانية';
  }

  @override
  String today(String time) {
    return 'اليوم $time';
  }

  @override
  String yesterday(String time) {
    return 'الأمس $time';
  }

  @override
  String get dynDnsDescriptionLong =>
      'يحتاج PhoneBlock إلى معرفة عنوان الإنترنت الخاص بصندوق فريتز! من أجل تسجيل جهاز الرد الآلي على صندوق فريتز! إذا كنت قد قمت بالفعل بإعداد MyFRITZ! أو مزود DynDNS آخر، يمكنك استخدام اسم النطاق هذا. إذا لم يكن كذلك، يمكنك ببساطة إعداد DynDNS من PhoneBlock، ثم تفعيل هذا المفتاح.';

  @override
  String get setupPhoneBlockDynDnsSnackbar => 'قم بإعداد PhoneBlock DynDNS.';

  @override
  String get setupFailed => 'فشل الإعداد';

  @override
  String cannotSetupDynDns(String message) {
    return 'يتعذر إعداد DynDNS: $message';
  }

  @override
  String get domainname => 'اسم النطاق';

  @override
  String get domainNameHintLong =>
      'اسم نطاق صندوق فريتز! (إما عنوان MyFRITZ! أو اسم نطاق DynDNS)';

  @override
  String get inputCannotBeEmpty => 'يجب ألا تكون المدخلات فارغة.';

  @override
  String get invalidDomainName => 'لا يوجد اسم نطاق صالح.';

  @override
  String get domainNameTooLong => 'اسم النطاق طويل جداً.';

  @override
  String get domainNameHintExtended =>
      'أدخل اسم النطاق الخاص بـ Fritz! إذا لم يكن لدى Fritz!Box الخاص بك اسم مجال، فقم بتفعيل PhoneBlock DynDNS. يمكنك العثور على اسم المجال الخاص بـ Fritz!Box الخاص بك تحت (ضمن الإنترنت > المشاركات > DynDNS). بدلاً من ذلك، يمكنك أيضًا إدخال عنوان MyFRITZ! (تحت الإنترنت > حساب MyFRITZ!)، على سبيل المثال z4z...l4n.myfritz.net.';

  @override
  String get checkingDomainName => 'تحقق من أسماء النطاقات.';

  @override
  String domainNameNotAccepted(String message) {
    return 'لم يتم قبول اسم النطاق: $message';
  }

  @override
  String get dynDnsInstructionsLong =>
      'افتح صفحة الإنترنت > المشاركات > صفحة DynDNS في إعدادات Fritz!Box وأدخل المعلومات الواردة هنا.';

  @override
  String get updateUrlHelp2 =>
      'عنوان URL الذي يستدعيه صندوق Fritz! لإعطاء PhoneBlock عنوان الإنترنت الخاص به. أدخل عنوان URL تماماً كما هو مكتوب هنا. لا تستبدل القيم الموجودة في أقواس الزوايا، فسيقوم صندوق Fritz! box الخاص بك بذلك تلقائيًا عند استدعائه. من الأفضل استخدام وظيفة النسخ لنسخ القيم.';

  @override
  String get domainNameHelp2 =>
      'لا يمكن حل اسم النطاق هذا بشكل علني لاحقاً. سيتم مشاركة عنوان الإنترنت الخاص بك مع PhoneBlock فقط.';

  @override
  String get username => 'اسم المستخدم';

  @override
  String get usernameHelp =>
      'اسم المستخدم الذي يسجل به صندوق Fritz! box الخاص بك الدخول إلى PhoneBlock لإعلان عنوان الإنترنت الخاص به.';

  @override
  String get passwordLabel => 'كلمة المرور';

  @override
  String get passwordHelp2 =>
      'كلمة المرور التي تقوم من خلالها بتسجيل الدخول إلى PhoneBlock لإظهار عنوان الإنترنت الخاص به. لأسباب أمنية، لا يمكنك إدخال كلمة المرور الخاصة بك، ولكن يجب عليك استخدام كلمة المرور التي تم إنشاؤها بشكل آمن بواسطة PhoneBlock.';

  @override
  String get checkingDynDns => 'تحقق من إعداد DynDNS.';

  @override
  String get notRegistered => 'غير مسجل';

  @override
  String fritzBoxNotRegistered(String message) {
    return 'لم يتم تسجيل Fritz!Box الخاص بك في PhoneBlock، لم يتم تحديث DynDNS: $message';
  }

  @override
  String get sipSetupInstructions =>
      'الآن قم بإعداد جهاز الرد الآلي PhoneBlock كـ \"هاتف (مع جهاز الرد الآلي وبدونه)\". للتأكد من أن هذا يعمل، يرجى اتباع الخطوات أدناه بالضبط:';

  @override
  String get sipSetupStep1 =>
      '1. افتح صفحة الاتصال الهاتفي > الأجهزة الهاتفية في إعدادات فريتز! بوكس وانقر على زر \"إعداد جهاز جديد\".';

  @override
  String get sipSetupStep2 =>
      '2. حدد الخيار \"الهاتف (مع جهاز الرد الآلي وبدونه)\" وانقر على \"التالي\".';

  @override
  String get sipSetupStep3 =>
      '3. حدد الخيار \"LAN/WLAN (هاتف IP)\"، وأطلق على الهاتف اسم \"PhoneBlock\" وانقر على \"التالي\".';

  @override
  String get sipSetupStep4 =>
      '4. أدخل الآن اسم المستخدم وكلمة المرور التاليين لجهاز الرد الآلي الخاص بك ثم انقر على \"التالي\".';

  @override
  String get usernameHelp2 =>
      'اسم المستخدم الذي يقوم جهاز الرد على المكالمات الهاتفية PhoneBlock بتسجيل الدخول به إلى جهاز الرد الآلي فريتز!';

  @override
  String get passwordHelp3 =>
      'كلمة المرور التي يستخدمها جهاز الرد الآلي PhoneBlock لتسجيل الدخول إلى جهازك فريتز! قام PhoneBlock بإنشاء كلمة مرور آمنة لك.';

  @override
  String get sipSetupStep5 =>
      '5. لا يهم رقم الهاتف الذي تم الاستعلام عنه الآن، لا يقوم جهاز الرد الآلي PhoneBlock بإجراء أي مكالمات بشكل فعال، ولكنه يقبل فقط المكالمات غير المرغوب فيها. يتم إلغاء تحديد رقم الهاتف مرة أخرى في الخطوة 9. ما عليك سوى النقر على \"التالي\" هنا.';

  @override
  String get sipSetupStep6 =>
      '6- حدد \"قبول جميع المكالمات\" وانقر على \"التالي\". لا يقبل جهاز الرد الآلي PhoneBlock المكالمات على أي حال إلا إذا كان رقم المتصل مدرجًا في قائمة الحظر. في الوقت نفسه، لا يقبل PhoneBlock أبدًا المكالمات الواردة من الأرقام الموجودة في دفتر هاتفك العادي.';

  @override
  String get sipSetupStep7 =>
      '7. سترى ملخصًا. اكتملت الإعدادات (تقريبًا)، انقر على \"تطبيق\".';

  @override
  String get sipSetupStep8 =>
      '8. سترى الآن \"PhoneBlock\" في قائمة الأجهزة الهاتفية. لا تزال هناك بعض الإعدادات المفقودة التي يمكنك إجراؤها لاحقًا فقط. لذلك، انقر على قلم التحرير في سطر جهاز الرد على المكالمات الهاتفية \"PhoneBlock\".';

  @override
  String get sipSetupStep9 =>
      '9. حدد الخيار الأخير (فارغ) في حقل \"المكالمات الصادرة\"، حيث أن PhoneBlock لا يقوم بإجراء مكالمات صادرة أبدًا، وبالتالي فإن جهاز الرد الآلي لا يتطلب رقمًا للمكالمات الصادرة.';

  @override
  String get sipSetupStep10 =>
      '10. حدد علامة التبويب \"بيانات تسجيل الدخول\". قم بتأكيد الاستجابة بالنقر على \"تطبيق\". حدّد الآن الخيار \"السماح بتسجيل الدخول من الإنترنت\" حتى يتمكن جهاز الرد الآلي للرد على المكالمات الهاتفية من PhoneBlock من سحابة PhoneBlock من تسجيل الدخول إلى جهازك فريتز! يجب إدخال كلمة مرور جهاز الرد الآلي (انظر أعلاه) مرة أخرى في حقل \"كلمة المرور\" قبل النقر على \"تطبيق\". احذف أولاً العلامات النجمية في الحقل.';

  @override
  String get sipSetupStep11 =>
      '11- تظهر لك رسالة تحذرك من إمكانية إنشاء اتصالات مشحونة عبر الإنترنت. يمكنك التأكد من ذلك بثقة، أولاً لأن PhoneBlock لا ينشئ اتصالات نشطة أبدًا، وثانيًا لأن PhoneBlock قد أنشأ كلمة مرور آمنة لك (انظر أعلاه) حتى لا يتمكن أي شخص آخر من الاتصال وثالثًا لأنك قمت بإلغاء تنشيط الاتصالات الصادرة في الخطوة 9. اعتمادًا على إعدادات جهاز فريتز! بوكس الخاص بك، قد تحتاج إلى تأكيد الإعداد على هاتف DECT المتصل مباشرةً بجهاز فريتز!';

  @override
  String get sipSetupStep12 =>
      '12. الآن تم كل شيء. انقر على رجوع للعودة إلى قائمة الأجهزة الهاتفية. يمكنك الآن تفعيل جهاز الرد على المكالمات الهاتفية باستخدام الزر الموجود في الأسفل.';

  @override
  String get tryingToRegisterAnswerbot => 'حاول تسجيل جهاز الرد الآلي...';

  @override
  String get answerbotRegistrationFailed => 'فشل تسجيل جهاز الرد الآلي';

  @override
  String registrationFailed(String message) {
    return 'فشل التسجيل: $message';
  }

  @override
  String get answerbotRegisteredSuccess =>
      'لقد تم تسجيل جهاز الرد الآلي PhoneBlock الخاص بك بنجاح. يمكن الآن للمتصلين التالين غير المرغوب فيهم التحدث إلى \"PhoneBlock\". إذا كنت ترغب في اختبار جهاز الرد الآلي PhoneBlock بنفسك، اطلب الرقم الداخلي لجهاز \"PhoneBlock\" الهاتفي الذي قمت بإعداده. يبدأ الرقم الداخلي عادةً بحرف \"**\".';
}
