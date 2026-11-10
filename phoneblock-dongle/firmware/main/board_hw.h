#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// Board hardware description (#730): which status LED the dongle drives and
// on which pins. A chip cannot tell which board it is soldered onto — there
// is no board ID, the eFuses describe the chip only — so the LED is a
// setting (config key "led_hw"), with a detected default (board.c) for the
// boards we know. Pure C, no ESP-IDF: parsing and the pin rules are
// host-tested (test/test_board_hw.c).
//
// Spec syntax, as stored and as the web UI sends it:
//   "none"                     no status LED
//   "gpio:<pin>"               plain LED, lit at a HIGH level
//   "gpio:<pin>:low"           plain LED, lit at a LOW level
//   "apa102:<data>:<clock>"    APA102 ("DotStar") RGB LED
//   "ws2812:<pin>"             WS2812 ("NeoPixel") RGB LED
// The empty string is not a spec: it means "not set, use the detected
// default".

typedef enum {
    BOARD_LED_NONE,
    BOARD_LED_GPIO,
    BOARD_LED_APA102,
    BOARD_LED_WS2812,
} board_led_type_t;

typedef struct {
    board_led_type_t type;
    int  pin;          // GPIO / APA102 data / WS2812 data; -1 for NONE
    int  clk;          // APA102 clock; -1 otherwise
    bool active_low;   // GPIO only
} board_led_t;

// Longest spec: "apa102:48:48" / "gpio:48:low", plus room.
#define BOARD_LED_SPEC_CAP 24

// Whether the LED can show colours (APA102, WS2812).
static inline bool board_led_is_rgb(const board_led_t *led)
{
    return led->type == BOARD_LED_APA102 || led->type == BOARD_LED_WS2812;
}

// Parses a spec (syntax only, pins 0..63). False for "" and for anything
// malformed; *out is then untouched.
bool board_led_parse(const char *spec, board_led_t *out);

// The canonical spec of `led` (round-trips through board_led_parse).
void board_led_format(const board_led_t *led, char *buf, size_t cap);

// The pin rules of the chip the firmware runs on.
typedef struct {
    int      gpio_count;    // valid pins are 0..gpio_count-1
    uint64_t forbidden;     // pins never to drive: flash/PSRAM bus, USB,
                            // console, input-only pins
    uint64_t display_pins;  // pins of the built-in display (0 = none)
} board_pins_t;

// Checks `led` against the chip's pin rules; with display_on, its pins must
// not collide with the display's. With display_on but display_switched_off,
// the pins are the display's until the next restart (it was running in this
// boot), and the reason says so. NULL when fine, else a short English
// reason naming the pin (static storage, for a log line or a 400 answer).
const char *board_led_check(const board_led_t *led, const board_pins_t *pins,
                            bool display_on, bool display_switched_off);
