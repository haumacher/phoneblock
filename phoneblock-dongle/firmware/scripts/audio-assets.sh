#!/usr/bin/env bash
#
# Build and publish the dongle's AUDIO CHANNEL (issue #429): the localized
# answer-bot announcements and the dialog clip sets, independent of any
# firmware version. The firmware reads
#
#   <OTA base>/audio/v<AUDIO_FORMAT_VERSION>/manifest.json
#
# (main/audio_manifest.h) — on the CDN firmware/audio/v<N>/ — and re-checks
# it once a day, so new sentences, voices or locales reach the fleet without
# a firmware release. The UI and mail packs stay in the firmware-versioned
# bundle (scripts/i18n-assets.sh): their keys belong to the code.
#
#   ./scripts/audio-assets.sh [options]
#
# The format version N is read from main/audio_manifest.h. Republishing into
# the same v<N> is the normal case; N is raised only for a change the
# firmware in the field could not read (see i18n/README.md, "Audio channel").
#
# Safe to run while devices download:
#  * every file name is content-addressed (<kind>-<lang>[-<tier>]-<sha8>),
#    so an upload never replaces a file of the live manifest;
#  * files are uploaded first, the manifest last, swapped in via sftp's
#    posix-rename (atomic);
#  * then files that neither the new nor the previous manifest references are
#    removed — a device that read the previous manifest a moment ago can
#    still fetch what it names.
#
# Sources (committed): i18n/audio/announcement-<lang>.alaw (or .wav/.mp3/
# .m4a/.flac, converted via ffmpeg) and i18n/audio/dialog/<lang>/
# <phase>-<n>.alaw, for the codes in i18n/languages.txt.
#
# Options:
#   --langs "a b"   Only these codes (default: all in languages.txt). Note:
#                   a published manifest then lists only these.
#   --from-audio D  Take the sources from dir D instead of i18n/audio.
#   --stage DIR     Staging dir (default: a mktemp).
#   --no-upload     Build locally only; the staging dir is kept and printed.
#   --dry-run       --no-upload, and also print (not run) the upload steps.
#   -h | --help     This help.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FW_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
SRC_DIR="${FW_DIR}/i18n"

CDN_HOST="${CDN_HOST:-haumac@cdn.phoneblock.net}"
CDN_BASE="${CDN_BASE:-/public_html/cdn/dongle}"
CDN_FIRMWARE="${CDN_BASE}/firmware"
# Unattended upload: never block on a prompt (as i18n-assets.sh, release.sh).
SSH_OPTS=(-o BatchMode=yes -o ConnectTimeout=20)

AUDIO_DIR="${SRC_DIR}/audio"
ONLY_LANGS=""
STAGE=""
NO_UPLOAD=0
DRY_RUN=0

die() { echo "ERROR: $*" >&2; exit 1; }

[[ -f "${SCRIPT_DIR}/release.settings" ]] && { set -a; . "${SCRIPT_DIR}/release.settings"; set +a; }

while [[ $# -gt 0 ]]; do
    case "$1" in
        --langs)      ONLY_LANGS="$2"; shift 2;;
        --from-audio) AUDIO_DIR="$2"; shift 2;;
        --stage)      STAGE="$2"; shift 2;;
        --no-upload)  NO_UPLOAD=1; shift;;
        --dry-run)    DRY_RUN=1; NO_UPLOAD=1; shift;;
        -h|--help)    sed -n '2,43p' "$0" | sed 's/^# \{0,1\}//'; exit 0;;
        *)            die "unknown option: $1";;
    esac
done

for t in jq sha256sum python3; do
    command -v "$t" >/dev/null || die "$t not found in PATH"
done

fw_define() {  # file name → the #define's value, as an integer
    local v
    v="$(sed -nE "s/^#define[[:space:]]+$2[[:space:]]+\(?([0-9 *]+)u?\)?.*/\1/p" "${FW_DIR}/main/$1" | tr -d 'u ')"
    [[ -n "$v" ]] || die "cannot read $2 from main/$1"
    echo $(( v ))
}
AUDIO_FORMAT="$(fw_define audio_manifest.h AUDIO_FORMAT_VERSION)"
REMOTE="${CDN_FIRMWARE}/audio/v${AUDIO_FORMAT}"
echo "==> audio channel v${AUDIO_FORMAT} (CDN firmware/audio/v${AUDIO_FORMAT})"

