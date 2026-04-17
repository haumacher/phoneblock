#include "sip_register.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <stdbool.h>
#include <errno.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include "esp_log.h"
#include "esp_random.h"
#include "esp_netif.h"
#include "esp_timer.h"
#include "mbedtls/md5.h"

#include "lwip/sockets.h"
#include "lwip/netdb.h"

#include "sdkconfig.h"

static const char *TAG = "sip";

#define SIP_LOCAL_PORT       5061  // local UDP for SIP (not the TCP dummy server)
#define SIP_RX_BUF_SIZE      4096
#define SIP_TX_BUF_SIZE      2048
#define SIP_RECV_TIMEOUT_S   3
#define SIP_MAX_CHALLENGE    256

typedef struct {
    // Parsed auth challenge from the 401 response.
    char realm[64];
    char nonce[SIP_MAX_CHALLENGE];
    char opaque[64];
    char qop[16];        // "auth" or empty
    char algorithm[16];  // "MD5" or empty (default MD5)
    bool valid;
} auth_challenge_t;

typedef struct {
    char     local_ip[INET_ADDRSTRLEN];
    char     call_id[40];
    char     from_tag[20];
    uint32_t cseq;
    int      sock;
    struct sockaddr_in registrar;
    auth_challenge_t challenge;
    bool     registered;
} sip_ctx_t;

static bool s_registered = false;

bool sip_register_is_registered(void)
{
    return s_registered;
}

// ---------------------------------------------------------------------------
// Hex / MD5 helpers
// ---------------------------------------------------------------------------

static void bytes_to_hex(const uint8_t *in, size_t len, char *out)
{
    static const char hex[] = "0123456789abcdef";
    for (size_t i = 0; i < len; i++) {
        out[i * 2]     = hex[in[i] >> 4];
        out[i * 2 + 1] = hex[in[i] & 0x0f];
    }
    out[len * 2] = '\0';
}

static void md5_str(const char *in, char *out_hex33)
{
    uint8_t digest[16];
    mbedtls_md5((const unsigned char *)in, strlen(in), digest);
    bytes_to_hex(digest, 16, out_hex33);
}

static void random_hex(char *out, size_t hex_bytes)
{
    uint32_t r[4];
    for (int i = 0; i < 4; i++) r[i] = esp_random();
    bytes_to_hex((uint8_t *)r, sizeof(r), out);
    out[hex_bytes] = '\0';
}

// ---------------------------------------------------------------------------
// Digest response
//
//   HA1 = MD5(user:realm:password)
//   HA2 = MD5(method:uri)
//   if qop == "auth":
//     response = MD5(HA1:nonce:nc:cnonce:qop:HA2)
//   else:
//     response = MD5(HA1:nonce:HA2)
// ---------------------------------------------------------------------------

static void digest_response(
    const char *user, const char *password, const char *realm,
    const char *nonce, const char *method, const char *uri,
    const char *qop, const char *nc, const char *cnonce,
    char *out_hex33)
{
    char tmp[256];
    char ha1[33], ha2[33];

    snprintf(tmp, sizeof(tmp), "%s:%s:%s", user, realm, password);
    md5_str(tmp, ha1);

    snprintf(tmp, sizeof(tmp), "%s:%s", method, uri);
    md5_str(tmp, ha2);

    if (qop && qop[0] != '\0') {
        snprintf(tmp, sizeof(tmp), "%s:%s:%s:%s:%s:%s",
                 ha1, nonce, nc, cnonce, qop, ha2);
    } else {
        snprintf(tmp, sizeof(tmp), "%s:%s:%s", ha1, nonce, ha2);
    }
    md5_str(tmp, out_hex33);
}

// ---------------------------------------------------------------------------
// WWW-Authenticate parser
//
// Handles the subset we care about: digest scheme, comma-separated
// key=value pairs, quoted values. Lenient w.r.t. whitespace.
// ---------------------------------------------------------------------------

static void copy_value(const char *src, size_t src_len, char *dst, size_t dst_cap)
{
    size_t n = src_len < dst_cap - 1 ? src_len : dst_cap - 1;
    memcpy(dst, src, n);
    dst[n] = '\0';
}

