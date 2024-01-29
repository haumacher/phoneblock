import 'dart:async';
import 'dart:convert';
import 'dart:html';
import 'package:flutter/services.dart';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:jsontool/jsontool.dart';
import 'package:phoneblock_answerbot_ui/proto.dart';
import 'package:sn_progress_dialog/progress_dialog.dart';
import 'package:url_launcher/url_launcher.dart';

// Todo: test only.
var username = "b6c95db0-986e-47b0-af24-e51e56b09ecf";
var password = "moykCqj2XqEo7XR3FidN";
var authHeader = 'Basic ${base64Encode(utf8.encode('$username:$password'))}';
const bool debugUser = false;
const bool debugBase = false;

void main() {
  runApp(const MyApp());
}

const Color pbColor = Color(0xFF00d1b2);

String basePath = getBasePath();

String getBasePath() {
  if (debugBase) {
    return "https://phoneblock.net/pb-test";
  }

  String protocol = window.location.protocol;
  String host = window.location.host;
  String contextPath = getContextPath();
  String base = "$protocol//$host$contextPath";
  return base;
}

String getContextPath() {
  var path = window.location.pathname;
  if (path == null || path.isEmpty) {
    return "";
  }

  var sep = path.indexOf("/", 1);
  if (sep < 0) {
    return path;
  }

  return path.substring(0, sep);
}


class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'PhoneBlock',
      theme: ThemeData(
        primaryColorLight: pbColor,
        appBarTheme: const AppBarTheme(
          color: pbColor,
          foregroundColor: Colors.white,
        ),
        primaryColor: pbColor,
        floatingActionButtonTheme: const FloatingActionButtonThemeData(
          backgroundColor: pbColor,
          foregroundColor: Colors.white,
        ),
      ),
      home: const HomeScreen(),
    );
  }
}

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const AnswerBotList();
  }
}

Future<http.Response> sendRequest(SetupRequest request) async {
  var response = http.post(Uri.parse('$basePath/ab/setup'),
    encoding: const Utf8Codec(),
    headers: {
      "Content-Type": "application/json",
      if (debugUser) 'Authorization': authHeader,
    },
    body: request.toString(),
  );
  return response;
}

class BotSetupForm extends StatefulWidget {
  final CreateAnswerbotResponse creation;

  const BotSetupForm(this.creation, {super.key});

  @override
  State<StatefulWidget> createState() {
    return BotSetupState();
  }
}

enum SetupState {
  domainSetup,
  dynDnsSetup,
  enableSip,
  finish
}

class BotSetupState extends State<BotSetupForm> {
  final _formKey = GlobalKey<FormState>();

  SetupState state = SetupState.domainSetup;
  bool phoneblockDns = false;
  final _hostName = TextEditingController();

  SetupDynDnsResponse? dynDns;

  @override
  Widget build(BuildContext context) {
    switch (state) {
      case SetupState.domainSetup:
        return domainSetup(context);
      case SetupState.dynDnsSetup:
        return dynDnsSetup(context);
      case SetupState.enableSip:
        return enableSip(context);
      case SetupState.finish:
        return sipFinish(context);
      default: return showError(context);
    }
  }

