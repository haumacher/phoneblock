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
// always NUL-terminated). `dial_prefix` is the line's country in "+NN" form
// (config_dial_prefix()); the trunk and international prefixes that go with
// it come from dial_rules_for(). Rules, with a German line as the example:
//   - empty / NULL                → PHONE_SKIP
//   - contains '*' (wildcard)     → PHONE_SKIP
//   - "+<digits>"                 → passed through (already E.164)
//   - "<intl><digits>" ("0049…")  → "+<digits>"
//   - "<trunk><digits>" ("030…")  → "<dial_prefix><digits>"
//   - bare "<digits>", country with no trunk prefix (Italy, Denmark, …)
//                                 → "<dial_prefix><digits>", leading zero kept
//   - bare "<digits>", country with a trunk prefix → PHONE_SKIP (ambiguous)
//   - any non-digit in the number part → PHONE_SKIP
//
// The last two differ from normalize_e164(), which expands a bare national
// number instead of skipping it: these entries are typed into the Fritz!Box
// by hand and a guess would rate a number nobody called, whereas a caller ID
// arrives in canonical form.
//
// The prefix is passed in rather than read from config so the unit stays
// pure and host-testable; callers use config_dial_prefix().
phone_class_t phone_normalise(const char *in, char *out, size_t cap,
                              const char *dial_prefix);
