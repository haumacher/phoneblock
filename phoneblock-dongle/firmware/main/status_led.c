#include "status_led.h"

#include <string.h>

#include "driver/gpio.h"
#include "driver/rmt_tx.h"
#include "soc/soc_caps.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "sdkconfig.h"

#include "api.h"
#include "board.h"
#include "config.h"
#include "sip_register.h"
#include "wifi.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static const char *TAG = "status_led";

#define TICK_MS 50

// The LED itself — type and pins — comes from board.h (a setting with a
// detected default). RGB LEDs (APA102, WS2812) show the state in colour on
// top of the blink pattern, so a single-colour LED keeps the same meaning.
// Colours are kept dim: the LED sits behind a translucent case or in a
// living room. R, G, B at full scale 255; the APA102 additionally runs at a
// low global current.
#define APA102_BRIGHTNESS 4      // 1..31, the APA102's 5-bit global current

typedef struct { uint8_t r, g, b; } rgb_t;

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

static rgb_t colour_for(led_state_t s)
{
    switch (s) {
        case ST_PAIRING:    return (rgb_t){ 0x00, 0x00, 0x30 };   // blue
        case ST_CONNECTING: return (rgb_t){ 0x28, 0x10, 0x00 };   // amber
        case ST_SETUP:      return (rgb_t){ 0x14, 0x14, 0x14 };   // white
        case ST_DEGRADED:   return (rgb_t){ 0x30, 0x00, 0x00 };   // red
        case ST_READY:
        default:            return (rgb_t){ 0x00, 0x20, 0x00 };   // green
    }
}

// --- drivers ------------------------------------------------------------

static board_led_t s_led;              // the LED being driven
// status_led_reconfigure() bumps the request, the task acknowledges it
// once the new LED is set up.
static volatile uint32_t s_reconf_req, s_reconf_done;
static TaskHandle_t s_task;

// Bit-banged APA102 frame for a single LED: 32 zero bits start frame,
// one LED frame (0b111 + 5-bit brightness, B, G, R), 32 one bits end
// frame. Data is latched on the rising clock edge; a GPIO write takes
// far longer than the APA102's minimum clock period, so no delays.
static void apa102_byte(uint8_t b)
{
    for (int i = 7; i >= 0; i--) {
        gpio_set_level(s_led.pin, (b >> i) & 1);
        gpio_set_level(s_led.clk, 1);
        gpio_set_level(s_led.clk, 0);
    }
}

static void apa102_set(rgb_t c)
{
    for (int i = 0; i < 4; i++) apa102_byte(0x00);
    // The colours above are scaled for the WS2812; the APA102 dims through
    // its global current instead, so stretch them back to full scale.
    apa102_byte(0xE0 | APA102_BRIGHTNESS);
    apa102_byte(c.b ? (uint8_t)(c.b * 5) : 0);
    apa102_byte(c.g ? (uint8_t)(c.g * 5) : 0);
    apa102_byte(c.r ? (uint8_t)(c.r * 5) : 0);
    for (int i = 0; i < 4; i++) apa102_byte(0xFF);
}

// WS2812 through the RMT peripheral (the 800 kHz one-wire protocol is too
// fast to bit-bang through the GPIO driver). RMT at 10 MHz (0.1 µs ticks):
// a 0 bit is 0.3 µs high + 0.9 µs low, a 1 bit 0.9 µs high + 0.3 µs low.
// The line idles low between frames, which is the latch.
static rmt_channel_handle_t s_ws_chan;
static rmt_encoder_handle_t s_ws_enc;

static bool ws2812_open(void)
{
    rmt_tx_channel_config_t ch = {
        .gpio_num          = s_led.pin,
        .clk_src           = RMT_CLK_SRC_DEFAULT,
        .resolution_hz     = 10 * 1000 * 1000,
        .mem_block_symbols = SOC_RMT_MEM_WORDS_PER_CHANNEL,
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
        ESP_LOGW(TAG, "WS2812 on GPIO %d: %s", s_led.pin, esp_err_to_name(err));
        return false;
    }
    return true;
}

static void ws2812_close(void)
{
    if (s_ws_chan) {
        rmt_disable(s_ws_chan);
        rmt_del_channel(s_ws_chan);
        s_ws_chan = NULL;
    }
    if (s_ws_enc) {
        rmt_del_encoder(s_ws_enc);
        s_ws_enc = NULL;
    }
}

static void ws2812_set(rgb_t c)
{
    static uint8_t grb[3];   // must stay valid until the transfer is done
    grb[0] = c.g;
    grb[1] = c.r;
    grb[2] = c.b;
    rmt_transmit_config_t tx = { .loop_count = 0 };
    if (rmt_transmit(s_ws_chan, s_ws_enc, grb, sizeof(grb), &tx) == ESP_OK) {
        rmt_tx_wait_all_done(s_ws_chan, 100);
    }
}

