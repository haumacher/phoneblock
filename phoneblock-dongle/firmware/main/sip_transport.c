#ifndef ESP_PLATFORM
#define _POSIX_C_SOURCE 200809L
#endif

#include "sip_transport.h"
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <strings.h>
#include <errno.h>

#ifdef ESP_PLATFORM
#include "esp_netif.h"
#include "lwip/netdb.h"
#else
#include <arpa/inet.h>
#include <netdb.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <unistd.h>
#endif

#include "sip_frame.h"
#include "platform.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static const char *TAG = "sip_transport";

// Per-message reassembly window for stream transports. 4 KiB matches
// SIP_RX_BUF_SIZE in sip_register.c — large enough for an INVITE-with-SDP
// from any mainstream registrar.
#define SIP_TCP_FRAME_BUF 4096

typedef enum { TR_UDP, TR_TCP, TR_TLS } transport_kind_t;

// TLS handshake budget. Real-world experience: Telekom and sipgate
// complete in <2 s; 10 s gives us headroom for slower paths without
// blocking the SIP task long enough to feel like a hang.
#define SIP_TLS_HANDSHAKE_TIMEOUT_MS 10000

// Send bounds for the stream transports (TCP/TLS). Without them a send on a
// half-dead link (the TCP window fills, the peer never ACKs) blocks in
// lwip_write → sys_arch_sem_wait(timeout=0) *forever*: the WDT-subscribed
// sip_register task then never loops back to esp_task_wdt_reset() and the
// task watchdog panics the device (field crash, 1.4.1 —
// esp_tls_conn_write → mbedtls_net_send → lwip_write, IDLE1 reported but
// sip_register named as the offender).
//
// SIP_STREAM_SEND_SLICE_MS is set as SO_SNDTIMEO so each blocking write
// returns at least that often (as EAGAIN on raw TCP / WANT_WRITE on TLS),
// letting the loop reclaim control; SIP_STREAM_SEND_DEADLINE_MS is the
// overall budget after which the send is treated as failed and the link is
// reconnected. Both stay well under CONFIG_ESP_TASK_WDT_TIMEOUT_S (60 s),
// so the deadline — not a WDT feed — is what bounds the send. The 2 s slice
// mirrors SIP_REGISTER_RECV_SLICE_MS on the recv path.
#define SIP_STREAM_SEND_SLICE_MS    2000
#define SIP_STREAM_SEND_DEADLINE_MS 10000

struct sip_transport {
    transport_kind_t kind;
    int  sock;                       // UDP/TCP socket fd; -1 for TLS
    pb_tls_t *tls;                   // TLS only
    struct sockaddr_in registrar;    // peer addr for UDP/TCP; informational for TLS
    char registrar_host[64];         // saved for TCP/TLS reconnect (esp-tls needs hostname for SNI)
    int  registrar_port;
    char tls_sni[64];                // SNI + cert name for TLS; service domain, may differ from registrar_host (#363)
    char local_ip[INET_ADDRSTRLEN];
    int  local_port;
    char via_token[8];
    char uri_param[8];

    // Stream-only (TCP/TLS).
    sip_framer_t framer;
    char        *frame_buf;
    bool         reconnected_flag;
};

static bool tcp_connect(sip_transport_t *t);
static void tcp_drop(sip_transport_t *t);
static void tcp_reconnect(sip_transport_t *t);
static bool tls_connect(sip_transport_t *t);
static void tls_drop(sip_transport_t *t);

// ---------------------------------------------------------------------------
// Common helpers
// ---------------------------------------------------------------------------

// Connection-oriented transports (TCP/TLS), as opposed to UDP. They share
// the stream framer, the transparent-reconnect logic and the persistent-
// connection send/recv path.
static inline bool is_stream(transport_kind_t kind)
{
    return kind == TR_TCP || kind == TR_TLS;
}

// Bound how long a single send() on a stream socket may block, so a stalled
// TCP window can't wedge the SIP task forever (see SIP_STREAM_SEND_* above).
// A failure here only means we fall back to the old blocking behaviour, so
// it's not worth failing the connect over — but WARN so it surfaces on the
// web Protokoll panel if a build ever lacks SO_SNDTIMEO support.
static void set_send_timeout(int fd)
{
    struct timeval tv = {
        .tv_sec  = SIP_STREAM_SEND_SLICE_MS / 1000,
        .tv_usec = (SIP_STREAM_SEND_SLICE_MS % 1000) * 1000,
    };
    if (setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv)) < 0) {
        pb_log_warn(TAG, "SO_SNDTIMEO: %s", strerror(errno));
    }
}

