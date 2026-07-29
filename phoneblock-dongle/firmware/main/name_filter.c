#include "name_filter.h"

#include <string.h>

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static bool is_space(char c)
{
    return c == ' ' || c == '\t' || c == '\r' || c == '\n';
}

static char lower(char c)
{
    return (c >= 'A' && c <= 'Z') ? (char)(c - 'A' + 'a') : c;
}

// Case-insensitive substring search. Hand-rolled because newlib has no
// strcasestr(); the inputs are a display name and a short pattern, so the naive
// O(n*m) scan is irrelevant next to the SIP round-trip it saves.
static bool contains_ci(const char *haystack, const char *needle)
{
    for (const char *h = haystack; *h; h++) {
        const char *a = h;
        const char *b = needle;
        while (*a && *b && lower(*a) == lower(*b)) { a++; b++; }
        if (!*b) return true;
    }
    return false;
}

int name_filter_parse(const char *spec, name_filter_t *out)
{
    out->count   = 0;
    out->dropped = false;
    if (!spec) return 0;

    const char *p = spec;
    while (*p) {
        const char *sep = strchr(p, '|');
        const char *end = sep ? sep : p + strlen(p);

        // Trim both ends of this alternative, so " tellows | Score " reads like
        // the hand-typed input it is. Interior spaces stay — "Score 8" is one
        // pattern, not two.
        const char *b = p;
        while (b < end && is_space(*b))     b++;
        const char *e = end;
        while (e > b && is_space(*(e - 1))) e--;

        size_t n = (size_t)(e - b);
        if (n > 0) {
            if (out->count >= NAME_FILTER_MAX || n >= NAME_FILTER_PATTERN_CAP) {
                // Over the cap, or too long to store in full: drop it. The
                // caller reports this — a truncated pattern is a *shorter*
                // pattern and would silently block more callers than asked for.
                out->dropped = true;
            } else {
                memcpy(out->pat[out->count], b, n);
                out->pat[out->count][n] = '\0';
                out->count++;
            }
        }

        if (!sep) break;
        p = sep + 1;
    }
    return out->count;
}

const char *name_filter_match(const name_filter_t *f, const char *display)
{
    if (!display || !*display) return NULL;
    for (int i = 0; i < f->count; i++)
        if (contains_ci(display, f->pat[i])) return f->pat[i];
    return NULL;
}
