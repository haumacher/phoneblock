#!/usr/bin/env bash
#
# Generate, sign and publish the dongle's localized assets (issue #460):
# per-language answer-bot announcement audio and status-mail string packs,
# plus a signed manifest the firmware pulls from the CDN (i18n_sync.c).
#
# The firmware carries NO per-language payload — this script is how a
# language reaches the fleet. Adding one is: append a line to
# scripts/i18n/languages.txt and re-run this. No firmware change, no reflash.
#
#   ./scripts/i18n-assets.sh [options]
#
# Options:
#   --version V     Manifest version label (default: git describe / date).
#   --langs "a b"   Only build these firmware codes (default: all in
#                   languages.txt).
#   --from-audio D  Use pre-rendered recordings D/announcement.<lang>.{mp3,wav,m4a}
#                   instead of ElevenLabs TTS (for human voice-overs).
#   --key FILE      Sign with this ECDSA-P256 private-key PEM directly
#                   (skips the KeePassXC pull; used by the roundtrip test).
#   --stage DIR     Staging dir for the built artifacts (default: a mktemp).
#   --no-upload     Build + sign locally, skip the CDN upload. Implies the
#                   staging dir is kept and printed.
#   --dry-run       --no-upload, and also print (not run) the sftp/scp cmds.
#   -h | --help     This help.
#
# Credentials (env or scripts/release.settings, same file release.sh uses):
#   DEEPL_API_KEY         DeepL auth key (translate the German source).
#   DEEPL_API_URL         default https://api-free.deepl.com/v2/translate
#   ELEVENLABS_API_KEY    ElevenLabs key (text-to-speech).
#   ELEVENLABS_VOICE_ID   voice to synthesize with (required for TTS).
#   ELEVENLABS_MODEL      default eleven_multilingual_v2.
#   KEEPASS_DB/ENTRY/ATTACHMENT   OTA signing key (see sign-manifest.sh).
#
# Signed payload for the manifest (must match i18n_sync.c I18N_SIG_DOMAIN):
#
#   phoneblock-dongle-i18n-v1\n            <-- domain tag, then the raw
#   <exact bytes of manifest.json>            manifest.json bytes
#
# The detached signature (base64 ECDSA-P256-SHA256) is published next to the
# manifest as manifest.json.sig.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="${SCRIPT_DIR}/i18n"
FW_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# --- CDN layout (mirrors release.sh) ---------------------------------------
CDN_HOST="${CDN_HOST:-haumac@cdn.phoneblock.net}"
CDN_BASE="${CDN_BASE:-/public_html/cdn/dongle}"
CDN_I18N="${CDN_BASE}/i18n"

# --- signing payload domain tag (must equal i18n_sync.c) -------------------
SIG_DOMAIN=$'phoneblock-dongle-i18n-v1\n'

# --- ElevenLabs defaults ---------------------------------------------------
ELEVENLABS_MODEL="${ELEVENLABS_MODEL:-eleven_multilingual_v2}"
DEEPL_API_URL="${DEEPL_API_URL:-https://api-free.deepl.com/v2/translate}"

VERSION=""
ONLY_LANGS=""
FROM_AUDIO=""
KEY_PEM=""
STAGE=""
NO_UPLOAD=0
DRY_RUN=0

die() { echo "ERROR: $*" >&2; exit 1; }

# release.settings holds credentials (gitignored); source it if present.
[[ -f "${SCRIPT_DIR}/release.settings" ]] && { set -a; . "${SCRIPT_DIR}/release.settings"; set +a; }

while [[ $# -gt 0 ]]; do
    case "$1" in
        --version)    VERSION="$2"; shift 2;;
        --langs)      ONLY_LANGS="$2"; shift 2;;
        --from-audio) FROM_AUDIO="$2"; shift 2;;
        --key)        KEY_PEM="$2"; shift 2;;
        --stage)      STAGE="$2"; shift 2;;
        --no-upload)  NO_UPLOAD=1; shift;;
        --dry-run)    DRY_RUN=1; NO_UPLOAD=1; shift;;
        -h|--help)    sed -n '2,60p' "$0" | sed 's/^# \{0,1\}//'; exit 0;;
        *)            die "unknown option: $1";;
    esac
