#!/usr/bin/env python3
"""Speak the answer-bot dialog sentences into the dongle's clip files.

Reads i18n/l10n/dialog/dialog_<lang>.arb (German source + DeepL translations,
see i18n/README.md) and, for every speaker of i18n/audio/dialog/speakers.json
and every key <phase>_<n>, writes into i18n/audio/dialog/<lang>/<speaker>/

    <phase>-<n>.txt    the sentence that was spoken            (committed)
    voice.json         the voice, model and tempo that spoke    (committed)
    <phase>-<n>.mp3    the master: ElevenLabs' 44.1 kHz output re-encoded to
                       MASTER_FFMPEG (24 kHz mono, 48 kbit/s)   (on the CDN)
    <phase>-<n>.alaw   converted locally with ffmpeg: raw G.711 A-law,
                       8 kHz mono                               (local only)

through the ElevenLabs text-to-speech API. Every speaker is one voice in all
languages (the premade and multilingual library voices speak them all).

The masters are what costs API credits, and with several speakers they are too
large for git: --upload copies them with their .txt and voice.json to the CDN
(CDN_TTS below, named like the local files: <lang>/<speaker>/question-14.mp3),
--fetch brings them into a fresh checkout. A different device encoding
(trimming, loudness, a wideband codec) is a local re-run of --convert-only,
not new API credits.

The .txt is the transcript and the cache key: a clip is only (re)generated
when its sentence changed, so a run after editing one sentence costs one
request per speaker. A speaker whose voice.json records another voice or
model is regenerated as a whole. An interrupted run resumes with the clips
still missing. Clips whose key is gone from the ARB are deleted.
scripts/audio-assets.sh publishes the result.

Usage:
    scripts/tts-dialog.py --list-voices
    scripts/tts-dialog.py [--langs "de en"] [--speakers "roger"] [--force] [--dry-run]
    scripts/tts-dialog.py --convert-only [--langs ...] [--speakers ...]
    scripts/tts-dialog.py --upload | --fetch [--langs ...] [--speakers ...]

The API key comes from the ELEVENLABS_API_KEY environment variable, else
elevenlabs.apiKey in ~/.gradle/gradle.properties (next to deepl.apiKey, never
committed). A speaker's voice is a name from --list-voices (the part before
" - ", case-insensitive) or a voice ID; it must be a premade voice, one in the
account's library, or a voice ID from the shared library. The CDN account is
CDN_HOST (default haumac@cdn.phoneblock.net, as audio-assets.sh).
"""
import argparse
import filecmp
import json
import math
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
FIRMWARE = os.path.dirname(HERE)
L10N = os.path.join(FIRMWARE, "i18n", "l10n", "dialog")
AUDIO = os.path.join(FIRMWARE, "i18n", "audio", "dialog")
SPEAKERS = os.path.join(AUDIO, "speakers.json")
LANGUAGES = os.path.join(FIRMWARE, "i18n", "languages.txt")
API = "https://api.elevenlabs.io/v1"

CDN_HOST = os.environ.get("CDN_HOST", "haumac@cdn.phoneblock.net")
CDN_BASE = os.environ.get("CDN_BASE", "/public_html/cdn/dongle")
# The masters: https://cdn.phoneblock.net/dongle/firmware/audio/tts/<lang>/<speaker>/.
# Outside the audio/v<N> channels: they are the source, not a device format.
CDN_TTS = CDN_BASE + "/firmware/audio/tts"
SSH_OPTS = ["-o", "BatchMode=yes", "-o", "ConnectTimeout=20"]

# The model all speakers speak with.
MODEL = "eleven_v4"
# Speed-up applied locally when converting a master (ffmpeg atempo, pitch
# kept): eleven_v4 speaks ~30 % slower than eleven_multilingual_v2 and ignores
# the API's voice_settings.speed. 1.25 brings it back to about the v2 pace.
# Recorded per speaker in voice.json; changing it only re-converts, no API
# requests.
TEMPO = 1.25

