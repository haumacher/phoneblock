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
}
