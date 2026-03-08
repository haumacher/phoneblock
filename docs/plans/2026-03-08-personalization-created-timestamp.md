# Personalization Created Timestamp Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a `CREATED` timestamp column to the `PERSONALIZATION` table and expose it through the blacklist/whitelist API and mobile app UI.

**Architecture:** DB migration adds the column, MyBatis mapper methods are updated to insert/select it, the msgbuf `PersonalizedNumber` model gets a new `created` field, and the mobile app displays the date in list tiles.

**Tech Stack:** Java 17 + MyBatis (server), msgbuf proto (model generation), Flutter/Dart (mobile), H2 SQL (database)

---

### Task 1: Database Migration

**Files:**
- Create: `phoneblock/src/main/java/de/haumacher/phoneblock/db/db-migration-18.sql`
- Modify: `phoneblock/src/main/java/de/haumacher/phoneblock/db/db-schema.sql`

**Step 1: Create migration script**

Create `phoneblock/src/main/java/de/haumacher/phoneblock/db/db-migration-18.sql`:
```sql
ALTER TABLE PERSONALIZATION ADD COLUMN CREATED BIGINT DEFAULT 0 NOT NULL;
```

**Step 2: Update db-schema.sql**

In `db-schema.sql`, update the version from `17` to `18`:
```sql
INSERT INTO PROPERTIES (NAME, VAL) VALUES('db.version', '18');
```

In the `PERSONALIZATION` table definition, add the `CREATED` column after `BLOCKED`:
```sql
CREATE TABLE PERSONALIZATION (
	USERID BIGINT NOT NULL,
	PHONE CHARACTER VARYING(100) NOT NULL,
	SHA1 BINARY(20),
	BLOCKED BOOLEAN DEFAULT true NOT NULL,
	CREATED BIGINT DEFAULT 0 NOT NULL,
	CONSTRAINT PERSONALIZATION_PK PRIMARY KEY (USERID,PHONE)
);
```

**Step 3: Commit**
```
feat: add CREATED column to PERSONALIZATION table (migration 18)
```

---

### Task 2: Update DBPersonalization and BlockList Mapper

**Files:**
- Modify: `phoneblock/src/main/java/de/haumacher/phoneblock/db/DBPersonalization.java`
- Modify: `phoneblock/src/main/java/de/haumacher/phoneblock/db/BlockList.java`

**Step 1: Add `created` field to DBPersonalization**

In `phoneblock/src/main/java/de/haumacher/phoneblock/db/DBPersonalization.java`, add:
```java
private long created;

public long getCreated() {
    return created;
}

public void setCreated(long created) {
    this.created = created;
}
```

**Step 2: Update BlockList.java mapper methods**

Update `addPersonalization()` to accept and insert `created`:
```java
@Insert("""
        insert into PERSONALIZATION (USERID, PHONE, SHA1, BLOCKED, CREATED)
        values (#{userId}, #{phone}, #{sha1}, true, #{created})
        """)
void addPersonalization(long userId, String phone, byte[] sha1, long created);
```

Update `addExclude()` similarly:
```java
@Insert("""
        insert into PERSONALIZATION (USERID, PHONE, SHA1, BLOCKED, CREATED)
        values (#{userId}, #{phone}, #{sha1}, false, #{created})
        """)
void addExclude(long userId, String phone, byte[] sha1, long created);
```

Update `getPersonalizations()` to return `List<DBPersonalization>` and select `CREATED`:
```java
@Select("""
        select PHONE, CREATED from PERSONALIZATION
        where USERID = #{userId} and BLOCKED
        order by PHONE
        """)
List<DBPersonalization> getPersonalizations(long userId);
```

Update `getWhiteList()` to return `List<DBPersonalization>` and select `CREATED`:
```java
@Select("""
        select PHONE, CREATED from PERSONALIZATION
        where USERID = #{userId} and NOT BLOCKED
        order by PHONE
        """)
List<DBPersonalization> getWhiteList(long userId);
```

**Step 3: Commit**
```
feat: update BlockList mapper and DBPersonalization for created timestamp
```

---

### Task 3: Update Callers of addPersonalization/addExclude

