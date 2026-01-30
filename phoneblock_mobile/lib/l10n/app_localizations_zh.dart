// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Chinese (`zh`).
class AppLocalizationsZh extends AppLocalizations {
  AppLocalizationsZh([String locale = 'zh']) : super(locale);

  @override
  String get appTitle => 'PhoneBlock Mobile';

  @override
  String get settings => '设置';

  @override
  String get deleteAll => '全部删除';

  @override
  String get noCallsYet => '尚未过滤来电';

  @override
  String get noCallsDescription => 'PhoneBlock 会自动筛选来电并阻止垃圾邮件电话。';

  @override
  String get blocked => '受阻';

  @override
  String get accepted => '已接受';

  @override
  String votes(int count) {
    return '$count票数';
  }

  @override
  String get viewOnPhoneBlock => '在 PhoneBlock 上显示';

  @override
  String get confirmDeleteAll => '删除所有已过滤的通话？';

  @override
  String get confirmDeleteAllMessage => '该操作无法撤销。';

  @override
  String get cancel => '取消';

  @override
  String get delete => '删除';

  @override
  String get settingsTitle => '设置';

  @override
  String get callScreening => '呼叫过滤';

  @override
  String get minSpamReports => '尽量减少垃圾邮件';

  @override
  String minSpamReportsDescription(int count) {
    return '从 $count 信息开始，号码将被屏蔽';
  }

  @override
  String get blockNumberRanges => '区块编号范围';

  @override
  String get blockNumberRangesDescription => '屏蔽垃圾邮件较多的区域';

  @override
  String get minSpamReportsInRange => '在以下领域尽量减少垃圾邮件';

  @override
  String minSpamReportsInRangeDescription(int count) {
    return '从$count信息开始，区域将被封锁';
  }

  @override
  String get about => '关于';

  @override
  String get version => '版本';

  @override
  String get developer => '开发人员';

  @override
  String get developerName => '伯恩哈德-豪马赫';

  @override
  String get website => '网站';

  @override
  String get websiteUrl => 'phoneblock.net';

  @override
  String get sourceCode => '源代码';

  @override
  String get sourceCodeLicense => '开放源代码（GPL-3.0）';

  @override
  String get aboutDescription => 'PhoneBlock 是一个开放源代码项目，没有跟踪和广告。该服务由捐款资助。';

  @override
  String get donate => '捐赠';

  @override
  String pendingCallsNotification(int count) {
    return '<x1>计数</x1';
  }

  @override
  String get tapToOpen => '轻点打开应用程序';

  @override
  String get setupWelcome => '欢迎访问 PhoneBlock Mobile';

  @override
  String get setupPermissionsRequired => '所需授权';

  @override
  String get grantPermission => '授权';

  @override
  String get continue_ => '更多';

  @override
  String get finish => '准备就绪';

  @override
  String get loginRequired => 'PhoneBlock 注册';

  @override
  String get loginToPhoneBlock => '注册 PhoneBlock';

  @override
  String get verifyingLogin => '登记将被检查...';

  @override
  String get loginFailed => '登录失败';

  @override
  String get loginSuccess => '注册成功！';

  @override
  String get reportAsLegitimate => '报告为合法';

  @override
  String get reportAsSpam => '报告为垃圾邮件';

  @override
  String get viewOnPhoneBlockMenu => '在 PhoneBlock 上查看';

  @override
  String get deleteCall => '删除';

  @override
  String get report => '报告';

  @override
  String get notLoggedIn => '尚未注册。请登录。';

  @override
  String reportedAsLegitimate(String phoneNumber) {
    return '$phoneNumber被报告为合法号码';
  }

  @override
  String reportError(String error) {
    return '报告时出错：<x1>错误</x1';
  }

  @override
  String reportedAsSpam(String phoneNumber) {
    return '$phoneNumber被报告为垃圾邮件';
  }

  @override
  String get selectSpamCategory => '选择垃圾邮件类别';

