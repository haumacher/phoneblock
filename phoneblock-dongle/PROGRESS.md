# PhoneBlock Dongle — Entwicklungsstand

Lebender Statusbericht: was steht, was fehlt, was wir unterwegs gelernt
haben. Übergreifende Zielsetzung und Architektur siehe [README.md](README.md),
Hardware-Entscheidungsmatrix [HARDWARE.md](HARDWARE.md), QEMU-Setup
[GETTING_STARTED.md](GETTING_STARTED.md), Testszenarien
[firmware/README.md](firmware/README.md).

## Abgeschlossen

### Projekt-Setup
- [x] Modulstruktur `phoneblock-dongle/` angelegt
- [x] ESP-IDF v5.3 Toolchain + QEMU-Setup dokumentiert
  (`GETTING_STARTED.md`), inkl. Gotcha WLAN-HF / OpenCores-Ethernet
- [x] Minimales ESP-IDF-Firmware-Projekt mit Kconfig-basierter
  Credential-Konfiguration, gitignored `sdkconfig.defaults.local`

### Hardware-Evaluierung
- [x] `HARDWARE.md` mit Modul-/Board-Entscheidungsmatrix (SoC-Familien,
  Modul-Suffixe D/U/E/UE, USB-UART-Wandler, Formfaktoren, Budget)
- [x] Beschaffungswahl: „ESP32-WROOM-32 CH340C TYPE-C" 10er-Pack
  (3,30 €/Stk) für die Entwicklung
- [x] EGBO-PICO-D4-USB-Dongle als Kandidat für spätere Pilot-Serie
  identifiziert (echter Stick-Formfaktor, 5,44 €/Stk im 10er)

### PhoneBlock-API-Integration
- [x] WLAN/Ethernet-Connect via `protocol_examples_common`
- [x] HTTPS-Abfrage `GET /api/num/{phone}?format=json` mit Bearer-Auth
  (`api.c/h`, aus `main.c` extrahiert)
- [x] JSON-Parse via cJSON → `votes` → SPAM/LEGITIMATE-Verdikt
- [x] Test- und Produktions-Endpunkte dokumentiert
  (`https://phoneblock.net/pb-test/api` vs. `…/phoneblock/api`)

### TCP-Dummy-Server (Port 5060)
- [x] Lokaler Server, der eine Nummer + `\n` entgegennimmt, gegen die
  API abfragt und mit `SPAM\n`/`LEGITIMATE\n`/`ERROR\n` antwortet —
  Vorstufe für das eigentliche SIP-Handling, blieb als Diagnose-Tool

### SIP-Stack — REGISTER (UDP :5061)
- [x] REGISTER mit Digest-Authentication (MD5, mit und ohne qop=auth)
- [x] Periodischer Refresh bei `Expires/2`
- [x] `select()`-basierter, interrupt-getriebener Empfangs-Loop
- [x] Volllogging aller SIP-Pakete (TX/RX) auf INFO-Level
- [x] NAT-Overrides via Kconfig (`CONFIG_SIP_CONTACT_HOST_OVERRIDE`,
  `CONFIG_SIP_CONTACT_PORT_OVERRIDE`) für QEMU-Betrieb
- [x] Gegen echte Fritz!Box verifiziert

### SIP-Response-Helper
- [x] `find_header`, `echo_header_line`, `echo_to_with_tag` für Routing-
  Header-Echo (Via/From/To/Call-ID/CSeq)
- [x] Generische `build_response` / `send_response` mit optionalem Body
  (SDP) + korrekter Content-Length
- [x] OPTIONS: `200 OK` + Allow-Header
- [x] Dispatcher in `handle_incoming` (Requests vs. Responses)

### SIP-Stack — INVITE / ACK / BYE / CANCEL (Kernfunktion)
- [x] INVITE → sofort `100 Trying`, um Fritz!Box-Retransmits zu stoppen
- [x] Caller-Nummer aus `From:`-URI extrahieren, E.164-Normalisierung
  (DE-zentriert: `+49…` / `0049…` / national → `+49…`)
- [x] Synchroner PhoneBlock-API-Call — 1–2 s, vertretbar im Budget
- [x] **Spam-Fall**: `200 OK` + Dummy-SDP → ACK → (Audio/BYE) → 200 OK
- [x] **Nicht-Spam-Fall**: `486 Busy Here` — Fritz!Box klingelt reguläre
  Nebenstellen weiter
- [x] `CANCEL`-Handling während INVITE-Phase (`200 OK` + `487`)
- [x] BYE empfangen und senden mit korrektem Dialog-Routing
  (Call-ID, Tags, CSeq, R-URI aus gespeichertem Dialog)
- [x] **Dialog-State** mit Call-ID + Tags als Dedupe-Schlüssel, ein
  aktiver Dialog gleichzeitig, Zweit-INVITE → 486
- [x] **Idempotentes UDP-Retransmit-Handling** — dieselbe Response
  wird pro Dialog-State wiederholt gesendet
- [x] **Early-Skip vor API-Abfrage**: interne Codes (`**622`, `*21#`)
  und Telefonbuch-Treffer (nicht-numerischer Display-Name) überspringen
  den HTTPS-Roundtrip

### SDP
- [x] Build: Minimal-SDP für `200 OK` mit PCMA/8000, eigener RTP-Port
- [x] Parse des INVITE-Body: `c=IN IP4 <ip>` + `m=audio <port>` → Dialog-
  RTP-Destination
- [x] Host-Override auch für SDP-`c=`-Zeile (gleicher QEMU-NAT-Override
  wie Via/Contact)

