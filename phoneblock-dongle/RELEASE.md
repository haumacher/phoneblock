# Dongle-Release

## Firmware

```
. $IDF_PATH/export.sh
git tag dongle-vX.Y.Z
firmware/scripts/release.sh
```

Voraussetzung: sauberer Tree, HEAD genau auf einem `dongle-v*`-Tag,
KeePassXC-Master-Passwort zur Hand (siehe „OTA-Signing-Key").

Pfad zur `.kdbx` einmalig in `firmware/scripts/release.settings`
eintragen — `release.sh` sourcet die Datei automatisch.
`firmware/scripts/release.settings.template` zeigt die erwarteten
Variablen (`KEEPASS_DB`, optional `KEEPASS_ENTRY` /
`KEEPASS_ATTACHMENT`). Datei ist gitignored, Credentials bleiben
lokal.

`release.sh` pusht den Tag erst, *nachdem* der CDN-Upload geklappt
hat — bricht der Lauf vorher ab (Build-Fehler, falsches KeePass-PW,
Netzwerk-Hänger), bleibt der Tag lokal. Aufräumen mit
`git tag -d dongle-vX.Y.Z` und neu taggen, kein Force-Push nötig.

Der Schlüssel wird beim Signieren in `${TMPDIR:-/tmp}` materialisiert
und nach dem Signieren `shred`et. Wer einen Host-`keepassxc-cli` (≥ 2.7.5)
hat, kann mit `TMPDIR=/dev/shm` das tmpfs erzwingen — Flatpak'd KeePassXC
sieht den Host-`/dev/shm` nicht und braucht den `/tmp`-Default.

Flatpak'd KeePassXC: einmalig einen Wrapper anlegen, damit `release.sh`
das CLI findet:

```
sudo tee /usr/local/bin/keepassxc-cli >/dev/null <<'EOF'
#!/bin/sh
exec flatpak run --command=keepassxc-cli org.keepassxc.KeePassXC "$@"
EOF
sudo chmod +x /usr/local/bin/keepassxc-cli
```

`release.sh` baut, signiert die App-Binary, lädt nach
`cdn.phoneblock.net:/public_html/cdn/dongle/firmware/<version>/`
und flippt `stable/manifest.json` atomar. Vorige Versionen bleiben liegen — Rollback heißt
`stable/manifest.json` und `stable/version.json` aus der Zielversion neu hochladen
(z.&nbsp;B. `git checkout dongle-v<alt> -- firmware/scripts/manifest.json.tmpl` + `release.sh --stage`,
dann die beiden Dateien per sftp ersetzen).

`--stage` baut nur lokal nach `firmware/release/<version>/`, `--dry-run` zeigt die ssh/scp-Befehle.
In beiden Modi wird signiert — sonst wäre der Stage-Output kein Spiegel des Live-Manifests.

## OTA-Signing-Key

Die OTA-Update-Pfad-Verifikation hängt an einem ECDSA-P256-Schlüssel
(`prime256v1`, Hash SHA-256): das CDN liefert ein signiertes Manifest,
der Dongle prüft Signatur und SHA-256 der App-Binary, bevor er die neue
Partition aktiviert. Lokales `idf.py flash` und manueller
`POST /api/firmware`-Upload bleiben **ohne** Signaturzwang — die
Verifikation greift nur auf dem CDN-Pull-Pfad.

P-256 ist gewählt, weil ESP-IDF's mbedtls die Kurve nativ aktiviert hat
(`MBEDTLS_ECP_DP_SECP256R1_ENABLED`, `MBEDTLS_ECDSA_C`); kein
Custom-Config-Header, keine vendored Crypto-Lib, keine extra Komponente.

### Schlüssel anlegen (einmalig)

```
# Privater Schlüssel in einem temporären Verzeichnis erzeugen — /tmp,
# weil Flatpak'd KeePassXC sich aus /dev/shm nichts holen kann. Auf
# einem reinen Host-Setup zur Sicherheit lieber /dev/shm nehmen.
WORK=$(mktemp -d)
cd "$WORK"
openssl ecparam -name prime256v1 -genkey -noout -out private.pem
openssl pkey -in private.pem -pubout -out public.pem

# Public Key als DER-SubjectPublicKeyInfo (91 Bytes, geht 1:1 in
# firmware/main/firmware_update.c als OTA_PUBKEY_DER[]):
openssl pkey -in private.pem -pubout -outform DER > public.der
xxd -i public.der
```

Den Inhalt von `private.pem` in KeePassXC ablegen:

1. Eintrag mit Titel **`PhoneBlock-Dongle Signing Key`** anlegen
   (Titel ist in `sign-manifest.sh` der Default; abweichend per
   `KEEPASS_ENTRY` setzbar).
2. `private.pem` als Attachment dranhängen (Dateiname **`private.pem`**;
   `KEEPASS_ATTACHMENT` zum Überschreiben).
3. Optional `public.pem` und den Hex-Dump von `public.der` als
   Custom-Field „pubkey", damit man später die in der Firmware
   kompilierte Public-Key-Konstante gegen die KeePassXC-Quelle
   vergleichen kann.
4. Als Backup: zweite Kopie der `.kdbx` auf einem Offline-Stick.

```
# Aufräumen.
shred -u "$WORK"/private.pem "$WORK"/public.pem "$WORK"/public.der
rmdir   "$WORK"
```

### Schlüssel rotieren

Die Firmware kompiliert zwei Public-Key-Slots ein (`PRIMARY` + `NEXT`).
Rotation:

1. Neues Schlüsselpaar wie oben anlegen, das neue `public.der` in den
   `OTA_PUBKEY_NEXT`-Slot einkompilieren, Firmware-Release ausspielen
   (signiert noch mit dem alten Key, weil ein Großteil der Flotte erst
   mit diesem Release den neuen Slot lernt).
2. KeePassXC-Eintrag-Attachment auf den **neuen** privaten Schlüssel
   umstellen — ab dem nächsten Release signiert `release.sh` mit dem
   neuen Schlüssel; akzeptiert wird er von der Flotte über den
   `OTA_PUBKEY_NEXT`-Slot.
3. Nach genug Roll-out-Zeit den alten Slot (`OTA_PUBKEY_PRIMARY`) durch
   den neuen ersetzen, `OTA_PUBKEY_NEXT` auf leeres Array setzen.

### Verlust des privaten Schlüssels

Sollte der Schlüssel verloren gehen, **bevor** ein Rotations-Release durchlief,
können CDN-Updates nicht mehr signiert werden — die Flotte bleibt auf der
letzten validen Firmware. Recovery: neue Firmware mit neuem `OTA_PUBKEY_PRIMARY`
**lokal flashen** (USB ist signaturfrei). Genau dafür ist der lokale Flash-Pfad
bewusst ohne Signaturzwang gehalten.

## esp-web-tools

```
firmware/scripts/mirror-installer-deps.sh
```

Ohne Argument → npm `latest`. Mit Argument auf eine Version pinnen.
