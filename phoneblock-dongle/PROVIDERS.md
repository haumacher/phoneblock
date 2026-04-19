# PhoneBlock Dongle — SIP-Setup bei deutschen VoIP-Anbietern

Referenz für den Bau eines generischen SIP-Setup-UIs, das den Dongle
*ohne* Fritz!Box als Gateway direkt bei einem VoIP-Anbieter anmeldet.
Für Fritz!Box-Betrieb siehe TR-064-Autoprovisioning (`tr064.c`).

Die Daten stammen aus Anbieter-Docs, Community-Foren und Reseller-FAQs
(Stand 2026-04). Vor der Produktnutzung beim jeweiligen Anbieter
gegenchecken — Registrar-Namen und Port-/Transport-Zwang können sich
ändern, insbesondere Richtung „TLS-Pflicht".

## Kompatibilitätsmatrix

| Anbieter | Generischer SIP-Client | Credentials verfügbar | Besonderheit |
|---|---|---|---|
| sipgate (basic/Comfort) | ✅ | Webportal | nomadisch nutzbar |
| easybell | ✅ | Kundenportal | TLS+SRTP voll supported |
| 1&1 | ✅ | Control-Center, separates „Telefoniepasswort" | Realm `1und1.de` |
| Vodafone (DSL/Kabel) | ✅ | MeinVodafone, separates SIP-PW | regionsabhängiger Registrar |
| NetCologne / NetAachen | ✅ | `einstellungen.netcologne.de` | „immer über Internet" in FB **aus** |
| M-net | ✅ | M-net-Kundenportal | Premium-Trunks TLS |
| Telekom MagentaZuhause | ✅ | Kundencenter (E-Mail + Webpasswort) | nur aus Telekom-Netz — für stationären Dongle unkritisch |
| Telekom CompanyFlex / DeutschlandLAN | ✅ | Geschäftskunden-Portal | **TLS+SRTP Pflicht** |
| Congstar | ⚠️ | Kundencenter | toleriert, kein Support |
| O2 / Telefónica | ❌ | nur per TR-069 an CPE | Registrierung nur aus O2-IP-Range, Daten nicht ausgehändigt |

## Parameter je Anbieter

### Telekom MagentaZuhause (Privatkunden)

| Feld | Wert |
|---|---|
| Registrar | `tel.t-online.de` (DNS via NAPTR/SRV, A-Record reicht nicht) |
| Port / Transport | 5060 UDP/TCP **oder** 5061 TLS (TLS empfohlen, wird wohl Pflicht) |
| SIP-User | `+49<Vorwahl><Rufnr>` (E.164 mit `+`) |
| Auth-User | E-Mail des T-Online-Accounts |
| Passwort | T-Online-Webpasswort |
| SRTP | bei TLS Pflicht; G.711a (PCMA) Pflicht für Notruf |
| Stolperfalle | Reg nur aus Telekom-Netz, fremde ISPs werden abgelehnt |

### Vodafone (DSL / Kabel, ehem. Arcor/KDG)

| Feld | Wert |
|---|---|
| Registrar | `<Vorwahl ohne 0>.sip.arcor.de` (z.B. `0211.sip.arcor.de`) |
| Port / Transport | 5060 UDP |
| SIP-User | Rufnummer **ohne** führende 0 |
| Passwort | SIP-Passwort aus MeinVodafone (**nicht** Kundenkennwort) |
| TLS/SRTP | nein |
| Codec | PCMA, PCMU, teils G.722 |
| Stolperfalle | Region bestimmt Subdomain; Daten teils auf Antrag per Post |

### 1&1

| Feld | Wert |
|---|---|
| Registrar / Outbound | `sip.1und1.de`:5060 UDP/TCP bzw. 5061 TLS |
| SIP-User / Authname | `49<Vorwahl><Rufnr>` (ohne `+`, ohne `0`) |
| Realm | `1und1.de` |
| Passwort | „Telefoniepasswort" aus Control-Center (auto-generiert, änderbar) |
| STUN | `stun.1und1.de:3478` |
| Codec | PCMA, PCMU, G.722 |
| Stolperfalle | kein separater Web-User; jede Rufnummer hat eigenes PW |

### O2 / Telefónica

| Feld | Wert |
|---|---|
| Registrar | dynamisch via TR-069, je nach Tarifgeneration `sip.alice-voip.de` o.ä. |
| Port / Transport | 5060 UDP |
| SIP-User | `49<Vorwahl><Rufnr>` |
| Passwort | nur per ACS an CPE übertragen; neuere Tarife teils in MeinO2 sichtbar |
| Stolperfalle | **offiziell kein Fremdgeräte-Support**; Reg nur aus O2-IP-Range |

### sipgate basic / Comfort

