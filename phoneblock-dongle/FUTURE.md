# FUTURE.md — Linux-Port des PhoneBlock-Dongles

Ziel: die Dongle-Firmware als natives Linux-Programm mit systemd-Dienst, das
den Java/Docker-Answerbot auf Self-Hoster-Systemen (Raspberry Pi, Homeserver,
NAS) ersetzt — aus demselben Quellbaum wie die ESP32-Firmware.

## Stand

Umgesetzt (zuletzt #617, #627, #631, #633). Der ursprüngliche Plan sah eigene
Verzeichnisse `frontend_esp32/` und `frontend_linux/` vor; stattdessen bauen
beide Targets dieselben Module aus `firmware/main/` und `firmware/core/`, nur
die Plattformschicht ist getrennt:

- `core/platform.h` mit `platform_esp32.c` und `platform_linux.c` (Logging,
  Tasks, Mutexe, Zeit, Zufall, Key-Value-Store als Datei mit atomarem Rewrite);
  `platform_linux_net.c` für HTTP (libcurl) sowie TLS, Hashes und Signaturen
  (OpenSSL).
- Web-UI: dieselbe `index.html` und dieselben Handler in `web.c` auf beiden
  Plattformen, über die Abstraktion `web_http.h` (`esp_http_server` bzw.
  libmicrohttpd). Funktionen ohne Linux-Gegenstück (Firmware-Upload,
  Werksreset, Statusmail) blendet die UI aus.
- Einstiegspunkt `main/main_linux.c`, Debian-Paket unter `debian/`
  (Dienstbenutzer `phoneblock`, Zustand unter `/var/lib/phoneblock`), gebaut
  mit `debian/build-deb.sh` in der Version des `dongle-v*`-Tags.
- Betrieb hinter Home-Assistant-Ingress (Pfadpräfix, Anmeldung per Token aus
  der App-Konfiguration).

Build und Tests: `firmware/test/README.md` (`make test-linux`), Paket und
Installation: `debian/README.md`, manuelle Abnahme: `LINUX-VALIDATION.md`.

## Offen

- **CI:** GitHub-Actions-Build des Debian-Pakets für amd64, arm64 und armhf
  mit Release-Artefakten; dazu `make test-linux` in der CI.
- **Abnahme auf einem Raspberry Pi** (frisches Raspberry Pi OS): Paket
  installieren, Einrichtung komplett über `http://<pi>:8080/` (TR-064-Setup der
  Fritz!Box, PhoneBlock-Anmeldung), ein Spam-Testanruf mit Ansage, und der
  Dienst übersteht Neustart, Netzausfall und Fritz!Box-Neustart ohne Eingriff.
- **Nutzerdoku:** Linux-Abschnitt in `GETTING_STARTED.md`, Release-Notes.
- **Web-UI:** Live-Log (`/api/log/stream`) gibt es nur auf dem ESP32; unter
  Linux steht das Log im Journal. Relative URLs in `index.html` würden das
  Umschreiben für Reverse-Proxys überflüssig machen.
- **User-Agent:** Plattformkennung `(Linux)` bzw. `(Linux; HA)`, damit die
  Geräteliste der API-Schlüssel Linux-Installationen unterscheidet.

## Ideen, nicht geplant

- Import der `.phoneblock`-Konfiguration des Java-Answerbots.
- Betrieb ohne Fritz!Box an beliebigen SIP-Registrars (Sipgate, Telekom …).
- Flatpak/Snap, OpenWrt.
