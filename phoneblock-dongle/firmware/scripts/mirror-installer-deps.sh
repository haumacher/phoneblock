#!/usr/bin/env bash
#
# Mirror esp-web-tools to cdn.phoneblock.net/dongle/installer/.
#
# Run this once per esp-web-tools version bump. The install page on
# phoneblock.net loads /dongle/installer/install-button.js, which redirects
# (via symlink) to the currently-pinned major version directory.
#
#   ./scripts/mirror-installer-deps.sh 10.4.0
#

set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <esp-web-tools-version>" >&2
    exit 2
fi

VERSION="$1"
CDN_HOST="haumac@cdn.phoneblock.net"
CDN_INSTALLER="/public_html/cdn/dongle/installer"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

cd "$WORK"
npm pack "esp-web-tools@${VERSION}" >/dev/null
tar -xzf "esp-web-tools-${VERSION}.tgz"

if [[ ! -d package/dist/web ]]; then
    echo "ERROR: package/dist/web not found in esp-web-tools-${VERSION}" >&2
    exit 1
fi

REMOTE_VER="${CDN_INSTALLER}/esp-web-tools-${VERSION}"
ssh "$CDN_HOST" "mkdir -p '${REMOTE_VER}'"
scp -r package/dist/web/. "${CDN_HOST}:${REMOTE_VER}/"

# Flip the unversioned symlink so the install page picks up the new version.
ssh "$CDN_HOST" "
    set -e
    cd '${CDN_INSTALLER}'
    ln -sfn 'esp-web-tools-${VERSION}' esp-web-tools.new
    mv -Tf esp-web-tools.new esp-web-tools
"

echo "Mirrored esp-web-tools ${VERSION}."
echo "  Pinned: https://cdn.phoneblock.net/dongle/installer/esp-web-tools-${VERSION}/install-button.js"
echo "  Latest: https://cdn.phoneblock.net/dongle/installer/esp-web-tools/install-button.js"