  Widget domainSetup(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Anrufbeantworter einrichten"),
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
                    const Expanded(
                        child: Text("PhoneBlock-DynDNS benutzen")
                    ),
                    Switch(
                      thumbIcon: switchIcon,
                      value: phoneblockDns,
                      onChanged: (bool value) {
                        setState(() {
                          phoneblockDns = value;
                        });
                      },
                    )
                  ],
                ),
              ),

              hintText("PhoneBlock muss die Internet-Adresse Deiner "
                  "Fritz!Box kennen, um den Anrufbeantworter an Deiner "
                  "Fritz!Box anmelden zu können. Wenn Deine Fritz!Box "
                  "über MyFRITZ! oder DynDNS bereits einen Domainnamen hat, "
                  "kannst Du diesen verwenden. Wenn nicht kannst Du ganz einfach "
                  "DynDNS von PhoneBlock einrichten. Aktiviere dann diesen Schalter."),

              if (phoneblockDns) ...[
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: ElevatedButton(
                      onPressed: () async {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('Richte PhoneBlock DynDNS ein.')),
                        );

                        http.Response response = await sendRequest(SetupDynDns()..id=widget.creation.id);
                        if (!context.mounted) return;

                        if (response.statusCode != 200) {
                          return showErrorDialog(context, response, 'Einrichtung Fehlgeschlagen', "DynDNS kann nicht eingerichtet werden: ${response.body}");
                        }

                        setState(() {
                          dynDns = SetupDynDnsResponse.fromString(response.body);
                          state = SetupState.dynDnsSetup;
                        });
                      },
                      child:
                      const Text("PhoneBlock-DynDNS einrichten")
                  ),
                )
              ]
              else ...[
                TextFormField(
                  decoration: const InputDecoration(
                      labelText: 'Domainname',
                      hintText: "Domainname Deiner Fritz!Box (entweder MyFRITZ!-Adresse, oder DynDNS Domainname)"
                  ),
                  controller: _hostName,
                  validator: (value) {
                    if (value == null || value.isEmpty) {
                      return "Eingabe darf nicht leer sein.";
                    }
                    if (!hostNamePattern.hasMatch(value)) {
                      return "Kein gültiger Domain-Name.";
                    }
                    if (value.length > 255) {
                      return "Der Domain-Name ist zu lang.";
                    }
                    return null;
                  },
                  onTapOutside: (evt) => _formKey.currentState!.validate(),
                ),
                hintText("Wenn Du schon DynDNS eines anderen Anbieters in Deiner Fritz!Box eingerichtet "
                    "hast, musst Du hier den Domainnamen Deiner Fritz!Box eingeben (Unter Internet > Freigaben > DynDNS). "
                    "Wenn Du einen My!Fritz-Account hast (Internet > MyFRITZ!-Konto), kannst Du hier auch Deine "
                    "MyFRITZ!-Adresse angeben (z.B. z4z...l4n.myfritz.net)."),
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: ElevatedButton(
                      onPressed: () async {
                        if (_formKey.currentState!.validate()) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('Überprüfe Domainnamen.')),
                          );

                          http.Response response = await sendRequest(
                              EnterHostName()
                                ..id=widget.creation.id
                                ..hostName=_hostName.text);
                          if (!context.mounted) return;

                          if (response.statusCode != 200) {
                            return showErrorDialog(context, response, 'Einrichtung Fehlgeschlagen', "Domainname wurde nicht akzeptiert: ${response.body}");
                          }

                          setState(() {
                            state = SetupState.enableSip;
                          });
                        }
                      },
                      child:
                      const Text("Domainnamen überprüfen")),
                )
              ]
            ],
          ),
        ),
      ),
    );
  }

  Widget dynDnsSetup(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("DynDNS einrichten"),
      ),
      body: Form(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              hintText("Öffne in Deinen Fritz!Box-Einstellungen die Seite die "
                  "Internet > Freigaben > DynDNS und trage die hier angegebenen Informationen ein."),

              const InfoField('Update-URL',
                  "https://phoneblock.net/phoneblock/api/dynip?user=<username>&passwd=<passwd>&ip4=<ipaddr>&ip6=<ip6addr>",
                  key: Key("dynip.updateurl"),
                  help: "Die URL, die Deine Fritz!Box aufruft, um PhoneBlock ihre Internetadresse bekannt zu geben. "
                      "Gib die URL genau so ein, wie sie hier geschrieben ist. Ersetze nicht die Werte in den spitzen "
                      "Klammern, das macht Deine Fritz!Box beim Aufruf automatisch. Nutze am besten die Kopierfuntion, "
                      "um die Werte zu übernehmen."),
              InfoField('Domainname', "${dynDns!.dyndnsUser}.box.phoneblock.net",
                  key: const Key("dynip.domainname"),
                  help: "Dieser Domainname kann später nicht öffentlich aufgelöst werden. Deine Internetadresse wird "
                      "ausschließlich mit PhoneBlock geteilt."),
              InfoField('Benutzername', dynDns!.dyndnsUser,
                  key: const Key("dynip.username"),
                  help: "Der Benutzername, mit dem sich Deine Fritz!Box bei "
                  "PhoneBlock anmeldet, um ihre Internetadresse bekannt zu geben."),
              InfoField('Kennwort', dynDns!.dyndnsPassword,
                  key: const Key("dynip.password"),
                  help: "Das Kennwort, mit dem sich Deine Fritz!Box bei PhoneBlock anmeldet, "
                  "um ihre Internetadresse bekannt zu geben. Aus Sicherheitsgründen kannst Du kein eigenes Kennwort "
                  "eingeben, sondern musst das von PhoneBlock sicher generierte Kennwort verwenden. "),

              Padding(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: ElevatedButton(
                    onPressed: () async {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Überprüfe DynDNS Einrichtung.')),
                      );

                      http.Response response = await sendRequest(
                          CheckDynDns()
                            ..id=widget.creation.id
                      );
                      if (!context.mounted) return;

                      if (response.statusCode != 200) {
                        return showErrorDialog(context, response, 'Einrichtung Fehlgeschlagen', "DynDNS nicht aktuell: ${response.body}");
                      }

                      setState(() {
                        state = SetupState.enableSip;
                      });
                    },
                    child: const Text("DynDNS überprüfen")),
                )
              ]
          ),
        ),
      ),
    );
  }

  Widget enableSip(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Anrufbeantworter erstellen"),
      ),
      body: Form(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8),
          child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                hintText(
                    "1. Öffne in Deinen Fritz!Box-Einstellungen die Seite die "
                    "Telefonie > Telefoniegeräte und klicke auf den Knopf \"Neues Gerät einrichten\". "
                    "Wähle die Option \"Telefon (mit und ohne Anrufbeantworter)\" und klicke auf \"Weiter\"."),
                hintText(
                    "2. Wähle die Option \"Telefon (mit und ohne Anrufbeantworter)\" und klicke auf \"Weiter\"."),
                hintText(
                    "3. Wähle die Option \"LAN/WLAN (IP-Telefon)\", gib dem Telefon den Namen \"PhoneBlock\" und klicke auf \"Weiter\"."),

                InfoField('Benutzername', widget.creation.userName,
                    key: const Key("sip.username"),
                    help: "Der Benutzername, mit dem sich der PhoneBlock-Anrufbeantworter an Deiner Fritz!Box anmeldet."),
                InfoField('Kennwort', widget.creation.password,
                    key: const Key("sip.password"),
                    help: "Das Kennwort, das der PhoneBlock-Anrufbeantworter "
                    "nutzt, um sich an Deiner Fritz!Box anzumelden. "
                    "PhoneBlock hat für Dich ein sicheres Kennwort generiert.  "),

                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: ElevatedButton(
                      onPressed: () async {
                        const int maxCount = 20;
                        ProgressDialog pd = ProgressDialog(context: context);
                        pd.show(max: maxCount, msg: 'Versuche Anrufbeantworter anzumelden...');

                        {
                          http.Response response = await sendRequest(
                              EnableAnswerBot()
                                ..id=widget.creation.id
                          );
                          if (!context.mounted) return;

                          if (response.statusCode != 200) {
                            pd.close();

                            return showErrorDialog(context, response, 'Anmeldung des Anrufbeantworters fehlgeschlagen',
                                "Registrierung fehlgeschlagen: ${response.body}");
                          }
                        }

                        int sleep = 2500;
                        int n = 0;
                        while (true) {
                          http.Response response = await sendRequest(
                              CheckAnswerBot()
                                ..id=widget.creation.id
                          );
                          if (!context.mounted) return;

                          var responseCode = response.statusCode;
                          if (responseCode == 200) {
                            break;
                          } else {
                            var errorMessage = response.body;
                            if (responseCode != 409 || n++ == maxCount) {
                              pd.close();

                              return showErrorDialog(context, response, 'Anmeldung des Anrufbeantworters fehlgeschlagen',
                                  "Registrierung fehlgeschlagen: $errorMessage");
                            } else {
                              await Future.delayed(Duration(milliseconds: sleep));

                              pd.update(value: n, msg: "$errorMessage Versuche erneut...");
                            }
                          }
                        }

                        pd.close();
                        setState(() {
                          state = SetupState.finish;
                        });
                      },
                      child: const Text("Anrufbeantworter anmelden")),
                )
              ]
          ),
        ),
      ),
    );
  }

  Widget sipFinish(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Anrufbeantworter angemeldet"),
      ),
      body: Form(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8),
          child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                const Text(
                    "Dein PhoneBlock-Anrufbeantworter ist erfolgreich angemeldet. "
                    "Die nächsten Spam-Anrufer können sich jetzt ausgibig mit "
                        "PhoneBlock unterhalten. Wenn Du den PhoneBlock-Anrufbeantworter selber testen möchtest,"
                        "dann wähle die interne Rufnummer des von Dir eingerichteten Telefoniegerätes \"PhoneBlock\". "
                        "Die interne Nummer beginnt i.d.R. mit \"**\"."),

                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: ElevatedButton(
                      onPressed: () {
                        Navigator.of(context).pop();
                      },
                      child: const Text("Schließen")),
                )
              ]
          ),
        ),
      ),
    );
  }

  Widget showError(BuildContext context) {
    return Scaffold(
        appBar: AppBar(
        title: const Text("Fehler"),
    ),
    body: const Text("")
    );
  }

  hintText(String hint) {
    return             Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child:
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Padding(
              padding: EdgeInsets.only(right: 8),
              child: Icon(Icons.info_outline),
            ),
            Expanded(child:
            Text(hint),
            ),
          ],
        )
    );
  }

}

