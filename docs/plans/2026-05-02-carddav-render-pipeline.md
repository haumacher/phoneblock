# CardDAV-Render-Pipeline — Implementierungsplan (Hebel E + B)

## Zweck

Konkreter Implementierungsplan für die Hebel E (Lightweight Depth-0) und B
(StAX-Render) aus
[2026-05-02-carddav-performance.md](2026-05-02-carddav-performance.md).
Beide Hebel berühren denselben Render-Code; einzeln nacheinander gebaut
ergäbe das doppelte Umstellung. Plan beschreibt einen kohärenten Umbau der
Render-Pipeline, in dem E und B gemeinsam landen.

## Constraints

- **vCard-Inhalt unverändert** — Block-Titel, TEL-Einträge, CATEGORIES wie
  heute. Hebel A hat das schon eingestellt.
- **HREFs bleiben absolut** — der Kommentar in `Resource.java:264–271`
  beschreibt, dass relative HREFs einen FritzBox-DELETE-Bug auslösen.
  Reverse-Evaluierung gehört nicht in diesen Plan, sondern zu Hebel C.
- **ETag im stationären Zustand stabil**, einmalige Migrationswelle beim
  Deploy ist akzeptiert (wie bei Hebel A): die ETag-Komposition ändert sich
  bei der Umstellung von Wrapper-Hash auf Input-Hash, alle Clients machen
  einmal Vollsync. Danach ETag stabil über `CommonList.commonEtag` +
  Personal-Hash + `ListType.hashCode()`.
- **XML-Output äquivalent** — kanonisch identisch zum heutigen Output (das
  ist die testbare Bedingung); kosmetische Whitespace-Unterschiede sind
  tolerabel, sofern alle relevanten Clients den Output akzeptieren (Live-
  Smoke gegen iOS und FritzBox auf Staging).

## Schichtenschnitt

Damit die Render-Logik unit-testbar ohne Servlet-Container wird, müssen
die `Resource`-Methoden frei von `HttpServletRequest` sein. Heute lesen
sie genau eine Information aus dem Request — den authentifizierten User
für `current-user-principal`. Den Rest verarbeitet das Servlet vor dem
Aufruf.

**Lösung**: schmaler Render-Context als Wertobjekt, kein Servlet-Bezug.

```java
public record RenderContext(String authenticatedUser, String rootUrl) {}
```

Die Render-Schicht ist anschließend die `Resource`-Hierarchie selbst —
**kein separater `Renderer`-Typ**. Polymorphie auf `Resource` ist die
natürliche Modellierung des Konzepts „jede Resource-Sorte hat ihr
eigenes Property-Set".

Die einzige geteilte Hilfsfunktion ist die Multistatus-Envelope. Die
liegt als Mini-Helper in einem neuen `MultiStatusWriter` (statische
Methoden):

```java
public final class MultiStatusWriter {
    public static XMLStreamWriter open(OutputStream out) throws XMLStreamException;
    public static void close(XMLStreamWriter writer) throws XMLStreamException;
}
```

`open(...)` öffnet `<d:multistatus>` mit den nötigen Namespace-Bindings
(`DAV:`, `CARDDAV`, ggf. `calendarserver.org/ns/`); `close(...)` schließt
das Element und das Dokument. Ein einziger zentraler Punkt für
Namespace-Handling — dort wo sonst die meisten StAX-Stolpersteine
liegen.

Die Servlet-Schicht wird zum dünnen I/O-Adapter:

1. Auth + Resource-Resolution (`getResource(req)`).
2. Request-Body parsen → `List<QName> properties` bzw.
   `List<String> hrefs + List<QName> properties`. Beide Parser sind reine
   Funktionen über `InputStream` und liegen in einem neuen
   `CardDavRequestParser` (auch unit-testbar).
3. ETag bestimmen, `If-None-Match` über `EtagUtil` matchen → ggf. 304.
4. `RenderContext` aus `req` konstruieren (User + rootUrl), Envelope per
   `MultiStatusWriter.open(...)` öffnen, `resource.propfind(...)` /
   `resource.renderMultiGet(...)` aufrufen, schließen.
5. ETag-Header und Status setzen.

Das Servlet hat damit keine XML-Logik mehr, nur Routing + I/O.

## Code-Berührung

- `de.haumacher.phoneblock.carddav.CardDavServlet` — `doPropfind`,
  `doReport`, `marshalMultiStatus` (entfällt).
