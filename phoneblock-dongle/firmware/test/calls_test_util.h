#pragma once

// Shared by the host tests of the persistent call list (stats.c, #589): a
// call file in a fresh temp directory, and a count of the WARN/ERROR lines
// stats.c logs.

#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "platform.h"
#include "stats.h"

static char s_calls_dir[256];
static char s_calls_file[300];
static int  s_log_warns, s_log_errors;

static inline void count_log(char level, const char *tag, const char *fmt, va_list args)
{
    (void)tag; (void)fmt; (void)args;
    if (level == 'W') s_log_warns++;
    if (level == 'E') s_log_errors++;
}

// Creates the temp directory, opens the call list in it (a first start: the
// file is created) and counts log lines from here on.
static inline void calls_test_open(void)
{
    const char *tmp = getenv("TMPDIR");
    snprintf(s_calls_dir, sizeof(s_calls_dir), "%s/pb-calls-XXXXXX",
             tmp && tmp[0] ? tmp : "/tmp");
    if (!mkdtemp(s_calls_dir)) { perror("mkdtemp"); exit(1); }
    snprintf(s_calls_file, sizeof(s_calls_file), "%s/%s", s_calls_dir, STATS_CALLS_FILE);
    pb_log_set_sink(count_log);
    if (!stats_calls_open(s_calls_file)) { fprintf(stderr, "open failed\n"); exit(1); }
}

// A reboot of the list: the file closed and opened again.
static inline bool calls_test_reboot(void)
{
    stats_calls_close();
    return stats_calls_open(s_calls_file);
}

static inline void calls_test_cleanup(void)
{
    stats_calls_close();
    unlink(s_calls_file);
    rmdir(s_calls_dir);
}
