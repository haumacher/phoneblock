# Blocklist Add Number & Wildcard Support — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a + button to the blocklist view for directly entering phone numbers (with optional wildcard prefix blocking stored locally).

**Architecture:** Wildcard entries stored in a new local SQLite table `wildcard_blocks`, cached in SharedPreferences for `CallChecker` access. Exact numbers use the existing `/api/rate` server flow. The blocklist screen shows two sections: wildcard rules (local) and blocked numbers (server). Call screening checks local wildcards before the server API query.

**Tech Stack:** Flutter/Dart (UI, SQLite), Android/Java (CallChecker, SharedPreferences), German ARB + auto-translation for I18N.

**Design Doc:** `docs/plans/2026-03-07-blocklist-add-and-wildcard-design.md`

---

### Task 1: Add I18N Strings

**Files:**
- Modify: `phoneblock_mobile/lib/l10n/app_de.arb`

**Step 1: Add new German strings to `app_de.arb`**

Add the following entries before the closing `}` (around line 1421). Follow the existing camelCase naming and `@key` metadata pattern:

```json
  "wildcardRulesHeader": "Wildcard-Regeln",
  "@wildcardRulesHeader": {
    "description": "Section header for wildcard blocking rules in the blocklist view"
  },
  "blockedNumbersHeader": "Gesperrte Nummern",
  "@blockedNumbersHeader": {
    "description": "Section header for individually blocked numbers in the blocklist view"
  },
  "addNumber": "Nummer hinzufügen",
  "@addNumber": {
    "description": "Title for the add-number dialog"
  },
  "phoneNumberLabel": "Telefonnummer",
  "@phoneNumberLabel": {
    "description": "Label for phone number input field"
  },
  "phoneNumberHint": "z.B. +43 oder 0043123456",
  "@phoneNumberHint": {
    "description": "Hint text for phone number input field"
  },
  "wildcardToggle": "Nummernbereich sperren",
  "@wildcardToggle": {
    "description": "Label for the wildcard toggle switch"
  },
  "wildcardHint": "Sperrt alle Nummern, die mit diesem Präfix beginnen",
  "@wildcardHint": {
    "description": "Help text shown when wildcard toggle is on"
  },
  "wildcardTooShort": "Präfix muss mindestens eine Landesvorwahl enthalten (z.B. +43)",
  "@wildcardTooShort": {
    "description": "Validation error when wildcard prefix is too short"
  },
  "wildcardInvalidFormat": "Ungültiges Nummernformat. Bitte internationale Vorwahl verwenden (z.B. +43 oder 0043).",
  "@wildcardInvalidFormat": {
    "description": "Validation error when wildcard prefix has invalid format"
  },
  "wildcardDuplicate": "Dieser Nummernbereich ist bereits gesperrt.",
  "@wildcardDuplicate": {
    "description": "Error when wildcard prefix already exists"
  },
  "wildcardAdded": "Nummernbereich gesperrt",
  "@wildcardAdded": {
    "description": "Success message when wildcard rule was added"
  },
  "wildcardRemoved": "Wildcard-Regel entfernt",
  "@wildcardRemoved": {
    "description": "Success message when wildcard rule was removed"
  },
  "confirmRemoveWildcard": "Wildcard-Regel {prefix}* wirklich entfernen?",
  "@confirmRemoveWildcard": {
    "description": "Confirmation dialog text for removing a wildcard rule",
    "placeholders": {
      "prefix": {
        "type": "String"
      }
    }
  },
  "numberAdded": "Nummer zur Blacklist hinzugefügt",
  "@numberAdded": {
    "description": "Success message when exact number was added to blocklist"
  },
  "addCommentWildcard": "Kommentar hinzufügen (Optional)",
  "@addCommentWildcard": {
    "description": "Title for the comment dialog when adding a wildcard rule"
  },
  "commentHintWildcard": "Warum sperren Sie diesen Nummernbereich?",
  "@commentHintWildcard": {
    "description": "Hint text for the comment field in wildcard dialog"
  },
  "next": "Weiter",
  "@next": {
    "description": "Button label to proceed to next step in a dialog"
  },
  "add": "Hinzufügen",
  "@add": {
    "description": "Button label to add/submit an entry"
  },
  "invalidPhoneNumber": "Ungültige Telefonnummer",
  "@invalidPhoneNumber": {
    "description": "Validation error for an invalid exact phone number"
  },
  "wildcardBlocked": "Wildcard-Regel",
  "@wildcardBlocked": {
    "description": "Rating label shown for calls blocked by a wildcard rule"
  }
```

**Step 2: Generate translations and Dart code**

