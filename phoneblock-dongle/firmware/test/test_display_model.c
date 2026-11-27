// Host test for display_model.c: what the T-Dongle-S3 (160x80) and the
// T-Display (240x135, #708) show.
//
//   * the spam badge counts the call-list entries the web UI renders as
//     blocked spam, recounted from the stats ring (clear, re-rating and
//     ring overflow included);
//   * the caller screen: name over number, or the number with its rating,
//     text fitted to the panel's width with an ellipsis;
//   * the layout: the T-Dongle-S3's positions from before #708, and the
//     T-Display's derived from its size;
//   * which board a chip may drive;
//   * UTF-8 decoding never runs past the string, whatever the input.
//
// Built with AddressSanitizer (see the Makefile), so an overrun aborts.
// Synthetic numbers only.
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "display_model.h"
#include "stats.h"
#include "calls_test_util.h"

static int tests_run, tests_failed;

#define CHECK(cond, what) do {                                  \
    tests_run++;                                                \
    if (cond) printf("  ok: %s\n", (what));                     \
    else { tests_failed++; printf("  FAIL: %s (%s:%d)\n",       \
                                  (what), __FILE__, __LINE__); } \
} while (0)

#define ELLIPSIS "\xE2\x80\xA6"

static void record_checked(const char *number, verdict_t verdict,
                           pb_assessment_t a, int direct_votes)
{
    pb_check_result_t r;
    memset(&r, 0, sizeof(r));
    r.verdict      = verdict;
    r.assessment   = a;
    r.direct_votes = direct_votes;
    stats_record_call_checked(number, "", &r);
}

static int badge(void)
{
    int n = stats_count_calls(dm_is_listed_spam, DM_BADGE_CALLS);
    // The same rule over a snapshot gives the same count.
    stats_call_t calls[DM_BADGE_CALLS];
    int k = stats_snapshot_calls(calls, DM_BADGE_CALLS);
    if (dm_count_listed_spam(calls, k) != n) return -1;
    return n;
}

static void test_badge(void)
{
    printf("badge:\n");
    stats_clear_calls();
    CHECK(badge() == 0, "empty list → no badge");

    record_checked("+4930111111", VERDICT_SPAM, PB_ASSESS_SPAM, 12);
    stats_record_call("+4930222222", "Max Müller", VERDICT_LEGITIMATE);
    stats_record_call_assessed("+4930333333", "tellows Score 9", VERDICT_SPAM,
                               PB_ASSESS_NAME_PATTERN);
    stats_record_call_assessed("**622", "", VERDICT_SPAM, PB_ASSESS_TEST);
    record_checked("+4930444444", VERDICT_LEGITIMATE, PB_ASSESS_SUSPECT, 2);
    CHECK(badge() == 2, "[SPAM, LEGIT, SPAM(name), TEST, SUSPECT] → 2");

    record_checked("+4930555555", VERDICT_SPAM, PB_ASSESS_SPAM_LIST, 0);
    record_checked("+4930666666", VERDICT_SPAM, PB_ASSESS_BLACKLIST, 0);
    CHECK(badge() == 4, "block-list and blacklist spam count too");

    stats_set_assessment("+4930111111", PB_ASSESS_LEGITIMATE);
    CHECK(badge() == 3, "re-rated as not spam → decremented");
    stats_set_assessment("+4930444444", PB_ASSESS_BLACKLIST);
    CHECK(badge() == 4, "suspect reported as spam → incremented");

    stats_clear_calls();
    CHECK(badge() == 0, "cleared list → badge gone");

    char num[32];
    for (int i = 0; i < 12; i++) {
        snprintf(num, sizeof(num), "+49301000%02d", i);
        record_checked(num, VERDICT_SPAM, PB_ASSESS_SPAM, 3);
    }
    CHECK(badge() == DM_BADGE_CALLS, "12 spam calls → 10 (the newest counted)");
    stats_record_call("+4930999999", "", VERDICT_LEGITIMATE);
    CHECK(badge() == DM_BADGE_CALLS - 1, "oldest spam call dropped out of the count");
    stats_clear_calls();
}

static stats_call_t make_call(const char *number, const char *display,
                              pb_assessment_t a, int direct_votes)
{
    stats_call_t c;
    memset(&c, 0, sizeof(c));
    snprintf(c.number, sizeof(c.number), "%s", number);
    snprintf(c.display, sizeof(c.display), "%s", display);
    c.assessment   = a;
    c.verdict      = dm_class_of(a) == DM_CLASS_SPAM ? VERDICT_SPAM : VERDICT_LEGITIMATE;
    c.direct_votes = direct_votes;
    return c;
}

