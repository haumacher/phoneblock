// Host-side unit tests for main/sip_auth.{c,h}.
//
// Pins down the realm-override behaviour added in Phase 3 of
// EXTENDED_SIP.md (1&1 needs a fixed realm regardless of what the
// challenge advertises) plus the auth-user fallback. Also covers the
// challenge parser since it is what feeds those overrides.

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>

#include "sip_auth.h"

static int g_tests = 0;
static int g_failures = 0;

#define CHECK_STR(name, expected, got) do {                          \
    g_tests++;                                                       \
    if (strcmp((expected), (got)) != 0) {                            \
        fprintf(stderr, "FAIL %s: expected <<<%s>>> got <<<%s>>>\n", \
                name, (expected), (got));                            \
        g_failures++;                                                \
    }                                                                \
} while (0)

#define CHECK_BOOL(name, expected, got) do {                         \
    g_tests++;                                                       \
    if ((bool)(expected) != (bool)(got)) {                           \
        fprintf(stderr, "FAIL %s: expected %s got %s\n",             \
                name,                                                \
                (expected) ? "true" : "false",                       \
                (got)      ? "true" : "false");                      \
        g_failures++;                                                \
    }                                                                \
} while (0)

// ---------------------------------------------------------------------------
// Challenge parser
// ---------------------------------------------------------------------------

static void test_parse_typical_challenge(void)
{
    auth_challenge_t ch;
    sip_auth_parse_challenge(
        "Digest realm=\"fritz.box\", nonce=\"abc123\", "
        "qop=\"auth\", algorithm=MD5",
        &ch);
    CHECK_STR("typical: realm",     "fritz.box", ch.realm);
    CHECK_STR("typical: nonce",     "abc123",    ch.nonce);
    CHECK_STR("typical: qop",       "auth",      ch.qop);
    CHECK_STR("typical: algorithm", "MD5",       ch.algorithm);
    CHECK_BOOL("typical: valid",    true,        ch.valid);
}

// 1&1 sends realm=1und1.de via a multi-host setup. Quoted form, no
// algorithm, includes opaque.
static void test_parse_1und1_challenge(void)
{
    auth_challenge_t ch;
    sip_auth_parse_challenge(
        "Digest realm=\"1und1.de\", nonce=\"deadbeef\", "
        "opaque=\"opq001\", qop=\"auth\"",
        &ch);
    CHECK_STR("1und1: realm",  "1und1.de",  ch.realm);
    CHECK_STR("1und1: nonce",  "deadbeef",  ch.nonce);
    CHECK_STR("1und1: opaque", "opq001",    ch.opaque);
    CHECK_STR("1und1: qop",    "auth",      ch.qop);
    // Algorithm defaults to MD5 even when missing from challenge.
    CHECK_STR("1und1: algorithm default", "MD5", ch.algorithm);
}

// Some servers omit the "Digest" scheme prefix when the value is
// already inside a Digest-typed header. Parser must cope.
static void test_parse_no_scheme_prefix(void)
{
    auth_challenge_t ch;
    sip_auth_parse_challenge(
        "realm=\"sip.example.com\", nonce=\"n1\"",
        &ch);
    CHECK_STR("no-scheme: realm", "sip.example.com", ch.realm);
    CHECK_STR("no-scheme: nonce", "n1",              ch.nonce);
    CHECK_BOOL("no-scheme: valid", true, ch.valid);
}

// Unquoted values are legal per RFC 7616 for tokens; algorithm comes
// in unquoted on most stacks.
static void test_parse_unquoted_algorithm(void)
{
    auth_challenge_t ch;
    sip_auth_parse_challenge(
        "Digest realm=\"x\", nonce=\"n\", algorithm=SHA-256",
        &ch);
    CHECK_STR("unquoted: algorithm", "SHA-256", ch.algorithm);
}

