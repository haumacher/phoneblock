# PhoneBlock Release Notes

## Version 3.0.0 (2026-05-19)

**Key Features:**
- **PhoneBlock Dongle** — ESP32-based standalone SIP spam blocker that registers with the Fritz!Box, blocks community-rated spam calls, supports OTA firmware/announcement updates, task watchdog with panic-on-timeout, and automatic coredump upload after panic
- **"Login with PhoneBlock" SSO** — OAuth-style flow with one-shot login tickets, `/auth/gate` for browser auto-login, trust-on-first-use, and registered redirect targets for SpamBlocker and PhoneBlock Dongle apps
- **Personal blacklist/whitelist redesign** — split onto dedicated `/blacklist` and `/whitelist` pages with community vote counts, inline rate/comment/edit, and filtering of archived numbers
- **Disposable e-mail detection** — extracted `fake-mail-check` module with MX-based heuristic, daily import of public blocklists, active scrapers for MailTicking, SmailPro, Emailnator, temp-mail.org, emailfake.com, Mohmal, and a Chrome harvester extension; providers loaded dynamically from JNDI
- **AI comment classifier and summarizer** — replaces ChatGPT summary service with the Anthropic Java SDK using structured outputs; adds `COMMENTS.CLASSIFICATION` and hides BAD comments

