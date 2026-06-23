# Dongle Time Synchronization — Wall Clock + Timezone

**Goal:** Give the dongle a real wall clock so the scheduler can run jobs at a
*specified time of day* (e.g. "send the status mail at 08:00 local"), not just on
relative intervals. Today everything is driven by `esp_timer_get_time()`
(microseconds since boot); there is no calendar time and SNTP is not enabled.

**Approach in one line:** Acquire UTC via SNTP with an auto-discovered server
(DHCP option 42 → default gateway → `pool.ntp.org`), apply a POSIX timezone
(default *Europe/Berlin*, optionally learned from the browser hitting the web
UI), and extend the scheduler with wall-clock "time-of-day" jobs alongside the
existing interval jobs. `esp_timer` keeps bridging drift between SNTP syncs.

---

## 1. New module: `time_sync.c` / `time_sync.h`

Owns clock acquisition and the timezone. Registered in `main/CMakeLists.txt`.

### Public API

```c
void   time_sync_start(void);          // wire SNTP, register IP-event callbacks
bool   time_sync_valid(void);          // true once the clock was set at least once
int64_t time_sync_now_epoch(void);     // UTC seconds since 1970, or 0 if !valid
void   time_sync_set_timezone(const char *posix_tz); // setenv("TZ")+tzset(), persist
```

### Server discovery (priority order)

Build the SNTP server list so lwIP tries, in order:

1. **DHCP option 42.** Enable in `sdkconfig.defaults`:
   ```
   CONFIG_LWIP_DHCP_GET_NTP_SRV=y
   ```
   and in the SNTP config:
   ```c
   esp_sntp_config_t cfg = ESP_NETIF_SNTP_DEFAULT_CONFIG("pool.ntp.org");
   cfg.start                      = false;   // wait until we have an IP
   cfg.server_from_dhcp           = true;    // adopt option-42 servers
   cfg.index_of_first_server      = 1;       // DHCP fills index 0, pushes fallback down
   cfg.renew_servers_after_new_IP = true;
   cfg.ip_event_to_renew          = IP_EVENT_STA_GOT_IP;
   esp_netif_sntp_init(&cfg);
   ```
2. **Default gateway.** In the `on_got_ip` path, read `ip_info.gw` (already
   available in `wifi.c` — see `esp_netif_get_ip_info(s_sta_netif, …)` at
   `wifi.c:363`), format it, and register it as an explicit server via
   `esp_sntp_setservername(1, gw_str)` (or rebuild the list). The Fritz!Box —
   PhoneBlock's dominant target — always runs an NTP server on its LAN address,
   so this resolves on essentially every home network even when option 42 is
   absent.
3. **`pool.ntp.org`.** The compile-time fallback baked into the config above;
   only used if both LAN paths fail and outbound UDP/123 to the internet is open.

`time_sync_start()` is called once from `main.c` after Wi-Fi init.
`esp_netif_sntp_start()` is invoked from the existing `on_got_ip` handler in
`wifi.c` (we already have an IP-event hook there). On each (re)connect, SNTP
re-resolves the DHCP servers thanks to `renew_servers_after_new_IP`.

### Applying the time

Use the SNTP "time set" notification (`cfg.sync_cb`) to:
- mark `time_sync_valid()` true,
- log a single `ESP_LOGI` ("clock set: 2026-06-22T07:00:00Z via <server>") —
  it lands in the web-UI Protokoll panel automatically (see firmware CLAUDE.md),
- notify the scheduler so any wall-clock jobs recompute their next-due time.

Drift between syncs is handled for free: once `settimeofday()` is called (lwIP
SNTP does this internally), `gettimeofday()` advances off the same monotonic
source as `esp_timer`. Set `cfg.smooth_sync = false` (step, not slew) — a few-ms
jump is irrelevant for time-of-day scheduling and stepping converges instantly.

---

## 2. Timezone handling

The scheduler needs **local** time. SNTP gives **UTC**, so we apply a POSIX TZ
string with `setenv("TZ", tz, 1); tzset();`, after which `localtime_r()` yields
local wall-clock with correct DST.

**Important constraint:** ESP-IDF's newlib has **no IANA tz database**. You
cannot pass `"Europe/Berlin"` to `tzset()`; you must pass the POSIX form
`"CET-1CEST,M3.5.0,M10.5.0/3"`. That shapes the design below.

### Default

`CONFIG_DONGLE_DEFAULT_TZ` Kconfig, default `"CET-1CEST,M3.5.0,M10.5.0/3"`
(Europe/Berlin). Applied at boot in `config_load()` even before SNTP completes,
so the very first clock-set already lands in local time.

### Persisted setting

