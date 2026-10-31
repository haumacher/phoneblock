// Included by the littlefs core (lfs_util.h, -DLFS_DEFINES=lfs_sim_defs.h):
// its WARN and ERROR lines go to the simulation's log counter, as
// esp_littlefs' lfs_config.h routes them to ESP_LOGW/ESP_LOGE on the device.
#pragma once

void sim_lfs_log(char level, const char *fmt, ...)
    __attribute__((format(printf, 2, 3)));

#define LFS_TRACE(...)
#define LFS_DEBUG(...)
#define LFS_WARN(...)  sim_lfs_log('W', __VA_ARGS__)
#define LFS_ERROR(...) sim_lfs_log('E', __VA_ARGS__)
