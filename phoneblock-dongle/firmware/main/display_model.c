#include "display_model.h"

#include <string.h>

#include "strbuf.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

// "…" (U+2026), the first extra glyph.
static const char k_ellipsis[] = "\xE2\x80\xA6";

uint32_t dm_utf8_next(const char **p)
{
    const unsigned char *s = (const unsigned char *)*p;
    unsigned char b = s[0];
    if (b == 0) return 0;
    if (b < 0x80) { *p += 1; return b; }

    int len;
    uint32_t cp, min;
    if      ((b & 0xE0) == 0xC0) { len = 2; cp = b & 0x1F; min = 0x80; }
    else if ((b & 0xF0) == 0xE0) { len = 3; cp = b & 0x0F; min = 0x800; }
    else if ((b & 0xF8) == 0xF0) { len = 4; cp = b & 0x07; min = 0x10000; }
    else { *p += 1; return DM_UTF8_INVALID; }

    // Each continuation byte is checked before the next is read: a NUL
    // (or any non-continuation byte) ends the sequence as malformed.
    for (int i = 1; i < len; i++) {
        if ((s[i] & 0xC0) != 0x80) { *p += 1; return DM_UTF8_INVALID; }
        cp = (cp << 6) | (s[i] & 0x3F);
    }
    if (cp < min || cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) {
        *p += 1;
        return DM_UTF8_INVALID;
    }
    *p += len;
    return cp;
}

int dm_glyph_index(uint32_t cp)
{
    if (cp >= 0x20 && cp <= 0x7E) return (int)(cp - 0x20);
    if (cp >= 0xA0 && cp <= 0xFF) return 95 + (int)(cp - 0xA0);
    for (int i = 0; i < LCD_FONT_EXTRA_GLYPHS; i++) {
        if (lcd_font_extra_cps[i] == cp) return 95 + 96 + i;
    }
    return -1;
}

bool dm_renderable(const char *s)
{
    if (!s) return false;
    uint32_t cp;
    while ((cp = dm_utf8_next(&s)) != 0) {
        if (cp == DM_UTF8_INVALID || dm_glyph_index(cp) < 0) return false;
    }
    return true;
}

static int glyph_count(const char *s)
{
    int n = 0;
    while (s && dm_utf8_next(&s) != 0) n++;
    return n;
}

int dm_text_width(const lcd_font_t *font, const char *s)
{
    return glyph_count(s) * font->w;
}

int dm_fit_text(const lcd_font_t *font, const char *s, int max_px,
                char *out, size_t cap)
{
    if (cap == 0) return 0;
    out[0] = '\0';
    if (!s || cap < sizeof(k_ellipsis)) return 0;

    int fits = max_px / font->w;          // whole cells that fit
    if (fits <= 0) return 0;
    size_t bytes = strlen(s);
    int glyphs = glyph_count(s);
    if (glyphs <= fits && bytes < cap) {
        memcpy(out, s, bytes + 1);
        return glyphs * font->w;
    }

    // Keep as many leading code points as leave room for the ellipsis, in
    // cells and in bytes.
    const char *p = s;
    size_t keep = 0;
    int kept = 0;
    while (kept < fits - 1) {
        const char *q = p;
        if (dm_utf8_next(&q) == 0) break;
        if ((size_t)(q - s) + sizeof(k_ellipsis) > cap) break;
        p = q;
        keep = (size_t)(p - s);
        kept++;
    }
    memcpy(out, s, keep);
    memcpy(out + keep, k_ellipsis, sizeof(k_ellipsis));   // includes NUL
    return (kept + 1) * font->w;
}

// The 160x80 logo and the heights of the fonts' cells, which the layout
// centres (lcd_logo.h, lcd_font.h).
#define LOGO_W       160
#define LOGO_H       80
#define BAND_H       22
// A name (large font) over its number (small font): the number's cell top
// sits this far below the name's.
#define NAME_TO_NUMBER 25

