#include "status_led.h"

#include <string.h>

#include "driver/gpio.h"
#if CONFIG_IDF_TARGET_ESP32S3
#include "driver/rmt_tx.h"
#endif
#include "esp_chip_info.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "sdkconfig.h"

#include "api.h"
#if CONFIG_IDF_TARGET_ESP32S3
#include "board_s3.h"
#endif
#include "config.h"
#include "sip_register.h"
#include "wifi.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static const char *TAG = "status_led";

#define TICK_MS 50

// LilyGO T-Dongle-S3: one APA102 ("DotStar") RGB LED on two GPIOs, data
// and clock, BGR byte order (pins and order as in LilyGO's factory
// firmware). APA102_LED is the pseudo-pin resolve_status_led() returns
// for it: above the highest GPIO (48), yet within the 7 bits the task
// argument packs a pin into.
#define APA102_DI_GPIO 40
#define APA102_CI_GPIO 39
#define APA102_LED     0x7F
// Brightness 1..31 (the APA102's 5-bit global current), colour as B, G, R.
// Low: the LED sits a few millimetres behind a translucent case.
#define APA102_BRIGHTNESS 4
#define APA102_B 0xFF
#define APA102_G 0x60
#define APA102_R 0x00

// Other ESP32-S3 boards (Super Mini, DevKitC-1 v1.0, #730): one WS2812
// ("NeoPixel") RGB LED on GPIO 48, GRB byte order, driven by the RMT
// peripheral (the 800 kHz one-wire protocol is too fast for bit-banging
// through the GPIO driver). WS2812_LED is its pseudo-pin, like APA102_LED.
// Colour and brightness match the APA102's blue at its low brightness.
#define WS2812_GPIO    48
#define WS2812_LED     0x7E
#define WS2812_G 0x05
#define WS2812_R 0x00
#define WS2812_B 0x10

// Resolve CONFIG_STATUS_LED_GPIO into a concrete pin and polarity
// (or a sentinel telling the caller to disable the task):
//   -1            → caller-side "disable" sentinel kept verbatim.
//   -2 (default)  → auto-detect: chips with flash-in-package
//                   (PICO-D4) drive GPIO 10, which is the dongle
//                   target board's LED and is wired active-low;
//                   everything else falls back to GPIO 2, the
//                   on-board LED of ESP32-WROOM-32 dev boards
//                   (active-high). WROOM-32 routes the external
//                   SPI flash WP signal through GPIO 10, so we
//                   must not drive it there.
//                   ESP32-C3 builds use GPIO 8 instead, the
//                   active-low on-board LED of the C3 Super Mini.
//                   ESP32-S3 builds return APA102_LED on the T-Dongle-S3
//                   (no plain LED, only an APA102, see below) and
//                   WS2812_LED on every other S3 board (board_s3.h).
//   0..48         → explicit override, returned as-is. Polarity is
//                   then taken from CONFIG_STATUS_LED_ACTIVE_LOW so
//                   the user stays in control for non-standard
//                   wiring.
static int resolve_status_led(bool *active_low_out)
{
    const int kcfg = CONFIG_STATUS_LED_GPIO;
    if (kcfg == -1) return -1;
    if (kcfg >= 0) {
#ifdef CONFIG_STATUS_LED_ACTIVE_LOW
        *active_low_out = true;
#else
        *active_low_out = false;
#endif
        return kcfg;
    }

#if CONFIG_IDF_TARGET_ESP32S3
    *active_low_out = false;
    if (!board_is_t_dongle_s3()) {
        ESP_LOGI(TAG, "auto-detect → WS2812 on GPIO %d (ESP32-S3)", WS2812_GPIO);
        return WS2812_LED;
    }
    ESP_LOGI(TAG, "auto-detect → APA102 on GPIO %d/%d (ESP32-S3)",
             APA102_DI_GPIO, APA102_CI_GPIO);
    return APA102_LED;
#elif CONFIG_IDF_TARGET_ESP32C3
    *active_low_out = true;
    ESP_LOGI(TAG, "auto-detect → GPIO 8, active-low (ESP32-C3)");
    return 8;
#else
    esp_chip_info_t info;
    esp_chip_info(&info);
    bool emb_flash = (info.features & CHIP_FEATURE_EMB_FLASH) != 0;
    int  pin       = emb_flash ? 10 : 2;
    bool active_low = emb_flash;
    *active_low_out = active_low;
    ESP_LOGI(TAG, "auto-detect → GPIO %d, active-%s (%s flash)",
             pin,
             active_low ? "low" : "high",
             emb_flash ? "embedded" : "external");
    return pin;
#endif
}

typedef enum {
    ST_PAIRING,
    ST_CONNECTING,
    ST_SETUP,
    ST_DEGRADED,
    ST_READY,
} led_state_t;