Run:
```bash
cd phoneblock_mobile && ./gradlew translateArb
```
Then:
```bash
cd phoneblock_mobile && flutter gen-l10n
```

Expected: New `.arb` files for all target languages updated, and `lib/l10n/app_localizations_*.dart` files regenerated.

**Step 3: Verify build**

Run:
```bash
cd phoneblock_mobile && flutter analyze
```

Expected: No new analysis errors.

**Step 4: Commit**

```bash
git add phoneblock_mobile/lib/l10n/
git commit -m "feat: add I18N strings for blocklist add-number and wildcard support (#260)"
```

---

### Task 2: Add `wildcard_blocks` SQLite Table and CRUD Methods

**Files:**
- Modify: `phoneblock_mobile/lib/storage.dart` (DB version is currently 8, line 157)

**Step 1: Add WildcardBlock model class**

Add before the `ScreenedCallsDatabase` class (before line 136):

```dart
/// A locally stored wildcard blocking rule.
///
/// Blocks all phone numbers starting with [prefix] during call screening.
/// Stored only on the device (not synced to the server).
class WildcardBlock {
  final int? id;

  /// International format prefix, e.g. "+43", "+491234".
  final String prefix;

  /// Optional user comment explaining the rule.
  final String? comment;

  /// When this rule was created (Unix milliseconds).
  final DateTime created;

  WildcardBlock({
    this.id,
    required this.prefix,
    this.comment,
    required this.created,
  });

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'prefix': prefix,
      'comment': comment,
      'created': created.millisecondsSinceEpoch,
    };
  }

  factory WildcardBlock.fromMap(Map<String, dynamic> map) {
    return WildcardBlock(
      id: map['id'] as int?,
      prefix: map['prefix'] as String,
      comment: map['comment'] as String?,
      created: DateTime.fromMillisecondsSinceEpoch(map['created'] as int),
    );
  }
}
```

**Step 2: Bump DB version to 9 and add migration**

In `openDatabase()` call (line 157), change `version: 8` to `version: 9`.

In `_createDB()` (after the `fritzbox_config` CREATE TABLE around line 215), add:

```dart
    // Wildcard blocking rules table (local only)
    await db.execute('''
      CREATE TABLE wildcard_blocks (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        prefix TEXT NOT NULL UNIQUE,
        comment TEXT,
        created INTEGER NOT NULL
      )
    ''');
```

In `_upgradeDB()` (after the `if (oldVersion < 8)` block around line 307), add:

```dart
    if (oldVersion < 9) {
      // Add wildcard blocking rules table
      await db.execute('''
        CREATE TABLE wildcard_blocks (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          prefix TEXT NOT NULL UNIQUE,
          comment TEXT,
          created INTEGER NOT NULL
        )
      ''');
    }
```

**Step 3: Add CRUD methods**

Add these methods to the `ScreenedCallsDatabase` class (before the `close()` method around line 443):

```dart
  /// Inserts a new wildcard blocking rule.
  ///
  /// Returns the inserted [WildcardBlock] with its generated ID.
  /// Throws if a rule with the same [WildcardBlock.prefix] already exists.
  Future<WildcardBlock> insertWildcardBlock(WildcardBlock block) async {
    final db = await database;
    final id = await db.insert('wildcard_blocks', block.toMap(),
        conflictAlgorithm: ConflictAlgorithm.abort);
    return WildcardBlock(
      id: id,
      prefix: block.prefix,
      comment: block.comment,
      created: block.created,
    );
  }

  /// Returns all wildcard blocking rules sorted by prefix.
  Future<List<WildcardBlock>> getAllWildcardBlocks() async {
    final db = await database;
    final maps = await db.query('wildcard_blocks', orderBy: 'prefix ASC');
    return maps.map((m) => WildcardBlock.fromMap(m)).toList();
  }

  /// Returns all wildcard prefixes as a list of strings.
  Future<List<String>> getWildcardPrefixes() async {
    final db = await database;
    final maps = await db.query('wildcard_blocks', columns: ['prefix'], orderBy: 'prefix ASC');
    return maps.map((m) => m['prefix'] as String).toList();
  }

  /// Updates the comment for a wildcard blocking rule.
  Future<int> updateWildcardBlockComment(int id, String comment) async {
    final db = await database;
    return db.update(
      'wildcard_blocks',
      {'comment': comment},
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  /// Deletes a wildcard blocking rule by ID.
  Future<int> deleteWildcardBlock(int id) async {
    final db = await database;
    return db.delete('wildcard_blocks', where: 'id = ?', whereArgs: [id]);
  }

  /// Checks whether a wildcard prefix already exists.
  Future<bool> wildcardPrefixExists(String prefix) async {
    final db = await database;
    final result = await db.query('wildcard_blocks',
        where: 'prefix = ?', whereArgs: [prefix], limit: 1);
    return result.isNotEmpty;
  }
```