static void test_call_screen(void)
{
    printf("caller screen:\n");
    dm_call_screen_t s;
    dm_layout_t l;
    dm_layout(DM_TDONGLE_W, DM_TDONGLE_H, &l);

    stats_call_t c = make_call("+4930123456", "Max Müller", PB_ASSESS_LEGITIMATE, 0);
    dm_call_screen(&c, &l, &s);
    CHECK(s.named, "name present → named screen");
    CHECK(strcmp(s.main.text, "Max Müller") == 0, "name on the main line");
    CHECK(s.main.font == &lcd_font_large, "name in the large font");
    CHECK(s.main.width == 10 * lcd_font_large.w, "name width: 10 cells (ü is one)");
    CHECK(strcmp(s.sub.text, "+4930123456") == 0, "number on the line below");
    CHECK(s.sub.font == &lcd_font_small, "number below in the small font");
    CHECK(s.cls == DM_CLASS_LEGIT, "phone-book caller → legit class");
    CHECK(s.band.text[0] == '\0', "no band text for a contact");

    c = make_call("+4930123456", "", PB_ASSESS_SPAM, 12);
    dm_call_screen(&c, &l, &s);
    CHECK(!s.named, "no name → number screen");
    CHECK(strcmp(s.main.text, "+4930123456") == 0, "number on the main line");
    CHECK(s.main.font == &lcd_font_large, "short number in the large font");
    CHECK(s.cls == DM_CLASS_SPAM, "spam class");
    CHECK(strcmp(s.votes, "12") == 0, "vote text \"12\"");
    CHECK(strcmp(s.band.text, "SPAM 12\xC3\x97") == 0, "band \"SPAM 12×\"");
    CHECK(s.band.width == 8 * lcd_font_small.w, "band width: 8 cells");

    c = make_call("+4930123456", "", PB_ASSESS_SUSPECT, 2);
    dm_call_screen(&c, &l, &s);
    CHECK(s.cls == DM_CLASS_SUSPECT, "suspected → suspect class");
    CHECK(strcmp(s.votes, "2") == 0, "suspect vote text \"2\"");
    CHECK(strcmp(s.band.text, "SPAM? 2\xC3\x97") == 0, "band \"SPAM? 2×\"");

    c = make_call("+4930123456", "", PB_ASSESS_SPAM_LIST, 0);
    dm_call_screen(&c, &l, &s);
    CHECK(s.votes[0] == '\0' && strcmp(s.band.text, "SPAM") == 0,
          "list spam without counts → plain \"SPAM\"");

    c = make_call("**622", "", PB_ASSESS_TEST, 0);
    dm_call_screen(&c, &l, &s);
    CHECK(s.cls == DM_CLASS_NEUTRAL && strcmp(s.band.text, "TEST") == 0,
          "test call → neutral, \"TEST\"");

    c = make_call("+4930123456", "", PB_ASSESS_UNKNOWN, 0);
    snprintf(c.location, sizeof(c.location), "%s", "Berlin");
    dm_call_screen(&c, &l, &s);
    CHECK(s.cls == DM_CLASS_NEUTRAL && strcmp(s.band.text, "Berlin") == 0,
          "unknown caller → neutral, location in the band");

    c = make_call("+4930123456", "\xE7\x8E\x8B\xE5\xB0\x8F\xE6\x98\x8E",
                  PB_ASSESS_LEGITIMATE, 0);                     // CJK
    dm_call_screen(&c, &l, &s);
    CHECK(!s.named && strcmp(s.main.text, "+4930123456") == 0,
          "CJK name → falls back to the number");

    c = make_call("+4930123456", "\xD0\x98\xD0\xB2\xD0\xB0\xD0\xBD",
                  PB_ASSESS_LEGITIMATE, 0);                     // Иван
    dm_call_screen(&c, &l, &s);
    CHECK(!s.named && strcmp(s.main.text, "+4930123456") == 0,
          "Cyrillic name → falls back to the number");

    c = make_call("+4930123456", "Max \xC3", PB_ASSESS_LEGITIMATE, 0);
    dm_call_screen(&c, &l, &s);
    CHECK(!s.named, "invalid UTF-8 name → falls back to the number");

    c = make_call("030123456", "030123456", PB_ASSESS_UNKNOWN, 0);
    dm_call_screen(&c, &l, &s);
    CHECK(!s.named, "digits-only display name is not a name");

    c = make_call("+4930123456", "Maximilian Mustermann-Lüdenscheid",
                  PB_ASSESS_LEGITIMATE, 0);
    dm_call_screen(&c, &l, &s);
    // 154 px / 9 px = 17 cells: 16 code points and the ellipsis.
    CHECK(DM_TEXT_MAX_PX(DM_TDONGLE_W) / lcd_font_large.w == 17,
          "large font: 17 cells per line");
    CHECK(strcmp(s.main.text, "Maximilian Muste" ELLIPSIS) == 0,
          "long name truncated with an ellipsis");
    CHECK(s.main.width == 17 * lcd_font_large.w, "truncated width 153 px");
    CHECK(s.main.width <= DM_TEXT_MAX_PX(DM_TDONGLE_W) && s.main.width <= DM_TDONGLE_W,
          "truncated name fits the 160 px panel");

    c = make_call("+4930123456", "Müller-Lüdenscheidtäöü", PB_ASSESS_LEGITIMATE, 0);
    dm_call_screen(&c, &l, &s);
    CHECK(strcmp(s.main.text, "Müller-Lüdensche" ELLIPSIS) == 0
          && dm_text_width(&lcd_font_large, 1, s.main.text) == s.main.width,
          "truncation counts code points, not bytes");

    c = make_call("+49301234567890123", "", PB_ASSESS_UNKNOWN, 0);
    dm_call_screen(&c, &l, &s);
    CHECK(s.main.font == &lcd_font_small
          && strcmp(s.main.text, "+49301234567890123") == 0,
          "number too wide for the large font → small font, complete");

    c = make_call("", "", PB_ASSESS_UNKNOWN, 0);
    dm_call_screen(&c, &l, &s);
    CHECK(strcmp(s.main.text, "?") == 0, "no number → \"?\"");
}

