# Unified Call List Display

## Problem

The mobile app's call list shows 4 different visual layouts depending on call source (mobile vs Fritz!Box), block status, and data availability. This creates a chaotic, inconsistent user experience.

## Root Causes

1. **UI**: `_buildCallListItem()` in `main.dart` branches rendering logic based on `call.source`, producing different row layouts for mobile vs Fritz!Box calls.
2. **Server**: `NumberAnalyzer.phoneInfoFromNumber()` only sets `phone` but not `label` in the API response. This means personally blocked, personally whitelisted, globally whitelisted, and non-spam numbers all return without a formatted label. Only `NumServlet.lookup()` explicitly sets the label.

## Design

### Server Fix

**File:** `NumberAnalyzer.java` — `phoneInfoFromNumber()`

Add `.setLabel(number.getShortcut())` so all code paths that create PhoneInfo get a formatted label:

```java
public static PhoneInfo phoneInfoFromNumber(PhoneNumer number) {
    return PhoneInfo.create()
        .setPhone(number.getPlus())
        .setLabel(number.getShortcut());
}
```

This fixes label for: personal blocklist, personal whitelist, global whitelist, non-spam numbers. Truly unknown numbers (hash-only lookup, no resolution) remain without label — expected, since we don't have the number to format.

### Mobile UI Unification

**File:** `main.dart` — `_buildCallListItem()`

Unify all call entries to use this layout:

```
Line 1: call.label ?? call.phoneNumber              [BADGE]
Line 2: location/carrier                             (only if available)
Line 3: [icon] device-or-status · timestamp · duration
Line 4: report info                                   [menu]
```

**Line 3 rules:**
- Icon + color derived from block status:
  - Blocked → red block icon
  - Not blocked/accepted → green check icon
  - Missed → orange phone icon
- Text label:
  - Fritz!Box calls → device name (e.g., "Anrufbeantworter", "Telefon 1")
  - Mobile calls → status text ("Blockiert", "Nicht blockiert")
- Followed by: `· timestamp` (relative: "Heute, 19:33" / absolute: "06.03.2026, 14:45")
- Followed by: `· duration` (only if > 0, format "0:01")

**Line 4** unchanged — same blocklist/complaint/range logic.

**Badges** (top-right) unchanged — SPAM, SPAM?, Legitimate.

### What Does NOT Change

- Data model (`ScreenedCall`) — no changes needed
- Database schema — no changes needed
- Fritz!Box sync logic — no changes needed
- Badge rendering — stays as-is
