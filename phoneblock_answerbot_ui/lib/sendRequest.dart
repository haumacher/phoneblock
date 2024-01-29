import 'dart:convert';

import 'package:phoneblock_answerbot_ui/Debug.dart';
import 'package:phoneblock_answerbot_ui/proto.dart';
import 'package:http/http.dart' as http;

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

