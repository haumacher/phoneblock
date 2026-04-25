# Dongle Pairing Secret — Browser-to-Dongle Trust Anchor

**Goal:** Eliminate hostname/mDNS guesswork after the browser flashes the dongle. The browser injects a per-install secret into the dongle during flash; the dongle phones home with `(secret, local IP)`; the setup page polls and redirects directly to that IP.

**Why this works:** The trust relationship is established at the moment the user is flashing — same browser, same phoneblock.net session, no manual entry, no sticker, no extra hardware. CGNAT, mDNS-disabled networks, and Fritz!Box hostname-pinning all become irrelevant.

## Flow

1. Browser on `https://phoneblock.net/.../dongle-install` loads the page.
2. Page generates `secret = crypto.getRandomValues(16)`, stores it in `sessionStorage["pairingSecret"]` (hex).
3. Page fetches the static upstream manifest (`https://cdn.phoneblock.net/dongle/firmware/stable/manifest.json`), appends one extra part — `pairing.bin?secret=<hex>` at offset `0x12000`, with an absolute URL to the current webapp context (`/phoneblock/...` in prod, `/pb-test/...` for test builds) — and hands the result to esp-web-tools as a `Blob` object URL.
4. esp-web-tools flashes the four static parts straight from the CDN plus the per-install `pairing.bin` from phoneblock.net.
5. Flash completes, dongle reboots.
6. Firmware reads the `pairing` partition. If magic + CRC are valid, fires an async task that POSTs `{secret, ip}` to `/api/dongle/register`.
7. Server records `secret → (local_ip, ts)` in an in-memory map (TTL 30 min).
8. Browser polls `GET /api/dongle/lookup?secret=<hex>` every 2 s up to ~90 s. On hit → `location.href = "http://" + ip + "/"`.
9. On timeout or missing pairing partition (OTA-only dongles), fall back to the existing mDNS / manual-IP UI.

## Partition layout

New 4 KB `pairing` partition between `phy_init` and `ota_0`. App-slot offsets unchanged → no impact on OTA compatibility; only fresh USB-flashes write the partition.

`phoneblock-dongle/firmware/partitions.csv`:

```
nvs,        data, nvs,     0x9000,    0x6000
otadata,    data, ota,     0xf000,    0x2000
phy_init,   data, phy,     0x11000,   0x1000
pairing,    data, 0x40,    0x12000,   0x1000
ota_0,      app,  ota_0,   0x20000,   0x170000
ota_1,      app,  ota_1,   0x190000,  0x170000
storage,    data, spiffs,  0x300000,  0x100000
```

Subtype `0x40` = custom data; label `pairing` is what the firmware looks up.

## Pairing-partition format

4 KB partition, first 32 bytes used:

```
offset  0  uint32  magic   = 0x504B5042  ("PBPK", little-endian on flash)
offset  4  uint16  version = 1
offset  6  uint16  length  = 16
offset  8  uint8[16] secret
offset 24  uint32  crc32   over bytes 0..23
offset 28  4 bytes reserved (0xFF)
```

Rest of the 4 KB stays 0xFF. An OTA-only dongle has 0xFF everywhere → magic mismatch → loader returns "no secret", firmware silently skips registration.

## Firmware (`phoneblock-dongle/firmware/main/`)

New module `pairing.c` / `pairing.h`:

- `bool pairing_load(uint8_t out[16])` — finds partition via `esp_partition_find_first(ESP_PARTITION_TYPE_DATA, 0x40, "pairing")`, reads first 28 bytes, validates magic + version + CRC32. Returns false on any mismatch.
- `void pairing_register_async(const uint8_t secret[16])` — copies the secret onto the heap and starts a one-shot task that POSTs `{"secret":"<hex>","lanIp":"<local_ip>"}` to `https://phoneblock.net/phoneblock/api/dongle/register` over TLS. Retries with exponential backoff (1 s, 4 s, 15 s, 60 s, give up). Frees the heap copy when done.

Hook in `main.c` `app_main()` after `web_start()`, before SIP work:

```c
uint8_t secret[16];
if (pairing_load(secret)) {
    pairing_register_async(secret);
}
```

Must not block boot or affect the recovery UI — this is purely additive.

## Server (`phoneblock/`)

New package `de.haumacher.phoneblock.dongle.pairing`. All endpoints under `/phoneblock/`:

| Endpoint | Caller | Purpose |
| --- | --- | --- |
| `GET /dongle/pairing.bin?secret=<hex>` | esp-web-tools | 4 KB binary blob in the format above. `Content-Type: application/octet-stream`, `Cache-Control: no-store`. |
| `POST /api/dongle/register` | dongle | Body `{"secret":"<hex>","lanIp":"<lan_ip>"}`. Server stores `secret → (lan_ip, now)`. Rate-limited per public IP (anti-abuse only, no trust). Returns 204. |
| `GET /api/dongle/lookup?secret=<hex>` | browser | Returns `{"ip":"<lan_ip>"}` iff a registration with this secret exists. Otherwise 404. |

State: `ConcurrentHashMap<String, PairingEntry>` plus a scheduled cleaner (e.g. `SchedulerService`) that drops entries older than 30 minutes. Single-VM, no DB persistence — short-lived discovery state, not an asset worth surviving a restart.

Secret validation everywhere: regex `^[0-9a-f]{32}$`. Anything else → 400.

Trust model: 128-bit unguessable secret + 30-minute TTL. The looked-up `lan_ip` is a private-range address that is not routable from outside the user's LAN, so even a leaked secret discloses no externally actionable information.

