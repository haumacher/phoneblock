#ifndef ESP_PLATFORM
#define _POSIX_C_SOURCE 200809L
#endif

#include "rtp.h"

#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <math.h>

#ifdef ESP_PLATFORM
#include "lwip/sockets.h"
#include "lwip/netdb.h"
#else
#include <arpa/inet.h>
#include <netdb.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <unistd.h>
#endif

#include "audio.h"
#include "config.h"
#include "dialog.h"
#include "dialog_store.h"
#include "platform.h"
#include "rtp_rx.h"
#include "vad.h"

#include "srtp.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static const char *TAG = "rtp";

static const char *ipv4_to_string(const struct in_addr *address,
                                  char *out, size_t cap)
{
#ifdef ESP_PLATFORM
    return inet_ntoa_r(*address, out, (int)cap);
#else
    return inet_ntop(AF_INET, address, out, (socklen_t)cap);
#endif
}

// Singleton RTP socket, created once and reused for every call: the STUN
// probe (run from the SIP task before we answer) and the streaming task
// (run after ACK) must share one local port so the public mapping STUN
// learns is the one the announcement is then sent from. -1 = not yet open.
static int s_rtp_sock = -1;

int rtp_socket_ensure(void)
{
    if (s_rtp_sock >= 0) return s_rtp_sock;

    int sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (sock < 0) {
        pb_log_err(TAG, "RTP socket: %s", strerror(errno));
        return -1;
    }
    int reuse = 1;
    setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &reuse, sizeof(reuse));

    int rtp_port = config_rtp_port();
    struct sockaddr_in local = {
        .sin_family      = AF_INET,
        .sin_addr.s_addr = htonl(INADDR_ANY),
        .sin_port        = htons(rtp_port),
    };
    if (bind(sock, (struct sockaddr *)&local, sizeof(local)) < 0) {
        pb_log_err(TAG, "bind RTP :%d: %s", rtp_port, strerror(errno));
        close(sock);
        return -1;
    }
    s_rtp_sock = sock;
    return sock;
}

#define STUN_MAGIC_COOKIE 0x2112A442u

// Resolve a STUN server spec "host[:port]" (default port 3478) to an IPv4
// socket address.
static bool stun_resolve(const char *server, struct sockaddr_in *out)
{
    if (!server || !server[0]) return false;

    char host[128];
    snprintf(host, sizeof(host), "%s", server);
    int port = 3478;
    char *colon = strrchr(host, ':');
    if (colon) {
        *colon = '\0';
        int p = atoi(colon + 1);
        if (p > 0) port = p;
    }

    char port_str[12];
    snprintf(port_str, sizeof(port_str), "%d", port);
    struct addrinfo hints = { .ai_family = AF_INET, .ai_socktype = SOCK_DGRAM };
    struct addrinfo *res = NULL;
    if (getaddrinfo(host, port_str, &hints, &res) != 0 || !res) {
        pb_log_warn(TAG, "STUN: DNS lookup of %s failed", host);
        return false;
    }
    memcpy(out, res->ai_addr, sizeof(*out));
    freeaddrinfo(res);
    return true;
}

