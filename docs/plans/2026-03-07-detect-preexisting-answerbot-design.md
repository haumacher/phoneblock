# Detect Pre-Existing Answerbot Setup

## Problem

When a user connects a Fritz!Box in the mobile app, an answerbot that was previously set up (e.g. from the web interface or another device) is not detected. The CardDAV blocklist has similar detection that reconciles local state with Fritz!Box state, but no equivalent exists for the answerbot.

## Design

### Detection Method: Fritz!Box-First (Approach A)

A new `syncAnswerbotState()` method in `FritzBoxService` runs during the Fritz!Box settings screen load (`_loadData()`), after `syncBlocklistMode()` and before `_fetchAnswerbotInfo()`.

### Algorithm

1. Skip if not connected or `answerbotEnabled` is already `true`
2. List all VoIP clients from Fritz!Box via TR-064 (`getNumberOfClients()` + `getClient3(i)`)
3. If no VoIP clients exist, return early (skip API call)
4. Fetch all bots from PhoneBlock API (`GET /ab/list`)
5. For each bot: if `bot.userName` matches a VoIP client's `clientUsername`, auto-adopt:
   - `updateConfig(answerbotEnabled: true, answerbotId: bot.id, sipUsername: bot.userName, sipDeviceId: matchingClient.internalNumber)`
   - Return after first match

### Integration Point

In `FritzBoxSettingsScreen._loadData()`:

```dart
// Existing: detect pre-existing CardDAV
cardDavStatus = await FritzBoxService.instance.syncBlocklistMode();
config = await FritzBoxStorage.instance.getConfig();

// NEW: detect pre-existing answerbot
await FritzBoxService.instance.syncAnswerbotState();
config = await FritzBoxStorage.instance.getConfig();

// Existing: fetch answerbot info from server
final answerbotInfo = await _fetchAnswerbotInfo(config?.answerbotId);
```

### Error Handling

Wrap detection in try/catch. Log errors in debug mode, continue silently. Detection is best-effort and must never block the settings screen.

### Edge Cases

- **Multiple bots on same Fritz!Box**: Adopt first match
- **Bot on server but SIP device deleted from Fritz!Box**: No match, no adoption
- **Fritz!Box offline**: Skipped (connection check at step 1)
- **User not logged into PhoneBlock**: API call fails, caught and ignored
- **No SIP devices on Fritz!Box**: Skip API call entirely
