// Whether this build records the calls held in dialog mode (#663):
// PB_CALL_RECORDING is 1 on the T-Dongle-S3 (16 MB flash) and the Linux
// service, 0 on the 4 MB boards (classic ESP32, ESP32-C3). There every line
// of recording code is compiled out — call_rec.c and call_rec_mix.c are not
// even in their source list (main/CMakeLists.txt) — so their images do not
// grow: the C3 has only a few percent of its app partition left, and its
// 640 KB storage partition has no room for recordings anyway.
#pragma once

#ifdef ESP_PLATFORM
#include "sdkconfig.h"
#endif

#if !defined(ESP_PLATFORM) || defined(CONFIG_IDF_TARGET_ESP32S3)
#define PB_CALL_RECORDING 1
#else
#define PB_CALL_RECORDING 0
#endif
