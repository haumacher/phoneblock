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
# 7. Multi-chip manifest rendered from the real template: ESP32 under the
#    top-level integrity block, ESP32-C3 under its own. Both must verify,
#    and a broken C3 signature must fail the whole manifest.
# -----------------------------------------------------------------------
say "Test 7: multi-chip manifest from manifest.json.tmpl"
head -c 65536 /dev/urandom > "$TMP/app_c3.bin"
C3_OUT="$("$SCRIPT_DIR/sign-manifest-with-key.sh" \
          "$TMP/priv.pem" "$TMP/app_c3.bin" "$VERSION")"
C3_SHA256="$(printf '%s\n' "$C3_OUT" | sed -n 's/^SHA256=//p')"
C3_SIG="$(printf '%s\n' "$C3_OUT" | sed -n 's/^SIG=//p')"
render() {  # <c3-signature>
    sed -e "s/@VERSION@/${VERSION}/g" \
        -e "s/@APP_SHA256@/${APP_SHA256}/g" \
        -e "s|@SIGNATURE@|${APP_SIG}|g" \
        -e "s/@C3_APP_SHA256@/${C3_SHA256}/g" \
        -e "s|@C3_SIGNATURE@|$1|g" \
        "$SCRIPT_DIR/manifest.json.tmpl"
}
render "$C3_SIG" > "$TMP/manifest_multi.json"
if [[ "$(jq -r '.builds[0].chipFamily' "$TMP/manifest_multi.json")" == "ESP32" \
   && "$(jq -r '.integrity.app_sha256' "$TMP/manifest_multi.json")" == "$APP_SHA256" ]]; then
    ok "ESP32 stays builds[0] under the top-level integrity block"
else
    ng "ESP32 not at builds[0] / top-level integrity (breaks installed dongles)"
fi
if "$SCRIPT_DIR/verify-manifest.sh" "$TMP/manifest_multi.json" "$TMP/pub.pem" "$TMP/app_c3.bin" >/dev/null; then
    ok "multi-chip manifest verifies, C3 binary matches"
else
    ng "multi-chip manifest rejected"
fi
render "$APP_SIG" > "$TMP/manifest_multi_bad.json"
if "$SCRIPT_DIR/verify-manifest.sh" "$TMP/manifest_multi_bad.json" "$TMP/pub.pem" >/dev/null 2>&1; then
    ng "C3 build with a foreign signature accepted"
else
    ok "C3 build with a foreign signature rejected"
fi

# -----------------------------------------------------------------------
# 8. Per-chip OTA file (what the dongles poll) from ota-manifest.sh.
# -----------------------------------------------------------------------
say "Test 8: per-chip OTA file"
"$SCRIPT_DIR/ota-manifest.sh" "$VERSION" ESP32-C3 \
    "https://cdn.example/$VERSION/esp32c3/phoneblock_dongle.bin" \
    "$C3_SHA256" "$C3_SIG" > "$TMP/ota-esp32c3.json"
if "$SCRIPT_DIR/verify-manifest.sh" "$TMP/ota-esp32c3.json" "$TMP/pub.pem" "$TMP/app_c3.bin" >/dev/null; then
    ok "per-chip OTA file verifies, C3 binary matches"
else
    ng "per-chip OTA file rejected"
fi
if "$SCRIPT_DIR/verify-manifest.sh" "$TMP/ota-esp32c3.json" "$TMP/pub.pem" "$TMP/app.bin" >/dev/null 2>&1; then
    ng "per-chip OTA file accepted the ESP32 binary"
else
    ok "per-chip OTA file rejects another chip's binary"
fi

# -----------------------------------------------------------------------
say ""
say "Summary: $PASS passed, $FAIL failed"
[[ $FAIL -eq 0 ]] || exit 1
