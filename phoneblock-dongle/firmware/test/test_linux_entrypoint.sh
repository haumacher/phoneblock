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

if PHONEBLOCK_CONFIG_FILE="$config" "$binary" --check-config; then
    printf '%s\n' 'FAIL: incomplete config was accepted' >&2
    exit 1
fi

cat >> "$config" <<'EOF'
pb_token=test-token
EOF

PHONEBLOCK_CONFIG_FILE="$config" "$binary" --check-config
PHONEBLOCK_CONFIG_FILE="$config" "$binary" --probe-sip
"$binary" --help >/dev/null 2>&1
printf '%s\n' 'test_linux_entrypoint: all tests passed'
