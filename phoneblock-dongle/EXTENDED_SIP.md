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

- [x] Neuer `sip_transport.{c,h}` mit `sip_transport_t {
      open, send, recv, close }` und Implementierung `udp` als
      ersten Aufrufer (Verhalten bit-genau identisch).
- [x] Empfangsseite vereinheitlichen: `sip_transport_recv` liefert
      bei UDP genau ein Datagramm pro Aufruf zurück, mit `timeout_ms`-
      Argument statt der bisherigen `SO_RCVTIMEO`/`select`-Mischung.
      TCP/TLS-Implementierungen können denselben Vertrag mit interner
      Reassembly erfüllen, ohne dass `sip_register.c` etwas davon
      mitbekommt.
- [x] `advertised_host()` / `advertised_port()` pro Transport mit
      korrektem Transport-Token (`UDP`/`TCP`/`TLS`) im `Via`.
      `sip_transport_via_token()` und `sip_transport_uri_param()`
      liefern den Token; Via-Zeilen in REGISTER und BYE
      (`sip_register.c`) lesen daraus. Lokaler Port bleibt
      `SIP_LOCAL_PORT=5061` (für UDP wie TCP-Bind), TLS-Default
      5061 fällt in Phase 2 zusammen.
- [x] Host-Tests in `firmware/test/` für den Frame-Parser:
      `sip_frame.{c,h}` als pure-C-Modul plus
      `test_sip_frame.c` mit 24 Checks (Single, Split-Reads,
      \r\n\r\n-Boundary-Split, mehrere Messages pro Read,
      gestreamter Body, fehlendes/kaputtes Content-Length,
      Overflow, Reset).
- [x] Gate: ESP32-Build grün (`phoneblock_dongle.bin` 1.07 MB,
      26 % Headroom im OTA-Slot). FB-Roundtrip auf realer
      Hardware steht aus (UDP-Pfad ist bit-genau identisch zum
      Vor-Refactor-Stand — Risiko niedrig).

### Phase 1 — TCP (~0,5 d)

- [x] `sip_transport_tcp` über `lwip` Sockets:
      `sip_transport.c` dispatched UDP/TCP über `kind`-Feld;
      `tcp_connect()`/`tcp_send_all()`/`tcp_recv()` mit
      Reassembly via `sip_frame_t`.
- [x] REGISTER persistent halten (kein Reconnect pro Refresh):
      Socket bleibt im `sip_transport_t` über alle
      `do_register()`-Aufrufe hinweg offen.
- [x] Reconnect + Re-REGISTER bei Verbindungsabbruch:
      `tcp_reconnect()` schließt+verbindet, setzt das einmalige
      `reconnected_flag`. `sip_task` pollt
      `sip_transport_consume_reconnect()` jeden Loop-Tick und
      feuert sofort einen frischen REGISTER (analog
      `s_reload_requested`-Pfad).
- [x] `Via: SIP/2.0/TCP …`: REGISTER- und BYE-Builder lesen
      `sip_transport_via_token()`. `;transport=tcp`-Parameter
      hängt an der BYE-R-URI für alle Nicht-UDP-Transports.
- [ ] Smoketest: lokaler `kamailio` in QEMU/Hostnetz erreichbar.

### Phase 2 — TLS (~1–2 d, der Brocken)

- [x] `esp-tls` einbinden: `tls_connect()`/`tls_send_all()`/
      `tls_recv()` in `sip_transport.c` setzen auf
      `esp_tls_conn_new_sync()` + `esp_tls_get_conn_sockfd()` für
      die select()-Schleife auf. Reassembly läuft transparent
      über denselben `sip_frame_t` wie TCP.
- [x] Server-Verifikation gegen ESP-IDF-Root-Bundle:
      `CONFIG_MBEDTLS_CERTIFICATE_BUNDLE=y` und
      `CONFIG_MBEDTLS_CERTIFICATE_BUNDLE_DEFAULT_FULL=y` waren
      schon in `sdkconfig.defaults` für die HTTPS-API aktiv. TLS
      für SIP zieht das nur mit; Binary wächst um 2 KB
      (1.119.424 → 1.121.456 Bytes), 26 % Headroom im OTA-Slot
      bleibt.
- [x] Default-Port 5061; SNI = `config_sip_host()`:
      `sip_transport_open()` wendet 5061 als TLS-Default an, wenn
      `config_sip_port() == 0`. SNI sendet `esp_tls_conn_new_sync()`
      automatisch über das Hostname-Argument.
- [x] Pinning bewusst nicht eingebaut — Kommentar in
      `tls_connect()` dokumentiert die CA-Rotation-Begründung.
- [x] Sichtbare Warnung: `stats_record_error("sip", "TLS active
      but RTP is still plaintext (SRTP arrives in Phase 5)")`
      einmalig beim SIP-Task-Start, wenn `config_sip_transport()
      == "tls"`.
- [ ] DNS-NAPTR/SRV bewusst **nicht jetzt** — Telekom verlangt es
      laut PROVIDERS.md:33, in lwip aber nicht trivial. Erst
      A-Record erzwingen, SRV als optionalen Folge-Punkt
      (siehe unten).

### Phase 3 — Auth-User getrennt von SIP-User (~0,5 d)

Heute dient `config_sip_user()` als R-URI-User, From-User,
Contact-User **und** Digest-Username. Trennen in:

- [x] **Identity** (R-URI, From, To, Contact) =
      `current_identity_user()` → `config_sip_user()`.
      `config_sip_internal_number()` bleibt rein informativ
      (Dashboard-Anzeige der vom Fritz!Box vergebenen Nebenstelle);
      die SIP-Header verwenden nach wie vor den Auth-User-Wert,
      den der Registrar kennt.
- [x] **Authentifizierung** (Digest `username=`) =
      `current_auth_user()` → `config_sip_auth_user()` mit
      Fallback auf `config_sip_user()`.
- [x] **Realm-Override**: `current_realm(challenge)` →
      `config_sip_realm()` wenn gesetzt, sonst der Realm aus dem
      Challenge. Deckt den 1&1-Fall (`realm=1und1.de`) ab, ohne
      bestehende Provider zu brechen.
- [x] Helfer eingeführt und alle direkten `config_sip_user()`-
      Aufrufe in `digest_response()`-Aufrufer, `build_register()`,
      `build_bye()` und `build_response()` (Contact-Header) über
      die drei Helfer geführt. Die verbleibenden `config_sip_user()`-
      Stellen (Logs, Empty-Config-Check beim SIP-Task-Start) sind
      bewusst nicht angefasst — sie geben Diagnose-Werte aus, kein
      Header-Inhalt.

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
