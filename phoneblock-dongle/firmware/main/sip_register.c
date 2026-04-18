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
#include "api.h"

static const char *TAG = "sip";

#define SIP_LOCAL_PORT       5061  // local UDP for SIP (not the TCP dummy server)
#define SIP_RX_BUF_SIZE      4096
#define SIP_TX_BUF_SIZE      2048
#define SIP_RECV_TIMEOUT_S   3
#define SIP_MAX_CHALLENGE    256

// Dummy RTP port advertised in the SDP of our 200 OK. The dongle never
// actually listens on it; any RTP arriving there is dropped by the kernel.
#define SIP_DUMMY_RTP_PORT   5004

typedef enum {
    DIALOG_IDLE,        // no active call
    DIALOG_TRYING,      // received INVITE, 100 Trying sent, deciding
    DIALOG_ANSWERED,    // sent 200 OK with SDP, waiting for ACK → then BYE
    DIALOG_REJECTED,    // sent 486/480, waiting for ACK
    DIALOG_BYE_SENT,    // sent BYE, waiting for 200 OK
} dialog_state_t;

typedef struct {
    dialog_state_t state;
    char call_id[128];        // Call-ID of the active INVITE (dedupe key)
    char from_tag[64];        // remote's From tag
    char our_tag[20];         // our To tag (used as From tag in later BYE)
    char remote_uri[128];     // From URI, used as R-URI of our BYE
    uint32_t in_cseq;         // CSeq number of the original INVITE
    uint32_t out_cseq;        // next CSeq for our outgoing in-dialog requests
    struct sockaddr_in peer;  // where to send in-dialog responses/requests
    verdict_t verdict;        // cached API result for this call
} dialog_t;

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
    char     call_id[40];       // Call-ID we use for REGISTER
    char     from_tag[20];      // From tag we use for REGISTER
    uint32_t cseq;              // CSeq for REGISTER
    int      sock;
    struct sockaddr_in registrar;
    auth_challenge_t challenge;
    bool     registered;
    dialog_t dialog;            // at most one call at a time
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