- `de.haumacher.phoneblock.carddav.resource.RenderContext` — neu, kleines
  Wertobjekt.
- `de.haumacher.phoneblock.carddav.resource.MultiStatusWriter` — neu,
  statischer Envelope-Helper.
- `de.haumacher.phoneblock.carddav.CardDavRequestParser` — neu, reines
  Input-Parsing (PROPFIND-Body, REPORT-Multiget-Body) ohne
  Servlet-Bezug.
- `de.haumacher.phoneblock.carddav.resource.Resource` — `propfind`,
  `fillProperty` auf `XMLStreamWriter`-Signatur und `RenderContext`
  umstellen.
- `RootResource`, `PrincipalResource`, `AddressBookResource`,
  `AddressResource` — Override-Methoden auf neue Write-API.
- `de.haumacher.phoneblock.carddav.resource.AddressBookCollectionResource`
  — neu, Lightweight-Variante für Depth-0 (siehe Phase 3).
- `AddressBookCache` — `lookupCollectionMeta` neu, `CommonList` bekommt
  `commonEtag`.
- `de.haumacher.phoneblock.util.DomUtil` — Output-Helper (`appendElement`,
  `appendText`, `appendTextElement`) entfallen für Resource-Hierarchie;
  Eingangs-Parsing (Request-Body) bleibt.

## Render-API

Resource-Methoden ohne Servlet-Abhängigkeit:

```java
public abstract void propfind(RenderContext ctx, Resource parent,
    XMLStreamWriter writer, List<QName> properties) throws XMLStreamException;

public int fillProperty(RenderContext ctx, XMLStreamWriter writer,
    QName property) throws XMLStreamException;
```

Multi-get ist ein Address-Book-Konzept und bekommt eine eigene Methode
auf `AddressBookResource` (statt im Servlet zu leben):

```java
public void renderMultiGet(RenderContext ctx, XMLStreamWriter writer,
    List<String> hrefs, List<QName> properties) throws XMLStreamException;
```

Sie iteriert intern, schlägt jeden href über `lookupAddress(...)` nach
und ruft pro Treffer `child.propfind(...)` mit denselben Bausteinen wie
der Depth-1-Pfad.

Achtung beim Namespace-Handling: `setPrefix` muss **vor** dem ersten
`writeStartElement` für jedes verwendete Namespace passieren, sonst
generiert der Writer ungewollt `xmlns:ns0`-artige Bindings.
`MultiStatusWriter.open(...)` ist die einzige Stelle, an der das
festgelegt wird.

## Phasen

### Phase 1 — Pipeline auf StAX umstellen, RenderContext einführen

Eine kohärente Änderung über den ganzen Resource-Baum. Doppel-API mit DOM
parallel ist unnötig — der Code ist überschaubar (Resource + 4
Subklassen), und ein sauberer Diff hilft mehr als eine Übergangsphase.

1. `RenderContext`-Record und `MultiStatusWriter`-Helper einführen.
2. `Resource.propfind` und `fillProperty` auf neue Signatur umstellen
   (`RenderContext` + `XMLStreamWriter`).
3. `RootResource`, `PrincipalResource`, `AddressBookResource`,
   `AddressResource`: alle `appendElement`-/`appendText`-Aufrufe durch
   `writer.writeStartElement` / `writer.writeCharacters` /
   `writer.writeEndElement` ersetzen. `LoginFilter.getAuthenticatedUser(req)`
   wird zu `ctx.authenticatedUser()`.
4. `AddressBookResource.renderMultiGet(...)` neu — verschiebt die Schleife
   aus `CardDavServlet.doReport` in die Resource.
5. `CardDavRequestParser` einziehen mit zwei Methoden:
   `parsePropfindBody(InputStream)` → `List<QName>` und
   `parseMultiGetBody(InputStream)` → `(List<String> hrefs, List<QName>)`.
6. `CardDavServlet.doPropfind` und `doReport`: DOM-Document-Aufbau durch
   `MultiStatusWriter.open(...)` + Resource-Aufruf + `close(...)` ersetzen,
   Body-Parsing auf den neuen Parser umstellen, `marshalMultiStatus`
   entfällt. Conditional-GET-Pfad (`If-None-Match` → 304) bleibt
   unverändert.
7. `AddressResource.fillProperty` für die `address-data`-Property
   schreibt `vCardContent()` direkt per `writer.writeCharacters` in den
   Stream — keine Zwischen-Stringifizierung in eine `Element`-Textnode.

