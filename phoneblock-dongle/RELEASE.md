# Dongle-Release

## Firmware

```
. $IDF_PATH/export.sh
git tag dongle-vX.Y.Z
firmware/scripts/release.sh
git push --tags
```

Voraussetzung: sauberer Tree, HEAD genau auf einem `dongle-v*`-Tag.

`release.sh` baut, lädt nach `cdn.phoneblock.net:/public_html/cdn/dongle/firmware/<version>/`
und flippt `stable/manifest.json` atomar. Vorige Versionen bleiben liegen — Rollback heißt
`stable/manifest.json` und `stable/version.json` aus der Zielversion neu hochladen
(z.&nbsp;B. `git checkout dongle-v<alt> -- firmware/scripts/manifest.json.tmpl` + `release.sh --stage`,
dann die beiden Dateien per sftp ersetzen).

`--stage` baut nur lokal nach `firmware/release/<version>/`, `--dry-run` zeigt die ssh/scp-Befehle.

## esp-web-tools

```
firmware/scripts/mirror-installer-deps.sh
```

Ohne Argument → npm `latest`. Mit Argument auf eine Version pinnen.
