import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:phoneblock_answerbot_ui/ErrorDialog.dart';
import 'package:phoneblock_answerbot_ui/InfoField.dart';
import 'package:phoneblock_answerbot_ui/TitleRow.dart';
import 'package:phoneblock_answerbot_ui/proto.dart';
import 'package:phoneblock_answerbot_ui/sendRequest.dart';
import 'package:phoneblock_answerbot_ui/switchIcon.dart';
import 'package:sn_progress_dialog/progress_dialog.dart';
import 'package:url_launcher/url_launcher.dart';

class AnswerBotView extends StatefulWidget {
  final AnswerbotInfo bot;

  const AnswerBotView(this.bot, {super.key});

  @override
  State<StatefulWidget> createState() => AnswerBotViewState();
}

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
              const PopupMenuItem(
                  value: "delete",
                  child: Row(
                    children: [
                      Padding(padding: EdgeInsets.only(right: 16),
                        child: Icon(Icons.delete_forever, color: Colors.black),
                      ),
                      Text("Anrufbeantworter löschen")
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
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Row(
                  children: [
                    Expanded(
                      child: Row(
                        children: [
                          const Text('Anrufbeantworter'),
                          if (bot.enabled) Padding(padding: const EdgeInsets.only(left: 16),
                            child: bot.registered ?
                            const Chip(label: Text("aktiv"), backgroundColor: Colors.green, labelStyle: TextStyle(color: Colors.white),) :
                            const Chip(label: Text("verbinde..."), backgroundColor: Colors.orangeAccent, labelStyle: TextStyle(color: Colors.white),),
                          )
                          else const Padding(padding: EdgeInsets.only(left: 16),
                              child: Chip(label: Text("ausgeschaltet"), backgroundColor: Colors.black54, labelStyle: TextStyle(color: Colors.white),)
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
                              if (ok) {
                                bot.registered = true;
                              } else {
                                bot.enabled = false;
                              }
                            });
                          }
                        },
                      ),
                    ),
                  ],
                ),
              ),

              const Group("DNS Settings"),
              Text("DNS-Einstellung: ${internalDynDns ? "PhoneBlock-DNS" : "Anderer DynDNS-Anbieter"}"),
              if (internalDynDns) InfoField('DynDNS-User', bot.dyndnsUser, help: "Trage diesen ...."),
              if (internalDynDns) InfoField('DynDNS-Password', bot.dyndnsPassword),
              if (!internalDynDns) TextFormField(
                decoration: const InputDecoration(
                  labelText: 'Host',
                ),
                initialValue: bot.host,
              ),

              const Padding(
                padding: EdgeInsets.only(top: 16),
                child: Group("SIP Settings"),
              ),

              InfoField('User', bot.userName),
              InfoField('Password', bot.password),
            ],
          ),
        ),
      ),
      floatingActionButton: FloatingActionButton(
        tooltip: "Hilfe anzeigen",
        onPressed: () async {
          await launchUrl(Uri.parse("https://phoneblock.net/"));
        },
        child: const Icon(Icons.help),
      ),
    );
  }

  Future<bool> enableAnswerBot(AnswerbotInfo bot) async {
    const int maxCount = 20;
    ProgressDialog pd = ProgressDialog(context: context);
    pd.show(max: maxCount, msg: 'Schalte Anrufbeantworter ein...');

    var botId = bot.id;
    var response = await sendRequest(EnableAnswerBot(id: botId));

    if (!context.mounted) return false;

    if (response.statusCode != 200) {
      pd.close();

      showErrorDialog(context, response, 'Fehler beim Einschalten des Anrufbeantworters.',
          "Kann nicht einschalten: ${response.body}");
      return false;
    }

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
        if (responseCode != 409 || n++ == maxCount) {
          pd.close();

          showErrorDialog(context, response, 'Einschalten des Anrufbeantworters fehlgeschlagen',
              "Einschalten fehlgeschlagen: $errorMessage");
          return false;
        } else {
          await Future.delayed(Duration(milliseconds: sleep));

          pd.update(value: n, msg: "$errorMessage Versuche erneut...");
        }
      }
    }
  }

  static Future<void> deleteAnswerBot(BuildContext context, AnswerbotInfo bot) {
    return showDialog<void>(
      context: context,
      barrierDismissible: true,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('Anrufbeantworter löschen'),
          content: SingleChildScrollView(
            child: ListBody(
              children: <Widget>[
                Text('Soll der Anrufbeantworter ${bot.userName} wirklich gelöscht werden?'),
              ],
            ),
          ),
          actions: <Widget>[
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Abbrechen'),
            ),
            TextButton(
              child: const Text('Löschen'),
              onPressed: () {
                Navigator.of(context).pop();

                sendRequest(DeleteAnswerBot(id: bot.id)).then((value) {
                  if (!context.mounted) {
                    return;
                  }
                  if (value.statusCode == 200) {
                    Navigator.of(context).pop(true);
                  } else {
                    showErrorDialog(context, value, "Löschen Fehlgeschlagen", "Der Anrufbeantworter konnte nicht gelöscht werden");
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