# Most bytes the minimum set of a locale (clip 1 of every phase) may have:
# the smallest device tier audio-assets.sh publishes ("s", for the 640 KB
# storage partition of the 4 MB boards, see test/test_storage_sim.c). Every
# further clip goes into the larger tiers by priority, so only the minimum
# set is a hard limit here.
MAX_SET_BYTES = 128 * 1024

# Trimming: silence below this level at either end of a clip is cut (TTS output
# carries up to ~0.5 s of it, which also delays the bot's turn), keeping a
# short margin so the first and last syllable stay intact.
TRIM_DBFS = -45
TRIM_KEEP_MS = 50

# Level: every clip normalized to the same active speech level (TTS output
# spreads 5-8 dB between sentences), about the announcements' level, with
# the gain capped so peaks stay below PEAK_DBFS (no clipping).
TARGET_DBFS = -20
PEAK_DBFS = -1
ACTIVE_DBFS = -50     # 20 ms frames below this count as pause, not speech

# The committed master format: plenty for any telephony encoding (wideband
# tops out at 16 kHz), a third of the size of ElevenLabs' 44.1 kHz/128 kbit/s.
MASTER_FFMPEG = ["-ar", "24000", "-ac", "1", "-c:a", "libmp3lame", "-b:a", "48k"]

PHASES = ("hello", "who_is_there", "am_aparat", "question", "cant_hear_you")
MAX_CLIPS = 32  # DIALOG_MAX_CLIPS in main/dialog.h
KEY_RE = re.compile(r"^(%s)_([1-9][0-9]*)$" % "|".join(PHASES))


def gradle_property(name):
    path = os.path.expanduser("~/.gradle/gradle.properties")
    try:
        with open(path, encoding="utf-8") as f:
            for line in f:
                k, sep, v = line.partition("=")
                if sep and k.strip() == name:
                    return v.strip()
    except OSError:
        pass
    return None


def setting(env, prop, default=None):
    return os.environ.get(env) or gradle_property(prop) or default


