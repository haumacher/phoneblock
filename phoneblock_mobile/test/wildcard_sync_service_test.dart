import 'package:flutter_test/flutter_test.dart';
import 'package:phoneblock_mobile/api.dart' as api;
import 'package:phoneblock_mobile/storage.dart';
import 'package:phoneblock_mobile/wildcard_sync_service.dart';

api.PersonalizedNumber server(String prefix, {int created = 1000}) =>
    api.PersonalizedNumber(phone: prefix, wildcard: true, created: created);

WildcardBlock local(String prefix, {int id = 1}) => WildcardBlock(
      id: id,
      prefix: prefix,
      comment: null,
      created: DateTime.fromMillisecondsSinceEpoch(1000),
    );

/// In-memory stand-in for the SQLite table and the migration flag.
class FakeStore implements WildcardStore {
  final List<WildcardBlock> rules = [];
  bool migrated;
  int _nextId = 100;

  FakeStore({List<WildcardBlock> rules = const [], this.migrated = false}) {
    this.rules.addAll(rules);
  }

  List<String> get prefixes => rules.map((r) => r.prefix).toList()..sort();

  @override
  Future<List<WildcardBlock>> loadRules() async => List.of(rules);

  @override
  Future<void> insertRule(WildcardBlock rule) async {
    if (rules.any((r) => r.prefix == rule.prefix)) return;
    rules.add(WildcardBlock(id: _nextId++, prefix: rule.prefix, comment: rule.comment, created: rule.created));
  }

  @override
  Future<void> deleteRule(WildcardBlock rule) async => rules.removeWhere((r) => r.id == rule.id);

  @override
  Future<bool> isMigrated() async => migrated;

  @override
  Future<void> setMigrated() async => migrated = true;
}

/// In-memory stand-in for the user's server-side wildcard rules.
class FakeServer {
  final List<api.PersonalizedNumber> wildcards = [];
  bool uploadFails = false;
  final List<String> uploads = [];

  FakeServer([List<String> prefixes = const []]) {
    wildcards.addAll(prefixes.map(server));
  }

  Future<api.NumberList?> fetch(String authToken) async =>
      api.NumberList(numbers: [api.PersonalizedNumber(phone: '+4930555000'), ...wildcards]);

  Future<bool> upload(String prefix, String authToken) async {
    uploads.add(prefix);
    if (uploadFails) return false;
    wildcards.add(server(prefix));
    return true;
  }
}

WildcardSyncService service(FakeStore store, FakeServer srv) => WildcardSyncService(
      store: store,
      fetch: srv.fetch,
      upload: srv.upload,
      authToken: () async => 'token',
    );

