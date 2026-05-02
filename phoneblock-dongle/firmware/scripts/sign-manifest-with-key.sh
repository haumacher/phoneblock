#!/usr/bin/env bash
#
# Pure signing helper — takes a private-key PEM directly, no KeePassXC.
# Used by sign-manifest.sh after the KeePassXC pull, and by the
# host-side roundtrip test.
#
#   sign-manifest-with-key.sh <private.pem> <app.bin> <version>
#
# Stdout (two lines):
#   SHA256=<64-hex-chars>
#   SIG=<base64>
#
# See sign-manifest.sh for the signed-payload format.

set -euo pipefail

if [[ $# -ne 3 ]]; then
    echo "usage: $0 <private.pem> <app.bin> <version>" >&2
    exit 2
fi

KEY="$1"
APP_BIN="$2"
VERSION="$3"

if [[ ! -f "$KEY"     ]]; then echo "ERROR: key not found: $KEY"      >&2; exit 1; fi
if [[ ! -f "$APP_BIN" ]]; then echo "ERROR: bin not found: $APP_BIN"  >&2; exit 1; fi
command -v openssl >/dev/null || { echo "ERROR: openssl missing" >&2; exit 1; }

TMP_PAYLOAD="$(mktemp)"
TMP_SIG="$(mktemp)"
trap 'rm -f "$TMP_PAYLOAD" "$TMP_SIG"' EXIT

APP_SHA256="$(sha256sum "$APP_BIN" | awk '{print $1}')"

{
    printf 'phoneblock-dongle-ota-v1\n'
    printf 'version=%s\n' "$VERSION"
    printf 'app_sha256=%s\n' "$APP_SHA256"
} > "$TMP_PAYLOAD"

openssl dgst -sha256 -sign "$KEY" -out "$TMP_SIG" "$TMP_PAYLOAD"
SIG_B64="$(base64 -w0 < "$TMP_SIG")"

printf 'SHA256=%s\n' "$APP_SHA256"
printf 'SIG=%s\n'    "$SIG_B64"
