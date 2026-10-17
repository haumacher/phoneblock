// Host stand-in for IDF's esp_log.h.
//
// The SPIFFS core only uses ESP_LOGD for its (disabled) debug output. The
// firmware's storage module logs at INFO/WARN; those lines are handed to
// sim_log() so a test can count them (e.g. one WARN per failed attempt).
#pragma once
#include <inttypes.h>
#include <stdio.h>

void sim_log(char level, const char *tag, const char *fmt, ...)
    __attribute__((format(printf, 3, 4)));

#ifdef SIM_LOGD_ECHO   // -DSIM_LOGD_ECHO -DCONFIG_SPIFFS_DBG=1: trace the core
#define ESP_LOGD(tag, ...) fprintf(stderr, __VA_ARGS__)
#else
#define ESP_LOGD(tag, ...) do { (void)(tag); } while (0)
#endif
#define ESP_LOGV(tag, ...) do { (void)(tag); } while (0)
#define ESP_LOGI(tag, ...) sim_log('I', tag, __VA_ARGS__)
#define ESP_LOGW(tag, ...) sim_log('W', tag, __VA_ARGS__)
#define ESP_LOGE(tag, ...) sim_log('E', tag, __VA_ARGS__)