def recorded_speaker(out_dir):
    """The locale's voice.json, or {} if there is none."""
    try:
        with open(os.path.join(out_dir, "voice.json"), encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return {}


def voice_id(voice, key):
    """The ID of `voice`, a name from --list-voices or already an ID."""
    voices = json.loads(request("/voices", key))["voices"]
    for v in voices:
        if voice == v["voice_id"]:
            return voice
    want = voice.strip().casefold()
    hits = [v for v in voices
            if v["name"].casefold() == want or v["name"].split(" - ")[0].strip().casefold() == want]
    if len(hits) != 1:
        names = ", ".join(v["name"].split(" - ")[0] for v in voices)
        sys.exit(f"voice {voice!r}: {'ambiguous' if hits else 'not found'} (account has: {names})")
    return hits[0]["voice_id"]


def languages():
    with open(LANGUAGES, encoding="utf-8") as f:
        return [l.strip() for l in f if l.strip() and not l.startswith("#")]


def request(path, key, body=None):
    req = urllib.request.Request(API + path, headers={"xi-api-key": key})
    if body is not None:
        req.data = json.dumps(body).encode()
        req.add_header("Content-Type", "application/json")
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            detail = e.read().decode(errors="replace")[:300]
            if e.code == 429 and attempt < 3:      # rate limit: back off
                time.sleep(2 ** attempt * 5)
                continue
            sys.exit(f"ElevenLabs {path}: HTTP {e.code}: {detail}")
        except urllib.error.URLError as e:
            sys.exit(f"ElevenLabs {path}: {e.reason}")


def linear_to_alaw(sample):
    """G.711 A-law encode of one 16-bit linear sample."""
    sign = 0x80 if sample >= 0 else 0x00
    if sample < 0:
        sample = -sample - 1
    sample = min(sample, 32635) >> 3
    if sample >= 32:
        exp = sample.bit_length() - 5
        code = (exp << 4) | ((sample >> exp) & 0x0F)
    else:
        code = sample >> 1
    return (code | sign) ^ 0x55


def dbfs(x):
    return 20 * math.log10(max(x, 1e-9) / 32768)


def trim(pcm):
    """The clip without leading/trailing silence (10 ms windows below
    TRIM_DBFS), keeping TRIM_KEEP_MS on each side."""
    win = 80                                   # 10 ms at 8 kHz
    thr = 32768 * 10 ** (TRIM_DBFS / 20)
    loud = [i for i in range(0, len(pcm), win) if max(map(abs, pcm[i:i + win])) > thr]
    if not loud:
        return pcm
    keep = TRIM_KEEP_MS * 8
    return pcm[max(0, loud[0] - keep):min(len(pcm), loud[-1] + win + keep)]


def active_level(pcm):
    """RMS of the 20 ms frames above ACTIVE_DBFS, in dBFS — the speech level
    without the pauses between words."""
    thr = 32768 * 10 ** (ACTIVE_DBFS / 20)
    frames = [pcm[i:i + 160] for i in range(0, len(pcm) - 159, 160)]
    act = [f for f in frames if math.sqrt(sum(v * v for v in f) / 160) > thr]
    if not act:
        return None
    return dbfs(math.sqrt(sum(v * v for f in act for v in f) / (160 * len(act))))


def normalize(pcm):
    """Gain to TARGET_DBFS active speech level, capped so no peak exceeds
    PEAK_DBFS: every clip of a set at the same loudness."""
    level = active_level(pcm)
    if level is None:
        return pcm
    peak = dbfs(max(map(abs, pcm)))
    gain_db = min(TARGET_DBFS - level, PEAK_DBFS - peak)
    g = 10 ** (gain_db / 20)
    return [max(-32768, min(32767, round(v * g))) for v in pcm]


def to_alaw(master, out, tempo):
    """The device format from the master: ffmpeg speeds it up by `tempo` and
    resamples to 8 kHz mono 16-bit PCM; then silence is trimmed, the level
    normalized and the result encoded as raw A-law."""
    tempo_filter = ["-af", f"atempo={tempo}"] if tempo != 1 else []
    raw = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", master, *tempo_filter,
                          "-ar", "8000", "-ac", "1", "-f", "s16le", "-"],
                         check=True, stdout=subprocess.PIPE).stdout
    pcm = list(struct.unpack(f"<{len(raw) // 2}h", raw[:len(raw) // 2 * 2]))
    pcm = normalize(trim(pcm))
    tmp = out + ".tmp"
    with open(tmp, "wb") as f:
        f.write(bytes(linear_to_alaw(v) for v in pcm))
    os.replace(tmp, out)




def check_budget(d, label):
    """True if the minimum set in d (clip 1 of every phase) fits MAX_SET_BYTES."""
    total = sum(os.path.getsize(os.path.join(d, n)) for n in os.listdir(d)
                if n.endswith(".alaw"))
    minimum = sum(os.path.getsize(os.path.join(d, f"{ph.replace('_', '-')}-1.alaw"))
                  for ph in PHASES
                  if os.path.exists(os.path.join(d, f"{ph.replace('_', '-')}-1.alaw")))
    print(f"{label}: all clips {total} B ({total / 8000:.1f} s), "
          f"minimum set {minimum} B of {MAX_SET_BYTES} B")
    if minimum > MAX_SET_BYTES:
        print(f"{label}: minimum set too large for the smallest device tier")
        return False
    return True


def clips_of(lang):
    """{(phase, n): sentence} from dialog_<lang>.arb; exits on a malformed set."""
    path = os.path.join(L10N, f"dialog_{lang}.arb")
    with open(path, encoding="utf-8") as f:
        arb = json.load(f)
    clips = {}
    for k, v in arb.items():
        if k.startswith("@"):
            continue
        m = KEY_RE.match(k)
        if not m:
            sys.exit(f"{path}: unexpected key '{k}' (want <phase>_<n>, phase one of {PHASES})")
        if not isinstance(v, str) or not v.strip():
            sys.exit(f"{path}: empty sentence for '{k}'")
        clips[(m.group(1), int(m.group(2)))] = v.strip()
    for ph in PHASES:
        nums = sorted(n for p, n in clips if p == ph)
        if not nums:
            sys.exit(f"{path}: phase '{ph}' has no sentence")
        if nums != list(range(1, len(nums) + 1)) or len(nums) > MAX_CLIPS:
            sys.exit(f"{path}: phase '{ph}' must be numbered 1..n, n <= {MAX_CLIPS} (got {nums})")
    return clips


SPEAKER_ID_RE = re.compile(r"^[a-z][a-z0-9-]{0,15}$")


def speakers():
    """The speaker table: [{id, voice, gender}], the default first."""
    with open(SPEAKERS, encoding="utf-8") as f:
        table = json.load(f)
    rows = table["speakers"]
    ids = [s["id"] for s in rows]
    for s in rows:
        if not SPEAKER_ID_RE.match(s["id"]) or s.get("gender") not in ("female", "male"):
            sys.exit(f"{SPEAKERS}: bad speaker {s}")
    if len(set(ids)) != len(ids) or table.get("default") not in ids:
        sys.exit(f"{SPEAKERS}: duplicate ids or default not among {ids}")
    return sorted(rows, key=lambda s: s["id"] != table["default"])


def selected(a):
    langs = a.langs.split() if a.langs else languages()
    rows = speakers()
    if a.speakers:
        want = a.speakers.split()
        unknown = set(want) - {s["id"] for s in rows}
        if unknown:
            sys.exit(f"unknown speaker(s) {' '.join(sorted(unknown))}, see {SPEAKERS}")
        rows = [s for s in rows if s["id"] in want]
    return langs, rows


def sftp(batch):
    """Runs an sftp batch on CDN_HOST ('-' prefixed lines may fail)."""
    r = subprocess.run(["sftp", *SSH_OPTS, "-b", "-", CDN_HOST], input=batch,
                       text=True, capture_output=True)
    if r.returncode != 0:
        sys.exit(f"sftp {CDN_HOST}: {r.stderr.strip() or r.stdout.strip()}")


def upload(langs, rows):
    """Copies masters, transcripts and voice.json of every complete speaker
    directory to CDN_TTS (the next --upload repeats whatever an interrupted
    one did not finish; files are replaced in place)."""
    lines = []
    for p in ("/firmware", "/firmware/audio", "/firmware/audio/tts"):
        lines.append(f"-mkdir {CDN_BASE}{p}")
    n = 0
    for lang in langs:
        lines.append(f"-mkdir {CDN_TTS}/{lang}")
        for s in rows:
            d = os.path.join(AUDIO, lang, s["id"])
            if not os.path.exists(os.path.join(d, "voice.json")):
                continue
            remote = f"{CDN_TTS}/{lang}/{s['id']}"
            lines.append(f"-mkdir {remote}")
            names = sorted(x for x in os.listdir(d)
                           if x.endswith(".txt") and os.path.exists(os.path.join(d, x[:-4] + ".mp3")))
            # Master before transcript: a transcript on the CDN vouches for its master.
            for x in names:
                lines.append(f'put "{os.path.join(d, x[:-4] + ".mp3")}" {remote}/{x[:-4]}.mp3')
                lines.append(f'put "{os.path.join(d, x)}" {remote}/{x}')
            lines.append(f'put "{os.path.join(d, "voice.json")}" {remote}/voice.json')
            n += len(names)
            print(f"{lang}/{s['id']}: {len(names)} masters")
    sftp("\n".join(lines) + "\n")
    print(f"uploaded {n} masters to {CDN_HOST}:{CDN_TTS}")


def fetch(langs, rows):
    """Brings the CDN's master of every clip whose local transcript and
    voice.json match the CDN's, and converts it. Returns the number fetched."""
    n = 0
    for lang in langs:
        for s in rows:
            d = os.path.join(AUDIO, lang, s["id"])
            if not os.path.exists(os.path.join(d, "voice.json")):
                continue
            with tempfile.TemporaryDirectory() as tmp:
                r = subprocess.run(["sftp", *SSH_OPTS, "-b", "-", CDN_HOST],
                                   input=f"get {CDN_TTS}/{lang}/{s['id']}/* {tmp}/\n",
                                   text=True, capture_output=True)
                if r.returncode != 0 or not os.path.exists(os.path.join(tmp, "voice.json")):
                    print(f"{lang}/{s['id']}: nothing on the CDN")
                    continue
                key = lambda v: (v.get("voice_id"), v.get("model"))
                if key(recorded_speaker(tmp)) != key(recorded_speaker(d)):
                    print(f"{lang}/{s['id']}: the CDN has another voice or model, not fetched")
                    continue
                tempo = recorded_speaker(d).get("tempo", 1)
                got = 0
                for x in sorted(os.listdir(d)):
                    if not x.endswith(".txt"):
                        continue
                    mp3 = x[:-4] + ".mp3"
                    if os.path.exists(os.path.join(d, mp3)):
                        continue
                    remote_txt = os.path.join(tmp, x)
                    if (os.path.exists(remote_txt) and os.path.exists(os.path.join(tmp, mp3))
                            and filecmp.cmp(remote_txt, os.path.join(d, x), shallow=False)):
                        shutil.copyfile(os.path.join(tmp, mp3), os.path.join(d, mp3))
                        to_alaw(os.path.join(d, mp3), os.path.join(d, x[:-4] + ".alaw"), tempo)
                        got += 1
                print(f"{lang}/{s['id']}: fetched {got}")
                n += got
    return n


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--langs", help="space-separated locales (default: languages.txt)")
    ap.add_argument("--speakers", help="space-separated speaker ids (default: speakers.json)")
    ap.add_argument("--force", action="store_true", help="regenerate every clip")
    ap.add_argument("--dry-run", action="store_true", help="show what would be generated")
    ap.add_argument("--list-voices", action="store_true", help="list the account's voices")
    ap.add_argument("--convert-only", action="store_true",
                    help="re-create every .alaw from its .mp3 master, no API calls")
    ap.add_argument("--upload", action="store_true", help="copy the masters to the CDN")
    ap.add_argument("--fetch", action="store_true",
                    help="copy missing masters from the CDN and convert them")
    a = ap.parse_args()

    if not a.list_voices and not a.dry_run and not a.upload and not shutil.which("ffmpeg"):
        sys.exit("ffmpeg needed to convert the masters (e.g. sudo apt install ffmpeg)")
    langs, rows = selected(a)
    if a.upload:
        upload(langs, rows)
        return
    if a.fetch:
        print(f"fetched {fetch(langs, rows)}")
        return
    over = []
    if a.convert_only:
        n = 0
        for lang in langs:
            for s in rows:
                d = os.path.join(AUDIO, lang, s["id"])
                if not os.path.isdir(d):
                    continue
                tempo = recorded_speaker(d).get("tempo", 1)
                for name in sorted(os.listdir(d)):
                    if name.endswith(".mp3"):
                        to_alaw(os.path.join(d, name), os.path.join(d, name[:-4] + ".alaw"), tempo)
                        n += 1
                if not check_budget(d, f"{lang}/{s['id']}"):
                    over.append(f"{lang}/{s['id']}")
        print(f"converted {n}")
        if over:
            sys.exit(f"over budget: {' '.join(over)} — shorten the first sentence of a phase in dialog_de.arb")
        return

    key = setting("ELEVENLABS_API_KEY", "elevenlabs.apiKey")
    if not key and not a.dry_run:
        sys.exit("no API key: set ELEVENLABS_API_KEY or elevenlabs.apiKey in ~/.gradle/gradle.properties")

    if a.list_voices:
        voices = json.loads(request("/voices", key))["voices"]
        for v in voices:
            print(f"{v['voice_id']}  {v.get('category', ''):12}  {v['name']}")
        return

    made = kept = removed = missing = 0
    for s in rows:
        voice = voice_id(s["voice"], key) if key else s["voice"]   # --dry-run without a key
        speaker = {"voice": s["voice"], "voice_id": voice, "model": MODEL, "tempo": TEMPO}
        for lang in langs:
            if not os.path.exists(os.path.join(L10N, f"dialog_{lang}.arb")):
                print(f"{lang}: no dialog_{lang}.arb, skipped")
                continue
            clips = clips_of(lang)
            label = f"{lang}/{s['id']}"
            out_dir = os.path.join(AUDIO, lang, s["id"])
            os.makedirs(out_dir, exist_ok=True)
            wanted = {os.path.join(out_dir, "voice.json")}
            was = recorded_speaker(out_dir)
            # Spoken by another voice or model: none of the clips counts.
            respeak = (was.get("voice_id"), was.get("model")) != (voice, MODEL)
            if respeak and not a.dry_run:
                for name in os.listdir(out_dir):
                    if name.endswith((".mp3", ".alaw", ".txt")):
                        os.remove(os.path.join(out_dir, name))
                was = {}
            # Recorded up front: every clip with a transcript is then this
            # speaker's, and an interrupted run resumes where it stopped.
            if not a.dry_run:
                with open(os.path.join(out_dir, "voice.json"), "w", encoding="utf-8") as f:
                    json.dump(speaker, f, indent=2)
                    f.write("\n")
            # Same masters, other speed-up: re-convert the kept clips.
            reconvert = was.get("tempo", 1) != TEMPO
            for (ph, n), text in sorted(clips.items(), key=lambda c: (PHASES.index(c[0][0]), c[0][1])):
                base = os.path.join(out_dir, f"{ph.replace('_', '-')}-{n}")
                wanted.update((base + ".mp3", base + ".alaw", base + ".txt"))
                try:
                    with open(base + ".txt", encoding="utf-8") as f:
                        same = f.read().strip() == text and not respeak
                except OSError:
                    same = False
                if same and not a.force:
                    if not os.path.exists(base + ".mp3"):
                        missing += 1   # spoken, but the master is on the CDN (--fetch)
                    elif (reconvert or not os.path.exists(base + ".alaw")) and not a.dry_run:
                        to_alaw(base + ".mp3", base + ".alaw", TEMPO)
                    kept += 1
                    continue
                print(f"{label}: {os.path.basename(base)}: {text}")
                made += 1
                if a.dry_run:
                    continue
                audio = request(f"/text-to-speech/{voice}?output_format=mp3_44100_128", key,
                                {"text": text, "model_id": MODEL})
                if len(audio) < 1600:     # < 0.1 s at 128 kbit/s: not speech
                    sys.exit(f"{label}/{ph}_{n}: suspiciously short audio ({len(audio)} bytes)")
                with open(base + ".dl.mp3", "wb") as f:
                    f.write(audio)
                subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", base + ".dl.mp3",
                                *MASTER_FFMPEG, "-f", "mp3", base + ".mp3.tmp"], check=True)
                os.remove(base + ".dl.mp3")
                os.replace(base + ".mp3.tmp", base + ".mp3")
                to_alaw(base + ".mp3", base + ".alaw", TEMPO)
                # Last: the transcript marks the clip as done.
                with open(base + ".txt", "w", encoding="utf-8") as f:
                    f.write(text + "\n")
            for name in os.listdir(out_dir):
                path = os.path.join(out_dir, name)
                if path not in wanted and name.endswith((".mp3", ".alaw", ".txt", ".json")):
                    print(f"{label}: {name}: no longer in the ARB, removed")
                    if not a.dry_run:
                        os.remove(path)
                    removed += 1
            if not a.dry_run and not check_budget(out_dir, label):
                over.append(label)
    verb = "would generate" if a.dry_run else "generated"
    print(f"{verb} {made}, unchanged {kept}, removed {removed}")
    if missing:
        print(f"{missing} unchanged clip(s) have no local master: run --fetch")
    if made and not a.dry_run:
        print("new masters: run --upload, then commit the .txt and voice.json files")
    if over:
        sys.exit(f"over budget: {' '.join(over)} — shorten the first sentence of a phase in dialog_de.arb")


if __name__ == "__main__":
    main()
