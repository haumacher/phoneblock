#!/usr/bin/env bash
#
# Print the per-chip OTA manifest a dongle polls at
# <channel>/ota-<target>.json (see firmware_update.c / ota_manifest.h):
#
#   ota-manifest.sh <version> <chipFamily> <app-url> <app-sha256> <signature>
#
# Same shape as the esp-web-tools installer manifest, reduced to one build
# with only its app part and its own integrity block — so the firmware reads
# both files with one parser, and verify-manifest.sh checks both. Kept apart
# from manifest.json because installed dongles read that one into 2048 bytes;
# every chip added there eats into that budget, a per-chip file has none.

set -euo pipefail

if [[ $# -ne 5 ]]; then
    echo "usage: $0 <version> <chipFamily> <app-url> <app-sha256> <signature>" >&2
    exit 2
fi

cat <<JSON
{
  "version": "$1",
  "builds": [
    {
      "chipFamily": "$2",
      "parts": [ { "path": "$3" } ],
      "integrity": { "app_sha256": "$4", "signature": "$5" }
    }
  ]
}
JSON
