#!/usr/bin/env bash
#
# Build, package, and upload a PhoneBlock-Dongle firmware release.
#
#   ./scripts/release.sh             # full flow: build, stage, upload, flip channel(s)
#   ./scripts/release.sh --stage     # build + stage to release/<version>/, no upload
#   ./scripts/release.sh --dry-run   # all of the above + print scp/ssh commands
#
# Version comes from `git describe --tags --match "dongle-v*"`. The working tree
# must be clean and HEAD must sit exactly on a tag — between-tag builds (e.g.
# 1.4.2-3-gabcdef) are rejected to keep the CDN to released versions only.
#
# The target channel is derived from the tag: a pre-release suffix
# (dongle-v1.6.0-rc1) publishes to the "beta" channel only; a clean
# release (dongle-v1.6.0) publishes to "stable" *and* "beta". That
# keeps beta always >= stable, so a beta tester lands on the final
# build once it ships and never sees a downgrade.

set -euo pipefail

FIRMWARE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_DIR="${FIRMWARE_DIR}/scripts"
BUILD_DIR="${FIRMWARE_DIR}/build"
RELEASE_ROOT="${FIRMWARE_DIR}/release"

# Local settings (gitignored): KEEPASS_DB and friends so the user
# doesn't have to remember to `export` them before each release. The
# template lives at release.settings.template; copy and fill in.
# Sourced with `set -a` so plain KEY=VALUE lines are exported to
# subprocesses (sign-manifest.sh).
SETTINGS_FILE="${SCRIPT_DIR}/release.settings"
if [[ -f "$SETTINGS_FILE" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$SETTINGS_FILE"
    set +a
fi

CDN_HOST="haumac@cdn.phoneblock.net"
CDN_BASE="/public_html/cdn/dongle"
CDN_FIRMWARE="${CDN_BASE}/firmware"

MODE="full"
case "${1:-}" in
    --stage)   MODE="stage"   ;;
    --dry-run) MODE="dry-run" ;;
    "")        MODE="full"    ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
esac

run() {
    if [[ "$MODE" == "dry-run" ]]; then
        printf '+ %s\n' "$*"
    else
        "$@"
    fi
}

# Run an sftp batch script (heredoc on stdin). In dry-run, just print it.
sftp_batch() {
    local script
    script="$(cat)"
    if [[ "$MODE" == "dry-run" ]]; then
        printf '+ sftp -b - %s <<EOF\n%s\nEOF\n' "$CDN_HOST" "$script"
    else
        printf '%s\n' "$script" | sftp -b - "$CDN_HOST"
    fi
}

# ---------------------------------------------------------------------------
# 1. Resolve version from git tag.
# ---------------------------------------------------------------------------
cd "$FIRMWARE_DIR"

if ! git diff --quiet HEAD -- . || ! git diff --cached --quiet HEAD -- .; then
    echo "ERROR: firmware tree has uncommitted changes; refusing to release." >&2
    exit 1
fi

DESCRIBE="$(git describe --tags --match 'dongle-v*' --exact-match 2>/dev/null || true)"
if [[ -z "$DESCRIBE" ]]; then
    echo "ERROR: HEAD is not a 'dongle-v*' tag. Tag the release first:" >&2
    echo "       git tag dongle-vX.Y.Z && git push --tags" >&2
    exit 1
fi
VERSION="${DESCRIBE#dongle-v}"
echo "Releasing version: $VERSION"

# Every release must ship a release-notes file. The web UI links the
# installed version to the suffix-stripped base
#   .../phoneblock-dongle/firmware/release-notes/<X.Y.Z>.md
# so a pre-release (1.3.4-rc1) points at the upcoming release's notes —
# which therefore must already exist when the rc is cut.
BASE_VERSION="${VERSION%%-*}"
NOTES="${FIRMWARE_DIR}/release-notes/${BASE_VERSION}.md"
if [[ ! -f "$NOTES" ]]; then
    echo "ERROR: missing release notes: release-notes/${BASE_VERSION}.md" >&2
    echo "       The web UI links the version there; create it before releasing." >&2
    exit 1
fi

# Derive the target channel(s) from the tag. A pre-release suffix
# (anything after a '-', e.g. 1.6.0-rc1) ships to beta only; a clean
# release ships to stable *and* beta so beta never lags behind stable.
# The dongle's semver compare treats "1.6.0-rc2" < "1.6.0", so a beta
# device upgrades onto the final build automatically once it lands.
if [[ "$VERSION" == *-* ]]; then
    CHANNELS=(beta)
    echo "Pre-release tag → channel: beta"
else
    CHANNELS=(stable beta)
    echo "Release tag → channels: stable + beta"
fi

# ---------------------------------------------------------------------------
# 2. Build firmware. ESP-IDF reads version from version.txt next to CMakeLists,
#    which lands in esp_app_desc_t.version and the /api/status payload.
# ---------------------------------------------------------------------------
echo "$VERSION" > "${FIRMWARE_DIR}/version.txt"

