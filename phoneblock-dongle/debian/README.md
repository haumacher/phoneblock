# Debian-Paketierung

Baue das native Linux-Daemon-Paket im Verzeichnis `phoneblock-dongle`:

```sh
dpkg-buildpackage -us -uc
```

Das Paket baut die gemeinsam genutzten Firmware-Module als
`/usr/bin/phoneblock-dongle`. Die separate, ältere Implementierung
`frontend_linux` wird nicht verwendet. Das Programm nutzt die cJSON-, libsrtp2-,
libcurl- und OpenSSL-Bibliotheken der Distribution.

Die Einstellungen liegen wie der übrige Gerätezustand unter `/var/lib/phoneblock`:
Der Dienst schreibt `dongle.conf` selbst (die Geräte-ID, später die Einstellungen
aus der Weboberfläche). Bei der Installation wird die Datei aus der Vorlage
`/usr/share/doc/phoneblock-dongle/examples/dongle.conf.example` angelegt. Trage
die SIP-Zugangsdaten und den PhoneBlock-Token ein und prüfe die Konfiguration,
bevor du den Dienst aktivierst:

```sh
sudoedit /var/lib/phoneblock/dongle.conf
sudo -u phoneblock /usr/bin/phoneblock-dongle \
  --config /var/lib/phoneblock/dongle.conf --check-config
sudo systemctl enable --now phoneblock-dongle
```

`--probe-sip` prüft die Namensauflösung und das Binden des lokalen UDP-
Transports, ohne eine REGISTER-Anfrage zu senden. Die Registrierung übernimmt
nach dem Start das gemeinsame SIP-Firmwaremodul. Das Linux-System hat derzeit
dieselbe Web-Oberfläche wie die ESP-Firmware unter `http://<server>:8080/`.
Firmware-Upload, WLAN-Werksreset und E-Mail-Testversand sind hardwareabhängig
und auf Linux nicht verfügbar.