**Test**: ein neuer Equivalence-Test rendert das Multistatus-Output für
einen Fixture-`AddressBook` einmal über den (kurz noch parallel
mitgehaltenen) DOM-Pfad und einmal über den neuen Stream-Pfad und
vergleicht XML-kanonisch — beide Outputs durch denselben DOM-Parser
jagen und per `isEqualNode()` vergleichen. Sobald grün, DOM-Pfad
löschen.

### Phase 2 — ETag inputbasiert

1. `AddressBookCache.CommonList` bekommt ein berechnetes Feld
   `commonEtag`: SHA-1 über die sortierten Block-IDs und Block-ETags der
   `_blocks`-Liste, einmal beim Konstruktor mitberechnet (gleiche Kosten
   wie heute beim ersten `getEtag`-Aufruf einer common-only-Resource,
   aber zentral und gecacht).
2. `AddressBookResource.getEtag()` wird zu:
   ```
   Hash(commonEtag, personalHash, listType.hashCode())
   ```
   wobei `personalHash` über die deduplizierten Personal-Singletons +
   `personalSettingsHash(personalizations, exclusions)` läuft. Keine
   Iteration mehr über `_addressById`.
3. Für den full-pipeline-Pfad (effektive Exclusions, ~98 User) bleibt der
   alte Hash-Pfad oder bekommt eine eigene Komposition mit demselben
   Endergebnis-Format (12-Hex-SHA-1-Prefix).

**Migrations-Effekt**: ETag-Komposition ändert sich beim Deploy einmal —
alle Clients sehen einen neuen Wert, machen Vollsync. Akzeptiert.

**Test**: für drei Beispiel-User (common-only, common+personal,
full-pipeline) Stabilität über mehrere `getEtag`-Aufrufe (= byteidentisch
bei identischen Inputs) und Sensitivität gegenüber: einer einzelnen
Personal-Nummer mehr/weniger, einer einzelnen Common-Block-Änderung,
geändertem `ListType`.

### Phase 3 — Lightweight Depth-0-Pfad als eigene Resource-Subklasse

Polymorphie statt Sonderfall: der Lightweight-Pfad bekommt eine eigene
Resource-Subklasse mit denselben Render-Methoden, aber ohne Children.

1. `CollectionMeta` als `record CollectionMeta(String etag, String
   displayName, String path)` einführen.
2. `AddressBookCollectionResource extends Resource` neu — hält eine
   `CollectionMeta`, `list()` ist leer, `getEtag()` liefert `meta.etag()`,
   `propfind(...)` und `fillProperty(...)` bedienen dieselben Properties
   wie `AddressBookResource` für die Collection-Eigen-Response
   (`getctag`, `getetag`, `displayname`, `resourcetype`,
   `current-user-principal`).
3. **Property-Logik geteilt**: das gemeinsame Property-Set für die
   Address-Book-Collection wird aus beiden Subklassen über einen
   protected Helper bedient (z. B. `fillCollectionProperty(...)` auf
   einer gemeinsamen Basis oder als statische Methode in
   `AddressBookProps`). Eine Implementierung, beide Pfade.
4. `AddressBookCache.lookupCollectionMeta(principal, settings)` liefert
   `CollectionMeta` ohne `AddressBookResource`-Konstruktion. Verzweigt
   intern wie der heutige `computeBlocks`-Pfad zwischen common-only /
   common+personal / full-pipeline; nutzt für die ersten beiden Pfade
   nur den `_numberCache` und die `getPersonalizations` /
   `getExcluded`-Reads. Für full-pipeline materialisiert sie wie heute
   (selten genug, ~98 User).
5. `CardDavServlet.doPropfind` interpretiert den `Depth`-Header **vor**
   der Resource-Resolution: bei Depth: 0 auf `/addresses/{user}/` ruft
   sie `lookupCollectionMeta` und konstruiert eine
   `AddressBookCollectionResource`; bei Depth: 1 (oder REPORT) wie
   heute `lookupAddressBook`. Ab da identischer Code-Pfad — beide
   Resources implementieren dasselbe Interface.
6. Die Address-Book-Children im Heavy-Pfad bleiben über
   `resource.list()` + `child.propfind(...)` wie heute.

**Test**: Goldfile-Vergleich der Depth-0-Antwort gegen den heutigen
Output für common-only, common+personal, full-pipeline. ETag aus
`CollectionMeta` byteidentisch zu `AddressBookResource.getEtag()` für
denselben User+Settings.

### Phase 4 — Cleanup

