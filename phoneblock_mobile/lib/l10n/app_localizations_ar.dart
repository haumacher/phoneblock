// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Arabic (`ar`).
class AppLocalizationsAr extends AppLocalizations {
  AppLocalizationsAr([String locale = 'ar']) : super(locale);

  @override
  String get appTitle => 'فون بلوك موبايل';

  @override
  String get settings => 'الإعدادات';

  @override
  String get deleteAll => 'حذف الكل';

  @override
  String get noCallsYet => 'لم تتم تصفية أي مكالمات حتى الآن';

  @override
  String get noCallsDescription =>
      'سيعمل PhoneBlock على فحص المكالمات الواردة تلقائياً وحظر المكالمات غير المرغوب فيها.';

  @override
  String get blocked => 'محجوب';

  @override
  String get accepted => 'مقبولة';

  @override
  String get missed => 'فائتة';

  @override
  String votes(int count) {
    return '$countالأصوات';
  }

  @override
  String get viewOnPhoneBlock => 'عرض على PhoneBlock';

  @override
  String get confirmDeleteAll => 'حذف جميع المكالمات التي تمت تصفيتها؟';

  @override
  String get confirmDeleteAllMessage => 'لا يمكن التراجع عن هذا الإجراء.';

  @override
  String get cancel => 'إلغاء';

  @override
  String get delete => 'حذف';

  @override
  String get settingsTitle => 'الإعدادات';

  @override
  String get callScreening => 'تصفية المكالمات';

  @override
  String get minSpamReports => 'الحد الأدنى من الرسائل الاقتحامية';

  @override
  String minSpamReportsDescription(int count) {
    return 'يتم حظر الأرقام من $countالرسائل فصاعدًا';
  }

  @override
  String get blockNumberRanges => 'نطاقات أرقام المربع';

  @override
  String get blockNumberRangesDescription =>
      'حظر المناطق التي تحتوي على العديد من الرسائل الاقتحامية';

  @override
  String get minSpamReportsInRange =>
      'الحد الأدنى من الرسائل الاقتحامية في منطقة';

  @override
  String minSpamReportsInRangeDescription(int count) {
    return 'يتم حظر المناطق من $count الرسائل فصاعدًا';
  }

  @override
  String get about => 'نبذة عن';

  @override
  String get version => 'الإصدار';

  @override
  String get developer => 'المطور';

  @override
  String get developerName => 'برنهارد هاوماخر';

  @override
  String get website => 'الموقع الإلكتروني';

  @override
  String get websiteUrl => 'Phoneblock.net';

  @override
  String get sourceCode => 'كود المصدر';

  @override
  String get sourceCodeLicense => 'مفتوح المصدر (GPL-3.0)';

  @override
  String get aboutDescription =>
      'PhoneBlock هو مشروع مفتوح المصدر بدون تتبع وبدون إعلانات. يتم تمويل الخدمة عن طريق التبرعات.';

  @override
  String get donate => 'التبرعات';

