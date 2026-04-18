# Hardware-Optionen für den PhoneBlock-Dongle

Rekonstruktion der Entscheidungsfindung bei der Modul- und Board-Wahl. Dient
als Referenz, wenn später andere Varianten evaluiert oder eine Eigenentwicklung
aufgesetzt wird. Für den aktuellen Entwicklungsstand siehe
[PROGRESS.md](PROGRESS.md), für Zielsetzung und Architektur
[README.md](README.md).

## Anforderungen

Aus Sicht der Hardware legen folgende Rahmenbedingungen den Suchraum fest:

- **Formfaktor**: USB-Stick, der direkt in den USB-A-Port der Fritz!Box gesteckt
  werden kann — also **USB-A-Stecker** (nicht -Buchse) am Gerät.
- **Stromversorgung**: 5 V / ≤ 500 mA aus dem Router-USB-Port.
- **Funk**: 2,4-GHz-WLAN (b/g/n genügt), kein Bluetooth nötig.
- **Rechenleistung**: Nur Signalisierung (SIP-REGISTER/INVITE/BYE) plus ein
  HTTPS-Request pro Anruf. Keine Audioverarbeitung, kein RTP, kein Codec.
- **Speicher**: 4 MB Flash reichen für Firmware + Zertifikate + OTA-Slot.
- **Speicher RAM**: Zwei-stellige kB für SIP-State + mbedTLS-Session genügen.
- **Budget**: Gesamtpaket (Board + USB-Adapter + Versand) **≤ 10 €/Stück**
  bei realistischer Stückzahl.
- **Produzierbarkeit**: Bei späterer Serienauflage (10–100 Stück) soll eine
  Variante ohne nachträgliches Auslöten von Pins/Buchsen denkbar sein.

## SoC-Familien im Vergleich

| Familie | CPU | WLAN | BT | Natives USB | Typ. Preis | Eignung für Dongle |
|---|---|---|---|---|---|---|
| **ESP32 (Classic)** | Xtensa Dual-Core LX6, 240 MHz | 2,4 GHz b/g/n | Classic + BLE | nein (UART/USB-Brücke) | sehr günstig, riesige Stückzahl | ✅ Standardwahl, reichlich Ressourcen |
| **ESP32-S2** | Xtensa Single-Core LX7, 240 MHz | 2,4 GHz | — | ja (USB-OTG) | ähnlich | ⚠️ kein BT, sonst ok; seltener im Handel |
| **ESP32-S3** | Xtensa Dual-Core LX7, 240 MHz | 2,4 GHz | BLE 5 | ja (USB-OTG) | etwas teurer | ✅ zukunftssicher, nativ USB → spart CH340/CP2102 |
| **ESP32-C3** | RISC-V Single-Core, 160 MHz | 2,4 GHz | BLE 5 | ja | günstigste ESP32-Klasse | ✅ technisch völlig ausreichend, kleinster Fußabdruck |
| **ESP32-C6** | RISC-V Single-Core, 160 MHz | **WiFi 6** + 2,4 GHz | BLE 5 + 802.15.4 | ja | moderat | ✅ Zukunftsformat, aber Overkill |
| **ESP32-H2** | RISC-V Single-Core | kein WLAN, nur 802.15.4 | BLE | ja | — | ❌ scheidet mangels WLAN aus |

**Fazit**: Für diesen Anwendungsfall sind **ESP32-Classic**, **S3** und **C3**
alle technisch passend. Entscheidend ist nicht die CPU, sondern der
**Formfaktor des Boards** und der **Preis**.

## Modul-Bauformen (Classic-ESP32)

Die Espressif-Module tragen einen SoC plus Flash, Oszillator, Antenne und
HF-Abschirmung auf einem kleinen Metalldeckel-PCB.

| Bauform | Merkmale | Relevanz für Dongle |
|---|---|---|
| **WROOM** | Standard, PCB-Antenne oder U.FL, 4 MB Flash | ✅ passt |
| **WROVER** | WROOM + zusätzliches PSRAM (2–8 MB) | ❌ überflüssig, kein Audio, keine großen Puffer |
| **SOLO** | Single-Core-Variante des ESP32 | ✅ möglich, im Handel selten |
| **PICO** (z. B. ESP32-PICO-D4) | System-in-Package (SoC + Flash + Oszillator + Passive in einem Chip) | ✅ kleinste Bauform, ideal für Stick-Formfaktor |

