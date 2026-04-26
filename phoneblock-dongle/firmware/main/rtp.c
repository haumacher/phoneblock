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
    const uint8_t *alaw;
    size_t alaw_bytes;
} rtp_args_t;

void rtp_request_abort(void)
{
    s_abort = true;
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

    struct sockaddr_in local = {
        .sin_family      = AF_INET,
        .sin_addr.s_addr = htonl(INADDR_ANY),
        .sin_port        = htons(SIP_RTP_PORT),
    };
    if (bind(sock, (struct sockaddr *)&local, sizeof(local)) < 0) {
        ESP_LOGE(TAG, "bind RTP :%d: %s", SIP_RTP_PORT, strerror(errno));
        close(sock);
        goto done;
    }

    uint16_t seq       = (uint16_t)esp_random();
    uint32_t timestamp = esp_random();
    uint32_t ssrc      = esp_random();

    size_t total_frames = (a->alaw_bytes + FRAME_SAMPLES - 1) / FRAME_SAMPLES;
    char ip[INET_ADDRSTRLEN];
    inet_ntoa_r(a->dest.sin_addr, ip, sizeof(ip));
    ESP_LOGI(TAG, "stream %u bytes (%u frames ≈ %u ms) → %s:%d, ssrc=%08lx",
             (unsigned)a->alaw_bytes, (unsigned)total_frames,
             (unsigned)(total_frames * 20),
             ip, ntohs(a->dest.sin_port), (unsigned long)ssrc);

    uint8_t pkt[RTP_HEADER_BYTES + FRAME_BYTES];

    TickType_t next = xTaskGetTickCount();
    for (size_t off = 0; off < a->alaw_bytes; off += FRAME_SAMPLES) {
        if (s_abort) {
            ESP_LOGI(TAG, "stream aborted at frame %u/%u",
                     (unsigned)(off / FRAME_SAMPLES),
                     (unsigned)total_frames);
            break;
        }
        size_t remaining = a->alaw_bytes - off;
        size_t payload   = remaining < FRAME_SAMPLES ? remaining : FRAME_SAMPLES;

        memcpy(pkt + RTP_HEADER_BYTES, a->alaw + off, payload);
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

        int n = sendto(sock, pkt, RTP_HEADER_BYTES + FRAME_SAMPLES, 0,
                       (struct sockaddr *)&a->dest, sizeof(a->dest));
        if (n < 0) {
            ESP_LOGW(TAG, "rtp sendto: %s", strerror(errno));
        }

        seq++;
        timestamp += FRAME_SAMPLES;
        vTaskDelayUntil(&next, pdMS_TO_TICKS(20));
    }

    ESP_LOGI(TAG, "stream finished");
    close(sock);

done:
    free(a);
    vTaskDelete(NULL);
}

void rtp_play_audio(const struct sockaddr_in *dest,
                    const uint8_t *alaw, size_t alaw_bytes)
{
    rtp_args_t *args = malloc(sizeof(*args));
    if (!args) {
        ESP_LOGE(TAG, "malloc rtp args failed");
        return;
    }
    args->dest       = *dest;
    args->alaw       = alaw;
    args->alaw_bytes = alaw_bytes;
    s_abort = false;
    if (xTaskCreate(rtp_audio_task, "rtp_audio", 4096, args, 6, NULL) != pdPASS) {
        ESP_LOGE(TAG, "xTaskCreate failed");
        free(args);
    }
}
