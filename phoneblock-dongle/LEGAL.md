# Rechtliche Rahmenbedingungen für PhoneBlock-Hardware

Eine Sammlung aller Wege, wie der PhoneBlock-Dongle an Endnutzer gelangen
könnte, mit den jeweiligen regulatorischen Folgen. Das Dokument beantwortet
die Frage, **warum eine Serienproduktion als Privatperson oder lockerer
Community-Zusammenschluss in der EU praktisch unmöglich ist** — und welcher
Vertriebsweg am Ende übrig bleibt.

Für den Hardware-Entwicklungsstand siehe [HARDWARE.md](HARDWARE.md), für
Projektziele und Architektur [README.md](README.md).

## Ausgangslage

Ein PhoneBlock-Dongle kostet in Bauteilen ca. 3–5 € und hat einen klaren
Nutzwert: Er stellt Fritz!Box-Besitzern, die weder Container noch Raspberry
Pi betreiben wollen, die Answerbot-Funktionalität plug-and-play zur
Verfügung. Die naheliegende Frage ist: **Warum gibt es den Dongle nicht
längst für 15 € im Handel?**

Die Antwort liegt nicht in der Technik, sondern in der Regulatorik. Dieses
Dokument arbeitet alle sinnvollen Vertriebswege durch, von der eigenen
Serienproduktion bis zur Kooperation mit einem chinesischen Händler, und
zeigt auf, welches davon am Ende gangbar ist — und welche strukturelle
Schlussfolgerung sich daraus für EU-Produkthersteller im Allgemeinen
ergibt.

## Option A — Eigene Serienproduktion (10.000 Stück)

Der direkteste Weg: PCB selbst entwerfen, bei einem chinesischen
Contract Manufacturer fertigen lassen, in der EU verkaufen.

### Technische Machbarkeit

Hardwareseitig unproblematisch. Ein ESP32-C3-MINI-Modul plus AMS1117-LDO,
USB-A-Pads direkt am Platinenrand, zehn Passive, ein Gehäuse — das ist
Standardtechnik mit minimalem Risiko.

Eine realistische BOM bei 10.000 Stück Auflage:

| Position | Preis pro Stück |
|---|---|
| ESP32-C3-MINI-Modul | ~1,20 € |
| AMS1117-3.3 + Passive | ~0,30 € |
| PCB (1,6 mm ENIG, USB-Pads vergoldet) | ~0,40 € |
| SMT-Bestückung bei CM | ~0,80 € |
| Spritzguss-Gehäuse (nach Werkzeugamortisation) | ~0,30 € |
| Verpackung | ~0,30 € |
| Fracht EU, verzollt | ~0,50 € |
| **Summe Stückkosten** | **~3,80 €** |

Die eigentliche Herausforderung ist nicht die Stückliste, sondern alles
drumherum.

### Zertifizierungskette

Jedes Gerät mit 2,4-GHz-Funksender fällt unter die **Funkanlagenrichtlinie
(RED, 2014/53/EU)**. Das bedeutet:

- **CE-Kennzeichnung** mit vollständiger Konformitätsbewertung
- **EMV-Prüfung** nach EN 301 489 und EN 55032 — akkreditiertes Labor
- **Funkprüfung** nach EN 300 328 — zusätzliches Laborverfahren
- **Elektrische Sicherheit** nach EN 62368-1 (vormals EN 60950-1)

**Einmalige Laborkosten: 10.000–20.000 €.**

Ein vorzertifiziertes Funkmodul wie das ESP32-C3-MINI-1 (das CE, FCC und IC
bereits trägt) reduziert den Aufwand deutlich, eliminiert ihn aber nicht.
Sobald das Modul in ein Endgerät integriert wird, muss das **Endgerät**
selbst zertifiziert werden — nur in reduziertem Umfang, weil auf
Modul-Zertifikaten aufgesetzt werden darf.

Hinzu kommt die **Technische Dokumentation** (technical file), die
mindestens 10 Jahre nach Inverkehrbringen bereitzuhalten ist: Schaltpläne,
Layouts, BOMs, Risikoanalyse, Prüfprotokolle, DoC. Ohne ordentlich geführtes
Dokumentationssystem ist das bei einer Marktüberwachungsanfrage nicht
beibringbar — und das Fehlen gilt als Indiz für Nicht-Konformität.

### Betriebspflichten