**Step 4: Verify build**

Run:
```bash
cd phoneblock_mobile && flutter analyze
```

Expected: No new analysis errors.

**Step 5: Commit**

```bash
git add phoneblock_mobile/lib/storage.dart
git commit -m "feat: add wildcard_blocks SQLite table and CRUD methods (#260)"
```

---

### Task 3: Add SharedPreferences Cache for Wildcard Prefixes

This task adds the MethodChannel bridge so wildcard prefixes can be cached in SharedPreferences for `CallChecker` access.

**Files:**
- Modify: `phoneblock_mobile/lib/main.dart` (MethodChannel calls)
- Modify: `phoneblock_mobile/android/app/src/main/java/de/haumacher/phoneblock_mobile/MainActivity.java`

**Step 1: Add `setWildcardPrefixes` and `getWildcardPrefixes` MethodChannel handlers in MainActivity.java**

In the `processMessage()` method (around line 365, before the `default:` case), add:

```java
            case "setWildcardPrefixes": {
                List<String> prefixes = call.argument("prefixes");
                setWildcardPrefixes(prefixes);
                result.success(null);
                break;
            }
            case "getWildcardPrefixes": {
                result.success(getWildcardPrefixes());
                break;
            }
```

Add these methods to the `MainActivity` class (near the other getter/setter methods):

```java
    private void setWildcardPrefixes(List<String> prefixes) {
        SharedPreferences prefs = getPreferences(this);
        JSONArray jsonArray = new JSONArray(prefixes);
        prefs.edit().putString("wildcard_prefixes", jsonArray.toString()).apply();
    }

    private List<String> getWildcardPrefixes() {
        SharedPreferences prefs = getPreferences(this);
        String json = prefs.getString("wildcard_prefixes", "[]");
        try {
            JSONArray jsonArray = new JSONArray(json);
            List<String> result = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                result.add(jsonArray.getString(i));
            }
            return result;
        } catch (JSONException e) {
            return new ArrayList<>();
        }
    }
```

Add the necessary imports at the top of `MainActivity.java`:

```java
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
```

(Check if these are already imported — `JSONArray` and `JSONException` may already be there since `JSONObject` is imported in `CallChecker.java`. `ArrayList` and `List` are likely needed.)

**Step 2: Add Dart-side helper to sync wildcard prefixes to SharedPreferences**

In `main.dart`, add a top-level function near the other MethodChannel utility functions (near `getAuthToken()`, around line 100-150):

```dart
/// Syncs wildcard prefixes from SQLite to SharedPreferences for CallChecker access.
Future<void> syncWildcardPrefixesToNative() async {
  final prefixes = await ScreenedCallsDatabase.instance.getWildcardPrefixes();
  const channel = MethodChannel('de.haumacher.phoneblock_mobile/call_checker');
  await channel.invokeMethod('setWildcardPrefixes', {'prefixes': prefixes});
}
```

**Step 3: Verify build**

Run:
```bash
cd phoneblock_mobile && flutter analyze
```

Expected: No new analysis errors.

**Step 4: Commit**

```bash
git add phoneblock_mobile/lib/main.dart phoneblock_mobile/android/app/src/main/java/de/haumacher/phoneblock_mobile/MainActivity.java
git commit -m "feat: add MethodChannel bridge for wildcard prefix caching (#260)"
```

---

### Task 4: Add Wildcard Prefix Matching to CallChecker

**Files:**
- Modify: `phoneblock_mobile/android/app/src/main/java/de/haumacher/phoneblock_mobile/CallChecker.java`

**Step 1: Add wildcard prefix check before server query**

In `onScreenCall()`, after the number normalization check (line 93, after `return;` for null number), and before the `AtomicBoolean canceled` line (line 95), add:

```java
        // Check local wildcard blocking rules before server query
        String wildcardPrefixesJson = prefs.getString("wildcard_prefixes", "[]");
        try {
            org.json.JSONArray wildcardPrefixes = new org.json.JSONArray(wildcardPrefixesJson);
            for (int i = 0; i < wildcardPrefixes.length(); i++) {
                String prefix = wildcardPrefixes.getString(i);
                if (number.startsWith(prefix)) {
                    Log.d(CallChecker.class.getName(), "onScreenCall: Blocking call by wildcard rule: " + prefix + "* matches " + number);
                    respondToCall(callDetails, new CallResponse.Builder()
                        .setDisallowCall(true)
                        .setRejectCall(true)
                        .setSkipCallLog(true)
                        .setSkipNotification(true)
                        .build());
                    MainActivity.reportScreenedCall(CallChecker.this, rawNumber, true, 0, 0, "WILDCARD", null, null);
                    return;
                }
            }
        } catch (org.json.JSONException e) {
            Log.w(CallChecker.class.getName(), "Failed to parse wildcard prefixes", e);
        }
```

