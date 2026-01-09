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
  String votes(Object count) {
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
  String minSpamReportsDescription(Object count) {
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
  String minSpamReportsInRangeDescription(Object count) {
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
  String pendingCallsNotification(num count) {
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
  String reportedAsLegitimate(Object phoneNumber) {
    return '$phoneNumber الذي تم الإبلاغ عنه على أنه شرعي';
  }

  @override
  String reportError(Object error) {
    return 'خطأ عند الإبلاغ: $error';
  }

  @override
  String reportedAsSpam(Object phoneNumber) {
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
  String callsBlockedAfterReports(Object count) {
    return 'يتم حظر المكالمات من رسائل $count';
  }

  @override
  String rangesBlockedAfterReports(Object count) {
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
  String tokenVerificationFailed(Object error) {
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
  String ratePhoneNumber(Object phoneNumber) {
    return 'معدل $phoneNumber';
  }

  @override
  String reportsCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count الرسائل',
      one: '1 رسالة',
    );
    return '$_temp0';
  }

  @override
  String legitimateReportsCount(num count) {
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
  String todayTime(Object time) {
    return 'اليوم، $time';
  }

  @override
  String yesterdayTime(Object time) {
    return 'يوم أمس، $time';
  }

  @override
  String get callHistoryRetention => 'تخزين سجل المكالمات';

  @override
  String retentionPeriodDescription(num days) {
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
  String retentionDays(num days) {
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
}