[[ -z "$STAGE" ]] && STAGE="$(mktemp -d -t dongle-audio.XXXXXX)"
CHANNEL="${STAGE}/audio/v${AUDIO_FORMAT}"
mkdir -p "$CHANNEL"
echo "==> staging ${STAGE}"

# --- Announcement: the committed recording for <lang> ----------------------
# Prefer a ready ".alaw" (raw G.711 A-law 8 kHz mono, exactly what the device
# streams) used verbatim; otherwise convert a committed wav/mp3/m4a/flac via
# ffmpeg. Recordings live in AUDIO_DIR (i18n/audio, or --from-audio),
# committed to git so you can hand-record / tune each one. Returns non-zero
# when a language has no recording yet — that locale then ships text-only.
get_announcement() {
    local lang="$1" out_alaw="$2"
    if [[ -f "${AUDIO_DIR}/announcement-${lang}.alaw" ]]; then
        cp "${AUDIO_DIR}/announcement-${lang}.alaw" "$out_alaw"; return 0
    fi
    local src=""
    for ext in wav mp3 m4a flac; do
        [[ -f "${AUDIO_DIR}/announcement-${lang}.${ext}" ]] && src="${AUDIO_DIR}/announcement-${lang}.${ext}"
    done
    if [[ -n "$src" ]]; then
        command -v ffmpeg >/dev/null || die "ffmpeg needed to convert ${src}"
        ffmpeg -y -loglevel error -i "$src" -ar 8000 -ac 1 -f alaw "$out_alaw"
        return 0
    fi
    return 1
}

# Dialog clips: the set for <lang> from AUDIO_DIR/dialog/<lang>/, raw A-law
# like the announcement, numbered per phase (<phase>-1.alaw, <phase>-2.alaw,
# ... — the count is free, the header carries it). Published in up to three
# size tiers (main/storage_budget.h; the device takes the largest within its
# partition's budget), each ONE file dialog-<lang>-<tier>-<sha8>.bin —
# a single SPIFFS object on the device instead of one per clip (see
# test_storage_sim.c) — holding the clip table as a header (format in
# main/dialog.h), then the clips in phase and clip order:
#
#   s  at most STORAGE_DIALOG_TIER_S bytes (640 KB partition, 4 MB boards)
#   m  at most STORAGE_DIALOG_TIER_M bytes (1 MB partition, 16 MB boards)
#   l  every clip (the Linux port, no limit)
#
# Each tier is the longest prefix of one priority order that fits, whole clips
# only: first clip 1 of every phase (the minimum set), then the further
# questions, cant-hear-you, am-aparat, hello and who-is-there clips — a phase
# by clip number. A tier equal to a smaller one is not published. The limits
# are read from the firmware sources, so the two cannot drift apart.
#
# Sets MANIFEST_ASSETS[lang].dialog to the list of tiers {path, sha256,
# bytes}, smallest first, and prints one line per tier. Publishes nothing
# when the locale has no clips or misses a phase (the firmware only uses
# complete sets); dies when even the minimum set exceeds the s tier.
DIALOG_TIER_S="$(fw_define storage_budget.h STORAGE_DIALOG_TIER_S)"
DIALOG_TIER_M="$(fw_define storage_budget.h STORAGE_DIALOG_TIER_M)"
DIALOG_MAX_CLIPS="$(fw_define dialog.h DIALOG_MAX_CLIPS)"

