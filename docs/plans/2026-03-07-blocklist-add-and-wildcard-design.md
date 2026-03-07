# Blocklist Add Number & Wildcard Support

**Issue:** #260 - Users want to block country codes/number prefixes in their private blocklist
**Date:** 2026-03-07
**Scope:** Mobile app only (Flutter + Android native)

## Problem

Users cannot:
1. Directly add new numbers to the blocklist view (must go through call list rating flow)
2. Block number prefixes/country codes (e.g. block all calls from +43)

## Solution

Add a + button (FAB) to the blocklist view that lets users enter numbers directly, with an optional wildcard toggle for prefix-based blocking.

### Key Decisions

- **Exact numbers** go through the existing `/api/rate` server flow (stored in PERSONALIZATION table + global ratings)
- **Wildcard entries** are stored locally only in SQLite (no server changes)
- **Call screening** checks local wildcard prefixes before the server API query
- **Wildcard prefixes** stored in canonical international format: `+43`, `+491234`, etc.

## Data Model

### New SQLite table: `wildcard_blocks`

```sql
CREATE TABLE wildcard_blocks (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  prefix TEXT NOT NULL UNIQUE,  -- international format, e.g. "+43", "+491234"
  comment TEXT,                  -- optional user comment
  created INTEGER NOT NULL       -- Unix timestamp in milliseconds
);
```

- Prefixes always stored in international format with `+` prefix
- User input like `0043` or `0049...` normalized to `+43`, `+49...` before storage
- Minimum length: country code (e.g. `+43` = 3 chars)
- Must contain only `+` followed by digits

### SharedPreferences cache

Wildcard prefixes cached as JSON array in SharedPreferences for fast access by `CallChecker` (which cannot access SQLite directly):

```json
{"wildcard_prefixes": ["+43", "+491234"]}
```

Updated whenever wildcards are added or removed.

## UI Design

### Blocklist Screen Changes

**FloatingActionButton (+):** Bottom-right corner, opens the "Add Number" flow.

**Two-section display:**
1. **"Wildcard Rules"** section at top
   - Each entry shows prefix with `*` suffix (e.g. "+43*")
   - Filter/wildcard icon instead of block icon
   - Swipe-to-delete removes from local SQLite + SharedPreferences cache
   - Edit button allows modifying the comment
2. **"Blocked Numbers"** section below
   - Existing server-side entries (unchanged from today)
   - Swipe-to-delete calls server DELETE API
   - Edit button updates server comment

### Add Number Flow

**For exact numbers (wildcard toggle OFF) — 3 steps:**
1. Enter phone number + wildcard toggle (OFF)
2. Select spam category (PING, POLL, ADVERTISING, GAMBLE, FRAUD)
3. Add optional comment
4. Submit: POST to `/api/rate` with number, rating, comment

**For wildcard numbers (wildcard toggle ON) — 2 steps:**
1. Enter phone number prefix + wildcard toggle (ON)
2. Add optional comment (no spam category — makes no sense for a prefix)
3. Submit: normalize to international prefix, store in `wildcard_blocks` table, update SharedPreferences cache, reload list

### Number Entry Dialog

- Phone number `TextField` with `TextInputType.phone` keyboard
- "Wildcard" `Switch` toggle — when ON:
  - Visual `*` indicator shown after the number field
  - Validation changes to prefix validation (international format, minimum length)
  - Flow skips the spam category step
- When OFF:
  - Standard phone number validation
  - Full 3-step flow

## Call Screening

### CallChecker.java Changes

Before the existing server API query, add local wildcard prefix matching:

```
1. Incoming call → normalize to international format (existing)
2. Load wildcard prefixes from SharedPreferences
3. Check if normalized number starts with any stored prefix
4. If match → block immediately, report as wildcard-blocked
5. If no match → proceed with existing server API query
```

Wildcard matching is fast (iterate short prefix list, call `String.startsWith()`). No network latency. Happens before the server query to provide instant blocking for wildcard-matched calls.

### Reporting Wildcard-Blocked Calls

When a call is blocked by a wildcard match, report it to Flutter via `MainActivity.reportScreenedCall()` with a flag or special rating so the call list can show which rule triggered the block.

## Validation Rules

### Wildcard Prefix Validation
- After normalization, must start with `+`
- Must contain only digits after `+`
- Minimum length: 3 characters (e.g. `+43` for a country code)
- No maximum length (allows area code blocking like `+4912345`)
- Must not duplicate an existing wildcard prefix

### Exact Number Validation
- Existing validation via the `/api/rate` endpoint (server-side `NumberAnalyzer`)

## I18N

New strings needed in `app_de.arb` (German source):
- Section header: "Wildcard-Regeln" / "Gesperrte Nummern"
- Dialog title: "Nummer hinzufuegen"
- Wildcard toggle label: "Nummernbereich sperren"
- Wildcard suffix indicator text
- Validation error messages (too short, invalid format, duplicate)
- Confirmation messages for add/delete

## DB Migration

- Increment SQLite database version in `storage.dart`
- Add migration to create `wildcard_blocks` table
- Update `_createDB` schema for fresh installs

## Files to Modify

### Flutter (Dart)
- `phoneblock_mobile/lib/storage.dart` — new table, CRUD methods, DB migration
- `phoneblock_mobile/lib/main.dart` — FAB on blocklist, add-number flow, two-section display, SharedPreferences cache sync
- `phoneblock_mobile/lib/l10n/app_de.arb` — new German strings

### Android (Java)
- `CallChecker.java` — load wildcard prefixes from SharedPreferences, prefix matching before server query
- `MainActivity.java` — possibly add method to sync wildcard cache via MethodChannel

### Build
- Run `./gradlew translateArb` for auto-translation
- Run `flutter gen-l10n` for Dart localization code generation
