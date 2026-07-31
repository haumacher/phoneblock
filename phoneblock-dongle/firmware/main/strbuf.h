#pragma once

// Bounded string builder for assembling text into a fixed buffer.
//
// It hides the one dangerous part of the snprintf() API — the return value,
// which is the length snprintf WOULD have written, not what it did. Feeding
// that unclamped value into a running offset (`n += snprintf(buf + n, cap - n,
// ...)`) is how an over-long field runs the cursor past the buffer end, after
// which the next write gets a negative `cap - n` (a huge size_t) and overruns
// the allocation. sb_appendf() never exposes that value: it advances the
// cursor by at most the space left, so the builder is memory-safe no matter
// how long the inputs are, and records whether anything had to be dropped.
//
// Usage:
//     strbuf_t sb = sb_init(buf, sizeof(buf));
//     sb_appendf(&sb, "hello %s", name);
//     sb_appendf(&sb, " x=%d", x);
//     // sb.len  = bytes written (buf is NUL-terminated)
//     // sb.truncated = true if any append did not fit entirely
//
// Callers that must be all-or-nothing (e.g. a SIP message that would become
// invalid or mis-framed if a header were dropped) check sb.truncated at the
// end and discard the result. Callers for whom a clamped result is acceptable
// (log lines, best-effort URLs) can ignore it.

#include <stdarg.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdio.h>

typedef struct {
    char *buf;        // destination buffer
    int   cap;        // capacity in bytes, including the terminating NUL
    int   len;        // bytes written so far (always < cap; buf[len] == '\0')
    bool  truncated;  // set once any append could not be written in full
} strbuf_t;

// Initialise a builder over `buf` (capacity `cap`, including the NUL). The
// buffer is NUL-terminated immediately so a builder to which nothing is
// appended still yields a valid empty string.
static inline strbuf_t sb_init(char *buf, int cap)
{
    if (cap > 0) buf[0] = '\0';
    strbuf_t sb = { buf, cap, 0, false };
    return sb;
}

// Append the formatted text. If it does not fit entirely, as much as fits is
// written (the buffer stays NUL-terminated) and sb->truncated is set. The
// cursor is only ever advanced by bytes actually written, so sb->len stays in
// [0, cap - 1] and no write can leave the buffer.
static inline void sb_vappendf(strbuf_t *sb, const char *fmt, va_list ap)
{
    int room = sb->cap - sb->len;          // includes space for the NUL
    if (room <= 0) { sb->truncated = true; return; }

    int w = vsnprintf(sb->buf + sb->len, (size_t)room, fmt, ap);
    if (w < 0) {                           // encoding error
        sb->truncated = true;
    } else if (w >= room) {                // didn't fit: filled to the brim
        sb->len = sb->cap - 1;
        sb->truncated = true;
    } else {
        sb->len += w;
    }
}

static inline void sb_appendf(strbuf_t *sb, const char *fmt, ...)
    __attribute__((format(printf, 2, 3)));

static inline void sb_appendf(strbuf_t *sb, const char *fmt, ...)
{
    va_list ap;
    va_start(ap, fmt);
    sb_vappendf(sb, fmt, ap);
    va_end(ap);
}

// Append `s` as the *contents* of a JSON string (the enclosing quotes are the
// caller's). Escapes what RFC 8259 requires — '"', '\\' and every control
// character below 0x20 — and passes bytes >= 0x80 through untouched, so UTF-8
// text stays intact.
//
// Use this for any user-supplied text that ends up in a hand-built JSON body.
// Interpolating it with a plain %s instead is an injection: a single '"' ends
// the string early and the rest of the text becomes JSON structure, which can
// overwrite a sibling field.
//
// Truncation follows the usual rule (sb->truncated is set, nothing runs past
// the buffer). Note a clipped result may end mid-UTF-8-sequence, so a caller
// that must emit valid JSON has to honour sb->truncated rather than send what
// fits.
static inline void sb_append_json_escaped(strbuf_t *sb, const char *s)
{
    if (!s) return;
    for (const unsigned char *p = (const unsigned char *)s; *p; p++) {
        switch (*p) {
            case '"':  sb_appendf(sb, "%s", "\\\""); break;
            case '\\': sb_appendf(sb, "%s", "\\\\"); break;
            case '\b': sb_appendf(sb, "%s", "\\b");  break;
            case '\f': sb_appendf(sb, "%s", "\\f");  break;
            case '\n': sb_appendf(sb, "%s", "\\n");  break;
            case '\r': sb_appendf(sb, "%s", "\\r");  break;
            case '\t': sb_appendf(sb, "%s", "\\t");  break;
            default:
                if (*p < 0x20) sb_appendf(sb, "\\u%04x", (unsigned)*p);
                else           sb_appendf(sb, "%c", (char)*p);
                break;
        }
    }
}
