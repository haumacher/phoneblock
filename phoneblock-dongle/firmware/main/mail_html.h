#pragma once

#include <stdbool.h>
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

// Build the GitHub release-notes ("changelog") URL for firmware `version`
// into out[cap]. Returns true and a NUL-terminated URL when `version` is a
// released form — "X.Y.Z" optionally followed by "-<suffix>" of
// [0-9A-Za-z.] (e.g. "1.4.1", "1.4.1-rc1"); the URL always points at the
// bare "X.Y.Z.md" page (release.sh guarantees it exists). Returns false and
// out[0]='\0' for dev / git-describe builds ("1.3.4-5-gabcdef", second
// hyphen) that have no release-notes page. Mirrors fwLink() in the web UI.
// A cap of 160 comfortably holds any real version. Pure/host-testable, so
// the version parsing that feeds an update mail's link is covered by
// test/test_mail_html.c.
bool mail_changelog_url(const char *version, char *out, size_t cap);
