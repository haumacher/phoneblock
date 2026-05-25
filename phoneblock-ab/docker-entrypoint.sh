#!/bin/sh
#
# Entry point for the phoneblock answer bot container.
#
# Honors PUID and PGID environment variables to adjust the runtime user to
# match the host owner of bind-mounted directories (typical for NAS setups,
# where /opt/phoneblock/recordings is mounted from the host). If unset, the
# image default (999) is used.
#
set -e

PUID="${PUID:-999}"
PGID="${PGID:-999}"

current_uid="$(id -u phoneblock)"
current_gid="$(getent group phoneblock | cut -d: -f3)"

if [ "$PGID" != "$current_gid" ]; then
    groupmod -o -g "$PGID" phoneblock
fi
if [ "$PUID" != "$current_uid" ]; then
    usermod -o -u "$PUID" phoneblock
fi

# The recordings directory may be a bind-mount whose host owner does not match
# the in-container user; ensure the (possibly remapped) phoneblock user can
# write into it. Non-recursive on purpose to avoid touching existing recordings.
chown phoneblock:phoneblock /opt/phoneblock/recordings 2>/dev/null || true

exec gosu phoneblock java -jar /opt/phoneblock/bin/phoneblock-ab.jar "$@"
