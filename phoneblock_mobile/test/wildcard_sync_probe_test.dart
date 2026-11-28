import 'package:flutter_test/flutter_test.dart';
import 'package:phoneblock_mobile/api.dart' as api;
import 'package:phoneblock_mobile/storage.dart';
import 'package:phoneblock_mobile/wildcard_sync_service.dart';

api.PersonalizedNumber server(String prefix) =>
    api.PersonalizedNumber(phone: prefix, wildcard: true, created: 1000);

WildcardBlock local(String prefix, int id) => WildcardBlock(
    id: id, prefix: prefix, comment: null,
    created: DateTime.fromMillisecondsSinceEpoch(1000));

void main() {
  test('migration mixes adopt, upload and keep; converges to no-op', () {
    // Before migration: A on both sides, B only local (pre-#377), C only on
    // the server (created on the website), C listed twice by the server.
    final first = WildcardSyncService.diff(
      server: [server('+4930111'), server('+4930333'), server('+4930333')],
      local: [local('+4930111', 1), local('+4930222', 2)],
      migrated: false,
    );
    expect(first.toInsert.map((w) => w.phone), ['+4930333']);
    expect(first.toUpload.map((w) => w.prefix), ['+4930222']);
    expect(first.toDelete, isEmpty);

    // After the upload succeeded and the migration completed, both sides hold A, B, C.
    final second = WildcardSyncService.diff(
      server: [server('+4930111'), server('+4930222'), server('+4930333')],
      local: [local('+4930111', 1), local('+4930222', 2), local('+4930333', 3)],
      migrated: true,
    );
    expect(second.isEmpty, isTrue);

    // Deleted on the website afterwards: B goes locally, nothing is uploaded again.
    final third = WildcardSyncService.diff(
      server: [server('+4930111'), server('+4930333')],
      local: [local('+4930111', 1), local('+4930222', 2), local('+4930333', 3)],
      migrated: true,
    );
    expect(third.toDelete.map((w) => w.prefix), ['+4930222']);
    expect(third.toUpload, isEmpty);
    expect(third.toInsert, isEmpty);
  });
}