static const char *ipv4_to_string(const struct in_addr *address,
                                  char *out, size_t cap)
{
#ifdef ESP_PLATFORM
    return inet_ntoa_r(*address, out, (int)cap);
#else
    return inet_ntop(AF_INET, address, out, (socklen_t)cap);
#endif
}

// Copy a hostname into a fixed-size buffer, always NUL-terminated. A NULL
// or empty src clears the buffer. Used for both registrar_host and tls_sni
// so the truncate-and-terminate dance lives in one place.
static void set_host(char *dst, size_t cap, const char *src)
{
    if (src && src[0]) {
        strncpy(dst, src, cap - 1);
        dst[cap - 1] = '\0';
    } else {
        dst[0] = '\0';
    }
}

// Stash a stream transport's peer host/port + TLS SNI so it can
// transparently reconnect later (the TLS backend reapplies the SNI on retry).
// UDP keeps its peer in t->registrar (a resolved sockaddr) and needs none
// of this — only call this for stream transports.
static void save_stream_peer(sip_transport_t *t, const char *host,
                             int port, const char *tls_sni)
{
    set_host(t->registrar_host, sizeof(t->registrar_host), host);
    t->registrar_port = port;
    set_host(t->tls_sni, sizeof(t->tls_sni), tls_sni);
}

static bool discover_local_ip(struct sip_transport *t)
{
#ifdef ESP_PLATFORM
    esp_netif_t *netif = esp_netif_get_default_netif();
    if (!netif) {
        pb_log_err(TAG, "no default netif");
        return false;
    }
    esp_netif_ip_info_t ip;
    if (esp_netif_get_ip_info(netif, &ip) != ESP_OK) {
        pb_log_err(TAG, "get_ip_info failed");
        return false;
    }
    esp_ip4addr_ntoa(&ip.ip, t->local_ip, sizeof(t->local_ip));
    return true;
#else
    int fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (fd < 0) return false;
    if (connect(fd, (struct sockaddr *)&t->registrar,
                sizeof(t->registrar)) < 0) {
        close(fd);
        return false;
    }
    struct sockaddr_in local = {0};
    socklen_t len = sizeof(local);
    bool ok = getsockname(fd, (struct sockaddr *)&local, &len) == 0
           && ipv4_to_string(&local.sin_addr, t->local_ip,
                             sizeof(t->local_ip)) != NULL;
    close(fd);
    if (!ok) pb_log_err(TAG, "failed to discover local IPv4 address");
    return ok;
#endif
}

static bool dns_resolve(const char *host, int port, int socktype,
                        struct sockaddr_in *out)
{
    struct addrinfo hints = { .ai_family = AF_INET, .ai_socktype = socktype };
    struct addrinfo *res = NULL;

    char port_str[8];
    snprintf(port_str, sizeof(port_str), "%d", port);

    int err = getaddrinfo(host, port_str, &hints, &res);
    if (err != 0 || !res) {
        pb_log_err(TAG, "DNS lookup of %s failed: %d", host, err);
        return false;
    }
    memcpy(out, res->ai_addr, sizeof(*out));
    freeaddrinfo(res);
    return true;
}

bool sip_transport_resolve(sip_transport_t *t,
                           const char *host, int port, const char *tls_sni)
{
    int socktype = (t->kind == TR_UDP) ? SOCK_DGRAM : SOCK_STREAM;
    if (!dns_resolve(host, port, socktype, &t->registrar)) return false;

    char ip[INET_ADDRSTRLEN];
    ipv4_to_string(&t->registrar.sin_addr, ip, sizeof(ip));
    pb_log_info(TAG, "registrar %s:%d → %s", host, port, ip);

    // Stream transports keep host/port for transparent reconnects;
    // esp-tls in particular needs the hostname for SNI on every retry.
    if (is_stream(t->kind)) {
        save_stream_peer(t, host, port, tls_sni);
    }

    if (t->kind == TR_TCP) {
        tcp_drop(t);
        if (!tcp_connect(t)) return false;
        t->reconnected_flag = false;
    } else if (t->kind == TR_TLS) {
        tls_drop(t);
        if (!tls_connect(t)) return false;
        t->reconnected_flag = false;
    }
    return true;
}