// Send a STUN Binding Request to `srv` on `sock` and parse the public
// (XOR-)MAPPED-ADDRESS from the reply. Returns false on timeout / bad
// response. Two short attempts — we must not stall the 200 OK for long
// when called on the answer path (the INVITE transaction is waiting).
static bool stun_exchange(int sock, const struct sockaddr_in *srv,
                          char *ip_out, int ip_cap, int *port_out)
{
    // 20-byte header, no attributes; bytes 4..7 magic cookie, 8..19 a random
    // transaction id, both matched in the reply so a stray packet on the RTP
    // port can't be mistaken for the response.
    uint8_t req[20] = { 0x00, 0x01, 0x00, 0x00,
                        0x21, 0x12, 0xA4, 0x42 };
    for (int i = 8; i < 20; i++) req[i] = (uint8_t)pb_random_u32();

    for (int attempt = 0; attempt < 2; attempt++) {
        if (sendto(sock, req, sizeof(req), 0,
                   (struct sockaddr *)srv, sizeof(*srv)) < 0) {
            pb_log_warn(TAG, "STUN: sendto failed: %s", strerror(errno));
            continue;
        }

        fd_set rf;
        FD_ZERO(&rf);
        FD_SET(sock, &rf);
        struct timeval tv = { .tv_sec = 0, .tv_usec = 250000 };
        if (select(sock + 1, &rf, NULL, NULL, &tv) <= 0) continue;

        uint8_t resp[256];
        struct sockaddr_in from;
        socklen_t fl = sizeof(from);
        int n = recvfrom(sock, resp, sizeof(resp), 0,
                         (struct sockaddr *)&from, &fl);
        // Binding Success Response (0x0101) echoing our cookie + txid.
        if (n < 20 || resp[0] != 0x01 || resp[1] != 0x01) continue;
        if (memcmp(resp + 4, req + 4, 16) != 0) continue;

        int msg_len = (resp[2] << 8) | resp[3];
        int end = 20 + msg_len;
        if (end > n) end = n;
        for (int pos = 20; pos + 4 <= end; ) {
            int atype = (resp[pos] << 8) | resp[pos + 1];
            int alen  = (resp[pos + 2] << 8) | resp[pos + 3];
            int aval  = pos + 4;
            if (aval + alen > end) break;
            // (XOR-)MAPPED-ADDRESS, IPv4 (family byte at aval+1 == 0x01).
            if ((atype == 0x0020 || atype == 0x0001)
                && alen >= 8 && resp[aval + 1] == 0x01) {
                uint16_t xport = (resp[aval + 2] << 8) | resp[aval + 3];
                uint32_t xaddr = ((uint32_t)resp[aval + 4] << 24)
                               | ((uint32_t)resp[aval + 5] << 16)
                               | ((uint32_t)resp[aval + 6] << 8)
                               |  (uint32_t)resp[aval + 7];
                if (atype == 0x0020) {          // XOR-MAPPED: undo the cookie
                    xport ^= (STUN_MAGIC_COOKIE >> 16);
                    xaddr ^= STUN_MAGIC_COOKIE;
                }
                struct in_addr a = { .s_addr = htonl(xaddr) };
                ipv4_to_string(&a, ip_out, (size_t)ip_cap);
                *port_out = xport;
                return true;
            }
            // Attributes are padded to a 4-byte boundary.
            pos = aval + alen + ((alen & 3) ? (4 - (alen & 3)) : 0);
        }
    }
    return false;
}

bool rtp_stun_map(const char *stun_server, char *ip_out, int ip_cap,
                  int *port_out)
{
    int sock = rtp_socket_ensure();
    if (sock < 0) return false;
    struct sockaddr_in srv;
    if (!stun_resolve(stun_server, &srv)) return false;
    if (!stun_exchange(sock, &srv, ip_out, ip_cap, port_out)) {
        pb_log_warn(TAG, "STUN: no usable response from %s", stun_server);
        return false;
    }
    pb_log_info(TAG, "STUN: %s → public %s:%d", stun_server, ip_out, *port_out);
    return true;
}

// Built-in STUN servers used (besides the configured one) to probe the NAT
// mapping behaviour. Only their reachability and distinct IPs matter, not
// which provider runs them.
static const char *STUN_FALLBACKS[] = {
    "stun.t-online.de:3478",
    "stun.1und1.de:3478",
    "stun.sipgate.net:10000",
};

static nat_mapping_t s_nat_mapping = NAT_MAP_UNKNOWN;

nat_mapping_t rtp_nat_mapping(void) { return s_nat_mapping; }

const char *rtp_nat_mapping_str(void)
{
    switch (s_nat_mapping) {
        case NAT_MAP_ENDPOINT_INDEPENDENT: return "endpoint-independent";
        case NAT_MAP_ENDPOINT_DEPENDENT:   return "endpoint-dependent";
        default:                           return "unknown";
    }
}

