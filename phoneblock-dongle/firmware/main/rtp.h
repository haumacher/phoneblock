#pragma once

#include <stddef.h>
#include <stdint.h>
#include "lwip/sockets.h"

#include "announcement.h"

// The UDP port the dongle binds for RTP audio is configurable via
// config_rtp_port() (default 16000); the SDP advertises the same value.

// SDES master key (16 bytes) + master salt (14 bytes) for the
// AES_CM_128_HMAC_SHA1_80 SRTP suite. When `enabled`, rtp_play_audio
// encrypts every outbound packet as SRTP using this key; otherwise it
// sends plain RTP/AVP.
#define RTP_SRTP_KEY_LEN 30
typedef struct {
    bool    enabled;
    uint8_t key[RTP_SRTP_KEY_LEN];
} rtp_srtp_tx_t;

// Spawn a FreeRTOS task that streams the opened announcement `src` to
// `dest` as G.711 A-law (PCMA) RTP, 20 ms / 160-byte frames at 50
// packets/s, then exits. The task reads `src` frame-by-frame (so a
// custom SPIFFS announcement is streamed straight from flash, never
// buffered whole) and closes it when done.
//
// If `srtp` is non-NULL and srtp->enabled, the outbound stream is
// protected as SRTP (AES_CM_128_HMAC_SHA1_80) with the given master key;
// the SDP answer must have advertised RTP/SAVP with the matching key.
// Pass NULL (or enabled=false) for plain RTP/AVP. The struct is copied,
// so the caller need not keep it alive.
//
// Fire-and-forget: ownership of `src` (including its open file handle)
// is handed to the task — the caller must NOT close it afterwards.
// Even on failure to spawn, this releases `src`, so the caller is
// always relieved of it.
void rtp_play_audio(const struct sockaddr_in *dest,
                    announcement_src_t *src,
                    const rtp_srtp_tx_t *srtp);

// Ensure the singleton RTP UDP socket exists and is bound to
// config_rtp_port(); returns its fd or -1 on failure. The socket is
// reused across calls (and shared between the STUN probe and the
// streaming task) so the public NAT mapping a STUN query learns stays
// valid for the announcement that follows, and the binding stays warm.
int rtp_socket_ensure(void);

// Query `stun_server` ("host" or "host:port") over the RTP socket and
// return the public (post-NAT) IPv4 endpoint our media will appear to
// come from (from the response's XOR-MAPPED-ADDRESS). Returns false on
// empty server / DNS / timeout / parse failure — the caller should then
// fall back to the signalling-learned public IP and the local RTP port.
// Best-effort: a symmetric NAT maps per destination, so the value learned
// against the STUN server need not match the mapping toward the call's
// media gateway; the streaming task's inbound-RTP log confirms which.
bool rtp_stun_map(const char *stun_server, char *ip_out, int ip_cap,
                  int *port_out);

// NAT mapping behaviour as seen for the RTP socket (RFC 5780).
typedef enum {
    NAT_MAP_UNKNOWN = 0,            // probe not run / inconclusive
    NAT_MAP_ENDPOINT_INDEPENDENT,  // cone — a STUN-learned port is valid toward
                                   // any peer, including the media gateway
    NAT_MAP_ENDPOINT_DEPENDENT,    // symmetric — the external port varies per
                                   // destination, so STUN cannot predict the
                                   // port the gateway will see; only a
                                   // port-identical RTP forward (or a VoIP
                                   // router) fixes media here
} nat_mapping_t;

// Probe the NAT mapping behaviour by querying two STUN servers at different
// IPs from the same RTP socket and comparing the mapped ports (equal →
// endpoint-independent, differ → endpoint-dependent). `primary_stun` (the
// configured server, may be "") is tried first, then a built-in fallback
// list. Result is cached and also returned. Run once after a direct
// provider registration; takes up to ~1 s, so not on the call path.
nat_mapping_t rtp_probe_nat_mapping(const char *primary_stun);

// Last probe result (NAT_MAP_UNKNOWN until rtp_probe_nat_mapping ran), and
// its lowercase string form for the status JSON.
nat_mapping_t rtp_nat_mapping(void);
const char   *rtp_nat_mapping_str(void);

// Signal an in-flight rtp_play_audio task to stop at the next 20 ms
// frame boundary. Used by the SIP task to preempt the announcement
// when a second INVITE arrives while the first SPAM dialog is still
// streaming — no point making the new caller wait through 5–15 s of
// our own audio. Safe to call when no streaming task is active.
void rtp_request_abort(void);

// True from just before an announcement stream starts until the
// streaming task exits. The report-call worker waits on this so its TLS
// handshake never overlaps the SRTP media stream (combined heap use
// crashes the ESP32). Safe to call from any task.
bool rtp_streaming_active(void);
