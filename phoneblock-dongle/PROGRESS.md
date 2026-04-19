# PhoneBlock Dongle — Entwicklungsstand

Lebender Statusbericht: was steht, was fehlt, was wir unterwegs gelernt
haben. Übergreifende Zielsetzung und Architektur siehe [README.md](README.md),
Hardware-Entscheidungsmatrix [HARDWARE.md](HARDWARE.md), QEMU-Setup
[GETTING_STARTED.md](GETTING_STARTED.md), Testszenarien
[firmware/README.md](firmware/README.md).

## Abgeschlossen

### Projekt-Setup
- [x] Modulstruktur `phoneblock-dongle/` angelegt
- [x] ESP-IDF v5.3 Toolchain + QEMU-Setup dokumentiert
  (`GETTING_STARTED.md`), inkl. Gotcha WLAN-HF / OpenCores-Ethernet
- [x] Minimales ESP-IDF-Firmware-Projekt mit Kconfig-basierter
  Credential-Konfiguration, gitignored `sdkconfig.defaults.local`

### Hardware-Evaluierung
- [x] `HARDWARE.md` mit Modul-/Board-Entscheidungsmatrix (SoC-Familien,
  Modul-Suffixe D/U/E/UE, USB-UART-Wandler, Formfaktoren, Budget)
- [x] Beschaffungswahl: „ESP32-WROOM-32 CH340C TYPE-C" 10er-Pack
  (3,30 €/Stk) für die Entwicklung
- [x] EGBO-PICO-D4-USB-Dongle als Kandidat für spätere Pilot-Serie
  identifiziert (echter Stick-Formfaktor, 5,44 €/Stk im 10er)

### PhoneBlock-API-Integration
- [x] WLAN/Ethernet-Connect via `protocol_examples_common`
- [x] HTTPS-Abfrage `GET /api/num/{phone}?format=json` mit Bearer-Auth
  (`api.c/h`, aus `main.c` extrahiert)
- [x] JSON-Parse via cJSON → `votes` → SPAM/LEGITIMATE-Verdikt
- [x] Test- und Produktions-Endpunkte dokumentiert
  (`https://phoneblock.net/pb-test/api` vs. `…/phoneblock/api`)

### TCP-Dummy-Server (Port 5060)
- [x] Lokaler Server, der eine Nummer + `\n` entgegennimmt, gegen die
  API abfragt und mit `SPAM\n`/`LEGITIMATE\n`/`ERROR\n` antwortet —
  Vorstufe für das eigentliche SIP-Handling, blieb als Diagnose-Tool

### SIP-Stack — REGISTER (UDP :5061)
- [x] REGISTER mit Digest-Authentication (MD5, mit und ohne qop=auth)
- [x] Periodischer Refresh bei `Expires/2`
- [x] `select()`-basierter, interrupt-getriebener Empfangs-Loop
- [x] Volllogging aller SIP-Pakete (TX/RX) auf INFO-Level
- [x] NAT-Overrides via Kconfig (`CONFIG_SIP_CONTACT_HOST_OVERRIDE`,
  `CONFIG_SIP_CONTACT_PORT_OVERRIDE`) für QEMU-Betrieb
- [x] Gegen echte Fritz!Box verifiziert

### SIP-Response-Helper
- [x] `find_header`, `echo_header_line`, `echo_to_with_tag` für Routing-
  Header-Echo (Via/From/To/Call-ID/CSeq)
- [x] Generische `build_response` / `send_response` mit optionalem Body
  (SDP) + korrekter Content-Length
- [x] OPTIONS: `200 OK` + Allow-Header
- [x] Dispatcher in `handle_incoming` (Requests vs. Responses)

### SIP-Stack — INVITE / ACK / BYE / CANCEL (Kernfunktion)
- [x] INVITE → sofort `100 Trying`, um Fritz!Box-Retransmits zu stoppen
- [x] Caller-Nummer aus `From:`-URI extrahieren, E.164-Normalisierung
  (DE-zentriert: `+49…` / `0049…` / national → `+49…`)
