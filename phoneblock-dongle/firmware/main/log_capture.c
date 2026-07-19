#include "log_capture.h"

#include <string.h>

// ---------------------------------------------------------------------------
// Host-testable parsing. No ESP-IDF dependencies so test/test_log_capture.c
// can exercise it on the host.
//
// ESP-IDF's LOG_FORMAT macro expands to
//   LOG_COLOR_<L> "<L> (%lu) %s: " format LOG_RESET_COLOR "\n"
// i.e. an optional ANSI colour escape, the level letter, the timestamp
// in parentheses, then "<tag>: <message>". The colour escape is present
// because the build has CONFIG_LOG_COLORS=y, but we tolerate its absence
// too.
// ---------------------------------------------------------------------------

// Skip an optional leading ANSI colour escape ("ESC[...m").
static const char *skip_ansi(const char *p)
{
    if (*p == '\033') {
        while (*p && *p != 'm') p++;
        if (*p == 'm') p++;
    }
    return p;
}

static int is_level(char c)
{
    return c == 'E' || c == 'W' || c == 'I' || c == 'D' || c == 'V';
}

char log_capture_level(const char *line)
{
    if (!line) return '\0';
    char c = *skip_ansi(line);
    return is_level(c) ? c : '\0';
}

int log_strip_ansi(char *s, int len)
{
    int w = 0;
    for (int r = 0; r < len; ) {
        if (s[r] == '\033') {            // ESC: drop up to and including 'm'
            r++;
            while (r < len && s[r] != 'm') r++;
            if (r < len) r++;            // skip the terminating 'm'
        } else {
            s[w++] = s[r++];
        }
    }
    return w;
}

// Largest length <= len whose prefix ends on a UTF-8 character boundary.
// A hard byte-truncation at msg_cap can cut a multibyte sequence — an
// arrow in our own messages, or an umlaut in an external string like a
// SIP display name — leaving a dangling continuation byte. That byte is
// invalid UTF-8, which makes the /api/errors JSON unparseable and breaks
// the web "Protokoll" panel. Drop any incomplete trailing character.
static size_t utf8_trim(const char *s, size_t len)
{
    if (len == 0) return 0;
    // Walk back over trailing continuation bytes (10xxxxxx) to the lead.
    size_t i = len, cont = 0;
    while (i > 0 && ((unsigned char)s[i - 1] & 0xC0) == 0x80) { i--; cont++; }
    if (i == 0) return len;            // no lead byte in range — leave as-is
    unsigned char lead = (unsigned char)s[i - 1];
    size_t need;
    if      (lead < 0x80)           need = 1;   // ASCII
    else if ((lead & 0xE0) == 0xC0) need = 2;
    else if ((lead & 0xF0) == 0xE0) need = 3;
    else if ((lead & 0xF8) == 0xF0) need = 4;
    else                            need = 1;   // invalid lead byte
    if (cont + 1 == need) return len;  // trailing character is complete
    return i - 1;                       // drop the incomplete trailing char
}

char log_capture_parse(const char *line,
                       char *tag, size_t tag_cap,
                       char *msg, size_t msg_cap)
{
    if (tag_cap) tag[0] = '\0';
    if (msg_cap) msg[0] = '\0';
    if (!line) return '\0';

    const char *p = skip_ansi(line);
    char level = *p;
    if (!is_level(level)) return '\0';

    // Skip past the timestamp: "<L> (<ts>) " ends at the first ") ".
    const char *after_ts = strstr(p, ") ");
    if (!after_ts) return '\0';
    p = after_ts + 2;

    // "<tag>: <message>"
    const char *colon = strstr(p, ": ");
    if (!colon) return '\0';

    if (tag_cap) {
        size_t tlen = (size_t)(colon - p);
        if (tlen >= tag_cap) tlen = tag_cap - 1;
        memcpy(tag, p, tlen);
        tag[tlen] = '\0';
    }

    if (msg_cap) {
        // Message runs to the trailing ANSI reset / newline, if any.
        const char *m = colon + 2;
        const char *end = m;
        while (*end && *end != '\n' && *end != '\033') end++;
        size_t mlen = (size_t)(end - m);
        if (mlen >= msg_cap) mlen = msg_cap - 1;
        mlen = utf8_trim(m, mlen);
        memcpy(msg, m, mlen);
        msg[mlen] = '\0';
    }

    return level;
}

// ---------------------------------------------------------------------------
// Library-noise suppression (host-testable).
//
// A few ESP-IDF libraries log routine, self-recovering conditions at WARN
// because they have no INFO-level path for them. Mirrored into the web
// "Protokoll" panel those read as faults and alarm users even though
// nothing is wrong. Drop the specific lines from the ring — they still go
// to the serial console (log_hook forwards every line there), so they stay
// available for real debugging.
//
// Keep this list SHORT and SPECIFIC: an exact tag plus a message substring,
// at one level. It is the opposite of a blanket per-tag mute — a genuine
// error from the same component must still surface.
struct log_suppress {
    char        level;   // level letter this rule applies to ('W', 'E', …)
    const char *tag;     // exact ESP-IDF tag
    const char *needle;  // substring that must appear in the message
};

