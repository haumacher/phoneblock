#include "sip_transport.h"

#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <errno.h>

#include "esp_log.h"
#include "esp_netif.h"
#include "esp_tls.h"
#include "esp_crt_bundle.h"

#include "lwip/netdb.h"

#include "sip_frame.h"

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

struct sip_transport {
    transport_kind_t kind;
    int  sock;                       // UDP/TCP socket fd; -1 for TLS
    esp_tls_t *tls;                  // TLS only
    struct sockaddr_in registrar;    // peer addr for UDP/TCP; informational for TLS
    char registrar_host[64];         // saved for TCP/TLS reconnect (esp-tls needs hostname for SNI)
    int  registrar_port;
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
static bool tls_connect(sip_transport_t *t);
static void tls_drop(sip_transport_t *t);

// ---------------------------------------------------------------------------
// Common helpers
// ---------------------------------------------------------------------------

static bool discover_local_ip(struct sip_transport *t)
{
    esp_netif_t *netif = esp_netif_get_default_netif();
    if (!netif) {
        ESP_LOGE(TAG, "no default netif");
        return false;
    }
    esp_netif_ip_info_t ip;
    if (esp_netif_get_ip_info(netif, &ip) != ESP_OK) {
        ESP_LOGE(TAG, "get_ip_info failed");
        return false;
    }
    esp_ip4addr_ntoa(&ip.ip, t->local_ip, sizeof(t->local_ip));
    return true;
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
        ESP_LOGE(TAG, "DNS lookup of %s failed: %d", host, err);
        return false;
    }
    memcpy(out, res->ai_addr, sizeof(*out));
    freeaddrinfo(res);
    return true;
}

bool sip_transport_resolve(sip_transport_t *t,
                           const char *host, int port)
{
    int socktype = (t->kind == TR_UDP) ? SOCK_DGRAM : SOCK_STREAM;
    if (!dns_resolve(host, port, socktype, &t->registrar)) return false;

    char ip[INET_ADDRSTRLEN];
    inet_ntoa_r(t->registrar.sin_addr, ip, sizeof(ip));
    ESP_LOGI(TAG, "registrar %s:%d → %s", host, port, ip);

    // Stream transports keep host/port for transparent reconnects;
    // esp-tls in particular needs the hostname for SNI on every retry.
    if (t->kind == TR_TCP || t->kind == TR_TLS) {
        strncpy(t->registrar_host, host, sizeof(t->registrar_host) - 1);
        t->registrar_host[sizeof(t->registrar_host) - 1] = '\0';
        t->registrar_port = port;
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
        ESP_LOGE(TAG, "socket(UDP): %s", strerror(errno));
        return false;
    }

    struct sockaddr_in local = {
        .sin_family      = AF_INET,
        .sin_addr.s_addr = htonl(INADDR_ANY),
        .sin_port        = htons(local_port),
    };
    if (bind(t->sock, (struct sockaddr *)&local, sizeof(local)) < 0) {
        ESP_LOGE(TAG, "bind(UDP %d): %s", local_port, strerror(errno));
        close(t->sock);
        t->sock = -1;
        return false;
    }
    ESP_LOGI(TAG, "local IP %s, SIP UDP port %d", t->local_ip, local_port);
    return true;
}

// ---------------------------------------------------------------------------
// TCP open / connect / drop
// ---------------------------------------------------------------------------

static bool tcp_connect(sip_transport_t *t)
{
    int sock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (sock < 0) {
        ESP_LOGE(TAG, "socket(TCP): %s", strerror(errno));
        return false;
    }

    if (t->local_port > 0) {
        struct sockaddr_in local = {
            .sin_family      = AF_INET,
            .sin_addr.s_addr = htonl(INADDR_ANY),
            .sin_port        = htons(t->local_port),
        };
        int yes = 1;
        setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes));
        if (bind(sock, (struct sockaddr *)&local, sizeof(local)) < 0) {
            // Non-fatal: kernel will pick an ephemeral port. Via/Contact
            // get re-read below from getsockname() so consistency holds.
            ESP_LOGW(TAG, "TCP bind %d failed (%s); using ephemeral",
                     t->local_port, strerror(errno));
        }
    }

    if (connect(sock, (struct sockaddr *)&t->registrar,
                sizeof(t->registrar)) < 0) {
        ESP_LOGW(TAG, "TCP connect failed: %s", strerror(errno));
        close(sock);
        return false;
    }

    struct sockaddr_in actual = {0};
    socklen_t alen = sizeof(actual);
    if (getsockname(sock, (struct sockaddr *)&actual, &alen) == 0) {
        t->local_port = ntohs(actual.sin_port);
    }

    t->sock = sock;
    sip_framer_reset(&t->framer);
    ESP_LOGI(TAG, "TCP connected, local port %d", t->local_port);
    return true;
}

static void tcp_drop(sip_transport_t *t)
{
    if (t->sock >= 0) {
        close(t->sock);
        t->sock = -1;
    }
    sip_framer_reset(&t->framer);
}

