# CardDAV-Performance-Optimierung — Plan

## Hintergrund

Der PhoneBlock-Server läuft in CPU-Kapazitätsgrenzen. Die Re-Registrierung der
Answerbots scheidet als Hauptursache aus (~1000 Geräte, langsam wachsend).
Verdacht liegt auf der CardDAV-Synchronisation der Fritz!Boxen und anderer
Clients, die das deutlich häufiger als einmal pro Tag tun.

Dieser Plan dokumentiert die Diagnose und priorisiert die Optimierungs-Hebel.

## Constraint: Semantik der Blockliste bleibt unverändert

Die Auswahl der Nummern auf der Blockliste hat reale Wirkung — eine Nummer auf
der Liste bedeutet eine Anrufsperre für tausende Nutzer. Diese Optimierung ist
**rein technisch**: sie betrifft ausschließlich, wie die unveränderte Liste in
CardDAV-Kontakte aufbereitet, identifiziert und ausgeliefert wird.

Was unverändert bleibt:

- Top-K-Auswahl (`maxEntries`, `minVotes`, Weight-Berechnung, Age-Decay).
- Wildcard-Erkennung (`markWildcards`, alle Schwellenwerte).
- Personalisations-/Exclusions-Mechanik.
- vCard TEL-Einträge und CATEGORIES pro Block.

Was sich kosmetisch ändert (siehe A2): der angezeigte Name pro Kontakt
(`FN:`-Feld) wird vereinheitlicht.

Insbesondere: keine Hysterese auf Top-K-Mitgliedschaft oder
Wildcard-Schwellen — beides würde die Listen-Semantik modifizieren.

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
- `analysis/NumberTree.java`, `NumberBlock.java` — Block-Bildung.
- DB-Pfad: `SpamReports.getReports()` → `SELECT ... FROM NUMBERS WHERE ACTIVE`
  (Vollscan).

## Listen-Varianten (`ListType`)

Aus `ListType.java` und `SettingsServlet.java:289–319`:

| Feld | Erlaubte Werte | Anzahl |
|---|---|---|
| `minVotes` | {2, 4, 10, 100} | 4 |
| `maxLength` | {1000, 2000, 3000, 4000, 5000, 6000} | 6 |
| `wildcards` | {true, false} | 2 |
| `nationalOnly` | {true, false} | 2 |
| `dialPrefix` | 210 ISO-Country-Codes (`trunk-prefixes.csv`) | ~210 |

**Theoretischer Cache-Schlüsselraum:** 4 × 6 × 2 × 2 × 210 = 20 160. Praktisch
dominieren wenige Defaults (Plattform ist deutsch, also DE/AT/CH); plausible
aktive Varianten 5–30. Der `_numberCache` räumt selten genutzte Einträge per
TTL weg.

**Beobachtungen für die Hebel A/C:**

- `dialPrefix` ist auch bei `nationalOnly=false` relevant: er bestimmt den
  Weight-Boost (`AddressBookCache.java:330`, `+100` für lokale Nummern) und
  damit die Top-K-Auswahl. Unterschiedliche `dialPrefix` → unterschiedliche
  Listen, auch ohne Filter.
- `maxLength` ist Cap nach dem Sortieren. Top-6000 cachen und für kleinere
  `maxLength` einen Prefix daraus nehmen ist nicht möglich, weil das
  Bucketing-Ergebnis pro Cap unterschiedlich ist.
- `wildcards=false` und `wildcards=true` produzieren völlig unterschiedliche
  Tree-Outputs.
- ETag muss `ListType.hashCode()` mitberücksichtigen — sonst sähen zwei User
  mit unterschiedlichen Settings, aber identischer NUMBERS-Tabelle, denselben
  ETag.
- Hebel C: `byte[]`-Cache pro Variante mit ~30 aktiven ListTypes à 50–200 KB
  ergibt 1.5–6 MB RAM — vertretbar.

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
| iOS / DAVx5 / PeopleSync | < 4% | | < 4% |

