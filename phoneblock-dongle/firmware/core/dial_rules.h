#pragma once

#include <stddef.h>

// National dialling grammar per country, keyed by E.164 country calling
// code — the data `normalize_e164()` and `phone_normalise()` need to turn a
// locally-formatted caller ID into E.164 (issue #505, see also #432).
//
// Knowing the country code alone is not enough. Two other prefixes vary,
// and assuming Germany's is wrong for a large part of the world:
//
//   country          dial   trunk   international escape
//   Germany, UK, FR  +49…   "0"     "00"
//   Italy, Denmark   +39    (none)  "00"    ← the leading 0 is part of the
//   Czechia          +420   (none)  "00"      number, must NOT be stripped
//   NANP (US/CA/…)   +1     "1"     "011"
//   Russia           +7     "8"     "810"
//   Australia        +61    "0"     "0011"
//
// 106 of the 238 countries in the server's trunk-prefixes.csv have no trunk
// prefix at all, so the "strip one leading zero" rule would mangle every
// national number on such a line.
//
// The table lives in the firmware rather than being fetched, so the device
// stays self-sufficient: it needs only the configured dial prefix (one NVS
// key, seeded from the PhoneBlock account at activation) and derives the
// rest. Deviations from the "0"/"00" majority are stored explicitly; every
// country not listed falls through to that default, which keeps the whole
// table around 1 KB.

// Returned by value, with the fields inline rather than as pointers: the
// SIP task (caller IDs) and the sync task (barring entries) normalise
// concurrently, so shared scratch storage would race. The longest values
// in the table are 2 characters of trunk ("80", Belarus) and 4 of escape
// ("0011", Australia).
typedef struct {
    // National trunk prefix, "" when the country has none (then a leading
    // zero belongs to the number and must be kept).
    char trunk[4];
    // International access code ("00", "011", "810", …). Never empty.
    char intl[6];
} dial_rules_t;

// Dialling rules for `dial_prefix` ("+49", "+1", …). Unknown, malformed or
// NULL input yields the "0"/"00" default, which is also what the majority
// of countries use — so a stale or corrupt setting degrades to the old
// behaviour rather than to nonsense.
dial_rules_t dial_rules_for(const char *dial_prefix);
