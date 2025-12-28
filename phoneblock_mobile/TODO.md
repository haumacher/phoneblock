# PhoneBlock Mobile - Implementation TODO

This document outlines the implementation plan for completing the PhoneBlock Mobile app workflow.

## Current State

The app currently has:
- Basic OAuth token flow with phoneblock.net (working)
- CallScreeningService that queries PhoneBlock API and blocks spam calls
- Prototype UI with setup buttons
- Mock call data display (MyHomePage - not currently used)

## Implementation Tasks

### 1. Setup Flow & Permission Management

**Goal**: Create a guided setup process that checks requirements on each app startup.

- [ ] Create a setup state checker that verifies on startup:
  - [ ] Check if auth token exists and is valid
  - [ ] Check if ROLE_CALL_SCREENING permission is granted
  - [ ] Store setup completion status in SharedPreferences
- [ ] Design setup wizard UI with steps:
  - [ ] Welcome screen explaining app purpose
  - [ ] PhoneBlock account connection step (existing OAuth flow)
  - [ ] Call screening permission request step
  - [ ] Completion screen confirming setup success
- [ ] Add visual indicators for each setup step (checkmarks/progress)
- [ ] Handle setup re-verification on each app launch
- [ ] Navigate to main screen only when all requirements fulfilled

**Files to modify**:
- `lib/main.dart` - Add setup state management and routing logic
- `android/app/src/main/java/de/haumacher/phoneblock_mobile/MainActivity.java` - Add method to check permission status

---

### 2. Screened Calls Storage & Persistence

**Goal**: Store screened call information locally so it can be displayed to users.

- [ ] Extend CallChecker to persist screening results:
  - [ ] Create database/storage schema for screened calls (consider using sqflite or shared_preferences)
  - [ ] Store each screened call with: phone number, timestamp, screening decision (SPAM/legitimate), votes count
  - [ ] Add MethodChannel method to report screened calls from Java to Flutter
- [ ] Modify CallChecker.java to send screening results to Flutter side:
  - [ ] After blocking a call, send result to Flutter via MethodChannel
  - [ ] After accepting a call, send result to Flutter via MethodChannel
- [ ] Implement data model updates:
  - [ ] Extend Call model in state.dart if needed for screening metadata
  - [ ] Create ScreenedCall model if separate from existing Call model

**Files to create/modify**:
- `android/app/src/main/java/de/haumacher/phoneblock_mobile/CallChecker.java` - Add result reporting
- `lib/main.dart` - Add MethodChannel listener for screening results
- `lib/state.dart` - Add/extend data models
- Consider: `lib/storage.dart` - New file for persistence layer

---

### 3. Main Screen - Screened Calls List

**Goal**: Display a list of recently screened calls with their screening results.

- [ ] Create main screen UI:
  - [ ] Design list item showing: phone number, timestamp, SPAM/legitimate badge, votes count
  - [ ] Use color coding (red for SPAM, green for legitimate)
  - [ ] Show screening decision prominently
  - [ ] Sort by most recent first
- [ ] Load screened calls from storage on app launch
- [ ] Update list in real-time when new calls are screened
- [ ] Handle empty state (no screened calls yet)

**Files to modify**:
- `lib/main.dart` - Replace TestPhoneBlockMobile/MyHomePage with new main screen
- Reuse existing UI components from MyHomePage where applicable

---

### 4. Call Actions - Remove from List

**Goal**: Allow users to remove individual calls from the screened list.

- [ ] Implement swipe-to-dismiss functionality:
  - [ ] Use Dismissible widget (already present in prototype)
  - [ ] Add confirmation dialog before deletion (optional but recommended)
  - [ ] Remove from local storage
  - [ ] Update UI to reflect removal
- [ ] Add alternative removal option:
  - [ ] Long-press menu or action button
  - [ ] Consistent with swipe-to-dismiss

**Files to modify**:
- `lib/main.dart` - Add dismiss handlers and storage deletion

---

### 5. Call Actions - Create Contact

**Goal**: Allow users to create a new contact from a screened number.

- [ ] Implement "Create Contact" action:
  - [ ] Add button/menu item in call list item
  - [ ] Use flutter_contacts package to create new contact
  - [ ] Pre-fill phone number
  - [ ] Allow user to edit name and other details
  - [ ] Handle permission requirements (already has WRITE_CONTACTS)
- [ ] Provide feedback on success/failure

**Files to modify**:
- `lib/main.dart` - Add contact creation logic
- Reuse existing flutter_contacts integration

---

### 6. Call Actions - Report as SPAM

**Goal**: Allow users to report numbers as SPAM to the PhoneBlock database.

- [ ] Implement SPAM reporting flow:
  - [ ] Show RateScreen (already exists) to select spam category
  - [ ] Create API endpoint integration for reporting
  - [ ] Send report to PhoneBlock API with auth token
  - [ ] Handle API response (success/error)
