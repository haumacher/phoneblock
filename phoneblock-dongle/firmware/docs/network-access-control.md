# Network access control — two-mode gate

Status: implemented. Supersedes the "bind to LAN / drop non-RFC1918
peers" idea floated as recommendation #3 in issue #470 (a naive peer-IP
check silently no-ops behind a reverse proxy — see below).

> **⚠️ The dongle must never be made reachable from the internet.** It is
> a local-home-network device. Everything below is *defensive safety* for
> the user who exposes it by accident (a stray port-forward, a DMZ host) —
> not a blueprint for deploying it publicly. Where this note describes the
> authenticated / reverse-proxy mode, that is documentation of what the
> gate does under those conditions, **not** a recommendation to run the
> dongle that way.

## Design note

### The problem

The dongle serves its whole configuration + firmware surface over plain
HTTP on port 80. It was never meant to face the internet, but field
telemetry shows some users port-forward it or run it behind a reverse
proxy. On a default device the SSO gate is **off**, which means every
endpoint — including `POST /api/firmware` (arbitrary OTA) and
`POST /api/factory-reset` — is open to whoever can reach port 80.

We want a model that is *safe by default for the convenient case* (no
auth, internal use) and *safe when deliberately exposed* (auth on),
with no footgun in between.

### The two modes

The behaviour is derived from a single existing signal — whether the
SSO gate is enabled (`config_auth_enabled()`). No new configuration
knob:

| Gate | Local client | Remote client |
|------|--------------|---------------|
| **off** (default) | **served** | **refused** (403) |
| **on**  | must log in (session cookie) | must log in (session cookie) |

- **Gate off ⇒ LAN-only.** The convenient default keeps working for
  everyone on the home network and cannot answer a remote peer at all.
  Accidentally port-forwarding the dongle no longer exposes anything —
  remote requests get a 403 that tells the user to enable authentication.
- **Gate on ⇒ authenticated, from anywhere.** Once the user has
  deliberately turned on auth (pinned to their PhoneBlock account,
  trust-on-first-use), remote access is allowed *because* every request
  now needs a valid session tied to that account. This is the least-bad
  configuration *if* someone ignores the warning and puts the dongle
  behind an HTTPS-terminating reverse proxy — it is not an endorsement of
  doing so.

### Why a naive peer-IP check is not enough (the proxy problem)

"Refuse non-local peers" is trivial to write with `getpeername()`, and
it is correct for a *direct* connection: a completed TCP handshake
proves the source address (a spoofed-source SYN never completes, so no
request arrives). On-path attackers and source routing are the only
escapes, and neither applies on a home LAN.

But behind a reverse proxy **every request's peer address is the
proxy** — typically `127.0.0.1` or a LAN address. A peer-IP check then
sees *all* traffic as local and would happily answer remote clients the
proxy forwards. The real client address survives only in the
`X-Forwarded-For` / `Forwarded` headers, which a direct client can forge.

### The resolution — trust forwarding headers only from a local peer

