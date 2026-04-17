# phoneblock-dongle — Firmware

Erste Ausbaustufe der Dongle-Firmware. Nach dem Boot:

1. Netzverbindung aufbauen (Ethernet in QEMU, später WiFi auf Hardware).
2. Einmaliger Selbsttest gegen die PhoneBlock-API mit der konfigurierten
   Testnummer.
3. TCP-Server auf Port **5060** starten, der als Stand-in für den späteren
   SIP-Empfang dient.

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

Zwei Wege, deinen Token zu hinterlegen:

### A) Lokales Default-File (empfohlen für Dev)

Lege `sdkconfig.defaults.local` in diesem Verzeichnis an. Die Datei ist in
`.gitignore`:

```
CONFIG_PHONEBLOCK_TOKEN="pbt_DEIN_TOKEN"
CONFIG_PHONEBLOCK_TEST_NUMBER="030123456"
```

ESP-IDF merged diese Datei automatisch über `sdkconfig.defaults`.

### B) `idf.py menuconfig`

```
PhoneBlock Dongle
  → PhoneBlock Bearer token
  → Test phone number
```

Das schreibt die Werte in `sdkconfig` — ebenfalls gitignored.

## Bauen und in QEMU starten

```bash
source ~/esp/esp-idf/export.sh
cd phoneblock-dongle/firmware

idf.py set-target esp32
idf.py build

# Einfache Outbound-Only-Variante (Selbsttest gegen die API):
idf.py qemu --qemu-extra-args="-nic user,model=open_eth"

# Mit TCP-Port-Forwarding, damit der Host auf den Dummy-Server zugreifen kann:
idf.py qemu --qemu-extra-args="-nic user,model=open_eth,hostfwd=tcp::5060-:5060"
```

Erwartete Ausgabe:

```
I (xxxx) phoneblock: GET https://phoneblock.net/phoneblock/api/num/030123456?format=json
I (xxxx) phoneblock: HTTP 200, NNN bytes
I (xxxx) phoneblock: Raw response: {...}
I (xxxx) phoneblock: ═══════════════════════════════════════
I (xxxx) phoneblock:   Number:    030123456
I (xxxx) phoneblock:   Votes:     N
I (xxxx) phoneblock:   Verdict:   SPAM | clean
I (xxxx) phoneblock: ═══════════════════════════════════════
```

Beenden mit `Ctrl+A`, dann `X`.

## Flashen auf echte Hardware (später)

Vor dem Flashen auf WiFi umstellen:

```bash
idf.py menuconfig
# Example Connection Configuration
#   → Connect using: WiFi
#   → WiFi SSID / WiFi Password setzen
idf.py -p /dev/ttyUSB0 flash monitor
```

## Struktur

- `main/main.c` — Netzverbindung, HTTPS-GET, JSON-Parse
- `main/Kconfig.projbuild` — Konfigurationsmenü (Token, URL, Testnummer)
- `main/CMakeLists.txt` — Komponenten-Registrierung
- `sdkconfig.defaults` — versionierte Default-Einstellungen (Ethernet,
  TLS-Bundle, Placeholder-Token)
- `sdkconfig.defaults.local` — lokale Token-Overrides, **gitignored**