Add a `timezone` field to the config layer:
- getter `const char *config_timezone(void);` (returns NVS value or the Kconfig
  default),
- `const char *timezone;` in `config_update_t` (NULL = leave unchanged),
- NVS key `"timezone"` storing the **POSIX** string.

### Learning the timezone from the browser — yes, this works

The browser knows the user's zone and its full DST rules; the dongle does not.
Flow:

1. In `web/index.html`, on first load (or when `config.timezone` is still the
   default), the page computes a POSIX TZ string from the browser and offers to
   save it (auto-save on first setup, or a "Use this device's timezone" button).

   - IANA name for display: `Intl.DateTimeFormat().resolvedOptions().timeZone`
     → e.g. `"Europe/Berlin"`.
   - POSIX string for the firmware: derive it in JS from the browser's own
     offsets so it is correct for *any* zone without an on-device table.
     Sketch:
     ```js
     function posixTZ() {
       const y = new Date().getFullYear();
       const jan = -new Date(y,0,1).getTimezoneOffset();   // minutes east of UTC
       const jul = -new Date(y,6,1).getTimezoneOffset();
       const std = Math.min(jan, jul), dst = Math.max(jan, jul);
       const abbr = Intl.DateTimeFormat('en',{timeZoneName:'short'})
                      .formatToParts(new Date()).find(p=>p.type==='timeZoneName').value;
       const off = m => { const a=Math.abs(m); // POSIX sign is inverted
         return (m<=0?'+':'-')+String(Math.floor(a/60)).padStart(2,'0')
              + (a%60?':'+String(a%60).padStart(2,'0'):''); };
       if (std === dst) return `${stdAbbr()}${off(std)}`;        // no DST
       // With DST: emit std/dst abbrevs + offsets; transition rules (M-format)
       // are filled from the detected northern/southern hemisphere ordering.
       // For the European audience this reduces to the Berlin rule set; a small
       // helper finds the last-Sunday transitions to stay correct elsewhere.
       return buildWithRules(std, dst, abbr);
     }
     ```
     This keeps DST rules authoritative (generated from the browser) and needs
     **no IANA→POSIX table on the device**.

2. The page POSTs the POSIX string (and the IANA name, for display only) to
   `/api/config` as `timezone=<posix>`.

3. `handle_config_post` validates it cheaply (length ≤ 63, prints to a
   `time_t`/`tzset` round-trip) and calls `config_update({.timezone = …})`,
   which persists to NVS and calls `time_sync_set_timezone()` to apply it live.

**Fallbacks / robustness:**
- If JS is disabled or the dongle is configured purely via API, the Berlin
  default stands — never blocks setup.
- Optional belt-and-suspenders: keep a *tiny* IANA→POSIX table for the handful
  of Central-European zones, used only if the browser sends an IANA name but no
  usable POSIX string. Not required if the JS generator above is shipped.
- Reject obviously bad input server-side; on parse failure keep the previous TZ.

> Privacy note: the timezone is derived locally in the browser and sent only to
> the user's own dongle on the LAN — nothing leaves the network.

---

## 3. Scheduler changes (`scheduler.c`)

Today every job is `interval_s ± jitter` off `esp_timer` (`next_due_us`,
monotonic). Add a second job kind without disturbing the existing ones.

### Job model

Extend `sched_job_t` with a schedule kind:

```c
typedef enum { SCHED_INTERVAL, SCHED_DAILY } sched_kind_t;

typedef struct {
    const char  *name;
    sched_kind_t kind;
    // INTERVAL jobs: as today
    uint32_t     interval_s;
    uint32_t     jitter_s;
    uint32_t     first_delay_s;
    // DAILY jobs: local time-of-day
    uint8_t      at_hour;     // 0..23 local
    uint8_t      at_minute;   // 0..59 local
    int64_t      next_due_us; // still an esp_timer instant (see below)
    void       (*run)(void);
} sched_job_t;
```

### Computing the next due time for `SCHED_DAILY`

Keep the wait loop exactly as it is — it sleeps until the smallest
`next_due_us` (an `esp_timer` instant). Only the *computation* of `next_due_us`
changes for daily jobs:

```c
static int64_t next_due_daily(const sched_job_t *j, int64_t now_us) {
    if (!time_sync_valid())
        return now_us + (int64_t)3600 * 1000000;   // retry in 1 h until clock is set
    time_t   utc = time(NULL);
    struct tm lt; localtime_r(&utc, &lt);           // honours TZ + DST
    struct tm tgt = lt;
    tgt.tm_hour = j->at_hour; tgt.tm_min = j->at_minute; tgt.tm_sec = 0;
    time_t when = mktime(&tgt);
    if (when <= utc) when += 24*3600;               // already past today → tomorrow
    int64_t delta_us = (int64_t)(when - utc) * 1000000;
    return now_us + delta_us;                        // back into esp_timer domain
}
```