// Triggered after a recv/send error. Reconnects once; on success the
// caller's next REGISTER round-trip succeeds and we surface the
// reconnect_flag so sip_register can fire a fresh REGISTER. On failure
// the socket stays -1 and the next call retries the connect.
static void tcp_reconnect(sip_transport_t *t)
{
    tcp_drop(t);
    if (tcp_connect(t)) {
        t->reconnected_flag = true;
    }
}

// ---------------------------------------------------------------------------
// TLS connect / drop
// ---------------------------------------------------------------------------

static bool tls_connect(sip_transport_t *t)
{
    // Cert verification: rely on the ESP-IDF certificate bundle
    // (CONFIG_MBEDTLS_CERTIFICATE_BUNDLE=y in sdkconfig.defaults). We
    // intentionally do NOT pin a specific cert: a Telekom CA rotation
    // would otherwise require a firmware update before the dongle could
    // re-register. The bundle's update cycle is governed by IDF.
    //
    // SNI is implicit — esp_tls_conn_new_sync() sends the hostname
    // argument as SNI extension by default.
    esp_tls_cfg_t cfg = {
        .crt_bundle_attach = esp_crt_bundle_attach,
        .timeout_ms        = SIP_TLS_HANDSHAKE_TIMEOUT_MS,
    };

    esp_tls_t *tls = esp_tls_init();
    if (!tls) {
        ESP_LOGE(TAG, "esp_tls_init failed");
        return false;
    }
    int rc = esp_tls_conn_new_sync(t->registrar_host,
                                   (int)strlen(t->registrar_host),
                                   t->registrar_port, &cfg, tls);
    if (rc != 1) {
        ESP_LOGW(TAG, "TLS connect to %s:%d failed (rc=%d)",
                 t->registrar_host, t->registrar_port, rc);
        esp_tls_conn_destroy(tls);
        return false;
    }

    // Read back the kernel-assigned local port from the underlying fd
    // so Via/Contact stay consistent with what the registrar sees.
    int fd = -1;
    if (esp_tls_get_conn_sockfd(tls, &fd) == ESP_OK && fd >= 0) {
        struct sockaddr_in actual = {0};
        socklen_t alen = sizeof(actual);
        if (getsockname(fd, (struct sockaddr *)&actual, &alen) == 0) {
            t->local_port = ntohs(actual.sin_port);
        }
    }

    t->tls = tls;
    sip_framer_reset(&t->framer);
    ESP_LOGI(TAG, "TLS connected to %s:%d, local port %d",
             t->registrar_host, t->registrar_port, t->local_port);
    return true;
}