done

for t in jq openssl sha256sum curl; do
    command -v "$t" >/dev/null || die "$t not found in PATH"
done

if [[ -z "$VERSION" ]]; then
    VERSION="$(cd "$FW_DIR" && git describe --tags --always --dirty 2>/dev/null || true)"
    [[ -z "$VERSION" ]] && VERSION="$(TZ=UTC printf '%(%Y%m%d%H%M%S)T' -1)"
fi

[[ -z "$STAGE" ]] && STAGE="$(mktemp -d -t dongle-i18n.XXXXXX)"
ASSETS="${STAGE}/assets"
mkdir -p "${ASSETS}/audio" "${ASSETS}/mail"

echo "==> version ${VERSION}"
echo "==> staging ${STAGE}"

# --- DeepL: translate one text, protecting %-placeholders and <b> tags -----
# Placeholders (%s %d %u %lld …) and the literal <b>/</b> must survive
# translation verbatim. We swap them for opaque sentinels DeepL won't touch,
# translate, then swap back. Passthrough for the source language.
deepl_translate() {
    local target="$1" text="$2"
    if [[ "$target" == "-" || "$target" == "DE" ]]; then
        printf '%s' "$text"; return
    fi
    [[ -n "${DEEPL_API_KEY:-}" ]] || die "DEEPL_API_KEY not set (needed to translate to ${target})"
    DEEPL_API_KEY="$DEEPL_API_KEY" DEEPL_API_URL="$DEEPL_API_URL" \
    python3 - "$target" "$text" <<'PY'
import os, re, sys, json, urllib.request, urllib.parse
target, text = sys.argv[1], sys.argv[2]
# Protect printf specifiers and the bold tags with sentinels.
toks = []
def stash(m):
    toks.append(m.group(0)); return f"{len(toks)-1}"
protected = re.sub(r'%[0-9]*[a-zA-Z]+|</?b>', stash, text)
data = urllib.parse.urlencode({
    "text": protected, "target_lang": target,
    "tag_handling": "xml", "ignore_tags": "x",
}).encode()
req = urllib.request.Request(os.environ["DEEPL_API_URL"], data=data,
    headers={"Authorization": "DeepL-Auth-Key " + os.environ["DEEPL_API_KEY"]})
with urllib.request.urlopen(req, timeout=30) as r:
    out = json.load(r)["translations"][0]["text"]
# Restore sentinels.
out = re.sub("([0-9]+)", lambda m: toks[int(m.group(1))], out)
sys.stdout.write(out)
PY
}

# --- ElevenLabs TTS → mp3, then ffmpeg → raw G.711 A-law 8 kHz mono --------
synth_announcement() {
    local lang="$1" deepl="$2" out_alaw="$3"
    local mp3="${STAGE}/announcement.${lang}.mp3"

    if [[ -n "$FROM_AUDIO" ]]; then
        local src=""
        for ext in mp3 wav m4a; do
            [[ -f "${FROM_AUDIO}/announcement.${lang}.${ext}" ]] && src="${FROM_AUDIO}/announcement.${lang}.${ext}"
        done
        [[ -n "$src" ]] || die "no ${FROM_AUDIO}/announcement.${lang}.{mp3,wav,m4a}"
        command -v ffmpeg >/dev/null || die "ffmpeg not found"
        ffmpeg -y -loglevel error -i "$src" -ar 8000 -ac 1 -f alaw "$out_alaw"
        return
    fi

    [[ -n "${ELEVENLABS_API_KEY:-}" ]]  || die "ELEVENLABS_API_KEY not set (or use --from-audio)"
    [[ -n "${ELEVENLABS_VOICE_ID:-}" ]] || die "ELEVENLABS_VOICE_ID not set"
    command -v ffmpeg >/dev/null || die "ffmpeg not found"

    local text
    text="$(deepl_translate "$deepl" "$(cat "${SRC_DIR}/announcement.de.txt")")"
    local body
    body="$(jq -nc --arg t "$text" --arg m "$ELEVENLABS_MODEL" \
        '{text:$t, model_id:$m, voice_settings:{stability:0.5, similarity_boost:0.75}}')"
    curl -sf -X POST \
        "https://api.elevenlabs.io/v1/text-to-speech/${ELEVENLABS_VOICE_ID}" \
        -H "xi-api-key: ${ELEVENLABS_API_KEY}" \
        -H "Content-Type: application/json" \
        -H "Accept: audio/mpeg" \
        -d "$body" -o "$mp3" || die "ElevenLabs TTS failed for ${lang}"
    ffmpeg -y -loglevel error -i "$mp3" -ar 8000 -ac 1 -f alaw "$out_alaw"
}