- [ ] Update UI after successful report:
  - [ ] Show confirmation message
  - [ ] Update call item to show "reported" status
  - [ ] Consider storing report status locally
- [ ] Determine correct API endpoint:
  - [ ] Research PhoneBlock API documentation for spam reporting
  - [ ] Implement HTTP POST with Rating and phone number

**Files to modify**:
- `lib/main.dart` - Add report action and API integration
- `lib/state.dart` - May need to add report-related models
- Reuse existing RateScreen component

---

### 7. Call Actions - Search on PhoneBlock Website

**Goal**: Allow users to view detailed information and comments about a number on phoneblock.net.

- [ ] Implement "View on PhoneBlock" action:
  - [ ] Add button/menu item in call list item
  - [ ] Construct phoneblock.net URL for the specific phone number
  - [ ] Use url_launcher to open in browser
  - [ ] Use correct URL format (test vs production based on contextPath)
- [ ] Handle URL launch errors gracefully

**Files to modify**:
- `lib/main.dart` - Add URL construction and launch logic
- Reuse existing url_launcher integration

---

### 8. Clear All Screened Calls

**Goal**: Provide option to clear the entire screened calls list.

- [ ] Add "Clear All" functionality:
  - [ ] Add menu option in app bar or settings
  - [ ] Show confirmation dialog before clearing
  - [ ] Clear all screened calls from storage
  - [ ] Update UI to empty state
- [ ] Consider partial clearing options:
  - [ ] Clear only SPAM calls
  - [ ] Clear only legitimate calls
  - [ ] Clear calls older than X days

**Files to modify**:
- `lib/main.dart` - Add clear all action and dialog

---

### 9. Settings & Configuration

**Goal**: Allow users to configure app behavior.

- [ ] Create settings screen:
  - [ ] Minimum votes threshold for blocking (currently hardcoded to 4)
  - [ ] Option to re-run setup wizard
  - [ ] Account management (view auth status, logout)
  - [ ] About/version information
- [ ] Add settings button to main screen app bar
- [ ] Persist settings in SharedPreferences
- [ ] Update CallChecker to read settings from SharedPreferences

**Files to create/modify**:
- `lib/settings.dart` - New settings screen
- `lib/main.dart` - Add navigation to settings
- `android/app/src/main/java/de/haumacher/phoneblock_mobile/MainActivity.java` - Expose settings management methods

---

### 10. UI Polish & Error Handling

**Goal**: Ensure smooth user experience and handle edge cases.

- [ ] Add loading indicators for async operations
- [ ] Implement proper error handling:
  - [ ] Network errors when contacting PhoneBlock API
  - [ ] Permission denied scenarios
  - [ ] Invalid token/authentication failures
- [ ] Add user-friendly error messages
- [ ] Implement retry mechanisms for failed operations
- [ ] Add pull-to-refresh on main call list
- [ ] Ensure all strings are properly localized (currently German in prototype)
- [ ] Test on various Android versions and screen sizes

**Files to modify**:
- All UI files in `lib/`
- Error handling in both Dart and Java layers

---

### 11. Testing & Documentation

**Goal**: Ensure app reliability and maintainability.

- [ ] Write unit tests for:
  - [ ] Data models serialization/deserialization
  - [ ] Storage operations
  - [ ] API integration logic
- [ ] Write integration tests for:
  - [ ] Setup flow
  - [ ] Call screening integration
  - [ ] Permission handling
- [ ] Update README.md with:
  - [ ] App features and screenshots
  - [ ] Setup instructions for development
  - [ ] Build and release process
- [ ] Update CLAUDE.md with new architecture details

**Files to create/modify**:
- `test/` directory - Add test files
- `README.md` - Update documentation
- `CLAUDE.md` - Update with new architecture

---

## Implementation Order

Recommended implementation sequence:

1. **Setup Flow** (Task 1) - Foundation for the entire app
2. **Screened Calls Storage** (Task 2) - Required for displaying data
3. **Main Screen** (Task 3) - Core user interface
4. **Remove from List** (Task 4) - Simple, completes basic list management
5. **Search on Website** (Task 7) - Simple, uses existing url_launcher
6. **Create Contact** (Task 5) - Medium complexity, uses existing flutter_contacts
7. **Report as SPAM** (Task 6) - Requires API integration
8. **Clear All** (Task 8) - Simple addition after list is working
9. **Settings** (Task 9) - Enhancement feature
10. **UI Polish** (Task 10) - Ongoing throughout, final pass at end
11. **Testing & Documentation** (Task 11) - Final step

## Notes

- Consider using a state management solution (Provider, Riverpod, or Bloc) as the app grows beyond simple StatefulWidgets
- The existing MyHomePage and related UI code can be partially reused/refactored
- Ensure all PhoneBlock API endpoints are documented (may need to coordinate with backend team)
- Consider data retention policy (how long to keep screened call history)