### RTP + Audio-Ansage
- [x] G.711 A-law Encoder (`audio.c/h`), pure C, host-testbar
- [x] RTP-Sender-Task (`rtp.c/h`): UDP-Socket, 160-Byte/20-ms-Frames,
  zufällige SSRC/Seq/Timestamp, vTaskDelayUntil-paced
- [x] Eingebettete Sprachansage via `EMBED_FILES` als Default —
  ElevenLabs-TTS → `ffmpeg -ar 8000 -ac 1 -f alaw` →
  `main/audio/announcement.alaw`
- [x] **User-Upload** (`announcement.{c,h}`): SPIFFS-Partition
  (704 KB, Page-Size 1 KB getunet) für eine eigene Ansage. POST
  `/api/announcement` streamt direkt in eine .tmp-Datei und
  committet per atomic rename — kein 240-KB-Heap-Buffer für den
  Upload. Fallback auf Embedded-Default, wenn SPIFFS leer.
  GET `/api/announcement` liefert die aktiven A-law-Bytes;
  POST `/api/announcement/reset` löscht den Custom-Upload.
- [x] **Browser-Seite** in `index.html`: File-Input akzeptiert
  WAV/MP3/OGG, Konvertierung via Web Audio API
  (`decodeAudioData` → `OfflineAudioContext` 8 kHz mono →
  JS-implementierter G.711-A-law-Encoder nach ITU-Regeln).
  30-Sekunden-Cap im UI erzwungen. Abspiel-Button holt die
  gespeicherten Bytes, wrappt sie in einen PCM16-WAV-Header und
  spielt sie im `<audio>`-Element. „Auf Standard zurücksetzen"
  nur sichtbar, solange tatsächlich eine Custom-Ansage aktiv ist.
- [x] BYE-Zeitsteuerung nach Audiolänge via select-Timeout
- [x] End-to-End gegen echte Fritz!Box verifiziert (`X-RTP-Stat: PR=100`,
  Codec-Match PCMA beidseitig)

### Web-UI + Config-Management
- [x] **Stats-Modul** (`stats.{c,h}`): Mutex-geschützte Counter
  (gesamt / spam_blocked / legitimate / errors / sip_registered /
  api_latency) + Ring-Buffer für die letzten 10 Anrufe und die
  letzten 10 Fehler.
- [x] **Config-Modul** (`config.{c,h}`): NVS-gestützte Konfiguration
  mit Kconfig als Fallback, atomarer Multi-Field-Update via
  `config_update()`. Alle SIP-/PhoneBlock-/NAT-Nutzer auf Getter
  umgestellt.
- [x] **Web-Server** (`web.{c,h}`, `esp_http_server`, Port 80):
  Routen `/`, `/api/status`, `/api/calls`, `/api/errors`,
  `/api/config`, `/api/fritzbox-setup`, `/api/fritzbox-2fa-status`,
  `/register-start`, `/token-callback`. Heap-4 KB Header-Buffer für
  Browser-Round-Trips (`CONFIG_HTTPD_MAX_REQ_HDR_LEN`), 8 KB Task-
  Stack für die tiefe TR-064-Aufrufkette.
- [x] **HTML-Dashboard** (`web/index.html` via `EMBED_FILES`): Status-
  First-Architektur mit Hash-Router, Status-Pille + Token-Pille +
  Counter-Kacheln + Anrufe + Fehler als Landing. Bei leerem NVS
  erscheint ein „Willkommen"-Hero mit zwei CTAs zu den Wizards.
  i18n-Gerüst via `t(key, params?)`; aktuell nur `de` befüllt.
- [x] **Setup-Wizards** (alle als Sub-Views unter `#/setup/…`):
  Fritz!Box-Autoprovisioning (TR-064 + 2FA), Provider-Preset-
  Auswahl (Top-5: Telekom / Vodafone / 1&1 / sipgate / easybell),
  manuelles Expert-Formular, PhoneBlock-Token via OAuth-Redirect.
  Erfolgreiche Wizards springen per `location.hash='#/'` zurück
  aufs Status-Dashboard.
- [x] **Config-Form → Re-REGISTER**: POST `/api/config`, `config_update()`,
  `sip_register_request_reload()` triggert den SIP-Task, die
  aktuellen Getter zu benutzen.
- [x] **Kconfig-Credential-Fallbacks ausgebaut**: `CONFIG_SIP_REGISTRAR_HOST`,
  `CONFIG_SIP_USERNAME`, `CONFIG_SIP_PASSWORD`, `CONFIG_PHONEBLOCK_TOKEN`
  sind raus — Credentials leben nur noch im NVS und werden über die
  Wizards gesetzt. Dadurch signalisieren leere Felder eindeutig
  „noch nicht konfiguriert".
