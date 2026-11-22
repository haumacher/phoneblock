import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:phoneblock_mobile/l10n/app_localizations.dart';
import 'package:phoneblock_mobile/main.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  for (final status in [400, 503]) {
    testWidgets('whitelist validates inline and reports server status $status',
        (tester) async {
      appVersion = 'test';
      SharedPreferences.setMockInitialValues({'auth_token': 'test'});
      final posts = <String>[];
      await http.runWithClient(() async {
        await tester.pumpWidget(const MaterialApp(
          locale: Locale('de'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: PersonalizedNumberListScreen(
            authToken: 'test',
            listType: PersonalizedListType.whitelist,
          ),
        ));
        await tester.pumpAndSettle();
        await tester.tap(find.byType(FloatingActionButton));
        await tester.pumpAndSettle();
        await tester.enterText(find.byType(TextField), '+49 30*');
        await tester.tap(find.text('Weiter'));
        await tester.pumpAndSettle();
        expect(find.text('Platzhalter (*) sind nur in der Sperrliste möglich.'),
            findsOneWidget);
        expect(find.byType(AlertDialog), findsOneWidget);
        expect(posts, isEmpty);

        await tester.enterText(find.byType(TextField), '+49 abc');
        await tester.tap(find.text('Weiter'));
        await tester.pumpAndSettle();
        expect(find.text('Ungültige Telefonnummer'), findsOneWidget);
        expect(find.byType(AlertDialog), findsOneWidget);
        expect(posts, isEmpty);

        await tester.enterText(find.byType(TextField), '+49 30 1234-567');
        await tester.tap(find.text('Weiter'));
        await tester.pumpAndSettle();
        expect(jsonDecode(posts.single)['phone'], '+49301234567');
        expect(
          find.text(status == 400
              ? 'Die Nummer wurde vom Server nicht akzeptiert. Bitte prüfen Sie das Format.'
              : 'Fehler beim Melden: 503'),
          findsOneWidget,
        );
      },
          () => MockClient((request) async {
                if (request.method == 'POST') {
                  posts.add(request.body);
                  return http.Response('', status);
                }
                return http.Response('{"numbers":[]}', 200);
              }));
    });
  }

  testWidgets('whitelist entries scroll above the FAB and navigation inset',
      (tester) async {
    appVersion = 'test';
    await http.runWithClient(() async {
      await tester.pumpWidget(MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context)
              .copyWith(padding: const EdgeInsets.only(bottom: 24)),
          child: child!,
        ),
        home: const PersonalizedNumberListScreen(
          authToken: 'test',
          listType: PersonalizedListType.whitelist,
        ),
      ));
      await tester.pumpAndSettle();

      final list = tester.widget<ListView>(find.byType(ListView));
      expect(
          (list.padding! as EdgeInsets).bottom, greaterThanOrEqualTo(80 + 24));
      await tester.drag(find.byType(ListView), const Offset(0, -2000));
      await tester.pumpAndSettle();
      final editButton = find.descendant(
        of: find.byKey(const Key('+49305550119')),
        matching: find.byIcon(Icons.edit_outlined),
      );
      final fab = find.byType(FloatingActionButton);
      expect(tester.getBottomRight(editButton).dy,
          lessThan(tester.getTopLeft(fab).dy));
    },
        () => MockClient((request) async => http.Response(
            jsonEncode({
              'numbers': List.generate(
                  20,
                  (index) => {
                        'phone':
                            '+493055501${index.toString().padLeft(2, '0')}',
                      }),
            }),
            200)));
  });
}
