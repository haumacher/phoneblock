# Firmware-Tests

Host-basierte Tests für pure-C-Parser und Linux-Plattformdienste der Firmware.
Die Parser-Tests laufen direkt mit `gcc`; die Plattform- und API-Integrationstests
verwenden pthreads, libcurl und OpenSSL.

Die JSON-Tests ziehen `cJSON` aus
`$(IDF_PATH)/components/json/cJSON/` (nur die `.c`-/`.h`-Dateien, keine
Toolchain-Aktivierung). Der Linux-Firmware-Archivbuild verwendet außerdem die
verwalteten libsrtp-Header. Default `IDF_PATH=$(HOME)/tools/esp/esp-idf`, bei
abweichendem Pfad: `make test IDF_PATH=/...`.

## Ausführen

```bash
cd phoneblock-dongle/firmware/test
make test
```

Bei Fehlschlägen erscheint pro betroffenem Testfall eine Zeile mit
Funktionsname, Input, erwartetem Wert und tatsächlichem Wert.

## Linux-Firmwaremodule bauen

```bash
make linux-firmware IDF_PATH=/pfad/zu/esp-idf
```

Das kompiliert die portablen Firmwaremodule mit `-Werror` und erstellt
`../build/linux-host/libphoneblock_firmware_linux.a`. Der API-Integrationstest
linkt gegen dieses Archiv und führt `/api/test` sowie `/api/check-prefix` gegen
einen lokalen HTTP-Testserver aus:

```bash
make test_api_linux IDF_PATH=/pfad/zu/esp-idf
./test_api_linux
```

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
