#!/usr/bin/env bash
#
# Mirror esp-web-tools to cdn.phoneblock.net/dongle/installer/.
#
# Run this once per esp-web-tools version bump. The install page on
# phoneblock.net loads /dongle/installer/install-button.js, which redirects
# (via symlink) to the currently-pinned version directory.
#
#   ./scripts/mirror-installer-deps.sh           # use latest from npm
#   ./scripts/mirror-installer-deps.sh 10.4.0    # pin a specific version
#

set -euo pipefail
set -x

if [[ $# -gt 1 ]]; then
    echo "Usage: $0 [esp-web-tools-version]" >&2
    exit 2
fi

if [[ $# -eq 1 ]]; then
    VERSION="$1"
else
    VERSION="$(npm view esp-web-tools version)"
    echo "Latest esp-web-tools on npm: ${VERSION}"
fi
CDN_HOST="haumac@cdn.phoneblock.net"
CDN_BASE="/public_html/cdn/dongle"
CDN_INSTALLER="${CDN_BASE}/installer"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

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

# CDN host accepts sftp/scp only — no shell access. Create parent dirs via
# sftp's -mkdir (silently ignores "already exists"), bulk-upload via scp -r,
# then atomically flip the unversioned 'esp-web-tools' symlink.
sftp -b - "$CDN_HOST" <<SFTP
-mkdir ${CDN_BASE}
-mkdir ${CDN_INSTALLER}
SFTP

scp -r "${WORK}/package/dist/web" "${CDN_HOST}:${REMOTE_VER}"

# Drop the CORS .htaccess at the dongle/ root. Apache inherits it into
# firmware/ and installer/, which is what the web installer needs to fetch
# install-button.js as an ES module and the manifest/firmware bins via fetch().
scp "${SCRIPT_DIR}/htaccess.cors" "${CDN_HOST}:${CDN_BASE}/.htaccess"

sftp -b - "$CDN_HOST" <<SFTP
-rm ${CDN_INSTALLER}/esp-web-tools.new
symlink esp-web-tools-${VERSION} ${CDN_INSTALLER}/esp-web-tools.new
rename ${CDN_INSTALLER}/esp-web-tools.new ${CDN_INSTALLER}/esp-web-tools
SFTP

echo "Mirrored esp-web-tools ${VERSION}."
echo "  Pinned: https://cdn.phoneblock.net/dongle/installer/esp-web-tools-${VERSION}/install-button.js"
echo "  Latest: https://cdn.phoneblock.net/dongle/installer/esp-web-tools/install-button.js"