RegExp hostNamePattern = RegExp(r'^([a-zA-Z0-9]|[a-zA-Z0-9][-a-zA-Z0-9]{0,61}[a-zA-Z0-9])(\.([a-zA-Z0-9]|[a-zA-Z0-9][-a-zA-Z0-9]{0,61}[a-zA-Z0-9]))*$');

class AnswerBotList extends StatefulWidget {
  const AnswerBotList({super.key});

  @override
  State<StatefulWidget> createState() => AnswerBotListState();
}

class AnswerBotListState extends State<AnswerBotList> {

  String msg = 'Loading data...';

  List<AnswerbotInfo>? bots;

  @override
  void initState() {
    super.initState();
    requestBotList();
  }

  void requestBotList() {
    http.get(Uri.parse('$basePath/ab/list'),
      headers: {
        if (debugUser) 'Authorization': authHeader,
      },
    ).then(processResponse);
  }

  void processResponse(http.Response response) {
    setState(() {
      if (response.statusCode != 200) {
        msg = "Informationen können nicht abgerufen werden (Fehler ${response.statusCode}): ${response.body}";
        return;
      }

      var bots = ListAnswerbotResponse.read(JsonReader.fromString(response.body)).bots;
      if (bots.isEmpty) {
        msg = "Du hast noch keinen Anrufbeantworter, klicke den Plus-Knopf unten, um einen PhoneBlock-Anrufbeantworter anzulegen.";
        this.bots = null;
      } else {
        this.bots = bots;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Deine PhoneBlock Anrufbeantworter"),
        actions: [
          IconButton(
            onPressed: () {
              setState(refreshBotList);
            },
            icon: const Icon(Icons.refresh),
          )
        ],
      ),
      body: _botList(context),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _createAnswerBot(context),
        tooltip: 'Anrufbeantworter anlegen',
        child: const Icon(Icons.add),
      ),
    );
  }

