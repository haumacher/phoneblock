# Status-LED — Blink-Codes des PhoneBlock-Dongles

Der Dongle hat genau eine Status-LED. Sie spiegelt den Betriebszustand der
Firmware in fünf Mustern wider. Welches Muster gerade läuft, leitet die
Firmware alle 50 ms aus dem aktuellen System­zustand ab — WLAN-Verbindung,
SIP-Registrierung, PhoneBlock-Token und Token-Akzeptanz beim Server.

Quellcode:
[`firmware/main/status_led.c`](firmware/main/status_led.c),
[`firmware/main/status_led.h`](firmware/main/status_led.h).

> **Hinweis:** Der Begriff „Error-Code" ist hier weit gefasst — nur vier der
> fünf Muster zeigen einen gestörten Zustand an. Das fünfte (Dauerlicht)
> bedeutet: alles in Ordnung.

## Übersicht

| Muster       | Kadenz                | Zustand                                      |
|--------------|-----------------------|----------------------------------------------|
| `PAIRING`    | 100 ms an / 100 ms aus| WPS-PBC läuft — Kopplung mit dem Router      |
| `CONNECTING` | 500 ms an / 500 ms aus| WLAN-Verbindung wird aufgebaut               |
| `SETUP`      | 100 ms an / 900 ms aus| Online, aber Konfiguration unvollständig     |
| `DEGRADED`   | 900 ms an / 100 ms aus| Konfiguriert, aber Token vom Server abgelehnt|
| `READY`      | Dauerlicht            | Alles betriebsbereit                         |

In jeder Zeile steht ein Block für 50 ms (entspricht dem Polling-Tick der
Firmware). `█` = LED an, `░` = LED aus.

## `PAIRING` — schnelles Blinken

```
██░░██░░██░░██░░██░░██░░██░░██░░
```

100 ms an, 100 ms aus. Die LED flackert sichtbar.

**Bedeutet:** Der Dongle hat WPS-PBC aktiviert und wartet darauf, dass am
Router der Pairing-Knopf gedrückt wird. Während dieser Phase versucht der
Dongle, die WLAN-Zugangsdaten per WPS zu übernehmen.

**Was tun:** Den WPS- bzw. „Connect"-Knopf am Router drücken (bei einer
Fritz!Box typischerweise der Knopf mit dem WLAN-Symbol, ca. 6 Sekunden
gedrückt halten). Sobald die Verbindung steht, wechselt das Muster auf
`CONNECTING` oder direkt weiter.

## `CONNECTING` — langsames Blinken

```
██████████░░░░░░░░░░██████████░░░░░░░░░░
```

500 ms an, 500 ms aus. Ein ruhiger, gleichmäßiger Pulsschlag.

**Bedeutet:** Das WLAN-Modul ist aktiv, aber der Dongle hat noch keine
IP-Adresse — entweder weil die Verbindung gerade aufgebaut wird, der DHCP
noch keine Adresse vergeben hat, oder die Verbindung kurzzeitig abgerissen
ist und neu aushandelt wird.

**Was tun:** Erst einmal abwarten — eine knappe Minute ist normal.
Bleibt das Muster länger bestehen:

- WLAN-Reichweite prüfen (der Dongle braucht 2,4-GHz-WLAN).
- Router-Logs auf MAC-Filter oder fehlgeschlagene DHCP-Requests prüfen.
- Einfach weiter abwarten: nach rund 30 vergeblichen
  Verbindungs­versuchen (~2 Minuten) wischt die Firmware die
  gespeicherten WLAN-Zugangsdaten von selbst und fällt in den
  Pairing-Modus zurück (Muster wechselt dann auf `PAIRING`).
  Sobald das passiert, am Router den WPS-Knopf drücken.
- Wer nicht warten will: den Dongle per USB an den Rechner stecken
  und die Firmware mit dem Browser-Flasher neu aufspielen — dabei
  werden die Credentials gleich mit gelöscht.

## `SETUP` — kurzer Puls

