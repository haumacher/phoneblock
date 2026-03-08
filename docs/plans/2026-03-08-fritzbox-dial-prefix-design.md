# Fritz!Box Dial Prefix Query — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Query the Fritz!Box's configured country/area codes via TR-064 on connect, and use them for normalizing call list numbers instead of hardcoding "DE".

**Architecture:** On connect, query the Fritz!Box VoIP service for LKZ (country code), LKZPrefix (international dialing prefix), and OKZPrefix (trunk prefix). Store these in the existing `fritzbox_config` SQLite table. During call sync, use a new Dart-side `toInternationalForm()` function that mirrors `PhoneHash.toInternationalForm()` from the Java shared code.

**Tech Stack:** Flutter/Dart, `fritz_tr064` package (already a dependency), SQLite via `sqflite`

---

### Task 1: Add `toInternationalForm()` Dart utility

**Files:**
- Create: `phoneblock_mobile/lib/fritzbox/phone_number_utils.dart`
- Create: `phoneblock_mobile/test/phone_number_utils_test.dart`

**Step 1: Write the failing tests**

```dart
// test/phone_number_utils_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:phoneblock_mobile/fritzbox/phone_number_utils.dart';

void main() {
  group('toInternationalForm', () {
    // German Fritz!Box (lkz="49", lkzPrefix="00", okzPrefix="0")
    test('German local number with trunk prefix', () {
      expect(toInternationalForm('022376922894', '49', '00', '0'), '+4922376922894');
    });

    test('German international number with 00 prefix', () {
      expect(toInternationalForm('00441234567890', '49', '00', '0'), '+441234567890');
    });

    test('already international with + prefix', () {
      expect(toInternationalForm('+441234567890', '49', '00', '0'), '+441234567890');
    });

    test('rejects 000 prefix as invalid', () {
      expect(toInternationalForm('000123456', '49', '00', '0'), isNull);
    });

    // US Fritz!Box (lkz="1", lkzPrefix="011", okzPrefix="1")
    test('US local number with trunk prefix', () {
      expect(toInternationalForm('12125551234', '1', '011', '1'), '+12125551234');
    });

    test('US international number with 011 prefix', () {
      expect(toInternationalForm('01144207946000', '1', '011', '1'), '+44207946000');
    });

    // Italian Fritz!Box (lkz="39", lkzPrefix="00", okzPrefix="")
    test('Italian number with empty trunk prefix', () {
      expect(toInternationalForm('0612345678', '39', '00', ''), '+390612345678');
    });

    // Hungarian Fritz!Box (lkz="36", lkzPrefix="00", okzPrefix="06")
    test('Hungarian local number with 06 trunk prefix', () {
      expect(toInternationalForm('0611234567', '36', '00', '06'), '+3611234567');
    });

    test('rejects number without valid trunk prefix', () {
      expect(toInternationalForm('5551234', '49', '00', '0'), isNull);
    });

    test('rejects too-short number', () {
      expect(toInternationalForm('012345', '49', '00', '0'), isNull);
    });
  });
}
```

**Step 2: Run tests to verify they fail**

Run: `cd phoneblock_mobile && flutter test test/phone_number_utils_test.dart`
Expected: FAIL — `phone_number_utils.dart` does not exist yet

**Step 3: Write minimal implementation**

```dart
// lib/fritzbox/phone_number_utils.dart

/// Converts a phone number to international format using Fritz!Box dialing parameters.
///
/// [phone] - The raw phone number from the Fritz!Box call list.
/// [lkz] - Country calling code without "+" (e.g., "49" for Germany).
/// [lkzPrefix] - International dialing prefix (e.g., "00" for Europe, "011" for US).
/// [okzPrefix] - Domestic trunk prefix (e.g., "0" for Germany, "" for Italy).
///
/// Returns the phone number in international format (starting with "+"), or null if invalid.
String? toInternationalForm(String phone, String lkz, String lkzPrefix, String okzPrefix) {
  String? plus;

  if (phone.startsWith(lkzPrefix) && lkzPrefix.isNotEmpty) {
    if (phone.startsWith('${lkzPrefix}0') && lkzPrefix == '00') {
      // 000... is not a phone number
      return null;
    }
    plus = '+${phone.substring(lkzPrefix.length)}';
  } else if (phone.startsWith('+')) {
    plus = phone;
  } else if (okzPrefix.isNotEmpty && phone.startsWith(okzPrefix)) {
    plus = '+$lkz${phone.substring(okzPrefix.length)}';
  } else if (okzPrefix.isEmpty) {
    plus = '+$lkz$phone';
  }

  if (plus == null || plus.length <= 8) {
    return null;
  }

  return plus;
}
```

**Step 4: Run tests to verify they pass**

