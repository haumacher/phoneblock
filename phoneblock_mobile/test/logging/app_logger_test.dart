import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:phoneblock_mobile/logging/app_logger.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('phoneblock/log');
  final calls = <MethodCall>[];

  setUp(() {
    calls.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      if (call.method == 'getLogDir') return '/tmp/test';
      return null;
    });
    AppLogger.resetForTest();
  });

  test('buffers calls before init, flushes after init', () async {
    AppLogger.instance.info('tag', 'before');
    expect(calls.where((c) => c.method == 'log'), isEmpty);

    await AppLogger.instance.init();
    expect(calls.where((c) => c.method == 'log').length, 1);
    final args = calls.firstWhere((c) => c.method == 'log').arguments
        as Map<Object?, Object?>;
    expect(args['msg'], 'before');
    expect(args['tag'], 'tag');
    expect(args['level'], 'info');
  });

  test('forwards calls directly after init', () async {
    await AppLogger.instance.init();
    AppLogger.instance.warn('tagX', 'hello');
    final logs = calls.where((c) => c.method == 'log').toList();
    expect(logs.length, 1);
    final args = logs.first.arguments as Map<Object?, Object?>;
    expect(args['level'], 'warn');
    expect(args['msg'], 'hello');
    expect(args['tag'], 'tagX');
  });

  test('pending buffer drops oldest beyond 50', () async {
    for (var i = 0; i < 60; i++) {
      AppLogger.instance.info('t', 'msg$i');
    }
    await AppLogger.instance.init();
    final msgs = calls
        .where((c) => c.method == 'log')
        .map((c) => (c.arguments as Map)['msg'])
        .toList();
    expect(msgs.length, 50);
    expect(msgs.first, 'msg10');
    expect(msgs.last, 'msg59');
  });

  test('channel error does not throw', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      if (call.method == 'getLogDir') return '/tmp/test';
      throw PlatformException(code: 'boom');
    });
    await AppLogger.instance.init();
    expect(() => AppLogger.instance.error('t', 'x'), returnsNormally);
  });
}
