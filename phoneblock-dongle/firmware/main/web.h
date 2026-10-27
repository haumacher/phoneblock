#pragma once

#include <stddef.h>
#include <stdint.h>

// Start the web server that exposes the shared status + config UI.
// Idempotent; ESP serves on port 80, Linux on port 8080.
void web_start(void);
void web_stop(void);
void web_configure_listener(uint16_t port, const char *bind_address);
uint16_t web_listening_port(void);

// Percent-decode `src` (length `src_len`, '+' treated as space) into
// `dst` of capacity `cap`. NUL-terminates. esp_http_server's
// httpd_query_key_value() and similar APIs return raw values, so
// any caller that needs the decoded form has to call this first.
void url_decode(const char *src, int src_len, char *dst, size_t cap);
