import 'package:flutter_test/flutter_test.dart';
import 'package:phoneblock_mobile/logging/caller_source.dart';

void main() {
  test('extractCallerSource returns file:line from first frame outside logging/', () {
    final trace = StackTrace.fromString('''
#0      AppLogger.info (package:phoneblock_mobile/logging/app_logger.dart:42:5)
#1      fetchNumber (package:phoneblock_mobile/api.dart:123:7)
#2      main (package:phoneblock_mobile/main.dart:17:3)
''');
    final src = extractCallerSource(trace);
    expect(src, 'api.dart:123');
  });

  test('returns null when no non-logging frame exists', () {
    final trace = StackTrace.fromString('''
#0      AppLogger.info (package:phoneblock_mobile/logging/app_logger.dart:42:5)
''');
    expect(extractCallerSource(trace), isNull);
  });

  test('skips caller_source.dart itself', () {
    final trace = StackTrace.fromString('''
#0      extractCallerSource (package:phoneblock_mobile/logging/caller_source.dart:9:3)
#1      AppLogger.info (package:phoneblock_mobile/logging/app_logger.dart:42:5)
#2      doWork (package:phoneblock_mobile/x.dart:10:1)
''');
    expect(extractCallerSource(trace), 'x.dart:10');
  });
}
