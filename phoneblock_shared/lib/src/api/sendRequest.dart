import 'dart:convert';

import '../api/base_path.dart'
  if (dart.library.html) '../api/base_path_web.dart';
import '../api/Api.dart';
import '../models/proto.dart';
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

