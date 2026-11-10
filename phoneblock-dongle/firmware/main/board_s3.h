#pragma once

#include <stdbool.h>

// Which ESP32-S3 board the S3 image runs on (#730). One image serves every
// S3 board, so the board extras are chosen at run time:
//
//   LilyGO T-Dongle-S3  — ST7735 TFT, APA102 RGB LED on GPIO 40/39. Its
//                         chip is a plain ESP32-S3 without in-package PSRAM.
//   any other S3 board  — no display; a WS2812 RGB LED on GPIO 48, as on the
//                         ESP32-S3 Super Mini and the DevKitC-1 v1.0. Those
//                         carry an ESP32-S3R2/R8 or FH4R2 (in-package PSRAM)
//                         or a module with PSRAM.
//
// The discriminator is the PSRAM capacity burnt into the eFuses: the
// T-Dongle-S3 is the only supported S3 board without PSRAM. Reading it needs
// no PSRAM driver and touches no pin, so nothing is driven before the board
// is known.
//
// Built only for IDF_TARGET=esp32s3 (main/CMakeLists.txt).

// True on the LilyGO T-Dongle-S3. The first call logs the decision.
bool board_is_t_dongle_s3(void);
