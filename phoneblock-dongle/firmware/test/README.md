# Firmware-Tests

Host-basierte Unit-Tests für die pure-C-Parser unter `../main/`
(`sip_parse`, `tr064_parse`, `api_scan`). Keine
ESP-IDF-Toolchain, keine QEMU-Emulation — direkt mit `gcc` ausführbar,
einmal Suite-Durchlauf dauert ≪ 100 ms.

`test_api_scan` zieht zusätzlich `cJSON` aus
`$(IDF_PATH)/components/json/cJSON/` (nur die `.c`-/`.h`-Dateien, keine
Toolchain-Aktivierung). Default `IDF_PATH=$(HOME)/tools/esp/esp-idf`,
bei abweichendem Pfad: `make test IDF_PATH=/...`.

## Ausführen

```bash
cd phoneblock-dongle/firmware/test
make test
```

Erwartete Ausgabe:

```
88 tests, 0 failures
```

Bei Fehlschlägen erscheint pro betroffenem Testfall eine Zeile mit
Funktionsname, Input, erwartetem Wert und tatsächlichem Wert.

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

1. Den Prototyp in `../main/sip_parse.h`
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