- [x] Synchroner PhoneBlock-API-Call — 1–2 s, vertretbar im Budget
- [x] **Spam-Fall**: `200 OK` + Dummy-SDP → ACK → (Audio/BYE) → 200 OK
- [x] **Nicht-Spam-Fall**: `486 Busy Here` — Fritz!Box klingelt reguläre
  Nebenstellen weiter
- [x] `CANCEL`-Handling während INVITE-Phase (`200 OK` + `487`)
- [x] BYE empfangen und senden mit korrektem Dialog-Routing
  (Call-ID, Tags, CSeq, R-URI aus gespeichertem Dialog)
- [x] **Dialog-State** mit Call-ID + Tags als Dedupe-Schlüssel, ein
  aktiver Dialog gleichzeitig, Zweit-INVITE → 486
- [x] **Idempotentes UDP-Retransmit-Handling** — dieselbe Response
  wird pro Dialog-State wiederholt gesendet
- [x] **Early-Skip vor API-Abfrage**: interne Codes (`**622`, `*21#`)
  und Telefonbuch-Treffer (nicht-numerischer Display-Name) überspringen
  den HTTPS-Roundtrip

### SDP
- [x] Build: Minimal-SDP für `200 OK` mit PCMA/8000, eigener RTP-Port
- [x] Parse des INVITE-Body: `c=IN IP4 <ip>` + `m=audio <port>` → Dialog-
  RTP-Destination
- [x] Host-Override auch für SDP-`c=`-Zeile (gleicher QEMU-NAT-Override
  wie Via/Contact)

### RTP + Audio-Ansage
- [x] G.711 A-law Encoder (`audio.c/h`), pure C, host-testbar
- [x] RTP-Sender-Task (`rtp.c/h`): UDP-Socket, 160-Byte/20-ms-Frames,
  zufällige SSRC/Seq/Timestamp, vTaskDelayUntil-paced
- [x] Eingebettete Sprachansage via `EMBED_FILES` — ElevenLabs-TTS →
  `ffmpeg -ar 8000 -ac 1 -f alaw` → `main/audio/announcement.alaw`
- [x] BYE-Zeitsteuerung nach Audiolänge via select-Timeout
- [x] End-to-End gegen echte Fritz!Box verifiziert (`X-RTP-Stat: PR=100`,
  Codec-Match PCMA beidseitig)

### Web-UI + Config-Management
- [x] **Stats-Modul** (`stats.{c,h}`): Mutex-geschützte Counter
  (gesamt / spam_blocked / legitimate / errors / sip_registered /
  api_latency) + Ring-Buffer für die letzten 10 Anrufe und die
  letzten 10 Fehler.
- [x] **Config-Modul** (`config.{c,h}`): NVS-gestützte Konfiguration
  mit Kconfig als Fallback, atomarer Multi-Field-Update via
  `config_update()`. Alle SIP-/PhoneBlock-/NAT-Nutzer auf Getter
  umgestellt.
- [x] **Web-Server** (`web.{c,h}`, `esp_http_server`, Port 80):
  Routen `/`, `/api/status`, `/api/calls`, `/api/errors`,
  `/api/config`, `/api/fritzbox-setup`, `/api/fritzbox-2fa-status`,
  `/register-start`, `/token-callback`. Heap-4 KB Header-Buffer für
  Browser-Round-Trips (`CONFIG_HTTPD_MAX_REQ_HDR_LEN`), 8 KB Task-
  Stack für die tiefe TR-064-Aufrufkette.
- [x] **HTML-Dashboard** (`web/index.html` via `EMBED_FILES`): Status-
  First-Architektur mit Hash-Router, Status-Pille + Token-Pille +
  Counter-Kacheln + Anrufe + Fehler als Landing. Bei leerem NVS
  erscheint ein „Willkommen"-Hero mit zwei CTAs zu den Wizards.
  i18n-Gerüst via `t(key, params?)`; aktuell nur `de` befüllt.