static void parse_auth_challenge(const char *header_value, auth_challenge_t *out)
{
    memset(out, 0, sizeof(*out));
    // Default algorithm is MD5.
    strcpy(out->algorithm, "MD5");

    // Skip "Digest" scheme prefix if present.
    const char *p = header_value;
    while (*p == ' ') p++;
    if (strncasecmp(p, "Digest", 6) == 0) {
        p += 6;
        while (*p == ' ') p++;
    }

    while (*p) {
        // Skip whitespace and commas.
        while (*p == ' ' || *p == ',' || *p == '\t') p++;
        if (!*p) break;

        const char *key = p;
        while (*p && *p != '=' && *p != ',') p++;
        size_t key_len = p - key;
        if (*p != '=') {
            // Malformed; skip to next comma.
            while (*p && *p != ',') p++;
            continue;
        }
        p++;  // '='

        const char *val;
        size_t val_len;
        if (*p == '"') {
            p++;
            val = p;
            while (*p && *p != '"') p++;
            val_len = p - val;
            if (*p == '"') p++;
        } else {
            val = p;
            while (*p && *p != ',' && *p != ' ' && *p != '\r' && *p != '\n') p++;
            val_len = p - val;
        }

        if (key_len == 5 && strncasecmp(key, "realm", 5) == 0) {
            copy_value(val, val_len, out->realm, sizeof(out->realm));
        } else if (key_len == 5 && strncasecmp(key, "nonce", 5) == 0) {
            copy_value(val, val_len, out->nonce, sizeof(out->nonce));
        } else if (key_len == 6 && strncasecmp(key, "opaque", 6) == 0) {
            copy_value(val, val_len, out->opaque, sizeof(out->opaque));
        } else if (key_len == 3 && strncasecmp(key, "qop", 3) == 0) {
            // qop may be a comma-list (e.g., "auth,auth-int"). Pick "auth".
            char qvals[32];
            copy_value(val, val_len, qvals, sizeof(qvals));
            if (strstr(qvals, "auth-int") && !strstr(qvals, "auth,") && !strstr(qvals, "auth ")) {
                // only auth-int offered; not supported
                strcpy(out->qop, "");
            } else if (strstr(qvals, "auth")) {
                strcpy(out->qop, "auth");
            }
        } else if (key_len == 9 && strncasecmp(key, "algorithm", 9) == 0) {
            copy_value(val, val_len, out->algorithm, sizeof(out->algorithm));
        }
    }

    out->valid = out->realm[0] != '\0' && out->nonce[0] != '\0';
}

// ---------------------------------------------------------------------------
// Message building
// ---------------------------------------------------------------------------

