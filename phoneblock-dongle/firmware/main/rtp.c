#include "rtp.h"

#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <math.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"
#include "esp_random.h"
#include "esp_timer.h"
#include "lwip/sockets.h"

#include "config.h"
#include "audio.h"
#include "vad.h"
#include "recorder.h"

#include "srtp.h"

static const char *TAG = "rtp";

#define FRAME_SAMPLES    160          // 20 ms at 8 kHz
#define FRAME_BYTES      FRAME_SAMPLES // 1 byte per sample for PCMA
#define FRAME_MS         20
#define RTP_HEADER_BYTES 12

// Test beep played back to the caller on a detected silence onset: a
// short 425 Hz tone (European call-progress pitch), a handful of 20 ms
// frames long.
#define BEEP_FREQ_HZ     425.0f
#define BEEP_AMPLITUDE   8000.0f       // ≈ -12 dBFS — clearly audible, not harsh
#define BEEP_FRAMES      8             // 8 × 20 ms = 160 ms

// dBFS level is only mirrored to the log this often (one line per ~1 s)
// so a continuous stream does not flush the 32-entry web log ring. The
// speech/silence *transitions* are always logged.
#define VAD_LEVEL_LOG_FRAMES 50        // 50 × 20 ms = 1 s

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
    bool               srtp_rx_enabled;
    uint8_t            srtp_rx_key[RTP_SRTP_KEY_LEN];  // remote's SDES key+salt
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

// Create an inbound-only SRTP session keyed with the *remote's* SDES
// master key/salt (from the INVITE's a=crypto offer), to decrypt the
// caller's incoming media. Returns NULL on failure.
static srtp_t srtp_open_rx(const uint8_t key[RTP_SRTP_KEY_LEN])
{
    if (!srtp_global_init()) return NULL;

    srtp_policy_t policy;
    memset(&policy, 0, sizeof(policy));
    srtp_crypto_policy_set_aes_cm_128_hmac_sha1_80(&policy.rtp);
    srtp_crypto_policy_set_aes_cm_128_hmac_sha1_80(&policy.rtcp);
    policy.ssrc.type = ssrc_any_inbound;
    policy.key       = (uint8_t *)key;
    policy.next      = NULL;

    srtp_t session = NULL;
    srtp_err_status_t st = srtp_create(&session, &policy);
    if (st != srtp_err_status_ok) {
        ESP_LOGE(TAG, "srtp_create (rx) failed: %d", st);
        return NULL;
    }
    return session;
}

// Fill `n` bytes with quiet comfort noise ("live line" hiss) via a tiny
// LCG. The dongle plays this to the caller instead of the announcement,
// so testing the silence detection by talking to the dongle isn't fought
// by a voice talking back. `amp` is the peak sample amplitude derived from
// config_noise_db(). (The real interactive dialog will reuse this comfort
// noise between prompts in a later stage.)
static void fill_noise_frame(uint8_t *alaw, size_t n, uint32_t *rng, int amp)
{
    uint32_t x = *rng;
    for (size_t i = 0; i < n; i++) {
        // xorshift32: full-spectrum white noise (the old truncated-LCG top
        // byte gave only 256 levels and an audible periodic texture).
        x ^= x << 13;
        x ^= x >> 17;
        x ^= x << 5;
        int s = (int)(int16_t)(x >> 8) * amp / 32768;   // 16-bit res, ±amp
        alaw[i] = pcm_to_alaw((int16_t)s);
    }
    *rng = x;
}

// Peak sample amplitude for a comfort-noise level given in dBFS. The
// uniform-noise RMS sits ~5 dB below this peak, so the audible level is a
// touch quieter than the dBFS figure — fine for a "hiss vs. silence"
// control. Clamped to a usable, non-zero range.
static int noise_amp_from_db(int db)
{
    int amp = (int)(32768.0f * powf(10.0f, (float)db / 20.0f));
    if (amp < 1)     amp = 1;
    if (amp > 32767) amp = 32767;
    return amp;
}

// Fill `n` bytes with the next slice of a continuous 425 Hz A-law tone,
// advancing *phase so successive frames join seamlessly.
static void fill_beep_frame(uint8_t *alaw, size_t n, float *phase)
{
    const float step = 2.0f * (float)M_PI * BEEP_FREQ_HZ / 8000.0f;
    for (size_t i = 0; i < n; i++) {
        int16_t s = (int16_t)(BEEP_AMPLITUDE * sinf(*phase));
        alaw[i] = pcm_to_alaw(s);
        *phase += step;
        if (*phase > 2.0f * (float)M_PI) *phase -= 2.0f * (float)M_PI;
    }
}

