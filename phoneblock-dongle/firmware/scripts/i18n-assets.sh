#!/usr/bin/env bash
#
# Generate, sign and publish the dongle's localized assets (issue #460):
# per-language answer-bot announcement audio and status-mail string packs,
# plus a signed manifest the firmware pulls from the CDN (i18n_sync.c).
#
# The firmware carries NO per-language payload — this script is how a
# language reaches the fleet. Adding one is: append a line to
# i18n/languages.txt and re-run this. No firmware change, no reflash.
#
#   ./scripts/i18n-assets.sh [options]
#
# All sources are committed to git; nothing is translated at release time.
# Announcement recordings: i18n/audio/announcement-<lang>.alaw (like
# today's single main/audio/announcement.alaw) — hand-record / tune each; a
# language with no recording ships text-only (silent pickup). Mail + web-UI
# text: the committed ARB files under i18n/l10n/ (mail_<lang>.arb,
# ui_<lang>.arb), translated during development by the auto-translate-arb
# Gradle plugin (see i18n/l10n/); this script just strips their @key
# metadata into the published packs. Missing packs degrade to the firmware's
# compiled German fallback / the browser's inline German.
#
# Options:
#   --version V     Firmware release version (default: git describe). Assets
#                   go to firmware/<version>/i18n/; MUST match the firmware.
#   --langs "a b"   Only build these firmware codes (default: all in
#                   languages.txt).
#   --from-audio D  Take announcement recordings from dir D instead of
#                   i18n/audio (.alaw used as-is; wav/mp3/m4a/flac
#                   converted via ffmpeg).
#   --key FILE      Sign with this ECDSA-P256 private-key PEM directly
#                   (skips the KeePassXC pull; used by the roundtrip test).
#   --stage DIR     Staging dir for the built artifacts (default: a mktemp).
#   --no-upload     Build + sign locally, skip the CDN upload. Implies the
#                   staging dir is kept and printed.
#   --dry-run       --no-upload, and also print (not run) the sftp/scp cmds.
#   -h | --help     This help.
#
# Assembles committed files only — no translation runs here. Translations are
# produced during development (see i18n/README.md) and committed.
#
# Credentials (env or scripts/release.settings, same file release.sh uses):
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
FW_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
SRC_DIR="${FW_DIR}/i18n"

# --- CDN layout (mirrors release.sh) ---------------------------------------
# i18n assets are co-located with the firmware: firmware/<version>/i18n/.
CDN_HOST="${CDN_HOST:-haumac@cdn.phoneblock.net}"
CDN_BASE="${CDN_BASE:-/public_html/cdn/dongle}"
CDN_FIRMWARE="${CDN_BASE}/firmware"
# CDN_I18N (the firmware/<version>/i18n dir) is computed once VERSION is known.

# --- signing payload domain tag (must equal i18n_sync.c) -------------------
SIG_DOMAIN=$'phoneblock-dongle-i18n-v1\n'

# Announcement recordings are committed to the repo (like today's single
# main/audio/announcement.alaw). Default source dir; --from-audio overrides.
AUDIO_DIR="${SRC_DIR}/audio"

VERSION=""
ONLY_LANGS=""
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
        --from-audio) AUDIO_DIR="$2"; shift 2;;
        --key)        KEY_PEM="$2"; shift 2;;
        --stage)      STAGE="$2"; shift 2;;
        --no-upload)  NO_UPLOAD=1; shift;;
        --dry-run)    DRY_RUN=1; NO_UPLOAD=1; shift;;
        -h|--help)    sed -n '2,60p' "$0" | sed 's/^# \{0,1\}//'; exit 0;;
        *)            die "unknown option: $1";;
    esac
done

for t in jq openssl sha256sum; do
    command -v "$t" >/dev/null || die "$t not found in PATH"
done

# The firmware release version. Assets are published co-located with the .bin
# under firmware/<version>/i18n/, and the device fetches the subtree matching
# the version it runs. MUST equal what the device asks for: the firmware
# version (esp_app_get_description()->version) after version_release_tag()
# strips the git-describe build suffix. We derive the version the SAME way the
# firmware does — release.sh's version.txt if present, else a `dongle-v*` git
# describe (NOT a bare `git describe`, which in this monorepo picks up the
# unrelated Maven `3.2.x` web-app tags) — then apply the same suffix stripping.
if [[ -z "$VERSION" ]]; then
    if [[ -f "${FW_DIR}/version.txt" ]]; then
        VERSION="$(cat "${FW_DIR}/version.txt")"
    else
        VERSION="$(cd "$FW_DIR" && git describe --tags --match 'dongle-v*' --always --dirty 2>/dev/null || true)"
        VERSION="${VERSION#dongle-v}"
    fi
    [[ -z "$VERSION" ]] && die "cannot determine version — pass --version <firmware-version>"
