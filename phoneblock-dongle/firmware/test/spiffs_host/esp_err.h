// Host stand-in for IDF's esp_err.h — only what storage.c uses.
#pragma once
#include <stdint.h>

typedef int esp_err_t;

#define ESP_OK                 0
#define ESP_FAIL              -1
#define ESP_ERR_INVALID_STATE  0x103
#define ESP_ERR_NOT_FINISHED   0x10C

const char *esp_err_to_name(esp_err_t code);
