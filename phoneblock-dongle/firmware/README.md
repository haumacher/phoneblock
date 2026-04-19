# phoneblock-dongle — Firmware

Ausbaustufe der Dongle-Firmware. Nach dem Boot:

1. Netzverbindung aufbauen (Ethernet in QEMU, später WiFi auf Hardware).
2. Einmaliger Selbsttest gegen die PhoneBlock-API mit der konfigurierten
   Testnummer.
3. TCP-Server auf Port **5060** starten, der als Stand-in für den späteren
   SIP-Empfang dient.
4. SIP-Registrierung am konfigurierten Registrar (Fritz!Box), mit periodischem
   Refresh. Danach Empfangs-Loop über `select()` — bereit für eingehende
   Pakete (OPTIONS/INVITE, aktuell nur Logging).

## TCP-Server-Protokoll (Port 5060)

- Pro Verbindung eine Zeile mit einer Telefonnummer + `\n` (optional `\r\n`).
- Server prüft die Nummer über die PhoneBlock-API und antwortet mit einer
  Zeile:
    - `SPAM\n`         — Nummer hat eine oder mehr Spam-Stimmen
    - `LEGITIMATE\n`   — Nummer ist sauber (0 Stimmen)
    - `ERROR\n`        — Eingabe ungültig oder API-Fehler
- Verbindung wird sofort nach der Antwort geschlossen.

Beispiel vom Host aus (nc):

```bash
echo "01749999999" | nc -N localhost 5060
# → LEGITIMATE
```

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

- `CONFIG_PHONEBLOCK_BASE_URL` — Produktions- vs. Testinstanz
- `CONFIG_SIP_CONTACT_HOST_OVERRIDE` / `CONFIG_SIP_CONTACT_PORT_OVERRIDE`
  — NAT-Overrides für den QEMU-Betrieb
- `CONFIG_SIP_TEST_FORCE_SPAM_STAR_NUMBERS` — Test-Hook für den
  RTP/Audio-Pfad

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
für eingehende Pakete (SIP-INVITE, TCP-Dummy-Server-Queries) braucht es
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

### 2) TCP-Dummy-Server vom Host aus abfragen

Mit `hostfwd` kann der Host direkt gegen den Dummy-Server auf
Port 5060 sprechen:

```bash
idf.py qemu --qemu-extra-args="-nic user,model=open_eth,hostfwd=tcp::5060-:5060"
```

Zweites Terminal:

```bash
echo "01749999999" | nc -N localhost 5060
# → LEGITIMATE
```

### 3) SIP-Registrierung an der Fritz!Box

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

### 4) Eingehende SIP-Anrufe (INVITE) — mit NAT-Workaround

Hier hat die Fritz!Box das Problem, den Dongle **aktiv** zu erreichen.
Unsere REGISTER-Pakete tragen standardmäßig `Via: …/UDP 10.0.2.15:5061`
und `Contact: <sip:phoneblock-ab@10.0.2.15:5061>` — das ist eine Adresse,
die nur innerhalb des QEMU-Userspaces existiert. Die Fritz!Box wählt sie
zwar aus der Registration-Tabelle, das Paket versandet aber im NAT.

Damit INVITEs durchkommen, sind zwei Schritte nötig:

**a) QEMU: UDP-Hostfwd auf Port 5061.** So leitet der Host eingehende
SIP-Pakete an den emulierten Guest weiter:

```bash
idf.py qemu --qemu-extra-args="-nic user,model=open_eth,hostfwd=udp::5061-:5061"
```

**b) Firmware: Via/Contact auf die Host-IP umstellen.** Sonst weiß die
Fritz!Box nicht, dass sie `Host-IP:5061` verwenden soll. In
`sdkconfig.defaults.local` (gitignored):

```
CONFIG_SIP_CONTACT_HOST_OVERRIDE="192.168.178.22"   # IP des Host-PCs im LAN
CONFIG_SIP_CONTACT_PORT_OVERRIDE=5061
```

Nach dem Rebuild (`idf.py build`) enthält die REGISTER-Nachricht dann
`Via: …/UDP 192.168.178.22:5061` und `Contact: <sip:…@192.168.178.22:5061>`.
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
INVITE sip:phoneblock-ab@192.168.178.22:5061 SIP/2.0
Via: SIP/2.0/UDP 192.168.178.1:5060;branch=z9hG4bK...
From: <sip:**600**@fritz.box>;tag=...
To: <sip:phoneblock-ab@192.168.178.22:5061>
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

### 5) Web-UI im Browser öffnen

Der Dongle serviert unter Port 80 ein Status-Dashboard. Mit einem
TCP-Hostfwd kommt man vom Host-Browser dran:

```bash
idf.py qemu --qemu-extra-args="-nic user,model=open_eth,\
  hostfwd=udp::5061-:5061,\
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
  TCP-Dummy-Server, Start des SIP-Tasks
- `main/sip_register.{c,h}` — SIP-REGISTER mit Digest-Auth, select-basierter
  Empfangs-Loop für eingehende SIP-Pakete
- `main/Kconfig.projbuild` — Konfigurationsmenü (Token, URL, Testnummer,
  SIP-Credentials, NAT-Overrides)
- `main/CMakeLists.txt` — Komponenten-Registrierung
- `sdkconfig.defaults` — versionierte Default-Einstellungen (Ethernet,
  TLS-Bundle, leere Credentials-Placeholder)
- `sdkconfig.defaults.local` — lokale Credential- und NAT-Overrides,
  **gitignored**
