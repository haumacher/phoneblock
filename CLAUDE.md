# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PhoneBlock is a community-driven spam call blocking system for Fritz!Box routers and mobile phones. It provides several main components:

1. **Web Application** (`phoneblock/`) - Central web service for managing spam phone numbers
2. **Answer Bot** (`phoneblock-ab/`) - SIP-based answering machine that engages spam callers
3. **Mobile App** (`phoneblock_mobile/`) - Android/Flutter app for call screening on mobile devices
4. **Accounting Tool** (`phoneblock-accounting/`) - Command-line tool for importing contribution data from bank CSV exports

The system uses a community-maintained database where users report spam numbers, which are then shared with all PhoneBlock users through downloadable blocklists, CardDAV integration, or real-time API queries.

## Development Commands

### Maven (Java Backend)

**Build entire project:**
```bash
mvn clean install
```

**Build specific module:**
```bash
cd phoneblock  # or phoneblock-ab, phoneblock-shared
mvn clean package
```

**Run web application locally (Jetty):**
```bash
cd phoneblock
mvn jetty:run
```
Access at: http://localhost:8080/phoneblock

**Run tests:**
```bash
mvn test
```

**Run single test:**
```bash
mvn test -Dtest=YourTestClass
```

**Security vulnerability check:**
```bash
mvn dependency-check:check
```

**Update dependencies:**
```bash
mvn versions:use-latest-releases versions:update-properties -DgenerateBackupPoms=false
```

**Deploy to Tomcat:**
```bash
cd phoneblock
mvn tomcat7:redeploy
```
Requires `.phoneblock` configuration file with server credentials.

### Answer Bot

**Build standalone JAR:**
```bash
cd phoneblock-ab
mvn clean package
```
Creates `phoneblock-ab-*-jar-with-dependencies.jar` in target/

**Run locally:**
```bash
java -jar phoneblock-ab/target/phoneblock-ab-*-jar-with-dependencies.jar -f ~/.phoneblock
```
Requires `.phoneblock` configuration file (see `phoneblock-ab/.phoneblock.template`)

**Build Docker image:**
```bash
cd phoneblock-ab
docker build -t phoneblock/answerbot .
```

### Accounting Tool

**Build standalone JAR:**
```bash
cd phoneblock-accounting
mvn clean package
```
Creates `phoneblock-accounting-*-jar-with-dependencies.jar` in target/

**Run locally:**
```bash
java -jar phoneblock-accounting/target/phoneblock-accounting-*-jar-with-dependencies.jar <csv-file>
```
Imports contribution data from CSV bank exports for accounting purposes.

### Flutter Applications

**Answer Bot UI:**
```bash
cd phoneblock_answerbot_ui
flutter pub get
flutter build web --base-href /phoneblock/ab/
```
Output: `build/web/` (embedded into WAR at build time)

**Mobile App:**
```bash
cd phoneblock_mobile
flutter pub get
flutter run                    # Debug mode
flutter build apk              # Release APK
flutter build appbundle        # Play Store bundle
flutter test
flutter analyze
```

### Fritz!Box Client (JavaScript)

```bash
cd phoneblock/fbclient
npm install
npx webpack
```

## Architecture

### Module Structure

