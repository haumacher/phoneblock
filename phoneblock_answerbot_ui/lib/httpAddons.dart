import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';

/// Adds contentType property to [http.Response].
extension ResponseContentType on http.Response {

  MediaType get contentType {
    var contentType = headers['content-type'];
    if (contentType != null) return MediaType.parse(contentType);
    return MediaType('application', 'octet-stream');
  }

}