  @override
  String get errorDeletingAllCalls => '删除所有呼叫时出错';

  @override
  String get errorDeletingCall => '取消通话时出错';

  @override
  String get notLoggedInShort => '未登记';

  @override
  String get errorOpeningPhoneBlock => '打开 PhoneBlock 时出错。';

  @override
  String get permissionNotGranted => '尚未获得授权。';

  @override
  String get setupTitle => 'PhoneBlock Mobile - 设置';

  @override
  String get welcome => '欢迎来到';

  @override
  String get connectPhoneBlockAccount => '连接 PhoneBlock 账户';

  @override
  String get permissions => '授权';

  @override
  String get allowCallFiltering => '允许过滤呼叫';

  @override
  String get done => '准备就绪';

  @override
  String get setupComplete => '安装完毕';

  @override
  String get minReportsCount => '最少信息数量';

  @override
  String callsBlockedAfterReports(int count) {
    return '阻止来自 $count 信息的呼叫';
  }

  @override
  String rangesBlockedAfterReports(int count) {
    return '从$count信息开始，区域将被封锁';
  }

  @override
  String get welcomeMessage =>
      '欢迎访问 PhoneBlock Mobile！\n\n这款应用程序可帮助您自动拦截垃圾电话。您需要在 PhoneBlock.net 注册一个免费账户。\n\n连接您的 PhoneBlock 账户以继续：';

  @override
  String get connectToPhoneBlock => '连接 PhoneBlock';

  @override
  String get connectedToPhoneBlock => '与 PhoneBlock 连接';

  @override
  String get accountConnectedSuccessfully => '✓ 账户连接成功';

  @override
  String get permissionsMessage =>
      '要自动阻止垃圾电话，PhoneBlock Mobile 需要授权才能检查来电。\n\n只有获得授权，应用程序才能正常工作：';

  @override
  String get permissionGranted => '批准';

  @override
  String get permissionGrantedSuccessfully => '✓ 成功获得授权';

  @override
  String get setupCompleteMessage =>
      '安装完毕！\n\nPhoneBlock Mobile 现在可以拦截垃圾电话了。该应用程序会自动筛选来电，并根据 PhoneBlock 数据库拦截已知的垃圾号码。\n\n按 \"完成 \"进入主界面。';

  @override
  String get verifyingLoginTitle => '检查登录';

  @override
  String get loginSuccessMessage => '登录成功！';

  @override
  String get redirectingToSetup => '转发到设施...';

  @override
  String tokenVerificationFailed(String error) {
    return '令牌验证失败：<x1>错误</x1';
  }

  @override
  String get backToSetup => '返回设施';

  @override
  String get tokenBeingVerified => '令牌已检查...';

  @override
  String get failedToOpenPhoneBlock => 'PhoneBlock 无法打开。';

  @override
  String get ratingLegitimate => '合法';

  @override
  String get ratingAdvertising => '广告';

  @override
  String get ratingSpam => '垃圾邮件';

  @override
  String get ratingPingCall => '平移呼叫';

  @override
  String get ratingGamble => '竞赛';

  @override
  String get ratingFraud => '欺诈';

  @override
  String get ratingPoll => '调查';

  @override
  String get noLoginTokenReceived => '未收到登录令牌。';

  @override
  String get settingSaved => '已保存的设置';

  @override
  String get errorSaving => '保存时出错';

  @override
  String ratePhoneNumber(String phoneNumber) {
    return '费率 <x1> 电话号码</x1';
  }

  @override
  String reportsCount(int count) {
    return '<x1>计数</x1';
  }

  @override
  String rangeReportsCount(int count) {
    return '<x1>计数</x1';
  }

  @override
  String legitimateReportsCount(int count) {
    return '<x1>计数</x1';
  }

  @override
  String get noReports => '无信息';

  @override
  String todayTime(String time) {
    return '今天，<x1>时间</x1';
  }

