# Debian-Paketierung

Baue das native Linux-Daemon-Paket im Verzeichnis `phoneblock-dongle`:

```sh
dpkg-buildpackage -us -uc
```

Das Paket baut die gemeinsam genutzten Firmware-Module als
`/usr/bin/phoneblock-dongle` und installiert die Web-Oberfläche nach
`/usr/share/phoneblock-dongle/web`. Das Programm nutzt die cJSON-, libsrtp2-,
libcurl-, OpenSSL- und libmicrohttpd-Bibliotheken der Distribution; die
Build-Abhängigkeiten stehen in `debian/control`.

## Einrichtung

Dienst starten und die Web-Oberfläche öffnen:

```sh
sudo systemctl enable --now phoneblock-dongle
```

Unter `http://<server>:8080/` läuft dieselbe Oberfläche wie auf dem ESP32-Dongle:
Dort richtest du die Telefonie an der Fritz!Box ein (TR-064) und meldest dich
bei PhoneBlock an. Ohne eingerichtete Anmeldung ist sie nur aus dem lokalen
Netz erreichbar. Firmware-Upload, Werksreset und Statusmail sind
hardwareabhängig und auf Linux nicht verfügbar. Das Log steht im Journal
(`journalctl -u phoneblock-dongle -f`).

## Einstellungen

Die Einstellungen liegen wie der übrige Gerätezustand in
`/var/lib/phoneblock/dongle.conf`. Der Dienst schreibt die Datei selbst (die
Geräte-ID und die Einstellungen aus der Web-Oberfläche); bei der Installation
wird sie aus der Vorlage
`/usr/share/doc/phoneblock-dongle/examples/dongle.conf.example` angelegt.

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
