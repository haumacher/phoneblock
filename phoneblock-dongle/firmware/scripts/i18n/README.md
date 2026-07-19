# Dongle localized assets (issue #460)

Source of truth for the per-language **answer-bot announcement audio** and
**status-mail string packs** that the firmware pulls from the CDN. The
firmware carries no per-language payload — see `main/i18n_sync.c`.

## Files

- `languages.txt` — locales to publish: `<firmware-code> <deepl-target>`.
  Add a line, re-run the generator, done — no firmware change.
- `announcement.de.txt` — the German announcement script (the TTS source /
  DeepL translation source). **Edit this** to change what callers hear.
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

| Kind | Path on CDN | Fetched by | Integrity |
|------|-------------|-----------|-----------|
| announcement audio | `i18n/audio/announcement-<lang>.alaw` | firmware | SHA-256 in signed manifest |
| mail string pack | `i18n/mail/mail-<lang>.json` | firmware | SHA-256 in signed manifest |
| web-UI pack | `i18n/ui/lang-<code>.json` | browser | HTTPS (CDN TLS) |

Only the firmware-consumed assets (audio, mail) are in the signed
`manifest.json`; the UI packs are plain browser-fetched static files.

## Publishing

```bash
# Dry run: build + sign locally with a test key, print the upload commands.
../i18n-assets.sh --dry-run --key /path/to/test-private.pem

# Real release (needs DeepL + ElevenLabs keys and the OTA signing key):
../i18n-assets.sh --version 1.5.0
```

See `../i18n-assets.sh --help` for all options (`--from-audio` for human
voice-overs instead of TTS, `--langs` to build a subset, `--no-upload`).
Credentials come from `../release.settings` (gitignored) or the environment.

Adding a language is a **publish step, not a firmware change**: every
deployed dongle picks up the new locale from the CDN after the user selects
it. The manifest is signed with the same ECDSA-P256 release key as OTA.
