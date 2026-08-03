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

void main() {
  group('WildcardSyncService.diff', () {
    test('adopts rules the server has and this device does not', () {
      // The #487 case: created on the website, never seen by this device.
      final diff = WildcardSyncService.diff(
        server: [server('+4930123')],
        local: [],
        migrated: true,
      );

      expect(diff.toInsert.map((w) => w.phone), ['+4930123']);
      expect(diff.toDelete, isEmpty);
      expect(diff.toUpload, isEmpty);
    });

    test('drops rules deleted on the server once migrated', () {
      final diff = WildcardSyncService.diff(
        server: [server('+4930123')],
        local: [local('+4930123', id: 1), local('+49900', id: 2)],
        migrated: true,
      );

      expect(diff.toInsert, isEmpty);
      expect(diff.toDelete.map((w) => w.id), [2]);
    });

    test('uploads local-only rules instead of dropping them before migration', () {
      final diff = WildcardSyncService.diff(
        server: [],
        local: [local('+49900')],
        migrated: false,
      );

      expect(diff.toUpload.map((w) => w.prefix), ['+49900']);
      expect(diff.toDelete, isEmpty,
          reason: 'a rule that only exists locally must survive the migration');
    });

    test('leaves rules both sides already agree on alone', () {
      final diff = WildcardSyncService.diff(
        server: [server('+4930123')],
        local: [local('+4930123')],
        migrated: true,
      );

      expect(diff.toInsert, isEmpty);
      expect(diff.toDelete, isEmpty);
      expect(diff.toUpload, isEmpty);
    });
  });
}