The key move (and the crux of the model's soundness):

> **Honor `X-Forwarded-For` / `Forwarded` only when the immediate TCP
> peer is itself local. Ignore them entirely when the peer is remote.**

Each branch is sound:

- **Remote peer → ignore the headers → treat as remote.** A remote
  attacker cannot make their peer address local (TCP), and we refuse to
  read their forwarding headers, so they can never reach the "local"
  verdict — regardless of what they put in `X-Forwarded-For`.
- **Local peer + forwarding header → trust it.** The only way a request
  arrives with a local peer *and* a forwarding header is if a local
  process forwarded it, and on the trusted LAN that process is the
  user's own proxy.

The real payoff is that this **prevents the proxy from becoming a
confused deputy.** Without header trust, a proxy forwarding a *remote*
client makes every request look local, and a gate-off dongle would
answer the remote client. *With* it, the dongle reads the proxy-supplied
real client address, sees it is remote, and refuses. So header-trust is
exactly what preserves "gate off ⇒ local only" in the presence of a
forwarding proxy.

A **malicious LAN device** gains nothing by forging headers: it is
already local, so it is already served when the gate is off; forging a
header can only make it look *remote* and refuse itself. The forwarding
address is therefore used **only** for the local/remote decision and for
audit logging — never as an authorization principal or allowlist key.

### Residual assumption

While the gate is off, the trust boundary is "the LAN." A device already
on the LAN has local access by definition, and (per above) cannot
escalate through the forwarding headers. A user who runs the dongle on a
segment shared with untrusted devices (bridged guest Wi-Fi, a
compromised IoT gadget) and leaves the gate off is trusting those
devices — the answer for that user is to enable authentication, which
routes to the safe "authenticated from anywhere" branch.

One deployment caveat, documented for operators: a reverse proxy in
front of a **gate-off** dongle **must** forward the real client address
in `X-Forwarded-For`/`Forwarded`. A proxy that forwards without it makes
remote clients indistinguishable from a direct local client, and they
will be served. The robust recommendation is simply: **if you expose the
dongle through a proxy, enable authentication** — then locality is moot,
because every request is session-gated.

## Spec

### Address normalization

All addresses are normalized to a 16-byte IPv6 representation. IPv4
addresses (whether from a real `AF_INET` peer or, more commonly, a
dual-stack `AF_INET6` socket delivering `::ffff:a.b.c.d`) are stored in
**IPv4-mapped** form and unwrapped before classification. This makes one
classifier cover both families and correctly handles the mapped form the
LWIP dual-stack listener actually delivers.

### "Local" classification

An address is local if **any** of the following hold (checked after
unwrapping v4-mapped):

IPv4:
- loopback `127.0.0.0/8`
- private `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`
- link-local `169.254.0.0/16`
- **same subnet as the dongle's own IPv4** (`addr & mask == own & mask`),
  from `esp_netif_get_ip_info()`

IPv6:
- loopback `::1`
- link-local `fe80::/10`
- unique-local `fc00::/7`
- **same prefix as any of the dongle's own IPv6 addresses**
  (`esp_netif_get_all_ip6()`, matched on the address's prefix length,
  defaulting to /64)

The same-subnet/same-prefix checks are what make the model correct on
IPv6 home LANs, where devices commonly hold globally-routable (GUA)
addresses that no static private-range list would recognize. CGNAT
shared space (`100.64.0.0/10`) is deliberately **not** local.

Anything else — including any address the classifier cannot parse — is
**remote**. Fail closed.

### Effective-client derivation (`web_client_is_local`)

1. Read the immediate peer via `httpd_req_to_sockfd()` +
   `getpeername()`. If that fails → **not local** (fail closed).
2. If the peer is **not** local → **not local** (ignore all forwarding
   headers).
3. Peer is local. Look for a forwarding header, in order
   `X-Forwarded-For`, then `Forwarded`:
   - **Header present and parseable** → the parsed client's own locality
     is the answer (a local peer forwarding a remote client ⇒ remote).
   - **Header present but unparseable** → **not local** (fail closed: a
     proxy is clearly in play and we could not establish the client).
   - **No forwarding header** → **local** (a direct client on the LAN).

### Forwarding-header parsing

- `X-Forwarded-For: client, proxy1, proxy2` — take the **rightmost**
  comma-separated token. Rationale: each proxy *appends* the address it
  received from, so with a single trusted proxy the rightmost entry is
  the real client, and a client that pre-seeds a fake leftmost value
  cannot influence it. (Multi-proxy chains are out of scope — the home
  deployment has one reverse proxy; documented, not silently mishandled.)
- `Forwarded: for=..., for=...` (RFC 7239) — take the **last** `for=`
  value; strip quotes and `[...]` brackets; strip a trailing `:port` only
  for the bracketed-v6 and dotted-v4 forms (a bare IPv6's colons are part
  of the address). Parse case-insensitively.
- Both parsers return the address in v4-mapped-normalized form and
  **reject** anything `inet_pton` cannot read (fail closed).

### The choke point

Locality is enforced in exactly two shared helpers so no route can
forget it:

- `web_auth_required(req, is_api)` — used by every sensitive handler via
  the existing `REQUIRE_AUTH_API`/`REQUIRE_AUTH_HTML` macros. New leading
  behaviour: when the gate is **off**, allow only if
  `web_client_is_local(req)`; otherwise reject. When the gate is **on**,
  the unchanged session check applies (remote allowed with a valid
  cookie).
- `web_public_allowed(req, is_api)` — for the intentionally public routes
  (`/`, `/favicon.ico`, `/ab-logo-bot.svg`, `/auth/start`,
  `/auth/callback`, `/auth/logout`, and the OPTIONS PNA preflight). Fully
  public when the gate is **on** (the login flow must be reachable from
  wherever the user browses); LAN-only when the gate is **off** (so a
  remote scanner cannot even fingerprint the device or begin an
  activation it could never complete).

Both call `web_client_is_local` and, on refusal, emit a `403 Forbidden`:
a small JSON body `{"ok":false,"reason":"local_only"}` for API routes, or
a short HTML page pointing the user at "enable authentication" for HTML
routes.

### What is intentionally unchanged

- No new NVS field: the mode is a pure function of `config_auth_enabled()`.
- Gate-on local users still authenticate — "local" is never an auth
  bypass, only a reachability gate while the gate is off.
- The forwarding address never grants access; it only ever moves a
  verdict from local to remote (i.e. can only *reduce* access).

### Testability

The classifier and both header parsers are pure functions in
`net_local.c` (no ESP-IDF dependencies) so they run under the host test
harness (`test/test_net_local.c`): v4-mapped unwrap, each private/local
range, same-subnet, GUA-on-LAN via injected self-prefix, CGNAT-is-remote,
rightmost-XFF selection, client-spoof defeat, RFC 7239 `for=` with
brackets/ports, and malformed → fail-closed. The httpd/netif glue in
`web_access.c` is thin and not host-tested.
