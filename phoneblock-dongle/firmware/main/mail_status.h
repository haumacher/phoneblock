#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "mail_calls.h"
#include "stats.h"

// Assemble the HTML status-mail body into body[cap]. Returns the number of
// bytes written, excluding the terminating NUL.
size_t mail_status_build_html(char *body, size_t cap,
                              const stats_counters_t *cnt,
                              const mail_calls_window_t *calls, bool since_line,
                              const stats_error_t *errs, int nerr,
                              int64_t since_us, int64_t *newest_us,
                              bool include_log);