static int build_register(sip_ctx_t *c, char *buf, int cap, bool with_auth)
{
    char branch[20], cnonce[20];
    random_hex(branch, 16);

    // Via/Contact advertise where the registrar should reach us. On real
    // hardware that's the interface IP/UDP port. Inside QEMU user-mode
    // networking, these have to be overridden to the host-visible address
    // (host LAN IP + hostfwd port), otherwise the registrar cannot deliver
    // an incoming INVITE — it would try to connect to 10.0.2.x which only
    // exists inside the emulator.
    const char *advertised_host =
        strlen(CONFIG_SIP_CONTACT_HOST_OVERRIDE) > 0
            ? CONFIG_SIP_CONTACT_HOST_OVERRIDE : c->local_ip;
    int advertised_port =
        CONFIG_SIP_CONTACT_PORT_OVERRIDE != 0
            ? CONFIG_SIP_CONTACT_PORT_OVERRIDE : SIP_LOCAL_PORT;

    char request_uri[96];
    snprintf(request_uri, sizeof(request_uri), "sip:%s", CONFIG_SIP_REGISTRAR_HOST);

    int n = snprintf(buf, cap,
        "REGISTER %s SIP/2.0\r\n"
        "Via: SIP/2.0/UDP %s:%d;branch=z9hG4bK%s;rport\r\n"
        "Max-Forwards: 70\r\n"
        "From: <sip:%s@%s>;tag=%s\r\n"
        "To: <sip:%s@%s>\r\n"
        "Call-ID: %s\r\n"
        "CSeq: %lu REGISTER\r\n"
        "Contact: <sip:%s@%s:%d>\r\n"
        "Expires: %d\r\n"
        "User-Agent: phoneblock-dongle/0.1\r\n",
        request_uri,
        advertised_host, advertised_port, branch,
        CONFIG_SIP_USERNAME, CONFIG_SIP_REGISTRAR_HOST, c->from_tag,
        CONFIG_SIP_USERNAME, CONFIG_SIP_REGISTRAR_HOST,
        c->call_id,
        (unsigned long)c->cseq,
        CONFIG_SIP_USERNAME, advertised_host, advertised_port,
        CONFIG_SIP_EXPIRES);

    if (with_auth && c->challenge.valid) {
        char response[33];
        const char *qop = c->challenge.qop;
        const char *nc  = "00000001";
        random_hex(cnonce, 16);

        digest_response(
            CONFIG_SIP_USERNAME, CONFIG_SIP_PASSWORD,
            c->challenge.realm, c->challenge.nonce,
            "REGISTER", request_uri,
            qop, nc, cnonce,
            response);

        n += snprintf(buf + n, cap - n,
            "Authorization: Digest username=\"%s\", realm=\"%s\", "
            "nonce=\"%s\", uri=\"%s\", response=\"%s\", algorithm=%s",
            CONFIG_SIP_USERNAME, c->challenge.realm,
            c->challenge.nonce, request_uri,
            response, c->challenge.algorithm);

        if (qop[0]) {
            n += snprintf(buf + n, cap - n,
                ", qop=%s, nc=%s, cnonce=\"%s\"",
                qop, nc, cnonce);
        }
        if (c->challenge.opaque[0]) {
            n += snprintf(buf + n, cap - n,
                ", opaque=\"%s\"", c->challenge.opaque);
        }
        n += snprintf(buf + n, cap - n, "\r\n");
    }

    n += snprintf(buf + n, cap - n, "Content-Length: 0\r\n\r\n");
    return n;
}

// ---------------------------------------------------------------------------
// Response parsing
// ---------------------------------------------------------------------------

static int parse_status_code(const char *resp, int len)
{
    // "SIP/2.0 401 Unauthorized\r\n"
    if (len < 12) return -1;
    if (strncmp(resp, "SIP/2.0 ", 8) != 0) return -1;
    int status = 0;
    for (int i = 8; i < len && i < 12; i++) {
        if (resp[i] >= '0' && resp[i] <= '9') {
            status = status * 10 + (resp[i] - '0');
        } else break;
    }
    return status;
}

static const char *find_header(const char *resp, int len, const char *name)
{
    size_t name_len = strlen(name);
    const char *p = resp;
    const char *end = resp + len;
    // Skip first line.
    while (p < end && *p != '\n') p++;
    if (p < end) p++;
    while (p + name_len + 1 < end) {
        if (strncasecmp(p, name, name_len) == 0 && p[name_len] == ':') {
            p += name_len + 1;
            while (p < end && (*p == ' ' || *p == '\t')) p++;
            return p;
        }
        // Advance to next line.
        while (p < end && *p != '\n') p++;
        if (p < end) p++;
    }
    return NULL;
}

// Copy a header value into out, up to CR/LF (folded continuations not handled).
static int header_value(const char *p, const char *end, char *out, int cap)
{
    int n = 0;
    while (p < end && *p != '\r' && *p != '\n' && n < cap - 1) {
        out[n++] = *p++;
    }
    out[n] = '\0';
    return n;
}

// ---------------------------------------------------------------------------
// Networking
// ---------------------------------------------------------------------------

