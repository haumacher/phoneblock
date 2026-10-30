#pragma once

#include <stdint.h>

// Async fire-and-forget queue for /api/report-call and the call webhook
// (webhook.c), which shares the worker.
//
// /api/report-call is the fair-use contribution that lets the server
// keep tailored compact blocklists current — but it has no time
// constraint at all: the verdict has already been delivered, the
// SIP dialog is already running. Doing it on the SIP task would add
// a second TLS handshake (300–600 ms cert-verify + RTT) onto the
// critical path between SPAM verdict and the 200 OK, where the
// Fritz!Box is timing us against its own ringing-escalation window.
//
// This module owns a small worker task that drains queued numbers
// at its own pace. Failures are logged via stats_record_error inside
// phoneblock_report_call() and are otherwise ignored — overflow,
// reboot mid-queue, or one-off TLS hiccups all just cost the server
// a slightly stale call counter, never a missed block.

void report_queue_start(void);

// Enqueue a phone number for asynchronous /api/report-call POST.
// Safe to call from any task. Non-blocking: drops the entry on
// overflow (logged once) — a SPAM-burst hot enough to fill the
// queue means several reports go missing, which is harmless.
void report_queue_enqueue(const char *phone);

// Enqueue the call webhook for the listed call with this sequence number
// (stats_set_call_listener). Same rules as report_queue_enqueue: any task,
// non-blocking, dropped with a warning on overflow.
void report_queue_enqueue_webhook(uint32_t call_seq);

// Waits until a running announcement has finished (or one about to start
// has not started within ~2 s), capped at ~32 s. For worker-side requests
// that must not run next to the SRTP stream.
void report_queue_wait_announcement(void);

// Waits only for a running announcement to stop (capped at ~30 s), without
// waiting for one to start: for a call that has already ended, whose stream
// may still be winding down after the caller's BYE.
void report_queue_wait_stream_end(void);