nat_mapping_t rtp_probe_nat_mapping(const char *primary_stun)
{
    int sock = rtp_socket_ensure();
    if (sock < 0) { s_nat_mapping = NAT_MAP_UNKNOWN; return s_nat_mapping; }

    // Candidate list: the configured server first, then the built-in ones.
    #define N_STUN_FALLBACKS (sizeof(STUN_FALLBACKS) / sizeof(STUN_FALLBACKS[0]))
    const char *cands[1 + N_STUN_FALLBACKS];
    int nc = 0;
    if (primary_stun && primary_stun[0]) cands[nc++] = primary_stun;
    for (size_t i = 0; i < N_STUN_FALLBACKS; i++) cands[nc++] = STUN_FALLBACKS[i];
    #undef N_STUN_FALLBACKS

    // Query servers until two at *different* IPs answered, then compare the
    // mapped ports: same external port for both → endpoint-independent.
    uint32_t first_ip   = 0;
    int      first_port = -1;
    for (int i = 0; i < nc; i++) {
        struct sockaddr_in srv;
        if (!stun_resolve(cands[i], &srv)) continue;
        if (first_port >= 0 && srv.sin_addr.s_addr == first_ip) continue;

        char mip[INET_ADDRSTRLEN];
        int  mport = 0;
        if (!stun_exchange(sock, &srv, mip, sizeof(mip), &mport)) continue;

        if (first_port < 0) {
            first_ip   = srv.sin_addr.s_addr;
            first_port = mport;
            continue;
        }
        if (mport == first_port) {
            s_nat_mapping = NAT_MAP_ENDPOINT_INDEPENDENT;
            pb_log_info(TAG, "NAT mapping: endpoint-independent (port %d from two "
                          "servers) — STUN usable for media", mport);
        } else {
            s_nat_mapping = NAT_MAP_ENDPOINT_DEPENDENT;
            pb_log_warn(TAG, "NAT mapping: endpoint-dependent / symmetric (ports "
                          "%d vs %d) — STUN cannot fix media; for direct "
                          "provider registration use a port-identical UDP "
                          "forward of the RTP port, or a VoIP router",
                     first_port, mport);
        }
        return s_nat_mapping;
    }

    s_nat_mapping = NAT_MAP_UNKNOWN;
    pb_log_warn(TAG, "NAT mapping: probe inconclusive (need two reachable STUN "
                  "servers at different IPs)");
    return s_nat_mapping;
}

#define FRAME_SAMPLES    160          // 20 ms at 8 kHz
#define FRAME_BYTES      FRAME_SAMPLES // 1 byte per sample for PCMA
#define FRAME_MS         20
#define RTP_HEADER_BYTES 12
#define PT_PCMA          8

// Abort flag. Single-call-at-a-time semantics elsewhere in the SIP
// stack guarantee at most one rtp_audio_task ever runs, so a single
// volatile bool is enough. Same pattern as s_reload_requested in
// sip_register.c — set by SIP task, polled by RTP task per frame.
static volatile bool s_abort = false;

// True while an announcement is being streamed. The report-call worker
// polls this so its TLS handshake doesn't run concurrently with the SRTP
// stream — two TLS sessions plus the libsrtp session exhaust the ESP32
// heap (lwIP "Not enough space" on RTP sendto + mbedTLS PK-parse OOM,
// then an httpd-task watchdog reboot). Set in rtp_play_audio before the
// task starts (so there's no enqueue/stream-start race) and cleared when
// the task exits.
static volatile bool s_streaming = false;

bool rtp_streaming_active(void)
{
    return s_streaming;
}

typedef struct {
    struct sockaddr_in dest;
    announcement_src_t src;
    rtp_srtp_tx_t      srtp;
} rtp_args_t;

void rtp_request_abort(void)
{
    s_abort = true;
}

// libsrtp requires a one-time global init before any session is created.
// Guard it so repeated calls (one per spam call) don't re-init.
static bool srtp_global_init(void)
{
    static bool inited = false;
    if (inited) return true;
    srtp_err_status_t st = srtp_init();
    if (st != srtp_err_status_ok) {
        pb_log_err(TAG, "srtp_init failed: %d", st);
        return false;
    }
    inited = true;
    return true;
}

// Create an outbound-only SRTP session keyed with the SDES master key/salt
// we advertised in the SDP answer. Returns NULL on failure.
static srtp_t srtp_open_tx(const rtp_srtp_tx_t *keys)
{
    if (!srtp_global_init()) return NULL;

    srtp_policy_t policy;
    memset(&policy, 0, sizeof(policy));
    srtp_crypto_policy_set_aes_cm_128_hmac_sha1_80(&policy.rtp);
    srtp_crypto_policy_set_aes_cm_128_hmac_sha1_80(&policy.rtcp);
    policy.ssrc.type = ssrc_any_outbound;
    policy.key       = (uint8_t *)keys->key;   // 30 bytes, not retained by libsrtp after create
    policy.next      = NULL;

    srtp_t session = NULL;
    srtp_err_status_t st = srtp_create(&session, &policy);
    if (st != srtp_err_status_ok) {
        pb_log_err(TAG, "srtp_create failed: %d", st);
        return NULL;
    }
    return session;
}