// ---------------------------------------------------------------------------
// UDP open / close
// ---------------------------------------------------------------------------

static bool udp_open(sip_transport_t *t, int local_port)
{
    t->sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (t->sock < 0) {
        pb_log_err(TAG, "socket(UDP): %s", strerror(errno));
        return false;
    }

    struct sockaddr_in local = {
        .sin_family      = AF_INET,
        .sin_addr.s_addr = htonl(INADDR_ANY),
        .sin_port        = htons(local_port),
    };
    if (bind(t->sock, (struct sockaddr *)&local, sizeof(local)) < 0) {
        pb_log_err(TAG, "bind(UDP %d): %s", local_port, strerror(errno));
        close(t->sock);
        t->sock = -1;
        return false;
    }
    pb_log_info(TAG, "local IP %s, SIP UDP port %d", t->local_ip, local_port);
    return true;
}

// ---------------------------------------------------------------------------
// TCP open / connect / drop
// ---------------------------------------------------------------------------

static bool tcp_connect(sip_transport_t *t)
{
    int sock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (sock < 0) {
        pb_log_err(TAG, "socket(TCP): %s", strerror(errno));
        return false;
    }

    // Deliberately do NOT bind() the socket to the configured local port
    // (t->local_port). Doing so pinned every connect to the same source
    // port, so a reconnect to the same registrar produced a 4-tuple
    // (localIP:15060 → registrarIP:5060) identical to the just-closed
    // connection still lingering in TIME_WAIT — and connect() then failed
    // with EADDRINUSE ("Address already in use"). SO_REUSEADDR lets you
    // *bind* a TIME_WAIT port but does not relax the connect-side 4-tuple
    // uniqueness check, so binding was the trap, not the cure.
    //
    // A fixed source port buys nothing here: the advertised Contact/Via
    // port comes from config (advertised_port() in sip_register.c), and for
    // connection-oriented SIP the registrar reuses THIS connection for
    // responses and in-dialog requests (RFC 3261 §18.2 / RFC 5923) rather
    // than dialing back to the advertised port. Letting the kernel assign a
    // fresh ephemeral source port each connect means a reconnect draws a
    // new 4-tuple that can't collide with the lingering TIME_WAIT one. This
    // matches TLS, which already connects from an ephemeral port via
    // esp-tls and reconnects without trouble.

    if (connect(sock, (struct sockaddr *)&t->registrar,
                sizeof(t->registrar)) < 0) {
        pb_log_warn(TAG, "TCP connect failed: %s", strerror(errno));
        close(sock);
        return false;
    }

    set_send_timeout(sock);

    // Record the kernel-assigned ephemeral source port for diagnostics /
    // the local-port accessor. It is NOT what gets advertised — Via/Contact
    // use the configured port (advertised_port() in sip_register.c) — so a
    // fresh port on each reconnect is expected and harmless.
    struct sockaddr_in actual = {0};
    socklen_t alen = sizeof(actual);
    if (getsockname(sock, (struct sockaddr *)&actual, &alen) == 0) {
        t->local_port = ntohs(actual.sin_port);
    }

    t->sock = sock;
    sip_framer_reset(&t->framer);
    pb_log_info(TAG, "TCP connected, local port %d", t->local_port);
    return true;
}

static void tcp_drop(sip_transport_t *t)
{
    if (t->sock >= 0) {
        pb_log_info(TAG, "TCP connection to %s:%d closed",
                    t->registrar_host, t->registrar_port);
        close(t->sock);
        t->sock = -1;
    }
    sip_framer_reset(&t->framer);
}

static void tcp_reconnect(sip_transport_t *t)
{
    tcp_drop(t);
    if (tcp_connect(t)) t->reconnected_flag = true;
}

