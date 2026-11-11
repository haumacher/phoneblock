// Host tests for sip_register_digest_response() (main/sip_register.c),
// the digest response of a REGISTER.
//
// The long-nonce cases pin the fix for the format-truncation warnings in
// the old digest_response(): it formatted every MD5 input into a 256-byte
// buffer, so a nonce longer than about 158 chars was cut off and the
// registrar got a wrong response. Expected values for those cases come
// from Python's hashlib.md5 over the same, untruncated inputs.

#include <string.h>

#include "linux_test_util.h"
#include "sip_auth.h"
#include "sip_register.h"

static void check(const char *name, const char *expected, const char *nonce,
                  const char *qop)
{
    char got[33];
    REQUIRE(sip_register_digest_response("620", "1234", "fritz.box", nonce,
                                         "REGISTER", "sip:fritz.box", qop,
                                         "00000001", "0a4f113b0a4f113b",
                                         got));
    if (strcmp(got, expected) != 0) {
        fprintf(stderr, "FAIL %s: expected %s got %s\n", name, expected, got);
        exit(1);
    }
}

// The example of RFC 2617, section 3.5 (qop=auth).
static void test_rfc2617_example(void)
{
    char got[33];
    REQUIRE(sip_register_digest_response("Mufasa", "Circle Of Life",
                                         "testrealm@host.com",
                                         "dcd98b7102dd2f0e8b11d0f600bfb0c093",
                                         "GET", "/dir/index.html", "auth",
                                         "00000001", "0a4f113b", got));
    REQUIRE(strcmp(got, "6629fae49393a05397450978507c4ef1") == 0);
}

// A Fritz!Box-style nonce: 16 hex chars, fits the stack buffer.
static void test_short_nonce(void)
{
    check("short nonce, with qop", "862f3dfaf66a3522b50e311f044722f2",
          "0123456789abcdef", "auth");
}

// The longest nonce the challenge parser keeps (SIP_MAX_CHALLENGE - 1),
// with and without qop: both inputs exceed 256 bytes.
static void test_long_nonce(void)
{
    char nonce[SIP_MAX_CHALLENGE];
    memset(nonce, 'a', sizeof(nonce) - 1);
    nonce[sizeof(nonce) - 1] = '\0';
    REQUIRE(strlen(nonce) == 255);

    check("long nonce, with qop", "3ee036cd50b3ff0e0eddafeed4090ba0",
          nonce, "auth");
    check("long nonce, no qop", "ea168f48d99381905e03bb962450f01d",
          nonce, "");
}

int main(void)
{
    test_rfc2617_example();
    test_short_nonce();
    test_long_nonce();
    printf("test_sip_digest_linux: all tests passed\n");
    return 0;
}
