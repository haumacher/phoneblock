# SIPp Test-Rig für den PhoneBlock-Dongle

Mock-Registrar plus "Spam-Caller" auf Basis von [SIPp][sipp]. Damit
lässt sich der Dongle ohne Fritz!Box und ohne realen Provider gegen
einen lokalen Endpunkt anmelden und ein eingehender Anruf simulieren.

[sipp]: https://github.com/SIPp/sipp

## Voraussetzung

```bash
sudo apt install sip-tester
```

(Binary heißt `sipp`, Paket `sip-tester`.)

## Dateien

| Datei                     | Rolle                                                                             |
|---------------------------|-----------------------------------------------------------------------------------|
| `registrar.xml`           | UAS, akzeptiert REGISTER ohne Auth, antwortet 200 OK                              |
| `registrar-auth.xml`      | UAS mit Digest-Challenge **und** Passwort-Verifikation                            |
| `run-auth-registrar.sh`   | Wrapper, der den erwarteten Digest-Hash vorab rechnet und sipp damit startet      |
| `caller.xml`              | UAC, sendet INVITE direkt an den Dongle (UDP)                                     |
| `register-and-call.xml`   | Kombiniertes UAS-Szenario für TCP — REGISTER und INVITE über *eine* Connection    |

## Setup A — UDP (zwei separate Sipp-Prozesse)

1. **Sipp-Registrar starten** (in einem Terminal):

   ```bash
   sipp -sf registrar.xml -p 5060 -i <HOST_IP> -trace_msg
   ```

   - `-p 5060` — bind-Port; mit dem konfiguriert man den Dongle.
   - `-i <HOST_IP>` — die LAN-IP des Rechners, auf dem SIPp läuft.
     Pflicht, sonst bindet SIPp an `127.0.0.1` und der Dongle
     erreicht ihn nicht.
   - `-trace_msg` — schreibt jede SIP-Nachricht in
     `<scenario>_<pid>_messages.log`.

2. **Dongle konfigurieren** — im Web-UI (`http://<dongle>/`) auf der
   SIP-Seite:

   - Transport: `udp`
   - Host: `<HOST_IP>` (Rechner mit SIPp)
   - Port: `5060`
   - SIP-User / Auth-User / Passwort: beliebig (werden bei
     `registrar.xml` ignoriert)
   - Outbound-Proxy / Realm: leer

   Speichern → der Dongle re-REGISTERt sofort. SIPp loggt
   "REGISTER from <Contact>", danach "200 OK out".

3. **Anruf simulieren** (zweites Terminal):

   ```bash
   sipp -sf caller.xml -m 1 \
        -s 0163786575999 \
        -i <HOST_IP> -p 5070 \
        <DONGLE_IP>:5061
   ```

   - `-m 1` — genau ein Anruf, dann beenden.
   - `-s` — Anrufer-Nummer (wird an PhoneBlock zur Spam-Prüfung
     gesendet). Eine Nummer wählen, die auf phoneblock.net als Spam
     bewertet ist; alternativ den Build-Schalter
     `SIP_TEST_FORCE_SPAM_STAR_NUMBERS` setzen und dann `*123`
     anrufen.
   - `-p 5070` — eigener lokaler Port (5060 ist vom Registrar belegt).
   - `<DONGLE_IP>:5061` — Ziel des INVITE; lokaler SIP-Port des
     Dongles (Standard 5061).

   Erwartung: Dongle nimmt ab, spielt Ansage, sendet BYE. SIPp
   beendet das Szenario "successful call".

## Setup B — TCP (ein kombiniertes Sipp-Szenario)

Über TCP **kann** der UAS keine zweite Connection zum Dongle aufbauen
(der Dongle hört auf TCP nicht eingehend, sondern öffnet nur eine
ausgehende Connection zum Registrar — Stand-RFC 5626-Flow). Folge:
REGISTER und INVITE müssen auf demselben Socket laufen, also vom
selben Sipp-Scenario gefahren werden. `register-and-call.xml` macht
genau das mit `transport=t1` (single-socket TCP UAS):

```bash
sipp -sf register-and-call.xml -t t1 -p 5060 -i <HOST_IP> \
     -m 1 -s 0163786575999 -trace_msg
```

Dongle-Config:
- Transport: `tcp`
- Host: `<HOST_IP>`
- Port: `5060`
- Rest wie bei UDP.

Ablauf des Szenarios:

1. Sipp lauscht auf TCP 5060, akzeptiert die ausgehende Verbindung
   des Dongles.
2. Dongle schickt REGISTER → Sipp antwortet 200 OK.
3. 3 Sekunden Pause (Dongle-Settling).
4. Sipp schickt INVITE auf **demselben** Socket (neuer Call-ID, neuer
   Dialog).
5. Dongle nimmt an, spielt Ansage, sendet BYE.

Die RTP-Medien laufen bei beiden Setups weiter über UDP — `transport=t1`
betrifft nur die Signalisierung.