Für den Dongle ist **WROOM** die pragmatische Wahl — riesige Stückzahlen, viele
Tutorials, günstig. Der **PICO-D4** ist technisch attraktiver, wenn Platz knapp
ist (z. B. bei einem fertigen USB-Stick-Formfaktor), taucht aber seltener auf
DIY-DevKits auf.

**Abweichende GPIO-Belegung beachten:** Beim PICO-D4 sind durch DIO-Flash-Modus
GPIO 9 und 10 als freie Pins verfügbar, die auf einem WROOM-32 intern für den
Flash reserviert sind. Firmware-Portierungen zwischen beiden müssen die
Pin-Map anpassen.

### Modul-Suffixe (D, U, E, UE)

Die Buchstaben hinter `ESP32-WROOM-32` sind die wichtigste Stolperfalle beim
Kauf. Zwei unabhängige Dimensionen werden kombiniert:

| Suffix | Silizium-Revision | Antenne |
|---|---|---|
| (keins) | V1 (2017) | PCB |
| **D** | V1 (anderes Flash-Routing) | PCB |
| **U** | V1 | U.FL-Buchse (extern) |
| **E** | **V3 (ECO)** — aktuell, empfohlen | PCB |
| **UE** | V3 (ECO) | U.FL-Buchse |

- **E / UE** sind für Neuprojekte die offiziellen Empfehlungen — sie enthalten
  Bugfixes für PSRAM-Cache, ADC-Glitch auf GPIO36/39, Bluetooth-Reconnect
  und einige WLAN-Disconnect-Edge-Cases.
- Für **diesen konkreten Dongle** sind die E-Fixes **praktisch irrelevant**:
  kein PSRAM, keine ADC-Messung, kein Bluetooth — die WLAN-Disconnects sind
  per Software-Workaround in ESP-IDF ohnehin abgefangen. Selbst ein uralter
  WROOM-32 ohne Suffix würde tadellos laufen.
- **Antennenwahl**: Der Dongle hängt direkt am Router, Signalpegel absurd
  hoch → **PCB-Antenne (E)** reicht vollständig. U-Varianten lohnen nur in
  Metallgehäusen oder für Außeneinsatz.
- **Kombimodul „PCB + U.FL" gibt es offiziell nicht.** Einige Drittanbieter
  bestücken das eine oder andere über einen 0-Ω-Widerstand/Lötjumper; in der
  Praxis wacklig. Wer beides braucht, kauft zwei verschiedene Module.

## USB-Seriell-Wandler auf dem Trägerboard

Der ESP32-Classic hat **keinen nativen USB-Controller** — ein
Zusatzchip übersetzt USB↔UART für Flashen und Log-Monitor.

| Wandler | Treiber-Situation | Preis | Kommentar |
|---|---|---|---|
| **CP2102** (Silicon Labs) | Windows/macOS: signiert, unkompliziert. Linux: im Kernel | mittel | Der „Premium"-Wandler, sehr stabil, hohe Baudraten zuverlässig |
| **CH340C** (WCH) | Linux seit Jahren im Kernel, Windows/macOS oft Treiber nachinstallieren | günstigster | In Stückzahlen billiger als CP2102 — der Massenstandard auf AliExpress |
| **CH9102** (WCH) | Neuerer Chip, Linux-Unterstützung unvollständiger als CH340C | günstig | Begegnet einem auf einigen DevKits, eher vermeidbar |
| **CH343P** (WCH) | Linux-Kernel-Support seit 5.x, Windows/macOS WCH-Treiber | günstig | Neuer als CH340C, bis 6 Mbaud, optionale USB-HID-/Flash-Disk-Modi — wird bei einigen USB-Stick-Dongles verwendet |

**Für den Dongle**: Der Wandler wird nach dem Flashen nur noch für Logs
gebraucht. Im Serieneinsatz spielt er keine Rolle. Beide, CP2102 und CH340C,
sind funktional gleichwertig — CH340C gewinnt beim Preis.