**Files:**
- Modify: `phoneblock/src/main/java/de/haumacher/phoneblock/db/DB.java` (lines ~1062-1064)
- Modify: `phoneblock/src/main/java/de/haumacher/phoneblock/carddav/resource/AddressResource.java` (line ~152)

**Step 1: Update DB.java**

At `DB.java:1062-1064`, add `System.currentTimeMillis()` as the `created` parameter:
```java
if (block) {
    blocklist.addPersonalization(userId, phone, sha1, System.currentTimeMillis());
} else {
    blocklist.addExclude(userId, phone, sha1, System.currentTimeMillis());
}
```

**Step 2: Update AddressResource.java**

At `AddressResource.java:152`, add `System.currentTimeMillis()`:
```java
blockList.addPersonalization(currentUser, phoneId, sha1, System.currentTimeMillis());
```

**Step 3: Fix any other callers**

Search for other calls to `addPersonalization` or `addExclude` and update them the same way.

**Step 4: Commit**
```
feat: pass created timestamp when inserting personalizations
```

---

### Task 4: Update PersonalizationServlet to Return Created Timestamp

**Files:**
- Modify: `phoneblock/src/main/java/de/haumacher/phoneblock/app/api/PersonalizationServlet.java` (doGet method, lines ~118-160)

**Step 1: Update doGet() to use new return types**

The `getPersonalizations()` and `getWhiteList()` now return `List<DBPersonalization>` instead of `List<String>`. Update the code that iterates over the results.

Replace the current `List<String> phoneIds` logic with:
```java
List<DBPersonalization> entries;

String servletPath = req.getServletPath();
if (BLACKLIST_PATH.equals(servletPath)) {
    entries = blockList.getPersonalizations(userId);
    LOG.debug("Retrieved {} blocked numbers for user '{}'", entries.size(), userName);
} else if (WHITELIST_PATH.equals(servletPath)) {
    entries = blockList.getWhiteList(userId);
    LOG.debug("Retrieved {} whitelisted numbers for user '{}'", entries.size(), userName);
} else {
    ServletUtil.sendMessage(resp, HttpServletResponse.SC_NOT_FOUND, "Unknown endpoint");
    return;
}

// Extract phone IDs for comment lookup
List<String> phoneIds = entries.stream().map(DBPersonalization::getPhone).collect(java.util.stream.Collectors.toList());
```

Add `import de.haumacher.phoneblock.db.DBPersonalization;` at top.

Then update the loop that builds `PersonalizedNumber` objects to use entries and set created:
```java
// Build a map from phone to created timestamp
Map<String, Long> createdMap = new HashMap<>();
for (DBPersonalization entry : entries) {
    createdMap.put(entry.getPhone(), entry.getCreated());
}

List<PersonalizedNumber> numbers = new ArrayList<>();
for (String phoneId : phoneIds) {
    // ... existing phone parsing logic ...

    PersonalizedNumber pn = PersonalizedNumber.create()
        .setPhone(phoneInternational)
        .setLabel(label)
        .setComment(commentRating != null ? commentRating.getComment() : null)
        .setRating(commentRating != null ? commentRating.getRating() : null)
        .setCreated(createdMap.getOrDefault(phoneId, 0L));
    numbers.add(pn);
}
```

**Step 2: Commit**
```
feat: return created timestamp in blacklist/whitelist API responses
```

---

### Task 5: Update api.proto and Regenerate Models

**Files:**
- Modify: `phoneblock-shared/src/main/java/de/haumacher/phoneblock/app/api/model/api.proto`
- Generated: `phoneblock-shared/src/main/java/de/haumacher/phoneblock/app/api/model/PersonalizedNumber.java`

**Step 1: Add created field to api.proto**

In the `PersonalizedNumber` message (around line 438-453), add after `rating`:
```proto
/** Timestamp when this entry was added to the personalization list (milliseconds since epoch). */
long created;
```

**Step 2: Regenerate Java model**

Run from project root:
```bash
cd phoneblock-shared && mvn generate-sources
```

This regenerates `PersonalizedNumber.java` with the new `created` field, getter, setter, and JSON serialization.

**Step 3: Build to verify**

