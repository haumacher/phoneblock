#include "hostname.h"

#include <string.h>

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static bool is_space(char c)
{
    return c == ' ' || c == '\t' || c == '\r' || c == '\n';
}

static bool is_label_char(char c)
{
    return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-';
}

bool hostname_valid(const char *name)
{
    if (!name) return false;
    size_t n = strlen(name);
    if (n < 1 || n > HOSTNAME_MAX_LEN) return false;
    if (name[0] == '-' || name[n - 1] == '-') return false;
    for (size_t i = 0; i < n; i++) {
        if (!is_label_char(name[i])) return false;
    }
    return true;
}

bool hostname_normalize(const char *in, char *out, size_t out_cap)
{
    if (!out || out_cap < HOSTNAME_CAP) return false;
    out[0] = '\0';
    if (!in) return false;

    const char *start = in;
    while (is_space(*start)) start++;
    const char *end = start + strlen(start);
    while (end > start && is_space(end[-1])) end--;

    size_t n = (size_t)(end - start);
    if (n == 0) return true;            // empty: back to HOSTNAME_DEFAULT
    if (n > HOSTNAME_MAX_LEN) return false;

    char buf[HOSTNAME_CAP];
    for (size_t i = 0; i < n; i++) {
        char c = start[i];
        buf[i] = (c >= 'A' && c <= 'Z') ? (char)(c - 'A' + 'a') : c;
    }
    buf[n] = '\0';
    if (!hostname_valid(buf)) return false;
    memcpy(out, buf, n + 1);
    return true;
}
