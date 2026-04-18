# PhoneBlock Dongle — Entwicklungsstand

Lebender Statusbericht: was steht, was fehlt, was wir unterwegs gelernt
haben. Für die übergreifende Zielsetzung und Architektur siehe
[README.md](README.md), für das QEMU-Setup [GETTING_STARTED.md](GETTING_STARTED.md)
und für Test­szenarien [firmware/README.md](firmware/README.md).

## Abgeschlossen

### Projekt-Setup
- [x] Modulstruktur `phoneblock-dongle/` angelegt
- [x] QEMU + ESP-IDF v5.3 Toolchain dokumentiert
  (`GETTING_STARTED.md`) — inklusive Gotcha, dass WLAN-HF in QEMU nicht
  emuliert wird und man stattdessen den OpenCores-Ethernet-MAC mit
  `-nic user,model=open_eth` nutzen muss
- [x] Minimales ESP-IDF-Firmware-Projekt (`firmware/`) mit Kconfig-basierter
  Credential-Konfiguration und gitignored `sdkconfig.defaults.local`

### PhoneBlock-API-Integration (Firmware)
- [x] WLAN/Ethernet-Connect via `protocol_examples_common`
- [x] HTTPS-Abfrage `GET /api/num/{phone}?format=json` mit Bearer-Auth
- [x] JSON-Parse via cJSON, Extraktion der `votes` → SPAM/LEGITIMATE-Verdikt
- [x] Test-API-Endpunkt (`https://phoneblock.net/pb-test/api`) dokumentiert

### TCP-Dummy-Server (Port 5060)
- [x] Lokaler Server, der eine Nummer + `\n` entgegennimmt, gegen die API
  abfragt und mit `SPAM\n` / `LEGITIMATE\n` / `ERROR\n` antwortet — als
  Vorstufe zum eigentlichen SIP-Handling

### SIP-Stack — REGISTER (Port 5061, UDP)
- [x] `REGISTER` mit Digest-Authentication (MD5, mit und ohne qop=auth)
- [x] Periodischer Refresh bei `Expires/2`
- [x] `select()`-basierter Empfangs-Loop — Task schläft interrupt-getrieben,
  wacht bei eingehendem Paket oder Refresh-Deadline auf
- [x] Vollständiges Logging aller SIP-Pakete (TX/RX) auf INFO-Level
- [x] **NAT-Override via Kconfig** (`CONFIG_SIP_CONTACT_HOST_OVERRIDE` +
  `CONFIG_SIP_CONTACT_PORT_OVERRIDE`): für QEMU muss Via/Contact die
  Host-LAN-IP tragen, sonst erreichen eingehende INVITEs niemals den Guest
- [x] Verifiziert gegen echte Fritz!Box: REGISTER → 401 → Digest-Response
  → 200 OK → REGISTERED

### SIP-Response-Helper
- [x] `find_header()` / `echo_header_line()` / `echo_to_with_tag()` für
  das Echoen von Routing-Headern (Via/From/To/Call-ID/CSeq)
- [x] Generische `build_response()` / `send_response()` — Grundlage für
  alle UAS-Antworten
- [x] **OPTIONS**: antworten mit `200 OK` + Allow-Header
- [x] Dispatcher in `handle_incoming()` (Requests vs. Stray Responses)

## Offen / Nächste Schritte

### INVITE-Handling (Kernfunktion)
- [ ] `INVITE` → sofort `100 Trying` senden, damit die Fritz!Box nicht
  retransmittiert
- [ ] Caller-Nummer aus dem `From:`-Header extrahieren und in
  PhoneBlock-Format normalisieren (national `030…` / international
  `00<cc>…`)
- [ ] Parallele PhoneBlock-API-Abfrage (nicht blockierend oder mit
  `vTaskDelay`-freundlichem TLS-Call)
- [ ] **Spam-Fall**: `200 OK` mit Dummy-SDP → `ACK` empfangen →
  sofort `BYE` → `200 OK` auf BYE warten
- [ ] **Nicht-Spam-Fall**: `486 Busy Here` (oder `480 Temporarily
  Unavailable`), damit die Fritz!Box zu anderen Durchwahlen weitergeht
- [ ] `CANCEL` während INVITE-Phase behandeln (Anrufer legt vor unserer
  Antwort auf)

### Dialog-State
- [ ] `Call-ID` + Tags als Dialog-Key speichern, damit zusammengehörige
  Pakete (INVITE/ACK/BYE) einer Dialoginstanz zugeordnet werden
- [ ] Mehrere parallele Dialoge (mehrere INVITEs zeitgleich)?
  Vermutlich kurzfristig nicht nötig — der Dongle nimmt immer nur den
  ersten Anruf entgegen und lehnt Parallelrufe ab
- [ ] UDP-Retransmit-Handling (identisches Paket zweimal empfangen →
  ignorieren bzw. idempotent antworten)

### SDP-Generierung
- [ ] Minimal-SDP für 200 OK auf INVITE: PCMA-Codec, Dummy-Port, kein
  echtes RTP (Fritz!Box akzeptiert, wir hängen eh sofort auf)
- [ ] SDP-Parse des INVITE-Body nicht nötig — wir ignorieren das
  Audio-Angebot komplett

### Status-LED
- [ ] On-Board-LED als Betriebsanzeige nutzen (IDLE / REGISTERED /
  CHECKING / SPAM-BLOCKED, z. B. via Blink-Pattern)
- [ ] GPIO-Pin über Kconfig konfigurierbar machen —
  `CONFIG_STATUS_LED_GPIO` mit Default `2` (WROOM-32-DevKit) und Override
  `10` für den EGBO-PICO-D4-Dongle, da die LED dort an GPIO 10 hängt