- [x] **Setup-Wizards** (alle als Sub-Views unter `#/setup/…`):
  Fritz!Box-Autoprovisioning (TR-064 + 2FA), Provider-Preset-
  Auswahl (Top-5: Telekom / Vodafone / 1&1 / sipgate / easybell),
  manuelles Expert-Formular, PhoneBlock-Token via OAuth-Redirect.
  Erfolgreiche Wizards springen per `location.hash='#/'` zurück
  aufs Status-Dashboard.
- [x] **Config-Form → Re-REGISTER**: POST `/api/config`, `config_update()`,
  `sip_register_request_reload()` triggert den SIP-Task, die
  aktuellen Getter zu benutzen.
- [x] **Kconfig-Credential-Fallbacks ausgebaut**: `CONFIG_SIP_REGISTRAR_HOST`,
  `CONFIG_SIP_USERNAME`, `CONFIG_SIP_PASSWORD`, `CONFIG_PHONEBLOCK_TOKEN`
  sind raus — Credentials leben nur noch im NVS und werden über die
  Wizards gesetzt. Dadurch signalisieren leere Felder eindeutig
  „noch nicht konfiguriert".

### TR-064-Auto-Provisioning (firmware, komplett mit 2FA)
- [x] `tr064.{c,h}` mit generischem `call_action` für beliebige
  Services + optionalem `<avm:token>`-Header.
- [x] `X_AVM-DE_GetNumberOfClients` → nächster freier ClientIndex.
- [x] `X_AVM-DE_SetClient4` → legt IP-Phone mit generiertem Username
  (`phoneblock-<mac-suffix>`) und alphanumerischem 22-Zeichen-
  Passwort an, liefert die interne Nebenstellennummer zurück.
- [x] **Zwei-Faktor-Flow**: `X_AVM-DE_Auth:SetConfig(start)` →
  Token + Methoden (button/dtmf/googleauth), Web-UI pollt
  `/api/fritzbox-2fa-status`, Dongle ruft alle 2 s
  `X_AVM-DE_Auth:GetState` auf. Bei `authenticated` wird `SetClient4`
  mit `<avm:token>`-Header wiederholt.