1. `marshalMultiStatus`, alle DOM-`appendXxx`-Aufrufe und ggf.
   ungenutzte `DomUtil`-Helper löschen.
2. `_userCache`-TTL und Konstanten in `AddressBookCache.Cache`
   inspizieren — nach Phase 3 trifft der iOS-Pfad den `_userCache` nicht
   mehr; TTL bleibt aber für Depth: 1 / REPORT relevant. Heutige Werte
   (5 / 15 min) wahrscheinlich ok, kurz prüfen.
3. Dokumentation in `2026-05-02-carddav-performance.md` aktualisieren
   (Hebel B und Hebel E von "geplant" auf "umgesetzt" verschieben,
   "Nächste Schritte" auf C und D verkürzen).

## Test-Plan

Die Schichten-Trennung ist die Voraussetzung für sinnvoll fokussierte
Tests. Drei Test-Ebenen, jede mit klarer Zuständigkeit:

### Ebene 1 — Unit-Tests auf der Resource-Hierarchie

Setzen direkt auf den Resource-Subklassen auf. Konstruieren eine Resource
mit Test-Daten, rufen `propfind(...)` mit einem `RenderContext` und
einem `XMLStreamWriter` auf einen `ByteArrayOutputStream`, vergleichen
das Ergebnis kanonisch (`isEqualNode()` nach DOM-Re-Parse) gegen ein
Goldfile.

Keine Servlet-Infrastruktur, kein `HttpServletRequest`-Stub, keine DB.
Was die Resource an externen Daten braucht (Block-Liste, Principal-
Name, rootUrl), wird im Test-Setup geliefert.

**`TestRootResourceRender`**:
- Property-Set `{resourcetype, displayname, current-user-principal}` →
  Goldfile.
- Einzeln pro Property: nur `resourcetype` angefragt → nur
  `<d:resourcetype>`-Block; nur `current-user-principal` mit
  authenticatedUser=null → `404 Not Found`-Status im Propstat.

**`TestPrincipalResourceRender`**:
- Standard-Property-Set für CardDAV-Discovery → Goldfile.
- Unbekannte Property → `404`-Status.

**`TestAddressBookResourceRender`** (Heavy-Pfad):
- Depth: 0 (Eigen-Response) mit `{getctag, getetag, displayname,
  resourcetype}` → Goldfile.
- Depth: 1 (Eigen + Children) mit drei NumberBlock-Fixtures → Goldfile;
  drei `<d:response>`-Elemente für Children.
- `renderMultiGet` mit gemischten hrefs (existent + nicht existent) →
  Goldfile mit 200 / 404-Statuszeilen.

**`TestAddressResourceRender`**:
- vCard-Inhalt für Single-Number-Block, Multi-Number-Block,
  Wildcard-Block — Goldfiles.
- ETag stabil über wiederholte Aufrufe.

**`TestAddressBookCollectionResourceRender`** (Lightweight-Pfad):
- Depth: 0 mit identischem Property-Set wie `AddressBookResource`,
  identische `CollectionMeta` → kanonisch gleiches XML wie
  `TestAddressBookResourceRender` Depth: 0.
- `list()` ist leer (Smoke).

**Gemeinsamer Goldfile-Helper**: `resources/de/.../carddav/golden/*.xml`,
Lese-Vergleich-Utility in einer `RenderTestSupport`-Klasse.

### Ebene 2 — Unit-Tests auf den reinen Funktionen

**`TestCardDavRequestParser`**:
- `parsePropfindBody`: Real-World-Beispiele aus iOS, FritzBox, DAVx5,
  PeopleSync → erwartete `List<QName>`. Beispiel-Bodies als
  Test-Resources einchecken.
- `parsePropfindBody` mit kaputtem XML → klare Exception.
- `parseMultiGetBody`: hrefs + property-Liste extrahiert, Reihenfolge
  erhalten.

**`TestEtagUtil`** (existiert schon nach Hebel A — beibehalten).

**`TestCommonListEtag`** (Phase 2):
- Stabilität: gleiche Block-Liste → byteidentischer `commonEtag` über
  N Aufrufe.
- Sensitivität: ein Block hinzu/weg → anderer `commonEtag`; eine Nummer
  in einem Block geändert → anderer `commonEtag`; `ListType` geändert
  → anderer Wert.
- Reihenfolge-Robustheit: Block-Liste vorher permutieren → identischer
  `commonEtag`.

