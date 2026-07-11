# Diagnostics Framework: Log Storage, Analysis & Autonomous Help Mail

**Date:** 2026-07-11
**Status:** Draft — for review. **Implemented on branch `diagnostics-framework`**
(package `de.haumacher.phoneblock.diag`): Phase 1 (reader + dongle recognizer +
`DIAG_*` tables), Phase 2 (auth capabilities + the full `/api/diag` REST API —
signatures, origins timeline, rules + stats, templates + preview, scrub rules +
audit, notifications, ingest status, kill switch), Phases 3–4 (rule engine,
matcher, help-mail — seeded rules ship in SHADOW, mail kill switch off), the
hot-editable `DIAG_SCRUB_RULE` anonymizer (built-in baseline + LIVE DB rules
layered on, `applies_to`-aware, audit loop), and Phase 6 (opt-in server-log
recognizer). **Phase 5 (firmware-side scrub subset) is the only part not built**
— it belongs to a firmware release (#472).
**Scope:** `phoneblock/` server (generic ingest, rule engine, scheduler, mail),
`phoneblock-tools/` (offline analysis), plus per-source adapters (dongle firmware
first; mobile app and the server's own logs later)

## Problem

Several PhoneBlock components emit operational log lines that point at a *user's*
installation problem, but nothing turns them into help:

- **Dongles** ship their captured WARN/ERROR ring to `LogReportServlet`
  (`POST /api/dongle/log`) on non-crashing failures — lost SIP registration,
  WiFi trouble, failed rating uploads. Today each line is `LOG.warn`'d and
  forgotten.
- The **mobile app** and the **server itself** (e.g. the WARN/ERROR that
  `pb-log-summary.sh` already groups) produce the same *shape* of signal — a
  recurring, categorizable error tied to a user or an installation — with no
  path back to the affected user either.

A one-day dongle sample (2026-07-09 → 07-10, 24 devices, 569 lines) shows the
data is highly structured and genuinely actionable: one device had 32 sustained
`REGISTER rejected: 400` — a fixable misconfiguration the user never hears about
(see [Appendix A](#appendix-a-first-adapter-dongle-corpus)). Manual analysis
does not scale, and the interesting failure modes change as firmware, app, and
fleet evolve.

We want a **source-agnostic framework** — log storage + analysis + autonomous
help — that:

- **(a)** autonomously mails targeted help to a user when a *known, common*
  problem is detected in *any* source's logs — with a human on the trigger, not
  spamming, never mailing users for problems they cannot fix;
- **(b)** provides an **agent-introspection interface** so an agent can discover
  *new* problems in the accumulated data and extend the auto-help workflow
  **without redeploying the server**.

The **dongle is the first adapter**; the framework is built to take the mobile
app and the server's own logs as later sources with no schema change.

## Core principle: the workflow is data, not code

Requirement (b) dictates the architecture. If an agent must change *what is
detected* and *what mail is sent* without a redeploy, then detection rules and
mail copy cannot be Java — they must be **rows an agent can edit**. The server
becomes a small, declarative **rule engine** over a generic event store; all
intelligence lives in data.

**The redeploy boundary (honest):** a *declarative* rule — match a normalized
signature by source + tag + regex, apply a persistence threshold, pick a mail
template — is hot-editable. Genuinely new *detection logic* a regex over the
signature cannot express still needs a redeploy. Looking at the corpus, every
real category *is* a tag + message pattern with a threshold, so the declarative
engine covers the real world. Arbitrary hot-loaded code that auto-emails real
users would be a footgun regardless.

## Sources & adapters

The framework core is source-agnostic. Ingestion is **decoupled from the request
path**: every source already funnels its lines into the server's rolling log
file, and a single scheduled **reader** tails that log, parses relevant lines,
and batch-loads the aggregate tables (see
[Ingestion](#ingest--decoupled-log-reader-not-per-message-writes)). Nothing
writes to the diagnostics DB synchronously on a request.

Everything source-specific is a thin **recognizer** — a pure function
`(rawLogLine) → Event?` that returns the common event fields for lines it owns
(and `null` otherwise), establishing the reporting **origin** and the **user** to
attribute/notify.

Common event fields the core consumes:

`source, origin_id, user_id?, severity, timestamp, uptime_s?, tag, message`

| Source | How its lines reach the log | `origin_id` | Recognizer parses | Status |
|---|---|---|---|---|
| `DONGLE` | `LogReportServlet` `LOG.warn`s each posted line (already does) | device UUID | `Dongle error [user=…, agent=…]: <E\|W> +<uptime>s <tag>: <msg>` | **first** |
| `MOBILE_APP` | app-report endpoint `LOG.warn`s each line | install id | app envelope | later |
| `SERVER` | native tinylog WARN/ERROR | node/component id | `[date] LEVEL: [class]: msg` | later |

The key simplification: external sources (dongle, app) POST to an authenticated
endpoint whose **only** job is to `LOG.warn` the lines — no DB work on the
request. The server's *own* WARN/ERROR (the very lines `pb-log-summary.sh` greps)
are picked up by the same reader with no endpoint at all. `user_id` is optional:
sources with no user association never match a `USER`-actor rule (they route to
`DEV`).

## Architecture overview

```
  DONGLE ──POST /api/dongle/log──┐
  MOBILE_APP ──POST /api/…/log───┤ (endpoint only authenticates + LOG.warn)
                                 ▼
  SERVER (native WARN/ERROR) ─► ┌─────────────────────────────┐
                                │  rolling server log file    │  ← durable buffer
                                │  (tinylog; handles rotation)│    (request path ends here)
                                └─────────────────────────────┘
                                 │  tail from saved offset (DIAG_INGEST_CURSOR)
                                 ▼
  ┌───────────────────────────────────────────────────────────┐
  │ SchedulerService: LOG READER job  (~1–5 min, BATCHED)     │
  │   for each new line:                                       │
  │     per-source recognizer → Event?  (skip if null)        │
  │     SCRUB (LIVE rules → <email>/<phone>/…)                 │
  │     SIGNATURE (normalize ids/nums/hex → <UUID>/<N>/…)      │
  │   accumulate in memory, then ONE tx per batch:            │
  │     upsert DIAG_SIGNATURE + DIAG_ORIGIN_SIGNATURE,        │
  │     insert capped DIAG_SAMPLE (tail signatures only)      │
  │   advance cursor                                           │
  └───────────────────────────────────────────────────────────┘
                                 │  (DB — PII-free aggregates live forever)
                                 ▼
  ┌───────────────────────────────────────────────────────────┐
  │ SchedulerService: MATCH & NOTIFY job  (~hourly)           │
  │   reload rules if DIAG_RULESET_VERSION changed (no deploy) │
  │   per DIAG_ORIGIN_SIGNATURE: match LIVE/SHADOW rule →      │
  │     threshold met? → latch check →                        │
  │       USER + LIVE   → MailServiceImpl.sendMail            │
  │       USER + SHADOW → dry-run projection, send nothing    │
  │       DEV / NONE    → dev digest / silent count           │
  └───────────────────────────────────────────────────────────┘
        ▲  REST (read / dry-run / author; promote needs accessAdmin)
        │  optionally wrapped as a local MCP
  ┌───────────────────────────────────────────────────────────┐
  │ Agent introspection interface — the self-updating loop     │
  └───────────────────────────────────────────────────────────┘
```

## Normalization & PII — two distinct transforms

There are two text transforms, and PII affects them differently. Both are
source-agnostic.

### 1. Signature (grouping key) — reuse `pb-log-summary.sh`

`phoneblock-tools/bin/pb-log-summary.sh` **already** implements a tuned, ordered
normalization — built for the *server's own* logs, so it is source-agnostic by
origin — that collapses variable parts into a stable placeholder vocabulary:

| Token | Matches |
|---|---|
| `<ARG>` | quoted value |
| `<DATE>` | `java.util.Date.toString()` |
| `<IP>` | IPv4 / bracketed IPv6 |
| `<UUID>` | user / device id |
| `<TOKEN>` | random credential (lower+upper+digit, ≥12 chars) |
| `<HEX>` | sha1 / device token (hex with a letter) |
| `<N>` | any remaining number (phone, count, port) |

This is adopted as the **canonical signature spec**. The core reimplements the
same ordered rules in Java so the offline tool and the live server produce
**identical** keys — the script stays useful as the ground-truth oracle for a
Java parity unit test.

Two things this vocabulary already settles:

- `<N>` masks every digit run, so **phone numbers never reach the signature** —
  the PII risk lives in the retained raw *sample*, not the key.
- The script has **no email/hostname rule** today, so `status mail to <email>
  failed` would survive normalization. That is the gap to close: add `<email>`
  (and, for samples, `<phone>`) to the shared vocabulary.

The signature is the source-scoped grouping dimension: two sources emitting an
identical string still separate because `source` is part of the key.

### 2. Raw-sample scrubbing — a growable rule set

The agent needs *some* readable raw text to cluster new problems. That retained
sample is scrubbed on ingest by the **LIVE scrub rules** (`DIAG_SCRUB_RULE`),
hot-editable data like detection rules:

- **Emails / hostnames** — scrubbed in **both** signature and sample (no digits,
  so `<N>` misses them). A single high-confidence regex.
- **Phone numbers** — matter only for the sample; masking must be **contextual**
  (after `rate:` / `for `, inside `sip:` / `tel:` URIs), not a blanket
  digit-mask that would eat the HTTP status codes, SIP reason codes and byte
  counts that make samples diagnostic. Start with those contexts; grow the set.

Scrub rules may be global or source-scoped; the email rule is global, phone
contexts likely global too.

**Self-correcting growth.** The interface includes `audit_samples`, which scans
retained samples for anything still looking like PII. A leaked format sits in a
sample until the audit flags it → the agent proposes a new scrub rule. Scrub
rules use an **asymmetric gate**: *tightening* (more masking) may auto-apply;
*relaxing* (would start retaining more) needs human review.

### Client-side scrubbing (external sources) — baked subset, manual updates

For the dongle (and later the app), a small high-confidence subset of the scrub
patterns is compiled into the client and applied before transmit — cutting PII
at the source and trimming ring noise. Per decision, this ships with **normal
manually-cut releases**; there is **no** dynamic scrub-rule pull. Two constraints
make it safe:

1. **Defense in depth** — the server-side scrub *stays* as backstop and growing
   set. The fleet is never uniformly up to date (this sample mixes fw 1.4.0 and
   1.4.1); old clients keep sending raw, the server keeps catching it.
2. **Shared token vocabulary** — client and server must emit the *same* tokens
   (`<email>`, `<phone>`, …) or a line scrubbed at the source produces a
   different signature than one scrubbed at the server, forking aggregates.
   Defined once; both consume it; a client subset may lag without breaking
   grouping.

### Retention

Raw `DIAG_SAMPLE` rows: **30 days** (scheduled purge). Once scrubbed they are not
sensitive. Aggregates (`DIAG_SIGNATURE`, `DIAG_ORIGIN_SIGNATURE`, counts) are
PII-free by construction and live indefinitely as fleet-health history.

## Data model

Source-agnostic, MyBatis-mapped (`de.haumacher.phoneblock.diag.*`), plus schema
+ migration per the repo's DB workflow. Every event-derived row carries
`source`.

**`DIAG_SAMPLE`** — bounded raw evidence, 30-day retention.
`id, received_ms, source, sig_id, origin_id, user_id, severity(E|W),
uptime_s(nullable), tag, message_scrubbed`. Capped to N samples per signature,
and biased to the **tail**: samples exist so the agent can investigate *new /
unmatched* signatures — a signature already covered by a rule needs few or none.

**`DIAG_SIGNATURE`** — one row per `(source, signature)` (long-lived).
`sig_id (stable hash of source+signature — the id used in URLs), source,
signature, tag, category(nullable), sample_message, first_seen, last_seen,
total_events, origin_count, user_count`. `category IS NULL` ⇒ *unmatched* — the
agent's "what's new" feed.

**`DIAG_ORIGIN_SIGNATURE`** — per-origin rolling aggregate; what the matcher
evaluates for thresholds.
`source, origin_id, user_id, signature, first_seen, last_seen, event_count,
distinct_days`.

**`DIAG_RULE`** — hot-editable detection rules.
`id, name, source(nullable = all sources), match_tag(nullable), match_regex,
category, actor(USER|DEV|NONE), min_distinct_days, min_events,
template_id(nullable, USER only), state(DRAFT|SHADOW|LIVE|DISABLED), version,
author, created, updated, notes`.

**`DIAG_SCRUB_RULE`** — hot-editable anonymizer.
`id, name, source(nullable = all), pattern, replacement_token,
applies_to(SIGNATURE|SAMPLE|BOTH), state, version, author, updated`.

**`DIAG_TEMPLATE`** — mail copy as data (safe placeholder substitution only).
`id, lang, subject, body, version`.

**`DIAG_NOTIFICATION`** — idempotency, audit, dry-run projection.
`id, source, origin_id, user_id, rule_id, state(PENDING|SENT|CLEARED|SUPPRESSED),
dry_run(bool), first_matched, sent_at, cleared_at`.

**`DIAG_INGEST_CURSOR`** — the reader's checkpoint, one row per tailed log
stream. `stream_id, segment_count (tinylog {count}), byte_offset, last_line_ts,
updated`. The monotonic `segment_count` + `byte_offset` lets the reader resume
exactly-once across restarts and detect rotation (a higher count appeared) and
gaps (its count was pruned) without inode heuristics.

**Ruleset version** — a monotonic counter (`PROPERTIES` row, mirroring
`BlocklistVersionService`) bumped on any rule/scrub/template write; the scheduler
reloads its in-memory rule set on change. This is the "no redeploy" mechanism.

**`AuthToken` (existing table, extended)** — two new boolean capability columns
`accessDiagnostics` and `accessAdmin`, mirroring existing `accessRate`. See
[Authentication & access](#authentication--access).

## Runtime pipeline

### Ingest — decoupled log reader, not per-message writes

**Why not a synchronous per-event write.** For the dongle a per-line DB
transaction would be wasteful; for the server's own logs it would be actively
bad — a DB write (and its transaction) on every WARN/ERROR, coupled to the
request path. ACID per log line is irrelevant here: these are best-effort
diagnostic counts, not money. So ingestion is fully decoupled.

**The log file is the buffer.** Every source already lands its lines in the
rolling server log (tinylog, which already handles rotation, backpressure and
disk). The external endpoints (`/api/dongle/log`, later the app's) do nothing but
authenticate and `LOG.warn`; server components log natively. The request path
ends at the log write.

**The reader** is a `SchedulerService` job (every ~1–5 min) that tails the
rolling files from a saved offset (`DIAG_INGEST_CURSOR`). For each new line: run
the per-source recognizers (`(line) → Event?`, skip on `null`), apply LIVE scrub
rules (`applies_to` SAMPLE|BOTH), compute the signature (scrub BOTH + the
`pb-log-summary` normalization), and **accumulate in memory**. At the end of the
batch it writes **one transaction**: upsert `DIAG_SIGNATURE` and
`DIAG_ORIGIN_SIGNATURE` (merged counts), insert capped `DIAG_SAMPLE` rows, then
advance the cursor. The event `timestamp` comes from the line's `[date]` prefix,
so processing lag never distorts event times.

**Rotation & crash-safety.** tinylog's config
(`writer.file = phoneblock.log.{count}`, `writer.latest = phoneblock.log`,
`policies = size: 10mb`, `backups = 10`) rolls into **monotonically-numbered,
immutable segments** — the counter never resets (the live files are `.2465 …
.2474`), and only the newest `backups` are kept. This makes rotation detection
trivial: the reader follows the **numbered segments** with a cursor of
`(segment_count, byte_offset)`, tailing the highest-count file by offset;
**a higher `{count}` file appearing is the rotation signal** that the current
segment is complete, so the reader drains its remainder and advances. No inode or
size-shrink heuristics. The fixed-name `phoneblock.log` (the `latest` copy) is
**ignored** — it duplicates the active segment and would double-count. (For the
same reason the Java reader does not reuse `pb-log-summary.sh`'s `phoneblock.log*`
glob, which counts the active segment twice — harmless for an overview, not for
persisted aggregates.)

This gives exactly-once in the normal case. If the reader falls behind by more
than `backups` segments, its cursor's count has been pruned; it detects the gap,
resumes from the oldest surviving count, and logs the lost span — bounded,
best-effort loss, acceptable precisely because ACID per line does not matter.
Sizing the interval ≪ (`backups` × time-to-fill-10 MB) keeps this from happening.

**Decoupling bonus.** The reader only needs log-file read + DB write, so it can
run in the webapp JVM (Phase 1, reuses `DBService`) or be lifted into a
standalone `phoneblock-tools` process later without touching the rest. It is,
literally, `pb-log-summary.sh` that persists aggregates instead of printing them.

The recognizers:

- **Dongle** — matches `LogReportServlet`'s `Dongle error [user=…, agent=…]:
  <E|W> +<uptime>s <tag>: <msg>` lines; `origin_id` = device UUID from the agent,
  `user_id` from the logged user, `source = DONGLE`. `LogReportServlet` keeps its
  existing `LOG.warn` unchanged — no code change needed there for Phase 1.
- **Server** (later) — matches native tinylog `[date] LEVEL: [class]: msg` at
  WARN/ERROR; `source = SERVER`, `user_id` best-effort from the message.

### Match & notify (`SchedulerService` job, ~hourly)

Reload rules if `DIAG_RULESET_VERSION` changed. For each `DIAG_ORIGIN_SIGNATURE`,
find the matching LIVE/SHADOW rule (respecting the rule's `source` scope); if
`distinct_days ≥ min_distinct_days` **and** `event_count ≥ min_events` and no
active `DIAG_NOTIFICATION` latch:

- **USER + LIVE** → `MailServiceImpl.sendMail(...)` to `UserSettings.getEmail()`
  in the user's language, body from `DIAG_TEMPLATE`; record `SENT`.
- **USER + SHADOW** → record `PENDING` with `dry_run=true`; **send nothing**.
- **DEV** → append to a dev digest; never mails the user.
- **NONE** → silent count only.

Thresholds fire on **persistence** (distinct days), never single events — what
separates a sustained misconfiguration from transient noise. The latch is
one-shot per `(source, origin_id, rule)` with **clear-and-rearm**: when the
signature goes quiet for M days the notification is `CLEARED`, so a genuine
recurrence re-notifies. Mirrors the #456 firmware-update mail latch.

### Retention job (`SchedulerService`)

Purge `DIAG_SAMPLE` older than 30 days.

## Rule lifecycle & promotion

States: `DRAFT → SHADOW → LIVE`, plus `DISABLED`.

- An agent may create and move rules up to **SHADOW autonomously** — a shadow
  rule matches and records dry-run projections but **cannot mail anyone**. Needs
  only `accessDiagnostics`.
- **Promotion `SHADOW → LIVE` requires `accessAdmin`**, which the agent's token
  does not carry (this design starts with human promotion). A promote call from a
  diagnostics-only token hits the existing "privilege mismatch" rejection. Once
  LIVE, mailing for that rule is fully autonomous under the safety caps.

This gives requirement (a)'s autonomous mailing while keeping a person on the one
irreversible, outward-facing step — enforced by the capability bit, not policy.

## Authentication & access

No new auth stack. `AuthToken` already carries **per-capability boolean scopes**
(`accessQuery`, `accessDownload`, `accessCarddav`, `accessRate`, `accessLogin`),
and every endpoint self-gates via `LoginFilter.checkTokenAuthorization(...)`,
rejecting a mismatch with the existing "privilege mismatch" log. We slot in two
flags (new booleans on the msgbuf `AuthToken` + columns):

- **`accessDiagnostics`** — read, dry-run, author rules/templates/scrub in
  DRAFT/SHADOW. The agent / MCP-wrapper token carries this.
- **`accessAdmin`** — the elevated capability: `SHADOW → LIVE` promotion,
  enabling autonomous mail, and (deliberately generic) any future admin action
  across the app. The agent token does **not** carry this.

The agent-authors-freely / human-promotes boundary is a capability bit the
agent's token lacks, not a policy the server must remember.

**Minting is a direct DB call — intentionally.** No UI to create a
diagnostics/admin token, no per-user "admin" role. Mint an ordinary token in the
settings UI, then flip `accessDiagnostics` / `accessAdmin` on that row directly
in the DB. The trust root is "who can reach the DB." The token stays owned by its
user account, so authored rules are attributed via `getAuthenticatedUser`.

**The UI only *displays* scope, never grants it.** The settings token list
renders `accessDiagnostics` / `accessAdmin` as read-only badges so such a token
is recognizable; there is no create-with-elevated-scope control.

**Internet-accessible by design.** Agents run on the operator's machine, not on
prod, so the REST interface is reachable over the public `phoneblock.net` TLS
endpoint — the capability-scoped bearer token is the sole gate (no localhost
binding). Consequences, and why acceptable:

- The `accessAdmin` token is effectively the "enable mass mail to the fleet" key.
  Its blast radius is bounded not by network isolation but by the
  [safety rails](#safety-rails-non-negotiable): per-user + global daily mail
  caps, one-shot latches, and the global kill switch hold even if it leaks. Treat
  it as a secret; make it independently rotatable (revoke row, mint new).
- Both tokens ride existing `LoginFilter` throttling and are fully audited —
  every authored change a versioned row, every mail a `DIAG_NOTIFICATION` row.
- `accessDiagnostics` grants read of (scrubbed) fleet error data; a secret, but
  lower-stakes than `accessAdmin`.

## Agent introspection interface (REST, optionally wrapped as MCP)

The interface is a **plain authenticated REST API** — the primitive — callable
from `curl`, a scheduled agent, or a future admin page. An **MCP server is an
optional thin local wrapper** that shells out to those endpoints with the agent's
token; the server never depends on MCP. Because the wrapper is configured with a
diagnostics-only token, the promote path is simply absent from its toolset.

**Conventions.** Base path `/api/diag`. Bearer token (`Authorization: Bearer …`),
`Accept: application/json`. Reads/dry-runs gate on `accessDiagnostics`; the one
elevated transition (→ `LIVE`) gates on `accessAdmin` — enforced in each
servlet's `checkTokenAuthorization`. Writes are non-`GET`, so errors use
`ServletUtil.sendMessage` (per the repo's rule), never `sendError`. `source` is
an optional query filter everywhere (default: all).

### Endpoints

**Read / discover** — `accessDiagnostics`

| Method & path | Purpose | MCP tool |
|---|---|---|
| `GET /api/diag/signatures?source=&matched=false&minOrigins=&since=&limit=&offset=` | The unmatched long tail (`category IS NULL` when `matched=false`), ranked by origin-count × recency | `list_unmatched_signatures` |
| `GET /api/diag/signatures/{sigId}` | One signature: stats + capped scrubbed samples | `signature_detail` |
| `GET /api/diag/origins/{source}/{originId}/timeline?since=` | One origin's signatures over time | `origin_timeline` |
| `GET /api/diag/rules?source=&state=` | List rules | `list_rules` |
| `GET /api/diag/rules/{id}` | One rule | — |
| `GET /api/diag/rules/{id}/stats?window=` | Per-rule hit stats (incl. shadow projections) | `rule_hit_stats` |
| `GET /api/diag/notifications?source=&ruleId=&state=&since=` | Sent/pending mail audit | — |
| `GET /api/diag/ingest/status` | Reader health: cursor position, lag, last run | — |

**Experiment (no side effects)** — `accessDiagnostics`

| Method & path | Body | Returns | MCP tool |
|---|---|---|---|
| `POST /api/diag/rules/dryrun` | `{source?, matchTag?, matchRegex, minDistinctDays, minEvents, window}` | `{matchingOrigins, matchingUsers, projectedMailsPerWeek, sampleLines[]}` | `dryrun_rule` |
| `POST /api/diag/mail/preview` | `{templateId, originId?}` | `{subject, body, lang}` | `preview_mail` |
| `POST /api/diag/scrub/audit` | `{source?, candidatePattern?}` | `{matches[]}` — retained samples still matching PII shapes | `audit_samples` |

**Author (lands in DRAFT/SHADOW)** — `accessDiagnostics`

| Method & path | Purpose | MCP tool |
|---|---|---|
| `POST /api/diag/rules` · `PUT /api/diag/rules/{id}` | Create (DRAFT) / update a detection rule | `upsert_rule` |
| `POST /api/diag/templates` · `PUT /api/diag/templates/{id}` | Create / update a mail template | `upsert_template` |
| `POST /api/diag/scrub` · `PUT /api/diag/scrub/{id}` | Create / update a scrub rule — *tightening* applies live; *relaxing* returns `202` pending human review | `upsert_scrub_rule` |
| `POST /api/diag/rules/{id}/state` | Body `{state}`. `DRAFT`↔`SHADOW`, `DISABLED` allowed here | `set_rule_state` (≤ SHADOW) |

**Promote / admin** — `accessAdmin`

| Method & path | Purpose | MCP tool |
|---|---|---|
| `POST /api/diag/rules/{id}/state` with `{state: "LIVE"}` | The one elevated transition — same endpoint, `LIVE` target requires `accessAdmin`; a diagnostics-only token gets the standard privilege-mismatch rejection | *(not in the agent wrapper)* |
| `POST /api/diag/killswitch` | Body `{enabled}` — global mail cutoff | — |

The `set_rule_state` endpoint is shared: the *target state* selects the required
capability (`LIVE` → `accessAdmin`, everything else → `accessDiagnostics`), so the
human/agent split needs no separate route.

### The self-updating loop

A scheduled Claude agent (existing `/schedule`) periodically pulls the unmatched
tail for a source, clusters it, drafts a rule + localized template, dry-runs it
against history to show projected mail volume, and parks it in SHADOW. A human
reviews the projection and promotes. The workflow evolves without touching server
code — requirement (b).

## Safety rails (non-negotiable)

- Persistence thresholds (distinct days), never single events.
- One-shot latch per `(source, origin_id, rule)` with clear-and-rearm.
- Per-user daily cap **and** global daily mail cap (across all sources).
- SHADOW bake for every new rule (projection before it can mail).
- Global kill switch + per-rule `DISABLED`.
- `actor` routing: `DEV` and `NONE` rules can never mail a user; a source with no
  `user_id` can only ever match `DEV`/`NONE`.
- Full audit: every rule/scrub/template change versioned; every mail a
  `DIAG_NOTIFICATION` row.

## Reuse map

| Concern | Existing asset |
|---|---|
| Dongle line source (already logs) | `LogReportServlet.doPost` (unchanged — its `LOG.warn` is the feed) |
| Log-reader / signature normalization spec + oracle | `phoneblock-tools/bin/pb-log-summary.sh` (persist aggregates instead of print) |
| Scheduled reader / match / retention jobs | `SchedulerService` |
| Send + i18n (recipient, language) | `MailServiceImpl.sendMail`, `UserSettings.getEmail()`; existing `help-mail` precedent |
| Hot reload on version bump | `BlocklistVersionService` (`PROPERTIES` version pattern) |
| One-shot latch / debounce | #456 firmware-update mail |
| Auth capabilities + per-endpoint gate | `AuthToken` scopes, `LoginFilter.checkTokenAuthorization` |
| DB / mappers | `DBService`, MyBatis XML mappers, `db-schema.sql` + numbered migration |

## Phased rollout

1. **Log reader + dongle recognizer, persist only (no mail).** The
   `SchedulerService` reader (cursor, batched upserts), scrub + signature +
   aggregation tables, and the dongle recognizer; the `pb-log-summary`
   normalization reimplemented in Java with a parity test against the script.
   `LogReportServlet` is untouched — its existing `LOG.warn` is already the feed.
   Replaces log-only with structured, queryable storage.
2. **REST introspection (read + dry-run + author).** Human explores the tail and
   builds the first rules in SHADOW. Optional local MCP wrapper.
3. **Matcher in SHADOW-only.** Projections against live data validate thresholds;
   still no mail.
4. **Promote first rules to LIVE.** Autonomous mailing begins under caps.
5. **Client scrub subset** baked into the next manual dongle release.
6. **Second source** (the server's own tinylog WARN/ERROR, or the mobile app) —
   proves source-agnosticism; just a new recognizer + `source` value, no schema
   change and no new ingest path (the reader already tails that same log).

**Deferred:** dynamic scrub-rule sync to clients; bounded auto-promotion of rules
(both revisitable once human-promotion has a track record).

---

## Appendix A: first adapter — dongle corpus

24h window, 2026-07-09 → 07-10, 569 error lines, 24 devices (fw 1.4.0 + 1.4.1).
Categorized by *who must act* (this becomes the initial `DONGLE`-scoped rule set):

| Bucket | Example signatures | ≈Count | `actor` |
|---|---|---|---|
| **User install problem** | `sip: REGISTER rejected: 4xx (check user/extension)`, `DNS lookup of fritz.box/tel.t-online.de failed`, wifi `entering failsafe` | ~85 | `USER` |
| **Environmental / transient** | `wifi: disconnected (reason N)` (158), `re-REGISTER … no response from registrar (timeout)` | ~250 | `NONE` |
| **Firmware / server bug** | `api: rate: HTTP 400`, `sync: rate failed, keeping in Fritz!Box`, `phy_init checksum failure` | ~100 | `DEV` |
| **Internet scan noise** | `httpd_uri: Method not allowed for /boaform/… /shelly /HNAP1/` | ~65 | `NONE` |

Two standouts that motivate the two mail sinks:

- **A device with 32× `REGISTER rejected: 400`** and nothing else — a textbook
  fixable install problem (wrong SIP user/extension) the user is never told about
  → the `USER` help-mail path.
- **A device with 46× `api: rate: HTTP 400` + 46× `sync: rate failed`** (~16% of
  the corpus from one client), POSTing `/rate` bare digit strings the API rejects
  → the `DEV` digest path, not a user mail.
