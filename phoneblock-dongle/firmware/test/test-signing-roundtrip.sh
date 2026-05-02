#!/usr/bin/env bash
#
# End-to-end test of the OTA signing chain — generates a throwaway
# ECDSA-P256 key, signs a synthetic manifest, verifies it, then tampers
# with version/hash/signature and confirms each tamper breaks the chain.
#
# Tests the build-side scripts (sign-manifest-with-key.sh,
# verify-manifest.sh) and the deterministic payload format. The
# firmware-side mbedtls_pk_verify path is not exercised here — it's a
# stock mbedtls call that will accept any ASN.1-DER ECDSA-P256-SHA256
# signature openssl produces.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../scripts" && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

PASS=0
FAIL=0
say() { printf '%s\n' "$*" >&2; }
ok()  { say "  ✓ $*"; PASS=$((PASS+1)); }
ng()  { say "  ✗ $*"; FAIL=$((FAIL+1)); }

# -----------------------------------------------------------------------
# Setup: keypair + synthetic app binary + manifest skeleton.
# -----------------------------------------------------------------------
openssl ecparam -name prime256v1 -genkey -noout -out "$TMP/priv.pem"
openssl pkey -in "$TMP/priv.pem" -pubout -out "$TMP/pub.pem"

# 64 KB of deterministic-but-arbitrary content. Anything will do; the
# signing chain doesn't care what's in the binary.
head -c 65536 /dev/urandom > "$TMP/app.bin"

VERSION="9.9.9-test"

# -----------------------------------------------------------------------
# 1. Sign and verify a clean manifest.
# -----------------------------------------------------------------------
say "Test 1: clean sign + verify"
SIGN_OUT="$("$SCRIPT_DIR/sign-manifest-with-key.sh" \
            "$TMP/priv.pem" "$TMP/app.bin" "$VERSION")"
APP_SHA256="$(printf '%s\n' "$SIGN_OUT" | sed -n 's/^SHA256=//p')"
APP_SIG="$(printf '%s\n' "$SIGN_OUT" | sed -n 's/^SIG=//p')"

[[ -n "$APP_SHA256" ]] && ok "SHA256 emitted"  || ng "SHA256 missing"
[[ -n "$APP_SIG"    ]] && ok "SIG emitted"     || ng "SIG missing"

cat > "$TMP/manifest.json" <<JSON
{
  "version": "$VERSION",
  "builds": [
    { "chipFamily": "ESP32",
      "parts": [ { "path": "phoneblock_dongle.bin", "offset": 131072 } ] }
  ],
  "integrity": {
    "app_sha256": "$APP_SHA256",
    "signature":  "$APP_SIG"
  }
}
JSON

if "$SCRIPT_DIR/verify-manifest.sh" "$TMP/manifest.json" "$TMP/pub.pem" "$TMP/app.bin" >/dev/null; then
    ok "verify-manifest.sh accepts clean manifest"
else
    ng "verify-manifest.sh rejected clean manifest"
fi

# -----------------------------------------------------------------------
# 2. Tampered version (signed payload doesn't match).
# -----------------------------------------------------------------------
say "Test 2: version field tampered"
sed -e "s/$VERSION/9.9.9-evil/" "$TMP/manifest.json" > "$TMP/manifest_v.json"
if "$SCRIPT_DIR/verify-manifest.sh" "$TMP/manifest_v.json" "$TMP/pub.pem" "$TMP/app.bin" >/dev/null 2>&1; then
    ng "tampered version accepted"
else
    ok "tampered version rejected"
fi

# -----------------------------------------------------------------------
# 3. Tampered app_sha256 (signed payload doesn't match).
# -----------------------------------------------------------------------
say "Test 3: app_sha256 tampered"
EVIL_HASH="$(printf '%064d' 0)"
sed -e "s/$APP_SHA256/$EVIL_HASH/" "$TMP/manifest.json" > "$TMP/manifest_h.json"
if "$SCRIPT_DIR/verify-manifest.sh" "$TMP/manifest_h.json" "$TMP/pub.pem" "$TMP/app.bin" >/dev/null 2>&1; then
    ng "tampered app_sha256 accepted"
else
    ok "tampered app_sha256 rejected"
fi

# -----------------------------------------------------------------------
# 4. Tampered signature (single byte flip).
# -----------------------------------------------------------------------
say "Test 4: signature byte flipped"
EVIL_SIG="$(printf '%s' "$APP_SIG" | python3 -c '
import sys, base64
sig = base64.b64decode(sys.stdin.read())
sig = bytes([sig[0] ^ 0x01]) + sig[1:]
print(base64.b64encode(sig).decode(), end="")
')"
sed -e "s|$APP_SIG|$EVIL_SIG|" "$TMP/manifest.json" > "$TMP/manifest_s.json"
if "$SCRIPT_DIR/verify-manifest.sh" "$TMP/manifest_s.json" "$TMP/pub.pem" "$TMP/app.bin" >/dev/null 2>&1; then
    ng "tampered signature accepted"
else
    ok "tampered signature rejected"
fi

# -----------------------------------------------------------------------
# 5. Wrong public key (verify fails even though sig is well-formed).
# -----------------------------------------------------------------------
say "Test 5: wrong public key"
openssl ecparam -name prime256v1 -genkey -noout -out "$TMP/priv2.pem"
openssl pkey -in "$TMP/priv2.pem" -pubout -out "$TMP/pub2.pem"
if "$SCRIPT_DIR/verify-manifest.sh" "$TMP/manifest.json" "$TMP/pub2.pem" "$TMP/app.bin" >/dev/null 2>&1; then
    ng "wrong key accepted"
else
    ok "wrong key rejected"
fi

# -----------------------------------------------------------------------
# 6. App binary tampered (manifest sig OK, hash post-check fails).
# -----------------------------------------------------------------------
say "Test 6: app binary tampered after signing"
cp "$TMP/app.bin" "$TMP/app_evil.bin"
printf '\x42' | dd of="$TMP/app_evil.bin" bs=1 count=1 seek=0 conv=notrunc status=none
if "$SCRIPT_DIR/verify-manifest.sh" "$TMP/manifest.json" "$TMP/pub.pem" "$TMP/app_evil.bin" >/dev/null 2>&1; then
    ng "tampered app binary accepted"
else
    ok "tampered app binary rejected"
fi

# -----------------------------------------------------------------------
say ""
say "Summary: $PASS passed, $FAIL failed"
[[ $FAIL -eq 0 ]] || exit 1
