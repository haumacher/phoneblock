# phoneblock-dongle — Firmware

Ausbaustufe der Dongle-Firmware. Nach dem Boot:

1. Netzverbindung aufbauen (Ethernet in QEMU, später WiFi auf Hardware).
2. Einmaliger Selbsttest gegen die PhoneBlock-API mit der konfigurierten
   Testnummer.
3. SIP-Registrierung am konfigurierten Registrar (Fritz!Box), mit periodischem
   Refresh. Danach Empfangs-Loop über `select()` — bereit für eingehende
   Pakete (OPTIONS/INVITE, aktuell nur Logging).

## Voraussetzungen

- ESP-IDF v5.3 installiert (siehe
  [../GETTING_STARTED.md](../GETTING_STARTED.md))
- Konto auf https://phoneblock.net + generierter Bearer-Token (beginnt mit
  `pbt_`)

### API-Endpunkte

- `https://phoneblock.net/phoneblock/api` — Produktions-API
- `https://phoneblock.net/pb-test/api` — Test-Instanz

Ein Token gilt immer nur für die Instanz, auf der er generiert wurde. Das
Prefix `pbt_` („PhoneBlock-Token") ist in beiden Fällen dasselbe. Welche
URL du nutzt, legst du in `sdkconfig.defaults.local` (oder `idf.py
menuconfig`) fest.

## Konfiguration

Der PhoneBlock-Token und die SIP-Zugangsdaten werden zur Laufzeit über
das Web-UI des Dongles gesetzt (OAuth-Redirect für den Token, TR-064-
Autoprovisioning oder manuelles Formular für SIP) und im NVS-Flash
persistiert. Es gibt keinen Kconfig-Weg mehr, sie zu hinterlegen.

Dev-relevante Kconfig-Optionen (in `sdkconfig.defaults.local` oder
`idf.py menuconfig`):

- `CONFIG_PHONEBLOCK_BASE_URL` — Produktions- vs. Testinstanz (Site-
  Root ohne `/api`-Suffix; die Firmware hängt `/api/num/…`, `/api/rate`,
  `/mobile/login` usw. selbst an)
- `CONFIG_SIP_CONTACT_HOST_OVERRIDE` / `CONFIG_SIP_CONTACT_PORT_OVERRIDE`
  — NAT-Overrides für den QEMU-Betrieb
- `CONFIG_SIP_TEST_FORCE_SPAM_STAR_NUMBERS` — Test-Hook für den
  RTP/Audio-Pfad

## Netzwerkzugriff

> ## ⚠️ MACHE DEINEN PHONEBLOCK-DONGLE NIEMALS AUS DEM INTERNET ERREICHBAR!
>
> Der Dongle ist ein Gerät für das lokale Heimnetz. Er gehört nicht ins
> Internet — kein Port-Forwarding, keine DMZ, keine öffentliche Weiter-
> leitung. Er hält seine gesamte Konfiguration (inkl. Firmware-Upload und
> Werksreset) hinter einer einfachen HTTP-Oberfläche; im Internet ist das
> eine Einladung.

Damit ein **versehentliches** Offenlegen (falsch gesetztes Port-Forwarding,
DMZ-Host) nicht sofort zur Übernahme führt, gilt eine defensive Grundregel:

- **Ohne aktivierte Authentifizierung (Standard) antwortet der Dongle nur
  Clients aus dem lokalen Netz.** Entfernte Anfragen erhalten `403`. Das
  ist ein Sicherheitsnetz für den Fehlerfall — **keine** Einladung, das
  Gerät bewusst zu exponieren.

Alles Weitere richtet sich an alle, die genau wissen, was sie tun und die
Konsequenzen tragen. Details und Bedrohungsmodell:
[docs/network-access-control.md](docs/network-access-control.md).

- **Mit aktivierter Authentifizierung** („Login mit PhoneBlock", an das
  eigene PhoneBlock-Konto gebunden) verlangt jede Anfrage ein gültiges
  Session-Cookie; erst dann wird auch entfernter Zugriff überhaupt
  beantwortet.
- „Lokal" bezieht sich auf die unmittelbare TCP-Gegenstelle.
  `X-Forwarded-For`/`Forwarded` werden **nur** ausgewertet, wenn die
  Gegenstelle selbst lokal ist (der vertrauenswürdige Reverse-Proxy) — von
  einer entfernten Gegenstelle werden sie ignoriert und können daher nicht
  gefälscht werden.
- Wer trotz der Warnung einen Reverse-Proxy vor einen Dongle **mit
  deaktivierter** Authentifizierung setzt, muss die echte Client-Adresse in
  `X-Forwarded-For`/`Forwarded` weiterreichen — sonst sind entfernte
  Clients nicht von einem direkten lokalen Client zu unterscheiden und
  werden bedient. Sinnvoller ist ohnehin: erst die Authentifizierung
  aktivieren.

## Bauen

```bash
source ~/esp/esp-idf/export.sh
cd phoneblock-dongle/firmware

idf.py set-target esp32
idf.py build
```

Beenden eines laufenden QEMU-Prozesses jeweils mit `Ctrl+A`, dann `X`.

## Testszenarien im QEMU-Emulator

QEMU user-mode networking gibt dem emulierten ESP32 eine private IP
(`10.0.2.15`) hinter einem NAT. Für ausgehende Verbindungen reicht das;
für eingehende Pakete (SIP-INVITE) braucht es
explizite Port-Forwardings.

### 1) PhoneBlock-API-Selbsttest (nur Outbound)

Die Firmware fragt beim Boot die Testnummer über HTTPS ab und loggt das
Ergebnis. Für diesen Fall reicht die einfachste Netzkonfiguration:

```bash
idf.py qemu --qemu-extra-args="-nic user,model=open_eth"
```

Erwartetes Log:

```
I (xxxx) phoneblock: GET https://phoneblock.net/pb-test/api/num/01749999999?format=json
I (xxxx) phoneblock: HTTP 200, NNN bytes: {"phone":"...","votes":0,...}
I (xxxx) phoneblock: Number 01749999999 → 0 votes → LEGITIMATE
```

### 2) SIP-Registrierung an der Fritz!Box

Funktioniert über QEMUs NAT, weil REGISTER outbound ist und das
rport-/received-Mechanismus die Antwort automatisch zurückroutet. Keine
Sondermaßnahmen nötig — dieselbe Invocation wie in (1).

Erwartete SIP-Sequenz im Log:

```
I (xxxx) sip: → REGISTER (... bytes): REGISTER sip:192.168.178.1 SIP/2.0 ...
I (xxxx) sip: ← ... bytes: SIP/2.0 401 Unauthorized ... WWW-Authenticate: Digest ...
I (xxxx) sip: ← 401 (... bytes)
I (xxxx) sip: challenge: realm="fritz.box" qop=""
I (xxxx) sip: → REGISTER with auth (... bytes): ...Authorization: Digest ...
I (xxxx) sip: ← ... bytes: SIP/2.0 200 OK ...
I (xxxx) sip: ← 200 (authenticated)
I (xxxx) sip: REGISTERED as phoneblock-ab@192.168.178.1 (expires 300 s)
```

### 3) Eingehende SIP-Anrufe (INVITE) — mit NAT-Workaround

Hier hat die Fritz!Box das Problem, den Dongle **aktiv** zu erreichen.
Unsere REGISTER-Pakete tragen standardmäßig `Via: …/UDP 10.0.2.15:15060`
und `Contact: <sip:phoneblock-ab@10.0.2.15:15060>` — das ist eine Adresse,
die nur innerhalb des QEMU-Userspaces existiert. Die Fritz!Box wählt sie
zwar aus der Registration-Tabelle, das Paket versandet aber im NAT.

Damit INVITEs durchkommen, sind zwei Schritte nötig:

**a) QEMU: UDP-Hostfwd auf Port 15060.** So leitet der Host eingehende
SIP-Pakete an den emulierten Guest weiter:

