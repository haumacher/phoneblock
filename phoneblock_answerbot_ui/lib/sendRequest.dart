import 'dart:convert';

import 'package:phoneblock_answerbot_ui/base_path.dart'
  if (dart.library.html) 'package:phoneblock_answerbot_ui/base_path_web.dart';
import 'package:phoneblock_answerbot_ui/Api.dart';
import 'package:phoneblock_answerbot_ui/proto.dart';
import 'package:http/http.dart' as http;

Future<http.Response> sendRequest(SetupRequest request) async {
  var response = http.post(Uri.parse('$basePath/ab/setup'),
    encoding: const Utf8Codec(),
    headers: (await apiHeaders())
      ..addAll({
        "Content-Type": "application/json",
      }),
    body: request.toString(),
  );
  return response;
}