| Feld | Wert |
|---|---|
| Registrar | `sipgate.de` |
| Outbound Proxy | `sip.sipgate.de`:5060 UDP/TCP (TLS 5061 optional) |
| SIP-User | numerische SIP-ID (Webportal) |
| Passwort | separates SIP-PW |
| STUN | `stun.sipgate.net:10000` |
| Codec | PCMA/PCMU; G.722 nur mit Comfort |
| Stolperfalle | nomadisch nutzbar (Plus für Dongle-Tests) |

### easybell

| Feld | Wert |
|---|---|
| Registrar | `voip.easybell.de` (5060 UDP/TCP, 5061 TLS) |
| SIP-User / Authname | aus Kundenportal, `49…`-Format |
| SRTP/TLS | beides voll unterstützt |
| RTP-Range | 10000–50000 |
| Codec | G.722, PCMA (Pflicht Notruf), PCMU |
| Reg-Timer | 3600 s (min. 600) |
| Stolperfalle | RTP+SIP müssen aus gleicher Public-IP kommen; SIP-ALG aus |

### Congstar (Telekom-Resale)

| Feld | Wert |
|---|---|
| Registrar | `tel2.congstar.de` bzw. `tel2.congstar.plusnet.de` |
| Port / Transport | 5060 UDP |
| SIP-User / Passwort | Kundencenter (echtes SIP-PW) |
| Codec | PCMA |
| Stolperfalle | Homespot-Tarife haben keine Telefonie |

### NetCologne / NetAachen

| Feld | Wert |
|---|---|
| Registrar | `sip.netcologne.de` |
| SIP-User | `<Vorwahl ohne 0><Rufnr>@sip.netcologne.de` |
| Passwort | pro Rufnummer separat in `einstellungen.netcologne.de` generieren |
| Transport | 5060 UDP |
| STUN | `stun.netcologne.de` optional |
| Codec | PCMA, G.722 |
| Stolperfalle | „immer über Internet registrieren" (Fritz!Box-Option) aus |

### M-net