**Alternative**: Chips mit **nativem USB** (S2/S3/C3/C6) brauchen gar keinen
Zusatz-Wandler, das Board wird kleiner und ein Bauteil billiger.

## USB-Buchsentyp (Board-seitig)

| Typ | Baugröße | Mechanik | Vorkommen auf ESP32-Boards |
|---|---|---|---|
| **Mini-USB** | mittel | robuste Pins/Rasten | praktisch ausgestorben |
| **Micro-USB** | klein | filigran, verschleißt bei häufigem Stecken | bis heute häufig |
| **USB-C** (Type-C) | klein | symmetrisch, modern | zunehmend Standard, „zukunftssicher" |

Für einen Dongle, der **einmal eingesteckt und dann liegen gelassen** wird,
spielt die Mechanik kaum eine Rolle. Praktische Priorität:

1. **USB-C** bevorzugen (zukunftssicher, Adapter sind Commodity).
2. Micro-USB akzeptabel.
3. Mini-USB nur, wenn sonst alles passt.

## Formfaktor — wie wird das ein Dongle?

Die beste Board-Wahl nützt wenig, wenn der Stick am Ende nicht als Stick in
den Router passt. Die verlötete USB-Buchse auf einem DevKit ist eine
**Buchse**, kein Stecker. Drei Wege:

### Weg A — DevKit + USB-Adapter

Ein Standard-DevKit (WROOM-32E + CH340C + USB-C-Buchse) plus ein
**kurzes USB-A-Stecker-auf-USB-C-Stecker-Kabel** (5–10 cm). Optisch kein
„echter" Dongle, aber:

- ✅ Sofort verfügbar, 3–4 €/Board + 1–2 € Adapter
- ✅ Kein Custom-PCB, kein eigenes Layout
- ✅ Defekter Adapter oder Board einzeln tauschbar
- ❌ Zwei Bauteile statt einem, mechanisch klobig
- ❌ Die üblichen DevKits haben **Stiftleisten** — für den Dongle im Weg

### Weg B — Fertige USB-A-Boards

Kommerzielle „ESP32-USB-Sticks" mit aufgelötetem USB-A-Stecker direkt am
Platinenrand:

| Produkt | SoC | Besonderheiten | Preis |
|---|---|---|---|
| **EGBO ESP32 USB-Dongle** (AliExpress 1005006593432047) | ESP32-PICO-D4 | USB-A-Plug am PCB + Schutzkappe, CH343P, 4 MB Flash, 80 mA typ. | 6,59 € (1 Stk) · **5,44 €/Stk im 10er** |
| **LilyGO T-Dongle-S3** | ESP32-S3 | USB-A-Plug am PCB, 0,96″ Display, SD-Slot, Open-Hardware | ~18 € |
| **M5Stamp C3U** | ESP32-C3 | USB-A direkt am PCB, winzig | ~7–10 € |
| **ESP32-S2 „PS4-9.0-Exploit"-Dongle** | ESP32-S2 | USB-A-Plug, nativ USB (ohne CH340/CP2102), 4 MB Flash | ~6,30 € |
| ESP8266-„WiFi-Deauther"-Sticks | ESP8266 | Formfaktor passend, aber nicht ESP32 | ~5 € |

**Anmerkung zum EGBO-Dongle:** Gefunden nach Suche „esp32 dongle". 105 Verkäufe,
6 Bewertungen (3,7 ★) — wenig bewährt, aber formfaktormäßig genau das, was der
PhoneBlock-Dongle werden soll (echter USB-Stick mit Kappe, keine Adapter,
keine Pins). Verwandte Angebote desselben Händlers: **„ESP32 Key v3.0"**
(Nachfolger?, 8,79 €) und das **Splatoon-/Tasmota-Community-Pendant** zeigen,
dass der Formfaktor in der Gaming-Hack- und Home-Automation-Szene bereits
etabliert ist.

- ✅ Ideal für Kleinserien ohne eigenes PCB-Design
- ✅ T-Dongle-S3 hat Open-Hardware-Schaltpläne auf GitHub → Basis für
  Eigenentwicklung
- ❌ Preislich über dem 10-€-Budget (T-Dongle)
- ❌ Geringere Auswahl, weniger Händler, schlechtere Lieferzeiten

