# Design: Personal Block Override for Whitelisted Numbers

**Issue:** [#184](https://github.com/haumacher/phoneblock/issues/184) — When a user blocks a number that is globally whitelisted, the number is not blocked on subsequent calls.

## Problem

The mobile app queries `/api/check?sha1={hash}` when a call comes in. The server resolves the hash via `NUMBERS.SHA1`, then checks `getPhoneApiInfo()` which returns `A_LEGITIMATE` immediately for globally whitelisted numbers. The user's personal block (stored in `PERSONALIZATION`) is never consulted.

Additionally, when a user rates a whitelisted number as spam via `/api/rate`, the `addRating` flow calls `processVotes`, which creates a `NUMBERS` row with SHA1 hash — violating the invariant that whitelisted numbers must never appear in `NUMBERS` or be resolvable by hash.

## Constraints

- Globally whitelisted numbers (in the `WHITELIST` table, admin-curated via direct SQL) must **never** get a `NUMBERS` row or be resolvable by hash.
- Votes for whitelisted numbers must be **discarded** (no effect on community vote totals).
- The personal block must still be recorded and respected on future hash-based queries.
- The mobile app sends only SHA-1 hashes (privacy by design) and a Bearer token (user identity).

## Solution: Add SHA1 Column to PERSONALIZATION

### Database Schema

Add `SHA1 BINARY(20)` to the `PERSONALIZATION` table:

```sql
ALTER TABLE PERSONALIZATION ADD COLUMN SHA1 BINARY(20);
CREATE INDEX PERSONALIZATION_HASH_IDX ON PERSONALIZATION (USERID, SHA1);
```

Update `db-schema.sql` to include the column for fresh installs. Backfill existing rows by computing SHA1 from the stored `PHONE` value during migration.

### BlockList Mapper

- Modify `addPersonalization(userId, phone)` → `addPersonalization(userId, phone, sha1)` to store the hash.
- Modify `addExclude(userId, phone)` → `addExclude(userId, phone, sha1)` to store the hash.
- Add `getPersonalizationStateByHash(userId, sha1)` → returns `Boolean` (true = blocked, false = whitelisted, null = not personalized).

### SpamCheckServlet (`/api/check`)

Insert a personalization check **before** the existing `resolvePhoneHash` → `getPhoneApiInfo` flow:

1. Get authenticated user ID (already available via `LoginFilter.getAuthenticatedUser`).
2. Query `PERSONALIZATION` by `(userId, sha1)` using the new `getPersonalizationStateByHash`.
3. If `BLOCKED = true` → return a spam result (synthetic high votes, appropriate rating) — short-circuit, skip all other lookups.
4. If `BLOCKED = false` → return legitimate result — short-circuit.
5. If `null` → fall through to existing logic unchanged.

### DB.addRating

After recording the personalization entry (lines 1044-1060), check if the number is globally whitelisted:

```java
if (reports.isWhiteListed(phone)) {
    recordVote = false;
}
```

This ensures:
- The `PERSONALIZATION` entry (with SHA1) is created, so future hash-based queries match.
- No `NUMBERS` row is created or updated.
- No community vote is recorded.

### NumServlet (`/api/num/{phone}`)

The plain-number lookup endpoint should also check personalization for the authenticated user. Since the full phone number is available here (not hashed), use the existing `getPersonalizationState(userId, phone)`.

## What Does NOT Change

- **CardDAV/Fritz!Box integration** — Already handles personalizations correctly in `AddressBookCache`.
- **WHITELIST table** — No changes, remains admin-curated.
- **Mobile app (CallChecker)** — No changes needed. Already sends Bearer token and SHA1 hash. The server-side fix is transparent.
- **Rating UI flow** — The mobile app's `/api/rate` call still sends the plain phone number and works as before, only the server-side vote recording changes.

## Edge Cases

- **User blocks then unblocks a whitelisted number:** `removePersonalization` deletes the row (including SHA1). Subsequent queries fall through to existing logic, which returns `A_LEGITIMATE` from the WHITELIST check.
- **Existing PERSONALIZATION rows without SHA1:** Migration backfills by computing SHA1 from PHONE. Any rows that fail (invalid phone format) are logged but left with NULL SHA1.
- **Multiple users blocking the same whitelisted number:** Each gets their own PERSONALIZATION row. No community effect.