**Antwortgrößen 207 (Mittel):**

| | PROPFIND | REPORT |
|---|---|---|
| FritzBox | 47 KB | 21 KB |
| iOS | **68 KB** | 20 KB |
| macOS | 169 KB | – |
| PeopleSync | 82 KB | 7 KB |
| DAVx5 | 51 KB | 7 KB |

**Polling-Intervall zwischen Sync-Sessions (p50):**

| Client | p50 |
|---|---|
| iOS | **18 min** |
| macOS | 52 min |
| PeopleSync | 3.8 h |
| DAVx5 | 3.5 h |
| FritzBox | 21.6 h |

iOS pollt aggressiv, FritzBox tagesweise. Conditional-GET ist bei
PeopleSync verifiziert (vom Maintainer getestet); iOS, macOS, DAVx5 schicken
ETags ebenfalls (Annahme — die Verifizierung im laufenden Betrieb erfolgt
implizit über die 304-Quote nach Deployment).

## Befunde im Code

### Hot Spot 1 — Conditional GET wird ignoriert

ETags werden generiert (`AddressBookResource.java:115`,
`AddressResource.java:68`) und in Antworten eingetragen, aber **`If-None-Match`
wird nirgends gegen den Request-Header geprüft**. Suche nach `If-None-Match` im
Servlet liefert 0 Treffer.

### Hot Spot 2 — Block-IDs sind nicht stabil

`NumberBlock.getBlockId()` liefert `_numbers.get(0)` — die kleinste Nummer im
Block (NumberBlock.java:115). Die Block-Bildung in `NumberTree.createNumberBlocks`
ist **greedy** (4-Präfix + 9er-Cap) und ordnet Nummern im Kontext ihrer
Nachbarn zu. Dadurch ist die Block-ID einer Nummer **nicht ihre eigene
Eigenschaft**, sondern hängt von der Top-K-Nachbarschaft ab.

**Empirisch gemessen** (Top-1000 Blockliste, 14.500 Nummern): bei einem
simulierten Vote-Wackler mit k Nummern Austausch zwischen Top-K und Reserve:

| k | Heute: ID-Wechsel | Prefix-Bucketing: ID-Wechsel |
|---:|---:|---:|
| 1 | 12.5% | 1.2% |
| 5 | 58.6% | 1.8% |
| 10 | **77.9%** | 1.5% |
| 20 | 92.4% | 4.2% |
| 50 | >100% | 8.5% |

Bedeutet: **10 Vote-Wackler kippen heute 78% aller Block-URLs**, beim
Prefix-Bucketing 1.5%. Damit ist Conditional-GET heute praktisch wirkungslos —
selbst wenn der Servlet If-None-Match auswerten würde, würde der ETag bei
typischer Voting-Aktivität ständig kippen.

### Hot Spot 3 — Render pro Request, auch bei Cache-Hit

Bei jedem PROPFIND/REPORT:

- DOM-Tree mit 10k+ `<response>`-Elementen aufbauen
  (`CardDavServlet.java:197–209`).
- Pro Child eine vCard via `StringBuilder` neu generieren
  (`AddressResource.java:92`) — wird nicht gecacht.
- Zwei Pässe: erst DOM-Tree komplett bauen, dann `LSSerializer.write()` darüber
  (`CardDavServlet.java:218–223`).

### Hot Spot 4 — Auth-Loop bei FritzBox

Jeder FritzBox-CardDAV-Request kommt zweimal an: erst 401-Challenge, dann mit
`Authorization`-Header. Verdoppelt die FritzBox-Last.

### Verworfen — Common-Number-Cache-TTL hochziehen

Bei 8.6 RPS Gesamtlast wird der `_numberCache` (TTL 5 min) ohnehin permanent
warm gehalten. Cache-Misses sind statistisch selten und nicht der dominante
Cost.

