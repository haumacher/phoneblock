#!/bin/sh
# Device configuration of the ingress test service: creates dongle.conf from
# the package example on first start and sets the PhoneBlock server
# (pb_url) from PHONEBLOCK_BASE_URL on every start, before the service runs
# (it rewrites the file while running). SIP stays unconfigured. Then hands
# over to the release image's entrypoint.
set -eu

state=/var/lib/phoneblock
config="$state/dongle.conf"

install -d -m 0750 "$state"
if [ ! -e "$config" ]; then
    install -m 0600 /usr/share/phoneblock-dongle/dongle.conf.example "$config"
fi
if [ -n "${PHONEBLOCK_BASE_URL:-}" ]; then
    url=$(printf '%s' "$PHONEBLOCK_BASE_URL" | sed 's|/*$||')
    if grep -q '^pb_url=' "$config"; then
        sed -i "s|^pb_url=.*|pb_url=$url|" "$config"
    else
        sed -i "/^\[phoneblock\]/a pb_url=$url" "$config"
    fi
    echo "ingress-test: pb_url=$url"
fi

exec /usr/local/bin/phoneblock-dongle-entrypoint "$@"
