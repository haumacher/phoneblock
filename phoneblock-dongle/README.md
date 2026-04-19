# PhoneBlock Dongle

Ein ESP32-basierter WLAN-Dongle, der sich als IP-Telefon bei der Fritz!Box
(oder einem beliebigen SIP-Anbieter) anmeldet, eingehende Spam-Anrufe gegen
die PhoneBlock-Datenbank prüft und erkannte Spammer wegschnappt, bevor die
echten Telefone klingeln. Inbetriebnahme im Browser, keine Konfigurationsdatei.

## Features im Überblick

- **SIP-Client** (UDP), Registrar + eingehende INVITE/ACK/BYE/CANCEL,
  Digest-Auth (mit und ohne qop), idempotentes Retransmit-Handling.
- **Spam-Erkennung** via PhoneBlock-HTTPS-API (`/api/num/{phone}` +
  `/api/test`), Bearer-Token authentifiziert.
- **Spam-Ansage** — PCMA-RTP-Stream mit eingebauter Default-Ansage oder
  einer selbst hochgeladenen (WAV/MP3/OGG werden im Browser via Web
  Audio API auf 8 kHz A-law konvertiert und per SPIFFS gespeichert).
- **Fritz!Box-Auto-Provisioning** via TR-064:
  der Dongle legt sich auf Knopfdruck selbst als IP-Telefon an, inkl.
  2FA-Dialog („Knopf drücken / DTMF-Code"), legt gleichzeitig einen
  Phone-only App-User für spätere Sync-Zugriffe an.
- **Andere VoIP-Anbieter** als Preset oder manuelle Eingabe —
  Telekom, Vodafone, 1&1, sipgate, easybell (siehe [PROVIDERS.md](PROVIDERS.md)).
- **PhoneBlock-Token** per OAuth-Flow: Klick im Dongle-UI → Login
  auf phoneblock.net (Google/Facebook/Account) → Token landet
  automatisch zurück im Dongle.
- **Fritz!Box-Sperrlisten-Sync** (opt-in): Am Mobilteil gesperrte
  Nummern werden täglich zur PhoneBlock-Community gepusht und aus
  der FB-Liste entfernt — der Dongle fängt sie danach automatisch ab.
- **Web-Dashboard** mit Live-Statuspillen, letzten Anrufen, Fehler-
  log, Wizards für Erst- und Neu-Einrichtung, Factory-Reset.
- **i18n** DE / EN / FR / ES mit Sprachumschalter.
- **Host-Unit-Tests** für die komplexeren Parser (`sip_parse`,
  `tr064_parse`) — laufen per `make test` in wenigen hundert ms.

## Architektur

```
┌──────────────────┐        WLAN         ┌──────────────────────────┐
│   Fritz!Box      │ ◄─────SIP/UDP─────► │  ESP32-Dongle            │
│   (SIP-Registrar │                     │                          │
│    + TR-064)     │ ◄─TR-064/SOAP/HTTP┐ │  ┌──────────────────┐   │
└────────┬─────────┘                  │ │  │ SIP-Task         │   │
         │                            │ │  │ (REGISTER/INVITE │   │
         │ USB (5V)                   │ │  │  /ACK/BYE/RTP)   │   │
         └─── 5V ────────────────────►│ │  └────────┬─────────┘   │
                                      │ │           │             │
                                      │ │  ┌────────▼─────────┐   │
                                      │ │  │ PhoneBlock-Client│   │
                                      │ │  │ (HTTPS/mbedTLS)  │   │
                                      │ │  └────────┬─────────┘   │
                                      │ │           │             │
                                      │ │  ┌────────▼─────────┐   │
                                      │ │  │ Web-UI           │   │
                                      │ │  │ (esp_http_server)│   │
                                      │ │  └────────┬─────────┘   │
                                      │ │           │             │
                                      │ │  ┌────────▼─────────┐   │
                                      └─┼──┤ Sync-Task (täglich)  │
                                        │  │ Call-Barring →   │   │
                                        │  │ PhoneBlock       │   │
                                        │  └──────────────────┘   │
                                        └─────────────┬────────────┘
                                                      │ HTTPS
                                                      ▼
                                        ┌──────────────────────────┐
                                        │  phoneblock.net          │
                                        │  /api/num, /api/rate,    │
                                        │  /api/test, OAuth-Login  │
                                        └──────────────────────────┘
```

### Komponenten

| Modul | Aufgabe |
|---|---|
| `sip_register.c` | Registrierung, INVITE/ACK/BYE-Dialog, RTP-Start, Dialog-State |
| `sip_parse.{c,h}` | Pure-C-SIP-Header-Parser (host-testbar) |
| `rtp.c` / `audio.c` | PCMA-RTP-Sender-Task + G.711-A-law-Encoder |
| `announcement.{c,h}` | SPIFFS-basierter Announcement-Slot mit Embedded-Fallback |
| `api.c` | PhoneBlock-HTTPS-Client (`check`, `rate`, `selftest`) |
| `tr064.c` + `tr064_parse.{c,h}` | Fritz!Box-SOAP-Client für Setup, 2FA, RegisterApp, CallBarring |
| `sync.c` | Tägliche Sperrlisten-Synchronisation |
| `web.c` + `web/index.html` | Dashboard, Setup-Wizards, REST-API, i18n |
| `config.c` | NVS-gestützte Konfiguration mit Kconfig-Fallback |
| `stats.c` | Ring-Buffer für Anrufe + Fehler, Counter |
| `http_util.{c,h}` | Gemeinsamer User-Agent-Stempel |

## Inbetriebnahme

1. Dongle einstecken (USB-Port der Fritz!Box liefert die 5 V).
2. Im Browser `http://<dongle-ip>/` (oder `http://answerbot/` wenn
   mDNS-Hostname gesetzt — noch offen, siehe [PROGRESS.md](PROGRESS.md)).
3. Auf der Landingpage:
   - **„Telefonie einrichten"** → „Fritz!Box (empfohlen)" → Admin-
     Passwort eingeben → (2FA durchklicken falls aktiv) → Fertig.
   - Alternativ **„Anderer Anbieter"** mit Preset oder
     **„Manuell (Experte)"** für beliebige SIP-Registrars.
4. **„PhoneBlock verbinden"** → OAuth-Redirect zu phoneblock.net →
   Login → Token landet automatisch auf dem Dongle.
5. Der Dongle meldet sich an und fängt ab jetzt Spam-Anrufe ab.

Beide Schritte sind idempotent wiederholbar — ein zweiter Setup-Durchlauf
überschreibt vorhandene Einträge statt sie zu duplizieren.

### Fritz!Box-seitig

Nach der Auto-Provisionierung erscheint der Dongle unter
**Telefonie → Telefoniegeräte** als IP-Telefon „Answerbot". In der
**Rufbehandlung** sollte er auf alle eingehenden Rufnummern reagieren,
und die echten Telefone bekommen eine **Klingelverzögerung von 3–5 s** —
damit der Dongle Zeit für den PhoneBlock-API-Check hat und Spammer
wegschnappen kann, bevor ein Familienmitglied abnimmt.

## Spam-Ansage

Der Dongle spielt beim Annehmen eines Spam-Anrufs eine kurze Ansage ab
(PCMA/G.711 A-law, 8 kHz mono). Im Auslieferungszustand ist eine kurze
Default-Ansage eingebaut. Über das Dashboard-Panel „Spam-Ansage" lässt
sich eine eigene Datei (WAV/MP3/OGG, max. 30 s) hochladen: der Browser
dekodiert, resamplet auf 8 kHz Mono und encodet A-law. Abhören + auf
Default zurücksetzen sind im selben Panel.

## Sperrlisten-Sync (opt-in)

Wenn aktiviert (im Dashboard-Panel „Sperrlisten-Sync"), läuft täglich
ein Hintergrund-Task, der die am Handset gesperrten Nummern aus der
Fritz!Box-Rufsperre abholt, per `POST /api/rate` mit Rating `B_MISSED`
zur PhoneBlock-Community weitergibt und nach erfolgreichem Push aus
der FB-Liste entfernt. Der AB fängt diese Anrufer dann automatisch für
**alle** PhoneBlock-Nutzer ab.

Der Sync nutzt einen eigens beim Setup angelegten Fritz!Box-Account
(via `X_AVM-DE_AppSetup:RegisterApp`) mit ausschließlich Phone-Rechten
und ohne Internet-Zugriff — das Admin-Passwort muss der Dongle damit
**nicht** persistent speichern.

Ein „Jetzt synchronisieren"-Button in der UI triggert einen Lauf auch
dann, wenn die automatische Ausführung aus ist (z. B. für Tests).

## Hardware

**Modul: ESP32-WROOM-32** (CH340C, Type-C) — preisgünstig im 10er-Pack,
4 MB Flash, 520 KB SRAM, PCB-Antenne. Reicht für WLAN + mbedTLS + SIP-
State + SPIFFS-Partition für die Custom-Ansage. Stromaufnahme ~80 mA
idle, ~300 mA WLAN-TX — im USB-Budget der Fritz!Box (500 mA).

Eine ausführliche Diskussion der Modul- und Board-Alternativen sowie
Formfaktor-Optionen (DevKit vs. EGBO-PICO-D4-Dongle vs. Custom-PCB)
findet sich in [HARDWARE.md](HARDWARE.md).

## Latenzbudget

| Phase | Zeit (typ.) |
|---|---|
| INVITE eingegangen | t = 0 |
| DNS + TCP + TLS-Handshake (mit Session-Resume) | ~100–300 ms |
| `GET /api/num/{phone}` + Response | ~100–500 ms |
| Entscheidung + `200 OK` | ~10 ms |
| **Gesamt** | **~0,2–0,8 s** |

Die Fritz!Box wartet ca. 10–20 s auf eine Antwort — das Budget ist
komfortabel. Ohne Session-Resume kommt pro Anruf ein voller TLS-
Handshake (~500–1500 ms) hinzu, immer noch akzeptabel.

## Verhältnis zu `phoneblock-ab`

| Aspekt | `phoneblock-ab` | `phoneblock-dongle` |
|---|---|---|
| Plattform | Java / Docker / Server | ESP32 / C (ESP-IDF) |
| SIP | Voll mit Gesprächsverlauf (mjSIP) | Minimal, nur Ansage |
| Ziel | Spammer in längeres Gespräch verwickeln | Anruf wegschnappen |
| Statistik-Upload | Ja, an PhoneBlock zurück | Optional (Sperrlisten-Sync) |
| Deployment | Ein Server für viele Nutzer | Ein Gerät pro Haushalt |
| Einrichtung | Docker-Compose + Config-Datei | Browser-Wizard |

Die beiden Projekte ergänzen sich: Der Dongle ist die
„immer-an"-Heimlösung für Endnutzer, während `phoneblock-ab` als
zentrale Instanz Daten sammelt und Spammer aktiv bindet.

## Weiterführende Dokumentation

- **[PROGRESS.md](PROGRESS.md)** — aktueller Entwicklungsstand,
  Tech-Debt, Gotchas aus QEMU/Build-System/SIP/TR-064.
- **[HARDWARE.md](HARDWARE.md)** — Modul- und Board-Entscheidungen,
  Formfaktor, Budget.
- **[PROVIDERS.md](PROVIDERS.md)** — SIP-Parameter und
  Setup-Stolperfallen für die gängigen deutschen VoIP-Anbieter.
- **[GETTING_STARTED.md](GETTING_STARTED.md)** — ESP-IDF + QEMU-Setup.
- **[firmware/README.md](firmware/README.md)** — Testszenarien im
  Emulator, Konfigurationsdetails.
- **[LEGAL.md](LEGAL.md)** — Rechtliche Hinweise zur Nutzung.
