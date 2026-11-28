import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:phoneblock_mobile/api.dart' as api;
import 'package:phoneblock_mobile/blocklist_sync_service.dart';
import 'package:phoneblock_mobile/sync_catch_up.dart';

final DateTime now = DateTime(2026, 11, 28, 12);

DateTime ago(Duration d) => now.subtract(d);

bool decide({bool loggedIn = true, DateTime? lastSuccess, DateTime? lastFailedAttempt}) => shouldCatchUp(
      loggedIn: loggedIn,
      lastSuccess: lastSuccess,
      lastFailedAttempt: lastFailedAttempt,
      now: now,
    );

/// In-memory stand-in for the sync metadata and the sync itself.
class FakeEnv {
  bool loggedIn = true;
  DateTime? lastSuccess;
  DateTime? lastFailedAttempt;
  int syncs = 0;
  Completer<bool> pending = Completer<bool>();

  SyncCatchUp catchUp() => SyncCatchUp(
        loggedIn: () async => loggedIn,
        lastSuccess: () async => lastSuccess,
        lastFailedAttempt: () async => lastFailedAttempt,
        setLastFailedAttempt: (t) async => lastFailedAttempt = t,
        sync: () {
          syncs++;
          return pending.future;
        },
        now: () => now,
      );
}

void main() {
  group('shouldCatchUp', () {
    test('last success 37 h ago catches up', () {
      expect(decide(lastSuccess: ago(const Duration(hours: 37))), isTrue);
    });

    test('last success 35 h ago does not', () {
      expect(decide(lastSuccess: ago(const Duration(hours: 35))), isFalse);
    });

    test('never synced while logged in catches up', () {
      expect(decide(lastSuccess: null), isTrue);
    });

    test('a catch-up that failed 5 min ago is not retried yet', () {
      expect(
          decide(
              lastSuccess: ago(const Duration(hours: 40)),
              lastFailedAttempt: ago(const Duration(minutes: 5))),
          isFalse);
    });

    test('a catch-up that failed 20 min ago is retried', () {
      expect(
          decide(
              lastSuccess: ago(const Duration(hours: 40)),
              lastFailedAttempt: ago(const Duration(minutes: 20))),
          isTrue);
    });

    test('not logged in never catches up', () {
      expect(decide(loggedIn: false, lastSuccess: null), isFalse);
      expect(decide(loggedIn: false, lastSuccess: ago(const Duration(days: 10))), isFalse);
    });

    test('timestamps in the future (clock set back) count as unknown', () {
      expect(decide(lastSuccess: now.add(const Duration(days: 2))), isTrue);
      expect(
          decide(
              lastSuccess: ago(const Duration(hours: 40)),
              lastFailedAttempt: now.add(const Duration(days: 2))),
          isTrue);
    });
  });

  group('SyncCatchUp', () {
    test('runs the sync once, ignoring checks while it runs', () async {
      final env = FakeEnv();
      final catchUp = env.catchUp();

      expect(await catchUp.check('app start'), isTrue);
      expect(await catchUp.check('resume'), isFalse);
      expect(env.syncs, 1);

      env.lastSuccess = now;
      env.pending.complete(true);
      await catchUp.running;

      expect(catchUp.running, isNull);
      expect(env.lastFailedAttempt, isNull);
      expect(await catchUp.check('resume'), isFalse, reason: 'just synced');
      expect(env.syncs, 1);
    });

    test('concurrent checks start a single sync', () async {
      final env = FakeEnv();
      final catchUp = env.catchUp();

      final results = await Future.wait([catchUp.check('a'), catchUp.check('b')]);

      expect(results.where((r) => r), hasLength(1));
      expect(env.syncs, 1);
    });

    test('a failure is recorded and throttles the next catch-up', () async {
      final env = FakeEnv()..lastSuccess = ago(const Duration(hours: 40));
      final catchUp = env.catchUp();

      expect(await catchUp.check('resume'), isTrue);
      env.pending.complete(false);
      await catchUp.running;

      expect(env.lastFailedAttempt, now);
      expect(await catchUp.check('resume'), isFalse);
      expect(env.syncs, 1);
    });

    test('a crashing sync does not escape and counts as failure', () async {
      final env = FakeEnv();
      final catchUp = env.catchUp();

      expect(await catchUp.check('resume'), isTrue);
      env.pending.completeError(StateError('boom'));
      await catchUp.running;

      expect(catchUp.running, isNull);
      expect(env.lastFailedAttempt, now);
    });

    test('a failing state read does not throw', () async {
      final catchUp = SyncCatchUp(
        loggedIn: () async => true,
        lastSuccess: () async => throw StateError('db closed'),
        lastFailedAttempt: () async => null,
        setLastFailedAttempt: (_) async {},
        sync: () async => true,
        now: () => now,
      );

      expect(await catchUp.check('resume'), isFalse);
    });
  });

  group('BlocklistSyncService', () {
    test('concurrent syncs join the running one', () async {
      final store = FakeBlocklistStore();
      final server = FakeBlocklistServer()..gate = Completer<void>();
      var wildcardRuns = 0;
      final service = syncService(store, server, wildcards: () async {
        wildcardRuns++;
        return true;
      });

      final first = service.sync();
      final second = service.sync();
      server.gate!.complete();

      expect(await first, isTrue);
      expect(await second, isTrue);
      expect(server.requests, [5]);
      expect(wildcardRuns, 1);

      // Once done, the next call runs again.
      server.gate = null;
      expect(await service.sync(), isTrue);
      expect(server.requests, [5, 7]);
    });

    test('announces successful syncs only', () async {
      final store = FakeBlocklistStore();
      final server = FakeBlocklistServer()..status = 500;
      final service = syncService(store, server);
      var announced = 0;
      final subscription = service.onSynced.listen((_) => announced++);

      expect(await service.sync(), isFalse);
      await Future<void>.delayed(Duration.zero);
      expect(announced, 0);

      server.status = 200;
      expect(await service.sync(), isTrue);
      await Future<void>.delayed(Duration.zero);
      expect(announced, 1);

      await subscription.cancel();
    });

    test('wildcard sync runs even if the community sync failed', () async {
      var wildcardRuns = 0;
      final service = syncService(FakeBlocklistStore(), FakeBlocklistServer()..status = 500,
          wildcards: () async {
        wildcardRuns++;
        return true;
      });

      expect(await service.sync(), isFalse);
      expect(wildcardRuns, 1);
    });

    test('an incremental sync updates the cache', () async {
      final store = FakeBlocklistStore();
      final service = syncService(store, FakeBlocklistServer());

      expect(await service.sync(), isTrue);

      expect(store.phones, ['+49111', '+49222', '+49333']);
      expect(store.version, 7);
      expect(store.syncCount, 3);
    });

    test('a failed full resync leaves cache and version untouched, the next sync is incremental',
        () async {
      final store = FakeBlocklistStore(syncCount: BlocklistSyncService.fullResyncInterval);
      final server = FakeBlocklistServer()..status = 503;
      final service = syncService(store, server);

      expect(await service.sync(), isFalse);
      expect(server.requests, [0], reason: 'the full resync turn fetches everything');
      expect(store.phones, ['+49111', '+49222']);
      expect(store.version, 5);

      // The retry is incremental from the old version, not another full download.
      server.status = 200;
      expect(await service.sync(), isTrue);
      expect(server.requests, [0, 5]);
      expect(store.phones, ['+49111', '+49222', '+49333']);
      expect(store.version, 7);
    });

    test('an incomplete full resync response does not replace the cache', () async {
      final store = FakeBlocklistStore(syncCount: BlocklistSyncService.fullResyncInterval);
      final server = FakeBlocklistServer()..body = '{"numbers": [';
      final service = syncService(store, server);

      expect(await service.sync(), isFalse);
      expect(store.phones, ['+49111', '+49222']);
      expect(store.version, 5);

      server.body = null;
      expect(await service.sync(), isTrue);
      expect(server.requests, [0, 5]);
    });

    test('a successful full resync replaces the cache and the version', () async {
      final store = FakeBlocklistStore(syncCount: BlocklistSyncService.fullResyncInterval);
      final server = FakeBlocklistServer()..full = [entry('+49999')];
      final service = syncService(store, server);

      expect(await service.sync(), isTrue);

      expect(server.requests, [0]);
      expect(store.phones, ['+49999']);
      expect(store.version, 7);
      expect(store.syncCount, 1);

      // The next sync is incremental from the new version.
      expect(await service.sync(), isTrue);
      expect(server.requests, [0, 7]);
    });
  });
}