static const struct log_suppress k_suppress[] = {
    // esp_http_server logs this WARN every time a client socket drops
    // mid-recv: a browser closing a keep-alive connection, an RST, or an
    // idle EAGAIN timeout. Routine for a web server handing out the
    // dongle's own UI; not a fault. The library hard-codes it at WARN with
    // no INFO path, so the ring is the only place to filter it.
    // (esp-idf httpd_txrx.c: httpd_sock_err("recv", …) →
    //  "httpd_sock_err: error in recv : <errno>".)
    { 'W', "httpd_txrx", "error in recv" },
    // Same story on the send side: the server logs this WARN when a client
    // drops the socket while a response is being written — a browser closing
    // an SSE log stream (/api/log/stream), navigating away, or an idle
    // keep-alive RST (errno 9 = EBADF / 104 = ECONNRESET). Routine, not a
    // fault. (esp-idf httpd_txrx.c: httpd_sock_err("send", …) →
    //  "httpd_sock_err: error in send : <errno>".)
    { 'W', "httpd_txrx", "error in send" },
};

int log_capture_suppressed(char level, const char *tag, const char *msg)
{
    if (!tag || !msg) return 0;
    for (size_t i = 0; i < sizeof(k_suppress) / sizeof(k_suppress[0]); i++) {
        const struct log_suppress *s = &k_suppress[i];
        if (level == s->level && strcmp(tag, s->tag) == 0 && strstr(msg, s->needle))
            return 1;
    }
    return 0;
}

// ---------------------------------------------------------------------------
// ESP-IDF log hook. Compiled out on the host.
// ---------------------------------------------------------------------------
#ifdef ESP_PLATFORM

#include <stdio.h>
#include <stdarg.h>
#include <stdlib.h>

#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/message_buffer.h"

#include "esp_log.h"

#include "config.h"
#include "stats.h"

// Previous (console) sink, so the serial log stays fully intact.
static vprintf_like_t s_console;

// --- Live full-log stream state --------------------------------------
// One subscriber drains every formatted line through a message buffer.
// s_stream_on is the hot-path gate (read once per logged line); the
// buffer + scratch exist only while a client is attached. State changes
// (open/close) and the per-line tee both run under s_lock, so a tee
// never touches a buffer that close() is freeing.
#define LOG_STREAM_BUF_BYTES 6144   // message-buffer capacity for bursts

static volatile bool         s_stream_on;
static MessageBufferHandle_t s_stream_buf;
static char                 *s_stream_scratch;   // LOG_STREAM_LINE_MAX bytes
static uint32_t              s_stream_dropped;    // lines lost to a full buffer

// The hook runs on the stack of whatever task logs the line, and the
// formatting scratch dominates that cost. Keeping line/tag/msg here as
// file-static (rather than on capture_line's stack) is what makes a
// WARN/ERROR cost essentially the same stack as the vprintf the console
// does anyway: ~340 B of buffers off the stack instead of in the frame.
// That matters because some warnings are logged from small-stack tasks
// — wifi.c warns from the 2304-byte esp_event task — where an extra
// ~380 B frame would overflow. A FreeRTOS mutex guards the shared
// scratch; a non-blocking take means a concurrent or re-entrant capture
// simply skips the ring entry (the line is already on the console)
// rather than blocking the logging task.
static SemaphoreHandle_t s_lock;
static char s_line[192];
static char s_tag[STATS_ERROR_TAG_LEN];
static char s_msg[STATS_ERROR_MSG_LEN];

// Tee one log line into the live stream (caller holds s_lock). Re-formats
// at full width into the heap scratch — the 192 B ring buffer above would
// clip long lines (e.g. a SIP dump) far shorter than a stream viewer wants
// — strips the console's ANSI colour, and enqueues it. A full buffer drops
// the line and bumps a counter the reader surfaces.
static void stream_tee_locked(const char *fmt, va_list ap)
{
    if (!s_stream_on || !s_stream_buf || !s_stream_scratch) return;
    int n = vsnprintf(s_stream_scratch, LOG_STREAM_LINE_MAX, fmt, ap);
    if (n <= 0) return;
    if (n >= LOG_STREAM_LINE_MAX) n = LOG_STREAM_LINE_MAX - 1;
    n = log_strip_ansi(s_stream_scratch, n);
    if (n <= 0) return;
    if (xMessageBufferSend(s_stream_buf, s_stream_scratch, (size_t)n, 0) == 0)
        s_stream_dropped++;
}

static void capture_line(char lvl, int level, bool stream,
                         const char *fmt, va_list ap)
{
    if (!s_lock || xSemaphoreTake(s_lock, 0) != pdTRUE) return;
    if (level) {
        va_list a1;
        va_copy(a1, ap);
        vsnprintf(s_line, sizeof(s_line), fmt, a1);
        va_end(a1);
        if (log_capture_parse(s_line, s_tag, sizeof(s_tag), s_msg, sizeof(s_msg))
                && !log_capture_suppressed(lvl, s_tag, s_msg)) {
            stats_record_error(level, s_tag, s_msg);
        }
    }
    if (stream) {
        va_list a2;
        va_copy(a2, ap);
        stream_tee_locked(fmt, a2);
        va_end(a2);
    }
    xSemaphoreGive(s_lock);
}