static int udp_send_recv(sip_ctx_t *c, const char *tx, int tx_len,
                         char *rx, int rx_cap)
{
    int n = sendto(c->sock, tx, tx_len, 0,
                   (struct sockaddr *)&c->registrar, sizeof(c->registrar));
    if (n < 0) {
        ESP_LOGE(TAG, "sendto(): %s", strerror(errno));
        return -1;
    }

    struct sockaddr_in from;
    socklen_t from_len = sizeof(from);
    int r = recvfrom(c->sock, rx, rx_cap - 1, 0,
                     (struct sockaddr *)&from, &from_len);
    if (r < 0) {
        ESP_LOGW(TAG, "recvfrom(): %s", strerror(errno));
        return -1;
    }
    rx[r] = '\0';
    return r;
}

static bool resolve_registrar(sip_ctx_t *c)
{
    struct addrinfo hints = { .ai_family = AF_INET, .ai_socktype = SOCK_DGRAM };
    struct addrinfo *res = NULL;

    char port_str[8];
    snprintf(port_str, sizeof(port_str), "%d", CONFIG_SIP_REGISTRAR_PORT);

    int err = getaddrinfo(CONFIG_SIP_REGISTRAR_HOST, port_str, &hints, &res);
    if (err != 0 || !res) {
        ESP_LOGE(TAG, "DNS lookup of %s failed: %d", CONFIG_SIP_REGISTRAR_HOST, err);
        return false;
    }
    memcpy(&c->registrar, res->ai_addr, sizeof(c->registrar));
    freeaddrinfo(res);

    char ip[INET_ADDRSTRLEN];
    inet_ntoa_r(c->registrar.sin_addr, ip, sizeof(ip));
    ESP_LOGI(TAG, "registrar %s:%d → %s", CONFIG_SIP_REGISTRAR_HOST,
             CONFIG_SIP_REGISTRAR_PORT, ip);
    return true;
}

static bool discover_local_ip(sip_ctx_t *c)
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
    esp_ip4addr_ntoa(&ip.ip, c->local_ip, sizeof(c->local_ip));
    ESP_LOGI(TAG, "local IP %s, SIP UDP port %d", c->local_ip, SIP_LOCAL_PORT);
    return true;
}