void dm_layout(int width, int height, dm_layout_t *out)
{
    memset(out, 0, sizeof(*out));
    out->width  = width;
    out->height = height;

    // The logo grows by 3/2 where that fits (240x120 on the T-Display),
    // and stays 1:1 otherwise.
    bool grow = width >= LOGO_W * 3 / 2 && height >= LOGO_H * 3 / 2;
    out->logo_num = grow ? 3 : 1;
    out->logo_den = grow ? 2 : 1;
    out->logo_w   = LOGO_W * out->logo_num / out->logo_den;
    out->logo_h   = LOGO_H * out->logo_num / out->logo_den;
    out->logo_x   = (width - out->logo_w) / 2;
    out->logo_y   = (height - out->logo_h) / 2;

    // The badge sits in the logo's empty top-right corner, above the word
    // mark, one pixel in from the logo's top and the panel's right edge.
    out->badge_h      = 17;
    out->badge_top    = out->logo_y + 1;
    out->badge_right  = width - 2;
    out->badge_text_y = out->badge_top + (out->badge_h - lcd_font_small.h) / 2;

    // The rating band at the bottom, its text centred in it; the caller's
    // name and number, or the number alone, centred in the space above.
    out->band_y      = height - BAND_H;
    out->band_text_y = out->band_y + (BAND_H - lcd_font_small.h) / 2;
    // From the name's top to the number's bottom.
    int block = NAME_TO_NUMBER + lcd_font_small.h;
    out->name_y         = (out->band_y - block) / 2;
    out->name_number_y  = out->name_y + NAME_TO_NUMBER;
    out->number_large_y = (out->band_y - lcd_font_large.h) / 2;
    out->number_small_y = (out->band_y - lcd_font_small.h) / 2;
}

dm_class_t dm_class_of(pb_assessment_t assessment)
{
    switch (assessment) {
        case PB_ASSESS_SPAM:
        case PB_ASSESS_SPAM_LIST:
        case PB_ASSESS_BLACKLIST:
        case PB_ASSESS_NAME_PATTERN: return DM_CLASS_SPAM;
        case PB_ASSESS_SUSPECT:      return DM_CLASS_SUSPECT;
        case PB_ASSESS_LEGITIMATE:   return DM_CLASS_LEGIT;
        default:                     return DM_CLASS_NEUTRAL;
    }
}

bool dm_is_listed_spam(const stats_call_t *call)
{
    return dm_class_of(call->assessment) == DM_CLASS_SPAM;
}

int dm_count_listed_spam(const stats_call_t *calls, int n)
{
    int count = 0;
    for (int i = 0; i < n; i++) {
        if (dm_is_listed_spam(&calls[i])) count++;
    }
    return count;
}

// A display name that is only a number (the Fritz!Box announces the digits
// when it knows no name) is not a name.
static bool is_name(const char *display)
{
    for (const char *p = display; *p; p++) {
        if (!strchr("0123456789+*#-()/. ", *p)) return true;
    }
    return false;
}

static void set_line(dm_line_t *line, const lcd_font_t *font, const char *text,
                     int width)
{
    line->font  = font;
    line->width = dm_fit_text(font, text, DM_TEXT_MAX_PX(width),
                              line->text, sizeof(line->text));
}

void dm_call_screen(const stats_call_t *call, int width, dm_call_screen_t *out)
{
    memset(out, 0, sizeof(*out));
    out->cls = dm_class_of(call->assessment);
    const char *number = call->number[0] ? call->number : "?";

    out->named = is_name(call->display) && dm_renderable(call->display);
    if (out->named) {
        set_line(&out->main, &lcd_font_large, call->display, width);
        set_line(&out->sub, &lcd_font_small, number, width);
    } else if (dm_text_width(&lcd_font_large, number)
               <= DM_TEXT_MAX_PX(width)) {
        set_line(&out->main, &lcd_font_large, number, width);
    } else {
        set_line(&out->main, &lcd_font_small, number, width);
    }

    if ((out->cls == DM_CLASS_SPAM || out->cls == DM_CLASS_SUSPECT)
        && call->direct_votes > 0) {
        snprintf(out->votes, sizeof(out->votes), "%d", call->direct_votes);
    }

    char band[DM_LINE_CAP];
    strbuf_t sb = sb_init(band, sizeof(band));
    if (out->cls == DM_CLASS_SPAM || out->cls == DM_CLASS_SUSPECT) {
        sb_appendf(&sb, "%s", out->cls == DM_CLASS_SPAM ? "SPAM" : "SPAM?");
        // "×" (U+00D7): "reported n times", without a word to translate.
        if (out->votes[0]) sb_appendf(&sb, " %s\xC3\x97", out->votes);
    } else if (call->assessment == PB_ASSESS_TEST) {
        sb_appendf(&sb, "%s", "TEST");
    } else if (!out->named && dm_renderable(call->location)) {
        // Where an unnamed caller is from (operator / area), as PhoneBlock
        // reports it.
        sb_appendf(&sb, "%s", call->location);
    }
    if (band[0]) set_line(&out->band, &lcd_font_small, band, width);
}