Zusätzlich zur einmaligen Zertifizierung entsteht laufender Aufwand:

- **Stiftung EAR (WEEE-Registrierung)**: Pflicht für jeden Hersteller/Importeur
  von Elektrogeräten in Deutschland. ~500 € einmalig plus jährliche Gebühren
  und mengenabhängige Garantieleistungen für Altgeräterücknahme.
- **LUCID/Zentrale Stelle Verpackungsregister**: Registrierung der
  Verpackungen, Mengenmeldung, Lizenzgebühren je nach Material und Gewicht.
- **Produkthaftpflichtversicherung**: ~500–2.000 €/Jahr, je nach
  Deckungssumme. Formal freiwillig, faktisch unverzichtbar, sobald Geräte
  mit Netzzugang und Router-Kontakt an Dritte gehen.
- **Rücknahmepflicht** nach ElektroG: Jedes verkaufte Gerät muss bei
  Defekt/Ende-der-Nutzung zurückgenommen und fachgerecht entsorgt werden.
- **Garantie und Gewährleistung**: 2 Jahre gesetzlich in der EU,
  Rückstellungen bilden, Support-Infrastruktur betreiben.

### Vorfinanzierung

Für eine 10.000er-Erstserie kristallisiert sich folgender Kapitalbedarf
heraus:

| Posten | Kosten |
|---|---|
| Zertifizierung (CE/RED/EMV/Sicherheit) | ~15.000 € |
| Werkzeugbau Gehäuse-Spritzguss | ~5.000 € |
| PCB-Engineering, 2–3 Proto-Runden | ~10.000 € |
| Erstproduktion 10.000 Stück | ~40.000 € |
| Verpackung, Anleitungen, Übersetzungen | ~3.000 € |
| Fracht, Verzollung, Einfuhrumsatzsteuer | ~5.000 € |
| WEEE/VerpackG Anmeldung + Rückstellung | ~3.000 € |
| Puffer Rückläufer + Unvorhergesehenes | ~10.000 € |
| **Gesamt** | **~90.000 €** |

Das ist die Einstiegshürde. Refinanzierung setzt einen Verkaufspreis von
mindestens 15 € voraus, um nach Steuern und Rücklagen das Risiko zu tragen —
und geht damit deutlich über das ursprüngliche „10 €/Stück"-Budget der
[HARDWARE.md](HARDWARE.md) hinaus.

### Warum das scheitert

Nicht an der Technik, nicht einmal primär am Geld. Es scheitert an der
Tatsache, dass **die 90.000 € vorab investiert werden müssen, ohne dass eine
Abnahmegarantie existiert**. Eine Community wie die PhoneBlock-Nutzerschaft
kann 10.000 Geräte absetzen, vielleicht auch nicht. Eine Privatperson oder
ein Verein hat diese Risikokapazität schlicht nicht — eine Gesellschaft mit
beschränkter Haftung müsste gegründet, finanziert, versichert, geführt und
buchhalterisch betrieben werden. All das für ein Produkt, das bei 15 € Verkaufspreis
höchstens 5 € Deckungsbeitrag erwirtschaftet.

Eine Serienproduktion ergibt daher nur Sinn, wenn:

- Jemand **professionell in die Hardware-Hersteller-Rolle einsteigt**
  (GmbH, Vollzeit, mit Risikokapital), oder
- Ein **existierender Hardware-Hersteller** den PhoneBlock-Dongle als
  Nebenprodukt aufnimmt.

Beide Bedingungen erfüllt PhoneBlock als Community-Projekt nicht.

## Option B — Verschenken statt Verkaufen

Naheliegender Reflex: Wenn die Regulatorik am kommerziellen Vertrieb hängt,
dann verschenkt man die Geräte eben.

Die Antwort ist ernüchternd: **Das ändert praktisch nichts.**

### Der Begriff „Inverkehrbringen"

Artikel 3 (1) der Marktüberwachungsverordnung (EU) 2019/1020 definiert:

> *„Bereitstellung auf dem Markt: jede entgeltliche oder unentgeltliche
> Abgabe eines Produkts zum Vertrieb, Verbrauch oder zur Verwendung auf
> dem Unionsmarkt im Rahmen einer Geschäftstätigkeit."*

