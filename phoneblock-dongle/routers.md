# PhoneBlock Dongle — Router mit lokaler SIP-Telefon-Anmeldung

Referenz dazu, an welchen Heim-Routern sich der Dongle als *internes*
SIP-Telefon (Nebenstelle) anmelden kann. Das ist der bequeme Weg:
Der Dongle registriert sich nicht direkt beim Provider (siehe
[PROVIDERS.md](PROVIDERS.md)), sondern beim Router im eigenen Netz —
genau wie ein Fritz!Box-IP-Telefon. Der Router kümmert sich um die
Provider-Anbindung; der Dongle braucht nur Registrar-IP, Benutzer und
Passwort.

Entscheidend ist, ob der Router einen **eingebauten internen
SIP-Registrar** mitbringt, an dem sich ein beliebiges SIP-Endgerät
anmelden darf. „Voice over Cable", reine Analog-/DECT-Anschlüsse oder
ein nach außen registrierendes Trunk-Gateway genügen **nicht**.

Daten aus Hersteller-Docs und Community-Foren (Stand 2026-06). Vor der
Produktnutzung gegenchecken — Firmware-Updates ändern den Funktionsumfang.

## Kompatibilitätsmatrix

| Router | Interner SIP-Registrar | Anmeldung des Dongles | Hinweis |
|---|---|---|---|
| AVM Fritz!Box (alle aktuellen, inkl. Cable) | ✅ | automatisch per TR-064 oder manuell | Referenzgerät, bis zu 10 IP-Telefone |
| 1&1 HomeServer (Fritz!Box-OEM) | ✅ | wie Fritz!Box | provider-gebrandete Fritz!Box |
| Telekom Speedport Smart 4 / 4 Plus | ✅ | manuell | werkseitig **deaktiviert**, LAN-Ports erst freigeben |
| Telekom Speedport Smart 3 / W724V / älter | ❌ | — | kein SIP-Server, nur DECT/analog |
| Vodafone Kabel (Station, Connect Box, Compal) | ❌ | — | Voice over Cable, keine SIP-Daten |
| Vodafone EasyBox 804/805 | ❌ | — | kein SIP-Server, nur analog |
| o2 HomeBox 6441/6641/6741 | ❌ | — | TK-Anlage nur für analog/ISDN/DECT |
| OpenWrt + Asterisk / Raspberry Pi (RasPBX) / Turris | ✅ | manuell | nur für Bastler, kein Out-of-the-box-Feature |

## Router mit lokaler SIP-Anmeldung

### AVM Fritz!Box (Referenz)

Standardmerkmal aller aktuellen FRITZ!OS-Versionen, vom kleinen 4020 bis
7590 AX und den Cable-Modellen. Der Dongle wird wie ein IP-Telefon
angelegt:

- **Registrar:** `fritz.box` bzw. die Box-IP (Werkseinstellung `192.168.178.1`)
- **Port / Transport:** 5060 UDP
- **Benutzer + Kennwort:** beim Anlegen unter *Telefonie → Telefoniegeräte
  → Telefon (mit/ohne AB) → LAN/WLAN (IP-Telefon)* vergeben (Kennwort ≥ 8 Zeichen)
- **Anzahl:** bis zu 10 IP-Telefone gleichzeitig

Der Dongle erledigt das nach Möglichkeit selbst per
TR-064-Autoprovisioning (`tr064.c`); manuell geht es genauso.

### 1&1 HomeServer

Baugleich zu einer Fritz!Box (AVM-OEM) und damit identisch im Verhalten:
voller interner Registrar, Anmeldung wie oben. Einzige Einschränkung: die
Oberfläche ist teils provider-gebrandet/eingeschränkt.

### Telekom Speedport Smart 4 / 4 Plus

Die neueren Smart-4-Modelle haben — anders als ältere Speedports — eine
integrierte IP-Telefonanlage, die als interner Registrar fungiert. Sie ist
**werkseitig deaktiviert** und muss in der Weboberfläche eingeschaltet
werden.

- **Registrar:** `speedport.ip`
- **SIP-User / Nutzerkennung:** vom Router erzeugter Eintrag (interne Nummer `**71`)
- **Passwort:** auto-generiert (empfohlen: danach selbst setzen)
- **Stolperfallen:** LAN-Ports müssen erst „freigegeben" werden; teils
  Codec-Probleme (manche Geräte brauchen G.726); möglichst keine externen
  Rufnummern parallel konfigurieren.

### OpenWrt + Asterisk / Raspberry Pi / Turris (für Bastler)

