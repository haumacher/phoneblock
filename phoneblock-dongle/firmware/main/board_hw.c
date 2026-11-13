#include "board_hw.h"

#include <stdio.h>
#include <string.h>

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

// Parses a decimal pin 0..63 at *p, advancing *p past it.
static bool parse_pin(const char **p, int *out)
{
    const char *s = *p;
    int v = 0, n = 0;
    while (*s >= '0' && *s <= '9' && n < 2) {
        v = v * 10 + (*s - '0');
        s++;
        n++;
    }
    if (n == 0 || v > 63) return false;
    *p = s;
    *out = v;
    return true;
}

static bool starts(const char **p, const char *prefix)
{
    size_t n = strlen(prefix);
    if (strncmp(*p, prefix, n) != 0) return false;
    *p += n;
    return true;
}

bool board_led_parse(const char *spec, board_led_t *out)
{
    if (!spec) return false;
    board_led_t led = { .type = BOARD_LED_NONE, .pin = -1, .clk = -1 };
    const char *p = spec;
    if (strcmp(p, "none") == 0) {
        *out = led;
        return true;
    }
    if (starts(&p, "gpio:")) {
        led.type = BOARD_LED_GPIO;
        if (!parse_pin(&p, &led.pin)) return false;
        if (strcmp(p, ":low") == 0) {
            led.active_low = true;
            p += 4;
        }
    } else if (starts(&p, "apa102:")) {
        led.type = BOARD_LED_APA102;
        if (!parse_pin(&p, &led.pin)) return false;
        if (*p++ != ':') return false;
        if (!parse_pin(&p, &led.clk)) return false;
    } else if (starts(&p, "ws2812:")) {
        led.type = BOARD_LED_WS2812;
        if (!parse_pin(&p, &led.pin)) return false;
    } else {
        return false;
    }
    if (*p != '\0') return false;
    *out = led;
    return true;
}

void board_led_format(const board_led_t *led, char *buf, size_t cap)
{
    switch (led->type) {
    case BOARD_LED_GPIO:
        snprintf(buf, cap, "gpio:%d%s", led->pin, led->active_low ? ":low" : "");
        break;
    case BOARD_LED_APA102:
        snprintf(buf, cap, "apa102:%d:%d", led->pin, led->clk);
        break;
    case BOARD_LED_WS2812:
        snprintf(buf, cap, "ws2812:%d", led->pin);
        break;
    case BOARD_LED_NONE:
    default:
        snprintf(buf, cap, "none");
        break;
    }
}

static const char *check_pin(int pin, const board_pins_t *pins, bool display_on,
                             bool display_switched_off)
{
    static char reason[64];
    if (pin < 0 || pin >= pins->gpio_count) {
        snprintf(reason, sizeof(reason), "GPIO %d does not exist on this chip", pin);
        return reason;
    }
    uint64_t bit = 1ULL << pin;
    if (pins->forbidden & bit) {
        snprintf(reason, sizeof(reason), "GPIO %d is reserved (flash, USB or console)", pin);
        return reason;
    }
    if (display_on && (pins->display_pins & bit)) {
        snprintf(reason, sizeof(reason), display_switched_off
                 ? "GPIO %d belongs to the display until the next restart"
                 : "GPIO %d is used by the display", pin);
        return reason;
    }
    return NULL;
}

const char *board_led_check(const board_led_t *led, const board_pins_t *pins,
                            bool display_on, bool display_switched_off)
{
    if (led->type == BOARD_LED_NONE) return NULL;
    const char *r = check_pin(led->pin, pins, display_on, display_switched_off);
    if (r || led->type != BOARD_LED_APA102) return r;
    if (led->clk == led->pin) return "APA102 data and clock must be different pins";
    return check_pin(led->clk, pins, display_on, display_switched_off);
}

bool board_display_fits(const board_pins_t *pins)
{
    return pins->display_pins != 0 && (pins->display_pins & pins->forbidden) == 0;
}
