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
#include "lwip/sockets.h"

#include "config.h"
#include "audio.h"
#include "vad.h"
#include "recorder.h"
#include "conversation.h"

#include "srtp.h"

static const char *TAG = "rtp";

#define FRAME_SAMPLES    160          // 20 ms at 8 kHz
#define FRAME_BYTES      FRAME_SAMPLES // 1 byte per sample for PCMA
#define FRAME_MS         20
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

// Fill one 20 ms outbound frame with continuous quiet comfort noise, with
// up to `clip_n` samples of speech (decoded from A-law `clip`) mixed on
// top. Running the noise *under* the speech — instead of switching it on
// only between prompts — keeps the line sounding the same throughout, so
// the clip↔await transitions are seamless (no hiss toggling on and off,
// which made the speech sound choppy). `amp` is the noise peak amplitude.
static void fill_outbound(uint8_t *alaw, uint32_t *rng, int amp,
                          const uint8_t *clip, size_t clip_n)
{
    uint32_t x = *rng;
    for (size_t i = 0; i < FRAME_SAMPLES; i++) {
        x ^= x << 13; x ^= x >> 17; x ^= x << 5;     // xorshift32 white noise
        int s = (int)(int16_t)(x >> 8) * amp / 32768;
        if (i < clip_n) s += alaw_to_pcm(clip[i]);   // speech over the floor
        if (s > 32767)  s = 32767;
        if (s < -32768) s = -32768;
        alaw[i] = pcm_to_alaw((int16_t)s);
    }
    *rng = x;
}

// --- Dialog state machine -------------------------------------------
//
// Hardcoded conversation (issue #429; web configurability deferred to 3.0):
//   say(hello)
//   intro phase:    await → spoke ? say(am-aparat), advance to question
//                            else   say(who-is-there)
//   question phase  await → spoke ? say(<rotating question>)
//   (forever):               else   say(cant-hear-you)
//
// await(): after the bot speaks it listens. If the caller talks, it
// continues as soon as they have been quiet for the configured response
// time (config_vad_min_silence_ms — short). If the caller says nothing it
// waits the full AWAIT_TIMEOUT_MS (long) — the call-centre "make them
// wait, then it's their turn" behaviour.
#define AWAIT_TIMEOUT_MS 3000

typedef enum { DLG_SAY, DLG_AWAIT } dlg_mode_t;

typedef struct {
    dlg_mode_t mode;
    int        phase;     // 0 = intro, 1 = question
    clip_t     clip;      // current clip while DLG_SAY
    size_t     pos;       // bytes already emitted from clip
    bool       spoke;     // caller spoke during the current await
    int        await_ms;  // elapsed await time
    uint32_t   rng;       // question-rotation state
} dlg_t;

static void dlg_say(dlg_t *d, clip_t c, const char *name)
{
    d->mode = DLG_SAY;
    d->clip = c;
    d->pos  = 0;
    ESP_LOGI(TAG, "dialog: say(%s) %u ms", name, (unsigned)(c.len / 8));
}

static void dlg_begin_await(dlg_t *d, vad_t *vad)
{
    d->mode     = DLG_AWAIT;
    d->spoke    = false;
    d->await_ms = 0;
    vad_init(vad, config_vad_silence_db(), config_vad_min_silence_ms());
}

// An await resolved (`received` = the caller spoke). Pick and start the
// next clip per the hardcoded flow above.
static void dlg_resolve(dlg_t *d, bool received)
{
    if (d->phase == 0) {                 // intro
        if (received) { d->phase = 1; dlg_say(d, conversation_am_aparat(), "am-aparat"); }
        else            dlg_say(d, conversation_who_is_there(), "who-is-there");
    } else {                             // question
        if (received)   dlg_say(d, conversation_question(&d->rng), "question");
        else            dlg_say(d, conversation_cant_hear_you(), "cant-hear-you");
    }
}

// --- RX jitter buffer for the recorder ------------------------------
//
// Caller packets arrive ~50/s but are not phase-locked to our 20 ms TX
// tick, so naively recording "this tick's last packet" decimates and
// duplicates them — the recorded voice comes out blurry. Accumulate every
// received sample here and emit exactly one 20 ms frame per tick: a tiny
// jitter buffer that keeps the caller's audio intact in the mix.
#define RX_RING_SAMPLES 1024            // ~128 ms, power of two
static int16_t s_rx_ring[RX_RING_SAMPLES];
static int     s_rx_wr, s_rx_rd;

