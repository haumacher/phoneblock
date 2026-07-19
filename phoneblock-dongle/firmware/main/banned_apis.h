#pragma once
//
// Compile-time ban on string functions that have no safe bounded form. Any use
// of one below becomes a hard error ("attempt to use poisoned ...") pointing
// straight at the call site. Use instead:
//
//   * building/appending a string (esp. multi-step) -> strbuf.h:
//         strbuf_t sb = sb_init(buf, sizeof buf);
//         sb_appendf(&sb, "...", ...);   // never overruns; sets sb.truncated
//   * a single bounded format  -> snprintf(dst, sizeof dst, ...)
//   * a fixed-length copy      -> memcpy with an explicit length
//
// IMPORTANT: #pragma GCC poison bans the identifier for the REST of the
// translation unit, including inside system headers. This header must
// therefore be included LAST, after every <...> / IDF header. Included
// earlier, the poison fires inside e.g. <stdio.h>'s own declaration.
//
// Deliberately NOT banned:
//   * snprintf / vsnprintf — bounds-safe. The footgun was only feeding
//     snprintf's return value into an accumulator offset
//     (`n += snprintf(buf + n, cap - n, ...)`); that pattern now lives behind
//     strbuf.h. New multi-step assembly must use strbuf, not raw snprintf.
//   * strcpy — remaining uses are audited-safe (string literals / length-
//     guarded escapes); prefer memcpy/snprintf for new code.
#pragma GCC poison sprintf vsprintf strcat gets
