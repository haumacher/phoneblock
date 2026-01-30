# Changelog

All notable changes to PhoneBlock Mobile will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Show list of blocked/suspicious calls in notification
- "New" indicator for calls received while app was closed
- Display range votes in call list
- Display formatted phone number and caller location in call list

### Changed
- Improved notifications with PhoneBlock branding and call details
- Skip call log and notification for blocked spam calls
- Show notification for suspicious calls, not just blocked ones
- Improved call list display to show actual rating and blocked status
- Improved answerbot feature description to mention Fritz!Box
- Move server settings from drawer to settings page
- Open donate link in internal WebView instead of external browser

### Fixed
- "Open in App" button on mobile response page for Firefox compatibility
- WebView loading indicator during content loading

## [1.1.0] - 2026-01-25

### Added
- Dark mode support
- AnswerBot management to control your Fritz!Box spam answering machine from the app
- Navigation drawer for easier menu access
- Share phone numbers from other apps to look them up in PhoneBlock

### Changed
- Replaced settings button with navigation drawer
- Open server settings in native browser instead of WebView

## [1.0.5] - 2026-01-09

### Added
- Help message for empty black/white list display
- Webpages displayed in device locale

### Changed
- Improved display for legitimate numbers
- Consistent rating icons in personalized number lists
- Blacklist/whitelist displays user rating
- Better contrast for advertising label (darker orange)

### Fixed
- Back button on setup page only shows when navigation is possible
- Menu options consistent for all call types

## [1.0.4] - 2025-12-31

### Added
- Black/white list management pages in settings
- Phone number search on main screen
- Server settings page
- Edit comments for numbers in black/white lists

### Changed
- Use device SIM country code for dial prefix detection

### Fixed
- UI inconsistency when number removal fails

## [1.0.3] - 2025-12-29

### Added
- Black/white list synchronization with server

## [1.0.2] - 2025-12-29

Initial tracked release.

[Unreleased]: https://github.com/haumacher/phoneblock/compare/pb-mobile-1.1.0...HEAD
[1.1.0]: https://github.com/haumacher/phoneblock/compare/pb-mobile-1.0.5...pb-mobile-1.1.0
[1.0.5]: https://github.com/haumacher/phoneblock/compare/pb-mobile-1.0.4...pb-mobile-1.0.5
[1.0.4]: https://github.com/haumacher/phoneblock/compare/pb-mobile-1.0.3...pb-mobile-1.0.4
[1.0.3]: https://github.com/haumacher/phoneblock/compare/pb-mobile-1.0.2...pb-mobile-1.0.3
[1.0.2]: https://github.com/haumacher/phoneblock/releases/tag/pb-mobile-1.0.2
