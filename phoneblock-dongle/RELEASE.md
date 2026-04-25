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
und flippt `stable/manifest.json` atomar. Vorige Versionen bleiben liegen — Rollback per
Hand: `ssh haumac@cdn.phoneblock.net 'cd /public_html/cdn/dongle/firmware && rm -rf stable && cp -r <alte-version> stable'`
(oder das `manifest.json` aus `<alte-version>/` mit absoluten Pfaden neu rendern).

`--stage` baut nur lokal nach `firmware/release/<version>/`, `--dry-run` zeigt die ssh/scp-Befehle.

## esp-web-tools

```
firmware/scripts/mirror-installer-deps.sh
```

Ohne Argument → npm `latest`. Mit Argument auf eine Version pinnen.
