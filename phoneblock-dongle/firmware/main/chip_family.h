#pragma once

#include "sdkconfig.h"

// Chip family of this build, spelled as esp-web-tools spells it in a
// manifest's builds[].chipFamily. Improv reports it to the browser
// installer; the OTA path uses it to pick its own build from the manifest.
#if CONFIG_IDF_TARGET_ESP32C3
#define PB_CHIP_FAMILY "ESP32-C3"
#else
#define PB_CHIP_FAMILY "ESP32"
#endif
