#include "display_model.h"

#include <stdlib.h>
#include <string.h>

#include "strbuf.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

// "…" (U+2026), the first extra glyph.
static const char k_ellipsis[] = "\xE2\x80\xA6";

dm_connection_t dm_connection_update(dm_connection_state_t *state,
                                     int64_t now_us,
                                     bool pairing, bool wifi_up,
                                     bool token_set, bool sip_configured,
                                     bool sip_registered, bool token_valid)
{
    dm_connection_t fault = pairing ? DM_CONNECTION_OK
        : !wifi_up ? DM_CONNECTION_WIFI
        : !token_set || !sip_configured ? DM_CONNECTION_OK
        : !sip_registered ? DM_CONNECTION_SIP
        : !token_valid ? DM_CONNECTION_TOKEN
        : DM_CONNECTION_OK;
    if (fault != state->pending) {
        state->pending = fault;
        state->since_us = now_us;
    }
    return fault != DM_CONNECTION_OK
        && now_us - state->since_us >= DM_CONNECTION_GRACE_US
        ? fault : DM_CONNECTION_OK;
}

dm_connection_t dm_connection_visible(dm_connection_t connection,
                                      bool call_shown)
{
    return call_shown ? DM_CONNECTION_OK : connection;
}

// All geometry uses a 1000-unit square, sampled at pixel centres.
static bool circle(int x, int y, int cx, int cy, int r)
{
    int dx = x - cx, dy = y - cy;
    return dx * dx + dy * dy <= r * r;
}

static bool segment(int x, int y, int ax, int ay, int bx, int by, int r)
{
    int dx = bx - ax, dy = by - ay;
    int px = x - ax, py = y - ay;
    int length2 = dx * dx + dy * dy;
    int dot = px * dx + py * dy;
    if (dot <= 0) return circle(x, y, ax, ay, r);
    if (dot >= length2) return circle(x, y, bx, by, r);
    int cross = px * dy - py * dx;
    return (int64_t)cross * cross <= (int64_t)r * r * length2;
}

static bool rect(int x, int y, int x0, int y0, int x1, int y1)
{
    return x >= x0 && x <= x1 && y >= y0 && y <= y1;
}

// Earpiece of the handset: flat top with rounded corners, chamfered
// towards the handle. The mouthpiece is the same shape mirrored.
static bool handset_pad(int x, int y)
{
    if (x > 400 || y < 130) return false;
    if (y > 350 && x * 100 + y * 92 > 72200) return false;
    if (y < 190 && x < 190) return circle(x, y, 190, 190, 60);
    if (y < 190 && x > 340) return circle(x, y, 340, 190, 60);
    return true;
}

// Phone handset in the 1000-unit square: a band of the circle around
// (870, 130) between radius 585 and 740, plus earpiece and mouthpiece.
// The mouthpiece is the earpiece mirrored across the diagonal through
// that centre, which maps (x, y) to (1000 - y, 1000 - x).
static bool handset(int x, int y)
{
    int dx = x - 870, dy = y - 130;
    if (dx > 0 || dy < 0 || x < 130 || y > 870) return false;
    int d2 = dx * dx + dy * dy;
    if (d2 > 740 * 740) return false;
    return d2 >= 585 * 585 || handset_pad(x, y)
        || handset_pad(1000 - y, 1000 - x);
}

// Blocky "SIP" in the top right: letters 100 wide, 260 high, strokes 45.
static bool sip_letters(int x, int y)
{
    // S
    if (rect(x, y, 520, 60, 620, 105) || rect(x, y, 520, 168, 620, 212)
        || rect(x, y, 520, 275, 620, 320) || rect(x, y, 520, 60, 565, 190)
        || rect(x, y, 575, 190, 620, 320)) return true;
    // I
    if (rect(x, y, 665, 60, 710, 320)) return true;
    // P
    return rect(x, y, 755, 60, 800, 320) || rect(x, y, 755, 60, 855, 105)
        || rect(x, y, 755, 168, 855, 212) || rect(x, y, 810, 60, 855, 190);
}

