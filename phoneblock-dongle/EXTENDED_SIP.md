# EXTENDED_SIP.md — Extended SIP-Backend für Nicht-Fritz!Box-Provider

Plan zur Umsetzung des "Extended SIP-Backends", das den Dongle ohne
Fritz!Box-Gateway direkt bei einem VoIP-Anbieter anmeldet. Treiber ist
PROVIDERS.md (Top-5 deutsche Anbieter); Reihenfolge entspricht
PROGRESS.md → "Extended-SIP-Backend".

## Ausgangslage

- UI + NVS für `transport`, `auth_user`, `outbound`, `realm`, `srtp`
  sind komplett (`firmware/main/config.h:42–46`).
- `firmware/main/sip_register.c:1080–1100` warnt heute nur, dass diese
  Felder ignoriert werden — der ganze Stack ist UDP, Auth-User =
  SIP-User, Realm aus dem Challenge, kein SRTP, kein Outbound-Proxy.
- Reihenfolge nach Bedarf: **TCP → TLS → Auth-User-Trennung →
  Outbound-Proxy → SRTP**. TLS ist Telekom-Pflicht und damit der
  wichtigste Treiber, TCP fällt als billige Vorstufe ab.

## Was bewusst nicht im Scope ist

- **Keine** SIP-Stack-Bibliothek (PJSIP, sofia-sip) reinholen — der
  Flash-Footprint passt nicht in unser OTA-Slot-Layout (1280 KB), und
  der bisherige handgeschriebene Stack passt zur Architektur. Die
  Erweiterungen sind alle additiv, nicht ersetzend.
- **Kein** dynamisches Codec-Negotiation-Framework — PCMA bleibt fest,
  wegen Notruf-Pflicht (PROVIDERS.md:38).
- **Kein** ICE/STUN über das hinaus, was die UI bereits speichert. Die
  Top-5-Provider funktionieren ohne, der Dongle ist stationär hinter
  dem Heimrouter.

## Phasen

### Phase 0 — Refactor: Transport-Abstraktion (~0,5–1 d)

`sip_register.c` ist 1.257 Zeilen lang und kennt überall direkt
`sendto`/`recvfrom`. Bevor TCP/TLS/Outbound dazukommen, wird das
Transport-Handling in eine schmale Schicht gezogen, sonst zerfasert
die Datei vollends.

- [ ] Neuer `sip_transport.{c,h}` mit `sip_transport_t {
      open, send, recv, close }` und Implementierung `udp` als
      ersten Aufrufer (Verhalten bit-genau identisch).
- [ ] Empfangsseite vereinheitlichen: bei UDP bleibt eine
      Datagram-Grenze pro `recv`; das Interface bringt aber bereits
      einen Hook für stream-reassembly mit, damit Phase 1/2 nur die
      neue Implementierung brauchen.
- [ ] `advertised_host()` / `advertised_port()` pro Transport mit
      passenden Defaults (5060/5060/5061) und korrektem
      Transport-Token (`UDP`/`TCP`/`TLS`) im `Via`.
- [ ] Host-Tests in `firmware/test/` für den Frame-Parser
      (Reassembly, Split-Pakete, mehrere Responses in einem
      TCP-Read) — die Klasse Bug, die in QEMU sonst nur sporadisch
      auftritt.
- [ ] Gate: ESP32-Build grün, REGISTER + spam-Anruf gegen die
      Referenz-Fritz!Box läuft wie vor dem Refactor.

### Phase 1 — TCP (~0,5 d)

- [ ] `sip_transport_tcp` über `lwip` Sockets.
- [ ] REGISTER persistent halten (kein Reconnect pro Refresh).
- [ ] Reconnect + Re-REGISTER bei Verbindungsabbruch (analog
      Reload-Pfad in `sip_register.c`).
- [ ] `Via: SIP/2.0/TCP …`, R-URI-Parameter `;transport=tcp` für
      ausgehende In-Dialog-Requests (BYE).
- [ ] Smoketest: lokaler `kamailio` in QEMU/Hostnetz erreichbar.

### Phase 2 — TLS (~1–2 d, der Brocken)

- [ ] `esp-tls` einbinden (bereits Dependency in
      `firmware/main/CMakeLists.txt:8`).
- [ ] Server-Verifikation gegen ESP-IDF-Root-Bundle
      (`CONFIG_MBEDTLS_CERTIFICATE_BUNDLE=y`); Bundle-Größe und
      Flash-Layout prüfen — OTA-Slot ist 1280 KB, Reserve ist da.
- [ ] Default-Port 5061; SNI = `config_sip_host()`.
- [ ] Pinning bewusst **nicht** einbauen (CA-Rotation würde sonst
      ein Telekom-Update brauchen).
- [ ] Sichtbare Warnung über `stats_record_error`, dass RTP weiterhin
      Klartext ist, solange Phase 5 (SRTP) nicht steht.
