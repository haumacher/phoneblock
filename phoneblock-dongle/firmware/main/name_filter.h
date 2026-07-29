#pragma once

#include <stdbool.h>
#include <stddef.h>

// Caller-name filter (issue #502).
//
// Some users keep a large third-party spam list (e.g. Tellows) as a Fritz!Box
// phonebook. The Box then resolves such a caller against that phonebook and
// announces a *name* — "tellows Score 8 Aggressive advertising" — which
// is_known_contact() (sip_parse.c) reads as "a contact the user saved
// themselves", so the call is let through untouched. Importing those lists into
// the user's PhoneBlock blacklist is not an option (they are huge and not ours
// to republish), so instead the user tells the dongle which *names* mean spam.
//
// Syntax
// ------
// One stored string (config_spam_names(), one NVS key, one form field) holding
// alternatives separated by '|':
//
//     tellows|Score|SPAM
//
// A call is spam when the announced name *contains* any alternative, compared
// case-insensitively. That is the whole syntax: no wildcards, no anchors, no
// regular expressions. A '*' would be pointless here — with substring matching
// a leading or trailing one is a no-op, so "score" and "score*" would mean the
// same thing, which is worse than having no metacharacter at all.
//
// Consequence of the separator: a pattern cannot contain a literal '|'.
//
// Pure string handling with no I/O, so it is host-tested
// (test/test_name_filter.c).

// Per-alternative capacity including the NUL. Long enough for the labels the
// Fritz!Box announces; an alternative that does not fit is dropped rather than
// truncated, since a clipped pattern matches *more* names than the user wrote.
#define NAME_FILTER_PATTERN_CAP 40

// Upper bound on alternatives per spec. This is a household-scale rule set
// checked on the INVITE critical path, not a filter language.
#define NAME_FILTER_MAX 8

// Capacity holding the longest acceptable spec: NAME_FILTER_MAX alternatives of
// NAME_FILTER_PATTERN_CAP - 1 chars each, the '|' between them, and the NUL. The
// NVS field and the form buffer are both sized from this, so a spec the UI
// accepts round-trips through a save without ever being truncated.
#define NAME_FILTER_SPEC_CAP (NAME_FILTER_MAX * NAME_FILTER_PATTERN_CAP)

typedef struct {
    char pat[NAME_FILTER_MAX][NAME_FILTER_PATTERN_CAP];
    int  count;                  // usable alternatives in pat[]
    bool dropped;                // an alternative was discarded (too long, or over NAME_FILTER_MAX)
} name_filter_t;

// Split `spec` on '|' into `out`. Surrounding whitespace of each alternative is
// trimmed and empty alternatives are skipped, so "a|" and " a | b " both parse
// cleanly; whitespace *inside* an alternative is significant ("Score 8").
// Alternatives that do not fit NAME_FILTER_PATTERN_CAP, and any beyond
// NAME_FILTER_MAX, are dropped and flagged in out->dropped. A NULL or all-empty
// spec yields count == 0, i.e. the filter is off. Returns out->count.
int name_filter_parse(const char *spec, name_filter_t *out);

// Returns the first alternative contained in `display` (case-insensitive), or
// NULL when none matches. The returned pointer aliases `f`, so it stays valid
// as long as the caller's name_filter_t does — it is meant for the log line
// naming which rule fired. An empty or NULL `display` never matches: a call
// with no announced name carries no evidence, and matching it would block every
// anonymous caller.
const char *name_filter_match(const name_filter_t *f, const char *display);