### Verworfen — sync-collection (RFC 6578)

`sync-token` ist explizit als unsupported markiert (`Resource.java:55`).
Implementierung wäre aufwendig (DB-Schema für Versionierung der NUMBERS-Tabelle
nötig). Lohnt sich nicht, solange Hebel A noch offen ist.

### Verworfen — Hysterese auf Top-K oder Wildcard-Schwellen

Würde die Listen-Semantik ändern (siehe Constraint-Abschnitt oben). Stabilität
muss aus der technischen Aufbereitung kommen, nicht aus modifizierten
Schwellwerten.

## Optimierungs-Hebel

### Hebel A — Stabile Block-Aufteilung + Conditional GET

**Wirkt auf:** iOS, macOS, DAVx5, PeopleSync (~25% aller CardDAV-Requests).
FritzBox profitiert nicht.

Hebel A ist mehrstufig, weil Conditional-GET nur dann nicht-trivial wirkt, wenn
die Block-IDs unter typischer Voting-Aktivität stabil bleiben (siehe Hot Spot 2
oben).

#### A1 — Deterministisches Präfix-Bucketing

Block-Bildung neu auf einen Algorithmus umstellen, der Nummern unabhängig von
ihrer Nachbarschaft auf Buckets abbildet:

```
1. Top-K-Auswahl wie heute (deterministisch nach Weight + Nummer).
   Wildcard-Marker aus markWildcards() bleiben Teil der Liste.
2. Initial: jede Nummer (oder Wildcard-Marker) wird ihrem 4-stelligen
   Präfix-Bucket zugeordnet.
3. Solange irgendein Bucket > 9 Mitglieder enthält:
   - Spalte diesen Bucket in (n+1)-Präfix-Sub-Buckets auf.
   - Wiederhole, bis alle Buckets ≤ 9 Mitglieder haben.
4. Block-ID = der konkrete Präfix-String des Buckets ("030", "0301",
   "+49152187", …).
5. Block-Title = "prefix (first..last)" wie heute.
```

**Eigenschaften:**

- Lokal stabil: Eine Nummer landet immer im flachsten Präfix-Bucket, dessen
  Population ≤ 9 ist. Ändert sich was bei `+490900xxx`, sind die `+49301xxx`-Buckets
  unbeeinflusst.
- ID = Präfix-String, menschenlesbar, URL-tauglich.
- Wildcards aus `markWildcards()` werden wie konkrete Nummern behandelt — der
  Marker `+491521*` landet im `+49152`-Bucket bzw. tiefer, je nach
  Nachbar-Population.

**Test:** Bei Konstruktion mit identischem Top-K-Input identische Block-IDs.
Bei Hinzufügen einer Nummer in einen "ruhigen" Bereich: nur der eine Bucket
ändert sich, alle anderen Block-IDs bleiben unverändert.

#### A2 — Stabile, content-basierte ETags und neuer Title

**Block-IDs:**

- `NumberBlock.getBlockId()` = der Präfix-String aus A1.

**Block-Titel** vereinheitlicht und immer mit `SPAM:`-Präfix versehen
(`AnswerBot.SPAM_MARKER` heute schon im FN-Feld der vCard, aber an den Title
vorgehängt — wir konsolidieren das in den Title selbst):

- size > 1 → `SPAM: <bucket-prefix>...` (z. B. `SPAM: +491521...`).
- size = 1 → `SPAM: <number>` (z. B. `SPAM: +491521098765`).

`AddressResource.vCardContent()` schreibt das Title-Feld dann ohne separat
vorangestellten `SPAM_MARKER`. Macht in Anrufer-Anzeigen klar, was zusammengehört.

**ETags:**

- `AddressResource.getEtag()` = Hash über Title + sortierte Nummern
  (deterministisch, kollisionssicher → SHA-1 truncated, ~12 Hex-Zeichen).
