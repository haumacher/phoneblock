import 'dart:async';
import 'dart:convert';
import 'dart:html';
import 'package:flutter/services.dart';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:jsontool/jsontool.dart';
import 'package:phoneblock_answerbot_ui/proto.dart';
import 'package:url_launcher/url_launcher.dart';

String basePath = getBasePath();

String getBasePath() {
  if (kDebugMode) {
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

// Todo: test only.
var username = "b6c95db0-986e-47b0-af24-e51e56b09ecf";
var password = "moykCqj2XqEo7XR3FidN";
var authHeader = 'Basic ${base64Encode(utf8.encode('$username:$password'))}';

void main() {
  runApp(const MyApp());
}

const Color pbColor = Color(0xFF00d1b2);

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
    return Scaffold(
      appBar: AppBar(
        title: const Text("Deine PhoneBlock Anrufbeantworter"),
      ),
      body: const AnswerBotList(),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _createAnswerBot(context),
        tooltip: 'Anrufbeantworter anlegen',
        child: const Icon(Icons.add),
      ),
    );
  }

  void _createAnswerBot(BuildContext context) async {
    var response = await http.post(Uri.parse('$basePath/ab/create'),
      encoding: const Utf8Codec(),
      headers: {
        "Content-Type": "application/json",
        if (kDebugMode)
          'Authorization': authHeader,
      },
      body: "{}",
    );
    if (!context.mounted) return;

    if (response.statusCode != 200) {
      return showErrorDialog(context, response, 'Anlage fehlgeschlagen', "Der Anrufbeantworter konnte nicht angelegt werden");
    }

    var creation = CreateAnswerbotResponse.read(JsonReader.fromString(response.body));
    Navigator.push(context, MaterialPageRoute(builder: (context) => _setupAnswerBot(context, creation)));
  }

  Widget _setupAnswerBot(BuildContext context, CreateAnswerbotResponse creation) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Anrufbeantworter einrichten"),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            Text(creation.userName),
          ],
        ),
      ),
    );
  }
}

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

    http.get(Uri.parse('$basePath/ab/list'),
      headers: {
        if (kDebugMode) 'Authorization': authHeader,
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
      } else {
        this.bots = bots;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
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
        return SizedBox(
          height: 50,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.start,
            children: [
              Column(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Anrufbeantworter ${bots[index].userName}'),
                ],
              ),
              IconButton(
                icon: const Icon(Icons.arrow_right),
                iconSize: 32,
                onPressed: () => showAnswerBot(context, bots[index]),
              )
            ],
          )
        );
      },
      separatorBuilder: (BuildContext context, int index) => const Divider(),
    );
  }

  showAnswerBot(BuildContext context, AnswerbotInfo bot) {
    Navigator.push(context, MaterialPageRoute(builder: (context) => AnswerBotView(bot)));
  }
}

class AnswerBotView extends StatefulWidget {
  AnswerbotInfo bot;

  AnswerBotView(this.bot, {super.key});

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

  final MaterialStateProperty<Icon?> switchIcon = MaterialStateProperty.resolveWith<Icon?>(
    (Set<MaterialState> states) {
      if (states.contains(MaterialState.selected)) {
        return const Icon(Icons.check);
      }
      return const Icon(Icons.close);
    },
  );

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
            children: <Widget>[
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Row(
                  children: [
                    const Expanded(child: Text("PhoneBlock-DynDNS benutzen")),
                    Switch(
                      thumbIcon: switchIcon,
                      value: internalDynDns,
                      onChanged: (bool value) {
                        setState(() {
                          internalDynDns = value;
                        });
                      },
                    )
                  ],
                ),
              ),

              if (internalDynDns) InfoField('DynDNS-User', bot.dyndnsUser, help: "Trage diesen ...."),
              if (internalDynDns) InfoField('DynDNS-Password', bot.dyndnsPassword),
              if (!internalDynDns) TextFormField(
                decoration: const InputDecoration(
                  labelText: 'Host',
                ),
                initialValue: bot.host,
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
}

class InfoField extends StatelessWidget {
  final String label;
  final String? help;
  final String? value;

  const InfoField(this.label, this.value, {this.help, super.key});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: TextFormField(
            decoration: InputDecoration(
              labelText: label,
              helperText: help,
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
