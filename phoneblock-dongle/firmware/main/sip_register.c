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
#include "mbedtls/base64.h"

#include "lwip/sockets.h"

#include "sdkconfig.h"
#include "api.h"
#include "blocklist_sync.h"
#include "config.h"
#include "announcement.h"
#include "report_queue.h"
#include "sip_parse.h"
#include "sip_response.h"
#include "strbuf.h"
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

// NAT/connection keepalive interval. The REGISTER refresh runs at only
// granted/2 (~30 min for Telekom's 3600 s grant); between refreshes the
// long-lived TLS/TCP connection sits idle and the Fritz!Box NAT mapping (or
// the carrier) drops it, so the next refresh finds a reset connection
// (esp-tls read error -0x0050, then reconnect). A periodic double-CRLF ping
// (RFC 5626 §3.5.1 / RFC 5626 keep-alive) keeps the mapping and the
// connection warm. 30 s stays safely under the typical ~30 s UDP NAT window
// and is trivially cheap (4 bytes) on a mains-powered device.
#define SIP_KEEPALIVE_INTERVAL_S      30

// Issue #380: after we decide a call is not spam we must NOT reply with a
// fast final response (486/480) — that makes the Fritz!Box drop the
// Fritz!Fon app from the call (it suppresses the app while DECT keeps
// ringing). It is not enough to merely precede the decline with a 180:
// field testing showed a 180 immediately followed by 480 still leaves the
// app silent. The box only keeps escalating the call to the app if our
// branch stays "ringing" (180) for a while first. So we hold the 180 for
// this long before sending the decline; the other phones keep ringing the
// whole time and are NOT cut off when we drop out. 3 s is a tested-good
// value; the true minimum is unknown.
#define SIP_DECLINE_DELAY_US          (3LL * 1000000LL)

// After answering a spam caller with 200 OK we wait for the ACK before
// streaming the tone. If that ACK is lost (UDP) or never arrives, the
// single dialog slot must not stay wedged in DIALOG_ANSWERED forever —
// give up after this long and BYE, freeing the slot for the next call.
// 32 s ≈ RFC 3261 Timer H (64*T1); a real ACK arrives in milliseconds.
#define SIP_ACK_TIMEOUT_US            (32LL * 1000000LL)

// After we send a final teardown message we wait for the far side to
// confirm it: the ACK to our 480 decline (DIALOG_REJECTED) or the 200 to
// our BYE (DIALOG_BYE_SENT). If that confirmation is lost (UDP) and no new
// call comes in to reclaim the slot, don't sit in the teardown state
// forever — after this long, give up and return the dialog to IDLE so the
// dongle is always back at rest on its own. 32 s ≈ RFC 3261 Timer F/H; a
// real confirmation arrives in milliseconds on the LAN.
#define SIP_TEARDOWN_TIMEOUT_US       (32LL * 1000000LL)

// How much of an incoming INVITE we keep so the main loop can build the
// delayed decline response (it echoes Via/From/To/Call-ID/CSeq, all near
// the top — a truncated SDP tail is irrelevant).
#define SIP_INVITE_STORE_CAP          1500