This goes right after line 93 (after the `return;` for null number normalization) and before line 95 (`AtomicBoolean canceled`).

**Step 2: Verify build**

Run:
```bash
cd phoneblock_mobile && flutter build apk --debug 2>&1 | tail -5
```

Expected: Build succeeds (or at least Java compilation succeeds).

**Step 3: Commit**

```bash
git add phoneblock_mobile/android/app/src/main/java/de/haumacher/phoneblock_mobile/CallChecker.java
git commit -m "feat: add wildcard prefix matching to call screener (#260)"
```

---

### Task 5: Refactor Blocklist Screen for Two Sections and FAB

This is the main UI task. The `PersonalizedNumberListScreen` needs to be refactored to show two sections and add the FAB.

**Files:**
- Modify: `phoneblock_mobile/lib/main.dart` (lines 3713-4004, `PersonalizedNumberListScreen`)

**Step 1: Add wildcard state and loading to `_PersonalizedNumberListScreenState`**

Add to the state fields (after line 3730, `String? _errorMessage;`):

```dart
  List<WildcardBlock> _wildcardBlocks = [];
```

Modify `_loadNumbers()` to also load wildcard blocks (only for blacklist). After the existing `setState` that sets `_numbers` and `_isLoading = false` (around line 3753-3755), load wildcards:

Replace the entire `_loadNumbers()` method body with one that loads both:

```dart
  Future<void> _loadNumbers() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final numberList = _isBlacklist
          ? await fetchBlacklist(widget.authToken)
          : await fetchWhitelist(widget.authToken);

      List<WildcardBlock> wildcards = [];
      if (_isBlacklist) {
        wildcards = await ScreenedCallsDatabase.instance.getAllWildcardBlocks();
      }

      if (numberList != null) {
        setState(() {
          _numbers = numberList.numbers;
          _wildcardBlocks = wildcards;
          _isLoading = false;
        });
      } else {
        setState(() {
          _errorMessage = context.l10n.errorLoadingList;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (kDebugMode) {
        print('Error loading ${_isBlacklist ? 'blacklist' : 'whitelist'}: $e');
      }
      setState(() {
        _errorMessage = context.l10n.errorLoadingList;
        _isLoading = false;
      });
    }
  }
```

**Step 2: Add the FAB to the Scaffold (blacklist only)**

In the `build()` method, add a `floatingActionButton` to the `Scaffold` (around line 3843). Change:

```dart
    return Scaffold(
      appBar: AppBar(
        title: Text(title),
      ),
```

To:

```dart
    return Scaffold(
      appBar: AppBar(
        title: Text(title),
      ),
      floatingActionButton: _isBlacklist
          ? FloatingActionButton(
              onPressed: () => _showAddNumberDialog(context),
              child: const Icon(Icons.add),
            )
          : null,
```

**Step 3: Replace the ListView with a two-section list**

Replace the `RefreshIndicator` + `ListView.builder` block (the section starting at line 3891 with `RefreshIndicator(`) through to the closing parenthesis of the ternary. The new body should handle the case where both lists are empty, or show the two-section layout:

```dart
              : (_numbers.isEmpty && _wildcardBlocks.isEmpty)
                  ? Center(/* existing empty state widget */)
                  : RefreshIndicator(
                      onRefresh: _loadNumbers,
                      child: ListView(
                        children: [
                          // Wildcard rules section (blacklist only)
                          if (_isBlacklist && _wildcardBlocks.isNotEmpty) ...[
                            Padding(
                              padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                              child: Text(
                                context.l10n.wildcardRulesHeader,
                                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                                  color: Colors.grey[600],
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ),
                            ..._wildcardBlocks.map((block) => _buildWildcardTile(context, block)),
                            const Divider(),
                          ],
                          // Blocked/whitelisted numbers section
                          if (_numbers.isNotEmpty) ...[
                            if (_isBlacklist && _wildcardBlocks.isNotEmpty)
                              Padding(
                                padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
                                child: Text(
                                  context.l10n.blockedNumbersHeader,
                                  style: Theme.of(context).textTheme.titleSmall?.copyWith(
                                    color: Colors.grey[600],
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              ),
                            ..._numbers.map((pn) => _buildNumberTile(context, pn, confirmRemoveMessage, defaultIcon)),
                          ],
                        ],
                      ),
                    ),
```

**Step 4: Extract existing number tile into `_buildNumberTile` method**