build_dialog() {
    local lang="$1" src="${AUDIO_DIR}/dialog/$1"
    [[ -d "$src" ]] || return 0
    local tiers rc=0
    tiers="$(python3 -I - "$src" "$lang" "${CHANNEL}" "$DIALOG_MAX_CLIPS" \
             "s=${DIALOG_TIER_S}" "m=${DIALOG_TIER_M}" "l=all" <<'PY'
import hashlib, json, os, re, struct, sys

src, lang, out_dir, max_clips = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
# Tier name and byte limit, smallest first; "all": no limit.
limits = [(t, None if v == "all" else int(v))
          for t, v in (a.split("=") for a in sys.argv[5:])]
PHASES = ["hello", "who-is-there", "am-aparat", "question", "cant-hear-you"]
# After the minimum set (clip 1 of each phase): the further clips, phase by
# phase in this order.
EXTRA = ["question", "cant-hear-you", "am-aparat", "hello", "who-is-there"]

def say(msg):
    print("   dialog: " + msg, file=sys.stderr)

clips = {}
for ph in PHASES:
    found = []
    for name in os.listdir(src):
        m = re.fullmatch(re.escape(ph) + r"-([0-9]+)\.alaw", name)
        if m:
            found.append((int(m.group(1)), name))
    if not found:
        say(f"no '{ph}' clip — the {lang} set is incomplete, not published")
        sys.exit(3)
    if len(found) > max_clips:
        say(f"{len(found)} '{ph}' clips, the firmware takes at most {max_clips}")
        sys.exit(4)
    data = []
    for _, name in sorted(found):
        with open(os.path.join(src, name), "rb") as f:
            b = f.read()
        if not b:
            say(f"{name} is empty — the {lang} set is not published")
            sys.exit(3)
        data.append(b)
    clips[ph] = data

order = [(ph, 0) for ph in PHASES]
order += [(ph, i) for ph in EXTRA for i in range(1, len(clips[ph]))]

def build(n):
    """The set file of the first n clips of the priority order."""
    count = {ph: 0 for ph in PHASES}
    for ph, _ in order[:n]:
        count[ph] += 1
    hlen = 8 + len(PHASES) + 8 * n
    table, body, off = b"", b"", hlen
    for ph in PHASES:
        for i in range(count[ph]):   # a phase's included clips are its first ones
            c = clips[ph][i]
            table += struct.pack("<II", off, len(c))
            body += c
            off += len(c)
    head = b"PBDS" + struct.pack("<BBH", 1, len(PHASES), hlen)
    head += bytes(count[ph] for ph in PHASES)
    return head + table + body, count

tiers, last_n = [], 0
for tier, limit in limits:
    n = 0
    while n < len(order) and (limit is None or len(build(n + 1)[0]) <= limit):
        n += 1
    if n < len(PHASES):
        size = len(build(len(PHASES))[0])
        say(f"the {lang} minimum set (one clip per phase) has {size} bytes, "
            f"over the {tier} tier's {limit}")
        sys.exit(4)
    if n == last_n:
        continue   # same clips as the tier below
    last_n = n
    blob, count = build(n)
    sha = hashlib.sha256(blob).hexdigest()
    # Content-addressed: a republished set never overwrites a file a device
    # may be downloading (scripts/audio-assets.sh).
    rel = f"dialog-{lang}-{tier}-{sha[:8]}.bin"
    with open(os.path.join(out_dir, rel), "wb") as f:
        f.write(blob)
    tiers.append({"path": rel, "sha256": sha, "bytes": len(blob)})
    say(f"{tier}: {len(blob)} B, {n} of {len(order)} clips (" +
        ", ".join(f"{ph} {count[ph]}" for ph in PHASES) + ")")
print(json.dumps(tiers, separators=(",", ":")))
PY
    )" || rc=$?
    case "$rc" in
        0) ;;
        3) return 0;;
        *) die "dialog: the ${lang} set cannot be published (see above)";;
    esac
    MANIFEST_ASSETS="$(jq -c --arg l "$lang" --argjson d "$tiers" \
        '.[$l] = ((.[$l] // {}) + {dialog: $d})' <<< "$MANIFEST_ASSETS")"
}

