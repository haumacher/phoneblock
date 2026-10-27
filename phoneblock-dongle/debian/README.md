# Debian-Paketierung

Baue das native Linux-Daemon-Paket im Verzeichnis `phoneblock-dongle`:

```sh
debian/build-deb.sh        # Quell- und Binärpaket; -b: nur Binärpaket
```

Das Skript leitet die Paketversion aus demselben `dongle-v*`-Tag ab wie die
ESP32-Firmware (`git describe`) und schreibt sie nach `debian/changelog` (nicht
im Git): `dongle-v1.7.0-beta4` wird zu `1.7.0~beta4`, ein Stand zwölf Commits
danach zu `1.7.0~beta4+12.g<hash>`. Paket, User-Agent und ESP32-Image desselben
Commits tragen so dieselbe Version. Die Ergebnisse landen in
`phoneblock-dongle/dist/`. Ein direktes `dpkg-buildpackage` scheitert ohne die
erzeugte `debian/changelog`.

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
