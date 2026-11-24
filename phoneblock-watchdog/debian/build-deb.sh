#!/bin/sh
# Builds the Debian package of the watchdog.
#
#   debian/build-deb.sh <version> <arch> [<out-dir>]
#
# <arch> is a Debian architecture (amd64, arm64, armhf). The binary is
# cross-compiled statically (CGO_ENABLED=0), so one package per architecture
# runs on every Debian/Ubuntu release. Needs go and dpkg-deb; run from any
# directory. Used by .github/workflows/watchdog-release.yml.
set -eu

if [ $# -lt 2 ]; then
    echo "Usage: $0 <version> <arch> [<out-dir>]" >&2
    exit 1
fi
VERSION=$1
ARCH=$2
DEBIAN_DIR=$(cd "$(dirname "$0")" && pwd)
SRC_DIR=$(dirname "$DEBIAN_DIR")
OUT_DIR=$(mkdir -p "${3:-$SRC_DIR/dist}" && cd "${3:-$SRC_DIR/dist}" && pwd)

case $ARCH in
amd64) GOARCH=amd64; GOARM= ;;
arm64) GOARCH=arm64; GOARM= ;;
armhf) GOARCH=arm;   GOARM=7 ;;
*) echo "Unsupported architecture: $ARCH" >&2; exit 1 ;;
esac

PKG=phoneblock-watchdog
ROOT=$(mktemp -d)
trap 'rm -rf "$ROOT"' EXIT
chmod 755 "$ROOT"

mkdir -p "$ROOT/DEBIAN" "$ROOT/usr/bin" "$ROOT/lib/systemd/system" "$ROOT/etc/default" \
    "$ROOT/usr/share/doc/$PKG"

(cd "$SRC_DIR" && CGO_ENABLED=0 GOOS=linux GOARCH=$GOARCH GOARM=$GOARM \
    go build -trimpath -ldflags "-s -w -X main.version=$VERSION" -o "$ROOT/usr/bin/$PKG" .)

install -m 644 "$DEBIAN_DIR/$PKG.service" "$ROOT/lib/systemd/system/$PKG.service"
install -m 644 "$DEBIAN_DIR/default" "$ROOT/etc/default/$PKG"
install -m 644 "$SRC_DIR/README.md" "$ROOT/usr/share/doc/$PKG/README.md"
for script in postinst prerm postrm; do
    install -m 755 "$DEBIAN_DIR/$script" "$ROOT/DEBIAN/$script"
done
echo "/etc/default/$PKG" > "$ROOT/DEBIAN/conffiles"

cat > "$ROOT/DEBIAN/control" <<EOF
Package: $PKG
Version: $VERSION
Architecture: $ARCH
Maintainer: PhoneBlock <https://github.com/haumacher/phoneblock>
Section: net
Priority: optional
Depends: ufw
Installed-Size: $(du -sk "$ROOT" --exclude=DEBIAN | cut -f1)
Homepage: https://phoneblock.net
Description: PhoneBlock watchdog for the Apache access log
 Tails the Apache access log of the PhoneBlock server, counts the IP
 addresses that request the access challenge without solving it and
 blocks them temporarily with ufw.
EOF

dpkg-deb --root-owner-group --build "$ROOT" "$OUT_DIR/${PKG}_${VERSION}_${ARCH}.deb"