```bash
cd phoneblock && mvn compile -q
```

**Step 4: Commit**
```
feat: add created field to PersonalizedNumber proto and generated model
```

---

### Task 6: Update Dart API Model (Mobile App)

**Files:**
- Modify: `phoneblock_mobile/lib/api.dart`

**Step 1: Add `created` field to PersonalizedNumber class**

Since `DartLib` generation is commented out in `api.proto`, manually update the Dart class.

In `phoneblock_mobile/lib/api.dart`, in the `PersonalizedNumber` class (around line 1957):

Add field:
```dart
/// Timestamp when this entry was added to the personalization list (milliseconds since epoch).
int created;
```

Update constructor:
```dart
PersonalizedNumber({
    this.phone = "",
    this.label,
    this.comment,
    this.rating,
    this.created = 0,
});
```

Add to `_readProperty()`:
```dart
case 'created': created = json.expectInt(); break;
```

Add to `_writeProperties()`:
```dart
json.addProperty('created', created);
```

**Step 2: Run flutter analyze**

```bash
cd phoneblock_mobile && flutter analyze
```

**Step 3: Commit**
```
feat: add created field to Dart PersonalizedNumber model
```

---

### Task 7: Display Created Date in Mobile App

**Files:**
- Modify: `phoneblock_mobile/lib/main.dart` (in `_buildNumberTile` method, around line 4626)
- Modify: `phoneblock_mobile/lib/l10n/app_de.arb`

**Step 1: Add localization string**

In `phoneblock_mobile/lib/l10n/app_de.arb`, add:
```json
"personalizedAddedDate": "Hinzugefügt: {date}",
"@personalizedAddedDate": {
  "description": "Shows when a number was added to the blacklist or whitelist",
  "placeholders": {
    "date": {
      "type": "String"
    }
  }
}
```

**Step 2: Update `_buildNumberTile()` to show created date**

In `phoneblock_mobile/lib/main.dart`, update the `_buildNumberTile` method. Replace the current `subtitle` in the `ListTile` (around line 4631) with a combined subtitle that shows both the comment and the created date:

```dart
subtitle: _buildNumberSubtitle(context, personalizedNumber),
```

Add a helper method in the `PersonalizedNumberListScreen` state class:

```dart
Widget? _buildNumberSubtitle(BuildContext context, api.PersonalizedNumber pn) {
  final comment = pn.comment;
  final created = pn.created;

  final hasComment = comment != null && comment.isNotEmpty;
  final hasDate = created > 0;

  if (!hasComment && !hasDate) return null;

  final dateFormat = DateFormat.yMMMd(Localizations.localeOf(context).toString());

  return Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      if (hasComment)
        Text(
          comment!,
          style: TextStyle(color: Colors.grey[600], fontSize: 14),
        ),
      if (hasDate)
        Text(
          context.l10n.personalizedAddedDate(dateFormat.format(DateTime.fromMillisecondsSinceEpoch(created))),
          style: TextStyle(color: Colors.grey[500], fontSize: 12),
        ),
    ],
  );
}
```

**Step 3: Run flutter analyze**

```bash
cd phoneblock_mobile && flutter analyze
```

**Step 4: Commit**
```
feat: display created date in blacklist/whitelist views
```

---

### Task 8: Update OpenAPI Specification

**Files:**
- Modify: `phoneblock/src/main/webapp/api/phoneblock.json`

**Step 1: Add `created` to PersonalizedNumber schema**

In the `PersonalizedNumber` schema (around line 1365-1404), add the `created` property after `rating`:
```json
"created": {
    "type": "integer",
    "format": "int64",
    "description": "Timestamp when this entry was added to the personalization list (milliseconds since epoch).",
    "example": 1709913600000
}
```

**Step 2: Update example responses**

Update the blacklist and whitelist GET response examples to include `created` fields.

**Step 3: Commit**
```
docs: add created field to PersonalizedNumber in OpenAPI spec
```

---

### Task 9: Build and Verify

**Step 1: Full Maven build**

```bash
mvn clean install -q
```

**Step 2: Flutter analyze**

```bash
cd phoneblock_mobile && flutter analyze
```

**Step 3: Final commit if any fixups needed**
