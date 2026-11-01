#include "http_util.h"

#include <ctype.h>
#include <stdio.h>
#include <string.h>

#include "config.h"
#include "platform.h"
#include "strbuf.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static char s_user_agent[120] = "";

const char *http_util_user_agent(void)
{
    if (s_user_agent[0]) return s_user_agent;
    const char *v = pb_firmware_version();
    if (!v || !v[0]) v = "0.0.0";
    // Embed the stable device id so the server can attribute token
    // renewals (and recognise obsolete tokens) to the same physical
    // dongle. config_device_id() is set once at boot, before any HTTP
    // call, so it is populated by the time this lazily builds the string.
    const char *id = config_device_id();
    if (id && id[0]) {
        // The server reads the device id from the last bracket
        // (DongleRecognizer, DiagnosticsMapper), so the platform comment
        // goes before it, and is dropped rather than cutting off the id
        // when both do not fit. Without an id there is no platform
        // comment either, or DiagnosticsMapper's LIKE '...(%)' would take
        // the comment for a device id.
        const char *platform = pb_platform_tag();
        int n = -1;
        if (platform && platform[0]) {
            n = snprintf(s_user_agent, sizeof(s_user_agent),
                         "PhoneBlock-Dongle/%s (%s) (%s)", v, platform, id);
        }
        if (n < 0 || (size_t)n >= sizeof(s_user_agent)) {
            snprintf(s_user_agent, sizeof(s_user_agent),
                     "PhoneBlock-Dongle/%s (%s)", v, id);
        }
    } else {
        snprintf(s_user_agent, sizeof(s_user_agent),
                 "PhoneBlock-Dongle/%s", v);
    }
    return s_user_agent;
}

void http_url_for_log(const char *url, char *out, size_t cap)
{
    if (out == NULL || cap == 0) return;
    out[0] = '\0';
    if (url == NULL) return;

    // Authority starts after "scheme://" (or at the start without one).
    const char *scheme_end = strstr(url, "://");
    const char *auth = scheme_end ? scheme_end + 3 : url;
    // The authority ends at the first '/', '?' or '#'; a '@' inside it
    // ends the user info.
    size_t auth_len = strcspn(auth, "/?#");
    const char *host = auth;
    for (size_t i = 0; i < auth_len; i++) {
        if (auth[i] == '@') host = auth + i + 1;
    }
    const char *auth_end = auth + auth_len;
    // The path runs to the query or fragment.
    size_t path_len = strcspn(auth_end, "?#");

    strbuf_t sb = sb_init(out, (int)cap);
    sb_appendf(&sb, "%.*s%.*s%.*s",
               (int)(auth - url), url,
               (int)(auth_end - host), host,
               (int)path_len, auth_end);
}

// Copies up to `max` bytes of `src[0..n)` into `dst` (capacity `dcap`),
// each run of whitespace or control characters turned into one space and
// leading/trailing ones dropped. Stops on a UTF-8 character boundary.
// Returns true when not all of `src` fit.
static bool collapse_text(const char *src, size_t n, size_t max,
                          char *dst, size_t dcap)
{
    if (dcap == 0) return n > 0;
    size_t limit = max < dcap - 1 ? max : dcap - 1;
    size_t o = 0;
    bool pending_space = false;
    bool cut = false;
    for (size_t i = 0; i < n; i++) {
        unsigned char c = (unsigned char)src[i];
        if (c < 0x20 || c == 0x7f || c == ' ') {
            pending_space = o > 0;
            continue;
        }
        size_t need = pending_space ? 2 : 1;
        if (o + need > limit) {
            cut = true;
            if ((c & 0xC0) == 0x80) {
                // Cut inside a multi-byte character: drop its lead and
                // continuation bytes already copied.
                while (o > 0 && ((unsigned char)dst[o - 1] & 0xC0) == 0x80) o--;
                if (o > 0 && ((unsigned char)dst[o - 1] & 0xC0) == 0xC0) o--;
                while (o > 0 && dst[o - 1] == ' ') o--;
            }
            break;
        }
        if (pending_space) dst[o++] = ' ';
        pending_space = false;
        dst[o++] = (char)c;
    }
    dst[o] = '\0';
    return cut;
}

// Case-insensitive search for `needle` (lower case) in `hay[0..n)`.
static const char *find_ci(const char *hay, size_t n, const char *needle)
{
    size_t m = strlen(needle);
    for (size_t i = 0; i + m <= n; i++) {
        size_t k = 0;
        while (k < m && tolower((unsigned char)hay[i + k]) == needle[k]) k++;
        if (k == m) return hay + i;
    }
    return NULL;
}

void http_body_summary(const char *body, size_t len, bool echo_text,
                       char *out, size_t cap)
{
    if (out == NULL || cap == 0) return;
    strbuf_t sb = sb_init(out, (int)cap);
    if (body == NULL) len = 0;

    size_t start = 0;
    while (start < len && isspace((unsigned char)body[start])) start++;
    if (start == len) {
        sb_appendf(&sb, "%s", "empty body");
        return;
    }

    char text[HTTP_BODY_SUMMARY_TEXT_MAX + 1];
    if (body[start] == '<') {
        const char *end = body + len;
        const char *tag = find_ci(body, len, "<title");
        const char *open = tag ? memchr(tag, '>', (size_t)(end - tag)) : NULL;
        if (open) {
            open++;
            const char *close = memchr(open, '<', (size_t)(end - open));
            size_t tlen = (size_t)((close ? close : end) - open);
            bool cut = collapse_text(open, tlen, HTTP_BODY_SUMMARY_TEXT_MAX,
                                     text, sizeof(text));
            if (text[0]) {
                sb_appendf(&sb, "HTML page \"%s%s\"", text, cut ? "..." : "");
                return;
            }
        }
        sb_appendf(&sb, "HTML page, %u bytes", (unsigned)len);
        return;
    }

    if (!echo_text) {
        sb_appendf(&sb, "%u bytes", (unsigned)len);
        return;
    }
    bool cut = collapse_text(body + start, len - start,
                             HTTP_BODY_SUMMARY_TEXT_MAX, text, sizeof(text));
    sb_appendf(&sb, "\"%s%s\"", text, cut ? "..." : "");
}

http_fetch_outcome_t http_fetch_classify(int transport_result, int http_status)
{
    if (transport_result != PB_HTTP_OK) return HTTP_FETCH_RETRY;
    if (http_status >= 200 && http_status < 300) return HTTP_FETCH_OK;
    // Only a 404 is a definite "not there". A 403 can be a misconfigured
    // server, a 410 is not what a CDN sends for a never-published file, and
    // a 5xx or 429 says nothing about the file at all.
    if (http_status == 404) return HTTP_FETCH_ABSENT;
    return HTTP_FETCH_RETRY;
}

#ifdef ESP_PLATFORM
void http_util_set_user_agent(esp_http_client_handle_t client)
{
    esp_http_client_set_header(client, "User-Agent", http_util_user_agent());
}
#endif
