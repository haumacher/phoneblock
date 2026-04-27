#pragma once

// DNS SRV-record lookup for SIP — partial RFC 3263 (no NAPTR).
//
// SIP providers advertise their actual edge-server hostname/port via
// SRV records keyed by service+protocol+domain. Telekom for example
// only publishes _sips._tcp.tel.t-online.de → sip-tls-1.t-online.de:5061;
// there is no A record for tel.t-online.de itself. Without SRV the
// dongle could not register against such providers.
//
// This module splits in two:
//   - sip_srv_parse / sip_srv_pick: pure-C, host-tested via test_sip_srv.
//   - sip_srv_lookup: device-only, builds a UDP/53 query against the
//     resolver returned by dns_getserver(0) and hands the response to
//     the parser. Compiled out on the host build.

#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>

#define SIP_SRV_TARGET_MAX 64
#define SIP_SRV_MAX_RECORDS 8

typedef struct {
    char     target[SIP_SRV_TARGET_MAX];  // e.g. "sip-tls-1.t-online.de"
    uint16_t priority;
    uint16_t weight;
    uint16_t port;
} sip_srv_record_t;

// Parse a DNS response packet. Skips the questions, walks the answer
// section, and extracts every SRV (TYPE=33) record into the caller's
// array. Returns:
//    >0  number of records written (capped at max)
//     0  no SRV records (NXDOMAIN, empty answer, or non-SRV answers)
//    -1  malformed packet (truncated, bad name compression, etc.)
int sip_srv_parse(const uint8_t *resp, int resp_len,
                  sip_srv_record_t *records, int max);

// RFC 2782 selection: lowest priority wins; within that group, do a
// weighted-random pick using the supplied random number. Records
// with weight 0 are picked only if every record in the group has
// weight 0 (in which case the first one wins). Returns the chosen
// index or -1 if n <= 0.
int sip_srv_pick(const sip_srv_record_t *records, int n, uint32_t rnd);

#ifdef ESP_PLATFORM
// Look up _<service>._<proto>.<domain> against the system resolver.
// On success fills target/port with the chosen SRV endpoint and
// returns true; on any failure (no DNS, NXDOMAIN, parse error,
// timeout) returns false so the caller can fall back to a direct
// A-record query on the configured host.
//
//   service: "sip" or "sips"
//   proto:   "udp" or "tcp"
bool sip_srv_lookup(const char *service, const char *proto,
                    const char *domain,
                    char *target_out, int target_cap,
                    int *port_out);
#endif