| Feld | Wert |
|---|---|
| Registrar | `maxi.m-call.de` (Privat), `voip.m-net.de` (Business) |
| STUN | `stun.mnet-voip.de` |
| SIP-User / Passwort | M-net-Kundenportal („SIP-Telefonnummer" + VoIP-Passwort) |
| Transport | 5060 UDP, TLS bei Premium-Trunks |
| Codec | PCMA, G.722 |

### Reseller (Tele2 etc.)

Meist Wiederverkäufer auf Telekom-/Vodafone-Backbone → gleiche Codec-
und Transport-Erwartungen wie der Carrier, Registrar aber providereigen
(oft `sip.<provider>.de`). Kein einheitliches Profil; immer
Kundenportal konsultieren.

## Minimale UI-Felder für den Dongle

Deckt die Top-5 ohne Provider-Preset komplett ab.

**Pflicht:**
- Registrar-Host (Freitext, mit DNS-SRV-Lookup)
- Port (Default 5060, 5061 bei TLS)
- Transport (UDP / TCP / TLS, Dropdown)
- SIP-User / „Internet-Rufnummer" (Freitext, beide Formate `+49…` und `49…` zulassen)
- Auth-User (separates Feld, Default = SIP-User; für Telekom = E-Mail)
- Passwort
- Outbound-Proxy (optional, leer = Registrar)
- Realm (optional, für 1&1 vorausgefüllt)

**Empfohlen:**
- Registrierungs-Intervall (60–3600 s)
- SRTP-Modus (off / SDES / mandatory)
- Codec-Reihenfolge (PCMA **zuerst** wegen Notruf-Pflicht)
- STUN-Server (für O2/sipgate-Spezialfälle)

**Usability-Gewinn:**
- **Provider-Preset-Dropdown** (Fritz!Box-Auto / Telekom / Vodafone /
  1&1 / sipgate / easybell / „Eigener Registrar") — füllt alle Felder
  vor und versteckt den Rest hinter „Experteneinstellungen".

## Anbieter mit Einschränkungen

- **O2 / Telefónica** — SIP-Daten werden grundsätzlich nicht
  herausgegeben, Registrierung nur aus O2-IP-Range. Dongle realistisch
  nur *hinter* einem O2-Router per Fritz!Box-Autoprovisioning.
- **Congstar** — toleriert, aber kein Support. Funktioniert, sobald
  SIP-Daten ausgehändigt sind.

**Nicht einschränkend für den stationären Dongle:**
- Telekom MagentaZuhause — „nur aus Telekom-Netz" heißt nur:
  nicht nomadisch nutzbar (kein Hotel-WLAN, kein Ausland). Der Dongle
  hängt immer beim Kunden daheim, also im Telekom-Netz. Damit ist
  MagentaZuhause regulär als Provider-Preset aufnehmbar. TLS wird aber
  voraussichtlich Pflicht.

Für O2-Anschlüsse bleibt TR-064-Autoprovisioning via Fritz!Box der
einzig zuverlässige Weg.

## Quellen

- [Telekom Hilfe — Einrichtung SIP-Client](https://www.telekom.de/hilfe/werksreset-speedport/werksreset-faq/einrichtung-sip-client)
- [Telekom Hilfe — IP-Telefonie mit anderen Clients](https://www.telekom.de/hilfe/festnetz-internet-tv/telefonieren-einstellungen/ip-telefonie-mit-anderen-clients)
- [Telekom Community — SIP-Service außerhalb des Heimnetzes](https://telekomhilft.telekom.de/conversations/festnetz-internet/nutzung-des-sip-service-magenta-zuhause-au%C3%9Ferhalb-des-heimnetzes/679e012fddc17d747cd79b23)
- [Telekom DeutschlandLAN SIP-Trunk Technische Unterlage](https://geschaeftskunden.telekom.de/internet-dsl/tarife/companyflex/sip-trunk/sip-trunk-technische-unterlage)
- [Asterisk Community — TLS & SRTP for Telekom DeutschlandLAN](https://community.asterisk.org/t/tls-rstp-for-telekom-deutschlandlan-sip-trunk/77114)
- [Vodafone Community — SIP-Daten Festnetztelefonie eigener Router](https://forum.vodafone.de/t5/Community-Blog/SIP-Daten-Festnetz-Telefonie-f%C3%BCr-Deinen-eigenen-Router/ba-p/3044338)
- [3CX-Forum — Vodafone Kabel SIP Einrichtung](https://www.3cx.de/forum/threads/vodafone-kabel-deutschland-sip-einrichtung.97453/)
- [1&1 Versatel — Voice SIP Hilfe](https://www.1und1.net/hilfe/sip)
- [Snom Wiki — 1&1 Interoperability](http://wiki.snom.com/Interoperability/ITSP/1&1)
- [1&1 Hilfe — VoIP verschlüsseln](https://hilfe-center.1und1.de/voip-telefonie-verschluesseln)
- [O2 Community — VoIP Zugangsdaten in FRITZ!Box](https://hilfe.o2online.de/dsl-kabel-glasfaser-router-software-internet-telefonie-34/internetrufnummer-voip-sip-an-fritzbox-einrichten-zugangsdaten-608050)
- [O2 Community — VoIP in Fremdnetzen](https://hilfe.o2online.de/dsl-kabel-glasfaser-router-software-internet-telefonie-34/voip-in-fremdnetzen-227635)
- [RedTeam Pentesting — o2 ACS VoIP Credentials Disclosure](https://www.redteam-pentesting.de/en/advisories/rt-sa-2015-005/)
- [sipgate basic — Zugangsdaten](https://app.sipgatebasic.de/konfiguration/253/zugangsdaten-basic)
- [sipgate Hilfe — Allgemeine Konfigurationsdaten](https://basicsupport.sipgate.de/hc/de/articles/206519079-Allgemeine-Konfigurationsdaten)
- [sipgate — SRTP/TLS Sprachverschlüsselung](https://www.sipgate.de/funktionen/sprachverschluesselung-mit-srtp-und-tls)
- [easybell — VoIP-Experteneinstellungen](https://www.easybell.de/hilfe/telefon-konfiguration/allgemein/voip-experteneinstellungen/)
- [easybell — SIP-Zugangsdaten einsehen](https://www.easybell.de/hilfe/fragen/vertragsfragen/antwort/wo-kann-ich-mein-sip-passwort-aendern/)
- [easybell — Telefonie verschlüsseln](https://www.easybell.de/hilfe/telefon-konfiguration/allgemein/so-koennen-sie-telefonie-verschluesseln/)
- [Congstar Forum — All-IP Zugangsdaten für SIP-Telefon](https://forum.congstar.de/thread/58234-all-ip-zugangsdaten-f%C3%BCr-sip-telefon/)
- [TP-Link FAQ — Congstar VoIP Modemrouter](https://www.tp-link.com/de/support/faq/1519/)
- [NetCologne — Eigener Router: Zugangsdaten Telefonie](https://www.netcologne.de/privatkunden/hilfe/eigenen-router-einrichten/zugangsdaten-fuer-telefonie/)
- [LANCOM KB — SIP-Leitung NetCologne/NetAachen](https://knowledgebase.lancom-systems.de/pages/viewpage.action?pageId=32983537)
- [M-net Hilfe — Zugangsdaten Endgerät](https://www.m-net.de/hilfe-service/fragen-und-antworten/frage/show/wofuer-sind-die-zugangsdaten-was-mache-ich-damit/1/endgeraet)
- [IPTAM PBX — M-net SIP Trunk Einstellungen](https://www.iptam.com/Einstellungen%253A%2BM-net%2BSIP%2BTrunk%2B%2528M-net%2BTelekommunikations%2BGmbH%2529)
