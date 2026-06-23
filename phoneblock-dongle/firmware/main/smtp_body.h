#pragma once

#include <stddef.h>

// Sink for one chunk of encoded body bytes. Returns 0 on success, or a
// non-zero code to abort the encode (propagated back to the caller).
typedef int (*smtp_body_sink)(void *ctx, const char *data, size_t len);

// Encode a mail body for the SMTP DATA stream and push it through `sink`
// chunk by chunk:
//   - bare LF -> CRLF: SMTP/MIME lines end in CRLF (RFC 5321 §2.3.8); a
//     LF-only text/plain body renders as one run-on line in many clients.
//   - dot-stuffing: a line starting with '.' gets a second '.' prepended
//     (RFC 5321 §4.5.2) so body text can't be read as the "<CRLF>.<CRLF>"
//     end-of-DATA marker.
// Has no I/O of its own (the sink does the writing), so the wire encoding
// is host-testable without a TLS channel. Returns 0 on success, or the
// sink's non-zero abort code.
int smtp_encode_body(const char *body, smtp_body_sink sink, void *ctx);