- [ ] Aktiv-High/Aktiv-Low ebenfalls per Kconfig, falls Boards invertieren

### Provisioning & Deployment
- [ ] Konfiguration via NVS statt Kconfig — gleiche Firmware, pro Gerät
  individuelle SSID/Passwort/Credentials
- [ ] Captive-Portal für Ersteinrichtung (Dongle öffnet WLAN-AP, User
  konfiguriert via Browser)
- [ ] OTA-Update über HTTPS
- [ ] WiFi-Reconnect-Strategie bei LAN-Ausfall
- [ ] Feldfeste Fehlerbehandlung: Fritz!Box down, API down, TLS-Fehler…

### Hardware
- [ ] Bestellung des 10er-Pakets „ESP32-WROOM-32 CH340C TYPE-C" von
  AliExpress (10PCS-CH340C, 32,99 € versandfrei — siehe
  [README.md](README.md#hardware))
- [ ] USB-A-Stecker auf USB-C-Stecker Adapter dazu
- [ ] Test auf echter Hardware: Von WLAN auf echtes LAN umstellen,
  NAT-Overrides wieder leeren, gegen Produktions-Fritz!Box testen
- [ ] Stromaufnahme + Wärme bei Dauerbetrieb messen
- [ ] Gehäuse/Formfaktor (optional: 3D-Druck)

### Production-Readiness
- [ ] `phoneblock.net`-Produktions-API statt Test-Instanz
- [ ] Bearer-Token pro Dongle / pro Nutzer generieren
- [ ] Analytics/Report an PhoneBlock zurückschicken (wie viele Spam-Calls
  geblockt, welche Nummern)
- [ ] Firmware-Versionierung + Changelog-Policy
- [ ] Sicherheitsreview: Token in NVS, optional via Flash-Encryption
  schützen

## Gotchas, die wir unterwegs gelernt haben

### QEMU
- **WLAN-HF ist nicht emuliert.** Jeder Versuch, den echten WiFi-Treiber
  zu initialisieren, crasht in `register_chipv7_phy`. Workaround:
  `CONFIG_EXAMPLE_USE_OPENETH=y` → OpenCores Ethernet MAC mit
  `-nic user,model=open_eth`.
- **Die `idf.py qemu monitor`-Chained-Invocation funktioniert nicht**,
  weil `idf_monitor` einen TCP-Socket auf `localhost:5555` erwartet,
  den QEMU ohne `--gdb` nicht öffnet. Stattdessen
  `idf.py qemu --qemu-extra-args="…"` direkt und Logs auf stdio lesen.
- **User-mode NAT leitet keine unangeforderten Inbound-Pakete weiter.**
  Für eingehende SIP-INVITEs ist `hostfwd=udp::5061-:5061` + Via/Contact-
  Override zwingend. Analog für den RTP-Rückkanal vom Angerufenen:
  `hostfwd=udp::16000-:16000`. Die gleiche `CONFIG_SIP_CONTACT_HOST_OVERRIDE`
  wird auch für die SDP-Connection-Line (`c=IN IP4 …`) verwendet, sonst
  bewirbt der Dongle `10.0.2.15` — ein Adresse, die außerhalb QEMUs
  nirgendwohin führt.
- **Die Kombination `CONFIG_X=""` in defaults + nicht-leer in .local hat
  bei uns sporadisch nicht gezogen** (vermutlich Build-Cache-Artefakt).
  Immer `rm -rf build && idf.py build` nach Konfig-Änderungen, wenn
  Werte seltsam nicht greifen.

### Firmware-Design
- **Große Puffer nie auf den Task-Stack legen.** SIP-TX-(2 KB) + RX-(4 KB)
  lokal + lwip-Overhead → Stack-Overflow. Lösung: `malloc`/`free` pro
  Aufruf. Der nachfolgende „Timer-Init-Crash beim Boot", den wir lange
  gesucht hatten, war nur ein **Folge-Crash nach Reboot** — die Panic
  trat auf, weil beim Neustart korrupte Memory-Strukturen gefunden wurden.
- **`sdkconfig.defaults.local` wird nicht automatisch geladen.** In der
  `CMakeLists.txt` muss `set(SDKCONFIG_DEFAULTS …)` beide Dateien
  auflisten, bevor `include($ENV{IDF_PATH}/tools/cmake/project.cmake)`
  kommt.
- **`select()` ist unter POSIX nicht Busy-Wait.** Der Task schläft
  interrupt-getrieben, die CPU darf idle gehen. Das ist der Standard-
  Pattern für UDP-Server und sollte so bleiben.

### SIP
- **Fritz!Box nutzt Legacy-Digest ohne qop** (`realm="fritz.box"`,
  kein `qop` im Challenge). Unser Code unterstützt beide Varianten.
- **`rport`/`received`-Mechanismus ist essentiell** für REGISTER durch
  NAT — ohne den würde die Antwort auf den internen Guest-IP geroutet
  und nicht zurückkommen. Aber eingehende **neue** Dialoge (INVITE) gehen
  eben nicht via rport zurück, sondern an die URI im `Contact:`-Header.
- **Fritz!Box schickt sporadisch OPTIONS als Keepalive** — jetzt
  beantwortet.

## Commit-Historie (für die Nachwelt)

```
3cb58a72 feat(dongle): SIP receive loop + NAT overrides for QEMU INVITE testing
d7b003ac feat(dongle): Implement SIP REGISTER with Digest auth against Fritz!Box
5511baa1 feat(dongle): Add TCP dummy server on port 5060 for number lookups
ded6d0a6 feat(dongle): Add Hello-World firmware that queries PhoneBlock API
fe045c9d docs: Add phoneblock-dongle module plan and ESP32 getting-started guide
```
(OPTIONS-Support folgt im nächsten Commit.)
