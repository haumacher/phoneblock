#include "mail_rcpt.h"

#include <string.h>

#include "strbuf.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static bool is_space(char c)
{
    return c == ' ' || c == '\t' || c == '\r' || c == '\n';
}

int mail_rcpt_parse(const char *spec, mail_rcpt_list_t *out)
{
    out->count   = 0;
    out->dropped = false;
    if (!spec) return 0;

    const char *p = spec;
    while (*p) {
        const char *sep = strchr(p, ';');
        const char *end = sep ? sep : p + strlen(p);

        // Trim both ends of this entry, so " a@b.de ; c@d.de " parses like
        // the pasted-from-an-address-book input it usually is.
        const char *b = p;
        while (b < end && is_space(*b))     b++;
        const char *e = end;
        while (e > b && is_space(*(e - 1))) e--;

        size_t n = (size_t)(e - b);
        if (n > 0) {
            if (out->count >= MAIL_RCPT_MAX || n >= MAIL_RCPT_ADDR_CAP) {
                // Over the cap, or too long to store in full: drop it. The
                // caller reports this — a truncated address would silently
                // deliver to the wrong mailbox (or bounce).
                out->dropped = true;
            } else {
                memcpy(out->addr[out->count], b, n);
                out->addr[out->count][n] = '\0';
                out->count++;
            }
        }

        if (!sep) break;
        p = sep + 1;
    }
    return out->count;
}

bool mail_rcpt_header(const mail_rcpt_list_t *l, char *buf, size_t cap)
{
    strbuf_t sb = sb_init(buf, (int)cap);
    for (int i = 0; i < l->count; i++)
        sb_appendf(&sb, "%s<%s>", i ? ", " : "", l->addr[i]);
    return !sb.truncated;
}