  void _createAnswerBot(BuildContext context) async {
    http.Response response = await sendRequest(CreateAnswerBot());
    if (!context.mounted) return;

    if (response.statusCode != 200) {
      return showErrorDialog(context, response, 'Anlage fehlgeschlagen', "Der Anrufbeantworter konnte nicht angelegt werden");
    }

    var creation = CreateAnswerbotResponse.read(JsonReader.fromString(response.body));
    Navigator.push(context, MaterialPageRoute(builder: (context) => _setupAnswerBot(context, creation))).then((value) => refreshBotList());
  }

  Widget _setupAnswerBot(BuildContext context, CreateAnswerbotResponse creation) {
    return BotSetupForm(creation);
  }
  
  Widget _botList(BuildContext context) {
    var bots = this.bots;

    if (bots == null || bots.isEmpty) {
      return Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            Text(msg,
              style: const TextStyle(fontSize: 20),
              textAlign: TextAlign.center,
            )
          ],
        ),
      );
    }

    return ListView.separated(
      padding: const EdgeInsets.all(8),
      itemCount: bots.length,
      itemBuilder: (BuildContext context, int index) {
        var bot = bots[index];

        return SizedBox(
          height: 50,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Text('Anrufbeantworter ${bot.userName}'),
                        if (bot.enabled) Padding(padding: const EdgeInsets.only(left: 16),
                          child: bot.registered ?
                            const Chip(label: Text("aktiv"), backgroundColor: Colors.green, labelStyle: TextStyle(color: Colors.white),) :
                            const Chip(label: Text("verbinde..."), backgroundColor: Colors.orangeAccent, labelStyle: TextStyle(color: Colors.white),),
                        )
                        else Padding(padding: const EdgeInsets.only(left: 16),
                          child: Chip(label: Text("ausgeschaltet"), backgroundColor: Colors.black54, labelStyle: TextStyle(color: Colors.white),)
                        )
                      ],
                    )
                  ],
                ),
              ),
              IconButton(
                icon: const Icon(Icons.arrow_right),
                iconSize: 32,
                onPressed: () => showAnswerBot(context, bot),
              )
            ],
          )
        );
      },
      separatorBuilder: (BuildContext context, int index) => const Divider(),
    );
  }

  showAnswerBot(BuildContext context, AnswerbotInfo bot) {
    var result = Navigator.push(context, MaterialPageRoute(builder: (context) => AnswerBotView(bot)));
    result.then((value) {
      refreshBotList();
    });
  }

  refreshBotList() {
      msg = 'Refreshing data...';
      requestBotList();
  }
}

