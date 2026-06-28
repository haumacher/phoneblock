#include "rtp.h"

#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"
#include "esp_random.h"
#include "lwip/sockets.h"
#include "lwip/netdb.h"

#include "config.h"

#include "srtp.h"

static const char *TAG = "rtp";

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
        ESP_LOGE(TAG, "RTP socket: %s", strerror(errno));
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
        ESP_LOGE(TAG, "bind RTP :%d: %s", rtp_port, strerror(errno));
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
        ESP_LOGW(TAG, "STUN: DNS lookup of %s failed", host);
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
    for (int i = 8; i < 20; i++) req[i] = (uint8_t)esp_random();

    for (int attempt = 0; attempt < 2; attempt++) {
        if (sendto(sock, req, sizeof(req), 0,
                   (struct sockaddr *)srv, sizeof(*srv)) < 0) {
            ESP_LOGW(TAG, "STUN: sendto failed: %s", strerror(errno));
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
                inet_ntoa_r(a, ip_out, ip_cap);
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
        ESP_LOGW(TAG, "STUN: no usable response from %s", stun_server);
        return false;
    }
    ESP_LOGI(TAG, "STUN: %s → public %s:%d", stun_server, ip_out, *port_out);
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
            ESP_LOGI(TAG, "NAT mapping: endpoint-independent (port %d from two "
                          "servers) — STUN usable for media", mport);
        } else {
            s_nat_mapping = NAT_MAP_ENDPOINT_DEPENDENT;
            ESP_LOGW(TAG, "NAT mapping: endpoint-dependent / symmetric (ports "
                          "%d vs %d) — STUN cannot fix media; for direct "
                          "provider registration use a port-identical UDP "
                          "forward of the RTP port, or a VoIP router",
                     first_port, mport);
        }
        return s_nat_mapping;
    }

    s_nat_mapping = NAT_MAP_UNKNOWN;
    ESP_LOGW(TAG, "NAT mapping: probe inconclusive (need two reachable STUN "
                  "servers at different IPs)");
    return s_nat_mapping;
}

#define FRAME_SAMPLES    160          // 20 ms at 8 kHz
#define FRAME_BYTES      FRAME_SAMPLES // 1 byte per sample for PCMA
#define RTP_HEADER_BYTES 12

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
        ESP_LOGE(TAG, "srtp_init failed: %d", st);
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
        ESP_LOGE(TAG, "srtp_create failed: %d", st);
        return NULL;
    }
    return session;
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
            ESP_LOGE(TAG, "SRTP requested but session setup failed — no audio");
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

    uint16_t seq       = (uint16_t)esp_random();
    uint32_t timestamp = esp_random();
    uint32_t ssrc      = esp_random();

    size_t total_frames = (a->src.len + FRAME_SAMPLES - 1) / FRAME_SAMPLES;
    char ip[INET_ADDRSTRLEN];
    inet_ntoa_r(a->dest.sin_addr, ip, sizeof(ip));
    ESP_LOGI(TAG, "stream %u bytes (%u frames ≈ %u ms) → %s:%d, ssrc=%08lx%s",
             (unsigned)a->src.len, (unsigned)total_frames,
             (unsigned)(total_frames * 20),
             ip, ntohs(a->dest.sin_port), (unsigned long)ssrc,
             srtp_session ? " (SRTP)" : "");

    uint8_t pkt[RTP_HEADER_BYTES + FRAME_BYTES];
    // SRTP appends an auth tag (10 bytes for HMAC_SHA1_80); leave room.
    uint8_t txbuf[RTP_HEADER_BYTES + FRAME_BYTES + SRTP_MAX_TRAILER_LEN];

    TickType_t next = xTaskGetTickCount();
    for (size_t frame = 0; frame < total_frames; frame++) {
        if (s_abort) {
            ESP_LOGI(TAG, "stream aborted at frame %u/%u",
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

        pkt[0]  = 0x80;                              // V=2, P=X=0, CC=0
        pkt[1]  = 8;                                 // M=0, PT=8 (PCMA)
        pkt[2]  = (uint8_t)(seq >> 8);
        pkt[3]  = (uint8_t)(seq);
        pkt[4]  = (uint8_t)(timestamp >> 24);
        pkt[5]  = (uint8_t)(timestamp >> 16);
        pkt[6]  = (uint8_t)(timestamp >> 8);
        pkt[7]  = (uint8_t)(timestamp);
        pkt[8]  = (uint8_t)(ssrc >> 24);
        pkt[9]  = (uint8_t)(ssrc >> 16);
        pkt[10] = (uint8_t)(ssrc >> 8);
        pkt[11] = (uint8_t)(ssrc);

        const uint8_t *send_buf = pkt;
        size_t send_len = RTP_HEADER_BYTES + FRAME_SAMPLES;
        if (srtp_session) {
            size_t out_len = sizeof(txbuf);
            srtp_err_status_t st = srtp_protect(srtp_session, pkt,
                                                RTP_HEADER_BYTES + FRAME_SAMPLES,
                                                txbuf, &out_len, 0);
            if (st != srtp_err_status_ok) {
                ESP_LOGW(TAG, "srtp_protect failed: %d", st);
                seq++;
                timestamp += FRAME_SAMPLES;
                vTaskDelayUntil(&next, pdMS_TO_TICKS(20));
                continue;
            }
            send_buf = txbuf;
            send_len = out_len;
        }

        int n = sendto(sock, send_buf, send_len, 0,
                       (struct sockaddr *)&a->dest, sizeof(a->dest));
        if (n < 0) {
            ESP_LOGW(TAG, "rtp sendto: %s", strerror(errno));
        }

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
                inet_ntoa_r(raddr.sin_addr, inbound_src, sizeof(inbound_src));
                inbound_port = ntohs(raddr.sin_port);
            }
            inbound_pkts++;
        }

        seq++;
        timestamp += FRAME_SAMPLES;
        vTaskDelayUntil(&next, pdMS_TO_TICKS(20));
    }

    ESP_LOGI(TAG, "inbound RTP during stream: %u packet(s) from %s:%d",
             inbound_pkts, inbound_src, inbound_port);

    ESP_LOGI(TAG, "stream finished");
    if (srtp_session) srtp_dealloc(srtp_session);

done:
    announcement_close(&a->src);
    free(a);
    s_streaming = false;
    vTaskDelete(NULL);
}

void rtp_play_audio(const struct sockaddr_in *dest,
                    announcement_src_t *src,
                    const rtp_srtp_tx_t *srtp)
{
    rtp_args_t *args = malloc(sizeof(*args));
    if (!args) {
        ESP_LOGE(TAG, "malloc rtp args failed");
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
    if (xTaskCreate(rtp_audio_task, "rtp_audio", 6144, args, 6, NULL) != pdPASS) {
        ESP_LOGE(TAG, "xTaskCreate failed");
        s_streaming = false;
        announcement_close(&args->src);
        free(args);
    }
}
