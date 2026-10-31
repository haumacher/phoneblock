// Host stand-in for IDF's esp_log.h. The firmware's storage module logs at
// INFO/WARN/ERROR; the lines go to sim_log() so a test can count them (the
// WARN/ERROR ones are what the web UI's log panel would show).
#pragma once
#include <inttypes.h>
#include <stdio.h>

void sim_log(char level, const char *tag, const char *fmt, ...)
    __attribute__((format(printf, 3, 4)));

#define ESP_LOGD(tag, ...) do { (void)(tag); } while (0)
#define ESP_LOGV(tag, ...) do { (void)(tag); } while (0)
#define ESP_LOGI(tag, ...) sim_log('I', tag, __VA_ARGS__)
#define ESP_LOGW(tag, ...) sim_log('W', tag, __VA_ARGS__)
#define ESP_LOGE(tag, ...) sim_log('E', tag, __VA_ARGS__)
