# Dongle dialog: speaker choice and own dialog sets (#429)

Follows `2026-10-31-dongle-interactive-dialog-design.md`. The dialog clips now
exist per speaker (`i18n/audio/dialog/<lang>/<speaker>/`, `speakers.json`,
PR #671): Leonie (female, default, all languages) and Roger (male, all
languages); further speakers may speak only some languages (`langs`), German
first. This change lets the user pick a speaker, and assemble an own set in the
browser.

## Decisions

1. **The device never assembles CDN clips.** `audio-assets.sh` publishes one
   set file (`PBDS` header + clips, `dialog.h`) per locale, speaker and size
   tier. The device downloads one file, as today. The masters in
   `audio/tts/` are source material only (and serve as previews, 6).

2. **Per-locale dialog index.** One manifest listing every speaker's tiers in
   every locale outgrows `MANIFEST_CAP` (12 KB) already with two speakers. The
   channel manifest therefore names, per locale, one small index file instead
   of the tier list:

   ```json
   manifest.json:
   { "format": 1,
     "assets": { "de": { "announcement": { "path": …, "sha256": …, "bytes": … },
                         "dialog": { "path": "dialog-de-1a2b3c4d.json",
                                     "sha256": "<hex>", "bytes": 2210 } } } }

   dialog-de-1a2b3c4d.json:
   { "speakers": [
       { "id": "leonie", "name": "Leonie", "gender": "female",
         "preview": "tts/de/leonie/hello-1.mp3",
         "sets": [ { "path": "dialog-de-leonie-s-5e6f7a8b.bin", "sha256": …, "bytes": 117995 },
                   { "path": "dialog-de-leonie-m-…bin", … },
                   { "path": "dialog-de-leonie-l-…bin", … } ] },
       { "id": "roger", … } ] }
   ```

   The first speaker of an index is the locale's default (`speakers.json`
   default). `preview` is relative to `<OTA base>/audio/` (the masters are not
   in a format channel). Everything content-addressed as today; the index is
   uploaded before the manifest and survives one generation like the sets.

   `AUDIO_FORMAT_VERSION` stays **1**: v1 was never published and no released
   firmware reads the channel, so the format is redefined rather than bumped.
   From the first publication on, the usual rule applies again.

3. **Choice on the device.** New config key `dialog_speaker` (a speaker id,
   `[a-z][a-z0-9-]{0,15}`, empty until the first pick). The locale is chosen
   as today (ui_lang → en → de, the first with a dialog index). The size tier
   as today (largest within `storage_dialog_budget()`). One set on flash, as
   today.

   **Random pick.** So the fleet does not sound alike (and a caller cannot
   recognize "a PhoneBlock bot" by its voice): when `dialog_speaker` is empty
   or not in the locale's index, the sync picks uniformly among the locale's
   speakers, persists the pick (stable across days and reboots, no daily
   re-download) and logs it once. The table's gender mix steers the picks on
   purpose — spam callers talk longer to a woman, so female voices may
   outnumber male ones. A ui_lang change always clears the setting, even a
   speaker the user chose: dongles start in English, and a pick made there
   must not pin the speaker after the switch to the user's language. The web
   UI shows the current speaker, picked or chosen, and offers the others; no
   "Random" entry.

4. **The device keeps the locale's index** (verified by SHA-256) next to the
   set, so the web UI can list the speakers without a CDN round trip and after
   a reboot: `dialog-speakers.json`, rewritten only when its SHA changes.
   `GET /api/dialog/speakers` answers `{selected, effective, own, speakers:
   [{id, name, gender, preview_url}]}` (`preview_url` absolute,
   `CONFIG_PHONEBLOCK_OTA_BASE_URL` + `audio/` + `preview`).

5. **Own set.** The user's own set replaces the CDN set and is never touched
   by the daily sync, like the own announcement:
   - `POST /api/dialog` — body a complete set file; refused with a visible
     message unless `dialog_header_parse()` accepts it (every phase at least
     one clip, at most `DIALOG_MAX_CLIPS`) and it fits
     `storage_dialog_budget()`; needs `ab_mode=dialog`. Stored as the set of
     the pseudo-locale `own` (index line `own`), the CDN set removed.
   - `GET /api/dialog` — streams the current set (own or CDN), so the browser
     can edit it and the user can save it as a file.
   - `POST /api/dialog/reset` — drops the own set; the next sync (triggered at
     once) fetches the chosen speaker's set.
   - Choosing a CDN speaker in the UI while an own set exists asks first (only
     one set fits on flash) and then resets.
   - Leaving dialog mode deletes the set as today — own sets included; the UI
     says so and offers to download it first.
   - The own set is language-independent: a ui_lang change keeps it.

6. **Editor in the device web UI** (`main/web/index.html`, dialog mode only).
   Not on phoneblock.net: an HTTPS page cannot send to `http://<device>`.
   - Speaker list from `/api/dialog/speakers`, each with a play button
     (`<audio src=preview_url>`, no CORS needed), plus "Own set" when present.
   - "Own set" editor: starts from `GET /api/dialog`, split into its clips by
     the header. Five phase lists; per clip play / replace / delete / move up
     and down; "add" per phase. Files are decoded with the existing
     `convertToAlaw()` path (decodeAudioData → 8 kHz OfflineAudioContext),
     then trimmed and level-normalized like `tts-dialog.py` (port its
     `trim()`/`normalize()` constants), then A-law encoded.
   - The browser builds the set file (header layout of `dialog.h`, clips in
     phase order) and shows a gauge "x of y KB" against the budget from
     `/api/status`; upload is disabled with a reason while a phase is empty,
     a phase has more than `DIALOG_MAX_CLIPS` clips or the set is over budget.
   - "Download set" saves the current set as `.bin`; "Upload set file" loads
     one into the editor.
   - Microphone recording is not offered: browsers allow it on HTTPS pages
     only.
   - A visible hint: never record "Ja"/"yes" — a recorded yes can be abused
     as consent.

## Out of scope

More German speakers (only `speakers.json` + `tts-dialog.py`, no code), a
transcript per own clip, wideband sets.
