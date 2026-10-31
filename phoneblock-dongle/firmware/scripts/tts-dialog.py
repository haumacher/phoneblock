#!/usr/bin/env python3
"""Speak the answer-bot dialog sentences into the dongle's clip files.

Reads i18n/l10n/dialog/dialog_<lang>.arb (German source + DeepL translations,
see i18n/README.md) and writes, for every key <phase>_<n>,

    i18n/audio/dialog/<lang>/<phase>-<n>.mp3    the master: ElevenLabs' 44.1 kHz
                                                output re-encoded to MASTER_FFMPEG
                                                (24 kHz mono, 48 kbit/s)
    i18n/audio/dialog/<lang>/<phase>-<n>.alaw   converted locally with ffmpeg:
                                                raw G.711 A-law, 8 kHz mono
    i18n/audio/dialog/<lang>/<phase>-<n>.txt    the sentence that was spoken

through the ElevenLabs text-to-speech API. The master is kept so a different
device encoding (trimming, loudness, a wideband codec) is a local re-run of
--convert-only, not new API credits. The .txt is the transcript and the cache
key: a clip is only (re)generated when its sentence changed, so a run after
editing one sentence costs one request. Clips whose key is gone from the
ARB are deleted. scripts/i18n-assets.sh publishes the result.

Usage:
    scripts/tts-dialog.py --list-voices
    scripts/tts-dialog.py [--langs "de en"] [--force] [--dry-run]
    scripts/tts-dialog.py --convert-only [--langs "de en"]

Credentials and voice, looked up in this order:
    ELEVENLABS_API_KEY / ELEVENLABS_VOICE_ID / ELEVENLABS_MODEL environment
    variables, else elevenlabs.apiKey / elevenlabs.voiceId / elevenlabs.model
    in ~/.gradle/gradle.properties (next to deepl.apiKey, never committed).
    The model defaults to eleven_multilingual_v2.
"""
import argparse
import json
import math
import os
import re
import shutil
import struct
import subprocess
import sys
import time
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
FIRMWARE = os.path.dirname(HERE)
L10N = os.path.join(FIRMWARE, "i18n", "l10n", "dialog")
AUDIO = os.path.join(FIRMWARE, "i18n", "audio", "dialog")
LANGUAGES = os.path.join(FIRMWARE, "i18n", "languages.txt")
API = "https://api.elevenlabs.io/v1"

# Most bytes the minimum set of a locale (clip 1 of every phase) may have:
# the smallest device tier i18n-assets.sh publishes ("s", for the 640 KB
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