static bool tls_connect(sip_transport_t *t)
{
    // Keep the SNI/certificate name separate from the resolved edge host:
    // some providers route SIP by the configured service domain.
    pb_tls_t *tls = pb_tls_connect(t->registrar_host, t->registrar_port,
                                   t->tls_sni, SIP_TLS_HANDSHAKE_TIMEOUT_MS);
    if (!tls) {
        pb_log_warn(TAG, "TLS connect to %s:%d failed",
                    t->registrar_host, t->registrar_port);
        return false;
    }

    int fd = pb_tls_fd(tls);
    if (fd >= 0) {
        set_send_timeout(fd);
        struct sockaddr_in actual = {0};
        socklen_t alen = sizeof(actual);
        if (getsockname(fd, (struct sockaddr *)&actual, &alen) == 0)
            t->local_port = ntohs(actual.sin_port);
    }

    t->tls = tls;
    sip_framer_reset(&t->framer);
    pb_log_info(TAG, "TLS connected to %s:%d (SNI %s), local port %d",
                t->registrar_host, t->registrar_port,
                t->tls_sni[0] ? t->tls_sni : t->registrar_host, t->local_port);
    return true;
}

static void tls_drop(sip_transport_t *t)
{
    if (t->tls) {
        pb_log_info(TAG, "TLS connection to %s:%d closed",
                t->registrar_host, t->registrar_port);
        pb_tls_destroy(t->tls);
        t->tls = NULL;
    }
    sip_framer_reset(&t->framer);
}

static void tls_reconnect(sip_transport_t *t)
{
    tls_drop(t);
    if (tls_connect(t)) {
        t->reconnected_flag = true;
    }
}

// ---------------------------------------------------------------------------
// Open / close (top-level)
// ---------------------------------------------------------------------------

sip_transport_t *sip_transport_open(const char *transport,
                                    const char *registrar_host,
                                    int registrar_port,
                                    const char *tls_sni,
                                    int local_port)
{
    transport_kind_t kind = TR_UDP;
    const char *via = "UDP";
    const char *uri = "udp";

    if (transport && transport[0] && strcasecmp(transport, "udp") != 0) {
        if (strcasecmp(transport, "tcp") == 0) {
            kind = TR_TCP;
            via  = "TCP";
            uri  = "tcp";
        } else if (strcasecmp(transport, "tls") == 0) {
            kind = TR_TLS;
            via  = "TLS";
            uri  = "tls";
        } else {
            pb_log_warn(TAG,
                     "transport \"%s\" not yet implemented, falling back to UDP",
                     transport);
        }
    }

    // Per-transport default port when caller passed 0/<=0.
    if (registrar_port <= 0) {
        registrar_port = (kind == TR_TLS) ? 5061 : 5060;
        pb_log_info(TAG, "applying default port %d for transport %s",
                 registrar_port, via);
    }

    struct sip_transport *t = calloc(1, sizeof(*t));
    if (!t) return NULL;
    t->sock = -1;
    t->kind = kind;
    t->local_port = local_port;
    strcpy(t->via_token, via);
    strcpy(t->uri_param, uri);
    if (is_stream(kind)) {
        save_stream_peer(t, registrar_host, registrar_port, tls_sni);
    }

    int socktype = (kind == TR_UDP) ? SOCK_DGRAM : SOCK_STREAM;
    if (!dns_resolve(registrar_host, registrar_port, socktype, &t->registrar)) {
        goto fail;
    }
    if (!discover_local_ip(t)) goto fail;
    char ip[INET_ADDRSTRLEN];
    ipv4_to_string(&t->registrar.sin_addr, ip, sizeof(ip));
    pb_log_info(TAG, "registrar %s:%d → %s", registrar_host, registrar_port, ip);

    if (is_stream(kind)) {
        t->frame_buf = malloc(SIP_TCP_FRAME_BUF);
        if (!t->frame_buf) {
            pb_log_err(TAG, "frame buffer malloc failed");
            goto fail;
        }
        sip_framer_init(&t->framer, t->frame_buf, SIP_TCP_FRAME_BUF);
        if (kind == TR_TCP) {
            if (!tcp_connect(t)) goto fail;
        } else {
            if (!tls_connect(t)) goto fail;
        }
    } else {
        if (!udp_open(t, local_port)) goto fail;
    }
    return t;

fail:
    if (t->frame_buf) free(t->frame_buf);
    if (t->sock >= 0) close(t->sock);
    if (t->tls) pb_tls_destroy(t->tls);
    free(t);
    return NULL;
}

void sip_transport_close(sip_transport_t *t)
{
    if (!t) return;
    // Route stream teardown through the drop helpers so the "connection
    // closed" log fires here too (transport switch, task shutdown), not
    // only on reconnect.
    if (t->kind == TR_TLS)      tls_drop(t);
    else if (t->kind == TR_TCP) tcp_drop(t);
    else if (t->sock >= 0)      close(t->sock);
    if (t->frame_buf) free(t->frame_buf);
    free(t);
}

