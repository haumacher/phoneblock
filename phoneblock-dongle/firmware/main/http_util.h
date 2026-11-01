#pragma once

#include <stdbool.h>
#include <stddef.h>

#ifdef ESP_PLATFORM
#include "esp_http_client.h"
#endif

// Return a stable User-Agent string for all outbound HTTP(S) calls,
// formatted "PhoneBlock-Dongle/<version> (<platform>) (<device id>)", the
// platform comment from pb_platform_tag(), e.g. "ESP32-C3" or "Linux; HA".
// Without a device id it is just "PhoneBlock-Dongle/<version>". The
// version comes from pb_firmware_version() at first call and is then
// cached. Callers of pb_http_request() pass it as a "User-Agent" header
// (the platform layer can't reach config / this module).
const char *http_util_user_agent(void);

#ifdef ESP_PLATFORM
// Convenience: set the User-Agent header on an esp_http_client handle
// (crashreport.c, which streams from flash and stays on esp_http_client).
void http_util_set_user_agent(esp_http_client_handle_t client);
#endif

// Copies `url` into `out` (capacity `cap`, always NUL-terminated, clamped
// when too small) as it may appear in a log line: scheme, host, port and
// path only. A user-info part ("user:pass@") is dropped, and so are the
// query and the fragment, which may carry secrets or hashed numbers.
void http_url_for_log(const char *url, char *out, size_t cap);

// Renders the first `len` bytes of a server response body (need not be
// NUL-terminated; `body` may be NULL when `len` is 0) as a short, single-line
// summary for an error log line, into `out` (always NUL-terminated):
//
//   - empty body:          `empty body`
//   - HTML (first non-blank byte '<'): `HTML page "<title>"`, the title
//     whitespace-collapsed, or `HTML page, N bytes` without one.
//     The full page would bloat the log panel and make every slightly
//     different error page its own diagnostics signature.
//   - other text, with `echo_text`: the body in quotes, line breaks and other
//     control characters collapsed to single spaces, with a trailing "..."
//     when cut.
//   - other text, without `echo_text`: `N bytes`, for responses whose
//     content must not reach the log (a login ticket, an account name).
//
// Text and title are clamped to HTTP_BODY_SUMMARY_TEXT_MAX bytes, on a UTF-8
// character boundary: the web UI's log ring keeps 128 bytes per line
// (STATS_ERROR_MSG_LEN), and the request and URL come before the summary.
#define HTTP_BODY_SUMMARY_TEXT_MAX 60
void http_body_summary(const char *body, size_t len, bool echo_text,
                       char *out, size_t cap);

// What a failed (or successful) GET of an optional, published file means for
// the caller's retry policy: the request reached the server and got the file
// (OK), the server said definitely that there is no such file (ABSENT — a
// 404, e.g. the i18n bundle of a dev build that was never published, #673),
// or anything else — no connection, a timeout, a 5xx, another 4xx — which may
// well pass on a later attempt (RETRY).
typedef enum {
    HTTP_FETCH_OK,
    HTTP_FETCH_ABSENT,
    HTTP_FETCH_RETRY,
} http_fetch_outcome_t;

// Classifies a request by its transport result (PB_HTTP_OK or the
// platform's error, platform.h) and its HTTP status (ignored unless the
// transport succeeded).
http_fetch_outcome_t http_fetch_classify(int transport_result, int http_status);