typedef enum {
    DIALOG_IDLE,        // no active call
    DIALOG_TRYING,      // received INVITE, 100 Trying sent, deciding
    DIALOG_PROCEEDING,  // non-spam: sent 180 Ringing, letting the other phones
                        // ring; declines after SIP_DECLINE_DELAY_US (#380)
    DIALOG_ANSWERED,    // sent 200 OK with SDP, waiting for ACK → then stream
    DIALOG_STREAMING,   // playing tone to spam caller, BYE scheduled
    DIALOG_REJECTED,    // declined (480), waiting for ACK
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
    bool     srtp_tx;             // true → answer/stream as SRTP (RTP/SAVP)
    int      srtp_tag;            // crypto tag to echo in the SDP answer
    uint8_t  srtp_tx_key[RTP_SRTP_KEY_LEN];  // our SDES master key+salt
    char     media_ip[48];        // SDP c=/o= address to advertise (public,
                                  // STUN-mapped if available, else signalling IP)
    int      media_port;          // SDP m= port to advertise (public, post-NAT;
                                  // 0 until prepare_media_endpoint ran)
    char     contact_uri[200];    // INVITE Contact = remote target (BYE R-URI)
    char     route[320];          // INVITE Record-Route, echoed as BYE Route
                                  // (Telekom's IMS token URI is ~211 chars;
                                  // truncation produced a malformed Route → 400)
    int64_t  bye_at_us;           // abs. deadline for the next timed dialog
                                  // action (send BYE after a tone, or send the
                                  // delayed 480 while PROCEEDING); 0 = none
    verdict_t verdict;            // cached API result for this call
    char     invite_msg[SIP_INVITE_STORE_CAP];  // stored INVITE so the main
    int      invite_len;          // loop can send the delayed decline (#380)
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

    strbuf_t sb = sb_init(buf, cap);
    sb_appendf(&sb,
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

        sb_appendf(&sb,
            "Authorization: Digest username=\"%s\", realm=\"%s\", "
            "nonce=\"%s\", uri=\"%s\", response=\"%s\", algorithm=%s",
            auth_user, realm,
            c->challenge.nonce, request_uri,
            response, c->challenge.algorithm);

        if (qop[0]) {
            sb_appendf(&sb,
                ", qop=%s, nc=%s, cnonce=\"%s\"",
                qop, nc, cnonce);
        }
        if (c->challenge.opaque[0]) {
            sb_appendf(&sb,
                ", opaque=\"%s\"", c->challenge.opaque);
        }
        sb_appendf(&sb, "\r\n");
    }

    sb_appendf(&sb, "Content-Length: 0\r\n\r\n");
    // A truncated REGISTER would be malformed; report it un-buildable so the
    // caller doesn't put a partial request on the wire.
    return sb.truncated ? -1 : sb.len;
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
            // INFO, not WARN: a single missed response is a routine
            // transient (it usually recovers on the next refresh while the
            // FB binding still covers us). The caller decides whether to
            // surface it — keeping this off WARN/ERROR avoids flooding the
            // web "Protokoll" ring (log_capture.c) on every blip (#402).
            ESP_LOGI(TAG, "no response from registrar within %d ms",
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
    if (tx_len < 0) {
        ESP_LOGE(TAG, "REGISTER exceeds %d-byte buffer", SIP_TX_BUF_SIZE);
        if (err) snprintf(err, err_cap, "REGISTER aborted: request too large");
        result = REGISTER_DEFINITIVE;
        goto cleanup;
    }
    ESP_LOGI(TAG, "→ REGISTER (%d bytes):\n%.*s", tx_len, tx_len, tx);
    int rx_len = sip_send_recv(c, tx, tx_len, rx, SIP_RX_BUF_SIZE);
    if (rx_len < 0) {
        // Don't log here: do_register's contract is to NOT surface the
        // failure itself (see header comment) — that decision belongs to
        // the caller. The auth-retry path below (sip_send_recv at the
        // 401/407 stage) already stays silent; match it so a transient
        // refresh blip doesn't reach the web log (#402).
        if (err) snprintf(err, err_cap,
            "REGISTER: no response from registrar (timeout/transport)");
        result = REGISTER_TRANSIENT;
        goto cleanup;
    }
    ESP_LOGI(TAG, "← %d bytes:\n%.*s", rx_len, rx_len, rx);

    int status = parse_status_code(rx, rx_len);
    ESP_LOGI(TAG, "← %d (%d bytes)", status, rx_len);

    if (status == 200) {
        int granted = parse_register_expires(rx, rx_len, config_device_id());
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
        if (tx_len < 0) {
            ESP_LOGE(TAG, "REGISTER (with auth) exceeds %d-byte buffer",
                     SIP_TX_BUF_SIZE);
            if (err) snprintf(err, err_cap,
                "REGISTER aborted: authenticated request too large");
            result = REGISTER_DEFINITIVE;
            goto cleanup;
        }
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

        // Surface the registrar's actual cause, not just the bare code.
        // The full response is only ESP_LOGI (serial-only); the operator
        // sees the web UI "Protokoll" panel, which mirrors WARN/ERROR. So
        // lift the status-line reason phrase plus any Warning/Retry-After
        // header into the ERROR line — that is what turns "403" into a
        // diagnosis ("Forbidden - Registration limit exceeded; Retry-After:
        // 1800") without anyone attaching a serial console.
        char reason[64] = "", warn[80] = "", retry[24] = "";
        parse_reason_phrase(rx, rx_len, reason, sizeof(reason));
        const char *wh = find_header(rx, rx_len, "Warning");
        if (wh) header_value(wh, rx + rx_len, warn, sizeof(warn));
        const char *rh = find_header(rx, rx_len, "Retry-After");
        if (rh) header_value(rh, rx + rx_len, retry, sizeof(retry));

        char diag[STATS_ERROR_MSG_LEN];
        int m = snprintf(diag, sizeof(diag), "authentication rejected: %d%s%s",
                         status, reason[0] ? " " : "", reason);
        if (retry[0] && m > 0 && m < (int)sizeof(diag))
            m += snprintf(diag + m, sizeof(diag) - m, "; Retry-After: %s", retry);
        if (warn[0] && m > 0 && m < (int)sizeof(diag))
            snprintf(diag + m, sizeof(diag) - m, "; Warning: %s", warn);

        if (err) snprintf(err, err_cap, "%s", diag);
        ESP_LOGE(TAG, "%s", diag);
        result = REGISTER_DEFINITIVE;
        goto cleanup;
    }
    {
        int granted = parse_register_expires(rx, rx_len, config_device_id());
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

// The SIP response builder (echo Via/From/To/Call-ID/CSeq, add Contact +
// Allow + User-Agent + optional SDP) lives in sip_response.c so its buffer
// arithmetic can be unit-tested on the host — see sip_response.h and
// test/test_sip_response.c. send_response() resolves the local identity /
// advertised host+port and, for stateless responses (no override tag),
// mints a fresh To-tag, then hands everything to sip_response_build().
static void send_response(sip_ctx_t *c, const struct sockaddr_in *peer,
                          const char *req, int req_len,
                          int status, const char *reason,
                          const char *our_tag_override,
                          const char *body)
{
    char tag_buf[20];
    const char *our_tag;
    if (our_tag_override && our_tag_override[0]) {
        our_tag = our_tag_override;
    } else {
        random_hex(tag_buf, 16);
        our_tag = tag_buf;
    }

    char *tx = malloc(SIP_TX_BUF_SIZE);
    if (!tx) {
        ESP_LOGE(TAG, "malloc failed for response buffer");
        return;
    }
    int tx_len = sip_response_build(req, req_len, status, reason,
                                    our_tag, body,
                                    current_identity_user(),
                                    advertised_host(c), advertised_port(),
                                    tx, SIP_TX_BUF_SIZE);
    if (tx_len < 0) {
        // The request's headers were too large to echo into a complete
        // response. Send nothing rather than a partial, invalid message —
        // only reachable from a malformed/oversized request.
        ESP_LOGW(TAG, "dropping %d %s: response exceeds %d-byte buffer",
                 status, reason, SIP_TX_BUF_SIZE);
        free(tx);
        return;
    }
    ESP_LOGI(TAG, "→ %d %s (%d bytes):\n%.*s", status, reason, tx_len, tx_len, tx);
    sip_transport_send_to(c->transport, peer, tx, tx_len);
    free(tx);
}


// Write a minimal SDP body announcing PCMA audio on our local RTP port.
// The RTP task binds that port just-in-time and streams a tone there.
// When the dialog negotiated SRTP (d->srtp_tx), the media line uses the
// secure RTP/SAVP profile and carries our SDES key in an a=crypto line
// echoing the offer's crypto tag; otherwise it stays plain RTP/AVP.
static int build_sdp_body(const char *fallback_ip, const dialog_t *d,
                          char *out, int cap)
{
    // Advertise the endpoint prepare_media_endpoint() resolved (public,
    // STUN-mapped). Fall back to the signalling IP / local port if it was
    // never set (defensive — the answer path always sets it first).
    const char *ip = (d && d->media_ip[0]) ? d->media_ip : fallback_ip;
    int port       = (d && d->media_port > 0) ? d->media_port : config_rtp_port();

    strbuf_t sb = sb_init(out, cap);
    sb_appendf(&sb,
        "v=0\r\n"
        "o=phoneblock-dongle 0 0 IN IP4 %s\r\n"
        "s=-\r\n"
        "c=IN IP4 %s\r\n"
        "t=0 0\r\n"
        "m=audio %d RTP/%s 8\r\n"
        "a=rtpmap:8 PCMA/8000\r\n",
        ip, ip, port,
        (d && d->srtp_tx) ? "SAVP" : "AVP");

    if (d && d->srtp_tx) {
        // base64 of 30 bytes is 40 chars + NUL.
        char key_b64[48];
        size_t b64_len = 0;
        if (mbedtls_base64_encode((unsigned char *)key_b64, sizeof(key_b64),
                                  &b64_len, d->srtp_tx_key,
                                  RTP_SRTP_KEY_LEN) == 0) {
            sb_appendf(&sb,
                "a=crypto:%d AES_CM_128_HMAC_SHA1_80 inline:%.*s\r\n",
                d->srtp_tag, (int)b64_len, key_b64);
        }
    }

    sb_appendf(&sb, "a=sendrecv\r\n");
    return sb.len;
}

// Decide the IP:port to advertise as our media endpoint in the SDP answer,
// storing it in the dialog for build_sdp_body (and any later retransmit).
//
// The signalling layer already knows our public IP (REGISTER "received="),
// but not the public UDP *port* our RTP is mapped to: we bind a fixed local
// port and used to advertise it verbatim, which is only correct when the
// router preserves that port across NAT. So we ask STUN — on the very RTP
// socket the announcement will stream from — for the post-NAT endpoint and
// advertise that, falling back to the signalling IP + local port when STUN
// yields nothing.
//
// STUN only applies to direct-provider registration behind NAT, where a
// preset fills in sip_stun. Without a configured server (the Fritz!Box case)
// there is no NAT to traverse and we advertise the local endpoint directly.
static void prepare_media_endpoint(sip_ctx_t *c, dialog_t *d)
{
    snprintf(d->media_ip, sizeof(d->media_ip), "%s", advertised_host(c));
    d->media_port = config_rtp_port();

    // No STUN server configured (the Fritz!Box-on-the-LAN default): the dongle
    // registers on the local network and RTP flows straight to the box, so the
    // local endpoint is exactly right — no NAT to traverse. Advertise it
    // quietly; a WARN here would only alarm those users on the field panel.
    if (!config_stun_server()[0]) {
        ESP_LOGI(TAG, "no STUN configured — advertising local RTP endpoint "
                      "%s:%d", d->media_ip, d->media_port);
        return;
    }

    // Under endpoint-dependent (symmetric) NAT the STUN-learned port wouldn't
    // match the one the media gateway sees, so advertising it is pointless —
    // stay on the local port. The boot-time probe already warned that only a
    // port-identical RTP forward (or a VoIP router) fixes audio here.
    if (rtp_nat_mapping() == NAT_MAP_ENDPOINT_DEPENDENT) {
        ESP_LOGW(TAG, "symmetric NAT — advertising local RTP endpoint %s:%d "
                      "(audio needs a port-identical RTP port-forward)",
                 d->media_ip, d->media_port);
        return;
    }

    char ip[INET_ADDRSTRLEN];
    int  port = 0;
    if (rtp_stun_map(config_stun_server(), ip, sizeof(ip), &port) && port > 0) {
        ESP_LOGI(TAG, "media endpoint via STUN: %s:%d (local was %s:%d)",
                 ip, port, d->media_ip, d->media_port);
        snprintf(d->media_ip, sizeof(d->media_ip), "%s", ip);
        d->media_port = port;
    } else {
        ESP_LOGW(TAG, "STUN gave no mapping — advertising local RTP endpoint "
                      "%s:%d (only correct if the router preserves the port)",
                 d->media_ip, d->media_port);
    }
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

    // Keep a copy of the INVITE so the main loop can build a delayed decline
    // response (480) after the PROCEEDING ring window (#380).
    d->invite_len = req_len < SIP_INVITE_STORE_CAP ? req_len : SIP_INVITE_STORE_CAP;
    memcpy(d->invite_msg, req, d->invite_len);

    const char *end = req + req_len;

    const char *hdr = find_header(req, req_len, "From");
    if (hdr) {
        const char *eol = hdr;
        while (eol < end && *eol != '\r' && *eol != '\n') eol++;
        int val_len = (int)(eol - hdr);
        parse_uri(hdr, val_len, d->remote_uri, sizeof(d->remote_uri));
        parse_tag(hdr, val_len, d->from_tag, sizeof(d->from_tag));
    }

    // Remote target + route set for in-dialog requests (our BYE). Telekom's
    // IMS routes in-dialog requests by the INVITE Contact (which carries an
    // opaque "mavodi-…" routing token) and the Record-Route; addressing the
    // BYE to the From AOR instead returns 481 Call Leg Does Not Exist and a
    // ~10 s delayed teardown. Falls back to the From URI when absent (e.g.
    // the Fritz!Box, where Contact == AOR works either way).
    d->contact_uri[0] = '\0';
    const char *ct = find_header(req, req_len, "Contact");
    if (ct) {
        const char *eol = ct;
        while (eol < end && *eol != '\r' && *eol != '\n') eol++;
        parse_uri(ct, (int)(eol - ct), d->contact_uri, sizeof(d->contact_uri));
    }
    // Record-Route (single hop for Telekom). Stored verbatim and echoed as a
    // Route header on our BYE for loose routing (the entry carries ;lr).
    // A value too long for the buffer must NOT be stored truncated — a
    // half a URI is a malformed Route header and gets the BYE rejected
    // with 400; better to send no Route (481, delayed teardown) than that.
    d->route[0] = '\0';
    const char *rr = find_header(req, req_len, "Record-Route");
    if (rr) {
        const char *eol = rr;
        while (eol < end && *eol != '\r' && *eol != '\n') eol++;
        if ((size_t)(eol - rr) < sizeof(d->route)) {
            header_value(rr, eol, d->route, sizeof(d->route));
        } else {
            ESP_LOGW(TAG, "Record-Route too long (%d) — BYE sent without Route",
                     (int)(eol - rr));
        }
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

    // SRTP negotiation. Telekom (and other IMS networks) offer RTP/SAVP
    // with SDES a=crypto keys and reject a plain RTP/AVP answer. Whenever
    // the INVITE offers a crypto suite we support we mirror it: answer
    // RTP/SAVP and generate our own master key/salt to encrypt the
    // announcement. There is no good reason to answer plaintext to a
    // crypto offer (it just gets the dialog torn down), so this is not
    // gated on a config knob — a plain RTP/AVP offer (e.g. the Fritz!Box
    // on the LAN) simply carries no crypto and is answered RTP/AVP.
    d->srtp_tx  = false;
    d->srtp_tag = 0;
    char remote_key_b64[64];
    int crypto_tag = parse_sdp_crypto(req, req_len,
                                      remote_key_b64, sizeof(remote_key_b64));
    if (crypto_tag > 0) {
        // Generate a fresh 30-byte SDES master key + salt.
        esp_fill_random(d->srtp_tx_key, sizeof(d->srtp_tx_key));
        d->srtp_tx  = true;
        d->srtp_tag = crypto_tag;
        ESP_LOGI(TAG, "SRTP offered (crypto tag %d) → answering RTP/SAVP", crypto_tag);
    } else if (parse_sdp_audio_savp(req, req_len)) {
        ESP_LOGW(TAG, "remote offers RTP/SAVP but no supported crypto suite "
                      "— answering plain RTP (media will likely be rejected)");
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

    // In-dialog R-URI = the remote target, i.e. the INVITE's Contact URI
    // (carries the IMS routing token). Fall back to the From AOR when the
    // INVITE had no Contact. For the AOR fallback on non-UDP transports we
    // append ";transport=…" so the peer doesn't drop to UDP; the Contact
    // path is loose-routed via the Route header below, so it's used as-is.
    const char *uri_param = sip_transport_uri_param(c->transport);
    char ruri[200];
    if (d->contact_uri[0]) {
        snprintf(ruri, sizeof(ruri), "%s", d->contact_uri);
    } else if (strcmp(uri_param, "udp") == 0) {
        snprintf(ruri, sizeof(ruri), "%s", d->remote_uri);
    } else {
        snprintf(ruri, sizeof(ruri), "%s;transport=%s",
                 d->remote_uri, uri_param);
    }

    char route_hdr[sizeof(d->route) + 16];
    if (d->route[0]) {
        snprintf(route_hdr, sizeof(route_hdr), "Route: %s\r\n", d->route);
    } else {
        route_hdr[0] = '\0';
    }

    return snprintf(buf, cap,
        "BYE %s SIP/2.0\r\n"
        "Via: SIP/2.0/%s %s:%d;branch=z9hG4bK%s;rport\r\n"
        "%s"
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
        route_hdr,
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
    // Arm a teardown timeout: if the 200 to this BYE is lost, the main loop
    // resets the dialog to IDLE instead of lingering in BYE_SENT. A received
    // 200 (handle_incoming) supersedes this by memset-ing the dialog. The
    // preempt path in handle_invite also wipes it when a new call arrives.
    d->bye_at_us = esp_timer_get_time() + SIP_TEARDOWN_TIMEOUT_US;
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
// Both cases return VERDICT_LEGITIMATE so the dongle keeps ringing (180) and
// then declines, letting the Fritz!Box continue its normal ring routing (#380).
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

    // Local-blocklist fast path: the daily-synced binary files cover the
    // common case (number on the community list, or on the user's own
    // overrides) without an HTTPS round-trip. The API call only runs when
    // the local lookup is UNKNOWN — either no file synced yet, or the
    // number is genuinely in no list (in which case the API will also
    // refresh server-side LASTPING counters, so we keep that path live).
    pb_check_result_t result;
    memset(&result, 0, sizeof(result));
    verdict_t v;
    const char *digits = (number[0] == '+') ? number + 1 : number;
    // Skip the local files entirely when the cache is disabled — every
    // call then resolves against the live server API.
    bool wildcard = false, personal = false;
    blocklist_verdict_t local = config_blocklist_enabled()
        ? blocklist_sync_check_ex(digits, config_blocklist_wildcards(),
                                  &wildcard, &personal)
        : BLOCKLIST_UNKNOWN;
    if (local == BLOCKLIST_SPAM) {
        result.verdict  = VERDICT_SPAM;
        result.wildcard = wildcard;
        // A personal-list SPAM hit is the user's own blacklist; a
        // community-list hit is generic blocklist spam. Neither carries
        // per-number vote counts, so the label uses the (Nummer)/(Bereich)
        // qualifier instead — this is what fixed the misleading
        // "SPAM (0 Stimmen)" the local cache used to log.
        result.assessment = personal ? PB_ASSESS_BLACKLIST : PB_ASSESS_SPAM_LIST;
        v = VERDICT_SPAM;
        ESP_LOGI(TAG, "local blocklist → SPAM (%s, %s) for %s",
                 personal ? "personal" : "community",
                 wildcard ? "wildcard" : "exact", number);
    } else if (local == BLOCKLIST_LEGIT) {
        result.verdict    = VERDICT_LEGITIMATE;
        // On a whitelist (personal or the general community list) the
        // number is genuinely legitimate — unlike an un-rated number,
        // which phoneblock_check() leaves as PB_ASSESS_UNKNOWN.
        result.assessment = PB_ASSESS_LEGITIMATE;
        v = VERDICT_LEGITIMATE;
        ESP_LOGI(TAG, "local blocklist → LEGIT (%s) for %s",
                 personal ? "personal" : "community", number);
    } else {
        v = phoneblock_check(number, &result, NULL);
    }
    stats_record_call_checked(number, display, &result);

    // Fair-use contribution required by /api/check-prefix: when our
    // privacy-preserving lookup hides which number we queried, the
    // server cannot keep tailored compact blocklists current on its
    // own. POST the plaintext number back on a positive match so the
    // call counter / LASTPING are refreshed.
    //
    // Enqueued for an async worker rather than POSTed synchronously:
    // the second TLS handshake (300–600 ms cert-verify + RTT) sat on
    // the critical path between verdict and the 200 OK, exactly
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
            char sdp[384];
            build_sdp_body(advertised_host(c), d, sdp, sizeof(sdp));
            send_response(c, from, req, req_len, 200, "OK",
                          d->our_tag, sdp);
            break;
        }
        case DIALOG_PROCEEDING:
            send_response(c, from, req, req_len, 180, "Ringing",
                          d->our_tag, NULL);
            break;
        case DIALOG_REJECTED:
            // Must match the decline sent from the main loop (480), so a
            // retransmitted INVITE doesn't see two different final responses.
            send_response(c, from, req, req_len, 480, "Temporarily Unavailable",
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

    // A new INVITE (different Call-ID — the retransmit case returned above)
    // always wins: the dongle screens the newest call, so no incoming call is
    // silently ignored because an earlier dialog got stuck. Tear the old slot
    // down and fall through to handle the new caller.
    //
    // Only an already-answered dialog (2xx sent) needs a BYE to close it
    // properly; STREAMING additionally aborts the tone. Every other state is
    // either unestablished/deciding (TRYING), kept deliberately ringing
    // (PROCEEDING, #380), already declined (REJECTED, awaiting an ACK that may
    // never come), or already being torn down (BYE_SENT, awaiting a 200 that
    // may be lost) — nothing to send, just reclaim the slot. Any straggler
    // ACK/200/BYE for the old Call-ID Call-ID-mismatches in handle_incoming
    // and is discarded.
    //
    // We deliberately do NOT reply 486 Busy Here for the old call: a "busy"
    // from us makes the Fritz!Box drop the Fritz!Fon app for that call (#380).
    // Leaving the old branch without a final response keeps the real phones
    // ringing; the box CANCELs it when that call ends (handle_cancel still
    // 200-OKs the CANCEL).
    if (d->state != DIALOG_IDLE) {
        if (d->state == DIALOG_STREAMING) {
            rtp_request_abort();
            send_bye(c);
        } else if (d->state == DIALOG_ANSWERED) {
            send_bye(c);
        }
        ESP_LOGI(TAG, "second INVITE during state %d → preempt, screen new caller",
                 d->state);
        memset(d, 0, sizeof(*d));
    }

    capture_dialog(c, req, req_len, from);
    d->state = DIALOG_TRYING;
    ESP_LOGI(TAG, "INVITE accepted, Call-ID=%s, checking caller…", d->call_id);

    // Stop Fritz!Box retransmits immediately, and signal "ringing" so the
    // Fritz!Box starts ringing the other phones (incl. waking the Fritz!Fon
    // app by push) while we check the caller. A fast final response here
    // would make the box drop the app from the call — issue #380.
    send_response(c, from, req, req_len, 100, "Trying", d->our_tag, NULL);
    send_response(c, from, req, req_len, 180, "Ringing", d->our_tag, NULL);
    d->state = DIALOG_PROCEEDING;
    d->bye_at_us = esp_timer_get_time() + SIP_DECLINE_DELAY_US;

    // Synchronous API check. Budget is ~500 ms–1 s; Fritz!Box waits longer
    // than that. Later this can move to a worker task if needed.
    d->verdict = check_invite_caller(req, req_len);

    if (d->verdict == VERDICT_SPAM) {
        char sdp[384];
        // Resolve the public media endpoint (STUN) before answering, so the
        // SDP carries the post-NAT IP:port the announcement is sent from.
        prepare_media_endpoint(c, d);
        build_sdp_body(advertised_host(c), d, sdp, sizeof(sdp));
        send_response(c, from, req, req_len, 200, "OK", d->our_tag, sdp);
        d->state = DIALOG_ANSWERED;
        // Arm an ACK-timeout safety net (SIP_ACK_TIMEOUT_US): a received ACK
        // (handle_ack) supersedes it by moving to STREAMING and setting the
        // real post-tone BYE deadline; if no ACK ever arrives the main loop
        // BYEs and frees the slot instead of wedging it forever.
        d->bye_at_us = esp_timer_get_time() + SIP_ACK_TIMEOUT_US;
        ESP_LOGI(TAG, "SPAM → 200 OK sent, waiting for ACK to hang up");
    } else {
        // VERDICT_LEGITIMATE or VERDICT_ERROR → don't take the call, but don't
        // decline immediately either. Experiment #380 showed the Fritz!Box only
        // keeps ringing the Fritz!Fon app if our branch stays "ringing" for a
        // short while before declining: a 180 *immediately* followed by 480
        // (verified with a known contact, where the verdict short-circuits with
        // no API call, so 180/480 go out back-to-back) leaves the app silent;
        // a 180 held for ~3 s before the 480 lets the app ring. So we keep the
        // 180 (sent above) and let the main loop send the 480 only after
        // SIP_DECLINE_DELAY_US has elapsed.
        ESP_LOGI(TAG, "%s → 180 Ringing, declining in %lld ms",
                 d->verdict == VERDICT_LEGITIMATE ? "LEGITIMATE" : "ERROR",
                 (long long)(SIP_DECLINE_DELAY_US / 1000));
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
            rtp_srtp_tx_t srtp = { .enabled = d->srtp_tx };
            if (d->srtp_tx) {
                memcpy(srtp.key, d->srtp_tx_key, sizeof(srtp.key));
            }
            rtp_play_audio(&d->rtp_dest, &src, &srtp);   // task owns src now, closes it
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
        // Non-spam: we declined with 480, ACK confirms, dialog done.
        ESP_LOGI(TAG, "ACK received after 480 → dialog closed");
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
    if ((d->state == DIALOG_TRYING || d->state == DIALOG_PROCEEDING)
        && same_call_id(d->call_id, cid)) {
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

    bool is_response = (len >= 7 && strncmp(pkt, "SIP/2.0", 7) == 0);
    char method[16];
    int  method_len  = is_response ? 0 : parse_method(pkt, len, method, sizeof(method));

    // A SIP CRLF keep-alive carries no method and no SIP/2.0 status line.
    // Handle it here — before the generic packet dump below — so it is
    // logged exactly once (one distinct, greppable marker) instead of also
    // being dumped (with its blank CRLFs) by that line. RFC 5626 §4.4.1: a
    // double-CRLF "ping" (>=4 bytes) is answered with a single-CRLF "pong"
    // (2 bytes). Telekom probes us with pings every ~60 s and resets the
    // flow if we never answer. A lone CRLF (2 bytes) is a pong answering our
    // own ping — log it (this is how we see that Telekom really does answer)
    // but don't reply, that would loop.
    if (!is_response && method_len == 0) {
        if (len >= 4) {
            ESP_LOGI(TAG, "← SIP keepalive ping (%d bytes) from %s:%d",
                     len, from_ip, ntohs(from->sin_port));
            sip_transport_send(c->transport, "\r\n", 2);
            ESP_LOGI(TAG, "→ SIP keepalive pong (2 bytes)");
        } else {
            ESP_LOGI(TAG, "← SIP keepalive pong (%d bytes) from %s:%d",
                     len, from_ip, ntohs(from->sin_port));
        }
        return;
    }

    ESP_LOGI(TAG, "← from %s:%d  %d bytes:\n%.*s",
             from_ip, ntohs(from->sin_port),
             len, len, pkt);

    // Handle responses to our own out-of-dialog requests (BYE from us).
    if (is_response) {
        int status = parse_status_code(pkt, len);
        if (c->dialog.state == DIALOG_BYE_SENT) {
            ESP_LOGI(TAG, "← %d on BYE → dialog closed", status);
            memset(&c->dialog, 0, sizeof(c->dialog));
        } else {
            ESP_LOGW(TAG, "ignoring stray response %d", status);
        }
        return;
    }

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
        ESP_LOGW(TAG, "method \"%s\" not implemented yet", method);
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

// Force a fresh transport connection (new source port, new TLS session)
// before the next REGISTER. Used when a *previously established*
// registration is suddenly refused with a definitive error: a negative
// SIP response never tears the connection down on its own (it is a
// successfully-received response), so the dongle would otherwise re-REGISTER
// forever over the very same connection. If the registrar is wedged on
// soft-state bound to that connection — observed on Telekom hybrid lines
// (#423), where a byte-identical REGISTER that earned a 200 starts earning
// 403 on a stable connection — only a clean connection can reset it, the
// way a real phone recovers by re-registering on a fresh socket.
//
// Reuses the configured transport kind. A NULL reopen result keeps the old
// handle so the caller never dereferences NULL; the next retry then runs
// over the old connection (or its transparent reconnect) as before.
static void reopen_transport(sip_ctx_t *c)
{
    char host[64];
    int  port;
    dial_destination(host, sizeof(host), &port);
    const char *outbound = config_sip_outbound();
    const char *sni = (outbound && outbound[0]) ? host : config_sip_host();

    sip_transport_t *nt = sip_transport_open(config_sip_transport(),
                                             host, port, sni,
                                             config_sip_local_port());
    if (nt) {
        sip_transport_close(c->transport);
        c->transport = nt;
        ESP_LOGW(TAG, "reopened SIP connection after definitive rejection");
    } else {
        ESP_LOGW(TAG, "reopen after definitive rejection failed — "
                      "keeping existing connection");
    }
}

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
    // SRTP is negotiated per call from the INVITE's a=crypto offer (see
    // capture_dialog): the dongle answers RTP/SAVP and encrypts the
    // announcement whenever the caller offers a supported suite, unless
    // sip_srtp is explicitly "off". There is nothing to warn about at
    // registration time — the media profile depends on each offer.
    const int retry_delay_s = 30;
    char *rx = NULL;
    int64_t refresh_at_us = 0;  // absolute deadline for next REGISTER (microseconds)
    int64_t keepalive_at_us = 0; // absolute deadline for next NAT keepalive ping

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
    // First keepalive one interval out; it self-reschedules in the loop and
    // stays active across reconnects/refreshes regardless of register state.
    keepalive_at_us = esp_timer_get_time() + (int64_t)SIP_KEEPALIVE_INTERVAL_S * 1000000LL;

    rx = malloc(SIP_RX_BUF_SIZE);
    if (!rx) {
        ESP_LOGE(TAG, "malloc rx buffer failed — aborting SIP task");
        s_sip_task = NULL;
        vTaskDelete(NULL);
        return;
    }

    // One-shot NAT mapping probe for direct-provider setups (a STUN server is
    // configured only by a provider preset, never for a Fritz!Box on the LAN).
    // Run here — after the initial registration attempt put the network up, but
    // before the watchdog is armed below — so its ~1 s of STUN exchanges can't
    // trip it. The result gates per-call STUN (see prepare_media_endpoint). A
    // later provider switch via reload keeps this boot result until reboot; the
    // NAT type is a property of the router, not the provider.
    if (config_stun_server()[0]) {
        rtp_probe_nat_mapping(config_stun_server());
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
            // Defer the idle keepalive (see reconnect path): this REGISTER
            // is fresh traffic, no need to ping right after it.
            keepalive_at_us = esp_timer_get_time()
                            + (int64_t)SIP_KEEPALIVE_INTERVAL_S * 1000000LL;
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
            // A REGISTER is traffic; defer the idle keepalive a full
            // interval so we never ping a connection we just used — least
            // of all a freshly reconnected one, where an immediate ping
            // only draws a lone-CRLF pong with nothing to keep alive.
            keepalive_at_us = esp_timer_get_time()
                            + (int64_t)SIP_KEEPALIVE_INTERVAL_S * 1000000LL;
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
                } else if (ctx.dialog.state == DIALOG_ANSWERED) {
                    // ACK for our 200 OK never arrived (SIP_ACK_TIMEOUT_US) —
                    // don't wedge the single dialog slot; BYE and free it so
                    // the next call can be screened.
                    ESP_LOGW(TAG, "ACK timeout in ANSWERED → BYE, freeing dialog");
                    send_bye(&ctx);
                } else if (ctx.dialog.state == DIALOG_PROCEEDING) {
                    // #380: the other phones have had SIP_DECLINE_DELAY_US to
                    // start ringing; now decline with 480 (built from the
                    // stored INVITE) and observe whether they keep ringing.
                    ESP_LOGI(TAG, "decline delay elapsed → 480 Temporarily Unavailable");
                    send_response(&ctx, &ctx.dialog.peer,
                                  ctx.dialog.invite_msg, ctx.dialog.invite_len,
                                  480, "Temporarily Unavailable",
                                  ctx.dialog.our_tag, NULL);
                    ctx.dialog.state = DIALOG_REJECTED;
                    // Wait for the ACK to our 480, but not forever (see
                    // SIP_TEARDOWN_TIMEOUT_US) — reset to IDLE if it's lost.
                    ctx.dialog.bye_at_us = now + SIP_TEARDOWN_TIMEOUT_US;
                } else if (ctx.dialog.state == DIALOG_REJECTED) {
                    // ACK to our 480 never arrived — return to rest.
                    ESP_LOGI(TAG, "ACK timeout after 480 → dialog reset to idle");
                    memset(&ctx.dialog, 0, sizeof(ctx.dialog));
                } else if (ctx.dialog.state == DIALOG_BYE_SENT) {
                    // 200 to our BYE never arrived — return to rest.
                    ESP_LOGI(TAG, "200 timeout after BYE → dialog reset to idle");
                    memset(&ctx.dialog, 0, sizeof(ctx.dialog));
                }
                continue;
            }
            // NAT/connection keepalive: a double-CRLF ping keeps the
            // long-lived stream connection and the Fritz!Box NAT mapping warm
            // between the (expires/2) REGISTER refreshes, so the idle TLS
            // connection isn't silently dropped and only noticed at the next
            // refresh. Skipped when a refresh is already due (the REGISTER
            // itself is traffic). A failed send just means the connection is
            // already gone — harmless; the refresh/recv path reconnects.
            if (now >= keepalive_at_us) {
                if (now < refresh_at_us) {
                    sip_transport_send(ctx.transport, "\r\n\r\n", 4);
                    ESP_LOGI(TAG, "→ SIP keepalive ping (4 bytes)");
                }
                keepalive_at_us = now + (int64_t)SIP_KEEPALIVE_INTERVAL_S * 1000000LL;
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
            // Defer the idle keepalive (see reconnect path): the refresh
            // REGISTER is traffic, so the next ping is a full interval out.
            keepalive_at_us = now + (int64_t)SIP_KEEPALIVE_INTERVAL_S * 1000000LL;
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
                // INFO, not WARN: while the FB binding still covers us this
                // is a self-healing retry, not a problem the user needs to
                // see. If the binding does eventually lapse, the definitive
                // branch below logs the stashed reason at ERROR as a single
                // dashboard entry. Keeping the covered case off WARN/ERROR
                // stops the web "Protokoll" ring filling with blips (#402).
                ESP_LOGI(TAG, "re-REGISTER transient (%s) — binding valid "
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
                bool was_registered = s_registered;
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

                // A definitive rejection of an *established* binding is the
                // #423 signature: the connection is healthy at the TCP/TLS
                // layer (no transport error fired, so nothing else reopens
                // it), yet the registrar refuses a REGISTER that succeeded
                // minutes earlier. Force a clean connection before the next
                // attempt so it gets a fresh start instead of re-presenting
                // the same wedged context every 30 s. Gated on was_registered
                // so a genuine wrong-credentials setup (never registered)
                // doesn't churn the connection, and on a connection-oriented
                // transport (UDP has no connection state to reset).
                if (r == REGISTER_DEFINITIVE && was_registered
                        && strcasecmp(canonical_transport(), "udp") != 0) {
                    reopen_transport(&ctx);
                }
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
