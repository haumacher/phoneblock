# Debian-Paketierung

Baue das native Linux-Daemon-Paket im Verzeichnis `phoneblock-dongle`:

```sh
dpkg-buildpackage -us -uc
```

Das Paket baut die gemeinsam genutzten Firmware-Module als
`/usr/bin/phoneblock-dongle`. Die separate, ältere Implementierung
`frontend_linux` wird nicht verwendet. Das Programm nutzt die cJSON-, libsrtp2-,
libcurl- und OpenSSL-Bibliotheken der Distribution.

Kopiere `/etc/phoneblock/dongle.conf.example` nach `dongle.conf`, trage die
SIP-Zugangsdaten und den PhoneBlock-Token ein und prüfe die Konfiguration, bevor
du den Dienst aktivierst:

```sh
sudo install -o phoneblock -g phoneblock -m 0600 \
  /etc/phoneblock/dongle.conf.example /etc/phoneblock/dongle.conf
sudoedit /etc/phoneblock/dongle.conf
sudo -u phoneblock /usr/bin/phoneblock-dongle \
  --config /etc/phoneblock/dongle.conf --check-config
sudo systemctl enable --now phoneblock-dongle
```

`--probe-sip` prüft die Namensauflösung und das Binden des lokalen UDP-
Transports, ohne eine REGISTER-Anfrage zu senden. Die Registrierung übernimmt
nach dem Start das gemeinsame SIP-Firmwaremodul. Das Linux-System hat derzeit
kein Web-Dashboard; dieses folgt in Schritt 4.