Das „unentgeltlich" ist kein Redaktionsversehen. Die EU wollte beim Erlass
der Richtlinien ausdrücklich verhindern, dass Hersteller über
„Kostenlos-Aktionen", Werbegeschenke oder kostenlose Produktmuster die
CE-Pflicht umgehen. Eine Fritz!Box, die als „Geschenk beim Vertragsabschluss"
vergeben wird, muss genauso zertifiziert sein wie eine gekaufte.

### Was trotzdem erhalten bleibt

Bei unentgeltlicher Abgabe in größerem Umfang:

- **CE/RED/EMV**: komplett identisch zu bezahltem Verkauf
- **WEEE/ElektroG**: Herstellerregistrierung bleibt Pflicht, weil Entsorgungsverantwortung übernommen wird
- **Produkthaftung** nach ProdHaftG: gilt auch für Geschenke
- **VerpackG**: LUCID-Registrierung der Verpackung unabhängig vom Preis

### Echte Ausnahmen

Es existieren drei schmale Schlupflöcher:

1. **„Privater Rahmen"**: Einige wenige Geräte an Freunde und Familie fallen
   faktisch nicht unter Marktüberwachung. Die Grenze ist fließend und
   nirgends sauber definiert. In der Praxis ignorieren Behörden einzelne
   Stücke; ab zweistelligen Mengen mit öffentlicher Bewerbung wird es eng.
   **Bei 10.000 Stück greift diese Ausnahme definitiv nicht.**
2. **Bausatz/Kit**: Wer nicht fertige Geräte, sondern PCB plus Bauteile zum
   Selbstlöten abgibt, kann in manchen Auslegungen die Produktrichtlinien
   umgehen — der Empfänger wird zum „Hersteller für Eigenbedarf" (RED
   Artikel 1 Absatz 2). Sobald aber ein vorbestücktes SMD-Board mit
   verlötetem Funkmodul dabei ist, trägt diese Argumentation nicht mehr.
3. **Open-Hardware-Veröffentlichung**: Statt Geräte zu verschenken,
   veröffentlicht man das Design. Der Nutzer bestellt selbst und wird zum
   Eigenbauer. Dazu unten mehr.

Für 10.000 funktionsfähige Sticks gilt: **Verschenken hilft nicht.**

## Option C — Fertige Dongles einkaufen, flashen, weitergeben

Der naheliegende nächste Gedanke: Wenn das Ausgangsgerät bereits zertifiziert
ist — etwa ein im Handel erhältlicher ESP32-USB-Dongle — dann müsste sich
der Aufwand durch bloßes Umflashen drastisch reduzieren.

Das stimmt teilweise. Aber der regulatorische Zauberbegriff heißt **„wesentliche
Veränderung"**.

### Importeur-Pflichten

Sobald man Geräte aus China importiert, übernimmt man nach Artikel 4 der
Marktüberwachungsverordnung die Rolle des **Importeurs**. Das ist
deutlich schlanker als die Herstellerrolle:

- Konformitätserklärung (DoC) des Originalherstellers einholen und
  10 Jahre aufbewahren.
- Kontaktdaten des Importeurs auf Gerät oder Verpackung anbringen.
- Technische Unterlagen auf Anforderung der Marktüberwachung bereitstellen.
- Stichproben durchführen, Beschwerden nachgehen, bei Nichtkonformität
  Rückrufe koordinieren.

Keine eigenen Prüfungen, keine Laborkosten — aber WEEE- und LUCID-Pflichten
gelten weiter, und die Produkthaftung wandert vollständig zum Importeur.

Kritisch: Das funktioniert nur, wenn der Originalhersteller **belastbare
CE-/RED-Dokumentation** liefert. Bei AliExpress-No-Name-Sticks ist das
„CE"-Symbol häufig dekorativ, die DoC nicht beschaffbar oder gefälscht.
Bei seriösen EU-Distributoren (Mouser, Digikey, Reichelt) liegt die
Dokumentation vor.

### Wesentliche Veränderung durch Firmware-Flash

Der **Blue Guide** der EU-Kommission (Leitfaden zur Umsetzung der
Produktvorschriften) legt fest:

> *Wird ein Produkt nach dem Inverkehrbringen wesentlich verändert, gilt es
> als neues Produkt. Die Person, die es verändert, wird zum neuen Hersteller
> mit allen Pflichten.*