Wer einen offenen Router oder einen kleinen Server betreibt, kann mit
Asterisk/FreePBX einen vollwertigen internen Registrar aufsetzen, an dem
sich der Dongle als Nebenstelle (Extension + Passwort) anmeldet:

- **OpenWrt**: Pakete `asterisk` + `asterisk-res-pjsip` (PJSIP empfohlen,
  `chan_sip` veraltet), inkl. LuCI-Oberfläche für SIP-Accounts.
- **Raspberry Pi / RasPBX**: Asterisk + FreePBX-Image, Extensions im Web-UI.
- **Turris Omnia/MOX**: OpenWrt-Basis, dieselben Asterisk-Pakete.

Vollwertig, aber manueller Installations- und NAT-/Audio-Tuning-Aufwand —
kein vorinstalliertes Feature.

## Router OHNE lokale SIP-Anmeldung

Bei diesen Provider-Routern ist eine Anmeldung des Dongles als internes
SIP-Telefon **nicht möglich**. Abhilfe: eine Fritz!Box vorschalten und den
Dongle dort anmelden, oder den Dongle direkt beim Provider registrieren
(siehe [PROVIDERS.md](PROVIDERS.md)).

- **Vodafone Kabel (Vodafone Station, Connect Box, Compal CH7465):**
  Telefonie läuft als „Voice over Cable" über ein internes Provider-Netz;
  es werden keine SIP-Zugangsdaten herausgegeben, kein offener Registrar.
- **Vodafone EasyBox 804/805:** kein SIP-Server, Telefonie nur analog.
- **o2 HomeBox 6441/6641/6741:** hat eine interne Telefonanlage, aber nur
  für analoge, ISDN- und DECT-Geräte — kein Menüpunkt zur Anmeldung von
  IP-/SIP-Telefonen. o2 verweist für IP-Telefonie selbst auf die Fritz!Box.
- **Telekom Speedport Smart 3, W724V und ältere:** „Speedports haben keinen
  SIP-Server" — eine langjährig dokumentierte Einschränkung. Üblicher
  Workaround: Fritz!Box hinter den Speedport hängen.

## Quellen

- [AVM — IP-Telefon an FRITZ!Box anmelden und einrichten](https://fritz.com/service/wissensdatenbank/dok/FRITZ-Box-7590-AX/42_IP-Telefon-an-FRITZ-Box-anmelden-und-einrichten/)
- [AVM — Anzahl Telefoniegeräte](https://fritz.com/service/wissensdatenbank/dok/FRITZ-Box-7590/1634_Anzahl-Telefoniegerate-die-mit-FRITZ-Box-verwendet-werden-konnen/)
- [dslweb.de — 1&1 HomeServer (AVM-OEM)](https://www.dslweb.de/1und1-homeserver.php)
- [Telekom Handbuch Speedport Smart 4 Plus — IP-Telefonanlage einschalten](https://www.manualslib.de/manual/757934/T-Mobile-Speedport-Smart-4-Plus.html?page=141)
- [GEQUDIO — Installation am Speedport Smart 4 (PDF)](https://gequdio.com/.cm4all/uproc.php/0/telefon/GEQUDIO-Speedport_Smart4-Installation-2025-10-08.pdf)
- [IP-Phone-Forum — Welcher Speedport kann SIP intern?](https://www.ip-phone-forum.de/threads/welcher-speedport-kann-sip-intern.220881/)
- [IP-Phone-Forum — Speedport Smart 3: kein eigener SIP-Server](https://www.ip-phone-forum.de/threads/speedport-smart-3-ip-telefon.304157/)
- [Vodafone Community — SIP/IP-Telefon an Vodafone Station](https://forum.vodafone.de/t5/Archiv-Internet-Ger%C3%A4te/SIP-IP-Telefon-direkt-%C3%BCber-Wlan-an-Vodafone-Station/td-p/2198910)
- [Vodafone Community — IP-Telefon an EasyBox 804](https://forum.vodafone.de/t5/Archiv-Internet-Ger%C3%A4te/ip-telefon-anschlie%C3%9Fen-EasyBox-804/td-p/2637668)
- [o2 Community — Homebox: Telefonie per VoIP oder analog?](https://hilfe.o2online.de/router-software-internet-telefonie-34/o2-dsl-homebox-2-telefonie-per-voip-oder-analog-156357)
- [OpenWrt — Asterisk/PJSIP Pakete](https://github.com/openwrt/telephony/blob/master/net/asterisk/Makefile)
- [RasPBX — FreePBX/Asterisk auf dem Raspberry Pi](https://github.com/playfultechnology/RasPBX)
