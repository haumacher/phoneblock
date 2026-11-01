#pragma once

#include <stddef.h>
#include <stdint.h>

#include "cJSON.h"
#include "stats.h"

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

// Seconds since the call: from its stored time and `now_epoch`
// (time_sync_now_epoch(), 0 = clock not set), else — a call of this boot —
// from its uptime and `now_us` (pb_monotonic_us()). -1 when it is not known
// (a call of an earlier boot without a time, or the clock not set yet).
int64_t web_call_age_s(const stats_call_t *call, int64_t now_us, int64_t now_epoch);

// One listed call as JSON, as /api/calls lists it and the call webhook
// (webhook.c) posts it: age_s as web_call_age_s(), epoch_s the call's own
// time (0 = not known, stats_call_t.epoch_s). The caller owns the returned
// object.
cJSON *web_call_json(const stats_call_t *call, int64_t now_us,
                     int64_t now_epoch);