„Wesentlich" ist jede Änderung, die die ursprüngliche Zweckbestimmung
verschiebt oder Schutzziele der Richtlinien berührt. Bei Funkgeräten
konkretisiert das die **Delegierte Verordnung (EU) 2022/30** zur RED:
Software, die Funkparameter (Sendeleistung, Frequenzbänder, Duty-Cycle,
Kanalbandbreite) verändert, ist wesentlich. Software, die **nur auf der
Anwendungsebene** läuft und den zertifizierten Funkstack unberührt lässt,
ist es in der Regel nicht.

Die PhoneBlock-Firmware ändert keine Funkparameter — sie nutzt den
ESP-IDF-WLAN-Stack unverändert. Technisch liegt also **keine wesentliche
Veränderung** vor.

Juristisch bleibt dennoch ein Problem.

### Quasi-Hersteller

Nach **§4 Abs. 1 Nr. 2 Produkthaftungsgesetz** gilt als Hersteller auch,
**wer sich durch das Anbringen seines Namens, seiner Marke oder eines anderen
unterscheidungskräftigen Kennzeichens als Hersteller ausgibt**. Dasselbe
gilt nach **§3 Nr. 9 ElektroG**: „Hersteller" ist, wer Geräte erstmals
**unter eigenem Namen oder eigener Marke** anbietet — unabhängig davon, wer
sie physisch produziert.

Sobald also Geräte mit PhoneBlock-Branding auf Gerät oder Verpackung
verteilt werden, greift die volle Herstellerhaftung — selbst bei
vorzertifiziertem Ausgangsprodukt. Die EMV-Laborkosten lassen sich sparen,
die WEEE- und Haftungspflichten nicht.

### Realistischer Umfang

Bei dieser Konstellation sind die verbleibenden Fixkosten:

| Pflicht | Einmalig | Laufend |
|---|---|---|
| EAR-Registrierung (WEEE) | ~500 € | ~500 €/Jahr + mengenabhängige Garantie |
| LUCID (VerpackG) | 0 € | ~50–200 €/Jahr |
| Importeurs-Dokumentation | ~1.000 € Rechtsberatung | 10 Jahre Aufbewahrung |
| Produkthaftpflichtversicherung | — | ~500–2.000 €/Jahr |

Eine Größenordnung unter der eigenen Produktion — aber weiterhin nicht null,
und für eine Community ohne professionelle Struktur immer noch prohibitiv.

## Option D — AliExpress-Privatimport

Der Weg, den jeder Nutzer faktisch heute schon geht: Dongle bei AliExpress
bestellen, selbst flashen, selbst nutzen.

### Wie die Kette theoretisch funktionieren sollte

Die EU hat ein sauberes System gedacht:

1. Der chinesische Hersteller benennt einen **EU-Bevollmächtigten**
   (Authorised Representative), der DoC, technische Unterlagen und
   Ansprechbarkeit sicherstellt. Seit der **General Product Safety
   Regulation (GPSR)** EU 2023/988, gültig ab 13. Dezember 2024, ist das
   für fast alle Produkte Pflicht.
2. Der **Endkäufer** bestellt zum Eigenbedarf, fällt unter die
   Privatimport-Freigrenze, übernimmt keine Vertriebsverpflichtungen.
3. Der **EU-Bevollmächtigte** hält Dokumentation vor, bearbeitet
   Marktüberwachungsanfragen, koordiniert Rückrufe.
4. Der **Zoll** filtert Sendungen ohne korrekte Kennzeichnung heraus.

In diesem Modell hätte jemand die Registrierungskosten längst bezahlt —
nämlich der Hersteller, der den AR beauftragt hat.

### Was tatsächlich passiert

Die Praxis weicht ab:

- **AliExpress, Temu, Shein** haben seit Ende 2024 Pflichtfelder für
  „EU-Responsible-Person". In vielen Fällen ist der Eintrag eine
  Briefkastenfirma in Polen oder Litauen, die für ~30 €/Jahr pro Produkt
  Rubberstamp-Compliance anbietet, ohne je ein Gerät gesehen zu haben.
  DoCs sind häufig frei erfunden.
- **Der Zoll** filtert massenhaft nur bei Verdacht (Markenrechte, Waffen,
  Arzneimittel). Funkgeräte unter 150 € gehen in der Regel durch.
  BNetzA-Kontrollen sind Stichproben.
