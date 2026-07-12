#pragma once

#include <stddef.h>

// Normalisation of Fritz!Box call-barring entries into a form the
// PhoneBlock /api/rate endpoint accepts. Extracted from sync.c as a pure,
// host-testable unit (see test/test_phone_norm.c) — issue #469.
//
// The server accepts neither a wildcard barring pattern (e.g. "+43*") nor
// a non-E.164 number (e.g. "1727905225"); both used to be forwarded
// verbatim, rejected with HTTP 400, kept in the box and retried on every
// sync run, flooding the log ring. The classification below lets the sync
// path drop such entries *before* they reach the network.

typedef enum {
    // `out` holds a normalised E.164 number ("+49…") safe to submit.
    PHONE_RATEABLE = 0,
    // Wildcard pattern or a value that cannot be normalised to E.164 —
    // must not be sent to /api/rate. `out` is left empty.
    PHONE_SKIP,
} phone_class_t;

// Classify and, when rateable, normalise `in` into `out` (capacity `cap`,
// always NUL-terminated). Rules:
//   - empty / NULL                → PHONE_SKIP
//   - contains '*' (wildcard)     → PHONE_SKIP
//   - "+<digits>"                 → passed through (already E.164)
//   - "00<digits>"               → "+<digits>"
//   - "0<digits>"                → "+49<digits>" (German national)
//   - bare "<digits>" (no + / 0)  → PHONE_SKIP (no country context)
//   - any non-digit in the number part → PHONE_SKIP
phone_class_t phone_normalise(const char *in, char *out, size_t cap);