api.BlockListEntry entry(String phone, {int votes = 4}) =>
    api.BlockListEntry(phone: phone, votes: votes, rating: api.Rating.gFraud);

/// In-memory stand-in for the `blocklist` cache and its sync metadata.
class FakeBlocklistStore implements BlocklistStore {
  final Set<String> _phones = {'+49111', '+49222'};
  int version = 5;
  int syncCount;
  int lastSyncTime = 1000;

  FakeBlocklistStore({this.syncCount = 2});

  List<String> get phones => _phones.toList()..sort();

  @override
  Future<Map<String, dynamic>> getSyncInfo() async =>
      {'version': version, 'lastSyncTime': lastSyncTime, 'syncOffset': 0, 'syncCount': syncCount};

  @override
  Future<(int, int)> apply(List<api.BlockListEntry> entries, int version, {required bool replace}) async {
    if (replace) _phones.clear();
    var upserted = 0;
    var deleted = 0;
    for (final e in entries) {
      if (e.votes > 0) {
        _phones.add(e.phone);
        upserted++;
      } else {
        _phones.remove(e.phone);
        deleted++;
      }
    }
    this.version = version;
    syncCount = replace ? 1 : syncCount + 1;
    lastSyncTime = 2000;
    return (upserted, deleted);
  }

  @override
  Future<void> restartFullResyncCycle() async => syncCount = 0;
}

/// In-memory stand-in for `GET /api/blocklist?since=`: version 7, the full list is [full],
/// changes since an older version add `+49333`.
class FakeBlocklistServer {
  final List<int> requests = [];
  int status = 200;
  String? body;
  Completer<void>? gate;
  List<api.BlockListEntry> full = [entry('+49111'), entry('+49222'), entry('+49333')];

  Future<http.Response> fetch(int since, String authToken) async {
    requests.add(since);
    if (gate != null) await gate!.future;
    if (status != 200) return http.Response('error', status);
    final numbers = since == 0 ? full : since < 7 ? [entry('+49333')] : <api.BlockListEntry>[];
    return http.Response(body ?? _json(numbers, 7), 200);
  }

  static String _json(List<api.BlockListEntry> numbers, int version) => jsonEncode({
        'numbers': [
          for (final n in numbers) {'phone': n.phone, 'votes': n.votes, 'rating': 'G_FRAUD', 'lastActivity': 0},
        ],
        'version': version,
      });
}

BlocklistSyncService syncService(FakeBlocklistStore store, FakeBlocklistServer server,
        {Future<bool> Function()? wildcards}) =>
    BlocklistSyncService(
      store: store,
      fetch: server.fetch,
      authToken: () async => 'token',
      wildcards: wildcards ?? () async => true,
    );