**API:**
- Privacy-preserving k-anonymity prefix lookup `/api/check-prefix` (#280); bearer-token only, prefixes restricted to even length
- `/api/report-call` endpoint for blocklist-tailoring activity stats; quota tightened to 20/day
- `/check`, `/num`, `/check-prefix` return the user's own comment (#301)
- Wildcard ranges in `/check-prefix` filtered by aggregation threshold (#315)
- Personal blacklist/whitelist applied in `/api/num/` and prefix overlays
- `PhoneInfo.archived` reflects ACTIVE state correctly; range10/100 prefixes returned in E.164

**Web App:**
- New `/festnetz` decision-tree wizard consolidating landline setup options; user-agent-aware homepage platform card highlight
- CardDAV performance: two-layer assembly with personal dedup, content-based ETags, `If-None-Match` in PROPFIND/REPORT, StAX render pipeline, Depth-0 lightweight PROPFIND on the address-book URL, serving from the published snapshot
- Stats page: daily growth in tooltips and current-day totals, viewport-scaled chart height, default-hidden non-primary datasets, friendly user-agent labels
- 301-redirects strip `;jsessionid=` path parameters
- Per-page SEO titles and descriptions
- Canonical URLs and sitemap entries use internationalized phone numbers
- Comments section moved above the rating form on the number-info page

**Auth:**
- Wrong-account login loop broken via `user_hint` on `/auth/gate` and one logout-retry
- `/auth/*` exempt from proof-of-work; loopback callbacks accept any single-label hostname

**Mobile App (1.3.1):**
- Diagnostic log viewer with share + clear, native + Dart crash handler, hashed-number sanitization (#282)
- Background-isolate log bridge as a local Flutter plugin; Logback rolling appender, SLF4J logging
- Single-digit country code support in wildcard blocking
- Refresh cached settings after display-name / e-mail change (#279)
- Fix blocklist background sync via Flutter `SharedPreferences`
- Fix null `Uri` in `CallChecker.onScreenCall`

**Answer Bot:**
- Respect personal blacklist and whitelist in the call filter

**Tools:**
- `phonebook-sync` CLI to compare a Fritz!Box phonebook against the blocklist
- `fake-mail-check` standalone CLI with MX resolution, versioned schema migrations, and import/scrape/resolve-mx commands

**Database:** Schema version 24. Migrations 21–24 cover `SOURCE_SYSTEM` string IDs, `DOMAIN_CHECK.STATUS` enum, `MX_HOST_STATUS`, and `COMMENTS.CLASSIFICATION`.

**Build:**
- Maven dependency versions consolidated in the parent POM
- `mjSIP` resolved from JitPack instead of GitHub Packages
- `Messages_*.properties` translation wired into the POM via `tl-maven-plugin`
- `.phoneblock` configuration is now validated rather than silently skipped

## Version 2.0.0 (2026-03-14)

**Key Features:**
- FTC Do Not Call data import: US spam numbers from FTC complaints integrated into main database (DB migrations 19+20)
- Statistics page (`/stats`) with three charts:
  - User registration growth with per-country breakdown (top 10 dial prefixes)
  - Active installations by user agent prefix (top 5) plus registered answerbots
  - Blocked numbers by country (pie chart)

**Improvements:**
- Centralized dependency management in parent POM
- Updated dependencies to latest minor/patch versions

**Bug Fixes:**
- Fixed missing spam number counts on status page (statistics map key mismatch)

## Version 1.9.4 (2026-03-08)

**Key Features:**
- Created timestamp tracking for personal blacklist/whitelist entries (DB migration 18)
- `blackListed` field in PhoneInfo API response for personal blocklist detection (#266)

**Improvements:**
- Return real community votes for personally blocked numbers
- Include label and location in all API responses (personally blocked, whitelisted, non-spam numbers) (#270)
- Updated OpenAPI spec to document all API response fields

**Bug Fixes:**
- Fixed missing example values removed from prefix hash parameters in API spec

## Version 1.9.3 (2026-03-07)

**Key Features:**
- Prefix hash lookup for range-based spam detection in /api/check (#254)
- Personal block/whitelist resolution by SHA1 hash in spam check API
- Per-bot "Accept Local Calls" setting for answer bots (#131)
- Last-activity date tracking for blocklist entries (#246)
- Privacy protection: hide details for positively-rated numbers with few votes (#248)
- EPC QR code on bank transfer donation page for easy payment (#261)

**Improvements:**
- Simplified vote normalization to single minimum threshold with published votes
- Link "Fehler melden" to issues overview instead of new issue form

**Bug Fixes:**
- Fixed missing incremental update for archived SPAM reports (#250)
- Fixed umlaut encoding in API requests (#247)

## Version 1.9.2 (2026-02-01)

**Improvements:**
- Handle malformed auth tokens gracefully with better error logging
- Skip credentials page for mobile OAuth flow to match email flow
- Disable URL-based session tracking to prevent jsessionid in URLs
- Centralize language/locale resolution with default fallback

**Bug Fixes:**
- Fixed password not shown during account setup
- Fixed OGNL exception when navigating directly to /show-api-key
- Fixed location parameter loss in OAuth flow
- Fixed appId parameter loss during mobile login flow
- Preserved location when returning from failed captcha in mobile login

## Version 1.9.1 (2026-01-31)

**Bug Fixes:**
- Fixed blocklist incremental sync with `since=0` returning zero-vote entries

## Version 1.9.0 (2026-01-31)

**Key Features:**
- Incremental blocklist synchronization API with version tracking
- Vote count normalization to threshold values (2, 4, 10, 20, 50, 100)
- Blocklist client library and CLI tool in phoneblock-shared
- Comprehensive JNDI configuration documentation
- Rate limiting guidance for API consumers

**Bug Fixes:**
- Fixed incremental blocklist sync with `since=0` returning zero-vote entries

## Version 1.8.6 (2026-01-23)

**Changes:**
- Fixed potential null pointer exception with dial prefix handling

## Version 1.8.5 (2026-01-23)

**Changes:**
- Fixed character encoding issues in email and web interfaces
- Improved invalid phone number handling in blocklist
- Enhanced error handling for malformed phone numbers in database

## Version 1.8.4 (2026-01-23)

**Changes:**
- Fixed personalization API access (no login token required for authenticated users)
- Standardized on international phone number format across all APIs

## Version 1.8.3 (2026-01-19)

**Major Features:**
- User display name editing functionality
- Email address change capability
- Improved phone number parsing for international formats

**Improvements:**
- Enhanced help messages for invalid phone numbers
- Better localization coverage for error messages and emails
- Fixed trunk prefix handling for Italy and Russia
- Improved phone number display formatting for countries with non-standard prefixes

**Technical:**
- Added trunk prefix data from ITU specifications
- Switched to CSV-based country data with regex support
- Improved URL normalization for POST requests

## Version 1.8.2 (2026-01-17)

**Major Features:**
- New landing page and welcome page design
- Token-based CardDAV synchronization
- App registration system for third-party integrations
- Improved onboarding flow with device-aware routing

**Mobile App:**
- Dark mode support
- PlayStore and App Store links
- Support for PhoneBlock Mobile app

**Documentation:**
- Integration guidelines (INTEGRATIONS.md)
- Improved API documentation
- Docker installation guide for Answer Bot

## Version 1.8.1 (2026-01-09)

**Major Features:**
- API key rename functionality
- Device locale-based page rendering
- Automatic resource translation system using Gradle

**Improvements:**
- Canonical phone number format (00-prefix) in template links
- Help messages for empty black/whitelist displays
- Better language detection and session handling

**Mobile App:**
- Updated to latest dependencies
- Improved resource management

**Technical:**
- Auto-translate plugin integration
- Text checksum generation for translation tracking
- Simplified language indexing

## Version 1.8.0 (2026-01-02)

**Major Features:**
- PhoneBlock Mobile app support with comprehensive integration
- Personal black/whitelist management API
- Account management API for locale synchronization
- Phone number search functionality

**API Enhancements:**
- `/api/blacklist` and `/api/whitelist` endpoints
- `/api/account` for settings management
- Bearer token authentication support throughout
- OpenAPI documentation updates

**Mobile App Features:**
- Call history with configurable retention (default 3 days)
- Privacy-preserving SHA1 phone number hashing
- Swipe-to-dismiss and swipe-to-report gestures
- Number range blocking
- Context menus and rating system
- Notification system for blocked calls
- Setup wizard with OAuth integration

**Email System:**
- Welcome emails for new app users
- Email template localization system
- Plain text email generation from HTML templates
- DKIM signing support

**User Interface:**
- New usage documentation pages
- Updated setup instructions with visual guides
- Consistent internationalization across all pages
- Mobile-optimized views

**Technical:**
- Fixed async context usage patterns
- Improved error reporting in servlets
- Enhanced phone number normalization
- Country code to dial prefix mapping

## Version 1.7.16 (2025-12-28)

**Mobile App Improvements:**
- Complete internationalization (German and English)
- App icon badge counter for filtered calls
- Persistent notifications for blocked SPAM
- Number range blocking using wildcard votes
- Settings screen with configurable thresholds
- WebView integration for number details
- Swipe gestures for call management

**Features:**
- Report numbers as legitimate
- Context menus for call actions
- Real-time call list updates
- Setup wizard improvements
- OAuth token validation

**Bug Fixes:**
- BuildContext async gap warnings eliminated
- Deprecation warnings resolved
- Back gesture navigation fixed

**Documentation:**
- Added CLAUDE.md for AI-assisted development
- Comprehensive TODO.md for workflow completion

## Version 1.7.15 (2025-11-08)

**Changes:**
- Expanded "good bot" list
- Reduced false positives for firewall blocklist

## Version 1.7.14 (2025-11-04)

**Changes:**
- Added Qwantbot to allowed bot list
- Refined proof-of-work requirements

## Version 1.7.13 (2025-11-04)

**Changes:**
- Proof-of-work challenge for Answer Bot main page
- Reduced excessive login request logging

## Version 1.7.12 (2025-11-04)

**New Features:**
- UFW firewall integration for bot management
- Watchdog service for blocking malicious bot access
- Continuous log monitoring capability

**Bug Fixes:**
- Excluded Answer Bot resources from proof-of-work
- Fixed log tailing to process only new entries

## Version 1.7.11 (2025-11-03)

**Changes:**
- Added ChatGPT-User to allowed bot list
- Improved email accessibility (alt attributes)
- UI enhancements for button layout

## Version 1.7.10 (2025-11-02)

**New Features:**
- Proof-of-work system for bot protection
- Good bot vs bad bot differentiation
- Mobile login page templates with translations

**Improvements:**
- Excluded CardDAV from proof-of-work checks
- Session management improvements
- Bot pattern updates (Barkrowler, Nutch)

**Bug Fixes:**
- Fixed proof-of-work computation
- Prevented page reload failures
- Build system updates

## Version 1.7.9 (2025-11-02)

**Changes:**
- Added link to phoneblock-for-3cx project
- Translation updates

## Version 1.7.8 (2025-09-29)

**Features:**
- Configurable welcome email system
- Help email throttling (3-day delay)

**Technical:**
- Build system improvements
- Plugin updates

## Version 1.7.7 (2025-06-27)

**Features:**
- Added numbering plans for Austria and Italy
- New web resource download tools

**Improvements:**
- Better handling of numbers exceeding plan limits
- Enhanced robot detection (meta-externalagent, Owler)
- Configuration improvements for first-time setup

**Bug Fixes:**
- Fixed domain name configuration
- Adjusted numbering plan validation for German reserved prefixes

## Version 1.7.6 (2025-05-02)

**Features:**
- DeepL translation integration
- Page translation links

**Improvements:**
- Better English translations
- User-specific dial prefix in whitelist management
- Local number format display with tooltips

**Bug Fixes:**
- Fixed Italian translation for "Save" (Issue #168)

## Version 1.7.5 (2025-05-01)

**Changes:**
- Status page improvements with shortcut numbers
- Updated mail parser
- Fixed Italian translation for legitimate calls (Issue #167)

## Version 1.7.4 (2025-04-28)

**Features:**
- International phone number format support
- Deep link support for mobile app

**Improvements:**
- User dial prefix used for search and personalization
- International format for address book cache and search results

**Bug Fixes:**
- Fixed missing Fritz!Box search translation (Issue #165)
- Fixed dark mode compatibility (Issue #158)
- Fixed phone summary direction (Issue #162)
- Fixed error page display (Issue #153)

**Dependencies:**
- Upgraded to ip2location-java 8.12.4

---

## Release Frequency

PhoneBlock follows a continuous deployment model with frequent releases:

- **Major releases** (X.Y.0): New features, API changes, significant improvements
- **Minor releases** (X.Y.Z): Bug fixes, translations, small enhancements
- **Development snapshots**: Active development between releases

## Getting Updates

- **Production**: https://phoneblock.net/phoneblock/
- **Test Environment**: https://phoneblock.net/pb-test/
- **GitHub**: https://github.com/haumacher/phoneblock
- **Docker Hub**: Available for Answer Bot

## Migration Notes

### Upgrading to 1.8.0+

- Mobile app users: Install PhoneBlock Mobile from app stores
- API consumers: Update to use Bearer token authentication
- Review new `/api/account`, `/api/blacklist`, `/api/whitelist` endpoints

### Upgrading to 1.7.10+

- Web access may require proof-of-work for unauthenticated users
- CardDAV and mobile login excluded from proof-of-work

## Support

- **Issues**: https://github.com/haumacher/phoneblock/issues
- **Documentation**: See INTEGRATIONS.md, JNDI-CONFIGURATION.md, CLAUDE.md
- **API Specification**: https://phoneblock.net/phoneblock/api/phoneblock.json