- `AddressBookResource.getEtag()` = Hash über sortierten konkatenierten
  Block-ID- und Block-ETag-Strom. Heute `Sum(hashCode)` — kollisionsanfällig.
- Beide ETags müssen `ListType.hashCode()` (siehe Listen-Varianten-Abschnitt)
  mitmischen — sonst sehen zwei User mit identischer NUMBERS-Tabelle, aber
  unterschiedlichen Settings denselben ETag. Personalisations-Hash kommt für
  betroffene User in A4 dazu.

#### A3 — `If-None-Match` im Servlet auswerten

In `CardDavServlet.doPropfind()` und `doReport()` für Collection-Requests:

1. Ressource resolven (Cache-Lookup, ggf. Compute).
2. Collection-ETag bestimmen.
3. `If-None-Match`-Header parsen (RFC 7232, kommagetrennte Liste, `*` als
   Wildcard berücksichtigen).
4. Bei Match: `setStatus(304)`, ETag-Header zurücksenden, kein Body.
5. Sonst: bisheriger Pfad.

REPORT-multiget: pro adressierter Resource individuelles ETag-Match (spart die
vCard-Generierung pro Eintrag).

Achtung: Auch der Cache-Lookup-Pfad muss vor dem 304 laufen, weil ohne
materialisierten Block-Set kein ETag bestimmbar ist. Bei warmem Cache
(praktisch immer der Fall, siehe Datenlage) ist das billig — der gesparte Teil
ist der Render.

#### A4 — Personalisierte User: Common-Cache + Personal-Buckets

**Heute:** Sobald ein User Personalizations *oder* Exclusions hat, wird in
`AddressBookCache.loadNumbers()` der gesamte Pfad neu gerechnet — voller
NUMBERS-Scan, neuer NumberTree, Wildcards, Top-K — und der `_numberCache` wird
für diesen User umgangen. Personalizations werden mit Weight `+10.000.000`,
Exclusions mit `−1.000.000` in den Tree eingespritzt.

**Verteilung in der DB (Stand 2026-05-01, ohne Aktivitäts-Filter):**

| Kategorie | User | Anteil | Pfad in der neuen Lösung |
|---|---:|---:|---|
| keine Personalisierung | 21 012 | 73.6% | Common-Cache pur |
| nur Personalizations | 7 039 | 24.7% | Common-Cache + Personal-Buckets |
| Exclusions, alle aktuell ineffektiv | ~393 | 1.4% | wie Personalization-only |
| Exclusions, ≥ 1 effektiv | ~98 | 0.3% | volle Pipeline (wie heute) |

Personal-Listen sind klein: 68% der personalisierten User haben 1–5 Einträge,
Median ~3, nur 251 User haben >100.

Die Aufteilung der Exclusion-User wurde gegen die aktuelle NUMBERS-Tabelle
gemessen (`PERSONALIZATION` ⨝ `NUMBERS WHERE ACTIVE AND VOTES >= 4`). 80% der
Exclusions zeigen auf Nummern, die heute gar nicht in der Common-Liste stehen —
typisch sind Reverts früherer Falschmeldungen, die ohne den eigenen Vote nie
über `minVotes` gekommen sind.

**Konsequenz:** ~99.7% aller User-Sessions lassen sich aus dem Common-Cache
heraus bedienen. Nur ~0.3% (effektive Exclusions) brauchen die volle Pipeline,
das ist kostenneutral zur heutigen Lage.

**Kein zweites Adressbuch:** Der User richtet seine Box mit *einem* CardDAV-
Endpoint ein. Common- und Personal-Einträge erscheinen in *derselben*
Collection — der Server rendert sie aus zwei Quellen, der Client sieht eine
einheitliche Liste.

**Common-Cache erweitern:**

Beim Aufbau des `_numberCache` zusätzlich zu `List<NumberBlock>` zwei
Lookup-Strukturen ablegen:

