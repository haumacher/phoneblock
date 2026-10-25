#include "log_capture.h"

#include <stdarg.h>
#include <stdio.h>

#include "config.h"
#include "platform.h"
#include "stats.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

// Linux counterpart of the ESP-IDF log hook in log_capture.c. pb_log_* hand
// level, tag and arguments to this sink directly (see platform.h), so the
// same WARN/ERROR ring fills without the text parser, which only exists for
// the formatted lines ESP-IDF's libraries deliver. The same rules apply:
// WARN/ERROR always, INFO only while the user enabled it for troubleshooting,
// and the suppression list. The console line is printed regardless.
static void log_sink(char level, const char *tag, const char *fmt,
                     va_list args)
{
    int ring_level;
    if      (level == 'E') ring_level = STATS_LOG_LEVEL_ERROR;
    else if (level == 'W') ring_level = STATS_LOG_LEVEL_WARN;
    else if (level == 'I' && config_log_info()) ring_level = STATS_LOG_LEVEL_INFO;
    else return;

    // Linux threads have ample stack, so the line is formatted locally;
    // stats_record_error() copies it under the stats lock.
    char message[STATS_ERROR_MSG_LEN];
    vsnprintf(message, sizeof(message), fmt, args);
    if (log_capture_suppressed(level, tag, message)) return;
    stats_record_error(ring_level, tag, message);
}

void log_capture_start(void)
{
    pb_log_set_sink(log_sink);
}
