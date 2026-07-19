#pragma once

// Pure network-address locality logic for the two-mode web access gate
// (see docs/network-access-control.md). No ESP-IDF dependencies, so it
// runs under the host test harness (test/test_net_local.c). The httpd /
// esp_netif glue that feeds these functions lives in web_access.c.

#include <stdbool.h>
#include <stdint.h>

// Max of the dongle's own IPv6 addresses we track for same-prefix
// checks. LWIP ships CONFIG_LWIP_IPV6_NUM_ADDRESSES (3 here); 8 leaves
// headroom without a config-time coupling.
#define NETLOC_MAX_V6 8

// An address normalized to 16 bytes in network byte order. IPv4 is held
// in IPv4-mapped form (::ffff:a.b.c.d) so one representation covers both
// families.
typedef struct {
    uint8_t bytes[16];
} net_addr_t;

// The dongle's own interface addresses, used for same-subnet / same-
// prefix "local" checks. Populated from esp_netif by the caller (or by
// the test harness directly).
typedef struct {
    bool    have_v4;
    uint8_t v4_addr[4];     // network byte order (a.b.c.d)
    uint8_t v4_mask[4];
    int     n_v6;
    struct {
        uint8_t addr[16];
        uint8_t prefix_len; // bits; typically 64
    } v6[NETLOC_MAX_V6];
} netloc_self_t;

// Parse a textual IP ("192.168.1.5", "2001:db8::1", "::ffff:1.2.3.4")
// into `out` in v4-mapped-normalized form. Returns false if the string
// is not a valid IPv4 or IPv6 literal.
bool netloc_parse_ip(const char *s, net_addr_t *out);

// True iff `a` is a loopback / link-local / private / unique-local
// address, or shares the dongle's own IPv4 subnet or any own-IPv6
// prefix. CGNAT shared space (100.64.0.0/10) and global addresses that
// do not match an own prefix are NOT local. `self` may be NULL (skips
// the same-subnet checks; the static ranges still apply).
bool netloc_is_local(const net_addr_t *a, const netloc_self_t *self);

// Parse the rightmost comma-separated token of an X-Forwarded-For header
// value into `out`. Returns false if the header is empty or the chosen
// token is not a valid IP literal (fail closed).
bool netloc_xff_rightmost(const char *xff, net_addr_t *out);

// Parse the last `for=` element of an RFC 7239 Forwarded header value
// into `out`. Handles quoting, `[v6]` brackets and a trailing `:port`
// on the bracketed-v6 / dotted-v4 forms. Returns false if none is a
// valid IP literal (fail closed).
bool netloc_forwarded_last(const char *forwarded, net_addr_t *out);
