# Dongle localized assets (issue #460)

Source of truth for the per-language **answer-bot announcement audio** and
**status-mail string packs** that the firmware pulls from the CDN. The
firmware carries no per-language payload — see `main/i18n_sync.c`.

## Files

- `languages.txt` — locales to publish: `<firmware-code> <deepl-target>`.
  Add a line, re-run the generator, done — no firmware change.
- `audio/announcement-<lang>.alaw` — the **committed announcement recordings**
  (raw G.711 A-law, 8 kHz mono — exactly what the device streams; the same
  format as the old single `main/audio/announcement.alaw`). Hand-record or
  synthesize each one however you like and drop it here; the release script
  uses it verbatim. `main/audio/convert.sh` converts a wav/mp3 to `.alaw`. A
  language with no file here ships **text-only** (the device answers silently).
- `announcement.de.txt` — reference transcript of the German recording (what
  the voice says); not consumed by the build, just handy for re-recording and
  for translators.
- `mail.de.json` — the German status-mail strings, keyed. Mirrors the
  compiled-in fallback table in `main/mail_i18n.c`; keep the keys in sync.
  Values may contain `printf` specifiers (`%d`, `%s`, `%lld`, `%u`) and the
  `<b>…</b>` tag — the generator preserves them across translation.
- `ui/lang-<code>.json` — reviewed **web-UI** translations (en/fr/es
  extracted from the former inline dicts). German is NOT here: it stays
  inline in `main/web/index.html` as the offline base and the DeepL source.
  A locale without a reviewed pack here is DeepL-translated from the
  extracted German at publish time.
- `extract-de-ui.js` — prints `I18N.de` from `index.html` as JSON (the
  machine-readable German UI source; no German text is duplicated in the repo).
- `public.pem` — the release **public** key (same one baked into
  `main/manifest_sig.c`). Used only for the generator's signature self-check.

## Asset kinds

Assets are **co-located with the firmware release** on the CDN, under
`firmware/<version>/i18n/` — the device fetches the subtree matching the
version it runs (see `main/i18n_sync.c`), so an older release in the field is
never affected by a newer one's key changes, and there is a single version
axis to reason about.

| Kind | Path on CDN (under `firmware/<version>/i18n/`) | Fetched by | Integrity |
|------|-------------|-----------|-----------|
| announcement audio | `audio/announcement-<lang>.alaw` | firmware | SHA-256 in signed manifest |
| mail string pack | `mail/mail-<lang>.json` | firmware | SHA-256 in signed manifest |
| web-UI pack | `ui/lang-<code>.json` | browser | HTTPS (CDN TLS) |

Only the firmware-consumed assets (audio, mail) are in the signed
`manifest.json`; the UI packs are plain browser-fetched static files.

## Publishing

Assets ship **with the firmware, in one step**: `scripts/release.sh` calls
`i18n-assets.sh` for the release version, so `release.sh` alone publishes the
`.bin` and its co-located i18n bundle. To (re)publish assets on their own:

```bash
# Dry run: build + sign locally with a test key, print the upload commands.
../i18n-assets.sh --dry-run --key /path/to/test-private.pem

# Standalone real publish for a given firmware version:
../i18n-assets.sh --version 1.5.0
```

`--version` MUST equal the firmware release version (what the device reports
as `firmware_version`). See `../i18n-assets.sh --help` for all options
(`--from-audio` to take recordings from another dir, `--langs` for a subset,
`--no-upload`). The only credential a fully-committed setup needs is the OTA
signing key (KeePassXC, via `../release.settings`); `DEEPL_API_KEY` is used
only to fill in text packs for locales that don't have a committed one.

Adding a language is a **publish step, not a firmware change**: every
deployed dongle picks up the new locale from its version's CDN subtree after
the user selects it. The manifest is signed with the same ECDSA-P256 release
key as OTA.
