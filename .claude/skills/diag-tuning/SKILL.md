---
name: diag-tuning
description: Tune the PhoneBlock diagnostics log analysis — inspect aggregated log signatures, find noise / signature fragmentation / PII leaks, and propose, audit and promote scrub rules (and detection rules) over the admin API. Use when asked to look at the diagnostics results, "what rules could we add", reduce log noise, or clean up how server/dongle log lines are grouped. Backed by the local `phoneblock-diag` MCP server.
---

# Diagnostics log tuning

The diagnostics framework (branch `diagnostics-framework`, PR #473) reads the
server and dongle logs, scrubs PII, normalizes each line to a **signature**, and
rolls signatures up into `DIAG_*` aggregate tables. On top of the always-on
built-in scrubber sits a hot-editable layer of **scrub rules** and a set of
**detection rules** that can raise a user-facing help mail. This skill is the
loop for keeping that analysis honest: collapse fragmented signatures, plug PII
leaks, and turn recurring failures into actionable rules.

The **test system reads the live server's logs** (including server errors), so
its data is real production traffic. Base URL: `https://phoneblock.net/pb-test/api`.

## Access — the `phoneblock-diag` MCP

Tools come from the local MCP server registered in `.mcp.json`
(`.claude/skills/diag-tuning/mcp/diag_mcp.py`, zero-dependency Python). It reads
the admin token itself from `~/.m2/settings.xml` (server id `phoneblock-admin`,
`<password>` or `<passphrase>`) — **the token never lives in the repo**. Point it
at another deployment with `PHONEBLOCK_API`, or override the token with
`PHONEBLOCK_ADMIN_TOKEN`.

Key tools: `ingest_status`, `list_signatures` (returns a volume-ranked, compact
list), `get_signature`, `audit_scrub`, `list_scrub_rules`, `create_scrub_rule`,
`set_scrub_state`, `list_rules`, `list_notifications`, `origin_timeline`.

**Always use these MCP tools — do not hand-roll `curl` plus token extraction from
`settings.xml`.** The server exists precisely to hold the token and the endpoints
for you. If you find yourself writing a shell script to hit the API, stop: the MCP
is either not loaded (check `.mcp.json` is trusted, reload the session) or a tool
is missing something worth adding. The 1:1 mapping to `GET/POST
<base>/admin/diag/…` is documentation of what each tool does and a stopgap for
when the MCP **process itself** is down — not a routine alternative.

## Mental model — the pipeline

Per log line: **scrub → normalize → signature**.

1. **Scrub** (`Scrubber.java`) runs *first* and masks high-confidence PII shapes
   (email, `sip:`/`tel:` phone, international subscriber number). It deliberately
   does **not** mask bare digit runs (those are HTTP/SIP codes, uptimes, byte
   counts). Built-in rules are always on; LIVE `DIAG_SCRUB_RULE` rows layer on top.
2. **Normalize** (`LogNormalizer`) then replaces variable tokens with `<N>`,
   `<ARG>`, `<UUID>`, `<TOKEN>`, `<username>`, etc. — this is what produces the
   `<N>` you see in signatures. Because scrub runs *before* normalize, a scrub
   rule matches the **raw** text, and its output is what gets normalized.
3. **Signature** = the normalized+scrubbed string; its content hash is `sigId`.

The retained **sample** is scrubbed-but-not-normalized, so `get_signature` shows
you the real raw tail — that is what you design a rule against.

`applies_to` splits the scrub pass: `SIGNATURE` shapes only the grouping key,
`SAMPLE` only the retained text, `BOTH` shapes both.

## Two symptoms worth hunting

- **Signature fragmentation** — one real error split across many signatures
  because a variable tail (a username, path, hostname) survives into the key.
  This *hides* true volume in the ranking. Fix: a `SIGNATURE`-anchored scrub rule
  that collapses the tail.
- **PII leak** — a real identifier the built-in scrubber missed (classic case:
  a **URL-encoded email** like `phoneblock%40gaerti.de`, since `%40` ≠ `@`). Fix:
  a `BOTH` rule so it is masked in samples too.

## Workflow

1. `ingest_status` — confirm the ingestor is live and not badly lagged.
2. `list_signatures` (rank by `totalEvents`) — scan for noise, fragmented
   families (same `tag`, near-identical text differing only in a tail), and PII.
3. For a candidate family: `get_signature` on a couple of members to read the
   **raw sample tails**.
4. Design an **anchored** scrub rule (see below). Prove the collapse locally
   (a quick Python `re.sub` over the sample strings), then run `audit_scrub` with
   the `candidatePattern` against live samples — confirm it hits **only** the
   intended `tag` and roughly the expected volume.
5. `create_scrub_rule` — lands as **DRAFT** (stored, not applied). Review.
6. Promote with `set_scrub_state` → `LIVE` (**admin token required**). A scrub
   rule only shapes events **going forward**; it does not retro-merge existing
   signatures.

### Rule design rules-of-thumb

- **Anchor on a stable prefix** and capture it: `(Prevent deleting card: ).*` →
  `$1<CARD>`. Never write a bare `.*` that could match unrelated lines.
- Match the **raw** tail (scrub runs before normalize) — e.g. `\d+`, not `<N>`.
- Beware repeated anchors in one line: if a token appears twice (e.g. `/addresses/`
  in both an arg and the real path), anchor on the fuller, unique prefix.
- `SIGNATURE` when the tail is diagnostically useful and low-sensitivity (keep it
  visible in samples); `BOTH` when the tail is PII or a possible secret.
- Don't lower the built-in phone threshold to catch short partials (`+4990`) — it
  would eat legit country prefixes (`+49`). Collapse those with a context-anchored
  rule instead.

## Scrub rules vs detection rules

- **Scrub rules** (`DIAG_SCRUB_RULE`, this skill's main focus) only *mask* — safe,
  additive, tune grouping/anonymization.
- **Detection rules** (`list_rules`, e.g. `DongleSilenceDetector`) *match* a
  signature and can raise a help mail. They carry SHADOW→LIVE state and a
  kill-switch; treat promotion as outbound-affecting and confirm first.

## Scrub rules currently LIVE (server source)

Three scrub rules run against the `SERVER` source, each collapsing a fragmented
family (`list_scrub_rules`). Scrub rules apply to new events only — they do not
retro-merge the pre-existing fragmented signatures, which linger in the ranking
and just stop growing:

- `diag-dyndns-host` — `(wrong password \(\d+ characters\): ).*` → `$1<DYNDNS-HOST>`,
  `SIGNATURE` (folded ~1.3k DynIpServlet events into one signature; keeps the raw
  tail in samples so the `fb-fb-…` config-bug and which hosts are hammered stay
  visible — flip to `BOTH` if the occasional password-in-username matters).
- `diag-addressbook-path` — `(/phoneblock/contacts/addresses/)[^/]+/[^'\s]*` →
  `$1<BOOK>/<CARD>`, `BOTH` (~0.8k events; also masks the URL-encoded email leak).
- `diag-address-card` — `(Prevent deleting card: ).*` → `$1<CARD>`, `BOTH` (~0.5k).

## Open observations from the live logs

Surfaced from the logs, not yet acted on:

- **Scanning / recon clusters** → candidates for a `category=SECURITY` tag
  (track, don't mail): auth scanning (`DB: Invalid user name` ~3.7k, invalid
  password, DynDNS wrong-password ~1.4k, login failures — the largest block);
  DNS-server recon (`DnsServer: No zone found for: version.bind. / id.server. /
  shadowserver.org. / .` plus ~360 malformed-packet `WireParseException`s); and
  SIP scanning (`SipProvider: … OPTIONS sip:nm …` — sipvicious/svmap).
- **Onboarding signal:** `CardDavServlet: … not found: / Benutzername/` — a user
  pasted the literal placeholder "Benutzername" from a setup guide into their
  CardDAV client. Low volume, but a concrete docs/UX fix.
- **No stale-fragment cleanup:** there is no step to prune signatures with no
  recent events, so pre-rule fragments stay visible in the ranking indefinitely.

Already handled (do not re-propose):

- **Answerbot registration failures** (`SipService: … register ab-<N> … Timeout`)
  are the retry churn of the existing mechanism: `SipService.onRegistrationFailure`
  disables a bot after `disableTimeout` (3 days) and, for a bot that had
  registered before, sends `sendDiableMail`. A never-registered ("temporary") bot
  is disabled silently (no mail) — the one arguable gap.
- **Two code bugs** — the `Goolge` typo and the Google/IndexNow log flood (~62% of
  events) — fixed in this PR (backoff + one-shot logging in the two index services).
