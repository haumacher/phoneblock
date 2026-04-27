#pragma once

// Pure-C framer for SIP-over-stream transports (TCP/TLS).
//
// SIP-over-TCP framing per RFC 3261 §7.5: a message is the header block
// (terminated by an empty line) plus exactly Content-Length bytes of
// body. UDP delivers one datagram per recv() — for stream sockets we have
// to reassemble across reads and split when several messages arrive in
// the same recv buffer.
//
// The framer owns a caller-provided buffer; bytes are appended as they
// arrive on the socket and pop'd one complete SIP message at a time.
// No socket calls, no ESP-IDF — kept testable on the host.

#include <stdbool.h>
#include <stddef.h>

typedef struct {
    char *buf;
    int   cap;
    int   have;
} sip_framer_t;

// Bind the framer to a caller-owned buffer.
void sip_framer_init(sip_framer_t *f, char *buf, int cap);

// Discard buffered data. Use after a fatal frame error or before
// reconnecting so the next bytes don't get parsed against a stale prefix.
void sip_framer_reset(sip_framer_t *f);

// Append bytes received from the socket. Returns:
//    0 on success
//   -1 if the buffer is full — an oversized SIP message is unrecoverable
//      on a stream because we can't tell where the next message begins,
//      so the caller must drop the connection.
int sip_framer_append(sip_framer_t *f, const void *data, int len);

// Try to copy one complete SIP message into out (NUL-terminated). On
// success the message is removed from the framer's buffer; any trailing
// bytes that already belong to the next message stay buffered.
//
// Returns:
//    >0  message length (excl. NUL terminator)
//     0  incomplete — need more data
//    -1  malformed (e.g. negative / non-numeric Content-Length, or the
//        completed message is larger than out_cap). Caller should treat
//        the connection as broken and reset the framer.
int sip_framer_pop(sip_framer_t *f, char *out, int out_cap);
