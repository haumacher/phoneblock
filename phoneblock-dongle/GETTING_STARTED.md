# Getting Started — PhoneBlock Dongle

Dieser Leitfaden führt dich Schritt für Schritt durch das Aufsetzen der
Entwicklungsumgebung für den PhoneBlock-Dongle. Ziel: Du kannst die Firmware
**ohne echte Hardware** im QEMU-Emulator bauen, starten und debuggen.

Voraussetzung: Ein Linux-System (Ubuntu/Debian-basiert). Die Anleitung setzt
Ubuntu 22.04 oder neuer voraus; für andere Distributionen die Paketnamen
entsprechend anpassen.

## 1. ESP-IDF installieren

ESP-IDF ist das offizielle Entwicklungsframework von Espressif, basierend auf
FreeRTOS und C/C++.

```bash
sudo apt install -y git wget flex bison gperf python3 python3-pip python3-venv \
    cmake ninja-build ccache libffi-dev libssl-dev dfu-util libusb-1.0-0

mkdir -p ~/esp && cd ~/esp
git clone --recursive --depth 1 -b v5.3 https://github.com/espressif/esp-idf.git
cd esp-idf
./install.sh esp32
```

Für bequemes Aktivieren der Umgebung einen Alias in `~/.bashrc` eintragen:

```bash
alias get_idf='. $HOME/esp/esp-idf/export.sh'
```

In jeder neuen Shell anschließend `get_idf` aufrufen — danach sind `idf.py`,
der Xtensa-Compiler und die Python-Werkzeuge im Pfad.

## 2. QEMU installieren

Espressif liefert eine eigene QEMU-Version als optionales IDF-Tool, das den
Xtensa-Core des ESP32 unterstützt.

```bash
get_idf
python $IDF_PATH/tools/idf_tools.py install qemu-xtensa qemu-riscv32
. $IDF_PATH/export.sh
qemu-system-xtensa --version
```

Die Binaries landen unter `~/.espressif/tools/qemu-xtensa/` und werden nach
dem nächsten `export.sh` automatisch in den Pfad gehängt.

## 3. Erstes Projekt bauen und in QEMU starten

Als Sanity-Check das mitgelieferte Hello-World-Beispiel:

```bash
cp -r $IDF_PATH/examples/get-started/hello_world ~/esp/hello_world
cd ~/esp/hello_world

idf.py set-target esp32
idf.py build
idf.py qemu monitor
```

Erwartung: Boot-Log und die Meldung „Hello world!". Monitor beenden mit
`Ctrl+]`.

## 4. Netzwerk einrichten

### Wichtig: WLAN nicht verfügbar in QEMU

QEMU emuliert **keine WLAN-HF-Ebene**. Versuche, den echten WiFi-Treiber zu
initialisieren, enden mit einem `Guru Meditation Error` in
`register_chipv7_phy`. Stattdessen nutzt man den emulierten **OpenCores
Ethernet MAC** (`open_eth`). Aus Sicht des IP-Stacks verhält er sich
identisch — DHCP, DNS, TCP, TLS, UDP funktionieren ganz normal.

In den meisten ESP-IDF-Beispielen lässt sich das über `idf.py menuconfig`
umschalten:

```
Example Connection Configuration
  → Connect using: Ethernet
  → Ethernet Type: Internal EMAC
  → Ethernet PHY: OpenCores Ethernet MAC
```

### Einfacher Outbound (nur Internet, ohne Inbound)

Für rein ausgehende Verbindungen (z. B. HTTPS zur PhoneBlock-API) reicht
QEMUs User-Mode-Netzwerk. Der richtige Aufruf ist:

```bash
idf.py qemu --qemu-extra-args="-nic user,model=open_eth"
```

Der emulierte ESP32 bekommt automatisch `10.0.2.15` per DHCP, Gateway
`10.0.2.2`, DNS funktioniert. Boot-Log erscheint direkt im Terminal. Beenden
mit `Ctrl+A`, dann `X`.

**Wichtig:** `=` nach `--qemu-extra-args` verwenden, sonst zerlegt der Shell-
Parser die Argumente.

### Monitor mit ELF-Symbolauflösung (getrennter Terminal)

`idf.py qemu monitor` in einem Aufruf funktioniert **nicht** — `idf_monitor`
sucht einen TCP-Socket auf `localhost:5555`, der aber nur geöffnet wird,
wenn QEMU im GDB-Modus läuft. Stattdessen zwei Terminals verwenden:

Terminal 1:
```bash
idf.py qemu --gdb --qemu-extra-args="-nic user,model=open_eth"
```

Terminal 2:
```bash
idf.py monitor -p socket://localhost:5555
```

Der Monitor hat dann auch Zugriff auf die ELF-Symbole und kann Backtraces
mit Funktionsnamen + Zeilennummern auflösen.

### TAP-Device für bidirektionalen SIP-Verkehr

Der Dongle empfängt SIP-`INVITE`-Nachrichten der Fritz!Box per UDP. Für
Inbound-Traffic im Emulator brauchst du ein TAP-Device plus NAT:

```bash
# TAP-Device anlegen (einmalig pro Reboot)
sudo ip tuntap add name tap0 mode tap user $USER
sudo ip addr add 192.168.10.1/24 dev tap0
sudo ip link set tap0 up

# IP-Forwarding und NAT für Outbound-Internet
sudo sysctl net.ipv4.ip_forward=1
UPLINK=$(ip route get 8.8.8.8 | awk '{print $5; exit}')
sudo iptables -t nat -A POSTROUTING -o $UPLINK -j MASQUERADE
sudo iptables -A FORWARD -i tap0 -j ACCEPT
sudo iptables -A FORWARD -o tap0 -m state --state RELATED,ESTABLISHED -j ACCEPT

# QEMU mit TAP + OpenCores-Ethernet starten
idf.py qemu --qemu-extra-args="-nic tap,ifname=tap0,script=no,downscript=no,model=open_eth"
```