```
██░░░░░░░░░░░░░░░░░░██░░░░░░░░░░░░░░░░░░
```

100 ms an, 900 ms aus. Ein kurzes „Blink" einmal pro Sekunde.

**Bedeutet:** Der Dongle ist online (WLAN steht, IP vorhanden), aber die
Konfiguration ist noch unvollständig. Konkret: entweder fehlt der
PhoneBlock-API-Token, oder die SIP-Registrierung beim Router/Provider ist
nicht erfolgreich (oder beides).

**Was tun:** Die Web-Oberfläche des Dongles öffnen — die IP steht im
Router-Verzeichnis der angemeldeten Geräte. Dort prüfen:

- **PhoneBlock-Token gesetzt?** Über die Pairing-Seite mit dem
  PhoneBlock-Konto verknüpfen.
- **SIP-Zugangsdaten korrekt?** Benutzername, Passwort und Registrar (die
  Fritz!Box wird normalerweise automatisch erkannt; bei externem Provider
  muss der Registrar manuell gesetzt werden).
- **Anmeldung am Router erlaubt?** In der Fritz!Box muss „Anmeldung von
  IP-Telefonen erlauben" aktiv sein.

## `DEGRADED` — kurzer Aussetzer

```
██████████████████░░██████████████████░░
```

900 ms an, 100 ms aus. Die LED ist fast durchgehend hell und zuckt einmal
pro Sekunde kurz dunkel — das **visuelle Negativ** zu `SETUP`.

**Bedeutet:** Der Dongle ist vollständig konfiguriert (WLAN steht, SIP
registriert, ein PhoneBlock-Token ist eingetragen), aber der letzte
HTTPS-Aufruf gegen `phoneblock.net` hat **HTTP 401 oder 403** geliefert. Das
heißt: das Token wurde serverseitig abgelehnt — typischerweise weil es
revoziert oder rotiert wurde, nachdem es einmal eingerichtet war. Die Spam-
Datenbank-Abfrage funktioniert in diesem Zustand nicht mehr; eingehende
Anrufe können nicht mehr klassifiziert werden.

Den gleichen Zustand zeigt auch die Web-Oberfläche an („Token abgelehnt"
mit Hinweis „Bitte neu anfordern"). LED und Web-UI ziehen aus derselben
Quelle ([`api_token_is_valid()`](firmware/main/api.h)), die nach jedem
API-Aufruf aktualisiert wird — kein Latch, keine Hysterese.

**Was tun:** In der Web-Oberfläche des Dongles unter dem Token-Status auf
„Neu anfordern" klicken — das startet den OAuth-Flow erneut und hinterlegt
ein frisches Token. Sobald der nächste API-Aufruf eine 2xx-Antwort liefert,
wechselt die LED automatisch wieder auf `READY`.

## `READY` — Dauerlicht

```
████████████████████████████████
```

LED ist konstant an.

**Bedeutet:** Alles bereit. WLAN steht, SIP ist registriert, der
PhoneBlock-Token ist gesetzt **und wurde vom Server akzeptiert** — der
Dongle hört auf eingehende Anrufe und fragt bei jedem unbekannten Anrufer
die PhoneBlock-Datenbank ab.

**Was tun:** Nichts. Der Dongle macht seine Arbeit.

## Wenn die LED komplett dunkel bleibt

Kein Muster ist auch ein Signal: bleibt die LED beim Einstecken vollständig
dunkel, ist die Firmware nicht oder nicht korrekt gestartet. Mögliche
Ursachen:

- Stromversorgung zu schwach (manche USB-Ports am Router liefern wenig
  Strom — alternatives Netzteil testen).
- Firmware nicht geflasht oder Flash-Vorgang abgebrochen.
- Hardware defekt.

Bei vermeintlich toter LED hilft ein Blick in den seriellen Log
(`idf.py monitor` oder ein UART-Terminal an 115200 baud) — die Firmware
loggt jeden Zustandswechsel.
