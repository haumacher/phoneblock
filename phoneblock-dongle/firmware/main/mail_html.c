#include "mail_html.h"

size_t append_str(char *body, size_t cap, size_t len, const char *s)
{
    while (*s && len < cap - 1) body[len++] = *s++;
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
    return len;
}