// qop list "auth,auth-int" → pick "auth". qop "auth-int" only → empty
// (we do not implement auth-int, so falling back to RFC 2069 form is
// the safe path).
static void test_parse_qop_list(void)
{
    auth_challenge_t ch;
    sip_auth_parse_challenge(
        "Digest realm=\"r\", nonce=\"n\", qop=\"auth,auth-int\"",
        &ch);
    CHECK_STR("qop list: picks auth", "auth", ch.qop);

    sip_auth_parse_challenge(
        "Digest realm=\"r\", nonce=\"n\", qop=\"auth-int\"",
        &ch);
    CHECK_STR("qop list: only auth-int → empty", "", ch.qop);
}

// Missing realm or nonce → invalid; downstream code must not blindly
// compute a digest from such a challenge.
static void test_parse_missing_fields(void)
{
    auth_challenge_t ch;
    sip_auth_parse_challenge("Digest realm=\"r\"", &ch);
    CHECK_BOOL("missing nonce: invalid", false, ch.valid);

    sip_auth_parse_challenge("Digest nonce=\"n\"", &ch);
    CHECK_BOOL("missing realm: invalid", false, ch.valid);

    sip_auth_parse_challenge("", &ch);
    CHECK_BOOL("empty: invalid", false, ch.valid);
}

// ---------------------------------------------------------------------------
// Effective-realm — the new Phase-3 behaviour
// ---------------------------------------------------------------------------

static void test_effective_realm_default(void)
{
    auth_challenge_t ch;
    sip_auth_parse_challenge("Digest realm=\"telekom\", nonce=\"n\"", &ch);
    // No override configured (empty/NULL) → fall back to challenge.
    CHECK_STR("override empty: use challenge",
              "telekom", sip_auth_effective_realm("",   &ch));
    CHECK_STR("override NULL: use challenge",
              "telekom", sip_auth_effective_realm(NULL, &ch));
}

// 1&1: server advertises realm "1und1net" but expects HA1 hashed
// against "1und1.de" — config_sip_realm() override pins it.
static void test_effective_realm_override(void)
{
    auth_challenge_t ch;
    sip_auth_parse_challenge("Digest realm=\"1und1net\", nonce=\"n\"", &ch);
    CHECK_STR("override set: wins over challenge",
              "1und1.de", sip_auth_effective_realm("1und1.de", &ch));
}

// The override and the challenge realm must drive the same selection
// — i.e. callers can rely on a single source-of-truth string for the
// realm that goes into HA1 *and* the Authorization header. Verify
// equal-pointer return when override is empty (so HA1 and the header
// see exactly the same bytes).
static void test_effective_realm_pointer_consistency(void)
{
    auth_challenge_t ch;
    sip_auth_parse_challenge("Digest realm=\"x\", nonce=\"n\"", &ch);
    const char *r1 = sip_auth_effective_realm("",     &ch);
    const char *r2 = sip_auth_effective_realm(NULL,   &ch);
    g_tests++;
    if (r1 != ch.realm || r2 != ch.realm) {
        fprintf(stderr, "FAIL realm pointer: expected pointer into challenge\n");
        g_failures++;
    }
}

// ---------------------------------------------------------------------------
// Effective-user (auth_user override)
// ---------------------------------------------------------------------------

static void test_effective_user_default(void)
{
    CHECK_STR("auth empty: fall back to identity",
              "alice", sip_auth_effective_user("",   "alice"));
    CHECK_STR("auth NULL: fall back to identity",
              "alice", sip_auth_effective_user(NULL, "alice"));
}

// Telekom case: identity = SIP user (the +49…@t-online.de URI) but
// the auth username is the e-mail address.
static void test_effective_user_override(void)
{
    CHECK_STR("auth override wins",
              "user@example.com",
              sip_auth_effective_user("user@example.com", "alice"));
}

// ---------------------------------------------------------------------------

int main(void)
{
    test_parse_typical_challenge();
    test_parse_1und1_challenge();
    test_parse_no_scheme_prefix();
    test_parse_unquoted_algorithm();
    test_parse_qop_list();
    test_parse_missing_fields();

    test_effective_realm_default();
    test_effective_realm_override();
    test_effective_realm_pointer_consistency();

    test_effective_user_default();
    test_effective_user_override();

    printf("test_sip_auth: %d tests, %d failures\n", g_tests, g_failures);
    return g_failures == 0 ? 0 : 1;
}
