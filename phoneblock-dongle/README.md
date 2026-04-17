# PhoneBlock Dongle

Ein minimaler ESP32-basierter WLAN-Dongle, der sich als IP-Telefon bei der
Fritz!Box anmeldet und ankommende Spam-Anrufe „wegschnappt", bevor die echten
Telefone klingeln. Der Dongle soll direkt in den USB-Port der Fritz!Box gesteckt
werden können (Stromversorgung über USB, Kommunikation über WLAN).

## Idee

Der bestehende [phoneblock-ab](../phoneblock-ab) (Answer Bot) ist eine
vollständige SIP-Gegensprechanlage in Java, die Spam-Anrufer in ein Gespräch
verwickelt. Dieser Dongle verfolgt ein **reduziertes Ziel**:

- Anruf annehmen, wenn die Nummer auf der PhoneBlock-Blockliste steht.
- **Kein** Gespräch, **kein** Audio, **kein** RTP.
- Sofort wieder auflegen — der Spam-Anruf ist damit „verbraucht", die echten
  Telefone haben nie geklingelt.

Durch den Wegfall der Audioverarbeitung passt die komplette Logik in einen
billigen ESP32 und wird zu einem unauffälligen USB-Stick am Router.

## Hardware

### Modulwahl: **ESP-WROOM-32E**

| Kriterium | Wert | Begründung |
|---|---|---|
| Flash | 4 MB | Firmware + Zertifikate + OTA passen locker |
| SRAM | 520 KB | Reicht für WLAN-Stack + mbedTLS + SIP-State |
| PSRAM | nicht nötig | Keine Audiopuffer, keine großen Datenmengen |
| Antenne | PCB | Dongle steckt direkt am Router — sehr starkes Signal |
| Revision | ECO V3 (`E`) | Aktuelle Silizium-Revision, empfohlen für Neuprojekte |

Varianten wie `WROOM-32UE` (externe Antenne) oder `WROVER-E/IE` (mit PSRAM)
bringen für diesen Anwendungsfall keinen Mehrwert.

### Trägerboard

Ein fertiges NodeMCU-32E-Devkit mit CH340- oder CP2102-USB-Seriell-Wandler und
Micro-USB-Anschluss genügt. Stromaufnahme:

- Idle: ~80 mA
- WLAN-TX-Peaks: ~300 mA
- Der USB-Port der Fritz!Box liefert 5 V / 500 mA — ausreichend.

## Architektur

```
+------------------+        WLAN         +---------------------+
|   Fritz!Box      | <-----------------> |  ESP32-Dongle       |
|   (SIP-Registrar)|       SIP/UDP       |                     |
+--------+---------+                     |  +---------------+  |
         |                               |  | SIP Client    |  |
         | USB (nur 5V)                  |  | Zustands-     |  |
         +-- 5V -----------------------> |  | maschine      |  |
                                         |  +------+--------+  |
                                         |         |           |
                                         |         v           |
                                         |  +---------------+  |
                                         |  | HTTPS-Client  |  |
                                         |  | (mbedTLS)     |  |
                                         |  +------+--------+  |
                                         |         |           |
                                         +---------+-----------+
                                                   |
                                                   v WLAN/Internet
                                         +---------------------+
                                         |  phoneblock.net     |
                                         |  /api/num/{phone}   |
                                         +---------------------+
```

### SIP-Zustandsmaschine

```
        +--------+
        |  BOOT  |
        +---+----+
            |
            | WLAN verbunden
            v
        +--------+
        | IDLE   | <------------+
        +---+----+              |
            |                   |
            | REGISTER alle     |
            | ~60 s             |
            v                   |
        +-----------+           |
        | REGISTERED|           |
        +---+-------+           |
            |                   |
            | INVITE empfangen  |
            v                   |
        +--------------+        |
        | CHECKING     |        |
        | (API-Query)  |        |
        +---+----------+        |
            |                   |
     Spam?  +--- nein ----> 486 Busy / kein Ring
            |                   |
            | ja                |
            v                   |
        +-----------+           |
        | ANSWERED  |           |
        | (200 OK)  |           |
        +---+-------+           |
            |                   |
            | ACK empfangen     |
            v                   |
        +-----------+           |
        | HANGUP    |           |
        | (BYE)     |           |
        +---+-------+           |
            |                   |
            +-------------------+
```

Die SIP-Implementierung benötigt nur einen winzigen Teil von RFC 3261:

- `REGISTER` / `401 Unauthorized` + Digest-Auth / `200 OK`
- Eingehende `INVITE` annehmen (`100 Trying` → `180 Ringing` → `200 OK`)
- `ACK` empfangen, dann selbst `BYE` senden
- `OPTIONS`-Pings beantworten (Fritz!Box-Keepalive)

Kein RTP/SDP-Audio-Handling, nur ein gültiges SDP im 200 OK mit einem
beliebigen Platzhalter-Port, damit die Fritz!Box zufrieden ist.

