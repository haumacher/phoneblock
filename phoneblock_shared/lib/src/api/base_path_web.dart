import 'dart:html';

import 'package:flutter/foundation.dart';

String get basePath {
  if (kDebugMode) {
    return "https://phoneblock.net/pb-test";
  }

  String contextPath = _contextPath();
  
  String protocol = window.location.protocol;
  String host = window.location.host;
  return "$protocol//$host$contextPath";
}

String _contextPath() {
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
