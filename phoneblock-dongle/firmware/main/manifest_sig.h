#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// Shared release-signature verification for the dongle's CDN manifests.
//
// One ECDSA-P256 key pair signs everything the dongle pulls from the CDN:
// the OTA firmware manifest (firmware_update.c) and the i18n asset manifest
// (i18n_sync.c). The public key(s) live here so there is a single copy to
// rotate; the private key stays offline (KeePassXC, see RELEASE.md).
//
// Each caller hashes its own domain-separated payload with SHA-256 and
// passes the digest here — the payload prefix ("phoneblock-dongle-ota-v1",
// "phoneblock-dongle-i18n-v1", …) keeps a signature valid for one path from
// ever being accepted on another.

// Verify an ECDSA-P256 signature (ASN.1-DER, as produced by
// `openssl dgst -sha256 -sign`) over the given 32-byte SHA-256 digest,
// against every embedded release public key. Returns true on the first
// key that accepts it. sig_len is the DER length in bytes.
bool manifest_sig_verify_hash(const uint8_t hash[32],
                              const uint8_t *sig, size_t sig_len);
