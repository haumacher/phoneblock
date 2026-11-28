import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:phoneblock_mobile/country_filter.dart';
import 'package:phoneblock_mobile/l10n/app_localizations.dart';
import 'package:phoneblock_mobile/main.dart';
import 'package:phoneblock_mobile/storage.dart';

/// Stands in for the native SharedPreferences behind the method channel.
class FakeNative {
  bool? enabled;
  List<String>? countries;
  final calls = <MethodCall>[];

  Future<Object?> handle(MethodCall call) async {
    calls.add(call);
    switch (call.method) {
      case 'getCountryFilter':
        return {
          'enabled': enabled ?? false,
          'countries': countries ?? ['DE'],
          'home': 'DE',
        };
      case 'setCountryFilter':
        final args = call.arguments as Map;
        enabled = args['enabled'] as bool;
        countries = List<String>.from(args['countries'] as List);
        return null;
      case 'getCountries':
        return [
          {'region': 'DE', 'name': 'Deutschland', 'code': 49},
          {'region': 'CA', 'name': 'Kanada', 'code': 1},
          {'region': 'AT', 'name': 'Österreich', 'code': 43},
          {'region': 'US', 'name': 'Vereinigte Staaten', 'code': 1},
        ];
    }
    return null;
  }
}

void main() {
  late FakeNative native;

  setUp(() {
    native = FakeNative();
    TestWidgetsFlutterBinding.ensureInitialized()
        .defaultBinaryMessenger
        .setMockMethodCallHandler(platform, native.handle);
  });

  tearDown(() {
    TestWidgetsFlutterBinding.ensureInitialized()
        .defaultBinaryMessenger
        .setMockMethodCallHandler(platform, null);
  });

  Widget screen() => const MaterialApp(
        locale: Locale('de'),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: CountryFilterScreen(),
      );

  testWidgets('country filter settings round-trip through native storage', (tester) async {
    await tester.pumpWidget(screen());
    await tester.pumpAndSettle();

    // Off by default; the list appears once enabled, pre-filled with the home country.
    expect(find.text('Deutschland (+49)'), findsNothing);
    await tester.tap(find.byType(Switch));
    await tester.pumpAndSettle();
    expect(native.enabled, isTrue);
    expect(native.countries, ['DE']);
    expect(find.text('Deutschland (+49)'), findsOneWidget);

    // Add a country from the searchable picker.
    await tester.tap(find.text('Land hinzufügen'));
    await tester.pumpAndSettle();
    expect(find.text('Deutschland (+49)'), findsNothing, reason: 'already allowed');
    await tester.enterText(find.byType(TextField), 'öster');
    await tester.pumpAndSettle();
    expect(find.text('Kanada (+1)'), findsNothing);
    await tester.tap(find.text('Österreich (+43)'));
    await tester.pumpAndSettle();
    expect(native.countries, ['DE', 'AT']);
    expect(find.text('Österreich (+43)'), findsOneWidget);

    // Remove by tap.
    await tester.tap(find.byTooltip('Entfernen').first);
    await tester.pumpAndSettle();
    expect(native.countries, ['AT']);

    // The last country stays.
    await tester.tap(find.byTooltip('Entfernen'));
    await tester.pumpAndSettle();
    expect(native.countries, ['AT']);
    expect(find.text('Mindestens ein Land muss erlaubt bleiben.'), findsOneWidget);

    // Reopening shows the stored state.
    await tester.pumpWidget(const SizedBox());
    await tester.pumpWidget(screen());
    await tester.pumpAndSettle();
    expect(tester.widget<Switch>(find.byType(Switch)).value, isTrue);
    expect(find.text('Österreich (+43)'), findsOneWidget);
    expect(find.text('Deutschland (+49)'), findsNothing);
    expect(native.calls.lastWhere((c) => c.method == 'getCountries').arguments, 'de');

    // Switching off keeps the list for later.
    await tester.tap(find.byType(Switch));
    await tester.pumpAndSettle();
    expect(native.enabled, isFalse);
    expect(native.countries, ['AT']);
  });

  testWidgets('picker names the countries sharing a calling code', (tester) async {
    native.enabled = true;
    await tester.pumpWidget(screen());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Land hinzufügen'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), '+1');
    await tester.pumpAndSettle();
    expect(find.text('Kanada (+1)'), findsOneWidget);
    expect(find.text('Gilt auch für Vereinigte Staaten'), findsOneWidget);
    expect(find.text('Gilt auch für Kanada'), findsOneWidget);
    expect(find.text('Österreich (+43)'), findsNothing);
  });

  group('call list', () {
    final l10n = lookupAppLocalizations(const Locale('de'));

    ScreenedCall fromNative(Map<String, Object?> data) => ScreenedCall(
          phoneNumber: data['phoneNumber'] as String,
          timestamp: DateTime.fromMillisecondsSinceEpoch(data['timestamp'] as int),
          wasBlocked: data['wasBlocked'] as bool,
          votes: 0,
        ).copyWithApiResponse(data);

    test('a call blocked by the country filter shows the reason "Länderfilter"', () {
      final reported = fromNative({
        'phoneNumber': '+43123456789',
        'wasBlocked': true,
        'votes': 5,
        'votesWildcard': 0,
        'timestamp': 1700000000000,
        'rating': 'COUNTRY',
      });
      expect(reported.isCountryBlocked, isTrue);
      expect(reported.isWildcardBlocked, isFalse);
      expect(reported.rating, isNull);
      expect(reported.votes, 5);

      // Survives the database round trip.
      final stored = ScreenedCall.fromMap(reported.toMap());
      expect(stored.isCountryBlocked, isTrue);
      expect(stored.wasBlocked, isTrue);
      expect(localRuleBadgeText(l10n, stored), 'Länderfilter');
    });

    test('other calls keep their labels', () {
      final wildcard = ScreenedCall.fromMap(fromNative({
        'phoneNumber': '+49900123456',
        'wasBlocked': true,
        'timestamp': 1700000000000,
        'rating': 'WILDCARD',
        'matchedWildcard': '+4990012',
      }).toMap());
      expect(wildcard.isCountryBlocked, isFalse);
      expect(localRuleBadgeText(l10n, wildcard), 'Filter: +4990012*');

      final voted = ScreenedCall.fromMap(fromNative({
        'phoneNumber': '+4930123456',
        'wasBlocked': true,
        'votes': 5,
        'timestamp': 1700000000000,
        'rating': 'G_FRAUD',
      }).toMap());
      expect(voted.isCountryBlocked, isFalse);
      expect(localRuleBadgeText(l10n, voted), isNull);
    });
  });
}
