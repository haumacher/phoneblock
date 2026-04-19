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
#include "config.h"
#include "sip_parse.h"
#include "rtp.h"
#include "stats.h"

// Voice announcement baked into the binary via EMBED_FILES (see
// main/CMakeLists.txt). The linker emits _binary_<file>_<ext>_{start,end}
// symbols; we alias them to cleaner C names.
extern const uint8_t announcement_start[] asm("_binary_announcement_alaw_start");
extern const uint8_t announcement_end[]   asm("_binary_announcement_alaw_end");

static const char *TAG = "sip";

#define SIP_LOCAL_PORT       5061  // local UDP for SIP (not the TCP dummy server)
#define SIP_RX_BUF_SIZE      4096
#define SIP_TX_BUF_SIZE      2048
#define SIP_RECV_TIMEOUT_S   3
#define SIP_MAX_CHALLENGE    256

typedef enum {
    DIALOG_IDLE,        // no active call
    DIALOG_TRYING,      // received INVITE, 100 Trying sent, deciding
    DIALOG_ANSWERED,    // sent 200 OK with SDP, waiting for ACK → then stream
    DIALOG_STREAMING,   // playing tone to spam caller, BYE scheduled
    DIALOG_REJECTED,    // sent 486/480, waiting for ACK
    DIALOG_BYE_SENT,    // sent BYE, waiting for 200 OK
} dialog_state_t;

typedef struct {
    dialog_state_t state;
    char call_id[128];            // Call-ID of the active INVITE (dedupe key)
    char from_tag[64];            // remote's From tag
    char our_tag[20];             // our To tag (used as From tag in later BYE)
    char remote_uri[128];         // From URI, used as R-URI of our BYE
    uint32_t in_cseq;             // CSeq number of the original INVITE
    uint32_t out_cseq;            // next CSeq for our outgoing in-dialog requests
    struct sockaddr_in peer;      // where to send in-dialog responses/requests
    struct sockaddr_in rtp_dest;  // remote RTP endpoint (from INVITE SDP)
    bool     rtp_dest_valid;      // true if we successfully parsed c= / m=
    int64_t  bye_at_us;           // abs. deadline to send BYE (0 = no deadline)
    verdict_t verdict;            // cached API result for this call
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
static volatile bool s_reload_requested = false;
static TaskHandle_t s_sip_task = NULL;

bool sip_register_is_registered(void)
{
    return s_registered;
}

void sip_register_request_reload(void)
{
    // First-time configuration at runtime: the SIP task may not be
    // running yet because the device booted with empty credentials.
    // Kick it off now so the newly stored creds actually get used.
    if (!s_sip_task) {
        sip_register_start();
        return;
    }
    s_reload_requested = true;
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
    return strlen(config_contact_host_override()) > 0
               ? config_contact_host_override() : c->local_ip;
}

static int advertised_port(void)
{
    return config_contact_port_override() != 0
               ? config_contact_port_override() : SIP_LOCAL_PORT;
}

static int build_register(sip_ctx_t *c, char *buf, int cap, bool with_auth)
{
    char branch[20], cnonce[20];
    random_hex(branch, 16);

    const char *our_host = advertised_host(c);
    int our_port = advertised_port();

    char request_uri[96];
    snprintf(request_uri, sizeof(request_uri), "sip:%s", config_sip_host());

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
        config_sip_user(), config_sip_host(), c->from_tag,
        config_sip_user(), config_sip_host(),
        c->call_id,
        (unsigned long)c->cseq,
        config_sip_user(), our_host, our_port,
        config_sip_expires());

    if (with_auth && c->challenge.valid) {
        char response[33];
        const char *qop = c->challenge.qop;
        const char *nc  = "00000001";
        random_hex(cnonce, 16);

        digest_response(
            config_sip_user(), config_sip_pass(),
            c->challenge.realm, c->challenge.nonce,
            "REGISTER", request_uri,
            qop, nc, cnonce,
            response);

        n += snprintf(buf + n, cap - n,
            "Authorization: Digest username=\"%s\", realm=\"%s\", "
            "nonce=\"%s\", uri=\"%s\", response=\"%s\", algorithm=%s",
            config_sip_user(), c->challenge.realm,
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
    snprintf(port_str, sizeof(port_str), "%d", config_sip_port());

    int err = getaddrinfo(config_sip_host(), port_str, &hints, &res);
    if (err != 0 || !res) {
        ESP_LOGE(TAG, "DNS lookup of %s failed: %d", config_sip_host(), err);
        return false;
    }
    memcpy(&c->registrar, res->ai_addr, sizeof(c->registrar));
    freeaddrinfo(res);

    char ip[INET_ADDRSTRLEN];
    inet_ntoa_r(c->registrar.sin_addr, ip, sizeof(ip));
    ESP_LOGI(TAG, "registrar %s:%d → %s", config_sip_host(),
             config_sip_port(), ip);
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
        config_sip_user(), advertised_host(c), advertised_port());

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


// Write a minimal SDP body announcing PCMA audio on our local RTP port.
// The RTP task binds that port just-in-time and streams a tone there.
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
        our_ip, our_ip, SIP_RTP_PORT);
}

