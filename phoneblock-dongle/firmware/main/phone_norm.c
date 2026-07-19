#include "phone_norm.h"

#include <stdbool.h>
#include <stdio.h>
#include <string.h>

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

phone_class_t phone_normalise(const char *in, char *out, size_t cap)
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
    if (in[0] == '0' && in[1] == '0') {
        if (!all_digits(in + 2)) return PHONE_SKIP;
        snprintf(out, cap, "+%s", in + 2);
        return PHONE_RATEABLE;
    }
    if (in[0] == '0') {
        if (!all_digits(in + 1)) return PHONE_SKIP;
        snprintf(out, cap, "+49%s", in + 1);
        return PHONE_RATEABLE;
    }

    // Bare number with no country context (e.g. "1727905225"): we cannot
    // reliably turn it into E.164, and the raw value is rejected by the
    // server — skip rather than forward garbage.
    return PHONE_SKIP;
}
