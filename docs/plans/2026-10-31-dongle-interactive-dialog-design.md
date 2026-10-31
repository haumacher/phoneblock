# Dongle: interactive answer-bot dialog — design (#429)

Port of the dialog part of PR #430 onto the current firmware (platform layer,
CDN-delivered i18n assets, storage budget, Linux build). The call recorder of
PR #430 is split out to #663. PR #430 (branch
`issue-429-dongle-interactive-answerbot-master`) is the *reference
implementation*, not a merge source: its code predates the platform layer and
the #460 i18n decision.

## Decisions

1. **Opt-in mode.** New config `ab_mode` = `announcement` (default) | `dialog`.
   The fleet keeps today's behavior (play the announcement, then BYE) until the
   user switches. Dialog mode and announcements do not coexist on flash:
   switching to `dialog` deletes the user's own recording (after a confirmation
   in the web UI; it can be uploaded again after switching back) and the
   localized announcement is neither downloaded nor kept. Uploading an own
   recording is only possible in announcement mode. If dialog mode is on but
   the clip set is not (yet) on the device, the call ends without audio and a
   WARN names the reason.

2. **Fixed flow, dynamic number of clips.** The state machine is fixed in code
   (as in PR #430):

   ```
   say(hello)
   intro:    await → caller spoke? yes → say(am-aparat), go to question
                                   no  → say(who-is-there)
   question: await → caller spoke? yes → say(question)
   (endless)                       no  → say(cant-hear-you)
   ```

   Each of the five phases (`hello`, `who-is-there`, `am-aparat`, `question`,
   `cant-hear-you`) is a **set of 1..N clips** delivered by the CDN; the firmware
   has no compiled-in count. A phase picks a random clip, avoiding an immediate
   repeat of the previous clip of that phase when N > 1. Adding a variant means
   adding a file and republishing the i18n bundle, never a firmware change.

3. **await()** as in PR #430: if the caller speaks, the turn ends once they have
   been quiet for `vad_min_silence_ms`; if they stay silent, it ends after a fixed
   `AWAIT_TIMEOUT_MS` (3 s, constant). Continuous comfort noise is mixed under
   prompts and pauses so the transitions are seamless.

4. **Call end.** No time limit: the dialog runs until the remote BYE, however
   long the caller keeps talking (wasting the spammer's time is the feature).
   Guard against a call whose BYE never arrives (lost UDP, dead NAT mapping):
   if no RTP packet from the remote arrives for `DIALOG_RX_TIMEOUT_MS` (30 s),
   the dialog ends and the dongle sends its own BYE. Packets are counted before
   decryption, so this also works when the dialog runs deaf.

5. **One file per clip set, streamed from SPIFFS**, frame by frame like the
   announcement — never buffered whole in RAM, never embedded in the firmware
   image (#460: no per-language payload in the firmware). The set (~64 KB for
   the German one) is a rarely-written asset in the storage budget.
   `i18n_sync` downloads it **only while `ab_mode=dialog`** and deletes it when
   the mode is switched off or the locale changes (stale cleanup like
   `announcement-<lang>.alaw`). A single file, not one per clip: the storage
   simulator (`test_storage_sim`) shows that eight small files cost so much
   SPIFFS overhead that the partition GC fails, while one file of the same
   size does not.

6. **Manifest: additive key.** Under `assets.<lang>` a new `dialog` entry is one
   asset `{path, sha256, bytes}` like the others, plus `phases`: phase → array
   of `[offset, length]` clips within that file. Older firmware ignores the
   key. The set is taken **as a whole** from the first locale in the existing
   `ui_lang → en → de` chain that offers a complete set (all five phases
   non-empty), never mixed across languages.

7. **Source layout.** `i18n/audio/dialog/<lang>/<phase>-<n>.alaw` plus a `.txt`
   transcript next to each clip; `scripts/i18n-assets.sh` concatenates them into
   one published file per locale (`firmware/<version>/i18n/audio/dialog-<lang>.alaw`)
   and writes the clip table into the manifest. German comes from PR #430;
   other languages follow once recorded (TTS is a manual step today). Until then
   the locale chain falls back to German, like the announcement fallback.

8. **Two-way audio.** The RTP task receives on the shared RTP socket
   (full duplex); SRTP-RX decrypts with the remote key from the INVITE's
   `a=crypto` (libsrtp 3 API, base64/random through the platform layer); plain
   RTP is parsed directly. Only PCMA (PT 8) feeds the VAD. A missing or
   undecodable RX key logs a WARN and the dialog runs "deaf" (every await times
   out → cant-hear-you loop), it does not abort the call.

9. **VAD** (`vad.c`, pure, host-tested): per-20 ms dBFS, threshold, minimum
   silence, short speech-confirm debounce — as PR #430 / Cloud-AB
   `AlawSilenceTrimmer`. Config: `vad_silence_db`, `vad_min_silence_ms`,
   `comfort_noise_level`. (`padding_ms` of PR #430 is dropped; it belonged to
   recording.)

10. **WiFi power save** is switched to `WIFI_PS_NONE` only **for the duration of
    a dialog call** and restored afterwards (ESP32 only; a no-op on Linux): the
    burst delivery of modem sleep delays the caller's audio to the VAD, but an
    always-on radio heats the small boards (C3 Super Mini ~72 °C).

11. **Testability.** The dialog state machine lives in its own pure module,
    driven by "clip finished" / "VAD verdict" / "tick" events with an injected
    random source, so it is host-tested without RTP. Host tests: VAD, A-law
    decode, dialog state machine, manifest parsing of dialog sets (complete /
    incomplete / fallback chain). The Linux build keeps building.

12. **Web UI.** Mode selection plus the VAD and comfort-noise settings; the
    status shows whether a dialog clip set is present and in which language.
    Strings in `i18n/l10n/ui/ui_de.arb`, translated with `translateArb`.

## Out of scope

- Call recording (#663).
- A configurable flow (steps and transitions set by the user).
- Dialog clips in languages other than German (recording step).
- User-uploaded dialog clips (#664).
