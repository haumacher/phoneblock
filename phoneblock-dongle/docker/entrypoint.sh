#!/bin/sh
# Starts the dongle service in the container, in the foreground, as the
# package's system user, as the systemd unit does on a Debian install.
#
# /var/lib/phoneblock is a volume: a fresh one, or a host directory owned by
# someone else, so the state directory and the settings file are prepared on
# every start, the way debian/phoneblock-dongle.postinst does on installation.
# Arguments are passed on to phoneblock-dongle; set the web port and address
# with PHONEBLOCK_WEB_PORT and PHONEBLOCK_WEB_BIND instead of --web/--web-bind,
# so the health check finds the web UI.
set -eu

state=/var/lib/phoneblock
config="$state/dongle.conf"

if [ "$(id -u)" -ne 0 ]; then
    # Started with --user: no ownership to fix, run as that user.
    exec /usr/bin/phoneblock-dongle --config "$config" "$@"
fi

install -d -o phoneblock -g phoneblock -m 0750 "$state"
if [ ! -e "$config" ]; then
    install -o phoneblock -g phoneblock -m 0600 \
        /usr/share/phoneblock-dongle/dongle.conf.example "$config"
fi
# Files from a host directory or an older image may belong to another user.
chown -R phoneblock:phoneblock "$state"

exec setpriv --reuid=phoneblock --regid=phoneblock --init-groups \
    /usr/bin/phoneblock-dongle --config "$config" "$@"
