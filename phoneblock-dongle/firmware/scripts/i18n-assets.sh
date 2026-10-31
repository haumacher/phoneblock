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
# language with no recording ships text-only (silent pickup). Dialog clips
# of the interactive answer bot (#429): i18n/audio/dialog/<lang>/
# <phase>-<n>.alaw, concatenated into ONE published file per locale
# (audio/dialog-<lang>.alaw) and only as a complete set (all five phases),
# under the manifest's additive assets.<lang>.dialog key with the clip
# table (phase -> [[offset, length], ...]). Mail + web-UI
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
#   --stage DIR     Staging dir for the built artifacts (default: a mktemp).
#   --no-upload     Build locally, skip the CDN upload. Implies the staging
#                   dir is kept and printed.
#   --dry-run       --no-upload, and also print (not run) the sftp/scp cmds.
#   -h | --help     This help.
#
# Assembles committed files only — no translation runs here. Translations are
# produced during development (see i18n/README.md) and committed.
#
# The manifest is NOT signed, unlike the OTA manifest (scripts/sign-manifest.sh
# and manifest_sig.c in the firmware). These assets are display strings and
# announcement audio: HTTPS + the cert bundle is the transport trust, and a
# hostile CDN would at worst show a user wrong text. Dropping the signature is
# what makes publishing a bundle for an unreleased dev build a one-liner that
# needs no release key — the consumers treat pack strings as untrusted input.

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

# The upload runs unattended, so SSH must never block on a prompt: BatchMode
# turns a missing key/passphrase into an immediate "Permission denied" instead
# of a passphrase prompt that hangs forever, and refuses an unknown host key
# rather than waiting on a yes/no; ConnectTimeout bounds a dead/slow host.
# (Set up ssh-agent + a known_hosts entry for $CDN_HOST once, as for release.sh.)
SSH_OPTS=(-o BatchMode=yes -o ConnectTimeout=20)

# Announcement recordings are committed to the repo (like today's single
# main/audio/announcement.alaw). Default source dir; --from-audio overrides.
AUDIO_DIR="${SRC_DIR}/audio"

VERSION=""
ONLY_LANGS=""
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
        --stage)      STAGE="$2"; shift 2;;
        --no-upload)  NO_UPLOAD=1; shift;;
        --dry-run)    DRY_RUN=1; NO_UPLOAD=1; shift;;
        -h|--help)    sed -n '2,60p' "$0" | sed 's/^# \{0,1\}//'; exit 0;;
        *)            die "unknown option: $1";;
    esac
done

