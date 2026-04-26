#pragma once

// Async fire-and-forget queue for /api/report-call.
//
// /api/report-call is the fair-use contribution that lets the server
// keep tailored compact blocklists current — but it has no time
// constraint at all: the verdict has already been delivered, the
// SIP dialog is already running. Doing it on the SIP task would add
// a second TLS handshake (300–600 ms cert-verify + RTT) onto the
// critical path between SPAM verdict and 200 OK / 486, where the
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
