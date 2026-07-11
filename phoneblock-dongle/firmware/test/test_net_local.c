// Host test for net_local.c — the pure locality classifier and
// forwarding-header parsers behind the two-mode web access gate
// (docs/network-access-control.md). Pure libc.
#include <assert.h>
#include <stdio.h>
#include <string.h>

#include "net_local.h"

// Parse `s`, then classify against `self` (may be NULL).
static bool local_of(const char *s, const netloc_self_t *self)
{
    net_addr_t a;
    assert(netloc_parse_ip(s, &a));   // test inputs are all valid literals
    return netloc_is_local(&a, self);
}

// True iff the rightmost XFF token parses to a local address (self NULL).
static bool xff_local(const char *hdr, const netloc_self_t *self)
{
    net_addr_t a;
    assert(netloc_xff_rightmost(hdr, &a));
    return netloc_is_local(&a, self);
}

static bool fwd_local(const char *hdr, const netloc_self_t *self)
{
    net_addr_t a;
    assert(netloc_forwarded_last(hdr, &a));
    return netloc_is_local(&a, self);
}

int main(void)
{
    // --- parsing ---
    net_addr_t tmp;
    assert(netloc_parse_ip("192.168.1.5", &tmp));
    assert(netloc_parse_ip("::1", &tmp));
    assert(netloc_parse_ip("2001:db8::1", &tmp));
    assert(netloc_parse_ip("::ffff:8.8.8.8", &tmp));   // explicit v4-mapped
    assert(!netloc_parse_ip("", &tmp));
    assert(!netloc_parse_ip("not-an-ip", &tmp));
    assert(!netloc_parse_ip("999.1.1.1", &tmp));
    assert(!netloc_parse_ip("192.168.1.5.6", &tmp));

    // A dotted-quad and its explicit v4-mapped form classify identically.
    assert(local_of("10.0.0.1", NULL) == local_of("::ffff:10.0.0.1", NULL));

    // --- IPv4 locality (no self context) ---
    assert(local_of("127.0.0.1", NULL));
    assert(local_of("10.1.2.3", NULL));
    assert(local_of("172.16.0.1", NULL));
    assert(local_of("172.31.255.254", NULL));
    assert(!local_of("172.32.0.1", NULL));   // just outside 172.16/12
    assert(!local_of("172.15.0.1", NULL));   // just below
    assert(local_of("192.168.178.22", NULL));
    assert(local_of("169.254.10.10", NULL)); // link-local
    assert(!local_of("8.8.8.8", NULL));      // public
    assert(!local_of("100.64.0.1", NULL));   // CGNAT is NOT local
    assert(!local_of("100.127.255.255", NULL));

    // --- IPv6 locality (no self context) ---
    assert(local_of("::1", NULL));                 // loopback
    assert(local_of("fe80::1", NULL));             // link-local
    assert(local_of("fe80::abcd:1234:5678:9abc", NULL));
    assert(local_of("fd00::1", NULL));             // ULA (fc00::/7)
    assert(local_of("fc00::1", NULL));
    assert(!local_of("2001:db8::1", NULL));        // documentation GUA → remote
    assert(!local_of("2606:4700:4700::1111", NULL)); // public GUA → remote

    // --- same-subnet / same-prefix via self context ---
    netloc_self_t self;
    memset(&self, 0, sizeof(self));
    // Dongle on 192.168.178.22/24.
    self.have_v4 = true;
    self.v4_addr[0] = 192; self.v4_addr[1] = 168; self.v4_addr[2] = 178; self.v4_addr[3] = 22;
    self.v4_mask[0] = 255; self.v4_mask[1] = 255; self.v4_mask[2] = 255; self.v4_mask[3] = 0;
    // Dongle also holds a global IPv6 on 2001:db8:abcd:1::/64.
    net_addr_t ownv6;
    assert(netloc_parse_ip("2001:db8:abcd:1::22", &ownv6));
    memcpy(self.v6[0].addr, ownv6.bytes, 16);
    self.v6[0].prefix_len = 64;
    self.n_v6 = 1;

    // A different private /24 is still "local" via the static RFC1918
    // range even though it's off the dongle's own subnet.
    assert(local_of("10.9.9.9", &self));
    // GUA on the dongle's own /64 → local (the key IPv6-on-LAN case).
    assert(local_of("2001:db8:abcd:1::5", &self));
    // GUA on a *different* /64 → still remote.
    assert(!local_of("2001:db8:abcd:2::5", &self));
    // A public v4 remains remote even with self set.
    assert(!local_of("8.8.8.8", &self));

    // --- X-Forwarded-For: rightmost token wins ---
    assert(xff_local("192.168.1.7", NULL));
    assert(!xff_local("8.8.8.8", NULL));
    // client, proxy chain — rightmost is what our single trusted proxy saw.
    assert(!xff_local("203.0.113.9, 8.8.8.8", NULL));  // rightmost public → remote
    assert(xff_local("8.8.8.8, 192.168.1.7", NULL));   // rightmost local → local
    // Client-spoofed leftmost cannot fake a local verdict: a real remote
    // client appended by the proxy is the rightmost and decides.
    assert(!xff_local("10.0.0.1, 198.51.100.2", NULL));
    // Whitespace + trailing comma tolerated.
    assert(xff_local("  8.8.8.8 ,  192.168.1.7  ", NULL));
    assert(xff_local("192.168.1.7,", NULL));
    // Malformed / empty → fail closed (no parse).
    net_addr_t a;
    assert(!netloc_xff_rightmost("", &a));
    assert(!netloc_xff_rightmost("garbage", &a));
    assert(!netloc_xff_rightmost("1.2.3.4, garbage", &a));

    // --- RFC 7239 Forwarded ---
    assert(fwd_local("for=192.168.1.7", NULL));
    assert(!fwd_local("for=8.8.8.8", NULL));
    assert(fwd_local("For=192.168.1.7", NULL));            // case-insensitive
    assert(fwd_local("for=\"192.168.1.7\"", NULL));        // quoted
    assert(fwd_local("for=\"[2001:db8:abcd:1::5]:8080\"", &self)); // bracketed v6 + port
    assert(!fwd_local("for=\"[2001:db8:abcd:2::5]:8080\"", &self));// other /64 → remote
    assert(fwd_local("for=192.168.1.7:51234", NULL));      // v4 + port
    // proxy=...;for=... with by/proto noise; last for= wins.
    assert(!fwd_local("for=192.168.1.7;proto=https, for=8.8.8.8", NULL));
    assert(fwd_local("by=203.0.113.1;for=8.8.8.8, for=192.168.1.7;proto=https", NULL));
    // Unknown / obfuscated identifiers → fail closed.
    assert(!netloc_forwarded_last("for=_hidden", &a));
    assert(!netloc_forwarded_last("for=unknown", &a));
    assert(!netloc_forwarded_last("by=192.168.1.1;proto=https", &a)); // no for=

    printf("test_net_local: OK\n");
    return 0;
}
