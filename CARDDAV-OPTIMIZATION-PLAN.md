# CardDAV-Performance-Optimierung — Plan

## Hintergrund

Der PhoneBlock-Server läuft in CPU-Kapazitätsgrenzen. Die Re-Registrierung der
Answerbots scheidet als Hauptursache aus (~1000 Geräte, langsam wachsend). Verdacht
liegt auf der CardDAV-Synchronisation der Fritz!Boxen und anderer Clients, die das
deutlich häufiger als einmal pro Tag tun.

Dieser Plan dokumentiert die Diagnose und priorisiert die Optimierungs-Hebel.

## Untersuchter Code

CardDAV-Stack unter `phoneblock/src/main/java/de/haumacher/phoneblock/carddav/`:

- `CardDavServlet.java` — HTTP-Entry, dispatcht PROPFIND/REPORT/GET/PUT/DELETE.
- `resource/AddressBookCache.java` — Zwei-Stufen-Cache:
  - `_userCache` (per Principal): `AddressBookResource` mit Children.
  - `_numberCache` (per `ListType`): `List<NumberBlock>` für User ohne
    Personalisations/Exclusions ("Common-List").
  - TTL: 5 min unused, 15 min max.
- `resource/AddressBookResource.java`, `AddressResource.java`, `Resource.java`,
  `RootResource.java`, `PrincipalResource.java`.
- DB-Pfad: `SpamReports.getReports()` → `SELECT ... FROM NUMBERS WHERE ACTIVE`
  (Vollscan).

## Datenlage (Apache-Access-Logs, 18.04.–01.05.2026, 14 Tage)

**Volumen:** 10.4 Mio CardDAV-Requests, ~8.6 RPS Schnitt. 75% auf der
Adressbuch-Collection (`/contacts/addresses/{user}/`).

**Client-Verteilung:**

| Klasse | Requests | Anteil | unique IPs/Tag |
|---|---|---|---|
| FritzBox | 7.5 M | 72% | 1.4k |
| iOS | 2.18 M | 21% | **4.6k** |
| PeopleSync | 0.40 M | 4% | 0.9k |
| DAVx5 | 0.10 M | 1% | 0.2k |
| macOS | 45 k | <1% | 22 |

**Auth-Loop (FritzBox macht kein preemptive Auth):**

| | 401 (Challenge) | 207 (mit Auth) | Anteil 401 |
|---|---|---|---|
| FritzBox REPORT | 2.65 M | 2.65 M | **50%** |
| FritzBox PROPFIND | 1.12 M | 1.10 M | **50%** |
| iOS PROPFIND | 36 k | 1.23 M | 3% |
| iOS REPORT | 9.5 k | 0.31 M | 3% |
| DAVx5 / PeopleSync | <2% | | <2% |

→ **3.8 Mio FritzBox-401-Antworten in 14 Tagen, ~3 RPS reine Auth-Roundtrips
ohne Nutzdaten.**

**Antwortgrößen 207 (Mittel):**

| | PROPFIND | REPORT |
|---|---|---|
| FritzBox | 47 KB | 21 KB |
| iOS | **68 KB** | 20 KB |
| macOS | 169 KB | – |
| PeopleSync | 82 KB | 7 KB |
| DAVx5 | 51 KB | 7 KB |

**Polling-Intervall zwischen Sync-Sessions (p50):**

| Client | p25 | **p50** | p75 | p90 |
|---|---|---|---|---|
| iOS | 17 min | **18 min** | 38 min | 72 min |
| macOS | 15 min | **52 min** | 60 min | 62 min |
| PeopleSync | 1 h | **3.8 h** | 4 h | 4.8 h |
| DAVx5 | 55 min | **3.5 h** | 4 h | 5 h |
| FritzBox | 4.5 h | **21.6 h** | 24 h | 238 h |

iOS pollt aggressiv, FritzBox tagesweise.

## Befunde im Code

### Hot Spot 1 — Conditional GET wird ignoriert

ETags werden generiert (`AddressBookResource.java:115`,
`AddressResource.java:68`) und in Antworten eingetragen, aber **`If-None-Match`
wird nirgends gegen den Request-Header geprüft**. Suche nach `If-None-Match` im
Servlet liefert 0 Treffer.

iOS, macOS, DAVx5, PeopleSync schicken `If-None-Match` mit hoher
Wahrscheinlichkeit — würden also `304 Not Modified` akzeptieren und damit den
gesamten Render-Pfad sparen. FritzBox profitiert nicht.

