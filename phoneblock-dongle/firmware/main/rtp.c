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

#include "config.h"

#include "srtp.h"

static const char *TAG = "rtp";

#define FRAME_SAMPLES    160          // 20 ms at 8 kHz
#define FRAME_BYTES      FRAME_SAMPLES // 1 byte per sample for PCMA
#define RTP_HEADER_BYTES 12

// Abort flag. Single-call-at-a-time semantics elsewhere in the SIP
// stack guarantee at most one rtp_audio_task ever runs, so a single
// volatile bool is enough. Same pattern as s_reload_requested in
// sip_register.c — set by SIP task, polled by RTP task per frame.
static volatile bool s_abort = false;

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

    int sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (sock < 0) {
        ESP_LOGE(TAG, "socket(): %s", strerror(errno));
        goto done;
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
        goto done;
    }

    // Set up SRTP if the SDP answer advertised RTP/SAVP. If session
    // creation fails we cannot send anything the remote will accept
    // (it expects encrypted media), so abort the stream.
    srtp_t srtp_session = NULL;
    if (a->srtp.enabled) {
        srtp_session = srtp_open_tx(&a->srtp);
        if (!srtp_session) {
            ESP_LOGE(TAG, "SRTP requested but session setup failed — no audio");
            close(sock);
            goto done;
        }
    }

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

        seq++;
        timestamp += FRAME_SAMPLES;
        vTaskDelayUntil(&next, pdMS_TO_TICKS(20));
    }

    ESP_LOGI(TAG, "stream finished");
    if (srtp_session) srtp_dealloc(srtp_session);
    close(sock);

done:
    announcement_close(&a->src);
    free(a);
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
    // 6 KB stack: SRTP AES key-expansion + per-packet protect needs more
    // headroom than the old plain-RTP 4 KB.
    if (xTaskCreate(rtp_audio_task, "rtp_audio", 6144, args, 6, NULL) != pdPASS) {
        ESP_LOGE(TAG, "xTaskCreate failed");
        announcement_close(&args->src);
        free(args);
    }
}