- [x] **Status-Pillen** nach konsistentem Schema: rot = Handlung nötig
  (nicht eingerichtet / kein Token), gelb = Zwischenzustand
  („Verbinde…"), grün = OK. Inline-CTAs an jeder Pille:
  SIP-„neu einrichten", Token-„Testen" + „Neu anfordern".
- [x] **Self-Test via `/test`**: `phoneblock_selftest()` prüft API-
  Erreichbarkeit + Token-Gültigkeit ohne synthetische Nummer.
  Läuft beim Boot, direkt nach einem OAuth-Token-Callback und per
  POST `/api/token-test` manuell aus der Web-UI. Dashboard zeigt
  API-Latenz + Body-Detail bei Token-Ablehnung.
- [x] **Fehler-Log leeren**: POST `/api/errors/clear` +
  `stats_clear_errors()`. Kleiner „Log leeren"-Button in der
  Fehler-Sektion, sichtbar solange Einträge vorhanden sind.
- [x] **Bootstrap-Safety**: `app_main` bringt den Web-Server
  unconditional hoch, bevor SIP/Self-Test starten. Fehlender Token
  oder fehlende SIP-Config sind INFO, nicht ERROR — ein frisches
  Gerät ist erreichbar, ohne sich still zu beenden.
- [x] **Sockets aufgeräumt**: `CONFIG_LWIP_MAX_SOCKETS=16` (Default 10
  reicht nicht bei paralleler Browser-Polling + SIP + TR-064);
  `post_soap` retry bei `ESP_ERR_HTTP_CONNECT`.
- [x] **URL-Decode des OAuth-Tokens**: `httpd_query_key_value` gibt
  rohe Escapes zurück — Tokens mit `+`/`/` landeten als `%2B`/`%2F`
  im NVS und wurden vom Server abgelehnt. Jetzt korrekt dekodiert.
- [x] **i18n vollständig**: DE / EN / FR / ES-Blöcke gefüllt,
  Sprachumschalter im Footer, Auswahl persistent in `localStorage`,
  Default aus `navigator.language` mit DE-Fallback. Backend-
  Error-Strings bewusst durchgehend Englisch; Info-Strings
  werden über `code`-Diskriminatoren (`status.token.test.ok` etc.)
  im UI lokalisiert.
- [x] **Erweiterte SIP-Parameter im UI** (Transport / Auth-User /
  Outbound-Proxy / Realm / SRTP-Modus) als ausklappbarer
  „Erweitert"-Block im Manuell-Wizard. NVS-round-trip gebaut;
  `sip_register.c` loggt eine `stats_record_error`-Warnung, wenn
  ein Setting aktiviert wird, das der UDP-only-Stack noch nicht
  umsetzt — damit der User im Dashboard sieht, warum sein
  TLS-Toggle nicht greift.
- [x] **Factory-Reset** via POST `/api/factory-reset` —
  `nvs_erase_all` auf den `phoneblock`-Namespace plus
  `announcement_reset()`, dann `esp_restart()` aus einer kurz-
  lebigen Task (damit die HTTP-Antwort erst noch rausgeht).
  „Konfiguration zurücksetzen"-Button in der Status-Seite mit
  Confirm-Dialog.
- [x] **Anrufliste mit PhoneBlock-Links**: Nummern in „Letzte
  Anrufe" werden zu `<site>/nums/<number>`-Links (neuer Tab),
  interne `*`/`**`-Codes bleiben Klartext. Basis-URL kommt aus
  `phoneblock.base_url` mit gestripptem `/api`, also passt für
  Prod- und Test-Instanz gleichermaßen.
- [x] **Stabiler User-Agent**: `http_util.{c,h}` baut einen cache-
  baren String `PhoneBlock-Dongle/<esp_app_desc.version>` aus der
  Firmware-Version und stempelt ihn an jedem outbound HTTP-Call
  an (api.c, tr064.c, sync.c). Server-Logs zeigen jetzt die echte
  Firmware statt `ESP32 HTTP Client/1.0`.

### TR-064-Auto-Provisioning (firmware, komplett mit 2FA)
- [x] `tr064.{c,h}` mit generischem `call_action` für beliebige
  Services + optionalem `<avm:token>`-Header.
- [x] `X_AVM-DE_GetNumberOfClients` → Anzahl vorhandener Clients.
- [x] `X_AVM-DE_SetClient4` → legt IP-Phone mit MAC-basiertem
  Username (`phoneblock-<base-mac-suffix>`, mit `esp_random`-
  Fallback bei leerem eFuse) und alphanumerischem 22-Zeichen-
  Passwort an, liefert die interne Nebenstellennummer zurück.
- [x] **Idempotent-Setup**: `find_client_slot` iteriert
  `X_AVM-DE_GetClient3` über die vorhandenen Clients und liefert
  entweder den existierenden Index für unseren Username oder den
  nächsten freien — beseitigt UPnPError 820 beim zweiten Setup-
  Durchlauf. Interne Nebenstellennummer wird persistent in NVS
  gespeichert und auf dem Dashboard angezeigt; Registrar-Wechsel
  löscht sie automatisch.
- [x] **Default-User-Lookup**: wenn der User das „Benutzer"-Feld
  leer lässt, ermittelt `tr064_get_default_username()` via
  `LANConfigSecurity:X_AVM-DE_GetUserList` den last-logged-in
  Account (Attribut `last_user="1"`) und verwendet ihn für die
  Digest-Auth. Die Box akzeptiert diesen Call ohne Challenge;
  `call_action` erkennt das und überspringt den ClientAuth-Schritt.
- [x] **Zwei-Faktor-Flow** (End-to-End verifiziert):
  `X_AVM-DE_Auth:SetConfig(start)` → Token + Methoden
  (button/dtmf/googleauth), Web-UI pollt `/api/fritzbox-2fa-status`,
  Dongle ruft alle 2 s `X_AVM-DE_Auth:GetState` mit dem Token im
  `<avm:token>`-SOAP-Header auf. Bei `authenticated` wird
  `SetClient4` mit demselben Token wiederholt.
- [x] **Stale-Session-Cleanup**: vor jedem `SetConfig(start)` ein
  Best-Effort-`SetConfig(stop)`, räumt hängengebliebene
  2FA-Sitzungen früherer Versuche weg (Fehlercode 868 „busy").
- [x] **Diagnostische Fehlermeldungen**: `call_action` klassifiziert
  Fehler via Sentinel-Codes TR064_ERR_TRANSPORT / _AUTH / _HTTP /
  _PARSE und erkennt AVMs HTTP-200-mit-Fault-Variante („Auth. failed"
  im Body), damit die Web-UI spezifische Hinweise zeigt statt
  generischem „TR-064 fehlgeschlagen".
- [x] UPnPError-Codes/-Beschreibungen werden aus dem Fault-XML
  extrahiert und in der Web-UI als deutsche Klartexte angezeigt
  (866 = 2FA, 803 = Zeichen, 820/402 = Argumentfehler, etc.).
- [x] Fritz!Box-Admin-Passwort nur flüchtig im RAM während der
  2FA-Sitzung, `memset` beim Abschluss.
- [x] **Dedizierte App-Credentials via RegisterApp**:
  `tr064_register_dongle_app()` wrappt
  `X_AVM-DE_AppSetup:RegisterApp`. Beim Setup (direkter Pfad + 2FA-
  Abschluss) wird eine App-Instance `phoneblockdongle` mit
  Phone-Right-only, kein Internet-Zugriff, 20-Zeichen-Random-
  Passwort nach AVMs Strength-Regeln (Ziffer/Groß/Klein/Special,
  Fisher-Yates-shuffle) angelegt und in NVS persistiert. Damit
  kann der Sync-Task TR-064 ansprechen, ohne dass das Admin-
  Passwort persistent im Flash liegen muss. Fehler beim
  RegisterApp ist nicht fatal — Setup läuft ohne Sync-Option
  weiter.

### Fritz!Box-Blocklist-Sync
- [x] **Ziel**: Am Mobilteil gesperrte Nummern („Nummer sperren")
  als Contribution zur PhoneBlock-Datenbank weiterleiten und
  danach aus der FB-Sperrliste löschen — dann fängt der AB alle
  Anrufer ab, nicht nur die lokalen Einträge.
- [x] **TR-064-API-Wrapper**: `tr064_call_barring_list_url()`
  (`X_AVM-DE_OnTel:GetCallBarringList` → signierte URL) und
  `tr064_call_barring_delete()` (`DeleteCallBarringEntryUID`).
  Nutzen die App-Credentials (Phone-Right reicht).
- [x] **PhoneBlock-API-Wrapper**: `phoneblock_rate()` postet
  `{phone, rating, comment?}` nach `/api/rate`. Rating für
  mobiltiel-gesperrte Nummern: `B_MISSED` — SPAM ohne Aussage
  über die Art.
- [x] **Sync-Task** (`sync.{c,h}`): FreeRTOS-Task blockt auf
  Binary-Semaphore mit 24-h-Timeout. Pro Lauf:
  `GetCallBarringList` → HTTP GET (8 KB Heap) → XML parsen →
  normalisieren (`0…` → `+49…`, `00…` → `+…`) →
  `phoneblock_rate(B_MISSED)` → bei HTTP 200:
  `DeleteCallBarringEntryUID`. Fehler werden gezählt, nicht
  fatal — nächster Lauf retryed.
- [x] **UI-Integration**: Sektion „Sperrlisten-Sync" auf dem
  Status-Dashboard. Checkbox „Sperrlisten-Sync aktivieren"
  (Default **aus** — das Feature ist opt-in, weil es Nummern im
  Namen des Users als Spam meldet). „Jetzt synchronisieren"-
  Button umgeht den Toggle als Escape-Hatch für manuelles
  Testen. Statuszeile zeigt den letzten Lauf (grün / orange bei
  Teil-Erfolgen / rot bei Fehler) mit Anzahl gepushter + gescheiterten
  Nummern. Deaktiviert, wenn keine Fritz!Box-App-Credentials im
  NVS liegen.
- [x] **Phonebook-XML-Parser** (`tr064_parse_phonebook_contacts`):
  Hostseitige Testabdeckung für die realen AVM-Export-Shapes
  (Attribute auf `<number …>` und `<contact …>`, Newlines
  zwischen Tag-Name und Attribut, `<contacts>` nicht versehentlich
  als `<contact>` matchen, unvollständige Einträge skippen).

### OAuth-Redirect für den PhoneBlock-Token
- [x] **Firmware-Seite** (`web.c`): `/register-start` erzeugt eine
  32-Hex-Char-CSRF-Nonce, baut die Callback-URL aus dem `Host`-Header
  (inkl. QEMU-Port), redirected auf das bestehende PhoneBlock-Mobile-
  Login (`/mobile/login?appId=PhoneBlockDongle&…`). `/token-callback`
  verifiziert die Nonce, speichert den Token via `config_update()`.
- [x] **Server-Seite** (`CreateAuthTokenServlet`): neuer
  `appId=PhoneBlockDongle` plus `callback`+`state`-Parameter,
  Whitelist für `http://`-Callbacks auf Private-IPs, `.fritz.box`,
  `.local`, `localhost`, `answerbot`. `MobileLoginController` schleppt
  die Parameter durch Captcha-/Login-Rundreisen.

### Entwicklungs-Infrastruktur
- [x] Pure-C-Parser-Module `sip_parse.{c,h}` und `tr064_parse.{c,h}`
  ohne ESP-IDF-Abhängigkeiten. tr064_parse umfasst
  `xml_find_text`/`xml_escape`/`xml_unescape_inplace` (attribut- und
  namespace-tolerant), `pick_default_user` (User-List-Heuristik),
  `parse_phonebook_contacts` (Call-Barring-XML).
- [x] Host-basierte Unit-Tests unter `firmware/test/` — sip_parse
  (107 Assertions) + tr064_parse (25 Fälle inkl. verbatim Real-
  World-Fritz!Box-Export). Laufzeit ≪ 100 ms, Aufruf `make test`.
- [x] Dev-Hook `CONFIG_SIP_TEST_FORCE_SPAM_STAR_NUMBERS`, damit das
  RTP/Audio-Pfad ohne echte Spam-Nummer getestet werden kann.
- [x] Shared-HTTP-Utility (`http_util.{c,h}`) für den einheitlichen
  User-Agent-Stempel und künftige HTTP-Client-Defaults.

## Offen / Nächste Schritte

### Ohne echte Hardware machbar (nächste Kandidaten)

- [ ] **Extended-SIP-Backend** — `sip_register.c` macht weiterhin
  nur UDP, ohne Outbound-Proxy, ohne Realm-Override, ohne SRTP.
  Die UI-Felder + NVS-Persistierung stehen bereits; zu tun ist
  der Stack-Teil. Reihenfolge nach Bedarf: TCP und TLS (Telekom-
  Pflicht), dann Auth-User-Separation (Telekom: Login = E-Mail,
  Authname = Rufnummer), Outbound-Proxy für Fälle wo der
  Registrar nicht direkt erreichbar ist, zuletzt SRTP.
- [ ] **Paralleler API-Check** — synchroner `phoneblock_check`
  blockiert den SIP-Task 1–2 s. Umbau auf einen zweiten Task
  mit Message-Queue; testbar in QEMU über den bestehenden
  `CONFIG_SIP_TEST_FORCE_SPAM_STAR_NUMBERS`-Hook.
- [ ] **Logging-Level-Regler** im Dashboard —
  `esp_log_level_set("sip", ESP_LOG_DEBUG)` zur Laufzeit, damit
  man bei Feldproblemen verboser werden kann ohne Reflash.
- [ ] **CANCEL-Handler spec-konform** (siehe Tech-Debt) — Testbar
  via QEMU+Fritz!Box, braucht keine neue Hardware.
- [ ] **Event-getriebener Reload-Wakeup** (siehe Tech-Debt) —
  reiner Refactor, lässt sich mit den existierenden Host-Tests
  absichern.
- [x] **OTA-Update** — Partitionslayout auf ota_0/ota_1/otadata
  umgestellt (1280 KB pro Slot, 4 MB Flash voll genutzt, SPIFFS auf
  1408 KB vergrößert). Zwei Upload-Pfade im Web-UI:
  `POST /api/firmware` für lokal hochgeladene .bin-Dateien und
  `POST /api/firmware/check` für den HTTPS-Pull vom PhoneBlock-CDN
  (`CONFIG_PHONEBLOCK_OTA_MANIFEST_URL`, Default
  `https://cdn.phoneblock.net/dongle/latest.json`). In beiden Fällen
  bootet der neue Slot in PENDING_VERIFY; `app_main` markiert ihn
  nach erfolgreichem `example_connect` + `web_start` valid, sonst
  rollt der Bootloader auf die vorige Version zurück. Rollback-
  Kriterien sind bewusst weich — WLAN up + Webserver reichen.
- [ ] **OAuth-Endpoint ins nächste Server-Release rollen** —
  `CreateAuthTokenServlet`-Erweiterung ist committed, muss aber
  auf `phoneblock.net` deployed werden, damit neue Tokens auch
  produktiv funktionieren.
- [ ] **Täglicher PhoneBlock-Token-Health-Check** — aktuell wird das
  Token nur einmal beim Boot (und direkt nach OAuth) via
  `phoneblock_selftest` gegen `/api/test` geprüft. Löscht der Nutzer
  das Token serverseitig im PhoneBlock-Account, bleibt der Dongle
  still kaputt: eingehende Calls werden nicht mehr korrekt klassi-
  fiziert, aber der Status zeigt weiterhin „READY". Task alle 24 h
  (mit zufälligem Offset pro Device, damit die CDN-Hits nicht
  synchron einlaufen), `stats_record_error` bei 401/403, plus einen
  neuen Status-Bit („token_invalid") in `/api/status`, damit die
  LED-FSM das als ERROR-Pattern rendert (siehe Status-LED-Folge-
  punkt unten). Idealerweise die gleiche Task-Infrastruktur wie
  `sync_start` (Scheduler + Wake-Callback).
- [ ] **Privacy-Upgrade: API-Query auf SHA1-Hash umstellen** —
  aktuell sendet `phoneblock_check` die Klartext-Rufnummer an
  `GET /api/num/{phone}?format=json`. Die Mobile-App und der
  Docker-AnswerBot nutzen stattdessen `GET /api/check?sha1=…`,
  wodurch die Nummer den Server nie im Klartext erreicht. Zu tun:
  SHA1-Hashing der international normalisierten Nummer im
  Dongle-Client, Umstellung der URL, Auswertung des
  `blackListed`-Flags aus der Response (persönliche Blacklist
  des angemeldeten Users hat Vorrang vor der Vote-Schwelle;
  ohne dieses Flag ist das Verhalten analog zum Bug aus
  Issue #297). `/api/num/` bleibt parallel erhalten und honoriert
  das Flag inzwischen ebenfalls, ist aber nicht datenschutzoptimal.

### Status-LED
- [x] On-Board-LED als Betriebsanzeige — `status_led.{c,h}`-Modul
  mit 50-ms-Tick-FSM, vier Zuständen:
  - `PAIRING` (100 ms on/off) — WPS aktiv
  - `CONNECTING` (500 ms on/off) — WiFi sucht / reconnect
  - `SETUP` (100 ms on, 900 ms off) — Netz da, Config unvollständig
  - `READY` (dauer-an) — SIP registriert + Token gesetzt
  State-Probes aus `wifi_is_wps_active()` + `wifi_has_ip()` +
  `sip_register_is_registered()` + `config_phoneblock_token()`.
  `CONFIG_STATUS_LED_GPIO` (Default 2 für WROOM-32) und
  `CONFIG_STATUS_LED_ACTIVE_LOW` (Default n — WROOM ist active-high).
  GPIO < 0 deaktiviert das Task komplett.
- [ ] Folgezustände: `ERROR` (Doppel-Blink-Burst, z. B. SIP-401,
  API unreachable, **Token serverseitig gelöscht/invalide** — siehe
  „Täglicher PhoneBlock-Token-Health-Check" oben) und `CALL`
  (Flicker während Anruf-Handling).

### Provisioning & Deployment

**Ziel-UX für die erste Inbetriebnahme** („Oma-tauglich"):

1. Dongle einstecken
2. Am Router den WPS-/„Neues-Gerät"-Knopf drücken → WLAN konfiguriert sich
3. Im Browser `http://answerbot/` öffnen
4. Fritz!Box-Admin-Passwort eingeben → Dongle legt sich selbst als
   IP-Telefon bei der Fritz!Box an (inkl. 2FA-Dialog, falls nötig)
5. Auf „Bei PhoneBlock anmelden" klicken → OAuth-Redirect holt den
   Bearer-Token vom PhoneBlock-Server zurück (wie in der Mobile-App)
6. Fertig — Dongle registriert sich, prüft eingehende Anrufe

Schritt 3–5 sind firmware-seitig umgesetzt und End-to-End gegen
eine reale Fritz!Box getestet (SIP-Registrierung + Blocklist-Sync
laufen durch). Server-seitig ist Schritt 5 committed, aber noch
nicht deployed. Schritt 2 fehlt (WPS), Schritt 3 setzt den
Hostname-Eintrag voraus.

Umsetzungsschritte:

- [x] **WPS-PBC für WLAN** — eigenes `wifi.c`/`wifi.h` ersetzt den
  WiFi-Pfad von `example_connect` (Ethernet/QEMU bleibt bei
  `example_connect`). `wifi_connect()` probiert der Reihe nach:
  1. persistente Credentials aus dem WiFi-NVS (von einem früheren
     WPS-Erfolg oder Baked-Seed),
  2. kompilierte `CONFIG_EXAMPLE_WIFI_SSID/PASSWORD` als Seed für
     die Entwicklerumgebung (`sdkconfig.defaults.local.fritzbox`),
  3. WPS-PBC — `esp_wifi_wps_enable(WPS_TYPE_PBC) +
     esp_wifi_wps_start(0)`; bei Timeout/Failure endloses Retry,
     bei Success werden die Credentials automatisch ins WiFi-NVS
     geschrieben. `WIFI_STORAGE_FLASH` stellt sicher, dass der
     nächste Boot ohne weitere Aktion durchläuft.
- [x] **Auffindbarkeit per Name `answerbot`** — `setup_hostname()` in
  `main.c` setzt parallel `esp_netif_set_hostname(netif, "answerbot")`
  (Fritz!Box übernimmt das in ihren internen DNS) und
  `mdns_hostname_set("answerbot")` + `_http._tcp`-Service
  (macOS/iOS/Linux/Windows-10). Erreichbar als `http://answerbot/` oder
  `http://answerbot.local/`.
- [ ] **Fritz!Box-Auto-Discovery** statt Feld „Host" im Formular.
  Neues Modul `discovery.{c,h}`, ~100 Zeilen. Drei-Stufen-Suche:
  1. DNS-Lookup `fritz.box`.
  2. DHCP-Gateway als Fallback.
  3. Verifikation via `GET http://<ip>:49000/tr64desc.xml`, muss
     `<manufacturer>AVM Berlin</manufacturer>` enthalten.
  Wird in `config_load()` aufgerufen, wenn `sip_host` im NVS leer ist.
  Umsetzung erst mit echter Hardware — QEMUs User-mode-Netzwerk
  simuliert keinen sinnvollen Gateway/DNS-Raum.
- [ ] **Periodischer OTA-Check** (aktuell nur manuell über den
  „Auf Aktualisierung prüfen"-Button). Timer-Task, der z. B. alle
  24 h `handle_firmware_check`-Logik ausführt — mit zufälligem
  Offset pro Device, damit die CDN-Hits nicht synchron einlaufen.
- [ ] **Signatur-Verifikation im OTA-Pfad** — derzeit verlassen
  wir uns rein auf TLS zum CDN. Bei CDN-Kompromittierung oder DNS-
  Hijacking käme eine manipulierte Firmware durch. Lösung: Build-
  Pipeline signiert die `.bin` mit einem Entwickler-Key, Public-Key
  als Byte-Array in die Firmware eingebacken, `esp_https_ota` nur
  nach erfolgreicher Signaturprüfung `esp_ota_set_boot_partition`
  aufrufen lassen (custom validation hook). Kein Secure Boot V2 —
  lokaler USB-Flash soll weiterhin ohne Signatur möglich bleiben,
  damit Community-Forks und Bastler nicht ausgesperrt werden.
- [ ] **WiFi-Reconnect-Strategie** bei Router-Ausfall (Backoff,
  NVS-gepinnte Zugangsdaten).
- [ ] **Feldfeste Fehlerbehandlung**: Fritz!Box down, API down,
  TLS-Fehler, Zertifikatsrotation.

### Hardware-Reife
- [x] 10er-Pack WROOM-32 bestellen (siehe [HARDWARE.md](HARDWARE.md))
- [x] USB-A-Stecker-auf-USB-C-Adapter dazu
- [x] Test auf echter Hardware: WLAN statt QEMU-Ethernet, NAT-Overrides
  leeren, gegen Produktions-Fritz!Box
- [ ] Stromaufnahme + Wärmeentwicklung im Dauerbetrieb messen
- [ ] Gehäuse/Formfaktor (Schrumpfschlauch für den Start, später 3D-Druck
  oder Wechsel auf EGBO-PICO-D4-Dongle)

### Production-Readiness
- [ ] Umzug von Test-Instanz auf `phoneblock.net`-Produktions-API
- [ ] OAuth-Endpunkt deployen — `CreateAuthTokenServlet`-Erweiterung
  ist committed, muss aber mit der nächsten Server-Release rausgehen.
- [ ] Optionales Reporting zurück an PhoneBlock (geblockte Calls,
  welche Nummern)
- [ ] Firmware-Versionierung + Changelog-Policy

### Generisches Provider-Setup (ohne Fritz!Box)
- [x] **UI-Wizard für Top-5-Anbieter** (Telekom, Vodafone, 1&1, sipgate,
  easybell) als Provider-Preset mit Credential-Feldern. Nur O2 bleibt
  Fritz!Box-only; MagentaZuhause ist im stationären Dongle-Einsatz
  direkt nutzbar. Quelle: [PROVIDERS.md](PROVIDERS.md).
- [x] **Erweiterte SIP-Parameter im UI + NVS**: Transport, Auth-User,
  Outbound-Proxy, Realm, SRTP-Modus werden vom Manuell-Wizard
  entgegengenommen und persistiert. Backend-Umsetzung steht noch
  aus (siehe „Ohne echte Hardware machbar" / Extended-SIP-Backend).

### Tech-Debt / Feinschliff
- [ ] **Event-getriebener Reload-Wakeup für den SIP-Task**: aktuell
  cappt der select()-Timeout auf 500 ms, damit der vom Web-UI gesetzte
  `s_reload_requested`-Flag schnell wirksam wird. Saubere Variante:
  Self-Wake-UDP-Socket (Loopback) oder `esp_vfs_eventfd` in die fd_set
  aufnehmen und beim Setzen des Flags ein Byte hineinschreiben. Spart
  die 2 Hz Leerlauf-Wakeups und reagiert innerhalb von Millisekunden.
- [ ] **CANCEL-Handler**: 487 Request Terminated wird mit den CANCEL-
  Headern gebaut, spec-konform wäre, die ursprünglichen INVITE-Header
  zu nehmen (CSeq-Method INVITE). Fritz!Box ist tolerant; könnte bei
  strengeren Registrars ein Problem werden.
- [ ] Paralleler API-Check statt synchroner Aufruf im SIP-Task — heute
  blockiert der Task 1–2 s während der TLS-Abfrage, was eingehende
  Pakete kurz liegenlässt. Für den 1-Call-at-a-time-Dongle unkritisch.
- [ ] Audio-Partition: Wenn die Ansage über ~200 KB wächst, Partition-
  Layout überdenken (App-Slot auf 2 MB, Audio separat in SPIFFS).
- [ ] `tr064.c` holt die Control-URLs hardcoded (`/upnp/control/x_voip`
  und `/upnp/control/x_auth`). Saubere Implementierung fetcht sie aus
  `http://<box>:49000/tr64desc.xml`. Fällt als Nebeneffekt der
  Discovery-Arbeit aus dem oben genannten Auto-Discovery-Modul ab.

## Gotchas, die wir unterwegs gelernt haben

### QEMU
- **WLAN-HF ist nicht emuliert.** Jeder Versuch, den echten WiFi-Treiber
  zu initialisieren, crasht in `register_chipv7_phy`. Workaround:
  `CONFIG_EXAMPLE_USE_OPENETH=y` → OpenCores Ethernet MAC mit
  `-nic user,model=open_eth`.
- **`idf.py qemu monitor`-Chained-Invocation funktioniert nicht**, weil
  `idf_monitor` einen TCP-Socket auf `localhost:5555` erwartet, den
  QEMU ohne `--gdb` nicht öffnet. Stattdessen `idf.py qemu --qemu-extra-args=…`
  und Logs auf stdio.
- **User-mode NAT leitet keine unangeforderten Inbound-Pakete weiter.**
  Die drei heute gebrauchten Forwards, zusammen einmalig für bequemes
  Debugging:
  ```
  -nic user,model=open_eth,\
    hostfwd=udp::5061-:5061,\
    hostfwd=udp::16000-:16000,\
    hostfwd=tcp::8080-:80
  ```
  - `udp::5061` → eingehende SIP-INVITEs von der Fritz!Box
  - `udp::16000` → RTP-Rückkanal (nicht funktional, aber Fritz!Box
    meldet sonst in `X-RTP-Stat` einseitigen Stream)
  - `tcp::8080` → Zugriff auf die Dongle-Web-UI unter `http://localhost:8080/`
    (Port 80 im Guest, 8080 auf dem Host, damit man kein sudo braucht)
- **`CONFIG_SIP_CONTACT_HOST_OVERRIDE` gilt auch für die SDP-`c=`-Zeile.**
  Ohne Override bewirbt der Dongle `10.0.2.15` — eine Adresse, die
  außerhalb QEMUs nicht existiert, und der RTP-Rückweg kommt nie an.

### Build-System
- **`sdkconfig` wird aus `sdkconfig.defaults*` nur bei Neuanlage
  erzeugt.** Einmal vorhanden, bleibt `sdkconfig` die Quelle der Wahrheit
  und ignoriert neue Defaults. Nach Bool-Änderungen in
  `sdkconfig.defaults.local` → `rm sdkconfig && idf.py build`.
- **Die Kombination `CONFIG_X=""` in defaults + nicht-leer in `.local`
  hat sporadisch nicht gezogen** (Build-Cache-Artefakt). Notfalls
  `rm -rf build && idf.py build`.
- **`sdkconfig.defaults.local` wird nicht automatisch geladen.** In
  `CMakeLists.txt` muss `set(SDKCONFIG_DEFAULTS …)` beide Dateien
  explizit listen.
- **`EMBED_FILES` in `idf_component_register`** erzeugt Linker-Symbole
  `_binary_<name>_<ext>_start` / `_end`. Aliasing via `asm("_binary…")`
  auf saubere C-Namen macht den Aufrufcode lesbar.
- **Typografische Anführungszeichen („…") in C-Stringliteralen** brechen
  den Compiler (`stray '\342' in program`). Nur ASCII-Quotes verwenden.
- **`esp_http_server`-Default-Puffer sind klein**: 512 B Header und
  512 B URI reichen für browserbasierte Rückleitungen nicht. Auf
  4 KB bzw. 1 KB via `CONFIG_HTTPD_MAX_REQ_HDR_LEN` /
  `CONFIG_HTTPD_MAX_URI_LEN` heben. Der httpd-Worker-Task-Default
  (4 KB Stack) ist ebenfalls zu klein für die TR-064-Aufrufkette —
  in `web_start()` auf 8 KB setzen.

### Firmware-Design
- **Große Puffer nie auf den Task-Stack legen.** SIP-TX-/RX-Puffer
  (>6 KB) lokal + lwip-Overhead → Stack-Overflow. Lösung: `malloc`/`free`
  pro Aufruf.
- **`select()` ist unter POSIX nicht Busy-Wait.** Der Task schläft
  interrupt-getrieben, die CPU darf idle. Standard-Pattern für UDP-
  Server — beibehalten.
- **`random_hex`-Overflow** (hat früher immer 33 Bytes in beliebig große
  Buffer geschrieben) war ein latenter Memory-Corruption-Bug. Jetzt
  schreibt die Funktion genau `hex_chars + 1` Bytes.
- **Namenskollision `stats_init`**: lwip exportiert ein gleichnamiges
  Macro — deshalb heißt unsere Init-Funktion `stats_setup()`.

### SIP-Protokoll
- **Fritz!Box nutzt Legacy-Digest ohne `qop`** (`realm="fritz.box"`,
  kein `qop` im Challenge). Unser Code unterstützt beide Varianten.
- **`rport`/`received`-Mechanismus ist essentiell** für REGISTER durch
  NAT. Eingehende **neue** Dialoge (INVITE) gehen nicht via rport
  zurück, sondern an die URI im `Contact:`-Header.
- **Fritz!Box schickt sporadisch OPTIONS als Keepalive.** Muss
  beantwortet werden, sonst droppt die Box die Registrierung.
- **Interne Nebenstellen haben `**NN`-Präfix** und werden vom
  PhoneBlock-Server mit HTTP 400 abgelehnt — daher Early-Skip
  vor der API.

### TR-064 / 2FA
- **Feldnamen *müssen* aus der `fritz_tr064`-Dart-Lib übernommen
  werden — Raten scheitert.** Beispiel: Count von
  `GetNumberOfClients` steckt in `NewX_AVM-DE_NumberOfClients`, nicht
  in `NewX_AVM-DE_ClientNumber` oder `…ClientIndex`.
- **Control-URLs:** `/upnp/control/x_voip` (X_VoIP) und
  `/upnp/control/x_auth` (X_AVM-DE_Auth). Stabil seit Jahren, derzeit
  hardcoded; sauberer wäre aus `tr64desc.xml` (→ Discovery).
- **Passwort-Alphabet für SIP-Clients: nur `[A-Za-z0-9]`.** Sonderzeichen
  (`!@#$%-_+=`) führen zu UPnPError 803 „Argument contains invalid
  characters".
- **2FA-Sessions bleiben „busy"** (Code 868) nach fehlgeschlagenen
  SetClient4-Versuchen, bis entweder der Router-Timeout (~60 s)
  abläuft oder wir `SetConfig(stop)` schicken. Vor jedem
  `SetConfig(start)` daher Best-Effort-Stop.
- **`<avm:token>`-Header in SOAP-Envelope** ist der Weg, wie 2FA-
  Freigaben an 2FA-pflichtige Aktionen gekoppelt werden — *nicht*
  via HTTP-Header oder Cookie.

### Web-UI
- Die Dashboard-Seite ist ein **Einmalnutzungs-Werkzeug** (Setup plus
  gelegentlicher Diagnosebesuch). 3-Sekunden-Polling reicht, SSE/
  WebSocket wäre Overkill. Siehe Memory-Eintrag
  [feedback_dongle_web_ui_scope](../.claude/…/feedback_dongle_web_ui_scope.md).
