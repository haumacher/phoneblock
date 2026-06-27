#pragma once

#include <stddef.h>

// Pure, dependency-free string builders for assembling the HTML status mail
// body in mail.c. Each appends onto body[len], writes a terminating '\0' at
// the new end, and returns the new length; none ever writes past `cap` (the
// loop bounds stop below cap with room for the terminator), so a body that
// fills up truncates cleanly instead of overflowing, and the buffer stays a
// valid C string after any call — the assembled body is sent as a C string,
// so a missing terminator would stream trailing uninitialised heap into the
// mail. Factored out of mail.c so the escaping — which
// runs over caller-controlled text (SIP/Fritz!Box display names, log
// messages) and therefore guards the mail against HTML injection — is
// host-testable in isolation (see test/test_mail_html.c).

// Append the raw bytes of `s` (already safe for the target context).
size_t append_str(char *body, size_t cap, size_t len, const char *s);

// Append `s` with the five HTML-significant characters (& < > " ') escaped.
size_t append_html_escaped(char *body, size_t cap, size_t len, const char *s);

// Append `s` percent-encoded for a URL path segment (e.g. a phone number,
// whose leading '+' must become %2B).
size_t append_url_encoded(char *body, size_t cap, size_t len, const char *s);