// Takes over the LED of board_effective(). False when there is none (or
// it could not be set up).
static bool led_open(void)
{
    bool display;
    board_effective(&s_led, &display);
    char spec[BOARD_LED_SPEC_CAP];
    board_led_format(&s_led, spec, sizeof(spec));
    ESP_LOGI(TAG, "status LED: %s", spec);
    switch (s_led.type) {
    case BOARD_LED_WS2812:
        return ws2812_open();
    case BOARD_LED_GPIO:
    case BOARD_LED_APA102: {
        gpio_config_t cfg = {
            .pin_bit_mask = 1ULL << s_led.pin
                | (s_led.type == BOARD_LED_APA102 ? 1ULL << s_led.clk : 0),
            .mode         = GPIO_MODE_OUTPUT,
            .pull_up_en   = GPIO_PULLUP_DISABLE,
            .pull_down_en = GPIO_PULLDOWN_DISABLE,
            .intr_type    = GPIO_INTR_DISABLE,
        };
        if (gpio_config(&cfg) != ESP_OK) return false;
        if (s_led.type == BOARD_LED_APA102) gpio_set_level(s_led.clk, 0);
        return true;
    }
    case BOARD_LED_NONE:
    default:
        return false;
    }
}

// Dark, then releases the pins (back to their reset state), so a changed
// setting starts from a clean slate.
static void led_close(bool opened)
{
    if (opened) {
        rgb_t off = { 0, 0, 0 };
        switch (s_led.type) {
        case BOARD_LED_WS2812: ws2812_set(off); ws2812_close(); break;
        case BOARD_LED_APA102: apa102_set(off); break;
        case BOARD_LED_GPIO:   gpio_set_level(s_led.pin, s_led.active_low); break;
        default: break;
        }
    }
    if (s_led.type != BOARD_LED_NONE && s_led.type != BOARD_LED_WS2812) {
        gpio_reset_pin(s_led.pin);
        if (s_led.type == BOARD_LED_APA102) gpio_reset_pin(s_led.clk);
    }
}

static void led_show(bool on, rgb_t c)
{
    rgb_t off = { 0, 0, 0 };
    switch (s_led.type) {
    case BOARD_LED_WS2812: ws2812_set(on ? c : off); break;
    case BOARD_LED_APA102: apa102_set(on ? c : off); break;
    case BOARD_LED_GPIO:   gpio_set_level(s_led.pin, s_led.active_low ? !on : on); break;
    default: break;
    }
}

static void led_task(void *arg)
{
    (void)arg;
    bool opened = led_open();

    // Diagnostic: report the smallest free stack seen so far — i.e. right
    // after the driver setup, this task's deepest logging call. This is the
    // hard number behind the stack sizing (was a 2 KB stack overflowing
    // here once the log hook was added); keep an eye on it staying well
    // clear of 0.
    ESP_LOGI(TAG, "stack high-water mark: %u bytes free",
             (unsigned)uxTaskGetStackHighWaterMark(NULL));

    led_state_t last_state = (led_state_t)-1;
    bool        last_quiet = false;
    pattern_t   pat        = pattern_for(ST_CONNECTING, false);
    uint8_t     tick       = 0;
    int         last_on    = -1;   // only send a frame on a change
    rgb_t       colour     = colour_for(ST_CONNECTING);

    while (1) {
        uint32_t req = s_reconf_req;
        if (req != s_reconf_done) {
            led_close(opened);
            opened = led_open();
            last_state = (led_state_t)-1;
            s_reconf_done = req;
        }
        if (!opened) {
            vTaskDelay(pdMS_TO_TICKS(TICK_MS * 10));
            continue;
        }

        led_state_t s     = derive_state();
        bool        quiet = config_led_quiet();
        // Recompute on a quiet-mode toggle too, not just a state change:
        // the user may flip the setting while the dongle sits in READY,
        // when the state itself never changes.
        if (s != last_state || quiet != last_quiet) {
            pat = pattern_for(s, quiet);
            colour = colour_for(s);
            tick = 0;
            last_on = -1;   // new colour: send even if on/off is unchanged
            last_state = s;
            last_quiet = quiet;
        }
        bool on = (pat.on_mask >> tick) & 0x1u;
        if (on != last_on) {
            led_show(on, colour);
            last_on = on;
        }

        tick = (uint8_t)((tick + 1) % pat.ticks);
        vTaskDelay(pdMS_TO_TICKS(TICK_MS));
    }
}

void status_led_reconfigure(void)
{
    uint32_t req = ++s_reconf_req;
    if (!s_task) return;
    // Wait for the task (it ticks every 50 ms, 500 ms when idle), so the
    // caller may hand pins the old LED released to someone else (the
    // display) right after.
    for (int i = 0; i < 100 && s_reconf_done != req; i++) {
        vTaskDelay(pdMS_TO_TICKS(10));
    }
}

void status_led_start(void)
{
    // Always started, even without an LED: the user may configure one at
    // run time (status_led_reconfigure). Idle it only polls a flag.
    //
    // 3.5 KB: the global log hook (log_capture.c) adds a ~64 B frame to
    // *every* log call — including the deep multi-arg INFO line
    // gpio_config() emits. 2 KB overflowed once the hook was added; 2.5 KB
    // left 804 B on the ESP32-C3 but only 324 B on the ESP32-S3, whose
    // Xtensa register windows make the same calls deeper. See the note on
    // stack sizing in log_capture.c. +512 B for the WS2812's RMT setup
    // (#730): 3 KB left 600 B on the S3 Super Mini.
    s_reconf_done = s_reconf_req;
    xTaskCreate(led_task, "status_led", 3584, NULL, 1, &s_task);
}