static void test_utf8(void)
{
    printf("utf-8:\n");
    const char *p = "a\xC3\xA4\xE2\x80\xA6\xF0\x9F\x98\x80";
    CHECK(dm_utf8_next(&p) == 'a', "ASCII");
    CHECK(dm_utf8_next(&p) == 0xE4, "2-byte ä");
    CHECK(dm_utf8_next(&p) == 0x2026, "3-byte …");
    CHECK(dm_utf8_next(&p) == 0x1F600, "4-byte emoji");
    CHECK(dm_utf8_next(&p) == 0 && *p == '\0', "stops at NUL without advancing");

    static const char *bad[] = {
        "\x80",                 // stray continuation
        "\xC3",                 // truncated by the NUL
        "\xE2\x82",             // truncated 3-byte
        "\xF0\x9F\x98",         // truncated 4-byte
        "\xC0\xAF",             // overlong '/'
        "\xE0\x80\xAF",         // overlong 3-byte
        "\xED\xA0\x80",         // surrogate
        "\xF4\x90\x80\x80",     // > U+10FFFF
        "\xFF",                 // never valid
    };
    bool all = true;
    for (size_t i = 0; i < sizeof(bad) / sizeof(bad[0]); i++) {
        // Copied to an exact-size heap block: ASan catches any read past it.
        size_t len = strlen(bad[i]);
        char *s = malloc(len + 1);
        memcpy(s, bad[i], len + 1);
        const char *q = s;
        if (dm_utf8_next(&q) != DM_UTF8_INVALID || q != s + 1) all = false;
        int guard = 0;
        while (dm_utf8_next(&q) != 0 && guard++ < 16) {}
        if (guard >= 16 || q != s + len) all = false;
        if (dm_renderable(s)) all = false;
        free(s);
    }
    CHECK(all, "malformed sequences: invalid, advance one byte, loop ends at NUL");

    char out[8];
    int w = dm_fit_text(&lcd_font_small, 1, "\xC3\xA4\xC3\xB6\xC3\xBC\xC3\xA4\xC3\xB6",
                        DM_TEXT_MAX_PX(DM_TDONGLE_W), out, sizeof(out));
    CHECK(strlen(out) < sizeof(out) && strcmp(out, "\xC3\xA4\xC3\xB6" ELLIPSIS) == 0
          && w == 3 * lcd_font_small.w,
          "byte capacity: cut at a code point boundary, ellipsis kept");

    w = dm_fit_text(&lcd_font_small, 1, "ab\x80" "cd", DM_TEXT_MAX_PX(DM_TDONGLE_W),
                    out, sizeof(out));
    CHECK(w == 5 * lcd_font_small.w, "a malformed byte counts as one cell");
    CHECK(dm_fit_text(&lcd_font_small, 1, "abc", DM_TEXT_MAX_PX(DM_TDONGLE_W), out, 3) == 0
          && out[0] == '\0', "no room for the ellipsis → empty");
}

static void test_glyphs(void)
{
    printf("glyphs:\n");
    CHECK(dm_glyph_index(' ') == 0 && dm_glyph_index('A') == 33, "ASCII");
    CHECK(dm_glyph_index(0xE4) == 95 + 0x44, "ä in the Latin-1 block");
    CHECK(dm_glyph_index(0xDF) >= 0 && dm_glyph_index(0xD6) >= 0, "ß and Ö");
    CHECK(dm_glyph_index(0x2026) == 191, "ellipsis is the first extra glyph");
    CHECK(dm_glyph_index(0x20AC) == 192, "euro sign");
    CHECK(dm_glyph_index(0x7F) < 0 && dm_glyph_index(0x9F) < 0, "control codes");
    CHECK(dm_glyph_index(0x0141) < 0 && dm_glyph_index(0x4E00) < 0,
          "Ł and CJK are not in the fonts");
    CHECK(lcd_font_large.bytes_per_glyph == (lcd_font_large.w * lcd_font_large.h + 7) / 8
          && lcd_font_small.bytes_per_glyph == (lcd_font_small.w * lcd_font_small.h + 7) / 8,
          "glyph size matches the cell");
    CHECK(DM_TEXT_MAX_PX(DM_TDONGLE_W) <= DM_TDONGLE_W
          && DM_TEXT_MAX_PX(DM_TDISPLAY_W) <= DM_TDISPLAY_W,
          "text width within the panel");
}