  @override
  String yesterdayTime(String time) {
    return '昨天，<x1>时间</x1';
  }

  @override
  String get callHistoryRetention => '通话记录存储';

  @override
  String retentionPeriodDescription(int days) {
    return '<x1>天数</x1';
  }

  @override
  String get retentionInfinite => '保留所有通话';

  @override
  String retentionDays(int days) {
    return '<x1>天数</x1';
  }

  @override
  String get retentionInfiniteOption => '无限制';

  @override
  String get addCommentSpam => '添加评论（可选）';

  @override
  String get commentHintSpam => '为什么是垃圾邮件？电话的内容是什么？请保持礼貌。';

  @override
  String get addCommentLegitimate => '添加评论（可选）';

  @override
  String get commentHintLegitimate => '为什么这是合法的？谁打电话给你？请保持礼貌。';

  @override
  String get serverSettings => '服务器设置';

  @override
  String get serverSettingsDescription => '管理你的 PhoneBlock 账户设置';

  @override
  String get searchNumber => '搜索号码';

  @override
  String get searchPhoneNumber => '搜索电话号码';

  @override
  String get enterPhoneNumber => '输入电话号码';

  @override
  String get phoneNumberHint => '例如：+49 123 456789';

  @override
  String get search => '搜索';

  @override
  String get invalidPhoneNumber => '请输入有效的电话号码';

  @override
  String get blacklistTitle => '黑名单';

  @override
  String get blacklistDescription => '您已阻止的号码';

  @override
  String get whitelistTitle => '白名单';

  @override
  String get whitelistDescription => '您已标记为合法的号码';

  @override
  String get blacklistEmpty => '您的黑名单是空的';

  @override
  String get whitelistEmpty => '您的白名单是空的';

  @override
  String get blacklistEmptyHelp => '将不需要的电话报告为垃圾邮件，从而增加号码。';

  @override
  String get whitelistEmptyHelp => '将被拦截的来电报告为合法来电，从而增加号码。';

  @override
  String get errorLoadingList => '加载列表出错';

  @override
  String get numberRemovedFromList => '删除数量';

  @override
  String get errorRemovingNumber => '删除数字时出错';

  @override
  String get confirmRemoval => '确认移除';

  @override
  String confirmRemoveFromBlacklist(Object phone) {
    return '从黑名单中删除 $phone 吗？';
  }

  @override
  String confirmRemoveFromWhitelist(Object phone) {
    return '从白名单中删除 $phone 吗？';
  }

  @override
  String get remove => '移除';

  @override
  String get retry => '再试一次';

  @override
  String get editComment => '编辑评论';

  @override
  String get commentLabel => '评论';

  @override
  String get commentHint => '为该号码添加备注';

  @override
  String get save => '节省';

  @override
  String get commentUpdated => '评论已更新';

  @override
  String get errorUpdatingComment => '更新注释时出错';

  @override
  String get appearance => '外观';

  @override
  String get themeMode => '设计';

  @override
  String get themeModeDescription => '选择浅色或深色设计';

  @override
  String get themeModeSystem => '系统标准';

  @override
  String get themeModeLight => '灯光';

  @override
  String get themeModeDark => '黑暗';

  @override
  String get experimentalFeatures => '实验功能';

  @override
  String get answerbotFeature => '答录机（Answerbot）';

  @override
  String get answerbotFeatureDescription => '实验：在应用程序中管理 Fritz!Box 的垃圾邮件应答机';

  @override
  String get answerbotMenuTitle => '答录机';

  @override
  String get answerbotMenuDescription => '管理垃圾邮件应答机';

  @override
  String potentialSpamLabel(String rating) {
    return '可疑：<x1>评级</x1';
  }

  @override
  String get statistics => '统计资料';

  @override
  String get blockedCallsCount => '被阻止的电话';

  @override
  String get suspiciousCallsCount => '可疑电话';
}
