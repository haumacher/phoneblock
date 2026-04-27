#pragma once

// SIP digest-auth helpers — pure-C subset of sip_register.c so the
// challenge parser and override-selection logic can be unit-tested on
// the host without dragging in mbedtls or the config layer.
//
// The MD5 math itself (digest_response) stays inside sip_register.c
// because mbedtls_md5 is device-only; it is unchanged from before
// Phase 3 and not what this module covers.

#include <stdbool.h>
#include <stddef.h>

#define SIP_MAX_CHALLENGE 256

typedef struct {
    char realm[64];
    char nonce[SIP_MAX_CHALLENGE];
    char opaque[64];
    char qop[16];        // "auth" or empty
    char algorithm[16];  // "MD5" by default
    bool valid;
} auth_challenge_t;

// Parse a WWW-Authenticate / Proxy-Authenticate header value into an
// auth_challenge_t. Skips an optional "Digest " scheme prefix; handles
// quoted and unquoted values; tolerates whitespace and comma lists.
// Sets out->valid = true iff realm and nonce both came back non-empty.
//
// qop selection: when the server offers "auth,auth-int" we pick
// "auth" (the only qop the firmware knows). When only "auth-int" is
// offered, qop is left empty so the digest falls back to RFC 2069
// style without qop — that's the safest behaviour given we don't
// implement auth-int.
void sip_auth_parse_challenge(const char *header_value,
                              auth_challenge_t *out);

// Pick the realm that goes into HA1 and into the Authorization
// header's realm= attribute. Override (config_sip_realm()) wins when
// non-empty; otherwise the realm advertised in the server challenge
// is used.
//
// Returns a pointer into either argument — caller must keep both
// alive for the duration of digest computation.
const char *sip_auth_effective_realm(const char *override,
                                     const auth_challenge_t *challenge);

// Pick the username that goes into the Digest username= attribute.
// override (config_sip_auth_user()) wins when non-empty; otherwise
// fall back to the SIP identity user.
const char *sip_auth_effective_user(const char *override,
                                    const char *identity_user);
