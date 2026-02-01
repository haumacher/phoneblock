# Fritz!Box Mobile Integration - Feature Specification

**Status:** Draft
**Version:** 0.1
**Last Updated:** 2026-02-01

## Overview

This document specifies the feature for connecting Fritz!Box routers directly to the PhoneBlock mobile app.

## Current State

### Existing Architecture

```
Fritz!Box Router ←──→ PhoneBlock Web Service ←──→ Mobile App
      │                        │
      │  CardDAV sync          │  REST API
      │  Call reports          │  SHA1-based queries
      └────────────────────────┴──────────────────────────
                    (No direct connection)
```

**Fritz!Box Integration (via Web Service):**
- CardDAV synchronization for blocklist contacts
- Users can manually add spam contacts to CardDAV phonebook (cumbersome and error-prone)

**Mobile App Features:**
- Real-time call screening via Android CallScreeningService
- Local call history in SQLite database
- OAuth authentication with PhoneBlock
- Manual spam rating submission

### Gap

Currently, there is no direct connection between the Fritz!Box router and the mobile app. Users must configure each system separately, and there's no unified view of blocked calls from both devices.

---

## Proposed Feature

### Goals

1. **Call Log Display** - Retrieve and display calls from Fritz!Box in the mobile app
   - Show all incoming calls **except** those with a caller name in the Fritz!Box call log
   - Heuristic: If Fritz!Box resolved a name for the caller (from its phonebooks), the call is from a known contact and is filtered out
   - **Exception:** Calls with a name starting with `"SPAM: "` are **not filtered** - these are blocklist entries from CardDAV sync and should be displayed
   - This approach avoids reading the user's address book for privacy
   - Display spam status from PhoneBlock database for each call
   - Indicate which calls were blocked vs. rang through
2. **Simplified Setup** - Provide easy one-tap setup for PhoneBlock integration with three user-selectable options:
   - **Option A: CardDAV Blocklist Only** - Configure a CardDAV phonebook connected to PhoneBlock and create a call blocking rule
   - **Option B: Answer Bot Only** - Automatically register a PhoneBlock Answer Bot as a SIP device on the Fritz!Box
   - **Option C: Both (Recommended)** - Set up both CardDAV blocklist AND Answer Bot. This shows "SPAM:" markers in local phone call logs while also engaging spam callers with the bot

### Connectivity Mode

**Local Network Only** - The feature will only work when the mobile device is connected to the same local network (WiFi) as the Fritz!Box router.

### Authentication

**PhoneBlock Account:** Required (existing app requirement - no change)

**Fritz!Box Credentials - Two-tier approach:**
1. **Initial Setup (Admin):** User enters Fritz!Box admin credentials once during setup. These are used to:
   - Create the PhoneBlock app user on the Fritz!Box
   - Configure CardDAV phonebook and/or Answer Bot
   - Admin credentials are NOT stored after setup completes

2. **Ongoing Access (App User):** The app creates a dedicated Fritz!Box user with restricted permissions:
   - Only has access to read the call log
   - Credentials stored securely on the device
   - Used for all subsequent call log fetches

### Answer Bot Details

The Answer Bot option registers the Fritz!Box to use **PhoneBlock's hosted cloud answer bot service**:
- Requires external access to Fritz!Box for SIP connections
- PhoneBlock service registers as a SIP device on the Fritz!Box
- Spam calls are forwarded to the cloud answer bot
- Bot engages callers with automated conversation

**External Access Discovery (priority order):**
1. **MyFritz** - Check if MyFritz is already configured. If yes, use the MyFritz domain name.
2. **Existing Dynamic DNS** - Check if another dynamic DNS provider is configured. If yes, use that domain.
3. **PhoneBlock Dynamic DNS** - If neither is available, automatically configure Fritz!Box to use PhoneBlock's dynamic DNS service.

**Server-side API:** Already available via the embedded Answer Bot UI (`phoneblock_answerbot_ui`). The mobile app can reuse this existing infrastructure for Fritz!Box registration.

### Non-Goals

- Remote Fritz!Box management via internet (call log requires local network)
- Multiple Fritz!Box support (initial version)
- Running answer bot on the mobile device itself

---

## Feature Summary

This feature combines **call log display** with **simplified setup** for Fritz!Box protection:

| Capability | Description |
|------------|-------------|
| Call Log | Retrieve and display all incoming calls (except known contacts) from Fritz!Box |
| Merged Timeline | Show Fritz!Box and mobile calls together with source indicator |
| Offline Cache | View previously synced calls when away from home network |
| Full Rating | Rate Fritz!Box calls with same spam categories as mobile |
| CardDAV Setup | One-tap configuration of PhoneBlock blocklist sync |
| Answer Bot Setup | One-tap registration of PhoneBlock cloud answer bot |
| Combined Setup | Both CardDAV + Answer Bot (recommended for SPAM markers in call logs) |
| App User | Automatic creation of restricted Fritz!Box user for secure ongoing access |

---

## Technical Considerations

### TR-064 Protocol

Fritz!Box exposes management APIs via TR-064 (SOAP-based protocol):
- Requires local network access
- Authentication with Fritz!Box credentials
- Can retrieve call lists, phonebook entries, network status

### Security

- Fritz!Box credentials must be stored securely
- Local network discovery requires appropriate permissions
- Consider mDNS/UPnP for device discovery

### Platform Constraints

- Android: Background service limitations
- Network permissions required
- Local network access may require user approval (Android 10+)

### Fritz!Box Requirements

- **Minimum FRITZ!OS: 7.20** - Required for CardDAV sync with custom servers (PhoneBlock)
- TR-064 protocol supported from the beginning
- Note: CardDAV sync from server occurs once every 24 hours (midnight); changes to Fritz!Box are immediate

---

## User Stories

### Setup Stories

**US-1: First-time Fritz!Box Setup**
> As a user, I want to connect my Fritz!Box to PhoneBlock through the mobile app so that I can protect my landline from spam calls.

**US-2: Choose CardDAV Protection**
> As a user, I want to set up CardDAV blocklist sync so that my Fritz!Box automatically blocks known spam numbers.

**US-3: Choose Answer Bot Protection**
> As a user, I want to register the PhoneBlock Answer Bot so that spam callers are engaged by an automated system instead of bothering me.

**US-3a: Choose Combined Protection (Recommended)**
> As a user, I want to set up both CardDAV blocklist and Answer Bot so that I see SPAM markers in my phone's call log AND spam callers are engaged by the bot.

**US-3b: Secure App Access**
> As a user, I want the app to create a dedicated Fritz!Box user with minimal permissions so that my admin credentials are not stored and ongoing access is secure.

### Call History Stories

**US-4: View Unified Call History**
> As a user, I want to see calls from both my mobile and Fritz!Box in one timeline so that I have a complete view of spam activity.

**US-5: Identify Call Source**
> As a user, I want to see which device received each call so that I know if it was my mobile or landline.

**US-6: View Cached Calls Offline**
> As a user, I want to see my Fritz!Box call history even when away from home so that I can review recent calls.

### Rating Stories

**US-7: Rate Fritz!Box Calls**
> As a user, I want to rate spam calls received on my Fritz!Box so that I can contribute to the community database.

**US-8: Block Number from Fritz!Box Call**
> As a user, I want to block a number directly from a Fritz!Box call entry so that future calls are blocked on both devices.

---

## Technical Implementation

### Fritz!Box Communication (TR-064)

The app communicates with Fritz!Box using the **TR-064 protocol** (SOAP over HTTP/HTTPS).