// The wider panel (#708): the same fonts drawn at twice the size, so a line
// holds 13 large or 16 small cells and a number fits the large font only up
// to 13 characters.
static void test_call_screen_wide(void)
{
    printf("caller screen, 240 px at scale 2:\n");
    dm_call_screen_t s;
    // One name line: the two-line wrap has its own test below.
    dm_layout_t l;
    dm_layout(DM_TDISPLAY_W, DM_TDISPLAY_H, &l);
    l.name_lines = 1;
    stats_call_t c = make_call("+4930123456", "Maximilian Mustermann-Lüdenscheid",
                               PB_ASSESS_LEGITIMATE, 0);
    dm_call_screen(&c, &l, &s);
    // 234 px / 18 px = 13 cells: 12 code points and the ellipsis.
    CHECK(DM_TEXT_MAX_PX(DM_TDISPLAY_W) / (lcd_font_large.w * 2) == 13,
          "large font at 2x: 13 cells per line");
    CHECK(strcmp(s.main.text, "Maximilian M" ELLIPSIS) == 0,
          "long name cut at the scaled cell width");
    CHECK(s.main.width == 13 * lcd_font_large.w * 2
          && s.main.width <= DM_TEXT_MAX_PX(DM_TDISPLAY_W),
          "truncated name fits the 240 px panel at 2x");
    CHECK(s.sub.font == &lcd_font_small && s.sub.width == 11 * lcd_font_small.w * 2,
          "number below the name measured at 2x");

    c = make_call("+493012345678", "", PB_ASSESS_UNKNOWN, 0);
    dm_call_screen(&c, &l, &s);
    CHECK(s.main.font == &lcd_font_large && s.main.width == 13 * lcd_font_large.w * 2
          && strcmp(s.main.text, "+493012345678") == 0,
          "a number that fits at 2x large stays large");

    c = make_call("+493012345678901", "", PB_ASSESS_UNKNOWN, 0);
    dm_call_screen(&c, &l, &s);
    CHECK(s.main.font == &lcd_font_small
          && strcmp(s.main.text, "+493012345678901") == 0
          && s.main.width == 16 * lcd_font_small.w * 2,
          "a number too wide for 2x large falls back to 2x small");

    c = make_call("+49301234567890123456789012", "", PB_ASSESS_UNKNOWN, 0);
    dm_call_screen(&c, &l, &s);
    CHECK(s.main.font == &lcd_font_small && s.main.width == 16 * lcd_font_small.w * 2
          && strcmp(s.main.text, "+49301234567890" ELLIPSIS) == 0,
          "an over-long number is cut with the ellipsis at 2x small");

    // The longest line the buffers must hold: 33 small cells of 3-byte
    // UTF-8 (the euro sign), 32 of them and the ellipsis.
    char text[160];
    size_t n = 0;
    for (int i = 0; i < 40; i++) {
        text[n++] = '\xE2'; text[n++] = '\x82'; text[n++] = '\xAC';
    }
    text[n] = '\0';
    dm_line_t line;
    line.width = dm_fit_text(&lcd_font_small, 1, text,
                             DM_TEXT_MAX_PX(DM_TDISPLAY_W), line.text, sizeof(line.text));
    CHECK(DM_TEXT_MAX_PX(DM_TDISPLAY_W) / lcd_font_small.w == 33
          && line.width == 33 * lcd_font_small.w
          && strlen(line.text) == 32 * 3 + 3,
          "33 small cells of 3-byte UTF-8 fit the line buffer");
}

static void wrap_screen(const char *name, dm_call_screen_t *s)
{
    dm_layout_t l;
    dm_layout(DM_TDISPLAY_W, DM_TDISPLAY_H, &l);
    stats_call_t c = make_call("+4930123456", name, PB_ASSESS_LEGITIMATE, 0);
    dm_call_screen(&c, &l, s);
}