# Move local build state out of the way so the release is built from
# sdkconfig.defaults alone — gitignored sticky config has no business
# in a CDN artifact:
#   * sdkconfig                  — frozen from the last dev build,
#                                  may keep CONFIG_SIP_TEST_FORCE_… or
#                                  similar dev knobs after the .local
#                                  was already cleaned up.
#   * sdkconfig.defaults.local   — typically points at the test
#                                  PhoneBlock backend, ships WiFi
#                                  credentials, enables test hooks.
# Files are restored on EXIT regardless of how the script ends, so a
# dev workspace is undisturbed even if the build fails or the user
# Ctrl-C's. Trap is installed *before* the stash so a Ctrl-C between
# the two mv's still restores cleanly.
STASHED=()
cleanup() {
    rm -f "${FIRMWARE_DIR}/version.txt"
    for f in "${STASHED[@]}"; do
        if [[ -e "${f}.release-stash" ]]; then
            mv "${f}.release-stash" "$f"
        fi
    done
}
trap cleanup EXIT

for f in "${FIRMWARE_DIR}/sdkconfig" "${FIRMWARE_DIR}/sdkconfig.defaults.local"; do
    # Refuse to run if a previous release.sh died before its trap got
    # to restore — otherwise the mv below would clobber the original.
    if [[ -e "${f}.release-stash" ]]; then
        echo "ERROR: leftover ${f}.release-stash from a previous run." >&2
        echo "       Inspect it, then either 'mv ${f}.release-stash ${f}'" >&2
        echo "       or 'rm ${f}.release-stash' before retrying." >&2
        exit 1
    fi
    if [[ -e "$f" ]]; then
        mv "$f" "${f}.release-stash"
        STASHED+=("$f")
    fi
done

if [[ -z "${IDF_PATH:-}" ]]; then
    echo "ERROR: IDF_PATH not set. Source ESP-IDF's export.sh first." >&2
    exit 1
fi

# Wipe the build directory so the release is configured from
# sdkconfig.defaults from scratch. Stashing the gitignored `sdkconfig`
# above is NOT enough on its own: `idf.py build` against an existing
# build/ reuses the cached build/config/sdkconfig.h and does not re-run
# Kconfig just because `sdkconfig` vanished. That is how 1.3.4 shipped
# with CONFIG_ESP_TASK_WDT_TIMEOUT_S=5 even though sdkconfig.defaults
# had already raised it to 120 — the cached 5 s config from the last dev
# build was baked into the CDN artifact. A full wipe forces regeneration
# from defaults, so every config value in sdkconfig.defaults actually
# reaches the released binary.
rm -rf "$BUILD_DIR"

idf.py -C "$FIRMWARE_DIR" build

# ---------------------------------------------------------------------------
# 3. Stage release artifacts into release/<version>/.
# ---------------------------------------------------------------------------
STAGE_VERSION="${RELEASE_ROOT}/${VERSION}"
# Channel manifest: absolute URLs into the version dir. Identical for
# every channel (stable/beta), so it's built once and uploaded to each
# target channel in step 4.
STAGE_CHAN="${RELEASE_ROOT}/channel"
rm -rf "$STAGE_VERSION" "$STAGE_CHAN"
mkdir -p "$STAGE_VERSION" "$STAGE_CHAN"

cp "${BUILD_DIR}/bootloader/bootloader.bin"           "${STAGE_VERSION}/"
cp "${BUILD_DIR}/partition_table/partition-table.bin" "${STAGE_VERSION}/"
cp "${BUILD_DIR}/ota_data_initial.bin"                "${STAGE_VERSION}/"
cp "${BUILD_DIR}/phoneblock_dongle.bin"               "${STAGE_VERSION}/"

# Ship the unstripped ELF alongside the .bin. Not used by devices —
# it is the symbolication input for crash dumps that the dongle
# uploads to /api/dongle/coredump. Resolve a backtrace later with:
#   espcoredump.py info_corefile -c <dump.elf> phoneblock_dongle.elf
# No signing, no manifest entry: this is a debug artefact, not part
# of the OTA payload.
cp "${BUILD_DIR}/phoneblock_dongle.elf"               "${STAGE_VERSION}/"

# Sign the app binary. sign-manifest.sh pulls the Ed25519 private key from
# KeePassXC (master password prompt on tty), signs a deterministic payload
# of (domain-tag, version, app-sha256), and emits two KEY=VALUE lines. The
# signature lands in both the versioned and the stable manifest below.
SIGN_OUT="$("${SCRIPT_DIR}/sign-manifest.sh" \
    "${STAGE_VERSION}/phoneblock_dongle.bin" "${VERSION}")"
APP_SHA256="$(printf '%s\n' "$SIGN_OUT" | sed -n 's/^SHA256=//p')"
APP_SIG="$(printf '%s\n' "$SIGN_OUT" | sed -n 's/^SIG=//p')"
if [[ -z "$APP_SHA256" || -z "$APP_SIG" ]]; then
    echo "ERROR: sign-manifest.sh did not return SHA256 + SIG." >&2
    exit 1
fi