static led_state_t derive_state(void)
{
    if (wifi_is_wps_active())                  return ST_PAIRING;
    if (!wifi_has_ip())                        return ST_CONNECTING;

    // SETUP — the user still has to fill something in. SIP is "not
    // configured" when host or user is empty (same definition the
    // web UI uses to gate its setup hero); the token is "not set"
    // when no Bearer string is stored.
    bool token_set     = strlen(config_phoneblock_token()) > 0;
    bool sip_configured = strlen(config_sip_host()) > 0
                         && strlen(config_sip_user()) > 0;
    if (!token_set || !sip_configured)         return ST_SETUP;

    // DEGRADED — everything has been filled in, but the dongle
    // cannot actually use it: either the SIP REGISTER is currently
    // not in place (bad credentials, registrar unreachable, transient
    // reconnect), or the last Bearer call to phoneblock.net got a
    // 401/403. Both are level signals — the LED returns to READY
    // automatically once the underlying state recovers, so the user
    // is not punished for a momentary glitch. For the why/what,
    // they open the web UI.
    if (!sip_register_is_registered())         return ST_DEGRADED;
    if (!api_token_is_valid())                 return ST_DEGRADED;

    return ST_READY;
}

// Period in TICK_MS units; on_mask is a bit-per-tick on/off schedule,
// LSB first. Sequences are chosen so the period is always a multiple
// of TICK_MS and fits in a uint32_t.
typedef struct {
    uint32_t on_mask;  // bit N set = LED on during tick N
    uint8_t  ticks;    // total ticks before the pattern wraps
} pattern_t;

static pattern_t pattern_for(led_state_t s, bool quiet)
{
    // Quiet mode inverts only the READY signal: a healthy dongle keeps
    // its LED dark, so any light at all means "look at me". The fault
    // states below are left untouched — they must stay visible.
    if (s == ST_READY && quiet) {
        return (pattern_t){ .on_mask = 0x0, .ticks = 1 };
    }
    switch (s) {
        case ST_PAIRING:
            // 100 ms on / 100 ms off  →  (on off) repeating every 4 ticks
            return (pattern_t){ .on_mask = 0x03, .ticks = 4 };
        case ST_CONNECTING:
            // 500 ms on / 500 ms off  →  period 20 ticks, first 10 on
            return (pattern_t){ .on_mask = 0x000003FFu, .ticks = 20 };
        case ST_SETUP:
            // 100 ms on / 900 ms off  →  period 20 ticks, first 2 on
            return (pattern_t){ .on_mask = 0x00000003u, .ticks = 20 };
        case ST_DEGRADED:
            // 900 ms on / 100 ms off  →  period 20 ticks, first 18 on.
            // Visual inverse of SETUP — mostly lit, brief dropout once
            // a second.
            return (pattern_t){ .on_mask = 0x0003FFFFu, .ticks = 20 };
        case ST_READY:
        default:
            // Solid on: every tick lit. ticks=1 keeps the mask simple.
            return (pattern_t){ .on_mask = 0x1, .ticks = 1 };
    }
}

// Packed (gpio, active_low) → single intptr_t for xTaskCreate's void*
// arg, so we don't need a separately allocated heap struct that would
// outlive status_led_start(). GPIO range is 0..48, polarity is one bit.
#define LED_ARG_PACK(gpio, active_low) \
    ((intptr_t)(((unsigned)(gpio) & 0x7Fu) | ((active_low) ? 0x80u : 0u)))
#define LED_ARG_GPIO(arg)        ((int)((intptr_t)(arg) & 0x7F))
#define LED_ARG_ACTIVE_LOW(arg)  (((intptr_t)(arg) & 0x80) != 0)

// Bit-banged APA102 frame for a single LED: 32 zero bits start frame,
// one LED frame (0b111 + 5-bit brightness, B, G, R), 32 one bits end
// frame. Data is latched on the rising clock edge; a GPIO write takes
// far longer than the APA102's minimum clock period, so no delays.
static void apa102_byte(uint8_t b)
{
    for (int i = 7; i >= 0; i--) {
        gpio_set_level(APA102_DI_GPIO, (b >> i) & 1);
        gpio_set_level(APA102_CI_GPIO, 1);
        gpio_set_level(APA102_CI_GPIO, 0);
    }
}

static void apa102_set(bool on)
{
    for (int i = 0; i < 4; i++) apa102_byte(0x00);
    apa102_byte(0xE0 | (on ? APA102_BRIGHTNESS : 0));
    apa102_byte(on ? APA102_B : 0);
    apa102_byte(on ? APA102_G : 0);
    apa102_byte(on ? APA102_R : 0);
    for (int i = 0; i < 4; i++) apa102_byte(0xFF);
}

#if CONFIG_IDF_TARGET_ESP32S3
static rmt_channel_handle_t s_ws_chan;
static rmt_encoder_handle_t s_ws_enc;