// ---------------------------------------------------------------------------
// Send
// ---------------------------------------------------------------------------

static int tcp_send_all(sip_transport_t *t, const void *buf, int len)
{
    if (t->sock < 0 && !tcp_connect(t)) return -1;

    const char *p = buf;
    int remaining = len;
    int64_t deadline = pb_monotonic_us()
                     + (int64_t)SIP_STREAM_SEND_DEADLINE_MS * 1000;
    while (remaining > 0) {
        int n = send(t->sock, p, remaining, 0);
        if (n < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) {
            // The per-syscall SO_SNDTIMEO slice expired with a full send
            // window. Retry within the overall budget so a brief stall
            // doesn't needlessly tear down the registrar link; a
            // persistently dead peer reconnects instead of hanging the SIP
            // task into a watchdog panic. The blocking send paces the loop.
            if ((int64_t)pb_monotonic_us() >= deadline) {
                pb_log_warn(TAG, "TCP send stalled >%d ms — reconnecting",
                         SIP_STREAM_SEND_DEADLINE_MS);
                tcp_reconnect(t);
                return -1;
            }
            continue;
        }
        if (n <= 0) {
            pb_log_warn(TAG, "TCP send: %s", strerror(errno));
            tcp_reconnect(t);
            return -1;
        }
        p += n;
        remaining -= n;
    }
    return len;
}

static int tls_send_all(sip_transport_t *t, const void *buf, int len)
{
    if (!t->tls && !tls_connect(t)) return -1;

    const char *p = buf;
    int remaining = len;
    int64_t deadline = pb_monotonic_us()
                     + (int64_t)SIP_STREAM_SEND_DEADLINE_MS * 1000;
    while (remaining > 0) {
        int n = pb_tls_write(t->tls, p, (size_t)remaining);
        if (n == PB_TLS_WANT_READ || n == PB_TLS_WANT_WRITE) {
            // The per-syscall SO_SNDTIMEO fired (a stalled TCP window on a
            // half-dead link) or a renegotiation wants the other direction.
            // Retry until the overall deadline, then fail-and-reconnect.
            // We deliberately do NOT feed the task watchdog here: the
            // deadline is well under CONFIG_ESP_TASK_WDT_TIMEOUT_S, so it
            // bounds this loop on its own — and if that ever stops holding,
            // the watchdog must still fire and produce a crash dump pointing
            // here, rather than us silently masking the hang. The blocking
            // send paces the loop, so it can't hot-spin.
            if ((int64_t)pb_monotonic_us() > deadline) {
                pb_log_warn(TAG, "TLS write stalled >%d ms — reconnecting",
                         SIP_STREAM_SEND_DEADLINE_MS);
                tls_reconnect(t);
                return -1;
            }
            continue;
        }
        if (n <= 0) {
            pb_log_warn(TAG, "TLS write rc=%d", (int)n);
            tls_reconnect(t);
            return -1;
        }
        p += n;
        remaining -= (int)n;
    }
    return len;
}

int sip_transport_send(sip_transport_t *t, const void *buf, int len)
{
    if (t->kind == TR_TLS) return tls_send_all(t, buf, len);
    if (t->kind == TR_TCP) return tcp_send_all(t, buf, len);

    int n = sendto(t->sock, buf, len, 0,
                   (struct sockaddr *)&t->registrar, sizeof(t->registrar));
    if (n < 0) {
        pb_log_err(TAG, "sendto(registrar): %s", strerror(errno));
    }
    return n;
}

int sip_transport_send_to(sip_transport_t *t, const struct sockaddr_in *peer,
                          const void *buf, int len)
{
    if (is_stream(t->kind)) {
        // Per RFC 3261 §18.2.1 the registrar reuses the existing
        // connection for in-dialog messages; the peer arg is
        // informational only. Cheap sanity-log when it disagrees.
        if (peer && peer->sin_addr.s_addr
                 && peer->sin_addr.s_addr != t->registrar.sin_addr.s_addr) {
            pb_log_info(TAG, "stream send_to: peer differs from registrar — ignored");
        }
        return (t->kind == TR_TLS) ? tls_send_all(t, buf, len)
                                   : tcp_send_all(t, buf, len);
    }

    int n = sendto(t->sock, buf, len, 0,
                   (struct sockaddr *)peer, sizeof(*peer));
    if (n < 0) {
        pb_log_err(TAG, "sendto(peer): %s", strerror(errno));
    }
    return n;
}

