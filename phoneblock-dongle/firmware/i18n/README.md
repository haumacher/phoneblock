# Dongle localized assets (issue #460)

Source of truth for the dongle's localized **answer-bot announcement audio**,
**status-mail strings**, and **web-UI strings**. The firmware carries no
per-language payload — assets are published to the CDN, co-located with the
firmware release, and pulled on demand (see `main/i18n_sync.c`,
`main/mail_i18n.c`, the loader in `main/web/index.html`).

Everything is **committed to git and translated during development**. Nothing
is translated at release time.

## Files

- `languages.txt` — one locale code per line (the `ui_lang` the UI selects /
  the device stores). Add a line to add a language.
- `audio/announcement-<lang>.alaw` — **committed announcement recordings**
  (raw G.711 A-law, 8 kHz mono — exactly what the device streams, the same
  format as the old single `main/audio/announcement.alaw`). Hand-record or
  synthesize each however you like and drop it here; the release script uses
  it verbatim (a `.wav`/`.mp3`/`.m4a`/`.flac` is converted). A language with
  no recording ships **text-only** (the device answers silently).
- `audio/announcement-<lang>.txt` — the **transcript of the recording next to
  it**, co-located with the `.alaw`. Keep it in lock-step with what the audio
  actually says; it's the source when re-recording / re-synthesizing and the
  reference for translating the spoken text to a new language. Not consumed by
  the build.
- `l10n/` — the **translation project** (see "Translating" below):
  - `mail/mail_<lang>.arb` — status-mail strings. `mail_de.arb` is the German
    source (mirrors the compiled fallback in `main/mail_i18n.c`); the other
    `mail_<lang>.arb` are its translations. Values keep `printf` specifiers
    (`%d %s %lld %u`) and `<b>` tags.
  - `ui/ui_<lang>.arb` — web-UI strings. `ui_de.arb` is regenerated from the
    inline `I18N.de` in `index.html` (the runtime German source) by
    `gen-ui-de-arb.js`; the others are its translations.
- `gen-ui-de-arb.js` — regenerates `l10n/ui/ui_de.arb` from `index.html`,
  preserving the plugin's per-key CRC so unchanged strings aren't re-translated.
- `public.pem` — the release **public** key (same one in `main/manifest_sig.c`);
  used only for the release script's signature self-check.

## Translating (development time)

Reuses `de.haumacher:auto-translate-arb` — the **same Gradle plugin the mobile
app uses** (`phoneblock_mobile`) — with the same `deepl` server credential in
`~/.m2/settings.xml`. Outputs are committed; no translation runs at release.

```bash
# 1. If the German UI text changed, refresh the UI source from index.html:
node i18n/gen-ui-de-arb.js

# 2. Translate both projects (mail + ui) into en/fr/es (and any new locale):
cd i18n/l10n && gradle translateArb

# 3. Review + commit the updated l10n/**/*_<lang>.arb.
```

Edit German only: `l10n/mail/mail_de.arb` for mail, `index.html`'s `I18N.de`
(then step 1) for the UI. A new locale: add it to `targetLangs` in
`l10n/mail/build.gradle` + `l10n/ui/build.gradle` and to `languages.txt`.

## Asset kinds & CDN layout

Assets are **co-located with the firmware release**, under
`firmware/<version>/i18n/` — the device fetches the subtree matching the
version it runs, so an older release in the field is never affected by a newer
one's key changes, and there is a single version axis.

| Kind | Path (under `firmware/<version>/i18n/`) | Source | Fetched by | Integrity |
|------|------|------|-----------|-----------|
| announcement audio | `audio/announcement-<lang>.alaw` | `audio/*.alaw` | firmware | SHA-256 in signed manifest |
| mail string pack | `mail/mail-<lang>.json` | `l10n/mail/*.arb` | firmware | SHA-256 in signed manifest |
| web-UI pack | `ui/lang-<code>.json` | `l10n/ui/*.arb` | browser | HTTPS (CDN TLS) |

The published packs are the ARB files with their `@key` metadata stripped
(plain key→string JSON). Only the firmware-consumed assets (audio, mail) are
in the signed `manifest.json`; the UI packs are plain browser-fetched files.

## Publishing (release time)

Assets ship **with the firmware, in one step**: `scripts/release.sh` calls
`i18n-assets.sh` for the release version, publishing the `.bin` and its
co-located i18n bundle together. To (re)publish assets on their own:

```bash
# Dry run: assemble + sign locally with a test key, print the upload commands.
../i18n-assets.sh --dry-run --key /path/to/test-private.pem

# Standalone real publish for a firmware version:
../i18n-assets.sh --version 1.5.0
```

`--version` MUST equal the firmware release version (what the device reports
as `firmware_version`). The only credential needed at release is the OTA
signing key (KeePassXC, via `../release.settings`) — no translation runs here.
A missing pack degrades to the firmware's compiled German fallback / the
browser's inline German. The manifest is signed with the same ECDSA-P256
release key as OTA.
