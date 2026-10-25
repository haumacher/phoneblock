#pragma once

#include <stdbool.h>

#include "stats.h"

#ifdef ESP_PLATFORM
#include "sdkconfig.h"
#endif

// The LilyGO T-Dongle-S3's 0.96" ST7735 TFT (160x80). Built only for
// IDF_TARGET=esp32s3 (display.c, display_model.c, lcd_*.c, see
// main/CMakeLists.txt); on the ESP32 and ESP32-C3 images and the Linux
// daemon the notifications below are empty inline functions, so the
// callers in stats.c / sip_register.c need no #if and those builds carry
// none of it.
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
// or a call, dimmed otherwise. Drawing runs from an esp_timer callback, so
// the SIP task and the web server never wait for the panel. Failures are
// logged once and leave the display as it is — the dongle works without it
// (the board also exists without a display).

#if CONFIG_IDF_TARGET_ESP32S3

#define DISPLAY_PRESENT 1

void display_start(void);

// Turns the display upside down (true) or back to normal and redraws the
// current screen. Called by the web UI after the setting was stored; does
// nothing if the display did not come up.
void display_set_flipped(bool flipped);

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
