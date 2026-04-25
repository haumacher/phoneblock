#!/usr/bin/env bash
#
# Mirror esp-web-tools to cdn.phoneblock.net/dongle/installer/.
#
# Run this once per esp-web-tools version bump. The install page on
# phoneblock.net loads /dongle/installer/esp-web-tools/install-button.js
# directly — no symlink layer, since the host's SFTP subsystem silently
# rejects symlink creation.
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
REMOTE_DIR="${CDN_INSTALLER}/esp-web-tools"
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

# CDN host accepts sftp/scp only — no shell access. Create the target dir
# via sftp's -mkdir (silently ignores "already exists") so the subsequent scp
# always sees an existing destination — that side-steps the
# `scp -r DIR DEST`-footgun where the result depends on whether DEST exists.
sftp -b - "$CDN_HOST" <<SFTP
-mkdir ${CDN_BASE}
-mkdir ${CDN_INSTALLER}
-mkdir ${REMOTE_DIR}
SFTP

# dist/web is a flat list of files — local shell expands the glob, scp ships
# each file directly into REMOTE_DIR. No subdirectory wrapping. Files from a
# previous version with the same name get overwritten; chunk filenames carry
# a content hash so old ones (e.g. install-dialog-C5LjR_e6.js from one bundle
# vs install-dialog-aB3xZ_q1.js from the next) accumulate harmlessly until
# the next manual cleanup.
scp "${WORK}"/package/dist/web/* "${CDN_HOST}:${REMOTE_DIR}/"

# Drop the CORS .htaccess at the dongle/ root. Apache inherits it into
# firmware/ and installer/, which is what the web installer needs to fetch
# install-button.js as an ES module and the manifest/firmware bins via fetch().
scp "${SCRIPT_DIR}/htaccess.cors" "${CDN_HOST}:${CDN_BASE}/.htaccess"

echo "Mirrored esp-web-tools ${VERSION}."
echo "  https://cdn.phoneblock.net/dongle/installer/esp-web-tools/install-button.js"
