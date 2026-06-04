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
        memcpy(msg, m, mlen);
        msg[mlen] = '\0';
    }

    return level;
}

// ---------------------------------------------------------------------------
// ESP-IDF log hook. Compiled out on the host.
// ---------------------------------------------------------------------------
#ifdef ESP_PLATFORM

#include <stdio.h>
#include <stdarg.h>

#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"

#include "esp_log.h"

#include "stats.h"

// Previous (console) sink, so the serial log stays fully intact.
static vprintf_like_t s_console;

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

static void capture_line(int level, const char *fmt, va_list ap)
{
    if (!s_lock || xSemaphoreTake(s_lock, 0) != pdTRUE) return;
    vsnprintf(s_line, sizeof(s_line), fmt, ap);
    if (log_capture_parse(s_line, s_tag, sizeof(s_tag), s_msg, sizeof(s_msg))) {
        stats_record_error(level, s_tag, s_msg);
    }
    xSemaphoreGive(s_lock);
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

    // Only WARN/ERROR pays for capture (a va_copy + the formatting in
    // capture_line). INFO/DEBUG — the overwhelming majority, including
    // deep driver lines like the GPIO dump — fall straight through.
    if (lvl == 'E' || lvl == 'W') {
        va_list copy;
        va_copy(copy, ap);
        capture_line(lvl == 'E' ? ESP_LOG_ERROR : ESP_LOG_WARN, fmt, copy);
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
