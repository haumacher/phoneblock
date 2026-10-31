# Dongle localized assets (issue #460)

Source of truth for the dongle's localized **answer-bot announcement audio**,
the **dialog clips** of the interactive answer bot, **status-mail strings**, and
**web-UI strings**. The firmware carries no
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
  it verbatim (a `.wav`/`.mp3`/`.m4a`/`.flac` is converted with ffmpeg). When
  both a `.alaw` and a source recording (e.g. `announcement-<lang>.mp3`) are
  committed, the `.alaw` is used verbatim and the source is kept only for
  re-encoding. A language with no recording ships **text-only** (the device
  answers silently).
- `audio/announcement-<lang>.txt` — the **transcript of the recording next to
  it**, co-located with the `.alaw`. Keep it in lock-step with what the audio
  actually says; it's the source when re-recording / re-synthesizing and the
  reference for translating the spoken text to a new language. Not consumed by
  the build.
- `audio/dialog/<lang>/<phase>-<n>.alaw` — the **dialog clips** of the
  interactive answer bot (#429, `ab_mode=dialog`): raw A-law like the
  announcement, one short sentence each, with a `<phase>-<n>.txt` transcript
  next to it (same rules as the announcement's). The five phases are `hello`,
  `who-is-there`, `am-aparat`, `question` and `cant-hear-you` (the flow is in
  `main/dialog.h`); each has 1..8 numbered clips (`question-1.alaw`,
  `question-2.alaw`, …), of which the device picks one at random, never the
  same twice in a row. Adding a variant is adding a file and republishing — no
  firmware change. `i18n-assets.sh` concatenates a locale's clips (phase
  order, clip order) into one published file, `audio/dialog-<lang>.alaw`. A locale is published only with **all five** phases; until
  a language is recorded (TTS is a manual step), devices fall back along
  `ui_lang → en → de` to the German set.
- `l10n/` — the **translation project** (see "Translating" below):
  - `mail/mail_<lang>.arb` — status-mail strings. `mail_de.arb` is the single
    normative German source (edit it directly); the other `mail_<lang>.arb` are
    its translations. Its English translation `mail_en.arb` is baked into the
    firmware as the offline fallback (stripped to JSON at build,
    `main/CMakeLists.txt`), consumed by `main/mail_i18n.c`. Values use ICU
    `{name}` placeholders (never `printf` specifiers) and may contain `<b>` tags.
  - `ui/ui_<lang>.arb` — web-UI strings. `ui_de.arb` is the single normative
    German UI source (edit it directly — nothing is duplicated in
    `index.html`); the other `ui_<lang>.arb` are its translations. Its English
    translation `ui_en.arb` is baked into the firmware as the offline fallback
    (most widely understood; stripped to JSON at build, `main/CMakeLists.txt`)
    and served at `/api/i18n/ui` whenever the active locale's pack is missing.

## Translating (development time)

Reuses `de.haumacher:auto-translate-arb` — the **same Gradle plugin the mobile
app uses** (`phoneblock_mobile`). Outputs are committed; no translation runs at
release.

The DeepL key is supplied the **Gradle** way — `serverId = 'deepl'` in the
`build.gradle` files names the Gradle property `deepl.apiKey`, which belongs in
your user-global `~/.gradle/gradle.properties` (never in a committed
`gradle.properties`):

```properties
deepl.apiKey=YOUR_DEEPL_API_KEY
```

`DEEPL_API_KEY` in the environment works as a fallback. This is a *different*
credential store from the Maven-side translation of the web app's
`Messages_*.properties`, which reads the `deepl` server from
`~/.m2/settings.xml` — the same key, kept in both places.

Needs Gradle 8.x (the plugin's `plugins {}` block does not load under the
Gradle 4.x some distributions still package as `gradle`).

```bash
# Edit the German sources directly: l10n/mail/mail_de.arb and l10n/ui/ui_de.arb.
# Then translate both projects (mail + ui) into every target locale:
cd i18n/l10n && gradle translateArb

# 3. Review + commit the updated l10n/**/*_<lang>.arb.
```

Edit German only: `l10n/mail/mail_de.arb` and `l10n/ui/ui_de.arb`. A new
locale: add it to `targetLangs` in `l10n/mail/build.gradle` +
`l10n/ui/build.gradle` and to `languages.txt`.

## Asset kinds & CDN layout

Assets are **co-located with the firmware release**, under
`firmware/<version>/i18n/` — the device fetches the subtree matching the
version it runs, so an older release in the field is never affected by a newer
one's key changes, and there is a single version axis.

| Kind | Path (under `firmware/<version>/i18n/`) | Source | Fetched by | Integrity |
|------|------|------|-----------|-----------|
| announcement audio | `audio/announcement-<lang>.alaw` | `audio/*.alaw` | firmware | SHA-256 from the manifest |
| mail string pack | `mail/mail-<lang>.json` | `l10n/mail/*.arb` | firmware | SHA-256 from the manifest |
| web-UI pack | `ui/lang-<code>.json` | `l10n/ui/*.arb` | firmware, served at `/api/i18n/ui` | SHA-256 from the manifest |
| dialog clips | `audio/dialog-<lang>.alaw` | `audio/dialog/<lang>/` | firmware, only while `ab_mode=dialog` | SHA-256 from the manifest |

The published packs are the ARB files with their `@key` metadata stripped
(plain key→string JSON).

The dialog clips are an **additive** manifest key: `assets.<lang>.dialog` is
one asset `{path, sha256, bytes}` like the others, plus `phases`, the clip
table — each phase mapped to the `[offset, length]` of its clips within that
file, in clip order. Firmware that predates the dialog ignores it; phases and
fields a firmware does not know are ignored too.

```json
"de": { "announcement": {…}, "mail": {…}, "ui": {…},
        "dialog": { "path": "audio/dialog-de.alaw", "sha256": "…", "bytes": 63903,
                    "phases": { "hello":    [[0, 7431]],
                                "question": [[22292, 7802], [30094, 11146], [41240, 12260]],
                                … } } }
```

One file rather than one per clip: on the dongle's SPIFFS every file costs
index pages and GC relocations, and eight small files drove the storage
simulator (`test/test_storage_sim.c`) into GC failures that one file of the
same size does not.

The device takes the set **as a whole** from the first locale of its
`ui_lang → en → de` chain that has all five phases (never mixed across
languages), stores it as `dialog-<lang>.alaw` plus a `dialog.idx` holding the
clip table, written after the file is in place (`main/dialog_store.h`), and
removes it when `ab_mode` goes back to `announcement` or the chosen locale
changes. In dialog mode the set takes the place of both announcements: the
localized one is neither downloaded nor kept, and switching to dialog deletes
an own recording (a new one can be uploaded in announcement mode only). The
set (~64 KB for German) is a rarely written asset in the storage budget, ahead
of the community blocklist. The whole manifest must stay below the firmware's
8 KB fetch buffer (`MANIFEST_CAP` in `main/i18n_sync.c`).

**The manifest is not signed** — unlike the OTA manifest. The per-asset SHA-256
protects against a truncated or corrupted transfer, not against a hostile CDN:
transport trust is HTTPS plus the cert bundle, and the worst a substituted
bundle achieves is wrong text or a wrong announcement. That is a deliberate
trade for the development loop below, which would otherwise need the release
key for every string change. Consequence for anyone consuming a pack: treat the
strings as untrusted input — never interpolate one into HTML unescaped (see how
`status.info` is assembled in `main/web/index.html`).

## Publishing (release time)

Assets ship **with the firmware, in one step**: `scripts/release.sh` calls
`i18n-assets.sh` for the release version, publishing the `.bin` and its
co-located i18n bundle together. To (re)publish assets on their own:

```bash
# Dry run: assemble locally, print the upload commands.
../i18n-assets.sh --dry-run

# Standalone real publish for a firmware version:
../i18n-assets.sh --version 1.5.0
```

`--version` MUST equal what the device reports as `firmware_version`, minus its
git-describe dev suffix. Omit it and the script derives exactly that from the
workspace (same `git describe` + same normalization as the firmware), which is
what makes the dev loop below work. No credential beyond CDN ssh access is
needed — no translation and no signing run here. A missing pack (UI or mail)
degrades to the firmware's embedded English fallback for that surface.

## Testing an unreleased bundle on a device

The device asks for **its own version's** bundle, so a dev build looks for the
tag it was built from — not for the release you have in mind. `git describe`
strips only the `-<N>-g<hash>[-dirty]` suffix, so a build 49 commits past
`dongle-v1.5.2` asks for `1.5.2/i18n/`, and a bundle published as `1.6.0-rc1`
would simply be ignored.

So make the tag say what you want **before building**, then publish bare:

```bash
git tag dongle-v1.6.0-beta1              # local is enough; do not push a
                                         # throwaway tag that could collide
                                         # with the real release tag
cd phoneblock-dongle/firmware
./scripts/i18n-assets.sh                 # derives 1.6.0-beta1 from the tag
idf.py reconfigure && idf.py build       # reconfigure: PROJECT_VER is computed
                                         # at configure time and a new tag
                                         # changes no file, so a plain build
                                         # would silently keep the old version
```

One bundle then covers the whole development period — the commit count and
`-dirty` are stripped, so every later build on that line resolves to the same
tag. `git tag -d dongle-v1.6.0-beta1` when done.