void main() {
  group('WildcardSyncService.diff', () {
    test('adopts rules the server has and this device does not', () {
      // The #487 case: created on the website, never seen by this device.
      final diff = WildcardSyncService.diff(
        server: [server('+4990012')],
        local: [],
        migrated: true,
      );

      expect(diff.toInsert.map((w) => w.phone), ['+4990012']);
      expect(diff.toDelete, isEmpty);
      expect(diff.toUpload, isEmpty);
    });

    test('drops rules deleted on the server once migrated', () {
      final diff = WildcardSyncService.diff(
        server: [server('+4990012')],
        local: [local('+4990012', id: 1), local('+3312', id: 2)],
        migrated: true,
      );

      expect(diff.toInsert, isEmpty);
      expect(diff.toDelete.map((w) => w.id), [2]);
      expect(diff.toUpload, isEmpty);
    });

    test('uploads local-only rules instead of dropping them before migration', () {
      final diff = WildcardSyncService.diff(
        server: [],
        local: [local('+3312')],
        migrated: false,
      );

      expect(diff.toUpload.map((w) => w.prefix), ['+3312']);
      expect(diff.toDelete, isEmpty,
          reason: 'a rule that only exists locally must survive the migration');
    });

    test('leaves rules both sides already agree on alone', () {
      for (final migrated in [true, false]) {
        final diff = WildcardSyncService.diff(
          server: [server('+4990012')],
          local: [local('+4990012')],
          migrated: migrated,
        );
        expect(diff.isEmpty, isTrue);
      }
    });

    test('inserts a prefix the server lists twice only once', () {
      final diff = WildcardSyncService.diff(
        server: [server('+4990012'), server('+4990012')],
        local: [],
        migrated: true,
      );
      expect(diff.toInsert.map((w) => w.phone), ['+4990012']);
    });
  });

  group('WildcardSyncService.sync', () {
    test('adopts a server-only rule into the local store', () async {
      final store = FakeStore(migrated: true);
      final srv = FakeServer(['+4990012']);

      expect(await service(store, srv).sync(), isTrue);

      expect(store.prefixes, ['+4990012']);
      expect(srv.uploads, isEmpty);
    });

    test('removes a rule deleted on the server', () async {
      final store = FakeStore(rules: [local('+4990012', id: 1), local('+3312', id: 2)], migrated: true);
      final srv = FakeServer(['+4990012']);

      await service(store, srv).sync();

      expect(store.prefixes, ['+4990012']);
    });

    test('uploads a local-only rule before migration and keeps it', () async {
      final store = FakeStore(rules: [local('+3312')]);
      final srv = FakeServer();

      await service(store, srv).sync();

      expect(srv.uploads, ['+3312']);
      expect(store.prefixes, ['+3312']);
      expect(store.migrated, isTrue);
    });

    test('a failed migration upload keeps the rule and retries on the next run', () async {
      final store = FakeStore(rules: [local('+3312')]);
      final srv = FakeServer()..uploadFails = true;
      final sync = service(store, srv);

      await sync.sync();

      expect(store.prefixes, ['+3312'], reason: 'the rule must not be lost');
      expect(store.migrated, isFalse, reason: 'migration is incomplete while an upload failed');

      // Next run, still failing: the rule is neither deleted nor given up on.
      await sync.sync();
      expect(store.prefixes, ['+3312']);
      expect(store.migrated, isFalse);
      expect(srv.uploads, ['+3312', '+3312']);

      // Once the server accepts it, the migration completes and the rule is on both sides.
      srv.uploadFails = false;
      await sync.sync();
      expect(srv.uploads, ['+3312', '+3312', '+3312']);
      expect(store.migrated, isTrue);
      expect(srv.wildcards.map((w) => w.phone), ['+3312']);
      expect(store.prefixes, ['+3312']);
    });

    test('leaves the local store untouched when the server is unreachable', () async {
      final store = FakeStore(rules: [local('+3312')], migrated: true);
      final sync = WildcardSyncService(
        store: store,
        fetch: (_) async => null,
        upload: (prefix, token) async => fail('no upload expected'),
        authToken: () async => 'token',
      );

      expect(await sync.sync(), isFalse);
      expect(store.prefixes, ['+3312']);
    });

    test('skips without an auth token', () async {
      final store = FakeStore(rules: [local('+3312')], migrated: true);
      final sync = WildcardSyncService(
        store: store,
        fetch: (_) async => fail('no fetch expected'),
        upload: (prefix, token) async => fail('no upload expected'),
        authToken: () async => null,
      );

      expect(await sync.sync(), isFalse);
      expect(store.prefixes, ['+3312']);
    });

    test('is idempotent: a second run finds nothing to do', () async {
      final store = FakeStore(rules: [local('+3312', id: 1), local('+4930', id: 2)]);
      final srv = FakeServer(['+4990012', '+4930']);
      final sync = service(store, srv);

      await sync.sync();
      expect(store.prefixes, ['+3312', '+4930', '+4990012']);

      final rulesAfterFirst = await store.loadRules();
      final diff = WildcardSyncService.diff(
        server: (await srv.fetch('token'))!.numbers.where((n) => n.wildcard).toList(),
        local: rulesAfterFirst,
        migrated: store.migrated,
      );
      expect(diff.isEmpty, isTrue);

      final uploadsBefore = srv.uploads.length;
      await sync.sync();
      expect(srv.uploads.length, uploadsBefore);
      expect(store.prefixes, ['+3312', '+4930', '+4990012']);
      expect((await store.loadRules()).map((r) => r.id), rulesAfterFirst.map((r) => r.id));
    });

    test('concurrent runs during the migration do not drop the uploaded rule', () async {
      // The second run must not reconcile against a server snapshot taken before the first
      // run's upload, or it would delete the rule once the first run marks the migration done.
      final store = FakeStore(rules: [local('+3312')]);
      final srv = FakeServer();
      final sync = service(store, srv);

      await Future.wait([sync.sync(), sync.sync()]);

      expect(store.prefixes, ['+3312']);
      expect(store.migrated, isTrue);
      expect(srv.uploads, ['+3312']);
    });
  });
}
