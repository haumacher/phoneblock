#include "status_led.h"

#include <string.h>

#include "driver/gpio.h"
#include "esp_chip_info.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "sdkconfig.h"

#include "api.h"
#include "config.h"
#include "sip_register.h"
#include "wifi.h"

static const char *TAG = "status_led";

#define TICK_MS 50

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

static pattern_t pattern_for(led_state_t s)
{
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

static void led_task(void *arg)
{
    const int  gpio       = LED_ARG_GPIO(arg);
    const bool active_low = LED_ARG_ACTIVE_LOW(arg);

    gpio_config_t cfg = {
        .pin_bit_mask = 1ULL << gpio,
        .mode         = GPIO_MODE_OUTPUT,
        .pull_up_en   = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type    = GPIO_INTR_DISABLE,
    };
    gpio_config(&cfg);

    led_state_t last_state = (led_state_t)-1;
    pattern_t   pat        = pattern_for(ST_CONNECTING);
    uint8_t     tick       = 0;

    while (1) {
        led_state_t s = derive_state();
        if (s != last_state) {
            pat = pattern_for(s);
            tick = 0;
            last_state = s;
        }
        bool on = (pat.on_mask >> tick) & 0x1u;
        int level = active_low ? !on : on;
        gpio_set_level(gpio, level);

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
    xTaskCreate(led_task, "status_led", 2048,
                (void *)LED_ARG_PACK(gpio, active_low), 1, NULL);
}
