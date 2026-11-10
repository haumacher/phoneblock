// Host test for board_hw.c — the status LED setting (#730): spec parsing,
// the canonical form, and the pin rules a stored LED must pass before the
// firmware drives it. Pure libc.
#include <stdio.h>
#include <string.h>

#include "board_hw.h"

static int tests_run, tests_failed;

#define CHECK(cond, what) do {                                  \
    tests_run++;                                                \
    if (cond) printf("  ok: %s\n", (what));                     \
    else { tests_failed++; printf("  FAIL: %s (%s:%d)\n",       \
                                  (what), __FILE__, __LINE__); } \
} while (0)

// write → read back → equal, through the canonical spec.
static void roundtrip(const char *spec)
{
    board_led_t a, b;
    char out[BOARD_LED_SPEC_CAP];
    bool ok = board_led_parse(spec, &a);
    board_led_format(&a, out, sizeof(out));
    CHECK(ok && strcmp(out, spec) == 0 && board_led_parse(out, &b)
          && a.type == b.type && a.pin == b.pin && a.clk == b.clk
          && a.active_low == b.active_low, spec);
}

static void test_parse(void)
{
    printf("parse / format\n");
    roundtrip("none");
    roundtrip("gpio:8:low");
    roundtrip("gpio:2");
    roundtrip("apa102:40:39");
    roundtrip("ws2812:48");
    roundtrip("ws2812:0");

    board_led_t l;
    CHECK(board_led_parse("gpio:8:low", &l) && l.type == BOARD_LED_GPIO
          && l.pin == 8 && l.active_low && l.clk == -1, "gpio fields");
    CHECK(board_led_parse("apa102:40:39", &l) && l.type == BOARD_LED_APA102
          && l.pin == 40 && l.clk == 39 && !l.active_low, "apa102 fields");
    CHECK(board_led_parse("none", &l) && l.type == BOARD_LED_NONE && l.pin == -1,
          "none fields");
    CHECK(board_led_is_rgb(&(board_led_t){ .type = BOARD_LED_WS2812 })
          && !board_led_is_rgb(&(board_led_t){ .type = BOARD_LED_GPIO }), "rgb");

    // Leading zero accepted, written back canonical.
    char out[BOARD_LED_SPEC_CAP];
    CHECK(board_led_parse("gpio:08", &l) && (board_led_format(&l, out, sizeof(out)),
          strcmp(out, "gpio:8") == 0), "gpio:08 → gpio:8");

    const char *bad[] = {
        "", "auto", "gpio", "gpio:", "gpio:x", "gpio:64", "gpio:123", "gpio:8:high",
        "gpio:8:low:", "gpio:-1", "apa102:40", "apa102:40:", "apa102:40;39",
        "apa102:40:39:1", "ws2812:", "ws2812:48:low", "WS2812:48", " ws2812:48",
        "ws2812:48 ", "none:1", "rgb:48",
    };
    for (size_t i = 0; i < sizeof(bad) / sizeof(bad[0]); i++) {
        board_led_t untouched = { .type = BOARD_LED_APA102, .pin = 7, .clk = 9 };
        board_led_t x = untouched;
        char what[64];
        snprintf(what, sizeof(what), "refused: \"%s\"", bad[i]);
        CHECK(!board_led_parse(bad[i], &x) && x.pin == 7 && x.clk == 9, what);
    }
    CHECK(!board_led_parse(NULL, &l), "refused: NULL");
}

static void test_check(void)
{
    printf("pin rules\n");
    // An S3-like chip: 49 pins, 26..37 flash/PSRAM, 19/20 USB; display on
    // 1-5 and 38 (T-Dongle-S3).
    board_pins_t p = {
        .gpio_count   = 49,
        .forbidden    = (((1ULL << 38) - 1) & ~((1ULL << 26) - 1))
                        | (1ULL << 19) | (1ULL << 20),
        .display_pins = 0x3EULL | (1ULL << 38),
    };
    board_led_t l;
    board_led_parse("ws2812:48", &l);
    CHECK(board_led_check(&l, &p, true, false) == NULL, "ws2812:48 fine");
    board_led_parse("apa102:40:39", &l);
    CHECK(board_led_check(&l, &p, true, false) == NULL, "apa102:40:39 fine next to the display");
    board_led_parse("none", &l);
    CHECK(board_led_check(&l, &p, true, false) == NULL, "none always fine");

    const char *r;
    board_led_parse("ws2812:49", &l);
    r = board_led_check(&l, &p, false, false);
    CHECK(r && strstr(r, "GPIO 49") && strstr(r, "does not exist"), "pin beyond the chip");
    board_led_parse("gpio:30", &l);
    r = board_led_check(&l, &p, false, false);
    CHECK(r && strstr(r, "GPIO 30") && strstr(r, "reserved"), "flash pin refused");
    board_led_parse("gpio:20:low", &l);
    CHECK(board_led_check(&l, &p, false, false) != NULL, "USB pin refused");
    board_led_parse("gpio:38", &l);
    r = board_led_check(&l, &p, true, false);
    CHECK(r && strstr(r, "display"), "display pin refused with display on");
    CHECK(board_led_check(&l, &p, false, false) == NULL, "display pin fine with display off");
    r = board_led_check(&l, &p, true, true);
    CHECK(r && strstr(r, "GPIO 38") && strstr(r, "until the next restart"),
          "display pin refused while a started display is switched off");
    board_led_parse("apa102:40:3", &l);
    r = board_led_check(&l, &p, true, false);
    CHECK(r && strstr(r, "GPIO 3"), "APA102 clock on a display pin refused");
    board_led_parse("apa102:40:40", &l);
    CHECK(board_led_check(&l, &p, false, false) != NULL, "APA102 data == clock refused");
    board_led_parse("apa102:40:26", &l);
    CHECK(board_led_check(&l, &p, false, false) != NULL, "APA102 clock on flash refused");
}

int main(void)
{
    test_parse();
    test_check();
    printf("test_board_hw: %d tests, %d failures\n", tests_run, tests_failed);
    return tests_failed ? 1 : 0;
}
