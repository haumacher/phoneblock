# Design: Add "created" timestamp to PERSONALIZATION table and API

## Summary

Add a `CREATED` column to the `PERSONALIZATION` table to track when each personalized number entry was added. Expose this timestamp via the blacklist/whitelist API and display it in the mobile app's list views.

## Database

- Add `CREATED BIGINT DEFAULT 0 NOT NULL` to `PERSONALIZATION` table
- Migration `db-migration-18.sql`: `ALTER TABLE PERSONALIZATION ADD COLUMN CREATED BIGINT DEFAULT 0 NOT NULL`
- Update `db-schema.sql` to version 18
- Existing rows default to `0` (unknown creation time)

## Server-side (Java)

### BlockList.java (MyBatis mapper)

- Modify `addPersonalization()` and `addExclude()` to accept a `long created` parameter and INSERT it
- Modify `getPersonalizations()` and `getWhiteList()` to return `List<DBPersonalization>` instead of `List<String>`, selecting `PHONE` and `CREATED`

### DBPersonalization.java

- Add `long created` field with getter/setter (currently has `phone` and `blocked`)

### PersonalizationServlet.java

- In `doGet()`, use the `created` value from the DB query result to populate `PersonalizedNumber.setCreated()`

### Callers of addPersonalization/addExclude

- `DB.java` line ~1062: pass `System.currentTimeMillis()`
- `AddressResource.java` line ~152: pass `System.currentTimeMillis()`

## API Model (msgbuf)

### api.proto

- Add `long created` field to `PersonalizedNumber` message

### Generated code

- Regenerate `PersonalizedNumber.java` (phoneblock-shared)
- Regenerate `api.dart` (phoneblock_mobile) — manually, since DartLib is commented out

### OpenAPI spec

- Update `phoneblock.json` to include `created` in the `PersonalizedNumber` schema and examples

## Mobile App (Flutter)

### api.dart

- Add `int created` field to `PersonalizedNumber` class (via regeneration or manual edit)

### main.dart

- In `_buildNumberTile()`, display the `created` timestamp as a secondary line in the subtitle (formatted date, e.g., "Added: 8 Mar 2026")
- Only show date if `created > 0` (skip unknown creation times)

### Localization

- Add a localization string for the date display format in `app_de.arb`
