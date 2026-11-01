# PhoneBlock sign-in through Home Assistant ingress: device-code link (#621)

Status: design, implemented on branch `issue-621-device-code-link`.
Background: #619 (the three obstacles), #617 (Linux service), #622 (sign-in in a new tab).

## Problem

The dongle's sign-in ends with phoneblock.net redirecting the browser to
`http://<host>/token-callback`. Behind Home Assistant ingress that redirect cannot arrive:

1. `LoopbackCallbacks.validate` refuses custom LAN domains (`homeassistant.3a`), `https` and
   remote hosts (Nabu Casa, DuckDNS). The allowlist stays: it keeps a token off public hosts.
2. Even with an allowed host (HA by LAN IP), ingress answers the cross-site navigation coming
   from phoneblock.net with `401`: its session cookie is `SameSite=Strict` and is not sent.

Today the Linux service hides the sign-in under ingress (`token_signin: false`) and the HA app
takes the token as an app option.

## Decision

An RFC 8628-style device-code flow, **only** when the request reaches the Linux service with an
`X-Ingress-Path` header. Without the header, and always on the ESP32, the redirect flow runs
unchanged. Token delivery goes from phoneblock.net to the *service* (server-to-server poll), so
nothing has to travel back through the browser and the ingress cookie no longer matters.

The browser only ever sees the user code. The device code (256 bit) stays between service and
server.

## Server API (phoneblock.net)

Both endpoints are anonymous (no bearer token — the device has none yet), JSON in, JSON out.

### `POST /api/link/start`

Request: `{"appId": "PhoneBlockDongle", "label": "PhoneBlock-Dongle"}` (`label` optional, ≤ 64
chars, shown on the confirmation page and becomes the token label).

Response `200`:

```json
{
  "deviceCode": "<43 chars base64url, 256 bit>",
  "userCode":   "BCDF-GHJK",
  "verifyUrl":  "https://phoneblock.net/phoneblock/link?code=BCDF-GHJK",
  "interval":   5,
  "expiresIn":  600
}
```

`429` with a message when a limit is hit (per client IP: 20 open links — retries and several households behind one CGNAT address must not lock a user out, there is no cancel; total: 10 000 open links).

- User codes: 8 letters from the RFC 8628 consonant alphabet `BCDFGHJKLMNPQRSTVWXZ`, shown as
  `XXXX-XXXX`; input is normalized (case, dashes, spaces), unique among open entries.
- `verifyUrl` is built from the server's configured public base URL, not from the request's
  `Host` (the service may reach the server under an internal name).

### `POST /api/link/poll`

Request: `{"deviceCode": "…"}`. Response `200`: `{"status": "pending"}`, `{"status": "denied"}`,
`{"status": "expired"}` (also for an unknown or already collected code), or
`{"status": "approved", "token": "<api token>"}`.

- The token is minted **at pickup** with `createAPIToken(user, now, <poll User-Agent>, label)`,
  so an approval nobody collects leaves no token behind. The entry is removed on pickup: single
  use; a second poll answers `expired`.
- Polling faster than `interval` answers `{"status": "slow_down"}` (RFC 8628 §3.5); the client
  adds 5 s to its interval.

### `/link` (browser page, login required)

- Not logged in: the normal login (login forms, then back to `/link?code=…`).
- `GET /link?code=…`: shows the account name, the device label and the code in large type with
  the instruction to compare it with the code on the device and to confirm only a link the user
  started; buttons **[Verknüpfen]** / **[Ablehnen]** (POST, CSRF-protected like the other forms).
- `GET /link` without code (or an unknown code): a code entry field — works on a second device
  (phone) when the browser that shows the code cannot open the tab.
- After either button: a result page that calls `window.close()` and otherwise tells the user
  to return to Home Assistant.
- Wrong codes are rate-limited per session/IP (brute force over 20⁸ codes is hopeless anyway,
  the limit keeps logs clean).

State is in memory (`ConcurrentHashMap`, 10-minute entries, swept on access and by the
scheduler), no migration. A server restart loses open links — the user starts again.

Old firmware, the mobile app and third-party apps keep using `/mobile/login` → `/create-token`.

## Linux service (dongle firmware, `#ifndef ESP_PLATFORM`)

- `/api/status` adds `phoneblock.token_link: true` when the request carries `X-Ingress-Path` on
  the Linux build (`token_signin` stays `false` there). Old UIs ignore the field.
- `POST /api/link/start` (local, auth as every API route): calls the server's start, keeps the
  device code in RAM, starts one background poll task, answers
  `{"userCode", "verifyUrl", "expiresIn"}`. A second start cancels the running link.
- `GET /api/link/status`: `{"state": "idle|pending|approved|denied|expired|error",
  "userCode", "verifyUrl", "message"}`.
- The poll task polls every `interval` s (honouring `slow_down`) until a terminal state or
  expiry. On `approved` it runs **the same post-token steps as `/token-callback`** — one shared
  function: store the token, self-test, seed the country once. WARN on `denied`/`error`, so the
  reason shows in the log panel.
- Web UI, when `token_link` is set: the PhoneBlock step shows **PhoneBlock-Account verknüpfen**;
  the click opens a blank tab synchronously (popup blockers), posts `api/link/start`, points the
  tab to `verifyUrl`, shows the user code large plus the link and "or enter the code at
  phoneblock.net/link", then polls `api/link/status` until done. Denied/expired/error show their
  reason and a retry button.

## Ingress test setup ("cookie-swallowing firewall")

`phoneblock-dongle/docker/ingress-test/`: a Compose setup that reproduces #619 without Home
Assistant:

- `ingress`: nginx imitating HA ingress. `/` is an HA-like shell page that sets the session
  cookie (`SameSite=Strict`) and shows the dongle UI in an iframe at
  `/api/hassio_ingress/<token>/`. Requests below that prefix without the cookie get `401`; with
  it they go to the service with `X-Ingress-Path: /api/hassio_ingress/<token>` and the prefix
  stripped. phoneblock.net's redirect back to the callback therefore fails exactly as in HA.
- `phoneblock`: the Linux service built **from the working tree** (the release image installs
  the APT package and cannot carry an unreleased change), unconfigured SIP, `base_url` pointing
  to the local Jetty (`mvn jetty:run`) or to pb-test.

Expected: the redirect flow is hidden/fails through the proxy, the device-code flow completes;
opening the service directly (without the proxy) still shows the redirect flow.

## Rejected

- Widening the callback allowlist: it is what keeps tokens off public hosts.
- IP-match between start and confirm: dual stack, CGNAT and remote HA access differ by
  definition.
- Device code flow everywhere: the redirect flow needs no code comparison and binds delivery to
  the LAN; it stays the default.

## Known cost

Code phishing: an attacker starts a link and talks a victim into confirming. Mitigated by the
label + code with the compare instruction, 10-minute expiry, single use, the deny button and the
revocable token (listed under the account's API keys with the label).