### Weg C — Custom-PCB mit USB-A-Pads am Platinenrand

Klassische USB-Stick-Technik: die USB-A-Kontakte sind **keine separate
Buchse**, sondern **Leiterbahn-Pads am Rand der Platine**. Die PCB selbst
**ist** der Stecker (wie bei Wemos-D1-Clones).

- ✅ Schönster, kompaktester Formfaktor
- ✅ In Stückzahlen (50+) der günstigste Weg
- ✅ Komplette Kontrolle über Layout, Gehäuse, Kennzeichnung
- ❌ Initialaufwand: KiCad-Layout, JLCPCB-Bestellung, SMT-Bestückung,
  1–2 Proto-Runden
- ❌ Bei 1–10 Stück teurer als DevKit + Adapter (NRE + Mindestbestellmenge)

Grobe Preisschätzung bei **100 Stück** über JLCPCB + SMT-Assembly:

| Position | Preis pro Stück |
|---|---|
| ESP32-C3-MINI-Modul | ~1,50 € |
| AMS1117 + Passive | ~0,30 € |
| PCB (1,6 mm ENIG, USB-Pads selektiv vergoldet) | ~0,50 € |
| SMT-Bestückung | ~1,00 € |
| Gehäuse (3D-Druck oder Spritzguss) | ~0,50 € |
| Versand EU | ~2,00 € |
| **Summe** | **~5,80 €** |

Bei **1–10 Stück** explodiert das auf 15–20 €/Stück (Mindestmengen,
Einrichtungskosten).

## Stiftleisten: verlötet vs. unbestückt

Standard-DevKits werden meist **mit verlöteten Stiftleisten** verkauft — für
Steckbrett und Jumper-Kabel. Für den Dongle:

- **Mit Pins**: Pins schauen nach unten raus, machen den Stick dicker,
  mechanisch störend. Können abgeknipst werden, sieht aber unprofessionell aus.
- **Ohne Pins / „unsoldered"**: Pads frei, Board flach, besser fürs Gehäuse.
  Im Handel seltener, oft nur bei bestimmten Händlern als Variante.

Für Serienproduktion ist „unsoldered" klar überlegen. Für den Einzelaufbau
zum Entwickeln sind Pins kein Showstopper.

## Budget-Analyse (Stand April 2026)

Die Preise sind AliExpress-Spot-Preise und schwanken.

| Variante | Stück 1 | Stück 10 | Anmerkung |
|---|---|---|---|
| WROOM-32 CH340C Type-C (10.000+ verk.) | 3,79 € + 4,38 € Versand = 8,17 € | **3,30 €** versandfrei (32,99 €/10er) | Alte Silizium-Rev., USB-C-Buchse, Pins verlötet, Adapter nötig |
| WROOM-32E CH340 Micro-USB | ~2,60 € + Versand | — | Neuere Rev., Micro-USB-Buchse |
| WROOM-32 CP2102 Type-C | ~4,10 € | ähnlich | Wie oben, nur CP2102 statt CH340C |
| **EGBO PICO-D4 USB-Dongle** | **6,59 €** versandfrei | **5,44 €** (54,39 €/10er) | **Fertiger USB-Stick, kein Adapter, keine Pins, Kappe inklusive** |
| ESP32-S2 „PS4-Exploit"-Dongle | 6,29 € | — | USB-A-Stecker, nativ USB ohne UART-Bridge |
| ESP32-C3 SuperMini | 2–3 € | ~2 € | Winziges C3-Board, USB-C-Buchse, nativ USB |
| ESP32-C6 DevKitC-1 | ~18 € | — | Weit über Budget, WiFi 6 unnötig |
| LilyGO T-Dongle-S3 | ~18 € | ~15 € | Dongle-Formfaktor + Display eingebaut, aber teuer |

**Versand**: Bei AliExpress oft ab 3 Stück oder 10 € Bestellwert versandfrei
aus EU-Lager. Das verschiebt die Rechnung deutlich zugunsten der 10er-Pakete.

## Getroffene Entscheidung

**Gewählt: „10PCS-CH340C TYPE-C" vom 10.000+-verkauft Händler auf AliExpress**
(Item 1005005953505528) — `3,30 €/Stück` im 10er-Pack, versandfrei, Lieferung
aus DE-Lager.