// Create an inbound-only SRTP session keyed with the *remote's* SDES master
// key/salt (the INVITE's a=crypto offer), to decrypt the caller's media for
// the dialog's VAD. Returns NULL on failure.
static srtp_t srtp_open_rx(const uint8_t key[RTP_SRTP_KEY_LEN])
{
    if (!srtp_global_init()) return NULL;

    srtp_policy_t policy;
    memset(&policy, 0, sizeof(policy));
    srtp_crypto_policy_set_aes_cm_128_hmac_sha1_80(&policy.rtp);
    srtp_crypto_policy_set_aes_cm_128_hmac_sha1_80(&policy.rtcp);
    policy.ssrc.type = ssrc_any_inbound;
    policy.key       = (uint8_t *)key;    // not retained by libsrtp after create
    policy.next      = NULL;

    srtp_t session = NULL;
    srtp_err_status_t st = srtp_create(&session, &policy);
    if (st != srtp_err_status_ok) {
        pb_log_err(TAG, "srtp_create (rx) failed: %d", st);
        return NULL;
    }
    return session;
}

// Outbound stream state shared by the announcement and the dialog task.
typedef struct {
    int                       sock;
    const struct sockaddr_in *dest;
    srtp_t                    srtp;      // NULL = plain RTP/AVP
    uint16_t                  seq;
    uint32_t                  timestamp;
    uint32_t                  ssrc;
    // Packets lost to a failing sendto() since the first failure of the
    // current run; 0 while sending works. A WiFi TX stall (weak signal)
    // fails every packet for a second or more — one WARN per packet would
    // flush the 32-entry log ring, so the run gets one line at its start
    // and one when it ends.
    uint32_t                  send_failures;
} rtp_tx_t;

static void rtp_tx_init(rtp_tx_t *tx, int sock, const struct sockaddr_in *dest,
                        srtp_t srtp)
{
    tx->sock      = sock;
    tx->dest      = dest;
    tx->srtp      = srtp;
    tx->seq       = (uint16_t)pb_random_u32();
    tx->timestamp = pb_random_u32();
    tx->ssrc      = pb_random_u32();
    tx->send_failures = 0;
}

// Sends one 20 ms PCMA frame. `pkt` holds the FRAME_SAMPLES payload bytes
// at offset RTP_HEADER_BYTES; the header is filled in here. Sequence number
// and timestamp advance even when a packet could not be protected or sent,
// so the receiver sees the gap as loss, not as a time warp.
static void rtp_tx_frame(rtp_tx_t *tx, uint8_t pkt[RTP_HEADER_BYTES + FRAME_BYTES])
{
    // SRTP appends an auth tag (10 bytes for HMAC_SHA1_80); leave room.
    uint8_t txbuf[RTP_HEADER_BYTES + FRAME_BYTES + SRTP_MAX_TRAILER_LEN];

    pkt[0]  = 0x80;                              // V=2, P=X=0, CC=0
    pkt[1]  = PT_PCMA;                           // M=0, PT=8 (PCMA)
    pkt[2]  = (uint8_t)(tx->seq >> 8);
    pkt[3]  = (uint8_t)(tx->seq);
    pkt[4]  = (uint8_t)(tx->timestamp >> 24);
    pkt[5]  = (uint8_t)(tx->timestamp >> 16);
    pkt[6]  = (uint8_t)(tx->timestamp >> 8);
    pkt[7]  = (uint8_t)(tx->timestamp);
    pkt[8]  = (uint8_t)(tx->ssrc >> 24);
    pkt[9]  = (uint8_t)(tx->ssrc >> 16);
    pkt[10] = (uint8_t)(tx->ssrc >> 8);
    pkt[11] = (uint8_t)(tx->ssrc);
    tx->seq++;
    tx->timestamp += FRAME_SAMPLES;

    const uint8_t *send_buf = pkt;
    size_t send_len = RTP_HEADER_BYTES + FRAME_SAMPLES;
    if (tx->srtp) {
        // The switch is really the libsrtp API generation, not the
        // platform: esp_libsrtp ships the libsrtp 3 API (separate output
        // buffer, size_t lengths, MKI index), while Linux builds against
        // the distribution's libsrtp2 (in-place, int length). libsrtp2
        // exposes no version macro to test instead.
#ifdef ESP_PLATFORM
        size_t out_len = sizeof(txbuf);
        srtp_err_status_t st = srtp_protect(tx->srtp, pkt, send_len,
                                            txbuf, &out_len, 0);
#else
        memcpy(txbuf, pkt, send_len);
        int out_len = (int)send_len;
        srtp_err_status_t st = srtp_protect(tx->srtp, txbuf, &out_len);
#endif
        if (st != srtp_err_status_ok) {
            pb_log_warn(TAG, "srtp_protect failed: %d", st);
            return;
        }
        send_buf = txbuf;
        send_len = (size_t)out_len;
    }

    int n = sendto(tx->sock, send_buf, send_len, 0,
                   (const struct sockaddr *)tx->dest, sizeof(*tx->dest));
    if (n < 0) {
        if (tx->send_failures++ == 0) {
            pb_log_warn(TAG, "rtp sendto: %s", strerror(errno));
        }
    } else if (tx->send_failures > 0) {
        pb_log_warn(TAG, "rtp sendto: sending again after %u lost packet(s)",
                    (unsigned)tx->send_failures);
        tx->send_failures = 0;
    }
}