**`TestAddressBookEtag`** (existiert nach Hebel A — erweitern für
Phase 3):
- ETag-Identität Lightweight vs. Heavy: für denselben User+Settings
  liefert `lookupCollectionMeta(...).etag()` denselben Wert wie
  `lookupAddressBook(...).getEtag()` — über alle drei Pfade
  (common-only, common+personal, full-pipeline).
- ETag-Sensitivität: Personal-Add/Remove ändert ETag; Common-Mutation
  ändert ETag; Settings-Change ändert ETag.

### Ebene 3 — Servlet-Integration (schmal)

Hand-gestrickte Stubs für `HttpServletRequest` / `HttpServletResponse`
(getHeader / getInputStream / getOutputStream / setStatus / setHeader),
keine Mockito, kein Servlet-Container. Decken nur Glue-Logik ab —
**nicht** den XML-Inhalt (das machen Ebene 1).

**`TestCardDavServlet`**:
- Auth fehlt → 401-Challenge.
- Auth passt + PROPFIND Depth: 0 → 207, ETag-Header gesetzt,
  `MultiStatusWriter`-Pfad aufgerufen (Smoke: irgendein gültiges
  Multistatus-XML im Body).
- `If-None-Match` matcht aktuellen ETag → 304, kein Body.
- `If-None-Match` mit `*` und vorhandenem ETag → 304.
- PROPFIND Depth: 0 auf Address-Book → `AddressBookCollectionResource`
  wird konstruiert (über Cache-Stub verifizierbar); Depth: 1 →
  `AddressBookResource`.
- METHOD-Routing: OPTIONS, PROPFIND, REPORT, GET, PUT, DELETE.

### Ebene 4 — Live-Smoke auf Staging (vor Merge)

Manuell, nicht automatisiert:

- **iOS-Sync** läuft: ctag wird akzeptiert, kein leeres Adressbuch,
  Übergang von altem zu neuem ETag löst genau einen Vollsync aus.
- **FritzBox-Sync** läuft: Full-Sync gibt korrekte Liste, DELETE-
  Verhalten unverändert (ein Test-Block per Web-UI entfernen, FritzBox
  übernimmt das).
- **PeopleSync** liefert weiterhin 304 bei unverändertem ETag (das ist
  der einzige Client mit echtem `If-None-Match`-Pfad).

### Was Tests nicht abdecken

- Performance der `lookupCollectionMeta`-Pfads — die wird über die
  Live-Beobachtung nach Deploy gemessen (`Address book computed for: …`
  -Logs sollten für den iOS-Pfad auf null fallen).
- StAX-Whitespace-Layout — kanonischer Vergleich toleriert das per
  Design; Live-Smoke fängt, falls ein Client doch pingelig ist.

## Risiken

- **StAX-Namespace-Handling**: `setPrefix`-Reihenfolge,
  `writeNamespace`-Aufrufe nur einmal pro Element. Ein einziger
  zentraler Helper für die Multistatus-Envelope reduziert die
  Fehlerquellen auf eine Stelle.
- **XML-Declaration / Whitespace**: heutiger `LSSerializer` produziert
  ein bestimmtes Layout, StAX ein anderes. Spec-konform sind beide; in
  der Praxis ist FritzBox-CardDAV historisch pingelig. Goldfile-Test
  fängt das, Live-Smoke verifiziert.
- **Migrations-ETag-Welle**: einmaliger Vollsync aller Clients beim
  Deploy. Deployment-Fenster wählen wie bei Hebel A
  (Wochenende / Abend), nicht parallel zur 22:00-Release-Welle.

## Out of Scope

- **Hebel C** (pre-rendered `byte[]`-Cache pro `ListType`) — eigener
  Plan; setzt auf der hier eingezogenen Stream-API auf, also keine
  Doppelarbeit, aber HREF-Strategie und Cache-Layout sind eigene
  Entscheidungen.
- **Hebel D** (FritzBox-Auth-Loop) — unabhängiger Pfad,
  Reverse-Proxy-Thema.
- **`sync-collection`-Implementierung** — separates Thema, würde DB-
  Versionierung benötigen.

## Reihenfolge der Commits

Ein Feature-Branch (dieser), ein PR, Commits in der Reihenfolge der
Phasen. Phase 1 ist groß genug, dass sie ggf. in zwei Commits
zerlegbar ist (Resource-Hierarchie umstellen / Servlet umstellen +
DOM-Pfad löschen). Phasen 2, 3, 4 jeweils eigener Commit.