// A long name over two lines on the 240x135 panel (13 large cells a line).
static void test_name_wrap(void)
{
    printf("name over two lines:\n");
    dm_call_screen_t s;
    int cell = lcd_font_large.w * 2;

    wrap_screen("Maximilian Mustermann-Lüdenscheid", &s);
    CHECK(strcmp(s.main.text, "Maximilian") == 0 && s.main.width == 10 * cell
          && s.name2.font == &lcd_font_large
          && strcmp(s.name2.text, "Mustermann-L" ELLIPSIS) == 0
          && s.name2.width == 13 * cell,
          "wrap at the last space that fits, second line cut with the ellipsis");
    CHECK(s.sub.font == &lcd_font_small && strcmp(s.sub.text, "+4930123456") == 0,
          "number stays below the name");

    wrap_screen("Abcdefghijklm Nop", &s);
    CHECK(strcmp(s.main.text, "Abcdefghijklm") == 0 && s.main.width == 13 * cell
          && strcmp(s.name2.text, "Nop") == 0,
          "a space right after the 13th cell is a break point");

    wrap_screen("Abcdefghijklmnopqrst", &s);
    CHECK(strcmp(s.main.text, "Abcdefghijklm") == 0 && s.main.width == 13 * cell
          && strcmp(s.name2.text, "nopqrst") == 0 && s.name2.width == 7 * cell,
          "no space: hard break where line 1 is full");

    wrap_screen("Mustermann", &s);
    CHECK(strcmp(s.main.text, "Mustermann") == 0 && s.name2.font == NULL
          && s.main.width == 10 * cell, "a name that fits stays on one line");
    wrap_screen("Abcdefghijklm", &s);
    CHECK(s.name2.font == NULL && strcmp(s.main.text, "Abcdefghijklm") == 0,
          "exactly 13 cells stays on one line");

    wrap_screen("ÄÖÜäöüßÄÖÜäöüßÄÖ", &s);
    CHECK(strcmp(s.main.text, "ÄÖÜäöüßÄÖÜäöü") == 0 && s.main.width == 13 * cell
          && strcmp(s.name2.text, "ßÄÖ") == 0 && dm_renderable(s.main.text),
          "hard break never splits an umlaut");
    wrap_screen("Müller Lüdenscheid-Ärzte", &s);
    CHECK(strcmp(s.main.text, "Müller") == 0 && s.main.width == 6 * cell
          && strcmp(s.name2.text, "Lüdenscheid-" ELLIPSIS) == 0,
          "wrap at a space between umlaut words");

    // Scale 1 keeps one line: the T-Dongle-S3.
    stats_call_t c = make_call("+4930123456", "Maximilian Mustermann-Lüdenscheid",
                               PB_ASSESS_LEGITIMATE, 0);
    dm_layout_t l;
    dm_layout(DM_TDONGLE_W, DM_TDONGLE_H, &l);
    dm_call_screen(&c, &l, &s);
    CHECK(s.name2.font == NULL, "160x80: long name is cut, not wrapped");

    // Without a name the number screen is unchanged.
    stats_call_t n = make_call("+493012345678", "", PB_ASSESS_UNKNOWN, 0);
    dm_layout(DM_TDISPLAY_W, DM_TDISPLAY_H, &l);
    dm_call_screen(&n, &l, &s);
    CHECK(!s.named && s.name2.font == NULL && s.main.font == &lcd_font_large,
          "number only: no second line");
}