## Fritz!Box-Konfiguration

1. **Telefonie → Telefoniegeräte → Neues Gerät einrichten → Telefon (mit und
   ohne Anrufbeantworter) → LAN/WLAN (IP-Telefon)**
2. Benutzername, Passwort, interne Rufnummer vergeben (z. B. **620**).
3. Für die **echten** Telefone eine **Klingelverzögerung von 3–5 Sekunden**
   konfigurieren. Dadurch bekommt der Dongle Zeit für den API-Check und das
   Wegschnappen.
4. Den Dongle in die Rufbehandlung für **eingehende Gespräche** aufnehmen, auf
   allen Rufnummern.

## PhoneBlock-Integration

- Endpoint: `GET https://phoneblock.net/phoneblock/api/num/{phone}?format=json`
- Authentifizierung: Bearer-Token (im Dongle konfiguriert)
- Entscheidungsgrundlage: `votes` bzw. Rating aus der Antwort (Schwellwert
  konfigurierbar, Analog zur Mobile-App)
- **Nummernnormalisierung** wie in `NumberAnalyzer`: aus `From:`-Header der
  Fritz!Box (national/international gemischt) die kanonische Form bauen.

### Latenzbudget

| Phase | Zeit (typ.) |
|---|---|
| INVITE eingegangen | t = 0 |
| DNS + TCP + TLS-Handshake (session resume) | ~100–300 ms |
| API-Request + -Response | ~100–500 ms |
| Entscheidung + 200 OK | ~10 ms |
| **Gesamt** | **~0,2–0,8 s** |

Die Fritz!Box wartet ca. 10–20 s auf eine Antwort — das Budget ist komfortabel,
sofern TLS-Session-Reuse aktiv ist. Ohne Session-Reuse kommt pro Anruf ein
voller TLS-Handshake (~500–1500 ms) hinzu, was immer noch akzeptabel wäre.

## Risiken und offene Fragen

- **SIP-ALG / NAT**: Unkritisch, da Dongle und Registrar im selben LAN.
- **SIP-TLS / SRTP**: Falls die Fritz!Box verschlüsselte Signalisierung verlangt,
  wird es komplexer (SIPS + mbedTLS auf SIP-Ebene). Zunächst mit Klartext-SIP
  im LAN starten.
- **Race-Condition Doppel-Annahme**: Zu kurze Klingelverzögerung oder langsamer
  API-Check → echtes Telefon nimmt zuerst ab. Durch konservative Verzögerung
  (5 s) und zügige TLS-Sessions beherrschbar.
- **Caller-ID-Format**: Fritz!Box liefert im `From:`-Header mal national
  (`030…`), mal international (`+49…`), je nach Netz und Konfiguration.
  Normalisierung analog zur bestehenden Backend-Logik.
- **Firmware-Update**: OTA-Update über HTTPS vorsehen, damit der Dongle nach
  der Installation nicht mehr angefasst werden muss.
- **Mehrere Dongles / Redundanz**: Wenn ein Dongle offline ist, darf nichts
  Schlimmes passieren — die Fritz!Box klingelt dann einfach die echten
  Telefone wie bisher.

## Nächste Schritte

1. SIP-Minimal-Stack prototypisch als ESP-IDF-Komponente umsetzen
   (REGISTER + INVITE-Annahme + BYE).
2. Gegen eine lokale Fritz!Box-Testinstallation registrieren.
3. HTTPS-Client an die bestehende PhoneBlock-API anbinden
   (Bearer-Token-Konfiguration analog zu `phoneblock-ab`).
4. End-to-End-Test: Anruf von einem Spam-Test-Nummernpool auf die Box.
5. Konfigurationsspeicher (WLAN-SSID, SIP-Credentials, Bearer-Token) via
   Captive-Portal beim ersten Start.
6. Gehäuse/Formfaktor: 3D-gedruckter USB-Stick-Formfaktor oder fertiges
   „USB-Power-only"-Kabel an Standard-DevKit.

## Verhältnis zu `phoneblock-ab`

| Aspekt | `phoneblock-ab` | `phoneblock-dongle` |
|---|---|---|
| Plattform | Java / Docker / Server | ESP32 / C (ESP-IDF) |
| SIP | Voll mit Audio (mjSIP) | Nur Signalisierung |
| Ziel | Spammer in Gespräch verwickeln | Anruf unterdrücken |
| Statistik/Reporting | Ja, an PhoneBlock zurück | Optional (Minimalbericht) |
| Deployment | Ein Server für viele Nutzer | Ein Gerät pro Haushalt |

Die beiden Projekte ergänzen sich: Der Dongle ist die „immer-an"-Heimlösung für
Endnutzer, während `phoneblock-ab` als zentrale Instanz Daten sammelt und
Spammer aktiv bindet.
