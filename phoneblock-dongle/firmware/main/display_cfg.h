// Whether this build has the TFT code (#708, #773): DISPLAY_PRESENT is 1 on
// the ESP32-S3 (T-Dongle-S3) and the classic ESP32 (T-Display V1.1) build,
// 0 on the ESP32-C3 and the Linux service. Kept apart from display.h so
// config.h can guard the display settings without pulling in stats.h; on
// the C3 every line behind it is compiled out and its image does not grow.
#pragma once

#ifdef ESP_PLATFORM
#include "sdkconfig.h"
#endif

#if CONFIG_IDF_TARGET_ESP32S3 || CONFIG_IDF_TARGET_ESP32
#define DISPLAY_PRESENT 1
#else
#define DISPLAY_PRESENT 0
#endif