static void rtp_audio_task(void *arg)
{
    rtp_args_t *a = (rtp_args_t *)arg;

    // Shared singleton socket (see rtp_socket_ensure): same local port the
    // STUN probe used, so the announcement leaves from the mapping we
    // advertised. Not closed here — it outlives the call.
    int sock = rtp_socket_ensure();
    if (sock < 0) goto done;

    // Set up SRTP if the SDP answer advertised RTP/SAVP. If session
    // creation fails we cannot send anything the remote will accept
    // (it expects encrypted media), so abort the stream.
    srtp_t srtp_session = NULL;
    if (a->srtp.enabled) {
        srtp_session = srtp_open_tx(&a->srtp);
        if (!srtp_session) {
            pb_log_err(TAG, "SRTP requested but session setup failed — no audio");
            goto done;
        }
    }

    // Diagnostic: count inbound packets (the gateway's return media) on the
    // RTP socket. Receiving them proves the advertised endpoint is reachable
    // and the NAT pinhole is two-way — strong evidence the announcement
    // reaches the caller too. None arriving points at a one-way / NAT
    // mapping problem (the very thing STUN is meant to fix). We don't decode
    // them (they're SRTP when enabled) — source and count are enough.
    unsigned inbound_pkts = 0;
    char     inbound_src[INET_ADDRSTRLEN] = "";
    int      inbound_port = 0;

    rtp_tx_t tx;
    rtp_tx_init(&tx, sock, &a->dest, srtp_session);

    size_t total_frames = (a->src.len + FRAME_SAMPLES - 1) / FRAME_SAMPLES;
    char ip[INET_ADDRSTRLEN];
    ipv4_to_string(&a->dest.sin_addr, ip, sizeof(ip));
    pb_log_info(TAG, "stream %u bytes (%u frames ≈ %u ms) → %s:%d, ssrc=%08lx%s",
             (unsigned)a->src.len, (unsigned)total_frames,
             (unsigned)(total_frames * 20),
             ip, ntohs(a->dest.sin_port), (unsigned long)tx.ssrc,
             srtp_session ? " (SRTP)" : "");

    uint8_t pkt[RTP_HEADER_BYTES + FRAME_BYTES];

    pb_deadline_t next;
    pb_deadline_init(&next);
    for (size_t frame = 0; frame < total_frames; frame++) {
        if (s_abort) {
            pb_log_info(TAG, "stream aborted at frame %u/%u",
                     (unsigned)frame, (unsigned)total_frames);
            break;
        }
        // Pull the next 20 ms straight from the announcement source
        // (flash RODATA for the default, SPIFFS file for a custom one).
        size_t payload = announcement_read(&a->src, pkt + RTP_HEADER_BYTES,
                                           FRAME_SAMPLES);
        if (payload == 0) break;   // end of stream or read error
        // Pad short final frame with A-law silence (0xD5 = 16-bit PCM 0).
        if (payload < FRAME_SAMPLES) {
            memset(pkt + RTP_HEADER_BYTES + payload, 0xD5,
                   FRAME_SAMPLES - payload);
        }
        rtp_tx_frame(&tx, pkt);

        // Drain whatever the gateway sent back this frame (non-blocking),
        // remembering the first source for the post-stream summary.
        for (;;) {
            uint8_t rbuf[256];
            struct sockaddr_in raddr;
            socklen_t rlen = sizeof(raddr);
            int rn = recvfrom(sock, rbuf, sizeof(rbuf), MSG_DONTWAIT,
                              (struct sockaddr *)&raddr, &rlen);
            if (rn <= 0) break;
            if (inbound_pkts == 0) {
                ipv4_to_string(&raddr.sin_addr, inbound_src,
                               sizeof(inbound_src));
                inbound_port = ntohs(raddr.sin_port);
            }
            inbound_pkts++;
        }

        pb_task_delay_until(&next, 20);
    }

    pb_log_info(TAG, "inbound RTP during stream: %u packet(s) from %s:%d",
             inbound_pkts, inbound_src, inbound_port);

    // Stack headroom of this task after a full stream (SRTP key expansion +
    // per-packet protect peak here); the task ends right after, so this is
    // the only place it can be read. -1 where the platform can't tell.
    pb_log_info(TAG, "stream finished (stack headroom %d B of 6144)",
                pb_task_stack_free());
    if (srtp_session) srtp_dealloc(srtp_session);

done:
    announcement_close(&a->src);
    free(a);
    s_streaming = false;
}

