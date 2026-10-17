#pragma once

// Boot logo on the LilyGO T-Dongle-S3's 0.96" ST7735 TFT (160x80). Built
// only for IDF_TARGET=esp32s3 (see main/CMakeLists.txt); the ESP32 and
// ESP32-C3 images contain none of this.
//
// Draws the PhoneBlock logo once and owns the backlight (GPIO 38,
// active-low, LEDC PWM): full brightness for the first minute after boot,
// dimmed afterwards. No task; the dimming runs from a one-shot esp_timer.
// Failures are logged and leave the display dark — the dongle works
// without it (the board also exists without a display).
void display_start(void);