- **Stiftung EAR** hat seit 2022 Zugriff auf Fulfillment-Dienstleister
  (§3 Nr. 11a ElektroG) — Amazon FBA muss prüfen, ob Verkäufer
  WEEE-registriert sind, und hat daraufhin tausende Konten gesperrt.
  AliExpress liefert direkt aus China, daher greift diese Regel nicht.
- **VerpackG/LUCID**: Bei Direktimport aus Drittländern ist der erste
  inländische Inverkehrbringer pflichtig. Bei Privatimport wäre das der
  Endkäufer — wird aber bei Einzelpaketen nie verfolgt.

### Wer am Ende zahlt

Die ehrliche Antwort: **Niemand.** Oder genauer: das Budget der
Marktüberwachung, das durch Systemversagen den Fehlbetrag trägt. Jede
Stelle in der Kette verlässt sich darauf, dass eine andere Stelle compliant
ist, und am Ende ist es keiner. Deutsche und europäische Hersteller
subventionieren über ihre Registrierungs- und Zertifizierungskosten faktisch
den Direktimport-Markt aus China.

Dass dieses Ungleichgewicht als unfair erkannt ist, zeigt sich an der
beschleunigten Regulierungsaktivität:

- **GPSR** (seit 13.12.2024) verpflichtet Marktplätze, Verkäufer ohne
  EU-AR zu sperren. Erste Löschwelle bei AliExpress Anfang 2025.
- **Zollreform 2028**: Die 150-€-Zollfreigrenze fällt. Jedes Paket aus
  Drittländern wird verzollt und dokumentiert.
- **Digital Services Act**: AliExpress wurde 2023 als Very Large Online
  Platform (VLOP) eingestuft, muss Marktüberwachung aktiv unterstützen.
- **Einheitliche Marktüberwachungsdatenbank ICSMS**: BNetzA und Zoll können
  unsichere Produkte EU-weit flaggen.

Der Privatimport-Schlupfweg schließt sich in den nächsten 2–3 Jahren.

### Warum das nicht skaliert

Für den Einzelkäufer funktioniert dieser Weg — juristisch grau,
faktisch unbehelligt. Für einen systematischen Community-Vertrieb bricht
er sofort zusammen:

- Sobald **10, 100 oder 10.000 Geräte** an eine Sammeladresse geliefert
  werden, erkennt der Zoll das Muster. Die Sendung gilt nicht mehr als
  Privatimport.
- Sobald die Empfangsperson die Geräte an Dritte weitergibt, wird sie
  zum **Importeur im Sinne Art. 4 VO 2019/1020** mit allen Pflichten.
- Die Ersparnis aus dem Schlupfloch ist **nicht skalierbar** — genau das
  ist der Punkt. Das System setzt darauf, dass Stückzahlen nur
  gewerblich auftreten, und Gewerbliche unter die Pflichten fallen.

## Option E — Co-Branding mit chinesischem Händler

Wenn der chinesische Händler selbst als Verkäufer auf AliExpress auftritt
und der Dongle dort direkt an Endnutzer geht, bleibt man selbst außerhalb
der Lieferkette. Man liefert nur Firmware und Dokumentation.

### Das Athom-/Shelly-Modell

Diese Konstruktion ist in der Open-Hardware-Community etabliert:

- **Athom** (AliExpress-Store) verkauft ESPHome-vorgeflashte Smart-Plugs.
  Der ESPHome-Autor hat keinen Anteil und keine Haftung.
- **Shelly** (EU-Firma, Tasmota-flashbar) — die Tasmota-Community hat
  keinen Vertrag mit Shelly.
- **LibreTiny-kompatible Tuya-Geräte** — Community stellt Firmware bereit,
  Herstellergeräte werden einfach weitergenutzt.
- **RAK Wireless Meshtastic-Boards** — RAK verkauft auf AliExpress,
  Meshtastic-Projekt entwickelt Firmware.

Der chinesische Verkäufer trägt die Importeurs-/Händlerpflichten oder deren
Abwesenheit, die Firmware-Community bleibt Softwareautor.

### Was einen freistellt

Man ist **keiner** der EU-Wirtschaftsakteure (Art. 4 VO 2019/1020),
solange:

