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

  @override
  String get answerbot => '答录机';

  @override
  String get answerbotSettings => '答录机设置';

  @override
  String get minConfidence => '最低信心';

  @override
  String get minConfidenceHelp => '一个号码需要多少次投诉才能被应答机拦截。';

  @override
  String get blockNumberRanges => '区块编号范围';

  @override
  String get blockNumberRangesHelp =>
      '如果有理由怀疑某个号码属于垃圾邮件源的系统连接，即使该号码尚未被确认为垃圾邮件，也会接受呼叫。';

  @override
  String get preferIPv4 => '喜欢 IPv4 通信';

  @override
  String get preferIPv4Help =>
      '如果有，则通过 IPv4 处理与应答机的通信。有些电话连接虽然有 IPv6 地址，但似乎无法通过 IPv6 进行语音连接。';

  @override
  String get callRetention => '呼叫保留率';

  @override
  String get automaticDeletion => '自动删除';

  @override
  String get automaticDeletionHelp => '旧通话记录应该在多长时间后自动删除？永不删除会停用自动删除功能。';

  @override
  String get dnsSettings => 'DNS 设置';

  @override
  String get dnsSetting => 'DNS 设置';

  @override
  String get phoneBlockDns => 'PhoneBlock DNS';

  @override
  String get otherProviderOrDomain => '其他供应商或域名';

  @override
  String get dnsSettingHelp => '答录机如何在互联网上找到您的 Fritz!';

  @override
  String get updateUrl => '更新 URL';

  @override
  String get updateUrlHelp => 'Fritz 中 DynDNS 共享的用户名！';

  @override
  String get domainNameHelp => '您的 Fritz!';

  @override
  String get dyndnsUsername => 'DynDNS 用户名';

  @override
  String get dyndnsUsernameHelp => 'Fritz 中 DynDNS 共享的用户名！';

  @override
  String get dyndnsPassword => 'DynDNS 密码';

  @override
  String get dyndnsPasswordHelp => '您必须用于 DynDNS 共享的密码。';

  @override
  String get host => '主持人';

  @override
  String get hostHelp => '从互联网访问 Fritz!Box 的主机名。';

  @override
  String get sipSettings => 'SIP 设置';

  @override
  String get user => '用户';

  @override
  String get userHelp => '访问电话设备时必须在 Fritz!Box 中设置的用户名。';

  @override
  String get password => '密码';

  @override
  String get passwordHelp => '访问 Fritz 电话设备必须指定的密码！';

  @override
  String get savingSettings => '保存设置...';

  @override
  String get errorSavingSettings => '保存设置时出错。';

  @override
  String savingFailed(String message) {
    return '保存失败：<x1>消息</x1';
  }

  @override
  String get enableAfterSavingFailed => '保存失败后再次开机';

  @override
  String get enablingAnswerbot => '打开答录机...';

  @override
  String get errorEnablingAnswerbot => '打开答录机时出错。';

  @override
  String cannotEnable(String message) {
    return '无法打开：<x1>消息</x1';
  }

  @override
  String get enablingFailed => '未能打开答录机';

  @override
  String enablingFailedMessage(String message) {
    return '开机失败：<x1>消息</x1';
  }

  @override
  String retryingMessage(String message) {
    return '$message 再试一次...';
  }

  @override
  String get savingRetentionSettings => '保存存储设置...';

  @override
  String get errorSavingRetentionSettings => '保存存储设置时出错。';

  @override
  String get automaticDeletionDisabled => '已停用自动删除功能';

  @override
  String retentionSettingsSaved(String period) {
    return '保存的存储设置（<x1>周期</x1）';
  }

  @override
  String get oneWeek => '1 周';

  @override
  String get oneMonth => '1 个月';

  @override
  String get threeMonths => '3 个月';

  @override
  String get oneYear => '1 年';

  @override
  String get never => '从不';

  @override
  String deleteAnswerbotConfirm(String userName) {
    return '应答机 $userName 真的应该删除吗？';
  }

  @override
  String get cancel => '取消';

  @override
  String get delete => '删除';

  @override
  String get deletionFailed => '删除失败';

  @override
  String get answerbotCouldNotBeDeleted => '无法删除应答机';

  @override
  String get spamCalls => '垃圾邮件电话';

  @override
  String get deleteCalls => '删除通话';

  @override
  String get deletingCallsFailed => '删除失败';

  @override
  String get deleteRequestFailed => '无法处理删除请求。';

  @override
  String cannotRetrieveCalls(String message, int statusCode) {
    return '无法检索调用（错误 $statusCode）：<x2>消息</x2';
  }

  @override
  String get noNewCalls => '没有新电话。';

  @override
  String duration(int seconds) {
    return '持续时间 $seconds s';
  }

  @override
  String today(String time) {
    return '今天<x1>时间</x1';
  }

  @override
  String yesterday(String time) {
    return '昨天<x1>时间</x1';
  }

  @override
  String get dynDnsDescriptionLong =>
      'PhoneBlock需要知道您的Fritz！盒子的互联网地址，以便在Fritz！盒子上注册应答机。如果您已经设置了 MyFRITZ! 或其他 DynDNS 提供商，则可以使用此域名。如果没有，您只需通过 PhoneBlock 设置 DynDNS，然后激活此开关即可。';

  @override
  String get setupPhoneBlockDynDnsSnackbar => '设置 PhoneBlock DynDNS。';

  @override
  String get setupFailed => '设置失败';

  @override
  String cannotSetupDynDns(String message) {
    return '无法设置 DynDNS：<x1>消息</x1';
  }

  @override
  String get domainname => '域名';

  @override
  String get domainNameHintLong => '您的 Fritz！盒子的域名（MyFRITZ！地址或 DynDNS 域名）';

  @override
  String get inputCannotBeEmpty => '输入内容不得为空。';

  @override
  String get invalidDomainName => '无有效域名。';

  @override
  String get domainNameTooLong => '域名太长。';

  @override
  String get domainNameHintExtended =>
      '输入 Fritz 的域名！如果您的 Fritz！Box 还没有域名，请激活 PhoneBlock DynDNS。您可以在 (Under Internet > Shares > DynDNS) 下找到您 Fritz!Box 的域名。或者，您也可以输入 MyFRITZ! 地址 (Internet > MyFRITZ! account)，例如 z4z...l4n.myfritz.net。';

  @override
  String get checkingDomainName => '检查域名。';

  @override
  String domainNameNotAccepted(String message) {
    return '域名未被接受：<x1>消息</x1';
  }

  @override
  String get dynDnsInstructionsLong =>
      '在 Fritz!Box 设置中打开 Internet > Shares > DynDNS 页面，然后输入此处提供的信息。';

  @override
  String get updateUrlHelp2 =>
      '您的 Fritz！盒子为 PhoneBlock 提供互联网地址时调用的 URL。请按照此处所写的 URL 输入。不要替换角括号中的值，您的 Fritz!最好使用复制功能复制数值。';

  @override
  String get domainNameHelp2 => '该域名以后不能公开解析。您的互联网地址只与 PhoneBlock 共享。';

  @override
  String get username => '用户名';

  @override
  String get usernameHelp => 'Fritz! Box 登录 PhoneBlock 以显示其 Internet 地址的用户名。';

  @override
  String get passwordLabel => '密码';

  @override
  String get passwordHelp2 =>
      '用于 Fritz!Box 登录 PhoneBlock 以显示其互联网地址的密码。出于安全考虑，您不能输入自己的密码，而必须使用由 PhoneBlock 安全生成的密码。';

  @override
  String get checkingDynDns => '检查 DynDNS 设置。';

  @override
  String get notRegistered => '未登记';

  @override
  String fritzBoxNotRegistered(String message) {
    return '您的 Fritz!Box 尚未在 PhoneBlock 注册，DynDNS 不是最新版本：$message。';
  }

  @override
  String get sipSetupInstructions =>
      '现在将 PhoneBlock 应答机设置为 \"电话（带应答机和不带应答机）\"。为确保正常工作，请严格按照以下步骤操作：';

  @override
  String get sipSetupStep1 =>
      '1. 打开 Fritz!Box 设置中的电话 > 电话设备页面，然后单击 \"设置新设备 \"按钮。';

  @override
  String get sipSetupStep2 => '2. 选择 \"电话（带或不带答录机）\"选项，然后点击 \"下一步\"。';

  @override
  String get sipSetupStep3 =>
      '选择 \"LAN/WLAN（IP 电话）\"选项，将电话命名为 \"PhoneBlock\"，然后点击 \"下一步\"。';

  @override
  String get sipSetupStep4 => '4. 现在为应答机输入以下用户名和密码，然后点击 \"下一步\"。';

  @override
  String get usernameHelp2 => 'PhoneBlock 应答机登录 Fritz 时使用的用户名！';

  @override
  String get passwordHelp3 =>
      'PhoneBlock 应答机用于登录 Fritz 的密码！PhoneBlock 为您生成了一个安全密码。';

  @override
  String get sipSetupStep5 =>
      '5. 现在查询的电话号码并不重要，PhoneBlock 应答机不会主动拨打任何电话，只会接受垃圾邮件电话。在步骤 9 中再次取消选择电话号码。在此点击 \"下一步 \"即可。';

  @override
  String get sipSetupStep6 =>
      '选择 \"接受所有来电 \"并点击 \"下一步\"。无论如何，PhoneBlock 应答机只接受来电者号码在拦截列表中的来电。同时，PhoneBlock 绝不接受来自正常电话簿中号码的来电。';

  @override
  String get sipSetupStep7 => '7. 您将看到一个摘要。设置（基本）完成，点击 \"应用\"。';

  @override
  String get sipSetupStep8 =>
      '8. 现在您将在电话设备列表中看到 \"PhoneBlock\"。还有一些设置需要稍后才能完成。因此，请单击 PhoneBlock 应答机行中的编辑笔。';

  @override
  String get sipSetupStep9 =>
      '9. 在 \"拨出电话 \"字段中选择最后一个（空）选项，因为 PhoneBlock 从不拨出电话，因此应答机不需要拨出电话的号码。';

  @override
  String get sipSetupStep10 =>
      '10. 选择 \"登录数据 \"选项卡。点击 \"应用 \"确认回复。现在选择 \"允许从互联网登录 \"选项，这样 PhoneBlock 云中的电话答录机就可以登录 Fritz！在点击 \"应用 \"之前，您必须在 \"密码 \"字段中再次输入电话答录机密码（见上文）。首先删除字段中的星号。';

  @override
  String get sipSetupStep11 =>
      '11. 出现一条信息，警告你可以通过互联网接入建立收费连接。您可以放心地确认这一点，首先是因为 PhoneBlock 不会主动建立连接，其次是因为 PhoneBlock 为您创建了一个安全密码（见上文），因此其他人无法连接，第三是因为您在步骤 9 中停用了外线连接。根据 Fritz！Box 的设置，您可能需要在直接连接到 Fritz！Box 的 DECT 电话上确认设置！';

  @override
  String get sipSetupStep12 =>
      '12. 现在一切都完成了。单击 \"返回 \"返回电话设备列表。现在您可以通过底部的按钮激活答录机。';

  @override
  String get tryingToRegisterAnswerbot => '尝试注册答录机...';

  @override
  String get answerbotRegistrationFailed => '应答机注册失败';

  @override
  String registrationFailed(String message) {
    return '注册失败：<x1>消息</x1';
  }

  @override
  String get answerbotRegisteredSuccess =>
      '您的 PhoneBlock 应答机已成功注册。下一个垃圾邮件呼叫者现在可以与 PhoneBlock 通话。如果您想亲自测试 PhoneBlock 电话答录机，请拨打您已设置的 \"PhoneBlock \"电话设备的内部号码。内部号码通常以 \"**\"开头。';
}
