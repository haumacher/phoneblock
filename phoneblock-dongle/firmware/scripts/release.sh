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

# Versioned manifest: relative paths, served from /dongle/firmware/<version>/.
sed "s/@VERSION@/${VERSION}/g" "${SCRIPT_DIR}/manifest.json.tmpl" \
    > "${STAGE_VERSION}/manifest.json"

# Stable manifest: absolute URLs into the version directory. The install page
# pins to https://cdn.phoneblock.net/dongle/firmware/stable/manifest.json so
# the URL never changes; only this single file flips at release time.
BASE_URL="https://cdn.phoneblock.net/dongle/firmware/${VERSION}"
sed -e "s/@VERSION@/${VERSION}/g" \
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
REMOTE_STABLE_TMP="${CDN_FIRMWARE}/stable.tmp"
REMOTE_STABLE_OLD="${CDN_FIRMWARE}/stable.old"

run ssh "$CDN_HOST" "mkdir -p '${REMOTE_VERSION}' '${CDN_FIRMWARE}'"
run scp -r "${STAGE_VERSION}/." "${CDN_HOST}:${REMOTE_VERSION}/"

# Two-phase rename keeps the URL pointing at *some* valid manifest at all times.
run ssh "$CDN_HOST" "rm -rf '${REMOTE_STABLE_TMP}' && mkdir -p '${REMOTE_STABLE_TMP}'"
run scp "${STAGE_STABLE}/manifest.json" "${CDN_HOST}:${REMOTE_STABLE_TMP}/manifest.json"
run scp "${STAGE_STABLE}/version.json"  "${CDN_HOST}:${REMOTE_STABLE_TMP}/version.json"
run ssh "$CDN_HOST" "
    set -e
    rm -rf '${REMOTE_STABLE_OLD}'
    if [ -d '${REMOTE_STABLE}' ]; then mv '${REMOTE_STABLE}' '${REMOTE_STABLE_OLD}'; fi
    mv '${REMOTE_STABLE_TMP}' '${REMOTE_STABLE}'
    rm -rf '${REMOTE_STABLE_OLD}'
"

echo
echo "Released ${VERSION}."
echo "  Pinned: https://cdn.phoneblock.net/dongle/firmware/${VERSION}/manifest.json"
echo "  Stable: https://cdn.phoneblock.net/dongle/firmware/stable/manifest.json"
