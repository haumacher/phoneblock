# PhoneBlock JNDI Configuration Reference

This document describes all JNDI configuration options available for PhoneBlock services. JNDI (Java Naming and Directory Interface) properties can be configured in your servlet container (e.g., Tomcat's `context.xml`) or as system properties with dot notation (e.g., `-Ddb.url=...`).

## Configuration Lookup Mechanism

PhoneBlock uses a fallback configuration lookup mechanism:

1. **JNDI lookup** - First tries to load from `java:comp/env/<property>`
2. **System properties** - Falls back to system properties if JNDI lookup fails
3. **Default values** - Uses hardcoded defaults if neither is available

JNDI property names use forward slashes (`/`) as separators, while system properties use dots (`.`).

Example:
- JNDI: `java:comp/env/db/url`
- System Property: `-Ddb.url=jdbc:h2:...`

---

## Database Configuration

**JNDI Prefix:** `db/`
**System Property Prefix:** `db.`
**Source:** `DBService.java`, `DBConfig.java`

Configuration for the embedded H2 database used by PhoneBlock.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `db/url` | String | `jdbc:h2:/var/lib/tomcat10/work/phoneblock/h2` | JDBC connection URL for H2 database file location |
| `db/user` | String | `phone` | Database username |
| `db/password` | String | `block` | Database password |
| `db/port` | Integer | `9095` | TCP port for external database access. Set to `0` to disable DB server |
| `db/sendHelpMails` | Boolean | `false` | Automatically send help emails when user inactivity is detected |
| `db/sendWelcomeMails` | Boolean | `false` | Send welcome emails after first blocklist synchronization |

### Example Configuration

**Tomcat context.xml:**
```xml
<Context>
  <Environment name="db/url" value="jdbc:h2:/var/lib/phoneblock/db/h2" type="java.lang.String"/>
  <Environment name="db/user" value="phoneblock_user" type="java.lang.String"/>
  <Environment name="db/password" value="secret123" type="java.lang.String"/>
  <Environment name="db/port" value="9095" type="java.lang.Integer"/>
  <Environment name="db/sendHelpMails" value="true" type="java.lang.Boolean"/>
  <Environment name="db/sendWelcomeMails" value="true" type="java.lang.Boolean"/>
</Context>
```

**System Properties:**
```bash
-Ddb.url=jdbc:h2:/var/lib/phoneblock/db/h2 \
-Ddb.user=phoneblock_user \
-Ddb.password=secret123 \
-Ddb.port=9095 \
-Ddb.sendHelpMails=true \
-Ddb.sendWelcomeMails=true
```

---

## SMTP / Mail Service Configuration

**JNDI Prefix:** `smtp/`
**System Property Prefix:** `smtp.`
**Source:** `MailServiceStarter.java`

Configuration for outgoing email service (user verification, notifications, DKIM signing).

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `smtp/user` | String | Required | SMTP username for authentication |
| `smtp/password` | String | Required | SMTP password for authentication |
| `smtp/properties/*` | Context | - | SMTP properties as a sub-context (see JavaMail properties) |
| `smtp/signingSelector` | String | Optional | DKIM selector for email signing |
| `smtp/signingDomain` | String | Optional | Domain for DKIM signature |
| `smtp/signingKey` | String | Optional | Path to PKCS8-encoded RSA private key file for DKIM |

### SMTP Properties

The `smtp/properties` context can contain any JavaMail SMTP properties:
- `mail.smtp.host` - SMTP server hostname
- `mail.smtp.port` - SMTP server port
- `mail.smtp.auth` - Enable authentication (true/false)
- `mail.smtp.starttls.enable` - Enable STARTTLS (true/false)
- `smtp.test-only` - If "true", use dummy mail service (no actual emails sent)

### Example Configuration

**Tomcat context.xml:**
```xml
<Context>
  <Environment name="smtp/user" value="noreply@phoneblock.net" type="java.lang.String"/>
  <Environment name="smtp/password" value="smtp_password" type="java.lang.String"/>
  <Environment name="smtp/signingSelector" value="mail" type="java.lang.String"/>
  <Environment name="smtp/signingDomain" value="phoneblock.net" type="java.lang.String"/>
  <Environment name="smtp/signingKey" value="/etc/phoneblock/dkim-private.der" type="java.lang.String"/>

  <Environment name="smtp/properties/mail.smtp.host" value="smtp.example.com" type="java.lang.String"/>
  <Environment name="smtp/properties/mail.smtp.port" value="587" type="java.lang.String"/>
  <Environment name="smtp/properties/mail.smtp.auth" value="true" type="java.lang.String"/>
  <Environment name="smtp/properties/mail.smtp.starttls.enable" value="true" type="java.lang.String"/>
</Context>
```

**System Properties:**
```bash
-Dsmtp.user=noreply@phoneblock.net \
-Dsmtp.password=smtp_password \
-Dsmtp.signingSelector=mail \
-Dsmtp.signingDomain=phoneblock.net \
-Dsmtp.signingKey=/etc/phoneblock/dkim-private.der \
-Dsmtp.properties.mail.smtp.host=smtp.example.com \
-Dsmtp.properties.mail.smtp.port=587 \
-Dsmtp.properties.mail.smtp.auth=true \
-Dsmtp.properties.mail.smtp.starttls.enable=true
```

---

## ChatGPT Service Configuration

**JNDI Prefix:** `chatgpt.`
**System Property Prefix:** `chatgpt.`
**Source:** `ChatGPTService.java`

Configuration for OpenAI ChatGPT integration (automatic comment summarization).

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `chatgpt.secret` | String | Required | OpenAI API key for ChatGPT access |

### Example Configuration

**Tomcat context.xml:**
```xml
<Context>
  <Environment name="chatgpt/secret" value="sk-..." type="java.lang.String"/>
</Context>
```

**System Properties:**
```bash
-Dchatgpt.secret=sk-...
```

**Note:** The ChatGPT service is optional. If no API key is configured, the service will not start and no automatic summarization will occur.

---

## Answer Bot Configuration

**JNDI Prefix:** `answerbot/`
**System Property Prefix:** N/A (uses direct JNDI enumeration)
**Source:** `SipService.java`

Configuration for the SIP-based answer bot that engages spam callers.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `answerbot/configfile` | String | Optional | Path to answer bot configuration file (`.phoneblock` format) |
| `answerbot/*` | Various | - | Any other properties are passed as command-line style arguments to the answer bot |

### Example Configuration

**Tomcat context.xml:**
```xml
<Context>
  <Environment name="answerbot/configfile" value="/etc/phoneblock/answerbot.conf" type="java.lang.String"/>
</Context>
```

**Note:** Most answer bot configuration is done through the configuration file specified by `answerbot/configfile`. Additional properties can be set directly in JNDI and will override file settings.

---

## Location Service Configuration

**JNDI Prefix:** `location.`
**System Property Prefix:** `location.`
**Source:** `LocationService.java`

Configuration for IP geolocation using IP2Location databases.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `location.ip4db` | String | Required | Path to IPv4 location database file |
| `location.ip6db` | String | Required | Path to IPv6 location database file |

### Example Configuration

**Tomcat context.xml:**
```xml
<Context>
  <Environment name="location/ip4db" value="/var/lib/phoneblock/IP2LOCATION-LITE-DB1.BIN" type="java.lang.String"/>
  <Environment name="location/ip6db" value="/var/lib/phoneblock/IP2LOCATION-LITE-DB1.IPV6.BIN" type="java.lang.String"/>
</Context>
```

**System Properties:**
```bash
-Dlocation.ip4db=/var/lib/phoneblock/IP2LOCATION-LITE-DB1.BIN \
-Dlocation.ip6db=/var/lib/phoneblock/IP2LOCATION-LITE-DB1.IPV6.BIN
```

**Note:** IP2Location databases are downloaded automatically during Maven build. The service will disable location lookup if databases are not configured.

---

## DNS Service Configuration

**JNDI Prefix:** `dns/`
**System Property Prefix:** `dns.`
**Source:** `DnsService.java`

Configuration for the integrated DNS server (used for DynDNS functionality with Fritz!Box devices).

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `dns/port` | Integer | `0` (disabled) | UDP port for DNS server. Set to `0` to disable DNS service |

### Example Configuration

**Tomcat context.xml:**
```xml
<Context>
  <Environment name="dns/port" value="53" type="java.lang.Integer"/>
</Context>
```

**System Properties:**
```bash
-Ddns.port=53
```

**Note:** Running on port 53 typically requires root/administrator privileges. The DNS server is optional and only needed for DynDNS functionality.

---

## Web Crawler Configuration

**JNDI Prefix:** `crawler/`
**System Property Prefix:** N/A
**Source:** `CrawlerService.java`

Configuration for the optional web crawler that imports spam reports from external sources.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `crawler/url` | String | Optional | URL to crawl for spam reports |

### Example Configuration

**Tomcat context.xml:**
```xml
<Context>
  <Environment name="crawler/url" value="https://example.com/spam-feed" type="java.lang.String"/>
</Context>
```

**Note:** The crawler is optional. If no URL is configured or the URL contains Maven placeholders (`${...}`), the crawler will not start.

---

## Google Indexing Service Configuration

**JNDI Prefix:** `google/`
**System Property Prefix:** N/A
**Source:** `GoogleUpdateService.java`

Configuration for pushing URL updates to Google's Indexing API.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `google/accountfile` | String | Optional | Path to Google Cloud service account JSON file with Indexing API permissions |

### Example Configuration

**Tomcat context.xml:**
```xml
<Context>
  <Environment name="google/accountfile" value="/etc/phoneblock/google-service-account.json" type="java.lang.String"/>
</Context>
```

**Note:**
- Requires a Google Cloud service account with the "Indexing API" scope enabled
- The service will be deactivated if no account file is configured
- Service account must have `https://www.googleapis.com/auth/indexing` scope

---

## IndexNow Service Configuration

**JNDI Prefix:** `indexnow/`
**System Property Prefix:** N/A
**Source:** `IndexNowUpdateService.java`

Configuration for pushing URL updates to search engines via the IndexNow protocol (Bing, Yandex, etc.).

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `indexnow/key` | String | Optional | API key for IndexNow service |

### Example Configuration

**Tomcat context.xml:**
```xml
<Context>
  <Environment name="indexnow/key" value="abc123..." type="java.lang.String"/>
</Context>
```

**Note:** The service will be deactivated if no API key is configured.

---

## Logging Configuration

**JNDI Prefix:** `log/`
**System Property Prefix:** N/A
**Source:** `Application.java`

Configuration for tinylog logging framework.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `log/configfile` | String | Optional | Path to tinylog properties configuration file |

### Example Configuration

**Tomcat context.xml:**
```xml
<Context>
  <Environment name="log/configfile" value="/etc/phoneblock/tinylog.properties" type="java.lang.String"/>
</Context>
```

**Note:** If not configured, tinylog will use default logging configuration.

---

## IMAP Service Configuration

**JNDI Prefix:** `imap.`
**System Property Prefix:** `imap.`
**Source:** `ImapService.java`

Configuration for monitoring IMAP mailbox for donation notifications (PayPal email parsing).

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `imap.*` | Various | - | JavaMail IMAP properties (passed to JavaMail Session) |
| `credits.active` | Boolean | `false` | Enable donation processing |
| `credits.sendmails` | Boolean | `false` | Send thank you emails to donors |

### Common IMAP Properties

- `mail.imap.user` - IMAP username
- `mail.imap.password` - IMAP password
- `mail.imap.host` - IMAP server hostname
- `mail.imap.port` - IMAP server port (typically 993 for IMAPS)
- `mail.store.protocol` - Protocol (should be `imaps`)
- `mail.imap.ssl.enable` - Enable SSL (true/false)

### Example Configuration

**Tomcat context.xml:**
```xml
<Context>
  <Environment name="imap/mail.imap.host" value="imap.gmail.com" type="java.lang.String"/>
  <Environment name="imap/mail.imap.port" value="993" type="java.lang.String"/>
  <Environment name="imap/mail.imap.user" value="donations@phoneblock.net" type="java.lang.String"/>
  <Environment name="imap/mail.imap.password" value="app_password" type="java.lang.String"/>
  <Environment name="imap/mail.imap.ssl.enable" value="true" type="java.lang.String"/>
  <Environment name="credits/active" value="true" type="java.lang.String"/>
  <Environment name="credits/sendmails" value="true" type="java.lang.String"/>
</Context>
```

**System Properties:**
```bash
-Dimap.mail.imap.host=imap.gmail.com \
-Dimap.mail.imap.port=993 \
-Dimap.mail.imap.user=donations@phoneblock.net \
-Dimap.mail.imap.password=app_password \
-Dimap.mail.imap.ssl.enable=true \
-Dcredits.active=true \
-Dcredits.sendmails=true
```

**Note:** The IMAP service is designed to parse PayPal donation notification emails. It requires both IMAP credentials and donation processing to be enabled.

---

## Mail Check Service Configuration

**JNDI Prefix:** `mailcheck/`
**System Property Prefix:** N/A
**Source:** `EMailCheckService.java`

Configuration for checking disposable email addresses using RapidAPI's mailcheck service.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `mailcheck/apiKey` | String | Optional | RapidAPI key for mailcheck service |

### Example Configuration

**Tomcat context.xml:**
```xml
<Context>
  <Environment name="mailcheck/apiKey" value="your-rapidapi-key" type="java.lang.String"/>
</Context>
```

**Note:**
- The service will be deactivated if no API key is configured
- Results are cached in the database to minimize API calls
- Service automatically handles rate limiting and quota exhaustion

---

## Blocklist Version Service Configuration

**JNDI Prefix:** `blocklist/version/`
**System Property Prefix:** `blocklist.version.`
**Source:** `BlocklistVersionService.java`

Configuration for scheduled blocklist versioning (incremental synchronization support).

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `blocklist/version/hour` | Integer | `3` | Hour of day to run version assignment (0-23) |
| `blocklist/version/minute` | Integer | `0` | Minute of hour to run version assignment (0-59) |
| `blocklist/version/intervalMinutes` | Long | `1440` | Interval between runs in minutes (default: 24 hours) |
| `blocklist/version/initialDelayMinutes` | Long | `-1` | Initial delay in minutes for testing (-1 = use calculated schedule) |

### Example Configuration

**Tomcat context.xml:**
```xml
<Context>
  <Environment name="blocklist/version/hour" value="3" type="java.lang.Integer"/>
  <Environment name="blocklist/version/minute" value="0" type="java.lang.Integer"/>
  <Environment name="blocklist/version/intervalMinutes" value="1440" type="java.lang.Integer"/>
</Context>
```

**System Properties (Production):**
```bash
-Dblocklist.version.hour=3 \
-Dblocklist.version.minute=0 \
-Dblocklist.version.intervalMinutes=1440
```

**System Properties (Testing - immediate start, 5 minute intervals):**
```bash
-Dblocklist.version.initialDelayMinutes=0 \
-Dblocklist.version.intervalMinutes=5
```

**Note:**
- Default schedule runs daily at 3:00 AM
- The `initialDelayMinutes` property is intended for testing only
- Setting `initialDelayMinutes` to a non-negative value overrides the scheduled time calculation

---

## Complete Example Configuration

Here's a complete example Tomcat `context.xml` file with common production settings:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Context>
  <!-- Database Configuration -->
  <Environment name="db/url" value="jdbc:h2:/var/lib/phoneblock/db/h2" type="java.lang.String"/>
  <Environment name="db/user" value="phoneblock" type="java.lang.String"/>
  <Environment name="db/password" value="secure_password" type="java.lang.String"/>
  <Environment name="db/port" value="9095" type="java.lang.Integer"/>
  <Environment name="db/sendHelpMails" value="true" type="java.lang.Boolean"/>
  <Environment name="db/sendWelcomeMails" value="true" type="java.lang.Boolean"/>

  <!-- SMTP Configuration -->
  <Environment name="smtp/user" value="noreply@phoneblock.net" type="java.lang.String"/>
  <Environment name="smtp/password" value="smtp_password" type="java.lang.String"/>
  <Environment name="smtp/properties/mail.smtp.host" value="smtp.example.com" type="java.lang.String"/>
  <Environment name="smtp/properties/mail.smtp.port" value="587" type="java.lang.String"/>
  <Environment name="smtp/properties/mail.smtp.auth" value="true" type="java.lang.String"/>
  <Environment name="smtp/properties/mail.smtp.starttls.enable" value="true" type="java.lang.String"/>
  <Environment name="smtp/signingSelector" value="mail" type="java.lang.String"/>
  <Environment name="smtp/signingDomain" value="phoneblock.net" type="java.lang.String"/>
  <Environment name="smtp/signingKey" value="/etc/phoneblock/dkim-private.der" type="java.lang.String"/>

  <!-- Location Service -->
  <Environment name="location/ip4db" value="/var/lib/phoneblock/IP2LOCATION-LITE-DB1.BIN" type="java.lang.String"/>
  <Environment name="location/ip6db" value="/var/lib/phoneblock/IP2LOCATION-LITE-DB1.IPV6.BIN" type="java.lang.String"/>

  <!-- Logging -->
  <Environment name="log/configfile" value="/etc/phoneblock/tinylog.properties" type="java.lang.String"/>

  <!-- Optional Services (uncomment to enable) -->

  <!-- ChatGPT Integration -->
  <!-- <Environment name="chatgpt/secret" value="sk-..." type="java.lang.String"/> -->

  <!-- Google Indexing -->
  <!-- <Environment name="google/accountfile" value="/etc/phoneblock/google-service-account.json" type="java.lang.String"/> -->

  <!-- IndexNow -->
  <!-- <Environment name="indexnow/key" value="abc123..." type="java.lang.String"/> -->

  <!-- DNS Server -->
  <!-- <Environment name="dns/port" value="53" type="java.lang.Integer"/> -->

  <!-- Answer Bot -->
  <!-- <Environment name="answerbot/configfile" value="/etc/phoneblock/answerbot.conf" type="java.lang.String"/> -->

  <!-- Mail Checker -->
  <!-- <Environment name="mailcheck/apiKey" value="rapidapi-key" type="java.lang.String"/> -->

  <!-- IMAP Donations -->
  <!-- <Environment name="imap/mail.imap.host" value="imap.gmail.com" type="java.lang.String"/> -->
  <!-- <Environment name="imap/mail.imap.user" value="donations@phoneblock.net" type="java.lang.String"/> -->
  <!-- <Environment name="imap/mail.imap.password" value="app_password" type="java.lang.String"/> -->
  <!-- <Environment name="credits/active" value="true" type="java.lang.String"/> -->

  <!-- Blocklist Versioning Schedule -->
  <Environment name="blocklist/version/hour" value="3" type="java.lang.Integer"/>
  <Environment name="blocklist/version/minute" value="0" type="java.lang.Integer"/>
  <Environment name="blocklist/version/intervalMinutes" value="1440" type="java.lang.Integer"/>
</Context>
```

---

## Security Considerations

1. **Credentials:** Never commit JNDI configuration files with real credentials to version control
2. **File Permissions:** Restrict access to configuration files containing secrets (chmod 600)
3. **DKIM Keys:** Keep DKIM private key files secure and readable only by the application user
4. **API Keys:** Rotate API keys regularly and use environment-specific keys
5. **Database:** Use strong passwords and consider encrypting the H2 database file
6. **Ports:** Be careful when exposing database ports externally; use firewall rules to restrict access

---

## Troubleshooting

### Configuration Not Loading

1. Check servlet container logs for JNDI-related errors
2. Verify JNDI names use forward slashes (`/`) not dots
3. Ensure proper type attributes in `<Environment>` tags
4. Check that context.xml is in the correct location

### Service Not Starting

1. Check if required properties are configured (marked as "Required" in this document)
2. Review application logs for specific service initialization errors
3. Verify file paths (configuration files, databases, keys) are accessible
4. Check file permissions

### System Properties vs JNDI

If you need to use system properties instead of JNDI:

1. Convert slashes to dots: `db/url` → `db.url`
2. Pass as JVM arguments: `-Ddb.url=...`
3. Or set in Tomcat's `setenv.sh`: `CATALINA_OPTS="-Ddb.url=..."`

---

## References

- **Main Application:** `de.haumacher.phoneblock.app.Application`
- **JNDI Utility:** `de.haumacher.phoneblock.jndi.JNDIProperties`
- **Configuration Template:** `phoneblock/.phoneblock.template`
- **Tomcat JNDI:** https://tomcat.apache.org/tomcat-10.1-doc/jndi-resources-howto.html
