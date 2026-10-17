# Firmware-Tests

Host-basierte Tests für die pure-C-Parser und die Linux-Plattformdienste der
Firmware. Keine ESP-IDF-Toolchain, keine QEMU-Emulation — `make test` braucht
nur `gcc` (mit pthreads) und läuft in wenigen Sekunden.

Die JSON-Tests ziehen `cJSON` aus
`$(IDF_PATH)/components/json/cJSON/` (nur die `.c`-/`.h`-Dateien, keine
Toolchain-Aktivierung). Default `IDF_PATH=$(HOME)/tools/esp/esp-idf`, bei
abweichendem Pfad: `make test IDF_PATH=/...`.

## Ausführen

```bash
cd phoneblock-dongle/firmware/test
make test
```

Bei Fehlschlägen erscheint pro betroffenem Testfall eine Zeile mit
Funktionsname, Input, erwartetem Wert und tatsächlichem Wert.

## Linux-Build der Firmwaremodule (`make test-linux`)

Die portablen Firmwaremodule lassen sich auch für Linux bauen. Die
Plattformschicht ist dafür geteilt: `../core/platform_linux.c` (Logging, Tasks,
Events, Key-Value-Store, MAC — nur libc und pthreads) läuft in `make test` mit;
`../core/platform_linux_net.c` (HTTP über libcurl, TLS, Hashes, Signaturen und
base64 über OpenSSL) wird nur von `make test-linux` gebaut. Dafür zusätzlich
nötig:

- die Entwicklungspakete von libcurl und OpenSSL, unter Debian/Ubuntu:
  ```bash
  sudo apt install libcurl4-openssl-dev libssl-dev
  ```
- die libsrtp-Header unter `../managed_components/` — dieses Verzeichnis ist
  nicht eingecheckt, sondern wird vom ESP-IDF-Komponentenmanager angelegt.
  Einmal `idf.py build` (oder `idf.py reconfigure`) im Firmware-Verzeichnis
  ausführen.

```bash
make test-linux IDF_PATH=/pfad/zu/esp-idf
```

Das kompiliert die portablen Firmwaremodule mit `-Werror` zu
`../build/linux-host/libphoneblock_firmware_linux.a` (auch einzeln:
`make linux-firmware`) und führt dann aus:

- `test_platform_linux_net` — Hashes, base64, ECDSA-Prüfung und die
  Fehlerbenennung von `pb_http_request()`;
- `test_api_linux` — linkt gegen das Archiv und führt `/api/test` sowie
  `/api/check-prefix` gegen einen lokalen HTTP-Testserver aus.

Das Archiv ist kein vollständiger Geräte-Daemon: ESP-Hardwarestart, Web-UI und
Boarddienste bleiben außerhalb dieses Linux-Builds.

## Neue Tests hinzufügen

Ein neuer Testfall ist genau eine Zeile in der passenden `test_*`-Funktion.
Erster Parameter ist der **erwartete Wert**, zweiter der **Input**:

```c
expect_parse_uri("sip:alice@example.com",
                 "\"Alice\" <sip:alice@example.com>;tag=xyz");

expect_normalize_de("017412345678", "+4917412345678");

expect_dialable(false, "**622");
```

Wer einen komplett neuen Parser abdecken will, ergänzt

1. Den Prototyp in `../main/sip_parse.h` (beziehungsweise im jeweiligen
    Modulverzeichnis unter `../core/`)
2. Die Implementation in `../main/sip_parse.c`
3. Einen Wrapper `expect_<fn>(...)` in `test_sip_parse.c`
4. Eine `test_<fn>(void)`-Funktion mit den gewünschten Assertions
5. Einen Aufruf dieser Testfunktion in `main()`

## Warum separat vom ESP-IDF-Build?

Der Test-Runner soll auf jedem Entwickler-Rechner mit einem Standard-`gcc`
laufen, ohne ESP-IDF zu aktivieren. Deshalb enthält `sip_parse.c` bewusst
nur `<string.h>` / `<strings.h>`-Abhängigkeiten — keine `esp_*`-, `lwip`-
oder `mbedtls`-Headers.

Für Integrationstests mit Socket-Code ist eine QEMU-basierte Runde das
geeignete Mittel (siehe `firmware/README.md`).
