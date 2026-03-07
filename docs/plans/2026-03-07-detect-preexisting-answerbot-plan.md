# Detect Pre-Existing Answerbot Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Automatically detect and adopt a pre-existing PhoneBlock answerbot when a Fritz!Box is connected.

**Architecture:** New `syncAnswerbotState()` method in `FritzBoxService` lists VoIP clients via TR-064, fetches bots from PhoneBlock API, matches by SIP username, and auto-adopts. Called during Fritz!Box settings screen load.

**Tech Stack:** Flutter/Dart, fritz_tr064 (TR-064), phoneblock_shared (API client)

---

### Task 1: Add `syncAnswerbotState()` to FritzBoxService

**Files:**
- Modify: `phoneblock_mobile/lib/fritzbox/fritzbox_service.dart` (insert before `removeAnswerBot()` at line ~1298)

**Step 1: Add the method**

Insert this method before `removeAnswerBot()` (before line 1298):

```dart
/// Detects a pre-existing PhoneBlock answerbot on the connected Fritz!Box.
///
/// Lists VoIP clients via TR-064 and matches their usernames against
/// bots from the PhoneBlock API. If a match is found and no answerbot
/// is currently configured locally, auto-adopts the bot.
Future<void> syncAnswerbotState() async {
  final config = await FritzBoxStorage.instance.getConfig();
  if (!isConnected || _client == null) return;
  if (config?.answerbotEnabled ?? false) return;

  try {
    // Step 1: List VoIP clients from Fritz!Box via TR-064.
    final voipClients = await _withReconnect(() async {
      final voipService = _client!.voip();
      if (voipService == null) return <({String username, String? internalNumber})>[];

      final numberOfClients = await voipService.getNumberOfClients();
      final clients = <({String username, String? internalNumber})>[];
      for (int i = 0; i < numberOfClients; i++) {
        final client = await voipService.getClient3(i);
        clients.add((username: client.clientUsername, internalNumber: client.internalNumber));
      }
      return clients;
    });

    if (voipClients == null || voipClients.isEmpty) return;

    // Step 2: Fetch bots from PhoneBlock API.
    final headers = await apiHeaders();
    final response = await http.get(
      Uri.parse('$basePath/ab/list'),
      headers: headers,
    );
    if (response.statusCode != 200) return;

    final listResponse = ListAnswerbotResponse.read(
      JsonReader.fromString(response.body),
    );

    // Step 3: Match by username and auto-adopt.
    for (final bot in listResponse.bots) {
      for (final voipClient in voipClients) {
        if (bot.userName == voipClient.username) {
          await FritzBoxStorage.instance.updateConfig(
            answerbotEnabled: true,
            answerbotId: bot.id,
            sipUsername: bot.userName,
            sipDeviceId: voipClient.internalNumber,
          );
          if (kDebugMode) {
            debugPrint('syncAnswerbotState: Adopted bot ${bot.id} (username: ${bot.userName}, device: ${voipClient.internalNumber})');
          }
          return;
        }
      }
    }
  } catch (e) {
    if (kDebugMode) {
      debugPrint('syncAnswerbotState: Error detecting answerbot: $e');
    }
  }
}
```

**Step 2: Run analyze**

Run: `cd phoneblock_mobile && flutter analyze 2>&1 | grep -E '(error|syncAnswerbot)'`
Expected: No new errors related to the new method.

**Step 3: Commit**

```bash
git add phoneblock_mobile/lib/fritzbox/fritzbox_service.dart
git commit -m "Add syncAnswerbotState() to detect pre-existing answerbot"
```

---

### Task 2: Call `syncAnswerbotState()` from Fritz!Box settings screen

**Files:**
- Modify: `phoneblock_mobile/lib/fritzbox/screens/fritzbox_settings.dart:61-67` (inside `_loadData()`, the `if (connectionState == connected)` block)

**Step 1: Add the call**

In `_loadData()`, after `syncBlocklistMode()` and `config` reload (line 66), add:

```dart
        // Sync local blocklistMode with actual Fritz!Box state (bidirectional)
        cardDavStatus = await FritzBoxService.instance.syncBlocklistMode();
        config = await FritzBoxStorage.instance.getConfig();

        // Detect pre-existing answerbot setup
        await FritzBoxService.instance.syncAnswerbotState();
        config = await FritzBoxStorage.instance.getConfig();
```

The two new lines go right after the existing `config = await FritzBoxStorage.instance.getConfig();` on line 66, before the closing `}` of the `if (connected)` block.

**Step 2: Run analyze**

Run: `cd phoneblock_mobile && flutter analyze 2>&1 | grep error`
Expected: No new errors.

**Step 3: Commit**

```bash
git add phoneblock_mobile/lib/fritzbox/screens/fritzbox_settings.dart
git commit -m "Call syncAnswerbotState() during Fritz!Box settings load"
```