- **phoneblock-shared/** - Common data models using msgbuf protocol definitions
- **phoneblock-ab/** - SIP answering bot implementation using mjSIP library
- **phoneblock-accounting/** - Command-line tool for importing contribution accounting data from CSV bank exports
- **phoneblock/** - Main web application (WAR deployment)
- **phoneblock_mobile/** - Flutter Android app
- **phoneblock_answerbot_ui/** - Flutter web UI for answer bot management
- **phoneblock-tools/** - Utility tools
- **phoneblock-watchdog/** - Monitoring service

### Web Application (phoneblock/)

**Technology Stack:**
- Java 17, Jakarta EE servlets
- MyBatis for SQL database access
- H2 database (file-based or in-memory)
- Thymeleaf templating
- Bulma CSS framework
- pac4j for OAuth (Google, Facebook)
- OpenAI GPT integration for comment summarization

**Key Packages:**
- `de.haumacher.phoneblock.app` - Main servlets and filters (login, search, settings)
- `de.haumacher.phoneblock.app.api` - REST API endpoints
- `de.haumacher.phoneblock.db` - Database layer (MyBatis mappers, schema migrations)
- `de.haumacher.phoneblock.carddav` - CardDAV server implementation for Fritz!Box integration
- `de.haumacher.phoneblock.ab` - Answer bot management servlets
- `de.haumacher.phoneblock.callreport` - Call log upload from Fritz!Box
- `de.haumacher.phoneblock.scheduler` - Background task scheduling
- `de.haumacher.phoneblock.mail` - Email sending (verification, notifications)

**Database:**
- Schema: `phoneblock/src/main/java/de/haumacher/phoneblock/db/db-schema.sql`
- Migrations: `db-migration-*.sql` (numbered sequentially)
- MyBatis XML mappers in `de/haumacher/phoneblock/db/*.xml`
- Main tables: NUMBERS (phone data), USERS, RATINGS, COMMENTS, SPAMREPORTS, BLOCKLIST

**Configuration:**
- Requires `.phoneblock` file (see `.phoneblock.template`)
- Database URL, OAuth credentials, SMTP settings
- Filtering via Maven resource filtering during build

**Web UI:**
- Static assets: `src/main/webapp/assets/`
- Thymeleaf templates: `src/main/webapp/WEB-INF/templates/`
- Embedded Flutter app at `/ab` (from phoneblock_answerbot_ui)

### Answer Bot (phoneblock-ab/)

**SIP-based answering machine** that:
1. Registers as VoIP phone with Fritz!Box
2. Answers calls from numbers on blocklist
3. Engages callers in automated conversation using pre-recorded audio
4. Records conversations (optional)
5. Reports call statistics back to PhoneBlock

**Audio Conversation System:**
- Audio files stored in `conversation/` subdirectories
- Dialog phases: hello → waiting → question → still-there → (loop)
- Supports PCMA and PCMA-WB codecs
- Voice activity detection to respond to caller pauses
- Audio files prepared using `bin/convert-audio.sh`

**Configuration:**
- `.phoneblock` file with SIP credentials, registrar, audio paths
- See `.phoneblock.template` for all options
- Key settings: via-addr, host-port, sip-user, sip-passwd, conversation directory

**Integration:**
- Queries PhoneBlock API to check if number is spam
- Uses bearer token authentication
- Can be run standalone or via Docker

### Mobile App (phoneblock_mobile/)

**Android call screening app** using Flutter:
- Intercepts incoming calls via CallScreeningService
- Queries PhoneBlock API with SHA-1 hashed numbers
- Blocks calls based on configurable vote threshold
- Maintains local call history in SQLite
- OAuth login flow via deep linking

**Key Files:**
- `lib/main.dart` - UI, routing, OAuth handling
- `lib/state.dart` - Data models (jsontool)
- `lib/storage.dart` - SQLite database
- `android/.../MainActivity.java` - MethodChannel bridge
- `android/.../CallChecker.java` - CallScreeningService

**Build Requirements:**
- `key.properties` file for release signing (not in repo)
- Android SDK API 24+

### Shared Code (phoneblock-shared/)

Protocol buffer-like message definitions using msgbuf framework. Generated Java classes for API communication between modules.

## API Integration

**API Documentation:**
- OpenAPI 3.0 specification: `phoneblock/src/main/webapp/api/phoneblock.json`
- Accessible at: https://phoneblock.net/phoneblock/api/phoneblock.json
- When modifying API endpoints, update the OpenAPI specification to keep documentation current

**Base URL:** https://phoneblock.net/phoneblock/api/

**Key Endpoints:**
- `GET /num/{phone}?format=json` - Query spam status
- `POST /rate` - Submit spam rating
- `GET /blocklist` - Download blocklist with incremental sync support
  - Full sync: max once per month
  - Incremental sync (`?since=version`): max once per day

**Authentication:**
- Bearer token in Authorization header
- Tokens created in settings UI

## Development Workflow

### Database Migrations

When adding schema changes:
1. Create new `db-migration-XX.sql` file (increment number)
2. Update `db-schema.sql` with final state
3. Update `PROPERTIES` table version number
4. Add migration execution in `DB.java`

### Adding New Servlets

1. Create servlet class extending `HttpServlet` in appropriate package
2. Add `@WebServlet` annotation or configure in web.xml
3. Use `DBService.getInstance()` for database access
4. Follow existing patterns for session management and authentication

**Error Reporting:**
- **Never use `resp.sendError()` for non-GET requests** - The error response triggers the default error servlet which only accepts GET, causing HTTP 405 errors
- **Use `ServletUtil.sendMessage(resp, statusCode, message)` instead** - Sets status code and writes error message directly to response body
- Example: Replace `resp.sendError(SC_NOT_FOUND, "User not found")` with `ServletUtil.sendMessage(resp, SC_NOT_FOUND, "User not found")`

### Message Protocol Changes

When modifying `phoneblock-shared/`:
1. Edit `.proto` files in `src/main/proto/`
2. Run `mvn generate-sources` to regenerate Java classes
3. Rebuild dependent modules

### Audio Conversation Updates

1. Record audio files (WAV format recommended initially)
2. Place in appropriate `conversation/*/` directory
3. Run `bin/convert-audio.sh` to generate PCMA/PCMA-WB versions
4. Test with answer bot using test-prefix configuration

### Internationalization (I18N)

**IMPORTANT**: The `data-tx` attributes in HTML templates are auto-generated magic attributes that link translations across multiple language files.

**Source Language:**
- **Only edit `de/` (German) templates** - This is the ONLY source language file to edit
- **ALL other language directories are auto-generated** - Never edit them directly, including en-US (ar, da, el, en-US, es, fr, it, nb, nl, pl, sv, uk, zh-Hans, etc.)
- Translation tool automatically generates all other languages from de/ sources during build

**Page Title Translations:**
- When creating new pages, add page title keys to `phoneblock/src/main/java/Messages_de.properties`
- Format: `page.{page-name}.title=German Title Text`
- Example: `page.usage-search.title=Nach Nummern suchen - PhoneBlock Mobile`
- Used in template head: `<head th:replace="~{fragments/page :: head(title=#{page.usage-search.title})}"></head>`
- Without this entry, the page will fail to render with an error

Rules when modifying templates:
- **Never duplicate `data-tx` attributes** - Each unique text should have only one `data-tx` reference
- **Remove `data-tx` when changing text content** - Modified text breaks the I18N linkage
- **Keep `data-tx` on unmodified fallback text** - Original translations remain linked
- I18N generation tool automatically assigns `data-tx` values during build

Example - Adding conditional device name display:
```html
<!-- WRONG: Duplicated data-tx -->
<p th:if="${!#strings.isEmpty(label)}" data-tx="t0002">
    Link to <strong th:text="${label}">device</strong>