## Setup C — Digest-Auth mit echter Passwort-Verifikation

`registrar-auth.xml` + `run-auth-registrar.sh` zusammen prüfen, dass
der Dongle sich mit den richtigen Credentials anmeldet — nicht nur,
dass *irgendein* Authorization-Header ankommt.

Dongle-Config:
- Transport: `udp`
- Host: `<HOST_IP>`
- Port: `5060`
- SIP-User: `phoneblock-dongle`
- SIP-Pass: `dongle-password`
- Realm: leer (Dongle übernimmt `phoneblock.test` aus dem Challenge)

Starten:

```bash
./run-auth-registrar.sh <HOST_IP>
```

Was der Wrapper macht:

1. Rechnet `MD5(HA1:0123456789abcdef:HA2)` mit den Test-Credentials
   für die mitgegebene HOST_IP (= `sip:<HOST_IP>` als Authorization-URI).
2. Übergibt den Hash als `[$expected_response]` an Sipp.
3. Sipp prüft im zweiten REGISTER mit `<ereg check_it="true">`, dass
   sowohl `username="phoneblock-dongle"` als auch das `response="…"`
   exakt dem erwarteten Hash entspricht. Mismatch → Failed call.

Der Sipp-Challenge enthält **bewusst kein `qop`** — dadurch fällt der
Dongle in den deterministischen `MD5(HA1:nonce:HA2)`-Pfad
(`sip_register.c:267-272`), und der Hash ist vorab berechenbar. Mit
`qop="auth"` würden cnonce + nc mit reinspielen, das geht nicht ohne
Skripting im Sipp.

## Bekannte Einschränkungen

- **TLS noch nicht eingerichtet.** Funktional analog zu TCP — Sipp
  unterstützt `-t l1` als TLS-Single-Socket-Modus. Dazu fehlen aber
  noch Server-Cert-Setup und ggf. ein `CONFIG_*_INSECURE_SKIP_VERIFY`
  Dev-Flag im Dongle. Wenn benötigt: gleiche Szenario-Struktur wie
  `register-and-call.xml`, mit `[transport]` = TLS.
- **qop="auth" wird nicht getestet.** Setup C deckt RFC 2617 ohne
  qop ab. Den qop-Pfad des Dongles (mit cnonce + nc) prüft
  `firmware/test/test_sip_auth` host-seitig; ein Live-Test gegen
  Sipp würde Skript-Hooks erfordern, die das Tool nicht hat.
- **Kein RTP-Replay.** Die Caller-Szenarien schicken nur SIP-Signalling
  und SDP, kein Audio. Für Audio-Tests `pcap_play_audio` ergänzen und
  eine PCMA-PCAP-Datei mit-shippen — bewusst weggelassen, weil der
  Dongle die Spam-Erkennung *vor* der Audio-Phase macht
  (CallerID → `/api/check-prefix`).

## Troubleshooting

- **Dongle bleibt auf "Verbinde…"**: SIPp läuft auf `127.0.0.1`
  statt LAN-IP. `-i <HOST_IP>` prüfen.
- **REGISTER kommt, aber kein 200 OK zurück**: Firewall (`ufw`,
  `firewalld`) blockiert eingehende UDP-/TCP-Pakete an Port 5060.
- **INVITE kommt nicht beim Dongle an** (UDP-Setup): Dongle bindet
  auf 5061, nicht 5060. Argument `<DONGLE_IP>:5061` (nicht `:5060`).
- **TCP-Setup: nach REGISTER kommt kein INVITE durch**: Sipp wurde
  ohne `-t t1` gestartet — Default ist UDP, dann hört Sipp gar nicht
  auf TCP. Auch prüfen, dass der Dongle wirklich `transport=tcp`
  konfiguriert hat (Dashboard zeigt das in der SIP-Sektion).
- **Anruf landet als "kein Spam"**: Caller-Nummer ist auf
  phoneblock.net nicht als Spam bewertet **oder** liegt unter den
  Block-Schwellen. Schwellen im Dashboard prüfen
  (`Direkt-Stimmen` / `Range-Stimmen`); Build mit
  `SIP_TEST_FORCE_SPAM_STAR_NUMBERS=1` oder eine bekannte
  Spam-Nummer aus den Recent-Calls verwenden.
- **Auth-Setup C: REGISTER schlägt fehl mit "Failed call"**:
  `[$expected_response]` und der vom Dongle berechnete Hash gehen
  auseinander. Häufigste Ursachen: SIP-Pass hat einen Tippfehler;
  SIP-User ist nicht `phoneblock-dongle`; SIP-Host hat nicht denselben
  Wert wie das `<HOST_IP>`-Argument von `run-auth-registrar.sh` (R-URI
  fließt in HA2 ein). Sipp loggt den empfangenen `response="…"` in
  `registrar-auth_messages.log`; vergleichen mit dem vom Wrapper am
  Anfang ausgegebenen `Expected digest response`.
