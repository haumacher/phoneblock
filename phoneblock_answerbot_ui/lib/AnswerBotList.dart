import 'package:flutter/material.dart';
import 'package:jsontool/jsontool.dart';
import 'package:phoneblock_answerbot_ui/AnswerBotView.dart';
import 'package:phoneblock_answerbot_ui/BotSetupForm.dart';
import 'package:phoneblock_answerbot_ui/CallListView.dart';
import 'package:phoneblock_answerbot_ui/Debug.dart';
import 'package:phoneblock_answerbot_ui/ErrorDialog.dart';
import 'package:phoneblock_answerbot_ui/TitleRow.dart';
import 'package:phoneblock_answerbot_ui/httpAddons.dart';
import 'package:phoneblock_answerbot_ui/proto.dart';
import 'package:http/http.dart' as http;
import 'package:phoneblock_answerbot_ui/sendRequest.dart';
import 'package:phoneblock_answerbot_ui/httpAddons.dart';

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

      if (response.contentType.mimeType != "application/json") {
        msg = "Informationen können nicht abgerufen werden (Content-Type: ${response.contentType.mimeType}).";
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
        title: const TitleRow("Deine PhoneBlock Anrufbeantworter"),
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
                Padding(padding: const EdgeInsets.only(right: 16),
                  child: Image.asset("assets/images/ab-logo-color-128.png"),
                ),
                Expanded(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Anrufbeantworter ${bot.userName}', overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),),
                      Text('${bot.callsAccepted} Anrufe, ${bot.talkTime / 1000} s Gesprächszeit'),
                    ],
                  ),
                ),
                if (bot.enabled)
                  bot.registered ?
                  const Padding(padding: EdgeInsets.only(left: 16),
                      child: Chip(label: Text("aktiv"), backgroundColor: Colors.green, labelStyle: TextStyle(color: Colors.white),)) :
                  const Padding(padding: EdgeInsets.only(left: 16),
                      child: Chip(label: Text("verbinde..."), backgroundColor: Colors.orangeAccent, labelStyle: TextStyle(color: Colors.white),))
                else
                  setupComplete(bot) ?
                  const Padding(padding: EdgeInsets.only(left: 16),
                      child: Chip(label: Text("ausgeschaltet"), backgroundColor: Colors.black54, labelStyle: TextStyle(color: Colors.white),)) :
                  const Padding(padding: EdgeInsets.only(left: 16),
                      child: Chip(label: Text("unvollständig"), backgroundColor: Colors.black12, labelStyle: TextStyle(color: Colors.black),)),
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

  bool setupComplete(AnswerbotInfo bot) => isSet(bot.host) || isSet(bot.ip4) || isSet(bot.ip6);

  showAnswerBot(BuildContext context, AnswerbotInfo bot) {
    var result = Navigator.push(context, MaterialPageRoute(builder: (context) =>
        setupComplete(bot) ?
          CallListView(bot) :
          BotSetupForm(
            CreateAnswerbotResponse(
              id: bot.id,
              userName: bot.userName,
              password: bot.password))));
    result.then((value) {
      refreshBotList();
    });
  }

  refreshBotList() {
    msg = 'Refreshing data...';
    requestBotList();
  }

  bool isSet(String? host) => host != null && !host.isEmpty;
}

