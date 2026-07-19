# Dongle i18n — implementation plan (issue #460)

Deliver localized device content (status mail, answer-bot announcement audio,
web UI) with **no per-language payload baked into the firmware**. Localized
assets are generated server-side and pulled from the CDN, keyed by a new
`ui_lang` config field. Adding a language = generate + publish assets; no
firmware change.

## Phases

1. **Foundation — `ui_lang` config field** (`config.c/.h`, `web.c`,
   `web/index.html`). Mirror the `timezone` field end-to-end; the UI's
   language selector POSTs the choice to `/api/config`. Validated/clamped
   charset so it is safe to splice into a URL / filename. **Rig-verifiable.**

2. **Audio — drop the embedded default, resolve a localized file, silent
   fallback** (`announcement.c/.h`, `CMakeLists.txt`, `Kconfig.projbuild`).
   Remove `announcement.alaw` from `EMBED_FILES` (reclaims ~77 KB/app-slot).
   `announcement_open()` resolves: user-uploaded `announcement.alaw` >
   downloaded `announcement-<lang>.alaw` > empty (`len==0`). The SIP path
   already treats `len==0` as "answer, silent, BYE" (`sip_register.c:1476`).
   **Rig-verifiable.**

3. **Asset sync — `i18n_sync.c`** (new). Fetch a signed `manifest.json` from
   the CDN (reuse `firmware_update.c` signature/verify + `blocklist_sync.c`
   stream-to-SPIFFS-temp-then-rename), download the `ui_lang` announcement
   (and mail pack) into SPIFFS when the stored SHA-256 differs. Trigger on
   `ui_lang` change and boot-if-missing. Live fetch needs the CDN assets
   (see phase 6) — pure bits host-tested.

4. **Mail — keyed lookup + German compiled fallback + optional downloaded
   pack** (`mail.c`). Refactor the ~34 hardcoded German strings to key-based
   lookup; German stays compiled in as the offline fallback, other locales
   come from the downloaded pack.

5. **Web UI — lazy-load language packs** (`web/index.html`). Keep German
   inline as the base; move en/fr/es (and future locales) to CDN-fetched
   `lang-<code>.json`, lazy-loaded by the browser on language pick.

6. **Release script — generate + sign + publish assets**
   (`scripts/i18n-assets.sh`). Translate the announcement script + UI/mail
   strings (DeepL), synthesize audio (ElevenLabs → ffmpeg `-f alaw`), build
   and sign the manifest (existing OTA key), upload to `cdn.phoneblock.net`.
   Prepared with credential placeholders; run by the maintainer at deploy.

## Verification

- Host tests (`test/`) for pure logic (lang validation, manifest parse,
  mail key lookup).
- `idf.py build` clean.
- OTA-flash the rig (`http://answerbot`) and verify: `ui_lang` persists
  across reboot via `/api/config`; announcement source resolution
  (no embedded default → silent when no file present; user upload still
  wins). Live CDN download verified once assets are published (phase 6).
