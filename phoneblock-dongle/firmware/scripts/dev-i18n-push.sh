#!/usr/bin/env bash
#
# Push localized resource bundles straight to a dongle, no CDN round-trip.
#
#   ./scripts/dev-i18n-push.sh [--host answerbot] [--langs "de en"] [--kinds "ui mail dialog"]
#
# Kinds: ui and mail (the default), and dialog — the interactive answer bot's
# clip set (#429): the concatenated clip file, then its clip table from the
# staged manifest. The dialog kind needs the dongle in dialog mode
# (ab_mode=dialog; the device answers 409 otherwise) and a locale that has a
# complete set in i18n/audio/dialog/ (German today).
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
# The pushed bundles stay until the device's next i18n sync — after a reboot,
# an OTA, or a UI language switch — which puts it back onto the published
# bundles. Re-run this after flashing a new dev build.

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
        -h|--help) sed -n '2,28p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0;;
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

# The first i18n sync runs 30 s (after an OTA) to 3 min after boot and would
# overwrite anything pushed before it. Wait for it to finish first.
i18n_settled() {
    curl -sf -m 10 "http://${HOST}/api/status" | python3 -c '
import sys, json
i = json.load(sys.stdin)["i18n"]
sys.exit(0 if i["ever_ran"] and not i["running"] else 1)'
}
if ! i18n_settled; then
    echo "==> waiting for the boot-time i18n sync on ${HOST}"
    for _ in $(seq 1 60); do
        sleep 5
        i18n_settled && break
    done
fi

STAGE="$(mktemp -d)"
trap 'rm -rf "${STAGE}"' EXIT

echo "==> building bundles with the release generator"
"${HERE}/i18n-assets.sh" --langs "${LANGS}" --no-upload --stage "${STAGE}" >/dev/null

# POST one file; prints the device's answer (its message on a refusal).
push() {  # kind lang file
    local out code
    out="$(mktemp)"
    code="$(curl -s -m 60 -o "$out" -w '%{http_code}' -X POST \
            "http://${HOST}/api/dev/i18n?kind=$1&lang=$2" --data-binary "@$3")"
    printf '    %s/%s: ' "$1" "$2"
    python3 -c '
import sys, json
d = json.load(open(sys.argv[1]))
print("%d bytes -> %s" % (d["bytes"], d["path"]) if d.get("ok")
      else "REFUSED (HTTP %s): %s" % (sys.argv[2], d.get("message")))' "$out" "$code"
    rm -f "$out"
    [[ "$code" == 200 ]]
}

for lang in ${LANGS}; do
    for kind in ${KINDS}; do
        case "$kind" in
            ui)     file="${STAGE}/assets/ui/lang-${lang}.json";;
            mail)   file="${STAGE}/assets/mail/mail-${lang}.json";;
            dialog) file="${STAGE}/assets/audio/dialog-${lang}.alaw";;
            *) echo "unknown kind: ${kind}" >&2; exit 2;;
        esac
        if [[ ! -f "$file" ]]; then
            echo "    ${kind}/${lang}: no bundle generated, skipped"
            continue
        fi
        if [[ "$kind" != dialog ]]; then
            push "$kind" "$lang" "$file"
            continue
        fi
        # The audio first (it drops the device's index), then the clip table
        # from the staged manifest, which makes the set usable.
        index="${STAGE}/dialog-index-${lang}.json"
        jq --arg l "$lang" '{($l): {dialog: .assets[$l].dialog}}' \
            "${STAGE}/assets/manifest.json" > "$index"
        push dialog "$lang" "$file" && push dialog-index "$lang" "$index"
    done
done

echo "==> done — reload the dongle web UI to pick the strings up"
