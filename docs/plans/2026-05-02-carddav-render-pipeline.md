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

## Code-Berührung

- `de.haumacher.phoneblock.carddav.CardDavServlet` — `doPropfind`,
  `doReport`, `marshalMultiStatus` (entfällt).
- `de.haumacher.phoneblock.carddav.resource.Resource` — `propfind`,
  `fillProperty`, `quote` (umbenannt nach `EtagUtil` schon erledigt).
- `RootResource`, `PrincipalResource`, `AddressBookResource`,
  `AddressResource` — Override-Methoden auf neue Write-API.
- `AddressBookCache` — `lookupCollectionMeta` neu, `CommonList` bekommt
  `commonEtag`.
- `de.haumacher.phoneblock.util.DomUtil` — Output-Helper (`appendElement`,
  `appendText`, `appendTextElement`) entfallen für Resource-Hierarchie;
  Eingangs-Parsing (Request-Body) bleibt.

## Render-API

Neue Signatur auf `Resource`:

```java
public abstract void propfind(HttpServletRequest req, Resource parent,
    XMLStreamWriter writer, List<QName> properties) throws XMLStreamException;

public int fillProperty(HttpServletRequest req, XMLStreamWriter writer,
    QName property) throws XMLStreamException;
```

Multistatus-Envelope öffnet/schließt `CardDavServlet`:

```java
XMLOutputFactory factory = XMLOutputFactory.newInstance();
XMLStreamWriter writer = factory.createXMLStreamWriter(
    resp.getOutputStream(), "UTF-8");
writer.writeStartDocument("UTF-8", "1.0");
writer.setPrefix("d", DavSchema.DAV_NS);
writer.setPrefix(CardDavSchema.CARDDAV_PREFIX, CardDavSchema.CARDDAV_NS);
writer.writeStartElement(DavSchema.DAV_NS, "multistatus");
writer.writeNamespace("d", DavSchema.DAV_NS);
writer.writeNamespace(CardDavSchema.CARDDAV_PREFIX, CardDavSchema.CARDDAV_NS);
// resource.propfind(req, null, writer, properties);
// for child : resource.list() child.propfind(req, resource, writer, properties);
writer.writeEndElement();
writer.writeEndDocument();
writer.flush();
```

Achtung beim Namespace-Handling: `setPrefix` muss **vor**
`writeStartElement` für jedes verwendete Namespace passieren, sonst
generiert der Writer ungewollt `xmlns:ns0`-artige Bindings. Ein
gemeinsamer Helper `openMultiStatus(writer)` / `closeMultiStatus(writer)`
fasst das zusammen.

## Phasen

### Phase 1 — Pipeline auf StAX umstellen

Eine kohärente Änderung über den ganzen Resource-Baum. Doppel-API mit DOM
parallel ist unnötig — der Code ist überschaubar (Resource + 4
Subklassen), und ein sauberer Diff hilft mehr als eine Übergangsphase.

1. `Resource.propfind` und `fillProperty` auf `XMLStreamWriter`-Signatur
   umstellen. `quote(...)` ist schon in `EtagUtil` ausgelagert.
2. `RootResource`, `PrincipalResource`, `AddressBookResource`,
   `AddressResource`: alle `appendElement`-/`appendText`-Aufrufe durch
   `writer.writeStartElement` / `writer.writeCharacters` /
   `writer.writeEndElement` ersetzen.
3. `CardDavServlet.doPropfind` und `doReport`: DOM-Document-Aufbau durch
   Stream-Envelope ersetzen, `marshalMultiStatus` entfällt. Conditional-
   GET-Pfad (`If-None-Match` → 304) bleibt unverändert.
4. `AddressResource.fillProperty` für die `address-data`-Property
   schreibt `vCardContent()` direkt per `writer.writeCharacters` in den
   Stream — keine Zwischen-Stringifizierung in eine `Element`-Textnode.

**Test**: ein neuer Equivalence-Test rendert das Multistatus-Output für
einen Fixture-`AddressBook` einmal über den (kurz noch parallel
mitgehaltenen) DOM-Pfad und einmal über den neuen Stream-Pfad und
vergleicht XML-kanonisch (z. B. `Canonicalizer` aus `xmlsec`, oder
einfacher: beide Outputs durch denselben DOM-Parser jagen und per
`isEqualNode()` vergleichen). Sobald grün, DOM-Pfad löschen.

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

### Phase 3 — Lightweight Depth-0-Pfad

1. `AddressBookCache.lookupCollectionMeta(principal, settings)` liefert
   `record CollectionMeta(String etag, String displayName, String path)`
   ohne `AddressBookResource`-Konstruktion. Verzweigt intern wie der
   heutige `computeBlocks`-Pfad zwischen common-only / common+personal /
   full-pipeline; nutzt für die ersten beiden Pfade nur den
   `_numberCache` und die `getPersonalizations` / `getExcluded`-Reads.
   Für full-pipeline materialisiert sie wie heute (selten genug, ~98
   User).
2. `CardDavServlet.doPropfind` erkennt **Depth: 0** + Pfad-Match auf
   `/addresses/{user}/`, ruft `lookupCollectionMeta`, ruft Helper
   `writeAddressBookCollectionProps(writer, req, meta, properties)`. Kein
   `getResource(req)`, kein `lookupAddressBook`.
3. `writeAddressBookCollectionProps` wird **auch** vom Heavy-Pfad
   (`AddressBookResource.propfind` für Depth: 1) für die Collection-
   Eigen-Response verwendet. Eine Implementierung, beide Pfade.
4. Die Address-Book-Children im Heavy-Pfad bleiben über
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

## Tests im Detail

- **XML-Equivalenz pro Resource × Property-Set** (Phase 1): konstruierter
  Fixture-User, Liste der relevanten Properties, kanonischer XML-
  Vergleich gegen einen Snapshot des heutigen Outputs.
- **ETag-Stabilität** (Phase 2): selber Input → byteidentischer ETag bei
  wiederholten Aufrufen; Mutation an Common, Personal oder Settings
  ändert ETag.
- **ETag-Identität Lightweight vs. Heavy** (Phase 3): für denselben User
  liefert `lookupCollectionMeta(...).etag` denselben Wert wie
  `lookupAddressBook(...).getEtag()`.
- **Goldfile Depth-0** (Phase 3): heutiger Depth-0-Output vs. neuer
  Lightweight-Output, kanonisch gleich.
- **Live-Smoke auf Staging** (vor Merge): iOS-Sync läuft (ctag wird
  akzeptiert, kein leeres Adressbuch); FritzBox-Sync läuft (Full-Sync
  gibt korrekte Liste, DELETE-Verhalten unverändert);
  Conditional-GET-Pfad (PeopleSync) liefert weiterhin 304 bei
  unverändertem ETag.

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
