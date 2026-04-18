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

### Entwicklungs-Infrastruktur
- [x] Pure-C-Parser-Modul `sip_parse.{c,h}` ohne ESP-IDF-Abhängigkeiten
- [x] Host-basierte Unit-Tests unter `firmware/test/` — 107 Assertions,
  Laufzeit ≪ 100 ms, Aufruf `make test`
- [x] Dev-Hook `CONFIG_SIP_TEST_FORCE_SPAM_STAR_NUMBERS`, damit das
  RTP/Audio-Pfad ohne echte Spam-Nummer getestet werden kann

## Offen / Nächste Schritte

### Status-LED
- [ ] On-Board-LED als Betriebsanzeige (Blink-Pattern für IDLE /
  REGISTERED / CHECKING / ANSWERED)
- [ ] `CONFIG_STATUS_LED_GPIO` (Default 2 für WROOM-32, 10 für EGBO-
  PICO-D4) + `CONFIG_STATUS_LED_ACTIVE_LOW`

### Provisioning & Deployment
- [ ] Konfiguration im **NVS** statt Kconfig — eine Firmware für alle
  Dongles, pro Gerät individuelle SSID/PW/SIP/Token
- [ ] **Captive-Portal** zur Ersteinrichtung (Dongle öffnet WLAN-AP,
  User konfiguriert per Browser)
- [ ] **OTA-Update** über HTTPS, damit nachgeflasht werden kann ohne
  physisches Anfassen
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
- [ ] **Bearer-Token pro Nutzer** generieren statt geteilter Dev-Token
- [ ] Optionales Reporting zurück an PhoneBlock (geblockte Calls,
  welche Nummern)
- [ ] Firmware-Versionierung + Changelog-Policy
- [ ] **Sicherheitsreview**: Token im NVS verschlüsselt, optional
  Flash-Encryption, Integritätsprüfung OTA-Payload

### Tech-Debt / Feinschliff
- [ ] **CANCEL-Handler**: 487 Request Terminated wird mit den CANCEL-
  Headern gebaut, spec-konform wäre, die ursprünglichen INVITE-Header
  zu nehmen (CSeq-Method INVITE). Fritz!Box ist tolerant; könnte bei
  strengeren Registrars ein Problem werden.
- [ ] Paralleler API-Check statt synchroner Aufruf im SIP-Task — heute
  blockiert der Task 1–2 s während der TLS-Abfrage, was eingehende
  Pakete kurz liegenlässt. Für den 1-Call-at-a-time-Dongle unkritisch.
- [ ] Audio-Partition: Wenn die Ansage über ~200 KB wächst, Partition-
  Layout überdenken (App-Slot auf 2 MB, Audio separat in SPIFFS).

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
  Für eingehende SIP-INVITEs ist `hostfwd=udp::5061-:5061` +
  Via/Contact-Override zwingend. Für den RTP-Rückkanal analog
  `hostfwd=udp::16000-:16000`.
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
