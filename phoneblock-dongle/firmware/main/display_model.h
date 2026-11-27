#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <time.h>

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

// DM_CONNECTION_GRACE_US is how long a fault must persist to replace the logo.
#define DM_CONNECTION_GRACE_US (30LL * 1000 * 1000)

// dm_connection_t identifies the connection indicator on the idle screen.
typedef enum {
    DM_CONNECTION_OK,      // No fault shown.
    DM_CONNECTION_WIFI,    // Wi-Fi has no IP address.
    DM_CONNECTION_SIP,     // SIP is not registered.
    DM_CONNECTION_TOKEN,   // PhoneBlock rejected the token.
} dm_connection_t;

// dm_connection_state_t holds the continuous-fault grace period.
typedef struct {
    dm_connection_t pending;
    int64_t since_us;
} dm_connection_state_t;

// dm_connection_update derives the indicator at monotonic time now_us,
// restarting the grace period when the fault changes or is suppressed.
dm_connection_t dm_connection_update(dm_connection_state_t *state,
                                     int64_t now_us,
                                     bool pairing, bool wifi_up,
                                     bool token_set, bool sip_configured,
                                     bool sip_registered, bool token_valid);

// dm_connection_visible gives the caller screen priority over an indicator.
dm_connection_t dm_connection_visible(dm_connection_t connection,
                                      bool call_shown);

// dm_icon_pixel_t names colour roles; outline uses the panel background.
typedef enum {
    DM_ICON_NONE,       // No icon pixel.
    DM_ICON_GLYPH,      // White connection glyph.
    DM_ICON_TRIANGLE,   // Orange warning triangle.
    DM_ICON_MARK,       // Dark exclamation mark.
    DM_ICON_OUTLINE,    // Background-coloured triangle outline.
} dm_icon_pixel_t;

// dm_icon_pixel returns the colour role at (x, y) in a square of side size.
dm_icon_pixel_t dm_icon_pixel(dm_connection_t kind, int size, int x, int y);

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
    // Square connection indicator, centered in the panel.
    int icon_x, icon_y, icon_size;
    // Spam badge: a pill badge_h high, right-aligned at badge_right (last
    // column), its top at badge_top; the digits' cell top at badge_text_y.
    int badge_top, badge_h, badge_right, badge_text_y;
    // Caller screen: the rating band from band_y to the bottom; text cell
    // tops for a name with the number below, or the number alone.
    int band_y, band_text_y;
    int name_y, name_number_y;
    // 2 where a name may take two lines (then the band is thin and the
    // wrap_* positions below apply to a name that needs both), else 1.
    // wrap_y1/wrap_y2: the lines' cell tops; wrap_number_y: the number's.
    int name_lines;
    int wrap_y1, wrap_y2, wrap_number_y;
    int number_large_y, number_small_y;
    // Scaled small-font time line, or -1 if the panel has no room.
    // A one-line name uses these alternate positions when time is shown;
    // a two-line name takes the time's space and keeps its wrap positions.
    int time_y, name_time_y, name_number_time_y;
    int number_large_time_y, number_small_time_y;
    // The fonts are drawn with every pixel as a text_scale x text_scale
    // block: 2 on the 240x135 panel, where the 160x80 fonts are too small
    // to read, 1 otherwise. The text positions above and the dm_text_width()
    // and dm_fit_text() measurements below take it as their scale. The
    // badge stays at scale 1.
    int text_scale;
} dm_layout_t;

void dm_layout(int width, int height, dm_layout_t *out);

// dm_call_epoch returns the recorded epoch, or derives it from the call's
// monotonic age when the current epoch and call time are known.
int64_t dm_call_epoch(int64_t epoch_s, int64_t at_us,
                      int64_t now_epoch_s, int64_t now_us);

// dm_format_call_time formats a known call's local time, adding its date
// unless now_local is the same local calendar day. A zero epoch or missing
// call_local produces an empty string; a missing now_local includes the date.
#define DM_CALL_TIME_CAP 13
void dm_format_call_time(int64_t epoch, const struct tm *call_local,
                         const struct tm *now_local,
                         char out[DM_CALL_TIME_CAP]);

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

// Width of `s` in pixels when drawn at `scale` (dm_layout_t.text_scale; 1
// for the badge): one cell of font->w * scale per code point (a malformed
// byte counts as one cell; display.c draws it as '?').
int dm_text_width(const lcd_font_t *font, int scale, const char *s);

// Copies `s` into `out`, cut at a code point boundary and ended with "…"
// (U+2026) if it would be wider than `max_px` or longer than `cap`, with
// text drawn at `scale`. Returns the width of the copy in pixels.
int dm_fit_text(const lcd_font_t *font, int scale, const char *s,
                int max_px, char *out, size_t cap);

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

// Bytes per line: 33 small-font cells (the 240 px panel at scale 1, the
// widest line possible), each up to three UTF-8 bytes.
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
    dm_line_t  name2;         // second name line, font NULL if none
    dm_line_t  sub;           // number below the name
    dm_line_t  band;          // rating band text: "SPAM 12×", "SPAM? 2×", ...
    char       votes[12];     // vote count shown in the band, "" if none
} dm_call_screen_t;

// What to show for an incoming call on the panel described by `l`. With a
// Fritz!Box display name (one that is not just digits, and that the fonts
// can render) the name goes large with the number below; otherwise the
// number alone, large if it fits. The band colour follows dm_class_of();
// spam and suspected spam carry the direct vote count.
//
// Lines are fitted to the panel's l->width, with the text drawn at
// l->text_scale: the fit and the large/small decision use the scaled cell
// widths, and the lines' widths are in scaled pixels. With l->name_lines
// 2, a name wider than one line breaks at the last space that fits on the
// first line (the space is dropped), or after the code point that fills it
// if there is no space; `main` is the first line, `name2` the rest, cut
// with the ellipsis if still too long. With 1 the name is cut on one line.
void dm_call_screen(const stats_call_t *call, const dm_layout_t *l,
                    dm_call_screen_t *out);