Extract the existing `Dismissible` + `ListTile` from the `ListView.builder`'s `itemBuilder` into a method:

```dart
  Widget _buildNumberTile(
    BuildContext context,
    api.PersonalizedNumber personalizedNumber,
    String Function(String) confirmRemoveMessage,
    Icon defaultIcon,
  ) {
    final phone = personalizedNumber.phone;
    final displayPhone = personalizedNumber.label ?? phone;
    return Dismissible(
      key: Key(phone),
      direction: DismissDirection.endToStart,
      background: Container(
        color: Colors.red,
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.only(right: 20),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            Text(
              context.l10n.delete,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 16,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(width: 12),
            const Icon(Icons.delete, color: Colors.white, size: 32),
          ],
        ),
      ),
      confirmDismiss: (direction) async {
        final confirmed = await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            title: Text(context.l10n.confirmRemoval),
            content: Text(confirmRemoveMessage(displayPhone)),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(context).pop(false),
                child: Text(context.l10n.cancel),
              ),
              TextButton(
                onPressed: () => Navigator.of(context).pop(true),
                child: Text(context.l10n.remove),
              ),
            ],
          ),
        );

        if (confirmed != true) return false;

        final success = _isBlacklist
            ? await removeFromBlacklist(personalizedNumber.phone, widget.authToken)
            : await removeFromWhitelist(personalizedNumber.phone, widget.authToken);

        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                success
                    ? context.l10n.numberRemovedFromList
                    : context.l10n.errorRemovingNumber,
              ),
              backgroundColor: success ? null : Colors.red,
              duration: const Duration(seconds: 2),
            ),
          );
        }

        return success;
      },
      onDismissed: (direction) {
        setState(() {
          _numbers.remove(personalizedNumber);
        });
      },
      child: ListTile(
        leading: personalizedNumber.rating != null
            ? buildRatingAvatar(_convertApiRating(personalizedNumber.rating!))
            : defaultIcon,
        title: Text(displayPhone),
        subtitle: personalizedNumber.comment != null && personalizedNumber.comment!.isNotEmpty
            ? Text(
                personalizedNumber.comment!,
                style: TextStyle(color: Colors.grey[600], fontSize: 14),
              )
            : null,
        trailing: IconButton(
          icon: const Icon(Icons.edit_outlined),
          onPressed: () => _editComment(personalizedNumber),
        ),
      ),
    );
  }
```

**Step 5: Add `_buildWildcardTile` method**

```dart
  Widget _buildWildcardTile(BuildContext context, WildcardBlock block) {
    final displayPrefix = '${block.prefix}*';
    return Dismissible(
      key: Key('wildcard_${block.id}'),
      direction: DismissDirection.endToStart,
      background: Container(
        color: Colors.red,
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.only(right: 20),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            Text(
              context.l10n.delete,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 16,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(width: 12),
            const Icon(Icons.delete, color: Colors.white, size: 32),
          ],
        ),
      ),
      confirmDismiss: (direction) async {
        final confirmed = await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            title: Text(context.l10n.confirmRemoval),
            content: Text(context.l10n.confirmRemoveWildcard(block.prefix)),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(context).pop(false),
                child: Text(context.l10n.cancel),
              ),
              TextButton(
                onPressed: () => Navigator.of(context).pop(true),
                child: Text(context.l10n.remove),
              ),
            ],
          ),
        );

        if (confirmed != true) return false;

        await ScreenedCallsDatabase.instance.deleteWildcardBlock(block.id!);
        await syncWildcardPrefixesToNative();

        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(context.l10n.wildcardRemoved),
              duration: const Duration(seconds: 2),
            ),
          );
        }

        return true;
      },
      onDismissed: (direction) {
        setState(() {
          _wildcardBlocks.remove(block);
        });
      },
      child: ListTile(
        leading: const Icon(Icons.filter_alt, color: Colors.orange),
        title: Text(displayPrefix),
        subtitle: block.comment != null && block.comment!.isNotEmpty
            ? Text(
                block.comment!,
                style: TextStyle(color: Colors.grey[600], fontSize: 14),
              )
            : null,
        trailing: IconButton(
          icon: const Icon(Icons.edit_outlined),
          onPressed: () => _editWildcardComment(block),
        ),
      ),
    );
  }
```

**Step 6: Add `_editWildcardComment` method**

