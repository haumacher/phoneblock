#pragma once

#include <stdbool.h>
#include "lwip/sockets.h"

// Transport abstraction for the SIP signaling channel.
//
// Phase 0/1 of EXTENDED_SIP.md: pulls the socket I/O out of sip_register.c
// behind a thin shim. Today UDP and TCP are implemented; TLS slots in
// next via additional sip_transport_open_*() variants without changes to
// the consumer (sip_register.c).
typedef struct sip_transport sip_transport_t;

// Open a SIP transport. Resolves the registrar host, creates the local
// socket bound to local_port, and discovers our outgoing IP. Returns
// NULL on any failure (errors logged with TAG="sip_transport").
//
// The "transport" string mirrors config_sip_transport(). "udp", "tcp"
// and "tls" are implemented; an unknown value logs a warning and falls
// back to UDP. If registrar_port == 0, a per-transport default is
// applied (5060 for UDP/TCP, 5061 for TLS).
sip_transport_t *sip_transport_open(const char *transport,
                                    const char *registrar_host,
                                    int registrar_port,
                                    int local_port);

void sip_transport_close(sip_transport_t *t);

// Re-resolve the registrar (e.g. after a config change). For UDP the
// local socket is reused so that pending datagrams are not lost. For
// TCP the existing connection is closed and a fresh connect to the new
// address is made; framing buffer is reset.
bool sip_transport_resolve(sip_transport_t *t,
                           const char *registrar_host,
                           int registrar_port);

// Send buf to the configured registrar. Phase 4 will route this via the
// outbound proxy when one is configured. Returns bytes sent or -1.
int sip_transport_send(sip_transport_t *t, const void *buf, int len);

// Send buf to a specific peer. Used for in-dialog responses: UDP sends
// to the source address of the request. TCP sends back over the same
// connection (peer is informational only; the registrar reuses the
// connection per RFC 3261 §18.2.1).
int sip_transport_send_to(sip_transport_t *t,
                          const struct sockaddr_in *peer,
                          const void *buf, int len);

// Receive the next SIP message. Blocks up to timeout_ms; pass a
// negative value for an indefinite wait.
//   >0: message length, *from filled with sender address
//    0: timeout
//   -1: error (logged); for TCP the transport may have closed and
//       attempted to reconnect — call sip_transport_consume_reconnect()
//       to find out.
// For UDP, *from is the datagram source. For TCP, *from is filled with
// the registrar's address.
int sip_transport_recv(sip_transport_t *t, int timeout_ms,
                       void *buf, int cap,
                       struct sockaddr_in *from);

// Local IP discovered from the default netif. Stable for the lifetime
// of the transport.
const char *sip_transport_local_ip(const sip_transport_t *t);

// Local port the socket is bound to.
int sip_transport_local_port(const sip_transport_t *t);

// Transport token for the Via header: "UDP" / "TCP" / "TLS".
const char *sip_transport_via_token(const sip_transport_t *t);

// Lower-cased transport token suitable for the SIP URI ";transport="
// parameter. "udp"/"tcp"/"tls". For UDP the parameter can be omitted
// (it is the SIP default), so callers normally only emit it for non-UDP.
const char *sip_transport_uri_param(const sip_transport_t *t);

// One-shot signal: true if the transport transparently reconnected since
// the last call (TCP only). The caller (sip_register task) uses this to
// fire an immediate re-REGISTER over the fresh connection. UDP always
// returns false. Calling clears the flag.
bool sip_transport_consume_reconnect(sip_transport_t *t);
