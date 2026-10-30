#pragma once

#include <stdbool.h>
#include <stdint.h>

// Call webhook (#644): one HTTP POST per listed call to a URL the user sets
// in the web UI, so Home Assistant, ioBroker or Node-RED learn about calls
// without polling /api/calls (which a machine client cannot reach at all
// once the PhoneBlock login gate is on). The body is the call as /api/calls
// lists it (web_call_json()).
//
// Deliberately small, since it runs on the ESP32 too: plain http only (no
// TLS next to a running announcement, see report_queue.c), the host must be
// in the home network (web_host_locality()), short timeout, no retry. It
// runs on the report_queue worker; a failure is a WARN in the log panel.

// Registers the stats call listener that queues the webhook while a URL
// is configured. Call after report_queue_start().
void webhook_start(void);

// Whether `url` may be stored as the webhook URL: empty (off), or a plain
// http URL netloc_http_url_host() accepts that fits CONFIG_WEBHOOK_URL_CAP.
// The home-network check needs DNS and happens on delivery.
bool webhook_url_valid(const char *url);

// Posts the listed call with this sequence number to the configured URL.
// Runs on the report_queue worker (report_queue_enqueue_webhook()).
void webhook_deliver(uint32_t call_seq);
