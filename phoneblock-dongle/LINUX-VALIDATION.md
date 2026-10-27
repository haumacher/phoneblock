# Linux-Dienst abnehmen

Checkliste für einen Release-Kandidaten auf einem Rechner im selben LAN wie die
Fritz!Box (SIP- und Web-Port nicht aus dem Internet erreichbar). Installation,
Einrichtung und Konfiguration: [debian/README.md](debian/README.md).

- [ ] Installation aus dem APT-Repository (Kanal `beta`) auf Bookworm und
      Trixie; `dpkg-query -W phoneblock-dongle` zeigt dieselbe Version wie
      `firmware_version` in `/api/status` (in Debian-Syntax).
- [ ] Einrichtung allein über die Web-Oberfläche: TR-064-Setup an der
      Fritz!Box (mit 2FA), PhoneBlock-Anmeldung; danach Statuspille
      „Angemeldet".
- [ ] Firmware-Update, Werksreset und Status-E-Mails sind ausgeblendet;
      `GET /health` antwortet `ok`.
- [ ] Spam-Testanruf: wird angenommen, die Ansage läuft, der Anruf steht unter
      „Letzte Anrufe".
- [ ] Eigene Ansage hochladen, abhören, zurücksetzen.
- [ ] `systemctl restart`: Einstellungen bleiben, die Registrierung kommt
      wieder.
- [ ] Netzausfall und Fritz!Box-Neustart: Dienst registriert sich ohne
      Eingriff neu.
- [ ] Geänderter Port per systemd-Override wirkt.
- [ ] `apt upgrade` auf die nächste Version behält `dongle.conf`.
