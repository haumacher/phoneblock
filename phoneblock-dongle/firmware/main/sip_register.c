#include "sip_register.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <stdbool.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include "esp_log.h"
#include "esp_random.h"
#include "esp_task_wdt.h"
#include "esp_timer.h"
#include "mbedtls/md5.h"

#include "lwip/sockets.h"

#include "sdkconfig.h"
#include "api.h"
#include "config.h"
#include "announcement.h"
#include "report_queue.h"
#include "sip_parse.h"
#include "sip_auth.h"
#include "sip_transport.h"
#include "sip_srv.h"
#include "rtp.h"
#include "stats.h"

static const char *TAG = "sip";

// The local SIP port (bound + advertised) is configurable via
// config_sip_local_port(); its default (15060) and rationale live in
// config.c. RTP likewise via config_rtp_port() (default 16000).
#define SIP_RX_BUF_SIZE      4096
#define SIP_TX_BUF_SIZE      2048
// REGISTER round-trip wait: how long to block on the 401/200 response
// before giving up. Telekom takes several seconds to emit the final 200
// of the *first* successful registration (the binding is provisioned in
// their backend); 3 s missed it and wasted a full 30 s retry cycle. 10 s
// catches it while staying well under the 20 s task watchdog. The wait is
// sliced (SIP_REGISTER_RECV_SLICE_MS) so the watchdog is fed even when a
// dead registrar makes us wait the whole budget.
#define SIP_REGISTER_RECV_TIMEOUT_MS 10000
// Per-select slice of the round-trip wait. Bounds how long a single
// blocking recv runs before we loop back to feed the task watchdog.
#define SIP_REGISTER_RECV_SLICE_MS    2000

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
    char     call_id[40];       // Call-ID we use for REGISTER
    char     from_tag[20];      // From tag we use for REGISTER
    uint32_t cseq;              // CSeq for REGISTER
    sip_transport_t *transport; // owns socket, registrar address, local IP
    auth_challenge_t challenge;
    bool     registered;
    dialog_t dialog;            // at most one call at a time
} sip_ctx_t;

static bool s_registered = false;
static volatile bool s_reload_requested = false;
// Whether the next REGISTER (initial after task spawn, or post-reload)
// should wait 1.5 s for the registrar to settle. Set together with
// s_reload_requested by sip_register_request_reload(); cleared by the
// task once it has consumed the delay.
static volatile bool s_settle_pending  = false;
static TaskHandle_t s_sip_task = NULL;

// Absolute deadline at which the registrar's binding for the last
// successful REGISTER actually expires (us, esp_timer clock). Lets
// the periodic-refresh path debounce transient failures: the Fritz!Box
// gives us a granted-expiry of typically 600 s and we refresh at
// granted/2, so a single missed refresh leaves ~granted/2 of valid
// binding before the FB drops us. Within that window, transient
// failures retry silently — only when the binding actually lapses
// without recovery is an ERROR logged (and thereby surfaced on the web
// UI via the log hook). 0 = no valid binding (boot, after factory
// reset, after a definitive failure).
static int64_t s_binding_expires_at_us = 0;
// Most recent transient-failure reason, stashed so that if the binding
// does eventually expire without a successful refresh we can surface
// the actual cause instead of a generic "binding expired".
static char    s_pending_error[STATS_ERROR_MSG_LEN] = "";

// Public IP as the registrar sees us, learned from the "received="
// parameter the registrar adds to our Via in the REGISTER response.
// Empty until the first response arrives. Used as the advertised host in
// Via/Contact/SDP so a port-forwarded UDP setup behind NAT is reachable,
// and surfaced in the web UI for the user to verify. The public IP is the
// router's WAN address and identical for all transports, so this is also
// correct (if unused) for TCP/TLS.
static char    s_public_ip[INET_ADDRSTRLEN] = "";

bool sip_register_is_registered(void)
{
    return s_registered;
}

const char *sip_register_public_ip(void)
{
    return s_public_ip;
}