### Manifest assembly (browser-side)

The static manifest stays on the CDN at `cdn.phoneblock.net/dongle/firmware/stable/manifest.json` exactly as today; the release pipeline is unchanged. The install page fetches it, appends one extra `parts` entry for the per-install `pairing.bin` (absolute URL into the current webapp context, e.g. `https://phoneblock.net/phoneblock/dongle/pairing.bin?secret=<hex>`), wraps the result in a `Blob`, and hands `URL.createObjectURL(blob)` to `<esp-web-install-button manifest=…>`. esp-web-tools then fetches the four static parts directly from the CDN and the one dynamic part from phoneblock.net.

Reason for browser-side assembly: the only dynamic input is a per-session secret that already lives in the browser; routing the manifest through phoneblock.net would force an avoidable upstream-fetch round trip on the server with no extra capability to show for it. The server-side pairing.bin endpoint is the only new origin involved.

## Private Network Access (PNA)

Chrome 130+ and current Edge enforce PNA on top-level navigations from a public origin (`https://phoneblock.net`) to a private IP target (`http://192.168.x.x/`): the browser sends a CORS preflight `OPTIONS` to the dongle with header `Access-Control-Request-Private-Network: true`. The navigation only proceeds if the dongle answers with `Access-Control-Allow-Private-Network: true` and matching `Access-Control-Allow-Origin`.

Required dongle-side changes in `phoneblock-dongle/firmware/main/web.c`:

- Register a global `HTTP_OPTIONS` handler (or per-route, but global is cleaner) that returns 204 with these headers when `Access-Control-Request-Private-Network: true` is present:
  ```
  Access-Control-Allow-Origin: https://phoneblock.net
  Access-Control-Allow-Private-Network: true
  Access-Control-Allow-Methods: GET, POST, OPTIONS
  Access-Control-Allow-Headers: Content-Type
  Access-Control-Max-Age: 600
  Vary: Origin, Access-Control-Request-Private-Network
  ```
- On all regular `GET`/`POST` responses from the root path, also send `Access-Control-Allow-Origin: https://phoneblock.net` and `Access-Control-Allow-Private-Network: true` so the browser accepts the navigation result without a follow-up failure.
- Origin allowlist is exact-match against `https://phoneblock.net`. No wildcards — the dongle's web UI is for the local owner only, the PNA opt-in is purely to make the redirect from the official install page work.

Same-origin polling on `https://phoneblock.net/phoneblock/api/dongle/lookup` is HTTPS-to-HTTPS same-origin and not affected.

## Browser (`dongle-install.html`)

Edit only `phoneblock/src/main/webapp/WEB-INF/templates/de/dongle-install.html` — auto-translate regenerates the rest.

Replace the hard-coded `manifest=…` attribute with runtime initialisation:

```html
<esp-web-install-button id="pb-installer"></esp-web-install-button>
<script>
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  const secret = [...bytes].map(b => b.toString(16).padStart(2, '0')).join('');
  sessionStorage.setItem('pairingSecret', secret);
  document.getElementById('pb-installer').setAttribute(
    'manifest',
    `/phoneblock/dongle/manifest.json?secret=${secret}`
  );
</script>
```

Listen for the `state-changed` event; when state transitions to `FINISHED`, start polling:

```js
async function pollDongle() {
  for (let i = 0; i < 45; i++) {                    // 45 × 2 s ≈ 90 s
    const r = await fetch(`/phoneblock/api/dongle/lookup?secret=${secret}`);
    if (r.ok) {
      const { ip } = await r.json();
      location.href = `http://${ip}/`;
      return;
    }
    await new Promise(res => setTimeout(res, 2000));
  }
  showFallbackInstructions();                       // existing mDNS/manual-IP block
}
```

The fallback block (`http://answerbot/`, `http://answerbot.local/`, manual IP) stays — it's the recovery path for OTA-only dongles, VPN'd browsers, or any failure.

## Implementation tasks

1. **Partition + firmware loader.** `partitions.csv`, `pairing.c/.h` with parser + CRC32, unit test for parse-success and parse-fail-on-erased-flash.
2. **Firmware register task.** HTTPS POST with retries; reuses the existing TLS bundle the rest of the firmware uses for phoneblock.net.
3. **Server pairing.bin generator endpoint.** Pure byte-buffer generation matching the firmware on-flash layout, ~30 lines.
4. **Server register + lookup endpoints.** `ConcurrentHashMap`, scheduled cleanup, public-IP guard, rate limit.
5. **Dongle PNA preflight handler** in `web.c` — global `OPTIONS` route plus PNA/ACAO headers on the regular routes that the browser may navigate to.
6. **Browser changes** in `de/dongle-install.html` plus a Messages_de.properties entry for any new UI strings (then run translate plugin).
7. **Manual smoke test:** USB-flash a dongle from the dev page in Chrome and Edge, verify the PNA preflight succeeds and the redirect happens within ~30 s of `FINISHED`. Then verify OTA path: re-flash an existing dongle via OTA (no pairing partition write), confirm fallback UX kicks in cleanly after timeout.

## Non-goals / accepted limitations

- **OTA dongles get no secret.** Intentional — pairing only solves first-time discovery; already-paired dongles don't need it.
- **Cross-VM persistence** not implemented. Server restart drops in-flight pairings; users see fallback. Acceptable given the 30-min window.
- **Not an auth mechanism.** The secret only routes the browser to the right LAN IP; the dongle's web UI handles its own access control as it does today.
