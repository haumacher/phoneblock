#include "mail_html.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

// Each builder writes a terminating '\0' at body[len] before returning, so
// the buffer is always a valid C string after any call. This matters
// because the assembled body is consumed as a C string (smtp_encode_body
// reads to the NUL): without the terminator the mail would stream whatever
// uninitialised heap follows the content until a stray NUL — leaking heap
// memory into the message. All three reserve at least one byte (the loop
// bounds stop below cap), so body[len] is always in range.

size_t append_str(char *body, size_t cap, size_t len, const char *s)
{
    while (*s && len < cap - 1) body[len++] = *s++;
    body[len] = '\0';
    return len;
}

size_t append_html_escaped(char *body, size_t cap, size_t len, const char *s)
{
    // Reserve room for the longest entity ("&quot;", 6 bytes) so a near-full
    // buffer never writes a partial escape.
    for (; *s && len < cap - 7; s++) {
        switch (*s) {
            case '&':  len = append_str(body, cap, len, "&amp;");  break;
            case '<':  len = append_str(body, cap, len, "&lt;");   break;
            case '>':  len = append_str(body, cap, len, "&gt;");   break;
            case '"':  len = append_str(body, cap, len, "&quot;"); break;
            case '\'': len = append_str(body, cap, len, "&#39;");  break;
            default:   body[len++] = *s;                           break;
        }
    }
    body[len] = '\0';
    return len;
}

size_t append_url_encoded(char *body, size_t cap, size_t len, const char *s)
{
    static const char hex[] = "0123456789ABCDEF";
    // Reserve room for one "%XX" triple.
    for (; *s && len < cap - 4; s++) {
        unsigned char ch = (unsigned char)*s;
        if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') ||
            (ch >= '0' && ch <= '9') ||
            ch == '-' || ch == '_' || ch == '.' || ch == '~') {
            body[len++] = (char)ch;
        } else {
            body[len++] = '%';
            body[len++] = hex[ch >> 4];
            body[len++] = hex[ch & 0x0F];
        }
    }
    body[len] = '\0';
    return len;
}

// Extract the released "X.Y.Z" prefix of `version` into base[cap], matching
// the web UI's fwLink regex ^(\d+\.\d+\.\d+)(?:-[0-9A-Za-z.]+)?$: three
// dot-separated digit groups, then either end-of-string or a single '-'
// followed by one-or-more [0-9A-Za-z.] and nothing else. A git-describe dev
// build ("1.3.4-5-gabcdef") has a second '-', which the suffix rule rejects
// — so it (correctly) gets no changelog page. Returns false (base cleared)
// on any deviation or if base is too small.
static bool parse_release_base(const char *v, char *base, size_t cap)
{
    if (cap) base[0] = '\0';
    const char *p = v;
    size_t bi = 0;
    for (int group = 0; group < 3; group++) {
        if (group > 0) {
            if (*p != '.' || bi + 1 >= cap) return false;
            base[bi++] = *p++;
        }
        if (!(*p >= '0' && *p <= '9')) return false;   // need at least one digit
        while (*p >= '0' && *p <= '9') {
            if (bi + 1 >= cap) return false;
            base[bi++] = *p++;
        }
    }
    base[bi] = '\0';
    if (*p == '\0') return true;                       // bare X.Y.Z
    if (*p != '-') return false;
    if (*++p == '\0') return false;                    // "-" with empty suffix
    for (; *p; p++) {
        char c = *p;
        if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') ||
              (c >= 'a' && c <= 'z') || c == '.'))
            return false;                              // e.g. a second '-'
    }
    return true;
}

bool mail_changelog_url(const char *version, char *out, size_t cap)
{
    if (cap) out[0] = '\0';
    char base[16];   // "255.255.255" and headroom; longer → parse fails
    if (!parse_release_base(version, base, sizeof(base))) return false;

    // The base is [0-9.] only, so appending it raw is injection-safe.
    size_t len = append_str(out, cap, 0,
        "https://github.com/haumacher/phoneblock/blob/master/"
        "phoneblock-dongle/firmware/release-notes/");
    len = append_str(out, cap, len, base);
    len = append_str(out, cap, len, ".md");
    // A too-small buffer would silently truncate into a broken link; reject
    // it (and clear the partial write) rather than emit one. The full URL is
    // ~113 bytes for X.Y.Z.
    if (len >= cap - 1) {
        out[0] = '\0';
        return false;
    }
    return true;
}