static void test_layout(void)
{
    printf("layout:\n");
    dm_layout_t l;

    // The T-Dongle-S3: exactly the positions display.c used before #708.
    dm_layout(DM_TDONGLE_W, DM_TDONGLE_H, &l);
    CHECK(l.logo_num == 1 && l.logo_den == 1 && l.logo_x == 0 && l.logo_y == 0
          && l.logo_w == 160 && l.logo_h == 80, "160x80: logo 1:1, filling the panel");
    CHECK(l.badge_top == 1 && l.badge_h == 17 && l.badge_right == 158
          && l.badge_text_y == 3, "160x80: badge as before");
    CHECK(l.band_y == 58 && l.band_text_y == 62, "160x80: band as before");
    CHECK(l.name_y == 10 && l.name_number_y == 35
          && l.number_large_y == 20 && l.number_small_y == 22
          && l.text_scale == 1,
          "160x80: name and number as before");
    CHECK(l.name_lines == 1 && l.wrap_y1 == 0 && l.wrap_y2 == 0
          && l.wrap_number_y == 0, "160x80: one name line");
    CHECK(l.time_y == -1, "160x80: no time line");

    // The T-Display: the logo by 3/2, centred; the text centred above the
    // band at the bottom.
    dm_layout(DM_TDISPLAY_W, DM_TDISPLAY_H, &l);
    CHECK(l.logo_num == 3 && l.logo_den == 2 && l.logo_w == 240 && l.logo_h == 120
          && l.logo_x == 0 && l.logo_y == 7, "240x135: logo 240x120 at row 7");
    CHECK(l.badge_top == 8 && l.badge_right == 238 && l.badge_text_y == 10,
          "240x135: badge in the logo's top-right corner");
    CHECK(l.text_scale == 2, "240x135: text at twice the size");
    CHECK(l.band_y == 105 && l.band_text_y == 105 + (30 - 26) / 2,
          "240x135: thin band (small font cell + 4 px) at the bottom");
    CHECK(l.name_y == 14 && l.name_number_y == 64
          && l.number_large_y == 34 && l.number_small_y == 39,
          "240x135: scaled text centred above the band");
    CHECK(l.name_lines == 2 && l.wrap_y1 == 2 && l.wrap_y2 == 38
          && l.wrap_number_y == 76,
          "240x135: two name lines, 100 px block centred in 105 px");
    CHECK(l.time_y == 0 && l.name_time_y == 26 && l.name_number_time_y == 76,
          "240x135: time and one-line name positions");
    CHECK(l.time_y + lcd_font_small.h * l.text_scale == l.name_time_y
          && l.band_y - (l.name_number_time_y + lcd_font_small.h * l.text_scale) == 3,
          "240x135: name below time, three-pixel gap above band");
    CHECK(l.number_large_time_y == 47 && l.number_small_time_y == 52,
          "240x135: numbers centred between time and band");
    CHECK(l.time_y + lcd_font_small.h * l.text_scale > l.wrap_y1,
          "240x135: wrapped name has no room for time");

    // Everything inside the panel, for both sizes.
    int sizes[][2] = { { DM_TDONGLE_W, DM_TDONGLE_H }, { DM_TDISPLAY_W, DM_TDISPLAY_H } };
    for (int i = 0; i < 2; i++) {
        dm_layout(sizes[i][0], sizes[i][1], &l);
        bool inside = l.logo_x >= 0 && l.logo_x + l.logo_w <= l.width
            && l.logo_y >= 0 && l.logo_y + l.logo_h <= l.height
            && l.badge_top + l.badge_h <= l.height && l.badge_right < l.width
            && l.name_number_y + lcd_font_small.h * l.text_scale <= l.band_y
            && l.number_large_y + lcd_font_large.h * l.text_scale <= l.band_y
            && l.band_text_y + lcd_font_small.h * l.text_scale <= l.height;
        CHECK(inside, i == 0 ? "160x80: all inside the panel"
                             : "240x135: all inside the panel");
    }
}

static void test_call_time(void)
{
    printf("call time:\n");
    CHECK(dm_call_epoch(123, 0, 0, 0) == 123, "recorded epoch takes precedence");
    CHECK(dm_call_epoch(0, 1500000, 1000, 4100000) == 998,
          "epoch derived from monotonic age");
    CHECK(dm_call_epoch(0, 1500000, 0, 4100000) == 0, "clock unknown");
    CHECK(dm_call_epoch(0, 0, 1000, 4100000) == 0, "call uptime unknown");
    CHECK(dm_call_epoch(0, 4100001, 1000, 4100000) == 0, "call uptime in future");
    CHECK(dm_call_epoch(0, 4100000, 1000, 4100000) == 1000,
          "call uptime equals current uptime");
    char text[DM_CALL_TIME_CAP];
    struct tm call = { .tm_year = 126, .tm_mon = 10, .tm_mday = 27,
                       .tm_hour = 14, .tm_min = 5 };
    struct tm now = call;
    now.tm_hour = 23;
    dm_format_call_time(1, &call, &now, text);
    CHECK(strcmp(text, "14:05") == 0, "today: time only");
    now.tm_mday = 28;
    dm_format_call_time(1, &call, &now, text);
    CHECK(strcmp(text, "27.11. 14:05") == 0, "yesterday: date and time");
    dm_format_call_time(0, &call, &now, text);
    CHECK(text[0] == '\0', "unknown epoch: no time");
    dm_format_call_time(1, NULL, &now, text);
    CHECK(text[0] == '\0', "missing call time: no time");
    call.tm_hour = 23;
    call.tm_min = 59;
    now.tm_hour = 0;
    now.tm_min = 0;
    dm_format_call_time(1, &call, &now, text);
    CHECK(strcmp(text, "27.11. 23:59") == 0, "across midnight: date shown");
    call.tm_year = 125;
    call.tm_mon = 11;
    call.tm_mday = 31;
    now.tm_year = 126;
    now.tm_mon = 0;
    now.tm_mday = 1;
    dm_format_call_time(1, &call, &now, text);
    CHECK(strcmp(text, "31.12. 23:59") == 0, "year change: date shown");
    now = call;
    now.tm_year++;
    dm_format_call_time(1, &call, &now, text);
    CHECK(strcmp(text, "31.12. 23:59") == 0, "same day in another year: date shown");
    dm_format_call_time(1, &call, NULL, text);
    CHECK(strcmp(text, "31.12. 23:59") == 0, "unknown current time: date shown");
    call.tm_min = 60;
    dm_format_call_time(1, &call, &now, text);
    CHECK(text[0] == '\0', "invalid local time: no partial output");
}

