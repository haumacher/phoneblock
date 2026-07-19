#include "smtp_body.h"

#include <string.h>

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

int smtp_encode_body(const char *body, smtp_body_sink sink, void *ctx)
{
    for (const char *p = body; *p; ) {
        if (*p == '.' && sink(ctx, ".", 1) != 0)
            return -1;                       // dot-stuff this line's leading '.'

        const char *nl  = strchr(p, '\n');
        size_t      seg = nl ? (size_t)(nl - p) : strlen(p);
        if (seg && sink(ctx, p, seg) != 0)
            return -1;
        if (!nl) break;                      // trailing text without a newline
        if (sink(ctx, "\r\n", 2) != 0)
            return -1;
        p = nl + 1;                          // next line starts after the LF
    }
    return 0;
}
