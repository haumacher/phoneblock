import 'dart:convert';
import 'dart:html';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:jsontool/jsontool.dart';
import 'package:phoneblock_answerbot_ui/proto.dart';

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
var authHeaders = {
  'authorization': authHeader,
};

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
        title: Text("PhoneBlock Anrufbeantworter"),
      ),
      body: const Padding(
        padding: EdgeInsets.symmetric(horizontal: 16),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            Text('Du hast noch keinen Anrufbeantworter, klicke den Plus-Knopf unten, um einen PhoneBlock-Anrufbeantworter anzulegen.',
              style: TextStyle(fontSize: 20),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
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
        'Authorization': authHeader,
      },
      body: "{}",
    );
    if (!context.mounted) return;

    if (response.statusCode != 200) {
      return showErrorDialog(context, response);
    }

    var creation = CreateAnswerbotResponse.read(JsonReader.fromString(response.body));
    Navigator.push(context, MaterialPageRoute(builder: (context) => _setupAnswerBot(context, creation)));
  }

  Widget _setupAnswerBot(BuildContext context, CreateAnswerbotResponse creation) {
    return Scaffold(
      appBar: AppBar(
        title: Text("Anrufbeantworter einrichten"),
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

Future<void> showErrorDialog(BuildContext context, http.Response response) {
  return showDialog(
    context: context,
    barrierDismissible: false, // user must tap button!
    builder: (BuildContext context) {
      return AlertDialog(
        title: const Text('Anlage fehlgeschlagen'),
        content: SingleChildScrollView(
          child: ListBody(
            children: <Widget>[
              Text('Der Anrufbeantworter konnte nicht angelegt werden (Fehler ${response.statusCode}).'),
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