### Hot Spot 2 — Render pro Request, auch bei Cache-Hit

Bei jedem PROPFIND/REPORT:

- DOM-Tree mit 10k–100k `<response>`-Elementen aufbauen
  (`CardDavServlet.java:197–209`).
- Pro Child eine vCard via `StringBuilder` neu generieren
  (`AddressResource.java:92`) — wird **nicht** gecacht.
- Zwei Pässe: erst DOM-Tree komplett bauen, dann `LSSerializer.write()` darüber
  (`CardDavServlet.java:218–223`).

Auch bei vollständigem Cache-Hit auf der `AddressBookResource` läuft dieser
Pfad bei jeder Antwort komplett neu durch.

### Hot Spot 3 — Auth-Loop bei FritzBox

Jeder FritzBox-CardDAV-Request kommt zweimal an: erst 401-Challenge, dann mit
`Authorization`-Header. Verdoppelt die FritzBox-Last.

### Verworfen — Common-Number-Cache-TTL

Initial vorgeschlagen, dann verworfen: Bei 8.6 RPS Gesamtlast wird der
`_numberCache` (TTL 5 min) ohnehin permanent warm gehalten. Die Cache-Misses
sind statistisch selten und nicht der dominante Cost. Die TTL hochzuziehen
würde messbar nichts bringen.

### Verworfen — sync-collection (RFC 6578)

`sync-token` ist explizit als unsupported markiert (`Resource.java:55`).
Implementierung wäre aufwendig (DB-Schema für Versionierung der NUMBERS-Tabelle
nötig). Lohnt sich nicht, solange die einfachere Conditional-GET-Optimierung
(Hebel A) noch offen ist.

## Optimierungs-Hebel

### Hebel A — Conditional GET (`If-None-Match` auf Collection)

**Wirkt auf:** iOS, macOS, DAVx5, PeopleSync (geschätzt ~25% aller
CardDAV-Requests).

**Idee:** `doPropfind()` und `doReport()` werten `If-None-Match` gegen den
Collection-ETag aus. Bei Match: `304 Not Modified` ohne Render.

**Stabiler ETag — wichtig:** Der heutige
`AddressBookResource.getEtag()` (Hash-Summe der Block-IDs) ist
kollisionsanfällig und wird erst nach Cache-Miss + NumberTree-Aufbau berechnet.
Stattdessen aus DB-Aggregat ableiten:

```sql
SELECT MAX(UPDATED), COUNT(*) FROM NUMBERS WHERE ACTIVE
```

Plus User-Settings-Hash (für `ListType`) und Personalizations/Exclusions-Hash
(falls vorhanden). Diese Aggregat-Query ist schnell und kann **vor** dem
Cache-Lookup ausgewertet werden, sodass der 304-Pfad nicht nur den Render,
sondern auch den `loadNumbers()`-Pfad einspart.

**Konkret:**

1. Methode `getCollectionEtag(principal, settings)` in `AddressBookCache` oder
   neu in `SpamReports` Mapper, die das DB-Aggregat zurückgibt.
2. In `doPropfind()` / `doReport()` für Collection-Requests:
   - ETag bestimmen.
   - `If-None-Match`-Header parsen (RFC 7232: kommagetrennte Liste, `*` als
     Wildcard).
   - Bei Match: `setStatus(304)`, `setHeader("ETag", quote(etag))`, return.
3. Bei Hit-Pfad: Der bisherige `getEtag()` der `AddressBookResource` muss zum
   neuen DB-basierten ETag konsistent sein (sonst sendet der Server in der
   207-Antwort einen anderen ETag, als er beim 304 erwartet).

**Risiko:** ETag muss zuverlässig wechseln, sobald sich für den User
relevante Daten ändern. Andernfalls: Clients sehen Updates verspätet.

**Test:** Logs prüfen, ob iOS tatsächlich `If-None-Match` schickt — falls nicht,
ist Hebel A weniger wirksam als geschätzt. Header-Logging temporär aktivieren.

### Hebel B — Render-Pfad: DOM → StAX

**Wirkt auf:** Alle 207-Antworten, vor allem die großen iOS- (68 KB) und
macOS-Antworten (169 KB).

**Idee:** `XMLStreamWriter` (StAX) statt DOM + `LSSerializer`. Schreibt direkt
in den Response-OutputStream:

