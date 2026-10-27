# Linux-Dongle testen

Diese Checkliste gilt für den Linux-Dienst (Debian-Paket aus `debian/`) auf
einem Rechner im selben LAN wie die Fritz!Box. SIP- und Web-Port dürfen nicht
aus dem Internet erreichbar sein.

## Installation

Paket bauen (siehe `debian/README.md`) und installieren:

```sh
debian/build-deb.sh -b
sudo apt install ./dist/phoneblock-dongle_*_$(dpkg --print-architecture).deb
dpkg-query -W phoneblock-dongle
sudo systemctl enable --now phoneblock-dongle
journalctl -u phoneblock-dongle -f
```

Die Paketversion entspricht dem `dongle-v*`-Stand, aus dem gebaut wurde; die
Web-Oberfläche zeigt dieselbe Version in `/api/status` (`firmware_version`, mit
`-` statt `~`/`+`).

## Einrichtung über die Web-Oberfläche

Öffne `http://<server>:8080/` von einem Rechner im LAN:

- **Telefonie einrichten** → Fritz!Box: legt das IP-Telefon per TR-064 an
  (2FA-Bestätigung an der Fritz!Box, falls aktiv).
- **PhoneBlock verbinden**: Anmeldung auf phoneblock.net, der Token landet
  automatisch im Dienst.
- Die Abschnitte Firmware-Update, Werksreset und Status-E-Mails sind auf Linux
  ausgeblendet; `GET /health` antwortet mit `ok`.
- Optional eine eigene Ansage unter „Spam-Ansage" hochladen; sie liegt danach
  als `/var/lib/phoneblock/announcement.alaw`.

## Prüfungen von Hand

Für eine Einrichtung ohne Web-Oberfläche den Dienst anhalten (er schreibt
`dongle.conf` aus seinem Speicher neu), die Datei bearbeiten und als
Dienstbenutzer prüfen:

```sh
sudo systemctl stop phoneblock-dongle
sudoedit /var/lib/phoneblock/dongle.conf
sudo -u phoneblock /usr/bin/phoneblock-dongle \
  --config /var/lib/phoneblock/dongle.conf --check-config
sudo -u phoneblock /usr/bin/phoneblock-dongle \
  --config /var/lib/phoneblock/dongle.conf --probe-sip
sudo systemctl start phoneblock-dongle
```

`--check-config` prüft die erforderlichen SIP- und PhoneBlock-Einstellungen.
`--probe-sip` prüft die Namensauflösung des Registrars und ob sich der
konfigurierte lokale SIP-Transport binden lässt; dabei findet keine
Authentifizierung und keine Registrierung statt.

## Betrieb

- Der Dienst registriert sich an der Fritz!Box (Statuspille „Angemeldet").
- Ein Testanruf einer Spam-Nummer wird angenommen, die Ansage gespielt und der
  Anruf erscheint unter „Letzte Anrufe".
- `systemctl restart phoneblock-dongle`: die Einstellungen aus der
  Web-Oberfläche bleiben erhalten, die Registrierung kommt wieder.
- Netzausfall und Fritz!Box-Neustart übersteht der Dienst ohne Eingriff.
- Bei SIGTERM beendet sich der Prozess; die Registrierung läuft danach beim
  Registrar ab (keine SIP-Abmeldung).