void sip_register_request_reload(bool needs_settle)
{
    // Latch the settle preference *before* the task wakes up — the
    // task reads it once and clears it. Set it before s_reload_requested
    // so a busy task that picks up the reload flag right away still
    // sees the right settle value.
    s_settle_pending = needs_settle;

    // First-time configuration at runtime: the SIP task may not be
    // running yet because the device booted with empty credentials.
    // Kick it off now so the newly stored creds actually get used.
    // The initial-register block in the task reads s_settle_pending
    // and applies the same 1.5 s pause as the reload-handler.
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
// Identity / auth / realm — Phase 3 of EXTENDED_SIP.md
//
// Three concepts that used to share config_sip_user():
//
//   identity = R-URI / From / To / Contact user. For Fritz!Box-driven
//              setups the dashboard shows config_sip_internal_number()
//              alongside it, but the registrar still authenticates the
//              configured SIP user — the internal extension is purely
//              informational.
//   auth     = Digest "username=" attribute. Some providers (1&1) hand
//              out a separate auth-user that does not match the SIP
//              identity. Falls back to the identity if not configured.
//   realm    = Digest "realm=" attribute. Normally taken from the
//              server's challenge, but some providers want a fixed
//              realm regardless of what their challenge advertises
//              (1&1 sends realm=1und1.de via a multi-host setup).
// ---------------------------------------------------------------------------

static const char *current_identity_user(void)
{
    return config_sip_user();
}

static const char *current_auth_user(void)
{
    return sip_auth_effective_user(config_sip_auth_user(), config_sip_user());
}

static const char *current_realm(const auth_challenge_t *challenge)
{
    return sip_auth_effective_realm(config_sip_realm(), challenge);
}

// ---------------------------------------------------------------------------
// Outbound proxy — Phase 4 of EXTENDED_SIP.md
//
// The SIP-level identity (R-URI, From, To) is always the registrar
// host (config_sip_host()). When config_sip_outbound() is set, the
// IP-layer destination of every signalling message is the outbound
// proxy instead — useful when the registrar lives on an internal
// hostname that the dongle cannot reach directly, or when a provider
// loadbalances via a separate edge proxy.
//
// Spec format: "host" or "host:port". Empty → fall through to the
// registrar address. The transport-level default port still applies
// when only "host" was given.
// ---------------------------------------------------------------------------

// Split "host[:port]" into a host buffer + port. Returns the parsed
// port, or 0 if no ":port" suffix is present (transport default
// applies). Truncates host to cap-1 bytes silently — outbound specs
// are short DNS names in practice.
static int parse_host_port(const char *spec, char *host_out, int cap)
{
    if (cap > 0) host_out[0] = '\0';
    if (!spec || !spec[0] || cap <= 0) return 0;
    const char *colon = strchr(spec, ':');
    int host_len = colon ? (int)(colon - spec) : (int)strlen(spec);
    if (host_len >= cap) host_len = cap - 1;
    memcpy(host_out, spec, host_len);
    host_out[host_len] = '\0';
    return colon ? atoi(colon + 1) : 0;
}

// Compute the actual dial destination for the transport. Order of
// preference:
//   1. config_sip_outbound() — explicit user override always wins
//      (skip SRV; if the user typed a host, they meant that host).
//   2. DNS-SRV record for the (transport, sip_host) pair, but only
//      when no explicit port was configured. Some providers (Telekom)
//      publish only an SRV record and do not have an A record on the
//      bare domain, so SRV is the only working path.
//   3. Direct A-record lookup on config_sip_host() with the configured
//      (or transport-default) port.
// Transport's default SIP port when none is configured/discovered.
static int default_sip_port(void)
{
    return (strcmp(config_sip_transport(), "tls") == 0) ? 5061 : 5060;
}

// Canonical transport token ("udp"/"tcp"/"tls") — exactly the kind
// sip_transport_open() ends up creating: an empty or unimplemented value
// collapses to UDP, mirroring the fallback inside sip_transport_open().
// Used by the reload handler to detect a live transport-kind change so it
// can reopen the transport instead of re-resolving the old socket kind.
static const char *canonical_transport(void)
{
    const char *tr = config_sip_transport();
    if (strcasecmp(tr, "tcp") == 0) return "tcp";
    if (strcasecmp(tr, "tls") == 0) return "tls";
    return "udp";
}

static void dial_destination(char *host_out, int cap, int *port_out)
{
    const char *outbound = config_sip_outbound();
    if (outbound && outbound[0]) {
        int p = parse_host_port(outbound, host_out, cap);
        *port_out = p > 0 ? p : default_sip_port();
        return;
    }

#ifdef ESP_PLATFORM
    // SRV only when the user did not override the port; the lookup is
    // free to suggest a non-default port (e.g. 5061 for sips/tcp).
    if (config_sip_port() == 0) {
        const char *tr = config_sip_transport();
        const char *svc   = (strcmp(tr, "tls") == 0) ? "sips" : "sip";
        const char *proto = (strcmp(tr, "udp") == 0) ? "udp"  : "tcp";
        char srv_target[SIP_SRV_TARGET_MAX];
        int  srv_port = 0;
        if (sip_srv_lookup(svc, proto, config_sip_host(),
                           srv_target, sizeof(srv_target), &srv_port)) {
            strncpy(host_out, srv_target, cap - 1);
            host_out[cap - 1] = '\0';
            *port_out = srv_port;
            return;
        }
    }
#endif

    strncpy(host_out, config_sip_host(), cap - 1);
    host_out[cap - 1] = '\0';
    // No explicit port and no SRV record (e.g. fritz.box): substitute the
    // transport default HERE, not only in sip_transport_open(). The reload
    // path (config change → sip_transport_resolve()) does not default a 0
    // port; it would resolve host:0 and send UDP to port 0 — the cause of
    // "Fritz!Box + empty port stops registering after a settings save".
    int port = config_sip_port();
    *port_out = port > 0 ? port : default_sip_port();
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
    if (strlen(config_contact_host_override()) > 0)
        return config_contact_host_override();   // explicit manual/QEMU override wins
    if (s_public_ip[0])
        return s_public_ip;                      // learned public IP (UDP NAT)
    return sip_transport_local_ip(c->transport); // direct / not yet learned
}

static int advertised_port(void)
{
    return config_contact_port_override() != 0
               ? config_contact_port_override() : config_sip_local_port();
}

static int build_register(sip_ctx_t *c, char *buf, int cap, bool with_auth)
{
    char branch[20], cnonce[20];
    random_hex(branch, 16);

    const char *our_host = advertised_host(c);
    int our_port = advertised_port();

    char request_uri[96];
    snprintf(request_uri, sizeof(request_uri), "sip:%s", config_sip_host());

    const char *identity = current_identity_user();

    // Advertise a stable instance id (RFC 5626 / GRUU) so the registrar
    // recognises a re-registration as the same device and REPLACES the
    // old binding instead of piling up a fresh one per reboot/connection
    // — Telekom otherwise accumulates a contact per boot until each
    // expires. Omitted only if no device id is available (NVS failure).
    // ";+sip.instance=\"<urn:uuid:" + 36-char UUID + ">\"" is exactly 64
    // chars; size generously so the closing quote can never be truncated
    // (a clipped Contact param earns a 400 Bad Request from the registrar).
    char instance[80];
    const char *device_id = config_device_id();
    if (device_id && device_id[0]) {
        snprintf(instance, sizeof(instance),
                 ";+sip.instance=\"<urn:uuid:%s>\"", device_id);
    } else {
        instance[0] = '\0';
    }

    int n = snprintf(buf, cap,
        "REGISTER %s SIP/2.0\r\n"
        "Via: SIP/2.0/%s %s:%d;branch=z9hG4bK%s;rport\r\n"
        "Max-Forwards: 70\r\n"
        "From: <sip:%s@%s>;tag=%s\r\n"
        "To: <sip:%s@%s>\r\n"
        "Call-ID: %s\r\n"
        "CSeq: %lu REGISTER\r\n"
        "Contact: <sip:%s@%s:%d>%s\r\n"
        "Expires: %d\r\n"
        "User-Agent: phoneblock-dongle/0.1\r\n",
        request_uri,
        sip_transport_via_token(c->transport),
        our_host, our_port, branch,
        identity, config_sip_host(), c->from_tag,
        identity, config_sip_host(),
        c->call_id,
        (unsigned long)c->cseq,
        identity, our_host, our_port, instance,
        config_sip_expires());

    if (with_auth && c->challenge.valid) {
        char response[33];
        const char *qop = c->challenge.qop;
        const char *nc  = "00000001";
        const char *auth_user = current_auth_user();
        const char *realm     = current_realm(&c->challenge);
        random_hex(cnonce, 16);

        digest_response(
            auth_user, config_sip_pass(),
            realm, c->challenge.nonce,
            "REGISTER", request_uri,
            qop, nc, cnonce,
            response);

        n += snprintf(buf + n, cap - n,
            "Authorization: Digest username=\"%s\", realm=\"%s\", "
            "nonce=\"%s\", uri=\"%s\", response=\"%s\", algorithm=%s",
            auth_user, realm,
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
// REGISTER round-trip helper
// ---------------------------------------------------------------------------

// Send a request to the registrar and block for the response. Returns
// the response length (NUL-terminated in rx) or -1 on send/recv error
// or timeout.
static int sip_send_recv(sip_ctx_t *c, const char *tx, int tx_len,
                         char *rx, int rx_cap)
{
    if (sip_transport_send(c->transport, tx, tx_len) < 0) {
        return -1;
    }
    // Wait for a complete SIP response until the budget runs out. A single
    // recv returning 0 does NOT mean "no answer": on a stream transport
    // (TLS/TCP) a TLS record boundary that doesn't line up with a SIP
    // message boundary, a post-handshake record, or a partial read all
    // surface as 0 here. The old one-shot code treated that as failure,
    // which made TLS spuriously report "no response" a few hundred ms
    // after sending even though the registrar answered. Keep looping on 0
    // and only give up once the deadline is actually reached.
    int64_t deadline = esp_timer_get_time()
                     + (int64_t)SIP_REGISTER_RECV_TIMEOUT_MS * 1000;
    int r = 0;
    for (;;) {
        // Feed the task watchdog between slices so a long total wait (slow
        // or dead registrar) can't trip the 20 s WDT mid-registration.
        // Harmless no-op when this task isn't subscribed (initial register
        // runs before esp_task_wdt_add()).
        if (esp_task_wdt_status(NULL) == ESP_OK) esp_task_wdt_reset();

        int64_t remaining_us = deadline - esp_timer_get_time();
        if (remaining_us <= 0) {
            ESP_LOGW(TAG, "no response from registrar within %d ms",
                     SIP_REGISTER_RECV_TIMEOUT_MS);
            return -1;
        }
        int slice_ms = (int)(remaining_us / 1000);
        if (slice_ms > SIP_REGISTER_RECV_SLICE_MS)
            slice_ms = SIP_REGISTER_RECV_SLICE_MS;
        struct sockaddr_in from;
        r = sip_transport_recv(c->transport, slice_ms, rx, rx_cap - 1, &from);
        if (r < 0) return -1;   // transport error (already logged/reconnected)
        if (r > 0) break;       // complete message
        // r == 0: slice expired or partial frame — keep waiting.
    }
    rx[r] = '\0';
    // Learn our public IP from the registrar's view (Via "received="), so
    // Via/Contact/SDP can advertise it for UDP NAT traversal.
    char rcv[INET_ADDRSTRLEN];
    if (parse_via_received(rx, r, rcv, sizeof(rcv)) && rcv[0]
            && strcmp(rcv, s_public_ip) != 0) {
        strncpy(s_public_ip, rcv, sizeof(s_public_ip) - 1);
        s_public_ip[sizeof(s_public_ip) - 1] = '\0';
        ESP_LOGI(TAG, "public IP (via received=): %s", s_public_ip);
    }
    return r;
}

// ---------------------------------------------------------------------------
// REGISTER exchange: send initial, parse challenge, send with auth
// ---------------------------------------------------------------------------

// Outcome of a single REGISTER exchange. Splitting "transient" vs.
// "definitive" lets the periodic-refresh caller debounce the noisy
// transient class (Fritz!Box not answering on this attempt, transport
// hiccup) while still surfacing the definitive class (auth rejected,
// 4xx other than 401/407, broken challenge) right away — those will
// not get better with retries.
typedef enum {
    REGISTER_OK = 0,
    REGISTER_TRANSIENT,
    REGISTER_DEFINITIVE,
} register_outcome_t;

// Run a REGISTER transaction, including a digest-auth retry on 401/407.
// On success returns REGISTER_OK and writes the registrar-granted
// expiry (in seconds) into *granted_expires; if the response carries
// no Expires information, *granted_expires is set to the requested
// value so the caller never has to deal with a sentinel.
//
// On failure writes a diagnostic into err[err_cap] and returns either
// REGISTER_TRANSIENT (network/timeout — caller may want to retry
// silently while a still-valid binding gives us cover) or
// REGISTER_DEFINITIVE (registrar said "no" or the protocol is broken
// in a way that retries can't fix). Does NOT log the failure itself —
// the policy of when to surface it (and thus mirror it to the web UI via
// the log hook) belongs at the caller, which knows whether it's an
// initial register, a periodic refresh, or a defensive re-register
// after a TCP reconnect.
static register_outcome_t do_register(sip_ctx_t *c, int *granted_expires,
                                      char *err, size_t err_cap)
{
    if (err && err_cap > 0) err[0] = '\0';
    *granted_expires = config_sip_expires();
    // Heap-allocate the 6 KB of message buffers — on the 8 KB task stack
    // they overflow once lwip's sendto/recvfrom add their own overhead.
    char *tx = malloc(SIP_TX_BUF_SIZE);
    char *rx = malloc(SIP_RX_BUF_SIZE);
    if (!tx || !rx) {
        ESP_LOGE(TAG, "malloc failed for SIP buffers");
        if (err) snprintf(err, err_cap,
            "REGISTER aborted: out of memory for SIP buffers");
        free(tx); free(rx);
        return REGISTER_DEFINITIVE;
    }

    register_outcome_t result = REGISTER_DEFINITIVE;
    c->cseq++;
    int tx_len = build_register(c, tx, SIP_TX_BUF_SIZE, false);
    ESP_LOGI(TAG, "→ REGISTER (%d bytes):\n%.*s", tx_len, tx_len, tx);
    int rx_len = sip_send_recv(c, tx, tx_len, rx, SIP_RX_BUF_SIZE);
    if (rx_len < 0) {
        ESP_LOGE(TAG, "sip_send_recv failed");
        if (err) snprintf(err, err_cap,
            "REGISTER: no response from registrar (timeout/transport)");
        result = REGISTER_TRANSIENT;
        goto cleanup;
    }
    ESP_LOGI(TAG, "← %d bytes:\n%.*s", rx_len, rx_len, rx);

    int status = parse_status_code(rx, rx_len);
    ESP_LOGI(TAG, "← %d (%d bytes)", status, rx_len);

    if (status == 200) {
        int granted = parse_register_expires(rx, rx_len);
        if (granted >= 0) *granted_expires = granted;
        result = REGISTER_OK;
        goto cleanup;
    }
    if (status != 401 && status != 407) {
        if (err) snprintf(err, err_cap,
            "REGISTER rejected: %d (check user / extension / Fritz!Box log)", status);
        ESP_LOGE(TAG, "REGISTER rejected: %d", status);
        result = REGISTER_DEFINITIVE;
        goto cleanup;
    }

    // Parse challenge.
    const char *hdr = find_header(rx, rx_len, "WWW-Authenticate");
    if (!hdr) hdr = find_header(rx, rx_len, "Proxy-Authenticate");
    if (!hdr) {
        if (err) snprintf(err, err_cap,
            "REGISTER %d without WWW-Authenticate header — bad registrar", status);
        ESP_LOGE(TAG, "REGISTER %d without WWW-Authenticate header", status);
        result = REGISTER_DEFINITIVE;
        goto cleanup;
    }
    char val[SIP_MAX_CHALLENGE + 64];
    header_value(hdr, rx + rx_len, val, sizeof(val));
    sip_auth_parse_challenge(val, &c->challenge);
    if (!c->challenge.valid) {
        ESP_LOGE(TAG, "auth challenge missing realm/nonce");
        if (err) snprintf(err, err_cap,
            "REGISTER: auth challenge missing realm/nonce");
        result = REGISTER_DEFINITIVE;
        goto cleanup;
    }
    ESP_LOGI(TAG, "challenge: realm=\"%s\" qop=\"%s\"",
             c->challenge.realm, c->challenge.qop);

    // Resend with Authorization header. A server may answer the first
    // authenticated REGISTER with another 401/407 carrying stale=true and
    // a fresh nonce — the credentials are fine, the nonce just needs
    // refreshing. Telekom does this regularly because Via/Contact switch
    // from the LAN IP to the learned public IP between the two requests,
    // and the nonce is bound to that. Re-auth with the new nonce instead
    // of declaring the auth rejected; a genuine wrong password yields a
    // plain 401 (no stale) and still fails fast. Bounded so a misbehaving
    // registrar that always says stale can't spin us forever.
    const int max_stale_retries = 2;
    for (int stale_retry = 0; ; stale_retry++) {
        c->cseq++;
        tx_len = build_register(c, tx, SIP_TX_BUF_SIZE, true);
        ESP_LOGI(TAG, "→ REGISTER with auth (%d bytes):\n%.*s",
                 tx_len, tx_len, tx);
        rx_len = sip_send_recv(c, tx, tx_len, rx, SIP_RX_BUF_SIZE);
        if (rx_len < 0) {
            if (err) snprintf(err, err_cap,
                "REGISTER (with auth): no response from registrar");
            result = REGISTER_TRANSIENT;
            goto cleanup;
        }
        ESP_LOGI(TAG, "← %d bytes:\n%.*s", rx_len, rx_len, rx);

        status = parse_status_code(rx, rx_len);
        if (status == 200) {
            ESP_LOGI(TAG, "← %d (authenticated)", status);
            break;
        }

        // Refresh the challenge and retry only when the server explicitly
        // marked the nonce stale; otherwise this is a real rejection.
        if ((status == 401 || status == 407)
                && stale_retry < max_stale_retries) {
            const char *h = find_header(rx, rx_len, "WWW-Authenticate");
            if (!h) h = find_header(rx, rx_len, "Proxy-Authenticate");
            if (h) {
                char v[SIP_MAX_CHALLENGE + 64];
                header_value(h, rx + rx_len, v, sizeof(v));
                auth_challenge_t fresh;
                sip_auth_parse_challenge(v, &fresh);
                if (fresh.valid && fresh.stale) {
                    c->challenge = fresh;
                    ESP_LOGI(TAG, "← %d stale nonce → re-auth with fresh nonce",
                             status);
                    continue;
                }
            }
        }

        ESP_LOGI(TAG, "← %d (authenticated)", status);
        if (err) snprintf(err, err_cap,
            "authentication rejected: %d — check SIP user/password/realm",
            status);
        ESP_LOGE(TAG, "authentication rejected: %d", status);
        result = REGISTER_DEFINITIVE;
        goto cleanup;
    }
    {
        int granted = parse_register_expires(rx, rx_len);
        if (granted >= 0) *granted_expires = granted;
    }
    result = REGISTER_OK;

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
        current_identity_user(), advertised_host(c), advertised_port());

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
    sip_transport_send_to(c->transport, peer, tx, tx_len);
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
        our_ip, our_ip, config_rtp_port());
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

    // For non-UDP transports, the in-dialog R-URI carries an explicit
    // ";transport=<tcp|tls>" so the registrar/UA on the other side
    // doesn't fall back to UDP for our BYE.
    const char *uri_param = sip_transport_uri_param(c->transport);
    char ruri[160];
    if (strcmp(uri_param, "udp") == 0) {
        snprintf(ruri, sizeof(ruri), "%s", d->remote_uri);
    } else {
        snprintf(ruri, sizeof(ruri), "%s;transport=%s",
                 d->remote_uri, uri_param);
    }

    return snprintf(buf, cap,
        "BYE %s SIP/2.0\r\n"
        "Via: SIP/2.0/%s %s:%d;branch=z9hG4bK%s;rport\r\n"
        "Max-Forwards: 70\r\n"
        "From: <sip:%s@%s:%d>;tag=%s\r\n"
        "To: <%s>;tag=%s\r\n"
        "Call-ID: %s\r\n"
        "CSeq: %lu BYE\r\n"
        "Contact: <sip:%s@%s:%d>\r\n"
        "User-Agent: phoneblock-dongle/0.1\r\n"
        "Content-Length: 0\r\n\r\n",
        ruri,
        sip_transport_via_token(c->transport),
        our_host, our_port, branch,
        current_identity_user(), advertised_host(c), our_port, d->our_tag,
        d->remote_uri, d->from_tag,
        d->call_id,
        (unsigned long)d->out_cseq,
        current_identity_user(), our_host, our_port);
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

    sip_transport_send_to(c->transport, &d->peer, tx, tx_len);
    free(tx);
    d->state = DIALOG_BYE_SENT;
}

// Extract the caller's number (user part of the From URI), normalize it
// for Germany, and query the PhoneBlock API.
//
// Two short-circuits skip the API call:
//  1. Fritz!Box delivers a non-numeric display-name ("Haui Mobil") → the
//     caller matched a phonebook entry the user added themselves, so
//     they already trust them. Don't waste an API round-trip.
//     Phonebook entries imported via PhoneBlock's CardDAV blocklist
//     carry the "SPAM:" marker (see is_known_contact()) and must NOT
//     short-circuit — those are exactly the calls we want to block.
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

    // Optional: any '*'-prefixed internal Fritz!Box dial code (**622,
    // *21#, …) is treated as spam so the 200 OK + tone + BYE path
    // can be exercised by calling the dongle's extension from another
    // internal phone, without blacklisting a real external number.
    // Toggleable in the web UI under Spam-Ansage; default comes from
    // CONFIG_SIP_TEST_FORCE_SPAM_STAR_NUMBERS until the user changes it.
    if (config_accept_test_calls() && raw_user[0] == '*') {
        ESP_LOGW(TAG, "TEST MODE: caller '%s' forced to SPAM", raw_user);
        stats_record_call(raw_user, display, VERDICT_SPAM);
        return VERDICT_SPAM;
    }

    if (is_known_contact(display)) {
        ESP_LOGI(TAG, "caller '%s' resolved via phone book → skip API",
                 display);
        if (config_log_known_calls()) {
            stats_record_call(raw_user, display, VERDICT_LEGITIMATE);
        } else {
            stats_record_call_counters_only(VERDICT_LEGITIMATE);
        }
        return VERDICT_LEGITIMATE;
    }

    char number[64];
    normalize_de(raw_user, number, sizeof(number));
    ESP_LOGI(TAG, "caller URI=%s raw=%s normalized=%s", uri, raw_user, number);

    if (!looks_dialable(number)) {
        ESP_LOGI(TAG, "non-external caller '%s' → skip API", number);
        if (config_log_known_calls()) {
            stats_record_call(number, display, VERDICT_LEGITIMATE);
        } else {
            stats_record_call_counters_only(VERDICT_LEGITIMATE);
        }
        return VERDICT_LEGITIMATE;
    }

    pb_check_result_t result;
    verdict_t v = phoneblock_check(number, &result, NULL);
    stats_record_call_checked(number, display, &result);

    // Fair-use contribution required by /api/check-prefix: when our
    // privacy-preserving lookup hides which number we queried, the
    // server cannot keep tailored compact blocklists current on its
    // own. POST the plaintext number back on a positive match so the
    // call counter / LASTPING are refreshed.
    //
    // Enqueued for an async worker rather than POSTed synchronously:
    // the second TLS handshake (300–600 ms cert-verify + RTT) sat on
    // the critical path between verdict and 200 OK / 486, exactly
    // the window where the Fritz!Box decides whether to escalate to
    // ringing the real phones.
    if (v == VERDICT_SPAM) {
        report_queue_enqueue(number);
    }
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

    // Second call arriving while we're busy with another.
    if (d->state != DIALOG_IDLE) {
        // The only state worth preempting is DIALOG_STREAMING — there
        // we'd otherwise hold the new caller off for 5–15 s of audio
        // playback against an already-classified spammer. All other
        // states (TRYING/ANSWERED/REJECTED/BYE_SENT) clear within
        // milliseconds; making the new caller wait that out is fine,
        // and bailing during TRYING would just be caller-roulette
        // since we don't even have a verdict yet.
        if (d->state != DIALOG_STREAMING) {
            ESP_LOGW(TAG, "second INVITE in state %d → 486 Busy Here",
                     d->state);
            send_response(c, from, req, req_len, 486, "Busy Here",
                          NULL, NULL);
            return;
        }
        ESP_LOGI(TAG, "second INVITE during STREAMING → preempt: "
                      "abort announcement, BYE old dialog, take new call");
        rtp_request_abort();
        send_bye(c);
        // BYE is fire-and-forget UDP. Any straggler 200/ACK/BYE for
        // the old Call-ID will Call-ID-mismatch in handle_incoming
        // and be discarded — wipe and fall through to the IDLE path.
        memset(d, 0, sizeof(*d));
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
        announcement_src_t src;
        announcement_open(&src);
        if (src.len > 0 && d->rtp_dest_valid) {
            // PCMA at 8 kHz → 8000 bytes == 1 s, so duration_us = bytes * 125.
            // Add a short tail margin so the last frame is delivered before BYE.
            int64_t duration_us = (int64_t)src.len * 125LL;
            ESP_LOGI(TAG, "ACK received → streaming announcement (%u bytes ≈ %lld ms), then BYE",
                     (unsigned)src.len, (long long)(duration_us / 1000));
            rtp_play_audio(&d->rtp_dest, &src);   // task owns src now, closes it
            d->bye_at_us = esp_timer_get_time() + duration_us + 200000LL;
            d->state = DIALOG_STREAMING;
        } else {
            announcement_close(&src);   // nothing to stream — release the handle
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
        // If we were still streaming the announcement, stop it now —
        // the remote is gone, every further RTP packet is wasted.
        rtp_request_abort();
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

    char dial_host[64];
    int  dial_port;
    const char *outbound;
    const char *tls_sni;

    // Open the SIP transport, retrying until it succeeds. A failure here
    // is almost always transient: the common case is a WiFi outage
    // (reason 6 deauth from the AP) that races the task (re)start, so for
    // a few seconds there's no default netif and DNS can't resolve.
    // Earlier code treated any open failure as fatal ("check host/port")
    // and self-deleted the task — but a WiFi reconnect does NOT respawn
    // it (on_got_ip only sets an event bit), so a momentary blip turned
    // into permanently-lost SIP registration until the next reboot or
    // config Save. Keep the task alive and re-read the config on every
    // attempt, so a genuine host/port fix is also picked up here without
    // a restart.
    const int open_retry_s = 15;
    for (int attempt = 0; ; attempt++) {
        // Re-read every attempt: a config change made while we were
        // failing (user fixes host/port and hits Save) takes effect on
        // the next try. Clear the reload flag so the main loop doesn't
        // immediately do a redundant re-REGISTER once we get going.
        s_reload_requested = false;
        dial_destination(dial_host, sizeof(dial_host), &dial_port);

        // TLS SNI / cert name: the service domain the user configured,
        // not the SRV-resolved edge host (#363). With an explicit
        // outbound proxy the TLS peer *is* the dial host, so use that.
        outbound = config_sip_outbound();
        tls_sni  = (outbound && outbound[0]) ? dial_host : config_sip_host();

        ctx.transport = sip_transport_open(config_sip_transport(),
                                           dial_host, dial_port, tls_sni,
                                           config_sip_local_port());
        if (ctx.transport) {
            break;
        }

        // Surface the failure on the dashboard, but only on the first try
        // and then roughly every 5 min (every 20th attempt at the 15 s
        // cadence). Logging an ERROR on every retry would flush the
        // 32-entry log ring and bury whatever else the operator needs to
        // see. dial_host is up to 64 chars; cap to keep the formatted
        // line within the stats error-message buffer.
        if (attempt == 0 || (attempt % 20) == 0) {
            char msg[STATS_ERROR_MSG_LEN];
            snprintf(msg, sizeof(msg),
                     "transport open failed (%s %.40s:%d) — retrying, "
                     "check host/port if persistent",
                     config_sip_transport(), dial_host, dial_port);
            ESP_LOGE(TAG, "%s", msg);
        }
        s_registered = false;
        stats_record_sip_state(false);
        vTaskDelay(pdMS_TO_TICKS(open_retry_s * 1000));
    }
    if (strcmp(dial_host, config_sip_host()) != 0) {
        const char *via = (outbound && outbound[0]) ? "outbound proxy" : "SRV";
        ESP_LOGI(TAG, "%s %s:%d active (SIP host: %s)",
                 via, dial_host, dial_port, config_sip_host());
    }

    // Extended SIP parameters are persisted but the firmware doesn't
    // (yet) implement all of them. Warn once per start so the user sees
    // in the dashboard errors that a setting is being ignored, and can
    // react instead of wondering why a feature never engaged.
    const char *tr = config_sip_transport();
    if (tr[0] && strcmp(tr, "udp") != 0
              && strcmp(tr, "tcp") != 0
              && strcmp(tr, "tls") != 0) {
        char msg[80];
        snprintf(msg, sizeof(msg),
                 "transport %.8s ignored — firmware implements UDP/TCP/TLS only",
                 tr);
        ESP_LOGW(TAG, "%s", msg);
    }
    // TLS protects only the signaling. Without SRTP (Phase 5) the audio
    // path is still RTP/AVP cleartext on UDP/RTP. Surface that visibly
    // so users do not have a false sense of end-to-end encryption.
    if (strcmp(tr, "tls") == 0) {
        const char *msg =
            "TLS active but RTP is still plaintext (SRTP arrives in Phase 5)";
        ESP_LOGW(TAG, "%s", msg);
    }
    if (strcmp(config_sip_srtp(), "off") != 0
        && strcmp(config_sip_srtp(), "") != 0) {
        char msg[80];
        snprintf(msg, sizeof(msg),
                 "SRTP %.12s ignored — firmware only does plain RTP",
                 config_sip_srtp());
        ESP_LOGW(TAG, "%s", msg);
    }
    const int retry_delay_s = 30;
    char *rx = NULL;
    int64_t refresh_at_us = 0;  // absolute deadline for next REGISTER (microseconds)

    // Settle pause only when the caller asked for it (TR-064
    // provisioning sets s_settle_pending). For boot or manual config
    // there's no FB-internal race to absorb, and 1.5 s of "Verbinde…"
    // on every Save is bad UX.
    if (s_settle_pending) {
        s_settle_pending = false;
        vTaskDelay(pdMS_TO_TICKS(1500));
    }

    // Initial registration. Any failure here is surfaced immediately —
    // there's no prior binding to give us cover, and the user wants
    // to know right away whether their freshly-entered SIP creds work.
    int granted_expires = 0;
    char err[STATS_ERROR_MSG_LEN] = "";
    register_outcome_t r = do_register(&ctx, &granted_expires, err, sizeof(err));
    bool ok = (r == REGISTER_OK);
    s_registered = ok;
    stats_record_sip_state(ok);
    if (ok) {
        ESP_LOGI(TAG, "REGISTERED as %s@%s (granted %d s, requested %d s)",
                 config_sip_user(), config_sip_host(),
                 granted_expires, config_sip_expires());
        s_binding_expires_at_us = esp_timer_get_time()
                                + (int64_t)granted_expires * 1000000LL;
        s_pending_error[0] = '\0';
        refresh_at_us = esp_timer_get_time() + (int64_t)(granted_expires / 2) * 1000000LL;
    } else {
        ESP_LOGE(TAG, "initial registration failed (%s), retry in %d s",
                 err[0] ? err : "no detail", retry_delay_s);
        s_binding_expires_at_us = 0;
        s_pending_error[0] = '\0';
        refresh_at_us = esp_timer_get_time() + (int64_t)retry_delay_s * 1000000LL;
    }

    rx = malloc(SIP_RX_BUF_SIZE);
    if (!rx) {
        ESP_LOGE(TAG, "malloc rx buffer failed — aborting SIP task");
        s_sip_task = NULL;
        vTaskDelete(NULL);
        return;
    }

    // Subscribe to the task watchdog. Any iteration that doesn't loop
    // back to esp_task_wdt_reset() within CONFIG_ESP_TASK_WDT_TIMEOUT_S
    // triggers a panic + coredump (see sdkconfig.defaults). The
    // synchronous phoneblock_check() and do_register() inside this
    // loop can wedge on a half-closed TLS connection past their own
    // 10 s timeouts; without this the task hangs silently and the
    // web UI gets starved of any "still alive" signal.
    esp_task_wdt_add(NULL);

    while (1) {
        esp_task_wdt_reset();
        // Config changed? Re-register with the new credentials before
        // going back to sleep in select(). The web-UI POST handler sets
        // the flag via sip_register_request_reload().
        if (s_reload_requested) {
            s_reload_requested = false;
            ESP_LOGI(TAG, "config reload requested → re-REGISTER with new creds");
            // Refresh dial address too, in case the registrar host or
            // outbound proxy changed.
            char new_dial_host[64];
            int  new_dial_port;
            dial_destination(new_dial_host, sizeof(new_dial_host), &new_dial_port);
            const char *new_outbound = config_sip_outbound();
            const char *new_tls_sni  = (new_outbound && new_outbound[0])
                                           ? new_dial_host : config_sip_host();

            // A live transport-kind change (e.g. switching a running
            // Fritz!Box/UDP binding to a Telekom TLS preset) cannot be
            // honored by re-resolving the existing transport: it keeps its
            // original socket kind and would, say, send a UDP datagram to
            // the TLS-only port 5061 — which the registrar silently drops
            // (no response). It also explains why switching to TCP "works"
            // by accident: the leftover UDP socket hits port 5060, which
            // Telekom answers over UDP too. Reopen the transport whenever
            // the kind differs; a plain host/port re-resolve still suffices
            // for credential- or registrar-only changes.
            const char *want = canonical_transport();
            if (strcasecmp(want, sip_transport_via_token(ctx.transport)) != 0) {
                ESP_LOGI(TAG, "transport changed %s → %s, reopening",
                         sip_transport_via_token(ctx.transport), want);
                sip_transport_t *nt =
                    sip_transport_open(config_sip_transport(), new_dial_host,
                                       new_dial_port, new_tls_sni,
                                       config_sip_local_port());
                if (nt) {
                    sip_transport_close(ctx.transport);
                    ctx.transport = nt;
                } else {
                    // Reopen failed (e.g. TLS handshake rejected). Keep the
                    // old handle so we don't crash on NULL; the register
                    // below fails and schedules the usual 30 s retry.
                    ESP_LOGE(TAG, "reopen as %s failed — keeping %s transport",
                             want, sip_transport_via_token(ctx.transport));
                    sip_transport_resolve(ctx.transport, new_dial_host,
                                          new_dial_port, new_tls_sni);
                }
            } else {
                sip_transport_resolve(ctx.transport, new_dial_host,
                                      new_dial_port, new_tls_sni);
            }
            // Settle pause only when the caller asked for it
            // (TR-064 provisioning). FB needs a beat for a newly
            // created extension to go live on its own SIP stack;
            // without that beat the first REGISTER hits a not-yet-
            // active slot and falls into the 30 s retry. Manual
            // edits / provider presets skip the pause so the
            // "Verbinde…" state clears within ~500 ms after Save.
            if (s_settle_pending) {
                s_settle_pending = false;
                vTaskDelay(pdMS_TO_TICKS(1500));
            }
            // Config-reload register: like the initial one, surface any
            // failure directly — the user just hit Save and is waiting
            // to see whether the new creds work.
            err[0] = '\0';
            r = do_register(&ctx, &granted_expires, err, sizeof(err));
            ok = (r == REGISTER_OK);
            s_registered = ok;
            stats_record_sip_state(ok);
            if (ok) {
                ESP_LOGI(TAG, "re-REGISTERED after config change (granted %d s)",
                         granted_expires);
                s_binding_expires_at_us = esp_timer_get_time()
                                        + (int64_t)granted_expires * 1000000LL;
                s_pending_error[0] = '\0';
                refresh_at_us = esp_timer_get_time() + (int64_t)(granted_expires / 2) * 1000000LL;
            } else {
                ESP_LOGE(TAG, "REGISTER with new config failed (%s), retry in %d s",
                         err[0] ? err : "no detail", retry_delay_s);
                s_binding_expires_at_us = 0;
                s_pending_error[0] = '\0';
                refresh_at_us = esp_timer_get_time() + (int64_t)retry_delay_s * 1000000LL;
            }
        }

        // For TCP/TLS the transport reconnects transparently after a
        // dropped connection. Surface that here as an immediate REGISTER
        // refresh so the registrar binds the new connection to our
        // extension, rather than waiting out the (expires/2) refresh
        // window during which incoming INVITEs would be lost.
        if (sip_transport_consume_reconnect(ctx.transport)) {
            // A dropped TCP/TLS connection is worth surfacing: WARN so the
            // log hook records it, letting the operator correlate a
            // transport flap with whatever else they see in the log.
            ESP_LOGW(TAG, "transport reconnected → re-REGISTER");
            err[0] = '\0';
            r = do_register(&ctx, &granted_expires, err, sizeof(err));
            ok = (r == REGISTER_OK);
            s_registered = ok;
            stats_record_sip_state(ok);
            if (ok) {
                s_binding_expires_at_us = esp_timer_get_time()
                                        + (int64_t)granted_expires * 1000000LL;
                s_pending_error[0] = '\0';
                refresh_at_us = esp_timer_get_time() + (int64_t)(granted_expires / 2) * 1000000LL;
            } else {
                ESP_LOGE(TAG, "re-REGISTER after reconnect failed (%s), retry in %d s",
                         err[0] ? err : "no detail", retry_delay_s);
                s_binding_expires_at_us = 0;
                s_pending_error[0] = '\0';
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

        struct sockaddr_in from;
        int n = sip_transport_recv(ctx.transport, (int)(remaining_us / 1000),
                                   rx, SIP_RX_BUF_SIZE - 1, &from);
        if (n < 0) {
            vTaskDelay(pdMS_TO_TICKS(1000));
            continue;
        }

        if (n == 0) {
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
            // REGISTER refresh — the debounced path. While the FB still
            // has a valid binding for us (now < s_binding_expires_at_us)
            // a transient failure is silently retried. Definitive
            // failures (auth rejected, broken challenge, 4xx other than
            // 401/407) and transient failures past the binding deadline
            // surface as a single dashboard entry.
            err[0] = '\0';
            r = do_register(&ctx, &granted_expires, err, sizeof(err));
            now = esp_timer_get_time();
            if (r == REGISTER_OK) {
                ESP_LOGI(TAG, "re-REGISTERED (granted %d s)", granted_expires);
                if (!s_registered) stats_record_sip_state(true);
                s_registered = true;
                s_binding_expires_at_us = now + (int64_t)granted_expires * 1000000LL;
                s_pending_error[0] = '\0';
                refresh_at_us = now + (int64_t)(granted_expires / 2) * 1000000LL;
            } else if (r == REGISTER_TRANSIENT && now < s_binding_expires_at_us) {
                // Transient failure with the FB-side binding still
                // valid for a while — keep s_registered true (the FB
                // does still consider us bound), stash the reason so
                // it becomes the surfaced cause if the binding does
                // eventually lapse, and try again at the standard
                // retry interval.
                int64_t left_s = (s_binding_expires_at_us - now) / 1000000LL;
                ESP_LOGW(TAG, "re-REGISTER transient (%s) — binding valid "
                              "for %lld s more, retry in %d s",
                         err, (long long)left_s, retry_delay_s);
                if (err[0]) {
                    strncpy(s_pending_error, err, sizeof(s_pending_error) - 1);
                    s_pending_error[sizeof(s_pending_error) - 1] = '\0';
                }
                refresh_at_us = now + (int64_t)retry_delay_s * 1000000LL;
            } else {
                // Either definitive (won't recover with retries) or
                // transient that has finally outlived the binding.
                if (s_registered) stats_record_sip_state(false);
                s_registered = false;
                char msg[STATS_ERROR_MSG_LEN];
                if (r == REGISTER_TRANSIENT) {
                    // "binding expired: " is 17 chars, leaving 110 for
                    // the reason. Explicit precision keeps GCC happy
                    // about the worst-case pending_error/err of 128.
                    snprintf(msg, sizeof(msg), "binding expired: %.110s",
                             s_pending_error[0] ? s_pending_error : err);
                } else {
                    snprintf(msg, sizeof(msg), "%.127s",
                             err[0] ? err : "REGISTER failed");
                }
                ESP_LOGE(TAG, "re-REGISTER failed: %s — retry in %d s",
                         msg, retry_delay_s);
                s_binding_expires_at_us = 0;
                s_pending_error[0] = '\0';
                refresh_at_us = now + (int64_t)retry_delay_s * 1000000LL;
            }
            continue;
        }

        // Incoming packet.
        rx[n] = '\0';
        handle_incoming(&ctx, rx, n, &from);
    }
}

void sip_register_start(void)
{
    // Host + user are mandatory; an empty password is a valid credential
    // (digest HA1 = MD5(user:realm:"")) — some providers register an
    // anonymous account (e.g. Telekom's anonymous@t-online.de) with no
    // password. Don't refuse locally: attempt the REGISTER and let the
    // registrar's response surface in the status UI.
    if (strlen(config_sip_host()) == 0 ||
        strlen(config_sip_user()) == 0) {
        ESP_LOGW(TAG, "SIP config incomplete, skipping registration");
        return;
    }
    if (s_sip_task) {
        // Already running — request_reload() handles credential changes.
        return;
    }
    ESP_LOGI(TAG, "starting SIP registrar task (host=%s user=%s)",
             config_sip_host(), config_sip_user());
    // Stack sized for the synchronous HTTPS check in handle_invite(): mbedtls
    // pk_verify spikes 6–8 KB during the TLS handshake to phoneblock.net,
    // stacked on top of the SIP parser. 8 KB was within ~1 KB of the limit
    // and overflowed in the field (crash-reports/1.0.9). If the API call ever
    // moves into a dedicated worker, this can come back down.
    xTaskCreate(sip_task, "sip_register", 12288, NULL, 5, &s_sip_task);
}
