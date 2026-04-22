# FUTURE.md — Linux-Port des PhoneBlock-Dongles

Plan für einen Linux-Build der Dongle-Firmware neben dem bestehenden ESP32-Target. Ziel: ein schlankes natives Binary (ca. 200–500 KB) mit systemd-Service, das den Java/Docker-Answerbot auf Self-Hoster-Systemen (Raspberry Pi, HomeServer, NAS) ersetzt.

## Motivation

- Viele Nutzer haben ohnehin einen Pi oder kleinen Server laufen. Der Java-Answerbot im Docker-Container ist dafür überdimensioniert.
- Die ESP32-Firmware ist zu 95 % reines C ohne HW-I/O (keine I2S, keine GPIO-Audio — die Ansage ist ein eingebettetes `.alaw`-Byte-Array, das via RTP gestreamt wird).
- Ein gemeinsamer Code-Tree mit zwei Build-Targets hält beide Varianten zukunftssicher wartbar.

## Zielbild

```
firmware/
├── core/                  # NEU — plattformunabhängig
│   ├── sip_parse.{c,h}
│   ├── rtp.{c,h}
│   ├── audio.{c,h}
│   ├── sip_register.{c,h}
│   └── stats.{c,h}
├── platform/              # NEU — Shim-Layer
│   ├── platform.h         # Abstrakte API
│   ├── platform_esp32.c
│   └── platform_linux.c
├── frontend_esp32/        # ESP-IDF-spezifisch
│   ├── main.c
│   ├── config_nvs.c
│   ├── web_esphttpd.c
│   ├── api_espclient.c
│   └── tr064_espclient.c
├── frontend_linux/        # NEU — Linux-spezifisch
│   ├── main.c
│   ├── config_file.c
│   ├── web_mongoose.c
│   ├── api_curl.c
│   └── tr064_curl.c
└── CMakeLists.txt         # top-level, Target-Wahl via -DPLATFORM=esp32|linux
```

## Platform-Shim — `platform.h`

Die Kern-Module (`sip_register.c`, `rtp.c`, `stats.c`) bekommen keine ESP-Includes mehr, sondern rufen den Shim auf. Dieser ist die einzige Datei, die pro Plattform unterschiedlich ist. Geschätzter Umfang: < 100 Zeilen pro Implementierung.

```c
// platform.h — Auszug
void        pb_log_info(const char *tag, const char *fmt, ...);
void        pb_log_warn(const char *tag, const char *fmt, ...);
void        pb_log_err (const char *tag, const char *fmt, ...);

uint64_t    pb_monotonic_us(void);
uint32_t    pb_random_u32(void);

typedef struct pb_task  pb_task_t;
typedef struct pb_mutex pb_mutex_t;

pb_task_t  *pb_task_create(void (*fn)(void *), void *arg,
                           const char *name, size_t stack_bytes);
void        pb_task_sleep_ms(uint32_t ms);
void        pb_task_yield(void);

pb_mutex_t *pb_mutex_create(void);
void        pb_mutex_lock  (pb_mutex_t *m);
void        pb_mutex_unlock(pb_mutex_t *m);

int         pb_get_mac(uint8_t mac[6]);        // für TR-064 UUID-Seed
```

Mapping:

| Shim-API | ESP32 | Linux |
|----------|-------|-------|
| `pb_log_*` | `ESP_LOGx` | `fprintf(stderr, ...)` bzw. `syslog(3)` |
| `pb_monotonic_us` | `esp_timer_get_time` | `clock_gettime(CLOCK_MONOTONIC)` |
| `pb_random_u32` | `esp_random` | `getrandom(2)` |
| `pb_task_create` | `xTaskCreate` | `pthread_create` |
| `pb_mutex_*` | FreeRTOS semphr | `pthread_mutex` |
| `pb_get_mac` | `esp_read_mac` | `getifaddrs` → MAC der Default-Route |

Sockets bleiben POSIX — lwip ist bewusst API-kompatibel. `#include "lwip/sockets.h"` wird durch `#include <sys/socket.h>` + `<netdb.h>` + `<arpa/inet.h>` ersetzt; der restliche Code ist identisch.

## Schrittplan

### Phase 1 — Extraktion der Core-Module (2 Tage)