// ---------------------------------------------------------------------------
// Dialog / INVITE handling
// ---------------------------------------------------------------------------

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

    // Parse the remote RTP endpoint from the INVITE's SDP body, so we
    // can stream a tone there after answering. Failure here is tolerable
    // — we just skip the streaming and send BYE immediately after ACK.
    char rtp_ip[INET_ADDRSTRLEN] = {0};
    parse_sdp_connection_ip(req, req_len, rtp_ip, sizeof(rtp_ip));
    int rtp_port = parse_sdp_audio_port(req, req_len);
    d->rtp_dest_valid = false;
    if (rtp_ip[0] && rtp_port > 0) {
        struct in_addr addr;
        if (inet_aton(rtp_ip, &addr)) {
            d->rtp_dest.sin_family      = AF_INET;
            d->rtp_dest.sin_addr        = addr;
            d->rtp_dest.sin_port        = htons(rtp_port);
            d->rtp_dest_valid           = true;
            ESP_LOGI(TAG, "remote RTP endpoint parsed: %s:%d", rtp_ip, rtp_port);
        }
    }
    if (!d->rtp_dest_valid) {
        ESP_LOGW(TAG, "could not parse remote RTP endpoint — no tone will play");
    }
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
        config_sip_user(), advertised_host(c), our_port, d->our_tag,
        d->remote_uri, d->from_tag,
        d->call_id,
        (unsigned long)d->out_cseq,
        config_sip_user(), our_host, our_port);
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
//
// Two short-circuits skip the API call:
//  1. Fritz!Box delivers a non-numeric display-name ("Haui Mobil") → the
//     caller matched an address-book entry, so the user already trusts
//     them. Don't waste an API round-trip.
//  2. The dialed number isn't a real external number (internal **NN
//     codes, *21# feature dials, etc.). The API would reject them with
//     HTTP 400 anyway, but the TLS handshake still costs ~1–2 s.
// Both cases return VERDICT_LEGITIMATE so the dongle sends 486 Busy and
// the Fritz!Box continues its normal ring routing.
static verdict_t check_invite_caller(const char *req, int req_len)
{
    const char *hdr = find_header(req, req_len, "From");
    if (!hdr) {
        ESP_LOGW(TAG, "INVITE without From header");
        stats_record_call("", "", VERDICT_ERROR);
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
        stats_record_call(uri, "", VERDICT_ERROR);
        return VERDICT_ERROR;
    }

    char display[64];
    parse_display_name(hdr, val_len, display, sizeof(display));

#if CONFIG_SIP_TEST_FORCE_SPAM_STAR_NUMBERS
    // Dev hook: any '*'-prefixed internal dial code (**622, *21#, …)
    // is treated as spam so the 200 OK + tone + BYE path can be
    // exercised without blacklisting a real external number.
    if (raw_user[0] == '*') {
        ESP_LOGW(TAG, "TEST MODE: caller '%s' forced to SPAM", raw_user);
        stats_record_call(raw_user, display, VERDICT_SPAM);
        return VERDICT_SPAM;
    }
#endif

    if (display[0] && !is_phone_number_like(display)) {
        ESP_LOGI(TAG, "caller '%s' resolved via phone book → skip API",
                 display);
        stats_record_call(raw_user, display, VERDICT_LEGITIMATE);
        return VERDICT_LEGITIMATE;
    }

    char number[64];
    normalize_de(raw_user, number, sizeof(number));
    ESP_LOGI(TAG, "caller URI=%s raw=%s normalized=%s", uri, raw_user, number);

    if (!looks_dialable(number)) {
        ESP_LOGI(TAG, "non-external caller '%s' → skip API", number);
        stats_record_call(number, display, VERDICT_LEGITIMATE);
        return VERDICT_LEGITIMATE;
    }

    verdict_t v = phoneblock_check(number);
    stats_record_call(number, display, v);
    return v;
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
        case DIALOG_ANSWERED:
        case DIALOG_STREAMING: {
            char sdp[256];
            build_sdp_body(advertised_host(c), sdp, sizeof(sdp));
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
        build_sdp_body(advertised_host(c), sdp, sizeof(sdp));
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
        // Spam call: we answered with 200 OK, ACK confirms.
#if CONFIG_RTP_PLAY_ANNOUNCEMENT
        size_t bytes = announcement_end - announcement_start;
        if (bytes > 0 && d->rtp_dest_valid) {
            // PCMA at 8 kHz → 8000 bytes == 1 s, so duration_us = bytes * 125.
            // Add a short tail margin so the last frame is delivered before BYE.
            int64_t duration_us = (int64_t)bytes * 125LL;
            ESP_LOGI(TAG, "ACK received → streaming announcement (%u bytes ≈ %lld ms), then BYE",
                     (unsigned)bytes, (long long)(duration_us / 1000));
            rtp_play_audio(&d->rtp_dest, announcement_start, bytes);
            d->bye_at_us = esp_timer_get_time() + duration_us + 200000LL;
            d->state = DIALOG_STREAMING;
        } else {
            ESP_LOGI(TAG, "ACK received → no audio (no rtp_dest or empty) → BYE");
            send_bye(c);
        }
#else
        ESP_LOGI(TAG, "ACK received → announcement disabled → BYE");
        send_bye(c);
#endif
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
    stats_record_sip_state(ok);
    if (ok) {
        ESP_LOGI(TAG, "REGISTERED as %s@%s (expires %d s)",
                 config_sip_user(), config_sip_host(),
                 config_sip_expires());
        refresh_at_us = esp_timer_get_time() + (int64_t)(config_sip_expires() / 2) * 1000000LL;
    } else {
        ESP_LOGE(TAG, "initial registration failed, retry in %d s", retry_delay_s);
        stats_record_error("sip", "initial REGISTER failed");
        refresh_at_us = esp_timer_get_time() + (int64_t)retry_delay_s * 1000000LL;
    }

    rx = malloc(SIP_RX_BUF_SIZE);
    if (!rx) {
        ESP_LOGE(TAG, "malloc rx buffer failed");
        vTaskDelete(NULL);
        return;
    }

    while (1) {
        // Config changed? Re-register with the new credentials before
        // going back to sleep in select(). The web-UI POST handler sets
        // the flag via sip_register_request_reload().
        if (s_reload_requested) {
            s_reload_requested = false;
            ESP_LOGI(TAG, "config reload requested → re-REGISTER with new creds");
            // Refresh registrar address too, in case host changed.
            resolve_registrar(&ctx);
            ok = do_register(&ctx);
            s_registered = ok;
            stats_record_sip_state(ok);
            if (ok) {
                ESP_LOGI(TAG, "re-REGISTERED after config change");
                refresh_at_us = esp_timer_get_time() + (int64_t)(config_sip_expires() / 2) * 1000000LL;
            } else {
                ESP_LOGE(TAG, "REGISTER with new config failed, retry in %d s", retry_delay_s);
                stats_record_error("sip", "REGISTER with new config failed");
                refresh_at_us = esp_timer_get_time() + (int64_t)retry_delay_s * 1000000LL;
            }
        }

        int64_t now = esp_timer_get_time();
        // Next wake-up: whichever of {REGISTER refresh, BYE-after-stream}
        // is sooner. bye_at_us == 0 disables that deadline.
        int64_t deadline = refresh_at_us;
        if (ctx.dialog.bye_at_us && ctx.dialog.bye_at_us < deadline) {
            deadline = ctx.dialog.bye_at_us;
        }
        int64_t remaining_us = deadline - now;
        if (remaining_us <= 0) remaining_us = 1;
        // Cap so s_reload_requested (set from the web UI on config save)
        // is noticed within half a second instead of waiting out the
        // full REGISTER-refresh interval.
        if (remaining_us > 500000) remaining_us = 500000;

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
            now = esp_timer_get_time();
            // BYE deadline first — the dialog is still active and needs
            // tearing down before anything else.
            if (ctx.dialog.bye_at_us && now >= ctx.dialog.bye_at_us) {
                ctx.dialog.bye_at_us = 0;
                if (ctx.dialog.state == DIALOG_STREAMING) {
                    ESP_LOGI(TAG, "tone finished → sending BYE");
                    send_bye(&ctx);
                }
                continue;
            }
            // Most select() returns now are just the 500ms reload-poll
            // cap firing; only refresh when the real deadline is due.
            if (now < refresh_at_us) continue;
            // REGISTER refresh.
            ok = do_register(&ctx);
            s_registered = ok;
            stats_record_sip_state(ok);
            if (ok) {
                ESP_LOGI(TAG, "re-REGISTERED (expires %d s)", config_sip_expires());
                refresh_at_us = esp_timer_get_time() + (int64_t)(config_sip_expires() / 2) * 1000000LL;
            } else {
                ESP_LOGE(TAG, "re-REGISTER failed, retry in %d s", retry_delay_s);
                stats_record_error("sip", "re-REGISTER failed");
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
    if (strlen(config_sip_host()) == 0 ||
        strlen(config_sip_user()) == 0 ||
        strlen(config_sip_pass()) == 0) {
        ESP_LOGW(TAG, "SIP config incomplete, skipping registration");
        return;
    }
    if (s_sip_task) {
        // Already running — request_reload() handles credential changes.
        return;
    }
    ESP_LOGI(TAG, "starting SIP registrar task (host=%s user=%s)",
             config_sip_host(), config_sip_user());
    xTaskCreate(sip_task, "sip_register", 8192, NULL, 5, &s_sip_task);
}
