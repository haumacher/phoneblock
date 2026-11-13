#pragma once

#include <stdbool.h>

#include "stats.h"

#ifdef ESP_PLATFORM
#include "sdkconfig.h"
#endif

// The board's TFT: on the ESP32-S3 build the LilyGO T-Dongle-S3's 0.96"
// ST7735 (160x80), always; on the classic ESP32 build the LilyGO T-Display
// V1.1's 1.14" ST7789 (240x135), when the user picked it in the web UI
// (#708; default: no display, and then nothing below touches a pin). Built
// for IDF_TARGET=esp32s3 and esp32 (display.c, display_model.c, lcd_*.c,
// see main/CMakeLists.txt); on the ESP32-C3 image and the Linux daemon the
// notifications below are empty inline functions, so the callers in
// stats.c / sip_register.c need no #if and those builds carry none of it.
//
// Screens (layout in display_model.h, drawing in display.c):
//   idle  — the PhoneBlock logo, with a red badge counting the blocked spam
//           calls currently in the call list (hidden while 0; clearing the
//           list in the web UI clears it).
//   call  — the incoming caller, like a phone in the room: name and number,
//           or the number with its PhoneBlock rating. Shown from the
//           screening decision until the call ends for the dongle (CANCEL,
//           the spam call's teardown) or a 60 s fallback timeout, whichever
//           comes first; a new call replaces it.
// The backlight is full while a call is shown and for a minute after boot
// or a call, dimmed otherwise (off on the T-Display, which switches it by
// GPIO). Drawing runs from an esp_timer callback, so
// the SIP task and the web server never wait for the panel. Failures are
// logged once and leave the display as it is — the dongle works without it
// (the board also exists without a display).

#if CONFIG_IDF_TARGET_ESP32S3 || CONFIG_IDF_TARGET_ESP32

#define DISPLAY_PRESENT 1

// The board's wiring. The status LED must keep off these pins while the
// display is on (board.c).
#if CONFIG_IDF_TARGET_ESP32S3
// T-Dongle-S3, as in LilyGO's factory firmware.
#define PIN_MOSI      3
#define PIN_SCLK      5
#define PIN_CS        4
#define PIN_DC        2
#define PIN_RST       1
#define PIN_BACKLIGHT 38    // active-low
#else
// T-Display V1.1, as in LilyGO's TTGO-T-Display examples.
#define PIN_MOSI      19
#define PIN_SCLK      18
#define PIN_CS        5
#define PIN_DC        16    // the flash on chips with embedded flash (board.c)
#define PIN_RST       23
#define PIN_BACKLIGHT 4     // active-high
#endif
#define DISPLAY_PIN_MASK ((1ULL << PIN_MOSI) | (1ULL << PIN_SCLK) | (1ULL << PIN_CS) \
                          | (1ULL << PIN_DC) | (1ULL << PIN_RST) | (1ULL << PIN_BACKLIGHT))

// Brings the panel up and shows the logo. Called only where the display is
// on (board_effective()).
void display_start(void);

// Turns the display upside down (true) or back to normal and redraws the
// current screen. Called by the web UI after the setting was stored; does
// nothing if the display did not come up.
void display_set_flipped(bool flipped);

// Switches the display on or off at run time (the "display" setting,
// board.h). Starts it if it was never brought up; off leaves the panel
// dark until switched on again.
void display_set_enabled(bool on);

// Whether display_start() ran in this boot. Its pins then stay the
// display's even while it is switched off: SPI and backlight are
// routed once, and a status LED borrowing a pin would cut that routing
// (board.c refuses it until the next restart).
bool display_was_started(void);

// A new incoming call was screened; `call` is its entry as the call list
// would show it (copied). Replaces any call on screen.
void display_call_started(const stats_call_t *call);

// The call on screen ended for the dongle. Back to the idle screen.
void display_call_ended(void);

// The call list changed (entry added or dropped, re-rated, cleared): the
// badge is recounted.
void display_calls_changed(void);

#else

#define DISPLAY_PRESENT 0

static inline void display_call_started(const stats_call_t *call) { (void)call; }
static inline void display_call_ended(void) {}
static inline void display_calls_changed(void) {}

#endif
