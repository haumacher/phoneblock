# Linux-Dienst (Debian-Paket)

Die Dongle-Firmware läuft als nativer Linux-Dienst `phoneblock-dongle` — gleiche
Module, gleiche Web-Oberfläche und gleiche Version wie das ESP32-Image.

## Installation aus dem APT-Repository

Jedes Firmware-Release (`dongle-v*`-Tag) baut die Pakete für Bookworm und Trixie
auf amd64, arm64 und armhf und veröffentlicht sie im APT-Repository
<https://haumacher.github.io/phoneblock/apt/> (Workflow
`.github/workflows/dongle-linux.yml`). Die Seite dort beschreibt, wie man das
Repository einträgt, welche Suite zu welchem System gehört und welche Version in
den Kanälen `stable` und `beta` liegt. Sie entsteht beim Veröffentlichen aus
`debian/apt-index.html` (`debian/apt-publish.sh`).

## Einrichtung

```sh
sudo systemctl enable --now phoneblock-dongle
```

Unter `http://<server>:8080/` läuft dieselbe Oberfläche wie auf dem ESP32-Dongle:
Dort richtest du die Telefonie an der Fritz!Box ein (TR-064) und meldest dich
bei PhoneBlock an. Ohne eingerichtete Anmeldung ist sie nur aus dem lokalen
Netz erreichbar. Firmware-Upload, Werksreset und Statusmail sind
hardwareabhängig und auf Linux nicht verfügbar.

## Port und Adresse der Web-Oberfläche

Standard ist Port 8080 auf allen Adressen. Port und Adresse stehen
(auskommentiert) in `/etc/default/phoneblock-dongle`; nach einer Änderung
`sudo systemctl restart phoneblock-dongle`. Auch ein Port unter 1024 (z. B. 80)
geht ohne weiteren Schritt. Beim direkten Aufruf überschreiben `--web` und
`--web-bind` diese Werte (`phoneblock-dongle --help`).

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

## Fehlersuche

Startet der Dienst nicht oder ist die Oberfläche nicht erreichbar, steht der
Grund im Journal — etwa ein ungültiger Wert in `/etc/default/phoneblock-dongle`
oder ein schon belegter Port:

```sh
systemctl status phoneblock-dongle
journalctl -u phoneblock-dongle -b       # seit dem Systemstart
journalctl -u phoneblock-dongle -f       # laufend mitlesen
```

## Docker

Für NAS und andere Docker-Hosts gibt es den Dienst als Image
[`phoneblock/answerbot2`](https://hub.docker.com/r/phoneblock/answerbot2) auf
Docker Hub (amd64, arm64, armhf). Es enthält genau das Paket aus dem
APT-Repository und entsteht bei jedem Release im selben Workflow
(`phoneblock-dongle/docker`). Tags: die Firmware-Version (z. B. `1.7.0-beta7`),
`beta` für die jeweils neueste Version und `latest` für das neueste stabile
Release.

```sh
docker run -d --name phoneblock --network host --restart unless-stopped \
  -v phoneblock:/var/lib/phoneblock -e TZ=Europe/Berlin \
  phoneblock/answerbot2:beta
```

Oder mit Docker Compose:

```yaml
services:
  phoneblock:
    image: phoneblock/answerbot2:beta
    network_mode: host
    restart: unless-stopped
    environment:
      TZ: Europe/Berlin
    volumes:
      - phoneblock:/var/lib/phoneblock
volumes:
  phoneblock:
```

Danach geht es weiter wie unter "Einrichtung", mit der Oberfläche unter
`http://<server>:8080/`.

- **Netzwerk:** Mit `--network host` erreicht die Fritz!Box den Dienst über
  SIP und RTP (UDP) direkt unter der Adresse des Hosts. Port und Adresse der
  Oberfläche setzen `PHONEBLOCK_WEB_PORT` und `PHONEBLOCK_WEB_BIND` als
  Umgebungsvariablen. Ohne Host-Netzwerk müssen die SIP- und RTP-Ports
  (`sip_lport`, `rtp_port`) weitergeleitet und `contact_host`/`contact_port`
  in den Einstellungen auf die Adresse des Hosts gesetzt werden.
- **Daten:** Einstellungen, Geräte-ID und Ansage liegen im Volume unter
  `/var/lib/phoneblock`. Der Dienst läuft als Benutzer `phoneblock` (UID 100);
  ein eingebundenes Host-Verzeichnis übergibt der Container beim Start diesem
  Benutzer. Wer das nicht will, startet mit `--user <uid>:<gid>` und einem
  Verzeichnis, in das dieser Benutzer schreiben darf.
- **Updates:** `docker pull phoneblock/answerbot2:beta` und den Container neu
  anlegen; die Einstellungen bleiben im Volume.
- **Home Assistant (Ingress):** Hinter dem Ingress von Home Assistant kann
  die Anmeldung bei phoneblock.net nicht per Weiterleitung zurückkommen.
  Dort zeigt die Oberfläche stattdessen **PhoneBlock-Account verknüpfen**:
  die Oberfläche zeigt einen Code, du gibst ihn auf phoneblock.net ein (der
  Knopf öffnet die Seite in einem neuen Tab), und der Dienst holt den Token
  selbst ab. Den
  Aufbau zum Ausprobieren ohne Home Assistant beschreibt
  `docker/ingress-test/README.md`.
- **Vom alten AnswerBot-Container (`phoneblock/answerbot`):** Die Einstellungen
  werden nicht übernommen; Fritz!Box und PhoneBlock-Anmeldung richtest du in
  der Oberfläche neu ein. Der Dienst nimmt Spam-Anrufe an und spielt die Ansage
  ab, ein Gespräch mit dem Anrufer wie beim AnswerBot führt er noch nicht.

Ein Image aus einer veröffentlichten Version selbst bauen (Debian-Syntax der
Version, siehe `debian/pb-version`):

```sh
docker build --build-arg PHONEBLOCK_VERSION=1.7.0~beta7 \
  -t phoneblock-dongle phoneblock-dongle/docker
```

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