| Rolle | Trigger | Vermeidung |
|---|---|---|
| Hersteller | Gerät unter eigenem Namen/eigener Marke | Keine PhoneBlock-Marke auf Hardware/Verpackung |
| Importeur | Physische Einfuhr in die EU | Ware geht direkt CN → Endkunde |
| Händler | Ware in Lieferkette bereitstellen | Keine Berührung mit physischer Ware |
| Fulfillment-Dienstleister | Lager/Versand/Zahlungsabwicklung | Weder Lager noch Bestellabwicklung |
| Bevollmächtigter | Vertrag mit Hersteller | Kein Mandat annehmen |

Als **reiner Softwareautor** schreibt man Firmware, dokumentiert
Kompatibilität, empfiehlt Hardware-Modelle. Das ist keine regulierte
Rolle.

### Wo es zusammenbricht

Drei Bruchstellen, an denen man ungewollt in eine Wirtschaftsakteursrolle
rutscht:

1. **Branding**: Erscheint „PhoneBlock" auf Gerät, Verpackung oder
   AliExpress-Listing, greift §4 ProdHaftG (Quasi-Hersteller) und §3 Nr. 9
   ElektroG (Hersteller durch Markenanbringung). Auch die GPSR-Pflicht
   zum EU-AR fällt auf einen zurück.
2. **Geldfluss**: Jede Zahlung vom Händler an einen selbst —
   Affiliate-Provision, Lizenzgebühr, Sachgeschenk im Gegenzug für
   Empfehlung — verschiebt die Rolle. Aus Autor wird Vertriebspartner.
3. **Empfehlungskommunikation**: Ein direkter Kauflink auf
   phoneblock.net mit Affiliate-ID wäre kennzeichnungspflichtige Werbung
   (§5a UWG). Eine unvergütete Empfehlung mit Hinweis „getestete
   Community-Hardware" ist dagegen unbedenklich.

### Was trotzdem bleibt

Auch ohne formale Haftung:

- **Support-Last**: Jede Nutzeranfrage landet bei einem selbst.
  Chinesische AliExpress-Verkäufer reagieren auf technische Fragen
  erfahrungsgemäß mit Standardantworten. Bei Router-Schäden schreibt der
  Nutzer dem Firmware-Autor.
- **Qualitätsrisiko**: Der Händler kann jederzeit die BOM ändern
  (ESP32 durch billigeren Klon), das Gehäuse, den Flash-Zustand, die
  Verfügbarkeit. Man hat keinerlei Kontrolle.
- **Reputationsrisiko**: Wird ein empfohlenes Produkt als unsicher oder
  gesundheitsgefährdend auffällig, fällt das auf die Empfehlung zurück —
  moralisch, und im Zweifel über §280 BGB auch vertragsrechtlich.

## Option F — Generisches Produkt mit austauschbarem Provider (gewählt)

Die eleganteste Konstruktion: Der Dongle ist kein PhoneBlock-Produkt,
sondern ein **generischer intelligenter Spam-Anrufbeantworter** mit
austauschbaren Spam-Check-Providern. PhoneBlock ist ein Provider von
mehreren.

### Das SpamBlocker-Modell

Die F-Droid-Android-App **SpamBlocker** pflegt intern eine Liste
unterstützter Services: Tellows, PhoneBlock, diverse CSV-Blocklisten,
Offline-Datenbanken. Der Nutzer wählt im Setup, welcher Dienst abgefragt
wird. Niemand fragt den PhoneBlock-Betreiber nach CE-Konformität der App,
weil PhoneBlock lediglich Endpunkt einer austauschbaren Schnittstelle ist.

Genau dieses Verhältnis soll der Dongle zu PhoneBlock einnehmen:

- **Gerät** → „Intelligent SPAM-AB" oder vergleichbare neutrale Bezeichnung
- **Firmware** → generisch, mit Provider-Abstraktion
- **PhoneBlock** → ein Default-Preset, aber nicht das einzige Backend
- **Andere Provider** → Tellows, lokale Blocklisten, Custom-URL, regionale
  Communities

### Rollenverteilung

| Akteur | Rolle | Regulatorischer Status |
|---|---|---|
| Chinesischer Händler (EGBO o. ä.) | Hersteller/Verkäufer | trägt eigene Produktverantwortung |
| Firmware-Autor (PhoneBlock-Projekt) | Softwareautor | nicht Wirtschaftsakteur |
| PhoneBlock-Server | Diensteanbieter (ein Provider von mehreren) | Impressum, Datenschutzerklärung |
| Endnutzer | Käufer & Eigenbauer (flasht ggf. selbst) | Endverbraucher |

