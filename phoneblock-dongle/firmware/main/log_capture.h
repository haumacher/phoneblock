#pragma once

#include <stddef.h>

// Global log capture: mirrors every WARN/ERROR line the firmware emits
// — our own modules and the ESP-IDF libraries alike — into the stats
// error ring, which the web UI shows as the "Protokoll" (log) panel.
// This is what makes field diagnosis possible: a customer (or we) can
// read the actual warnings/errors off the dongle's web page instead of
// needing a serial console.
//
// The mechanism is a single esp_log_set_vprintf() hook, so there is
// nothing to remember at individual call sites: anything logged at
// WARN/ERROR shows up automatically. To surface a new unusual condition,
// log it at the right level — no extra plumbing.

// Install the log hook. Call once, after stats_setup(). ESP-only.
void log_capture_start(void);

// --- Host-testable log-line parsing (no ESP-IDF dependencies) -------
// Split out so test/test_log_capture.c can pin the fragile parsing on
// the host without an ESP-IDF build.

// Severity letter of an ESP-IDF formatted log line ('E','W','I','D','V'),
// skipping an optional leading ANSI colour escape, or '\0' if the first
// non-escape character is not a known level. Cheap: looks only at the
// head, used as the pre-filter before the expensive vsnprintf.
char log_capture_level(const char *line);

// Parse a fully formatted ESP-IDF log line of the shape
//   [ESC[..m]<L> (<timestamp>) <tag>: <message>[ESC[0m][\n]
// into `tag` and `message` (NUL-terminated; trailing ANSI reset and
// newline stripped). Returns the level letter, or '\0' if the line is
// not parseable. The caller decides which levels to keep.
char log_capture_parse(const char *line,
                       char *tag, size_t tag_cap,
                       char *msg, size_t msg_cap);

// True if a parsed (level, tag, message) is known-benign ESP-IDF library
// noise that should NOT reach the web "Protokoll" ring. The line is still
// printed to the serial console — this only suppresses the user-facing
// mirror. Matching is deliberately narrow (exact tag + message substring,
// at a specific level) so a genuine error from the same component still
// surfaces. `level` is the letter from log_capture_parse ('W','E',…).
int log_capture_suppressed(char level, const char *tag, const char *msg);
