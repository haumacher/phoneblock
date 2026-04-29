#pragma once

#include <stddef.h>

// Start the on-device HTTP server that exposes the status + config web
// UI under http://<dongle-ip>/. Idempotent; safe to call once during
// app_main() after networking is up. Runs in its own FreeRTOS task
// spawned by esp_http_server.
void web_start(void);

// Percent-decode `src` (length `src_len`, '+' treated as space) into
// `dst` of capacity `cap`. NUL-terminates. esp_http_server's
// httpd_query_key_value() and similar APIs return raw values, so
// any caller that needs the decoded form has to call this first.
void url_decode(const char *src, int src_len, char *dst, size_t cap);
