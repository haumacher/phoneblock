#include "phone_norm.h"

#include <stdbool.h>
#include <stdio.h>
#include <string.h>

#include "dial_rules.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

// True if `s` is non-empty and every character is an ASCII digit.
static bool all_digits(const char *s)
{
    if (!*s) return false;
    for (; *s; s++) {
        if (*s < '0' || *s > '9') return false;
    }
    return true;
}

phone_class_t phone_normalise(const char *in, char *out, size_t cap,
                              const char *dial_prefix)
{
    if (cap == 0) return PHONE_SKIP;
    out[0] = '\0';
    if (!in || !*in) return PHONE_SKIP;

    // Wildcard barring patterns (e.g. "+43*", "08*") are a local blocklist
    // concept, not a concrete number to report — never rate them.
    if (strchr(in, '*')) return PHONE_SKIP;

    if (in[0] == '+') {
        // Already E.164; keep as-is once we've confirmed digits follow.
        if (!all_digits(in + 1)) return PHONE_SKIP;
        strncpy(out, in, cap - 1);
        out[cap - 1] = '\0';
        return PHONE_RATEABLE;
    }
    const dial_rules_t r = dial_rules_for(dial_prefix);
    const size_t intl_len  = strlen(r.intl);
    const size_t trunk_len = strlen(r.trunk);

    if (strncmp(in, r.intl, intl_len) == 0) {
        // International escape ("00" here, "011" in NANP, "810" in Russia).
        // Checked before the trunk prefix, which is often a prefix of it.
        if (!all_digits(in + intl_len)) return PHONE_SKIP;
        snprintf(out, cap, "+%s", in + intl_len);
        return PHONE_RATEABLE;
    }
    if (!dial_prefix || *dial_prefix != '+' || !dial_prefix[1]) {
        // No country configured — anything national is unresolvable.
        return PHONE_SKIP;
    }
    if (trunk_len > 0 && strncmp(in, r.trunk, trunk_len) == 0) {
        // National form with the country's trunk prefix → strip, prepend.
        if (!all_digits(in + trunk_len)) return PHONE_SKIP;
        snprintf(out, cap, "%s%s", dial_prefix, in + trunk_len);
        return PHONE_RATEABLE;
    }
    if (trunk_len == 0 && all_digits(in)) {
        // Country without a trunk prefix (Italy, Denmark, Czechia, …): the
        // national form *is* the bare number, and a leading zero belongs to
        // it. Only the country code is missing.
        snprintf(out, cap, "%s%s", dial_prefix, in);
        return PHONE_RATEABLE;
    }

    // A bare number in a country that does have a trunk prefix (e.g. German
    // "30123456"): we cannot tell an area code from a truncated entry, and
    // the server rejects the raw value — skip rather than forward garbage.
    //
    // Deliberately stricter than normalize_e164(), which expands this case:
    // these are barring entries typed into the Fritz!Box by hand, not caller
    // IDs delivered in canonical form, so guessing here would rate numbers
    // nobody called (issue #469).
    return PHONE_SKIP;
}