- `Set<String> commonNumbers` — alle konkreten Nummern in den Common-Buckets.
- `List<String> commonWildcards` — alle Wildcard-Präfixe, sortiert für
  schnellen Präfix-Match.

Beide entstehen ohnehin im selben Pfad — ihr Aufbau ist im Cache-Refresh
billig.

**Personal-Buckets bauen (für die 7 039 User):**

```
für jede personalNumber aus Personalizations:
    falls commonNumbers.contains(personalNumber):    // schon konkret drin
        überspringen
    falls matchesAnyWildcard(commonWildcards, personalNumber):  // unter Wildcard abgedeckt
        überspringen
    sonst:
        als Singleton-Bucket der Collection beifügen
```

- Block-ID = die Nummer selbst (perfekt stabil — kann nie wackeln).
- Block-Title = `SPAM: <number>` (Format aus A2).
- vCard mit der einen TEL-Zeile.

Singletons (statt Personal-Nummern in Common-Bucketing einzumischen) gewählt,
damit Personal-Einträge die Common-Bucket-IDs nicht kippen können.

**ETag-Komposition für Common+Personal-User:**

```
ETag = Hash(Common-Collection-ETag, ListType-Hash,
            Hash(deduplicated-personalizations))
```

Personalizations ändern sich nur, wenn der User selbst eine Nummer meldet →
`flushUserCache()` invalidiert den User-Eintrag (Mechanismus existiert bereits
in `AddressResource.put()`).

**Subtilität — Common-Liste verändert sich:** Wenn die Common-Liste eine
bisher als Personal-Bucket serialisierte Nummer aufnimmt, fällt der
Personal-Bucket beim nächsten Sync weg. Semantisch korrekt, der Block bleibt
aktiv. Der User-ETag ändert sich dabei sowieso, weil die Common-ETag
mitgehasht ist. Kein Sonderhandling nötig.

**Exclusion-User: Effektivitätsprüfung pro Sync.** Eine Exclusion auf eine
Nummer, die gar nicht in der Common-Liste steht (weder konkret noch unter
einer Wildcard), ist semantisch ein No-Op. Solche User können wie
Personalization-only behandelt werden:

```
für jede excludedNumber aus Exclusions:
    falls commonNumbers.contains(excludedNumber): effektiv
    falls matchesAnyWildcard(commonWildcards, excludedNumber): effektiv
    sonst: ineffektiv

falls alle Exclusions ineffektiv:
    Pfad = Common-Cache + Personal-Buckets
sonst:
    Pfad = volle Pipeline wie heute
```

Der Check ist billig (n Lookups gegen die Strukturen, die A4 ohnehin baut)
und muss pro Sync gemacht werden, weil sich der Effektivitäts-Status mit der
Common-Liste ändern kann. Wenn eine bisher ineffektive Exclusion durch
ein Common-Liste-Update relevant wird, ändert sich Common-ETag → User-ETag →
Re-Sync → der nimmt dann die volle Pipeline. Korrekt automatisch.

Damit verbleiben nur die ~98 User mit effektiven Exclusions im individuellen
Pfad — die heutige Performance dort ist akzeptabel.

#### Tests

Vorhanden: `TestNumberTree`, JUnit Jupiter, kein Mockito, kein Spring-Test,
keine Servlet-Container-Test-Infrastruktur.

**Bucketing — `TestNumberTree` erweitern:**

1. Determinismus: gleicher Input liefert byte-identische Bucket-Liste.
2. Reihenfolge-Robustheit: permutierter Input liefert identische Buckets.
3. 9er-Limit auf konstruierten und realen Eingaben.
4. Vollständigkeit + Disjunktheit: `union(buckets) == top-K`, kein Eintrag
   doppelt.
5. Bucket-ID = Präfix: für jede Nummer im Bucket gilt
   `n.startsWith(bucket.id)` (Wildcard-Marker als Sonderfall).
