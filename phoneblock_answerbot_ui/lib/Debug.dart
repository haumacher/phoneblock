// Todo: test only.
import 'dart:convert';
import 'dart:html';

const String username = "user";
const String password = "passwd";
final String authHeader = 'Basic ${base64Encode(utf8.encode('$username:$password'))}';
const bool debugUser = false;
const bool debugBase = false;

String contextPath = getContextPath();
String basePath = getBasePath();

String getBasePath() {
  if (debugBase) {
    return "http://localhost:8080/phoneblock";
    return "https://phoneblock.net/pb-test";
  }

  String protocol = window.location.protocol;
  String host = window.location.host;
  return "$protocol//$host$contextPath";
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