Das entscheidende Merkmal: **Keine der Rollen kann auf das PhoneBlock-Projekt
Herstellerpflichten aufladen**, weil weder Marke auf Hardware erscheint noch
Geldfluss existiert noch eine Vertriebsbeziehung besteht.

### Firmware-Architektur

Die Trennung ist im aktuellen Quellcode bereits implizit angelegt. Der
PhoneBlock-spezifische Teil konzentriert sich auf `main/api.c` — SIP, RTP
und Audio sind bereits providerunabhängig. Eine saubere Refaktorisierung
würde:

- eine **Provider-Abstraktion** mit Funktionszeigern `check_number()`,
  `report_spam()`, `get_metadata()` einführen,
- PhoneBlock-spezifischen Code nach `providers/phoneblock.c` verschieben,
- ein zweites Backend (etwa Tellows) als Proof-of-Concept ergänzen,
- das Setup-Web-UI um einen Provider-Picker erweitern.

SIP/RTP/Audio bleiben unangetastet — die Arbeit beschränkt sich auf
~200–300 Zeilen C.

### Hersteller-Pitch

Für den chinesischen Händler wird das Angebot dadurch **attraktiver**:

- Statt: „Baut einen Dongle, der nur mit einer deutschen Community-Website
  funktioniert"
- Nun: „Baut einen universellen Anti-Spam-Dongle mit austauschbaren
  Providern. Firmware ist fertig, deutsche Community schickt sofort Käufer,
  internationaler Markt offen."

Die MOQ-Verhandlung verschiebt sich zugunsten einer breiteren Zielgruppe:
nicht mehr ~20.000 deutsche PhoneBlock-Fritz!Box-Nutzer, sondern jeder
Fritz!Box-Besitzer mit Spam-Problem, potenziell EU-weit oder global.

### Hinweis auf phoneblock.net

Für die Community-Sichtbarkeit entsteht eine Unterseite analog zur
SpamBlocker-Servicelisting:

- Tabelle getesteter Geräte mit AliExpress-Listing verlinkt
- Kurzes Setup-Protokoll
- Bewusst mehrere Hardware-Optionen, sobald vorhanden — verstärkt den
  „wir sind einer unter vielen"-Charakter
- Kein Shop-Charakter, keine Kaufbuttons, keine Affiliate-Links
- Formulierung: „Folgende im Handel erhältlichen Geräte unterstützen die
  PhoneBlock-API als einen von mehreren Spam-Check-Providern."

Damit rückt das Projekt sprachlich und strukturell von der Hardware ab:
PhoneBlock ist **Diensteanbieter**, dessen Schnittstelle von Drittgeräten
genutzt werden kann.

## Fazit — Warum in der EU faktisch niemand produziert

Die Diskussion zeigt ein strukturelles Muster: **Kleine und mittlere
Akteure können in der EU keine Elektronik-Endgeräte in Verkehr bringen**,
ohne die Kostenbasis eines etablierten Industrieunternehmens zu haben.

### Die Fixkostenschwelle

Ein Elektronikgerät mit Funkkomponente kostet vor dem ersten verkauften
Exemplar:

- Zertifizierung: 10.000–20.000 €
- Registrierungen und Versicherungen: 1.000–3.000 € jährlich
- Technisches Dossier, Rechtsberatung: mehrere tausend Euro
- 10 Jahre Dokumentations- und Rücknahmepflicht

Das amortisiert sich bei Produkten mit 5 € Deckungsbeitrag erst ab
einigen tausend Stück. Eine Community, die unsicher ist, ob sie 1.000
oder 10.000 Geräte absetzen kann, scheitert genau an dieser Unsicherheit.

### Die Asymmetrie zu Direktimporten

Während EU-Hersteller die komplette Registrierungs- und
Dokumentationsmaschine tragen, gehen Millionen von AliExpress-Paketen
mit nominaler oder gefälschter CE-Kennzeichnung durch den Zoll. GPSR
und die Zollreform 2028 versuchen, das zu schließen — aber bis dahin
besteht die Schieflage weiter, und nach der Schließung bleiben die
Fixkosten für EU-Akteure bestehen. Das Marktversagen wird nicht durch
gesenkte Fixkosten für EU-Produzenten gelöst, sondern durch erhöhte
Hürden für Drittland-Importe. EU-Akteure stehen weiterhin außen vor.

### Der strukturelle Punkt

