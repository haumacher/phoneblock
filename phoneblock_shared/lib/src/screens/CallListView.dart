import 'package:flutter/material.dart';
import 'package:jsontool/jsontool.dart';
import '../api/base_path.dart'
  if (dart.library.html) '../api/base_path_web.dart';
import '../api/Api.dart';
import './AnswerBotView.dart';
import '../widgets/ErrorDialog.dart';
import '../widgets/TitleRow.dart';
import '../models/proto.dart';
import 'package:http/http.dart' as http;
import '../api/sendRequest.dart';
import 'package:intl/intl.dart';
import 'package:url_launcher/url_launcher.dart';

class CallListView extends StatefulWidget {
  final AnswerbotInfo bot;
  int get botId => bot.id;
  
  const CallListView(this.bot, {super.key});

  @override
  State<StatefulWidget> createState() => CallListViewState();
}

class CallListViewState extends State<CallListView> {

  String? msg = 'Loading data...';

  ListCallsResponse? callInfo;

  @override
  void initState() {
    super.initState();
    requestCallList();
  }

  void requestCallList() async {
    http.post(Uri.parse('$basePath/ab/setup'),
      headers: await apiHeaders(),
      body: ListCalls(id: widget.botId).toString()
    ).then(processResponse);
  }

  void processResponse(http.Response response) {
    setState(() {
      if (response.statusCode != 200) {
        msg = "Anrufe können nicht abgerufen werden (Fehler ${response.statusCode}): ${response.body}";
        return;
      }

      callInfo = ListCallsResponse.read(JsonReader.fromString(response.body));
      msg = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const TitleRow("SPAM Anrufe"),
        actions: [
          IconButton(
            onPressed: () {
              Navigator.push(context, MaterialPageRoute(builder: (context) => AnswerBotView(widget.bot)));
            },
            icon: const Icon(Icons.settings),
          ),
          IconButton(
            onPressed: () {
              setState(refreshCallList);
            },
            icon: const Icon(Icons.refresh),
          )
        ],
      ),
      body: _callList(context),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _clearCalls(context),
        tooltip: 'Anrufe löschen',
        child: const Icon(Icons.delete_forever),
      ),
    );
  }

  void _clearCalls(BuildContext context) async {
    http.Response response = await sendRequest(ClearCallList(id: widget.botId));
    if (!context.mounted) return;

    if (response.statusCode != 200) {
      return showErrorDialog(context, response, 'Löschen fehlgeschlagen', "Die Löschanforderung konnte nicht bearbeitet werden.");
    }

    setState(refreshCallList);
  }

  Widget _callList(BuildContext context) {
    var callInfo = this.callInfo;

    if (callInfo == null || callInfo.calls.isEmpty) {
      return Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            Text(msg ?? "Keine neuen Anrufe.",
              style: const TextStyle(fontSize: 20),
              textAlign: TextAlign.center,
            )
          ],
        ),
      );
    }

    var calls = callInfo.calls;
    var dateFormat = relativeDate();

    return ListView.separated(
      padding: const EdgeInsets.all(8),
      itemCount: calls.length,
      itemBuilder: (BuildContext context, int index) {
        var call = calls[index];

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
                      Text(call.caller, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 20)),
                      Text("${dateFormat(DateTime.fromMillisecondsSinceEpoch(call.started))}, Dauer ${(call.duration / 1000).round()} s"),
                    ],
                  ),
                ),
                if (!call.caller.startsWith("*")) IconButton(
                  icon: const Icon(Icons.arrow_right),
                  iconSize: 32,
                  onPressed: () => showCall(context, call),
                )
              ],
            )
        );
      },
      separatorBuilder: (BuildContext context, int index) => const Divider(),
    );
  }

  String Function(DateTime) relativeDate() {
    final DateTime today = DateTime.now();
    final DateTime yesterDay = today.subtract(const Duration(days: 1));

    bool sameDay(DateTime t1, DateTime t2) {
      if (t1.year == t2.year) {
        if (t1.month == t2.month) {
          if (t1.day == t2.day) {
            return true;
          }
        }
      }
      return false;
    }

    return (DateTime dateTime) {
      if (sameDay(today, dateTime)) {
        return "Heute ${DateFormat.jm().format(dateTime)}";
      }
      else if (sameDay(today, dateTime)) {
        return "Gestern ${DateFormat.jm().format(dateTime)}";
      }
      else {
        return DateFormat.yMd().add_jm().format(dateTime);
      }
    };
  }

  showCall(BuildContext context, CallInfo call) {
    launchUrl(Uri.parse('https://phoneblock.net/phoneblock/nums/${call.caller}'), webOnlyWindowName: "inspect-number");
  }

  refreshCallList() {
    msg = 'Refreshing data...';
    requestCallList();
  }
}
