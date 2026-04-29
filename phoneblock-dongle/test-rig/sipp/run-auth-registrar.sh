#!/usr/bin/env bash
#
# Wrapper for registrar-auth.xml: pre-computes the expected Digest
# response for the test credentials and the configured registrar URI,
# then runs sipp with the value pinned via -key. The scenario then
# uses check_it="true" regexes to fail the call on any mismatch — so
# this is a genuine credential test, not a decorative one.
#
# Required dongle config:
#   Transport: udp
#   SIP-Host:  <HOST_IP>   (whatever you pass to this script)
#   SIP-Port:  5060
#   SIP-User:  phoneblock-dongle
#   SIP-Pass:  dongle-password
#   SIP-Realm: empty (the dongle picks up phoneblock.test from our
#              challenge) — or set it explicitly to phoneblock.test.

set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "usage: $0 <HOST_IP>" >&2
    echo "  HOST_IP — LAN IP sipp binds to AND the dongle's SIP-Host" >&2
    exit 1
fi

HOST_IP="$1"
USERNAME="phoneblock-dongle"
PASSWORD="dongle-password"
REALM="phoneblock.test"
NONCE="0123456789abcdef"

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

echo "Expected digest response for $USERNAME:$REALM @ sip:$HOST_IP : $EXPECTED"
echo "Starting sipp on $HOST_IP:5060…"

exec sipp -sf registrar-auth.xml \
    -key expected_response "$EXPECTED" \
    -p 5060 -i "$HOST_IP" \
    -trace_msg -trace_screen \
    -message_file registrar-auth_messages.log \
    -screen_file registrar-auth_screen.log \
    "$@"