fi
# Match version_release_tag() (main/version_cmp.c): drop the "-dirty" flag and
# the "-<commits>-g<hash>" git-describe suffix, but KEEP an "-rcN" pre-release
# tag (an rc has its own asset subtree). So 1.5.1-3-gdeadbee-dirty -> 1.5.1,
# 1.6.0-rc1-2-gabc1234 -> 1.6.0-rc1.
VERSION="${VERSION%-dirty}"
VERSION="$(printf '%s' "$VERSION" | sed -E 's/-[0-9]+-g[0-9a-f]+$//')"
[[ -z "$VERSION" ]] && die "empty version after normalization"
echo "==> firmware version ${VERSION} (assets go to firmware/${VERSION}/i18n)"
CDN_I18N="${CDN_FIRMWARE}/${VERSION}/i18n"

[[ -z "$STAGE" ]] && STAGE="$(mktemp -d -t dongle-i18n.XXXXXX)"
ASSETS="${STAGE}/assets"
mkdir -p "${ASSETS}/audio" "${ASSETS}/mail" "${ASSETS}/ui"

echo "==> staging ${STAGE}"

# --- Announcement: use the committed recording for <lang> ------------------
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

# Clean an ARB file into a published pack: drop the @key metadata blocks but
# KEEP @@locale, so the browser can tell a real locale pack from the German
# fallback the device serves while a pack is still downloading.
strip_arb() {
    jq 'with_entries(select(.key == "@@locale" or (.key | startswith("@") | not)))' "$1" > "$2"
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

while read -r code _rest; do
    [[ -z "$code" || "$code" == \#* ]] && continue
    if [[ -n "$ONLY_LANGS" && " $ONLY_LANGS " != *" $code "* ]]; then continue; fi
    echo "==> ${code}"

    # Announcement recording (committed; a locale without one ships silent).
    ann_rel="audio/announcement-${code}.alaw"
    ann_abs="${ASSETS}/${ann_rel}"
    if get_announcement "$code" "$ann_abs"; then
        add_asset "$code" announcement "$ann_rel" "$ann_abs"
    else
        echo "   no announcement recording — text-only (silent pickup)"
    fi

    # Mail pack from the committed ARB (de = the source ARB). Strip the @key
    # metadata / @@locale so the published pack is a clean key→string dict.
    # Absent → the firmware's compiled German fallback is used for that locale.
    if [[ -f "${SRC_DIR}/l10n/mail/mail_${code}.arb" ]]; then
        strip_arb "${SRC_DIR}/l10n/mail/mail_${code}.arb" "${ASSETS}/mail/mail-${code}.json"
        add_asset "$code" mail "mail/mail-${code}.json" "${ASSETS}/mail/mail-${code}.json"
    else
        echo "   no committed mail translation — falls back to German mail"
    fi

    # UI pack — downloaded by the firmware and served same-origin, so a
    # configured dongle needs no CDN at runtime. English is also baked into the
    # image as the offline fallback, but we still publish EVERY locale listed
    # (incl. en and de) — uniform pipeline, no special-casing; a device whose
    # ui_lang has no published pack just keeps serving the embedded English.
    if [[ -f "${SRC_DIR}/l10n/ui/ui_${code}.arb" ]]; then
        strip_arb "${SRC_DIR}/l10n/ui/ui_${code}.arb" "${ASSETS}/ui/lang-${code}.json"
        add_asset "$code" ui "ui/lang-${code}.json" "${ASSETS}/ui/lang-${code}.json"
    else
        echo "   no committed UI translation — falls back to English UI"
    fi
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
# is caught before publishing. Ships as i18n/public.pem (the same
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
-mkdir ${CDN_FIRMWARE}
-mkdir ${CDN_FIRMWARE}/${VERSION}
-mkdir ${CDN_I18N}
-mkdir ${CDN_I18N}/audio
-mkdir ${CDN_I18N}/mail
-mkdir ${CDN_I18N}/ui
SFTP
    run scp "${ASSETS}"/audio/*    "${CDN_HOST}:${CDN_I18N}/audio/"
    run scp "${ASSETS}"/mail/*     "${CDN_HOST}:${CDN_I18N}/mail/"
    run scp "${ASSETS}"/ui/*.json  "${CDN_HOST}:${CDN_I18N}/ui/"
    sftp_batch <<SFTP
put ${ASSETS}/manifest.json      ${CDN_I18N}/manifest.json.tmp
put ${ASSETS}/manifest.json.sig  ${CDN_I18N}/manifest.json.sig.tmp
rename ${CDN_I18N}/manifest.json.sig.tmp ${CDN_I18N}/manifest.json.sig
rename ${CDN_I18N}/manifest.json.tmp     ${CDN_I18N}/manifest.json
SFTP
    [[ $DRY_RUN -eq 0 ]] && echo "==> published to ${CDN_HOST}:${CDN_I18N}"
fi

echo "==> done."