```dart
  Future<void> _editWildcardComment(WildcardBlock block) async {
    final scaffold = ScaffoldMessenger.of(context);
    final localizations = context.l10n;
    final textController = TextEditingController(text: block.comment ?? '');

    final newComment = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(localizations.editComment),
        content: TextField(
          controller: textController,
          decoration: InputDecoration(
            labelText: localizations.commentLabel,
            hintText: localizations.commentHint,
          ),
          maxLines: 3,
          autofocus: true,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: Text(localizations.cancel),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(textController.text),
            child: Text(localizations.save),
          ),
        ],
      ),
    );

    if (newComment != null) {
      await ScreenedCallsDatabase.instance.updateWildcardBlockComment(block.id!, newComment);
      setState(() {
        final index = _wildcardBlocks.indexOf(block);
        if (index >= 0) {
          _wildcardBlocks[index] = WildcardBlock(
            id: block.id,
            prefix: block.prefix,
            comment: newComment,
            created: block.created,
          );
        }
      });
      scaffold.showSnackBar(
        SnackBar(
          content: Text(localizations.commentUpdated),
          duration: const Duration(seconds: 2),
        ),
      );
    }
  }
```

**Step 7: Verify build**

Run:
```bash
cd phoneblock_mobile && flutter analyze
```