static void tls_drop(sip_transport_t *t)
{
    if (t->tls) {
        esp_tls_conn_destroy(t->tls);
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
            ESP_LOGW(TAG,
                     "transport \"%s\" not yet implemented, falling back to UDP",
                     transport);
        }
    }

    // Per-transport default port when caller passed 0/<=0.
    if (registrar_port <= 0) {
        registrar_port = (kind == TR_TLS) ? 5061 : 5060;
        ESP_LOGI(TAG, "applying default port %d for transport %s",
                 registrar_port, via);
    }

    struct sip_transport *t = calloc(1, sizeof(*t));
    if (!t) return NULL;
    t->sock = -1;
    t->kind = kind;
    t->local_port = local_port;
    t->registrar_port = registrar_port;
    strcpy(t->via_token, via);
    strcpy(t->uri_param, uri);
    if (kind == TR_TCP || kind == TR_TLS) {
        strncpy(t->registrar_host, registrar_host, sizeof(t->registrar_host) - 1);
    }

    if (!discover_local_ip(t)) goto fail;

    int socktype = (kind == TR_UDP) ? SOCK_DGRAM : SOCK_STREAM;
    if (!dns_resolve(registrar_host, registrar_port, socktype, &t->registrar)) {
        goto fail;
    }
    char ip[INET_ADDRSTRLEN];
    inet_ntoa_r(t->registrar.sin_addr, ip, sizeof(ip));
    ESP_LOGI(TAG, "registrar %s:%d → %s", registrar_host, registrar_port, ip);

    if (kind == TR_TCP || kind == TR_TLS) {
        t->frame_buf = malloc(SIP_TCP_FRAME_BUF);
        if (!t->frame_buf) {
            ESP_LOGE(TAG, "frame buffer malloc failed");
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
    if (t->tls) esp_tls_conn_destroy(t->tls);
    free(t);
    return NULL;
}

void sip_transport_close(sip_transport_t *t)
{
    if (!t) return;
    if (t->sock >= 0) close(t->sock);
    if (t->tls) esp_tls_conn_destroy(t->tls);
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
    while (remaining > 0) {
        int n = send(t->sock, p, remaining, 0);
        if (n <= 0) {
            ESP_LOGW(TAG, "TCP send: %s", strerror(errno));
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
    while (remaining > 0) {
        ssize_t n = esp_tls_conn_write(t->tls, p, (size_t)remaining);
        if (n == ESP_TLS_ERR_SSL_WANT_READ || n == ESP_TLS_ERR_SSL_WANT_WRITE) {
            // Synchronous mode shouldn't really return WANT_*, but if a
            // renegotiation pulls us here, just retry once after a tick.
            continue;
        }
        if (n <= 0) {
            ESP_LOGW(TAG, "TLS write rc=%d", (int)n);
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
        ESP_LOGE(TAG, "sendto(registrar): %s", strerror(errno));
    }
    return n;
}

int sip_transport_send_to(sip_transport_t *t, const struct sockaddr_in *peer,
                          const void *buf, int len)
{
    if (t->kind == TR_TCP || t->kind == TR_TLS) {
        // Per RFC 3261 §18.2.1 the registrar reuses the existing
        // connection for in-dialog messages; the peer arg is
        // informational only. Cheap sanity-log when it disagrees.
        if (peer && peer->sin_addr.s_addr
                 && peer->sin_addr.s_addr != t->registrar.sin_addr.s_addr) {
            ESP_LOGD(TAG, "stream send_to: peer differs from registrar — ignored");
        }
        return (t->kind == TR_TLS) ? tls_send_all(t, buf, len)
                                   : tcp_send_all(t, buf, len);
    }

    int n = sendto(t->sock, buf, len, 0,
                   (struct sockaddr *)peer, sizeof(*peer));
    if (n < 0) {
        ESP_LOGE(TAG, "sendto(peer): %s", strerror(errno));
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
            ESP_LOGE(TAG, "select(): %s", strerror(errno));
            return -1;
        }
        if (s == 0) return 0;
    }

    socklen_t from_len = sizeof(*from);
    int r = recvfrom(t->sock, buf, cap, 0,
                     (struct sockaddr *)from, &from_len);
    if (r < 0) {
        ESP_LOGW(TAG, "recvfrom(): %s", strerror(errno));
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
        ESP_LOGW(TAG, "TCP frame parse error → reconnect");
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
            ESP_LOGE(TAG, "select(): %s", strerror(errno));
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
        ESP_LOGW(TAG, "TCP recv: %s", strerror(errno));
        tcp_reconnect(t);
        return -1;
    }
    if (n == 0) {
        ESP_LOGI(TAG, "registrar closed TCP → reconnect");
        tcp_reconnect(t);
        return -1;
    }
    if (sip_framer_append(&t->framer, chunk, n) < 0) {
        ESP_LOGW(TAG, "TCP frame buffer full → reconnect");
        tcp_reconnect(t);
        return -1;
    }

    got = sip_framer_pop(&t->framer, buf, cap);
    if (got < 0) {
        ESP_LOGW(TAG, "TCP frame parse error → reconnect");
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
        ESP_LOGW(TAG, "TLS frame parse error → reconnect");
        tls_reconnect(t);
        return -1;
    }

    // 2. Recover a missing connection.
    if (!t->tls) {
        if (!tls_connect(t)) return -1;
        t->reconnected_flag = true;
    }

    // 3. Wait for data on the underlying socket. esp-tls in sync mode
    //    has no built-in select; reading the fd back lets us share the
    //    same timeout-driven loop with UDP/TCP.
    int fd = -1;
    if (esp_tls_get_conn_sockfd(t->tls, &fd) != ESP_OK || fd < 0) {
        ESP_LOGW(TAG, "esp_tls_get_conn_sockfd failed → reconnect");
        tls_reconnect(t);
        return -1;
    }
    if (timeout_ms >= 0) {
        struct timeval tv = {
            .tv_sec  = timeout_ms / 1000,
            .tv_usec = (timeout_ms % 1000) * 1000,
        };
        fd_set rfds;
        FD_ZERO(&rfds);
        FD_SET(fd, &rfds);
        int s = select(fd + 1, &rfds, NULL, NULL, &tv);
        if (s < 0) {
            ESP_LOGE(TAG, "select(): %s", strerror(errno));
            tls_reconnect(t);
            return -1;
        }
        if (s == 0) return 0;
    }

    // 4. Read one chunk through TLS. esp_tls_conn_read may decrypt to
    //    less than what's queued (records are processed one at a time);
    //    that's fine — the framer accumulates across calls.
    char chunk[1024];
    ssize_t n = esp_tls_conn_read(t->tls, chunk, sizeof(chunk));
    if (n == ESP_TLS_ERR_SSL_WANT_READ || n == ESP_TLS_ERR_SSL_WANT_WRITE) {
        // TLS-level retry needed (e.g. processing an alert record).
        // Surface as a no-op timeout; outer loop retries.
        return 0;
    }
    if (n < 0) {
        ESP_LOGW(TAG, "TLS read rc=%d → reconnect", (int)n);
        tls_reconnect(t);
        return -1;
    }
    if (n == 0) {
        ESP_LOGI(TAG, "registrar closed TLS → reconnect");
        tls_reconnect(t);
        return -1;
    }
    if (sip_framer_append(&t->framer, chunk, (int)n) < 0) {
        ESP_LOGW(TAG, "TLS frame buffer full → reconnect");
        tls_reconnect(t);
        return -1;
    }

    got = sip_framer_pop(&t->framer, buf, cap);
    if (got < 0) {
        ESP_LOGW(TAG, "TLS frame parse error → reconnect");
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
