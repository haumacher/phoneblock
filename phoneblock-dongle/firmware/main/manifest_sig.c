#include "manifest_sig.h"

#include "esp_log.h"
#include "mbedtls/pk.h"

static const char *TAG = "mfsig";

// ---------------------------------------------------------------------------
// Release signing keys.
//
// ECDSA-P256 (prime256v1) public keys in DER SubjectPublicKeyInfo format
// (91 bytes each). The build host signs the OTA manifest and the i18n asset
// manifest with the matching private key (kept in KeePassXC, see RELEASE.md).
// Verify only runs on the CDN-pull paths; local USB flash and manual uploads
// bypass it on purpose, so a lost private key isn't a brick.
//
// PUBKEY_NEXT is the rotation slot. During key rotation the new public key
// gets baked in here for one release first; the next release signs with the
// new key, the one after promotes it into PRIMARY and clears NEXT. See
// RELEASE.md → "Schlüssel rotieren".
// ---------------------------------------------------------------------------
static const uint8_t PUBKEY_PRIMARY[] = {
    0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x02,
    0x01, 0x06, 0x08, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x03, 0x01, 0x07, 0x03,
    0x42, 0x00, 0x04, 0xdb, 0x14, 0x2d, 0x82, 0x18, 0xdd, 0x79, 0x2e, 0x08,
    0x60, 0x33, 0xd0, 0x2e, 0xc6, 0xba, 0x5d, 0x6e, 0xa6, 0xad, 0x21, 0x7f,
    0xb1, 0xf1, 0xd9, 0xae, 0x08, 0xb6, 0xde, 0x07, 0xa5, 0x6e, 0x6f, 0x96,
    0x01, 0x2c, 0xa7, 0x4e, 0xa3, 0x7e, 0x86, 0x4b, 0xcc, 0x52, 0x8d, 0x37,
    0x94, 0x33, 0x52, 0xf3, 0x73, 0x15, 0x0a, 0xda, 0xdd, 0x31, 0x3a, 0xbe,
    0x52, 0x58, 0x16, 0xb2, 0x7f, 0xf7, 0xd9
};

static const struct {
    const uint8_t *der;
    size_t         len;
} PUBKEYS[] = {
    { PUBKEY_PRIMARY, sizeof(PUBKEY_PRIMARY) },
    // Add an entry here during rotation, e.g.:
    // { PUBKEY_NEXT,    sizeof(PUBKEY_NEXT) },
};

static bool verify_with_pubkey(const uint8_t *pubkey_der, size_t pubkey_len,
                               const uint8_t hash[32],
                               const uint8_t *sig, size_t sig_len)
{
    mbedtls_pk_context pk;
    mbedtls_pk_init(&pk);
    bool ok = false;

    int rc = mbedtls_pk_parse_public_key(&pk, pubkey_der, pubkey_len);
    if (rc != 0) {
        ESP_LOGE(TAG, "pk_parse_public_key: -0x%04x", -rc);
        goto done;
    }
    rc = mbedtls_pk_verify(&pk, MBEDTLS_MD_SHA256,
                           hash, 32, sig, sig_len);
    if (rc != 0) {
        // -0x4e80 = MBEDTLS_ERR_ECP_VERIFY_FAILED for ECDSA. WARN — a
        // single failure here is recoverable across the remaining slots.
        ESP_LOGW(TAG, "pk_verify: -0x%04x", -rc);
        goto done;
    }
    ok = true;
done:
    mbedtls_pk_free(&pk);
    return ok;
}

bool manifest_sig_verify_hash(const uint8_t hash[32],
                              const uint8_t *sig, size_t sig_len)
{
    if (!hash || !sig || sig_len == 0) return false;
    for (size_t i = 0; i < sizeof(PUBKEYS) / sizeof(PUBKEYS[0]); i++) {
        if (verify_with_pubkey(PUBKEYS[i].der, PUBKEYS[i].len,
                               hash, sig, sig_len)) {
            return true;
        }
    }
    return false;
}
