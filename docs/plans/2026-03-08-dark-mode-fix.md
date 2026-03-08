# Dark Mode Fix for Fritz!Box Integration Screens

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix dark-mode display problems in Fritz!Box integration screens (Issue #267) by replacing hardcoded light-theme colors with theme-aware colors.

**Architecture:** Replace all hardcoded `Colors.black87`, `Colors.grey[600]`, `Colors.grey[400]`, `Colors.green.shade50`, `Colors.red.shade50`, `Colors.orange[900]` etc. with theme-aware equivalents from `Theme.of(context).colorScheme`. Semantic status colors (green for success, red for error, orange for warning) stay as-is since they work in both themes — only text/icon/background colors that assume a light surface need fixing.

**Tech Stack:** Flutter, Dart, Material 3 ColorScheme

---

### Task 1: Fix `fritzbox_wizard.dart` — `_buildBlocklistOption`

**Files:**
- Modify: `phoneblock_mobile/lib/fritzbox/screens/fritzbox_wizard.dart:572-639`

**Step 1: Fix unselected title color (line 613-615)**

Replace:
```dart
                        color: selected
                            ? Theme.of(context).primaryColor
                            : Colors.black87,
```
With:
```dart
                        color: selected
                            ? Theme.of(context).colorScheme.primary
                            : null,
```

Rationale: `Colors.black87` is invisible on dark backgrounds. Using `null` lets the default text style provide the right color for both themes. Also switch from `primaryColor` to `colorScheme.primary` for Material 3 consistency.

**Step 2: Fix unselected icon color (line 598-600)**

Replace:
```dart
                color: selected
                    ? Theme.of(context).primaryColor
                    : Colors.grey[600],
```
With:
```dart
                color: selected
                    ? Theme.of(context).colorScheme.primary
                    : Theme.of(context).colorScheme.onSurfaceVariant,
```

**Step 3: Fix subtitle color (line 621-623)**

Replace:
```dart
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.grey[600],
                      ),
```
With:
```dart
                      style: TextStyle(
                        fontSize: 12,
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
```

**Step 4: Fix selected border/checkmark to use colorScheme.primary (lines 584, 599, 614, 632)**

Replace all `Theme.of(context).primaryColor` in `_buildBlocklistOption` with `Theme.of(context).colorScheme.primary`.

There are 4 occurrences in `_buildBlocklistOption`:
- Line 584 (border color)
- Line 599 (icon color) — already fixed in step 2
- Line 614 (title color) — already fixed in step 1
- Line 632 (checkmark color)

---

### Task 2: Fix `fritzbox_wizard.dart` — detection and login steps

**Files:**
- Modify: `phoneblock_mobile/lib/fritzbox/screens/fritzbox_wizard.dart:298-504`

**Step 1: Fix searching text color (line 317)**

Replace:
```dart
            style: const TextStyle(color: Colors.grey),
```
With:
```dart
            style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant),
```

**Step 2: Fix "not found" description text color (line 349)**

Replace:
```dart
            style: const TextStyle(color: Colors.grey),
```
With:
```dart
            style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant),
```

**Step 3: Fix login description text color (line 404)**

Replace:
```dart
          style: const TextStyle(color: Colors.grey),
```
With:
```dart
          style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant),
```

**Step 4: Fix credentials note color (line 473-475)**

Replace:
```dart
          style: TextStyle(
            fontSize: 12,
            color: Colors.grey[600],
          ),
```
With:
```dart
          style: TextStyle(
            fontSize: 12,
            color: Theme.of(context).colorScheme.onSurfaceVariant,
          ),
```

**Step 5: Fix blocklist description text color (line 513)**

Replace:
```dart
          style: const TextStyle(color: Colors.grey),
```
With:
```dart
          style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant),
```

---

### Task 3: Fix `fritzbox_wizard.dart` — version warning

**Files:**
- Modify: `phoneblock_mobile/lib/fritzbox/screens/fritzbox_wizard.dart:641-662`

**Step 1: Fix warning text color (line 656)**

Replace:
```dart
            child: Text(
              l10n.fritzboxVersionTooOldForCardDav,
              style: TextStyle(color: Colors.orange[900]),
            ),
```
With:
```dart
            child: Text(
              l10n.fritzboxVersionTooOldForCardDav,
              style: TextStyle(color: Colors.orange[700]),
            ),
```

Rationale: `Colors.orange[900]` is very dark brown — unreadable on dark backgrounds. `Colors.orange[700]` is readable on both light and dark surfaces.

**Step 2: Commit wizard fixes**

```bash
cd phoneblock_mobile
git add lib/fritzbox/screens/fritzbox_wizard.dart
git commit -m "fix: dark-mode colors in Fritz!Box wizard (#267)

Replace hardcoded Colors.black87, Colors.grey[600] etc. with
theme-aware colorScheme equivalents so the blocklist selection
cards, text, and icons are visible in dark mode."
```

---

### Task 4: Fix `fritzbox_settings.dart` — hardcoded greys

**Files:**
- Modify: `phoneblock_mobile/lib/fritzbox/screens/fritzbox_settings.dart`

**Step 1: Fix "not configured" icon color (line 354)**

Replace:
```dart
              color: Colors.grey[400],
```
With:
```dart
              color: Theme.of(context).colorScheme.onSurfaceVariant,
```

**Step 2: Fix "not configured" heading color (line 359-361)**

Replace:
```dart
              style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                    color: Colors.grey[600],
                  ),
```
With:
```dart
              style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
```

**Step 3: Fix "not configured" description color (line 367-369)**

Replace:
```dart
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: Colors.grey[600],
                  ),
```
With:
```dart
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
```

**Step 4: Fix info row label color (line 454)**

Replace:
```dart
          Text(label, style: const TextStyle(color: Colors.grey)),
```
With:
```dart
          Text(label, style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant)),
```

Note: `_buildInfoRow` doesn't have access to `context` via parameter — but it's an instance method on a `State` subclass, so `context` is available via the inherited getter.

**Step 5: Commit settings fixes**

```bash
cd phoneblock_mobile
git add lib/fritzbox/screens/fritzbox_settings.dart
git commit -m "fix: dark-mode colors in Fritz!Box settings (#267)

Replace hardcoded Colors.grey shades with theme-aware
colorScheme.onSurfaceVariant for proper dark-mode contrast."
```

---

### Task 5: Fix `fritzbox_answerbot_setup.dart` — result card

**Files:**
- Modify: `phoneblock_mobile/lib/fritzbox/screens/fritzbox_answerbot_setup.dart:245-273`

**Step 1: Fix result card background and text colors**

Replace the entire `_buildResultCard` method body (lines 246-272):

```dart
  Widget _buildResultCard(AppLocalizations l10n) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    final backgroundColor = _succeeded
        ? (isDark ? Colors.green.withValues(alpha: 0.2) : Colors.green.shade50)
        : (isDark ? Colors.red.withValues(alpha: 0.2) : Colors.red.shade50);
    final textColor = _succeeded
        ? (isDark ? Colors.green.shade300 : Colors.green.shade900)
        : (isDark ? Colors.red.shade300 : Colors.red.shade900);

    return Card(
      color: backgroundColor,
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Row(
          children: [
            Icon(
              _succeeded ? Icons.check_circle : Icons.error,
              color: _succeeded ? Colors.green : Colors.red,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                _succeeded
                    ? l10n.fritzboxAnswerbotSetupSuccess
                    : l10n.fritzboxAnswerbotSetupFailed,
                style: TextStyle(color: textColor),
              ),
            ),
          ],
        ),
      ),
    );
  }
```

Rationale: `Colors.green.shade50` / `Colors.red.shade50` are near-white backgrounds that look washed out or invisible in dark mode. Use semi-transparent green/red in dark mode for visible but subtle background. Text uses `.shade300` (light enough to read on dark) instead of `.shade900` (near-black).

**Step 2: Commit answerbot setup fixes**

```bash
cd phoneblock_mobile
git add lib/fritzbox/screens/fritzbox_answerbot_setup.dart
git commit -m "fix: dark-mode colors in answerbot setup screen (#267)

Use brightness-aware background and text colors for the
success/failure result card."
```

---

### Task 6: Verify with flutter analyze

**Step 1: Run analyze**

```bash
cd phoneblock_mobile
flutter analyze
```

Expected: No new warnings or errors introduced. Watch for:
- Removal of `const` from `TextStyle` constructors (since `Theme.of(context)` is not const)
- Any unused imports

**Step 2: Fix any issues found**

If `const` removal causes warnings or there are other analyzer issues, fix them.

**Step 3: Final commit if needed**

Only if step 2 required changes.