// Decode one received RTP/SRTP packet, feed its audio to the VAD, and
// hand the decoded PCM back in `out_pcm` (up to FRAME_SAMPLES samples,
// count in *out_n) so the caller can mix it into the recording. `buf`/
// `len` is the raw datagram; if `rx` is non-NULL it is decrypted in place
// first. Returns the VAD event, with the measured level in *out_dbfs.
static vad_event_t process_rx_packet(uint8_t *buf, int len, srtp_t rx,
                                     vad_t *vad, int *out_dbfs,
                                     int16_t *out_pcm, int *out_n)
{
    *out_n = 0;
    if (rx) {
        size_t plain_len = (size_t)len;
        srtp_err_status_t st = srtp_unprotect(rx, buf, (size_t)len,
                                              buf, &plain_len);
        if (st != srtp_err_status_ok) return VAD_NONE;  // replay/auth fail
        len = (int)plain_len;
    }
    if (len < RTP_HEADER_BYTES) return VAD_NONE;

    // Skip the RTP header, including any CSRC list (byte 0, low nibble).
    int cc      = buf[0] & 0x0F;
    int hdr     = RTP_HEADER_BYTES + 4 * cc;
    if (len <= hdr) return VAD_NONE;
    int n = len - hdr;
    if (n > FRAME_SAMPLES) n = FRAME_SAMPLES;   // ignore oversized payloads

    const uint8_t *payload = buf + hdr;
    for (int i = 0; i < n; i++) out_pcm[i] = alaw_to_pcm(payload[i]);
    *out_n = n;

    return vad_process(vad, out_pcm, (size_t)n, FRAME_MS, out_dbfs);
}

