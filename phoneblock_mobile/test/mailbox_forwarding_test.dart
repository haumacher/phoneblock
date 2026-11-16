import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:phoneblock_mobile/l10n/app_localizations.dart';
import 'package:phoneblock_mobile/mailbox_forwarding_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  test('service codes are encoded as tel URIs', () {
    expect(mailboxDialerUri('*#67#').toString(), 'tel:*%2367%23');
    expect(mailboxDialerUri('#67#').toString(), 'tel:%2367%23');
    expect(mailboxDialerUri('*67#').toString(), 'tel:*67%23');
    expect(
      mailboxDialerUri('**67*+493012345678#').toString(),
      'tel:**67*+493012345678%23',
    );
  });

  test('mailbox number accepts only digits and an optional leading plus', () {
    for (final number in ['0', '123456789', '+493012345678']) {
      expect(isValidMailboxNumber(number), isTrue, reason: number);
    }
    for (final number in [
      '',
      '+',
      '12+34',
      '++123',
      '12#34',
      '*123',
      '12 34',
      '123\n',
    ]) {
      expect(isValidMailboxNumber(number), isFalse, reason: number);
    }
  });

  testWidgets('a valid mailbox number survives reopening the screen', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    tester.view.physicalSize = const Size(1200, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    Widget screen() => MaterialApp(
          locale: const Locale('de'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const MailboxForwardingScreen(),
        );

    await tester.pumpWidget(screen());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Falls das Einschalten nicht klappt'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), '+493012345678');
    await tester.pumpAndSettle();
    await tester.pumpWidget(const SizedBox());
    await tester.pumpWidget(screen());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Falls das Einschalten nicht klappt'));
    await tester.pumpAndSettle();
    expect(find.text('+493012345678'), findsOneWidget);

    await tester.enterText(find.byType(TextField), '+49#');
    await tester.pumpAndSettle();
    await tester.tap(find.text('Mit Mailboxnummer einschalten'));
    await tester.pumpAndSettle();
    expect(find.text('Geben Sie nur Ziffern und optional ein führendes + ein.'),
        findsOneWidget);
    final preferences = await SharedPreferences.getInstance();
    expect(preferences.getString('mailbox_forwarding_number'), isNull);
  });

  testWidgets('dialer launch failure is visible', (tester) async {
    SharedPreferences.setMockInitialValues({});
    const channel = MethodChannel('plugins.flutter.io/url_launcher');
    MethodCall? launch;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, (
      call,
    ) async {
      launch = call;
      return false;
    });
    addTearDown(() {
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        channel,
        null,
      );
    });
    await tester.pumpWidget(
      const MaterialApp(
        locale: Locale('de'),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: MailboxForwardingScreen(),
      ),
    );
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Status prüfen'));
    await tester.tap(find.text('Status prüfen'));
    await tester.pumpAndSettle();
    expect(launch?.method, 'launch');
    expect((launch?.arguments as Map)['url'], 'tel:*%2367%23');
    expect((launch?.arguments as Map)['useWebView'], false);
    expect(find.text('Die Telefon-App konnte nicht geöffnet werden.'),
        findsOneWidget);
  });
}
