# PhoneBlock Release Notes

## Version 1.8.7 (In Development)

**Status:** Development

**Key Features:**
- Incremental blocklist synchronization API with version tracking
- Vote count normalization to threshold values (2, 4, 10, 20, 50, 100)
- Blocklist client library and CLI tool in phoneblock-shared
- Comprehensive JNDI configuration documentation
- Rate limiting guidance for API consumers

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