static bool open_sip_socket(sip_ctx_t *c)
{
    c->sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (c->sock < 0) {
        ESP_LOGE(TAG, "socket(): %s", strerror(errno));
        return false;
    }

    struct sockaddr_in local = {
        .sin_family      = AF_INET,
        .sin_addr.s_addr = htonl(INADDR_ANY),
        .sin_port        = htons(SIP_LOCAL_PORT),
    };
    if (bind(c->sock, (struct sockaddr *)&local, sizeof(local)) < 0) {
        ESP_LOGE(TAG, "bind(): %s", strerror(errno));
        close(c->sock);
        c->sock = -1;
        return false;
    }

    struct timeval tv = { .tv_sec = SIP_RECV_TIMEOUT_S, .tv_usec = 0 };
    setsockopt(c->sock, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    return true;
}

// ---------------------------------------------------------------------------
// REGISTER exchange: send initial, parse challenge, send with auth
// ---------------------------------------------------------------------------

static bool do_register(sip_ctx_t *c)
{
    // Heap-allocate the 6 KB of message buffers — on the 8 KB task stack
    // they overflow once lwip's sendto/recvfrom add their own overhead.
    char *tx = malloc(SIP_TX_BUF_SIZE);
    char *rx = malloc(SIP_RX_BUF_SIZE);
    if (!tx || !rx) {
        ESP_LOGE(TAG, "malloc failed for SIP buffers");
        free(tx); free(rx);
        return false;
    }

    bool result = false;
    c->cseq++;
    int tx_len = build_register(c, tx, SIP_TX_BUF_SIZE, false);
    ESP_LOGI(TAG, "→ REGISTER (%d bytes):\n%.*s", tx_len, tx_len, tx);
    int rx_len = udp_send_recv(c, tx, tx_len, rx, SIP_RX_BUF_SIZE);
    if (rx_len < 0) {
        ESP_LOGE(TAG, "udp_send_recv failed");
        goto cleanup;
    }
    ESP_LOGI(TAG, "← %d bytes:\n%.*s", rx_len, rx_len, rx);

    int status = parse_status_code(rx, rx_len);
    ESP_LOGI(TAG, "← %d (%d bytes)", status, rx_len);

    if (status == 200) {
        result = true;
        goto cleanup;
    }
    if (status != 401 && status != 407) {
        ESP_LOGE(TAG, "unexpected status %d", status);
        goto cleanup;
    }

    // Parse challenge.
    const char *hdr = find_header(rx, rx_len, "WWW-Authenticate");
    if (!hdr) hdr = find_header(rx, rx_len, "Proxy-Authenticate");
    if (!hdr) {
        ESP_LOGE(TAG, "%d without auth header", status);
        goto cleanup;
    }
    char val[SIP_MAX_CHALLENGE + 64];
    header_value(hdr, rx + rx_len, val, sizeof(val));
    parse_auth_challenge(val, &c->challenge);
    if (!c->challenge.valid) {
        ESP_LOGE(TAG, "auth challenge missing realm/nonce");
        goto cleanup;
    }
    ESP_LOGI(TAG, "challenge: realm=\"%s\" qop=\"%s\"",
             c->challenge.realm, c->challenge.qop);

    // Resend with Authorization header.
    c->cseq++;
    tx_len = build_register(c, tx, SIP_TX_BUF_SIZE, true);
    ESP_LOGI(TAG, "→ REGISTER with auth (%d bytes):\n%.*s", tx_len, tx_len, tx);
    rx_len = udp_send_recv(c, tx, tx_len, rx, SIP_RX_BUF_SIZE);
    if (rx_len < 0) goto cleanup;
    ESP_LOGI(TAG, "← %d bytes:\n%.*s", rx_len, rx_len, rx);

    status = parse_status_code(rx, rx_len);
    ESP_LOGI(TAG, "← %d (authenticated)", status);
    if (status != 200) {
        ESP_LOGE(TAG, "authentication rejected: %d", status);
        goto cleanup;
    }
    result = true;

cleanup:
    free(tx);
    free(rx);
    return result;
}

// ---------------------------------------------------------------------------
// Incoming request handling (step 1: log only)
// ---------------------------------------------------------------------------

static void handle_incoming(sip_ctx_t *c, const char *pkt, int len,
                            const struct sockaddr_in *from)
{
    char from_ip[INET_ADDRSTRLEN];
    inet_ntoa_r(from->sin_addr, from_ip, sizeof(from_ip));

    ESP_LOGI(TAG, "← from %s:%d  %d bytes:\n%.*s",
             from_ip, ntohs(from->sin_port),
             len, len, pkt);
}

// ---------------------------------------------------------------------------
// Task — select-based event loop
//
// Reads look like a busy `while (1)`, but it's the opposite: the task sleeps
// inside select() until either a packet arrives or the deadline hits. select()
// is a blocking POSIX syscall — while the task waits, FreeRTOS parks it and
// lets the idle task put the core into `waiti` (low-power wait). The wakeup
// chain is fully interrupt-driven:
//
//     +-----------+     +--------+     +-------+     +----------+
//     | Ethernet/ | IRQ | lwIP   | msg | socket| sig | SIP task |
//     | WLAN ISR  |---->| stack  |---->| layer |---->| resumes  |
//     +-----------+     +--------+     +-------+     +----------+
//
// The timeout argument to select() turns the socket-wait into an "X or wall
// clock" wait: whichever comes first wakes us up. We use that to schedule the
// REGISTER refresh precisely without a separate timer task — when select()
// returns 0, the refresh deadline was reached; when it returns > 0, a packet
// is queued on the socket and we drain it with recvfrom().
//
// This shape (receive-forever, with a timeout for periodic work) is the
// canonical way to write a UDP server under POSIX. No polling, no
// busy-waits — CPU usage is essentially zero between events.
// ---------------------------------------------------------------------------

static void sip_task(void *arg)
{
    sip_ctx_t ctx = {0};
    ctx.cseq = 1;
    random_hex(ctx.from_tag, 16);

    uint32_t cid_rand = esp_random();
    snprintf(ctx.call_id, sizeof(ctx.call_id), "%08lx@phoneblock",
             (unsigned long)cid_rand);

    if (!discover_local_ip(&ctx) || !resolve_registrar(&ctx) || !open_sip_socket(&ctx)) {
        ESP_LOGE(TAG, "setup failed; aborting SIP task");
        vTaskDelete(NULL);
        return;
    }

    // Clear the recv timeout set by open_sip_socket — we use select() now.
    struct timeval no_timeout = { .tv_sec = 0, .tv_usec = 0 };
    setsockopt(ctx.sock, SOL_SOCKET, SO_RCVTIMEO, &no_timeout, sizeof(no_timeout));

    const int retry_delay_s = 30;
    char *rx = NULL;
    int64_t refresh_at_us = 0;  // absolute deadline for next REGISTER (microseconds)

    // Initial registration.
    bool ok = do_register(&ctx);
    s_registered = ok;
    if (ok) {
        ESP_LOGI(TAG, "REGISTERED as %s@%s (expires %d s)",
                 CONFIG_SIP_USERNAME, CONFIG_SIP_REGISTRAR_HOST,
                 CONFIG_SIP_EXPIRES);
        refresh_at_us = esp_timer_get_time() + (int64_t)(CONFIG_SIP_EXPIRES / 2) * 1000000LL;
    } else {
        ESP_LOGE(TAG, "initial registration failed, retry in %d s", retry_delay_s);
        refresh_at_us = esp_timer_get_time() + (int64_t)retry_delay_s * 1000000LL;
    }

    rx = malloc(SIP_RX_BUF_SIZE);
    if (!rx) {
        ESP_LOGE(TAG, "malloc rx buffer failed");
        vTaskDelete(NULL);
        return;
    }

    while (1) {
        int64_t now = esp_timer_get_time();
        int64_t remaining_us = refresh_at_us - now;
        if (remaining_us <= 0) remaining_us = 1;

        struct timeval tv = {
            .tv_sec  = remaining_us / 1000000,
            .tv_usec = remaining_us % 1000000,
        };
        fd_set rfds;
        FD_ZERO(&rfds);
        FD_SET(ctx.sock, &rfds);

        int s = select(ctx.sock + 1, &rfds, NULL, NULL, &tv);
        if (s < 0) {
            ESP_LOGE(TAG, "select(): %s", strerror(errno));
            vTaskDelay(pdMS_TO_TICKS(1000));
            continue;
        }

        if (s == 0) {
            // Deadline hit → refresh REGISTER.
            ok = do_register(&ctx);
            s_registered = ok;
            if (ok) {
                ESP_LOGI(TAG, "re-REGISTERED (expires %d s)", CONFIG_SIP_EXPIRES);
                refresh_at_us = esp_timer_get_time() + (int64_t)(CONFIG_SIP_EXPIRES / 2) * 1000000LL;
            } else {
                ESP_LOGE(TAG, "re-REGISTER failed, retry in %d s", retry_delay_s);
                refresh_at_us = esp_timer_get_time() + (int64_t)retry_delay_s * 1000000LL;
            }
            continue;
        }

        // Incoming packet.
        struct sockaddr_in from;
        socklen_t from_len = sizeof(from);
        int n = recvfrom(ctx.sock, rx, SIP_RX_BUF_SIZE - 1, 0,
                         (struct sockaddr *)&from, &from_len);
        if (n < 0) {
            ESP_LOGW(TAG, "recvfrom(): %s", strerror(errno));
            continue;
        }
        rx[n] = '\0';
        handle_incoming(&ctx, rx, n, &from);
    }
}

void sip_register_start(void)
{
    if (strlen(CONFIG_SIP_REGISTRAR_HOST) == 0 ||
        strlen(CONFIG_SIP_USERNAME) == 0 ||
        strlen(CONFIG_SIP_PASSWORD) == 0) {
        ESP_LOGW(TAG, "SIP config incomplete, skipping registration");
        return;
    }
    ESP_LOGI(TAG, "starting SIP registrar task (host=%s user=%s)",
             CONFIG_SIP_REGISTRAR_HOST, CONFIG_SIP_USERNAME);
    xTaskCreate(sip_task, "sip_register", 8192, NULL, 5, NULL);
}
