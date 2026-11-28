import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fritz_tr064/fritz_tr064.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_models.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_status.dart';
import 'package:phoneblock_mobile/l10n/app_localizations_de.dart';
import 'package:phoneblock_mobile/logging/app_logger.dart';

/// Issue #544: the menu and the Fritz!Box page must show the same protection
/// status, and a failed online-phonebook query must neither be reported as a
/// sync error nor change the blocklist mode.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const logChannel = MethodChannel('phoneblock/log');
  final logs = <Map<Object?, Object?>>[];

  setUp(() async {
    logs.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(logChannel, (call) async {
      if (call.method == 'log') {
        logs.add(call.arguments as Map<Object?, Object?>);
      }
      return null;
    });
    AppLogger.resetForTest();
    await AppLogger.instance.init();
  });

  OnlinePhonebookInfo phonebook({
    bool enable = true,
    int status = OnlinePhonebookStatus.ok,
    String lastConnect = '2026-11-28 00:00',
  }) =>
      OnlinePhonebookInfo(
        enable: enable,
        status: status,
        lastConnect: lastConnect,
        url: 'https://phoneblock.net/phoneblock/contacts/addresses/user/',
        serviceId: OnlinePhonebookServiceId.cardDav,
        username: 'user',
        name: 'PhoneBlock SPAM',
      );

  /// Finds the first phonebook, standing in for the URL match.
  int? findFirst(List<(int, OnlinePhonebookInfo)> books) =>
      books.isEmpty ? null : books.first.$1;

  group('fritzBoxStatus', () {
    const connected = FritzBoxConnectionState.connected;

    test('synced PhoneBlock phonebook means protected', () {
      expect(fritzBoxStatus(connected, CardDavStatus.synced),
          FritzBoxStatus.protected);
    });

    test('anything but a synced phonebook is neutral connected', () {
      for (final s in CardDavStatus.values.where((s) => s != CardDavStatus.synced)) {
        expect(fritzBoxStatus(connected, s), FritzBoxStatus.connected,
            reason: '$s');
      }
    });

    test('connection states other than connected are passed through', () {
      for (final s in CardDavStatus.values) {
        expect(fritzBoxStatus(FritzBoxConnectionState.offline, s),
            FritzBoxStatus.offline);
        expect(fritzBoxStatus(FritzBoxConnectionState.error, s),
            FritzBoxStatus.error);
        expect(fritzBoxStatus(FritzBoxConnectionState.notConfigured, s),
            FritzBoxStatus.notConfigured);
      }
    });

    test('texts: protected vs. plain connected, no "not protected" claim', () {
      final l10n = AppLocalizationsDe();
      expect(fritzBoxStatusText(l10n, FritzBoxStatus.protected),
          'Verbunden und geschützt');
      expect(fritzBoxStatusText(l10n, FritzBoxStatus.connected), 'Verbunden');
      expect(fritzBoxStatusColor(FritzBoxStatus.protected), isNotNull);
      expect(fritzBoxStatusColor(FritzBoxStatus.connected), isNull,
          reason: 'neutral look, theme default color');
    });
  });

  group('resolveCardDavStatus', () {
    test('query failure yields unknown, not a sync error, and is logged',
        () async {
      final status = await resolveCardDavStatus(
          () async => throw Exception('OnTel service not available'), findFirst);

      expect(status, CardDavStatus.unknown);
      final errors = logs.where((l) => l['level'] == 'error').toList();
      expect(errors, hasLength(1));
      expect(errors.single['tag'], 'fritzbox');
      expect(errors.single['error'], contains('OnTel service not available'));
    });

    test('zero phonebooks means not configured', () async {
      final status = await resolveCardDavStatus(() async => (entries: <(int, OnlinePhonebookInfo)>[], complete: true), findFirst);
      expect(status, CardDavStatus.notConfigured);
      expect(logs.where((l) => l['level'] == 'error'), isEmpty);
    });

    test('synced PhoneBlock phonebook', () async {
      final status =
          await resolveCardDavStatus(() async => (entries: [(1, phonebook())], complete: true), findFirst);
      expect(status, CardDavStatus.synced);
    });

    test('sync error of an existing PhoneBlock phonebook stays an error',
        () async {
      final status = await resolveCardDavStatus(
          () async => (
                entries: [
                  (1, phonebook(status: OnlinePhonebookStatus.authenticationFailure))
                ],
                complete: true,
              ),
          findFirst);
      expect(status, CardDavStatus.error);
    });

    test(
        'entry 2 of 3 unreadable, PhoneBlock not among the readable ones: '
        'unknown, cardDav mode unchanged', () async {
      final status = await resolveCardDavStatus(
          () async => (entries: [(1, phonebook()), (3, phonebook())], complete: false),
          (_) => null);
      expect(status, CardDavStatus.unknown);
      expect(reconcileBlocklistMode(status, BlocklistMode.cardDav), isNull);
      expect(logs.where((l) => l['level'] == 'error'), hasLength(1));
    });

    test('entry 2 unreadable, entry 1 is the synced PhoneBlock phonebook: synced',
        () async {
      final status = await resolveCardDavStatus(
          () async => (entries: [(1, phonebook()), (3, phonebook())], complete: false),
          findFirst);
      expect(status, CardDavStatus.synced);
    });

    test('other phonebooks only: not configured', () async {
      final status = await resolveCardDavStatus(
          () async => (entries: [(1, phonebook())], complete: true), (_) => null);
      expect(status, CardDavStatus.notConfigured);
    });
  });

  group('reconcileBlocklistMode', () {
    test('unknown status never changes the mode', () {
      for (final mode in BlocklistMode.values) {
        expect(reconcileBlocklistMode(CardDavStatus.unknown, mode), isNull,
            reason: '$mode');
      }
    });

    test('no phonebook resets cardDav mode, leaves none alone', () {
      expect(reconcileBlocklistMode(CardDavStatus.notConfigured, BlocklistMode.cardDav),
          BlocklistMode.none);
      expect(reconcileBlocklistMode(CardDavStatus.notConfigured, BlocklistMode.none),
          isNull);
    });

    test('existing phonebook switches none to cardDav', () {
      for (final s in [
        CardDavStatus.synced,
        CardDavStatus.syncPending,
        CardDavStatus.error,
        CardDavStatus.disabled,
      ]) {
        expect(reconcileBlocklistMode(s, BlocklistMode.none), BlocklistMode.cardDav,
            reason: '$s');
        expect(reconcileBlocklistMode(s, BlocklistMode.cardDav), isNull,
            reason: '$s');
      }
    });

    test('failed query end to end: mode stays none, not switched to phonebook',
        () async {
      final status = await resolveCardDavStatus(
          () async => throw Exception('timeout'), findFirst);
      expect(reconcileBlocklistMode(status, BlocklistMode.none), isNull);
      expect(fritzBoxStatus(FritzBoxConnectionState.connected, status),
          FritzBoxStatus.connected);
    });

    test('zero phonebooks end to end: neutral, mode not phonebook', () async {
      final status = await resolveCardDavStatus(() async => (entries: <(int, OnlinePhonebookInfo)>[], complete: true), findFirst);
      expect(reconcileBlocklistMode(status, BlocklistMode.none), isNull);
      expect(reconcileBlocklistMode(status, BlocklistMode.cardDav),
          BlocklistMode.none);
      expect(fritzBoxStatus(FritzBoxConnectionState.connected, status),
          FritzBoxStatus.connected);
    });
  });
}
