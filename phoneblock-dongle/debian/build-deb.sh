#!/bin/sh
# Builds the Debian package with the firmware's version.
#
# The version comes from the same `git describe --match "dongle-v*"` as the
# ESP32 firmware (firmware/CMakeLists.txt), so the package, the binary's
# User-Agent and the ESP32 image of one commit carry one version. Debian
# syntax differs only in its separators:
#
#   dongle-v1.7.0                 -> 1.7.0
#   dongle-v1.7.0-beta4           -> 1.7.0~beta4          (sorts before 1.7.0)
#   dongle-v1.7.0-beta4-12-gabc   -> 1.7.0~beta4+12.gabc  (after beta4)
#   ...-dirty                     -> ...+dirty / ....dirty
#
# debian/changelog is generated here (not tracked in git); further arguments
# go to dpkg-buildpackage, e.g. `debian/build-deb.sh -b` for a binary-only
# build. dpkg-buildpackage writes its results next to the source directory,
# i.e. into the repository root; they are moved to phoneblock-dongle/dist/.
#
# Usage, in phoneblock-dongle/:  debian/build-deb.sh [dpkg-buildpackage args]
set -eu

cd "$(dirname "$0")/.."

DESCRIBE="$(git describe --tags --match 'dongle-v*' --dirty)" || {
    echo "ERROR: no dongle-v* tag reachable; cannot derive the version." >&2
    exit 1
}
VERSION="$(debian/pb-version to-debian "${DESCRIBE#dongle-v}")"
MAINTAINER="$(sed -n 's/^Maintainer: //p' debian/control)"
# The commit date, not the build date: the same commit builds the same package.
DATE="$(git log -1 --format=%cD)"

cat > debian/changelog <<EOF
phoneblock-dongle (${VERSION}) unstable; urgency=medium

  * Dongle firmware ${DESCRIBE}, built as a native Linux service.

 -- ${MAINTAINER}  ${DATE}
EOF
echo "phoneblock-dongle ${VERSION} (${DESCRIBE})"

dpkg-buildpackage -us -uc "$@"

mkdir -p dist
for f in ../phoneblock-dongle_"${VERSION}"[._]* \
         ../phoneblock-dongle-dbgsym_"${VERSION}"_*; do
    if [ -e "$f" ]; then mv -f "$f" dist/; fi
done
ls -1 dist/*"${VERSION}"*
