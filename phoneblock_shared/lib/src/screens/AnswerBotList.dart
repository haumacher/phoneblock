import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:jsontool/jsontool.dart';
import '../api/base_path.dart'
  if (dart.library.html) '../api/base_path_web.dart';
import '../api/Api.dart';
import './BotSetupForm.dart';
import './CallListView.dart';
import '../widgets/ErrorDialog.dart';
import '../widgets/TitleRow.dart';
import '../api/httpAddons.dart';
import '../models/proto.dart';
import 'package:http/http.dart' as http;
import '../api/sendRequest.dart';
import '../l10n_extensions.dart';

/// Callback function for handling login when authentication is required.
/// Returns true if login was successful, false otherwise.
typedef LoginHandler = Future<bool> Function(BuildContext context);

class AnswerBotList extends StatefulWidget {
  /// Optional login handler for when authentication is required.
  /// If not provided, shows a default "Login required" message.
  final LoginHandler? onLoginRequired;

  const AnswerBotList({super.key, this.onLoginRequired});

  @override
  State<StatefulWidget> createState() => AnswerBotListState();
}

class AnswerBotListState extends State<AnswerBotList> {

  bool loginRequired = false;
  String msg = '';

  List<AnswerbotInfo>? bots;

  @override
  void initState() {
    super.initState();
    requestBotList();
  }

  void requestBotList() async {
    var headers = await apiHeaders();
    if (kDebugMode) {
      debugPrint("Requesting bot list, authorization=${headers["Authorization"]}.");
    }
    http.get(Uri.parse('$basePath/ab/list'),
      headers: headers,
    ).then(processResponse);
  }

  void processResponse(http.Response response) {
    setState(() {
      if (response.statusCode == 401) {
        if (kDebugMode) {
          debugPrint("Unauthorized: ${response.body}");
        }

        loginRequired = true;
        return;
      }
      loginRequired = false;

      if (response.statusCode != 200) {
        msg = context.answerbotL10n.cannotLoadInfo(response.body, response.statusCode);
        return;
      }

      if (response.contentType.mimeType != "application/json") {
        msg = context.answerbotL10n.wrongContentType(response.contentType.mimeType);
        return;
      }

      var bots = ListAnswerbotResponse.read(JsonReader.fromString(response.body)).bots;
      if (bots.isEmpty) {
        msg = context.answerbotL10n.noAnswerbotsYet;
        this.bots = null;
      } else {
        this.bots = bots;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    if (loginRequired) {
      return Scaffold(
        appBar: AppBar(
          title: TitleRow(context.answerbotL10n.yourAnswerbots),
        ),
        body: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              Text(context.answerbotL10n.loginRequired,
                style: const TextStyle(fontSize: 20),
                textAlign: TextAlign.center,
              ),
              Padding(
                padding: const EdgeInsets.only(top: 16),
                child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      ElevatedButton(
                          onPressed: widget.onLoginRequired == null ? null : () async {
                            bool success = await widget.onLoginRequired!(context);
                            if (success) {
                              requestBotList();
                            }
                          },
                          child: Text(context.answerbotL10n.login)
                      )
                    ]
                ),
              )
            ],
          ),
        ),
      );
    }


    return Scaffold(
      appBar: AppBar(
        title: TitleRow(context.answerbotL10n.yourAnswerbots),
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
        tooltip: context.answerbotL10n.createAnswerbot,
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
                  child: Image.asset("packages/phoneblock_shared/assets/images/ab-logo-color-128.png"),
                ),
                Expanded(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(context.answerbotL10n.answerbotName(bot.userName), overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),),
                      Text(context.answerbotL10n.answerbotStats(bot.newCalls, bot.callsAccepted, (bot.talkTime / 1000).round()), overflow: TextOverflow.ellipsis,),
                    ],
                  ),
                ),
                if (bot.enabled)
                  bot.registered ?
                  Padding(padding: const EdgeInsets.only(left: 16),
                      child: Chip(label: Text(context.answerbotL10n.statusActive), backgroundColor: Colors.green, labelStyle: const TextStyle(color: Colors.white),)) :
                  Padding(padding: const EdgeInsets.only(left: 16),
                      child: Chip(label: Text(context.answerbotL10n.statusConnecting), backgroundColor: Colors.orangeAccent, labelStyle: const TextStyle(color: Colors.white),))
                else
                  setupComplete(bot) ?
                  Padding(padding: const EdgeInsets.only(left: 16),
                      child: Chip(label: Text(context.answerbotL10n.statusDisabled), backgroundColor: Colors.black54, labelStyle: const TextStyle(color: Colors.white),)) :
                  Padding(padding: const EdgeInsets.only(left: 16),
                      child: Chip(label: Text(context.answerbotL10n.statusIncomplete), backgroundColor: Colors.black12, labelStyle: const TextStyle(color: Colors.black),)),
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
    msg = '';
    requestBotList();
  }

  bool isSet(String? host) => host != null && host.isNotEmpty;
}