static bool icon_glyph(dm_connection_t kind, int x, int y)
{
    if (kind == DM_CONNECTION_WIFI) {
        if (circle(x, y, 470, 800, 65)) return true;
        int dx = x - 470, dy = y - 800;
        if (dy >= 0 || dx > -dy || -dx > -dy) return false;
        int d2 = dx * dx + dy * dy;
        const int radii[] = { 210, 390, 570 };
        for (int i = 0; i < 3; i++) {
            int r = radii[i];
            if (d2 >= (r - 38) * (r - 38)
                && d2 <= (r + 38) * (r + 38)) return true;
        }
        return false;
    }
    if (kind == DM_CONNECTION_SIP) {
        // The handset is drawn at 4/5 size in the upper left, which keeps
        // its mouthpiece clear of the warning triangle; "SIP" goes top right.
        return handset(x * 5 / 4, y * 5 / 4) || sip_letters(x, y);
    }
    if (kind == DM_CONNECTION_TOKEN) {
        // Horizontal key: bow on the left, two teeth pointing down.
        return (circle(x, y, 260, 420, 190)
                && !circle(x, y, 260, 420, 95))
            || segment(x, y, 450, 420, 900, 420, 48)
            || segment(x, y, 656, 420, 656, 540, 40)
            || segment(x, y, 810, 420, 810, 540, 40);
    }
    return false;
}

dm_icon_pixel_t dm_icon_pixel(dm_connection_t kind, int size, int x, int y)
{
    if (size <= 0 || x < 0 || y < 0 || x >= size || y >= size
        || kind < DM_CONNECTION_WIFI || kind > DM_CONNECTION_TOKEN) {
        return DM_ICON_NONE;
    }
    int px = (int)(((int64_t)2 * x + 1) * 500 / size);
    int py = (int)(((int64_t)2 * y + 1) * 500 / size);

    // The background-coloured border separates the warning from the glyph.
    int dx = px - 800;
    if (dx < 0) dx = -dx;
    if (py >= 600 && py <= 940 && dx * 340 <= (py - 600) * 190) {
        if (py < 640 || py > 915 || dx * 340 > (py - 640) * 190) {
            return DM_ICON_OUTLINE;
        }
        if ((dx <= 20 && py >= 720 && py <= 810)
            || (dx <= 20 && py >= 845 && py <= 880)) {
            return DM_ICON_MARK;
        }
        return DM_ICON_TRIANGLE;
    }
    return icon_glyph(kind, px, py) ? DM_ICON_GLYPH : DM_ICON_NONE;
}

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

int dm_text_width(const lcd_font_t *font, int scale, const char *s)
{
    return glyph_count(s) * font->w * scale;
}

