# Fake Mail Check

Disposable e-mail domain detection service for [PhoneBlock](https://phoneblock.net). Identifies temporary/disposable e-mail addresses used for spam registrations by combining multiple detection strategies.

## Detection Strategies

### 1. Domain Blocklist Import

Downloads a curated list of known disposable domains from [disposable-email-domains](https://github.com/disposable-email-domains/disposable-email-domains) and imports them into the local database.

### 2. Active Scraping

Scrapes disposable e-mail provider websites to discover their currently offered domains:

| Scraper | Provider | Method |
|---|---|---|
| YOPmailScraper | yopmail.com | Domain listing page |
| FakeMailGeneratorScraper | fakemailgenerator.com | @domain links |
| GuerrillaMailScraper | guerrillamail.com | Dropdown |
| MohmalScraper | mohmal.com | `<select>` dropdown |
| EmailFakeScraper | emailfake.com | Rotating domains |
| TMailorScraper | tmailor.com / temp-mail.io | JSON API |
| FumailScraper | fumail.co | `<select>` dropdown |
| PurpleMailScraper | purplemail.neweymail.com | Plaintext listing |

### 3. MX-Based Heuristic

Many disposable domains share the same mail server infrastructure. The service tracks MX hosts and IPs along with their disposition (`disposable`, `safe`, `mixed`). When a new unknown domain is encountered:

1. Resolve MX record via DNS
2. Check if the MX host is known as `disposable` or `safe`
3. Fall back to MX IP check
4. Only call external API providers if MX status is unknown or mixed

This avoids unnecessary API calls for domains that share infrastructure with already-known disposable providers.

### 4. External API Providers

Configurable chain of external check services, queried when local data is insufficient:

- **RapidAPI** (`mailcheck.p.rapidapi.com`) -- domain-level checks with MX info
- **UserCheck** (`api.usercheck.com`) -- domain and email-level checks

### 5. Public Provider Email Checks

For well-known public providers (Gmail, Outlook, Yahoo, iCloud, Proton), domain-level blocking is not possible. Instead, the service normalizes email addresses and checks them individually:

- **Gmail dot-trick**: `j.o.h.n@gmail.com` -> `john@gmail.com`
- **Plus addressing**: `user+tag@gmail.com` -> `user@gmail.com`
- **Domain aliasing**: `googlemail.com` -> `gmail.com`, `hotmail.com` -> `outlook.com`, etc.

Normalized addresses are stored in the `EMAIL_CHECK` table.

## Domain Status

Each checked domain receives one of three statuses (`DomainStatus` enum):

| Status | Meaning |
|---|---|
| `disposable` | Domain provides temporary/disposable email addresses |
| `safe` | Domain is a legitimate email provider |
| `invalid` | Domain has no valid MX record and cannot receive email |

## Database Schema

- **DOMAIN_CHECK** -- Cached domain classification with MX host/IP
- **EMAIL_CHECK** -- Normalized email addresses flagged as disposable (for public providers)
- **MX_HOST_STATUS** -- Aggregated disposition per MX hostname
- **MX_IP_STATUS** -- Aggregated disposition per MX IP address
- **MAILCHECK_PROPERTIES** -- Schema version tracking

Schema versioning is managed by `MailCheckSchema.initialize(SqlSessionFactory)`. Migrations are applied incrementally from `mailcheck-migration-XX.sql` files.

## CLI Usage

```bash
cd fake-mail-check
./mailcheck.sh <command> [options]
```

### Commands

| Command | Description |
|---|---|
| `init` | Initialize/upgrade the database schema |
| `import-list` | Download and import the GitHub disposable domain list |
| `scrape` | Run all web scrapers for disposable domains |
| `import-emails <file.json>` | Import harvested emails from browser extension export |
| `resolve-mx` | Resolve missing MX records via parallel DNS lookups (20 threads) |
| `rebuild-mx` | Rebuild MX status tables by re-aggregating from DOMAIN_CHECK |
| `check <email-or-domain>` | Check if an email or domain is disposable |
| `stats` | Show database statistics |

### Options

| Option | Description |
|---|---|
| `--db <path>` | H2 database path (default: `./mailcheck`) |

### Example Session

```bash
./mailcheck.sh init
./mailcheck.sh import-list
./mailcheck.sh scrape
./mailcheck.sh resolve-mx
./mailcheck.sh rebuild-mx
./mailcheck.sh check user@example.com
./mailcheck.sh stats
```

## Browser Extension

The `browser-extension/` directory contains a Chrome extension for harvesting disposable email addresses from temporary mail providers that are protected by Cloudflare.

The extension runs as a content script on the provider's page, using the site's own session and TLS fingerprint to bypass bot protection. It collects email addresses (including Gmail dot-trick and Outlook plus-addressing variants), normalizes them, and exports as JSON for import via the CLI.

### Installation

1. Open `chrome://extensions`
2. Enable Developer Mode
3. Load unpacked extension from `browser-extension/`
4. Navigate to the target provider (e.g. `22.do`)
5. Click the extension icon to start/stop harvesting

### Features

- Persistent state via `chrome.storage.local` (survives popup close)
- Auto-resume after page reload
- Email normalization (Gmail dot-trick, plus addressing, domain aliasing)
- Live progress with dynamic provider counters
- JSON export for CLI import

## Integration

### As a Library

Add the dependency and call the central initialization method:

```java
MailCheckSchema.initialize(sqlSessionFactory);
```

Then use the `EMailChecker` interface:

```java
EMailChecker checker = EMailCheckService.getInstance();
DomainStatus status = checker.check("user@example.com");

switch (status) {
    case DISPOSABLE -> // reject
    case INVALID    -> // domain cannot receive mail
    case SAFE       -> // allow
}
```

### Web Application

Register `EMailCheckService` as a `ServletContextListener`. Configure providers and API keys via JNDI (e.g. in Tomcat's `context.xml`):

```xml
<!-- Comma-separated list of provider class names -->
<Environment name="mailcheck/providers"
    value="de.haumacher.mailcheck.provider.rapidapi.RapidAPIProvider,de.haumacher.mailcheck.provider.usercheck.UserCheckProvider"
    type="java.lang.String"/>

<!-- RapidAPI Mailcheck (https://rapidapi.com/Top-Rated/api/mailcheck4) -->
<Environment name="mailcheck/apiKey"
    value="your-rapidapi-key"
    type="java.lang.String"/>

<!-- UserCheck (https://app.usercheck.com) -->
<Environment name="usercheck/apiKey"
    value="your-usercheck-key"
    type="java.lang.String"/>
```

### Provider Configuration

| Provider | JNDI Key | API | Free Tier |
|---|---|---|---|
| RapidAPI Mailcheck | `mailcheck/apiKey` | `GET https://mailcheck.p.rapidapi.com/?domain={domain}` | 300 requests/month |
| UserCheck | `usercheck/apiKey` | `GET https://api.usercheck.com/domain/{domain}` | 200 requests/day |

Both providers support rate limiting — the service automatically pauses when quotas are exceeded and resumes after the reset period.

Providers are queried in the order listed in `mailcheck/providers`. The first provider returning a result wins. If no providers are configured, only local data (domain blocklist, scrapers, MX heuristic) is used.