Run: `cd phoneblock_mobile && flutter test test/phone_number_utils_test.dart`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add phoneblock_mobile/lib/fritzbox/phone_number_utils.dart phoneblock_mobile/test/phone_number_utils_test.dart
git commit -m "feat(mobile): add toInternationalForm() for Fritz!Box number normalization"
```

---

### Task 2: Add dial prefix columns to FritzBoxConfig + DB migration

**Files:**
- Modify: `phoneblock_mobile/lib/fritzbox/fritzbox_models.dart` (FritzBoxConfig class)
- Modify: `phoneblock_mobile/lib/storage.dart` (DB version 14, migration, _createDB)
- Modify: `phoneblock_mobile/lib/fritzbox/fritzbox_storage.dart` (updateConfig)

**Step 1: Add fields to FritzBoxConfig**

In `phoneblock_mobile/lib/fritzbox/fritzbox_models.dart`, add three new fields to `FritzBoxConfig`:

```dart
// New fields in FritzBoxConfig class:
final String? countryCode;    // LKZ, e.g. "49"
final String? intlPrefix;     // LKZPrefix, e.g. "00"
final String? trunkPrefix;    // OKZPrefix, e.g. "0"
```

Update the constructor, `fromMap()`, `toMap()`, and `copyWith()` to include these fields. Column names: `country_code`, `intl_prefix`, `trunk_prefix`.

**Step 2: Add DB migration in storage.dart**

Bump DB version from 13 to 14. Add migration:

```dart
if (oldVersion < 14) {
  await db.execute("ALTER TABLE fritzbox_config ADD COLUMN country_code TEXT DEFAULT '49'");
  await db.execute("ALTER TABLE fritzbox_config ADD COLUMN intl_prefix TEXT DEFAULT '00'");
  await db.execute("ALTER TABLE fritzbox_config ADD COLUMN trunk_prefix TEXT DEFAULT '0'");
}
```

Also update `_createDB` to include the new columns in the `CREATE TABLE fritzbox_config` statement.

**Step 3: Add parameters to updateConfig()**

In `phoneblock_mobile/lib/fritzbox/fritzbox_storage.dart`, add `countryCode`, `intlPrefix`, `trunkPrefix` optional parameters to `updateConfig()` and wire them through to `copyWith()` / the fallback constructor.

**Step 4: Verify build compiles**

Run: `cd phoneblock_mobile && flutter analyze`
Expected: No errors

**Step 5: Commit**

```bash
git add phoneblock_mobile/lib/fritzbox/fritzbox_models.dart phoneblock_mobile/lib/storage.dart phoneblock_mobile/lib/fritzbox/fritzbox_storage.dart
git commit -m "feat(mobile): add dial prefix columns to FritzBoxConfig (DB v14)"
```

---

### Task 3: Query Fritz!Box country/area code on connect

**Files:**
- Modify: `phoneblock_mobile/lib/fritzbox/fritzbox_service.dart:114-170` (`_connect` method)

**Step 1: Query VoIP service after authentication succeeds**

In `_connect()`, after line 160 (`fritzosVersion: deviceInfo.fritzosVersion,`) and before `return true;`, add:

```dart
// Query Fritz!Box country and area code for number normalization
String? countryCode;
String? intlPrefix;
String? trunkPrefix;
try {
  final voipService = _client!.voip();
  if (voipService != null) {
    final cc = await voipService.getVoIPCommonCountryCode();
    countryCode = cc.lkz;
    intlPrefix = cc.lkzPrefix;

    final ac = await voipService.getVoIPCommonAreaCode();
    trunkPrefix = ac.okzPrefix;
  }
} catch (e) {
  if (kDebugMode) {
    print('Fritz!Box country code query failed: $e');
  }
  // Non-fatal: defaults will be used
}

await FritzBoxStorage.instance.updateConfig(
  host: credentials.host,
  fritzosVersion: deviceInfo.fritzosVersion,
  countryCode: countryCode,
  intlPrefix: intlPrefix,
  trunkPrefix: trunkPrefix,
);
```

This replaces the existing `updateConfig` call at lines 157-160.

**Step 2: Verify build compiles**

Run: `cd phoneblock_mobile && flutter analyze`
Expected: No errors

**Step 3: Commit**

```bash
git add phoneblock_mobile/lib/fritzbox/fritzbox_service.dart
git commit -m "feat(mobile): query Fritz!Box country/area code on connect via TR-064"
```

---

### Task 4: Use stored dial prefix for call sync normalization

**Files:**
- Modify: `phoneblock_mobile/lib/fritzbox/fritzbox_service.dart:433-520` (`syncCallList` method)

**Step 1: Replace normalizePhoneNumber() with toInternationalForm()**

Add import at top of file:
```dart
import 'package:phoneblock_mobile/fritzbox/phone_number_utils.dart' as phone_utils;
```

In `syncCallList()`, after line 440 (`final config = await FritzBoxStorage.instance.getConfig();`), read the dial prefix values:

```dart
final countryCode = config?.countryCode ?? '49';
final intlPrefix = config?.intlPrefix ?? '00';
final trunkPrefix = config?.trunkPrefix ?? '0';
```

Then replace line 467:
```dart
final normalizedNumber = await normalizePhoneNumber(call.phoneNumber);
```
with:
```dart
final normalizedNumber = phone_utils.toInternationalForm(
  call.phoneNumber, countryCode, intlPrefix, trunkPrefix,
);
```

**Step 2: Verify build compiles**

Run: `cd phoneblock_mobile && flutter analyze`
Expected: No errors

**Step 3: Commit**

```bash
git add phoneblock_mobile/lib/fritzbox/fritzbox_service.dart
git commit -m "feat(mobile): use Fritz!Box dial prefix for call list normalization"
```

---

### Task 5: Final verification

**Step 1: Run all tests**

Run: `cd phoneblock_mobile && flutter test`
Expected: ALL PASS

**Step 2: Run static analysis**

Run: `cd phoneblock_mobile && flutter analyze`
Expected: No issues

**Step 3: Verify build**

Run: `cd phoneblock_mobile && flutter build apk --debug`
Expected: BUILD SUCCESSFUL