// RMT at 10 MHz (0.1 µs ticks): a 0 bit is 0.3 µs high + 0.9 µs low, a 1 bit
// 0.9 µs high + 0.3 µs low. The line idles low between frames, which is the
// latch; frames are 50 ms apart at most, far beyond the 50 µs reset.
static bool ws2812_init(void)
{
    rmt_tx_channel_config_t ch = {
        .gpio_num          = WS2812_GPIO,
        .clk_src           = RMT_CLK_SRC_DEFAULT,
        .resolution_hz     = 10 * 1000 * 1000,
        .mem_block_symbols = 48,
        .trans_queue_depth = 1,
    };
    rmt_bytes_encoder_config_t enc = {
        .bit0 = { .level0 = 1, .duration0 = 3, .level1 = 0, .duration1 = 9 },
        .bit1 = { .level0 = 1, .duration0 = 9, .level1 = 0, .duration1 = 3 },
        .flags.msb_first = 1,
    };
    esp_err_t err = rmt_new_tx_channel(&ch, &s_ws_chan);
    if (err == ESP_OK) err = rmt_new_bytes_encoder(&enc, &s_ws_enc);
    if (err == ESP_OK) err = rmt_enable(s_ws_chan);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "WS2812 on GPIO %d: %s", WS2812_GPIO, esp_err_to_name(err));
        return false;
    }
    return true;
}

static void ws2812_set(bool on)
{
    static uint8_t grb[3];   // must stay valid until the transfer is done
    grb[0] = on ? WS2812_G : 0;
    grb[1] = on ? WS2812_R : 0;
    grb[2] = on ? WS2812_B : 0;
    rmt_transmit_config_t tx = { .loop_count = 0 };
    if (rmt_transmit(s_ws_chan, s_ws_enc, grb, sizeof(grb), &tx) == ESP_OK) {
        rmt_tx_wait_all_done(s_ws_chan, 100);
    }
}
#endif

static void led_task(void *arg)
{
    const int  gpio       = LED_ARG_GPIO(arg);
    const bool active_low = LED_ARG_ACTIVE_LOW(arg);

    const bool apa102 = gpio == APA102_LED;
    const bool ws2812 = gpio == WS2812_LED;
#if CONFIG_IDF_TARGET_ESP32S3
    if (ws2812 && !ws2812_init()) {
        vTaskDelete(NULL);
        return;
    }
#endif
    if (!ws2812) {
        gpio_config_t cfg = {
            .pin_bit_mask = apa102
                ? (1ULL << APA102_DI_GPIO) | (1ULL << APA102_CI_GPIO)
                : 1ULL << gpio,
            .mode         = GPIO_MODE_OUTPUT,
            .pull_up_en   = GPIO_PULLUP_DISABLE,
            .pull_down_en = GPIO_PULLDOWN_DISABLE,
            .intr_type    = GPIO_INTR_DISABLE,
        };
        gpio_config(&cfg);
        if (apa102) gpio_set_level(APA102_CI_GPIO, 0);
    }

    // Diagnostic: report the smallest free stack seen so far — i.e. right
    // after gpio_config(), this task's deepest logging call. This is the
    // hard number behind the stack sizing (was a 2 KB stack overflowing
    // here once the log hook was added); keep an eye on it staying well
    // clear of 0.
    ESP_LOGI(TAG, "stack high-water mark: %u bytes free",
             (unsigned)uxTaskGetStackHighWaterMark(NULL));

    led_state_t last_state = (led_state_t)-1;
    bool        last_quiet = false;
    pattern_t   pat        = pattern_for(ST_CONNECTING, false);
    uint8_t     tick       = 0;
    int         last_on    = -1;   // RGB LEDs: only send a frame on a change

    while (1) {
        led_state_t s     = derive_state();
        bool        quiet = config_led_quiet();
        // Recompute on a quiet-mode toggle too, not just a state change:
        // the user may flip the setting while the dongle sits in READY,
        // when the state itself never changes.
        if (s != last_state || quiet != last_quiet) {
            pat = pattern_for(s, quiet);
            tick = 0;
            last_state = s;
            last_quiet = quiet;
        }
        bool on = (pat.on_mask >> tick) & 0x1u;
        if (apa102 || ws2812) {
            if (on != last_on) {
#if CONFIG_IDF_TARGET_ESP32S3
                if (ws2812) ws2812_set(on); else
#endif
                apa102_set(on);
                last_on = on;
            }
        } else {
            gpio_set_level(gpio, active_low ? !on : on);
        }

        tick = (uint8_t)((tick + 1) % pat.ticks);
        vTaskDelay(pdMS_TO_TICKS(TICK_MS));
    }
}

void status_led_start(void)
{
    bool active_low = false;
    int  gpio       = resolve_status_led(&active_low);
    if (gpio < 0) {
        ESP_LOGI(TAG, "LED task disabled (CONFIG_STATUS_LED_GPIO=-1)");
        return;
    }
    // 3 KB: the global log hook (log_capture.c) adds a ~64 B frame to
    // *every* log call — including the deep multi-arg INFO line
    // gpio_config() emits. 2 KB overflowed once the hook was added; 2.5 KB
    // left 804 B on the ESP32-C3 but only 324 B on the ESP32-S3, whose
    // Xtensa register windows make the same calls deeper. See the note on
    // stack sizing in log_capture.c.
    xTaskCreate(led_task, "status_led", 3072,
                (void *)LED_ARG_PACK(gpio, active_low), 1, NULL);
}
