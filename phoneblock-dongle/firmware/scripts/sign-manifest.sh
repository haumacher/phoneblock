#!/usr/bin/env bash
#
# Sign a firmware release for the dongle's OTA path.
#
#   sign-manifest.sh <app.bin> <version>   # → prints SHA256=…  SIG=…
#
# Inputs are an app binary (phoneblock_dongle.bin) and the version string.
# Output is two `KEY=VALUE` lines on stdout that the caller — typically
# release.sh — substitutes into manifest.json.tmpl:
#
#   SHA256=<64-hex-chars>           # SHA-256 of the app binary
#   SIG=<base64>                    # ECDSA-P256-SHA256 signature, ASN.1-DER, see below
#
# Signed payload (deterministic byte string, hashed with SHA-256 by openssl):
#
#   phoneblock-dongle-ota-v1\n
#   version=<VERSION>\n
#   app_sha256=<SHA256-hex>\n
#
# The domain-separation prefix prevents the same key being abused to
# produce signatures that some other code path would accept.
#
# The private key lives in a KeePassXC database as an ECDSA-P256 PEM. We pull
# it via keepassxc-cli into a tmpfs file, sign, and shred. Required env / tools:
#
#   KEEPASS_DB    path to the .kdbx                (required)
#   KEEPASS_ENTRY entry title in the database      (default: "PhoneBlock-Dongle Signing Key")
#   KEEPASS_ATTACHMENT  attachment filename        (default: "private.pem")
#
# Master password is prompted by keepassxc-cli on stderr/tty — there is no
# unattended path on purpose.

set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "usage: $0 <app.bin> <version>" >&2
    exit 2
fi

APP_BIN="$1"
VERSION="$2"

KEEPASS_DB="${KEEPASS_DB:-}"
KEEPASS_ENTRY="${KEEPASS_ENTRY:-PhoneBlock-Dongle Signing Key}"
KEEPASS_ATTACHMENT="${KEEPASS_ATTACHMENT:-private.pem}"

if [[ -z "$KEEPASS_DB" ]]; then
    echo "ERROR: set KEEPASS_DB to the path of your .kdbx file." >&2
    exit 1
fi
if [[ ! -f "$APP_BIN" ]]; then
    echo "ERROR: app binary not found: $APP_BIN" >&2
    exit 1
fi

command -v keepassxc-cli >/dev/null || {
    echo "ERROR: keepassxc-cli not found in PATH." >&2
    exit 1
}
command -v shred >/dev/null || { echo "ERROR: shred missing" >&2; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Tmpfs for the extracted key. /dev/shm is RAM-backed on Linux; the key
# file never touches a physical disk.
TMP_KEY="$(mktemp -p /dev/shm dongle-ota-key.XXXXXX)"
cleanup() {
    # `shred -u` overwrites then unlinks. tmpfs lives in RAM, so this is
    # belt-and-braces, but cheap.
    shred -u "$TMP_KEY" 2>/dev/null || true
}
trap cleanup EXIT

# `attachment-export` writes the attachment bytes to <output>. The master
# password is read from /dev/tty by keepassxc-cli; if you're running in CI
# / over SSH without a tty, this will fail loudly — by design.
keepassxc-cli attachment-export \
    "$KEEPASS_DB" "$KEEPASS_ENTRY" "$KEEPASS_ATTACHMENT" "$TMP_KEY" >&2

# Hand off to the pure signing helper. Splitting these lets the host-side
# roundtrip test exercise the signing format without a KeePassXC database.
exec "${SCRIPT_DIR}/sign-manifest-with-key.sh" "$TMP_KEY" "$APP_BIN" "$VERSION"
