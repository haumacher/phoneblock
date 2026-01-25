// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Chinese (`zh`).
class AppLocalizationsZh extends AppLocalizations {
  AppLocalizationsZh([String locale = 'zh']) : super(locale);

  @override
  String get appTitle => 'PhoneBlock 应答机';

  @override
  String get yourAnswerbots => '您的答录机';

  @override
  String get loginRequired => '需要注册';

  @override
  String get login => '登录';

  @override
  String get loadingData => '加载数据...';

  @override
  String get refreshingData => '更新数据...';

  @override
  String get noAnswerbotsYet => '您还没有电话答录机，请点击下面的加号按钮创建 PhoneBlock 电话答录机。';

  @override
  String get createAnswerbot => '创建应答机';

  @override
  String answerbotName(String userName) {
    return '应答机 <x1>用户名</x1';
  }

  @override
  String answerbotStats(int newCalls, int talkTimeSeconds, int callsAccepted) {
    return '$newCalls新通话，$callsAccepted通话，$talkTimeSeconds总通话时间';
  }

  @override
  String get statusActive => '活动';

  @override
  String get statusConnecting => '连接...';

  @override
  String get statusDisabled => '关掉';

  @override
  String get statusIncomplete => '不完整';

  @override
  String get deleteAnswerbot => '删除答录机';

  @override
  String get enabled => '激活';

  @override
  String get minVotes => '最低票数';

  @override
  String get minVotesDescription => '答录机接受号码所需的最低语音数是多少？';

  @override
  String get minVotes2 => '2 - 立即锁定';

  @override
  String get minVotes4 => '4 - 等待确认';

  @override
  String get minVotes10 => '10 - 仅在安全的情况下';

  @override
  String get minVotes100 => '100 - 仅限顶级垃圾邮件发送者';

  @override
  String get cannotChangeWhileEnabled => '只能在应答机关闭时更改。';

  @override
  String get saveSettings => '保存设置';

  @override
  String get retentionPeriod => '存储时间';

  @override
  String get retentionPeriodDescription => '通话应保留多长时间？';

  @override
  String get retentionNever => '永不删除';

  @override
  String get retentionWeek => '1 周后删除';

  @override
  String get retentionMonth => '1 个月后删除';

  @override
  String get retentionQuarter => '3 个月后删除';

  @override
  String get retentionYear => '1 年后删除';

  @override
  String get saveRetentionSettings => '保存存储设置';

  @override
  String get showHelp => '显示帮助';

  @override
  String get newAnswerbot => '新答录机';

  @override
  String get usePhoneBlockDynDns => '使用 PhoneBlock DynDNS';

  @override
  String get dynDnsDescription => 'PhoneBlock 需要知道 Fritz!';

  @override
  String get setupPhoneBlockDynDns => '设置 PhoneBlock DynDNS';

  @override
  String get domainName => '域名';

  @override
  String get domainNameHint =>
      '输入 Fritz 的域名！如果您的 Fritz！Box 还没有域名，请激活 PhoneBlock-DynDNS。';

  @override
  String get checkDomainName => '检查域名';

  @override
  String get setupDynDns => '设置 DynDNS';

  @override
  String get dynDnsInstructions =>
      '在 Fritz!Box 设置中，打开 Internet > 共享 > DynDNS 页面，然后输入以下值：';

  @override
  String get checkDynDns => '检查 DynDNS';

  @override
  String get createAnswerbotTitle => '创建应答机';

  @override
  String get registerAnswerbot => '注册答录机';

  @override
  String get answerbotRegistered => '已注册的应答机';

  @override
  String get close => '关闭';

  @override
  String get error => '错误';

  @override
  String cannotLoadInfo(String message, int statusCode) {
    return '无法检索信息（错误 $statusCode）：<x2>消息</x2';
  }

  @override
  String wrongContentType(String contentType) {
    return '无法检索信息（Content-Type: $contentType）。';
  }

  @override
  String get callList => '通话清单';

  @override
  String get clearCallList => '删除通话清单';

  @override
  String get noCalls => '尚未接到电话';
}
