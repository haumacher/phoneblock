#!/bin/sh
# The Debian package carries the firmware version (debian/pb-version): the
# mapping must round-trip and keep the release order under dpkg's rules.
set -eu

PB_VERSION="$(dirname "$0")/../../debian/pb-version"

fail() { echo "FAIL: $*" >&2; exit 1; }

check() {   # firmware version, expected Debian version
    debian="$("$PB_VERSION" to-debian "$1")"
    [ "$debian" = "$2" ] || fail "to-debian $1: got $debian, want $2"
    back="$("$PB_VERSION" from-debian "$debian")"
    [ "$back" = "$1" ] || fail "from-debian $debian: got $back, want $1"
}

check 1.7.0                     1.7.0
check 1.7.0-beta4               1.7.0~beta4
check 1.6.0-rc1                 1.6.0~rc1
check 1.7.0-beta4-12-g092776f6  1.7.0~beta4+12.g092776f6
check 1.7.0-12-gabc             1.7.0+12.gabc
check 1.7.0-beta4-dirty         1.7.0~beta4+dirty
check 1.7.0-beta4-12-gabc-dirty 1.7.0~beta4+12.gabc.dirty
check 1.7.0-dirty               1.7.0+dirty

if command -v dpkg >/dev/null; then
    older() { dpkg --compare-versions "$1" lt "$2" || fail "$1 not < $2"; }
    older 1.6.0                  1.7.0~beta1
    older 1.7.0~beta1            1.7.0~beta4
    older 1.7.0~beta4            1.7.0~beta4+9.gabc
    older 1.7.0~beta4+9.gabc     1.7.0~beta4+12.gabc
    older 1.7.0~beta4+12.gabc    1.7.0
    older 1.7.0                  1.7.0+3.gabc
fi

echo "test_deb_version: all tests passed"