# --- Translate the German mail pack into <lang>, preserving keys -----------
build_mail_pack() {
    local lang="$1" deepl="$2" out="$3"
    if [[ "$deepl" == "-" || "$deepl" == "DE" ]]; then
        cp "${SRC_DIR}/mail.de.json" "$out"; return
    fi
    # Translate each value; keys and placeholders are preserved.
    local keys; keys="$(jq -r 'keys_unsorted[]' "${SRC_DIR}/mail.de.json")"
    local tmp; tmp="$(mktemp)"; echo '{}' > "$tmp"
    while IFS= read -r k; do
        local v; v="$(jq -r --arg k "$k" '.[$k]' "${SRC_DIR}/mail.de.json")"
        local tv; tv="$(deepl_translate "$deepl" "$v")"
        jq --arg k "$k" --arg v "$tv" '.[$k]=$v' "$tmp" > "${tmp}.n" && mv "${tmp}.n" "$tmp"
    done <<< "$keys"
    mv "$tmp" "$out"
}

# --- Build the assets + manifest -------------------------------------------
MANIFEST_ASSETS='{}'
add_asset() {  # lang kind relpath absfile
    local lang="$1" kind="$2" rel="$3" abs="$4"
    local sha bytes
    sha="$(sha256sum "$abs" | awk '{print $1}')"
    bytes="$(stat -c%s "$abs")"
    MANIFEST_ASSETS="$(jq -c --arg l "$lang" --arg k "$kind" --arg p "$rel" \
        --arg s "$sha" --argjson b "$bytes" \
        '.[$l] = ((.[$l] // {}) + {($k): {path:$p, sha256:$s, bytes:$b}})' \
        <<< "$MANIFEST_ASSETS")"
}