int dm_fit_text(const lcd_font_t *font, int scale, const char *s,
                int max_px, char *out, size_t cap)
{
    if (cap == 0) return 0;
    out[0] = '\0';
    if (!s || cap < sizeof(k_ellipsis)) return 0;

    int cell = font->w * scale;
    int fits = max_px / cell;             // whole cells that fit
    if (fits <= 0) return 0;
    size_t bytes = strlen(s);
    int glyphs = glyph_count(s);
    if (glyphs <= fits && bytes < cap) {
        memcpy(out, s, bytes + 1);
        return glyphs * cell;
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
    return (kept + 1) * cell;
}

// The 160x80 logo and the heights of the fonts' cells, which the layout
// centres (lcd_logo.h, lcd_font.h).
#define LOGO_W       160
#define LOGO_H       80
// The rating band's height and, for a name (large font) over its number
// (small font), how far the number's cell top sits below the name's, both
// at scale 1; they grow with the text scale.
#define BAND_H       22
#define NAME_TO_NUMBER 25
// Two-line name (scale 2 only): the band is just the small font's cell plus
// this padding (px, in total), and the number sits this far below the
// bottom of the second name line (px).
#define BAND_PAD_WRAP    4
#define NUMBER_GAP_WRAP  2
// The text doubles on panels at least this large.
#define SCALE2_W     240
#define SCALE2_H     135

void dm_layout(int width, int height, dm_layout_t *out)
{
    memset(out, 0, sizeof(*out));
    out->width  = width;
    out->height = height;
    out->time_y = -1;

    out->icon_size = (width < height ? width : height) * 3 / 4;
    out->icon_x = (width - out->icon_size) / 2;
    out->icon_y = (height - out->icon_size) / 2;

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
    int s = width >= SCALE2_W && height >= SCALE2_H ? 2 : 1;
    out->text_scale  = s;
    int small_h = lcd_font_small.h * s;
    int large_h = lcd_font_large.h * s;
    int band_h  = BAND_H * s;
    // Two name lines and the number: 2 x large + gap + small. Only at scale
    // 2 and up, where the band can shrink to the small font's cell; a name
    // of two large lines fits above the 160x80 band too, but that panel
    // keeps its one line and its band.
    int wrap_block = 2 * large_h + NUMBER_GAP_WRAP + small_h;
    if (s >= 2 && height - (small_h + BAND_PAD_WRAP) >= wrap_block) {
        band_h = small_h + BAND_PAD_WRAP;
        out->name_lines = 2;
    } else {
        out->name_lines = 1;
    }
    out->band_y      = height - band_h;
    out->band_text_y = out->band_y + (band_h - small_h) / 2;
    // From the name's top to the number's bottom.
    int block = NAME_TO_NUMBER * s + small_h;
    out->name_y         = (out->band_y - block) / 2;
    out->name_number_y  = out->name_y + NAME_TO_NUMBER * s;
    out->number_large_y = (out->band_y - large_h) / 2;
    out->number_small_y = (out->band_y - small_h) / 2;
    if (s >= 2 && small_h + block <= out->band_y
        && out->number_large_y >= small_h
        && out->number_small_y >= small_h) {
        out->time_y = 0;
        out->name_time_y = small_h;
        out->name_number_time_y = out->name_time_y + NAME_TO_NUMBER * s;
        out->number_large_time_y = (small_h + out->band_y - large_h) / 2;
        out->number_small_time_y = (small_h + out->band_y - small_h) / 2;
    }
    if (out->name_lines == 2) {
        out->wrap_y1       = (out->band_y - wrap_block) / 2;
        out->wrap_y2       = out->wrap_y1 + large_h;
        out->wrap_number_y = out->wrap_y2 + large_h + NUMBER_GAP_WRAP;
    }
}

int64_t dm_call_epoch(int64_t epoch_s, int64_t at_us,
                      int64_t now_epoch_s, int64_t now_us)
{
    if (epoch_s) return epoch_s;
    if (now_epoch_s && at_us > 0 && at_us <= now_us)
        return now_epoch_s - (now_us - at_us) / 1000000;
    return 0;
}

void dm_format_call_time(int64_t epoch, const struct tm *call_local,
                         const struct tm *now_local,
                         char out[DM_CALL_TIME_CAP])
{
    out[0] = '\0';
    if (!epoch || !call_local
        || call_local->tm_mon < 0 || call_local->tm_mon > 11
        || call_local->tm_mday < 1 || call_local->tm_mday > 31
        || call_local->tm_hour < 0 || call_local->tm_hour > 23
        || call_local->tm_min < 0 || call_local->tm_min > 59) return;

    unsigned day = (unsigned)call_local->tm_mday;
    unsigned month = (unsigned)call_local->tm_mon + 1;
    unsigned hour = (unsigned)call_local->tm_hour;
    unsigned minute = (unsigned)call_local->tm_min;
    if (now_local && call_local->tm_year == now_local->tm_year
        && call_local->tm_mon == now_local->tm_mon
        && call_local->tm_mday == now_local->tm_mday) {
        snprintf(out, DM_CALL_TIME_CAP, "%02u:%02u", hour, minute);
    } else {
        snprintf(out, DM_CALL_TIME_CAP, "%02u.%02u. %02u:%02u",
                 day, month, hour, minute);
    }
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

static void set_line(dm_line_t *line, const lcd_font_t *font, int scale,
                     const char *text, int width)
{
    line->font  = font;
    line->width = dm_fit_text(font, scale, text, DM_TEXT_MAX_PX(width),
                              line->text, sizeof(line->text));
}

// Splits a name that is too long for one line over two (see dm_layout_t).
static void wrap_name(dm_call_screen_t *out, const char *name, int scale,
                      int width)
{
    int cell = lcd_font_large.w * scale;
    int fits = DM_TEXT_MAX_PX(width) / cell;
    // Break at the last space within the first line's cells (dropped), else
    // after the code point that fills the line.
    const char *p = name, *end = name, *rest = name;
    int brk = 0;
    bool spaced = false;
    for (int i = 0; i <= fits; i++) {
        const char *q = p;
        uint32_t cp = dm_utf8_next(&q);
        if (cp == 0) return;
        if (i == fits && !spaced) {
            end = rest = p;
            brk = fits;
        }
        if (cp == ' ' && i > 0) {
            end = p;
            rest = q;
            brk = i;
            spaced = true;
        }
        p = q;
    }
    while (*rest == ' ') rest++;
    size_t bytes = (size_t)(end - name);
    if (!*rest || bytes >= sizeof(out->main.text)) return;
    memcpy(out->main.text, name, bytes);
    out->main.text[bytes] = '\0';
    out->main.width = brk * cell;
    set_line(&out->name2, &lcd_font_large, scale, rest, width);
}

void dm_call_screen(const stats_call_t *call, const dm_layout_t *l,
                    dm_call_screen_t *out)
{
    const int width = l->width, scale = l->text_scale;
    memset(out, 0, sizeof(*out));
    out->cls = dm_class_of(call->assessment);
    const char *number = call->number[0] ? call->number : "?";

    out->named = is_name(call->display) && dm_renderable(call->display);
    if (out->named) {
        set_line(&out->main, &lcd_font_large, scale, call->display, width);
        set_line(&out->sub, &lcd_font_small, scale, number, width);
        if (l->name_lines >= 2
            && dm_text_width(&lcd_font_large, scale, call->display)
               > DM_TEXT_MAX_PX(width)) {
            wrap_name(out, call->display, scale, width);
        }
    } else if (dm_text_width(&lcd_font_large, scale, number)
               <= DM_TEXT_MAX_PX(width)) {
        set_line(&out->main, &lcd_font_large, scale, number, width);
    } else {
        set_line(&out->main, &lcd_font_small, scale, number, width);
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
    if (band[0]) set_line(&out->band, &lcd_font_small, scale, band, width);
}

// --- backlight timeout ----------------------------------------------

static int dim_parse(const char *value, bool always_ok)
{
    if (!value || !value[0]) return DM_DIM_DEFAULT_S;
    if (strcmp(value, "always") == 0) return always_ok ? DM_DIM_ALWAYS : -1;
    static const char *const k_values[] = { "30", "60", "120", "300" };
    for (size_t i = 0; i < sizeof(k_values) / sizeof(k_values[0]); i++) {
        if (strcmp(value, k_values[i]) == 0) return atoi(k_values[i]);
    }
    return -1;
}

bool dm_dim_valid(const char *value, bool always_ok)
{
    return dim_parse(value, always_ok) >= 0;
}

int dm_dim_seconds(const char *value, bool always_ok)
{
    int s = dim_parse(value, always_ok);
    return s < 0 ? DM_DIM_DEFAULT_S : s;
}

// --- buttons and paging ---------------------------------------------

int dm_page_press(int index, dm_button_t button, bool lit, bool calling,
                  int count)
{
    int n = count < DM_BADGE_CALLS ? count : DM_BADGE_CALLS;
    if (n < 0) n = 0;
    if (!lit) return n > 0 ? 0 : DM_PAGE_NONE;
    if (calling) return index;
    // The list may have shrunk (cleared) since the position was set.
    if (index >= n) index = n - 1;
    if (index < DM_PAGE_NONE) index = DM_PAGE_NONE;
    if (button == DM_BUTTON_OLDER) {
        return index + 1 < n ? index + 1 : index;
    }
    return index > DM_PAGE_NONE ? index - 1 : DM_PAGE_NONE;
}

int dm_page_of_call(const stats_call_t *call)
{
    return call && call->seq != 0 ? 0 : DM_PAGE_NONE;
}

bool dm_button_sample(dm_button_state_t *state, bool low)
{
    if (!low) {
        state->low_samples = 0;
        if (state->high_samples < DM_BUTTON_STABLE) state->high_samples++;
        if (state->high_samples == DM_BUTTON_STABLE) state->reported = false;
        return false;
    }
    state->high_samples = 0;
    if (state->low_samples < DM_BUTTON_STABLE) state->low_samples++;
    if (state->low_samples < DM_BUTTON_STABLE || state->reported) return false;
    state->reported = true;
    return true;
}