void rtp_play_audio(const struct sockaddr_in *dest,
                    announcement_src_t *src,
                    const rtp_srtp_tx_t *srtp)
{
    rtp_args_t *args = malloc(sizeof(*args));
    if (!args) {
        pb_log_err(TAG, "malloc rtp args failed");
        announcement_close(src);
        return;
    }
    args->dest = *dest;
    args->src  = *src;   // ownership (incl. open file handle) moves to the task
    if (srtp) {
        args->srtp = *srtp;
    } else {
        args->srtp.enabled = false;
    }
    s_abort = false;
    s_streaming = true;   // set before task start so the report worker can't
                          // race in between enqueue and stream start
    // 6 KB stack: SRTP AES key-expansion + per-packet protect needs more
    // headroom than the old plain-RTP 4 KB.
    if (pb_task_create(rtp_audio_task, args, "rtp_audio", 6144,
                        PB_PRIO_MEDIA) != PB_OK) {
        pb_log_err(TAG, "task creation failed");
        s_streaming = false;
        announcement_close(&args->src);
        free(args);
    }
}

// --- Interactive dialog (issue #429) ---------------------------------------

typedef struct {
    struct sockaddr_in dest;
    rtp_srtp_tx_t      srtp;                           // our TX key
    bool               rx_key_valid;
    uint8_t            rx_key[RTP_SRTP_KEY_LEN];       // remote's SDES key+salt
    dialog_set_t       set;
} rtp_dialog_args_t;

static uint32_t dialog_rng(void *ctx)
{
    (void)ctx;
    return pb_random_u32();
}

// Peak amplitude of uniform white noise whose RMS is `db` dBFS: a uniform
// distribution over [-a, a] has an RMS of a/sqrt(3), so the configured level
// is the level the caller actually hears.
static int noise_amp_from_db(int db)
{
    double a = 32768.0 * pow(10.0, (double)db / 20.0) * sqrt(3.0);
    if (a < 1.0)     return 1;
    if (a > 32767.0) return 32767;
    return (int)a;
}

// One outbound 20 ms frame: continuous comfort noise, with up to `clip_n`
// samples of the prompt (A-law `clip`) mixed on top. Running the noise under
// the speech instead of only between prompts keeps the line sounding the same
// throughout, so the prompt ↔ await transitions are seamless.
static void mix_frame(uint8_t *out, uint32_t *rng, int amp,
                      const uint8_t *clip, size_t clip_n)
{
    uint32_t x = *rng;
    for (size_t i = 0; i < FRAME_SAMPLES; i++) {
        x ^= x << 13; x ^= x >> 17; x ^= x << 5;      // xorshift32
        int s = (int)(int16_t)(x >> 16) * amp / 32768;
        if (i < clip_n) s += alaw_to_pcm(clip[i]);
        if (s > 32767)  s = 32767;
        if (s < -32768) s = -32768;
        out[i] = pcm_to_alaw((int16_t)s);
    }
    *rng = x;
}

