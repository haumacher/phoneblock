#!/bin/sh
set -eu

binary=../build/linux-host/phoneblock-dongle
tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT HUP INT TERM
config="$tmpdir/dongle.conf"

cat > "$config" <<'EOF'
[phoneblock]
sip_host=127.0.0.1
sip_port=5060
sip_user=620
sip_pass=test
sip_lport=39123
pb_url=https://phoneblock.net/phoneblock
EOF

# Exactly 1: a crash (139), a missing binary (127) or a loader error must not
# pass as "rejected".
status=0
PHONEBLOCK_CONFIG_FILE="$config" "$binary" --check-config || status=$?
if [ "$status" -ne 1 ]; then
    printf 'FAIL: incomplete config: expected exit 1, got %s\n' "$status" >&2
    exit 1
fi

cat >> "$config" <<'EOF'
pb_token=test-token
EOF

PHONEBLOCK_CONFIG_FILE="$config" "$binary" --check-config
PHONEBLOCK_CONFIG_FILE="$config" "$binary" --probe-sip

# A root run on a fresh config persists the minted device id; the rewritten
# file must keep its owner, or the service account loses its settings.
if [ "$(id -u)" -eq 0 ]; then
    owned="$tmpdir/owned.conf"
    grep -v '^device_id=' "$config" > "$owned"
    chown 65534:65534 "$owned"
    PHONEBLOCK_CONFIG_FILE="$owned" "$binary" --check-config
    grep -q '^device_id=' "$owned" || { echo 'FAIL: device id not persisted' >&2; exit 1; }
    if [ "$(stat -c %u:%g "$owned")" != 65534:65534 ]; then
        echo "FAIL: root run changed the owner to $(stat -c %u:%g "$owned")" >&2
        exit 1
    fi
fi
# A web port that is already taken must stop the service with an error, not
# leave it running without its web UI. Unconfigured (no token, no SIP user),
# so neither instance talks to the network. Starting the service needs its
# state directory (PHONEBLOCK_DATA_DIR, /var/lib/phoneblock), so root only,
# as in the CI container.
if [ "$(id -u)" -eq 0 ]; then
unconfigured="$tmpdir/unconfigured.conf"
printf '[phoneblock]\nsip_lport=39124\n' > "$unconfigured"
port_file="$tmpdir/web.port"
PHONEBLOCK_CONFIG_FILE="$unconfigured" "$binary" --web 0 --web-bind 127.0.0.1 \
    --web-port-file "$port_file" >"$tmpdir/first.log" 2>&1 &
first=$!
for _ in $(seq 1 50); do [ -s "$port_file" ] && break; sleep 0.1; done
[ -s "$port_file" ] || { echo 'FAIL: no web port published' >&2; kill "$first"; exit 1; }
taken=$(cat "$port_file")
second="$tmpdir/second.conf"
printf '[phoneblock]\nsip_lport=39125\n' > "$second"
status=0
PHONEBLOCK_CONFIG_FILE="$second" timeout 10 "$binary" --web "$taken" \
    --web-bind 127.0.0.1 >"$tmpdir/second.log" 2>&1 || status=$?
kill "$first"; wait "$first" 2>/dev/null || true
if [ "$status" -ne 1 ]; then
    printf 'FAIL: taken web port: expected exit 1, got %s\n' "$status" >&2
    cat "$tmpdir/second.log" >&2
    exit 1
fi
fi

"$binary" --help >/dev/null 2>&1
printf '%s\n' 'test_linux_entrypoint: all tests passed'
