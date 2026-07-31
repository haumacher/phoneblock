#!/usr/bin/env bash
#
# Push localized resource bundles straight to a dongle, no CDN round-trip.
#
#   ./scripts/dev-i18n-push.sh [--host answerbot] [--langs "de en"] [--kinds "ui mail"]
#
# Why this exists: i18n_sync.c derives the CDN bundle path from the running
# firmware's *release tag* (git-describe suffix stripped). A dev build
# therefore looks for the bundle of the last release — which is missing
# exactly the keys the dev build just added, so new UI strings render as raw
# keys. Publishing a bundle per test build is not an option (that is a
# release step). This pushes the bundles the device would have downloaded.
#
# The bundles are produced by scripts/i18n-assets.sh --no-upload, i.e. the
# same generator the release uses, so what lands on the device is
# byte-identical to what the CDN would serve. Don't strip the ARBs by hand
# here — the strip rule (keep @@locale, drop @key metadata) lives there.
#
# Requires dev mode on the target (POST dev_mode=1 to /api/config); the
# upload route 404s otherwise. Dev mode also stops i18n_sync, so the pushed
# bundles are not replaced by the next daily pass. Turn it off with
# dev_mode=0 when you are done, then trigger a sync to get back onto the
# published bundles.

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOST="answerbot"
LANGS=""
KINDS="ui mail"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --host)  HOST="$2";  shift 2;;
        --langs) LANGS="$2"; shift 2;;
        --kinds) KINDS="$2"; shift 2;;
        -h|--help) sed -n '2,25p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0;;
        *) echo "unknown option: $1" >&2; exit 2;;
    esac
done

# Default to the locale the device is actually running, so the common case
# ("I changed a German string, show me") needs no arguments at all.
if [[ -z "$LANGS" ]]; then
    LANGS="$(curl -sf -m 10 "http://${HOST}/api/status" \
             | python3 -c 'import sys,json; print(json.load(sys.stdin).get("ui_lang") or "de")')"
    echo "==> target locale from ${HOST}: ${LANGS}"
fi

if ! curl -sf -m 10 -X POST "http://${HOST}/api/dev/i18n?kind=ui&lang=zz" \
        -d '{}' -o /dev/null 2>/dev/null; then
    # A 400 (bad locale) means the route is live; a 404 means dev mode is off.
    code="$(curl -s -m 10 -o /dev/null -w '%{http_code}' -X POST \
            "http://${HOST}/api/dev/i18n?kind=ui&lang=zz" -d '{}')"
    if [[ "$code" == "404" ]]; then
        echo "!!! dev mode is off on ${HOST}. Enable it with:" >&2
        echo "      curl -X POST http://${HOST}/api/config -d dev_mode=1" >&2
        exit 1
    fi
fi

STAGE="$(mktemp -d)"
trap 'rm -rf "${STAGE}"' EXIT

echo "==> building bundles with the release generator"
"${HERE}/i18n-assets.sh" --langs "${LANGS}" --no-upload --stage "${STAGE}" >/dev/null

for lang in ${LANGS}; do
    for kind in ${KINDS}; do
        case "$kind" in
            ui)   file="${STAGE}/assets/ui/lang-${lang}.json";;
            mail) file="${STAGE}/assets/mail/mail-${lang}.json";;
            *) echo "unknown kind: ${kind}" >&2; exit 2;;
        esac
        if [[ ! -f "$file" ]]; then
            echo "    ${kind}/${lang}: no bundle generated, skipped"
            continue
        fi
        printf '    %s/%s: ' "${kind}" "${lang}"
        curl -sf -m 60 -X POST \
             "http://${HOST}/api/dev/i18n?kind=${kind}&lang=${lang}" \
             --data-binary "@${file}" \
        | python3 -c 'import sys,json; d=json.load(sys.stdin); print("%d bytes -> %s" % (d["bytes"], d["path"]))'
    done
done

echo "==> done — reload the dongle web UI to pick the strings up"