Der emulierte ESP32 bekommt z. B. 192.168.10.2. Für lokale SIP-Tests kannst
du auf demselben Host einen Softphone-Server betreiben:

- **PJSUA** (`apt install pjsip-tools`) — simuliert einen anrufenden Teilnehmer
- **Baresip** — leichtgewichtiger SIP-Client für Testanrufe
- **Asterisk** — wenn du die Registrar-Seite mitsimulieren willst

Die echte Fritz!Box kommt erst in der Integrations-Phase mit Hardware zum
Einsatz.

## 5. VS-Code-Integration

Espressif bietet eine offizielle Extension, die Build, Flash, Monitor und
QEMU-Debugging integriert:

```
ext install espressif.esp-idf-extension
```

Nach der Ersteinrichtung (Command Palette → *ESP-IDF: Configure Extension*)
stehen folgende Kommandos zur Verfügung:

- *ESP-IDF: Build Project*
- *ESP-IDF: Launch QEMU Server*
- *ESP-IDF: Launch QEMU Debug Session* — startet GDB-Remote-Debugging im
  laufenden QEMU, Breakpoints + Step-through funktionieren direkt aus dem
  Editor

## 6. Einschränkungen der Emulation

Für den PhoneBlock-Dongle relevant:

| Aspekt | Status in QEMU |
|---|---|
| Xtensa-CPU, RAM, Flash | akkurat |
| FreeRTOS, Timer, Interrupts | akkurat |
| WLAN-Funkebene | **nicht emuliert** — Netzzugriff über TAP-Device |
| WLAN auf IP-Ebene (aus Firmware-Sicht) | funktioniert |
| mbedTLS / HTTPS | funktioniert |
| UDP-Inbound (SIP-`INVITE`) | funktioniert über TAP |
| NVS / OTA-Partition | funktioniert (auf simuliertem Flash-Image) |
| Bluetooth | nicht emuliert |
| ADC, Touch, RMT | teils unvollständig — für den Dongle egal |
| Zyklengenaue Timings | nein, aber für Protokoll-Logik ausreichend |

Die HF-Ebene lässt sich nicht simulieren — das ist normal und für unsere
Zwecke kein Blocker. Alles, was über der IP-Schicht passiert (WLAN-Reconnect,
DHCP, DNS, TLS, SIP, HTTPS, OTA), verhält sich in QEMU identisch zur echten
Hardware.

## 7. Plausibilitätstest: HTTPS-Request aus QEMU

Mit dem offiziellen HTTPS-Beispiel kannst du prüfen, ob dein Setup eine
echte TLS-Verbindung ins Internet bekommt:

```bash
cp -r $IDF_PATH/examples/protocols/http_request ~/esp/http_test
cd ~/esp/http_test

# Ethernet statt WiFi konfigurieren (siehe Abschnitt 4)
idf.py menuconfig   # → Example Connection Configuration → Ethernet/OpenCores

idf.py build
idf.py qemu --qemu-extra-args="-nic user,model=open_eth"
```

Beim ersten Lauf **niemals** den Default-WiFi-Modus der Beispiele in QEMU
belassen — sonst Crash in `register_chipv7_phy`.

### Fallback: QEMU direkt starten

Wenn du volle Kontrolle über die QEMU-Kommandozeile brauchst (z. B. für
tiefgehendes Debugging, `-d guest_errors,unimp` zum Aufspüren von Peripherie-
Aufrufen), QEMU ohne den IDF-Wrapper aufrufen:

```bash
idf.py build

# Merged Flash-Image bauen
esptool.py --chip esp32 merge_bin -o build/flash_image.bin \
    --flash_mode dio --flash_size 4MB --fill-flash-size 4MB \
    0x1000  build/bootloader/bootloader.bin \
    0x8000  build/partition_table/partition-table.bin \
    0x10000 build/<projektname>.bin

# QEMU direkt starten
qemu-system-xtensa -nographic \
    -machine esp32 \
    -drive file=build/flash_image.bin,if=mtd,format=raw \
    -netdev user,id=net0 -device open_eth,netdev=net0
```

`<projektname>.bin` ist die Applikations-Binary im `build/`-Verzeichnis (heißt
genauso wie das Projekt).

Klappt das, kannst du direkt die PhoneBlock-API-Abfrage einbauen:

```
GET https://phoneblock.net/phoneblock/api/num/{phone}?format=json
Authorization: Bearer <dein-Token>
```

## 8. Nächste Schritte

Sobald dein QEMU läuft:

1. Minimalen SIP-Stack aufsetzen (`REGISTER` + `INVITE`-Annahme + `BYE`),
   zunächst gegen einen lokalen Asterisk-Testserver.
2. HTTPS-Client zur PhoneBlock-API anbinden, Bearer-Token aus NVS laden.
3. Zustandsmaschine aus [README.md](README.md) umsetzen.
4. OTA-Update und Captive-Portal-Konfiguration integrieren.
5. Echte Fritz!Box-Integration mit Hardware (ESP32-DevKitC).

Die Architektur und SIP-Flow-Details findest du in der [README.md](README.md)
dieses Moduls.
