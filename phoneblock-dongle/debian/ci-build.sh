#!/bin/sh
# Builds the binary package inside a clean Debian container, as the
# dongle-release workflow does (.github/workflows/dongle-release.yml):
#
#   docker run --rm -v "$PWD":/src -w /src/phoneblock-dongle debian:bookworm \
#       debian/ci-build.sh [--test]
#
# Runs as root in the container: installs the build dependencies from
# debian/control, optionally runs the Linux host tests (--test), and builds
# with debian/build-deb.sh. The packages end up in phoneblock-dongle/dist/.
set -eu

cd "$(dirname "$0")/.."

export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq --no-install-recommends git ca-certificates dpkg-dev >/dev/null

# The checkout belongs to the host user, not to root in the container.
git config --global --add safe.directory '*'

debian/build-deb.sh --changelog-only
apt-get build-dep -y -qq ./ >/dev/null

if [ "${1:-}" = --test ]; then
    make -C firmware/test test-linux
fi

debian/build-deb.sh -b