// Mix the outbound frame (`tx_alaw`, what we send: comfort noise + any
// beep) with the caller's decoded inbound PCM (`rx_pcm`, `rx_n` samples;
// `have_rx` false ⇒ silence this tick) and stream the A-law sum to the
// recorder. Recording both directions on the dongle's own 20 ms tick puts
// the caller's voice and the beep on one timeline — so the gap between
// speech end and beep is directly measurable in the file.
static void record_mixed(const uint8_t *tx_alaw,
                         const int16_t *rx_pcm, bool have_rx, int rx_n)
{
    uint8_t out[FRAME_SAMPLES];
    for (int i = 0; i < FRAME_SAMPLES; i++) {
        int s = alaw_to_pcm(tx_alaw[i]);
        if (have_rx && i < rx_n) s += rx_pcm[i];
        if (s > 32767)  s = 32767;
        if (s < -32768) s = -32768;
        out[i] = pcm_to_alaw((int16_t)s);
    }
    recorder_write(out, FRAME_SAMPLES);
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

    // Inbound path: decrypt the caller's media (if SRTP) and run the VAD.
    // A failed RX-session setup is not fatal — we just lose silence
    // detection for this call and still play the announcement.
    srtp_t srtp_rx = NULL;
    if (a->srtp_rx_enabled) {
        srtp_rx = srtp_open_rx(a->srtp_rx_key);
        if (!srtp_rx) {
            ESP_LOGW(TAG, "SRTP rx setup failed — silence detection disabled");
        }
    }
    // Only run the VAD when we can actually read the inbound audio: plain
    // RTP is decoded directly; SRTP media needs a working rx session. If
    // the media is encrypted but the key didn't decode, inbound is opaque
    // ciphertext — skip it rather than feed noise to the detector.
    bool rx_vad_ok = (srtp_rx != NULL) || !a->srtp.enabled;
    vad_t vad;
    vad_init(&vad, config_vad_silence_db(), config_vad_min_silence_ms());
    int      beep_remaining = 0;    // outbound frames still to overwrite with tone
    float    beep_phase     = 0.0f;
    uint32_t noise_rng      = esp_random() | 1u;  // xorshift seed (never 0)
    int      noise_amp      = noise_amp_from_db(config_noise_db());
    int      level_log_in   = 0;    // frames until the next periodic level log
    bool     recording      = config_rec_url()[0] != '\0';

    uint16_t seq       = (uint16_t)esp_random();
    uint32_t timestamp = esp_random();
    uint32_t ssrc      = esp_random();

    char ip[INET_ADDRSTRLEN];
    inet_ntoa_r(a->dest.sin_addr, ip, sizeof(ip));
    ESP_LOGI(TAG, "comfort-noise stream (%d dBFS, endless) → %s:%d, "
             "ssrc=%08lx%s, rx VAD %s",
             config_noise_db(), ip, ntohs(a->dest.sin_port),
             (unsigned long)ssrc,
             srtp_session ? " (SRTP)" : "",
             a->srtp_rx_enabled ? (srtp_rx ? "on (SRTP)" : "off (key fail)")
                                : "on (plain)");

    uint8_t pkt[RTP_HEADER_BYTES + FRAME_BYTES];
    // SRTP appends an auth tag (10 bytes for HMAC_SHA1_80); leave room.
    uint8_t txbuf[RTP_HEADER_BYTES + FRAME_BYTES + SRTP_MAX_TRAILER_LEN];

    // Start the call recording (no-op unless config_rec_url() is set).
    recorder_call_begin(NULL);

    // Stream endlessly: comfort noise plays until the remote hangs up (its
    // BYE / a preempting INVITE sets s_abort via rtp_request_abort), so the
    // caller stays on the line — handy for calibrating the silence
    // threshold without the call dropping after a fixed announcement.
    // Diagnostics for the "1.5 s configured but ~4.5 s felt" report: count
    // how often an accumulating silence is reset by a stray loud frame, and
    // measure the clean silence run that finally triggers the onset.
    int     sil_resets   = 0;
    int64_t last_loud_us = esp_timer_get_time();
    // RX arrival-pattern stats: with WiFi power-save the AP bursts packets,
    // so max_burst >> 1 and latency climbs; smooth delivery keeps it ~1.
    int     rx_total = 0, rx_max_burst = 0;

    TickType_t next = xTaskGetTickCount();
    for (size_t frame = 0; ; frame++) {
        if (s_abort) {
            ESP_LOGI(TAG, "stream aborted at frame %u", (unsigned)frame);
            break;
        }
        // Drain everything the caller sent us since the last tick (usually
        // one 20 ms packet; more under jitter) and feed each to the VAD.
        uint8_t rxbuf[RTP_HEADER_BYTES + FRAME_BYTES + SRTP_MAX_TRAILER_LEN
                      + 4 * 15 /* up to 15 CSRCs */];
        int rxn;
        int drained = 0;
        // Caller audio captured this tick, for mixing into the recording.
        int16_t tick_rx_pcm[FRAME_SAMPLES];
        int     tick_rx_n   = 0;
        bool    tick_have_rx = false;
        while (rx_vad_ok &&
               (rxn = recvfrom(sock, rxbuf, sizeof(rxbuf), MSG_DONTWAIT,
                               NULL, NULL)) > 0) {
            drained++;
            int prev_sil = vad.silence_ms;
            int db = VAD_DBFS_FLOOR;
            int rx_n = 0;
            vad_event_t ev = process_rx_packet(rxbuf, rxn, srtp_rx, &vad, &db,
                                               tick_rx_pcm, &rx_n);
            if (rx_n > 0) { tick_rx_n = rx_n; tick_have_rx = true; }
            int64_t now_us = esp_timer_get_time();
            if (ev == VAD_SILENCE_ONSET) {
                ESP_LOGI(TAG, "VAD: silence onset (%d dBFS), clean run %lld ms, "
                         "%d resets → beep", db,
                         (long long)((now_us - last_loud_us) / 1000), sil_resets);
                sil_resets = 0;
                beep_remaining = BEEP_FRAMES;   // start the test tone
            } else if (ev == VAD_SPEECH_ONSET) {
                ESP_LOGI(TAG, "VAD: speech (%d dBFS)", db);
                sil_resets   = 0;
                last_loud_us = now_us;
            } else if (vad.silence_ms == 0 && prev_sil >= 100) {
                // Confirmed speech zeroed an accumulating silence (a brief
                // blip no longer does — it's debounced away in the VAD).
                ESP_LOGI(TAG, "VAD: silence reset after %d ms by %d dBFS",
                         prev_sil, db);
                sil_resets++;
                last_loud_us = now_us;
            } else if (--level_log_in <= 0) {
                ESP_LOGI(TAG, "VAD: level %d dBFS (%s)", db,
                         vad.speaking ? "speech" : "silence");
                level_log_in = VAD_LEVEL_LOG_FRAMES;
            }
        }
        rx_total += drained;
        if (drained > rx_max_burst) rx_max_burst = drained;

        // Fill the outbound frame with quiet comfort noise instead of the
        // announcement, so the caller can talk to the dongle to exercise
        // silence detection without a voice talking over them.
        fill_noise_frame(pkt + RTP_HEADER_BYTES, FRAME_SAMPLES, &noise_rng, noise_amp);

        // Beep over the noise on a detected silence onset — the audible
        // test signal.
        if (beep_remaining > 0) {
            fill_beep_frame(pkt + RTP_HEADER_BYTES, FRAME_SAMPLES, &beep_phase);
            beep_remaining--;
        }

        // Record sent+received mixed, on this steady 20 ms tick: the beep
        // (sent) and the caller's voice (received) share one timeline.
        if (recording) {
            record_mixed(pkt + RTP_HEADER_BYTES, tick_rx_pcm, tick_have_rx,
                         tick_rx_n);
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

    recorder_call_end();   // flush + finalize the upload (no-op if off)

    ESP_LOGI(TAG, "stream finished — rx %d packets, max burst %d/tick "
             "(>1 ⇒ AP buffering / power-save)", rx_total, rx_max_burst);
    if (srtp_session) srtp_dealloc(srtp_session);
    if (srtp_rx) srtp_dealloc(srtp_rx);
    close(sock);

done:
    announcement_close(&a->src);
    free(a);
    s_streaming = false;
    vTaskDelete(NULL);
}

void rtp_play_audio(const struct sockaddr_in *dest,
                    announcement_src_t *src,
                    const rtp_srtp_tx_t *srtp,
                    const uint8_t *srtp_rx_key)
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
    if (srtp_rx_key) {
        args->srtp_rx_enabled = true;
        memcpy(args->srtp_rx_key, srtp_rx_key, RTP_SRTP_KEY_LEN);
    } else {
        args->srtp_rx_enabled = false;
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
