#pragma once

#include <stdbool.h>

// Chip-internal temperature sensor, on the chips that have a usable one
// (ESP32-C3, -S3, -C6, … — SOC_TEMP_SENSOR_SUPPORTED). The classic ESP32
// (PICO-D4 dongle) has none under ESP-IDF 5.x; there both calls are no-ops.
//
// It measures the die, not the air: on a mains-powered dongle with the radio
// always on (wifi.c) that is the figure that matters for "the board runs
// hot". Accuracy is a few °C — good for comparing settings on one board.

// Installs and enables the sensor. Call once at boot.
void chip_temp_init(void);

// Current die temperature in °C. False if the chip has no sensor or the
// reading failed.
bool chip_temp_read(float *celsius);