for t in jq sha256sum; do
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
# The dialog phases, in the firmware's order (main/dialog.h).
DIALOG_PHASES=(hello who-is-there am-aparat question cant-hear-you)

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
# Dialog clips: the set for <lang> from AUDIO_DIR/dialog/<lang>/, raw A-law
# like the announcement, numbered per phase (<phase>-1.alaw, <phase>-2.alaw,
# ... — the count is free, the firmware takes what the manifest lists).
# Concatenates them, in phase order and clip order, into ONE file
# ASSETS/audio/dialog-<lang>.alaw — a single SPIFFS object on the device
# instead of one per clip (see test_storage_sim.c) — and prints the
# manifest's dialog asset: {path, sha256, bytes, phases}, phases mapping each
# phase to the [offset, length] of its clips within the file. Prints nothing
# (and returns 1) when the locale has no clips or misses a phase: the firmware
# only uses complete sets, so a partial one is not published at all.
build_dialog() {
    local lang="$1" src="${AUDIO_DIR}/dialog/$1"
    [[ -d "$src" ]] || return 1
    local rel="audio/dialog-${lang}.alaw"
    local abs="${ASSETS}/${rel}"
    local phases='{}' phase f len off=0
    : > "$abs"
    for phase in "${DIALOG_PHASES[@]}"; do
        local files=()
        while IFS= read -r f; do files+=("$f"); done < <(
            find "$src" -maxdepth 1 -type f -name "${phase}-*.alaw" -printf '%f\n' \
                | grep -E "^${phase}-[0-9]+\.alaw$" | sort -V)
        if [[ ${#files[@]} -eq 0 ]]; then
            echo "   dialog: no '${phase}' clip — the ${lang} set is incomplete, not published" >&2
            rm -f "$abs"
            return 1
        fi
        for f in "${files[@]}"; do
            len="$(stat -c%s "${src}/${f}")"
            if [[ "$len" -eq 0 ]]; then
                echo "   dialog: ${f} is empty — the ${lang} set is not published" >&2
                rm -f "$abs"
                return 1
            fi
            cat "${src}/${f}" >> "$abs"
            phases="$(jq -c --arg ph "$phase" --argjson o "$off" --argjson n "$len" \
                '.[$ph] = ((.[$ph] // []) + [[$o, $n]])' <<< "$phases")"
            off=$((off + len))
        done
    done
    local sha
    sha="$(sha256sum "$abs" | awk '{print $1}')"
    jq -cn --arg p "$rel" --arg s "$sha" --argjson b "$off" --argjson ph "$phases" \
        '{path:$p, sha256:$s, bytes:$b, phases:$ph}'
}

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

    # Dialog clip set (complete sets only; others fall back along the
    # firmware's ui_lang → en → de chain).
    if dialog_json="$(build_dialog "$code")"; then
        MANIFEST_ASSETS="$(jq -c --arg l "$code" --argjson d "$dialog_json" \
            '.[$l] = ((.[$l] // {}) + {dialog: $d})' <<< "$MANIFEST_ASSETS")"
        echo "   dialog: $(jq -r '"\(.bytes) B: " + ([.phases | to_entries[] | "\(.key) \(.value | length)"] | join(", "))' <<< "$dialog_json")"
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

# --- Upload (atomic manifest publish, mirrors release.sh) ------------------
run() { if [[ $DRY_RUN -eq 1 ]]; then printf '+ %s\n' "$*"; else "$@"; fi; }
sftp_batch() {
    local script; script="$(cat)"
    if [[ $DRY_RUN -eq 1 ]]; then printf '+ sftp %s -b - %s <<EOF\n%s\nEOF\n' "${SSH_OPTS[*]}" "$CDN_HOST" "$script"
    else printf '%s\n' "$script" | sftp "${SSH_OPTS[@]}" -b - "$CDN_HOST"; fi
}

if [[ $NO_UPLOAD -eq 1 ]]; then
    echo "==> --no-upload: artifacts staged at ${ASSETS}"
    [[ $DRY_RUN -eq 1 ]] && echo "==> would upload:"
fi

if [[ $NO_UPLOAD -eq 0 || $DRY_RUN -eq 1 ]]; then
    # Ensure the dirs exist, ship the asset files, then swap the manifest in
    # last via posix-rename so a client never reads a manifest whose assets
    # aren't up yet.
    sftp_batch <<SFTP
-mkdir ${CDN_BASE}
-mkdir ${CDN_FIRMWARE}
-mkdir ${CDN_FIRMWARE}/${VERSION}
-mkdir ${CDN_I18N}
-mkdir ${CDN_I18N}/audio
-mkdir ${CDN_I18N}/mail
-mkdir ${CDN_I18N}/ui
SFTP
    run scp "${SSH_OPTS[@]}" "${ASSETS}"/audio/*    "${CDN_HOST}:${CDN_I18N}/audio/"
    run scp "${SSH_OPTS[@]}" "${ASSETS}"/mail/*     "${CDN_HOST}:${CDN_I18N}/mail/"
    run scp "${SSH_OPTS[@]}" "${ASSETS}"/ui/*.json  "${CDN_HOST}:${CDN_I18N}/ui/"
    sftp_batch <<SFTP
put ${ASSETS}/manifest.json  ${CDN_I18N}/manifest.json.tmp
rename ${CDN_I18N}/manifest.json.tmp ${CDN_I18N}/manifest.json
SFTP
    [[ $DRY_RUN -eq 0 ]] && echo "==> published to ${CDN_HOST}:${CDN_I18N}"
fi

echo "==> done."
