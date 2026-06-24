#pragma once

#include <stdint.h>
#include <stddef.h>

// Experimental real-time call recorder. When config_rec_url() is set, a
// background task streams the caller's incoming audio (8 kHz G.711 A-law)
// to that endpoint via a single chunked HTTP/HTTPS PUT per call.
//
// The RTP task drives it. To keep the 20 ms RTP cadence free of network
// latency, the producer/consumer are decoupled: recorder_write() only
// enqueues bytes into a stream buffer (non-blocking, drops on overflow),
// and a dedicated recorder task does all the socket I/O. All entry points
// are no-ops when recording is disabled, so callers need not check.
void recorder_setup(void);                    // create the task; call once at boot

// Begin/stream/end one call. `peer` (caller number, may be NULL) is only a
// filename hint. recorder_write() takes raw A-law bytes (the inbound RTP
// payload). recorder_call_end() flushes and finalizes the upload.
void recorder_call_begin(const char *peer);
void recorder_write(const uint8_t *alaw, size_t n);
void recorder_call_end(void);
