#include "recorder.h"

#include <string.h>
#include <time.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/stream_buffer.h"
#include "freertos/semphr.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "esp_heap_caps.h"
#include "esp_http_client.h"

#include "config.h"

static const char *TAG = "rec";

// Audio buffers at 8 KB/s (A-law). 16 KB ≈ 2 s of slack between the RTP
// producer and the network consumer — plenty for a LAN PUT, and a hard
// cap so a stalled upload can never grow without bound (it drops instead).
#define REC_SB_BYTES   16384
#define REC_CHUNK      1024     // bytes drained per network write

static StreamBufferHandle_t s_sb;
static SemaphoreHandle_t    s_begin;     // given by recorder_call_begin
static volatile bool        s_active;    // true between begin and end
static char                 s_filename[64];
static uint32_t             s_dropped;   // bytes dropped this call on overflow

void recorder_write(const uint8_t *alaw, size_t n)
{
    if (!s_active || n == 0) return;
    size_t sent = xStreamBufferSend(s_sb, alaw, n, 0);   // non-blocking
    if (sent < n) s_dropped += (n - sent);
}

// Build "call-YYYYMMDD-HHMMSS[-peer].al". Falls back to an uptime-based
// name when the wall clock has not been set yet.
static void make_filename(const char *peer, char *out, size_t cap)
{
    char digits[24] = "";
    if (peer) {
        size_t j = 0;
        for (size_t i = 0; peer[i] && j < sizeof(digits) - 1; i++)
            if ((peer[i] >= '0' && peer[i] <= '9') || peer[i] == '+')
                digits[j++] = peer[i];
        digits[j] = '\0';
    }

    time_t now = time(NULL);
    struct tm tm;
    localtime_r(&now, &tm);
    if (tm.tm_year + 1900 >= 2020) {
        snprintf(out, cap, "call-%04d%02d%02d-%02d%02d%02d%s%s.al",
                 tm.tm_year + 1900, tm.tm_mon + 1, tm.tm_mday,
                 tm.tm_hour, tm.tm_min, tm.tm_sec,
                 digits[0] ? "-" : "", digits);
    } else {
        snprintf(out, cap, "call-up%llu%s%s.al",
                 (unsigned long long)(esp_timer_get_time() / 1000000),
                 digits[0] ? "-" : "", digits);
    }
}

void recorder_call_begin(const char *peer)
{
    if (config_rec_url()[0] == '\0') return;     // recording disabled
    if (s_active) {                              // shouldn't happen (1 call at a time)
        ESP_LOGW(TAG, "begin while still active — ignoring");
        return;
    }
    make_filename(peer, s_filename, sizeof(s_filename));
    s_dropped = 0;
    xStreamBufferReset(s_sb);
    s_active = true;
    xSemaphoreGive(s_begin);
}

void recorder_call_end(void)
{
    // Just flip the gate; the task drains whatever is buffered and
    // finalizes the PUT. No-op if we weren't recording.
    s_active = false;
}

// Join the configured base URL and the per-call filename into `out`.
static void build_url(char *out, size_t cap)
{
    const char *base = config_rec_url();
    size_t blen = strlen(base);
    bool slash = blen > 0 && base[blen - 1] == '/';
    snprintf(out, cap, "%s%s%s", base, slash ? "" : "/", s_filename);
}

// esp_http_client_write() sends raw bytes — with a chunked request the
// HTTP chunk framing ("<hexlen>\r\n<data>\r\n") is ours to add. Returns
// false on a transport write error.
static bool chunk_write(esp_http_client_handle_t c, const uint8_t *buf, size_t n)
{
    char hdr[16];
    int hlen = snprintf(hdr, sizeof(hdr), "%X\r\n", (unsigned)n);
    if (esp_http_client_write(c, hdr, hlen) != hlen)        return false;
    if (esp_http_client_write(c, (const char *)buf, n) != (int)n) return false;
    if (esp_http_client_write(c, "\r\n", 2) != 2)           return false;
    return true;
}

static void stream_one_call(void)
{
    char url[256];
    build_url(url, sizeof(url));

    esp_http_client_config_t cfg = {
        .url            = url,
        .method         = HTTP_METHOD_PUT,
        .timeout_ms     = 10000,
        // Keep the client's own buffers small — we stream tiny A-law
        // chunks and the response is a bare status line.
        .buffer_size    = 1024,
        .buffer_size_tx = 1024,
    };
    esp_http_client_handle_t client = esp_http_client_init(&cfg);
    if (!client) {
        ESP_LOGE(TAG, "client init failed");
        return;
    }
    if (config_rec_auth()[0])
        esp_http_client_set_header(client, "Authorization", config_rec_auth());
    esp_http_client_set_header(client, "Content-Type", "audio/x-alaw-basic");

    // open(client, -1) → Transfer-Encoding: chunked, unknown length.
    esp_err_t err = esp_http_client_open(client, -1);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "open %s failed: %s", url, esp_err_to_name(err));
        esp_http_client_cleanup(client);
        // Drain and discard so the producer's buffer doesn't back up.
        uint8_t scratch[REC_CHUNK];
        while (s_active || xStreamBufferBytesAvailable(s_sb))
            xStreamBufferReceive(s_sb, scratch, sizeof(scratch), pdMS_TO_TICKS(100));
        return;
    }
    ESP_LOGI(TAG, "recording → %s (heap %u)", url,
             (unsigned)heap_caps_get_free_size(MALLOC_CAP_INTERNAL));

    uint8_t buf[REC_CHUNK];
    size_t total = 0;
    bool   write_err = false;
    for (;;) {
        size_t n = xStreamBufferReceive(s_sb, buf, sizeof(buf),
                                        pdMS_TO_TICKS(200));
        if (n > 0) {
            if (!chunk_write(client, buf, n)) { write_err = true; break; }
            total += n;
        } else if (!s_active) {
            break;                      // ended and buffer drained
        }
    }

    int status = -1;
    if (!write_err) {
        // Terminating zero-length chunk, then read the response status.
        esp_http_client_write(client, "0\r\n\r\n", 5);
        if (esp_http_client_fetch_headers(client) >= 0)
            status = esp_http_client_get_status_code(client);
    }
    esp_http_client_close(client);
    esp_http_client_cleanup(client);
    ESP_LOGI(TAG, "recording done: %u bytes (≈%us), status %d%s, dropped %u, "
             "heap %u", (unsigned)total, (unsigned)(total / 8000), status,
             write_err ? " (write error)" : "", (unsigned)s_dropped,
             (unsigned)heap_caps_get_free_size(MALLOC_CAP_INTERNAL));
}

static void recorder_task(void *arg)
{
    (void)arg;
    for (;;) {
        xSemaphoreTake(s_begin, portMAX_DELAY);
        stream_one_call();
    }
}

void recorder_setup(void)
{
    s_sb    = xStreamBufferCreate(REC_SB_BYTES, 1);
    s_begin = xSemaphoreCreateBinary();
    if (!s_sb || !s_begin) {
        ESP_LOGE(TAG, "alloc failed — recorder disabled");
        return;
    }
    // 5 KB stack: esp_http_client + (for https) an mbedTLS session.
    if (xTaskCreate(recorder_task, "recorder", 5120, NULL, 5, NULL) != pdPASS)
        ESP_LOGE(TAG, "task create failed — recorder disabled");
}