# Versioned manifest: relative paths, served from /dongle/firmware/<version>/.
# `|` as sed-delimiter for the signature line because the base64 output may
# contain `/` (no need for an explicit escape, and `+`/`=` are already safe).
sed -e "s/@VERSION@/${VERSION}/g" \
    -e "s/@APP_SHA256@/${APP_SHA256}/g" \
    -e "s|@SIGNATURE@|${APP_SIG}|g" \
    "${SCRIPT_DIR}/manifest.json.tmpl" \
    > "${STAGE_VERSION}/manifest.json"

# Channel manifest: absolute URLs into the version directory. The install
# page and the dongle pin to .../dongle/firmware/<channel>/manifest.json so
# the URL never changes; only this single file flips per channel at release
# time.
BASE_URL="https://cdn.phoneblock.net/dongle/firmware/${VERSION}"
sed -e "s/@VERSION@/${VERSION}/g" \
    -e "s/@APP_SHA256@/${APP_SHA256}/g" \
    -e "s|@SIGNATURE@|${APP_SIG}|g" \
    -e "s|\"path\": \"|\"path\": \"${BASE_URL}/|g" \
    "${SCRIPT_DIR}/manifest.json.tmpl" \
    > "${STAGE_CHAN}/manifest.json"

# Plain pointer file for tooling that wants just the version string.
printf '{ "version": "%s" }\n' "$VERSION" > "${STAGE_CHAN}/version.json"

echo "Staged: $STAGE_VERSION"
echo "Staged: $STAGE_CHAN → channels: ${CHANNELS[*]}"

if [[ "$MODE" == "stage" ]]; then
    exit 0
fi

# ---------------------------------------------------------------------------
# 4. Upload version directory, then atomically flip each target channel.
# ---------------------------------------------------------------------------
REMOTE_VERSION="${CDN_FIRMWARE}/${VERSION}"

# The CDN host accepts sftp/scp only — no shell access. Parent dirs are
# created via sftp (-mkdir ignores "already exists"). The version dir is
# explicitly mkdir'd so the subsequent scp always sees an existing target —
# that avoids the `scp -r DIR DEST`-footgun where the result depends on
# whether DEST exists (creates DEST.flat the first time, DEST/DIR/... the
# second time).
sftp_batch <<SFTP
-mkdir ${CDN_BASE}
-mkdir ${CDN_FIRMWARE}
-mkdir ${REMOTE_VERSION}
SFTP

# Local shell expands the glob; scp ships each file into REMOTE_VERSION
# directly — no subdirectory wrapping.
run scp "${STAGE_VERSION}"/* "${CDN_HOST}:${REMOTE_VERSION}/"

# ---- localized i18n assets, co-located under this version dir -------------
# One release, one location: the committed announcement recordings + mail/UI
# packs are published to firmware/<version>/i18n/, so the device fetches them
# from the same version tree as its .bin (see i18n_sync.c). This is the single
# deploy step for #460 — signs with the same OTA key, no extra services
# required (recordings and reviewed text packs are committed in
# scripts/i18n/). If it fails, the firmware release still completes — publish
# i18n separately with scripts/i18n-assets.sh --version ${VERSION}.
I18N_ARGS=(--version "${VERSION}")
[[ "$MODE" == "dry-run" ]] && I18N_ARGS+=(--dry-run)
if ! "${SCRIPT_DIR}/i18n-assets.sh" "${I18N_ARGS[@]}"; then
    echo "WARNING: i18n asset publish failed for ${VERSION}. The firmware is" >&2
    echo "         released; run scripts/i18n-assets.sh --version ${VERSION}" >&2
    echo "         once DeepL/ElevenLabs/signing keys are available." >&2
fi

# Atomic flip per channel: upload to *.tmp, then rename over the live file.
# OpenSSH's sftp uses posix-rename, which atomically replaces the target. The
# window in which <channel>/manifest.json doesn't exist is zero (after the
# first release) or one upload (the very first release of that channel, where
# there's nothing to overwrite yet).
for CH in "${CHANNELS[@]}"; do
    REMOTE_CH="${CDN_FIRMWARE}/${CH}"
    sftp_batch <<SFTP
-mkdir ${REMOTE_CH}
put ${STAGE_CHAN}/manifest.json ${REMOTE_CH}/manifest.json.tmp
put ${STAGE_CHAN}/version.json  ${REMOTE_CH}/version.json.tmp
rename ${REMOTE_CH}/manifest.json.tmp ${REMOTE_CH}/manifest.json
rename ${REMOTE_CH}/version.json.tmp  ${REMOTE_CH}/version.json
SFTP
done

echo
echo "Released ${VERSION}."
echo "  Pinned: https://cdn.phoneblock.net/dongle/firmware/${VERSION}/manifest.json"
for CH in "${CHANNELS[@]}"; do
    echo "  ${CH}: https://cdn.phoneblock.net/dongle/firmware/${CH}/manifest.json"
done

# Push the tag now that the CDN has accepted the upload. Pushing
# only after a successful release means a failed run (build error,
# signing failure, network drop during upload) leaves the tag local,
# so the user can `git tag -d ${DESCRIBE}` and re-tag the same
# version without a force-push to the remote.
run git push origin "$DESCRIBE"