</p>
<p th:if="${#strings.isEmpty(label)}" data-tx="t0002">
    Link to your device
</p>

<!-- CORRECT: Remove data-tx from modified version -->
<p th:if="${!#strings.isEmpty(label)}">
    Link to <strong th:text="${label}">device</strong>
</p>
<p th:if="${#strings.isEmpty(label)}" data-tx="t0002">
    Link to your device
</p>
```

**Where does new user-facing text belong?**

- **Default: inline German text in the `de/` template.** The HTML auto-translate tool regenerates all other locales. Use Thymeleaf expressions for dynamic values:
  ```html
  <p>Heute: <span th:text="${currentUserCount}">0</span> Nutzer insgesamt, <span th:text="${todayGrowth >= 0 ? '+' + todayGrowth : todayGrowth}">+0</span> seit gestern.</p>
  ```
  Do **not** invent a `Messages_*.properties` key for this.
- **Use `Messages_*.properties` only when the text is not template body text:** page titles referenced from fragment `th:replace` heads, strings produced from Java code (servlets, error messages), or values passed into templates via `th:text="#{...}"` attributes on elements that otherwise contain no translatable content. Every such key must be added to `Messages_de.properties` and then propagated with the `tl-maven-plugin` translate goal (see next section); never hand-write stubs in the other locale bundles.

### Server Messages Internationalization (Messages_*.properties)

Java `ResourceBundle` keys used by the web app live in `phoneblock/src/main/java/Messages_*.properties`.

- **Only edit `Messages_de.properties`** — it is the source of truth.
- **All other `Messages_<lang>.properties` files are auto-generated via DeepL** — never edit them directly.
- Translation is run via the `tl-maven-plugin` (`com.top-logic:tl-maven-plugin:translate`) with the `with-deepl` profile. Eclipse launch: `phoneblock/bin/Translate PhoneBlock Resources.launch`.
- Manual command-line trigger (uses the `translate-messages` execution configured in `phoneblock/pom.xml`, so the plugin version is taken from there):
  ```bash
  cd phoneblock
  mvn -Pwith-deepl com.top-logic:tl-maven-plugin:translate@translate-messages -N
  ```
- After adding/changing a key in `Messages_de.properties`, run the translation before committing so all language bundles stay in sync (otherwise the runtime throws `MissingResourceException`).

### Mobile App Internationalization (phoneblock_mobile/)

> **STRICT RULES — VIOLATION WILL BREAK ALL TRANSLATIONS:**
>
> **FORBIDDEN — Do NOT touch these files, they are 100% auto-generated:**
> - `lib/l10n/app_en.arb`, `app_fr.arb`, `app_es.arb`, etc. — ALL `.arb` files except `app_de.arb`
> - `lib/l10n/app_localizations.dart` and ALL `app_localizations_*.dart` files
>
> **The ONLY file you may edit is `lib/l10n/app_de.arb`** (German source).

**Adding or changing a message — the ONLY correct flow:**
1. Edit strings in `lib/l10n/app_de.arb` only — no `x-translated`, the plugin adds it:
   ```json
   "myKey": "German text",
   "@myKey": {
     "description": "What this string is for"
   }
   ```
2. Run `./gradlew translateArb` from `phoneblock_mobile/` (translates via DeepL)
3. Run `flutter gen-l10n` from `phoneblock_mobile/` (regenerates Dart code)
4. Use in code: `AppLocalizations.of(context)!.myKey`

**All three steps (edit, translate, gen-l10n) MUST be done together. Never commit with only step 1 done.**

### Flutter `use_build_context_synchronously` Warnings

In `State` subclasses, the analyzer cannot link a `context.mounted` check to later `context` uses because each `context` access re-evaluates the `State.context` getter. To fix:

1. **Add `BuildContext context` as an explicit parameter** to async methods that use `context` after an `await`:
   ```dart
   // WRONG — analyzer can't track State.context across awaits:
   Future<void> doWork() async {
     var response = await sendRequest(...);
     if (!context.mounted) return;      // checks State.context
     showDialog(context: context, ...); // re-evaluates State.context — warning!
   }

   // CORRECT — same variable checked and used:
   Future<void> doWork(BuildContext context) async {
     var response = await sendRequest(...);
     if (!context.mounted) return;      // checks parameter
     showDialog(context: context, ...); // uses same parameter — no warning
   }
   ```

2. **Add `if (!context.mounted) return;`** after every `await` that introduces a new async gap (e.g. `Future.delayed`, `sendRequest`) before any subsequent `context` use.

3. **In `.then()` callbacks**, check `context.mounted` before using `context`:
   ```dart
   deleteBot(context, bot).then((value) {
     if (!context.mounted) return;
     Navigator.pop(context);
   });
   ```

## Configuration Files

**Required for local development:**
- `phoneblock/.phoneblock` - Database, OAuth, SMTP credentials
- `phoneblock-ab/.phoneblock` - Answer bot SIP and audio settings
- `phoneblock_mobile/android/key.properties` - Release signing (mobile only)

**Templates provided:**
- `.phoneblock.template` files in respective directories
- Copy and fill in actual values

**JNDI Configuration:**
- See [JNDI-CONFIGURATION.md](JNDI-CONFIGURATION.md) for comprehensive documentation of all JNDI configuration options for production deployments
- JNDI properties can be configured in Tomcat's `context.xml` or as system properties
- When adding new JNDI properties to any service, update JNDI-CONFIGURATION.md

## Testing

**Backend tests:**
```bash
mvn test
```

**Mobile tests:**
```bash
cd phoneblock_mobile
flutter test
flutter analyze
```

**Manual testing:**
- Web app: Run with `mvn jetty:run`, access http://localhost:8080/phoneblock
- Answer bot: Use `test-prefix` configuration to force bot response for specific numbers
- Mobile: Use Android emulator or device with debugging enabled

## Deployment

**Web application:**
- Builds to `phoneblock/target/phoneblock-*.war`
- Deploy to Tomcat or servlet container
- Embeds answer bot UI automatically during build

**Answer bot:**
- Standalone JAR or Docker image
- Requires network access to Fritz!Box registrar
- Port forwarding may be needed for remote deployments

**Mobile app:**
- APK for direct installation
- App Bundle for Google Play Store
- Requires signing with production keys

## Documentation

- **RELEASE-NOTES.md** - Comprehensive release history since version 1.7.0
- **INTEGRATIONS.md** - Third-party API integration guide
- **JNDI-CONFIGURATION.md** - JNDI configuration reference for all services
- **OpenAPI Spec** - `phoneblock/src/main/webapp/api/phoneblock.json`

## Notes

- Java compiler parameter names must be enabled for MyBatis (`maven.compiler.parameters=true`)
- IP2Location databases downloaded automatically during build
- Logging via slf4j → tinylog
- All timestamps stored as Unix milliseconds (long)
- Phone numbers normalized and stored with international prefix
