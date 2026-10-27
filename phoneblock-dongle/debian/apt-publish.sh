#!/bin/sh
# Adds the packages of one dongle-v* release to the APT repository and
# regenerates its signed indices. Run by .github/workflows/dongle-linux.yml.
#
# Usage: apt-publish.sh REPO_DIR TAG ARTIFACT_DIR
#
#   REPO_DIR      the repository tree (checkout of the gh-pages branch, apt/)
#   TAG           the release tag, e.g. dongle-v1.7.0-beta4
#   ARTIFACT_DIR  one subdirectory per build, named <suite>-<arch>, with .deb files
#
# Channels as in firmware/scripts/release.sh: a pre-release tag (anything
# after a "-") goes to component "beta" only, a release to "stable" and
# "beta", so beta never lags behind stable. Signs with the only secret key in
# the gpg keyring; APT_SIGNING_PASSPHRASE unlocks it.
set -eu

REPO=$1
TAG=$2
ART=$3

SUITES="bookworm trixie"
ARCHS="amd64 arm64 armhf"
case "${TAG#dongle-v}" in
*-*) COMPONENTS="beta" ;;
*)   COMPONENTS="stable beta" ;;
esac

mkdir -p "$REPO"
for build in "$ART"/*/; do
    suite=$(basename "$build")
    suite=${suite%%-*}
    for component in $COMPONENTS; do
        mkdir -p "$REPO/pool/$suite/$component"
        for deb in "$build"*.deb; do
            case "$deb" in *-dbgsym_*) continue ;; esac
            cp "$deb" "$REPO/pool/$suite/$component/"
        done
    done
done

cd "$REPO"
sign() {   # sign MODE OUTPUT INPUT
    printf '%s' "${APT_SIGNING_PASSPHRASE:-}" | gpg --batch --yes \
        --pinentry-mode loopback --passphrase-fd 0 "$1" -o "$2" "$3"
}
for suite in $SUITES; do
    for component in stable beta; do
        for arch in $ARCHS; do
            index="dists/$suite/$component/binary-$arch"
            mkdir -p "$index"
            if [ -d "pool/$suite/$component" ]; then
                apt-ftparchive --arch "$arch" packages "pool/$suite/$component" \
                    > "$index/Packages"
            else
                : > "$index/Packages"
            fi
            gzip -9 -n -k -f "$index/Packages"
        done
    done
    rm -f "dists/$suite/Release" "dists/$suite/InRelease" "dists/$suite/Release.gpg"
    apt-ftparchive \
        -o APT::FTPArchive::Release::Origin=PhoneBlock \
        -o APT::FTPArchive::Release::Label="PhoneBlock Dongle" \
        -o APT::FTPArchive::Release::Suite="$suite" \
        -o APT::FTPArchive::Release::Codename="$suite" \
        -o APT::FTPArchive::Release::Architectures="$ARCHS" \
        -o APT::FTPArchive::Release::Components="stable beta" \
        release "dists/$suite" > "dists/$suite/Release.tmp"
    mv "dists/$suite/Release.tmp" "dists/$suite/Release"
    sign --clearsign "dists/$suite/InRelease" "dists/$suite/Release"
    sign --detach-sign "dists/$suite/Release.gpg" "dists/$suite/Release"
done

# The key users install (debian/README.md), exported from the signing key.
gpg --batch --yes --export -o phoneblock-dongle.gpg
gpg --batch --yes --armor --export -o phoneblock-dongle.asc
