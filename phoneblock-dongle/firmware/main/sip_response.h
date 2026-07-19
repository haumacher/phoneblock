#pragma once

// Builds SIP responses that echo the routing headers (Via/From/To/Call-ID/
// CSeq) from a received request. Split out of sip_register.c so the buffer
// arithmetic can be unit-tested on the host (see test/test_sip_response.c):
// a hostile request with oversized headers must never overrun the fixed
// response buffer.

// Assemble a SIP response into `out` (capacity `out_cap`, including the
// terminating NUL). Echoes Via/From/To/Call-ID/CSeq from `req`, appends a
// Contact built from `contact_user`/`contact_host`/`contact_port`, the
// standard Allow/User-Agent, and either an SDP `body` (NULL → none) with a
// matching Content-Length or an empty body.
//
// `our_tag` must be non-NULL: for a dialog-forming request whose To carries
// no tag yet, ";tag=<our_tag>" is appended to the echoed To. Callers that
// need a fresh stateless tag (e.g. OPTIONS) generate one and pass it in.
//
// Returns the number of bytes written (always strictly < out_cap). The
// result is NUL-terminated. The function never writes outside `out`, no
// matter how long the request's headers are — every field that does not fit
// in the remaining space is dropped whole rather than truncated mid-write.
int sip_response_build(const char *req, int req_len,
                       int status, const char *reason,
                       const char *our_tag, const char *body,
                       const char *contact_user, const char *contact_host,
                       int contact_port,
                       char *out, int out_cap);
