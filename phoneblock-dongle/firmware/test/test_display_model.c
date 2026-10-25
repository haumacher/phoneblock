// Host test for display_model.c: what the T-Dongle-S3 display shows.
//
//   * the spam badge counts the call-list entries the web UI renders as
//     blocked spam, recounted from the stats ring (clear, re-rating and
//     ring overflow included);
//   * the caller screen: name over number, or the number with its rating,
//     text fitted to the 160 px panel with an ellipsis;
//   * UTF-8 decoding never runs past the string, whatever the input.
//
// Built with AddressSanitizer (see the Makefile), so an overrun aborts.
// Synthetic numbers only.
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "display_model.h"
#include "stats.h"

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
    int n = stats_count_calls(dm_is_listed_spam);
    // The same rule over a snapshot gives the same count.
    stats_call_t calls[STATS_MAX_CALLS];
    int k = stats_snapshot_calls(calls, STATS_MAX_CALLS);
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
    CHECK(badge() == STATS_MAX_CALLS, "12 spam calls → 10 (ring size)");
    stats_record_call("+4930999999", "", VERDICT_LEGITIMATE);
    CHECK(badge() == STATS_MAX_CALLS - 1, "oldest spam call dropped off the ring");
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

    stats_call_t c = make_call("+4930123456", "Max Müller", PB_ASSESS_LEGITIMATE, 0);
    dm_call_screen(&c, &s);
    CHECK(s.named, "name present → named screen");
    CHECK(strcmp(s.main.text, "Max Müller") == 0, "name on the main line");
    CHECK(s.main.font == &lcd_font_large, "name in the large font");
    CHECK(s.main.width == 10 * lcd_font_large.w, "name width: 10 cells (ü is one)");
    CHECK(strcmp(s.sub.text, "+4930123456") == 0, "number on the line below");
    CHECK(s.sub.font == &lcd_font_small, "number below in the small font");
    CHECK(s.cls == DM_CLASS_LEGIT, "phone-book caller → legit class");
    CHECK(s.band.text[0] == '\0', "no band text for a contact");

    c = make_call("+4930123456", "", PB_ASSESS_SPAM, 12);
    dm_call_screen(&c, &s);
    CHECK(!s.named, "no name → number screen");
    CHECK(strcmp(s.main.text, "+4930123456") == 0, "number on the main line");
    CHECK(s.main.font == &lcd_font_large, "short number in the large font");
    CHECK(s.cls == DM_CLASS_SPAM, "spam class");
    CHECK(strcmp(s.votes, "12") == 0, "vote text \"12\"");
    CHECK(strcmp(s.band.text, "SPAM 12\xC3\x97") == 0, "band \"SPAM 12×\"");
    CHECK(s.band.width == 8 * lcd_font_small.w, "band width: 8 cells");

    c = make_call("+4930123456", "", PB_ASSESS_SUSPECT, 2);
    dm_call_screen(&c, &s);
    CHECK(s.cls == DM_CLASS_SUSPECT, "suspected → suspect class");
    CHECK(strcmp(s.votes, "2") == 0, "suspect vote text \"2\"");
    CHECK(strcmp(s.band.text, "SPAM? 2\xC3\x97") == 0, "band \"SPAM? 2×\"");

    c = make_call("+4930123456", "", PB_ASSESS_SPAM_LIST, 0);
    dm_call_screen(&c, &s);
    CHECK(s.votes[0] == '\0' && strcmp(s.band.text, "SPAM") == 0,
          "list spam without counts → plain \"SPAM\"");

    c = make_call("**622", "", PB_ASSESS_TEST, 0);
    dm_call_screen(&c, &s);
    CHECK(s.cls == DM_CLASS_NEUTRAL && strcmp(s.band.text, "TEST") == 0,
          "test call → neutral, \"TEST\"");

    c = make_call("+4930123456", "", PB_ASSESS_UNKNOWN, 0);
    snprintf(c.location, sizeof(c.location), "%s", "Berlin");
    dm_call_screen(&c, &s);
    CHECK(s.cls == DM_CLASS_NEUTRAL && strcmp(s.band.text, "Berlin") == 0,
          "unknown caller → neutral, location in the band");

    c = make_call("+4930123456", "\xE7\x8E\x8B\xE5\xB0\x8F\xE6\x98\x8E",
                  PB_ASSESS_LEGITIMATE, 0);                     // CJK
    dm_call_screen(&c, &s);
    CHECK(!s.named && strcmp(s.main.text, "+4930123456") == 0,
          "CJK name → falls back to the number");

    c = make_call("+4930123456", "\xD0\x98\xD0\xB2\xD0\xB0\xD0\xBD",
                  PB_ASSESS_LEGITIMATE, 0);                     // Иван
    dm_call_screen(&c, &s);
    CHECK(!s.named && strcmp(s.main.text, "+4930123456") == 0,
          "Cyrillic name → falls back to the number");

    c = make_call("+4930123456", "Max \xC3", PB_ASSESS_LEGITIMATE, 0);
    dm_call_screen(&c, &s);
    CHECK(!s.named, "invalid UTF-8 name → falls back to the number");

    c = make_call("030123456", "030123456", PB_ASSESS_UNKNOWN, 0);
    dm_call_screen(&c, &s);
    CHECK(!s.named, "digits-only display name is not a name");

    c = make_call("+4930123456", "Maximilian Mustermann-Lüdenscheid",
                  PB_ASSESS_LEGITIMATE, 0);
    dm_call_screen(&c, &s);
    // 154 px / 9 px = 17 cells: 16 code points and the ellipsis.
    CHECK(DM_TEXT_MAX_PX / lcd_font_large.w == 17, "large font: 17 cells per line");
    CHECK(strcmp(s.main.text, "Maximilian Muste" ELLIPSIS) == 0,
          "long name truncated with an ellipsis");
    CHECK(s.main.width == 17 * lcd_font_large.w, "truncated width 153 px");
    CHECK(s.main.width <= DM_TEXT_MAX_PX && s.main.width <= DM_WIDTH,
          "truncated name fits the 160 px panel");

    c = make_call("+4930123456", "Müller-Lüdenscheidtäöü", PB_ASSESS_LEGITIMATE, 0);
    dm_call_screen(&c, &s);
    CHECK(strcmp(s.main.text, "Müller-Lüdensche" ELLIPSIS) == 0
          && dm_text_width(&lcd_font_large, s.main.text) == s.main.width,
          "truncation counts code points, not bytes");

    c = make_call("+49301234567890123", "", PB_ASSESS_UNKNOWN, 0);
    dm_call_screen(&c, &s);
    CHECK(s.main.font == &lcd_font_small
          && strcmp(s.main.text, "+49301234567890123") == 0,
          "number too wide for the large font → small font, complete");

    c = make_call("", "", PB_ASSESS_UNKNOWN, 0);
    dm_call_screen(&c, &s);
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
    int w = dm_fit_text(&lcd_font_small, "\xC3\xA4\xC3\xB6\xC3\xBC\xC3\xA4\xC3\xB6",
                        DM_TEXT_MAX_PX, out, sizeof(out));
    CHECK(strlen(out) < sizeof(out) && strcmp(out, "\xC3\xA4\xC3\xB6" ELLIPSIS) == 0
          && w == 3 * lcd_font_small.w,
          "byte capacity: cut at a code point boundary, ellipsis kept");

    w = dm_fit_text(&lcd_font_small, "ab\x80" "cd", DM_TEXT_MAX_PX, out, sizeof(out));
    CHECK(w == 5 * lcd_font_small.w, "a malformed byte counts as one cell");
    CHECK(dm_fit_text(&lcd_font_small, "abc", DM_TEXT_MAX_PX, out, 3) == 0
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
    CHECK(DM_TEXT_MAX_PX <= DM_WIDTH, "text width within the panel");
}

int main(void)
{
    stats_setup();
    test_badge();
    test_call_screen();
    test_utf8();
    test_glyphs();
    printf("\n%d tests, %d failures\n", tests_run, tests_failed);
    return tests_failed ? 1 : 0;
}
