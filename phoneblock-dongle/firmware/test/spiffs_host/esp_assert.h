// Host stand-in for IDF's esp_assert.h (used by spiffs_config.h).
#pragma once
#include <assert.h>
#define ESP_STATIC_ASSERT _Static_assert