def to_alaw(master, out):
    """The device format from the master: ffmpeg resamples to 8 kHz mono
    16-bit PCM; then silence is trimmed, the level normalized and the result
    encoded as raw A-law."""
    raw = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", master,
                          "-ar", "8000", "-ac", "1", "-f", "s16le", "-"],
                         check=True, stdout=subprocess.PIPE).stdout
    pcm = list(struct.unpack(f"<{len(raw) // 2}h", raw[:len(raw) // 2 * 2]))
    pcm = normalize(trim(pcm))
    tmp = out + ".tmp"
    with open(tmp, "wb") as f:
        f.write(bytes(linear_to_alaw(v) for v in pcm))
    os.replace(tmp, out)


def check_budget(lang):
    """True if lang's minimum set (clip 1 of every phase) fits MAX_SET_BYTES."""
    d = os.path.join(AUDIO, lang)
    total = sum(os.path.getsize(os.path.join(d, n)) for n in os.listdir(d)
                if n.endswith(".alaw"))
    minimum = sum(os.path.getsize(os.path.join(d, f"{ph.replace('_', '-')}-1.alaw"))
                  for ph in PHASES
                  if os.path.exists(os.path.join(d, f"{ph.replace('_', '-')}-1.alaw")))
    print(f"{lang}: all clips {total} B ({total / 8000:.1f} s), "
          f"minimum set {minimum} B of {MAX_SET_BYTES} B")
    if minimum > MAX_SET_BYTES:
        print(f"{lang}: minimum set too large for the smallest device tier")
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


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--langs", help="space-separated locales (default: languages.txt)")
    ap.add_argument("--force", action="store_true", help="regenerate every clip")
    ap.add_argument("--dry-run", action="store_true", help="show what would be generated")
    ap.add_argument("--list-voices", action="store_true", help="list the account's voices")
    ap.add_argument("--convert-only", action="store_true",
                    help="re-create every .alaw from its .mp3 master, no API calls")
    a = ap.parse_args()

    if not a.list_voices and not a.dry_run and not shutil.which("ffmpeg"):
        sys.exit("ffmpeg needed to convert the masters (e.g. sudo apt install ffmpeg)")
    over = []
    if a.convert_only:
        langs = a.langs.split() if a.langs else languages()
        n = 0
        for lang in langs:
            d = os.path.join(AUDIO, lang)
            for name in sorted(os.listdir(d)) if os.path.isdir(d) else []:
                if name.endswith(".mp3"):
                    to_alaw(os.path.join(d, name), os.path.join(d, name[:-4] + ".alaw"))
                    n += 1
            if os.path.isdir(d) and not check_budget(lang):
                over.append(lang)
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

    voice = setting("ELEVENLABS_VOICE_ID", "elevenlabs.voiceId")
    model = setting("ELEVENLABS_MODEL", "elevenlabs.model", "eleven_multilingual_v2")
    if not voice and not a.dry_run:
        sys.exit("no voice: set ELEVENLABS_VOICE_ID or elevenlabs.voiceId (see --list-voices)")

    langs = a.langs.split() if a.langs else languages()
    made = kept = removed = 0
    for lang in langs:
        if not os.path.exists(os.path.join(L10N, f"dialog_{lang}.arb")):
            print(f"{lang}: no dialog_{lang}.arb, skipped")
            continue
        clips = clips_of(lang)
        out_dir = os.path.join(AUDIO, lang)
        os.makedirs(out_dir, exist_ok=True)
        wanted = set()
        for (ph, n), text in sorted(clips.items(), key=lambda c: (PHASES.index(c[0][0]), c[0][1])):
            base = os.path.join(out_dir, f"{ph.replace('_', '-')}-{n}")
            wanted.update((base + ".mp3", base + ".alaw", base + ".txt"))
            try:
                with open(base + ".txt", encoding="utf-8") as f:
                    same = f.read().strip() == text
            except OSError:
                same = False
            if same and os.path.exists(base + ".mp3") and not a.force:
                if not os.path.exists(base + ".alaw") and not a.dry_run:
                    to_alaw(base + ".mp3", base + ".alaw")
                kept += 1
                continue
            print(f"{lang}: {os.path.basename(base)}: {text}")
            made += 1
            if a.dry_run:
                continue
            audio = request(f"/text-to-speech/{voice}?output_format=mp3_44100_128", key,
                            {"text": text, "model_id": model})
            if len(audio) < 1600:     # < 0.1 s at 128 kbit/s: not speech
                sys.exit(f"{lang}/{ph}_{n}: suspiciously short audio ({len(audio)} bytes)")
            with open(base + ".dl.mp3", "wb") as f:
                f.write(audio)
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", base + ".dl.mp3",
                            *MASTER_FFMPEG, "-f", "mp3", base + ".mp3.tmp"], check=True)
            os.remove(base + ".dl.mp3")
            os.replace(base + ".mp3.tmp", base + ".mp3")
            to_alaw(base + ".mp3", base + ".alaw")
            with open(base + ".txt", "w", encoding="utf-8") as f:
                f.write(text + "\n")
        for name in os.listdir(out_dir):
            path = os.path.join(out_dir, name)
            if path not in wanted and name.endswith((".mp3", ".alaw", ".txt")):
                print(f"{lang}: {name}: no longer in the ARB, removed")
                if not a.dry_run:
                    os.remove(path)
                removed += 1
        if not a.dry_run and not check_budget(lang):
            over.append(lang)
    verb = "would generate" if a.dry_run else "generated"
    print(f"{verb} {made}, unchanged {kept}, removed {removed}")
    if over:
        sys.exit(f"over budget: {' '.join(over)} — shorten the first sentence of a phase in dialog_de.arb")


if __name__ == "__main__":
    main()
