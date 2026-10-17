// Host stand-in for IDF's esp_spiffs.h. Implemented by spiffs_sim.c on top of
// the real SPIFFS core, with the same semantics as esp_spiffs.c.
#pragma once
#include <stddef.h>
#include "esp_err.h"

esp_err_t esp_spiffs_info(const char *partition_label, size_t *total_bytes,
                          size_t *used_bytes);
esp_err_t esp_spiffs_gc(const char *partition_label, size_t size_to_gc);