- Keine Zwischenrepräsentation.
- Ein Pass statt zwei.
- Keine MB-große DOM-Allocation mehr → GC-Druck weg.

**Konkret:**

- `CardDavServlet.doPropfind()` / `doReport()` öffnen einen
  `XMLOutputFactory.newInstance().createXMLStreamWriter(resp.getOutputStream(), "utf-8")`.
- `Resource.propfind()` und `Resource.fillProperty()` werden auf eine neue
  Schreib-API umgestellt: statt `Element propElement` ein
  `XMLStreamWriter writer`.
- vCard-Inhalt: `writer.writeCharacters(vCardContent())` statt
  `appendText(container, vCardContent())`.

**Aufwand:** ~Tag. Touchiert die Resource-Hierarchie, aber das Datenmodell
bleibt unverändert.

**Wirkung:** ~Faktor 2–3 schnellerer Render, deutlich weniger Heap-Druck.

### Hebel C — Pre-rendered `byte[]`-Cache pro `ListType`

**Nur einsetzen, wenn A + B nicht ausreichen.**

**Idee:** Die fertig serialisierte XML-Antwort der Collection-PROPFIND wird als
`byte[]` neben der `List<NumberBlock>` im `_numberCache` gehalten. Pro Request
reduziert sich der Pfad auf:

```
Auth → ListType bestimmen → byte[] in Output schreiben
```

**Voraussetzung:** Die Antwort muss user-unabhängig sein. Aktuell stehen in
HREFs absolute Pfade `/phoneblock/contacts/addresses/{user}/{blockId}`.

**Optionen:**

- HREFs relativ machen (RFC 4918 erlaubt). Der Kommentar in
  `Resource.java:264–271` deutet an, dass das schon mal probiert wurde, aber
  wegen FritzBox-DELETE-Verhalten zurückgenommen wurde. Müsste neu evaluiert
  werden.
- Alternativ: HREFs mit Platzhalter (`{{user}}`) in den `byte[]`-Stream
  einbauen, beim Senden in zwei Hälften + UTF-8-bytes-of-username schreiben.
  Komplexer, aber nicht user-abhängig im Cache.

**Wirkung:** Render-CPU geht für Common-List-User auf praktisch null. Macht
Hebel B obsolet.

**Aufwand:** Größer als A + B zusammen.

### Hebel D — FritzBox-Auth-Loop (optional)

**Nicht in dieser Iteration.** Untersuchen, ob:

- Der `LoginFilter`-Pfad bei 401-Antworten DB-Hits macht (sollte nicht, aber
  verifizieren).
- Apache `mod_auth_basic` mit `mod_authn_socache` 401-Antworten direkt
  liefern könnte, ohne Tomcat zu erreichen.

Da PhoneBlock-Auth tokenbasiert ist, nicht trivial. Erst angehen, wenn A + B
ausgeschöpft sind.

## Reihenfolge / Entscheidungspunkte

1. **Hebel A umsetzen** — überschaubarer Eingriff, sofort messbar.
   - Vorher: Header-Logging temporär aktivieren, prüfen ob iOS/macOS/DAVx5
     tatsächlich `If-None-Match` mitschicken (sollte bei allen drei der Fall
     sein, aber verifizieren).
   - Erwartete Last-Reduktion: ~25% der CardDAV-CPU-Zeit (alle 207-Renders der
     Apple/Android-Welt).

2. **Wirkung messen** — CPU-Last vor/nach. Logs auf 304-Anteil prüfen.

3. **Entscheidungspunkt:** Reicht Hebel A?
   - Wenn ja: B/C zurückstellen.
   - Wenn nein: Hebel B (StAX) als sicherer nächster Schritt. Wirkt auf alle
     verbleibenden 207-Renders inkl. FritzBox.

4. **Hebel C** nur, wenn auch nach A + B noch CPU-Last auf der CardDAV-Strecke
   dominant ist — dann radikalste Variante.

## Nicht zum Plan gehörig (separate Themen)

- Auth-Filter-Pfad-Optimierung (Hebel D).
- `sync-collection`-Implementierung (RFC 6578).
- Cache-TTL-Tuning (durch Datenlage als unwirksam ausgeschlossen).
- Antwort-Komprimierung via `gzip` — kostet zusätzliche CPU, spart Bandbreite,
  nicht hilfreich gegen das eigentliche Problem.