This deliberately re-anchors the wall-clock target onto the monotonic
`esp_timer` axis the wait loop already uses — so the existing
`seconds_to_ticks()` sleep, manual-trigger notifications, and per-job
rescheduling all keep working untouched. DST shifts and clock steps self-correct
because `next_due_daily()` is recomputed from `localtime_r` after every run.

### Recompute on clock set

When SNTP first sets the clock (or after a large step), `time_sync` notifies the
scheduler (reuse the existing task-notification mechanism) so daily jobs that
were parked on the "+1 h retry" recompute against real local time immediately.

### Migration of existing jobs

The status `mail` job becomes `SCHED_DAILY` at a fixed local hour
(`CONFIG_MAIL_DAILY_HOUR`, default 23:00, to flush the day's spam reports) — it
is the natural consumer of the wall clock and the point of the feature. It keeps
its boot-relative `first_delay_s` first run (a `SCHED_DAILY` job may also fire
soon after boot), so a post-crash error still mails within minutes instead of
waiting for the next daily slot. The mail goes through the *user's own SMTP*
server, so unlike the other jobs there is no shared endpoint to spread across —
a fixed, predictable morning time is exactly what a user wants. The send is a
no-op unless there is a new error / new spam, so a quiet day mails nothing.

The server-facing jobs (`selftest`, `fw_update`, `sync`, `blocklist`) stay
`SCHED_INTERVAL`: they are best-effort and deliberately spread across the fleet
by boot time + jitter, so a fleet-wide power blip doesn't align every dongle
onto the same minute hammering phoneblock.net / the CDN. Wall-clock alignment
would re-introduce exactly that thundering herd.

---

## 4. Web UI / status surface

- `/api/status` (`handle_status`): add a `time` object — `{ valid, epoch_s,
  iso8601_local, tz }` — so the dashboard can show "Uhrzeit: 22.06.2026 09:00
  (Europe/Berlin)" and so QA can confirm sync without a serial console.
- `web/index.html`: show current device time + a timezone field (pre-filled from
  the browser via the generator in §2), POSTing to `/api/config`.
- All new user-facing strings: German inline in the template per the I18N rules;
  any Java-side strings are out of scope (this is firmware + embedded HTML).

---

## 5. Config / build wiring

- `main/Kconfig.projbuild`: `CONFIG_DONGLE_DEFAULT_TZ` (default Berlin POSIX
  string).
- `sdkconfig.defaults`: `CONFIG_LWIP_DHCP_GET_NTP_SRV=y`.
- `config.h/.c`: `config_timezone()` getter + `timezone` in `config_update_t`
  + NVS key (no schema migration needed — NVS is key/value, unknown-key-absent
  falls back to the Kconfig default, matching the existing pattern).
- `main/CMakeLists.txt`: add `time_sync.c` to `SRCS`.
- `main.c`: call `time_sync_start()` after Wi-Fi/netif init; `wifi.c` `on_got_ip`
  calls `esp_netif_sntp_start()`.

---

## 6. Testing

- **Host tests** (existing harness pattern, cf. `tr064_parse`/`log_capture`):
  - `next_due_daily()` pure logic: target later today, target already passed →
    tomorrow, across a DST boundary, and `!time_sync_valid()` → +1 h retry.
  - POSIX-TZ validation in the config handler (accept Berlin string, reject
    overly long / garbage).
- **QEMU / on-device:**
  - Boot with no DHCP option 42 → confirm it falls back to the gateway and the
    clock sets (log line + `/api/status.time.valid`).
  - Set a daily job to "now+2 min", confirm it fires once at the wall-clock
    minute and reschedules to +24 h.
  - Change timezone via the web UI → `/api/status` reflects new local time and a
    daily job's next-due shifts accordingly.

---

## 7. Out of scope / non-goals

- No battery-backed external RTC (always-online, mains-powered device; the
  internal RTC has no backup and is not used as a source of truth).
- No sub-second accuracy guarantees; time-of-day granularity is the target.
- TR-064 router-time acquisition is **not** in this plan (SNTP-to-gateway gives
  the same LAN-local source more simply); it remains a possible future fallback
  if a deployment surfaces a router that serves no NTP.

## Rollout order

1. `time_sync` module + SNTP discovery + `/api/status.time` (observable, no
   behavior change).
2. Timezone config + browser learning + Berlin default.
3. Scheduler `SCHED_DAILY` support + host tests.
4. Convert the status mail job to a fixed daily send
   (`CONFIG_MAIL_DAILY_HOUR`, default 23:00 local), keeping its boot-relative
   first run.
