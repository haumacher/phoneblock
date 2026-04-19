#include "tr064_parse.h"

#include <string.h>

int tr064_xml_find_text(const char *xml, const char *tag,
                        char *out, size_t cap)
{
    size_t name_len = strlen(tag);
    const char *p = xml;
    while ((p = strchr(p, '<')) != NULL) {
        if (p[1] == '/' || p[1] == '?' || p[1] == '!') { p++; continue; }
        p++;
        // Skip any "prefix:" before the local name.
        const char *tag_end = p;
        while (*tag_end && *tag_end != '>' && *tag_end != ' '
               && *tag_end != '\t' && *tag_end != '/') tag_end++;
        if (*tag_end == '\0') return -1;
        const char *local = p;
        const char *colon = memchr(p, ':', tag_end - p);
        if (colon) local = colon + 1;
        if ((size_t)(tag_end - local) == name_len
            && strncmp(local, tag, name_len) == 0) {
            // Found the opening tag. Skip attributes to the '>'.
            const char *gt = strchr(tag_end, '>');
            if (!gt) return -1;
            if (gt[-1] == '/') {
                // Self-closing empty element.
                out[0] = '\0';
                return 0;
            }
            const char *content_start = gt + 1;
            // Find first "</…localname>" after content_start.
            const char *q = content_start;
            while ((q = strstr(q, "</")) != NULL) {
                q += 2;
                const char *qgt = strchr(q, '>');
                if (!qgt) return -1;
                const char *qlocal = q;
                const char *qcolon = memchr(q, ':', qgt - q);
                if (qcolon) qlocal = qcolon + 1;
                if ((size_t)(qgt - qlocal) == name_len
                    && strncmp(qlocal, tag, name_len) == 0) {
                    size_t content_len = (q - 2) - content_start;
                    if (content_len >= cap) content_len = cap - 1;
                    memcpy(out, content_start, content_len);
                    out[content_len] = '\0';
                    return (int)content_len;
                }
                q = qgt + 1;
            }
            return -1;
        }
        p = tag_end;
    }
    return -1;
}

void tr064_xml_unescape_inplace(char *s)
{
    char *w = s;
    for (const char *r = s; *r; ) {
        if (*r == '&') {
            if (strncmp(r, "&amp;",  5) == 0) { *w++ = '&';  r += 5; continue; }
            if (strncmp(r, "&lt;",   4) == 0) { *w++ = '<';  r += 4; continue; }
            if (strncmp(r, "&gt;",   4) == 0) { *w++ = '>';  r += 4; continue; }
            if (strncmp(r, "&quot;", 6) == 0) { *w++ = '"';  r += 6; continue; }
            if (strncmp(r, "&apos;", 6) == 0) { *w++ = '\''; r += 6; continue; }
        }
        *w++ = *r++;
    }
    *w = '\0';
}

void tr064_xml_escape(const char *in, char *out, size_t cap)
{
    size_t o = 0;
    for (const char *p = in; *p && o + 6 < cap; p++) {
        switch (*p) {
            case '&':  strcpy(out + o, "&amp;");  o += 5; break;
            case '<':  strcpy(out + o, "&lt;");   o += 4; break;
            case '>':  strcpy(out + o, "&gt;");   o += 4; break;
            case '"':  strcpy(out + o, "&quot;"); o += 6; break;
            case '\'': strcpy(out + o, "&apos;"); o += 6; break;
            default:   out[o++] = *p; break;
        }
    }
    out[o] = '\0';
}

bool tr064_pick_default_user(char *xml, char *out, size_t cap)
{
    if (!out || !cap) return false;
    out[0] = '\0';

    // Preferred: the attribute-form last_user="1" marker.
    const char *markers[] = {
        "last_user=\"1\"", "last_user='1'", "last_user=1", NULL
    };
    for (int i = 0; markers[i]; i++) {
        const char *m = strstr(xml, markers[i]);
        if (!m) continue;
        const char *gt = strchr(m, '>');
        if (!gt) continue;
        const char *start = gt + 1;
        const char *end = strstr(start, "</Username>");
        if (!end) continue;
        size_t n = end - start;
        if (n >= cap) n = cap - 1;
        memcpy(out, start, n);
        out[n] = '\0';
        if (out[0]) return true;
    }

    // Legacy wrapper form.
    char *flag = strstr(xml, "<LastUser>1</LastUser>");
    char *block_start = NULL, *block_end = NULL;
    if (flag) {
        const char *p = xml;
        while ((p = strstr(p, "<User>")) != NULL && p < flag) {
            block_start = (char *)p;
            p++;
        }
        if (block_start) block_end = strstr(flag, "</User>");
    }
    if (block_start && block_end) {
        char save = *block_end;
        *block_end = '\0';
        int found = tr064_xml_find_text(block_start, "Username", out, cap);
        *block_end = save;
        if (found >= 0 && out[0]) return true;
    }

    // Fallback: first Username element (ignoring any attributes).
    return tr064_xml_find_text(xml, "Username", out, cap) >= 0 && out[0];
}
