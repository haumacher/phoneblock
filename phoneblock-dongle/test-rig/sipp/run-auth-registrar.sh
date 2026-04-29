#!/usr/bin/env bash
#
# Wrapper for registrar-auth.xml: figures out the LAN IP this machine
# would use to reach the dongle, pre-computes the expected Digest
# response for the test credentials and that IP, then runs sipp with
# the value pinned via -key. The scenario then uses check_it="true"
# regexes to fail the call on any mismatch — so this is a genuine
# credential test, not a decorative one.
#
# Usage:
#   ./run-auth-registrar.sh                  # auto-detect LAN IP
#   ./run-auth-registrar.sh 10.0.0.5         # override (multi-homed host)

set -euo pipefail

USERNAME="phoneblock-dongle"
PASSWORD="dongle-password"
REALM="phoneblock.test"
NONCE="0123456789abcdef"

# Auto-detect the IP this host uses for the default route — i.e. the
# LAN-facing one a device on the same network can actually reach.
# `ip route get 1.1.1.1` works without sending packets; it just asks
# the kernel which source IP / interface would be picked.
detect_lan_ip() {
    ip -4 -o route get 1.1.1.1 2>/dev/null \
        | awk '{ for (i=1;i<=NF;i++) if ($i == "src") print $(i+1) }' \
        | head -n1
}

if [[ $# -ge 1 ]]; then
    HOST_IP="$1"
    shift
else
    HOST_IP=$(detect_lan_ip)
    if [[ -z "$HOST_IP" ]]; then
        echo "Could not auto-detect LAN IP. Pass it explicitly:" >&2
        echo "  $0 <LAN_IP>" >&2
        exit 1
    fi
fi

# RFC 2617 digest, no qop — same math as digest_response() in
# sip_register.c (else branch when challenge.qop is empty).
EXPECTED=$(USERNAME="$USERNAME" PASSWORD="$PASSWORD" REALM="$REALM" \
           NONCE="$NONCE" HOST_IP="$HOST_IP" \
    python3 - <<'PY'
import hashlib, os
u = os.environ['USERNAME']
p = os.environ['PASSWORD']
r = os.environ['REALM']
n = os.environ['NONCE']
h = os.environ['HOST_IP']
ha1 = hashlib.md5(f"{u}:{r}:{p}".encode()).hexdigest()
ha2 = hashlib.md5(f"REGISTER:sip:{h}".encode()).hexdigest()
print(hashlib.md5(f"{ha1}:{n}:{ha2}".encode()).hexdigest())
PY
)

cat <<EOF
======================================================================
Sipp registrar with Digest password verification

Configure the dongle to:

  Transport : udp
  SIP-Host  : $HOST_IP
  SIP-Port  : 5060
  SIP-User  : $USERNAME
  SIP-Pass  : $PASSWORD
  Realm     : (leave empty)

Expected digest response : $EXPECTED
======================================================================
EOF

exec sipp -sf registrar-auth.xml \
    -key expected_response "$EXPECTED" \
    -p 5060 -i "$HOST_IP" \
    -trace_msg -trace_screen \
    -message_file registrar-auth_messages.log \
    -screen_file registrar-auth_screen.log \
    "$@"