- [x] **Stale-Session-Cleanup**: vor jedem `SetConfig(start)` ein
  Best-Effort-`SetConfig(stop)`, räumt hängengebliebene
  2FA-Sitzungen früherer Versuche weg (Fehlercode 868 „busy").
- [x] UPnPError-Codes/-Beschreibungen werden aus dem Fault-XML
  extrahiert und in der Web-UI als deutsche Klartexte angezeigt
  (866 = 2FA, 803 = Zeichen, 820/402 = Argumentfehler, etc.).
- [x] Fritz!Box-Admin-Passwort nur flüchtig im RAM während der
  2FA-Sitzung, `memset` beim Abschluss.

### OAuth-Redirect für den PhoneBlock-Token
- [x] **Firmware-Seite** (`web.c`): `/register-start` erzeugt eine
  32-Hex-Char-CSRF-Nonce, baut die Callback-URL aus dem `Host`-Header
  (inkl. QEMU-Port), redirected auf das bestehende PhoneBlock-Mobile-
  Login (`/mobile/login?appId=PhoneBlockDongle&…`). `/token-callback`
  verifiziert die Nonce, speichert den Token via `config_update()`.
- [x] **Server-Seite** (`CreateAuthTokenServlet`): neuer
  `appId=PhoneBlockDongle` plus `callback`+`state`-Parameter,
  Whitelist für `http://`-Callbacks auf Private-IPs, `.fritz.box`,
  `.local`, `localhost`, `answerbot`. `MobileLoginController` schleppt
  die Parameter durch Captcha-/Login-Rundreisen.

### Entwicklungs-Infrastruktur
- [x] Pure-C-Parser-Modul `sip_parse.{c,h}` ohne ESP-IDF-Abhängigkeiten
- [x] Host-basierte Unit-Tests unter `firmware/test/` — 107 Assertions,
  Laufzeit ≪ 100 ms, Aufruf `make test`
- [x] Dev-Hook `CONFIG_SIP_TEST_FORCE_SPAM_STAR_NUMBERS`, damit das
  RTP/Audio-Pfad ohne echte Spam-Nummer getestet werden kann

## Offen / Nächste Schritte

### Laufende Arbeit
- [ ] **2FA-Flow End-to-End verifizieren** — SetClient4 → 866 →
  SetConfig(start) → Polling → (Button/DTMF) → authenticated →
  SetClient4 mit Token → SIP-Creds in NVS. Bisher: Flow läuft bis
  `waitingforauth`, steht dort und wartet; der Schritt „Knopf am
  Router drücken / DTMF-Folge wählen" ist noch offen.

### Status-LED
- [ ] On-Board-LED als Betriebsanzeige (Blink-Pattern für IDLE /
  REGISTERED / CHECKING / ANSWERED)
- [ ] `CONFIG_STATUS_LED_GPIO` (Default 2 für WROOM-32, 10 für EGBO-
  PICO-D4) + `CONFIG_STATUS_LED_ACTIVE_LOW`

### Provisioning & Deployment

**Ziel-UX für die erste Inbetriebnahme** („Oma-tauglich"):

1. Dongle einstecken
2. Am Router den WPS-/„Neues-Gerät"-Knopf drücken → WLAN konfiguriert sich
3. Im Browser `http://answerbot/` öffnen
4. Fritz!Box-Admin-Passwort eingeben → Dongle legt sich selbst als
   IP-Telefon bei der Fritz!Box an (inkl. 2FA-Dialog, falls nötig)
5. Auf „Bei PhoneBlock anmelden" klicken → OAuth-Redirect holt den
   Bearer-Token vom PhoneBlock-Server zurück (wie in der Mobile-App)
6. Fertig — Dongle registriert sich, prüft eingehende Anrufe

Schritt 3–5 sind firmware-seitig umgesetzt (und server-seitig für 5);
Schritt 2 fehlt noch (WPS), und Schritt 3 setzt den Hostname-Eintrag
voraus.

Umsetzungsschritte:

- [ ] **WPS-PBC für WLAN** via `esp_wifi_wps_enable(WPS_TYPE_PBC)` +
  `esp_wifi_wps_start()`. Beim ersten Boot ohne NVS-WLAN-Credentials
  aktivieren; nach erfolgreichem Erhalt in NVS speichern.
- [ ] **Auffindbarkeit per Name `answerbot`** (bewusst *nicht* `phoneblock`,
  kollidiert mental mit der Website). Zwei Mechanismen parallel,
  jeweils ~10 Zeilen:
  - DHCP-Hostname via `esp_netif_set_hostname(netif, "answerbot")` —
    Fritz!Box übernimmt den Namen in ihren internen DNS → `http://answerbot/`
  - mDNS/Bonjour via `mdns_init()` + `mdns_hostname_set("answerbot")` —
    deckt macOS/iOS/Linux/Windows-10-mit-Bonjour ab
  - Umsetzung erst mit echter Hardware testen (QEMU-Routing verzerrt
    DHCP-Hostname-Rückmeldungen)
- [ ] **Fritz!Box-Auto-Discovery** statt Feld „Host" im Formular.
  Neues Modul `discovery.{c,h}`, ~100 Zeilen. Drei-Stufen-Suche:
  1. DNS-Lookup `fritz.box`.
  2. DHCP-Gateway als Fallback.
  3. Verifikation via `GET http://<ip>:49000/tr64desc.xml`, muss
     `<manufacturer>AVM Berlin</manufacturer>` enthalten.
  Wird in `config_load()` aufgerufen, wenn `sip_host` im NVS leer ist.
  Umsetzung erst mit echter Hardware — QEMUs User-mode-Netzwerk
  simuliert keinen sinnvollen Gateway/DNS-Raum.
- [ ] **Konfigurations-Cleanup** — Kconfig-Defaults entfernen, sobald
  NVS über das Web-UI durchgängig zuverlässig bespielt wird. Aktuell
  bleiben die Kconfig-Werte als Entwickler-Fallback sinnvoll.
- [ ] **OTA-Update** über HTTPS
- [ ] WiFi-Reconnect-Strategie bei Router-Ausfall (Backoff, NVS-
  gepinnte Zugangsdaten)
- [ ] Feldfeste Fehlerbehandlung: Fritz!Box down, API down, TLS-Fehler,
  Zertifikatsrotation

### Hardware-Reife
- [ ] 10er-Pack WROOM-32 bestellen (liegt noch, siehe
  [HARDWARE.md](HARDWARE.md))
- [ ] USB-A-Stecker-auf-USB-C-Adapter dazu
- [ ] Test auf echter Hardware: WLAN statt QEMU-Ethernet, NAT-Overrides
  leeren, gegen Produktions-Fritz!Box
- [ ] Stromaufnahme + Wärmeentwicklung im Dauerbetrieb messen
- [ ] Gehäuse/Formfaktor (Schrumpfschlauch für den Start, später 3D-Druck
  oder Wechsel auf EGBO-PICO-D4-Dongle)

### Production-Readiness
- [ ] Umzug von Test-Instanz auf `phoneblock.net`-Produktions-API
- [ ] OAuth-Endpunkt deployen — `CreateAuthTokenServlet`-Erweiterung
  ist committed, muss aber mit der nächsten Server-Release rausgehen.
- [ ] Optionales Reporting zurück an PhoneBlock (geblockte Calls,
  welche Nummern)
- [ ] Firmware-Versionierung + Changelog-Policy

### Generisches Provider-Setup (ohne Fritz!Box)
- [x] **UI-Wizard für Top-5-Anbieter** (Telekom, Vodafone, 1&1, sipgate,
  easybell) als Provider-Preset mit Credential-Feldern. Nur O2 bleibt
  Fritz!Box-only; MagentaZuhause ist im stationären Dongle-Einsatz
  direkt nutzbar. Quelle: [PROVIDERS.md](PROVIDERS.md).
- [ ] **Erweiterte SIP-Parameter**: Transport (UDP/TCP/TLS), Auth-User
  separat vom SIP-User (Telekom: E-Mail), Outbound-Proxy, Realm,
  SRTP-Modus. Braucht Backend-Erweiterung im `sip_register.c`.

### Tech-Debt / Feinschliff
- [ ] **Event-getriebener Reload-Wakeup für den SIP-Task**: aktuell
  cappt der select()-Timeout auf 500 ms, damit der vom Web-UI gesetzte
  `s_reload_requested`-Flag schnell wirksam wird. Saubere Variante:
  Self-Wake-UDP-Socket (Loopback) oder `esp_vfs_eventfd` in die fd_set
  aufnehmen und beim Setzen des Flags ein Byte hineinschreiben. Spart
  die 2 Hz Leerlauf-Wakeups und reagiert innerhalb von Millisekunden.
- [ ] **CANCEL-Handler**: 487 Request Terminated wird mit den CANCEL-
  Headern gebaut, spec-konform wäre, die ursprünglichen INVITE-Header
  zu nehmen (CSeq-Method INVITE). Fritz!Box ist tolerant; könnte bei
  strengeren Registrars ein Problem werden.
- [ ] Paralleler API-Check statt synchroner Aufruf im SIP-Task — heute
  blockiert der Task 1–2 s während der TLS-Abfrage, was eingehende
  Pakete kurz liegenlässt. Für den 1-Call-at-a-time-Dongle unkritisch.
- [ ] Audio-Partition: Wenn die Ansage über ~200 KB wächst, Partition-
  Layout überdenken (App-Slot auf 2 MB, Audio separat in SPIFFS).
- [ ] `tr064.c` holt die Control-URLs hardcoded (`/upnp/control/x_voip`
  und `/upnp/control/x_auth`). Saubere Implementierung fetcht sie aus
  `http://<box>:49000/tr64desc.xml`. Fällt als Nebeneffekt der
  Discovery-Arbeit aus dem oben genannten Auto-Discovery-Modul ab.

## Gotchas, die wir unterwegs gelernt haben

### QEMU
- **WLAN-HF ist nicht emuliert.** Jeder Versuch, den echten WiFi-Treiber
  zu initialisieren, crasht in `register_chipv7_phy`. Workaround:
  `CONFIG_EXAMPLE_USE_OPENETH=y` → OpenCores Ethernet MAC mit
  `-nic user,model=open_eth`.
- **`idf.py qemu monitor`-Chained-Invocation funktioniert nicht**, weil
  `idf_monitor` einen TCP-Socket auf `localhost:5555` erwartet, den
  QEMU ohne `--gdb` nicht öffnet. Stattdessen `idf.py qemu --qemu-extra-args=…`
  und Logs auf stdio.
- **User-mode NAT leitet keine unangeforderten Inbound-Pakete weiter.**
  Die drei heute gebrauchten Forwards, zusammen einmalig für bequemes
  Debugging:
  ```
  -nic user,model=open_eth,\
    hostfwd=udp::5061-:5061,\
    hostfwd=udp::16000-:16000,\
    hostfwd=tcp::8080-:80
  ```
  - `udp::5061` → eingehende SIP-INVITEs von der Fritz!Box
  - `udp::16000` → RTP-Rückkanal (nicht funktional, aber Fritz!Box
    meldet sonst in `X-RTP-Stat` einseitigen Stream)
  - `tcp::8080` → Zugriff auf die Dongle-Web-UI unter `http://localhost:8080/`
    (Port 80 im Guest, 8080 auf dem Host, damit man kein sudo braucht)
- **`CONFIG_SIP_CONTACT_HOST_OVERRIDE` gilt auch für die SDP-`c=`-Zeile.**
  Ohne Override bewirbt der Dongle `10.0.2.15` — eine Adresse, die
  außerhalb QEMUs nicht existiert, und der RTP-Rückweg kommt nie an.

### Build-System
- **`sdkconfig` wird aus `sdkconfig.defaults*` nur bei Neuanlage
  erzeugt.** Einmal vorhanden, bleibt `sdkconfig` die Quelle der Wahrheit
  und ignoriert neue Defaults. Nach Bool-Änderungen in
  `sdkconfig.defaults.local` → `rm sdkconfig && idf.py build`.
- **Die Kombination `CONFIG_X=""` in defaults + nicht-leer in `.local`
  hat sporadisch nicht gezogen** (Build-Cache-Artefakt). Notfalls
  `rm -rf build && idf.py build`.
- **`sdkconfig.defaults.local` wird nicht automatisch geladen.** In
  `CMakeLists.txt` muss `set(SDKCONFIG_DEFAULTS …)` beide Dateien
  explizit listen.
- **`EMBED_FILES` in `idf_component_register`** erzeugt Linker-Symbole
  `_binary_<name>_<ext>_start` / `_end`. Aliasing via `asm("_binary…")`
  auf saubere C-Namen macht den Aufrufcode lesbar.
- **Typografische Anführungszeichen („…") in C-Stringliteralen** brechen
  den Compiler (`stray '\342' in program`). Nur ASCII-Quotes verwenden.
- **`esp_http_server`-Default-Puffer sind klein**: 512 B Header und
  512 B URI reichen für browserbasierte Rückleitungen nicht. Auf
  4 KB bzw. 1 KB via `CONFIG_HTTPD_MAX_REQ_HDR_LEN` /
  `CONFIG_HTTPD_MAX_URI_LEN` heben. Der httpd-Worker-Task-Default
  (4 KB Stack) ist ebenfalls zu klein für die TR-064-Aufrufkette —
  in `web_start()` auf 8 KB setzen.

### Firmware-Design
- **Große Puffer nie auf den Task-Stack legen.** SIP-TX-/RX-Puffer
  (>6 KB) lokal + lwip-Overhead → Stack-Overflow. Lösung: `malloc`/`free`
  pro Aufruf.
- **`select()` ist unter POSIX nicht Busy-Wait.** Der Task schläft
  interrupt-getrieben, die CPU darf idle. Standard-Pattern für UDP-
  Server — beibehalten.
- **`random_hex`-Overflow** (hat früher immer 33 Bytes in beliebig große
  Buffer geschrieben) war ein latenter Memory-Corruption-Bug. Jetzt
  schreibt die Funktion genau `hex_chars + 1` Bytes.
- **Namenskollision `stats_init`**: lwip exportiert ein gleichnamiges
  Macro — deshalb heißt unsere Init-Funktion `stats_setup()`.

### SIP-Protokoll
- **Fritz!Box nutzt Legacy-Digest ohne `qop`** (`realm="fritz.box"`,
  kein `qop` im Challenge). Unser Code unterstützt beide Varianten.
- **`rport`/`received`-Mechanismus ist essentiell** für REGISTER durch
  NAT. Eingehende **neue** Dialoge (INVITE) gehen nicht via rport
  zurück, sondern an die URI im `Contact:`-Header.
- **Fritz!Box schickt sporadisch OPTIONS als Keepalive.** Muss
  beantwortet werden, sonst droppt die Box die Registrierung.
- **Interne Nebenstellen haben `**NN`-Präfix** und werden vom
  PhoneBlock-Server mit HTTP 400 abgelehnt — daher Early-Skip
  vor der API.

### TR-064 / 2FA
- **Feldnamen *müssen* aus der `fritz_tr064`-Dart-Lib übernommen
  werden — Raten scheitert.** Beispiel: Count von
  `GetNumberOfClients` steckt in `NewX_AVM-DE_NumberOfClients`, nicht
  in `NewX_AVM-DE_ClientNumber` oder `…ClientIndex`.
- **Control-URLs:** `/upnp/control/x_voip` (X_VoIP) und
  `/upnp/control/x_auth` (X_AVM-DE_Auth). Stabil seit Jahren, derzeit
  hardcoded; sauberer wäre aus `tr64desc.xml` (→ Discovery).
- **Passwort-Alphabet für SIP-Clients: nur `[A-Za-z0-9]`.** Sonderzeichen
  (`!@#$%-_+=`) führen zu UPnPError 803 „Argument contains invalid
  characters".
- **2FA-Sessions bleiben „busy"** (Code 868) nach fehlgeschlagenen
  SetClient4-Versuchen, bis entweder der Router-Timeout (~60 s)
  abläuft oder wir `SetConfig(stop)` schicken. Vor jedem
  `SetConfig(start)` daher Best-Effort-Stop.
- **`<avm:token>`-Header in SOAP-Envelope** ist der Weg, wie 2FA-
  Freigaben an 2FA-pflichtige Aktionen gekoppelt werden — *nicht*
  via HTTP-Header oder Cookie.

### Web-UI
- Die Dashboard-Seite ist ein **Einmalnutzungs-Werkzeug** (Setup plus
  gelegentlicher Diagnosebesuch). 3-Sekunden-Polling reicht, SSE/
  WebSocket wäre Overkill. Siehe Memory-Eintrag
  [feedback_dongle_web_ui_scope](../.claude/…/feedback_dongle_web_ui_scope.md).