while read -r code deepl; do
    [[ -z "$code" || "$code" == \#* ]] && continue
    if [[ -n "$ONLY_LANGS" && " $ONLY_LANGS " != *" $code "* ]]; then continue; fi
    echo "==> ${code} (deepl=${deepl})"

    ann_rel="audio/announcement-${code}.alaw"
    ann_abs="${ASSETS}/${ann_rel}"
    synth_announcement "$code" "$deepl" "$ann_abs"
    add_asset "$code" announcement "$ann_rel" "$ann_abs"

    mail_rel="mail/mail-${code}.json"
    mail_abs="${ASSETS}/${mail_rel}"
    build_mail_pack "$code" "$deepl" "$mail_abs"
    add_asset "$code" mail "$mail_rel" "$mail_abs"
done < "${SRC_DIR}/languages.txt"

# Canonical manifest bytes (compact, stable key order per language).
jq -nc --arg v "$VERSION" --argjson a "$MANIFEST_ASSETS" \
    '{version:$v, assets:$a}' > "${ASSETS}/manifest.json"
echo "==> manifest:"; jq . "${ASSETS}/manifest.json"

# --- Sign: base64(ECDSA-P256-SHA256 over SIG_DOMAIN + manifest bytes) ------
sign_payload() {  # writes payload (domain tag + manifest bytes) to $1
    { printf '%s' "$SIG_DOMAIN"; cat "${ASSETS}/manifest.json"; } > "$1"
}

PAYLOAD="$(mktemp)"; SIGBIN="$(mktemp)"
trap 'rm -f "$PAYLOAD" "$SIGBIN"' EXIT
sign_payload "$PAYLOAD"

if [[ -n "$KEY_PEM" ]]; then
    [[ -f "$KEY_PEM" ]] || die "key not found: $KEY_PEM"
    openssl dgst -sha256 -sign "$KEY_PEM" -out "$SIGBIN" "$PAYLOAD"
else
    command -v keepassxc-cli >/dev/null || die "keepassxc-cli not found (or pass --key)"
    command -v shred >/dev/null || die "shred not found"
    [[ -n "${KEEPASS_DB:-}" ]] || die "KEEPASS_DB not set (or pass --key)"
    TMP_KEY="$(mktemp -p "${TMPDIR:-/tmp}" dongle-i18n-key.XXXXXX)"
    trap 'shred -u "$TMP_KEY" 2>/dev/null || true; rm -f "$PAYLOAD" "$SIGBIN"' EXIT
    keepassxc-cli attachment-export "$KEEPASS_DB" \
        "${KEEPASS_ENTRY:-PhoneBlock-Dongle Signing Key}" \
        "${KEEPASS_ATTACHMENT:-private.pem}" "$TMP_KEY" >&2
    openssl dgst -sha256 -sign "$TMP_KEY" -out "$SIGBIN" "$PAYLOAD"
fi
base64 -w0 < "$SIGBIN" > "${ASSETS}/manifest.json.sig"
echo "==> signature written (${ASSETS}/manifest.json.sig)"

# Self-check: verify against the release public key so a signing-key mixup
# is caught before publishing. Ships as scripts/i18n/public.pem (the same
# key baked into manifest_sig.c); override with I18N_PUBLIC_PEM for tests.
PUBLIC_PEM="${I18N_PUBLIC_PEM:-${SRC_DIR}/public.pem}"
if [[ -f "$PUBLIC_PEM" ]]; then
    if openssl dgst -sha256 -verify "$PUBLIC_PEM" \
        -signature "$SIGBIN" "$PAYLOAD" >/dev/null 2>&1; then
        echo "==> signature self-check OK (${PUBLIC_PEM})"
    else
        die "signature self-check FAILED — private key does not match ${PUBLIC_PEM}"
    fi
fi

# --- Upload (atomic manifest publish, mirrors release.sh) ------------------
run() { if [[ $DRY_RUN -eq 1 ]]; then printf '+ %s\n' "$*"; else "$@"; fi; }
sftp_batch() {
    local script; script="$(cat)"
    if [[ $DRY_RUN -eq 1 ]]; then printf '+ sftp -b - %s <<EOF\n%s\nEOF\n' "$CDN_HOST" "$script"
    else printf '%s\n' "$script" | sftp -b - "$CDN_HOST"; fi
}

if [[ $NO_UPLOAD -eq 1 ]]; then
    echo "==> --no-upload: artifacts staged at ${ASSETS}"
    [[ $DRY_RUN -eq 1 ]] && echo "==> would upload:"
fi

if [[ $NO_UPLOAD -eq 0 || $DRY_RUN -eq 1 ]]; then
    # Ensure the dirs exist, ship the asset files, then swap the manifest +
    # signature in last via posix-rename so a client never reads a manifest
    # whose assets aren't up yet.
    sftp_batch <<SFTP
-mkdir ${CDN_BASE}
-mkdir ${CDN_I18N}
-mkdir ${CDN_I18N}/audio
-mkdir ${CDN_I18N}/mail
SFTP
    run scp "${ASSETS}"/audio/* "${CDN_HOST}:${CDN_I18N}/audio/"
    run scp "${ASSETS}"/mail/*  "${CDN_HOST}:${CDN_I18N}/mail/"
    sftp_batch <<SFTP
put ${ASSETS}/manifest.json      ${CDN_I18N}/manifest.json.tmp
put ${ASSETS}/manifest.json.sig  ${CDN_I18N}/manifest.json.sig.tmp
rename ${CDN_I18N}/manifest.json.sig.tmp ${CDN_I18N}/manifest.json.sig
rename ${CDN_I18N}/manifest.json.tmp     ${CDN_I18N}/manifest.json
SFTP
    [[ $DRY_RUN -eq 0 ]] && echo "==> published to ${CDN_HOST}:${CDN_I18N}"
fi

echo "==> done."
