#include "board.h"

#include <stdint.h>
#include <string.h>

#include "driver/gpio.h"
#include "esp_chip_info.h"
#include "esp_log.h"
#include "sdkconfig.h"
#if CONFIG_IDF_TARGET_ESP32S3
#include "esp_efuse.h"
#include "esp_efuse_table.h"
#include "display.h"
#endif

#include "config.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static const char *TAG = "board";

#define BIT_RANGE(lo, hi) ((~0ULL >> (63 - (hi))) & ~((1ULL << (lo)) - 1))

#if CONFIG_IDF_TARGET_ESP32
static bool emb_flash(void)
{
    esp_chip_info_t info;
    esp_chip_info(&info);
    return (info.features & CHIP_FEATURE_EMB_FLASH) != 0;
}
#endif

#if CONFIG_IDF_TARGET_ESP32S3
// PSRAM_CAP and PSRAM_VENDOR (2 bits each): both zero means no PSRAM in the
// package — the T-Dongle-S3's plain ESP32-S3.
static bool s3_has_psram(void)
{
    uint8_t cap = 0, vendor = 0;
    esp_efuse_read_field_blob(ESP_EFUSE_PSRAM_CAP, &cap, 2);
    esp_efuse_read_field_blob(ESP_EFUSE_PSRAM_VENDOR, &vendor, 2);
    return cap != 0 || vendor != 0;
}
#endif

static const board_pins_t *pins(void)
{
    static board_pins_t p;
    if (p.gpio_count) return &p;
    p.gpio_count = GPIO_PIN_COUNT;
    for (int i = 0; i < GPIO_PIN_COUNT; i++) {
        if (!GPIO_IS_VALID_OUTPUT_GPIO(i)) p.forbidden |= 1ULL << i;
    }
#if CONFIG_IDF_TARGET_ESP32
    // Flash bus (PICO-D4: 6-9, 11, 16, 17 — GPIO 10 is free there and is
    // the dongle's LED), UART0 console 1/3 (Improv on the USB-UART boards).
    p.forbidden |= emb_flash()
        ? BIT_RANGE(6, 9) | (1ULL << 11) | (1ULL << 16) | (1ULL << 17)
        : BIT_RANGE(6, 11);
    p.forbidden |= (1ULL << 1) | (1ULL << 3);
#elif CONFIG_IDF_TARGET_ESP32C3
    // Flash bus 12-17, USB 18/19 (console and Improv).
    p.forbidden |= BIT_RANGE(12, 19);
#elif CONFIG_IDF_TARGET_ESP32S3
    // Flash and (octal) PSRAM bus 26-37, USB 19/20 (console and Improv).
    p.forbidden |= BIT_RANGE(26, 37) | (1ULL << 19) | (1ULL << 20);
    p.display_pins = DISPLAY_PIN_MASK;
#endif
    return &p;
}

bool board_display_supported(void)
{
#if CONFIG_IDF_TARGET_ESP32S3
    return true;
#else
    return false;
#endif
}

void board_detected(board_led_t *led, bool *display)
{
    *display = false;
    *led = (board_led_t){ .type = BOARD_LED_NONE, .pin = -1, .clk = -1 };
#if CONFIG_STATUS_LED_GPIO == -1
    return;
#elif CONFIG_STATUS_LED_GPIO >= 0
    led->type = BOARD_LED_GPIO;
    led->pin  = CONFIG_STATUS_LED_GPIO;
#ifdef CONFIG_STATUS_LED_ACTIVE_LOW
    led->active_low = true;
#endif
#if CONFIG_IDF_TARGET_ESP32S3
    *display = !s3_has_psram();
#endif
#else
#if CONFIG_IDF_TARGET_ESP32S3
    if (s3_has_psram()) {
        *led = (board_led_t){ .type = BOARD_LED_WS2812, .pin = 48, .clk = -1 };
    } else {
        *led = (board_led_t){ .type = BOARD_LED_APA102, .pin = 40, .clk = 39 };
        *display = true;
    }
#elif CONFIG_IDF_TARGET_ESP32C3
    *led = (board_led_t){ .type = BOARD_LED_GPIO, .pin = 8, .clk = -1, .active_low = true };
#else
    bool emb = emb_flash();
    *led = (board_led_t){ .type = BOARD_LED_GPIO, .pin = emb ? 10 : 2, .clk = -1,
                          .active_low = emb };
#endif
#endif
}

// The display's pins are taken while it is on, and — once started — until
// the next restart (display_was_started()).
static const char *check_led(const board_led_t *led, bool display)
{
#if CONFIG_IDF_TARGET_ESP32S3
    bool started = display_was_started();
    return board_led_check(led, pins(), display || started, !display && started);
#else
    return board_led_check(led, pins(), display, false);
#endif
}

static bool display_setting(const char *v, bool dflt)
{
    if (!board_display_supported()) return false;
    if (strcmp(v, "1") == 0) return true;
    if (strcmp(v, "0") == 0) return false;
    return dflt;
}

void board_effective(board_led_t *led, bool *display)
{
    board_led_t dled;
    bool ddisp;
    board_detected(&dled, &ddisp);
    *display = display_setting(config_display(), ddisp);

    const char *spec = config_led_hw();
    *led = dled;
    if (!spec[0]) return;
    board_led_t set;
    const char *why = board_led_parse(spec, &set)
        ? check_led(&set, *display) : "unknown LED type";
    if (why) {
        static bool s_warned;
        if (!s_warned) {
            s_warned = true;
            ESP_LOGW(TAG, "status LED setting \"%s\" ignored (%s); using the detected one",
                     spec, why);
        }
        return;
    }
    *led = set;
}

const char *board_check_settings(const char *led_hw, const char *display)
{
    board_led_t dled;
    bool ddisp;
    board_detected(&dled, &ddisp);
    if (display) {
        if (display[0] && strcmp(display, "0") != 0 && strcmp(display, "1") != 0)
            return "display must be 1, 0 or empty";
        if (strcmp(display, "1") == 0 && !board_display_supported())
            return "this firmware has no display support";
    }
    bool disp = display_setting(display ? display : config_display(), ddisp);
    const char *spec = led_hw ? led_hw : config_led_hw();
    board_led_t led = dled;
    if (spec[0] && !board_led_parse(spec, &led))
        return "LED: expected none, gpio:<pin>[:low], apa102:<data>:<clock> or ws2812:<pin>";
    return check_led(&led, disp);
}