// ---------------------------------------------------------------------------
// Recv
// ---------------------------------------------------------------------------

static int udp_recv(sip_transport_t *t, int timeout_ms,
                    void *buf, int cap, struct sockaddr_in *from)
{
    if (timeout_ms >= 0) {
        struct timeval tv = {
            .tv_sec  = timeout_ms / 1000,
            .tv_usec = (timeout_ms % 1000) * 1000,
        };
        fd_set rfds;
        FD_ZERO(&rfds);
        FD_SET(t->sock, &rfds);
        int s = select(t->sock + 1, &rfds, NULL, NULL, &tv);
        if (s < 0) {
            pb_log_err(TAG, "select(): %s", strerror(errno));
            return -1;
        }
        if (s == 0) return 0;
    }

    socklen_t from_len = sizeof(*from);
    int r = recvfrom(t->sock, buf, cap, 0,
                     (struct sockaddr *)from, &from_len);
    if (r < 0) {
        pb_log_warn(TAG, "recvfrom(): %s", strerror(errno));
        return -1;
    }
    return r;
}

static int tcp_recv(sip_transport_t *t, int timeout_ms,
                    void *buf, int cap, struct sockaddr_in *from)
{
    // 1. Drain a fully-buffered message first — coalesced TCP segments
    //    routinely deliver two SIP messages in a single read.
    int got = sip_framer_pop(&t->framer, buf, cap);
    if (got > 0) {
        if (from) *from = t->registrar;
        return got;
    }
    if (got < 0) {
        pb_log_warn(TAG, "TCP frame parse error → reconnect");
        tcp_reconnect(t);
        return -1;
    }

    // 2. Recover a missing socket.
    if (t->sock < 0) {
        if (!tcp_connect(t)) return -1;
        t->reconnected_flag = true;
    }

    // 3. Wait for data.
    if (timeout_ms >= 0) {
        struct timeval tv = {
            .tv_sec  = timeout_ms / 1000,
            .tv_usec = (timeout_ms % 1000) * 1000,
        };
        fd_set rfds;
        FD_ZERO(&rfds);
        FD_SET(t->sock, &rfds);
        int s = select(t->sock + 1, &rfds, NULL, NULL, &tv);
        if (s < 0) {
            pb_log_err(TAG, "select(): %s", strerror(errno));
            tcp_reconnect(t);
            return -1;
        }
        if (s == 0) return 0;
    }

    // 4. Read one chunk into the framer; stop short of cap so the
    //    framer can hold a partial follow-up message.
    char chunk[1024];
    int n = recv(t->sock, chunk, sizeof(chunk), 0);
    if (n < 0) {
        pb_log_warn(TAG, "TCP recv: %s", strerror(errno));
        tcp_reconnect(t);
        return -1;
    }
    if (n == 0) {
        pb_log_info(TAG, "registrar closed TCP → reconnect");
        tcp_reconnect(t);
        return -1;
    }
    if (sip_framer_append(&t->framer, chunk, n) < 0) {
        pb_log_warn(TAG, "TCP frame buffer full → reconnect");
        tcp_reconnect(t);
        return -1;
    }

    got = sip_framer_pop(&t->framer, buf, cap);
    if (got < 0) {
        pb_log_warn(TAG, "TCP frame parse error → reconnect");
        tcp_reconnect(t);
        return -1;
    }
    if (got > 0 && from) *from = t->registrar;
    // got may be 0: chunk completed only part of a message; caller's
    // outer loop will return to select() and try again.
    return got;
}

