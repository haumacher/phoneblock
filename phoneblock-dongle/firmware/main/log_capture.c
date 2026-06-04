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

#include "esp_log.h"

#include "stats.h"

// Previous (console) sink, so the serial log stays fully intact.
static vprintf_like_t s_console;

static int log_hook(const char *fmt, va_list ap)
{
    va_list copy;
    va_copy(copy, ap);

    // Console first, unchanged — diagnosis on the web UI must never come
    // at the cost of the serial log.
    int ret = s_console ? s_console(fmt, ap) : vprintf(fmt, ap);

    // The level letter is a literal in the format string, so we can
    // skip the expensive vsnprintf for the common INFO/DEBUG lines.
    char lvl = log_capture_level(fmt);
    if (lvl == 'E' || lvl == 'W') {
        char line[192];
        vsnprintf(line, sizeof(line), fmt, copy);
        char tag[STATS_ERROR_TAG_LEN];
        char msg[STATS_ERROR_MSG_LEN];
        char got = log_capture_parse(line, tag, sizeof(tag), msg, sizeof(msg));
        if (got == 'E' || got == 'W') {
            stats_record_error(got == 'E' ? ESP_LOG_ERROR : ESP_LOG_WARN,
                               tag, msg);
        }
    }

    va_end(copy);
    return ret;
}

void log_capture_start(void)
{
    // esp_log_set_vprintf returns the previous sink; chain to it so the
    // console keeps working. No recursion guard is needed: nothing in
    // the capture path (vsnprintf, the parser, stats_record_error) logs.
    s_console = esp_log_set_vprintf(&log_hook);
}

#endif // ESP_PLATFORM
