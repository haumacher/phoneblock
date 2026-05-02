#!/usr/bin/env bash
#
# Build, package, and upload a PhoneBlock-Dongle firmware release.
#
#   ./scripts/release.sh             # full flow: build, stage, upload, flip stable
#   ./scripts/release.sh --stage     # build + stage to release/<version>/, no upload
#   ./scripts/release.sh --dry-run   # all of the above + print scp/ssh commands
#
# Version comes from `git describe --tags --match "dongle-v*"`. The working tree
# must be clean and HEAD must sit exactly on a tag — between-tag builds (e.g.
# 1.4.2-3-gabcdef) are rejected to keep the CDN to released versions only.

set -euo pipefail

FIRMWARE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_DIR="${FIRMWARE_DIR}/scripts"
BUILD_DIR="${FIRMWARE_DIR}/build"
RELEASE_ROOT="${FIRMWARE_DIR}/release"

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

# ---------------------------------------------------------------------------
# 2. Build firmware. ESP-IDF reads version from version.txt next to CMakeLists,
#    which lands in esp_app_desc_t.version and the /api/status payload.
# ---------------------------------------------------------------------------
echo "$VERSION" > "${FIRMWARE_DIR}/version.txt"
trap 'rm -f "${FIRMWARE_DIR}/version.txt"' EXIT

if [[ -z "${IDF_PATH:-}" ]]; then
    echo "ERROR: IDF_PATH not set. Source ESP-IDF's export.sh first." >&2
    exit 1
fi

idf.py -C "$FIRMWARE_DIR" build

# ---------------------------------------------------------------------------
# 3. Stage release artifacts into release/<version>/.
# ---------------------------------------------------------------------------
STAGE_VERSION="${RELEASE_ROOT}/${VERSION}"
STAGE_STABLE="${RELEASE_ROOT}/stable"
rm -rf "$STAGE_VERSION" "$STAGE_STABLE"
mkdir -p "$STAGE_VERSION" "$STAGE_STABLE"

cp "${BUILD_DIR}/bootloader/bootloader.bin"           "${STAGE_VERSION}/"
cp "${BUILD_DIR}/partition_table/partition-table.bin" "${STAGE_VERSION}/"
cp "${BUILD_DIR}/ota_data_initial.bin"                "${STAGE_VERSION}/"
cp "${BUILD_DIR}/phoneblock_dongle.bin"               "${STAGE_VERSION}/"

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

# Stable manifest: absolute URLs into the version directory. The install page
# pins to https://cdn.phoneblock.net/dongle/firmware/stable/manifest.json so
# the URL never changes; only this single file flips at release time.
BASE_URL="https://cdn.phoneblock.net/dongle/firmware/${VERSION}"
sed -e "s/@VERSION@/${VERSION}/g" \
    -e "s/@APP_SHA256@/${APP_SHA256}/g" \
    -e "s|@SIGNATURE@|${APP_SIG}|g" \
    -e "s|\"path\": \"|\"path\": \"${BASE_URL}/|g" \
    "${SCRIPT_DIR}/manifest.json.tmpl" \
    > "${STAGE_STABLE}/manifest.json"

# Plain pointer file for tooling that wants just the version string.
printf '{ "version": "%s" }\n' "$VERSION" > "${STAGE_STABLE}/version.json"

echo "Staged: $STAGE_VERSION"
echo "Staged: $STAGE_STABLE"

if [[ "$MODE" == "stage" ]]; then
    exit 0
fi

# ---------------------------------------------------------------------------
# 4. Upload version directory, then atomically flip stable/.
# ---------------------------------------------------------------------------
REMOTE_VERSION="${CDN_FIRMWARE}/${VERSION}"
REMOTE_STABLE="${CDN_FIRMWARE}/stable"

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
-mkdir ${REMOTE_STABLE}
SFTP

# Local shell expands the glob; scp ships each file into REMOTE_VERSION
# directly — no subdirectory wrapping.
run scp "${STAGE_VERSION}"/* "${CDN_HOST}:${REMOTE_VERSION}/"

# Atomic flip: upload to *.tmp, then rename over the live file. OpenSSH's sftp
# uses posix-rename, which atomically replaces the target. The window in which
# stable/manifest.json doesn't exist is zero (after the first release) or one
# upload (on the very first release, where there's nothing to overwrite yet).
sftp_batch <<SFTP
put ${STAGE_STABLE}/manifest.json ${REMOTE_STABLE}/manifest.json.tmp
put ${STAGE_STABLE}/version.json  ${REMOTE_STABLE}/version.json.tmp
rename ${REMOTE_STABLE}/manifest.json.tmp ${REMOTE_STABLE}/manifest.json
rename ${REMOTE_STABLE}/version.json.tmp  ${REMOTE_STABLE}/version.json
SFTP

echo
echo "Released ${VERSION}."
echo "  Pinned: https://cdn.phoneblock.net/dongle/firmware/${VERSION}/manifest.json"
echo "  Stable: https://cdn.phoneblock.net/dongle/firmware/stable/manifest.json"
