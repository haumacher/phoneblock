#pragma once

#include <stddef.h>
#include <stdbool.h>
#include <stdint.h>

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

// --- Live full-log stream (ESP-only) --------------------------------
// Beyond the 32-entry WARN/ERROR ring, one client at a time can follow
// the *complete* log live: every formatted line that reaches the serial
// console, all levels, ANSI colour stripped. The HTTP handler in web.c
// drives this:
//
//   log_stream_open()  reserves the single slot and allocates the buffer;
//                      returns false if a stream is already active or out
//                      of memory.
//   log_stream_read()  blocks up to timeout_ms for the next line, copies
//                      it into out (cap must be >= LOG_STREAM_LINE_MAX),
//                      returns its byte count — 0 on timeout, when the
//                      caller should emit a heartbeat to detect a gone
//                      client.
//   log_stream_close() releases the slot and frees the buffer.
//
// While a stream is open, the log hook tees each line into it. With none
// open the hot-path cost is a single bool read per logged line.
#define LOG_STREAM_LINE_MAX 1024   // bytes mirrored per line (longer clipped)

bool   log_stream_open(void);
size_t log_stream_read(char *out, size_t cap, uint32_t timeout_ms);
void   log_stream_close(void);

// Strip in-place every ANSI escape ("ESC[...m") from the first `len`
// bytes of `s`, returning the new length. Host-testable; used to turn a
// colourised console line into plain text for the stream and to pin the
// behaviour in test/test_log_capture.c.
int log_strip_ansi(char *s, int len);

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