static inline int rx_ring_count(void)
{
    return (s_rx_wr - s_rx_rd) & (RX_RING_SAMPLES - 1);
}
static inline void rx_ring_push(int16_t s)
{
    if (rx_ring_count() < RX_RING_SAMPLES - 1) {  // drop on overflow (RX>TX, rare)
        s_rx_ring[s_rx_wr] = s;
        s_rx_wr = (s_rx_wr + 1) & (RX_RING_SAMPLES - 1);
    }
}
static inline int16_t rx_ring_pop(void)           // 0 on underflow = caller silent
{
    if (rx_ring_count() == 0) return 0;
    int16_t s = s_rx_ring[s_rx_rd];
    s_rx_rd = (s_rx_rd + 1) & (RX_RING_SAMPLES - 1);
    return s;
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
    vad_t vad;                                  // (re)initialised at each await
    uint32_t noise_rng = esp_random() | 1u;     // comfort-noise generator (xorshift)
    int      noise_amp = noise_amp_from_db(config_noise_db());
    bool     recording = config_rec_url()[0] != '\0';

    uint16_t seq       = (uint16_t)esp_random();
    uint32_t timestamp = esp_random();
    uint32_t ssrc      = esp_random();

    char ip[INET_ADDRSTRLEN];
    inet_ntoa_r(a->dest.sin_addr, ip, sizeof(ip));
    ESP_LOGI(TAG, "dialog start → %s:%d, ssrc=%08lx%s, rx VAD %s, response %d ms",
             ip, ntohs(a->dest.sin_port), (unsigned long)ssrc,
             srtp_session ? " (SRTP)" : "",
             a->srtp_rx_enabled ? (srtp_rx ? "on (SRTP)" : "off (key fail)")
                                : "on (plain)",
             config_vad_min_silence_ms());

    uint8_t pkt[RTP_HEADER_BYTES + FRAME_BYTES];
    // SRTP appends an auth tag (10 bytes for HMAC_SHA1_80); leave room.
    uint8_t txbuf[RTP_HEADER_BYTES + FRAME_BYTES + SRTP_MAX_TRAILER_LEN];

    // Start the call recording (no-op unless config_rec_url() is set).
    s_rx_wr = s_rx_rd = 0;     // fresh RX jitter buffer for this call
    recorder_call_begin(NULL);

    // Greet, then run the dialog until the remote hangs up (its BYE / a
    // preempting INVITE sets s_abort via rtp_request_abort).
    dlg_t dlg = { .phase = 0, .rng = esp_random() | 1u };
    dlg_say(&dlg, conversation_hello(), "hello");

    TickType_t next = xTaskGetTickCount();
    for (size_t frame = 0; ; frame++) {
        if (s_abort) {
            ESP_LOGI(TAG, "dialog aborted at frame %u", (unsigned)frame);
            break;
        }
        // Drain everything the caller sent us since the last tick (usually
        // one 20 ms packet; more under jitter) and feed each to the VAD.
        uint8_t rxbuf[RTP_HEADER_BYTES + FRAME_BYTES + SRTP_MAX_TRAILER_LEN
                      + 4 * 15 /* up to 15 CSRCs */];
        int rxn;
        bool tick_speech = false, tick_silence = false;
        while (rx_vad_ok &&
               (rxn = recvfrom(sock, rxbuf, sizeof(rxbuf), MSG_DONTWAIT,
                               NULL, NULL)) > 0) {
            int db = VAD_DBFS_FLOOR;
            int rx_n = 0;
            int16_t pcm[FRAME_SAMPLES];
            vad_event_t ev = process_rx_packet(rxbuf, rxn, srtp_rx, &vad, &db,
                                               pcm, &rx_n);
            // Buffer every received sample (not just this tick's last
            // packet) so the recorded voice stays intact.
            if (recording)
                for (int i = 0; i < rx_n; i++) rx_ring_push(pcm[i]);
            // VAD only matters while we are awaiting the caller's turn.
            if (dlg.mode == DLG_AWAIT) {
                if (ev == VAD_SPEECH_ONSET)  tick_speech  = true;
                if (ev == VAD_SILENCE_ONSET) tick_silence = true;
            }
        }

        // --- Advance the dialog (once per steady 20 ms wall-clock tick) ---
        if (dlg.mode == DLG_AWAIT) {
            dlg.await_ms += FRAME_MS;
            if (tick_speech) dlg.spoke = true;
            if (dlg.spoke && tick_silence) {
                dlg_resolve(&dlg, true);          // caller finished talking
            } else if (!dlg.spoke && dlg.await_ms >= AWAIT_TIMEOUT_MS) {
                dlg_resolve(&dlg, false);         // said nothing → move on
            }
        }

        // --- Build the outbound frame -------------------------------------
        // Continuous comfort noise; while saying, the clip is mixed on top.
        uint8_t *payload = pkt + RTP_HEADER_BYTES;
        bool saying = (dlg.mode == DLG_SAY && dlg.pos < dlg.clip.len);
        if (!saying && dlg.mode == DLG_SAY) dlg_begin_await(&dlg, &vad);
        size_t clip_n = 0;
        const uint8_t *clip_chunk = NULL;
        if (saying) {
            size_t remaining = dlg.clip.len - dlg.pos;
            clip_n     = remaining < FRAME_SAMPLES ? remaining : FRAME_SAMPLES;
            clip_chunk = dlg.clip.data + dlg.pos;
        }
        fill_outbound(payload, &noise_rng, noise_amp, clip_chunk, clip_n);
        if (saying) dlg.pos += clip_n;

        // Record sent+received mixed on this 20 ms tick: the bot's clips and
        // the caller's voice land on one timeline. Pop exactly one frame
        // from the jitter buffer so the caller's audio is neither decimated
        // nor duplicated.
        if (recording) {
            int16_t rxframe[FRAME_SAMPLES];
            for (int i = 0; i < FRAME_SAMPLES; i++) rxframe[i] = rx_ring_pop();
            record_mixed(payload, rxframe, true, FRAME_SAMPLES);
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

    ESP_LOGI(TAG, "dialog finished");
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