// Decrypts (if `rx`) and parses one received datagram and feeds its PCMA
// audio to the VAD, the VAD's transitions to the dialog. Returns false if
// the packet was not usable audio.
static bool dialog_rx_packet(uint8_t *buf, int len, srtp_t rx, vad_t *vad,
                             dialog_sm_t *dlg)
{
    if (rx) {
#ifdef ESP_PLATFORM
        size_t plain_len = (size_t)len;
        srtp_err_status_t st = srtp_unprotect(rx, buf, (size_t)len, buf,
                                              &plain_len);
#else
        int plain_len = len;
        srtp_err_status_t st = srtp_unprotect(rx, buf, &plain_len);
#endif
        if (st != srtp_err_status_ok) return false;   // auth / replay failure
        len = (int)plain_len;
    }
    const uint8_t *payload;
    size_t n;
    if (!rtp_rx_payload(buf, (size_t)len, PT_PCMA, &payload, &n)) return false;

    // One VAD frame per FRAME_SAMPLES; a 30 ms packet becomes 20 + 10 ms.
    while (n > 0) {
        int16_t pcm[FRAME_SAMPLES];
        size_t k = n < FRAME_SAMPLES ? n : FRAME_SAMPLES;
        for (size_t i = 0; i < k; i++) pcm[i] = alaw_to_pcm(payload[i]);
        int ms = (int)(k / 8);                         // 8 samples per ms
        vad_event_t ev = vad_process(vad, pcm, k, ms > 0 ? ms : 1, NULL);
        if (ev != VAD_NONE) dialog_vad(dlg, ev);
        payload += k;
        n -= k;
    }
    return true;
}