Expected: No new analysis errors (the `_showAddNumberDialog` method referenced in the FAB is not yet implemented — that's Task 6. You may temporarily stub it with an empty method body to pass analysis).

**Step 8: Commit**

```bash
git add phoneblock_mobile/lib/main.dart
git commit -m "feat: refactor blocklist screen with two sections and FAB (#260)"
```

---

### Task 6: Implement the Add Number Dialog Flow

**Files:**
- Modify: `phoneblock_mobile/lib/main.dart` (add methods to `_PersonalizedNumberListScreenState`)

**Step 1: Add the `_showAddNumberDialog` method**

This is the entry point called by the FAB. It shows a dialog to enter a phone number with a wildcard toggle.

```dart
  /// Shows a dialog to enter a phone number for blocking.
  ///
  /// If the wildcard toggle is on, the number is treated as a prefix
  /// and stored locally. Otherwise, the full 3-step rating flow is used.
  Future<void> _showAddNumberDialog(BuildContext context) async {
    final phoneController = TextEditingController();
    bool isWildcard = false;
    String? errorText;

    final result = await showDialog<({String phone, bool wildcard})>(
      context: context,
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            return AlertDialog(
              title: Text(context.l10n.addNumber),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: phoneController,
                          keyboardType: TextInputType.phone,
                          decoration: InputDecoration(
                            labelText: context.l10n.phoneNumberLabel,
                            hintText: context.l10n.phoneNumberHint,
                            errorText: errorText,
                            border: const OutlineInputBorder(),
                            suffixText: isWildcard ? '*' : null,
                            suffixStyle: const TextStyle(
                              fontSize: 20,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          onChanged: (_) {
                            if (errorText != null) {
                              setDialogState(() => errorText = null);
                            }
                          },
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  SwitchListTile(
                    title: Text(context.l10n.wildcardToggle),
                    subtitle: isWildcard ? Text(context.l10n.wildcardHint) : null,
                    value: isWildcard,
                    onChanged: (value) {
                      setDialogState(() => isWildcard = value);
                    },
                    contentPadding: EdgeInsets.zero,
                  ),
                ],
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: Text(context.l10n.cancel),
                ),
                TextButton(
                  onPressed: () {
                    final phone = phoneController.text.trim();
                    if (phone.isEmpty) {
                      setDialogState(() => errorText = context.l10n.invalidPhoneNumber);
                      return;
                    }
                    Navigator.of(context).pop((phone: phone, wildcard: isWildcard));
                  },
                  child: Text(context.l10n.next),
                ),
              ],
            );
          },
        );
      },
    );

    if (result == null) return;

    if (!context.mounted) return;

    if (result.wildcard) {
      await _addWildcardNumber(context, result.phone);
    } else {
      await _addExactNumber(context, result.phone);
    }
  }
```

**Step 2: Add `_addWildcardNumber` method (2-step: validate + comment)**

```dart
  /// Validates a wildcard prefix and stores it locally.
  Future<void> _addWildcardNumber(BuildContext context, String phoneInput) async {
    // Normalize the input to international format prefix
    final prefix = _normalizeWildcardPrefix(phoneInput);

    if (prefix == null || prefix.length < 3) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.wildcardTooShort),
            backgroundColor: Colors.red,
          ),
        );
      }
      return;
    }

    // Check for duplicate
    final exists = await ScreenedCallsDatabase.instance.wildcardPrefixExists(prefix);
    if (exists) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.wildcardDuplicate),
            backgroundColor: Colors.red,
          ),
        );
      }
      return;
    }

    // Step 2: Comment dialog
    if (!context.mounted) return;
    final comment = await _showWildcardCommentDialog(context);
    if (comment == null) return; // cancelled

    // Store locally
    final block = WildcardBlock(
      prefix: prefix,
      comment: comment.isEmpty ? null : comment,
      created: DateTime.now(),
    );

    await ScreenedCallsDatabase.instance.insertWildcardBlock(block);
    await syncWildcardPrefixesToNative();
    await _loadNumbers();

    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(context.l10n.wildcardAdded),
          backgroundColor: Colors.green,
        ),
      );
    }
  }

  /// Normalizes user input to an international prefix format.
  ///
  /// Accepts formats like "0043", "+43", "0049123" and returns "+43", "+49123".
  /// Returns null if the input cannot be normalized.
  String? _normalizeWildcardPrefix(String input) {
    var cleaned = input.replaceAll(RegExp(r'[\s\-\(\)\/]'), '');

    // Handle 00XX format → +XX
    if (cleaned.startsWith('00') && cleaned.length >= 4) {
      cleaned = '+${cleaned.substring(2)}';
    }

    // Must start with +
    if (!cleaned.startsWith('+')) {
      return null;
    }

    // Must contain only digits after +
    final afterPlus = cleaned.substring(1);
    if (afterPlus.isEmpty || !RegExp(r'^[0-9]+$').hasMatch(afterPlus)) {
      return null;
    }

    return cleaned;
  }

  /// Shows a comment dialog for wildcard rules.
  Future<String?> _showWildcardCommentDialog(BuildContext context) async {
    final controller = TextEditingController();

    return showDialog<String>(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: Text(context.l10n.addCommentWildcard),
          content: TextField(
            controller: controller,
            decoration: InputDecoration(
              hintText: context.l10n.commentHintWildcard,
              border: const OutlineInputBorder(),
            ),
            maxLines: 3,
            maxLength: 500,
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(null),
              child: Text(context.l10n.cancel),
            ),
            TextButton(
              onPressed: () => Navigator.of(context).pop(controller.text),
              child: Text(context.l10n.add),
            ),
          ],
        );
      },
    );
  }
```

**Step 3: Add `_addExactNumber` method (3-step: rating + comment + API)**

This reuses the existing `_showRatingDialog` and `_showCommentDialog` patterns. Note: these are currently instance methods on `_MainScreenState`, not on `_PersonalizedNumberListScreenState`. We need standalone versions or top-level functions. The simplest approach is to duplicate the dialog logic (it's small) or extract into shared functions.

Since `_showRatingDialog` and `_showCommentDialog` are defined on `_MainScreenState` (lines 2035-2103), we'll create similar methods directly on `_PersonalizedNumberListScreenState`:

```dart
  /// Adds an exact phone number via the server rating API.
  Future<void> _addExactNumber(BuildContext context, String phone) async {
    // Step 2: Select spam category
    final rating = await _showBlocklistRatingDialog(context);
    if (rating == null) return;

    // Step 3: Optional comment
    if (!context.mounted) return;
    final comment = await _showBlocklistCommentDialog(context);
    if (comment == null) return;

    String? token = await getAuthToken();
    if (token == null) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.notLoggedIn),
            backgroundColor: Colors.red,
          ),
        );
      }
      return;
    }

    try {
      // Create RateRequest
      final rateRequest = api.RateRequest(
        phone: phone,
        rating: _convertStateRatingToApi(rating),
        comment: comment,
      );

      final buffer = StringBuffer();
      final jsonWriter = jsonStringWriter(buffer);
      rateRequest.writeContent(jsonWriter);
      final jsonBody = buffer.toString();

      final response = await http.post(
        Uri.parse('$pbBaseUrl/api/rate'),
        headers: {
          'Authorization': 'Bearer $token',
          'Content-Type': 'application/json; charset=UTF-8',
        },
        body: jsonBody,
      );

      if (context.mounted) {
        if (response.statusCode == 200) {
          await _loadNumbers();
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(context.l10n.numberAdded),
              backgroundColor: Colors.green,
            ),
          );
        } else {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(context.l10n.reportError(response.statusCode.toString())),
              backgroundColor: Colors.red,
            ),
          );
        }
      }
    } catch (e) {
      if (kDebugMode) {
        print('Error adding number: $e');
      }
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.reportError(e.toString())),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  /// Shows rating selection dialog (same pattern as MainScreen's).
  Future<Rating?> _showBlocklistRatingDialog(BuildContext context) async {
    return showDialog<Rating>(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: Text(context.l10n.selectSpamCategory),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: Rating.values
                  .where((r) => r != Rating.aLEGITIMATE && r != Rating.uNKNOWN)
                  .map((rating) {
                    final color = bgColor(rating);
                    return ListTile(
                      leading: Icon(ratingIcon(rating), color: color),
                      title: Text(labelText(context, rating),
                          style: TextStyle(color: color, fontWeight: FontWeight.w500)),
                      tileColor: color.withValues(alpha: 0.1),
                      onTap: () => Navigator.of(context).pop(rating),
                    );
                  })
                  .toList(),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: Text(context.l10n.cancel),
            ),
          ],
        );
      },
    );
  }

  /// Shows comment input dialog (same pattern as MainScreen's).
  Future<String?> _showBlocklistCommentDialog(BuildContext context) async {
    final controller = TextEditingController();
    return showDialog<String>(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: Text(context.l10n.addCommentSpam),
          content: TextField(
            controller: controller,
            decoration: InputDecoration(
              hintText: context.l10n.commentHintSpam,
              border: const OutlineInputBorder(),
            ),
            maxLines: 3,
            maxLength: 500,
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(null),
              child: Text(context.l10n.cancel),
            ),
            TextButton(
              onPressed: () => Navigator.of(context).pop(controller.text),
              child: Text(context.l10n.report),
            ),
          ],
        );
      },
    );
  }

  /// Converts state.Rating to api.Rating.
  api.Rating _convertStateRatingToApi(Rating rating) {
    switch (rating) {
      case Rating.aLEGITIMATE: return api.Rating.aLegitimate;
      case Rating.uNKNOWN: return api.Rating.bMissed;
      case Rating.pING: return api.Rating.cPing;
      case Rating.pOLL: return api.Rating.dPoll;
      case Rating.aDVERTISING: return api.Rating.eAdvertising;
      case Rating.gAMBLE: return api.Rating.fGamble;
      case Rating.fRAUD: return api.Rating.gFraud;
    }
  }
```

**Step 4: Verify build and analyze**

Run:
```bash
cd phoneblock_mobile && flutter analyze
```

Expected: No analysis errors.

**Step 5: Commit**

```bash
git add phoneblock_mobile/lib/main.dart
git commit -m "feat: implement add-number dialog with wildcard and exact number flows (#260)"
```

---

### Task 7: Handle WILDCARD Rating in Call List Display

When `CallChecker` blocks a call with `rating: "WILDCARD"`, the call list should display it meaningfully.

**Files:**
- Modify: `phoneblock_mobile/lib/main.dart` (call list display, around the `_buildCallInfo` or rating display logic)

**Step 1: Handle WILDCARD rating string in call display**

Find where the rating string from native is converted to a `Rating` enum (this happens in the `_handleScreenedCall` method or similar). The `rating` string `"WILDCARD"` won't map to any existing `Rating` enum value, so it should be handled gracefully.

Search for where `rating` from screened calls is parsed. In the `_handleScreenedCall` callback (invoked when `CallChecker` sends `onCallScreened`), around line 551-590, the rating string is converted. The code should handle `"WILDCARD"` by storing it as `null` rating (the `Rating.values.firstWhere` with `orElse` already handles unknown strings gracefully).

In the call info display (around line 2574 where `votesWildcard` is shown), add a check: if the call has `rating == null` and `wasBlocked == true` and `votes == 0` and `votesWildcard == 0`, it was likely a wildcard block. Show the `wildcardBlocked` l10n string.

This is a minor enhancement — the exact implementation depends on the existing rating display logic. Look for where `labelText(context, rating)` is called for the call list items and add a fallback for wildcard-blocked calls.

**Step 2: Verify build**

Run:
```bash
cd phoneblock_mobile && flutter analyze
```

**Step 3: Commit**

```bash
git add phoneblock_mobile/lib/main.dart
git commit -m "feat: display wildcard-blocked calls in call list (#260)"
```

---

### Task 8: Final Integration Testing and Cleanup

**Step 1: Run full analysis**

```bash
cd phoneblock_mobile && flutter analyze
```

Expected: Zero errors, zero warnings (or only pre-existing warnings).

**Step 2: Run tests**

```bash
cd phoneblock_mobile && flutter test
```

Expected: All tests pass.

**Step 3: Test manually on emulator/device**

1. Open the app, navigate to Blacklist from the drawer
2. Tap the + FAB
3. Enter a phone number WITHOUT wildcard toggle → verify 3-step flow (number → rating → comment → submitted to server)
4. Enter a prefix WITH wildcard toggle → verify 2-step flow (number → comment → stored locally)
5. Verify the blocklist shows both sections: "Wildcard Rules" at top, "Blocked Numbers" below
6. Swipe to delete a wildcard entry → verify it's removed
7. Edit a wildcard comment → verify it updates
8. Test call screening: make a test call from a number matching a wildcard prefix → verify it's blocked

**Step 4: Commit any final fixes**

```bash
git add -A
git commit -m "chore: final cleanup for blocklist add-number and wildcard support (#260)"
```
