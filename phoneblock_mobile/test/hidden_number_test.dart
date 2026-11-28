import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:phoneblock_mobile/l10n/app_localizations.dart';
import 'package:phoneblock_mobile/logging/app_logger.dart';
import 'package:phoneblock_mobile/main.dart';
import 'package:phoneblock_mobile/storage.dart';

import 'support/sqlite_channel.dart';

/// Issue #806: block calls with a withheld number.
///
/// All numbers are synthetic.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final l10n = lookupAppLocalizations(const Locale('de'));

  /// A call as `CallChecker` reports it to Flutter (`onCallScreened` / stored results).
  ScreenedCall fromNative(Map<String, Object?> data) => ScreenedCall(
        phoneNumber: data['phoneNumber'] as String,
        timestamp: DateTime.fromMillisecondsSinceEpoch(data['timestamp'] as int),
        wasBlocked: data['wasBlocked'] as bool,
        votes: 0,
      ).copyWithApiResponse(data);

  final hiddenReport = <String, Object?>{
    'phoneNumber': '',
    'wasBlocked': true,
    'votes': 0,
    'votesWildcard': 0,
    'timestamp': 1795000000000,
    'rating': 'HIDDEN',
  };

  test('a hidden call is a local rule without number', () {
    final call = fromNative(hiddenReport);
    expect(call.isHiddenNumber, isTrue);
    expect(call.hasNumber, isFalse);
    expect(call.rating, isNull);
    expect(call.isCountryBlocked, isFalse);
    expect(call.isWildcardBlocked, isFalse);
    expect(localRuleBadgeText(l10n, call), 'Anonym');

    final normal = fromNative({
      'phoneNumber': '+4930123456',
      'wasBlocked': true,
      'votes': 5,
      'timestamp': 1795000000000,
      'rating': 'G_FRAUD',
    });
    expect(normal.isHiddenNumber, isFalse);
    expect(normal.hasNumber, isTrue);
    expect(localRuleBadgeText(l10n, normal), isNull);
  });

  group('with database', () {
    late Directory dir;
    late SqliteChannel sqlite;
    late Map<String, Object?> prefs;
    const logChannel = MethodChannel('phoneblock/log');
    const secureStorageChannel =
        MethodChannel('plugins.it_nomads.com/flutter_secure_storage');

    setUpAll(() => dir = Directory.systemTemp.createTempSync('pb-806-'));
    tearDownAll(() => dir.deleteSync(recursive: true));

    setUp(() async {
      sqlite = SqliteChannel(dir)..install();
      prefs = {};
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(logChannel, (call) async => null);
      messenger.setMockMethodCallHandler(secureStorageChannel, (call) async => null);
      // Stands in for the native SharedPreferences.
      messenger.setMockMethodCallHandler(platform, (call) async {
        switch (call.method) {
          case 'getBlockHiddenNumbers':
            return prefs['block_hidden_numbers'];
          case 'setBlockHiddenNumbers':
            prefs['block_hidden_numbers'] = call.arguments;
            return null;
          case 'getCountryFilter':
            return {'enabled': false, 'countries': ['DE'], 'home': 'DE'};
        }
        return null;
      });
      AppLogger.resetForTest();
      await AppLogger.instance.init();
    });

    tearDown(() async {
      await ScreenedCallsDatabase.resetForTest();
      sqlite.uninstall();
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(platform, null);
      for (final f in dir.listSync()) {
        f.deleteSync(recursive: true);
      }
    });

    Widget app(Widget home) => MaterialApp(
          locale: const Locale('de'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: home,
        );

    void bigView(WidgetTester tester) {
      tester.view.physicalSize = const Size(1200, 3000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
    }

    Future<void> settle(WidgetTester tester) async {
      for (var i = 0; i < 5; i++) {
        await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 20)));
        await tester.pump();
      }
    }

    test('a hidden call survives the database round trip', () async {
      final db = ScreenedCallsDatabase.instance;
      await db.insertScreenedCall(fromNative(hiddenReport));
      final stored = (await db.getAllScreenedCalls()).single;
      expect(stored.phoneNumber, '');
      expect(stored.isHiddenNumber, isTrue);
      expect(stored.hasNumber, isFalse);
      expect(stored.wasBlocked, isTrue);
      expect(stored.rating, isNull);
      expect(stored.toMap()['rating'], 'HIDDEN');
    });

    Finder hiddenSwitch() =>
        find.widgetWithText(SwitchListTile, 'Anrufe mit unterdrückter Nummer blockieren');

    testWidgets('setting is off by default and round-trips through native storage',
        (tester) async {
      bigView(tester);
      await tester.pumpWidget(app(const SettingsScreen()));
      await settle(tester);

      expect(hiddenSwitch(), findsOneWidget);
      expect(tester.widget<SwitchListTile>(hiddenSwitch()).value, isFalse);
      expect(find.text('Anrufe ohne übermittelte Rufnummer werden abgewiesen.'), findsOneWidget);

      await tester.tap(hiddenSwitch());
      await settle(tester);
      expect(prefs['block_hidden_numbers'], isTrue);

      // Reopening shows the stored state.
      await tester.pumpWidget(const SizedBox());
      await tester.pumpWidget(app(const SettingsScreen()));
      await settle(tester);
      expect(tester.widget<SwitchListTile>(hiddenSwitch()).value, isTrue);

      await tester.tap(hiddenSwitch());
      await settle(tester);
      expect(prefs['block_hidden_numbers'], isFalse);
    });

    testWidgets('the call list shows a hidden call without number-based actions',
        (tester) async {
      bigView(tester);
      await tester.runAsync(() async {
        final db = ScreenedCallsDatabase.instance;
        await db.insertScreenedCall(fromNative(hiddenReport));
      });

      await tester.pumpWidget(app(const MainScreen()));
      await settle(tester);

      expect(find.text('Unterdrückte Nummer'), findsOneWidget);
      expect(find.text('Anonym'), findsOneWidget);
      expect(find.text('Blockiert'), findsOneWidget);
      expect(find.text('Keine Meldungen'), findsNothing);
      expect(find.byIcon(Icons.visibility_off), findsOneWidget);

      // Only "delete" in the menu.
      await tester.tap(find.byIcon(Icons.more_vert));
      await settle(tester);
      expect(find.text('Löschen'), findsOneWidget);
      expect(find.text('Als SPAM melden'), findsNothing);
      expect(find.text('Als legitim melden'), findsNothing);
      expect(find.text('Im Browser öffnen'), findsNothing);
      expect(find.text('Nummer kopieren'), findsNothing);
      await tester.tapAt(const Offset(5, 5));
      await settle(tester);

      // No swipe to report, and a tap opens nothing.
      final dismissible = tester.widget<Dismissible>(find.byType(Dismissible));
      expect(dismissible.direction, DismissDirection.startToEnd);
      await tester.tap(find.text('Unterdrückte Nummer'));
      await settle(tester);
      expect(find.text('Unterdrückte Nummer'), findsOneWidget);
      expect(find.byType(MainScreen), findsOneWidget);
    });

    testWidgets('a call with a number keeps its actions', (tester) async {
      bigView(tester);
      await tester.runAsync(() async {
        await ScreenedCallsDatabase.instance.insertScreenedCall(fromNative({
          'phoneNumber': '+4930123456',
          'wasBlocked': true,
          'votes': 5,
          'timestamp': 1795000000000,
          'rating': 'G_FRAUD',
          'label': 'Anruf mit Nummer',
        }));
      });

      await tester.pumpWidget(app(const MainScreen()));
      await settle(tester);
      expect(find.text('Anruf mit Nummer'), findsOneWidget);
      expect(tester.widget<Dismissible>(find.byType(Dismissible)).direction,
          DismissDirection.horizontal);

      await tester.tap(find.byIcon(Icons.more_vert));
      await settle(tester);
      expect(find.text('Als SPAM melden'), findsOneWidget);
      expect(find.text('Nummer kopieren'), findsOneWidget);
      expect(find.text('Löschen'), findsOneWidget);
    });
  });
}
