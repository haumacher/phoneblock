// Host simulation of the dongle's "storage" partition: the littlefs core of
// the joltwallet/littlefs component (managed_components/, the very sources
// the firmware links) on a RAM-backed NOR flash, configured as esp_littlefs.c
// mounts a flash partition with the component's Kconfig defaults:
//
//   partition size  SIM_PART_SIZE   (storage line of partitions.csv)
//   block size      SIM_BLOCK_SIZE  (= flash erase sector, 4 KB)
//   read/prog size  128, cache 512, lookahead 128, block_cycles 512
//
// esp_littlefs.h / esp_partition.h stand-ins (this directory) let
// storage.c's LittleFS backend run against it unchanged: partition_content(),
// storage_mount(), the budget and storage_make_room(). The file helpers
// mirror what esp_littlefs.c's VFS layer makes of the firmware's stdio calls
// (open with the mtime attribute and a sync, one lfs_file_write per stdio
// buffer flush, close; remove; rename).
#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifndef SIM_PART_SIZE
#  error "SIM_PART_SIZE missing: the Makefile derives it from partitions.csv"
#endif

#define SIM_BLOCK_SIZE 4096u

// Erases the whole flash (all 0xFF), formats LittleFS and mounts it.
void sim_init(void);

// Erases the whole flash and leaves it unmounted — a factory-fresh partition
// for storage_mount().
void sim_erase(void);

// Writes raw bytes at `offset` of the partition (NOR semantics: clears bits
// only) — e.g. an older firmware's SPIFFS for storage_mount() to find.
void sim_write_raw(uint32_t offset, const void *data, size_t n);

// Unmounts (a reboot); the flash keeps its content.
void sim_unmount(void);
bool sim_mounted(void);

// Number of lfs_format() calls since the last sim_init()/sim_erase().
int sim_format_count(void);

// Raw littlefs error code of the last failing helper call (0 if none).
int sim_last_err(void);

// Writes a file of `size` synthetic bytes the way fopen("wb") + fwrite +
// fclose does on the device with a stdio buffer of `chunk` bytes: one
// lfs_file_write per full buffer, the remainder on close. True on success;
// on failure *written holds the bytes accepted and sim_last_err() the
// littlefs error (LFS_ERR_NOSPC when out of space). The partial file is left
// in place, as on the device.
bool sim_write_file(const char *path, size_t size, size_t chunk,
                    size_t *written);

// unlink() / rename(); true on success.
bool sim_unlink(const char *path);
bool sim_rename(const char *from, const char *to);

// Size of an existing file, or -1.
long sim_file_size(const char *path);

// Number of block erases since sim_init() (flash wear).
long sim_erase_count(void);

// Log lines captured since the last reset: storage.c's (esp_log.h stand-in)
// and the littlefs core's WARN/ERROR lines.
int  sim_log_count(char level);
void sim_log_reset(void);
// The message of the last line at `level` ("" if none).
const char *sim_last_log(char level);
// When true, captured lines are also printed (debugging a test).
extern bool sim_log_echo;
