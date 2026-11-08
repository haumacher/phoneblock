#pragma once

#include <stdbool.h>

#include "board_hw.h"

// The board's extras — status LED and the display (T-Dongle-S3 on the
// ESP32-S3 build, T-Display V1.1 on the classic ESP32 build, #708) — as
// settings with a detected default (#730).
//
// A chip cannot identify the board it sits on (no board ID; the eFuses
// describe the chip only), so detection is a guess from chip properties:
//   ESP32     embedded flash (PICO-D4 dongle) → gpio:10:low, else gpio:2;
//             never a display (the T-Display is only switched on by hand)
//   ESP32-C3  → gpio:8:low (C3 Super Mini)
//   ESP32-S3  no PSRAM in the package (LilyGO T-Dongle-S3) → apa102:40:39
//             and the display; with PSRAM (S3 Super Mini, DevKitC-1 v1.0)
//             → ws2812:48, no display
// CONFIG_STATUS_LED_GPIO (Kconfig) replaces the detected LED of a custom
// build. The user's settings ("led_hw", "display", web UI ▸ System) win over
// all of it; the guess only fills in what they did not set.
//
// ESP-IDF only (all targets); the pure parsing and pin rules are board_hw.h.

// The detected default.
void board_detected(board_led_t *led, bool *display);

// What the dongle uses: each setting if set (and, for the LED, valid on this
// chip), else the detected default. A stored LED the pin rules refuse (e.g.
// written by another firmware) falls back to the default with a WARN.
void board_effective(board_led_t *led, bool *display);

// Checks the settings the web UI wants to store ("" = back to the detected
// default; display "1"/"0"). NULL when fine, else a short English reason.
const char *board_check_settings(const char *led_hw, const char *display);

// Whether this dongle can drive a display at all: the S3 and classic ESP32
// builds, unless a display pin is forbidden on the chip (board_display_fits(),
// e.g. the T-Display on the PICO-D4).
bool board_display_supported(void);
