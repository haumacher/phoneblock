# Linux-Dienst (Debian-Paket)

Die Dongle-Firmware läuft als nativer Linux-Dienst `phoneblock-dongle` — gleiche
Module, gleiche Web-Oberfläche und gleiche Version wie das ESP32-Image.

## Installation aus dem APT-Repository

Jedes Firmware-Release (`dongle-v*`-Tag) baut die Pakete für amd64, arm64 und
armhf und veröffentlicht sie unter `https://haumacher.github.io/phoneblock/apt`
(Workflow `.github/workflows/dongle-linux.yml`).

| System | Suite |
|---|---|
| Debian 12, Raspberry Pi OS (Bookworm), Ubuntu 22.04 | `bookworm` |
| Debian 13, Raspberry Pi OS (Trixie), Ubuntu 24.04 und neuer | `trixie` |

Kanal `stable` enthält nur Releases, `beta` zusätzlich Vorabversionen (wie die
Firmware-Kanäle).

```sh
sudo curl -fsSLo /usr/share/keyrings/phoneblock-dongle.gpg \
  https://haumacher.github.io/phoneblock/apt/phoneblock-dongle.gpg
echo "deb [signed-by=/usr/share/keyrings/phoneblock-dongle.gpg] https://haumacher.github.io/phoneblock/apt bookworm stable" \
  | sudo tee /etc/apt/sources.list.d/phoneblock-dongle.list
sudo apt update
sudo apt install phoneblock-dongle
```

Updates kommen danach mit `apt upgrade`.

## Einrichtung

```sh
sudo systemctl enable --now phoneblock-dongle
```

Unter `http://<server>:8080/` läuft dieselbe Oberfläche wie auf dem ESP32-Dongle:
Dort richtest du die Telefonie an der Fritz!Box ein (TR-064) und meldest dich
bei PhoneBlock an. Ohne eingerichtete Anmeldung ist sie nur aus dem lokalen
Netz erreichbar. Firmware-Upload, Werksreset und Statusmail sind
hardwareabhängig und auf Linux nicht verfügbar. Das Log steht im Journal
(`journalctl -u phoneblock-dongle -f`).

## Port und Adresse der Web-Oberfläche

Standard ist Port 8080 auf allen Adressen. Geändert wird das über die
Kommandozeile des Dienstes, mit einem systemd-Override:

```sh
sudo systemctl edit phoneblock-dongle
```

```ini
[Service]
ExecStart=
ExecStart=/usr/bin/phoneblock-dongle --config /var/lib/phoneblock/dongle.conf --web 8081 --web-bind 192.168.178.10
```

`--web PORT` setzt den Port (`0` = ein freier Port, siehe `--web-port-file
PATH`), `--web-bind ADDRESS` bindet an eine einzelne IPv4- oder IPv6-Adresse.
Der Dienst läuft als Benutzer `phoneblock`; für einen Port unter 1024 (z. B.
80) zusätzlich `AmbientCapabilities=CAP_NET_BIND_SERVICE` in denselben
`[Service]`-Abschnitt. Danach `sudo systemctl restart phoneblock-dongle`.

## Einstellungen

Die Einstellungen liegen wie der übrige Gerätezustand in
`/var/lib/phoneblock/dongle.conf`. Der Dienst schreibt die Datei selbst (die
Geräte-ID und die Einstellungen aus der Web-Oberfläche); bei der Installation
wird sie aus der Vorlage `/usr/share/phoneblock-dongle/dongle.conf.example`
angelegt.

Wer sie lieber von Hand bearbeitet, hält den Dienst vorher an — er schreibt die
Datei bei jeder Änderung aus seinem Speicher neu und überschreibt sonst die
Handänderung:

```sh
sudo systemctl stop phoneblock-dongle
sudoedit /var/lib/phoneblock/dongle.conf
sudo -u phoneblock /usr/bin/phoneblock-dongle \
  --config /var/lib/phoneblock/dongle.conf --check-config
sudo systemctl start phoneblock-dongle
```

`--check-config` prüft die erforderlichen SIP- und PhoneBlock-Einstellungen.
`--probe-sip` prüft die Namensauflösung des Registrars und das Binden des
lokalen SIP-Transports, ohne eine REGISTER-Anfrage zu senden.

## Paket selbst bauen

Im Verzeichnis `phoneblock-dongle`:

```sh
debian/build-deb.sh        # Quell- und Binärpaket; -b: nur Binärpaket
```

Die Paketversion folgt dem `dongle-v*`-Tag wie die ESP32-Firmware (Abbildung
auf Debian-Syntax: `debian/pb-version`); das Skript schreibt sie nach
`debian/changelog`, die deshalb nicht im Git liegt. Die Pakete landen in
`dist/`. Build-Abhängigkeiten: `debian/control`. Einen Build in einem sauberen
Debian-Container, wie ihn der Release-Workflow macht, startet
`debian/ci-build.sh`.