- [ ] DNS-NAPTR/SRV bewusst **nicht jetzt** — Telekom verlangt es
      laut PROVIDERS.md:33, in lwip aber nicht trivial. Erst
      A-Record erzwingen, SRV als optionalen Folge-Punkt
      (siehe unten).

### Phase 3 — Auth-User getrennt von SIP-User (~0,5 d)

Heute dient `config_sip_user()` als R-URI-User, From-User,
Contact-User **und** Digest-Username. Trennen in:

- [ ] **Identity** (R-URI, From, To, Contact) =
      `config_sip_user()` bzw. `config_sip_internal_number()` falls
      Fritz!Box.
- [ ] **Authentifizierung** (Digest `username=`) =
      `config_sip_auth_user()` mit Fallback auf `config_sip_user()`.
- [ ] **Realm-Override**: wenn `config_sip_realm()` gesetzt, den
      verwenden statt des Challenge-Realms — der 1&1-Fall
      (`Realm=1und1.de`).
- [ ] Helfer `current_identity_user()` / `current_auth_user()` /
      `current_realm(challenge)` einführen und alle direkten
      `config_sip_user()`-Aufrufe in `digest_response()`
      (`sip_register.c:155`), `build_register()` (`:282`),
      `build_bye()` (`:697`), `build_response()` (`:566`) darüber
      routen.

### Phase 4 — Outbound-Proxy (~0,5 d)

- [ ] `resolve_registrar()` löst weiterhin den **Registrar**-
      Hostnamen für die R-URI auf.
- [ ] Neue Funktion `resolve_outbound()` löst
      `config_sip_outbound()` (Format `host[:port]`) auf und
      liefert die Ziel-`sockaddr`. Wenn leer → fällt auf den
      Registrar zurück.
- [ ] Sämtliche `sendto(c->sock, …, &c->registrar)`-Aufrufe nutzen
      stattdessen `&c->outbound_dest`. Symmetrisch für TCP/TLS-
      Connect-Ziel.
- [ ] `Route`-Header (Loose-Routing) **nicht** für die erste
      Version — sipgate/easybell/Telekom funktionieren ohne. Erst
      nachziehen, wenn ein Provider per `Record-Route` darauf
      besteht.

### Phase 5 — SRTP (~1–2 d, optional zunächst)

`mbedtls` kann SRTP-Crypto, aber kein Keying-Protokoll. Wir machen
**nur SDES** (`a=crypto:` in SDP), kein DTLS-SRTP.

- [ ] `rtp.c`: `srtp_session`-Variante, die jedes 160-Byte-PCMA-
      Paket vor dem Senden authenticated-encryptet
      (AES-CM-128 + HMAC-SHA1-80).
- [ ] Modus `off` — heute, klartext.
- [ ] Modus `optional` — `m=audio … RTP/AVP …` + `a=crypto`-
      Vorschlag. Wenn Gegenseite mitmacht, verschlüsseln.
- [ ] Modus `mandatory` — `m=audio … RTP/SAVP …`. Ohne SRTP
      kein Audio.
- [ ] Gate: **erst starten, wenn ein realer Telekom- oder
      DeutschlandLAN-Anschluss zum Testen verfügbar ist** — sonst
      entwickeln wir in den Wind.

## Tests / Validierung

- [ ] Host-Unit-Tests (`firmware/test/`): TCP-Frame-Reassembly,
      Realm-Override-Verhalten in Digest-Berechnung
      (Erweiterung `test_sip_parse`).
- [ ] QEMU: TCP gegen lokalen Kamailio (UDP/TCP/TLS-Profile).
- [ ] QEMU: TLS mit selbstsigniertem Cert + temporärem
      `CONFIG_*_INSECURE_SKIP_VERIFY` als Dev-Flag.
- [ ] Echte Hardware: sipgate-basic-Account (kostenlos, nomadisch,
      TLS-fähig) als billigster End-to-End-Test ohne weiteren
      Anbieter-Vertrag.
- [ ] Pro Provider aus PROVIDERS.md Kompatibilitätsmatrix:
      Registrierungs-Roundtrip + ein eingehender Spam-Anruf,
      Ergebnis in PROGRESS.md/PROVIDERS.md festhalten.

## Folge-Punkte (nicht im Hauptplan)

- DNS-SRV/NAPTR-Lookup für Telekom — separat, sobald die TLS-Phase
  steht und das echte Bedürfnis klar ist.
- `Route`-Header / Record-Route-Verarbeitung, falls ein realer
  Provider das verlangt.
- DTLS-SRTP statt SDES, falls ein Provider SDES nicht (mehr) anbietet.

## Cross-Referenzen

- [PROVIDERS.md](PROVIDERS.md) — Anbieter-Parameter und
  Kompatibilitätsmatrix.
- [PROGRESS.md](PROGRESS.md) → "Extended-SIP-Backend",
  "Generisches Provider-Setup (ohne Fritz!Box)".
- `firmware/main/config.h` — Persistierte UI-Felder.
- `firmware/main/sip_register.c` — heutiger UDP-only-Stack.
