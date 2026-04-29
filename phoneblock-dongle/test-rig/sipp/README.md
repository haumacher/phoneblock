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

| Datei                  | Rolle                                                |
|------------------------|------------------------------------------------------|
| `registrar.xml`        | UAS, akzeptiert REGISTER ohne Auth, antwortet 200 OK |
| `registrar-auth.xml`   | UAS mit `401 + WWW-Authenticate` → 200 OK            |
| `caller.xml`           | UAC, sendet INVITE direkt an den Dongle              |

## Setup

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
        -s +4915112345678 \
        -rsa <DONGLE_IP>:5061 \
        -i <HOST_IP>
   ```

   - `-m 1` — genau ein Anruf, dann beenden.
   - `-s` — Anrufer-Nummer (wird an PhoneBlock zur Spam-Prüfung
     gesendet). Eine Nummer wählen, die auf phoneblock.net als Spam
     bewertet ist; alternativ den Build-Schalter
     `SIP_TEST_FORCE_SPAM_STAR_NUMBERS` setzen und dann `*123`
     anrufen.
   - `-rsa <DONGLE_IP>:5061` — Ziel des INVITE; lokaler SIP-Port des
     Dongles (Standard 5061).

   Erwartung: Dongle nimmt ab, spielt Ansage, sendet BYE. SIPp
   beendet das Szenario "successful call".

## Bekannte Einschränkungen

- **Nur UDP.** TCP/TLS würde voraussetzen, dass das INVITE auf
  derselben persistent-Connection ankommt wie REGISTER — das ist
  zwischen zwei SIPp-Instanzen nicht ohne Weiteres koordinierbar.
  Für TCP-/TLS-Stack-Tests Kamailio oder einen echten Provider
  verwenden.
- **Digest wird nicht geprüft.** `registrar-auth.xml` akzeptiert
  jeden Authorization-Header, ohne den Hash zu validieren — SIPp
  kann MD5 nicht zur Laufzeit über den Challenge berechnen. Für
  echte Digest-Tests Kamailio oder Provider verwenden; die
  Auth-User-/Realm-Logik des Dongles ist über `test_sip_auth`
  schon host-getestet.
- **Kein RTP-Replay.** `caller.xml` schickt nur SIP-Signalling,
  kein Audio. Für Audio-Tests `pcap_play_audio` ergänzen und eine
  PCMA-PCAP-Datei mit-shippen — bewusst weggelassen, weil der
  Dongle die Spam-Erkennung *vor* der Audio-Phase macht
  (CallerID → `/api/check-prefix`).

## Troubleshooting

- **Dongle bleibt auf "Verbinde…"**: SIPp läuft auf `127.0.0.1`
  statt LAN-IP. `-i <HOST_IP>` prüfen.
- **REGISTER kommt, aber kein 200 OK zurück**: Firewall (`ufw`,
  `firewalld`) blockiert eingehende UDP-Pakete an Port 5060.
- **INVITE kommt nicht beim Dongle an**: Dongle bindet auf 5061,
  nicht 5060. `-rsa <DONGLE_IP>:5061` (nicht `:5060`).
- **Anruf landet als "kein Spam"**: Caller-Nummer ist auf
  phoneblock.net nicht als Spam bewertet. Build mit
  `SIP_TEST_FORCE_SPAM_STAR_NUMBERS=1` oder eine bekannte
  Spam-Nummer aus den Recent-Calls verwenden.