  @override
  String pendingCallsNotification(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '<عدد المكالمات الجديدة التي تمت تصفيتها',
      one: '1 مكالمة جديدة تمت تصفيتها',
    );
    return '$_temp0';
  }

  @override
  String get tapToOpen => 'انقر لفتح التطبيق';

  @override
  String get setupWelcome => 'مرحباً بك في PhoneBlock Mobile';

  @override
  String get setupPermissionsRequired => 'التصاريح المطلوبة';

  @override
  String get grantPermission => 'منح التفويض';

  @override
  String get continue_ => 'المزيد';

  @override
  String get finish => 'جاهز';

  @override
  String get loginRequired => 'التسجيل في PhoneBlock';

  @override
  String get loginToPhoneBlock => 'التسجيل مع PhoneBlock';

  @override
  String get verifyingLogin => 'سيتم التحقق من التسجيل...';

  @override
  String get loginFailed => 'فشل تسجيل الدخول';

  @override
  String get loginSuccess => 'تم التسجيل بنجاح!';

  @override
  String get reportAsLegitimate => 'تقرير شرعي';

  @override
  String get reportAsSpam => 'الإبلاغ عن الرسائل الاقتحامية';

  @override
  String get viewOnPhoneBlockMenu => 'عرض على PhoneBlock';

  @override
  String get deleteCall => 'حذف';

  @override
  String get report => 'تقرير';

  @override
  String get notLoggedIn => 'غير مسجل. يرجى تسجيل الدخول';

  @override
  String reportedAsLegitimate(String phoneNumber) {
    return '$phoneNumber الذي تم الإبلاغ عنه على أنه شرعي';
  }

  @override
  String reportError(String error) {
    return 'خطأ عند الإبلاغ: $error';
  }

  @override
  String reportedAsSpam(String phoneNumber) {
    return '$phoneNumber الذي تم الإبلاغ عنه كرسالة غير مرغوب فيها';
  }

  @override
  String get selectSpamCategory => 'حدد فئة الرسائل الاقتحامية';

  @override
  String get errorDeletingAllCalls => 'خطأ عند حذف جميع المكالمات';

  @override
  String get errorDeletingCall => 'خطأ عند إلغاء المكالمة';

  @override
  String get notLoggedInShort => 'غير مسجل';

  @override
  String get errorOpeningPhoneBlock => 'خطأ في فتح PhoneBlock.';

  @override
  String get permissionNotGranted => 'لم يتم منح التفويض.';

  @override
  String get setupTitle => 'فون بلوك موبايل - الإعداد';

  @override
  String get welcome => 'مرحباً بك في';

  @override
  String get connectPhoneBlockAccount => 'ربط حساب PhoneBlock';

  @override
  String get permissions => 'التفويضات';

  @override
  String get allowCallFiltering => 'السماح بتصفية المكالمات';

  @override
  String get done => 'جاهز';

  @override
  String get setupComplete => 'اكتمل التثبيت';

  @override
  String get minReportsCount => 'الحد الأدنى لعدد الرسائل';

  @override
  String callsBlockedAfterReports(int count) {
    return 'يتم حظر المكالمات من رسائل $count';
  }

  @override
  String rangesBlockedAfterReports(int count) {
    return 'يتم حظر المناطق من $count الرسائل فصاعدًا';
  }

  @override
  String get welcomeMessage =>
      'مرحباً بك في PhoneBlock Mobile!\n\nيساعدك هذا التطبيق على حظر المكالمات غير المرغوب فيها تلقائيًا. تحتاج إلى حساب مجاني مع PhoneBlock.net.\n\nقم بتوصيل حساب PhoneBlock الخاص بك للمتابعة:';

  @override
  String get connectToPhoneBlock => 'تواصل مع PhoneBlock';

  @override
  String get connectedToPhoneBlock => 'متصل مع PhoneBlock';

  @override
  String get accountConnectedSuccessfully => 'تم توصيل الحساب بنجاح';

  @override
  String get permissionsMessage =>
      'لحظر المكالمات غير المرغوب فيها تلقائياً، يتطلب PhoneBlock Mobile تفويضاً للتحقق من المكالمات الواردة.\n\nهذا التفويض مطلوب لكي يعمل التطبيق:';

  @override
  String get permissionGranted => 'التفويض الممنوح';

  @override
  String get permissionGrantedSuccessfully => 'تم منح التفويض بنجاح';

  @override
  String get setupCompleteMessage =>
      'اكتمل التثبيت!\n\nPhoneBlock Mobile جاهز الآن لحظر المكالمات غير المرغوب فيها. يقوم التطبيق تلقائيًا بفحص المكالمات الواردة وحظر الأرقام المزعجة المعروفة بناءً على قاعدة بيانات PhoneBlock.\n\nاضغط على \"تم\" للانتقال إلى العرض الرئيسي.';

  @override
  String get verifyingLoginTitle => 'تحقق من تسجيل الدخول';

  @override
  String get loginSuccessMessage => 'تم تسجيل الدخول بنجاح!';

  @override
  String get redirectingToSetup => 'إعادة التوجيه إلى المنشأة...';

  @override
  String tokenVerificationFailed(String error) {
    return 'فشل التحقق من الرمز المميز: <x1> خطأ<x1>';
  }

  @override
  String get backToSetup => 'العودة إلى المنشأة';

  @override
  String get tokenBeingVerified => 'يتم التحقق من الرمز المميز...';

  @override
  String get failedToOpenPhoneBlock => 'تعذر فتح \"كتلة الهاتف\".';

  @override
  String get ratingLegitimate => 'شرعي';

  @override
  String get ratingAdvertising => 'الدعاية والإعلان';

  @override
  String get ratingSpam => 'الرسائل الاقتحامية';

  @override
  String get ratingPingCall => 'مكالمة بينغ';

  @override
  String get ratingGamble => 'المنافسة';

  @override
  String get ratingFraud => 'الاحتيال';

  @override
  String get ratingPoll => 'المسح';

  @override
  String get noLoginTokenReceived => 'لم يتم استلام رمز تسجيل الدخول المميز.';

  @override
  String get settingSaved => 'الإعداد المحفوظ';

  @override
  String get errorSaving => 'خطأ عند الحفظ';

  @override
  String ratePhoneNumber(String phoneNumber) {
    return 'معدل $phoneNumber';
  }

  @override
  String reportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count',
      one: '1 شكوى',
    );
    return '$_temp0';
  }

  @override
  String rangeReportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count الشكاوى في نطاق الأرقام',
      one: '1 شكوى في نطاق الأرقام',
    );
    return '$_temp0';
  }

  @override
  String legitimateReportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countالرسائل الشرعية',
      one: '1 رسالة شرعية 1 رسالة شرعية',
    );
    return '$_temp0';
  }

  @override
  String get noReports => 'لا توجد رسائل';

  @override
  String todayTime(String time) {
    return 'اليوم، $time';
  }

  @override
  String yesterdayTime(String time) {
    return 'يوم أمس، $time';
  }

  @override
  String get callHistoryRetention => 'تخزين سجل المكالمات';

  @override
  String retentionPeriodDescription(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: 'الاحتفاظ بالمكالمات $days أيام',
      one: 'الاحتفاظ بالمكالمات 1 يوم واحد',
    );
    return '$_temp0';
  }

  @override
  String get retentionInfinite => 'احتفظ بجميع المكالمات';

  @override
  String retentionDays(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: 'أيام $days أيام',
      one: '1 يوم واحد',
    );
    return '$_temp0';
  }

  @override
  String get retentionInfiniteOption => 'غير محدود';

  @override
  String get addCommentSpam => 'إضافة تعليق (اختياري)';

  @override
  String get commentHintSpam =>
      'لماذا هذه رسالة غير مرغوب فيها؟ عن ماذا كانت المكالمة؟ يرجى البقاء مهذباً';

  @override
  String get addCommentLegitimate => 'إضافة تعليق (اختياري)';

  @override
  String get commentHintLegitimate =>
      'لماذا هذا شرعي؟ من اتصل بك؟ يرجى البقاء مهذباً';

  @override
  String get serverSettings => 'إعدادات الخادم';

  @override
  String get serverSettingsDescription =>
      'إدارة إعدادات حساب PhoneBlock الخاص بك';

  @override
  String get searchNumber => 'رقم البحث';

  @override
  String get searchPhoneNumber => 'البحث عن رقم الهاتف';

  @override
  String get enterPhoneNumber => 'أدخل رقم الهاتف';

  @override
  String get phoneNumberHint => 'على سبيل المثال +49 123 123 456789+';

  @override
  String get search => 'بحث';

  @override
  String get invalidPhoneNumber => 'يُرجى إدخال رقم هاتف صحيح';

  @override
  String get blacklistTitle => 'القائمة السوداء';

  @override
  String get blacklistDescription => 'الأرقام التي قمت بحظرها';

  @override
  String get whitelistTitle => 'القائمة البيضاء';

  @override
  String get whitelistDescription => 'الأرقام التي حددتها على أنها شرعية';

  @override
  String get blacklistEmpty => 'قائمتك السوداء فارغة';

  @override
  String get whitelistEmpty => 'قائمتك البيضاء فارغة';

  @override
  String get blacklistEmptyHelp =>
      'إضافة أرقام عن طريق الإبلاغ عن المكالمات غير المرغوب فيها كرسائل غير مرغوب فيها.';

  @override
  String get whitelistEmptyHelp =>
      'إضافة أرقام عن طريق الإبلاغ عن المكالمات المحظورة على أنها مشروعة.';

  @override
  String get errorLoadingList => 'خطأ في تحميل القائمة';

  @override
  String get numberRemovedFromList => 'الرقم الذي تمت إزالته';

  @override
  String get errorRemovingNumber => 'خطأ عند إزالة الرقم';

  @override
  String get confirmRemoval => 'تأكيد الإزالة';

  @override
  String confirmRemoveFromBlacklist(Object phone) {
    return 'إزالة <x1> الهاتف من القائمة السوداء؟';
  }

  @override
  String confirmRemoveFromWhitelist(Object phone) {
    return 'إزالة <x1> الهاتف من القائمة البيضاء؟';
  }

  @override
  String get remove => 'إزالة';

  @override
  String get retry => 'حاول مرة أخرى';

  @override
  String get editComment => 'تعديل التعليق';

  @override
  String get commentLabel => 'تعليق';

  @override
  String get commentHint => 'أضف ملاحظة إلى هذا الرقم';

  @override
  String get save => 'الحفظ';

  @override
  String get commentUpdated => 'تم تحديث التعليق';

  @override
  String get errorUpdatingComment => 'خطأ عند تحديث التعليق';

  @override
  String get appearance => 'المظهر';

  @override
  String get themeMode => 'التصميم';

  @override
  String get themeModeDescription => 'اختر تصميماً فاتحاً أو غامقاً';

  @override
  String get themeModeSystem => 'معيار النظام';

  @override
  String get themeModeLight => 'خفيف';

  @override
  String get themeModeDark => 'داكن';

  @override
  String get experimentalFeatures => 'الوظائف التجريبية';

  @override
  String get answerbotFeature => 'جهاز الرد على المكالمات الهاتفي (Answerbot)';

  @override
  String get answerbotFeatureDescription =>
      'تجريبي: إدارة جهاز الرد على الرسائل الاقتحامية للرد على الرسائل الاقتحامية في التطبيق';

  @override
  String get answerbotMenuTitle => 'جهاز الرد الآلي';

  @override
  String get answerbotMenuDescription =>
      'إدارة جهاز الرد على الرسائل الاقتحامية';

  @override
  String potentialSpamLabel(String rating) {
    return 'مشبوه: <x1>التصنيف<x1>';
  }

  @override
  String get statistics => 'الإحصائيات';

  @override
  String get blockedCallsCount => 'المكالمات المحظورة';

  @override
  String get suspiciousCallsCount => 'المكالمات المشبوهة';

  @override
  String get fritzboxTitle => 'فريتز!بوكس';

  @override
  String get fritzboxConnected => 'متصل';

  @override
  String get fritzboxOffline => 'غير متوفر';

  @override
  String get fritzboxError => 'خطأ في الاتصال';

  @override
  String get fritzboxNotConfiguredShort => 'لم يتم إعداده';

  @override
  String get fritzboxNotConfigured => 'لا يوجد صندوق فريتز!';

  @override
  String get fritzboxNotConfiguredDescription =>
      'قم بتوصيل جهاز Fritz!Box الخاص بك لمشاهدة المكالمات من خطك الأرضي.';

  @override
  String get fritzboxConnect => 'توصيل فريتز!بوكس';

  @override
  String get fritzboxDisconnect => 'قطع الاتصال فريتز!';

  @override
  String get fritzboxDisconnectTitle => 'افصل صندوق فريتز';

  @override
  String get fritzboxDisconnectMessage =>
      'يتم حذف المكالمات المحفوظة وبيانات الوصول.';

  @override
  String get fritzboxSyncNow => 'المزامنة الآن';

  @override
  String get fritzboxSyncDescription => 'استرداد قائمة المكالمات من فريتز!';

  @override
  String fritzboxSyncComplete(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '<x1> عدد المكالمات الجديدة المتزامنة',
      one: 'تمت مزامنة 1 مكالمة جديدة',
      zero: 'لا توجد مكالمات جديدة',
    );
    return '$_temp0';
  }

  @override
  String get fritzboxSyncError => 'خطأ أثناء المزامنة';

  @override
  String get fritzboxVersion => 'إصدار FRITZ!OS';

  @override
  String get fritzboxHost => 'العنوان';

  @override
  String get fritzboxCachedCalls => 'المكالمات المحفوظة';

  @override
  String get fritzboxLastSync => 'آخر مزامنة';

  @override
  String get fritzboxJustNow => 'الآن فقط';

  @override
  String fritzboxMinutesAgo(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'قبل $count دقيقة',
      one: 'منذ 1 دقيقة',
    );
    return '$_temp0';
  }

  @override
  String fritzboxHoursAgo(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countمنذ ساعات مضت',
      one: 'منذ 1 ساعة مضت',
    );
    return '$_temp0';
  }

  @override
  String get fritzboxWizardTitle => 'توصيل فريتز!بوكس';

  @override
  String get fritzboxStepDetection => 'ابحث عن فريتز!';

  @override
  String get fritzboxStepDetectionSubtitle => 'البحث التلقائي في الشبكة';

  @override
  String get fritzboxStepLogin => 'تسجيل الدخول';

  @override
  String get fritzboxStepLoginSubtitle => 'إدخال بيانات الوصول';

  @override
  String get fritzboxSearching => 'ابحث عن Fritz!Box!';

  @override
  String get fritzboxNotFound => 'فريتز! الصندوق غير موجود';

  @override
  String get fritzboxNotFoundDescription =>
      'تعذر العثور على Fritz!Box تلقائياً. الرجاء إدخال العنوان يدوياً.';

  @override
  String get fritzboxHostLabel => 'عنوان صندوق فريتز';

  @override
  String get fritzboxRetrySearch => 'البحث مرة أخرى';

  @override
  String get fritzboxManualConnect => 'الاتصال';

  @override
  String get fritzboxLoginDescription =>
      'أدخل بيانات وصولك إلى Fritz!Box. يمكنك العثور عليها في واجهة مستخدم Fritz!Box ضمن النظام > مستخدم Fritz!Box.';

  @override
  String get fritzboxUsernameLabel => 'اسم المستخدم';

  @override
  String get fritzboxUsernameHint => 'المشرف أو مستخدم Fritz!Box الخاص بك';

  @override
  String get fritzboxPasswordLabel => 'كلمة المرور';

  @override
  String get fritzboxCredentialsNote =>
      'يتم تخزين بيانات الوصول الخاصة بك بشكل آمن على جهازك.';

  @override
  String get fritzboxTestAndSave => 'الاختبار والحفظ';

  @override
  String get fritzboxConnectionFailed =>
      'فشل الاتصال. يرجى التحقق من بيانات الوصول.';

  @override
  String get fritzboxFillAllFields => 'يرجى ملء جميع الحقول.';

  @override
  String get fritzboxOfflineBanner =>
      'لا يمكن الوصول إلى صندوق فريتز! - إظهار المكالمات المحفوظة';

  @override
  String get sourceMobile => 'الهاتف المحمول';

  @override
  String get sourceFritzbox => 'فريتز!بوكس';

  @override
  String get fritzboxStepBlocklist => 'الحماية من الرسائل غير المرغوب فيها';

  @override
  String get fritzboxStepBlocklistSubtitle => 'إعداد قائمة الحظر';

  @override
  String get fritzboxBlocklistDescription =>
      'حدد كيفية حماية جهاز Fritz!Box الخاص بك من المكالمات غير المرغوب فيها.';

  @override
  String get fritzboxCardDavTitle => 'قائمة حظر CardDAV';

  @override
  String get fritzboxCardDavDescription =>
      'يقوم Fritz!Box بمزامنة قائمة الحظر مباشرةً مع PhoneBlock. موصى به لـ FRITZ!OS 7.20+.';

  @override
  String get fritzboxSkipBlocklist => 'الإعداد لاحقاً';

  @override
  String get fritzboxSkipBlocklistDescription =>
      'يمكنك تفعيل الحماية من الرسائل غير المرغوب فيها لاحقاً في الإعدادات.';

  @override
  String get fritzboxVersionTooOldForCardDav =>
      'يتطلب CardDAV إصدار FRITZ!OS 7.20 أو أحدث. يحتوي Fritz!Box الخاص بك على إصدار أقدم.';

  @override
  String get fritzboxFinishSetup => 'إنهاء الإعداد النهائي';

  @override
  String get fritzboxPhoneBlockNotLoggedIn =>
      'يرجى تسجيل الدخول إلى PhoneBlock أولاً.';

  @override
  String get fritzboxCannotGetUsername => 'تعذر استرداد اسم مستخدم PhoneBlock.';

  @override
  String get fritzboxBlocklistConfigFailed => 'تعذر إعداد قائمة الحظر.';

  @override
  String get fritzboxCardDavStatus => 'حالة CardDAV';

  @override
  String get fritzboxCardDavStatusSynced => 'متزامن';

  @override
  String get fritzboxCardDavStatusPending => 'المزامنة معلقة';

  @override
  String get fritzboxCardDavStatusError => 'خطأ في المزامنة';

  @override
  String get fritzboxCardDavStatusDisabled => 'معطلة';

  @override
  String get fritzboxCardDavNote =>
      'يقوم Fritz!Box بمزامنة دفتر الهاتف مرة واحدة يومياً في منتصف الليل.';

  @override
  String get fritzboxBlocklistMode => 'وضع الحماية من الرسائل غير المرغوب فيها';

  @override
  String get fritzboxBlocklistModeCardDav => 'CardDAV (المزامنة التلقائية)';

  @override
  String get fritzboxBlocklistModeNone => 'غير مفعل';

  @override
  String get fritzboxEnableCardDav => 'تنشيط CardDAV';

  @override
  String get fritzboxEnableCardDavDescription =>
      'مزامنة قائمة حظر الرسائل غير المرغوب فيها مباشرة مع Fritz!Box';

  @override
  String get fritzboxCardDavEnabled => 'تم تنشيط قائمة حظر CardDAV';

  @override
  String get fritzboxDisableCardDav => 'إلغاء تنشيط CardDAV';

  @override
  String get fritzboxDisableCardDavTitle => 'إلغاء تنشيط CardDAV؟';

  @override
  String get fritzboxDisableCardDavMessage =>
      'تمت إزالة قائمة حظر CardDAV من Fritz!';

  @override
  String get fritzboxDisable => 'إلغاء التنشيط';

  @override
  String get fritzboxCardDavDisabled => 'تم إلغاء تنشيط قائمة حظر CardDAV';
}