**Library:** Use existing Dart implementation: [github.com/haumacher/fritz_tr64](https://github.com/haumacher/fritz_tr64)

**Required TR-064 Services:**
- `X_AVM-DE_OnTel` - Call list retrieval
- `X_AVM-DE_TAM` - Answering machine (for answer bot registration)
- `X_AVM-DE_Phonebook` - Phonebook management (for CardDAV setup)
- `LANConfigSecurity` - User management (**needs implementation in fritz_tr64 library**)

**Key Operations:**
```
GetCallList() → Returns URL to call list XML
GetPhonebookList() → List configured phonebooks
SetPhonebook() → Create/configure phonebook
SetDeflection() → Configure call blocking rules
AddUser() → Create app-specific user with restricted permissions
GetMyFritzInfo() → Check if MyFritz is configured, get domain
GetDynDnsInfo() → Check existing dynamic DNS configuration
SetDynDns() → Configure PhoneBlock dynamic DNS provider
```

### Data Model Changes

**New SQLite table: `fritzbox_calls`**
```sql
CREATE TABLE fritzbox_calls (
  id INTEGER PRIMARY KEY,
  fritzbox_id TEXT,           -- Fritz!Box unique call ID
  phone_number TEXT,
  timestamp INTEGER,          -- Unix timestamp
  duration INTEGER,           -- Call duration in seconds
  call_type INTEGER,          -- 1=incoming, 2=missed, 3=outgoing, 10=blocked
  device TEXT,                -- Which phone answered
  votes INTEGER,              -- PhoneBlock votes (fetched separately)
  rating TEXT,                -- User's spam rating if submitted
  synced_at INTEGER           -- When this record was fetched
);
```

**Filtering Logic:** During sync, calls are filtered based on caller name:
- **No name** → Include (unknown caller)
- **Name starts with "SPAM: "** → Include (blocklist entry from CardDAV)
- **Other name** → Exclude (known contact from user's phonebook)

**New SQLite table: `fritzbox_config`**
```sql
CREATE TABLE fritzbox_config (
  id INTEGER PRIMARY KEY,
  host TEXT,                  -- Fritz!Box hostname/IP
  app_username TEXT,          -- App user created during setup (encrypted)
  app_password TEXT,          -- App user password (encrypted)
  protection_mode TEXT,       -- 'carddav', 'answerbot', or 'both'
  last_fetch_timestamp INTEGER, -- Timestamp of newest call from last fetch (for incremental sync)
  phonebook_id TEXT,          -- CardDAV phonebook ID if configured
  sip_device_id TEXT          -- Answer bot SIP device ID if configured
);
```

**Note:** Admin credentials are NOT stored. Only the app-specific user credentials (with restricted call log access) are persisted.

### New Dependencies

```yaml
dependencies:
  fritz_tr64:
    git:
      url: https://github.com/haumacher/fritz_tr64
```

### Security Considerations

- **Admin credentials never stored** - Used only during initial setup, then discarded
- **App user credentials** stored using Android Keystore encryption
- **Restricted permissions** - App user only has call log read access
- TR-064 communication over HTTPS when available
- No credentials transmitted to PhoneBlock servers
- Local network verification before attempting connection

---

## API Changes

### PhoneBlock Server API

**Existing APIs** (no changes required):
- `GET /api/check?sha1={hash}` - Check spam status for Fritz!Box calls
- `POST /api/rate` - Submit ratings for Fritz!Box calls
- Answer bot registration - Existing API from embedded Answer Bot UI (`phoneblock_answerbot_ui`)

**Potential new API** (if PhoneBlock Dynamic DNS doesn't exist yet):
- Dynamic DNS update endpoint for Fritz!Box to report its external IP
- Domain name assignment for new Fritz!Box registrations

---

## UI/UX Design

### Call History View

**Merged Timeline** - A single chronological list showing calls from both sources:
- Mobile-screened calls (existing functionality)
- Fritz!Box calls (new)

Each call entry displays:
- Phone number and location info
- Timestamp
- Spam status/votes from PhoneBlock
- **Source indicator** showing where the call was received:
  - Mobile device icon for calls screened by the app
  - Fritz!Box/router icon for calls received on landline
- Block status (blocked, missed, answered)

### Offline Behavior

When not connected to the home network:
- Display previously synced Fritz!Box calls from local cache
- Mark cached data with "last synced" timestamp
- Indicate connection status (e.g., "Fritz!Box: offline - showing cached data")
- Automatically refresh when reconnecting to home network

### Setup Wizard

**Guided step-by-step flow:**

1. **Welcome / Detection**
   - Detect if on a network with Fritz!Box
   - Display found Fritz!Box device info
   - Manual entry option if auto-detection fails

2. **Admin Login**
   - Enter Fritz!Box admin credentials (one-time use)
   - Test connection and validate credentials
   - Explain that admin access is only needed for initial setup

3. **Choose Protection Method**
   - **Option A: CardDAV Blocklist Only**
     - Creates PhoneBlock CardDAV phonebook on Fritz!Box
     - Sets up call blocking rule for numbers in blocklist
   - **Option B: Answer Bot Only**
     - Registers PhoneBlock cloud answer bot as SIP device
     - Configures call forwarding for spam numbers to bot
   - **Option C: Both (Recommended)**
     - Sets up CardDAV blocklist AND Answer Bot
     - Shows "SPAM:" markers in local phone call logs
     - Spam callers are engaged by the bot
   - Default selection: Option C

4. **App User Creation**
   - App automatically creates a dedicated Fritz!Box user "PhoneBlock"
   - User has read-only access to call log
   - Credentials generated and stored securely
   - Admin credentials discarded after this step

5. **Configuration**
   - Method-specific settings
   - For CardDAV: sync interval, phonebook name
   - For Answer Bot: External access setup (automatic)
     1. Check for existing MyFritz → use if available
     2. Check for existing Dynamic DNS → use if available
     3. Otherwise → configure PhoneBlock Dynamic DNS automatically

6. **Confirmation**
   - Summary of configured settings
   - Test call option (optional)
   - Done - return to main app

### Call Rating

Users can rate Fritz!Box calls with the **same spam categories** as mobile calls:
- LEGITIMATE, PING, POLL, ADVERTISING, GAMBLE, FRAUD
- Ratings are submitted to PhoneBlock database
- Contributes to community spam detection

### Sync Behavior

**On app open only** - No background sync:
- Call log syncs automatically when the app is opened
- Requires active connection to home network
- Pull-to-refresh available for manual sync
- No battery drain from background processes

**Incremental Fetching:**
- App tracks the timestamp of the last successful fetch
- On sync, only fetches calls that arrived after the last fetch timestamp
- Reduces data transfer and processing time
- Initial sync fetches calls matching the app's **call history retention period** (default: 3 days, configurable: 1/3/7 days or infinite)

---

## Implementation Phases

### Phase 1: Fritz!Box Connection & Call Log

**Scope:**
- Fritz!Box detection and connection
- Admin credential entry (temporary)
- App user creation with restricted permissions
- App user credential secure storage
- TR-064 call list retrieval
- Display Fritz!Box calls in merged timeline
- Source indicator (mobile vs. Fritz!Box)
- Offline caching of call history

**Deliverables:**
- Fritz!Box settings screen
- Connection wizard (steps 1-2, 4)
- App user creation via TR-064
- Modified call history UI with source indicators
- Local SQLite schema for Fritz!Box data

### Phase 2: CardDAV Setup

**Scope:**
- Create PhoneBlock CardDAV phonebook on Fritz!Box
- Configure blocking rule for phonebook
- Display setup status and sync info
- Support standalone or combined with Answer Bot

**Deliverables:**
- CardDAV configuration wizard (step 3, 5, 6)
- Phonebook creation via TR-064
- Blocking rule configuration
- Status display in settings

### Phase 3: Answer Bot Registration

**Scope:**
- Detect external access method (MyFritz → existing DynDNS → PhoneBlock DynDNS)
- Configure PhoneBlock Dynamic DNS if needed
- Register PhoneBlock answer bot as SIP device
- Configure call forwarding for spam numbers
- Support standalone or combined with CardDAV (recommended)

**Deliverables:**
- Answer bot setup wizard (step 3, 5, 6)
- External access detection and configuration
- PhoneBlock Dynamic DNS integration
- SIP device registration via TR-064
- Server-side answer bot provisioning integration
- Call forwarding rule configuration
- Combined setup flow (both CardDAV + Answer Bot)

### Phase 4: Rating & Reporting

**Scope:**
- Rate Fritz!Box calls with spam categories
- Submit ratings to PhoneBlock
- Block numbers from Fritz!Box call entries

**Deliverables:**
- Rating UI for Fritz!Box calls
- API integration for rating submission
- Quick-block action

---

## Open Questions (Resolved)

1. ~~**Minimum Fritz!Box firmware version**~~ **RESOLVED: FRITZ!OS 7.20** required for CardDAV sync with custom servers. TR-064 supported from the beginning.

2. ~~**Multiple phones on Fritz!Box**~~ **RESOLVED:** Fritz!Box has a common call log for all connected phones - no special handling needed.

3. ~~**Initial sync limit**~~ **RESOLVED:** Use the app's configured **call history retention period** (default: 3 days, options: 1/3/7 days or infinite). This keeps Fritz!Box sync consistent with mobile call retention.

4. **User creation API** - **NEEDS IMPLEMENTATION:** The `fritz_tr64` library does not yet support user creation. The `LANConfigSecurity` TR-064 service (which handles user management) is listed as "not yet implemented" in the library. This service needs to be added to support creating the restricted PhoneBlock app user.

5. ~~**PhoneBlock Dynamic DNS**~~ **RESOLVED:** Already fully implemented and available for manual Answer Bot setup in the app.

---

## Appendix

### References

- [TR-064 Protocol Specification](https://avm.de/service/schnittstellen/)
- [Fritz!Box API Documentation](https://avm.de/fileadmin/user_upload/Global/Service/Schnittstellen/)
- **[fritz_tr64 Dart Library](https://github.com/haumacher/fritz_tr64)** - TR-064 implementation to use for this feature
- PhoneBlock CardDAV Implementation: `phoneblock/src/main/java/de/haumacher/phoneblock/carddav/`
- PhoneBlock Answer Bot UI: `phoneblock_answerbot_ui/`
- Mobile App: `phoneblock_mobile/`
