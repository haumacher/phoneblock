# EPC QR Code for Bank Transfer Donations

**Issue:** [#261](https://github.com/haumacher/phoneblock/issues/261)

**Goal:** Display an EPC QR code (GiroCode) on the bank transfer donation page so users can scan it with their banking app to initiate a SEPA credit transfer.

## Design Decisions

- **No pre-filled amount** — user chooses their own contribution in the banking app
- **Server-side generation** — QR code rendered as PNG by a Java servlet using Nayuki qrcodegen
- **Per-user reference** — QR code includes the user-specific `supporterId` as remittance text for automatic donation matching
- **No third-party service dependency** — EPC payload generated locally per EPC069-12 standard

## Components

### 1. Maven Dependency

```xml
<dependency>
    <groupId>io.nayuki</groupId>
    <artifactId>qrcodegen</artifactId>
    <version>1.8.0</version>
</dependency>
```

### 2. New Servlet: `EpcQrCodeServlet`

- **URL:** `/support-banktransfer/qr.png`
- Reads bank details from `phoneblock.properties` (`bank.receiver`, `bank.account`, `bank.bic`)
- Reads `supporterId` from logged-in user session
- Builds EPC QR code payload (12-line text, EPC069-12 v002)
- Uses Nayuki `QrCode.encodeText()` with error correction level M
- Renders QR modules to `BufferedImage`, writes as PNG to response
- Returns 401 if user not logged in

### 3. EPC Payload Format

```
BCD
002
1
SCT
{bank.bic}
{bank.receiver}
{bank.account}

CHAR

PhoneBlock-{username13}

```

Fields:
- Service tag: `BCD` (fixed)
- Version: `002`
- Encoding: `1` (UTF-8)
- Identification: `SCT` (SEPA Credit Transfer)
- BIC: from config
- Beneficiary name: from config (max 70 chars)
- IBAN: from config (max 34 chars)
- Amount: empty (user decides)
- Purpose: `CHAR` (charity)
- Creditor reference: empty
- Remittance text: user-specific supporterId (max 140 chars)
- Information: empty

### 4. Template Change: `support-banktransfer.html`

Add QR code image below the bank detail list with a label like "Scanne den QR-Code mit Deiner Banking-App:".

## Out of Scope

- Amount selection UI
- Client-side QR rendering
- Database changes
- Changes to contribution matching logic
