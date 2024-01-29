import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

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
