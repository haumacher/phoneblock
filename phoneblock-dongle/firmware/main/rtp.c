#include "rtp.h"
#include "audio.h"

#include <math.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"
#include "esp_random.h"
#include "lwip/sockets.h"

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

static const char *TAG = "rtp";

#define SAMPLE_RATE      8000
#define FRAME_SAMPLES    160          // 20 ms at 8 kHz
#define FRAME_BYTES      FRAME_SAMPLES // 1 byte per sample for PCMA
#define RTP_HEADER_BYTES 12
#define TONE_FREQ        440
#define TONE_AMPLITUDE   12000        // ~−8 dBFS, not harsh

typedef struct {
    struct sockaddr_in dest;
    int duration_ms;
} rtp_args_t;

static void fill_tone_frame(double *phase, uint8_t *out_alaw)
{
    const double step = 2.0 * M_PI * TONE_FREQ / SAMPLE_RATE;
    for (int i = 0; i < FRAME_SAMPLES; i++) {
        int16_t pcm = (int16_t)(sin(*phase) * TONE_AMPLITUDE);
        out_alaw[i] = pcm_to_alaw(pcm);
        *phase += step;
        if (*phase > 2.0 * M_PI) *phase -= 2.0 * M_PI;
    }
}

static void rtp_tone_task(void *arg)
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

    int frames = (a->duration_ms + 19) / 20;
    char ip[INET_ADDRSTRLEN];
    inet_ntoa_r(a->dest.sin_addr, ip, sizeof(ip));
    ESP_LOGI(TAG, "stream %d ms (%d frames) → %s:%d, ssrc=%08lx",
             a->duration_ms, frames, ip, ntohs(a->dest.sin_port),
             (unsigned long)ssrc);

    uint8_t pkt[RTP_HEADER_BYTES + FRAME_BYTES];
    double phase = 0.0;

    TickType_t next = xTaskGetTickCount();
    for (int i = 0; i < frames; i++) {
        fill_tone_frame(&phase, pkt + RTP_HEADER_BYTES);

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

        int n = sendto(sock, pkt, sizeof(pkt), 0,
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

void rtp_play_tone(const struct sockaddr_in *dest, int duration_ms)
{
    rtp_args_t *args = malloc(sizeof(*args));
    if (!args) {
        ESP_LOGE(TAG, "malloc rtp args failed");
        return;
    }
    args->dest        = *dest;
    args->duration_ms = duration_ms;
    if (xTaskCreate(rtp_tone_task, "rtp_tone", 4096, args, 6, NULL) != pdPASS) {
        ESP_LOGE(TAG, "xTaskCreate failed");
        free(args);
    }
}
