# Partner report exchange (Nize) — design

Date: 2026-11-23

## Problem

A partner service (Nize, caller-ID app) offers to exchange spam reports mutually. Their feed
(`GET /api/v1/external/comments?from=<ms>&to=<ms>`) contains only reports made directly by their
own users, never imported data, so neither side echoes the other's data back. PhoneBlock must offer
the same: an incremental feed of its own users' reports, without imported votes.

PhoneBlock's vote counters (`NUMBERS.VOTES`, the EMA evidence columns, the rating tallies) add up all
sources, so the feed cannot be derived from them.

## Key observation

Every vote a PhoneBlock user casts already leaves a per-user row:

| Path | Per-user trace |
|---|---|
| web rating, `POST /api/rate` (mobile app, dongle), settings blacklist | `PERSONALIZATION` + `COMMENTS` (USERID, RATING, COMMENT) |
| Fritz!Box CardDAV block | `PERSONALIZATION` |

Imports never write `PERSONALIZATION`: meta search writes `COMMENTS` with `SERVICE` set and no
`USERID`, the FTC import writes only vote counters.

So **an exported report is a blocked `PERSONALIZATION` entry**, enriched from the user's
`COMMENTS` row — imports are excluded by construction and the vote aggregation stays untouched.

Out of scope: anonymous (captcha) web ratings — no user, no stable sender id.

## Privacy rule

Only spam reports are exchanged. Legitimate ("trustworthy") reports are neither exported nor
imported, and imported reports that are not counted are not stored either.

## Export

`GET /api/partner/reports?from=<ms>&to=<ms>` (bearer token with a dedicated partner access right,
not obtainable through the normal settings UI).

- Window clamped to `[today 00:00 UTC − 10 days, today 00:00 UTC]`, default the previous full
  UTC day; reports become visible the day after they are made.
- Source: blocked personal block-list entries (`PERSONALIZATION` with `BLOCKED = true`, no
  wildcards), windowed on their creation time (`CREATED`), for numbers with at least 2 votes (see
  below); left-joined with the newest `COMMENTS` row on (USERID, PHONE). `created` = `CREATED`,
  sorted by `created` ascending.
- Corroboration filter: a report is exported only if its number has at least
  `PartnerExport.MIN_VOTES = 2` votes in `NUMBERS.VOTES` (all sources, including imported votes)
  at feed-generation time; a number without a `NUMBERS` row is never exported. An exported report
  cannot be withdrawn, so a user's single test block (e.g. of the own number to try the filter)
  must not leave PhoneBlock. The filter is evaluated when the feed is requested; a report whose
  number has fewer than 2 votes at that time is simply not delivered (accepted, no promise that it
  shows up later).
- Response, same shape as the partner's feed:
  `{"comments": [{"id", "phone", "senderId", "created", "text", "score", "typeId"}]}`
  - `phone`: E.164 (`+49…`)
  - `senderId`: HMAC-SHA256(secret, userId), hex, truncated to 32 chars — not a plain hash, user
    ids are sequential and would be trivially reversible
  - `id`: HMAC-SHA256(secret, userId | phone | created), same format — stable
  - `score`: always 1
  - `typeId`: from the rating (table below); `text` only when the comment is non-blank; no `name`

## Import

Daily scheduled task (like the FTC import), polling the partner's previous full UTC day (and on
catch-up, the days since the last successful run, at most 10).

Classification of a partner report, rules in order:

1. `typeId == 2` (trustworthy) → skip
2. `score >= 3` → skip
3. `score` is 1 or 2 → import; category from `typeId`, missing or Unknown (1) → `B_MISSED`
4. no `score` and `typeId` in 3..11 → import with that category
5. anything else → skip

An imported report:

- is recorded in the ledger `EXTERNAL_REPORTS` (SOURCE, ID, PHONE, SENDER, CREATED, RATING);
  an existing (SOURCE, ID) means it was already imported → skip (overlapping or repeated windows
  are harmless),
- counts as one full vote at its `created` time (`processVotes` + rating tally), like every
  FTC complaint and every meta-search comment — decay handles aging, no per-sender refresh logic,
- creates a `COMMENTS` row (`SERVICE = 'nize'`, ID = partner id, no USERID) **only when its text
  is non-blank**,
- never touches `PERSONALIZATION`, so it can never reappear in our export.

The ledger keeps `SENDER` for later analysis (e.g. a per-sender cap) and allows reverting a
source's votes should the exchange end.

## Category mapping

| PhoneBlock rating | export typeId | partner typeId | import rating |
|---|---|---|---|
| B_MISSED | 1 Unknown | 1 Unknown | B_MISSED |
| C_PING | 10 Ping calls | 10 Ping calls | C_PING |
| D_POLL | 6 Survey | 6 Survey | D_POLL |
| E_ADVERTISING | 5 Aggressive advertising | 5, 9 | E_ADVERTISING |
| F_GAMBLE | 3 Sweepstakes | 3 Sweepstakes | F_GAMBLE |
| G_FRAUD | 8 Cost trap | 4, 7, 8 | G_FRAUD |
| — | — | 11 SMS spam | B_MISSED |
| A_LEGITIMATE | never exported | 2 | never imported |

A blacklist entry without a `COMMENTS` row exports as typeId 1.

## Configuration (JNDI)

- `exchange/secret` — HMAC secret for `id`/`senderId` (export disabled without it)
- `nize/enabled`, `nize/url`, `nize/token`, `nize/schedule/hour`, `nize/schedule/minute`