Konkret:

- Modul: **ESP32-WROOM-32** (nicht-E, alte Silizium-Revision) — für den Dongle
  egal, siehe oben
- USB-Wandler: **CH340C**
- Buchse: **USB-C**
- Pins: verlötet (später abknipsen oder bei Serie auf unsoldered-Variante
  wechseln)
- Antenne: PCB

**Zusatz**: USB-A-Stecker → USB-C-Stecker-Kurzkabel als Adapter, damit das
DevKit direkt in den Fritz!Box-Port passt. Kosten: ca. 1–2 €.

### Warum nicht ESP32-C3 Super Mini (günstiger, nativ USB)?

- Großer Erfahrungsschatz mit ESP32-Classic in ESP-IDF-Tutorials und
  Community-Projekten
- Dual-Core gibt Headroom für zukünftige Erweiterungen (z. B. SIP-TLS)
- 10er-Paket liefert Reserve für Prototypen, Reserveboards und Iteration
- Preisunterschied bei 10 Stück minimal (~1 €/Stück)
- Falls Serie kommt, ist der Umstieg auf C3 jederzeit möglich — die
  Firmware-Logik ist SoC-agnostisch

### Warum nicht LilyGO T-Dongle-S3 (fertiger USB-A-Formfaktor)?

- Preis 15 €+, weit über Budget
- Display und SD-Slot sind für diesen Use-Case überflüssig und kosten Strom
- Sobald man eine eigene Serie baut, ist ein Custom-PCB mit C3 billiger
- Als **Referenzdesign** für spätere Eigen-PCB trotzdem interessant

## Offene Hardware-Punkte

Aus [PROGRESS.md](PROGRESS.md):

- [ ] Bestellung 10er-Paket platzieren
- [ ] USB-A-auf-USB-C-Kurzkabel mitbestellen (Datenfähig, nicht nur Charging)
- [ ] Test auf echter Hardware: von QEMU-Ethernet auf echtes WLAN umstellen,
      `CONFIG_SIP_CONTACT_HOST_OVERRIDE`/`PORT_OVERRIDE` leeren
- [ ] Stromaufnahme + Wärmeentwicklung im Dauerbetrieb messen
- [ ] Gehäuse-Design (3D-Druck) oder Schrumpfschlauch-Lösung

## Spätere Iterationen

### Demo-/Endnutzer-Variante ohne eigenes PCB

Der **EGBO ESP32 USB-Dongle (PICO-D4, 5,44 €/Stk im 10er)** liefert ohne
Adapterkabel und ohne Pins genau den Stick-Formfaktor, den Endnutzer erwarten.
Aufpreis gegenüber dem Entwicklungs-DevKit nur ~2 €/Stück. Sobald die Firmware
stabil läuft und eine Pilot-Serie an PhoneBlock-Nutzer verteilt werden soll,
ist das die naheliegendste Upgrade-Option.

Zu verifizieren vor Umstieg:
- GPIO-Map PICO-D4 vs. WROOM-32 in der Firmware korrekt abbilden
- Stromaufnahme unter WLAN-TX-Peaks im Dauerbetrieb (80 mA „typisch" ist
  Mittelwert, Peaks liegen auch hier bei ~300 mA)
- Antennenleistung der PICO-PCB-Antenne — direkt am Router aber unkritisch
- CH343P-Treiber auf den Zielplattformen der Firmware-Flasher

### Eigenentwicklung für echte Serie

Wenn der Dongle als **Serie** für PhoneBlock-Nutzer produziert werden soll,
lohnt sich ein **Custom-PCB mit ESP32-C3** und USB-A-Pads am Platinenrand:

- Geringste Bauteilkosten (C3-Modul ~1,50 €)
- Nativ USB → kein CH340/CP2102/CH343P auf der BOM
- Single-SoC, kleinstes Layout möglich
- Vorbild: LilyGO T-Dongle-S3-Schaltplan, auf C3 vereinfacht
- Open-Hardware-Veröffentlichung (KiCad-Projekt im Repo) denkbar

Für die **jetzige Entwicklungsphase** bleibt das WROOM-32-DevKit mit
Adapterkabel die pragmatischste Wahl.