1. Neues CMake-Layout anlegen (`core/`, `platform/`, `frontend_esp32/`, `frontend_linux/`).
2. ESP32-Build reparieren: ESP-IDF-Component-Registrierung zeigt auf neue Pfade, Verhalten bleibt bit-genau identisch.
3. Shim-Header `platform.h` definieren, `platform_esp32.c` als dünne Wrapper-Schicht schreiben.
4. In `sip_register.c`, `rtp.c`, `stats.c`, `audio.c`, `sip_parse.c` alle `ESP_LOGx`, `xTaskCreate`, `esp_timer_get_time`, `esp_random`, `FreeRTOS`-Semaphoren etc. durch Shim-Aufrufe ersetzen.
5. **Gate**: ESP32-Firmware baut und läuft auf der Referenz-Hardware wie vor der Umstellung. Regression an echter Fritz!Box verifizieren.

### Phase 2 — Linux-Platform-Shim (1 Tag)

1. `platform_linux.c` implementieren (pthread, clock_gettime, getrandom, syslog).
2. Unit-Tests für Shim-API unter Linux (dann werden sie später auch in CI laufen).

### Phase 3 — Config-Backend (0.5 Tage)

1. `frontend_linux/config_file.c` mit denselben Gettern wie `config.h`.
2. Lesen/Schreiben über einfaches INI-Format (z. B. libinih, single-header) unter `/etc/phoneblock/dongle.conf` oder `$XDG_CONFIG_HOME/phoneblock/dongle.conf`.
3. Laufzeit-Updates (`config_update`): atomisches Rewrite via `mkstemp` + `rename`.

### Phase 4 — HTTP-Client (1 Tag)

1. `frontend_linux/api_curl.c` und `tr064_curl.c` über **libcurl** (verbreitet, in allen Distros vorhanden).
2. TLS: System-CA-Bundle — kein `esp_crt_bundle` nötig.
3. Response-Streaming über CURLOPT_WRITEFUNCTION analog zum ESP-HTTP-Event-Callback.
4. Dieselben Funktions-Prototypen (`phoneblock_check`, `tr064_*`) wie in der ESP32-Variante — der SIP-Core merkt nicht, welches Backend drunter liegt.

### Phase 5 — Embedded Web-UI (2–3 Tage)

1. HTTP-Server auf **mongoose** (single-file, MIT) oder **libmicrohttpd** — Entscheidung bewusst treffen:
   - mongoose: Event-basiert, eine Datei, sehr klein. Eher wie esp_http_server.
   - libmicrohttpd: GNU-Paket, in allen Distros als .deb/.rpm. Threaded-Model, mehr Boilerplate.
2. Alle URI-Handler aus `web.c` übertragen. Logik bleibt, nur die Request-/Response-API wechselt.
3. `index.html` wird zur Build-Zeit mit `xxd -i` eingebettet **oder** aus `/usr/share/phoneblock/web/` geladen — letzteres einfacher zum Patchen.
4. Authentifizierung/Cookie-Handling 1:1 übertragen.

**Designentscheidung — Scope-Reduktion:** Der WiFi-Provisioning-Teil des Setup-Wizards entfällt (auf Linux ist das Netz bereits up). Der TR-064-Autodiscovery-Flow bleibt — er ist der Hauptgrund, warum das Dongle-Setup „magisch" funktioniert, und genau diesen Vorteil wollen wir auf den Pi bringen.

### Phase 6 — Main + Integration (1 Tag)

1. `frontend_linux/main.c`:
   - kein `nvs_flash_init`, kein `esp_netif_init`, kein `example_connect`
   - `signal(SIGTERM)` für sauberes Shutdown
   - Optional: `--config <path>`, `--foreground`, `--verbose` via `getopt`
2. Pidfile nur bei expliziter Anforderung — unter systemd nicht nötig.
3. `main()` ruft `config_load()`, `sip_register_start()`, `web_start()` und `pause()`.

### Phase 7 — Paketierung & Deployment (2 Tage)

1. **systemd-Unit** (`debian/phoneblock-dongle.service`):
   ```ini
   [Service]
   Type=simple
   User=phoneblock
   ExecStart=/usr/bin/phoneblock-dongle
   Restart=on-failure
   ProtectSystem=strict
   ProtectHome=yes
   NoNewPrivileges=yes
   ```
2. **Debian-Paket** (`debian/`) für armhf/arm64/amd64 — baubar via `dpkg-buildpackage`.
3. **GitHub Actions**: Cross-Build-Matrix (amd64, arm64, armhf) mit Release-Artefakten. ESP32-Build parallel.
4. **README-Ergänzung** im Dongle-Ordner: Installationsanleitung für Pi.
5. End-to-End-Test: Pi im LAN, Fritz!Box als SIP-Registrar, echter Anruf prüft Blockverhalten.

