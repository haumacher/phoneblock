// Host stand-in for the joltwallet/littlefs component's esp_littlefs.h:
// register (mount), format and info on the simulation's RAM flash, with the
// configuration esp_littlefs.c mounts a flash partition with (lfs_sim.h).
#pragma once
#include <stdbool.h>
#include <stddef.h>

#include "esp_err.h"

typedef struct {
    const char *base_path;
    const char *partition_label;
    bool        format_if_mount_failed;
    bool        dont_mount;
} esp_vfs_littlefs_conf_t;

// Mounts; on failure with format_if_mount_failed, logs the library's WARN,
// formats and mounts again — as esp_vfs_littlefs_register() does.
esp_err_t esp_vfs_littlefs_register(const esp_vfs_littlefs_conf_t *conf);
esp_err_t esp_littlefs_format(const char *partition_label);
// total = block count * block size, used = lfs_fs_size() * block size.
esp_err_t esp_littlefs_info(const char *partition_label, size_t *total,
                            size_t *used);