static int tls_recv(sip_transport_t *t, int timeout_ms,
                    void *buf, int cap, struct sockaddr_in *from)
{
    // 1. Drain a fully-buffered message first (a single TLS record can
    //    decrypt to plaintext that holds two SIP messages).
    int got = sip_framer_pop(&t->framer, buf, cap);
    if (got > 0) {
        if (from) *from = t->registrar;
        return got;
    }
    if (got < 0) {
        pb_log_warn(TAG, "TLS frame parse error → reconnect");
        tls_reconnect(t);
        return -1;
    }

    // 2. Recover a missing connection.
    if (!t->tls) {
        if (!tls_connect(t)) return -1;
        t->reconnected_flag = true;
    }

    // 3. Wait for data on the underlying socket — but ONLY when mbedTLS
    //    has nothing decrypted and buffered already. A single TLS record
    //    can decrypt to more plaintext than the 1 KiB chunk in step 4
    //    consumes; the remainder then sits in mbedTLS's internal buffer
    //    while the raw socket goes empty. select() watches only the
    //    socket fd, so it would block here until timeout even though a
    //    complete SIP response is waiting inside the TLS layer — which is
    //    exactly how a 1590-byte Telekom 200 OK got stranded for a full
    //    cycle (read one chunk, miss the rest, time out, pick it up only
    //    after the *next* REGISTER nudged the socket). Draining the TLS
    //    buffer first closes that gap. esp-tls in sync mode has no
    //    built-in select; reading the fd back lets us share the same
    //    timeout-driven loop with UDP/TCP.
    int fd = pb_tls_fd(t->tls);
    if (fd < 0 || fd < 0) {
        pb_log_warn(TAG, "esp_tls_get_conn_sockfd failed → reconnect");
        tls_reconnect(t);
        return -1;
    }
    if (timeout_ms >= 0 && pb_tls_pending(t->tls) <= 0) {
        struct timeval tv = {
            .tv_sec  = timeout_ms / 1000,
            .tv_usec = (timeout_ms % 1000) * 1000,
        };
        fd_set rfds;
        FD_ZERO(&rfds);
        FD_SET(fd, &rfds);
        int s = select(fd + 1, &rfds, NULL, NULL, &tv);
        if (s < 0) {
            pb_log_err(TAG, "select(): %s", strerror(errno));
            tls_reconnect(t);
            return -1;
        }
        if (s == 0) return 0;
    }

    // 4. Read one chunk through TLS. esp_tls_conn_read may decrypt to
    //    less than what's queued (records are processed one at a time);
    //    that's fine — the framer accumulates across calls.
    char chunk[1024];
    int n = pb_tls_read(t->tls, chunk, sizeof(chunk));
    if (n == PB_TLS_WANT_READ || n == PB_TLS_WANT_WRITE) {
        // TLS-level retry needed (e.g. processing an alert record).
        // Surface as a no-op timeout; outer loop retries.
        return 0;
    }
    if (n < 0) {
        pb_log_warn(TAG, "TLS read rc=%d → reconnect", (int)n);
        tls_reconnect(t);
        return -1;
    }
    if (n == 0) {
        pb_log_info(TAG, "registrar closed TLS → reconnect");
        tls_reconnect(t);
        return -1;
    }
    if (sip_framer_append(&t->framer, chunk, (int)n) < 0) {
        pb_log_warn(TAG, "TLS frame buffer full → reconnect");
        tls_reconnect(t);
        return -1;
    }

    got = sip_framer_pop(&t->framer, buf, cap);
    if (got < 0) {
        pb_log_warn(TAG, "TLS frame parse error → reconnect");
        tls_reconnect(t);
        return -1;
    }
    if (got > 0 && from) *from = t->registrar;
    return got;
}

int sip_transport_recv(sip_transport_t *t, int timeout_ms,
                       void *buf, int cap,
                       struct sockaddr_in *from)
{
    if (t->kind == TR_TLS) return tls_recv(t, timeout_ms, buf, cap, from);
    if (t->kind == TR_TCP) return tcp_recv(t, timeout_ms, buf, cap, from);
    return udp_recv(t, timeout_ms, buf, cap, from);
}

// ---------------------------------------------------------------------------
// Accessors
// ---------------------------------------------------------------------------

const char *sip_transport_local_ip(const sip_transport_t *t)
{
    return t->local_ip;
}

int sip_transport_local_port(const sip_transport_t *t)
{
    return t->local_port;
}

const char *sip_transport_via_token(const sip_transport_t *t)
{
    return t->via_token;
}

const char *sip_transport_uri_param(const sip_transport_t *t)
{
    return t->uri_param;
}

bool sip_transport_consume_reconnect(sip_transport_t *t)
{
    if (!t->reconnected_flag) return false;
    t->reconnected_flag = false;
    return true;
}