Das EU-Produktrecht ist so angelegt, dass **jede einzelne Pflicht für
sich sinnvoll** ist — Verbraucherschutz, Umwelt, Funkstörungssicherheit,
Produkthaftung. Aber die **Summe** der Pflichten lässt nur zwei
Akteurstypen am Markt überleben:

1. Etablierte Industrieunternehmen mit eigenen Compliance-Abteilungen,
   für die die Kosten im Umsatzpromille-Bereich liegen.
2. Importeure, die aus Drittländern Massenware einführen und die
   Stückkosten über Volumen drücken.

Kleinstserien, Einzelunternehmer, Vereine, Open-Hardware-Initiativen und
Community-Projekte fallen **strukturell** durchs Raster. Die EU hat für
sie keine tragfähige Konstruktion — Schwellenwerte für „Kleinserien" oder
„nichtkommerzielle Abgabe" fehlen weitgehend oder sind so eng, dass sie
für funkenthaltende Produkte nicht greifen.

Für das PhoneBlock-Projekt folgt daraus, dass **eigene Hardware-Produktion
keine realistische Option ist** — unabhängig von Geldaufwand oder
technischer Machbarkeit. Die gewählte Konstruktion (Option F) umgeht das
Dilemma nicht durch Regelverstoß, sondern durch **Rollenvermeidung**:
Nicht Hersteller werden, sondern Softwareautor bleiben. Die Hardware
macht jemand anderes, der diese Rolle bereits einnimmt.

Das ist keine elegante Lösung, sondern die einzige verbleibende.

## Empfohlene Vorgehensweise

Zusammengefasst der Weg, der sich aus der Analyse ergibt:

1. **Firmware refaktorisieren** zu einer Provider-unabhängigen Architektur.
   PhoneBlock als ein Backend von mehreren.
2. **Zweites Provider-Backend** implementieren (Tellows oder CSV-Blockliste),
   um die Austauschbarkeit nicht nur zu behaupten, sondern zu demonstrieren.
3. **Neutrale Produktbezeichnung** wählen und durchziehen: der Dongle ist
   „Intelligent SPAM-AB" oder vergleichbar, nicht „PhoneBlock-Dongle".
4. **Chinesischen Hersteller ansprechen** mit dem generisch formulierten
   Angebot. EGBO als Ausgangskontakt naheliegend.
5. **Kompatibilitätsseite auf phoneblock.net** als unverbindliche
   Community-Empfehlung gestalten, mehrere Geräte parallel listen.
6. **Keine Markenlizenzierung**, **keine Geldflüsse**, **keine
   Vertriebskooperation** schriftlich oder mündlich vereinbaren. Handshake
   auf Firmware-Veröffentlichungsebene, nicht mehr.

Damit bleibt das PhoneBlock-Projekt, was es ist: eine Software-Community
mit einer öffentlich zugänglichen API, deren Schnittstelle zufällig auch
von Hardware Dritter genutzt werden kann.

## Referenzen

- **Marktüberwachungsverordnung (EU) 2019/1020** — Artikel 4 definiert
  Wirtschaftsakteure und ihre Pflichten
- **Funkanlagenrichtlinie (RED) 2014/53/EU** — Zertifizierungsrahmen für
  Funkgeräte
- **Delegierte Verordnung (EU) 2022/30** — Software-Anforderungen an
  Funkgeräte, Begriff der wesentlichen Veränderung
- **General Product Safety Regulation (GPSR) EU 2023/988** — seit
  13.12.2024, EU-AR-Pflicht für fast alle Produkte
- **Blue Guide der EU-Kommission** — Leitfaden zur Umsetzung der
  Produktvorschriften, Kapitel zu „wesentlicher Veränderung"
- **Digital Services Act (DSA) EU 2022/2065** — Marktplatzhaftung für
  VLOPs
- **ElektroG (Deutschland)** — §3 Nr. 9 (Herstellerbegriff), §3 Nr. 11a
  (Fulfillment-Dienstleister)
- **ProdHaftG (Deutschland)** — §4 Abs. 1 Nr. 2 (Quasi-Hersteller durch
  Markenanbringung)
- **VerpackG (Deutschland)** — LUCID-Registrierung bei Zentraler Stelle
  Verpackungsregister
- **UWG (Deutschland)** — §5a (Kennzeichnungspflicht kommerzieller
  Kommunikation)
