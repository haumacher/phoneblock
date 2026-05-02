#!/usr/bin/env bash
#
# Verify a signed manifest against a public-key PEM. Mirrors what the
# dongle does on every CDN-pull, so this is the right tool when a
# release "doesn't update" and you suspect the signature.
#
#   verify-manifest.sh <manifest.json> <public-key.pem> [<app.bin>]
#
# Returns 0 on success, non-zero with a diagnostic on failure. Optional
# <app.bin> additionally checks that the binary's SHA-256 matches what
# the manifest claims (the dongle does the same check after download).

set -euo pipefail

if [[ $# -lt 2 || $# -gt 3 ]]; then
    echo "usage: $0 <manifest.json> <public-key.pem> [<app.bin>]" >&2
    exit 2
fi

MANIFEST="$1"
PUBKEY="$2"
APPBIN="${3:-}"

command -v jq      >/dev/null || { echo "ERROR: jq missing"      >&2; exit 1; }
command -v openssl >/dev/null || { echo "ERROR: openssl missing" >&2; exit 1; }

VERSION="$(jq -r .version "$MANIFEST")"
APP_SHA256="$(jq -r .integrity.app_sha256 "$MANIFEST")"
SIG_B64="$(jq -r .integrity.signature "$MANIFEST")"

if [[ "$VERSION"    == "null" || -z "$VERSION"    ]]; then echo "ERROR: manifest has no .version"               >&2; exit 1; fi
if [[ "$APP_SHA256" == "null" || -z "$APP_SHA256" ]]; then echo "ERROR: manifest has no .integrity.app_sha256"  >&2; exit 1; fi
if [[ "$SIG_B64"    == "null" || -z "$SIG_B64"    ]]; then echo "ERROR: manifest has no .integrity.signature"   >&2; exit 1; fi

TMP_PAYLOAD="$(mktemp)"; TMP_SIG="$(mktemp)"
trap 'rm -f "$TMP_PAYLOAD" "$TMP_SIG"' EXIT

# Reconstruct the same byte string sign-manifest.sh signed.
{
    printf 'phoneblock-dongle-ota-v1\n'
    printf 'version=%s\n' "$VERSION"
    printf 'app_sha256=%s\n' "$APP_SHA256"
} > "$TMP_PAYLOAD"

printf '%s' "$SIG_B64" | base64 -d > "$TMP_SIG"

if openssl dgst -sha256 -verify "$PUBKEY" -signature "$TMP_SIG" "$TMP_PAYLOAD" >/dev/null; then
    echo "signature OK (version=${VERSION})"
else
    echo "ERROR: signature does NOT verify against $PUBKEY" >&2
    exit 1
fi

if [[ -n "$APPBIN" ]]; then
    ACTUAL="$(sha256sum "$APPBIN" | awk '{print $1}')"
    if [[ "$ACTUAL" == "$APP_SHA256" ]]; then
        echo "app sha256 matches manifest"
    else
        echo "ERROR: app binary sha256 ($ACTUAL) != manifest ($APP_SHA256)" >&2
        exit 1
    fi
fi