6. Lokale Stabilität: eine Nummer im Top-K hinzufügen oder entfernen ändert
   ≤ 2 Bucket-IDs.

**Block-Effekt-Äquivalenz — neue Klasse `TestBlockingEquivalence`:**

7. Set der effektiv geblockten Nummern (alle TEL-Einträge plus
   Wildcard-Expansion gegen Eingabe-Universum) ist beim alten und neuen
   Algorithmus identisch. Fixture: heruntergeladene Blocklist (15 800
   Nummern). Matrix: ListType-Default + drei Variationen (Wildcards an/aus,
   nationalOnly an/aus, maxLength 1000/3000). Das ist der einzige Test, der
   "semantisch unverändert" wirklich beweist.

**ETags — neue Klasse `TestAddressBookEtag`:**

8. Block-ETag stabil bei identischen Mitgliedern, anders bei einer geänderten
   Nummer oder geändertem Title.
9. Collection-ETag stabil bei identischer Bucket-Menge, anders bei
   Bucket-Add/Remove und bei anderem `ListType.hashCode()`.

**Personal-Layer + Dedup — neue Klasse `TestPersonalLayer`:**

10. Personalization, die in Common konkret existiert → wird gefiltert.
11. Personalization unter Common-Wildcard → wird gefiltert.
12. Personalization, die nicht in Common ist → erscheint als
    Singleton-Bucket mit ID = Nummer.

**Exclusion-Effektivitätsprüfung — in `TestPersonalLayer`:**

13. Exclusion auf konkrete Common-Nummer → effektiv.
14. Exclusion außerhalb Common → ineffektiv.
15. Exclusion unter Common-Wildcard → effektiv.

**Conditional-GET — als pure Logik:**

`If-None-Match`-Auswertung in eine reine Methode (`HttpUtil.matchesEtag`)
extrahieren, dort testen — keine Servlet-Container-Infrastruktur nötig:

16. `If-None-Match: "abc"` matcht `etag = "abc"` → true.
17. Liste `If-None-Match: "abc", "def"` matcht jeden enthaltenen ETag.
18. `If-None-Match: *` → true (wenn überhaupt ein ETag existiert).
19. Mismatch → false.
20. Quoting (RFC 7232: ETags müssen in Anführungszeichen stehen) korrekt
    behandelt.

#### Migration

- Block-ID-Schema ändert sich strukturell → einmalige Cache-Invalidierung bei
  allen Clients beim Deploy. Alle Clients machen einen Vollsync, danach
  Normalbetrieb.
- Deployment-Fenster wählen, in dem das Volumen verkraftbar ist.

#### Erwartete Wirkung

- 304-Anteil bei iOS/macOS/DAVx5/PeopleSync sollte hoch werden — wie hoch
  hängt davon ab, wie häufig die effektive Block-ID-Menge wirklich kippt
  (= Top-K-Mitgliedschaftswechsel + lokale Bucket-Splits an der 9er-Grenze).
- Realistisches Ziel: ~70–90% der Apple/Android-PROPFINDs werden 304.
- Reduktion der CardDAV-CPU-Last: geschätzt 20–25%.

### Hebel B — Render-Pfad: DOM → StAX

**Wirkt auf:** Alle verbleibenden 207-Antworten, vor allem die großen
iOS- (68 KB) und macOS-Antworten (169 KB), sowie alle FritzBox-Renders
(die nicht von Hebel A profitieren).

`XMLStreamWriter` (StAX) statt DOM + `LSSerializer`. Schreibt direkt in den
Response-OutputStream:

- Keine Zwischenrepräsentation.
- Ein Pass statt zwei.
- Keine MB-große DOM-Allocation → GC-Druck weg.

Konkret:

- `CardDavServlet.doPropfind()` / `doReport()` öffnen einen
  `XMLOutputFactory.newInstance().createXMLStreamWriter(resp.getOutputStream(), "utf-8")`.
