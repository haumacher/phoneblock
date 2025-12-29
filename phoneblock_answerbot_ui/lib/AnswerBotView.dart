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
import 'package:font_awesome_flutter/font_awesome_flutter.dart';

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
          child: ListView(
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
                              bot.registered = ok;
                            });
                          }
                        },
                      ),
                    ),
                  ],
                ),
              ),

              const Group("Anrufbeantworter-Einstellungen"),

              DropdownButtonFormField(
                items: const [
                  DropdownMenuItem(value: 2, child: Text("2 - sofort sperren")),
                  DropdownMenuItem(value: 4, child: Text("4 - Bestätigung abwarten")),
                  DropdownMenuItem(value: 10, child: Text("10 - erst wenn sicher")),
                  DropdownMenuItem(value: 100, child: Text("100 - nur Top-Spammer")),
                ],
                decoration: const InputDecoration(
                    labelText: 'Mindestkonfidenz',
                    helperText: "Wie viele Beschwerden notwendig sind, damit eine Nummer durch den Anrufbeantworter abgefangen wird."
                ),
                value: bot.minVotes,
                disabledHint: const Text("Kann nur bei ausgeschaltetem Anrufbeantworter geändert werden."),
                onChanged: (value) {
                  setState(() {
                    bot.minVotes = value ?? 4;
                  });
                },
              ),
              
              SwitchField("Nummernbereiche sperren", value: bot.wildcards,
                help: "Nimmt das Gespräch auch für einen Nummer an, die selbst noch nicht als SPAM bekannt ist, wenn die Vermutung naheliegt, dass die Nummer zu einem Anlagenanschluss gehört, von dem SPAM ausgeht.",
                onChanged: (bool value) {
                  setState(() {
                    bot.wildcards = value;
                  });
                }),

              SwitchField("IPv4 Kommunikation bevorzugen", value: bot.preferIPv4,
                help: "Wenn verfügbar wird die Kommunikation mit dem Anrufbeantworter über IPv4 abgewickelt. Es scheint Telefonanschlüsse zu geben, bei denen eine Sprachverbindung über IPv6 nicht möglich ist, obwohl eine IPv6 Adresse verfügbar ist.",
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
                child: const Text("Einstellungen speichern"),
              ),

              const Padding(
                padding: EdgeInsets.only(top: groupSpacing),
                child: Group("Anruf-Aufbewahrung"),
              ),

              DropdownButtonFormField<RetentionPeriod>(
                items: const [
                  DropdownMenuItem(value: RetentionPeriod.never, child: Text("Niemals löschen")),
                  DropdownMenuItem(value: RetentionPeriod.week, child: Text("Nach 1 Woche löschen")),
                  DropdownMenuItem(value: RetentionPeriod.month, child: Text("Nach 1 Monat löschen")),
                  DropdownMenuItem(value: RetentionPeriod.quarter, child: Text("Nach 3 Monaten löschen")),
                  DropdownMenuItem(value: RetentionPeriod.year, child: Text("Nach 1 Jahr löschen")),
                ],
                decoration: const InputDecoration(
                    labelText: 'Automatische Löschung',
                    helperText: "Nach welcher Zeit sollen alte Anrufprotokolle automatisch gelöscht werden? 'Niemals löschen' deaktiviert die automatische Löschung."
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
                child: const Text("Aufbewahrungseinstellungen speichern"),
              ),

              const Padding(
                padding: EdgeInsets.only(top: groupSpacing),
                child: const Group("DNS Settings"),),

              InfoField('DNS-Einstellung', internalDynDns ? "PhoneBlock-DNS" : "Anderer Anbieter oder Domainname",
                help: "Wie der Anrufbeantworter Deine Fritz!Box im Internet findet.",
                noCopy: true,
                padding: fieldSpacing,),

              if (internalDynDns) ...[
                const InfoField('Update-URL',
                  "https://phoneblock.net/phoneblock/api/dynip?user=<username>&passwd=<passwd>&ip4=<ipaddr>&ip6=<ip6addr>",
                  help: "Nutzername für die DynDNS-Freige in Deiner Fritz!Box.",
                  padding: fieldSpacing,),
                InfoField('Domainname', "${bot.dyndnsUser}box.phoneblock.net",
                  help: "Name den Deine Fritz!Box im Internet erhält.",
                  padding: fieldSpacing,),
                InfoField('DynDNS-Benutzername', bot.dyndnsUser,
                  help: "Nutzername für die DynDNS-Freige in Deiner Fritz!Box.",
                  padding: fieldSpacing,),
                InfoField('DynDNS-Kennwort', bot.dyndnsPassword,
                  password: true,
                  help: "Das Kennwort, dass Du für die DynDNS-Freigabe verwenden musst.",
                  padding: fieldSpacing,),
              ],

              if (!internalDynDns)
                Padding(
                  padding: const EdgeInsets.only(top: fieldSpacing),
                  child: TextFormField(
                    decoration: const InputDecoration(
                        labelText: 'Host',
                        helperText: "Der Host-Name, über den Deine Fritz!Box aus dem Internet erreichbar ist."
                    ),
                    initialValue: bot.host,
                  ),
                ),

              const Padding(
                padding: EdgeInsets.only(top: groupSpacing),
                child: Group("SIP Settings"),
              ),

              InfoField('User', bot.userName,
                help: "Der Nutzername, der in der Fritz!Box für den Zugriff auf das Telefoniegerät eingerichtet sein muss.",),
              InfoField('Password', bot.password,
                password: true,
                help: "Das Passwort, dass für den Zugriff auf das Telefoniegerät in der Fritz!Box vergeben sein muss.",
                padding: fieldSpacing,),
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

  final int maxRetry = 20;

  Future<bool> updateAnswerBot(AnswerbotInfo bot) async {
    ProgressDialog pd = ProgressDialog(context: context);
    pd.show(max: maxRetry, msg: 'Speichere Einstellungen...');

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

      showErrorDialog(context, response, 'Fehler beim Speichern der Einstellungen.',
        "Speichern fehlgeschlagen: ${response.body}");
      return false;
    }

    if (bot.enabled) {
      return await waitForRegister(botId, pd, 'Neu einschalten nach Speichern fehlgeschlagen');
    } else {
      pd.close();
      return false;
    }
  }

  Future<bool> enableAnswerBot(AnswerbotInfo bot) async {
    ProgressDialog pd = ProgressDialog(context: context);
    pd.show(max: maxRetry, msg: 'Schalte Anrufbeantworter ein...');

    var botId = bot.id;
    var response = await sendRequest(EnableAnswerBot(id: botId));

    if (!context.mounted) return false;

    if (response.statusCode != 200) {
      pd.close();

      showErrorDialog(context, response, 'Fehler beim Einschalten des Anrufbeantworters.',
        "Kann nicht einschalten: ${response.body}");
      return false;
    }

    return await waitForRegister(botId, pd, 'Einschalten des Anrufbeantworters fehlgeschlagen');
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
              "Einschalten fehlgeschlagen: $errorMessage");
          return false;
        } else {
          await Future.delayed(Duration(milliseconds: sleep));
    
          pd.update(value: n, msg: "$errorMessage Versuche erneut...");
        }
      }
    }
  }

  Future<void> updateRetentionPolicy(AnswerbotInfo bot) async {
    ProgressDialog pd = ProgressDialog(context: context);
    pd.show(max: 1, msg: 'Speichere Aufbewahrungseinstellungen...');

    var response = await sendRequest(
      SetRetentionPolicy(
        id: bot.id,
        period: bot.retentionPeriod,
      ));

    pd.close();

    if (!context.mounted) return;

    if (response.statusCode != 200) {
      showErrorDialog(context, response, 'Fehler beim Speichern der Aufbewahrungseinstellungen.',
        "Speichern fehlgeschlagen: ${response.body}");
    } else {
      String message = bot.retentionPeriod == "NEVER" 
        ? "Automatische Löschung deaktiviert"
        : "Aufbewahrungseinstellungen gespeichert (${_getRetentionDisplayName(bot.retentionPeriod)})";
      
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(message)),
      );
    }
  }

  String _getRetentionDisplayName(RetentionPeriod period) {
    switch (period) {
      case RetentionPeriod.week: return "1 Woche";
      case RetentionPeriod.month: return "1 Monat";
      case RetentionPeriod.quarter: return "3 Monate";
      case RetentionPeriod.year: return "1 Jahr";
      case RetentionPeriod.never: return "Niemals";
      default: return period.name;
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