static void test_connection(void)
{
    dm_connection_state_t s = {0};
    int64_t grace = DM_CONNECTION_GRACE_US;
    printf("connection indicator:\n");
    CHECK(dm_connection_update(&s, 0, false, false, true, true, false, true)
          == DM_CONNECTION_OK,
          "Wi-Fi loss starts grace period");
    CHECK(dm_connection_update(&s, grace - 1, false, false, true, true, false, true)
          == DM_CONNECTION_OK,
          "short Wi-Fi loss keeps logo");
    CHECK(dm_connection_update(&s, grace, false, false, true, true, false, true)
          == DM_CONNECTION_WIFI,
          "Wi-Fi loss wins over SIP loss");
    CHECK(dm_connection_visible(DM_CONNECTION_WIFI, true) == DM_CONNECTION_OK,
          "caller screen has priority");
    CHECK(dm_connection_visible(DM_CONNECTION_WIFI, false) == DM_CONNECTION_WIFI,
          "indicator appears after caller hold");
    CHECK(dm_connection_update(&s, grace + 1, false, true, true, true, false, true)
          == DM_CONNECTION_OK,
          "changing fault restarts grace period");
    CHECK(dm_connection_update(&s, 2 * grace, false, true, true, true, false, true)
          == DM_CONNECTION_OK,
          "SIP loss shorter than grace keeps logo");
    CHECK(dm_connection_update(&s, 2 * grace + 1, false, true, true, true, false, true)
          == DM_CONNECTION_SIP,
          "persistent SIP loss shows phone");
    CHECK(dm_connection_update(&s, 2 * grace + 2, false, true, true, true, true, true)
          == DM_CONNECTION_OK,
          "registration recovery restores logo");
    CHECK(dm_connection_update(&s, 3 * grace, false, true, true, false, false, true)
          == DM_CONNECTION_OK,
          "unconfigured SIP keeps logo");
    CHECK(dm_connection_update(&s, 4 * grace, true, false, true, true, false, true)
          == DM_CONNECTION_OK,
          "WPS pairing suppresses Wi-Fi loss");
    CHECK(dm_connection_update(&s, 5 * grace, false, false, true, true, false, true)
          == DM_CONNECTION_OK,
          "pairing ends before new grace period");
    CHECK(dm_connection_update(&s, 6 * grace, false, false, true, true, false, true)
          == DM_CONNECTION_WIFI,
          "Wi-Fi loss after pairing gets its own grace period");
    CHECK(dm_connection_update(&s, 6 * grace + 1, false, true, true, false, false, true)
          == DM_CONNECTION_OK,
          "Wi-Fi recovery in setup restores logo");
    CHECK(dm_connection_update(&s, 7 * grace, false, true, true, true, false, true)
          == DM_CONNECTION_OK,
          "SIP loss starts new grace period");
    CHECK(dm_connection_update(&s, 7 * grace + 1, false, true, true, true, true, true)
          == DM_CONNECTION_OK,
          "short SIP glitch recovers");
    CHECK(dm_connection_update(&s, 8 * grace, false, true, true, true, false, true)
          == DM_CONNECTION_OK,
          "a second SIP glitch does not reuse the previous grace period");
    CHECK(dm_connection_update(&s, 9 * grace, false, true, true, true, false, true)
          == DM_CONNECTION_SIP,
          "second SIP loss eventually shows phone");
    CHECK(dm_connection_update(&s, 9 * grace + 1, true, false, true, true, false, true)
          == DM_CONNECTION_OK,
          "WPS hides an existing indicator immediately");
    CHECK(dm_connection_update(&s, 10 * grace, false, false, true, true, false, true)
          == DM_CONNECTION_OK,
          "Wi-Fi glitch starts grace");
    CHECK(dm_connection_update(&s, 10 * grace + 1, false, true, true, true, true, true)
          == DM_CONNECTION_OK,
          "short Wi-Fi glitch recovers");
    CHECK(dm_connection_update(&s, 11 * grace, false, false, true, true, false, true)
          == DM_CONNECTION_OK,
          "new Wi-Fi loss gets a fresh grace period");

    dm_connection_state_t token = {0};
    CHECK(dm_connection_update(&token, 0, false, true, true, true, true, false)
          == DM_CONNECTION_OK, "rejected token starts grace");
    CHECK(dm_connection_update(&token, grace - 1, false, true, true, true, true, false)
          == DM_CONNECTION_OK, "rejected token waits for grace");
    CHECK(dm_connection_update(&token, grace, false, true, true, true, true, false)
          == DM_CONNECTION_TOKEN, "persistent rejected token shows key");
    CHECK(dm_connection_visible(DM_CONNECTION_TOKEN, true) == DM_CONNECTION_OK,
          "caller takes priority over token fault");
    CHECK(dm_connection_update(&token, grace + 1, false, true, true, true, false, false)
          == DM_CONNECTION_OK, "SIP loss starts a fresh grace over token fault");
    CHECK(dm_connection_update(&token, 2 * grace + 1, false, true, true, true, false, false)
          == DM_CONNECTION_SIP, "SIP loss takes priority over rejected token");
    CHECK(dm_connection_update(&token, 2 * grace + 2, false, true, true, true, true, false)
          == DM_CONNECTION_OK, "registration recovery starts token grace");
    CHECK(dm_connection_update(&token, 3 * grace + 2, false, true, true, true, true, false)
          == DM_CONNECTION_TOKEN, "token fault returns after its own grace");
    CHECK(dm_connection_update(&token, 3 * grace + 3, false, true, true, true, true, true)
          == DM_CONNECTION_OK, "valid token restores logo");
    CHECK(dm_connection_update(&token, 4 * grace, false, true, false, true, false, false)
          == DM_CONNECTION_OK, "missing token suppresses SIP and token faults");
    CHECK(dm_connection_update(&token, 5 * grace, false, true, true, false, true, false)
          == DM_CONNECTION_OK, "unconfigured SIP suppresses token fault");
    CHECK(dm_connection_update(&token, 6 * grace, false, false, false, false, false, false)
          == DM_CONNECTION_OK, "Wi-Fi loss in setup starts grace");
    CHECK(dm_connection_update(&token, 7 * grace, false, false, false, false, false, false)
          == DM_CONNECTION_WIFI, "Wi-Fi loss takes priority over setup");
    CHECK(dm_connection_update(&token, 8 * grace, true, false, false, false, false, false)
          == DM_CONNECTION_OK, "WPS takes priority over every fault");

    const int sizes[][2] = {{160, 80}, {240, 135}};
    for (int i = 0; i < 2; i++) {
        dm_layout_t l;
        dm_layout(sizes[i][0], sizes[i][1], &l);
        CHECK(l.icon_size > 0 && l.icon_x >= 0 && l.icon_y >= 0
              && l.icon_x + l.icon_size <= l.width
              && l.icon_y + l.icon_size <= l.height
              && abs(2 * l.icon_x + l.icon_size - l.width) <= 1
              && abs(2 * l.icon_y + l.icon_size - l.height) <= 1,
              i == 0 ? "160x80 icon centered and inside"
                     : "240x135 icon centered and inside");
    }
}