### Phase 8 — Dokumentation (0.5 Tage)

1. `GETTING_STARTED.md` um Linux-Abschnitt erweitern.
2. Neues `HARDWARE.md`-Pendant nicht nötig — Linux läuft halt auf Linux.
3. Release-Notes im Haupt-Repo (`RELEASE-NOTES.md`).

## Aufwands-Summe

| Phase | Tage |
|-------|------|
| 1 Core-Extraktion | 2 |
| 2 Linux-Shim | 1 |
| 3 Config | 0.5 |
| 4 HTTP-Client | 1 |
| 5 Web-UI | 2–3 |
| 6 Main | 1 |
| 7 Paketierung | 2 |
| 8 Doku | 0.5 |
| **Summe** | **10–11 PT** |

## Risiken & offene Punkte

- **mbedtls vs. OpenSSL**: Der Auth-Digest in `sip_register.c` nutzt `mbedtls/md5.h`. Auf Linux wäre OpenSSL bequemer — entweder mbedtls als Dep mitschleppen (kleines Paket) oder eine zweite Implementierung des MD5-Aufrufs hinter dem Shim.
- **NAT/Port-Forwarding**: Auf dem ESP32 war der Nutzer nah an seiner Fritz!Box (im LAN). Bei Linux ist das meist auch so, aber der Code, der eigene IP/Port über `esp_netif_get_ip_info` ermittelt, muss über `getifaddrs` neu implementiert werden (in `platform_linux.c`).
- **Audio-Format**: Sollte sich das Ansagen-Format von A-Law auf z. B. Opus ändern, muss das **in beiden** Frontends möglich bleiben — die embedded-Files sollten im Core-Modul definiert sein, nicht im Frontend. Bei Linux als Datei, bei ESP32 via `EMBED_FILES`. Ein gemeinsamer Getter `audio_get_announcement(&data, &len)` kapselt das.
- **PhoneBlock-API-Kompatibilität**: Der Linux-Port nutzt denselben Token-Flow wie ESP32 — d. h. Nutzer braucht einen PhoneBlock-Account. Das ist ok, aber die Doku muss es klar sagen, damit keine Erwartung entsteht, das laufe „standalone".
- **Konfigurationsmigration vom Java-Answerbot**: Nice-to-have — ein `phoneblock-dongle-import` Kommando, das `.phoneblock`-Files einliest und in das neue INI-Format konvertiert. Ca. halber Tag, nicht MVP.

## Nicht im Scope

- Windows-/macOS-Build (POSIX-Annahmen)
- Paketierung für Alpine/musl (später machbar, aber nicht Ziel des MVP)
- OTA-Update-Mechanismus — übernehmen apt/dnf
- Web-UI-Rewrites — die HTML-Einseiter bleibt identisch zum ESP32

## Exit-Kriterien

Der Linux-Port gilt als fertig, wenn:

1. Auf einem frisch installierten Raspberry Pi OS (Bookworm) das `.deb` installiert, der Service startet, und der Nutzer über `http://<pi-ip>:8080` den Setup-Wizard durchläuft.
2. TR-064-Autoconfig erkennt eine Fritz!Box im LAN und provisioniert SIP-Zugangsdaten.
3. Ein Testanruf einer SPAM-Nummer wird korrekt als `SPAM` gewertet und der RTP-Announcement gespielt.
4. Der Service überlebt Neustart, Netz-Wackler und Fritz!Box-Neustart ohne manuelles Eingreifen.
5. CI baut alle drei Zielarchitekturen (amd64, arm64, armhf) grün.

## Folgeprojekte

- **Fritz!Box-lose Betriebsart**: Der Dongle funktioniert heute nur in Kombination mit einer Fritz!Box als Registrar. Ein Linux-Port könnte perspektivisch als generischer SIP-Client an beliebigen Registrars hängen (Sipgate, Telekom DeutschlandLAN etc.) — eigener Scope, nicht Teil dieses Plans.
- **Flatpak/Snap**: Für Desktop-Nutzer mit Dauerbetrieb-Notebook. Nicht MVP.
- **ARM-Builds für OpenWrt**: Router als Host — interessant, aber benötigt eigene Toolchain-Arbeit.