// Write exactly hex_chars of random hex into out, NUL-terminated.
// Previous implementation always wrote 33 bytes regardless of hex_chars,
// causing a silent stack/struct overflow for any smaller buffer.
static void random_hex(char *out, size_t hex_chars)
{
    static const char hex[] = "0123456789abcdef";
    size_t i = 0;
    while (i < hex_chars) {
        uint32_t r = esp_random();
        for (int b = 0; b < 4 && i < hex_chars; b++) {
            out[i++] = hex[(r >> 4) & 0x0f];
            if (i < hex_chars) out[i++] = hex[r & 0x0f];
            r >>= 8;
        }
    }
    out[hex_chars] = '\0';
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

// Via/Contact advertise where the registrar should reach us. On real
// hardware that's the interface IP/UDP port. Inside QEMU user-mode
// networking, these have to be overridden to the host-visible address
// (host LAN IP + hostfwd port), otherwise the registrar cannot deliver
// an incoming INVITE — it would try to connect to 10.0.2.x which only
// exists inside the emulator.
static const char *advertised_host(const sip_ctx_t *c)
{
    return strlen(CONFIG_SIP_CONTACT_HOST_OVERRIDE) > 0
               ? CONFIG_SIP_CONTACT_HOST_OVERRIDE : c->local_ip;
}

static int advertised_port(void)
{
    return CONFIG_SIP_CONTACT_PORT_OVERRIDE != 0
               ? CONFIG_SIP_CONTACT_PORT_OVERRIDE : SIP_LOCAL_PORT;
}

static int build_register(sip_ctx_t *c, char *buf, int cap, bool with_auth)
{
    char branch[20], cnonce[20];
    random_hex(branch, 16);

    const char *our_host = advertised_host(c);
    int our_port = advertised_port();

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
        our_host, our_port, branch,
        CONFIG_SIP_USERNAME, CONFIG_SIP_REGISTRAR_HOST, c->from_tag,
        CONFIG_SIP_USERNAME, CONFIG_SIP_REGISTRAR_HOST,
        c->call_id,
        (unsigned long)c->cseq,
        CONFIG_SIP_USERNAME, our_host, our_port,
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
// Incoming request handling
// ---------------------------------------------------------------------------

// Copy one full "Name: value\r\n" line from req into out. Returns bytes
// written (0 if the header is absent or doesn't fit).
static int echo_header_line(const char *req, int req_len, const char *name,
                            char *out, int out_cap)
{
    const char *val = find_header(req, req_len, name);
    if (!val) return 0;

    // Scan forward to end of line.
    const char *end = req + req_len;
    const char *eol = val;
    while (eol < end && *eol != '\r' && *eol != '\n') eol++;
    int val_len = (int)(eol - val);

    int written = snprintf(out, out_cap, "%s: %.*s\r\n", name, val_len, val);
    return written > 0 && written < out_cap ? written : 0;
}

// Like echo_header_line, but appends ";tag=<our_tag>" to the To-header's
// URI part (for generating a valid UAS response to a dialog-forming request).
static int echo_to_with_tag(const char *req, int req_len,
                            const char *our_tag, char *out, int out_cap)
{
    const char *val = find_header(req, req_len, "To");
    if (!val) return 0;
    const char *end = req + req_len;
    const char *eol = val;
    while (eol < end && *eol != '\r' && *eol != '\n') eol++;
    int val_len = (int)(eol - val);

    // Check if the request already has a tag (some re-INVITEs do).
    if (memmem(val, val_len, ";tag=", 5)) {
        return snprintf(out, out_cap, "To: %.*s\r\n", val_len, val);
    }
    int written = snprintf(out, out_cap, "To: %.*s;tag=%s\r\n",
                           val_len, val, our_tag);
    return written > 0 && written < out_cap ? written : 0;
}

// Build a SIP response that echoes routing headers (Via/From/To/Call-ID/
// CSeq) from the request, adds Contact + Allow + User-Agent, an optional
// SDP body, and a matching Content-Length. Caller provides the status line,
// an optional fixed To-tag (NULL → generate a fresh one, for stateless
// responses like OPTIONS), and an optional body (NULL → empty).
static int build_response(sip_ctx_t *c, const char *req, int req_len,
                          int status, const char *reason,
                          const char *our_tag_override,
                          const char *body,
                          char *out, int out_cap)
{
    char tag_buf[20];
    const char *our_tag;
    if (our_tag_override && our_tag_override[0]) {
        our_tag = our_tag_override;
    } else {
        random_hex(tag_buf, 16);
        our_tag = tag_buf;
    }

    int body_len = body ? (int)strlen(body) : 0;

    int n = snprintf(out, out_cap, "SIP/2.0 %d %s\r\n", status, reason);
    n += echo_header_line(req, req_len, "Via", out + n, out_cap - n);
    n += echo_header_line(req, req_len, "From", out + n, out_cap - n);
    n += echo_to_with_tag(req, req_len, our_tag, out + n, out_cap - n);
    n += echo_header_line(req, req_len, "Call-ID", out + n, out_cap - n);
    n += echo_header_line(req, req_len, "CSeq", out + n, out_cap - n);
    n += snprintf(out + n, out_cap - n,
        "Contact: <sip:%s@%s:%d>\r\n"
        "Allow: INVITE, ACK, CANCEL, BYE, OPTIONS\r\n"
        "User-Agent: phoneblock-dongle/0.1\r\n",
        CONFIG_SIP_USERNAME, advertised_host(c), advertised_port());

    if (body_len > 0) {
        n += snprintf(out + n, out_cap - n,
            "Content-Type: application/sdp\r\n"
            "Content-Length: %d\r\n\r\n%s",
            body_len, body);
    } else {
        n += snprintf(out + n, out_cap - n,
            "Content-Length: 0\r\n\r\n");
    }
    return n;
}

static void send_response(sip_ctx_t *c, const struct sockaddr_in *peer,
                          const char *req, int req_len,
                          int status, const char *reason,
                          const char *our_tag_override,
                          const char *body)
{
    char *tx = malloc(SIP_TX_BUF_SIZE);
    if (!tx) {
        ESP_LOGE(TAG, "malloc failed for response buffer");
        return;
    }
    int tx_len = build_response(c, req, req_len, status, reason,
                                our_tag_override, body, tx, SIP_TX_BUF_SIZE);
    ESP_LOGI(TAG, "→ %d %s (%d bytes):\n%.*s", status, reason, tx_len, tx_len, tx);
    int n = sendto(c->sock, tx, tx_len, 0,
                   (struct sockaddr *)peer, sizeof(*peer));
    if (n < 0) {
        ESP_LOGE(TAG, "sendto() failed: %s", strerror(errno));
    }
    free(tx);
}

// Extract the method (first whitespace-delimited token on the request line).
static int parse_method(const char *pkt, int len, char *method, int cap)
{
    int i = 0;
    while (i < len && i < cap - 1 && pkt[i] != ' ' && pkt[i] != '\r' && pkt[i] != '\n') {
        method[i] = pkt[i];
        i++;
    }
    method[i] = '\0';
    return i;
}

// Extract the CSeq number from the request ("CSeq: 12345 METHOD").
static uint32_t parse_cseq(const char *req, int req_len)
{
    const char *p = find_header(req, req_len, "CSeq");
    if (!p) return 0;
    uint32_t n = 0;
    while (*p >= '0' && *p <= '9') {
        n = n * 10 + (*p - '0');
        p++;
    }
    return n;
}

// Extract the Call-ID value, trimmed.
static void parse_call_id(const char *req, int req_len, char *out, int cap)
{
    const char *p = find_header(req, req_len, "Call-ID");
    if (!p) { out[0] = '\0'; return; }
    const char *end = req + req_len;
    int n = 0;
    while (p < end && *p != '\r' && *p != '\n' && n < cap - 1) {
        out[n++] = *p++;
    }
    out[n] = '\0';
    // trim trailing whitespace
    while (n > 0 && (out[n - 1] == ' ' || out[n - 1] == '\t')) out[--n] = '\0';
}

// Extract the value of a ";tag=..." parameter from a header value.
static void parse_tag(const char *hdr_val, int val_len, char *out, int cap)
{
    const char *end = hdr_val + val_len;
    const char *p = hdr_val;
    while (p + 5 < end) {
        if (strncasecmp(p, ";tag=", 5) == 0) {
            p += 5;
            int n = 0;
            while (p < end && *p != ';' && *p != ' ' && *p != '\t'
                   && *p != '\r' && *p != '\n' && n < cap - 1) {
                out[n++] = *p++;
            }
            out[n] = '\0';
            return;
        }
        p++;
    }
    out[0] = '\0';
}

// Extract the URI from a SIP URI-containing header (From/To/Contact):
//   "Name" <sip:user@host:port;params>;tag=xyz
//                ^^^^^^^^^^^^^^^^^^
//   → sip:user@host:port (parameters stripped)
// If no <...> brackets present, take from "sip:" up to ';' or whitespace.
static void parse_uri(const char *hdr_val, int val_len, char *out, int cap)
{
    const char *end = hdr_val + val_len;
    const char *lt = NULL, *gt = NULL;
    for (const char *p = hdr_val; p < end; p++) {
        if (*p == '<') lt = p + 1;
        else if (*p == '>') { gt = p; break; }
    }
    const char *start, *stop;
    if (lt && gt && gt > lt) {
        start = lt; stop = gt;
    } else {
        // Find "sip:" in the value.
        start = hdr_val;
        while (start + 4 < end && strncasecmp(start, "sip:", 4) != 0) start++;
        if (start + 4 >= end) { out[0] = '\0'; return; }
        stop = start;
        while (stop < end && *stop != ';' && *stop != ' ' && *stop != '\t'
               && *stop != '\r' && *stop != '\n') stop++;
    }
    // Strip URI parameters (everything after the first ';').
    const char *semi = start;
    while (semi < stop && *semi != ';') semi++;
    if (semi < stop) stop = semi;

    int n = stop - start;
    if (n >= cap) n = cap - 1;
    memcpy(out, start, n);
    out[n] = '\0';
}

// Extract the user part of a SIP URI: "sip:01234@fritz.box" → "01234".
// Returns length written (excluding NUL). Empty if URI malformed.
static int user_from_uri(const char *uri, char *out, int cap)
{
    const char *p = uri;
    if (strncasecmp(p, "sip:", 4) == 0) p += 4;
    else if (strncasecmp(p, "sips:", 5) == 0) p += 5;
    else if (strncasecmp(p, "tel:", 4) == 0) p += 4;
    int n = 0;
    while (*p && *p != '@' && *p != ';' && *p != ':' && n < cap - 1) {
        out[n++] = *p++;
    }
    out[n] = '\0';
    return n;
}

// Minimal German-centric number normalization for the PhoneBlock API.
// The server normalizes further (national/international format), but we
// standardize the leading prefix: international "+49..." becomes national
// "0...", and whitespace/dashes are dropped.
static void normalize_de(const char *raw, char *out, int cap)
{
    char buf[48];
    int n = 0;
    for (const char *p = raw; *p && n < (int)sizeof(buf) - 1; p++) {
        if (*p == ' ' || *p == '-' || *p == '(' || *p == ')' || *p == '/') continue;
        buf[n++] = *p;
    }
    buf[n] = '\0';

    const char *src = buf;
    int w = 0;
    if (strncmp(src, "+49", 3) == 0) {
        out[w++] = '0';
        src += 3;
    } else if (strncmp(src, "0049", 4) == 0) {
        out[w++] = '0';
        src += 4;
    }
    while (*src && w < cap - 1) {
        out[w++] = *src++;
    }
    out[w] = '\0';
}

// Write a minimal SDP body announcing PCMA audio. The port is a fixed
// placeholder — the dongle never actually listens on it. Fritz!Box
// accepts the call, the caller may start sending RTP, the kernel drops
// the packets, and we hang up via BYE anyway.
static int build_sdp_body(const char *our_ip, char *out, int cap)
{
    return snprintf(out, cap,
        "v=0\r\n"
        "o=phoneblock-dongle 0 0 IN IP4 %s\r\n"
        "s=-\r\n"
        "c=IN IP4 %s\r\n"
        "t=0 0\r\n"
        "m=audio %d RTP/AVP 8\r\n"
        "a=rtpmap:8 PCMA/8000\r\n"
        "a=sendrecv\r\n",
        our_ip, our_ip, SIP_DUMMY_RTP_PORT);
}

// ---------------------------------------------------------------------------
// Dialog / INVITE handling
// ---------------------------------------------------------------------------

// Compare two non-empty Call-IDs (case-insensitive, trimmed).
static bool same_call_id(const char *a, const char *b)
{
    if (!a || !b || !*a || !*b) return false;
    return strcasecmp(a, b) == 0;
}

// Extract and store the dialog-identifying fields of a fresh INVITE so we
// can reference them when sending ACK/BYE/responses later.
static void capture_dialog(sip_ctx_t *c, const char *req, int req_len,
                           const struct sockaddr_in *from)
{
    dialog_t *d = &c->dialog;

    parse_call_id(req, req_len, d->call_id, sizeof(d->call_id));
    d->in_cseq = parse_cseq(req, req_len);
    d->out_cseq = 1;
    d->peer = *from;

    const char *hdr = find_header(req, req_len, "From");
    if (hdr) {
        const char *end = req + req_len;
        const char *eol = hdr;
        while (eol < end && *eol != '\r' && *eol != '\n') eol++;
        int val_len = (int)(eol - hdr);
        parse_uri(hdr, val_len, d->remote_uri, sizeof(d->remote_uri));
        parse_tag(hdr, val_len, d->from_tag, sizeof(d->from_tag));
    }

    random_hex(d->our_tag, 16);
}

// Build a BYE for the active answered dialog.
static int build_bye(sip_ctx_t *c, char *buf, int cap)
{
    dialog_t *d = &c->dialog;
    char branch[20];
    random_hex(branch, 16);

    const char *our_host = advertised_host(c);
    int our_port = advertised_port();

    return snprintf(buf, cap,
        "BYE %s SIP/2.0\r\n"
        "Via: SIP/2.0/UDP %s:%d;branch=z9hG4bK%s;rport\r\n"
        "Max-Forwards: 70\r\n"
        "From: <sip:%s@%s:%d>;tag=%s\r\n"
        "To: <%s>;tag=%s\r\n"
        "Call-ID: %s\r\n"
        "CSeq: %lu BYE\r\n"
        "Contact: <sip:%s@%s:%d>\r\n"
        "User-Agent: phoneblock-dongle/0.1\r\n"
        "Content-Length: 0\r\n\r\n",
        d->remote_uri,
        our_host, our_port, branch,
        CONFIG_SIP_USERNAME, advertised_host(c), our_port, d->our_tag,
        d->remote_uri, d->from_tag,
        d->call_id,
        (unsigned long)d->out_cseq,
        CONFIG_SIP_USERNAME, our_host, our_port);
}

static void send_bye(sip_ctx_t *c)
{
    dialog_t *d = &c->dialog;
    d->out_cseq++;

    char *tx = malloc(SIP_TX_BUF_SIZE);
    if (!tx) {
        ESP_LOGE(TAG, "malloc for BYE failed");
        return;
    }
    int tx_len = build_bye(c, tx, SIP_TX_BUF_SIZE);
    ESP_LOGI(TAG, "→ BYE (%d bytes):\n%.*s", tx_len, tx_len, tx);

    int n = sendto(c->sock, tx, tx_len, 0,
                   (struct sockaddr *)&d->peer, sizeof(d->peer));
    if (n < 0) {
        ESP_LOGE(TAG, "sendto(BYE): %s", strerror(errno));
    }
    free(tx);
    d->state = DIALOG_BYE_SENT;
}

// Extract the caller's number (user part of the From URI), normalize it
// for Germany, and query the PhoneBlock API.
static verdict_t check_invite_caller(const char *req, int req_len)
{
    const char *hdr = find_header(req, req_len, "From");
    if (!hdr) {
        ESP_LOGW(TAG, "INVITE without From header");
        return VERDICT_ERROR;
    }
    const char *end = req + req_len;
    const char *eol = hdr;
    while (eol < end && *eol != '\r' && *eol != '\n') eol++;
    int val_len = (int)(eol - hdr);

    char uri[128];
    parse_uri(hdr, val_len, uri, sizeof(uri));

    char raw_user[64];
    if (user_from_uri(uri, raw_user, sizeof(raw_user)) == 0) {
        ESP_LOGW(TAG, "could not extract user from From URI '%s'", uri);
        return VERDICT_ERROR;
    }

    char number[64];
    normalize_de(raw_user, number, sizeof(number));
    ESP_LOGI(TAG, "caller URI=%s raw=%s normalized=%s", uri, raw_user, number);

    return phoneblock_check(number);
}

// Resend our most recent response for the active dialog — needed when the
// Fritz!Box retransmits an INVITE because its earlier wait for our final
// response timed out. Must be idempotent: reuses the stored dialog tag, no
// state change.
static void resend_last_response(sip_ctx_t *c, const char *req, int req_len,
                                 const struct sockaddr_in *from)
{
    dialog_t *d = &c->dialog;
    switch (d->state) {
        case DIALOG_TRYING:
            send_response(c, from, req, req_len, 100, "Trying",
                          d->our_tag, NULL);
            break;
        case DIALOG_ANSWERED: {
            char sdp[256];
            build_sdp_body(c->local_ip, sdp, sizeof(sdp));
            send_response(c, from, req, req_len, 200, "OK",
                          d->our_tag, sdp);
            break;
        }
        case DIALOG_REJECTED:
            send_response(c, from, req, req_len, 486, "Busy Here",
                          d->our_tag, NULL);
            break;
        default:
            ESP_LOGW(TAG, "INVITE retransmit in state %d, ignoring", d->state);
            break;
    }
}

static void handle_invite(sip_ctx_t *c, const char *req, int req_len,
                          const struct sockaddr_in *from)
{
    dialog_t *d = &c->dialog;
    char incoming_cid[128];
    parse_call_id(req, req_len, incoming_cid, sizeof(incoming_cid));

    // Re-transmission of the INVITE we're already processing?
    if (d->state != DIALOG_IDLE && same_call_id(d->call_id, incoming_cid)) {
        ESP_LOGI(TAG, "INVITE retransmit for active Call-ID, resending");
        resend_last_response(c, req, req_len, from);
        return;
    }

    // Second call arriving while we're busy with another: politely decline.
    if (d->state != DIALOG_IDLE) {
        ESP_LOGW(TAG, "second INVITE while dialog active → 486 Busy Here");
        send_response(c, from, req, req_len, 486, "Busy Here", NULL, NULL);
        return;
    }

    capture_dialog(c, req, req_len, from);
    d->state = DIALOG_TRYING;
    ESP_LOGI(TAG, "INVITE accepted, Call-ID=%s, checking caller…", d->call_id);

    // Stop Fritz!Box retransmits immediately.
    send_response(c, from, req, req_len, 100, "Trying", d->our_tag, NULL);

    // Synchronous API check. Budget is ~500 ms–1 s; Fritz!Box waits longer
    // than that. Later this can move to a worker task if needed.
    d->verdict = check_invite_caller(req, req_len);

    if (d->verdict == VERDICT_SPAM) {
        char sdp[256];
        build_sdp_body(c->local_ip, sdp, sizeof(sdp));
        send_response(c, from, req, req_len, 200, "OK", d->our_tag, sdp);
        d->state = DIALOG_ANSWERED;
        ESP_LOGI(TAG, "SPAM → 200 OK sent, waiting for ACK to hang up");
    } else {
        // VERDICT_LEGITIMATE or VERDICT_ERROR → don't take the call. 486
        // Busy Here lets the Fritz!Box continue ringing the real phones.
        send_response(c, from, req, req_len, 486, "Busy Here", d->our_tag, NULL);
        d->state = DIALOG_REJECTED;
        ESP_LOGI(TAG, "%s → 486 Busy sent",
                 d->verdict == VERDICT_LEGITIMATE ? "LEGITIMATE" : "ERROR");
    }
}

static void handle_ack(sip_ctx_t *c, const char *req, int req_len,
                       const struct sockaddr_in *from)
{
    dialog_t *d = &c->dialog;
    char cid[128];
    parse_call_id(req, req_len, cid, sizeof(cid));
    if (!same_call_id(d->call_id, cid)) {
        ESP_LOGW(TAG, "ACK for unknown Call-ID %s, ignoring", cid);
        return;
    }

    if (d->state == DIALOG_ANSWERED) {
        // Spam call: we answered with 200, ACK confirms, now hang up.
        ESP_LOGI(TAG, "ACK received after 200 OK → sending BYE");
        send_bye(c);
    } else if (d->state == DIALOG_REJECTED) {
        // Non-spam: we rejected with 486, ACK confirms, dialog done.
        ESP_LOGI(TAG, "ACK received after 486 → dialog closed");
        memset(d, 0, sizeof(*d));
    } else {
        ESP_LOGW(TAG, "ACK in unexpected state %d", d->state);
    }
}

static void handle_bye(sip_ctx_t *c, const char *req, int req_len,
                       const struct sockaddr_in *from)
{
    dialog_t *d = &c->dialog;
    char cid[128];
    parse_call_id(req, req_len, cid, sizeof(cid));

    // Always answer BYE with 200 OK — even if we don't recognize the dialog
    // (some edge cases, UDP retransmits after we wiped state).
    send_response(c, from, req, req_len, 200, "OK", d->our_tag, NULL);

    if (same_call_id(d->call_id, cid)) {
        ESP_LOGI(TAG, "BYE from remote → dialog closed");
        memset(d, 0, sizeof(*d));
    }
}

static void handle_cancel(sip_ctx_t *c, const char *req, int req_len,
                          const struct sockaddr_in *from)
{
    dialog_t *d = &c->dialog;
    char cid[128];
    parse_call_id(req, req_len, cid, sizeof(cid));

    // 200 OK on the CANCEL itself.
    send_response(c, from, req, req_len, 200, "OK", d->our_tag, NULL);

    // If we still had a pending INVITE for this Call-ID, answer it with
    // 487 Request Terminated so the remote knows the original INVITE is
    // fully resolved.
    if (d->state == DIALOG_TRYING && same_call_id(d->call_id, cid)) {
        ESP_LOGI(TAG, "CANCEL → 487 Request Terminated on original INVITE");
        // Synthesize a 487 response. We cannot easily reconstruct the
        // original INVITE's headers here, but the CANCEL's Via/From/To/
        // Call-ID/CSeq number match by spec — only the CSeq method differs
        // from INVITE. Fritz!Box is lenient about the CSeq method of 4xx
        // for a cancelled INVITE; if not, this needs refinement.
        send_response(c, from, req, req_len, 487, "Request Terminated",
                      d->our_tag, NULL);
        memset(d, 0, sizeof(*d));
    }
}

static void handle_incoming(sip_ctx_t *c, const char *pkt, int len,
                            const struct sockaddr_in *from)
{
    char from_ip[INET_ADDRSTRLEN];
    inet_ntoa_r(from->sin_addr, from_ip, sizeof(from_ip));

    ESP_LOGI(TAG, "← from %s:%d  %d bytes:\n%.*s",
             from_ip, ntohs(from->sin_port),
             len, len, pkt);

    // Handle responses to our own out-of-dialog requests (BYE from us).
    if (len >= 7 && strncmp(pkt, "SIP/2.0", 7) == 0) {
        int status = parse_status_code(pkt, len);
        if (c->dialog.state == DIALOG_BYE_SENT) {
            ESP_LOGI(TAG, "← %d on BYE → dialog closed", status);
            memset(&c->dialog, 0, sizeof(c->dialog));
        } else {
            ESP_LOGW(TAG, "ignoring stray response %d", status);
        }
        return;
    }

    char method[16];
    parse_method(pkt, len, method, sizeof(method));

    if (strcmp(method, "OPTIONS") == 0) {
        send_response(c, from, pkt, len, 200, "OK", NULL, NULL);
    } else if (strcmp(method, "INVITE") == 0) {
        handle_invite(c, pkt, len, from);
    } else if (strcmp(method, "ACK") == 0) {
        handle_ack(c, pkt, len, from);
    } else if (strcmp(method, "BYE") == 0) {
        handle_bye(c, pkt, len, from);
    } else if (strcmp(method, "CANCEL") == 0) {
        handle_cancel(c, pkt, len, from);
    } else {
        ESP_LOGW(TAG, "method %s not implemented yet", method);
    }
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