- `Resource.propfind()` und `Resource.fillProperty()` werden auf eine neue
  Schreib-API umgestellt: statt `Element propElement` ein
  `XMLStreamWriter writer`.
- vCard-Inhalt: `writer.writeCharacters(vCardContent())` statt
  `appendText(container, vCardContent())`.

Aufwand: ~Tag. Touchiert die Resource-Hierarchie, aber das Datenmodell bleibt
unverändert.

Wirkung: ~Faktor 2–3 schnellerer Render, deutlich weniger Heap-Druck.

### Hebel C — Pre-rendered `byte[]`-Cache pro `ListType`

**Nur einsetzen, wenn A + B nicht ausreichen.**

Die fertig serialisierte XML-Antwort wird als `byte[]` neben der
`List<NumberBlock>` im `_numberCache` gehalten. Pro Request reduziert sich der
Pfad auf:

```
Auth → ListType bestimmen → byte[] in Output schreiben
```

Voraussetzung: Antwort muss user-unabhängig sein. Aktuell stehen in HREFs
absolute Pfade `/phoneblock/contacts/addresses/{user}/{blockId}`.

Optionen:

- HREFs relativ machen (RFC 4918 erlaubt). Der Kommentar in
  `Resource.java:264–271` deutet an, dass das schon mal probiert wurde, aber
  wegen FritzBox-DELETE-Verhalten zurückgenommen wurde — neu evaluieren.
- Alternativ: HREFs mit Platzhalter (`{{user}}`) im `byte[]`-Stream einbauen,
  beim Senden in zwei Hälften + UTF-8-bytes-of-username schreiben.

Wirkung: Render-CPU geht für Common-List-User auf praktisch null. Macht Hebel B
obsolet.

Speicher: ~30 aktive Listen-Varianten (siehe Listen-Varianten-Abschnitt) à
50–200 KB serialisiert ergibt 1.5–6 MB RAM für den `byte[]`-Cache — vertretbar.

### Hebel D — FritzBox-Auth-Loop (offen)

**Nicht in dieser Iteration.** Untersuchen, ob:

- Der `LoginFilter`-Pfad bei 401-Antworten DB-Hits macht (sollte nicht, aber
  verifizieren).
- Apache `mod_auth_basic` mit `mod_authn_socache` 401-Antworten direkt liefern
  könnte, ohne Tomcat zu erreichen.

PhoneBlock-Auth ist tokenbasiert, nicht trivial. Erst angehen, wenn A + B
ausgeschöpft sind.

## Reihenfolge / Entscheidungspunkte

1. **Hebel A umsetzen** (A1–A4) als zusammenhängender Wechsel — A1 allein bringt
   nichts, A2/A3 ohne A1 kippt der ETag zu oft, A4 ist die Erweiterung auf
   personalisierte User.
2. **Wirkung messen** — 304-Quote pro User-Agent aus Access-Logs, CPU-Last
   vor/nach.
3. **Entscheidung:** Reicht Hebel A?
   - Wenn ja: B/C zurückstellen.
   - Wenn nein: Hebel B (StAX) als nächster Schritt — wirkt auf alle
     verbleibenden 207-Renders inkl. FritzBox.
4. **Hebel C** nur, wenn auch nach A + B noch CPU-Last auf der CardDAV-Strecke
   dominant ist.
5. **Hebel D** unabhängig, falls FritzBox-Auth-Roundtrips ein nennenswerter
   Anteil der verbleibenden Last sind.

## Nicht zum Plan gehörig

- `sync-collection`-Implementierung (RFC 6578) — separates Thema.
- Antwort-Komprimierung via `gzip` — kostet zusätzliche CPU, spart Bandbreite.
- Cache-TTL-Tuning — durch Datenlage als unwirksam ausgeschlossen.
- Modifikation der Top-K- oder Wildcard-Schwellen — verstößt gegen den
  Semantik-Constraint.
