#pragma once

// Pure-C SIP string helpers, split out of sip_register.c so they can be
// unit-tested on the host (see firmware/test/). No ESP-IDF, no lwip, no
// mbedtls — only <string.h> / <strings.h>.
//
// All char-writing helpers take an explicit capacity and NUL-terminate
// within that capacity. Inputs that are too long are silently truncated;
// the test suite pins down that behavior.

#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>

// Locate the first occurrence of "Name:" in the message after the start
// line and return a pointer to the first non-whitespace character of the
// value. NULL if the header is absent. Lookup is case-insensitive.
const char *find_header(const char *msg, int msg_len, const char *name);

// Copy a header's value (from pointer p, bounded by end) up to but not
// including CR/LF into out. Writes at most cap-1 bytes + NUL. Returns
// the number of bytes written (excluding NUL).
int header_value(const char *p, const char *end, char *out, int cap);

// Extract the request method (first whitespace-delimited token on the
// request line). Writes at most cap-1 bytes + NUL. Returns the number of
// bytes written.
int parse_method(const char *pkt, int len, char *method, int cap);

// Parse the numeric status from a response line "SIP/2.0 NNN Reason…".
// Returns -1 if the first line doesn't look like a status line.
int parse_status_code(const char *resp, int len);

// Extract the numeric part of the CSeq header. 0 if absent or malformed.
uint32_t parse_cseq(const char *req, int req_len);

// Copy the Call-ID header value (trimmed of trailing whitespace) into
// out. Writes at most cap-1 bytes + NUL; out[0] = '\0' if the header is
// absent.
void parse_call_id(const char *req, int req_len, char *out, int cap);

// Find the ";tag=<value>" parameter inside a header value and copy its
// value. out[0] = '\0' if no tag is present.
void parse_tag(const char *hdr_val, int val_len, char *out, int cap);

// Extract the URI from a From/To/Contact header value, stripping display
// name, <…> brackets, and URI parameters. Example:
//   "Alice" <sip:alice@example.com;transport=udp>;tag=xyz → sip:alice@example.com
void parse_uri(const char *hdr_val, int val_len, char *out, int cap);

// Extract the user part of a SIP/SIPS/TEL URI: "sip:01234@host" → "01234".
// Returns the length written (0 if the URI is malformed / has no user).
int user_from_uri(const char *uri, char *out, int cap);

// Extract the display name of a From/To header. Handles both quoted
// ("Name Surname") and unquoted (single token) forms, trims whitespace,
// returns 0 and empty out if no display name is present.
int parse_display_name(const char *hdr_val, int val_len, char *out, int cap);

// True if the string looks like a human rendering of a phone number —
// digits plus any of ' +-/().'. Used to distinguish "display name is
// really just the number" from "display name is an actual name".
bool is_phone_number_like(const char *s);

// True if the normalized number is plausible as an external phone number
// to query. Excludes Fritz!Box dial codes (**NN), feature codes
// (*21#...), and anything that doesn't start with a digit or '+'.
bool looks_dialable(const char *number);

// Minimal German-centric normalization for the PhoneBlock API:
// international "+49…" / "0049…" → national "0…". Also strips
// whitespace/dashes/parens. The server normalizes further.
void normalize_de(const char *raw, char *out, int cap);

// Case-insensitive compare of two non-empty Call-IDs. Returns false if
// either side is NULL or empty.
bool same_call_id(const char *a, const char *b);