final MaterialStateProperty<Icon?> switchIcon = MaterialStateProperty.resolveWith<Icon?>(
      (Set<MaterialState> states) {
    if (states.contains(MaterialState.selected)) {
      return const Icon(Icons.check);
    }
    return const Icon(Icons.close);
  },
);

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
        title: Text("Anrufbeantworter ${bot.userName}"),
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
                          Text('Anrufbeantworter'),
                          if (bot.enabled) Padding(padding: const EdgeInsets.only(left: 16),
                            child: bot.registered ?
                            const Chip(label: Text("aktiv"), backgroundColor: Colors.green, labelStyle: TextStyle(color: Colors.white),) :
                            const Chip(label: Text("verbinde..."), backgroundColor: Colors.orangeAccent, labelStyle: TextStyle(color: Colors.white),),
                          )
                          else Padding(padding: const EdgeInsets.only(left: 16),
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

              if (!bot.enabled) Padding(
                padding: const EdgeInsets.only(top: 16),
                child: ElevatedButton(
                  onPressed: () {
                    showDialog<void>(
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
                  },
                  child: const Text("Anrufbeantworter löschen"),
                )
              ),
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

class InfoField extends StatelessWidget {
  final String label;
  final String? help;
  final String? value;

  const InfoField(this.label, this.value, {this.help, super.key});

  @override
  Widget build(BuildContext context) {
    var help = this.help;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: TextFormField(
                decoration: InputDecoration(
                  labelText: label,
                ),
                initialValue: value ?? "<not set>",
                readOnly: true,
              ),
            ),
            IconButton(
                onPressed: () async {
                  await Clipboard.setData(ClipboardData(text: value ?? ""));
                  if (!context.mounted) return;
                  ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text("Copied to clipboard.")));
                },
                icon: const Icon(Icons.copy)
            ),
          ],
        ),
        if (help != null) Text(help, style: const TextStyle(fontSize: 12, color: Colors.black54))
      ],
    );
  }
}

Future<void> showErrorDialog(BuildContext context, http.Response response, String title, String msg) {
  return showDialog(
    context: context,
    barrierDismissible: false, // user must tap button!
    builder: (BuildContext context) {
      return AlertDialog(
        title: Text(title),
        content: SingleChildScrollView(
          child: ListBody(
            children: <Widget>[
              Text('$msg (Fehler ${response.statusCode}).'),
              Text(response.body),
            ],
          ),
        ),
        actions: <Widget>[
          TextButton(
            child: const Text('Ok'),
            onPressed: () {
              Navigator.of(context).pop();
            },
          ),
        ],
      );
    },
  );
}