static void test_connection_icons(void)
{
    const int sizes[] = {60, 101};
    printf("connection icon geometry:\n");
    for (int k = DM_CONNECTION_WIFI; k <= DM_CONNECTION_TOKEN; k++) {
        for (int i = 0; i < 2; i++) {
            int size = sizes[i];
            int counts[DM_ICON_OUTLINE + 1] = {0};
            bool outside = false, quadrant = true, mark_inside = true;
            for (int y = -1; y <= size; y++) {
                for (int x = -1; x <= size; x++) {
                    dm_icon_pixel_t p = dm_icon_pixel(k, size, x, y);
                    if (x < 0 || y < 0 || x >= size || y >= size) {
                        if (p != DM_ICON_NONE) outside = true;
                        continue;
                    }
                    counts[p]++;
                    if ((p == DM_ICON_TRIANGLE || p == DM_ICON_MARK
                         || p == DM_ICON_OUTLINE)
                        && (x < size / 2 || y < size / 2)) quadrant = false;
                    if (p == DM_ICON_MARK) {
                        int px = (2 * x + 1) * 500 / size;
                        int py = (2 * y + 1) * 500 / size;
                        int dx = abs(px - 800);
                        if (py < 640 || py > 915
                            || dx * 340 > (py - 640) * 190) mark_inside = false;
                    }
                }
            }
            CHECK(counts[DM_ICON_GLYPH] > 0, "glyph exists");
            CHECK(counts[DM_ICON_TRIANGLE] > 0 && quadrant,
                  "warning triangle is in lower right quadrant");
            CHECK(counts[DM_ICON_OUTLINE] > 0, "warning has an outline");
            CHECK(counts[DM_ICON_MARK] > 0 && mark_inside, "mark is inside triangle");
            CHECK(!outside, "no pixels outside icon square");
        }
    }
    CHECK(dm_icon_pixel(DM_CONNECTION_OK, 60, 30, 30) == DM_ICON_NONE,
          "healthy connection has no icon");
    CHECK(dm_icon_pixel(DM_CONNECTION_WIFI, 0, 0, 0) == DM_ICON_NONE,
          "empty icon has no pixels");
}

int main(void)
{
    stats_setup();
    calls_test_open();
    test_badge();
    test_call_screen();
    test_call_screen_wide();
    test_name_wrap();
    test_layout();
    test_call_time();
    test_connection();
    test_connection_icons();
    test_utf8();
    test_glyphs();
    printf("\n%d tests, %d failures\n", tests_run, tests_failed);
    calls_test_cleanup();
    return tests_failed ? 1 : 0;
}
