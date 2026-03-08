# Unified Call List Display — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make every call entry in the mobile app's call list look structurally identical regardless of source (mobile vs Fritz!Box), and fix the server to always return a formatted label.

**Architecture:** Server-side one-liner fix in `NumberAnalyzer.phoneInfoFromNumber()` to always set label+location. Mobile-side refactor of `_buildCallStatusBadge()` to unify Fritz!Box and mobile status line rendering.

**Tech Stack:** Java (server), Flutter/Dart (mobile)

---

### Task 1: Fix `phoneInfoFromNumber()` to include label and location

**Files:**
- Modify: `phoneblock/src/main/java/de/haumacher/phoneblock/analysis/NumberAnalyzer.java:115-118`

**Step 1: Write the failing test**

Add to `TestNumberAnalyzer.java`:

```java
@ParameterizedTest
@CsvSource({
    "+49891234567, +49, '(DE) 0891234567'",
    "+390123456789, +49, '(IT) 0123456789'",
})
void testPhoneInfoFromNumberIncludesLabel(String input, String dialPrefix, String expectedLabel) {
    PhoneNumer number = NumberAnalyzer.analyze(input, dialPrefix);
    assertNotNull(number);
    PhoneInfo info = NumberAnalyzer.phoneInfoFromNumber(number);
    assertEquals(expectedLabel, info.getLabel());
}
```

Needs import: `import de.haumacher.phoneblock.app.api.model.PhoneInfo;`

**Step 2: Run test to verify it fails**

Run: `cd /home/bhu/git/phoneblock/.worktrees/agent-b && mvn test -pl phoneblock -Dtest=TestNumberAnalyzer#testPhoneInfoFromNumberIncludesLabel`

Expected: FAIL — `info.getLabel()` returns null

**Step 3: Write minimal implementation**

Change `phoneInfoFromNumber()` at line 115-118 from:

```java
public static PhoneInfo phoneInfoFromNumber(PhoneNumer number) {
    return PhoneInfo.create()
        .setPhone(number.getPlus());
}
```

to:

```java
public static PhoneInfo phoneInfoFromNumber(PhoneNumer number) {
    PhoneInfo result = PhoneInfo.create()
        .setPhone(number.getPlus())
        .setLabel(number.getShortcut());
    if (number.hasCity()) {
        result.setLocation(number.getCity());
    }
    return result;
}
```

**Step 4: Run test to verify it passes**

Run: `cd /home/bhu/git/phoneblock/.worktrees/agent-b && mvn test -pl phoneblock -Dtest=TestNumberAnalyzer#testPhoneInfoFromNumberIncludesLabel`

Expected: PASS

**Step 5: Run all NumberAnalyzer tests to check for regressions**

Run: `cd /home/bhu/git/phoneblock/.worktrees/agent-b && mvn test -pl phoneblock -Dtest=TestNumberAnalyzer`

Expected: All tests PASS

**Step 6: Commit**

```bash
git add phoneblock/src/main/java/de/haumacher/phoneblock/analysis/NumberAnalyzer.java phoneblock/src/test/java/de/haumacher/phoneblock/analysis/TestNumberAnalyzer.java
git commit -m "fix: include label and location in phoneInfoFromNumber()"
```

---

### Task 2: Remove redundant label/location setting in `NumServlet.lookup()`

Now that `phoneInfoFromNumber()` always sets label and location, the explicit setting in `NumServlet.lookup()` (lines 72-75) is redundant. However, `NumServlet.lookup()` calls `db.getPhoneApiInfo()` which returns a PhoneInfo already populated via `phoneInfoFromId()` → `phoneInfoFromNumber()`. The `lookup()` then overwrites label/location from the analyzed number. Since `getPhoneApiInfo()` uses `phoneInfoFromId(phoneId)` which analyzes from a phoneId string (not the original input), and `lookup()` uses the already-analyzed `PhoneNumer` object, the values should be identical. But to be safe, keep this as-is — the overwrite is harmless and acts as a safety net.

**No code change. Skip this task.**

---

### Task 3: Unify `_buildCallStatusBadge()` for consistent status line

The current `_buildCallStatusBadge()` branches on `source == CallSource.fritzbox` vs mobile. The design says:
- Icon + color from block status (same for both sources)
- Text: device name for Fritz!Box, status text for mobile

**Files:**
- Modify: `phoneblock_mobile/lib/main.dart:2660-2716` — `_buildCallStatusBadge()`

**Step 1: Rewrite `_buildCallStatusBadge()`**

Replace the method body (lines 2660-2716) with unified logic:

```dart
List<Widget> _buildCallStatusBadge(
  BuildContext context,
  ScreenedCall call,
  CallSource source,
  bool wasBlocked,
) {
  IconData icon;
  String label;
  Color color;

  if (wasBlocked) {
    icon = Icons.block;
    color = Colors.red[400]!;
    label = (source == CallSource.fritzbox && call.device != null)
        ? call.device!
        : context.l10n.blocked;
  } else if (source == CallSource.fritzbox && call.callType == FritzBoxCallType.missed) {
    icon = Icons.phone_missed;
    color = Colors.orange[400]!;
    label = context.l10n.missed;
  } else {
    icon = Icons.check_circle_outline;
    color = Colors.green[400]!;
    label = (source == CallSource.fritzbox && call.device != null)
        ? call.device!
        : context.l10n.notBlocked;
  }

  return [
    Icon(icon, size: 14, color: color),
    const SizedBox(width: 4),
    Flexible(
      child: Text(
        label,
        style: TextStyle(
          fontSize: 12,
          color: color,
          fontWeight: FontWeight.w500,
        ),
        overflow: TextOverflow.ellipsis,
      ),
    ),
  ];
}
```

Key changes:
- Block status (icon + color) is determined by `wasBlocked` / `missed` — same for both sources
- Text label: Fritz!Box shows device name when available, mobile shows status text
- `phone_callback` icon replaced with `check_circle_outline` for consistency (both sources use same "accepted" icon)

**Step 2: Run flutter analyze**

Run: `cd /home/bhu/git/phoneblock/.worktrees/agent-b/phoneblock_mobile && flutter analyze`

Expected: No new warnings (pre-existing ~27 warnings in generated files are OK)

**Step 3: Commit**

```bash
git add phoneblock_mobile/lib/main.dart
git commit -m "refactor: unify call status badge for all sources"
```

---

### Task 4: Manual verification

**Step 1: Build and run the mobile app**

Run: `cd /home/bhu/git/phoneblock/.worktrees/agent-b/phoneblock_mobile && flutter run`

**Step 2: Verify the call list**

Check that:
- All entries show the same 4-line layout
- Phone numbers use formatted label (e.g., "(DE) 01632369971")
- Fritz!Box calls show device name with block status icon/color
- Mobile calls show "Blockiert"/"Nicht blockiert" with matching icon/color
- Location line appears when available
- Timestamps and durations display correctly

**Step 3: Take a screenshot and compare with the "before" screenshot**