// --- Live-stream subscriber API (see log_capture.h) ------------------

bool log_stream_open(void)
{
    if (!s_lock) return false;
    MessageBufferHandle_t mb = xMessageBufferCreate(LOG_STREAM_BUF_BYTES);
    char *scratch = malloc(LOG_STREAM_LINE_MAX);
    if (!mb || !scratch) {
        if (mb) vMessageBufferDelete(mb);
        free(scratch);
        return false;
    }
    bool ok = false;
    xSemaphoreTake(s_lock, portMAX_DELAY);
    if (!s_stream_on) {
        s_stream_buf     = mb;
        s_stream_scratch = scratch;
        s_stream_dropped = 0;
        s_stream_on      = true;   // publish last: gates the tee
        ok = true;
    }
    xSemaphoreGive(s_lock);
    if (!ok) { vMessageBufferDelete(mb); free(scratch); }  // lost the race
    return ok;
}

void log_stream_close(void)
{
    if (!s_lock) return;
    MessageBufferHandle_t mb = NULL;
    char *scratch = NULL;
    xSemaphoreTake(s_lock, portMAX_DELAY);
    if (s_stream_on) {
        s_stream_on      = false;  // retract first: no tee past this point
        mb               = s_stream_buf;     s_stream_buf = NULL;
        scratch          = s_stream_scratch; s_stream_scratch = NULL;
    }
    xSemaphoreGive(s_lock);
    if (mb) vMessageBufferDelete(mb);   // safe: tee is gated off, reader is us
    free(scratch);
}

size_t log_stream_read(char *out, size_t cap, uint32_t timeout_ms)
{
    if (!s_stream_buf || cap == 0) return 0;
    // Surface any overflow since the last read before the next real line.
    uint32_t dropped = 0;
    if (xSemaphoreTake(s_lock, 0) == pdTRUE) {
        dropped = s_stream_dropped;
        s_stream_dropped = 0;
        xSemaphoreGive(s_lock);
    }
    if (dropped) {
        int n = snprintf(out, cap,
                         "... %lu log line(s) dropped (stream overflow)\n",
                         (unsigned long)dropped);
        return (n > 0 && (size_t)n < cap) ? (size_t)n : 0;
    }
    return xMessageBufferReceive(s_stream_buf, out, cap,
                                 pdMS_TO_TICKS(timeout_ms));
}

// STACK SIZING — important. This hook runs on the stack of whatever task
// emits the log line, and a global esp_log_set_vprintf hook unavoidably
// adds a frame there: on Xtensa GCC does NOT tail-call the console
// hand-off below (windowed ABI — verified in the disassembly: callx8 +
// retw.n), so log_hook keeps a ~64 B frame on EVERY log call, INFO
// included; WARN/ERROR adds capture_line's small frame on top (the ~340 B
// of format scratch is file-static above, not on the stack, so it is not
// doubled with the console's vprintf). Consequence: every task that logs
// must carry that headroom. Most do; the few deliberately small ones that
// log are sized up explicitly (status_led, fw_reboot). The esp_event task
// (CONFIG_ESP_SYSTEM_EVENT_TASK_STACK_SIZE, 2304 B) warns from wifi.c and
// is the next candidate if a wifi warning ever overflows it.
static int log_hook(const char *fmt, va_list ap)
{
    // Level detection inlined (no call) to keep this frame minimal. The
    // level letter is a literal in the format string; skip an optional
    // leading ANSI colour escape first.
    const char *p = fmt;
    if (*p == '\033') { while (*p && *p != 'm') p++; if (*p) p++; }
    char lvl = *p;

    // WARN/ERROR are always captured. INFO only when the user enabled it
    // for troubleshooting (config_log_info) — checked after the W/E test
    // so the common path pays nothing extra. Either way capture_line's
    // ~48 B frame is the only added stack (the format scratch is static),
    // which the small-stack tasks have margin for.
    int level = 0;
    if      (lvl == 'E') level = ESP_LOG_ERROR;
    else if (lvl == 'W') level = ESP_LOG_WARN;
    else if (lvl == 'I' && config_log_info()) level = ESP_LOG_INFO;

    // A live subscriber gets *every* line regardless of level — that is the
    // point of "what the serial console shows". s_stream_on is a single
    // volatile read when nobody is streaming (the common case).
    bool stream = s_stream_on;
    if (level || stream) {
        va_list copy;
        va_copy(copy, ap);
        capture_line(lvl, level, stream, fmt, copy);
        va_end(copy);
    }

    return s_console(fmt, ap);
}

void log_capture_start(void)
{
    s_lock = xSemaphoreCreateMutex();
    // esp_log_set_vprintf returns the previous sink; chain to it so the
    // console keeps working. Guarantee it is non-NULL so log_hook can
    // call it unconditionally (an `?:` there would block the tail-call).
    s_console = esp_log_set_vprintf(&log_hook);
    if (!s_console) s_console = &vprintf;
}

#endif // ESP_PLATFORM
