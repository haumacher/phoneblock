import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import '../widgets/ErrorDialog.dart';
import '../widgets/InfoField.dart';
import '../widgets/TitleRow.dart';
import '../models/proto.dart';
import '../api/sendRequest.dart';
import '../widgets/switchIcon.dart';
import 'package:sn_progress_dialog/progress_dialog.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:font_awesome_flutter/font_awesome_flutter.dart';
import '../l10n_extensions.dart';

class AnswerBotView extends StatefulWidget {
  final AnswerbotInfo bot;

  const AnswerBotView(this.bot, {super.key});

  @override
  State<StatefulWidget> createState() => AnswerBotViewState();
}

const double fieldSpacing = 8;
const double groupSpacing = 16;

class AnswerBotViewState extends State<AnswerBotView> {
  AnswerbotInfo get bot => widget.bot;
  final _formKey = GlobalKey<FormState>();

  bool internalDynDns = true;

  @override
  void initState() {
    super.initState();

    var host = bot.host;
    internalDynDns = host == null || host.isEmpty;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: TitleRow(bot.userName),
        actions: [
          if (!bot.enabled) PopupMenuButton(
            itemBuilder: (BuildContext context) => [
              PopupMenuItem(
                  value: "delete",
                  child: Row(
                    children: [
                      const Padding(padding: EdgeInsets.only(right: 16),
                        child: Icon(Icons.delete_forever, color: Colors.black),
                      ),
                      Text(context.answerbotL10n.deleteAnswerbot)
                    ],
                  ))
            ],
            onSelected: (value) {
              AnswerBotViewState.deleteAnswerBot(context, bot).then((value) => Navigator.pop(context));
            },
          )
        ],
      ),
      body: Form(
        key: _formKey,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8),
          child: ListView(
            children: <Widget>[
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Row(
                  children: [
                    Expanded(
                      child: Row(
                        children: [
                          Text(context.answerbotL10n.answerbot),
                          if (bot.enabled) Padding(padding: const EdgeInsets.only(left: 16),
                            child: bot.registered ?
                            Chip(label: Text(context.answerbotL10n.statusActive), backgroundColor: Colors.green, labelStyle: const TextStyle(color: Colors.white),) :
                            Chip(label: Text(context.answerbotL10n.statusConnecting), backgroundColor: Colors.orangeAccent, labelStyle: const TextStyle(color: Colors.white),),
                          )
                          else Padding(padding: const EdgeInsets.only(left: 16),
                              child: Chip(label: Text(context.answerbotL10n.statusDisabled), backgroundColor: Colors.black54, labelStyle: const TextStyle(color: Colors.white),)
                          )
                        ],
                      ),
                    ),

                    Padding(padding: const EdgeInsets.only(left: 8),
                      child: Switch(
                        thumbIcon: switchIcon,
                        value: bot.enabled,
                        onChanged: (bool value) async {
                          if (bot.enabled) {
                            sendRequest(DisableAnswerBot(id: bot.id));
                            setState(() {
                              bot.enabled = false;
                            });
                          } else {
                            setState(() {
                              bot.enabled = true;
                            });
                            bool ok = await enableAnswerBot(bot);
                            setState(() {
                              bot.registered = ok;
                            });
                          }
                        },
                      ),
                    ),
                  ],
                ),
              ),

              Group(context.answerbotL10n.answerbotSettings),

              DropdownButtonFormField(
                items: [
                  DropdownMenuItem(value: 2, child: Text(context.answerbotL10n.minVotes2)),
                  DropdownMenuItem(value: 4, child: Text(context.answerbotL10n.minVotes4)),
                  DropdownMenuItem(value: 10, child: Text(context.answerbotL10n.minVotes10)),
                  DropdownMenuItem(value: 100, child: Text(context.answerbotL10n.minVotes100)),
                ],
                decoration: InputDecoration(
                    labelText: context.answerbotL10n.minConfidence,
                    helperText: context.answerbotL10n.minConfidenceHelp
                ),
                value: bot.minVotes,
                disabledHint: Text(context.answerbotL10n.cannotChangeWhileEnabled),
                onChanged: (value) {
                  setState(() {
                    bot.minVotes = value ?? 4;
                  });
                },
              ),
              
              SwitchField(context.answerbotL10n.blockNumberRanges, value: bot.wildcards,
                help: context.answerbotL10n.blockNumberRangesHelp,
                onChanged: (bool value) {
                  setState(() {
                    bot.wildcards = value;
                  });
                }),

              SwitchField(context.answerbotL10n.preferIPv4, value: bot.preferIPv4,
                help: context.answerbotL10n.preferIPv4Help,
                onChanged: (bool value) {
                  setState(() {
                    bot.preferIPv4 = value;
                  });
                }),

              ElevatedButton(
                onPressed: () async {
                  bool ok = await updateAnswerBot(bot);
                  setState(() {
                    bot.registered = ok;
                  });
                },
                child: Text(context.answerbotL10n.saveSettings),
              ),

              Padding(
                padding: const EdgeInsets.only(top: groupSpacing),
                child: Group(context.answerbotL10n.callRetention),
              ),

              DropdownButtonFormField<RetentionPeriod>(
                items: [
                  DropdownMenuItem(value: RetentionPeriod.never, child: Text(context.answerbotL10n.retentionNever)),
                  DropdownMenuItem(value: RetentionPeriod.week, child: Text(context.answerbotL10n.retentionWeek)),
                  DropdownMenuItem(value: RetentionPeriod.month, child: Text(context.answerbotL10n.retentionMonth)),
                  DropdownMenuItem(value: RetentionPeriod.quarter, child: Text(context.answerbotL10n.retentionQuarter)),
                  DropdownMenuItem(value: RetentionPeriod.year, child: Text(context.answerbotL10n.retentionYear)),
                ],
                decoration: InputDecoration(
                    labelText: context.answerbotL10n.automaticDeletion,
                    helperText: context.answerbotL10n.automaticDeletionHelp
                ),
                value: bot.retentionPeriod,
                onChanged: (value) {
                  setState(() {
                    bot.retentionPeriod = value ?? RetentionPeriod.never;
                  });
                },
              ),

              ElevatedButton(
                onPressed: () async {
                  await updateRetentionPolicy(bot);
                },
                child: Text(context.answerbotL10n.saveRetentionSettings),
              ),

              Padding(
                padding: const EdgeInsets.only(top: groupSpacing),
                child: Group(context.answerbotL10n.dnsSettings),),

              InfoField(context.answerbotL10n.dnsSetting,
                internalDynDns ? context.answerbotL10n.phoneBlockDns : context.answerbotL10n.otherProviderOrDomain,
                help: context.answerbotL10n.dnsSettingHelp,
                noCopy: true,
                padding: fieldSpacing,),

              if (internalDynDns) ...[
                InfoField(context.answerbotL10n.updateUrl,
                  "https://phoneblock.net/phoneblock/api/dynip?user=<username>&passwd=<passwd>&ip4=<ipaddr>&ip6=<ip6addr>",
                  help: context.answerbotL10n.updateUrlHelp,
                  padding: fieldSpacing,),
                InfoField(context.answerbotL10n.domainname, "${bot.dyndnsUser}box.phoneblock.net",
                  help: context.answerbotL10n.domainNameHelp,
                  padding: fieldSpacing,),
                InfoField(context.answerbotL10n.dyndnsUsername, bot.dyndnsUser,
                  help: context.answerbotL10n.dyndnsUsernameHelp,
                  padding: fieldSpacing,),
                InfoField(context.answerbotL10n.dyndnsPassword, bot.dyndnsPassword,
                  password: true,
                  help: context.answerbotL10n.dyndnsPasswordHelp,
                  padding: fieldSpacing,),
              ],

              if (!internalDynDns)
                Padding(
                  padding: const EdgeInsets.only(top: fieldSpacing),
                  child: TextFormField(
                    decoration: InputDecoration(
                        labelText: context.answerbotL10n.host,
                        helperText: context.answerbotL10n.hostHelp
                    ),
                    initialValue: bot.host,
                  ),
                ),

              Padding(
                padding: const EdgeInsets.only(top: groupSpacing),
                child: Group(context.answerbotL10n.sipSettings),
              ),

              InfoField(context.answerbotL10n.user, bot.userName,
                help: context.answerbotL10n.userHelp,),
              InfoField(context.answerbotL10n.password, bot.password,
                password: true,
                help: context.answerbotL10n.passwordHelp,
                padding: fieldSpacing,),
            ],
          ),
        ),
      ),
      floatingActionButton: FloatingActionButton(
        tooltip: context.answerbotL10n.showHelp,
        onPressed: () async {
          await launchUrl(Uri.parse("https://phoneblock.net/"));
        },
        child: const Icon(Icons.help),
      ),
    );
  }

  final int maxRetry = 20;

  Future<bool> updateAnswerBot(AnswerbotInfo bot) async {
    ProgressDialog pd = ProgressDialog(context: context);
    pd.show(max: maxRetry, msg: context.answerbotL10n.savingSettings);

    var botId = bot.id;
    var response = await sendRequest(
      UpdateAnswerBot(
        id: botId,
        enabled: bot.enabled,
        preferIPv4: bot.preferIPv4,
        minVotes: bot.minVotes,
        wildcards: bot.wildcards,
      ));

    if (!context.mounted) return false;

    if (response.statusCode != 200) {
      pd.close();

      showErrorDialog(context, response, context.answerbotL10n.errorSavingSettings,
        context.answerbotL10n.savingFailed(response.body));
      return false;
    }

    if (bot.enabled) {
      return await waitForRegister(botId, pd, context.answerbotL10n.enableAfterSavingFailed);
    } else {
      pd.close();
      return false;
    }
  }

  Future<bool> enableAnswerBot(AnswerbotInfo bot) async {
    ProgressDialog pd = ProgressDialog(context: context);
    pd.show(max: maxRetry, msg: context.answerbotL10n.enablingAnswerbot);

    var botId = bot.id;
    var response = await sendRequest(EnableAnswerBot(id: botId));

    if (!context.mounted) return false;

    if (response.statusCode != 200) {
      pd.close();

      showErrorDialog(context, response, context.answerbotL10n.errorEnablingAnswerbot,
        context.answerbotL10n.cannotEnable(response.body));
      return false;
    }

    return await waitForRegister(botId, pd, context.answerbotL10n.enablingFailed);
  }

  Future<bool> waitForRegister(int botId, ProgressDialog pd, String errorMessage) async {
    int sleep = 2500;
    int n = 0;
    while (true) {
      http.Response response = await sendRequest(
          CheckAnswerBot()
            ..id=botId
      );
      if (!context.mounted) return false;
    
      var responseCode = response.statusCode;
      if (responseCode == 200) {
        pd.close();
        return true;
      } else {
        var errorMessage = response.body;
        if (responseCode != 409 || n++ == maxRetry) {
          pd.close();
    
          showErrorDialog(context, response, errorMessage,
              context.answerbotL10n.enablingFailedMessage(errorMessage));
          return false;
        } else {
          await Future.delayed(Duration(milliseconds: sleep));

          pd.update(value: n, msg: context.answerbotL10n.retryingMessage(errorMessage));
        }
      }
    }
  }

  Future<void> updateRetentionPolicy(AnswerbotInfo bot) async {
    ProgressDialog pd = ProgressDialog(context: context);
    pd.show(max: 1, msg: context.answerbotL10n.savingRetentionSettings);

    var response = await sendRequest(
      SetRetentionPolicy(
        id: bot.id,
        period: bot.retentionPeriod,
      ));

    pd.close();

    if (!context.mounted) return;

    if (response.statusCode != 200) {
      showErrorDialog(context, response, context.answerbotL10n.errorSavingRetentionSettings,
        context.answerbotL10n.savingFailed(response.body));
    } else {
      String message = bot.retentionPeriod == "NEVER"
        ? context.answerbotL10n.automaticDeletionDisabled
        : context.answerbotL10n.retentionSettingsSaved(_getRetentionDisplayName(bot.retentionPeriod));

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(message)),
      );
    }
  }

  String _getRetentionDisplayName(RetentionPeriod period) {
    switch (period) {
      case RetentionPeriod.week: return context.answerbotL10n.oneWeek;
      case RetentionPeriod.month: return context.answerbotL10n.oneMonth;
      case RetentionPeriod.quarter: return context.answerbotL10n.threeMonths;
      case RetentionPeriod.year: return context.answerbotL10n.oneYear;
      case RetentionPeriod.never: return context.answerbotL10n.never;
      default: return period.name;
    }
  }

  static Future<void> deleteAnswerBot(BuildContext context, AnswerbotInfo bot) {
    return showDialog<void>(
      context: context,
      barrierDismissible: true,
      builder: (BuildContext dialogContext) {
        return AlertDialog(
          title: Text(context.answerbotL10n.deleteAnswerbot),
          content: SingleChildScrollView(
            child: ListBody(
              children: <Widget>[
                Text(context.answerbotL10n.deleteAnswerbotConfirm(bot.userName)),
              ],
            ),
          ),
          actions: <Widget>[
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(),
              child: Text(context.answerbotL10n.cancel),
            ),
            TextButton(
              child: Text(context.answerbotL10n.delete),
              onPressed: () {
                Navigator.of(dialogContext).pop();

                sendRequest(DeleteAnswerBot(id: bot.id)).then((value) {
                  if (!context.mounted) {
                    return;
                  }
                  if (value.statusCode == 200) {
                    Navigator.of(context).pop(true);
                  } else {
                    showErrorDialog(context, value, context.answerbotL10n.deletionFailed, context.answerbotL10n.answerbotCouldNotBeDeleted);
                  }
                });
              },
            ),
          ],
        );
      },
    );
  }
}

class SwitchField extends StatelessWidget {

  final String label;
  final bool value;
  final ValueChanged<bool>? onChanged;
  final String? help;

  const SwitchField(this.label, {super.key, this.value = false, this.onChanged, this.help});

  @override
  Widget build(BuildContext context) {
    var help = this.help;
    var switchTextStyle = TextStyle(fontSize: Theme.of(context).textTheme.bodyLarge?.fontSize);

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        children: [
          Expanded(
            child: help == null ?
              Text(label, style: switchTextStyle,) :
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(label, style: switchTextStyle),
                    Text(help)]),
          ),

          Padding(padding: const EdgeInsets.only(left: 8),
            child: Switch(
              thumbIcon: switchIcon,
              value: value,
              onChanged: onChanged,
            ),
          ),
        ],
      ),
    );
  }
  
}

class Group extends StatelessWidget {
  final String label;

  const Group(this.label, {super.key});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        const SizedBox(width: 32,
          child: Divider(
            height: 32,
            thickness: 3,
          ),
        ),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 8),
          child: Text(label),
        ),
        const Expanded(child:
        Divider(
          height: 32,
          thickness: 3,
        ),
        ),
      ],
    );
  }
}