MANIFEST_ASSETS='{}'
while read -r code _rest; do
    [[ -z "$code" || "$code" == \#* ]] && continue
    if [[ -n "$ONLY_LANGS" && " $ONLY_LANGS " != *" $code "* ]]; then continue; fi
    echo "==> ${code}"

    # Announcement (a locale without one falls back along the firmware's
    # ui_lang → en → de chain).
    tmp="${CHANNEL}/.announcement-${code}.alaw"
    if get_announcement "$code" "$tmp"; then
        sha="$(sha256sum "$tmp" | awk '{print $1}')"
        rel="announcement-${code}-${sha:0:8}.alaw"
        mv "$tmp" "${CHANNEL}/${rel}"
        bytes="$(stat -c%s "${CHANNEL}/${rel}")"
        MANIFEST_ASSETS="$(jq -c --arg l "$code" --arg p "$rel" --arg s "$sha" \
            --argjson b "$bytes" '.[$l] = ((.[$l] // {}) + {announcement: {path:$p, sha256:$s, bytes:$b}})' \
            <<< "$MANIFEST_ASSETS")"
        echo "   announcement: ${bytes} B"
    else
        echo "   no announcement recording"
    fi

    # Dialog clip set in its size tiers (complete sets only).
    build_dialog "$code"
done < "${SRC_DIR}/languages.txt"

jq -nc --argjson f "$AUDIO_FORMAT" --argjson a "$MANIFEST_ASSETS" \
    '{format:$f, assets:$a}' > "${CHANNEL}/manifest.json"
echo "==> manifest: $(stat -c%s "${CHANNEL}/manifest.json") B, $(jq '.assets | length' "${CHANNEL}/manifest.json") locales, $(ls "$CHANNEL" | grep -vc '^manifest.json$') files"

# --- Upload ------------------------------------------------------------------
run() { if [[ $DRY_RUN -eq 1 ]]; then printf '+ %s\n' "$*"; else "$@"; fi; }
sftp_batch() {
    local script; script="$(cat)"
    if [[ $DRY_RUN -eq 1 ]]; then printf '+ sftp %s -b - %s <<EOF\n%s\nEOF\n' "${SSH_OPTS[*]}" "$CDN_HOST" "$script"
    else printf '%s\n' "$script" | sftp "${SSH_OPTS[@]}" -b - "$CDN_HOST"; fi
}
# Names in the remote channel directory (empty in a dry run).
remote_files() {
    [[ $DRY_RUN -eq 1 ]] && return 0
    printf 'ls -1 %s\n' "$REMOTE" | sftp "${SSH_OPTS[@]}" -b - "$CDN_HOST" 2>/dev/null \
        | grep -v '^sftp>' | sed 's|.*/||' || true
}
# File names a manifest references.
manifest_paths() {
    jq -r '.assets[] | (.announcement.path // empty), ((.dialog // [])[] | .path)' "$1"
}

if [[ $NO_UPLOAD -eq 1 ]]; then
    echo "==> --no-upload: channel staged at ${CHANNEL}"
fi
if [[ $NO_UPLOAD -eq 1 && $DRY_RUN -eq 0 ]]; then
    echo "==> done."
    exit 0
fi
[[ $DRY_RUN -eq 1 ]] && echo "==> would upload:"

sftp_batch <<SFTP
-mkdir ${CDN_BASE}
-mkdir ${CDN_FIRMWARE}
-mkdir ${CDN_FIRMWARE}/audio
-mkdir ${REMOTE}
SFTP

# The previous generation: what the live manifest references stays.
PREV="${STAGE}/manifest.prev.json"
echo '{"assets":{}}' > "$PREV"
if [[ $DRY_RUN -eq 0 ]]; then
    printf 'get %s/manifest.json %s\n' "$REMOTE" "$PREV" \
        | sftp "${SSH_OPTS[@]}" -b - "$CDN_HOST" >/dev/null 2>&1 || echo '{"assets":{}}' > "$PREV"
fi
mapfile -t HAVE < <(remote_files)

# Content-addressed: a name on the CDN already has these bytes.
UPLOAD=()
for f in $(manifest_paths "${CHANNEL}/manifest.json"); do
    if [[ " ${HAVE[*]:-} " != *" ${f} "* ]]; then UPLOAD+=("${CHANNEL}/${f}"); fi
done
echo "==> ${#UPLOAD[@]} new file(s) to upload"
if [[ ${#UPLOAD[@]} -gt 0 ]]; then
    run scp "${SSH_OPTS[@]}" "${UPLOAD[@]}" "${CDN_HOST}:${REMOTE}/"
fi

# The manifest last, atomically (OpenSSH's sftp rename is posix-rename).
sftp_batch <<SFTP
put ${CHANNEL}/manifest.json ${REMOTE}/manifest.json.tmp
rename ${REMOTE}/manifest.json.tmp ${REMOTE}/manifest.json
SFTP

# Cleanup: files of older generations, referenced by neither manifest.
KEEP=" $(manifest_paths "${CHANNEL}/manifest.json" | tr '\n' ' ') $(manifest_paths "$PREV" | tr '\n' ' ') "
DROP=()
for f in "${HAVE[@]:-}"; do
    [[ -z "$f" ]] && continue
    [[ "$f" =~ ^(announcement|dialog)-.*\.(alaw|bin)$ ]] || continue
    [[ "$KEEP" == *" ${f} "* ]] || DROP+=("$f")
done
if [[ $DRY_RUN -eq 1 ]]; then
    echo "+ cleanup: remove ${REMOTE}/(announcement|dialog)-* named by neither the new nor the previous manifest"
elif [[ ${#DROP[@]} -gt 0 ]]; then
    echo "==> removing ${#DROP[@]} file(s) of older generations"
    for f in "${DROP[@]}"; do printf -- '-rm %s/%s\n' "$REMOTE" "$f"; done | sftp_batch
fi

[[ $DRY_RUN -eq 0 ]] && echo "==> published to ${CDN_HOST}:${REMOTE}"
echo "==> done."