```bash
idf.py qemu --qemu-extra-args="-nic user,model=open_eth,hostfwd=udp::15060-:15060"
```

**b) Firmware: Via/Contact auf die Host-IP umstellen.** Sonst weiß die
Fritz!Box nicht, dass sie `Host-IP:15060` verwenden soll. In
`sdkconfig.defaults.local` (gitignored):

```
CONFIG_SIP_CONTACT_HOST_OVERRIDE="192.168.178.22"   # IP des Host-PCs im LAN
CONFIG_SIP_CONTACT_PORT_OVERRIDE=15060
```

Nach dem Rebuild (`idf.py build`) enthält die REGISTER-Nachricht dann
`Via: …/UDP 192.168.178.22:15060` und `Contact: <sip:…@192.168.178.22:15060>`.
Die Fritz!Box sendet INVITEs an die Host-IP → `hostfwd` leitet an den
Guest → `recvfrom` liefert sie an den SIP-Task.

**Fritz!Box-seitige Einrichtung:**

Im Regelbetrieb legt der Dongle die Nebenstelle per TR-064 selbst an
(Web-UI → „Nebenstelle einrichten"). Manuell über die Fritz!Box-
Weboberfläche geht es genauso:

1. Telefonie → Telefoniegeräte → Neues Gerät einrichten →
   **Telefon (mit und ohne Anrufbeantworter)** → **LAN/WLAN (IP-Telefon)**.
2. Benutzername + Passwort setzen. Die Werte trägst du anschließend
   im Dongle-Web-UI unter „Manuelle Konfiguration" ein — Kconfig kennt
   die SIP-Credentials nicht mehr.
3. Dem Gerät eine **interne Rufnummer** zuweisen (z. B. `620`) und in
   der Rufbehandlung berücksichtigen.

Testanruf von einer anderen Durchwahl auf die interne Rufnummer:
Der Dongle sollte im Log einen eingehenden INVITE zeigen:

```
I (xxxx) sip: ← from 192.168.178.1:5060  723 bytes:
INVITE sip:phoneblock-ab@192.168.178.22:15060 SIP/2.0
Via: SIP/2.0/UDP 192.168.178.1:5060;branch=z9hG4bK...
From: <sip:**600**@fritz.box>;tag=...
To: <sip:phoneblock-ab@192.168.178.22:15060>
Call-ID: ...
CSeq: 1 INVITE
Contact: <sip:fritz.box>
Content-Type: application/sdp
...
```

Der Dongle antwortet auf eingehende INVITEs mit dem vollständigen
Dialog: `100 Trying` → Caller-Lookup via PhoneBlock-API → bei Spam
`200 OK` + SDP + 9,84 s RTP-Ansage + `BYE`, sonst `486 Busy Here`.
Das komplette Verhalten, inkl. Retransmit-Dedupe, CANCEL-Handling und
Tonausspielung, landet im gleichen `sip_register.c`.

### 4) Web-UI im Browser öffnen

Der Dongle serviert unter Port 80 ein Status-Dashboard. Mit einem
TCP-Hostfwd kommt man vom Host-Browser dran:

```bash
idf.py qemu --qemu-extra-args="-nic user,model=open_eth,\
  hostfwd=udp::15060-:15060,\
  hostfwd=udp::16000-:16000,\
  hostfwd=tcp::8080-:80"
```

Dann im Browser `http://localhost:8080/` aufrufen. Das Dashboard zeigt:

- SIP-Registrierungsstatus, IP, Uptime, Firmware-Version
- Zähler (Anrufe gesamt / Spam geblockt / legitim / Fehler)
- Letzte 10 Anrufe mit Urteil
- Letzte 10 Fehlermeldungen
- Konfigurationsformular für SIP- + PhoneBlock-Zugangsdaten. Speichern
  triggert automatisch einen Re-REGISTER mit den neuen Credentials.

JSON-Endpunkte für Debug: `/api/status`, `/api/calls`, `/api/errors`.

### QEMU-Limit: Software-Reset crasht im Emulator

Der Xtensa-QEMU räumt den Hardware-Timer-Peripherie-State bei
`esp_restart()` nicht sauber auf. Ein pending Timer-Interrupt-Flag
überlebt den Reset und feuert beim nächsten Boot, bevor
`esp_intr_alloc` seinen Handler registriert hat — Sprung auf NULL,
Guru Meditation mit `PC=0x00000000` direkt nach `spi_flash: flash
io: dio`.

Konsequenz für OTA-Tests:

- **Upload-Pfad** (Bytes im Flash) lässt sich in QEMU voll validieren.
  Ein `diff` gegen `build/flash_image.bin` an den ota_0/ota_1-Offsets
  bestätigt Byte-Identität nach dem POST auf `/api/firmware`.
- **Reboot-in-neuen-Slot** lässt sich in QEMU **nicht** Ende-zu-Ende
  testen — jede `esp_restart()`-Auslösung (OTA-Abschluss,
  Factory-Reset, Watchdog) trifft denselben Bug. Echte Hardware hat
  das Problem nicht, weil der ESP32-Reset-Controller die
  Peripherien korrekt mit-resettet.

Workaround während QEMU-Entwicklung: nach jedem Szenario, das
`esp_restart()` triggern würde, QEMU komplett beenden (`Ctrl-A X`)
und per `idf.py qemu …` neu starten. Der Kaltstart umgeht den
Emulator-Fehler.

## Flashen auf echte Hardware (später)

Vor dem Flashen auf WiFi umstellen:

```bash
idf.py menuconfig
# Example Connection Configuration
#   → Connect using: WiFi
#   → WiFi SSID / WiFi Password setzen
idf.py -p /dev/ttyUSB0 flash monitor
```

Auf echter Hardware sind die `CONFIG_SIP_CONTACT_*_OVERRIDE`-Einträge zu
entfernen (oder per `menuconfig` leeren): Dort liegt der ESP32 direkt im
LAN, hat eine echte DHCP-IP und braucht keine NAT-Akrobatik mehr.

## Struktur

- `main/main.c` — Boot-Sequenz, Netzverbindung, HTTPS-Selbsttest,
  Start des SIP-Tasks
- `main/improv.{c,h}`, `main/improv_proto.{c,h}` — WLAN-Einrichtung ohne
  WPS über den Browser-Installer (Improv-Serial-Protokoll auf UART0,
  Issue #372)
- `main/sip_register.{c,h}` — SIP-REGISTER mit Digest-Auth, select-basierter
  Empfangs-Loop für eingehende SIP-Pakete
- `main/Kconfig.projbuild` — Konfigurationsmenü (Token, URL, Testnummer,
  SIP-Credentials, NAT-Overrides)
- `main/CMakeLists.txt` — Komponenten-Registrierung
- `sdkconfig.defaults` — versionierte Default-Einstellungen (Ethernet,
  TLS-Bundle, leere Credentials-Placeholder)
- `sdkconfig.defaults.local` — lokale Credential- und NAT-Overrides,
  **gitignored** (`sdkconfig.defaults.local*` auch)
