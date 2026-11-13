#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "lcd_font.h"
#include "stats.h"

// What the display shows, as pure functions over the call list (stats.h)
// and the bitmap fonts (lcd_font.h): the spam badge on the idle screen and
// the caller screen during a call, laid out for the panel's size. No
// drawing, no ESP-IDF — display.c renders the result,
// test/test_display_model.c checks it on the host.
//
// Language neutral (issue #460, the firmware carries no per-language text):
// the screens use names, numbers, colours and the tokens "SPAM", "SPAM?" and
// "TEST" only.

// Panel sizes in landscape: the T-Dongle-S3 (ESP32-S3) and the T-Display
// V1.1 (classic ESP32, #708). DM_MAX_WIDTH sizes the line buffers.
#define DM_TDONGLE_W    160
#define DM_TDONGLE_H    80
#define DM_TDISPLAY_W   240
#define DM_TDISPLAY_H   135
#define DM_MAX_WIDTH    DM_TDISPLAY_W
// Pixels kept free at either side of a text line.
#define DM_MARGIN       3
#define DM_TEXT_MAX_PX(width) ((width) - 2 * DM_MARGIN)

// --- layout ---------------------------------------------------------

// Where things go on a panel of the given size. For 160x80 these are the
// T-Dongle-S3's positions from before #708; larger panels centre the same
// fonts in their space and scale the 160x80 logo by 3/2 when it fits.
typedef struct {
    int width, height;
    // Logo: the 160x80 bitmap scaled by logo_num / logo_den, placed at
    // (logo_x, logo_y); the rest of the panel is the logo's background.
    int logo_num, logo_den;
    int logo_x, logo_y, logo_w, logo_h;
    // Spam badge: a pill badge_h high, right-aligned at badge_right (last
    // column), its top at badge_top; the digits' cell top at badge_text_y.
    int badge_top, badge_h, badge_right, badge_text_y;
    // Caller screen: the rating band from band_y to the bottom; text cell
    // tops for a name with the number below, or the number alone.
    int band_y, band_text_y;
    int name_y, name_number_y;
    int number_large_y, number_small_y;
} dm_layout_t;

void dm_layout(int width, int height, dm_layout_t *out);

// --- UTF-8 and glyphs -----------------------------------------------

#define DM_UTF8_INVALID 0xFFFFFFFFu

// Decodes the code point at *p and advances *p past it. At the terminating
// NUL returns 0 without advancing. A malformed sequence (stray continuation
// byte, truncated or overlong sequence, surrogate, > U+10FFFF) returns
// DM_UTF8_INVALID and advances by exactly one byte, so a loop always ends
// and never reads past the NUL.
uint32_t dm_utf8_next(const char **p);

// Index of the glyph for `cp` in the lcd_font_t bitmaps, or -1 if the
// fonts lack it.
int dm_glyph_index(uint32_t cp);

// True if `s` is valid UTF-8 and every code point has a glyph.
bool dm_renderable(const char *s);

// Width of `s` in pixels: one cell per code point (a malformed byte counts
// as one cell; display.c draws it as '?').
int dm_text_width(const lcd_font_t *font, const char *s);

// Copies `s` into `out`, cut at a code point boundary and ended with "…"
// (U+2026) if it would be wider than `max_px` or longer than `cap`.
// Returns the width of the copy in pixels.
int dm_fit_text(const lcd_font_t *font, const char *s, int max_px,
                char *out, size_t cap);

// --- spam badge -----------------------------------------------------

// Colour class of a call, mirroring verdictClass() in web/index.html, which
// keys on the *current* assessment (a call the user re-rated changes class).
typedef enum {
    DM_CLASS_NEUTRAL,   // unknown, error, test
    DM_CLASS_LEGIT,     // phone book, whitelist
    DM_CLASS_SUSPECT,   // votes below the threshold ("SPAM-VERDACHT")
    DM_CLASS_SPAM,      // spam (API, block list, blacklist, caller-name pattern)
} dm_class_t;

dm_class_t dm_class_of(pb_assessment_t assessment);

// Badge rule: a listed call counts as a blocked spam call exactly when the
// web UI's call list renders it in the red "SPAM" class — assessment spam,
// spam_list, blacklist or name_pattern. Test calls ("Test", grey), suspected
// spam (orange) and calls the user re-rated as legitimate (green) do not count.
//
// Only the DM_BADGE_CALLS newest calls are counted — what the web UI's call
// list shows before "show all", and what the badge counted when the list
// was a 10-entry RAM ring. The persistent list (#589) holds weeks of calls; a
// badge summing them would only ever grow.
#define DM_BADGE_CALLS 10

bool dm_is_listed_spam(const stats_call_t *call);
int  dm_count_listed_spam(const stats_call_t *calls, int n);

// --- caller screen --------------------------------------------------

// Bytes per line: the widest panel holds 33 small-font cells, each up to
// three UTF-8 bytes.
#define DM_LINE_CAP 104

typedef struct {
    const lcd_font_t *font;   // NULL: line not shown
    char text[DM_LINE_CAP];
    int  width;               // pixels
} dm_line_t;

typedef struct {
    dm_class_t cls;
    bool       named;         // `main` is the caller's name, `sub` the number
    dm_line_t  main;          // name, or the number
    dm_line_t  sub;           // number below the name
    dm_line_t  band;          // rating band text: "SPAM 12×", "SPAM? 2×", ...
    char       votes[12];     // vote count shown in the band, "" if none
} dm_call_screen_t;

// What to show for an incoming call. With a Fritz!Box display name (one that
// is not just digits, and that the fonts can render) the name goes large with
// the number below; otherwise the number alone, large if it fits. The band
// colour follows dm_class_of(); spam and suspected spam carry the direct
// vote count. Lines are fitted to a panel `width` pixels wide.
void dm_call_screen(const stats_call_t *call, int width, dm_call_screen_t *out);
