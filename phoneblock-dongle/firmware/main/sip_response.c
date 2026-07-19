#define _GNU_SOURCE   // memmem
#include "sip_response.h"

#include <string.h>

#include "sip_parse.h"   // find_header
#include "strbuf.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

// Echo one full "Name: value\r\n" line from req into the builder. A missing
// header is simply skipped; if the header is present but does not fit, the
// builder records the truncation (checked once at the end).
static void echo_header_line(strbuf_t *sb, const char *req, int req_len,
                             const char *name)
{
    const char *val = find_header(req, req_len, name);
    if (!val) return;

    const char *end = req + req_len;
    const char *eol = val;
    while (eol < end && *eol != '\r' && *eol != '\n') eol++;
    int val_len = (int)(eol - val);

    sb_appendf(sb, "%s: %.*s\r\n", name, val_len, val);
}

// Echo the To header, appending ";tag=<our_tag>" when it has no tag yet.
static void echo_to_with_tag(strbuf_t *sb, const char *req, int req_len,
                             const char *our_tag)
{
    const char *val = find_header(req, req_len, "To");
    if (!val) return;
    const char *end = req + req_len;
    const char *eol = val;
    while (eol < end && *eol != '\r' && *eol != '\n') eol++;
    int val_len = (int)(eol - val);

    if (memmem(val, val_len, ";tag=", 5)) {
        sb_appendf(sb, "To: %.*s\r\n", val_len, val);
    } else {
        sb_appendf(sb, "To: %.*s;tag=%s\r\n", val_len, val, our_tag);
    }
}

int sip_response_build(const char *req, int req_len,
                       int status, const char *reason,
                       const char *our_tag, const char *body,
                       const char *contact_user, const char *contact_host,
                       int contact_port,
                       char *out, int out_cap)
{
    if (out_cap <= 0) return -1;
    int body_len = body ? (int)strlen(body) : 0;

    strbuf_t sb = sb_init(out, out_cap);
    sb_appendf(&sb, "SIP/2.0 %d %s\r\n", status, reason);
    echo_header_line(&sb, req, req_len, "Via");
    echo_header_line(&sb, req, req_len, "From");
    echo_to_with_tag(&sb, req, req_len, our_tag);
    echo_header_line(&sb, req, req_len, "Call-ID");
    echo_header_line(&sb, req, req_len, "CSeq");
    sb_appendf(&sb,
        "Contact: <sip:%s@%s:%d>\r\n"
        "Allow: INVITE, ACK, CANCEL, BYE, OPTIONS\r\n"
        "User-Agent: phoneblock-dongle/0.1\r\n",
        contact_user, contact_host, contact_port);

    if (body_len > 0) {
        sb_appendf(&sb,
            "Content-Type: application/sdp\r\n"
            "Content-Length: %d\r\n\r\n%s",
            body_len, body);
    } else {
        sb_appendf(&sb, "Content-Length: 0\r\n\r\n");
    }

    // All-or-nothing: a response with a header dropped from the middle is
    // invalid SIP (and on TCP a Content-Length framing hazard), so if anything
    // did not fit, report it un-buildable and let the caller send nothing.
    if (sb.truncated) return -1;
    return sb.len;
}