static void rtp_dialog_task(void *arg)
{
    rtp_dialog_args_t *a = (rtp_dialog_args_t *)arg;
    srtp_t srtp_tx = NULL, srtp_rx = NULL;
    // The set's single file stays open for the call; a prompt is a seek to
    // its clip and `clip_left` bytes from there.
    FILE *clips = NULL;
    uint32_t clip_left = 0;

    int sock = rtp_socket_ensure();
    if (sock < 0) goto done;

    {
        char path[DIALOG_STORE_PATH_CAP];
        dialog_store_file_path(path, sizeof(path), a->set.lang);
        clips = fopen(path, "rb");
        if (!clips) {
            // handle_ack() checked the set just now, so this is a race with
            // a sync removing it: the first prompt ends the dialog below and
            // the SIP task hangs up.
            pb_log_warn(TAG, "dialog: %s cannot be opened", path);
        }
    }

    if (a->srtp.enabled) {
        srtp_tx = srtp_open_tx(&a->srtp);
        if (!srtp_tx) {
            pb_log_err(TAG, "SRTP requested but session setup failed — no audio");
            goto done;
        }
    }

    // The caller's side: plain RTP is parsed directly; SRTP needs the
    // remote's key. Without a usable one the inbound audio is opaque, so the
    // dialog runs deaf (every await times out) rather than ending the call.
    bool deaf = false;
    if (a->srtp.enabled) {
        if (a->rx_key_valid) srtp_rx = srtp_open_rx(a->rx_key);
        if (!srtp_rx) {
            deaf = true;
            pb_log_warn(TAG, "dialog: no usable SRTP key for the caller's audio "
                             "(%s) — the bot cannot hear the caller this call",
                        a->rx_key_valid ? "session setup failed"
                                        : "missing or undecodable a=crypto key");
        }
    }

    pb_net_low_latency(true);

    rtp_tx_t tx;
    rtp_tx_init(&tx, sock, &a->dest, srtp_tx);

    int silence_db     = config_vad_silence_db();
    int min_silence_ms = config_vad_min_silence_ms();
    int noise_amp      = noise_amp_from_db(config_comfort_noise_db());
    uint32_t noise_rng = pb_random_u32() | 1u;
    vad_t vad;
    vad_init(&vad, silence_db, min_silence_ms);

    char ip[INET_ADDRSTRLEN];
    ipv4_to_string(&a->dest.sin_addr, ip, sizeof(ip));
    pb_log_info(TAG, "dialog (%s clips) → %s:%d, ssrc=%08lx%s, caller audio %s, "
                     "VAD %d dBFS / %d ms",
                a->set.lang, ip, ntohs(a->dest.sin_port), (unsigned long)tx.ssrc,
                srtp_tx ? " (SRTP)" : "",
                deaf ? "unreadable" : (srtp_rx ? "SRTP" : "plain"),
                silence_db, min_silence_ms);

    dialog_sm_t dlg;
    dialog_action_t act = dialog_start(&dlg, a->set.count, dialog_rng, NULL);
    bool clip_done = true;
    unsigned prompts = 0, rx_audio = 0, rx_other = 0;
    uint8_t pkt[RTP_HEADER_BYTES + FRAME_BYTES];

    pb_deadline_t next;
    pb_deadline_init(&next);
    for (;;) {
        if (s_abort) {
            pb_log_info(TAG, "dialog aborted (call ended)");
            break;
        }
        if (act.kind == DIALOG_ACT_END) {
            if (dialog_media_lost(&dlg)) {
                // The normal end when the caller's BYE got lost: INFO.
                pb_log_info(TAG, "no RTP from the caller for %u s → BYE",
                            DIALOG_RX_TIMEOUT_MS / 1000u);
            } else {
                pb_log_warn(TAG, "dialog: clip set incomplete — hanging up");
            }
            break;
        }
        if (act.kind == DIALOG_ACT_SAY) {
            const dialog_clip_t *k = &a->set.clip[act.phase][act.clip];
            if (!clips || fseek(clips, (long)k->off, SEEK_SET) != 0) {
                // No audio to say: end the call rather than loop in silence
                // (the WARN above, or a broken file, once per call).
                if (clips) pb_log_warn(TAG, "dialog: seek to clip %s-%d failed",
                                       dialog_phase_name(act.phase), act.clip + 1);
                break;
            }
            clip_left = k->len;
            clip_done = false;
            prompts++;
            pb_log_info(TAG, "dialog: say %s-%d",
                        dialog_phase_name(act.phase), act.clip + 1);
        } else if (act.kind == DIALOG_ACT_LISTEN) {
            vad_init(&vad, silence_db, min_silence_ms);
        }

        // Everything the caller sent since the last tick (usually one 20 ms
        // packet, more under jitter), in arrival order.
        for (;;) {
            uint8_t rbuf[512];
            int rn = recvfrom(sock, rbuf, sizeof(rbuf), MSG_DONTWAIT, NULL, NULL);
            if (rn <= 0) break;
            // Any datagram from the caller keeps the call alive, readable or
            // not (a deaf dialog still sees the media stop).
            dialog_rx(&dlg);
            if (deaf) continue;
            if (dialog_rx_packet(rbuf, rn, srtp_rx, &vad, &dlg)) rx_audio++;
            else rx_other++;
        }

        // This tick's prompt audio, straight from flash.
        uint8_t chunk[FRAME_SAMPLES];
        size_t chunk_n = 0;
        if (clip_left > 0) {
            size_t want = clip_left < sizeof(chunk) ? clip_left : sizeof(chunk);
            chunk_n = fread(chunk, 1, want, clips);
            clip_left = chunk_n < want ? 0 : clip_left - (uint32_t)chunk_n;
            if (clip_left == 0) clip_done = true;
        }
        mix_frame(pkt + RTP_HEADER_BYTES, &noise_rng, noise_amp, chunk, chunk_n);
        rtp_tx_frame(&tx, pkt);

        pb_task_delay_until(&next, FRAME_MS);
        act = dialog_tick(&dlg, FRAME_MS, clip_done);
    }

    pb_log_info(TAG, "dialog finished after %u s: %u prompt(s), %u audio packet(s) "
                     "from the caller, %u other (stack headroom %d B of %d)",
                (unsigned)(dlg.elapsed_ms / 1000), prompts, rx_audio, rx_other,
                pb_task_stack_free(), RTP_DIALOG_STACK);
    pb_net_low_latency(false);

done:
    if (clips) fclose(clips);
    if (srtp_tx) srtp_dealloc(srtp_tx);
    if (srtp_rx) srtp_dealloc(srtp_rx);
    free(a);
    s_streaming = false;
}

void rtp_play_dialog(const struct sockaddr_in *dest, const rtp_srtp_tx_t *srtp,
                     const uint8_t *srtp_rx_key, const dialog_set_t *set)
{
    rtp_dialog_args_t *args = calloc(1, sizeof(*args));
    if (!args) {
        pb_log_err(TAG, "malloc dialog args failed");
        return;
    }
    args->dest = *dest;
    if (srtp) args->srtp = *srtp;
    if (srtp_rx_key) {
        args->rx_key_valid = true;
        memcpy(args->rx_key, srtp_rx_key, RTP_SRTP_KEY_LEN);
    }
    args->set = *set;
    s_abort = false;
    s_streaming = true;   // before the task starts, see rtp_play_audio
    if (pb_task_create(rtp_dialog_task, args, "rtp_dialog", RTP_DIALOG_STACK,
                       PB_PRIO_MEDIA) != PB_OK) {
        pb_log_err(TAG, "dialog task creation failed");
        s_streaming = false;
        free(args);
    }
}
