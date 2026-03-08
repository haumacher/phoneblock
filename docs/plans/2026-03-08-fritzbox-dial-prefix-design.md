# Fritz!Box Dial Prefix Query

## Problem

When the mobile app syncs the Fritz!Box call list, local numbers (e.g., `022376922894`) need to be converted to international format for SHA-1 hash lookups against the PhoneBlock API. Currently the normalization hardcodes `"DE"` as the country code, which breaks for non-German Fritz!Boxes.

If the Fritz!Box's country differs from the mobile phone's SIM country, the same local number gets hashed with different prefixes, producing different SHA-1 hashes and failing the lookup.

## Solution

Query the Fritz!Box's configured country and area codes via TR-064 on connect, and use them for normalizing call list numbers.

### Fritz!Box TR-064 Data

The `X_VoIP:1` service provides:

| Action | Field | Example (DE) | Example (US) | Purpose |
|---|---|---|---|---|
| `GetVoIPCommonCountryCode` | `lkz` | `"49"` | `"1"` | Country calling code |
| `GetVoIPCommonCountryCode` | `lkzPrefix` | `"00"` | `"011"` | International dialing prefix |
| `GetVoIPCommonAreaCode` | `okzPrefix` | `"0"` | `"1"` | Domestic trunk prefix |

The `fritz_tr064` Dart package already supports these via `getVoIPCommonCountryCode()` and `getVoIPCommonAreaCode()`.

### Normalization Logic

```
toInternationalForm(phone, lkz, lkzPrefix, okzPrefix):
  if phone starts with lkzPrefix → "+" + phone[lkzPrefix.length:]
  if phone starts with "+"       → phone
  if okzPrefix non-empty and phone starts with okzPrefix → "+" + lkz + phone[okzPrefix.length:]
  if okzPrefix empty → "+" + lkz + phone   (e.g., Italy has no trunk prefix)
```

### Changes

| File | Change |
|---|---|
| `fritzbox_service.dart` | Query country/area code on connect; use new normalizer in syncCallList |
| `fritzbox_storage.dart` | Add 3 columns to `fritzbox_config` table (`countryCode`, `intlPrefix`, `trunkPrefix`) + DB migration |
| New Dart utility | `toInternationalForm()` function |

### Scope

- Mobile app only (Fritz!Box call sync path)
- Call screening (`CallChecker`) is NOT changed — it correctly uses `TelephonyManager.getNetworkCountryIso()`
- Answer bot and fbclient are separate deployment concerns

### Backward Compatibility

New SQLite columns default to `"49"`, `"00"`, `"0"` (German defaults) so existing installs that haven't reconnected yet behave as before.
