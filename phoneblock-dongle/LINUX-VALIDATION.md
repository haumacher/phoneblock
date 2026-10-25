# Linux-Dongle testen

Diese Checkliste gilt für den Linux-Dienst mit gemeinsam genutzten Firmware-
Modulen auf einem Debian-Rechner im selben LAN wie die Fritz!Box. SIP- und
Management-Ports dürfen nicht aus dem Internet erreichbar sein.

## Installation und Konfiguration

```sh
sudo apt install ./phoneblock-dongle_0.1.0_$(dpkg --print-architecture).deb
sudoedit /var/lib/phoneblock/dongle.conf
```

Bearbeite den Abschnitt `[phoneblock]`. Trage `sip_host`, `sip_user`,
`sip_pass` und `pb_token` ein. Die standardmäßige PhoneBlock-URL kann bleiben,
außer du verwendest einen Testserver. Eine Ansage wird als rohe, mono G.711-
A-law-Datei mit 8 kHz unter `/var/lib/phoneblock/announcement.alaw` abgelegt.

## Vorabprüfung

Führe die Prüfungen als Dienstbenutzer aus, bevor du systemd aktivierst:

```sh
sudo -u phoneblock /usr/bin/phoneblock-dongle \
  --config /var/lib/phoneblock/dongle.conf --check-config
sudo -u phoneblock /usr/bin/phoneblock-dongle \
  --config /var/lib/phoneblock/dongle.conf --probe-sip
```

`--check-config` prüft die erforderlichen SIP- und PhoneBlock-Einstellungen.
`--probe-sip` prüft die Namensauflösung des Registrars und ob sich der
konfigurierte lokale SIP-Transport binden lässt. Dabei findet keine
Authentifizierung und keine Registrierung statt.

## Dienst testen

```sh
sudo systemctl enable --now phoneblock-dongle
sudo systemctl status phoneblock-dongle
journalctl -u phoneblock-dongle -f
```

Prüfe, dass sich der Dienst an der Fritz!Box registriert, PhoneBlock-Abfragen
ausführt und SIGTERM verarbeitet. Bei SIGTERM wird der Prozess beendet; bei
einem Stopp läuft die Registrierung beim Registrar ab. Ein Web-Dashboard und
eine ordentliche SIP-Abmeldung gehören nicht zu diesem Schritt.

## Umfang

Der Dienst verwendet die gemeinsamen Firmware-Module für SIP, RTP, die
PhoneBlock-API, den Scheduler, Sperrlisten, i18n und Speicher. Firmware-Updates
sind unter Linux deaktiviert. Die ESP32-Weboberfläche und ihr HTTP-Server sind
nicht enthalten; sie folgen in naher Zukunft.
